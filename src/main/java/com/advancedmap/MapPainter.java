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
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * Draws the map: region pictures, the Wilderness levels, other plugins' layers, the player, a bar with the tile under
 * the mouse, and the buttons. The same drawing goes over the game view and into the pop-out window. It touches no client state, so it
 * runs on the client thread for the overlay and on the Swing thread for the window.
 */
final class MapPainter
{
	enum Button
	{
		CLOSE,
		AREA,
		MODE
	}

	static final int BUTTON_HEIGHT = 28;
	private static final int GAP = 8;
	private static final int AREA_WIDTH = 110;
	private static final int MODE_WIDTH = 100;

	private static final Color BACKGROUND = new Color(0x0B1520);
	private static final Color LOADING = new Color(0x13202D);
	private static final Color BAR = new Color(0, 0, 0, 170);
	private static final Color TEXT = new Color(0xE8E2D0);
	private static final Color PLAYER = new Color(0xFFFFFF);
	private static final Color OUTLINE = new Color(0, 0, 0, 200);
	private static final Color WILDERNESS_EDGE = new Color(0xE0, 0x30, 0x20, 230);
	private static final Color WILDERNESS_LINE = new Color(0xE0, 0x30, 0x20, 190);
	private static final Color WILDERNESS_TEXT = new Color(0xFF, 0xA0, 0x90);
	private static final Color TELEPORT_LINE = new Color(0xFF, 0xB0, 0x20, 230);
	private static final Stroke THIN = new BasicStroke(1);
	private static final Stroke THICK = new BasicStroke(2);

	private final RegionImages images;
	private final MapMessages messages;

	MapPainter(RegionImages images, MapMessages messages)
	{
		this.images = images;
		this.messages = messages;
	}

	/**
	 * Where the buttons sit, right to left along the top: close (over the game only; a window has its own), the map
	 * switch, and the button that moves the map between the game view and its own window.
	 */
	static Map<Button, Rectangle> buttons(int width, boolean window)
	{
		Map<Button, Rectangle> out = new EnumMap<>(Button.class);
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
	static Button buttonAt(int x, int y, int width, boolean window)
	{
		for (Map.Entry<Button, Rectangle> button : buttons(width, window).entrySet())
		{
			if (button.getValue().contains(x, y))
			{
				return button.getKey();
			}
		}
		return null;
	}

	/**
	 * Draws one frame. {@code player} is where the player stands (null when logged out), {@code mouse} where the
	 * pointer is (null when it is elsewhere), {@code wilderness} whether to draw the Wilderness levels.
	 */
	void paint(Graphics2D g, MapViewport.View view, MapArea area, WorldPoint player, Point mouse, boolean window,
		boolean wilderness)
	{
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, view.width, view.height);
		boolean loading = drawRegions(g, view, area);
		if (wilderness)
		{
			drawWilderness(g, view, area);
		}

		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		for (MapLayer layer : messages.layers())
		{
			drawLayer(g, view, layer);
		}
		drawPlayer(g, view, area, player);
		drawBar(g, view, mouse, loading, window);
		drawButtons(g, view.width, area, window);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	/** Draws the region pictures there are and asks for the rest. True while some are still being drawn. */
	private boolean drawRegions(Graphics2D g, MapViewport.View view, MapArea area)
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
		images.request(visible, level);

		Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, view.zoom > 4
			? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
			: RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		boolean loading = false;
		for (int id : visible)
		{
			int baseX = (id >> 8) << 6;
			int baseY = (id & 0xFF) << 6;
			int x0 = (int) Math.floor(view.screenX(baseX));
			int x1 = (int) Math.floor(view.screenX(baseX + 64));
			int y0 = (int) Math.floor(view.screenY(baseY + 64));
			int y1 = (int) Math.floor(view.screenY(baseY));
			BufferedImage image = images.get(id, level);
			if (image != null)
			{
				g.drawImage(image, x0, y0, x1 - x0, y1 - y0, null);
			}
			else if (!images.isAbsent(id))
			{
				loading = true;
				g.setColor(LOADING);
				g.fillRect(x0, y0, x1 - x0, y1 - y0);
			}
		}
		if (interpolation != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		}
		return loading;
	}

