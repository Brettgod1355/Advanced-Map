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
import java.awt.Stroke;
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
 * Draws the map: region pictures, other plugins' layers, the player, a bar with the tile under the mouse, and the
 * buttons. The same drawing goes over the game view and into the pop-out window. It touches no client state, so it
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
	 * pointer is (null when it is elsewhere).
	 */
	void paint(Graphics2D g, MapViewport.View view, MapArea area, WorldPoint player, Point mouse, boolean window)
	{
		g.setColor(BACKGROUND);
		g.fillRect(0, 0, view.width, view.height);
		boolean loading = drawRegions(g, view, area);

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
