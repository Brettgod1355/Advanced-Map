/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * The list down the map's right side of where a clicked transport goes (a fairy ring's every code, a spirit tree's
 * every tree): a click on a place moves the map there, on its map and floor. Open while the transport's lines show.
 */
final class TransportList
{
	static final int WIDTH = 220;
	static final int ROW = 20;
	private static final int PAD = 8;
	private static final int HEADER = 26;
	private static final int CLOSE = 16;

	private static final Color PANEL = new Color(0x10, 0x18, 0x22, 240);
	private static final Color EDGE = new Color(0x5A5040);
	private static final Color TEXT = new Color(0xE8E2D0);
	private static final Color QUIET = new Color(0x8C8676);
	private static final Color HOVER = new Color(255, 255, 255, 28);

	/** Where the list and its parts are on screen. */
	@Value
	static class Layout
	{
		Rectangle panel;
		Rectangle close;
		/** The rows, under the title. */
		Rectangle list;
	}

	/** What a press is on: nothing of the list, its close box, a place (by its index), or the list's other parts. */
	@Value
	static class Hit
	{
		static final int OUTSIDE = -3;
		static final int CLOSE = -2;
		static final int PANEL = -1;

		int index;
	}

	private volatile String title = "";
	private volatile WorldPoint from;
	private volatile List<Transports.Destination> places = Collections.emptyList();
	private int scroll;

	boolean isOpen()
	{
		return !places.isEmpty();
	}

	/** Opens the list of a transport's places, named {@code title}, from the transport at {@code from}. */
	void open(String title, WorldPoint from, List<Transports.Destination> places)
	{
		this.title = title;
		this.from = from;
		this.places = Collections.unmodifiableList(places);
		scroll = 0;
	}

	void close()
	{
		places = Collections.emptyList();
	}

	List<Transports.Destination> places()
	{
		return places;
	}

	/**
	 * The list's place: down the right side, under the buttons at the top and above the floor buttons; no taller than
	 * its rows need.
	 */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int rows)
	{
		Rectangle area = buttons.get(MapPainter.Button.AREA);
		Rectangle locate = buttons.get(MapPainter.Button.LOCATE);
		Rectangle floorUp = buttons.get(MapPainter.Button.FLOOR_UP);
		int top = area.y + area.height + 6;
		int room = Math.max(HEADER + ROW * 3, floorUp.y - 6 - top);
		int height = Math.min(room, HEADER + rows * ROW + PAD / 2);
		int x = locate.x + locate.width - WIDTH;
		Rectangle panel = new Rectangle(x, top, WIDTH, height);
		Rectangle close = new Rectangle(x + WIDTH - PAD - CLOSE, top + (HEADER - CLOSE) / 2, CLOSE, CLOSE);
		Rectangle list = new Rectangle(x, top + HEADER, WIDTH, height - HEADER - PAD / 2);
		return new Layout(panel, close, list);
	}

	/** What a press at a point is on. */
	Hit hitAt(int x, int y, Layout layout)
	{
		if (!layout.getPanel().contains(x, y))
		{
			return new Hit(Hit.OUTSIDE);
		}
		if (layout.getClose().contains(x, y))
		{
			return new Hit(Hit.CLOSE);
		}
		Rectangle list = layout.getList();
		int index = list.contains(x, y) ? (y - list.y + scroll) / ROW : -1;
		return new Hit(index >= 0 && index < places.size() ? index : Hit.PANEL);
	}

	/** Scrolls the rows by mouse wheel notches. */
	void scroll(double notches, Layout layout)
	{
		int max = Math.max(0, places.size() * ROW - layout.getList().height);
		scroll = (int) Math.max(0, Math.min(max, scroll + Math.round(notches * ROW * 3)));
	}

	/** Draws the list; the row under {@code mouse} (null when the pointer is elsewhere) is lit. */
	void draw(Graphics2D g, Layout layout, Point mouse)
	{
		List<Transports.Destination> shown = places;
		WorldPoint start = from;
		Rectangle panel = layout.getPanel();
		g.setColor(PANEL);
		g.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 8, 8);
		g.setColor(EDGE);
		g.drawRoundRect(panel.x, panel.y, panel.width - 1, panel.height - 1, 8, 8);

		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bold = g.getFontMetrics();
		Rectangle close = layout.getClose();
		String heading = fit(title, bold, close.x - panel.x - PAD * 2);
		g.setColor(TEXT);
		g.drawString(heading, panel.x + PAD, panel.y + (HEADER + bold.getAscent() - bold.getDescent()) / 2);
		g.setColor(QUIET);
		g.drawLine(close.x + 4, close.y + 4, close.x + close.width - 5, close.y + close.height - 5);
		g.drawLine(close.x + close.width - 5, close.y + 4, close.x + 4, close.y + close.height - 5);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		Rectangle list = layout.getList();
		Shape clip = g.getClip();
		g.clip(list);
		int y = list.y - scroll;
		for (Transports.Destination place : shown)
		{
			if (y + ROW >= list.y && y <= list.y + list.height)
			{
				if (mouse != null && list.contains(mouse) && mouse.y >= y && mouse.y < y + ROW)
				{
					g.setColor(HOVER);
					g.fillRect(list.x + 2, y, list.width - 4, ROW);
				}
				int baseline = y + (ROW + metrics.getAscent() - metrics.getDescent()) / 2;
				String note = start != null && MapArea.of(place.getPoint().getY()) != MapArea.of(start.getY())
					? MapArea.of(place.getPoint().getY()).label : "";
				int noteWidth = note.isEmpty() ? 0 : metrics.stringWidth(note) + PAD;
				g.setColor(TEXT);
				g.drawString(fit(place.label(), metrics, list.width - PAD * 2 - noteWidth), list.x + PAD, baseline);
				if (!note.isEmpty())
				{
					g.setColor(QUIET);
					g.drawString(note, list.x + list.width - PAD - metrics.stringWidth(note), baseline);
				}
			}
			y += ROW;
		}
		g.setClip(clip);

		int content = shown.size() * ROW;
		if (content > list.height)
		{
			int thumb = Math.max(20, list.height * list.height / content);
			int top = list.y + (list.height - thumb) * scroll / Math.max(1, content - list.height);
			g.setColor(new Color(255, 255, 255, 60));
			g.fillRoundRect(list.x + list.width - 5, top, 3, thumb, 3, 3);
		}
	}

	/** Text cut to a width, with "..." when it had to be cut. */
	private static String fit(String text, FontMetrics metrics, int width)
	{
		if (metrics.stringWidth(text) <= width)
		{
			return text;
		}
		String cut = text;
		while (!cut.isEmpty() && metrics.stringWidth(cut + "...") > width)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut + "...";
	}
}
