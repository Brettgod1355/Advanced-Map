/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * Draws the map: region pictures, the Wilderness levels, place names, icons, other plugins' layers, the player, an
 * icon's info box or menu, a bar with the tile under the mouse, and the buttons. The same drawing goes over the game
 * view and into the pop-out window. It touches no client state, so it runs on the client thread for the overlay and
 * on the Swing thread for the window.
 */
final class MapPainter
{
	enum Button
	{
		CLOSE,
		AREA,
		MODE,
		/** Shows or hides every icon at once; place names stay. */
		ICONS,
		/** Opens the list that turns each group and kind of icon on or off. */
		FILTERS,
		/** Moves the map back to the player. */
		LOCATE,
		FLOOR_UP,
		FLOOR_DOWN
	}

	/**
	 * What a right click or a click opened: the info box of an icon, or the menu of an icon or of a tile with no icon
	 * ({@code spot} null), with where on the screen and which tile.
	 */
	@Value
	static class Selection
	{
		MapSpot spot;
		boolean menu;
		int x;
		int y;
		WorldPoint point;
	}

	enum Action
	{
		HIDE,
		UNHIDE,
		ROUTE,
		CLEAR_ROUTE
	}

	@Value
	static class MenuEntry
	{
		Action action;
		String text;
	}

	static final int BUTTON_HEIGHT = 28;
	static final int LOCATE_SIZE = 36;
	/** The player's marker: the locate button's crosshair at this size (about 36 pixels across). */
	static final double PLAYER_MARKER_SCALE = 1.4;
	private static final float PLAYER_STROKE = 2.5f;
	private static final Color LOCATED = new Color(0x5AA9FF);
	static final int FLOOR_WIDTH = 64;
	static final int FLOOR_HEIGHT = 26;
	/** Room for the floor's name between its two buttons. */
	private static final int FLOOR_LABEL = 22;
	private static final int GAP = 8;
	private static final int AREA_WIDTH = 110;
	private static final int MODE_WIDTH = 100;
	private static final int ICONS_WIDTH = 76;
	private static final int FILTERS_WIDTH = 86;
	private static final int MENU_ROW = 20;
	private static final int PAD = 8;
	/** The Wilderness teleport limits show from this zoom in, as icons do. */
	static final double WILDERNESS_ZOOM = MapIcons.ICON_ZOOM;

