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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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
	/** For measuring text outside a frame, to wrap a row's lines before the list is drawn. */
	private static final Graphics2D MEASURE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
	/** The step between two lines of a wrapped title or heading, in the bold font (taller than {@link #SUB}). */
	static final int BOLD_LINE = MEASURE.getFontMetrics(FontManager.getRunescapeBoldFont()).getHeight();

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

		/** The lines under the text, each wrapped to the list's width. */
		List<String> subLines()
		{
			if (sub == null)
			{
				return Collections.emptyList();
			}
			List<String> lines = new ArrayList<>();
			for (String part : sub.split("; "))
			{
				lines.addAll(wrap(part, WIDTH - PAD * 3));
			}
			return lines;
		}

		/** The text, wrapped whole-word to what the row has beside its note; never cut short. */
		List<String> textLines()
		{
			if (isHeading())
			{
				return wrap(text, WIDTH - PAD * 2, FontManager.getRunescapeBoldFont());
			}
			int noteWidth = note == null || note.isEmpty() ? 0
				: MEASURE.getFontMetrics(FontManager.getRunescapeSmallFont()).stringWidth(note) + PAD;
			return wrap(text, WIDTH - PAD * 2 - noteWidth);
		}

		int height()
		{
			return height(textLines(), subLines());
		}

		/** The height of a row laid out as these lines. */
		int height(List<String> text, List<String> sub)
		{
			return (isHeading() ? HEADING_GAP : 0) + ROW + SUB * (text.size() - 1) + SUB * sub.size();
		}

		/** A heading over the rows after it ("Locations (nearest first)"): bold and underlined; a click does nothing. */
		static Row heading(String text)
		{
			return new Row(text, null, null, null, null, HEADING);
		}

		boolean isHeading()
		{
			return target == HEADING;
		}
	}

	/** What a heading row holds as its target: nothing to open. */
	private static final Object HEADING = new Object();
	/** Space above a heading, apart from the rows before it. */
	private static final int HEADING_GAP = 8;
	private static final Color HEADING_LINE = new Color(0xC8A050);

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

	/** A row's lines, wrapped when the list opened (they are the same every paint), and its height. */
	@Value
	static class Laid
	{
		List<String> text;
		List<String> sub;
		int height;
	}

	/** What is open: the rows and their lines, the title and its lines, whose it is; one object, so a paint sees one. */
	@Value
	private static class Content
	{
		String title;
		List<String> titleLines;
		List<Row> rows;
		List<Laid> laid;
		int height;
		Object owner;
	}

	private static final Content NOTHING = new Content("", Collections.singletonList(""), Collections.emptyList(),
		Collections.emptyList(), 0, null);

	private volatile Content content = NOTHING;
	/** Scrolled by the wheel on the Swing thread, read when the list is drawn on the client thread. */
	private volatile int scroll;

	boolean isOpen()
	{
		return !content.rows.isEmpty();
	}

	/** Opens a list of rows, named {@code title}, for {@code owner}: the rows' text is wrapped here, once. */
	void open(String title, List<Row> rows, Object owner)
	{
		List<Laid> laid = new ArrayList<>(rows.size());
		int height = 0;
		for (Row row : rows)
		{
			List<String> text = row.textLines();
			List<String> sub = row.subLines();
			Laid lines = new Laid(text, sub, row.height(text, sub));
			laid.add(lines);
			height += lines.height;
		}
		List<String> titleLines = wrap(title, WIDTH - PAD * 3 - CLOSE, FontManager.getRunescapeBoldFont());
		content = new Content(title, titleLines.isEmpty() ? Collections.singletonList("") : titleLines,
			Collections.unmodifiableList(new ArrayList<>(rows)), Collections.unmodifiableList(laid), height, owner);
		scroll = 0;
	}

	void close()
	{
		content = NOTHING;
	}

	List<Row> rows()
	{
		return content.rows;
	}

	/** What the list is for, so the same kind of click can tell it is already open (a transport, a shop). */
	Object owner()
	{
		return content.owner;
	}

	/** The rows' height together. */
	int contentHeight()
	{
		return content.height;
	}

	/**
	 * The list's place: down the right side, under the buttons at the top and above the floor buttons; no taller than
	 * its rows need ({@code content} pixels), with a one-line title.
	 */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int content)
	{
		return layout(buttons, content, HEADER);
	}

	/** As above, with a title {@code header} pixels tall. */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int content, int header)
	{
		Rectangle area = buttons.get(MapPainter.Button.AREA);
		Rectangle locate = buttons.get(MapPainter.Button.LOCATE);
		Rectangle below = buttons.get(MapPainter.Button.FLOOR_UP);
		int top = area.y + area.height + 6;
		int room = Math.max(header + ROW * 3, below.y - 6 - top);
		int height = Math.min(room, header + content + PAD / 2);
		int x = locate.x + locate.width - WIDTH;
		Rectangle panel = new Rectangle(x, top, WIDTH, height);
		Rectangle close = new Rectangle(x + WIDTH - PAD - CLOSE, top + (HEADER - CLOSE) / 2, CLOSE, CLOSE);
		Rectangle list = new Rectangle(x, top + header, WIDTH, height - header - PAD / 2);
		return new Layout(panel, close, list);
	}

	/** The list's layout for its rows and title now. */
	Layout layout(Map<MapPainter.Button, Rectangle> buttons)
	{
		return layout(buttons, contentHeight(), headerHeight());
	}

	/** The title in lines no wider than the room beside the close box: a long name wraps, it is never cut short. */
	List<String> titleLines()
	{
		return content.titleLines;
	}

	/** The title's height: one line fits the usual header; each line more adds a line's height. */
	int headerHeight()
	{
		return HEADER + (content.titleLines.size() - 1) * BOLD_LINE;
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
			List<Laid> shown = content.laid;
			for (int i = 0; i < shown.size(); i++)
			{
				int bottom = top + shown.get(i).height;
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

	/**
	 * Draws the list; the row under {@code mouse} (null when the pointer is elsewhere) is lit when a click on it does
	 * something: it has a place, or something to open.
	 */
	void draw(Graphics2D g, Layout layout, Point mouse)
	{
		Content shown = content;
		Rectangle panel = layout.getPanel();
		g.setColor(PANEL);
		g.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 8, 8);
		g.setColor(EDGE);
		g.drawRoundRect(panel.x, panel.y, panel.width - 1, panel.height - 1, 8, 8);

		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bold = g.getFontMetrics();
		Rectangle close = layout.getClose();
		g.setColor(TEXT);
		int titleY = panel.y + (HEADER + bold.getAscent() - bold.getDescent()) / 2;
		for (String line : shown.titleLines)
		{
			g.drawString(line, panel.x + PAD, titleY);
			titleY += BOLD_LINE;
		}
		g.setColor(QUIET);
		g.drawLine(close.x + 4, close.y + 4, close.x + close.width - 5, close.y + close.height - 5);
		g.drawLine(close.x + close.width - 5, close.y + 4, close.x + 4, close.y + close.height - 5);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		Rectangle list = layout.getList();
		Shape clip = g.getClip();
		g.clip(list);
		int y = list.y - scroll;
		for (int i = 0; i < shown.rows.size(); i++)
		{
			Row row = shown.rows.get(i);
			Laid lines = shown.laid.get(i);
			int height = lines.height;
			if (y + height >= list.y && y <= list.y + list.height)
			{
				if (row.isHeading())
				{
					drawHeading(g, lines, list, y + HEADING_GAP);
					y += height;
					continue;
				}
				if ((row.getPoint() != null || row.getTarget() != null) && mouse != null && list.contains(mouse) && mouse.y >= y
					&& mouse.y < y + height)
				{
					g.setColor(HOVER);
					g.fillRect(list.x + 2, y, list.width - 4, height);
				}
				int baseline = y + (ROW + metrics.getAscent() - metrics.getDescent()) / 2;
				String note = row.getNote() == null ? "" : row.getNote();
				g.setColor(TEXT);
				int line = baseline;
				for (String text : lines.text)
				{
					g.drawString(text, list.x + PAD, line);
					line += SUB;
				}
				line -= SUB;
				if (!note.isEmpty())
				{
					g.setColor(QUIET);
					g.drawString(note, list.x + list.width - PAD - metrics.stringWidth(note), baseline);
				}
				g.setColor(row.getSubColor() == null ? QUIET : row.getSubColor());
				for (String sub : lines.sub)
				{
					line += SUB;
					g.drawString(sub, list.x + PAD * 2, line);
				}
			}
			y += height;
		}
		g.setClip(clip);

		int all = shown.height;
		if (all > list.height)
		{
			int thumb = Math.max(20, list.height * list.height / all);
			int top = list.y + (list.height - thumb) * scroll / Math.max(1, all - list.height);
			g.setColor(new Color(255, 255, 255, 60));
			g.fillRoundRect(list.x + list.width - 5, top, 3, thumb, 3, 3);
		}
	}

	/** A heading row: its text in bold, underlined across the list. */
	private static void drawHeading(Graphics2D g, Laid lines, Rectangle list, int top)
	{
		java.awt.Font font = g.getFont();
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bold = g.getFontMetrics();
		int baseline = top + (ROW + bold.getAscent() - bold.getDescent()) / 2;
		g.setColor(TEXT);
		for (String line : lines.text)
		{
			g.drawString(line, list.x + PAD, baseline);
			baseline += SUB;
		}
		baseline -= SUB;
		g.setColor(HEADING_LINE);
		g.fillRect(list.x + PAD, baseline + 3, list.width - PAD * 2, 1);
		g.setFont(font);
	}

	/** Text in lines no wider than {@code width} in the list's font, broken between words only. */
	static List<String> wrap(String text, int width)
	{
		return wrap(text, width, FontManager.getRunescapeSmallFont());
	}

	/** Text in lines no wider than {@code width} in a font, broken between words only (a lone long word stays). */
	static List<String> wrap(String text, int width, java.awt.Font font)
	{
		FontMetrics metrics = MEASURE.getFontMetrics(font);
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			if (line.length() > 0 && metrics.stringWidth(line + " " + word) > width)
			{
				lines.add(line.toString());
				line.setLength(0);
			}
			line.append(line.length() > 0 ? " " : "").append(word);
		}
		if (line.length() > 0)
		{
			lines.add(line.toString());
		}
		return lines;
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
