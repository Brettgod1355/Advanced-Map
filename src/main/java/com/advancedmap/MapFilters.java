/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;
import net.runelite.client.ui.FontManager;

/**
 * The Filters list that drops down below the map's Filters button: at the top the switch that shows the icons the
 * player hid one by one (faded, so they can be unhidden), then a search box, "All on" and "All off", then every
 * group of icons with each kind of icon in it (the game's own picture and name, or Advanced Map's teleport mark), each
 * with a box that turns it on or off. It scrolls with the mouse wheel. Holds what is open, typed and scrolled; the
 * settings themselves are the plugin's. Laid out the same way for drawing and for clicks.
 */
final class MapFilters
{
	static final int WIDTH = 290;
	static final int ROW = 22;
	private static final int PAD = 8;
	private static final int SEARCH_HEIGHT = 24;
	private static final int MAX_HEIGHT = 470;
	/** Room left under the list; the bar along the bottom of the map is not drawn while the list is open. */
	static final int BOTTOM_ROOM = 8;
	private static final int INDENT = 18;
	private static final int BOX = 12;
	/** The line under "Show hidden icons" that says how to hide one. */
	private static final int HINT = 14;

	private static final Color PANEL = new Color(0x10, 0x18, 0x22, 240);
	private static final Color EDGE = new Color(0x5A5040);
	private static final Color TEXT = new Color(0xE8E2D0);
	private static final Color QUIET = new Color(0x8C8676);
	private static final Color FIELD = new Color(0x1E2833);
	private static final Color ON = new Color(0x3FA34D);

	/** One row of the list: a group, or a kind of icon in it. */
	@Value
	static class Entry
	{
		IconGroup group;
		/** Null for a group's row; else as {@link MapSettings#typeKey}. */
		String key;
		/** For a game category, the map area whose picture it shows, else -1. */
		int areaId;
		/** For a kind of teleport, else null. */
		Teleport.Type teleportType;
		String name;

		boolean isGroup()
		{
			return key == null;
		}
	}

	/** Where each part of the list is on the screen. */
	@Value
	static class Layout
	{
		Rectangle panel;
		/** "Your Slayer task", at the top. */
		Rectangle slayer;
		Rectangle showHidden;
		Rectangle search;
		Rectangle allOn;
		Rectangle allOff;
		Rectangle list;
	}

	enum Part
	{
		OUTSIDE,
		PANEL,
		SLAYER,
		SHOW_HIDDEN,
		SEARCH,
		ALL_ON,
		ALL_OFF,
		ENTRY
	}

	@Value
	static class Hit
	{
		Part part;
		/** For {@link Part#ENTRY}, the row. */
		Entry entry;
	}

	/** For each icon category the game has, the map area whose picture the list shows; empty until read. */
	private volatile Map<Integer, Integer> categoryIcons = Collections.emptyMap();
	private volatile boolean open;
	/** The Slayer task RuneLite's Slayer plugin has saved, "Kalphite workers (34 left)"; null when there is none. */
	private volatile String slayerTask;
	private volatile String search = "";
	private volatile int scroll;

	void setSlayerTask(String task)
	{
		slayerTask = task;
	}

	/** The map area whose picture stands for an icon category, or null while not read or for no such category. */
	Integer areaOf(int category)
	{
		return categoryIcons.get(category);
	}

	void setCategoryIcons(Map<Integer, Integer> icons)
	{
		categoryIcons = Collections.unmodifiableMap(icons);
	}

	/** Every kind of icon in a group, as {@link MapSettings#typeKey}. */
	List<String> keysOf(IconGroup group)
	{
		List<String> keys = new ArrayList<>();
		if (group == IconGroup.TELEPORTS)
		{
			for (Teleport.Type type : Teleport.Type.values())
			{
				keys.add(MapSettings.typeKey(type));
			}
		}
		for (int category : categoryIcons.keySet())
		{
			if (group != IconGroup.TELEPORTS && IconGroup.of(category) == group)
			{
				keys.add(MapSettings.typeKey(category));
			}
		}
		return keys;
	}

	boolean hasCategories()
	{
		return !categoryIcons.isEmpty();
	}

	boolean isOpen()
	{
		return open;
	}

