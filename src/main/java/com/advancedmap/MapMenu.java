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
 * map for the entry chosen (the welcome, the help, what's new, where to write, building the map again); Settings opens
 * RuneLite's own settings page instead. The Welcome and What's new pages also open by themselves (see
 * {@link #popUp}). Drawn by the painter over the game and in the window alike.
 */
final class MapMenu
{
	enum Page
	{
		WELCOME("Welcome", "Welcome to Advanced Map"),
		HELP("How the map works"),
		/** Opens the list down the right side instead of a page, as the next does. */
		MARKERS("Your markers", null),
		HIDDEN("Hidden icons", null),
		/** Opens RuneLite's settings page instead of a page. */
		SETTINGS("Settings", null),
		WHATS_NEW("What's new"),
		IMPROVE("Help improve the map"),
		REDRAW("Build the map again");

		final String label;
		/** The page's title, over its text; null for an entry that opens something else instead. */
		final String title;

		Page(String label)
		{
			this(label, label);
		}

		Page(String label, String title)
		{
			this.label = label;
			this.title = title;
		}

		/** Whether the entry opens a page beside the menu. */
		boolean isPage()
		{
			return title != null;
		}
	}

	/** How far the building of the map is, for the Welcome page. */
	@Value
	static class Build
	{
		/** Regions' floors saved on this computer; -1 while the saved map is not open yet. */
		int saved;
		/** Floors still to build in the background; -1 before they are counted. */
		int left;
		/** Whether the whole map is built in the background ("Build the whole map"). */
		boolean background;
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
		/** The page's first button: the help, the release notes, Discord, or "Delete and build again". */
		BUTTON1,
		/** The page's second button: Close, GitHub issues, or Cancel. */
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
		/**
		 * The page this layout is for, read once: input on another thread may close or change the page while a paint
		 * draws it, so drawing and clicks go by this, never by the menu's current page.
		 */
		Page page;
		/** That page's lines, wrapped to its width; null when not wrapped. */
		List<Line> lines;
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
	private static final Color BAR = new Color(90, 170, 90);

	/** What the sidebar tab says of the saved map, for the "Build the map again" page. */
	private final Supplier<String> savedStatus;
	private final Supplier<Build> build;
	private volatile boolean open;
	/** Whether the open page opened by itself: then Escape closes the menu with it. */
	private volatile boolean popped;
	private volatile Page page;
	private volatile int scroll;
	/** How tall the open page's text was last drawn, for scrolling. */
	private volatile int contentHeight;

	MapMenu(Supplier<String> savedStatus, Supplier<Build> build)
	{
		this.savedStatus = savedStatus;
		this.build = build;
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
		popped = false;
		scroll = 0;
	}

	/** Opens the menu on a page by itself, as the map opens. */
	void popUp(Page page)
	{
		open = true;
		setPage(page);
		popped = true;
	}

	/** Whether the open page opened by itself. */
	boolean popped()
	{
		return popped;
	}

	/**
	 * The page to open by itself as the map opens, or null: What's new once after an update; else Welcome the first
	 * time the map is opened, and once a run while the map is still first being built.
	 */
	static Page popUp(boolean whatsNewDue, boolean welcomed, boolean welcomedThisRun, boolean built, int left)
	{
		if (whatsNewDue)
		{
			return Page.WHATS_NEW;
		}
		return !welcomed || !welcomedThisRun && !built && left > 0 ? Page.WELCOME : null;
	}

	/** The menu and the open page, the page as wide and as tall as its text needs. */
	Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int height)
	{
		Page open = page;
		if (open == null || !open.isPage())
		{
			return layout(buttons, width, height, open);
		}
		List<Line> lines = lines(open, textWidth(pageWidth(buttons, width)));
		return layout(buttons, width, height, open, lines);
	}

	/** The menu under its button, and a page beside it down to the map's bottom edge. */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int height, Page page)
	{
		return layout(buttons, width, height, page, null);
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
	 * The menu under its button, as tall as its entries, and a page beside it as tall as its {@code lines} need with
	 * its title and buttons, no lower than the map's bottom edge (null lines: down to that edge).
	 */
	private static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int height, Page page,
		List<Line> lines)
	{
		int textHeight = lines == null ? -1 : lines.size() * LINE;
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
		if (page != null && page.isPage())
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
				case WELCOME:
				case IMPROVE:
				case REDRAW:
					button1 = new Rectangle(content.x + PAD, buttonsY, narrow, BUTTON);
					button2 = new Rectangle(content.x + PAD * 2 + narrow, buttonsY, narrow, BUTTON);
					break;
				default:
					break;
			}
		}
		return new Layout(menu, github, discord, content, button1, button2, content == null ? null : page,
			content == null ? null : lines);
	}

	Hit hitAt(int x, int y, Layout layout)
	{
		Rectangle content = layout.getContent();
		if (content != null && content.contains(x, y))
		{
			if (layout.getButton1() != null && layout.getButton1().contains(x, y))
			{
				return new Hit(Part.BUTTON1, layout.getPage());
			}
			if (layout.getButton2() != null && layout.getButton2().contains(x, y))
			{
				return new Hit(Part.BUTTON2, layout.getPage());
			}
			return new Hit(Part.CONTENT, layout.getPage());
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

	/** A line of a page: its text, and whether it is a heading; or a progress bar, filled {@code bar} of the way. */
	@Value
	private static class Line
	{
		String text;
		boolean heading;
		/** How far the bar is filled, 0 to 1; negative for a line of text. */
		double bar;
	}

	/** A page's lines, wrapped to {@code width}. */
	private List<Line> lines(Page page, int width)
	{
		List<Line> out = new ArrayList<>();
		switch (page)
		{
			case WELCOME:
				status(out, build.get(), width);
				for (String[] section : MapHelp.BUILDING)
				{
					heading(out, section[0], width);
					paragraph(out, section[1], width);
				}
				break;
			case HELP:
				for (String[] section : MapHelp.ALL)
				{
					heading(out, section[0], width);
					paragraph(out, section[1], width);
				}
				break;
			case WHATS_NEW:
				heading(out, "Advanced Map " + WhatsNew.VERSION, width);
				paragraph(out, WhatsNew.NOTE.isEmpty() ? "Nothing new to see in this version: fixes only."
					: Character.toUpperCase(WhatsNew.NOTE.charAt(0)) + WhatsNew.NOTE.substring(1) + ".", width);
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

	/** The Welcome page's first lines: how far the building is, with a bar while it goes on. */
	private static void status(List<Line> out, Build build, int width)
	{
		if (build.getSaved() < 0)
		{
			heading(out, "Getting ready", width);
			paragraph(out, "The map starts building a few seconds after the client starts.", width);
		}
		else if (!build.isBackground())
		{
			heading(out, "Built as you look around", width);
			paragraph(out, String.format(java.util.Locale.ROOT, "\"Build the whole map\" is off in the settings, so"
				+ " only the places you look at are built and saved: %,d regions and floors so far.", build.getSaved()),
				width);
		}
		else if (build.getLeft() < 0)
		{
			heading(out, "Building the map", width);
			paragraph(out, "Counting what is left to build...", width);
		}
		else if (build.getLeft() > 0)
		{
			int total = build.getSaved() + build.getLeft();
			heading(out, String.format(java.util.Locale.ROOT, "Building the map: %,d to go", build.getLeft()), width);
			out.add(new Line("", false, (double) build.getSaved() / total));
			paragraph(out, String.format(java.util.Locale.ROOT, "%,d regions and floors built of %,d. You can play"
				+ " and use the map meanwhile.", build.getSaved(), total), width);
		}
		else
		{
			heading(out, "The map is built", width);
			paragraph(out, String.format(java.util.Locale.ROOT, "All %,d regions and floors are saved on this"
				+ " computer, for every character.", build.getSaved()), width);
		}
	}

	private static void heading(List<Line> out, String text, int width)
	{
		if (!out.isEmpty())
		{
			out.add(new Line("", false, -1));
		}
		for (String line : SideList.wrap(text, width, FontManager.getRunescapeBoldFont()))
		{
			out.add(new Line(line, true, -1));
		}
	}

	private static void paragraph(List<Line> out, String text, int width)
	{
		Line last = out.isEmpty() ? null : out.get(out.size() - 1);
		if (last != null && !last.isHeading() && (!last.getText().isEmpty() || last.getBar() >= 0))
		{
			out.add(new Line("", false, -1));
		}
		for (String line : SideList.wrap(text, width))
		{
			out.add(new Line(line, false, -1));
		}
	}

	void draw(Graphics2D g, Layout layout, Point mouse)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		Rectangle menu = layout.getMenu();
		Page shown = layout.getPage();
		panel(g, menu);
		for (Page entry : Page.values())
		{
			int y = menu.y + PAD + entry.ordinal() * ROW;
			boolean lit = entry == shown
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
		if (content == null || shown == null)
		{
			return;
		}
		panel(g, content);
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bold = g.getFontMetrics();
		g.setColor(MapFilters.TEXT);
		g.drawString(shown.title, content.x + PAD, content.y + PAD + (ROW + bold.getAscent() - bold.getDescent()) / 2);
		// the text, scrolled, between the title and the buttons
		int top = textTop(content);
		int bottom = content.y + content.height - (layout.getButton1() != null ? BUTTON + PAD * 2 : PAD);
		Rectangle text = new Rectangle(content.x, top, content.width, bottom - top);
		List<Line> lines = layout.getLines() != null ? layout.getLines() : lines(shown, textWidth(content.width));
		int lineHeight = LINE;
		contentHeight = lines.size() * lineHeight;
		Shape clip = g.getClip();
		g.clip(text);
		int y = top - scroll;
		for (Line line : lines)
		{
			if (line.getBar() >= 0 && y + lineHeight >= top && y <= bottom)
			{
				int barWidth = textWidth(content.width);
				g.setColor(new Color(0, 0, 0, 120));
				g.fillRoundRect(content.x + PAD, y + 2, barWidth, lineHeight - 4, 6, 6);
				g.setColor(BAR);
				g.fillRoundRect(content.x + PAD, y + 2, (int) Math.round(barWidth * Math.min(1, line.getBar())),
					lineHeight - 4, 6, 6);
				g.setColor(MapFilters.EDGE);
				g.drawRoundRect(content.x + PAD, y + 2, barWidth - 1, lineHeight - 5, 6, 6);
			}
			else if (y + lineHeight >= top && y <= bottom)
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
		switch (shown)
		{
			case WELCOME:
				MapFilters.drawButton(g, layout.getButton1(), "How the map works", metrics);
				MapFilters.drawButton(g, layout.getButton2(), "Close", metrics);
				break;
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
