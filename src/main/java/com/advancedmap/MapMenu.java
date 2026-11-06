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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Value;
import net.runelite.client.ui.FontManager;

/**
 * The map's menu, behind the three lines at the top left: a list down the left side, and a page over the rest of the
 * map for the entry chosen (the help, what's new, where to write, building the map again); Settings opens RuneLite's
 * own settings page instead. Drawn by the painter over the game and in the window alike.
 */
final class MapMenu
{
	enum Page
	{
		HELP("How the map works"),
		SETTINGS("Settings"),
		WHATS_NEW("What's new"),
		IMPROVE("Help improve the map"),
		REDRAW("Build the map again");

		final String label;

		Page(String label)
		{
			this.label = label;
		}
	}

	enum Part
	{
		OUTSIDE,
		/** On the menu's panel, off any entry. */
		MENU,
		ENTRY,
		GITHUB,
		DISCORD,
		/** On the page, off its buttons. */
		CONTENT,
		/** The page's first button: the release notes, Discord, or "Delete and build again". */
		BUTTON1,
		/** The page's second button: GitHub issues, or Cancel. */
		BUTTON2
	}

	@Value
	static class Layout
	{
		Rectangle menu;
		Rectangle github;
		Rectangle discord;
		/** The page beside the menu; null with no page open. */
		Rectangle content;
		Rectangle button1;
		Rectangle button2;
	}

	@Value
	static class Hit
	{
		Part part;
		/** For {@link Part#ENTRY}, the entry. */
		Page page;
	}

	static final int WIDTH = 200;
	static final int ROW = 26;
	private static final int PAD = 8;
	private static final int GAP = 8;
	private static final int BUTTON = 24;
	/** The narrowest a page gets; on a map too narrow for it beside the menu, it is drawn over the menu. */
	private static final int LEAST_CONTENT = 160;
	/** The widest a page's text gets: a column that reads well on a wide map, rather than lines across the whole page. */
	private static final int WIDEST_TEXT = 640;
	/** A page's title row, above its text. */
	private static final int TITLE = PAD + ROW + 4;
	/** Room beside the text for the scroll bar. */
	private static final int SCROLL_ROOM = 6;
	/** The widest a page gets: its text column and the padding around it, no wider. */
	private static final int WIDEST_PAGE = WIDEST_TEXT + PAD * 2 + SCROLL_ROOM;
	/** How tall a line of a page's text is. */
	private static final int LINE = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
		.createGraphics().getFontMetrics(FontManager.getRunescapeSmallFont()).getHeight();
	private static final Color LIT = new Color(255, 255, 255, 34);

	/** What the sidebar tab says of the saved map, for the "Build the map again" page. */
	private final Supplier<String> savedStatus;
	private volatile boolean open;
	private volatile Page page;
	private volatile int scroll;
	/** How tall the open page's text was last drawn, for scrolling. */
	private volatile int contentHeight;

	MapMenu(Supplier<String> savedStatus)
	{
		this.savedStatus = savedStatus;
	}

	boolean isOpen()
	{
		return open;
	}

	/** Opens or closes the menu; closing closes its page too. */
	void setOpen(boolean open)
	{
		this.open = open;
		if (!open)
		{
			page = null;
		}
	}

	/** The page open beside the menu, or null. */
	Page page()
	{
		return page;
	}

	void setPage(Page page)
	{
		this.page = page;
		scroll = 0;
	}