	void setOpen(boolean open)
	{
		this.open = open;
		if (open)
		{
			scroll = 0;
		}
		else
		{
			// typed to find a kind this time: the next opening starts with the whole list, not last week's filter
			search = "";
		}
	}

	String search()
	{
		return search;
	}

	void type(char c)
	{
		search = search + c;
		scroll = 0;
	}

	void backspace()
	{
		String now = search;
		search = now.isEmpty() ? now : now.substring(0, now.length() - 1);
		scroll = 0;
	}

	/** Scrolls the list by mouse wheel notches, kept within the list. */
	void scroll(double notches, Layout layout, int rows)
	{
		int max = Math.max(0, rows * ROW - layout.getList().height);
		scroll = (int) Math.max(0, Math.min(max, scroll + Math.round(notches * ROW * 3)));
	}

	/** The rows to show: every group with its kinds, sorted by name; while searching, only what matches. */
	List<Entry> entries(Progress progress)
	{
		return entries(categoryIcons, progress, search);
	}

	static List<Entry> entries(Map<Integer, Integer> categoryIcons, Progress progress, String search)
	{
		String wanted = search.trim().toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>();
		for (IconGroup group : IconGroup.values())
		{
			List<Entry> kinds = new ArrayList<>();
			if (group == IconGroup.TELEPORTS)
			{
				for (Teleport.Type type : Teleport.Type.values())
				{
					kinds.add(new Entry(group, MapSettings.typeKey(type), -1, type, type.label));
				}
			}
			else
			{
				for (Map.Entry<Integer, Integer> category : categoryIcons.entrySet())
				{
					if (IconGroup.of(category.getKey()) != group)
					{
						continue;
					}
					String name = progress.categoryName(category.getKey());
					kinds.add(new Entry(group, MapSettings.typeKey(category.getKey()), category.getValue(), null,
						name != null ? name : "Icon " + category.getKey()));
				}
				kinds.sort(Comparator.comparing(entry -> entry.getName().toLowerCase(Locale.ROOT)));
			}
			boolean groupMatches = wanted.isEmpty() || group.label.toLowerCase(Locale.ROOT).contains(wanted);
			if (!groupMatches)
			{
				kinds.removeIf(entry -> !entry.getName().toLowerCase(Locale.ROOT).contains(wanted));
				if (kinds.isEmpty())
				{
					continue;
				}
			}
			out.add(new Entry(group, null, -1, null, group.label));
			out.addAll(kinds);
		}
		return out;
	}

	/** The list's parts, under the Filters button, as tall as the map allows. */
	static Layout layout(Rectangle button, int height)
	{
		int x = button.x;
		int y = button.y + button.height + 4;
		int panelHeight = Math.max(ROW * 8, Math.min(MAX_HEIGHT, height - y - BOTTOM_ROOM));
		Rectangle panel = new Rectangle(x, y, WIDTH, panelHeight);
		Rectangle slayer = new Rectangle(x + PAD, y + PAD / 2, WIDTH - PAD * 2, ROW);
		Rectangle showHidden = new Rectangle(x + PAD, slayer.y + slayer.height, WIDTH - PAD * 2, ROW);
		int searchY = showHidden.y + showHidden.height + HINT + PAD;
		Rectangle search = new Rectangle(x + PAD, searchY, WIDTH - PAD * 2, SEARCH_HEIGHT);
		int half = (WIDTH - PAD * 3) / 2;
		int buttonsY = search.y + search.height + 6;
		Rectangle allOn = new Rectangle(x + PAD, buttonsY, half, ROW);
		Rectangle allOff = new Rectangle(x + PAD * 2 + half, buttonsY, half, ROW);
		int listY = buttonsY + ROW + 6;
		Rectangle list = new Rectangle(x, listY, WIDTH, y + panelHeight - PAD / 2 - listY);
		return new Layout(panel, slayer, showHidden, search, allOn, allOff, list);
	}

