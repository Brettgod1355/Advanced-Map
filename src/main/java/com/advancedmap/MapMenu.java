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
import java.awt.font.FontRenderContext;
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
		/** The saved map could not be opened (the log says why): nothing is built or saved this run. */
		boolean unopened;
		/** The game's map data could not be read: the building has stopped until the plugin is updated. */
		boolean halted;
		/** Floors left out because they could not be drawn; the log says why. */
		int failed;
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
	/** How tall a line of a page's text is; a heading's is {@link SideList#BOLD_LINE}, its font standing taller. */
	static final int LINE = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
		.createGraphics().getFontMetrics(FontManager.getRunescapeSmallFont()).getHeight();
	private static final Color LIT = new Color(255, 255, 255, 34);
	private static final Color BAR = new Color(90, 170, 90);

	/** What the sidebar tab says of the saved map, for the "Build the map again" page. */
	private final Supplier<String> savedStatus;
	private final Supplier<Build> build;
	private volatile boolean open;
	/** The page's lines as last wrapped, with what they were wrapped for: a paint wraps them again only on a change. */
	private volatile Wrapped wrapped;
	/** Whether the open page opened by itself: then Escape closes the menu with it. */
	private volatile boolean popped;
	private volatile Page page;
	private volatile int scroll;
	/** How tall the open page's text was last drawn, for scrolling. */
	private volatile int contentHeight;
	/**
	 * How the painter last drew text: the page is wrapped by it. On a screen scaled by Windows (1.75 here) a line of the
	 * RuneScape fonts runs some 4% wider than unscaled, so text wrapped unscaled ran past the page's right edge.
	 */
	private volatile FontRenderContext measure = new FontRenderContext(null, false, false);

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
		return layout(buttons, width, height, open, wrappedLines(open, textWidth(pageWidth(buttons, width))));
	}

	/** Wraps the page from now on as {@code g} draws text: called by the painter before it lays the menu out. */
	void measureWith(Graphics2D g)
	{
		FontRenderContext drawn = g.getFontRenderContext();
		if (!drawn.equals(measure))
		{
			measure = drawn;
		}
	}

	@Value
	private static class Wrapped
	{
		/** The page, the column and what its text says that changes (the build's progress, the saved map). */
		List<Object> key;
		List<Line> lines;
	}

	/** How wide a page's text is, and how its words are measured. */
	@Value
	static class Column
	{
		int width;
		FontRenderContext measure;
	}

	/** A page's lines, wrapped again only when the page, the column or its changing text is not what it was. */
	private List<Line> wrappedLines(Page page, int width)
	{
		Object state = state(page);
		Column column = new Column(width, measure);
		List<Object> key = java.util.Arrays.asList(page, column, state);
		Wrapped last = wrapped;
		if (last != null && last.getKey().equals(key))
		{
			return last.getLines();
		}
		// wrapped from the very state the key holds, read once: never another moment's
		List<Line> lines = lines(page, column, state);
		wrapped = new Wrapped(key, lines);
		return lines;
	}

	/** How tall a page's lines are: a heading as its bold font, every other line as the text's. */
	static int height(List<Line> lines)
	{
		int height = 0;
		for (Line line : lines)
		{
			height += line.isHeading() ? SideList.BOLD_LINE : LINE;
		}
		return height;
	}

	/** How many times a page's text has been wrapped, for a test. */
	int wraps;

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

	static int textWidth(int pageWidth)
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
		int textHeight = lines == null ? -1 : height(lines);
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
		// the text this layout holds; the height last drawn may be another page's
		int height = layout.getLines() != null ? height(layout.getLines()) : contentHeight;
		int max = Math.max(0, height - textRoom(layout));
		scroll = (int) Math.max(0, Math.min(max, scroll + Math.round(notches * ROW * 3)));
	}

	/** How far the open page's text is scrolled, for a test. */
	int scrolled()
	{
		return scroll;
	}

	/** How tall the room for a page's text is, between its title and its buttons. */
	private static int textRoom(Layout layout)
	{
		return layout.getContent().height - TITLE - (layout.getButton1() != null ? BUTTON + PAD * 2 : PAD);
	}

	/** Where a page's text starts under its title. */
	private static int textTop(Rectangle content)
	{
		return content.y + TITLE;
	}

	/** A line of a page: its text, and whether it is a heading; or a progress bar, filled {@code bar} of the way. */
	@Value
	static class Line
	{
		String text;
		boolean heading;
		/** How far the bar is filled, 0 to 1; negative for a line of text. */
		double bar;
	}

	/** What a page shows that changes: the build's progress on Welcome, the saved map on the rebuild page. */
	private Object state(Page page)
	{
		return page == Page.WELCOME ? build.get() : page == Page.REDRAW ? savedStatus.get() : null;
	}

	/** A page's lines, wrapped to its {@code column}, its changing part as {@code state} says it. */
	private List<Line> lines(Page page, Column column, Object state)
	{
		wraps++;
		List<Line> out = new ArrayList<>();
		switch (page)
		{
			case WELCOME:
				status(out, (Build) state, column);
				for (String[] section : MapHelp.BUILDING)
				{
					heading(out, section[0], column);
					paragraph(out, section[1], column);
				}
				break;
			case HELP:
				for (String[] section : MapHelp.ALL)
				{
					heading(out, section[0], column);
					paragraph(out, section[1], column);
				}
				break;
			case WHATS_NEW:
				heading(out, "Advanced Map " + WhatsNew.VERSION, column);
				// empty for the first release as for one of fixes only: neither has news to tell
				paragraph(out, WhatsNew.NOTE.isEmpty() ? "Nothing new to tell of in this version."
					: Character.toUpperCase(WhatsNew.NOTE.charAt(0)) + WhatsNew.NOTE.substring(1) + ".", column);
				paragraph(out, "Earlier versions and the full notes are on GitHub.", column);
				break;
			case IMPROVE:
				paragraph(out, "Ideas, suggestions and bug reports are welcome, on Discord or on GitHub, whichever you"
					+ " prefer. Both open in your browser.", column);
				paragraph(out, "If something on the map is wrong or missing, say where. The map itself comes from the"
					+ " game's own data and keeps up with it; shops, prices, monsters and items on the ground come from"
					+ " the OSRS Wiki and are updated with the plugin.", column);
				break;
			case REDRAW:
				paragraph(out, (String) state, column);
				paragraph(out, "Building the map again deletes the map saved on this computer and builds every region"
					+ " again from the game's data. What this client has already shown stays on the map until it"
					+ " restarts; everything else is built again as you look around.", column);
				break;
			default:
				break;
		}
		return out;
	}

	/** The Welcome page's first lines: how far the building is, with a bar while it goes on. */
	private static void status(List<Line> out, Build build, Column column)
	{
		if (build.isUnopened())
		{
			heading(out, "The saved map could not be opened", column);
			paragraph(out, "The client's log says why. Until it is put right, the map is drawn as you look around and"
				+ " nothing is saved; a client restart tries again.", column);
		}
		else if (build.isHalted())
		{
			heading(out, "Building stopped", column);
			paragraph(out, String.format(java.util.Locale.ROOT, "The game's map data could not be read: a game update"
				+ " may have changed its format. What is saved (%,d regions and floors) still shows; nothing more is"
				+ " built until Advanced Map is updated, which RuneLite does by itself.", Math.max(0, build.getSaved())),
				column);
		}
		else if (build.getSaved() < 0)
		{
			heading(out, "Getting ready", column);
			paragraph(out, "The map starts building a few seconds after the client starts.", column);
		}
		else if (!build.isBackground())
		{
			heading(out, "Built as you look around", column);
			paragraph(out, String.format(java.util.Locale.ROOT, "\"Build the whole map\" is off in the settings, so"
				+ " only the places you look at are built and saved: %,d regions and floors so far.", build.getSaved()),
				column);
		}
		else if (build.getLeft() < 0)
		{
			heading(out, "Building the map", column);
			paragraph(out, "Counting what is left to build...", column);
		}
		else if (build.getLeft() > 0)
		{
			int total = build.getSaved() + build.getLeft();
			heading(out, String.format(java.util.Locale.ROOT, "Building the map: %,d to go", build.getLeft()), column);
			out.add(new Line("", false, (double) build.getSaved() / total));
			paragraph(out, String.format(java.util.Locale.ROOT, "%,d regions and floors built of %,d. You can play"
				+ " and use the map meanwhile.", build.getSaved(), total), column);
		}
		else
		{
			heading(out, "The map is built", column);
			paragraph(out, String.format(java.util.Locale.ROOT, "All %,d regions and floors are saved on this"
				+ " computer, for every character.", build.getSaved()) + (build.getFailed() == 0 ? "" : String.format(
				java.util.Locale.ROOT, " %,d could not be built; the client's log says why.", build.getFailed())),
				column);
		}
	}

	private static void heading(List<Line> out, String text, Column column)
	{
		if (!out.isEmpty())
		{
			out.add(new Line("", false, -1));
		}
		for (String line : SideList.wrap(text, column.getWidth(), FontManager.getRunescapeBoldFont(),
			column.getMeasure()))
		{
			out.add(new Line(line, true, -1));
		}
	}

	private static void paragraph(List<Line> out, String text, Column column)
	{
		Line last = out.isEmpty() ? null : out.get(out.size() - 1);
		if (last != null && !last.isHeading() && (!last.getText().isEmpty() || last.getBar() >= 0))
		{
			out.add(new Line("", false, -1));
		}
		for (String line : SideList.wrap(text, column.getWidth(), FontManager.getRunescapeSmallFont(),
			column.getMeasure()))
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
		int bottom = top + textRoom(layout);
		Rectangle text = new Rectangle(content.x, top, content.width, bottom - top);
		List<Line> lines = layout.getLines() != null ? layout.getLines()
			: lines(shown, new Column(textWidth(content.width), measure), state(shown));
		contentHeight = height(lines);
		// drawn no further than the text's end, also after the page got taller or its text shorter; kept here, not
		// written back, where the wheel (another thread) may be scrolling meanwhile
		int shownScroll = Math.min(scroll, Math.max(0, contentHeight - text.height));
		Shape clip = g.getClip();
		g.clip(text);
		int y = top - shownScroll;
		for (Line line : lines)
		{
			int lineHeight = line.isHeading() ? SideList.BOLD_LINE : LINE;
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
				// each line on its own font's baseline: a heading's bold letters stand taller than the text's, and
				// drawn on the text's they were cut at the top of the first line
				g.setFont(line.isHeading() ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
				g.setColor(MapFilters.TEXT);
				g.drawString(line.getText(), content.x + PAD, y + g.getFontMetrics().getAscent());
			}
			y += lineHeight;
		}
		g.setClip(clip);
		if (contentHeight > text.height)
		{
			int thumb = Math.max(20, text.height * text.height / contentHeight);
			int thumbY = text.y + (text.height - thumb) * shownScroll / Math.max(1, contentHeight - text.height);
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
