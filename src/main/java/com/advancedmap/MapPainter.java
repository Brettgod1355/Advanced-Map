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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
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
		/** Over the game only: the map's box fills the game view, or goes back to its size. */
		MAXIMIZE,
		AREA,
		MODE,
		/** Shows or hides every icon at once; place names stay. */
		ICONS,
		/** Opens the list that turns each group and kind of icon on or off. */
		FILTERS,
		/** Moves the map back to the player. */
		LOCATE,
		FLOOR_UP,
		FLOOR_DOWN,
		/** Shows or hides the Slayer task's spawns; there only while the "Slayer task" setting is on. */
		SLAYER
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
		CLEAR_ROUTE,
		ADD_MARKER,
		EDIT_MARKER,
		REMOVE_MARKER
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
	/** The names beside transport icons. */
	private static final Color STOP_NAME = Transports.LINE;
	static final int FLOOR_WIDTH = LOCATE_SIZE;
	static final int FLOOR_HEIGHT = 24;
	/** Room for the floor's name between its two buttons. */
	private static final int FLOOR_LABEL = 22;
	private static final int GAP = 8;
	private static final int AREA_WIDTH = 110;
	private static final int MODE_WIDTH = 100;
	private static final int ICONS_WIDTH = 76;
	private static final int FILTERS_WIDTH = 86;
	private static final int MENU_ROW = 20;
	private static final int PAD = 8;
	/** The most monsters a dungeon preview lists, its "and more" line included, when taller than its map. */
	private static final int MAX_PREVIEW_LINES = 20;
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

	/** How long the mouse must rest on an icon before its info box shows, without a click. */
	static final long HOVER_NANOS = 300_000_000L;

	/** For measuring text outside a frame (where the menu's entry is, for a click). */
	private static final Graphics2D MEASURE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();

	private final RegionImages images;
	private final MapMessages messages;
	private final MapIcons icons;
	private final MapLabels labels;
	private final MapFilters filters;
	private final SideList sideList;
	private final MapSearch search;
	private volatile MapPins pins = new MapPins();
	private volatile MarkerEditor editor = new MarkerEditor();
	private volatile boolean multiCombat;
	/** Each farming site's patches as Time Tracking last saved them; null while farming status is off. */
	private volatile Map<Farming.Site, List<Farming.Reading>> farming;
	/** The pin a right-click menu is for, or null. */
	private volatile MapPins.Pin menuPin;
	private volatile boolean infoOnHover;
	/** An arrow at the edge points to the player when they are off the view. */
	private volatile boolean playerArrow;
	/** The map's box over the game fills the game view (the maximize button shows "restore"). */
	private volatile boolean gameMaximized;
	/** The map keeps itself on the player (the locate button lit). */
	private volatile boolean following;
	/** A dungeon preview lists its monsters the highest level first; else the lowest. */
	private volatile boolean highestFirst = true;
	/** Set while a mouse button is held on the map (dragging it): nothing shows on hover meanwhile. */
	private volatile boolean dragging;
	/** Where a click on an icon would move the map to the other map (a dungeon's entrance), or null. */
	private volatile Function<MapSpot, WorldPoint> links = spot -> null;
	private final DungeonPreview preview;
	/** The icon or pin the mouse rests on (a {@link MapSpot} or a {@link MapPins.Pin}), and since when. */
	private Object hovered;
	private long hoveredSince;

	MapPainter(RegionImages images, MapMessages messages, MapIcons icons, MapLabels labels, MapFilters filters,
		SideList sideList, MapSearch search)
	{
		this.search = search;
		this.images = images;
		this.messages = messages;
		this.icons = icons;
		this.labels = labels;
		this.filters = filters;
		this.sideList = sideList;
		this.preview = new DungeonPreview(images);
	}

	/** Shows an icon's info box when the mouse rests on it, as well as on a click. */
	void setInfoOnHover(boolean on)
	{
		infoOnHover = on;
	}

	void setPlayerArrow(boolean on)
	{
		playerArrow = on;
	}

	boolean isPlayerArrow()
	{
		return playerArrow;
	}

	void setGameMaximized(boolean on)
	{
		gameMaximized = on;
	}

	void setFollowing(boolean on)
	{
		following = on;
	}

	void setHighestFirst(boolean on)
	{
		highestFirst = on;
	}

	/** The game's map links, for the ways from one underground place to another a preview follows. */
	void setMapLinks(Map<Integer, Integer> links)
	{
		preview.setMapLinks(links);
	}

	void setDragging(boolean on)
	{
		dragging = on;
	}

	/** The parts of the world each of the game's underground maps shows, which keep a preview to one dungeon. */
	void setDungeonMaps(List<List<com.advancedmap.cache.WorldMapElements.Piece>> maps)
	{
		preview.setMaps(maps);
	}

	void setLinks(Function<MapSpot, WorldPoint> links)
	{
		this.links = links;
	}

	void setPins(MapPins pins, MarkerEditor editor)
	{
		this.pins = pins;
		this.editor = editor;
	}

	void setMultiCombat(boolean on)
	{
		multiCombat = on;
	}

	void setFarming(Map<Farming.Site, List<Farming.Reading>> readings)
	{
		farming = readings;
	}

	void setMenuPin(MapPins.Pin pin)
	{
		menuPin = pin;
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
		out.put(Button.SLAYER, new Rectangle(width - GAP - FLOOR_WIDTH, down - FLOOR_LABEL - FLOOR_HEIGHT - GAP - FLOOR_WIDTH,
			FLOOR_WIDTH, FLOOR_WIDTH));
		int right = width - GAP;
		if (!window)
		{
			out.put(Button.CLOSE, new Rectangle(right - BUTTON_HEIGHT, GAP, BUTTON_HEIGHT, BUTTON_HEIGHT));
			right -= BUTTON_HEIGHT + GAP;
			out.put(Button.MAXIMIZE, new Rectangle(right - BUTTON_HEIGHT, GAP, BUTTON_HEIGHT, BUTTON_HEIGHT));
			right -= BUTTON_HEIGHT + GAP;
		}
		out.put(Button.AREA, new Rectangle(right - AREA_WIDTH, GAP, AREA_WIDTH, BUTTON_HEIGHT));
		right -= AREA_WIDTH + GAP;
		out.put(Button.MODE, new Rectangle(right - MODE_WIDTH, GAP, MODE_WIDTH, BUTTON_HEIGHT));
		return out;
	}

	/** The button under a point, or null. */
	static Button buttonAt(int x, int y, int width, int height, boolean window, MapSettings settings)
	{
		for (Map.Entry<Button, Rectangle> button : buttons(width, height, window).entrySet())
		{
			// the Slayer button is not there while the setting is off: the map under it takes the press
			if (button.getKey() == Button.SLAYER && (settings == null || !settings.isSlayerButton()))
			{
				continue;
			}
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
		boolean loading = drawRegions(g, view, area, plane, true);
		if (settings.isWildernessLines())
		{
			drawWilderness(g, view, area, id -> images.isAbsent(0, id));
		}
		if (multiCombat)
		{
			MultiCombat.draw(g, view);
		}
		if (settings.isPlaceNames())
		{
			drawLabels(g, view, area, labels.all(), buttons(view.width, view.height, window).values());
		}
		List<MapIcons.Shown> shown = icons.visible(view, area, plane, settings, progress);
		drawIcons(g, shown, selection);
		Map<Farming.Site, List<Farming.Reading>> farms = farming;
		if (farms != null)
		{
			drawFarming(g, shown, farms);
		}
		if (settings.isTransportNames() && view.zoom >= MapLabels.PLACE_ZOOM)
		{
			drawStopNames(g, shown);
		}

		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (MapLayer layer : messages.layers())
		{
			drawLayer(g, view, layer);
		}
		List<MapPins.Shown> pinsShown = pins.visible(view, area, plane, settings.getIconScale());
		MapPins.draw(g, pinsShown, view.zoom);
		drawPlayer(g, view, area, player);
		drawPlayerArrow(g, view, area, player, playerArrow);
		MapPins.Pin pin = pins.selected();
		if (pin != null)
		{
			for (MapPins.Shown each : pinsShown)
			{
				if (each.getPin().equals(pin))
				{
					drawBox(g, view, each.getBounds(), pin.getName().isEmpty() ? "Marker" : pin.getName(),
						pinLines(pin, settings.getFloorNumbers()));
				}
			}
		}
		if (selection != null)
		{
			MapIcons.Shown selected = find(shown, selection.getSpot());
			if (selection.isMenu())
			{
				drawMenu(g, view, selection, settings, progress, menuPin);
			}
			else if (selected != null)
			{
				drawInfo(g, view, selected, settings, progress, farmingLines(selected.getSpot(), farms));
			}
		}
		drawBar(g, view, mouse, loading, window, !shown.isEmpty(), infoOnHover);
		Integer slayerArea = settings.isSlayerButton() ? filters.areaOf(IconGroup.SLAYER_MASTER) : null;
		drawButtons(g, view, area, plane, window, settings, player != null, following, gameMaximized, filters.isOpen(),
			slayerArea == null ? null : icons.image(slayerArea, false));
		if (filters.isOpen())
		{
			Rectangle button = buttons(view.width, view.height, window).get(Button.FILTERS);
			filters.draw(g, MapFilters.layout(button, view.height), filters.entries(progress), settings, icons);
		}
		if (editor.isOpen())
		{
			editor.draw(g, MarkerEditor.layout(buttons(view.width, view.height, window)), mouse);
		}
		else if (sideList.isOpen())
		{
			sideList.draw(g, sideList.layout(buttons(view.width, view.height, window)), mouse);
		}
		search.draw(g, search.layout(buttons(view.width, view.height, window), view.width), mouse);
		drawHovered(g, view, area, window, mouse, settings, progress, selection, shown, pinsShown, farms);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	/**
	 * Draws the region pictures there are and, with {@code request}, asks for the rest (a dungeon preview asks for its
	 * own). On a floor above the ground, the floors below are drawn first and darkened, and the floor itself in full
	 * color on top, so it shows where it sits. True while some of the floor are still being drawn.
	 */
	private boolean drawRegions(Graphics2D g, MapViewport.View view, MapArea area, int plane, boolean request)
	{
		List<Integer> visible = regionsOn(view, area);
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
		if (request)
		{
			images.request(plane, visible, level);
		}

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
		int westColumn = area == MapArea.SURFACE ? counted.x >> 6 : Wilderness.UNDERGROUND_LINES_FROM_COLUMN;
		int firstColumn = Math.max(westColumn, (int) Math.floor(view.worldX(0) / 64));
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

	/**
	 * Draws the name of each network stop under its icon: a fairy ring's code, a spirit tree's or balloon's place. A
	 * name that would cover one already drawn is left out.
	 */
	static void drawStopNames(Graphics2D g, List<MapIcons.Shown> shown)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		List<Rectangle> taken = new ArrayList<>();
		for (MapIcons.Shown icon : shown)
		{
			String name = icon.getLook() == MapIcons.Look.HIDDEN ? null : Transports.stopName(icon.getSpot());
			if (name == null)
			{
				continue;
			}
			Rectangle at = icon.getBounds();
			int width = metrics.stringWidth(name);
			Rectangle box = new Rectangle(at.x + at.width / 2 - width / 2 - 1, at.y + at.height, width + 2,
				metrics.getHeight());
			if (overlaps(box, taken))
			{
				continue;
			}
			taken.add(box);
			drawShadowed(g, name, box.x + 1, box.y + metrics.getAscent(), STOP_NAME);
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

	/**
	 * The info box of the icon or pin the mouse has rested on for {@link #HOVER_NANOS}, unless a click already opened
	 * it, a menu is open, a mouse button is held, or the mouse is on a button or panel; for a dungeon's entrance, the
	 * dungeon's preview. It goes when the mouse moves off; a click keeps it (as the selection).
	 */
	private void drawHovered(Graphics2D g, MapViewport.View view, MapArea area, boolean window, Point mouse,
		MapSettings settings, Progress progress, Selection selection, List<MapIcons.Shown> shown,
		List<MapPins.Shown> pinsShown, Map<Farming.Site, List<Farming.Reading>> farms)
	{
		Object target = null;
		Rectangle at = null;
		if (infoOnHover && mouse != null && !dragging && (selection == null || !selection.isMenu())
			&& !onPanel(mouse, view, window, settings))
		{
			for (int i = pinsShown.size() - 1; i >= 0 && target == null; i--)
			{
				if (pinsShown.get(i).getBounds().contains(mouse))
				{
					target = pinsShown.get(i).getPin();
					at = pinsShown.get(i).getBounds();
				}
			}
			MapIcons.Shown icon = target == null ? MapIcons.at(shown, mouse.x, mouse.y) : null;
			if (icon != null)
			{
				target = icon.getSpot();
				at = icon.getBounds();
			}
		}
		long now = System.nanoTime();
		if (target == null || !target.equals(hovered))
		{
			hovered = target;
			hoveredSince = now;
			return;
		}
		if (now - hoveredSince < HOVER_NANOS)
		{
			return;
		}
		if (target instanceof MapPins.Pin)
		{
			MapPins.Pin pin = (MapPins.Pin) target;
			if (!pin.equals(pins.selected()))
			{
				drawBox(g, view, at, pin.getName().isEmpty() ? "Marker" : pin.getName(),
					pinLines(pin, settings.getFloorNumbers()));
			}
			return;
		}
		MapSpot spot = (MapSpot) target;
		if (selection != null && spot.equals(selection.getSpot()))
		{
			return;
		}
		WorldPoint to = links.apply(spot);
		if (to != null)
		{
			drawPreview(g, view, at, spot, to, settings, progress);
			return;
		}
		for (MapIcons.Shown icon : shown)
		{
			if (icon.getSpot().equals(spot))
			{
				drawInfo(g, view, icon, settings, progress, farmingLines(spot, farms));
			}
		}
	}

	/** True when a point is on one of the map's buttons, its open lists or boxes, or the search bar. */
	private boolean onPanel(Point mouse, MapViewport.View view, boolean window, MapSettings settings)
	{
		if (buttonAt(mouse.x, mouse.y, view.width, view.height, window, settings) != null)
		{
			return true;
		}
		Map<Button, Rectangle> buttons = buttons(view.width, view.height, window);
		if (filters.isOpen() && MapFilters.layout(buttons.get(Button.FILTERS), view.height).getPanel().contains(mouse))
		{
			return true;
		}
		if (editor.isOpen() ? MarkerEditor.layout(buttons).getPanel().contains(mouse)
			: sideList.isOpen() && sideList.layout(buttons).getPanel().contains(mouse))
		{
			return true;
		}
		MapSearch.Layout bar = search.layout(buttons, view.width);
		return bar.getBox().contains(mouse) || bar.getResults().contains(mouse);
	}

	/**
	 * A dungeon's preview beside its entrance: its name, a small map around where the entrance leads with that tile
	 * marked, and (for a way underground) the monsters inside; how to go in under it.
	 */
	private void drawPreview(Graphics2D g, MapViewport.View view, Rectangle at, MapSpot spot, WorldPoint to,
		MapSettings settings, Progress progress)
	{
		MapArea toArea = MapArea.of(to.getY());
		String name = DungeonPreview.name(spot, to, search.places(), labels.all());
		String title = name != null ? name : MapIcons.name(spot, progress);
		if (toArea == MapArea.SURFACE && MapArea.of(spot.getY()) != MapArea.SURFACE)
		{
			title = name != null ? "Up to " + name : "Way out";
		}
		boolean inside = toArea == MapArea.UNDERGROUND;
		DungeonPreview.Look look = inside ? preview.look(to) : null;
		MapViewport.View small = DungeonPreview.framing(to, look == null ? null : look.getFirstFloor());
		images.requestBesides(to.getPlane(), regionsOn(small, toArea), RegionImages.Level.forZoom(small.zoom),
			inside ? DungeonPreview.regionsAround(look.getFloors()) : Collections.emptyList());

		java.awt.Font titleFont = FontManager.getRunescapeBoldFont();
		java.awt.Font font = FontManager.getRunescapeSmallFont();
		FontMetrics titleMetrics = g.getFontMetrics(titleFont);
		FontMetrics metrics = g.getFontMetrics(font);
		List<String> list = new ArrayList<>();
		if (inside)
		{
			int room = Math.max(DungeonPreview.MAP_HEIGHT, MAX_PREVIEW_LINES * metrics.getHeight())
				/ metrics.getHeight() - 1;
			if (!look.isComplete() && !look.getMonsters().isEmpty())
			{
				// floors still being drawn may hold more; the line goes once they are looked through
				room--;
			}
			List<DungeonPreview.Inside> monsters = DungeonPreview.sorted(look.getMonsters(), highestFirst);
			for (int i = 0; i < monsters.size() && list.size() < room; i++)
			{
				if (i == room - 1 && monsters.size() > room)
				{
					list.add("and " + (monsters.size() - i) + " more");
					break;
				}
				list.add(monsters.get(i).text());
			}
			if (list.isEmpty())
			{
				list.add(look.isComplete() ? "No monsters" : "Looking...");
			}
			else if (!look.isComplete())
			{
				list.add("Still looking...");
			}
		}
		String heading = "Monsters inside";
		int listWidth = 0;
		if (inside)
		{
			listWidth = titleMetrics.stringWidth(heading);
			for (String line : list)
			{
				listWidth = Math.max(listWidth, metrics.stringWidth(line));
			}
		}
		String hint = "Click to go there";
		int contentHeight = Math.max(DungeonPreview.MAP_HEIGHT,
			inside ? titleMetrics.getHeight() + metrics.getHeight() * list.size() : 0);
		int width = PAD * 2 + Math.max(titleMetrics.stringWidth(title),
			DungeonPreview.MAP_WIDTH + (inside ? PAD + listWidth : 0));
		int height = PAD * 2 + titleMetrics.getHeight() + 4 + contentHeight + 4 + metrics.getHeight();
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
		int mapX = x + PAD;
		int mapY = y + PAD + titleMetrics.getHeight() + 4;

		Graphics2D map = (Graphics2D) g.create(mapX, mapY, DungeonPreview.MAP_WIDTH, DungeonPreview.MAP_HEIGHT);
		try
		{
			map.setColor(BACKGROUND);
			map.fillRect(0, 0, small.width, small.height);
			drawRegions(map, small, toArea, to.getPlane(), false);
			drawIcons(map, icons.visible(small, toArea, to.getPlane(), settings, progress), null);
			double cx = small.screenX(to.getX() + 0.5);
			double cy = small.screenY(to.getY() + 0.5);
			map.setColor(OUTLINE);
			map.fill(new Ellipse2D.Double(cx - 6, cy - 6, 12, 12));
			map.setColor(PLAYER);
			map.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
		}
		finally
		{
			map.dispose();
		}
		g.setColor(new Color(0x5A5040));
		g.drawRect(mapX - 1, mapY - 1, DungeonPreview.MAP_WIDTH + 1, DungeonPreview.MAP_HEIGHT + 1);

		if (inside)
		{
			int listX = mapX + DungeonPreview.MAP_WIDTH + PAD;
			int line = mapY + titleMetrics.getAscent();
			g.setFont(titleFont);
			g.setColor(TEXT);
			g.drawString(heading, listX, line);
			line += titleMetrics.getDescent() + metrics.getAscent();
			g.setFont(font);
			g.setColor(MapIcons.PLAIN);
			for (String text : list)
			{
				g.drawString(text, listX, line);
				line += metrics.getHeight();
			}
		}
		g.setFont(font);
		g.setColor(MapIcons.QUIET);
		g.drawString(hint, x + PAD, y + height - PAD - metrics.getDescent());
	}

	/** The ids of a map's regions a view shows. */
	private static List<Integer> regionsOn(MapViewport.View view, MapArea area)
	{
		List<Integer> ids = new ArrayList<>();
		int rx0 = Math.max(area.minRegionX, (int) Math.floor(view.worldX(0) / 64));
		int rx1 = Math.min(area.maxRegionX, (int) Math.floor(view.worldX(view.width) / 64));
		int ry0 = Math.max(area.minRegionY, (int) Math.floor(view.worldY(view.height) / 64));
		int ry1 = Math.min(area.maxRegionY, (int) Math.floor(view.worldY(0) / 64));
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				ids.add(rx << 8 | ry);
			}
		}
		return ids;
	}

	/** The info box beside an icon: its name, then what the player needs to know about it. Never cut short. */
	private static void drawInfo(Graphics2D g, MapViewport.View view, MapIcons.Shown icon, MapSettings settings,
		Progress progress, List<MapIcons.Line> extra)
	{
		List<MapIcons.Line> lines = new ArrayList<>(MapIcons.info(icon.getSpot(), progress,
			settings.isHidden(icon.getSpot()), settings.getFloorNumbers()));
		lines.addAll(extra);
		drawBox(g, view, icon.getBounds(), MapIcons.name(icon.getSpot(), progress), lines);
	}

	/** An info box beside {@code at}: a title and lines, kept on the screen. */
	private static void drawBox(Graphics2D g, MapViewport.View view, Rectangle at, String title,
		List<MapIcons.Line> lines)
	{
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

	/** A dot on each farming patch icon with its site's state: ready, diseased or dead, growing, empty. */
	private static void drawFarming(Graphics2D g, List<MapIcons.Shown> shown, Map<Farming.Site, List<Farming.Reading>> farms)
	{
		for (MapIcons.Shown icon : shown)
		{
			MapSpot spot = icon.getSpot();
			if (spot.getGroup() != IconGroup.FARMING || icon.getLook() == MapIcons.Look.HIDDEN)
			{
				continue;
			}
			Farming.Site site = Farming.siteNear(spot.getX(), spot.getY());
			Farming.Status status = site == null ? null : Farming.summary(farms.getOrDefault(site,
				Collections.emptyList()));
			if (status == null)
			{
				continue;
			}
			Rectangle at = icon.getBounds();
			int size = Math.max(7, at.width / 3);
			int x = at.x + at.width - size + 2;
			int y = at.y - 2;
			g.setColor(OUTLINE);
			g.fillOval(x - 1, y - 1, size + 2, size + 2);
			g.setColor(status.color);
			g.fillOval(x, y, size, size);
		}
	}

	/** A farming icon's info box lines: each patch of its site, what grows there and when it is ready. */
	static List<MapIcons.Line> farmingLines(MapSpot spot, Map<Farming.Site, List<Farming.Reading>> farms)
	{
		List<MapIcons.Line> lines = new ArrayList<>();
		if (farms == null || spot.getGroup() != IconGroup.FARMING)
		{
			return lines;
		}
		Farming.Site site = Farming.siteNear(spot.getX(), spot.getY());
		List<Farming.Reading> readings = site == null ? null : farms.get(site);
		if (readings == null || readings.isEmpty())
		{
			if (site != null)
			{
				lines.add(new MapIcons.Line("Not known yet: visit it with Time Tracking on", MapIcons.QUIET));
			}
			return lines;
		}
		long now = System.currentTimeMillis() / 1000;
		for (Farming.Reading reading : readings)
		{
			Farming.Patch patch = reading.getPatch();
			String where = patch.getName().isEmpty() ? kindName(patch.getKind()) : patch.getName() + " "
				+ kindName(patch.getKind()).toLowerCase(Locale.ROOT);
			String what = "Weeds".equals(reading.getProduce()) ? "empty" : reading.getProduce();
			String state = reading.getStatus() == Farming.Status.GROWING
				? "ready in " + Farming.timeLeft(reading.getDone() - now)
				: reading.getStatus().label.toLowerCase(Locale.ROOT);
			lines.add(new MapIcons.Line(where + ": " + what + (reading.getStatus() == Farming.Status.EMPTY ? ""
				: ", " + state), reading.getStatus().color));
		}
		return lines;
	}

	/** "HARDWOOD_TREE" as "Hardwood tree". */
	static String kindName(String kind)
	{
		String words = kind.toLowerCase(Locale.ROOT).replace('_', ' ');
		return words.isEmpty() ? words : Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	/** A pin's info box lines: what another plugin says of it, or that it is the player's marker, and its tile. */
	static List<MapIcons.Line> pinLines(MapPins.Pin pin, FloorNumbers floors)
	{
		List<MapIcons.Line> lines = new ArrayList<>();
		if (pin.getDetail() != null && !pin.getDetail().isEmpty() && !pin.getDetail().equals(pin.getName()))
		{
			lines.add(new MapIcons.Line(pin.getDetail(), MapIcons.PLAIN));
		}
		WorldPoint at = Prifddinas.toCity(pin.getPoint());
		lines.add(new MapIcons.Line(at.getX() + ", " + at.getY() + (at.getPlane() > 0 ? ", " + floors.name(at.getPlane())
			: ""), MapIcons.QUIET));
		if (pin.getMarker() != null)
		{
			lines.add(new MapIcons.Line("Right-click to edit or remove", MapIcons.QUIET));
		}
		return lines;
	}

	/**
	 * The right-click menu's entries: hide or unhide for an icon, route entries when a routing plugin is chosen, and
	 * Add marker here.
	 */
	static List<MenuEntry> menuEntries(Selection selection, MapSettings settings)
	{
		return menuEntries(selection, settings, null);
	}

	/** As above, for a menu opened on a pin when {@code pin} is set: a marker's Edit and Remove. */
	static List<MenuEntry> menuEntries(Selection selection, MapSettings settings, MapPins.Pin pin)
	{
		List<MenuEntry> entries = new ArrayList<>();
		MapSpot spot = selection.getSpot();
		if (pin != null && pin.getMarker() != null)
		{
			entries.add(new MenuEntry(Action.EDIT_MARKER, "Edit marker"));
			entries.add(new MenuEntry(Action.REMOVE_MARKER, "Remove marker"));
		}
		else if (spot != null)
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
		if (pin == null)
		{
			entries.add(new MenuEntry(Action.ADD_MARKER, "Add marker here"));
		}
		return entries;
	}

	/** The menu's name row: the icon's name, or the tile with no icon. */
	static String menuTitle(Selection selection, Progress progress, FloorNumbers floors, MapPins.Pin pin)
	{
		if (pin != null)
		{
			return pin.getName().isEmpty() ? "Marker" : pin.getName();
		}
		return menuTitle(selection, progress, floors);
	}

	static String menuTitle(Selection selection, Progress progress, FloorNumbers floors)
	{
		if (selection.getSpot() != null)
		{
			return MapIcons.name(selection.getSpot(), progress);
		}
		// the game's own tile, in Prifddinas the city's
		WorldPoint point = Prifddinas.toCity(selection.getPoint());
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
		Progress progress, MapPins.Pin pin)
	{
		String title = menuTitle(selection, progress, settings.getFloorNumbers(), pin);
		List<MenuEntry> entries = menuEntries(selection, settings, pin);
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
			for (WorldPoint at : line.getPoints())
			{
				// another plugin's tiles in Prifddinas, on the surface where the map draws the city
				WorldPoint point = Prifddinas.toSurface(at);
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
			WorldPoint point = Prifddinas.toSurface(marker.getPoint());
			double sx = view.screenX(point.getX() + 0.5);
			double sy = view.screenY(point.getY() + 0.5);
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

	/** How far in from the view's edges the arrow to the player stays: clear of the buttons along the top, the bar. */
	private static final int ARROW_SIDE = 26;
	private static final int ARROW_TOP = GAP + BUTTON_HEIGHT + 22;
	private static final int ARROW_BOTTOM = 40;
	private static final int ARROW_SIZE = 13;

	/**
	 * Where the arrow to the player is drawn (a box around its tip, which a click takes to them), or null: not on,
	 * the player not known, on the other map, or in view.
	 */
	static Rectangle playerArrow(MapViewport.View view, MapArea area, WorldPoint player, boolean on)
	{
		if (!on || player == null || MapArea.of(player.getY()) != area)
		{
			return null;
		}
		double px = view.screenX(player.getX() + 0.5);
		double py = view.screenY(player.getY() + 0.5);
		double left = ARROW_SIDE;
		double right = view.width - ARROW_SIDE;
		double top = ARROW_TOP;
		double bottom = view.height - ARROW_BOTTOM;
		if (px >= 0 && py >= 0 && px <= view.width && py <= view.height || right <= left || bottom <= top)
		{
			return null;
		}
		// from the middle of the view toward the player, to where that line leaves the inner box
		double cx = (left + right) / 2;
		double cy = (top + bottom) / 2;
		double dx = px - cx;
		double dy = py - cy;
		double t = Math.min(dx == 0 ? Double.MAX_VALUE : (dx > 0 ? right - cx : left - cx) / dx,
			dy == 0 ? Double.MAX_VALUE : (dy > 0 ? bottom - cy : top - cy) / dy);
		int x = (int) Math.round(cx + dx * t);
		int y = (int) Math.round(cy + dy * t);
		return new Rectangle(x - ARROW_SIZE - 2, y - ARROW_SIZE - 2, ARROW_SIZE * 2 + 4, ARROW_SIZE * 2 + 4);
	}

	/** The arrow at the edge pointing to the player when they are off the view, with how many tiles away. */
	private static void drawPlayerArrow(Graphics2D g, MapViewport.View view, MapArea area, WorldPoint player,
		boolean on)
	{
		Rectangle at = playerArrow(view, area, player, on);
		if (at == null)
		{
			return;
		}
		double tipX = at.getCenterX();
		double tipY = at.getCenterY();
		double angle = Math.atan2(view.screenY(player.getY() + 0.5) - tipY, view.screenX(player.getX() + 0.5) - tipX);
		Path2D arrow = new Path2D.Double();
		arrow.moveTo(ARROW_SIZE, 0);
		arrow.lineTo(-ARROW_SIZE * 0.7, ARROW_SIZE * 0.75);
		arrow.lineTo(-ARROW_SIZE * 0.35, 0);
		arrow.lineTo(-ARROW_SIZE * 0.7, -ARROW_SIZE * 0.75);
		arrow.closePath();
		Shape shape = java.awt.geom.AffineTransform.getRotateInstance(angle, 0, 0).createTransformedShape(arrow);
		shape = java.awt.geom.AffineTransform.getTranslateInstance(tipX, tipY).createTransformedShape(shape);
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(OUTLINE);
		g.draw(shape);
		g.setStroke(stroke);
		g.setColor(LOCATED);
		g.fill(shape);
		int tiles = (int) Math.max(Math.abs(player.getX() - view.centerX), Math.abs(player.getY() - view.centerY));
		String text = tiles + " tiles";
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		// the distance on the arrow's inner side, toward the middle of the view
		int tx = (int) Math.round(tipX - Math.cos(angle) * (ARROW_SIZE + 12) - metrics.stringWidth(text) / 2.0);
		int ty = (int) Math.round(tipY - Math.sin(angle) * (ARROW_SIZE + 10) + metrics.getAscent() / 2.0);
		drawShadowed(g, text, tx, ty, TEXT);
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
		boolean icons, boolean hover)
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
			text.append(hover ? ", point at an icon for details, click to keep them" : ", click an icon for details");
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
		MapSettings settings, boolean located, boolean following, boolean maximized, boolean filtersOpen,
		BufferedImage slayerIcon)
	{
		for (Map.Entry<Button, Rectangle> entry : buttons(view.width, view.height, window).entrySet())
		{
			Rectangle button = entry.getValue();
			g.setColor(BAR);
			if (entry.getKey() == Button.LOCATE)
			{
				drawLocate(g, button, located, following);
				continue;
			}
			if (entry.getKey() == Button.SLAYER)
			{
				if (settings.isSlayerButton())
				{
					drawSlayerButton(g, button, slayerIcon, settings.isSlayerShown());
				}
				continue;
			}
			if (entry.getKey() == Button.FLOOR_UP || entry.getKey() == Button.FLOOR_DOWN)
			{
				boolean up = entry.getKey() == Button.FLOOR_UP;
				// down from the ground leads underground
				boolean enabled = up ? plane < RegionImages.PLANES - 1 : plane > 0 || area == MapArea.SURFACE;
				drawFloorButton(g, button, up, enabled);
				if (up)
				{
					drawFloorName(g, button, area, plane, settings.getFloorNumbers());
				}
				continue;
			}
			g.fillRoundRect(button.x, button.y, button.width, button.height, 8, 8);
			switch (entry.getKey())
			{
				case CLOSE:
					drawCross(g, button);
					break;
				case MAXIMIZE:
					drawMaximize(g, button, maximized);
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

	/** The Slayer button: the game's Slayer master icon, lit while the task's spawns show, faded while hidden. */
	private static void drawSlayerButton(Graphics2D g, Rectangle button, BufferedImage icon, boolean shown)
	{
		g.setColor(shown ? new Color(0x4A3A1C) : BAR);
		g.fillRoundRect(button.x, button.y, button.width, button.height, 8, 8);
		if (shown)
		{
			g.setColor(new Color(0xC8A050));
			g.drawRoundRect(button.x, button.y, button.width - 1, button.height - 1, 8, 8);
		}
		java.awt.Composite composite = g.getComposite();
		if (!shown)
		{
			g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
		}
		if (icon != null)
		{
			g.drawImage(icon, button.x + (button.width - icon.getWidth()) / 2, button.y + (button.height - icon.getHeight()) / 2,
				null);
		}
		else
		{
			drawLabel(g, button, "S");
		}
		g.setComposite(composite);
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

	/**
	 * The floor's short name between the floor buttons: "G", "2" and so on; on the underground map "U", then "U2" for a
	 * floor above a dungeon's lowest.
	 */
	private static void drawFloorName(Graphics2D g, Rectangle up, MapArea area, int plane, FloorNumbers floors)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		String text = area == MapArea.UNDERGROUND ? plane == 0 ? "U" : "U" + floors.shortName(plane) : floors.shortName(plane);
		int width = Math.max(FLOOR_LABEL - 4, metrics.stringWidth(text) + 10);
		int x = up.x + (up.width - width) / 2;
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
	/** The button that finds the player: lit (a blue disc, a white crosshair) while the map follows them. */
	private static void drawLocate(Graphics2D g, Rectangle button, boolean located, boolean following)
	{
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(following && located ? LOCATED : BAR);
		g.fillOval(button.x, button.y, button.width, button.height);
		drawCrosshair(g, button.getCenterX(), button.getCenterY(), 1, following && located ? PLAYER
			: located ? LOCATED : new Color(0x707070), 2);
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

	/** The maximize button's square, or, while maximized, two overlapping squares to go back, as windows show. */
	private static void drawMaximize(Graphics2D g, Rectangle button, boolean maximized)
	{
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(2f));
		g.setColor(TEXT);
		int inset = 8;
		int size = button.width - inset * 2;
		if (maximized)
		{
			int small = size - 4;
			g.drawRect(button.x + inset + 4, button.y + inset, small, small);
			g.setColor(BAR);
			g.fillRect(button.x + inset, button.y + inset + 4, small, small);
			g.setColor(TEXT);
			g.drawRect(button.x + inset, button.y + inset + 4, small, small);
		}
		else
		{
			g.drawRect(button.x + inset, button.y + inset, size, size);
		}
		g.setStroke(stroke);
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