	/** What a press at a point is on. */
	Hit hitAt(int x, int y, Layout layout, List<Entry> entries)
	{
		if (!layout.getPanel().contains(x, y))
		{
			return new Hit(Part.OUTSIDE, null);
		}
		if (layout.getSlayer().contains(x, y))
		{
			return new Hit(Part.SLAYER, null);
		}
		if (layout.getShowHidden().contains(x, y))
		{
			return new Hit(Part.SHOW_HIDDEN, null);
		}
		if (layout.getSearch().contains(x, y))
		{
			return new Hit(Part.SEARCH, null);
		}
		if (layout.getAllOn().contains(x, y))
		{
			return new Hit(Part.ALL_ON, null);
		}
		if (layout.getAllOff().contains(x, y))
		{
			return new Hit(Part.ALL_OFF, null);
		}
		Rectangle list = layout.getList();
		if (list.contains(x, y))
		{
			int index = (y - list.y + scroll) / ROW;
			if (index >= 0 && index < entries.size())
			{
				return new Hit(Part.ENTRY, entries.get(index));
			}
		}
		return new Hit(Part.PANEL, null);
	}

	/** True when a row's box is ticked: its group is on and, for a kind, the kind is not turned off. */
	static boolean isOn(Entry entry, MapSettings settings)
	{
		if (!settings.shows(entry.getGroup()))
		{
			return false;
		}
		return entry.isGroup() || !settings.getHiddenTypes().contains(entry.getKey());
	}

	void draw(Graphics2D g, Layout layout, List<Entry> entries, MapSettings settings, MapIcons icons)
	{
		Rectangle panel = layout.getPanel();
		g.setColor(PANEL);
		g.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 8, 8);
		g.setColor(EDGE);
		g.drawRoundRect(panel.x, panel.y, panel.width - 1, panel.height - 1, 8, 8);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		drawSlayer(g, layout.getSlayer(), settings, icons, metrics);
		drawShowHidden(g, layout.getShowHidden(), settings, metrics);
		Rectangle search = layout.getSearch();
		g.setColor(FIELD);
		g.fillRect(search.x, search.y, search.width, search.height);
		g.setColor(EDGE);
		g.drawRect(search.x, search.y, search.width - 1, search.height - 1);
		int textY = search.y + (search.height + metrics.getAscent() - metrics.getDescent()) / 2;
		String typed = this.search;
		if (typed.isEmpty())
		{
			g.setColor(QUIET);
			g.drawString("Type to search", search.x + 6, textY);
		}
		else
		{
			g.setColor(TEXT);
			g.drawString(Caret.draw(typed + "|"), search.x + 6, textY);
		}
		drawButton(g, layout.getAllOn(), "All on", metrics);
		drawButton(g, layout.getAllOff(), "All off", metrics);

		Rectangle list = layout.getList();
		Shape clip = g.getClip();
		g.clip(list);
		int y = list.y - scroll;
		if (entries.isEmpty())
		{
			g.setColor(QUIET);
			g.drawString(categoryIcons.isEmpty() ? "Reading the icons..." : "Nothing matches", list.x + PAD,
				list.y + ROW - 6);
		}
		for (Entry entry : entries)
		{
			if (y + ROW >= list.y && y <= list.y + list.height)
			{
				drawEntry(g, entry, list.x, y, settings, icons, metrics);
			}
			y += ROW;
		}
		g.setClip(clip);

