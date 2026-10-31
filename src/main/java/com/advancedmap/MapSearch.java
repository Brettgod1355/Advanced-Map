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
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
	/** With less room than this between the top buttons at their full width, they get shorter to make room. */
	static final int MIN_WIDTH = 160;
	/** The narrowest the box gets on the top row: the glass, the "Search in" lines and a few letters. */
	private static final int SMALLEST = 100;
	static final int ROW = 22;
	/** The most results of one group listed; the list scrolls when they do not all fit. */
	static final int MAX_RESULTS = 50;
	/** Room kept under the results for the bar at the bottom of the map. */
	private static final int BOTTOM_ROOM = 30;
	/** The height of a group's heading in the results ("Places", "Monsters"). */
	static final int HEADING_ROW = 16;
	/** The shortest a row of the "Search in" list gets on a short map: the small font and a little room. */
	private static final int SCOPE_ROW_MIN = 14;

	/** The groups the results are listed in, in this order, each under its heading. */
	enum Group
	{
		PLACES("Places"),
		MONSTERS("Monsters"),
		ITEMS("Items"),
		OTHER("Other");

		final String label;

		Group(String label)
		{
			this.label = label;
		}
	}

	/** The group a result is listed in. */
	static Group groupOf(Entry entry)
	{
		return groupOf(entry.getKind());
	}

	/** The group a kind of result is listed in: NPCs (characters that cannot be attacked) under Other, not Monsters. */
	static Group groupOf(Kind kind)
	{
		switch (kind)
		{
			case PLACE:
			case DUNGEON:
			case MINIGAME:
			case MARKER:
			case ICON:
			case FAIRY_RING:
				return Group.PLACES;
			case MONSTER:
				return Group.MONSTERS;
			case ITEM:
			case GROUND_ITEM:
				return Group.ITEMS;
			default:
				return Group.OTHER;
		}
	}
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
	private static final Color HEADING = new Color(0xA08C64);

	/** What the search looks in: each turned off and on in the "Search in" list behind the lines in the box. */
	enum Scope
	{
		PLACES("Places"),
		DUNGEONS("Dungeons and minigames"),
		ICONS("Map icons"),
		FAIRY_RINGS("Fairy rings"),
		SHOPS("Shops and what they sell"),
		GROUND_ITEMS("Items on the ground"),
		MONSTERS("Monsters"),
		NPCS("NPCs"),
		SKILLING("Fishing and hunter spots"),
		MARKERS("Your markers");

		final String label;

		Scope(String label)
		{
			this.label = label;
		}

		/** The scopes turned off, as saved ("MONSTERS;NPCS"); names no longer known are left out. */
		static Set<Scope> parse(String saved)
		{
			Set<Scope> out = EnumSet.noneOf(Scope.class);
			for (String name : MapSettings.parseHidden(saved))
			{
				for (Scope scope : values())
				{
					if (scope.name().equals(name))
					{
						out.add(scope);
					}
				}
			}
			return Collections.unmodifiableSet(out);
		}

		static String save(Set<Scope> off)
		{
			return off.stream().map(Scope::name).collect(Collectors.joining(MapSettings.SEPARATOR));
		}
	}

	enum Kind
	{
		PLACE("Place", Scope.PLACES),
		MARKER("Your marker", Scope.MARKERS),
		DUNGEON("Dungeon", Scope.DUNGEONS),
		MINIGAME("Minigame", Scope.DUNGEONS),
		FAIRY_RING("Fairy ring", Scope.FAIRY_RINGS),
		ICON("Map icon", Scope.ICONS),
		SHOP("Shop", Scope.SHOPS),
		ITEM("Item", Scope.SHOPS),
		GROUND_ITEM("On the ground", Scope.GROUND_ITEMS),
		MONSTER("Monster", Scope.MONSTERS),
		NPC("NPC", Scope.NPCS),
		FISHING("Fishing", Scope.SKILLING),
		HUNTER("Hunter", Scope.SKILLING);

		final String label;
		final Scope scope;

		Kind(String label, Scope scope)
		{
			this.label = label;
			this.scope = scope;
		}
	}

	/**
	 * Something to find: a place, a shop, an item (with its shops, or where it lies on the ground), a monster or NPC
	 * (with its spawns).
	 */
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
		/** A monster, an NPC or an item on the ground, with its spawns; null otherwise. */
		Spawns.Creature creature;
		/**
		 * How big a place it is, for listing places first and the bigger first: a kingdom 3, a town 2, a smaller place,
		 * a dungeon or a marker 1 (from the size the game's map writes its name at); 0 for anything not a place.
		 */
		int importance;

		Entry(Kind kind, String name, String note, List<WorldPoint> points, List<Shops.Shop> shops,
			Spawns.Creature creature)
		{
			this(kind, name, note, points, shops, creature, kind == Kind.PLACE ? MapLabels.TOWN + 1
				: kind == Kind.DUNGEON || kind == Kind.MINIGAME || kind == Kind.MARKER ? 1 : 0);
		}

		Entry(Kind kind, String name, String note, List<WorldPoint> points, List<Shops.Shop> shops,
			Spawns.Creature creature, int importance)
		{
			this.kind = kind;
			this.name = name;
			this.key = normalize(name);
			this.note = note;
			this.points = points;
			this.shops = shops;
			this.creature = creature;
			this.importance = importance;
		}
	}

	@Value
	static class Layout
	{
		Rectangle box;
		Rectangle clear;
		/** The results under the box, or the "Search in" list when it is open; empty when neither shows. */
		Rectangle results;
		/** The lines that open the "Search in" list, beside the magnifying glass. */
		Rectangle scopes;
	}

	enum Part
	{
		OUTSIDE,
		BOX,
		CLEAR,
		RESULT,
		/** The lines that open and close the "Search in" list. */
		SCOPES,
		/** A row of the "Search in" list: index 0 "Search everything", then each {@link Scope}. */
		SCOPE
	}

	@Value
	static class Hit
	{
		Part part;
		int index;
	}

	private volatile List<Entry> places = Collections.emptyList();
	private static volatile Map<String, java.util.Set<String>> nicknames = Collections.emptyMap();
	private volatile List<Entry> index = Collections.emptyList();
	private volatile int indexedLabels = -1;
	/** How many loads have finished; the index is cached against it too, so a build that straddled a load is not kept. */
	private volatile int loads;
	private volatile int indexedLoads = -1;
	private volatile boolean focused;
	/** The "Search in" list is open, in place of the results. */
	private volatile boolean scopesOpen;
	/** What the search does not look in. */
	private volatile Set<Scope> skipped = Collections.emptySet();
	private volatile String query = "";
	private volatile List<Entry> results = Collections.emptyList();
	private volatile int highlighted;
	/** How far the results are scrolled, in pixels. */
	private volatile int scroll;
	/** How tall the results' list was last laid out. */
	private volatile int listHeight = Integer.MAX_VALUE;

	/** The named entrances, fishing spots and hunter areas, as {@link #load} read them. */
	List<Entry> places()
	{
		return places;
	}

	/** Reads the entrances, fishing spots and hunter areas from the plugin's resources. Off the client thread. */
	void load()
	{
		List<Entry> out = new ArrayList<>();
		try (InputStream in = MapSearch.class.getResourceAsStream("places.tsv"))
		{
			if (in != null)
			{
				List<Entry> icons = new ArrayList<>();
				List<Entry> read = parsePlaces(in, icons);
				out.addAll(read);
				minigames = read.stream().filter(entry -> entry.getKind() == Kind.MINIGAME).collect(Collectors.toList());
				minigameIcons = Collections.unmodifiableList(icons);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the places", e);
		}
		// RuneLite's list, then the hunter areas it lacks from the wiki: read as one, so each creature is one result
		try (InputStream runelite = MapSearch.class.getResourceAsStream("fishing-hunter.tsv");
			InputStream wiki = MapSearch.class.getResourceAsStream("hunter-wiki.tsv");
			InputStream in = runelite == null || wiki == null ? runelite : new java.io.SequenceInputStream(runelite, wiki))
		{
			if (in != null)
			{
				List<SkillSpot> spots = new ArrayList<>();
				out.addAll(parseSkilling(in, spots));
				skillSpots = Collections.unmodifiableList(spots);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the fishing and hunter spots", e);
		}
		places = Collections.unmodifiableList(out);
		try (InputStream in = MapSearch.class.getResourceAsStream("nicknames.tsv"))
		{
			if (in != null)
			{
				nicknames = parseNicknames(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the place nicknames", e);
		}
		// an index built before or while this ran (a search typed as the plugin started) lacks the places, shops and
		// monsters: the next index() builds again
		loads++;
	}

	/** Nicknames ("ardy") and the places they stand for, both {@link #normalize normalized}. */
	static Map<String, java.util.Set<String>> parseNicknames(InputStream in) throws IOException
	{
		Map<String, java.util.Set<String>> out = new java.util.HashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t");
			java.util.Set<String> places = new java.util.HashSet<>();
			for (String place : f[1].split(";"))
			{
				places.add(normalize(place));
			}
			out.put(normalize(f[0]), places);
		}
		return out;
	}

	/**
	 * Fishing spots by what they catch ("Lobster fishing spots": every spot with lobsters) and hunter areas by
	 * creature ("Chinchompa hunting", with its Hunter level).
	 */
	static List<Entry> parseSkilling(InputStream in) throws IOException
	{
		return parseSkilling(in, new ArrayList<>());
	}

	/** A fishing spot and what it catches, or a hunter area, its creature and level: for the game's icon there. */
	@Value
	static class SkillSpot
	{
		boolean fishing;
		List<String> what;
		int level;
		List<WorldPoint> tiles;
	}

	/** How far from the game's icon a fishing spot or hunter area of the list may be. */
	static final int SKILL_REACH = 12;
	private static volatile List<SkillSpot> skillSpots = Collections.emptyList();

	/** The fishing spot (or hunter area) of the list nearest a game icon at a tile, or null when none is near. */
	static SkillSpot skillNear(boolean fishing, int x, int y, int plane)
	{
		SkillSpot best = null;
		int bestDistance = SKILL_REACH + 1;
		for (SkillSpot spot : skillSpots)
		{
			for (WorldPoint tile : spot.tiles)
			{
				int distance = Math.max(Math.abs(tile.getX() - x), Math.abs(tile.getY() - y));
				if (spot.fishing == fishing && tile.getPlane() == plane && distance < bestDistance)
				{
					best = spot;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/** As above, and each fishing spot and hunter area goes in {@code spots} too. */
	static List<Entry> parseSkilling(InputStream in, List<SkillSpot> spots) throws IOException
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
				spots.add(new SkillSpot(true, Arrays.asList(f[2].split(", ")), 0, tiles));
			}
			else if ("H".equals(f[0]))
			{
				String[] xyz = f[4].split(",");
				WorldPoint tile = Prifddinas.toSurface(new WorldPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
					Integer.parseInt(xyz[2])));
				hunting.computeIfAbsent(f[2], k -> new ArrayList<>()).add(tile);
				levels.put(f[2], f[3]);
				spots.add(new SkillSpot(false, Collections.singletonList(f[2]), Integer.parseInt(f[3]),
					Collections.singletonList(tile)));
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
		return parsePlaces(in, new ArrayList<>());
	}

	/** How far from the game's minigame icon the minigame's listed spot may be, for the icon's name. */
	static final int MINIGAME_REACH = 12;
	private static volatile List<Entry> minigames = Collections.emptyList();
	private static volatile List<Entry> minigameIcons = Collections.emptyList();

	/**
	 * The name of the minigame listed nearest a minigame icon of the game's, or null when none is near. On any floor:
	 * a list may put the way in a floor up from the icon.
	 */
	static String minigameNear(int x, int y)
	{
		String best = null;
		int bestDistance = MINIGAME_REACH + 1;
		for (Entry minigame : minigames)
		{
			for (WorldPoint at : minigame.getPoints())
			{
				int distance = Math.max(Math.abs(at.getX() - x), Math.abs(at.getY() - y));
				if (distance < bestDistance)
				{
					best = minigame.getName();
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/** The minigames the game's map has no icon for, which get one of Advanced Map's ("icon" in places.tsv). */
	static List<Entry> minigameIcons()
	{
		return minigameIcons;
	}

	/** As above; the minigames marked to get an icon go in {@code icons} too. */
	static List<Entry> parsePlaces(InputStream in, List<Entry> icons) throws IOException
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
			if (f.length > 5 && "icon".equals(f[5]))
			{
				icons.add(new Entry(kind, f[1], kind.label, Collections.singletonList(at), Collections.emptyList(), null));
			}
		}
		return new ArrayList<>(out.values());
	}

	/**
	 * Everything searchable: the game's place names, the entrances, shops, items with their sellers, monsters and
	 * NPCs. Built again when the place names change (they are read from the game after start-up).
	 */
	List<Entry> index(List<MapLabels.Label> labels)
	{
		int loadsNow = loads;
		if (labels.size() == indexedLabels && loadsNow == indexedLoads)
		{
			return index;
		}
		List<Entry> out = new ArrayList<>(build(labels));
		index = Collections.unmodifiableList(out);
		indexedLabels = labels.size();
		indexedLoads = loadsNow;
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
		Map<String, Integer> sizes = new java.util.HashMap<>();
		for (MapLabels.Label label : labels)
		{
			String name = String.join(" ", label.getLines());
			names.computeIfAbsent(name, k -> new ArrayList<>()).add(new WorldPoint(label.getX(), label.getY(), 0));
			sizes.merge(name, label.getSize(), Math::max);
		}
		List<Entry> out = new ArrayList<>();
		// the map's names for where monsters are ("Blue dragons") are left out: the monsters themselves are found
		CreatureNames creatures = new CreatureNames(Spawns.all());
		names.forEach((name, at) ->
		{
			if (!creatures.named(name))
			{
				out.add(new Entry(Kind.PLACE, name, Kind.PLACE.label, at, Collections.emptyList(), null,
					sizes.get(name) + 1));
			}
		});
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
			Kind kind = creature.isItem() ? Kind.GROUND_ITEM : creature.isMonster() ? Kind.MONSTER : Kind.NPC;
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

	/**
	 * The names of monsters and NPCs, to tell a place name of the map that only says what is there ("Blue dragons",
	 * "Chaos druids", "Brutal black, blue & red dragons") from a place ("Goblin Village", "Dragon Forge").
	 */
	static final class CreatureNames
	{
		private final java.util.Set<String> names = new java.util.HashSet<>();
		/** By a monster name's last word ("dragon"), the other words of the names ending with it ("brutal", "black"). */
		private final Map<String, java.util.Set<String>> wordsByLast = new java.util.HashMap<>();

		CreatureNames(List<Spawns.Creature> creatures)
		{
			for (Spawns.Creature creature : creatures)
			{
				// monsters only: an NPC's spot keeps its place name ("Boot"), and some are scenery ("Iceberg")
				if (!creature.isMonster())
				{
					continue;
				}
				// "Goblin (Lumbridge farm)": the name without the wiki's version
				String name = normalize(creature.getName().replaceAll("\\s*\\(.*\\)", ""));
				if (name.isEmpty())
				{
					continue;
				}
				names.add(name);
				String[] parts = name.split(" ");
				wordsByLast.computeIfAbsent(parts[parts.length - 1], k -> new java.util.HashSet<>())
					.addAll(java.util.Arrays.asList(parts).subList(0, parts.length - 1));
			}
		}

		/**
		 * True for a place name that is a monster's or NPC's name (or its plural), or a list of them: every word a word
		 * of their names, the last a plural of a name's last word.
		 */
		boolean named(String place)
		{
			String key = normalize(place);
			if (key.isEmpty())
			{
				return false;
			}
			String[] parts = key.split(" ");
			String head = key.substring(0, key.length() - parts[parts.length - 1].length());
			for (String last : singulars(parts[parts.length - 1]))
			{
				if (names.contains(head + last))
				{
					return true;
				}
				// a list ("Brutal black, blue & red dragons"): a plural, and every other word one of those monsters'
				java.util.Set<String> others = last.equals(parts[parts.length - 1]) ? null : wordsByLast.get(last);
				if (others != null && parts.length > 1
					&& others.containsAll(java.util.Arrays.asList(parts).subList(0, parts.length - 1)))
				{
					return true;
				}
			}
			return false;
		}

		/** A word and what it could be the plural of: "dragons" dragon, "dwarves" dwarf, "axes" axe or ax. */
		static List<String> singulars(String word)
		{
			List<String> out = new ArrayList<>();
			out.add(word);
			if (word.endsWith("ies") && word.length() > 3)
			{
				out.add(word.substring(0, word.length() - 3) + "y");
			}
			if (word.endsWith("ves") && word.length() > 3)
			{
				out.add(word.substring(0, word.length() - 3) + "f");
			}
			if (word.endsWith("es") && word.length() > 2)
			{
				out.add(word.substring(0, word.length() - 2));
			}
			if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 1)
			{
				out.add(word.substring(0, word.length() - 1));
			}
			return out;
		}
	}

	static List<Entry> search(List<Entry> entries, String query)
	{
		return search(entries, query, null);
	}

	/**
	 * The best matches for a query, by group (places, monsters, items, the rest), at most {@link #MAX_RESULTS} a
	 * group. Within a group: first whatever is named exactly what was typed (or by
	 * a nickname typed, "ardy"); then the places with a word starting with each word typed, the bigger first (a kingdom,
	 * a town, then a smaller place, a dungeon, a marker), so "var" lists Varrock and Varlamore before Varrock armour; then
	 * everything else by how well it matches (start, words, initials, loosely). Among equals the nearest to {@code from}
	 * (when known) first, then the better score, the kind, the shorter name.
	 */
	static List<Entry> search(List<Entry> entries, String query, WorldPoint from)
	{
		String wanted = normalize(query);
		if (wanted.isEmpty())
		{
			return Collections.emptyList();
		}
		java.util.Set<String> nicknamed = nicknames.getOrDefault(wanted, Collections.emptySet());
		List<Ranked> ranked = new ArrayList<>();
		for (Entry entry : entries)
		{
			int score = score(wanted, entry.getKey());
			if (nicknamed.contains(entry.getKey()))
			{
				score = Math.max(score, NICKNAME_SCORE);
			}
			if (score > 0)
			{
				int group = score >= NICKNAME_SCORE ? 0 : entry.getImportance() > 0 && score >= PLACE_SCORE ? 1 : 2;
				ranked.add(new Ranked(entry, score, group));
			}
		}
		Comparator<Ranked> order = Comparator.<Ranked>comparingInt(r -> r.group)
			.thenComparingInt(r -> r.group < 2 ? -r.entry.getImportance() : 0)
			.thenComparingInt(r -> -r.score / 1000);
		if (from != null)
		{
			Map<Entry, Integer> distances = new java.util.IdentityHashMap<>();
			order = order.thenComparingInt(r -> distances.computeIfAbsent(r.entry, e -> nearest(e, from)));
		}
		ranked.sort(order.thenComparingInt(r -> -r.score)
			.thenComparingInt(r -> r.entry.getKind().ordinal())
			.thenComparingInt(r -> r.entry.getName().length()));
		// listed by group (places, monsters, items, the rest), each group best first, up to MAX_RESULTS a group: the list
		// scrolls
		Map<Group, List<Entry>> byGroup = new java.util.EnumMap<>(Group.class);
		java.util.Set<String> placesListed = new java.util.HashSet<>();
		for (Ranked match : ranked)
		{
			Entry entry = match.entry;
			// a dungeon's entrance and the game's name for it ("Ardougne Sewers" twice): the first only
			if (entry.getImportance() > 0 && entry.getKind() != Kind.MARKER && !placesListed.add(entry.getKey()))
			{
				continue;
			}
			List<Entry> group = byGroup.computeIfAbsent(groupOf(entry.getKind()), k -> new ArrayList<>());
			if (group.size() < MAX_RESULTS)
			{
				group.add(entry);
			}
		}
		List<Entry> out = new ArrayList<>();
		byGroup.values().forEach(out::addAll);
		return out;
	}

	/** A match with its score and its group in the list (0 named exactly, 1 a place, 2 anything else). */
	private static final class Ranked
	{
		final Entry entry;
		final int score;
		final int group;

		Ranked(Entry entry, int score, int group)
		{
			this.entry = entry;
			this.score = score;
			this.group = group;
		}
	}

	/** A nickname typed in full scores as the place's own name typed in full would. */
	static final int NICKNAME_SCORE = 10000;
	/** A place matching at least this well (each word typed starts a word of its name) is listed with the places. */
	static final int PLACE_SCORE = 7000;

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
	 * its start; every query word starting a word of the name ("d scim" finds "dragon scimitar"); every one starting or
	 * inside a word; the
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
		if (worst >= 10)
		{
			// every word typed starts a word of the name: "east ardougne" for "ardo"
			return 7000 + total * 20 - name.length();
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
		if (!focused)
		{
			scopesOpen = false;
		}
	}

	boolean isScopesOpen()
	{
		return scopesOpen;
	}

	void setScopesOpen(boolean open)
	{
		scopesOpen = open;
	}

	Set<Scope> skipped()
	{
		return skipped;
	}

	void setSkipped(Set<Scope> off)
	{
		skipped = Collections.unmodifiableSet(off.isEmpty() ? EnumSet.noneOf(Scope.class) : EnumSet.copyOf(off));
	}

	/** True for an entry of a kind the search looks in. */
	boolean looksIn(Entry entry)
	{
		return !skipped.contains(entry.getKind().scope);
	}

	/** The query searched again in {@code entries}, after what the search looks in changed. */
	void searchAgain(List<Entry> entries, WorldPoint from)
	{
		String now = query;
		results = search(entries, now, from);
		highlighted = 0;
		scroll = 0;
	}

	String query()
	{
		return query;
	}

	List<Entry> results()
	{
		return focused && !scopesOpen ? results : Collections.<Entry>emptyList();
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
		scopesOpen = false;
		query = "";
		results = Collections.emptyList();
		highlighted = 0;
		scroll = 0;
	}

	private void setQuery(String text, List<Entry> entries, WorldPoint from)
	{
		// typing shows what it finds
		scopesOpen = false;
		query = text;
		results = search(entries, text, from);
		highlighted = 0;
		scroll = 0;
	}

	/** Moves the lit result up or down, kept within the results. */
	void move(int delta)
	{
		int count = results.size();
		highlighted = count == 0 ? 0 : Math.max(0, Math.min(count - 1, highlighted + delta));
		if (count > 0)
		{
			// the lit result kept in view, its heading too when it is the first of its group
			int top = rowTops(results)[highlighted];
			int from = highlighted == 0 ? 0 : top - (startsGroup(results, highlighted) ? HEADING_ROW : 0);
			int room = Math.max(ROW, listHeight - 4);
			scroll = Math.max(Math.min(scroll, from), top + ROW - room);
		}
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
		return layout(buttons, width, rows, 0);
	}

	/** As above, with {@code headings} group headings among the rows. */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width, int rows, int headings)
	{
		Rectangle icons = buttons.get(MapPainter.Button.ICONS);
		Rectangle filters = buttons.get(MapPainter.Button.FILTERS);
		// after Filters, before "Show underground"
		Rectangle area = buttons.get(MapPainter.Button.AREA);
		int left = filters.x + filters.width + GAP;
		int right = area.x - GAP;
		Rectangle box;
		// on the top row between the buttons (which get shorter on a narrow map, see MapPainter.buttons), however
		// little room that leaves; under them only on a map too narrow for even a small box
		if (right - left >= SMALLEST)
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
		Rectangle scopes = new Rectangle(box.x + PAD + 16, box.y + (box.height - CLEAR) / 2, CLEAR, CLEAR);
		// as tall as the results, but no lower than the map's bottom (above its bar): a small map scrolls them
		Rectangle locate = buttons.get(MapPainter.Button.LOCATE);
		int bottom = locate.y + locate.height - BOTTOM_ROOM;
		int top = box.y + box.height + 2;
		int content = rows * ROW + headings * HEADING_ROW + (rows > 0 ? 4 : 0);
		Rectangle results = new Rectangle(x, top, boxWidth, Math.min(content, Math.max(rows > 0 ? ROW + 4 : 0,
			bottom - top)));
		return new Layout(box, clear, results, scopes);
	}

	Layout layout(Map<MapPainter.Button, Rectangle> buttons, int width)
	{
		List<Entry> shown = results();
		Layout layout = scopesOpen ? layout(buttons, width, Scope.values().length + 1, 0)
			: layout(buttons, width, shown.size(), headings(shown));
		listHeight = layout.getResults().height;
		return layout;
	}

	/** How tall all the results are, headings counted; more than the list when it scrolls. */
	private static int contentHeight(List<Entry> shown)
	{
		return shown.isEmpty() ? 0 : shown.size() * ROW + headings(shown) * HEADING_ROW + 4;
	}

	/** The mouse wheel over the results scrolls them. */
	void scroll(double notches)
	{
		int max = Math.max(0, contentHeight(results()) - listHeight);
		scroll = (int) Math.max(0, Math.min(max, scroll + Math.round(notches * ROW * 3)));
	}

	/** How many group headings a list of results shows: one where each group begins. */
	static int headings(List<Entry> shown)
	{
		int count = 0;
		for (int i = 0; i < shown.size(); i++)
		{
			count += startsGroup(shown, i) ? 1 : 0;
		}
		return count;
	}

	private static boolean startsGroup(List<Entry> shown, int i)
	{
		return i == 0 || groupOf(shown.get(i)) != groupOf(shown.get(i - 1));
	}

	/** Where each result's row begins under the top of the list, headings counted. */
	static int[] rowTops(List<Entry> shown)
	{
		int[] tops = new int[shown.size()];
		int y = 0;
		for (int i = 0; i < shown.size(); i++)
		{
			y += startsGroup(shown, i) ? HEADING_ROW : 0;
			tops[i] = y;
			y += ROW;
		}
		return tops;
	}

	Hit hitAt(int x, int y, Layout layout)
	{
		if (layout.getScopes().contains(x, y))
		{
			return new Hit(Part.SCOPES, -1);
		}
		if (!query.isEmpty() && layout.getClear().contains(x, y))
		{
			return new Hit(Part.CLEAR, -1);
		}
		Rectangle rows = layout.getResults();
		if (scopesOpen && rows.contains(x, y))
		{
			int row = (y - rows.y - 2) / scopeRow(rows);
			return row >= 0 && row <= Scope.values().length ? new Hit(Part.SCOPE, row) : new Hit(Part.BOX, -1);
		}
		if (layout.getBox().contains(x, y))
		{
			return new Hit(Part.BOX, -1);
		}
		Rectangle list = layout.getResults();
		if (list.height > 0 && list.contains(x, y))
		{
			int[] tops = rowTops(results());
			for (int i = 0; i < tops.length; i++)
			{
				int top = list.y + 2 + tops[i] - scroll;
				if (y >= top && y < top + ROW)
				{
					return new Hit(Part.RESULT, i);
				}
			}
			// on a group's heading: nothing to choose
			return new Hit(Part.BOX, -1);
		}
		return new Hit(Part.OUTSIDE, -1);
	}

	/** A group's heading in the results: its name, small and quiet, with a line to the list's edge. */
	private static void drawGroupHeading(Graphics2D g, String label, Rectangle list, int top, FontMetrics metrics)
	{
		int baseline = top + (HEADING_ROW + metrics.getAscent() - metrics.getDescent()) / 2 + 1;
		g.setColor(HEADING);
		g.drawString(label, list.x + PAD, baseline);
		int lineX = list.x + PAD + metrics.stringWidth(label) + 6;
		g.fillRect(lineX, baseline - metrics.getAscent() / 2 + 1, list.x + list.width - PAD - lineX, 1);
	}

	/** How tall a row of the "Search in" list is: shorter on a map too short for all of them at full height. */
	static int scopeRow(Rectangle list)
	{
		return Math.max(SCOPE_ROW_MIN, Math.min(ROW, (list.height - 4) / (Scope.values().length + 1)));
	}

	/** The "Search in" list: "Search everything", then a box to tick for each kind of result. */
	private void drawScopes(Graphics2D g, Rectangle list, Point mouse, FontMetrics metrics)
	{
		g.setColor(PANEL);
		g.fillRoundRect(list.x, list.y, list.width, list.height, 8, 8);
		g.setColor(EDGE);
		g.drawRoundRect(list.x, list.y, list.width - 1, list.height - 1, 8, 8);
		Scope[] scopes = Scope.values();
		int height = scopeRow(list);
		Shape clip = g.getClip();
		g.clip(list);
		for (int row = 0; row <= scopes.length; row++)
		{
			int y = list.y + 2 + row * height;
			if (mouse != null && list.contains(mouse) && mouse.y >= y && mouse.y < y + height)
			{
				g.setColor(LIT);
				g.fillRect(list.x + 2, y, list.width - 4, height);
			}
			int baseline = y + (height + metrics.getAscent() - metrics.getDescent()) / 2;
			if (row == 0)
			{
				// quiet while it would change nothing
				g.setColor(skipped.isEmpty() ? QUIET : TEXT);
				g.drawString("Search everything", list.x + PAD, baseline);
				continue;
			}
			Scope scope = scopes[row - 1];
			int box = y + (height - 10) / 2;
			g.setColor(EDGE);
			g.drawRect(list.x + PAD, box, 10, 10);
			if (!skipped.contains(scope))
			{
				g.setColor(FOCUS);
				g.fillRect(list.x + PAD + 3, box + 3, 5, 5);
			}
			g.setColor(skipped.contains(scope) ? QUIET : TEXT);
			g.drawString(SideList.fit(scope.label, metrics, list.width - PAD * 2 - 16), list.x + PAD + 16, baseline);
		}
		g.setClip(clip);
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
		// three lines, each shorter: the "Search in" list; lit while it is open or something is left out
		Rectangle lines = layout.getScopes();
		g.setColor(scopesOpen || !skipped.isEmpty() ? FOCUS : QUIET);
		for (int i = 0; i < 3; i++)
		{
			g.fillRect(lines.x + 1 + i * 2, lines.y + 3 + i * 4, lines.width - 2 - i * 4, 2);
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		int textX = layout.getScopes().x + layout.getScopes().width + 6;
		int baseline = box.y + (box.height + metrics.getAscent() - metrics.getDescent()) / 2;
		String text = query;
		int room = layout.getClear().x - textX - 4;
		if (text.isEmpty() && !focused)
		{
			g.setColor(QUIET);
			g.drawString(SideList.fit("Search places, items, monsters", metrics, room), textX, baseline);
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
			g.drawString(Caret.draw(shown, focused), textX, baseline);
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
		if (scopesOpen)
		{
			drawScopes(g, list, mouse, metrics);
		}
		else if (!shown.isEmpty())
		{
			g.setColor(PANEL);
			g.fillRoundRect(list.x, list.y, list.width, list.height, 8, 8);
			g.setColor(EDGE);
			g.drawRoundRect(list.x, list.y, list.width - 1, list.height - 1, 8, 8);
			int[] tops = rowTops(shown);
			Shape clip = g.getClip();
			g.clip(list);
			for (int i = 0; i < shown.size(); i++)
			{
				Entry entry = shown.get(i);
				int y = list.y + 2 + tops[i] - scroll;
				if (y + ROW < list.y || y - HEADING_ROW > list.y + list.height)
				{
					continue;
				}
				if (startsGroup(shown, i))
				{
					drawGroupHeading(g, groupOf(entry).label, list, y - HEADING_ROW, metrics);
				}
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
			}
			g.setClip(clip);
			int content = contentHeight(shown);
			if (content > list.height)
			{
				int thumb = Math.max(20, list.height * list.height / content);
				int top = list.y + (list.height - thumb) * scroll / Math.max(1, content - list.height);
				g.setColor(new Color(255, 255, 255, 60));
				g.fillRoundRect(list.x + list.width - 5, top, 3, thumb, 3, 3);
			}
		}
		else if (focused && !text.isEmpty())
		{
			// nothing to scroll
			g.setColor(PANEL);
			g.fillRoundRect(list.x, list.y, list.width, ROW + 4, 8, 8);
			g.setColor(QUIET);
			// say so when something is left out, or a search finding nothing looks like it is broken
			int off = skipped.size();
			String nothing = off == 0 ? "Nothing found" : "Nothing found; " + off + (off == 1 ? " kind" : " kinds")
				+ " of result off";
			g.drawString(SideList.fit(nothing, metrics, list.width - PAD * 2), list.x + PAD, list.y + 2
				+ (ROW + metrics.getAscent() - metrics.getDescent()) / 2);
		}
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}
}