	/**
	 * Draws the Wilderness levels as the game counts them ({@link Wilderness}): the counted area's outline, a line
	 * where each level starts (thinned out when zoomed out) with "Level N" at its west end, stronger lines where the
	 * level 20 and 30 teleport limits end, the level 5 patch, and the dungeon regions with a level of their own.
	 */
	static void drawWilderness(Graphics2D g, MapViewport.View view, MapArea area)
	{
		Shape clip = g.getClip();
		Stroke stroke = g.getStroke();
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();

		Rectangle counted = Wilderness.counted(area);
		Area own = new Area();
		for (int[] region : Wilderness.OWN_LEVEL)
		{
			own.add(new Area(screenRect(view, new Rectangle(region[0] << 6, region[1] << 6, 64, 64))));
		}
		Area lines = new Area(screenRect(view, counted));
		lines.subtract(own);
		g.clip(lines);

		double west = view.screenX(counted.x);
		double east = view.screenX(counted.x + counted.width);
		int step = Wilderness.lineStep(view.zoom);
		boolean labels = 8 * step * view.zoom >= metrics.getAscent() + 4;
		for (int level = 2; level <= Wilderness.topLevel(counted); level++)
		{
			if (!Wilderness.drawsLine(level, step))
			{
				continue;
			}
			int y = (int) Math.round(view.screenY(Wilderness.levelStartY(counted, level)));
			g.setStroke(THIN);
			g.setColor(WILDERNESS_LINE);
			g.drawLine((int) west, y, (int) east, y);
			// a teleport limit line just above would run through the text; its own label names the level
			boolean crossed = Wilderness.isTeleportLimit(level) && 8 * view.zoom < metrics.getAscent() + 4;
			if (labels && !crossed)
			{
				drawLineLabel(g, "Level " + level, view, west, east, y, true, WILDERNESS_TEXT, clip, own);
			}
		}
		for (int level = 1; level < Wilderness.topLevel(counted); level++)
		{
			if (!Wilderness.isTeleportLimit(level))
			{
				continue;
			}
			int y = (int) Math.round(view.screenY(Wilderness.levelStartY(counted, level + 1)));
			g.setStroke(THICK);
			g.setColor(TELEPORT_LINE);
			g.drawLine((int) west, y, (int) east, y);
			drawLineLabel(g, "Level " + level + " teleport limit", view, west, east, y, false, TELEPORT_LINE, clip,
				own);
		}
		g.setClip(clip);

		g.setStroke(THICK);
		g.setColor(WILDERNESS_EDGE);
		g.draw(screenRect(view, counted));
		if (area == MapArea.SURFACE)
		{
			Area five = new Area(screenRect(view, Wilderness.FLAT_FIVE));
			five.subtract(new Area(screenRect(view, Wilderness.FLAT_FIVE_HOLE)));
			g.draw(five);
			// in the lower half, clear of the hole in the middle
			drawCenteredLabel(g, "Level 5", view.screenX(Wilderness.FLAT_FIVE.getCenterX()),
				view.screenY(Wilderness.FLAT_FIVE.y + Wilderness.FLAT_FIVE.height / 4.0));
		}
		for (int[] region : Wilderness.OWN_LEVEL)
		{
			if (MapArea.of(region[1] << 6) != area)
			{
				continue;
			}
			Rectangle box = screenRect(view, new Rectangle(region[0] << 6, region[1] << 6, 64, 64));
			if (!box.intersects(0, 0, view.width, view.height))
			{
				continue;
			}
			g.setColor(WILDERNESS_EDGE);
			g.draw(box);
			if (box.width >= metrics.stringWidth("Level 00-00") + 4)
			{
				drawCenteredLabel(g, Wilderness.ownLevelLabel(region[0], region[1]), box.getCenterX(), box.getCenterY());
			}
		}
		g.setStroke(stroke);
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
	 * Text just above a line at screen row {@code y}, at its west or east end, kept on the screen while any of the
	 * line is. It is drawn whole, within the frame's {@code clip}, or not at all where it would cover {@code hidden}.
	 */
	private static void drawLineLabel(Graphics2D g, String text, MapViewport.View view, double west, double east,
		int y, boolean atWest, Color color, Shape clip, Area hidden)
	{
		FontMetrics metrics = g.getFontMetrics();
		int width = metrics.stringWidth(text);
		double x = atWest ? Math.max(west, 0) + 4 : Math.min(east, view.width) - width - 4;
		x = Math.max(west + 4, Math.min(east - width - 4, x));
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

	private static void drawCenteredLabel(Graphics2D g, String text, double cx, double cy)
	{
		FontMetrics metrics = g.getFontMetrics();
		drawShadowed(g, text, (int) Math.round(cx - metrics.stringWidth(text) / 2.0),
			(int) Math.round(cy + (metrics.getAscent() - metrics.getDescent()) / 2.0), WILDERNESS_TEXT);
	}

	private static void drawShadowed(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
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
		double r = 5;
		g.setColor(OUTLINE);
		g.fill(new Ellipse2D.Double(sx - r - 1.5, sy - r - 1.5, 2 * r + 3, 2 * r + 3));
		g.setColor(PLAYER);
		g.fill(new Ellipse2D.Double(sx - r, sy - r, 2 * r, 2 * r));
	}

	private static void drawBar(Graphics2D g, MapViewport.View view, Point mouse, boolean loading, boolean window)
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

	private static void drawButtons(Graphics2D g, int width, MapArea area, boolean window)
	{
		for (Map.Entry<Button, Rectangle> entry : buttons(width, window).entrySet())
		{
			Rectangle button = entry.getValue();
			g.setColor(BAR);
			g.fillRoundRect(button.x, button.y, button.width, button.height, 8, 8);
			switch (entry.getKey())
			{
				case CLOSE:
					drawCross(g, button);
					break;
				case AREA:
					drawLabel(g, button, "Show " + area.other().label.toLowerCase(Locale.ROOT));
					break;
				default:
					drawLabel(g, button, window ? "Over the game" : "Pop out");
					break;
			}
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