		int content = entries.size() * ROW;
		if (content > list.height)
		{
			int thumb = Math.max(20, list.height * list.height / content);
			int top = list.y + (list.height - thumb) * scroll / Math.max(1, content - list.height);
			g.setColor(new Color(255, 255, 255, 60));
			g.fillRoundRect(list.x + list.width - 7, top, 4, thumb, 4, 4);
		}
	}

	private static void drawEntry(Graphics2D g, Entry entry, int x, int y, MapSettings settings, MapIcons icons,
		FontMetrics metrics)
	{
		boolean on = isOn(entry, settings);
		boolean groupOn = settings.shows(entry.getGroup());
		int left = x + PAD + (entry.isGroup() ? 0 : INDENT);
		drawBox(g, left, y + (ROW - BOX) / 2, on);
		int textX = left + BOX + 8;
		if (!entry.isGroup())
		{
			Rectangle at = new Rectangle(textX, y + (ROW - 15) / 2, 15, 15);
			if (entry.getTeleportType() != null)
			{
				MapPainter.drawTeleportMark(g, at, entry.getTeleportType(), false);
			}
			else
			{
				BufferedImage image = icons.image(entry.getAreaId(), false);
				if (image != null)
				{
					g.drawImage(image, at.x + (15 - image.getWidth()) / 2, at.y + (15 - image.getHeight()) / 2, null);
				}
			}
			textX += 15 + 6;
		}
		g.setFont(entry.isGroup() ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
		FontMetrics rowMetrics = g.getFontMetrics();
		g.setColor(entry.isGroup() || groupOn ? TEXT : QUIET);
		g.drawString(entry.getName(), textX, y + (ROW + rowMetrics.getAscent() - rowMetrics.getDescent()) / 2);
		g.setFont(metrics.getFont());
	}

	/**
	 * "Show hidden icons", with how many the player has hidden, and under it how to hide one. A line under it sets it
	 * apart from the filters.
	 */
	/** "Your Slayer task", ticked while its spawns are marked, with a Slayer master's picture and the task. */
	private void drawSlayer(Graphics2D g, Rectangle row, MapSettings settings, MapIcons icons, FontMetrics metrics)
	{
		drawBox(g, row.x, row.y + (ROW - BOX) / 2, settings.isSlayerShown());
		int textX = row.x + BOX + 8;
		Integer area = areaOf(IconGroup.SLAYER_MASTER);
		BufferedImage image = area == null ? null : icons.image(area, false);
		if (image != null)
		{
			g.drawImage(image, textX + (15 - image.getWidth()) / 2, row.y + (ROW - image.getHeight()) / 2, null);
		}
		textX += 15 + 6;
		int textY = row.y + (ROW + metrics.getAscent() - metrics.getDescent()) / 2;
		String label = "Your Slayer task";
		g.setColor(TEXT);
		g.drawString(label, textX, textY);
		// the task, or that there is none (RuneLite's Slayer plugin keeps it)
		String task = slayerTask;
		String note = SideList.fit(task != null ? task : "no task", metrics,
			row.x + row.width - textX - metrics.stringWidth(label) - 8);
		g.setColor(QUIET);
		g.drawString(note, row.x + row.width - metrics.stringWidth(note), textY);
	}

	private static void drawShowHidden(Graphics2D g, Rectangle row, MapSettings settings, FontMetrics metrics)
	{
		drawBox(g, row.x, row.y + (ROW - BOX) / 2, settings.isShowHidden());
		int textY = row.y + (ROW + metrics.getAscent() - metrics.getDescent()) / 2;
		g.setColor(TEXT);
		g.drawString("Show hidden icons", row.x + BOX + 8, textY);
		int count = settings.getHidden().size();
		String hidden = count == 0 ? "none hidden" : count + " hidden";
		g.setColor(QUIET);
		g.drawString(hidden, row.x + row.width - metrics.stringWidth(hidden), textY);
		g.drawString("Right-click an icon on the map to hide or unhide it", row.x + BOX + 8,
			row.y + row.height + metrics.getAscent() - 2);
		int lineY = row.y + row.height + HINT + PAD / 2;
		g.setColor(EDGE);
		g.drawLine(row.x, lineY, row.x + row.width, lineY);
	}

	/** A tick box, green and ticked when on. */
	private static void drawBox(Graphics2D g, int x, int y, boolean on)
	{
		g.setColor(on ? ON : new Color(0x2A333D));
		g.fillRect(x, y, BOX, BOX);
		g.setColor(TEXT);
		g.drawRect(x, y, BOX, BOX);
		if (on)
		{
			g.drawLine(x + 3, y + 6, x + 5, y + 9);
			g.drawLine(x + 5, y + 9, x + 10, y + 3);
		}
	}

	private static void drawButton(Graphics2D g, Rectangle button, String text, FontMetrics metrics)
	{
		g.setColor(FIELD);
		g.fillRoundRect(button.x, button.y, button.width, button.height, 6, 6);
		g.setColor(TEXT);
		g.drawString(text, button.x + (button.width - metrics.stringWidth(text)) / 2,
			button.y + (button.height + metrics.getAscent() - metrics.getDescent()) / 2);
	}
}
