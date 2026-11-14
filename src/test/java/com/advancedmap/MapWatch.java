/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * The development client's map watch (never shipped: the test folder is not built into the plugin): what the map
 * believes, judged against what the game does while the owner plays. This class is the judging and the list of what
 * was found; {@link MapWatchPlugin} feeds it from the game. Each finding is one line of a file, counted at most once
 * every ten minutes, so a count tells how many separate times it was seen.
 */
final class MapWatch
{
	/** A finding seen again within this long is the same sighting. */
	static final long AGAIN = 10 * 60_000L;
	/** A move of more than this many tiles in one tick, or onto the other map, is a jump (a ladder, a boat). */
	static final int JUMP = 24;
	/** A dungeon icon this near where a jump began is the way taken. */
	static final int ICON_REACH = 6;
	/** A jump that ends within this of where the map says it leads is the same place. */
	static final int ARRIVE = 40;
	/** A monster this far from every spot the map lists for it is somewhere the map does not know. */
	static final int AWAY = 48;
	/** An item on the ground this near a listed spawn of it is that spawn. */
	static final int ITEM_REACH = 3;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
	static final String HEADER = "# kind\ttimes seen\tfirst seen\tlast seen\twhat\tdetail";

	@Getter
	@AllArgsConstructor
	static final class Finding
	{
		private final String kind;
		private final String key;
		private int count;
		private final long first;
		private long last;
		private String detail;
	}

	/** What was found, by kind and key, as the file holds it. */
	static final class Log
	{
		private final Map<String, Finding> found = new LinkedHashMap<>();
		private boolean changed;

		/** Notes a finding; false when it was seen within {@link #AGAIN} (only its detail is brought up to date). */
		synchronized boolean note(String[] finding, long now)
		{
			if (finding == null)
			{
				return false;
			}
			String id = finding[0] + "\t" + clean(finding[1]);
			Finding known = found.get(id);
			if (known == null)
			{
				found.put(id, new Finding(finding[0], clean(finding[1]), 1, now, now, clean(finding[2])));
				changed = true;
				return true;
			}
			known.detail = clean(finding[2]);
			if (now - known.last < AGAIN)
			{
				return false;
			}
			known.count++;
			known.last = now;
			changed = true;
			return true;
		}

		/**
		 * As {@link #note(String[], long)}, for a finding that differs from world to world (a shop's stock): the
		 * worlds it was seen on are kept at the end of its detail.
		 */
		synchronized boolean note(String[] finding, int world, long now)
		{
			if (finding == null)
			{
				return false;
			}
			Finding known = found.get(finding[0] + "\t" + clean(finding[1]));
			TreeSet<Integer> worlds = known == null ? new TreeSet<>() : worlds(known.detail);
			worlds.add(world);
			return note(finding(finding[0], finding[1], finding[2] + WORLDS + worlds), now);
		}

		/** True once since the last call if anything was noted: the file is due. */
		synchronized boolean takeChanged()
		{
			boolean was = changed;
			changed = false;
			return was;
		}

		synchronized int size()
		{
			return found.size();
		}

		/** The file's lines: each kind together, the most seen first. */
		synchronized List<String> lines()
		{
			List<Finding> all = new ArrayList<>(found.values());
			all.sort(Comparator.comparing(Finding::getKind).thenComparing(Finding::getCount, Comparator.reverseOrder())
				.thenComparing(Finding::getKey));
			List<String> out = new ArrayList<>();
			out.add(HEADER);
			for (Finding one : all)
			{
				out.add(one.kind + "\t" + one.count + "\t" + time(one.first) + "\t" + time(one.last) + "\t" + one.key
					+ "\t" + one.detail);
			}
			return out;
		}

		/** Takes up the file as it was left; a line that does not read is skipped. */
		synchronized void read(List<String> lines)
		{
			for (String line : lines)
			{
				String[] cells = line.split("\t", -1);
				if (line.startsWith("#") || cells.length < 6)
				{
					continue;
				}
				try
				{
					found.put(cells[0] + "\t" + cells[4], new Finding(cells[0], cells[4], Integer.parseInt(cells[1]),
						millis(cells[2]), millis(cells[3]), cells[5]));
				}
				catch (RuntimeException e)
				{
					// a line edited by hand
				}
			}
		}
	}

