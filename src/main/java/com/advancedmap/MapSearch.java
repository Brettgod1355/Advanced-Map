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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * The search box at the top of the map: places (the game's own names, dungeon and minigame entrances), shops, the
 * items they sell, monsters and NPCs, matched loosely as the Grand Exchange's search does ({@link #score}), the best
 * few listed under the box while typing. Choosing one is the plugin's ({@code AdvancedMapPlugin.choose}).
 */
@Slf4j
final class MapSearch
{
	static final int WIDTH = 340;
	/** Narrower than this, the box moves under the left buttons. */
	static final int MIN_WIDTH = 160;
	static final int ROW = 22;
	static final int MAX_RESULTS = 8;
	private static final int PAD = 8;
	private static final int GAP = 8;
	private static final int CLEAR = 14;

	private static final Color PANEL = new Color(0x10, 0x18, 0x22, 240);
	private static final Color FIELD = new Color(0, 0, 0, 170);
	private static final Color EDGE = new Color(0x5A5040);
	private static final Color FOCUS = new Color(0xC8AA6E);
	private static final Color TEXT = new Color(0xE8E2D0);
	private static final Color QUIET = new Color(0x8C8676);
	private static final Color LIT = new Color(255, 255, 255, 34);

	enum Kind
	{
		PLACE("Place"),
		MARKER("Your marker"),
		DUNGEON("Dungeon"),
		MINIGAME("Minigame"),
		FAIRY_RING("Fairy ring"),
		ICON("Map icon"),
		SHOP("Shop"),
		ITEM("Item"),
		MONSTER("Monster"),
		NPC("NPC"),
		FISHING("Fishing"),
		HUNTER("Hunter");

		final String label;

		Kind(String label)
		{
			this.label = label;
		}
	}

	/** Something to find: a place, a shop, an item (with its shops), a monster or NPC (with its spawns). */
	@Value
	static class Entry
	{
		Kind kind;
		String name;
		/** The name {@link #normalize normalized}, once, for matching. */
		String key;
		/** Shown on the result's right: "Dungeon", "Lvl 28", "100,000 gp". */
		String note;
		/** Where it is: a place's spots, a shop's, an item's sellers', a creature's spawns. */
		List<WorldPoint> points;
		/** A shop's or an item's sellers (the shop first for a shop); empty otherwise. */
		List<Shops.Shop> shops;
		/** A monster or NPC; null otherwise. */
		Spawns.Creature creature;

		Entry(Kind kind, String name, String note, List<WorldPoint> points, List<Shops.Shop> shops,
			Spawns.Creature creature)
		{
			this.kind = kind;
			this.name = name;
			this.key = normalize(name);
			this.note = note;
			this.points = points;
			this.shops = shops;
			this.creature = creature;
		}
	}

	@Value
	static class Layout
	{
		Rectangle box;
		Rectangle clear;
		/** The results under the box; empty when none show. */
		Rectangle results;
	}

	enum Part
	{
		OUTSIDE,
		BOX,
		CLEAR,
		RESULT
	}

	@Value
	static class Hit
	{
		Part part;
		int index;
	}

	private volatile List<Entry> places = Collections.emptyList();
	private volatile List<Entry> index = Collections.emptyList();
	private volatile int indexedLabels = -1;
	private volatile boolean focused;
	private volatile String query = "";
	private volatile List<Entry> results = Collections.emptyList();
	private volatile int highlighted;

	/** Reads the entrances, fishing spots and hunter areas from the plugin's resources. Off the client thread. */
	void load()
	{
		List<Entry> out = new ArrayList<>();
		try (InputStream in = MapSearch.class.getResourceAsStream("places.tsv"))
		{
			if (in != null)
			{
				out.addAll(parsePlaces(in));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the places", e);
		}
		try (InputStream in = MapSearch.class.getResourceAsStream("fishing-hunter.tsv"))
		{
			if (in != null)
			{
				out.addAll(parseSkilling(in));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the fishing and hunter spots", e);
		}
		places = Collections.unmodifiableList(out);
	}

	/**
	 * Fishing spots by what they catch ("Lobster fishing spots": every spot with lobsters) and hunter areas by
	 * creature ("Chinchompa hunting", with its Hunter level).
	 */
	static List<Entry> parseSkilling(InputStream in) throws IOException
	{
		Map<String, List<WorldPoint>> fishing = new java.util.TreeMap<>();
		Map<String, List<WorldPoint>> hunting = new java.util.TreeMap<>();
		Map<String, String> levels = new LinkedHashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t");
			if ("F".equals(f[0]))
			{
				List<WorldPoint> tiles = new ArrayList<>();
				for (String tile : f[3].split(";"))
				{
					String[] xyz = tile.split(",");
					tiles.add(Prifddinas.toSurface(new WorldPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
						Integer.parseInt(xyz[2]))));
				}
				for (String fish : f[2].split(", "))
				{
					fishing.computeIfAbsent(fish, k -> new ArrayList<>()).addAll(tiles);
				}
			}
			else if ("H".equals(f[0]))
			{
				String[] xyz = f[4].split(",");
				hunting.computeIfAbsent(f[2], k -> new ArrayList<>()).add(Prifddinas.toSurface(new WorldPoint(
					Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]))));
				levels.put(f[2], f[3]);
			}
		}
		List<Entry> out = new ArrayList<>();
		fishing.forEach((fish, tiles) -> out.add(new Entry(Kind.FISHING, fish + " fishing spots", Kind.FISHING.label,
			tiles, Collections.emptyList(), null)));
		hunting.forEach((creature, tiles) -> out.add(new Entry(Kind.HUNTER, creature + " hunting",
			"Lvl " + levels.get(creature) + " Hunter", tiles, Collections.emptyList(), null)));
		return out;
	}

	static List<Entry> parsePlaces(InputStream in) throws IOException
	{
		Map<String, Entry> out = new LinkedHashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t");
			Kind kind = "G".equals(f[0]) ? Kind.MINIGAME : Kind.DUNGEON;
			WorldPoint at = Prifddinas.toSurface(new WorldPoint(Integer.parseInt(f[2]), Integer.parseInt(f[3]),
				Integer.parseInt(f[4])));
			Entry known = out.get(kind + f[1]);
			List<WorldPoint> points = new ArrayList<>(known == null ? Collections.emptyList() : known.getPoints());
			points.add(at);
			out.put(kind + f[1], new Entry(kind, f[1], kind.label, points, Collections.emptyList(), null));
		}
		return new ArrayList<>(out.values());
	}

	/**
	 * Everything searchable: the game's place names, the entrances, shops, items with their sellers, monsters and
	 * NPCs. Built again when the place names change (they are read from the game after start-up).
	 */
	List<Entry> index(List<MapLabels.Label> labels)
	{
		if (labels.size() == indexedLabels)
		{
			return index;
		}
		List<Entry> out = new ArrayList<>(build(labels));
		index = Collections.unmodifiableList(out);
		indexedLabels = labels.size();
		return index;
	}

	/** {@link #index(List)} and {@code extra} (the player's markers, the kinds of map icon found so far). */
	List<Entry> index(List<MapLabels.Label> labels, List<Entry> extra)
	{
		List<Entry> base = index(labels);
		if (extra.isEmpty())
		{
			return base;
		}
		List<Entry> out = new ArrayList<>(base.size() + extra.size());
		out.addAll(base);
		out.addAll(extra);
		return out;
	}

	private List<Entry> build(List<MapLabels.Label> labels)
	{
		Map<String, List<WorldPoint>> names = new LinkedHashMap<>();
		for (MapLabels.Label label : labels)
		{
			names.computeIfAbsent(String.join(" ", label.getLines()), k -> new ArrayList<>())
				.add(new WorldPoint(label.getX(), label.getY(), 0));
		}
		List<Entry> out = new ArrayList<>();
		names.forEach((name, at) -> out.add(new Entry(Kind.PLACE, name, Kind.PLACE.label, at, Collections.emptyList(),
			null)));
		out.addAll(places);
		Map<String, List<Shops.Shop>> sellers = new LinkedHashMap<>();
		Map<String, String> prices = new LinkedHashMap<>();
		for (Shops.Shop shop : Shops.all())
		{
			out.add(new Entry(Kind.SHOP, shop.getName(), Kind.SHOP.label, Collections.singletonList(shop.getPoint()),
				Collections.singletonList(shop), null));
			for (Shops.Item item : shop.getItems())
			{
				sellers.computeIfAbsent(item.getName(), k -> new ArrayList<>()).add(shop);
				prices.putIfAbsent(item.getName(), item.priceText());
			}
		}
		sellers.forEach((item, shops) ->
		{
			List<WorldPoint> at = new ArrayList<>();
			shops.forEach(shop -> at.add(shop.getPoint()));
			String note = shops.size() == 1 ? prices.get(item) : shops.size() + " shops";
			out.add(new Entry(Kind.ITEM, item, note, at, shops, null));
		});
		for (Spawns.Creature creature : Spawns.all())
		{
			Kind kind = creature.isMonster() ? Kind.MONSTER : Kind.NPC;
			String note = creature.getLevels().isEmpty() ? kind.label : "Lvl " + creature.getLevels();
			out.add(new Entry(kind, creature.getName(), note, creature.getTiles(), Collections.emptyList(), creature));
		}
		for (Transports.Destination ring : Transports.network("Fairy ring"))
		{
			// "Fairy ring CKS", noted with the place it is at
			out.add(new Entry(Kind.FAIRY_RING, "Fairy ring " + ringName(ring.label()),
				SearchSpots.name(ring.getPoint(), labels), Collections.singletonList(ring.getPoint()),
				Collections.emptyList(), null));
		}
		return out;
	}

	static List<Entry> search(List<Entry> entries, String query)
	{
		return search(entries, query, null);
	}

	/**
	 * The best matches for a query, at most {@link #MAX_RESULTS}: by how well they match (whole name, start, words,
	 * initials, loosely), and among as good matches the nearest to {@code from} first (when known), then by kind,
	 * then shortest.
	 */
	static List<Entry> search(List<Entry> entries, String query, WorldPoint from)
	{
		String wanted = normalize(query);
		if (wanted.isEmpty())
		{
			return Collections.emptyList();
		}
		List<Object[]> scored = new ArrayList<>();
		for (Entry entry : entries)
		{
			int score = score(wanted, entry.getKey());
			if (score > 0)
			{
				scored.add(new Object[]{entry, score});
			}
		}
		Comparator<Object[]> order = Comparator.<Object[]>comparingInt(s -> -(Integer) s[1] / 1000);
		if (from != null)
		{
			Map<Entry, Integer> distances = new java.util.IdentityHashMap<>();
			order = order.thenComparingInt(s -> distances.computeIfAbsent((Entry) s[0], e -> nearest(e, from)));
		}
		scored.sort(order.thenComparingInt(s -> -(Integer) s[1])
			.thenComparingInt(s -> ((Entry) s[0]).getKind().ordinal())
			.thenComparingInt(s -> ((Entry) s[0]).getName().length()));
		List<Entry> out = new ArrayList<>();
		for (int i = 0; i < scored.size() && out.size() < MAX_RESULTS; i++)
		{
			out.add((Entry) scored.get(i)[0]);
		}
		return out;
	}

	/** A ring's codes as written ("CKS", "AIR / DLR"); a named one as a name ("ZANARIS" as "Zanaris"). */
	static String ringName(String label)
	{
		List<String> parts = new ArrayList<>();
		for (String part : label.split(", "))
		{
			parts.add(part.length() == 3 ? part : part.charAt(0) + part.substring(1).toLowerCase(Locale.ROOT));
		}
		return String.join(" / ", parts);
	}

	/** How far an entry's nearest place is from a tile ({@link SearchSpots#distance}). */
	static int nearest(Entry entry, WorldPoint from)
	{
		int best = Integer.MAX_VALUE;
		for (WorldPoint point : entry.getPoints())
		{
			best = Math.min(best, SearchSpots.distance(point, from));
		}
		return best;
	}

		/** Lower case, apostrophes dropped, anything else that is not a letter or digit a single space. */
	static String normalize(String text)
	{
		return text.toLowerCase(Locale.ROOT).replace("'", "").replaceAll("[^a-z0-9]+", " ").trim();
	}

	/**
	 * How well a name matches a query, both {@link #normalize normalized}; 0 for no match. Best first: the whole name;
	 * its start; every query word starting or inside a word of the name ("d scim" finds "dragon scimitar"); the
	 * name's initials ("kbd"); every query word matching a word's letters in order, or its start but the last letter
	 * ("dragon sim", "anchovy" for "anchovies"); the query's letters close together in order anywhere. Shorter names
	 * win ties within a kind of match.
	 */
	static int score(String query, String name)
	{
		// a name of no English letters or digits at all (Cyrillic, or only symbols) normalizes to nothing: never a match
		if (name.isEmpty() || query.isEmpty())
		{
			return 0;
		}
		if (name.equals(query))
		{
			return 10000;
		}
		if (name.startsWith(query))
		{
			return 9000 - name.length();
		}
		String[] words = name.split(" ");
		int total = 0;
		int worst = 10;
		for (String token : query.split(" "))
		{
			int best = 0;
			for (String word : words)
			{
				best = Math.max(best, wordScore(token, word));
			}
			total += best;
			worst = Math.min(worst, best);
			if (best == 0)
			{
				break;
			}
		}
		if (worst >= 6)
		{
			return 6000 + total * 20 - name.length();
		}
		String compact = query.replace(" ", "");
		StringBuilder initials = new StringBuilder();
		for (String word : words)
		{
			initials.append(word.charAt(0));
		}
		if (compact.length() >= 2 && initials.toString().startsWith(compact))
		{
			return 5000 - name.length();
		}
		if (worst > 0)
		{
			return 3000 + total * 20 - name.length();
		}
		if (compact.length() >= 4 && closeInOrder(compact, name.replace(" ", "")))
		{
			return 1000 - name.length();
		}
		return 0;
	}

	/** How well one query word matches one word of a name: its start 10, inside it 6, loosely 3, else 0. */
	private static int wordScore(String token, String word)
	{
		if (word.startsWith(token))
		{
			return 10;
		}
		if (word.contains(token))
		{
			return 6;
		}
		if (token.length() >= 5 && word.startsWith(token.substring(0, token.length() - 1)))
		{
			return 3;
		}
		if (token.length() >= 3 && word.charAt(0) == token.charAt(0) && inOrder(token, word))
		{
			return 3;
		}
		// one letter wrong, missing or extra in a longer word: "zenaris" for "zanaris"
		return token.length() >= 5 && oneEdit(token, word) ? 3 : 0;
	}

	/** True when two words differ by one letter changed, added or left out. */
	static boolean oneEdit(String a, String b)
	{
		if (Math.abs(a.length() - b.length()) > 1)
		{
			return false;
		}
		String shorter = a.length() <= b.length() ? a : b;
		String longer = shorter == a ? b : a;
		int i = 0;
		while (i < shorter.length() && shorter.charAt(i) == longer.charAt(i))
		{
			i++;
		}
		// the rest matches once the first difference is skipped: in both (a changed letter), or in the longer only
		return shorter.length() == longer.length()
			? shorter.substring(Math.min(i + 1, shorter.length())).equals(longer.substring(Math.min(i + 1, longer.length())))
			: shorter.substring(i).equals(longer.substring(i + 1));
	}

	/** True when every letter of {@code part} appears in {@code whole} in the same order. */
	static boolean inOrder(String part, String whole)
	{
		int at = 0;
		for (int i = 0; i < whole.length() && at < part.length(); i++)
		{
			if (whole.charAt(i) == part.charAt(at))
			{
				at++;
			}
		}
		return at == part.length();
	}

	/** True when {@code part}'s letters appear in order within a stretch of {@code whole} at most twice as long. */
	static boolean closeInOrder(String part, String whole)
	{
		for (int start = whole.indexOf(part.charAt(0)); start >= 0; start = whole.indexOf(part.charAt(0), start + 1))
		{
			int at = 0;
			int i = start;
			for (; i < whole.length() && at < part.length(); i++)
			{
				if (whole.charAt(i) == part.charAt(at))
				{
					at++;
				}
			}
			if (at == part.length() && i - start <= part.length() * 2)
			{
				return true;
			}
		}
		return false;
	}

	boolean isFocused()
	{
		return focused;
	}

	void setFocused(boolean focused)
	{
		this.focused = focused;
	}

	String query()
	{
		return query;
	}

	List<Entry> results()
	{
		return focused ? results : Collections.<Entry>emptyList();
	}

	int highlighted()
	{
		return highlighted;
	}

	void type(char c, List<Entry> entries, WorldPoint from)
	{
		setQuery(query + c, entries, from);
	}

	void backspace(List<Entry> entries, WorldPoint from)
	{
		String now = query;
		setQuery(now.isEmpty() ? now : now.substring(0, now.length() - 1), entries, from);
	}

	void clear()
	{
		query = "";
		results = Collections.emptyList();
		highlighted = 0;
	}

	private void setQuery(String text, List<Entry> entries, WorldPoint from)
	{
		query = text;
		results = search(entries, text, from);
		highlighted = 0;
	}

	/** Moves the lit result up or down, kept within the results. */
	void move(int delta)
	{
		int count = results.size();
		highlighted = count == 0 ? 0 : Math.max(0, Math.min(count - 1, highlighted + delta));
	}

	/** The result Enter chooses: the lit one, or null when there is none. */
	Entry chosen()
	{
		List<Entry> shown = results();
		return shown.isEmpty() ? null : shown.get(Math.min(highlighted, shown.size() - 1));
	}

	/**
	 * The box at the top, between the left and right buttons (on a map too narrow for that, under the left ones), and
	 * the results under it.
	 */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int rows)
	{
		Rectangle icons = buttons.get(MapPainter.Button.ICONS);
		Rectangle filters = buttons.get(MapPainter.Button.FILTERS);
		Rectangle mode = buttons.get(MapPainter.Button.MODE);
		int left = filters.x + filters.width + GAP;
		int right = mode.x - GAP;
		Rectangle box;
		if (right - left >= MIN_WIDTH)
		{
			int boxWidth = Math.min(WIDTH, right - left);
			int x = Math.max(left, Math.min(width / 2 - boxWidth / 2, right - boxWidth));
			box = new Rectangle(x, filters.y, boxWidth, filters.height);
		}
		else
		{
			box = new Rectangle(icons.x, filters.y + filters.height + GAP, Math.min(WIDTH, width - icons.x * 2),
				filters.height);
		}
		int x = box.x;
		int boxWidth = box.width;
		Rectangle clear = new Rectangle(box.x + box.width - PAD - CLEAR, box.y + (box.height - CLEAR) / 2, CLEAR, CLEAR);
		Rectangle results = new Rectangle(x, box.y + box.height + 2, boxWidth, rows * ROW + (rows > 0 ? 4 : 0));
		return new Layout(box, clear, results);
	}

	Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width)
	{
		return layout(buttons, width, results().size());
	}

	Hit hitAt(int x, int y, Layout layout)
	{
		if (!query.isEmpty() && layout.getClear().contains(x, y))
		{
			return new Hit(Part.CLEAR, -1);
		}
		if (layout.getBox().contains(x, y))
		{
			return new Hit(Part.BOX, -1);
		}
		Rectangle list = layout.getResults();
		if (list.height > 0 && list.contains(x, y))
		{
			int index = (y - list.y - 2) / ROW;
			return new Hit(Part.RESULT, Math.max(0, Math.min(results().size() - 1, index)));
		}
		return new Hit(Part.OUTSIDE, -1);
	}

	void draw(Graphics2D g, Layout layout, Point mouse)
	{
		Rectangle box = layout.getBox();
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(FIELD);
		g.fillRoundRect(box.x, box.y, box.width, box.height, 8, 8);
		g.setColor(focused ? FOCUS : EDGE);
		g.drawRoundRect(box.x, box.y, box.width - 1, box.height - 1, 8, 8);
		// a magnifying glass
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(1.6f));
		g.setColor(QUIET);
		int cx = box.x + PAD + 5;
		int cy = box.y + box.height / 2 - 1;
		g.drawOval(cx - 5, cy - 5, 9, 9);
		g.drawLine(cx + 3, cy + 3, cx + 7, cy + 7);
		g.setStroke(stroke);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		int textX = box.x + PAD + 18;
		int baseline = box.y + (box.height + metrics.getAscent() - metrics.getDescent()) / 2;
		String text = query;
		int room = layout.getClear().x - textX - 4;
		if (text.isEmpty() && !focused)
		{
			g.setColor(QUIET);
			g.drawString(SideList.fit("Search places, shops, items, monsters, fairy rings", metrics, room), textX, baseline);
		}
		else
		{
			String shown = text + (focused ? "|" : "");
			while (metrics.stringWidth(shown) > room && shown.length() > 1)
			{
				// the end of a long query stays in view
				shown = shown.substring(1);
			}
			g.setColor(TEXT);
			g.drawString(shown, textX, baseline);
		}
		if (!text.isEmpty())
		{
			Rectangle clear = layout.getClear();
			g.setColor(QUIET);
			g.drawLine(clear.x + 3, clear.y + 3, clear.x + clear.width - 4, clear.y + clear.height - 4);
			g.drawLine(clear.x + clear.width - 4, clear.y + 3, clear.x + 3, clear.y + clear.height - 4);
		}

		List<Entry> shown = results();
		Rectangle list = layout.getResults();
		if (!shown.isEmpty())
		{
			g.setColor(PANEL);
			g.fillRoundRect(list.x, list.y, list.width, list.height, 8, 8);
			g.setColor(EDGE);
			g.drawRoundRect(list.x, list.y, list.width - 1, list.height - 1, 8, 8);
			int y = list.y + 2;
			for (int i = 0; i < shown.size(); i++)
			{
				Entry entry = shown.get(i);
				boolean lit = i == highlighted || mouse != null && list.contains(mouse) && mouse.y >= y
					&& mouse.y < y + ROW;
				if (lit)
				{
					g.setColor(LIT);
					g.fillRect(list.x + 2, y, list.width - 4, ROW);
				}
				int rowBaseline = y + (ROW + metrics.getAscent() - metrics.getDescent()) / 2;
				String note = entry.getNote();
				int noteWidth = metrics.stringWidth(note) + PAD;
				g.setColor(QUIET);
				g.drawString(note, list.x + list.width - PAD - metrics.stringWidth(note), rowBaseline);
				g.setColor(TEXT);
				g.drawString(SideList.fit(entry.getName(), metrics, list.width - PAD * 2 - noteWidth), list.x + PAD,
					rowBaseline);
				y += ROW;
			}
		}
		else if (focused && !text.isEmpty())
		{
			g.setColor(PANEL);
			g.fillRoundRect(list.x, list.y, list.width, ROW + 4, 8, 8);
			g.setColor(QUIET);
			g.drawString("Nothing found", list.x + PAD, list.y + 2 + (ROW + metrics.getAscent()
				- metrics.getDescent()) / 2);
		}
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}
}