	/** The menu and the open page, the page as wide and as tall as its text needs. */
	Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int height)
	{
		Page open = page;
		if (open == null || open == Page.SETTINGS)
		{
			return layout(buttons, width, height, open);
		}
		int textWidth = textWidth(pageWidth(buttons, width));
		return layout(buttons, width, height, open, lines(open, textWidth).size() * LINE);
	}

	/** The menu under its button, and a page beside it down to the map's bottom edge. */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int height, Page page)
	{
		return layout(buttons, width, height, page, -1);
	}

	/** How wide a page is beside the menu on a map {@code width} wide. */
	private static int pageWidth(Map<MapPainter.Button, Rectangle> buttons, int width)
	{
		int x = buttons.get(MapPainter.Button.MENU).x + WIDTH + GAP;
		return Math.max(LEAST_CONTENT, Math.min(WIDEST_PAGE, width - x - GAP));
	}

	private static int textWidth(int pageWidth)
	{
		return Math.min(WIDEST_TEXT, pageWidth - PAD * 2 - SCROLL_ROOM);
	}

	/**
	 * The menu under its button, as tall as its entries, and a page beside it as tall as its text, {@code textHeight}
	 * pixels, needs with its title and buttons, no lower than the map's bottom edge (-1: down to that edge).
	 */
	private static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int height, Page page,
		int textHeight)
	{
		Rectangle button = buttons.get(MapPainter.Button.MENU);
		int y = button.y + button.height + 4;
		// the list as tall as its entries and the links under them; a page may run down to the map's bottom edge
		Rectangle menu = new Rectangle(button.x, y, WIDTH, ROW * Page.values().length + BUTTON + PAD * 3);
		int room = Math.max(menu.height, height - y - PAD);
		int half = (WIDTH - PAD * 3) / 2;
		int linksY = menu.y + menu.height - PAD - BUTTON;
		Rectangle github = new Rectangle(menu.x + PAD, linksY, half, BUTTON);
		Rectangle discord = new Rectangle(menu.x + PAD * 2 + half, linksY, half, BUTTON);
		Rectangle content = null;
		Rectangle button1 = null;
		Rectangle button2 = null;
		if (page != null && page != Page.SETTINGS)
		{
			int x = menu.x + menu.width + GAP;
			int contentWidth = pageWidth(buttons, width);
			boolean buttoned = page != Page.HELP;
			int contentHeight = textHeight < 0 ? room : Math.min(room, TITLE + textHeight
				+ (buttoned ? PAD * 2 + BUTTON : PAD));
			content = new Rectangle(Math.max(GAP, Math.min(x, width - GAP - contentWidth)), y, contentWidth,
				contentHeight);
			int buttonsY = content.y + content.height - PAD - BUTTON;
			int wide = Math.min(220, content.width - PAD * 2);
			int narrow = (content.width - PAD * 3) / 2;
			switch (page)
			{
				case WHATS_NEW:
					button1 = new Rectangle(content.x + PAD, buttonsY, wide, BUTTON);
					break;
				case IMPROVE:
				case REDRAW:
					button1 = new Rectangle(content.x + PAD, buttonsY, narrow, BUTTON);
					button2 = new Rectangle(content.x + PAD * 2 + narrow, buttonsY, narrow, BUTTON);
					break;
				default:
					break;
			}
		}
		return new Layout(menu, github, discord, content, button1, button2);
	}

	Hit hitAt(int x, int y, Layout layout)
	{
		Rectangle content = layout.getContent();
		if (content != null && content.contains(x, y))
		{
			if (layout.getButton1() != null && layout.getButton1().contains(x, y))
			{
				return new Hit(Part.BUTTON1, page);
			}
			if (layout.getButton2() != null && layout.getButton2().contains(x, y))
			{
				return new Hit(Part.BUTTON2, page);
			}
			return new Hit(Part.CONTENT, page);
		}
		Rectangle menu = layout.getMenu();
		if (!menu.contains(x, y))
		{
			return new Hit(Part.OUTSIDE, null);
		}
		if (layout.getGithub().contains(x, y))
		{
			return new Hit(Part.GITHUB, null);
		}
		if (layout.getDiscord().contains(x, y))
		{
			return new Hit(Part.DISCORD, null);
		}
		int row = (y - menu.y - PAD) / ROW;
		if (y >= menu.y + PAD && row < Page.values().length)
		{
			return new Hit(Part.ENTRY, Page.values()[row]);
		}
		return new Hit(Part.MENU, null);
	}

	/** The mouse wheel over the page scrolls its text. */
	void scroll(double notches, Layout layout)
	{
		Rectangle content = layout.getContent();
		if (content == null)
		{
			return;
		}
		int room = content.height - textTop(content) - (layout.getButton1() != null ? BUTTON + PAD * 2 : PAD);
		int max = Math.max(0, contentHeight - room);
		scroll = (int) Math.max(0, Math.min(max, scroll + Math.round(notches * ROW * 3)));
	}

	/** Where a page's text starts under its title. */
	private static int textTop(Rectangle content)
	{
		return content.y + TITLE;
	}

	/** A line of a page: its text, and whether it is a heading. */
	@Value
	private static class Line
	{
		String text;
		boolean heading;
	}

	/** A page's lines, wrapped to {@code width}. */
	private List<Line> lines(Page page, int width)
	{
		List<Line> out = new ArrayList<>();
		switch (page)
		{
			case HELP:
				for (String[] section : MapHelp.SECTIONS)
				{
					heading(out, section[0], width);
					paragraph(out, section[1], width);
				}
				break;
			case WHATS_NEW:
				heading(out, "Advanced Map " + WhatsNew.VERSION, width);
				paragraph(out, WhatsNew.NOTE.isEmpty() ? "Nothing new to see in this version: fixes only."
					: WhatsNew.NOTE + ".", width);
				paragraph(out, "Earlier versions and the full notes are on GitHub.", width);
				break;
			case IMPROVE:
				paragraph(out, "Ideas, suggestions and bug reports are welcome, on Discord or on GitHub, whichever you"
					+ " prefer. Both open in your browser.", width);
				paragraph(out, "If something on the map is wrong or missing, say where. The map itself comes from the"
					+ " game's own data and keeps up with it; shops, prices, monsters and items on the ground come from"
					+ " the OSRS Wiki and are updated with the plugin.", width);
				break;
			case REDRAW:
				paragraph(out, savedStatus.get(), width);
				paragraph(out, "Building the map again deletes the map saved on this computer and builds every region"
					+ " again from the game's data. What this client has already shown stays on the map until it"
					+ " restarts; everything else is built again as you look around.", width);
				break;
			default:
				break;
		}
		return out;
	}

	private static void heading(List<Line> out, String text, int width)
	{
		if (!out.isEmpty())
		{
			out.add(new Line("", false));
		}
		for (String line : SideList.wrap(text, width, FontManager.getRunescapeBoldFont()))
		{
			out.add(new Line(line, true));
		}
	}

	private static void paragraph(List<Line> out, String text, int width)
	{
		if (!out.isEmpty() && !out.get(out.size() - 1).isHeading() && !out.get(out.size() - 1).getText().isEmpty())
		{
			out.add(new Line("", false));
		}
		for (String line : SideList.wrap(text, width))
		{
			out.add(new Line(line, false));
		}
	}

	void draw(Graphics2D g, Layout layout, Point mouse)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		Rectangle menu = layout.getMenu();
		panel(g, menu);
		for (Page entry : Page.values())
		{
			int y = menu.y + PAD + entry.ordinal() * ROW;
			boolean lit = entry == page && entry != Page.SETTINGS
				|| mouse != null && menu.contains(mouse) && mouse.y >= y && mouse.y < y + ROW;
			if (lit)
			{
				g.setColor(LIT);
				g.fillRect(menu.x + 2, y, menu.width - 4, ROW);
			}
			g.setColor(MapFilters.TEXT);
			g.drawString(entry.label, menu.x + PAD + 4, y + (ROW + metrics.getAscent() - metrics.getDescent()) / 2);
		}
		MapFilters.drawButton(g, layout.getGithub(), "GitHub", metrics);
		MapFilters.drawButton(g, layout.getDiscord(), "Discord", metrics);

		Rectangle content = layout.getContent();
		if (content == null)
		{
			return;
		}
		panel(g, content);
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bold = g.getFontMetrics();
		g.setColor(MapFilters.TEXT);
		g.drawString(page.label, content.x + PAD, content.y + PAD + (ROW + bold.getAscent() - bold.getDescent()) / 2);
		// the text, scrolled, between the title and the buttons
		int top = textTop(content);
		int bottom = content.y + content.height - (layout.getButton1() != null ? BUTTON + PAD * 2 : PAD);
		Rectangle text = new Rectangle(content.x, top, content.width, bottom - top);
		List<Line> lines = lines(page, textWidth(content.width));
		int lineHeight = LINE;
		contentHeight = lines.size() * lineHeight;
		Shape clip = g.getClip();
		g.clip(text);
		int y = top - scroll;
		for (Line line : lines)
		{
			if (y + lineHeight >= top && y <= bottom)
			{
				g.setFont(line.isHeading() ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
				g.setColor(MapFilters.TEXT);
				g.drawString(line.getText(), content.x + PAD, y + metrics.getAscent());
			}
			y += lineHeight;
		}
		g.setClip(clip);
		if (contentHeight > text.height)
		{
			int thumb = Math.max(20, text.height * text.height / contentHeight);
			int thumbY = text.y + (text.height - thumb) * scroll / Math.max(1, contentHeight - text.height);
			g.setColor(new Color(255, 255, 255, 60));
			g.fillRoundRect(content.x + content.width - 7, thumbY, 4, thumb, 4, 4);
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		switch (page)
		{
			case WHATS_NEW:
				MapFilters.drawButton(g, layout.getButton1(), "Full release notes on GitHub", metrics);
				break;
			case IMPROVE:
				MapFilters.drawButton(g, layout.getButton1(), "Discord", metrics);
				MapFilters.drawButton(g, layout.getButton2(), "GitHub issues", metrics);
				break;
			case REDRAW:
				MapFilters.drawButton(g, layout.getButton1(), "Delete and build again", metrics);
				MapFilters.drawButton(g, layout.getButton2(), "Cancel", metrics);
				break;
			default:
				break;
		}
	}

	private static void panel(Graphics2D g, Rectangle box)
	{
		g.setColor(MapFilters.PANEL);
		g.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);
		g.setColor(MapFilters.EDGE);
		g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 8, 8);
	}
}