	private static final Color BACKGROUND = new Color(0x0B1520);
	private static final Color LOADING = new Color(0x13202D);
	/** Laid over the floors below the one shown: darker and duller, so the floor itself stands out. */
	private static final Color LOWER_FLOORS = new Color(0x0B, 0x15, 0x20, 165);
	private static final Color BAR = new Color(0, 0, 0, 170);
	private static final Color TEXT = new Color(0xE8E2D0);
	private static final Color PLAYER = new Color(0xFFFFFF);
	private static final Color OUTLINE = new Color(0, 0, 0, 200);
	private static final Color TELEPORT_LIMIT = new Color(0xE8, 0x2E, 0x22);
	private static final Stroke TELEPORT_LIMIT_STROKE = new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
		10, new float[]{10, 6}, 0);
	private static final Stroke THICK = new BasicStroke(2);

	/** For measuring text outside a frame (where the menu's entry is, for a click). */
	private static final Graphics2D MEASURE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

	private final RegionImages images;
	private final MapMessages messages;
	private final MapIcons icons;
	private final MapLabels labels;
	private final MapFilters filters;

	MapPainter(RegionImages images, MapMessages messages, MapIcons icons, MapLabels labels, MapFilters filters)
	{
		this.images = images;
		this.messages = messages;
		this.icons = icons;
		this.labels = labels;
		this.filters = filters;
	}

	/**
	 * Where the buttons sit: right to left along the top, close (over the game only; a window has its own), the map
	 * switch, and the button that moves the map between the game view and its own window; at the top left, the Icons
	 * switch and the Filters list's button; at the bottom right, the button that finds the player.
	 */
	static Map<Button, Rectangle> buttons(int width, int height, boolean window)
	{
		Map<Button, Rectangle> out = new EnumMap<>(Button.class);
		out.put(Button.ICONS, new Rectangle(GAP, GAP, ICONS_WIDTH, BUTTON_HEIGHT));
		out.put(Button.FILTERS, new Rectangle(GAP * 2 + ICONS_WIDTH, GAP, FILTERS_WIDTH, BUTTON_HEIGHT));
		out.put(Button.LOCATE, new Rectangle(width - GAP - LOCATE_SIZE, height - GAP - LOCATE_SIZE, LOCATE_SIZE,
			LOCATE_SIZE));
		int down = height - GAP - LOCATE_SIZE - GAP - FLOOR_HEIGHT;
		out.put(Button.FLOOR_DOWN, new Rectangle(width - GAP - FLOOR_WIDTH, down, FLOOR_WIDTH, FLOOR_HEIGHT));
		out.put(Button.FLOOR_UP, new Rectangle(width - GAP - FLOOR_WIDTH, down - FLOOR_LABEL - FLOOR_HEIGHT, FLOOR_WIDTH,
			FLOOR_HEIGHT));
		int right = width - GAP;
		if (!window)
		{
			out.put(Button.CLOSE, new Rectangle(right - BUTTON_HEIGHT, GAP, BUTTON_HEIGHT, BUTTON_HEIGHT));
			right -= BUTTON_HEIGHT + GAP;
		}
		out.put(Button.AREA, new Rectangle(right - AREA_WIDTH, GAP, AREA_WIDTH, BUTTON_HEIGHT));
		right -= AREA_WIDTH + GAP;
		out.put(Button.MODE, new Rectangle(right - MODE_WIDTH, GAP, MODE_WIDTH, BUTTON_HEIGHT));
		return out;
	}

	/** The button under a point, or null. */
	static Button buttonAt(int x, int y, int width, int height, boolean window)
	{
		for (Map.Entry<Button, Rectangle> button : buttons(width, height, window).entrySet())
		{
			if (button.getValue().contains(x, y))
			{
				return button.getKey();
			}
		}
		return null;
	}

	/**
	 * Draws one frame of a floor ({@code plane}, 0 for the ground). {@code player} is where the player stands (null when
	 * logged out), {@code mouse} where the pointer is (null when it is elsewhere), {@code selection} what a click
	 * opened (or null).
	 */
	void paint(Graphics2D g, MapViewport.View view, MapArea area, int plane, WorldPoint player, Point mouse,
		boolean window, MapSettings settings, Progress progress, Selection selection)
	{
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, view.width, view.height);
		boolean loading = drawRegions(g, view, area, plane);
		if (settings.isWildernessLines())
		{
			drawWilderness(g, view, area, id -> images.isAbsent(0, id));
		}
		if (settings.isPlaceNames())
		{
			drawLabels(g, view, area, labels.all(), buttons(view.width, view.height, window).values());
		}
		List<MapIcons.Shown> shown = icons.visible(view, area, plane, settings, progress);
		drawIcons(g, shown, selection);

		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (MapLayer layer : messages.layers())
		{
			drawLayer(g, view, layer);
		}
		drawPlayer(g, view, area, player);
		if (selection != null)
		{
			MapIcons.Shown selected = find(shown, selection.getSpot());
			if (selection.isMenu())
			{
				drawMenu(g, view, selection, settings, progress);
			}
			else if (selected != null)
			{
				drawInfo(g, view, selected, settings, progress);
			}
		}
		drawBar(g, view, mouse, loading, window, !shown.isEmpty());
		drawButtons(g, view, area, plane, window, settings, player != null, filters.isOpen());
		if (filters.isOpen())
		{
			Rectangle button = buttons(view.width, view.height, window).get(Button.FILTERS);
			filters.draw(g, MapFilters.layout(button, view.height), filters.entries(progress), settings, icons);
		}
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	/**
	 * Draws the region pictures there are and asks for the rest. On a floor above the ground, the floors below are
	 * drawn first and darkened, and the floor itself in full color on top, so it shows where it sits. True while some
	 * of the floor are still being drawn.
	 */
	private boolean drawRegions(Graphics2D g, MapViewport.View view, MapArea area, int plane)
	{
		int rx0 = Math.max(area.minRegionX, (int) Math.floor(view.worldX(0) / 64));
		int rx1 = Math.min(area.maxRegionX, (int) Math.floor(view.worldX(view.width) / 64));
		int ry0 = Math.max(area.minRegionY, (int) Math.floor(view.worldY(view.height) / 64));
		int ry1 = Math.min(area.maxRegionY, (int) Math.floor(view.worldY(0) / 64));

		List<Integer> visible = new ArrayList<>();
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				visible.add(rx << 8 | ry);
			}
		}
		// the middle of the screen first
		double cx = view.centerX / 64 - 0.5;
		double cy = view.centerY / 64 - 0.5;
		visible.sort(Comparator.comparingDouble(id ->
		{
			double dx = (id >> 8) - cx;
			double dy = (id & 0xFF) - cy;
			return dx * dx + dy * dy;
		}));

		RegionImages.Level level = RegionImages.Level.forZoom(view.zoom);
		images.request(plane, visible, level);

		Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, view.zoom > 4
			? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
			: RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		boolean loading = false;
		for (int p = 0; p <= plane; p++)
		{
			if (p == plane && plane > 0)
			{
				g.setColor(LOWER_FLOORS);
				g.fillRect(0, 0, view.width, view.height);
			}
			for (int id : visible)
			{
				int baseX = (id >> 8) << 6;
				int baseY = (id & 0xFF) << 6;
				int x0 = (int) Math.floor(view.screenX(baseX));
				int x1 = (int) Math.floor(view.screenX(baseX + 64));
				int y0 = (int) Math.floor(view.screenY(baseY + 64));
				int y1 = (int) Math.floor(view.screenY(baseY));
				BufferedImage image = images.get(p, id, level);
				if (image != null)
				{
					g.drawImage(image, x0, y0, x1 - x0, y1 - y0, null);
				}
				else if (!images.isAbsent(p, id) && p == plane)
				{
					loading = true;
					if (p == 0)
					{
						g.setColor(LOADING);
						g.fillRect(x0, y0, x1 - x0, y1 - y0);
					}
				}
			}
		}
		if (interpolation != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		}
		return loading;
	}

	/**
	 * Draws the two Wilderness teleport limits as red dashed lines, where level 21 and level 31 begin (teleports work up
	 * to level 20, the members' few up to 30), each with its name at its east end. Only when zoomed in; only across
	 * regions the game has (underground, the caves, not the empty space between them), and not across the dungeon
	 * regions that have a level of their own ({@link Wilderness}). {@code absent} says which region ids have no map.
	 */
	static void drawWilderness(Graphics2D g, MapViewport.View view, MapArea area, IntPredicate absent)
	{
		if (view.zoom < WILDERNESS_ZOOM)
		{
			return;
		}
		Shape clip = g.getClip();
		Stroke stroke = g.getStroke();
		g.setFont(FontManager.getRunescapeSmallFont());

		Rectangle counted = Wilderness.counted(area);
		Area own = new Area();
		for (int[] region : Wilderness.OWN_LEVEL)
		{
			own.add(new Area(screenRect(view, new Rectangle(region[0] << 6, region[1] << 6, 64, 64))));
		}
		int firstColumn = Math.max(counted.x >> 6, (int) Math.floor(view.worldX(0) / 64));
		int lastColumn = Math.min((counted.x + counted.width - 1) >> 6, (int) Math.floor(view.worldX(view.width) / 64));
		double west = view.screenX(counted.x);
		for (int level : Wilderness.TELEPORT_LIMITS)
		{
			int worldY = Wilderness.teleportLimitY(counted, level);
			int row = worldY >> 6;
			Area along = new Area();
			int eastColumn = -1;
			for (int column = firstColumn; column <= lastColumn; column++)
			{
				if (!absent.test(column << 8 | row))
				{
					along.add(new Area(screenRect(view, new Rectangle(column << 6, row << 6, 64, 64))));
					eastColumn = column;
				}
			}
			if (eastColumn < 0)
			{
				continue;
			}
			along.subtract(own);
			g.setClip(clip);
			g.clip(along);
			int y = (int) Math.round(view.screenY(worldY));
			double east = view.screenX(Math.min(counted.x + counted.width, (eastColumn + 1) << 6));
			g.setStroke(TELEPORT_LIMIT_STROKE);
			g.setColor(OUTLINE);
			g.drawLine((int) west, y + 1, (int) east, y + 1);
			g.setColor(TELEPORT_LIMIT);
			g.drawLine((int) west, y, (int) east, y);
			drawLineLabel(g, "Level " + level + " teleport limit", view, west, east, y, TELEPORT_LIMIT, clip, own);
		}
		g.setStroke(stroke);
		g.setClip(clip);
	}

	/** A world rectangle (tiles, max ends exclusive) on the screen. */
	private static Rectangle screenRect(MapViewport.View view, Rectangle world)
	{
		int x0 = (int) Math.round(view.screenX(world.x));
		int x1 = (int) Math.round(view.screenX(world.x + world.width));
		int y0 = (int) Math.round(view.screenY(world.y + world.height));
		int y1 = (int) Math.round(view.screenY(world.y));
		return new Rectangle(x0, y0, x1 - x0, y1 - y0);
	}

	/**
	 * Text just above a line at screen row {@code y}, at its east end, kept on the screen while any of the line is. It
	 * is drawn whole, within the frame's {@code clip}, or not at all where it would cover {@code hidden}.
	 */
	private static void drawLineLabel(Graphics2D g, String text, MapViewport.View view, double west, double east,
		int y, Color color, Shape clip, Area hidden)
	{
		FontMetrics metrics = g.getFontMetrics();
		int width = metrics.stringWidth(text);
		double x = Math.max(west + 4, Math.min(east, view.width) - width - 4);
		int baseline = y - 3;
		if (hidden.intersects(x - 1, baseline - metrics.getAscent(), width + 2, metrics.getHeight()))
		{
			return;
		}
		Shape lines = g.getClip();
		g.setClip(clip);
		drawShadowed(g, text, (int) x, baseline, color);
		g.setClip(lines);
	}

	private static void drawShadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/**
	 * Draws the place names that show at this zoom, kingdoms first, then towns, then smaller places, leaving out any
	 * that would cover one already drawn or a button.
	 */
	static void drawLabels(Graphics2D g, MapViewport.View view, MapArea area, List<MapLabels.Label> labels,
		Collection<Rectangle> buttons)
	{
		List<Rectangle> taken = new ArrayList<>(buttons);
		for (int size = MapLabels.KINGDOM; size >= MapLabels.PLACE; size--)
		{
			if (!MapLabels.shows(size, view.zoom))
			{
				continue;
			}
			g.setFont(labelFont(size));
			FontMetrics metrics = g.getFontMetrics();
			for (MapLabels.Label label : labels)
			{
				if (label.getSize() != size || MapArea.of(label.getY()) != area)
				{
					continue;
				}
				int width = 0;
				for (String line : label.getLines())
				{
					width = Math.max(width, metrics.stringWidth(line));
				}
				int height = metrics.getHeight() * label.getLines().size();
				int cx = (int) Math.round(view.screenX(label.getX() + 0.5));
				int cy = (int) Math.round(view.screenY(label.getY() + 0.5));
				Rectangle box = new Rectangle(cx - width / 2 - 2, cy - height / 2 - 2, width + 4, height + 4);
				if (!box.intersects(0, 0, view.width, view.height) || overlaps(box, taken))
				{
					continue;
				}
				taken.add(box);
				int y = box.y + 2 + metrics.getAscent();
				for (String line : label.getLines())
				{
					int x = cx - metrics.stringWidth(line) / 2;
					g.setColor(Color.BLACK);
					g.drawString(line, x + 1, y + 1);
					g.drawString(line, x - 1, y + 1);
					g.drawString(line, x + 1, y - 1);
					g.drawString(line, x - 1, y - 1);
					g.setColor(label.getColor());
					g.drawString(line, x, y);
					y += metrics.getHeight();
				}
			}
		}
	}

	private static java.awt.Font labelFont(int size)
	{
		switch (size)
		{
			case MapLabels.KINGDOM:
				return FontManager.getRunescapeBoldFont().deriveFont(24f);
			case MapLabels.TOWN:
				return FontManager.getRunescapeBoldFont();
			default:
				return FontManager.getRunescapeSmallFont();
		}
	}

	private static boolean overlaps(Rectangle box, List<Rectangle> taken)
	{
		for (Rectangle other : taken)
		{
			if (other.intersects(box))
			{
				return true;
			}
		}
		return false;
	}

	/** Draws the icons on screen, with a mark for each look and a ring round the one whose info box is open. */
	void drawIcons(Graphics2D g, List<MapIcons.Shown> shown, Selection selection)
	{
		java.awt.Composite composite = g.getComposite();
		for (MapIcons.Shown icon : shown)
		{
			Rectangle at = icon.getBounds();
			MapIcons.Look look = icon.getLook();
			if (look == MapIcons.Look.HIDDEN)
			{
				g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
			}
			Teleport teleport = icon.getSpot().getTeleport();
			if (teleport != null)
			{
				drawTeleportMark(g, at, teleport.getType(), look == MapIcons.Look.LOCKED);
			}
			else
			{
				BufferedImage image = icons.image(icon.getSpot().getAreaId(), look == MapIcons.Look.LOCKED);
				if (image != null)
				{
					if (at.width == image.getWidth() && at.height == image.getHeight())
					{
						g.drawImage(image, at.x, at.y, null);
					}
					else
					{
						// whole multiples keep the game's pixel look; other sizes are smoothed
						Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
						g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, at.width % image.getWidth() == 0
							? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
							: RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						g.drawImage(image, at.x, at.y, at.width, at.height, null);
						if (interpolation != null)
						{
							g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
						}
					}
				}
			}
			g.setComposite(composite);
			drawBadge(g, at, look);
			if (selection != null && icon.getSpot().equals(selection.getSpot()))
			{
				Stroke stroke = g.getStroke();
				g.setStroke(THICK);
				g.setColor(PLAYER);
				g.drawOval(at.x - 3, at.y - 3, at.width + 5, at.height + 5);
				g.setStroke(stroke);
			}
		}
	}

	/** Advanced Map's own teleport mark: a four-pointed spark on a disc in the colors of its spellbook or kind. */
	static void drawTeleportMark(Graphics2D g, Rectangle at, Teleport.Type type, boolean locked)
	{
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(OUTLINE);
		g.fillOval(at.x - 1, at.y - 1, at.width + 2, at.height + 2);
		g.setColor(locked ? new Color(0x6E6E6E) : teleportColor(type));
		g.fillOval(at.x, at.y, at.width, at.height);
		double cx = at.getCenterX();
		double cy = at.getCenterY();
		double r = at.width * 0.36;
		double w = r * 0.32;
		Path2D spark = new Path2D.Double();
		spark.moveTo(cx, cy - r);
		spark.lineTo(cx + w, cy - w);
		spark.lineTo(cx + r, cy);
		spark.lineTo(cx + w, cy + w);
		spark.lineTo(cx, cy + r);
		spark.lineTo(cx - w, cy + w);
		spark.lineTo(cx - r, cy);
		spark.lineTo(cx - w, cy - w);
		spark.closePath();
		g.setColor(locked ? new Color(0xBDBDBD) : Color.WHITE);
		g.fill(spark);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	static Color teleportColor(Teleport.Type type)
	{
		switch (type)
		{
			case NORMAL_MAGIC:
				return new Color(0x3D7FE0);
			case ANCIENT_MAGICKS:
				return new Color(0x8E4FCF);
			case LUNAR_MAGIC:
				return new Color(0x7F9CC4);
			case ARCEUUS_MAGIC:
				return new Color(0x2F9E68);
			case JEWELLERY:
				return new Color(0xC8961E);
			case SCROLL:
				return new Color(0xB59B62);
			default:
				return new Color(0x1E9EA6);
		}
	}

	/** A small mark at an icon's lower right: red no-entry for locked, a green tick for done, yellow for started. */
	private static void drawBadge(Graphics2D g, Rectangle at, MapIcons.Look look)
	{
		if (look != MapIcons.Look.LOCKED && look != MapIcons.Look.DONE && look != MapIcons.Look.STARTED)
		{
			return;
		}
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int size = 9;
		int x = at.x + at.width - size + 2;
		int y = at.y + at.height - size + 2;
		g.setColor(OUTLINE);
		g.fillOval(x - 1, y - 1, size + 2, size + 2);
		Stroke stroke = g.getStroke();
		switch (look)
		{
			case LOCKED:
				g.setColor(new Color(0xE0342A));
				g.fillOval(x, y, size, size);
				g.setColor(Color.WHITE);
				g.fillRect(x + 2, y + size / 2 - 1, size - 4, 2);
				break;
			case DONE:
				g.setColor(new Color(0x3FA34D));
				g.fillOval(x, y, size, size);
				g.setColor(Color.WHITE);
				g.setStroke(new BasicStroke(1.5f));
				g.drawPolyline(new int[]{x + 2, x + 4, x + 7}, new int[]{y + 5, y + 7, y + 2}, 3);
				break;
			default:
				g.setColor(new Color(0xF2C230));
				g.fillOval(x, y, size, size);
				break;
		}
		g.setStroke(stroke);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	private static MapIcons.Shown find(List<MapIcons.Shown> shown, MapSpot spot)
	{
		for (MapIcons.Shown icon : shown)
		{
			if (icon.getSpot().equals(spot))
			{
				return icon;
			}
		}
		return null;
	}

	/** The info box beside an icon: its name, then what the player needs to know about it. Never cut short. */
	private static void drawInfo(Graphics2D g, MapViewport.View view, MapIcons.Shown icon, MapSettings settings,
		Progress progress)
	{
		String title = MapIcons.name(icon.getSpot(), progress);
		List<MapIcons.Line> lines = MapIcons.info(icon.getSpot(), progress, settings.isHidden(icon.getSpot()),
			settings.getFloorNumbers());
		java.awt.Font titleFont = FontManager.getRunescapeBoldFont();
		java.awt.Font font = FontManager.getRunescapeSmallFont();
		FontMetrics titleMetrics = g.getFontMetrics(titleFont);
		FontMetrics metrics = g.getFontMetrics(font);
		int width = titleMetrics.stringWidth(title);
		for (MapIcons.Line line : lines)
		{
			width = Math.max(width, metrics.stringWidth(line.getText()));
		}
		width += PAD * 2;
		int height = PAD * 2 + titleMetrics.getHeight() + metrics.getHeight() * lines.size();
		Rectangle at = icon.getBounds();
		int x = at.x + at.width + 8;
		if (x + width > view.width - GAP)
		{
			x = at.x - 8 - width;
		}
		x = Math.max(GAP, x);
		int y = Math.max(GAP + BUTTON_HEIGHT + GAP, Math.min(at.y - PAD, view.height - height - GAP));
		drawPanel(g, new Rectangle(x, y, width, height));
		int baseline = y + PAD + titleMetrics.getAscent();
		g.setFont(titleFont);
		g.setColor(TEXT);
		g.drawString(title, x + PAD, baseline);
		baseline += titleMetrics.getDescent() + metrics.getAscent();
		g.setFont(font);
		for (MapIcons.Line line : lines)
		{
			g.setColor(line.getColor());
			g.drawString(line.getText(), x + PAD, baseline);
			baseline += metrics.getHeight();
		}
	}

	/** The right-click menu's entries: hide or unhide for an icon, and route entries when a routing plugin is chosen. */
	static List<MenuEntry> menuEntries(Selection selection, MapSettings settings)
	{
		List<MenuEntry> entries = new ArrayList<>();
		MapSpot spot = selection.getSpot();
		if (spot != null)
		{
			entries.add(settings.isHidden(spot) ? new MenuEntry(Action.UNHIDE, "Unhide this icon")
				: new MenuEntry(Action.HIDE, "Hide this icon"));
		}
		RoutingPlugin routing = settings.getRouting();
		if (routing != null && routing != RoutingPlugin.NONE)
		{
			entries.add(new MenuEntry(Action.ROUTE, "Route here (" + routing.getLabel() + ")"));
			entries.add(new MenuEntry(Action.CLEAR_ROUTE, "Clear route"));
		}
		return entries;
	}

	/** The menu's name row: the icon's name, or the tile with no icon. */
	static String menuTitle(Selection selection, Progress progress, FloorNumbers floors)
	{
		if (selection.getSpot() != null)
		{
			return MapIcons.name(selection.getSpot(), progress);
		}
		WorldPoint point = selection.getPoint();
		return point.getX() + ", " + point.getY() + (point.getPlane() > 0 ? ", " + floors.name(point.getPlane())
			: "");
	}

	/** Where the right-click menu sits: its name row, then its entries, kept on the screen. */
	static Rectangle menuBounds(Selection selection, String title, List<MenuEntry> entries, int width, int height)
	{
		FontMetrics metrics = MEASURE.getFontMetrics(FontManager.getRunescapeSmallFont());
		int menuWidth = metrics.stringWidth(title);
		for (MenuEntry entry : entries)
		{
			menuWidth = Math.max(menuWidth, metrics.stringWidth(entry.getText()));
		}
		menuWidth += PAD * 2;
		int menuHeight = MENU_ROW * (1 + entries.size());
		int x = Math.max(0, Math.min(selection.getX(), width - menuWidth));
		int y = Math.max(0, Math.min(selection.getY(), height - menuHeight));
		return new Rectangle(x, y, menuWidth, menuHeight);
	}

	/** The row of the menu's entry {@code index} (0 is the first entry, under the name row). */
	static Rectangle menuEntryBounds(Rectangle menu, int index)
	{
		return new Rectangle(menu.x, menu.y + MENU_ROW * (index + 1), menu.width, MENU_ROW);
	}

	private static void drawMenu(Graphics2D g, MapViewport.View view, Selection selection, MapSettings settings,
		Progress progress)
	{
		String title = menuTitle(selection, progress, settings.getFloorNumbers());
		List<MenuEntry> entries = menuEntries(selection, settings);
		Rectangle menu = menuBounds(selection, title, entries, view.width, view.height);
		drawPanel(g, menu);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		int baseline = (MENU_ROW + metrics.getAscent() - metrics.getDescent()) / 2;
		g.setColor(MapIcons.QUIET);
		g.drawString(title, menu.x + PAD, menu.y + baseline);
		for (int i = 0; i < entries.size(); i++)
		{
			Rectangle row = menuEntryBounds(menu, i);
			g.setColor(new Color(255, 255, 255, 30));
			g.fillRect(row.x + 2, row.y, row.width - 4, row.height - 2);
			g.setColor(TEXT);
			g.drawString(entries.get(i).getText(), row.x + PAD, row.y + baseline);
		}
	}

	private static void drawPanel(Graphics2D g, Rectangle box)
	{
		g.setColor(new Color(0x10, 0x18, 0x22, 235));
		g.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);
		g.setColor(new Color(0x5A5040));
		g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 8, 8);
	}

	private static void drawLayer(Graphics2D g, MapViewport.View view, MapLayer layer)
	{
		Stroke stroke = g.getStroke();
		for (MapLayer.Line line : layer.getLines())
		{
			Path2D path = new Path2D.Double();
			boolean first = true;
			for (WorldPoint point : line.getPoints())
			{
				double sx = view.screenX(point.getX() + 0.5);
				double sy = view.screenY(point.getY() + 0.5);
				if (first)
				{
					path.moveTo(sx, sy);
					first = false;
				}
				else
				{
					path.lineTo(sx, sy);
				}
			}
			float width = line.getWidth();
			float[] dash = line.isDashed() ? new float[]{width * 3, width * 2} : null;
			g.setStroke(new BasicStroke(width + 2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, dash, 0));
			g.setColor(OUTLINE);
			g.draw(path);
			g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, dash, 0));
			g.setColor(line.getColor());
			g.draw(path);
		}
		g.setStroke(stroke);

		for (MapLayer.Marker marker : layer.getMarkers())
		{
			double sx = view.screenX(marker.getPoint().getX() + 0.5);
			double sy = view.screenY(marker.getPoint().getY() + 0.5);
			if (sx < -50 || sy < -50 || sx > view.width + 50 || sy > view.height + 50)
			{
				continue;
			}
			drawMarker(g, marker, sx, sy);
		}
	}

	private static void drawMarker(Graphics2D g, MapLayer.Marker marker, double sx, double sy)
	{
		double r = marker.getSize() / 2.0;
		java.awt.Shape shape;
		switch (marker.getShape())
		{
			case SQUARE:
				shape = new Rectangle((int) Math.round(sx - r), (int) Math.round(sy - r), marker.getSize(), marker.getSize());
				break;
			case DIAMOND:
				shape = new Polygon(
					new int[]{(int) Math.round(sx), (int) Math.round(sx + r), (int) Math.round(sx), (int) Math.round(sx - r)},
					new int[]{(int) Math.round(sy - r), (int) Math.round(sy), (int) Math.round(sy + r), (int) Math.round(sy)},
					4);
				break;
			default:
				shape = new Ellipse2D.Double(sx - r, sy - r, marker.getSize(), marker.getSize());
				break;
		}
		g.setColor(marker.getColor());
		g.fill(shape);
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(1.5f));
		g.setColor(OUTLINE);
		g.draw(shape);
		g.setStroke(stroke);
		if (marker.getLabel() != null && !marker.getLabel().isEmpty())
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			int tx = (int) Math.round(sx + r + 4);
			int ty = (int) Math.round(sy + g.getFontMetrics().getAscent() / 2.0 - 1);
			g.setColor(Color.BLACK);
			g.drawString(marker.getLabel(), tx + 1, ty + 1);
			g.setColor(TEXT);
			g.drawString(marker.getLabel(), tx, ty);
		}
	}

	private static void drawPlayer(Graphics2D g, MapViewport.View view, MapArea area, WorldPoint point)
	{
		if (point == null || MapArea.of(point.getY()) != area)
		{
			return;
		}
		double sx = view.screenX(point.getX() + 0.5);
		double sy = view.screenY(point.getY() + 0.5);
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		// the locate button's crosshair, bigger, with a dark edge so it reads on any ground
		drawCrosshair(g, sx, sy, PLAYER_MARKER_SCALE, OUTLINE, PLAYER_STROKE + 2.5f);
		drawCrosshair(g, sx, sy, PLAYER_MARKER_SCALE, LOCATED, PLAYER_STROKE);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	/**
	 * A crosshair: a ring, four ticks pointing out and a dot in the middle, the shape of the locate button. At scale 1
	 * the ring is 16 pixels across and the ticks reach 13 from the middle.
	 */
	static void drawCrosshair(Graphics2D g, double cx, double cy, double scale, Color color, float width)
	{
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(color);
		double ring = 8 * scale;
		double tick = 13 * scale;
		g.draw(new Ellipse2D.Double(cx - ring, cy - ring, 2 * ring, 2 * ring));
		g.draw(new java.awt.geom.Line2D.Double(cx, cy - tick, cx, cy - ring));
		g.draw(new java.awt.geom.Line2D.Double(cx, cy + ring, cx, cy + tick));
		g.draw(new java.awt.geom.Line2D.Double(cx - tick, cy, cx - ring, cy));
		g.draw(new java.awt.geom.Line2D.Double(cx + ring, cy, cx + tick, cy));
		double dot = 3.5 * scale + (width - PLAYER_STROKE) / 2;
		g.fill(new Ellipse2D.Double(cx - dot, cy - dot, 2 * dot, 2 * dot));
		g.setStroke(stroke);
	}

	private static void drawBar(Graphics2D g, MapViewport.View view, Point mouse, boolean loading, boolean window,
		boolean icons)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		StringBuilder text = new StringBuilder();
		if (mouse != null && mouse.x >= 0 && mouse.y >= 0)
		{
			int tx = (int) Math.floor(view.worldX(mouse.x));
			int ty = (int) Math.floor(view.worldY(mouse.y));
			text.append(String.format(Locale.ROOT, "%d, %d", tx, ty)).append("    ");
		}
		text.append(window ? "Drag to move, scroll to zoom" : "Drag to move, scroll to zoom, Esc to close");
		if (icons)
		{
			text.append(", click an icon for details");
		}
		if (loading)
		{
			text.append("    Drawing the map...");
		}
		int barHeight = metrics.getHeight() + 8;
		g.setColor(BAR);
		g.fillRect(0, view.height - barHeight, metrics.stringWidth(text.toString()) + 16, barHeight);
		g.setColor(TEXT);
		g.drawString(text.toString(), 8, view.height - 4 - metrics.getDescent());
	}

	private static void drawButtons(Graphics2D g, MapViewport.View view, MapArea area, int plane, boolean window,
		MapSettings settings, boolean located, boolean filtersOpen)
	{
		for (Map.Entry<Button, Rectangle> entry : buttons(view.width, view.height, window).entrySet())
		{
			Rectangle button = entry.getValue();
			g.setColor(BAR);
			if (entry.getKey() == Button.LOCATE)
			{
				drawLocate(g, button, located);
				continue;
			}
			if (entry.getKey() == Button.FLOOR_UP || entry.getKey() == Button.FLOOR_DOWN)
			{
				boolean up = entry.getKey() == Button.FLOOR_UP;
				drawFloorButton(g, button, up, up ? plane < RegionImages.PLANES - 1 : plane > 0);
				if (up)
				{
					drawFloorName(g, button, plane, settings.getFloorNumbers());
				}
				continue;
			}
			g.fillRoundRect(button.x, button.y, button.width, button.height, 8, 8);
			switch (entry.getKey())
			{
				case CLOSE:
					drawCross(g, button);
					break;
				case AREA:
					drawLabel(g, button, "Show " + area.other().label.toLowerCase(Locale.ROOT));
					break;
				case ICONS:
					drawSwitch(g, button, "Icons", !settings.isIconsOff());
					break;
				case FILTERS:
					drawFiltersButton(g, button, filtersOpen, settings.getGroups().size() < IconGroup.values().length
						|| !settings.getHiddenTypes().isEmpty());
					break;
				default:
					drawLabel(g, button, window ? "Over the game" : "Pop out");
					break;
			}
		}
	}

	/** An arrow button that moves up or down a floor, dimmed when there is no floor that way. */
	private static void drawFloorButton(Graphics2D g, Rectangle button, boolean up, boolean enabled)
	{
		g.setColor(BAR);
		g.fillRoundRect(button.x, button.y, button.width, button.height, 8, 8);
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int cx = button.x + button.width / 2;
		int cy = button.y + button.height / 2;
		int half = 7;
		int tip = up ? cy - 5 : cy + 5;
		int base = up ? cy + 4 : cy - 4;
		g.setColor(enabled ? TEXT : new Color(0x5A5A5A));
		g.fill(new Polygon(new int[]{cx - half, cx + half, cx}, new int[]{base, base, tip}, 3));
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	/** The floor's name between the floor buttons: "Ground floor", "Floor 2" and so on. */
	private static void drawFloorName(Graphics2D g, Rectangle up, int plane, FloorNumbers floors)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		String text = floors.name(plane);
		int width = metrics.stringWidth(text) + 10;
		int x = up.x + up.width - width;
		int y = up.y + up.height + 2;
		g.setColor(BAR);
		g.fillRoundRect(x, y, width, FLOOR_LABEL - 4, 8, 8);
		g.setColor(TEXT);
		g.drawString(text, x + 5, y + (FLOOR_LABEL - 4 + metrics.getAscent() - metrics.getDescent()) / 2);
	}

	/**
	 * The Filters button: a funnel, its name and an arrow; lit while its list is open, with a dot while some icons are
	 * filtered out.
	 */
	private static void drawFiltersButton(Graphics2D g, Rectangle button, boolean open, boolean filtering)
	{
		if (open)
		{
			g.setColor(new Color(255, 255, 255, 40));
			g.fillRoundRect(button.x, button.y, button.width, button.height, 8, 8);
		}
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int x = button.x + PAD;
		int y = button.y + (button.height - 12) / 2;
		g.setColor(TEXT);
		g.fill(new Polygon(new int[]{x, x + 12, x + 7, x + 7, x + 5, x + 5}, new int[]{y, y, y + 6, y + 12, y + 10, y + 6},
			6));
		if (filtering)
		{
			g.setColor(new Color(0xF29A30));
			g.fillOval(x + 9, y - 3, 6, 6);
		}
		int arrowX = button.x + button.width - PAD - 7;
		int arrowY = button.y + button.height / 2 - 2;
		g.setColor(TEXT);
		g.fill(new Polygon(new int[]{arrowX, arrowX + 7, arrowX + 3}, open ? new int[]{arrowY + 4, arrowY + 4, arrowY}
			: new int[]{arrowY, arrowY, arrowY + 4}, 3));
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		g.drawString("Filters", x + 18, button.y + (button.height + metrics.getAscent() - metrics.getDescent()) / 2);
	}

	/** A button that is on or off: a box, ticked when on, before its name. */
	private static void drawSwitch(Graphics2D g, Rectangle button, String text, boolean on)
	{
		int box = 12;
		int bx = button.x + PAD;
		int by = button.y + (button.height - box) / 2;
		g.setColor(on ? new Color(0x3FA34D) : new Color(0x2A333D));
		g.fillRect(bx, by, box, box);
		g.setColor(TEXT);
		g.drawRect(bx, by, box, box);
		if (on)
		{
			Stroke stroke = g.getStroke();
			g.setStroke(new BasicStroke(2f));
			g.drawPolyline(new int[]{bx + 3, bx + 5, bx + 10}, new int[]{by + 6, by + 9, by + 3}, 3);
			g.setStroke(stroke);
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		g.drawString(text, bx + box + 6, button.y + (button.height + metrics.getAscent() - metrics.getDescent()) / 2);
	}

	/** The find-me button: a round button with a location mark, dimmed while there is no player to find. */
	private static void drawLocate(Graphics2D g, Rectangle button, boolean located)
	{
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(BAR);
		g.fillOval(button.x, button.y, button.width, button.height);
		drawCrosshair(g, button.getCenterX(), button.getCenterY(), 1, located ? LOCATED : new Color(0x707070),
			2);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	private static void drawLabel(Graphics2D g, Rectangle button, String text)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		g.setColor(TEXT);
		g.drawString(text, button.x + (button.width - metrics.stringWidth(text)) / 2,
			button.y + (button.height + metrics.getAscent() - metrics.getDescent()) / 2);
	}

	private static void drawCross(Graphics2D g, Rectangle button)
	{
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(TEXT);
		int inset = 9;
		g.drawLine(button.x + inset, button.y + inset, button.x + button.width - inset, button.y + button.height - inset);
		g.drawLine(button.x + button.width - inset, button.y + inset, button.x + inset, button.y + button.height - inset);
		g.setStroke(stroke);
	}
}