	private MapWatch()
	{
	}

	private static final String WORLDS = "; worlds ";

	/** The worlds at the end of a detail ("...; worlds [301, 302]"); none when it has none. */
	static TreeSet<Integer> worlds(String detail)
	{
		TreeSet<Integer> out = new TreeSet<>();
		int at = detail == null ? -1 : detail.lastIndexOf(WORLDS + "[");
		if (at < 0 || !detail.endsWith("]"))
		{
			return out;
		}
		for (String world : detail.substring(at + WORLDS.length() + 1, detail.length() - 1).split(", "))
		{
			try
			{
				out.add(Integer.parseInt(world.trim()));
			}
			catch (NumberFormatException e)
			{
				// a detail edited by hand
			}
		}
		return out;
	}

	private static String clean(String text)
	{
		return text == null ? "" : text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
	}

	private static String time(long millis)
	{
		return TIME.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
	}

	private static long millis(String time)
	{
		return LocalDateTime.parse(time, TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	private static String[] finding(String kind, String key, String detail)
	{
		return new String[]{kind, key, detail};
	}

	static String tile(WorldPoint at)
	{
		return at.getX() + ", " + at.getY() + ", " + at.getPlane();
	}

	/** A region and the 8-tile square in it: near enough that one spot is one finding. */
	static String cell(WorldPoint at)
	{
		return "region " + at.getRegionID() + " near " + (at.getX() & ~7) + ", " + (at.getY() & ~7) + ", "
			+ at.getPlane();
	}

	static int flat(WorldPoint a, WorldPoint b)
	{
		return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
	}

	private static boolean near(WorldPoint a, WorldPoint b, int reach)
	{
		return MapArea.of(a.getY()) == MapArea.of(b.getY()) && flat(a, b) <= reach;
	}

	/** True when a tick's move is no walk: onto the other map, or farther than {@link #JUMP}. */
	static boolean jumped(WorldPoint from, WorldPoint to)
	{
		return !near(from, to, JUMP);
	}

	/** The dungeon icon nearest a tile on its map, within {@link #ICON_REACH}, or null. */
	static MapSpot dungeonIconNear(List<MapSpot> spots, WorldPoint at)
	{
		MapSpot best = null;
		int bestDistance = ICON_REACH + 1;
		for (MapSpot spot : spots)
		{
			WorldPoint point = new WorldPoint(spot.getX(), spot.getY(), spot.getPlane());
			if (spot.getGroup() == IconGroup.DUNGEONS && near(point, at, ICON_REACH) && flat(point, at) < bestDistance)
			{
				best = spot;
				bestDistance = flat(point, at);
			}
		}
		return best;
	}

	/**
	 * A jump judged against the map. {@code used} is the object or NPC the player used just before (null: a spell,
	 * an item, a death, which are no ways on the map and are never findings). By a dungeon icon: the icon must lead
	 * where the player arrived. Anywhere else: the map must know a way from there to there ({@code known}).
	 */
	static String[] jump(WorldPoint from, WorldPoint to, String used, MapSpot icon, WorldPoint iconLeads,
		boolean known)
	{
		if (used == null)
		{
			return null;
		}
		String went = "used " + used + " at " + tile(from) + ", arrived at " + tile(to);
		if (icon != null)
		{
			String at = "dungeon icon " + icon.getX() + ", " + icon.getY() + ", " + icon.getPlane();
			if (iconLeads == null)
			{
				return known ? null : finding("link-missing", at, "the icon leads nowhere on the map; " + went);
			}
			return near(iconLeads, to, ARRIVE) ? null
				: finding("link-wrong", at, "the map says it leads to " + tile(iconLeads) + "; " + went);
		}
		return known ? null : finding("transport-unlisted", used + " in " + cell(from), went);
	}

	/** A name as the lists are keyed: lower case, without the wiki's "(place)" after it. */
	static String base(String name)
	{
		String lower = name.toLowerCase(Locale.ROOT).trim();
		int bracket = lower.indexOf(" (");
		return bracket > 0 ? lower.substring(0, bracket) : lower;
	}

	/** Where the map lists each monster (or each item on the ground), by {@link #base} name. */
	static Map<String, List<WorldPoint>> listed(List<Spawns.Creature> all, boolean items)
	{
		Map<String, List<WorldPoint>> out = new HashMap<>();
		for (Spawns.Creature creature : all)
		{
			if (items ? creature.isItem() : creature.isMonster())
			{
				out.computeIfAbsent(base(creature.getName()), name -> new ArrayList<>()).addAll(creature.getTiles());
			}
		}
		return out;
	}

	private static int nearest(List<WorldPoint> tiles, WorldPoint at)
	{
		int best = Integer.MAX_VALUE;
		for (WorldPoint tile : tiles)
		{
			if (MapArea.of(tile.getY()) == MapArea.of(at.getY()))
			{
				best = Math.min(best, flat(tile, at));
			}
		}
		return best;
	}

	/** A monster seen in game: not on the map's lists at all, or farther than {@link #AWAY} from every listed spot. */
	static String[] monster(String name, int level, WorldPoint at, Map<String, List<WorldPoint>> listed)
	{
		List<WorldPoint> tiles = listed.get(base(name));
		if (tiles == null)
		{
			return finding("monster-unlisted", name, "level " + level + ", seen at " + tile(at));
		}
		int distance = nearest(tiles, at);
		return distance <= AWAY ? null : finding("monster-away", name + " in region " + at.getRegionID(),
			"seen at " + tile(at) + ", " + (distance == Integer.MAX_VALUE ? "none listed on this map"
				: distance + " tiles from the nearest listed spot"));
	}

	/**
	 * An item on the ground with no timer to vanish by, where the map lists no spawn of it. The game puts a timer on
	 * everything a player or a monster drops and none on what it sets out itself, so a drop never comes here.
	 */
	static String[] groundItem(String name, WorldPoint at, Map<String, List<WorldPoint>> listed)
	{
		List<WorldPoint> tiles = listed.get(base(name));
		return tiles != null && nearest(tiles, at) <= ITEM_REACH ? null
			: finding("item-unlisted", name + " at " + tile(at), "no timer to vanish by, so the game set it out; "
				+ (tiles == null ? "the map lists no spawn of it anywhere" : "the map lists none here"));
	}

	/** The shop among those near whose stock on the map has the most of what the window shows: the nearest of equals. */
	static Shops.Shop shopFor(List<Shops.Shop> near, Collection<String> inGame)
	{
		TreeSet<String> game = lower(inGame);
		Shops.Shop best = null;
		int bestShared = -1;
		for (Shops.Shop shop : near)
		{
			int shared = 0;
			for (Shops.Item item : shop.getItems())
			{
				shared += game.contains(item.getName().toLowerCase(Locale.ROOT)) ? 1 : 0;
			}
			if (shared > bestShared)
			{
				best = shop;
				bestShared = shared;
			}
		}
		return best;
	}

	private static TreeSet<String> lower(Collection<String> names)
	{
		TreeSet<String> out = new TreeSet<>();
		for (String name : names)
		{
			out.add(name.toLowerCase(Locale.ROOT));
		}
		return out;
	}

	/**
	 * A shop's stock in game ({@code inGame}, in the window's order) against the map's, one finding for each item
	 * that only one of them has. What a player sold to the shop is not its stock, and the game does not say which is
	 * which, so an item the map lacks is judged by where it sits: a shop's own stock comes first and what players
	 * sold is added after it, and only a general store buys what it does not sell. So an item after all the stock
	 * the map knows, in a general store, is "shop-item-sold?" (most likely a player's); anything else the map lacks
	 * is "shop-item-unlisted". An item the map lists and the shop does not show is "shop-item-absent" either way.
	 */
	static List<String[]> shop(Shops.Shop shop, WorldPoint at, List<String> inGame)
	{
		List<String[]> out = new ArrayList<>();
		if (shop == null)
		{
			out.add(finding("shop-unlisted", cell(at), "no shop on the map near; it sells " + inGame));
			return out;
		}
		TreeSet<String> map = new TreeSet<>();
		for (Shops.Item item : shop.getItems())
		{
			map.add(item.getName().toLowerCase(Locale.ROOT));
		}
		int lastKnown = -1;
		for (int i = 0; i < inGame.size(); i++)
		{
			lastKnown = map.contains(inGame.get(i).toLowerCase(Locale.ROOT)) ? i : lastKnown;
		}
		boolean general = shop.getName().toLowerCase(Locale.ROOT).contains("general");
		for (int i = 0; i < inGame.size(); i++)
		{
			String name = inGame.get(i);
			if (map.contains(name.toLowerCase(Locale.ROOT)))
			{
				continue;
			}
			String key = shop.getName() + ": " + name;
			if (i < lastKnown)
			{
				out.add(finding("shop-item-unlisted", key, "sits among the stock the map knows, so it is the shop's own"));
			}
			else if (!general)
			{
				out.add(finding("shop-item-unlisted", key, "after the stock the map knows; no general store, and"
					+ " other shops buy only what they sell, so most likely the shop's own"));
			}
			else
			{
				out.add(finding("shop-item-sold?", key, "after the shop's own stock in a general store: most likely"
					+ " sold by a player; first and last seen days apart would make it the shop's own"));
			}
		}
		TreeSet<String> game = lower(inGame);
		for (Shops.Item item : shop.getItems())
		{
			if (!game.contains(item.getName().toLowerCase(Locale.ROOT)))
			{
				out.add(finding("shop-item-absent", shop.getName() + ": " + item.getName(),
					"the map lists it, the shop does not show it"));
			}
		}
		return out;
	}

	/** Pictures still being made for too long with the map open. */
	static final class Stuck
	{
		static final long TOO_LONG = 60_000L;
		private long since = -1;
		private boolean told;

		String[] add(boolean busy, long now)
		{
			if (!busy)
			{
				since = -1;
				told = false;
				return null;
			}
			since = since < 0 ? now : since;
			if (told || now - since < TOO_LONG)
			{
				return null;
			}
			told = true;
			return finding("loading-stuck", "map open", "pictures still being made after "
				+ (now - since) / 1000 + " s");
		}
	}

	/** The frame rate with the map open against with it closed, one finding for each time the map is opened. */
	static final class Fps
	{
		static final int SAMPLES = 20;
		private long closedSum;
		private int closedCount;
		private long openSum;
		private int openCount;
		private boolean told;

		String[] add(boolean open, int fps)
		{
			if (!open)
			{
				if (openCount > 0)
				{
					// closed again: the next comparison starts afresh
					closedSum = 0;
					closedCount = 0;
					openSum = 0;
					openCount = 0;
					told = false;
				}
				closedSum += fps;
				closedCount++;
				return null;
			}
			openSum += fps;
			openCount++;
			if (told || closedCount < SAMPLES || openCount < SAMPLES)
			{
				return null;
			}
			long closed = closedSum / closedCount;
			long opened = openSum / openCount;
			if (opened * 10 >= closed * 7)
			{
				return null;
			}
			told = true;
			return finding("fps", "map open", "about " + closed + " fps with the map closed, " + opened
				+ " with it open");
		}
	}

	/** Memory in use growing the longer the map stays open: the least in use each minute, against the first. */
	static final class Memory
	{
		static final long MINUTE = 60_000L;
		static final long LONG_ENOUGH = 10 * MINUTE;
		static final long GROWTH_MB = 200;
		private long opened = -1;
		private long first = -1;
		private long windowStart;
		private long windowLeast;
		private boolean told;

		String[] add(boolean open, long usedMb, long now)
		{
			if (!open)
			{
				opened = -1;
				first = -1;
				told = false;
				return null;
			}
			if (opened < 0)
			{
				opened = now;
				windowStart = now;
				windowLeast = usedMb;
				return null;
			}
			if (now - windowStart < MINUTE)
			{
				windowLeast = Math.min(windowLeast, usedMb);
				return null;
			}
			long least = windowLeast;
			windowStart = now;
			windowLeast = usedMb;
			if (first < 0)
			{
				first = least;
				return null;
			}
			if (told || now - opened < LONG_ENOUGH || least - first < GROWTH_MB)
			{
				return null;
			}
			told = true;
			return finding("memory", "map open", "least in use went from " + first + " MB to " + least + " MB over "
				+ (now - opened) / MINUTE + " min with the map open");
		}
	}
}
