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
 * Mines begin, though their floors touch).
 */
final class DungeonPreview
{
	/** The small map's size, in pixels, and how far in it is drawn (pixels a tile). */
	static final int MAP_WIDTH = 220;
	static final int MAP_HEIGHT = 150;
	static final double MAP_ZOOM = 2;
	/** How far from where the entrance leads a dungeon is looked through, in tiles. */
	static final int REACH = 128;
	/** How near an entrance's icon a named entrance ({@code places.tsv}) must be to name it. */
	static final int NAME_REACH = 8;
	/** How near where the entrance leads a place name of the game's must be to name the dungeon. */
	static final int LABEL_REACH = 80;
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

	@Value
	static class Look
	{
		List<Inside> monsters;
		boolean complete;
	}

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
		Floor floor = floor(to.getPlane());
		List<WorldMapElements.Piece> map = mapOf(to, maps);
		Floor fenced = map == null ? floor : fence(map, to.getPlane(), floor);
		Fill fill = flood(to.getX(), to.getY(), fenced);
		Look look = new Look(monsters(fill, to.getPlane(), Spawns.all()), fill.isComplete());
		looked.put(to, look);
		return look;
	}

	/** The regions a look at {@code to} reads: every one within {@link #REACH}. */
	static Collection<Integer> regionsAround(WorldPoint to)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		for (int rx = (to.getX() - REACH) >> 6; rx <= (to.getX() + REACH) >> 6; rx++)
		{
			for (int ry = (to.getY() - REACH) >> 6; ry <= (to.getY() + REACH) >> 6; ry++)
			{
				ids.add(rx << 8 | ry);
			}
		}
		return ids;
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
				if (tile.getPlane() == plane && fill.contains(tile.getX(), tile.getY()))
				{
					count++;
				}
			}
			if (count > 0)
			{
				out.add(new Inside(creature.getName(), creature.getLevels(), count));
			}
		}
		out.sort(Comparator.comparingInt((Inside inside) -> lowestLevel(inside.getLevels()))
			.thenComparing(Inside::getName));
		return out;
	}

	private static int lowestLevel(String levels)
	{
		try
		{
			return Integer.parseInt(levels.split(",")[0].trim());
		}
		catch (NumberFormatException e)
		{
			return Integer.MAX_VALUE;
		}
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
