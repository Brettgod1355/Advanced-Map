/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.WorldMapElements;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * What hovering a dungeon's entrance shows before going in: the dungeon's name, a small map of it around where the
 * entrance leads, and every monster inside. "Inside" is the floor reachable from where the entrance leads without
 * crossing a tile the game has nothing on (the dark between caves), found on the map's own smallest pictures, and
 * without leaving the game's own map of that part of the underground ("Taverley Underground" ends where the Dwarven
 * Mines begin, though their floors touch). From there it follows the ladders, ropes and stairs that stay on that map
 * ({@code inner-links.tsv}, and the game's own map links), so the floors below count too: the Kalphite Queen is a
 * rope below the lair's tunnels.
 */
final class DungeonPreview
{
	/** The small map's size, in pixels, and how far in it is drawn (pixels a tile). */
	static final int MAP_WIDTH = 220;
	static final int MAP_HEIGHT = 150;
	static final double MAP_ZOOM = 2;
	/** The farthest in the small map zooms to fill itself with a small dungeon floor. */
	static final double MAX_MAP_ZOOM = 3;
	/** The farthest out the small map zooms to show a big dungeon floor whole. */
	static final double MIN_MAP_ZOOM = 1.25;
	/** How far in from the small map's edge the tile an entrance leads to is always kept, in pixels. */
	static final int ARRIVAL_MARGIN = 16;
	/** How far from where the entrance leads a dungeon is looked through, in tiles. */
	static final int REACH = 128;
	/** How near an entrance's icon a named entrance ({@code places.tsv}) must be to name it. */
	static final int NAME_REACH = 8;
	/** How near where the entrance leads a place name of the game's must be to name the dungeon. */
	static final int LABEL_REACH = 80;
	/** The most floors (the first and those reached by ladders and ropes) one look goes through. */
	static final int MAX_FLOORS = 8;
	/** A dungeon not yet looked through whole (its pictures still being drawn) is looked through again this often. */
	private static final long RETRY_NANOS = 500_000_000L;
	private static final int KEPT = 64;

	static final int OPEN = 1;
	static final int CLOSED = 0;
	static final int UNKNOWN = -1;

	/** Whether a tile of one floor has ground: {@link #OPEN}, {@link #CLOSED}, or {@link #UNKNOWN} while not drawn. */
	interface Floor
	{
		int at(int x, int y);
	}

	/** The tiles reachable from a start, within {@link #REACH}. */
	@Value
	static class Fill
	{
		int minX;
		int minY;
		int size;
		BitSet tiles;
		/** False when some tile next to the fill was not drawn yet: there may be more. */
		boolean complete;

		boolean contains(int x, int y)
		{
			int dx = x - minX;
			int dy = y - minY;
			return dx >= 0 && dy >= 0 && dx < size && dy < size && tiles.get(dy * size + dx);
		}

		/** The world tiles the fill spans (x, y south-west), or null when it is empty. */
		java.awt.Rectangle bounds()
		{
			int x0 = Integer.MAX_VALUE;
			int y0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE;
			int y1 = Integer.MIN_VALUE;
			for (int i = tiles.nextSetBit(0); i >= 0; i = tiles.nextSetBit(i + 1))
			{
				x0 = Math.min(x0, i % size);
				x1 = Math.max(x1, i % size);
				y0 = Math.min(y0, i / size);
				y1 = Math.max(y1, i / size);
			}
			return x0 > x1 ? null : new java.awt.Rectangle(minX + x0, minY + y0, x1 - x0 + 1, y1 - y0 + 1);
		}
	}

	/** One monster inside: its name, combat levels and how many spawn there. */
	@Value
	static class Inside
	{
		String name;
		String levels;
		int count;

		String text()
		{
			return levels.isEmpty() ? name : name + " (" + levels + ")";
		}
	}

	/** A way from one underground tile to another: a ladder, a rope, stairs. */
	@Value
	static class Way
	{
		WorldPoint from;
		WorldPoint to;
	}

	@Value
	static class Look
	{
		List<Inside> monsters;
		/** Where each floor looked through was entered: the entrance's tile first. */
		List<WorldPoint> floors;
		/** The tiles the entrance's own floor spans, or null when none is known yet. */
		java.awt.Rectangle firstFloor;
		boolean complete;
	}

	private static volatile List<Way> innerWays = Collections.emptyList();

	private final RegionImages images;
	private final Map<WorldPoint, Look> looked = Collections.synchronizedMap(
		new LinkedHashMap<WorldPoint, Look>(16, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<WorldPoint, Look> eldest)
			{
				return size() > KEPT;
			}
		});
	private volatile List<List<WorldMapElements.Piece>> maps = Collections.emptyList();
	/** The game's own map links that join two underground places. */
	private volatile List<Way> mapWays = Collections.emptyList();
	private volatile WorldPoint lastTry;
	private volatile long lastTryAt;

	DungeonPreview(RegionImages images)
	{
		this.images = images;
	}

	/** The parts of the world each of the game's maps other than the surface shows, one list a map. */
	void setMaps(List<List<WorldMapElements.Piece>> maps)
	{
		this.maps = maps;
		looked.clear();
	}

	/** The game's map links (packed as {@code MapLinks.pack}): those from one underground place to another are ways. */
	void setMapLinks(Map<Integer, Integer> links)
	{
		List<Way> ways = new ArrayList<>();
		links.forEach((from, to) ->
		{
			Way way = new Way(new WorldPoint(com.advancedmap.cache.MapLinks.x(from), com.advancedmap.cache.MapLinks.y(from),
				com.advancedmap.cache.MapLinks.plane(from)), new WorldPoint(com.advancedmap.cache.MapLinks.x(to),
				com.advancedmap.cache.MapLinks.y(to), com.advancedmap.cache.MapLinks.plane(to)));
			if (MapArea.of(way.getFrom().getY()) == MapArea.UNDERGROUND && MapArea.of(way.getTo().getY()) == MapArea.UNDERGROUND)
			{
				ways.add(way);
			}
		});
		mapWays = ways;
		looked.clear();
	}

	/** Reads the ways inside dungeons from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (java.io.InputStream in = DungeonPreview.class.getResourceAsStream("inner-links.tsv"))
		{
			if (in != null)
			{
				innerWays = parseWays(in);
			}
		}
		catch (java.io.IOException | RuntimeException e)
		{
			// no lower floors then; the first still shows
		}
	}

	static List<Way> parseWays(java.io.InputStream in) throws java.io.IOException
	{
		List<Way> out = new ArrayList<>();
		java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in,
			java.nio.charset.StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t");
			out.add(new Way(new WorldPoint(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])),
				new WorldPoint(Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]))));
		}
		return Collections.unmodifiableList(out);
	}

	/** The game's map with the fewest parts that shows a tile (the most particular one), or null for none. */
	static List<WorldMapElements.Piece> mapOf(WorldPoint at, List<List<WorldMapElements.Piece>> maps)
	{
		List<WorldMapElements.Piece> best = null;
		for (List<WorldMapElements.Piece> map : maps)
		{
			if ((best == null || map.size() < best.size()) && shows(map, at.getX(), at.getY(), at.getPlane()))
			{
				best = map;
			}
		}
		return best;
	}

	private static boolean shows(List<WorldMapElements.Piece> map, int x, int y, int plane)
	{
		for (WorldMapElements.Piece piece : map)
		{
			if (piece.contains(x, y, plane))
			{
				return true;
			}
		}
		return false;
	}

	/** A map's squares and zones by where they are, for a quick "does it show this tile" over a whole fill. */
	static Floor fence(List<WorldMapElements.Piece> map, int plane, Floor floor)
	{
		Set<Integer> squares = new HashSet<>();
		Set<Integer> zones = new HashSet<>();
		for (WorldMapElements.Piece piece : map)
		{
			if (plane < piece.getMinLevel())
			{
				continue;
			}
			if (piece.getSize() == 64)
			{
				squares.add(piece.getX() >> 6 << 16 | piece.getY() >> 6);
			}
			else
			{
				zones.add(piece.getX() >> 3 << 16 | piece.getY() >> 3);
			}
		}
		return (x, y) -> squares.contains(x >> 6 << 16 | y >> 6) || zones.contains(x >> 3 << 16 | y >> 3)
			? floor.at(x, y) : CLOSED;
	}

	/**
	 * The monsters inside the dungeon an entrance leads to ({@code to}), from what is known of its floor so far; while
	 * some of it is still being drawn, looked through again every so often, and the pictures asked for.
	 */
	Look look(WorldPoint to)
	{
		Look known = looked.get(to);
		long now = System.nanoTime();
		if (known != null && (known.isComplete() || to.equals(lastTry) && now - lastTryAt < RETRY_NANOS))
		{
			return known;
		}
		lastTry = to;
		lastTryAt = now;
		List<Way> ways = new ArrayList<>(innerWays);
		ways.addAll(mapWays);
		Look look = look(to, maps, ways, this::floor, Spawns.all());
		looked.put(to, look);
		return look;
	}

	/**
	 * Looks through the floor an entrance leads to, then each floor a way from it leads to on the same game map, and
	 * so on, up to {@link #MAX_FLOORS}. Without a game map for the entrance (its map data not read yet) only the first.
	 */
	static Look look(WorldPoint to, List<List<WorldMapElements.Piece>> maps, List<Way> ways,
		java.util.function.IntFunction<Floor> floors, List<Spawns.Creature> creatures)
	{
		List<WorldMapElements.Piece> map = mapOf(to, maps);
		List<WorldPoint> starts = new ArrayList<>();
		List<Fill> fills = new ArrayList<>();
		java.util.ArrayDeque<WorldPoint> next = new java.util.ArrayDeque<>();
		next.add(to);
		boolean complete = true;
		while (!next.isEmpty() && fills.size() < MAX_FLOORS)
		{
			WorldPoint start = next.poll();
			if (inside(start, starts, fills))
			{
				continue;
			}
			Floor floor = floors.apply(start.getPlane());
			Fill fill = flood(start.getX(), start.getY(), map == null ? floor : fence(map, start.getPlane(), floor));
			starts.add(start);
			fills.add(fill);
			complete &= fill.isComplete();
			if (map == null)
			{
				break;
			}
			for (Way way : ways)
			{
				if (way.getFrom().getPlane() == start.getPlane() && fill.contains(way.getFrom().getX(), way.getFrom().getY())
					&& shows(map, way.getTo().getX(), way.getTo().getY(), way.getTo().getPlane()))
				{
					next.add(way.getTo());
				}
			}
		}
		return new Look(monsters(starts, fills, creatures), Collections.unmodifiableList(starts), fills.get(0).bounds(),
			complete);
	}

	private static boolean inside(WorldPoint at, List<WorldPoint> starts, List<Fill> fills)
	{
		for (int i = 0; i < fills.size(); i++)
		{
			if (starts.get(i).getPlane() == at.getPlane() && fills.get(i).contains(at.getX(), at.getY()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The small map's view: on the middle of the entrance's floor ({@code floor}, when known), zoomed to show it whole
	 * (out as far as {@link #MIN_MAP_ZOOM}, in as far as {@link #MAX_MAP_ZOOM}), so it shows the dungeon rather than
	 * the dark around its edge; the tile the entrance leads to always at least {@link #ARRIVAL_MARGIN} pixels inside.
	 */
	static MapViewport.View framing(WorldPoint to, java.awt.Rectangle floor)
	{
		double x = to.getX() + 0.5;
		double y = to.getY() + 0.5;
		if (floor == null)
		{
			return new MapViewport.View(x, y, MAP_ZOOM, MAP_WIDTH, MAP_HEIGHT);
		}
		double zoom = Math.max(MIN_MAP_ZOOM, Math.min(MAX_MAP_ZOOM, Math.min((double) MAP_WIDTH / (floor.width + 4),
			(double) MAP_HEIGHT / (floor.height + 4))));
		double reachX = Math.max(0, MAP_WIDTH / 2.0 - ARRIVAL_MARGIN) / zoom;
		double reachY = Math.max(0, MAP_HEIGHT / 2.0 - ARRIVAL_MARGIN) / zoom;
		double cx = Math.max(x - reachX, Math.min(x + reachX, floor.getCenterX()));
		double cy = Math.max(y - reachY, Math.min(y + reachY, floor.getCenterY()));
		return new MapViewport.View(cx, cy, zoom, MAP_WIDTH, MAP_HEIGHT);
	}

	/** The floors of regions ({@link RegionImages#key}) a look reads: every one within {@link #REACH} of each start. */
	static Collection<Integer> regionsAround(Collection<WorldPoint> starts)
	{
		Set<Integer> keys = new LinkedHashSet<>();
		for (WorldPoint to : starts)
		{
			for (int rx = (to.getX() - REACH) >> 6; rx <= (to.getX() + REACH) >> 6; rx++)
			{
				for (int ry = (to.getY() - REACH) >> 6; ry <= (to.getY() + REACH) >> 6; ry++)
				{
					keys.add(RegionImages.key(to.getPlane(), rx << 8 | ry));
				}
			}
		}
		return keys;
	}

	/** A floor as the map's pictures show it: ground where a picture has color, none where it is see-through. */
	private Floor floor(int plane)
	{
		return (x, y) ->
		{
			int id = (x >> 6) << 8 | (y >> 6);
			if (images.isAbsent(plane, id))
			{
				return CLOSED;
			}
			BufferedImage image = images.get(plane, id, RegionImages.Level.SMALL);
			if (image == null)
			{
				return UNKNOWN;
			}
			int perTile = Math.max(1, image.getWidth() / 64);
			int px = (x & 63) * perTile + perTile / 2;
			int py = (63 - (y & 63)) * perTile + perTile / 2;
			return image.getRGB(px, py) >>> 24 == 0 ? CLOSED : OPEN;
		};
	}

	/**
	 * The tiles reachable from a start, stepping north, south, east and west over ground, within {@link #REACH}. A
	 * start without ground (an entrance leading onto a wall's edge) begins from the nearest ground within 3 tiles.
	 */
	static Fill flood(int startX, int startY, Floor floor)
	{
		int size = REACH * 2 + 1;
		int minX = startX - REACH;
		int minY = startY - REACH;
		BitSet seen = new BitSet(size * size);
		BitSet tiles = new BitSet(size * size);
		int start = -1;
		for (int r = 0; r <= 3 && start < 0; r++)
		{
			for (int dx = -r; dx <= r && start < 0; dx++)
			{
				for (int dy = -r; dy <= r && start < 0; dy++)
				{
					if (floor.at(startX + dx, startY + dy) == OPEN)
					{
						start = (REACH + dy) * size + REACH + dx;
					}
				}
			}
		}
		boolean complete = true;
		if (start < 0)
		{
			return new Fill(minX, minY, size, tiles, floor.at(startX, startY) != UNKNOWN);
		}
		int[] queue = new int[size * size];
		int head = 0;
		int tail = 0;
		queue[tail++] = start;
		seen.set(start);
		tiles.set(start);
		int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		while (head < tail)
		{
			int at = queue[head++];
			int ax = at % size;
			int ay = at / size;
			for (int[] step : steps)
			{
				int nx = ax + step[0];
				int ny = ay + step[1];
				if (nx < 0 || ny < 0 || nx >= size || ny >= size)
				{
					continue;
				}
				int next = ny * size + nx;
				if (seen.get(next))
				{
					continue;
				}
				seen.set(next);
				int ground = floor.at(minX + nx, minY + ny);
				if (ground == OPEN)
				{
					tiles.set(next);
					queue[tail++] = next;
				}
				else if (ground == UNKNOWN)
				{
					complete = false;
				}
			}
		}
		return new Fill(minX, minY, size, tiles, complete);
	}

	/** The monsters with a spawn on the fill's floor, the lowest level first. */
	static List<Inside> monsters(Fill fill, int plane, List<Spawns.Creature> creatures)
	{
		return monsters(Collections.singletonList(new WorldPoint(0, 0, plane)), Collections.singletonList(fill),
			creatures);
	}

	/** The monsters with a spawn in any of the fills, each on its start's floor, the lowest level first. */
	static List<Inside> monsters(List<WorldPoint> starts, List<Fill> fills, List<Spawns.Creature> creatures)
	{
		List<Inside> out = new ArrayList<>();
		for (Spawns.Creature creature : creatures)
		{
			if (!creature.isMonster())
			{
				continue;
			}
			int count = 0;
			for (WorldPoint tile : creature.getTiles())
			{
				if (inside(tile, starts, fills))
				{
					count++;
				}
			}
			if (count > 0)
			{
				out.add(new Inside(creature.getName(), creature.getLevels(), count));
			}
		}
		return sorted(out, false);
	}

	/**
	 * The monsters by level: the highest first (by each one's highest level) or the lowest first (by its lowest),
	 * those without a level last either way, then by name.
	 */
	static List<Inside> sorted(List<Inside> monsters, boolean highestFirst)
	{
		List<Inside> out = new ArrayList<>(monsters);
		out.sort(Comparator.comparingInt((Inside inside) -> inside.getLevels().isEmpty() ? Integer.MAX_VALUE
			: highestFirst ? -level(inside.getLevels(), true) : level(inside.getLevels(), false))
			.thenComparing(Inside::getName));
		return out;
	}

	/** The highest or lowest of levels written "13, 18, 24". */
	private static int level(String levels, boolean highest)
	{
		int best = highest ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		for (String level : levels.split(","))
		{
			try
			{
				int value = Integer.parseInt(level.trim());
				best = highest ? Math.max(best, value) : Math.min(best, value);
			}
			catch (NumberFormatException e)
			{
				// "?" and the like are left out
			}
		}
		return best == Integer.MIN_VALUE || best == Integer.MAX_VALUE ? 0 : best;
	}

	/**
	 * The dungeon's name: the named entrance nearest the icon ({@code places.tsv}, "Edgeville Dungeon"), else the
	 * game's place name nearest where it leads, else null.
	 */
	static String name(MapSpot spot, WorldPoint to, List<MapSearch.Entry> places, List<MapLabels.Label> labels)
	{
		String best = null;
		int bestDistance = NAME_REACH + 1;
		for (MapSearch.Entry place : places)
		{
			if (place.getKind() != MapSearch.Kind.DUNGEON && place.getKind() != MapSearch.Kind.MINIGAME)
			{
				continue;
			}
			for (WorldPoint at : place.getPoints())
			{
				int d = Math.max(Math.abs(at.getX() - spot.getX()), Math.abs(at.getY() - spot.getY()));
				if (at.getPlane() == spot.getPlane() && d < bestDistance)
				{
					best = place.getName();
					bestDistance = d;
				}
			}
		}
		if (best != null)
		{
			return best;
		}
		bestDistance = LABEL_REACH + 1;
		MapArea area = MapArea.of(to.getY());
		for (MapLabels.Label label : labels)
		{
			int d = Math.max(Math.abs(label.getX() - to.getX()), Math.abs(label.getY() - to.getY()));
			if (MapArea.of(label.getY()) == area && d < bestDistance)
			{
				best = String.join(" ", label.getLines());
				bestDistance = d;
			}
		}
		return best;
	}
}
