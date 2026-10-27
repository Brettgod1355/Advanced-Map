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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * The list down the map's right side: where a clicked transport goes (a fairy ring's every code, with its fare and
 * what it needs), what a shop sells and for how much, or where a searched monster or item is. A click on a row with
 * a place moves the map there, on its map and floor; the list stays open until closed.
 */
final class SideList
{
	static final int WIDTH = 240;
	static final int ROW = 20;
	/** The height each line under a row's text adds. */
	static final int SUB = 14;
	private static final int PAD = 8;
	private static final int HEADER = 26;
	private static final int CLOSE = 16;

	private static final Color PANEL = new Color(0x10, 0x18, 0x22, 240);
	private static final Color EDGE = new Color(0x5A5040);
	private static final Color TEXT = new Color(0xE8E2D0);
	private static final Color QUIET = new Color(0x8C8676);
	private static final Color HOVER = new Color(255, 255, 255, 28);

	/** One row: its text, a note on its right (a fare, a price, the other map), an optional second line, a place. */
	@Value
	static class Row
	{
		String text;
		String note;
		/** Lines under the text, split at "; " (what a trip needs, each on its own line), or null. */
		String sub;
		Color subColor;
		/** Where a click on the row moves the map, or null for a row that only informs. */
		WorldPoint point;
		/** What a click on the row opens besides (a shop's stock), or null. */
		Object target;

		List<String> subLines()
		{
			return sub == null ? Collections.emptyList() : Arrays.asList(sub.split("; "));
		}

		int height()
		{
			return ROW + SUB * subLines().size();
		}
	}

	/** Where the list and its parts are on screen. */
	@Value
	static class Layout
	{
		Rectangle panel;
		Rectangle close;
		/** The rows, under the title. */
		Rectangle list;
	}

	/** What a press is on: nothing of the list, its close box, a row (by its index), or the list's other parts. */
	@Value
	static class Hit
	{
		static final int OUTSIDE = -3;
		static final int CLOSE = -2;
		static final int PANEL = -1;

		int index;
	}

	private volatile String title = "";
	private volatile List<Row> rows = Collections.emptyList();
	/** What the list is for, so the same kind of click can tell it is already open (a transport, a shop). */
	private volatile Object owner;
	private int scroll;

	boolean isOpen()
	{
		return !rows.isEmpty();
	}

	/** Opens a list of rows, named {@code title}, for {@code owner}. */
	void open(String title, List<Row> rows, Object owner)
	{
		this.title = title;
		this.rows = Collections.unmodifiableList(rows);
		this.owner = owner;
		scroll = 0;
	}

	void close()
	{
		rows = Collections.emptyList();
		owner = null;
	}

	List<Row> rows()
	{
		return rows;
	}

	Object owner()
	{
		return owner;
	}

	/** The rows' height together. */
	int contentHeight()
	{
		int height = 0;
		for (Row row : rows)
		{
			height += row.height();
		}
		return height;
	}

	/**
	 * The list's place: down the right side, under the buttons at the top and above the floor buttons; no taller than
	 * its rows need ({@code content} pixels).
	 */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int content)
	{
		Rectangle area = buttons.get(MapPainter.Button.AREA);
		Rectangle locate = buttons.get(MapPainter.Button.LOCATE);
		// above the Slayer button (whether it is there or not), which sits above the floor buttons
		Rectangle below = buttons.get(MapPainter.Button.SLAYER);
		int top = area.y + area.height + 6;
		int room = Math.max(HEADER + ROW * 3, below.y - 6 - top);
		int height = Math.min(room, HEADER + content + PAD / 2);
		int x = locate.x + locate.width - WIDTH;
		Rectangle panel = new Rectangle(x, top, WIDTH, height);
		Rectangle close = new Rectangle(x + WIDTH - PAD - CLOSE, top + (HEADER - CLOSE) / 2, CLOSE, CLOSE);
		Rectangle list = new Rectangle(x, top + HEADER, WIDTH, height - HEADER - PAD / 2);
		return new Layout(panel, close, list);
	}

	/** The list's layout for its rows now. */
	Layout layout(Map<MapPainter.Button, Rectangle> buttons)
	{
		return layout(buttons, contentHeight());
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
		if (list.contains(x, y))
		{
			int top = list.y - scroll;
			List<Row> shown = rows;
			for (int i = 0; i < shown.size(); i++)
			{
				int bottom = top + shown.get(i).height();
				if (y >= top && y < bottom)
				{
					return new Hit(i);
				}
				top = bottom;
			}
		}
		return new Hit(Hit.PANEL);
	}

	/** Scrolls the rows by mouse wheel notches. */
	void scroll(double notches, Layout layout)
	{
		int max = Math.max(0, contentHeight() - layout.getList().height);
		scroll = (int) Math.max(0, Math.min(max, scroll + Math.round(notches * ROW * 3)));
	}

	/** Draws the list; the row under {@code mouse} (null when the pointer is elsewhere) is lit when it has a place. */
	void draw(Graphics2D g, Layout layout, Point mouse)
	{
		List<Row> shown = rows;
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
		for (Row row : shown)
		{
			int height = row.height();
			if (y + height >= list.y && y <= list.y + list.height)
			{
				if (row.getPoint() != null && mouse != null && list.contains(mouse) && mouse.y >= y
					&& mouse.y < y + height)
				{
					g.setColor(HOVER);
					g.fillRect(list.x + 2, y, list.width - 4, height);
				}
				int baseline = y + (ROW + metrics.getAscent() - metrics.getDescent()) / 2;
				String note = row.getNote() == null ? "" : row.getNote();
				int noteWidth = note.isEmpty() ? 0 : metrics.stringWidth(note) + PAD;
				g.setColor(TEXT);
				g.drawString(fit(row.getText(), metrics, list.width - PAD * 2 - noteWidth), list.x + PAD, baseline);
				if (!note.isEmpty())
				{
					g.setColor(QUIET);
					g.drawString(note, list.x + list.width - PAD - metrics.stringWidth(note), baseline);
				}
				g.setColor(row.getSubColor() == null ? QUIET : row.getSubColor());
				int line = baseline;
				for (String sub : row.subLines())
				{
					line += SUB;
					g.drawString(fit(sub, metrics, list.width - PAD * 3), list.x + PAD * 2, line);
				}
			}
			y += height;
		}
		g.setClip(clip);

		int content = contentHeight();
		if (content > list.height)
		{
			int thumb = Math.max(20, list.height * list.height / content);
			int top = list.y + (list.height - thumb) * scroll / Math.max(1, content - list.height);
			g.setColor(new Color(255, 255, 255, 60));
			g.fillRoundRect(list.x + list.width - 5, top, 3, thumb, 3, 3);
		}
	}

	/** Text cut to a width, with "..." when it had to be cut. */
	static String fit(String text, FontMetrics metrics, int width)
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
