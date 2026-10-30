/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * A searched monster's or NPC's spawns on the map: every spawn marked, and the spawns grouped into areas (spawns
 * within {@link #NEAR} tiles of each other, on one floor of one map, and groups nearest the same place name), each
 * named after the game's nearest place name.
 */
final class SearchSpots
{
	/** Spawns this close together are one area. */
	static final int NEAR = 16;
	/** A place name further than this from an area does not name it. */
	static final int NAME_REACH = 200;
	static final Color MARK = new Color(0xFF6A3D);

	/**
	 * One area of spawns: its name, the spawn nearest its middle, how many spawns, how far from the player, and the
	 * floor that spawn is really on (another than its point's when the game's map has no ground on its own there).
	 */
	@Value
	static class Area
	{
		String name;
		WorldPoint point;
		int count;
		int distance;
		int floor;

		Area(String name, WorldPoint point, int count, int distance)
		{
			this(name, point, count, distance, point.getPlane());
		}

		Area(String name, WorldPoint point, int count, int distance, int floor)
		{
			this.name = name;
			this.point = point;
			this.count = count;
			this.distance = distance;
			this.floor = floor;
		}
	}

	private SearchSpots()
	{
	}

	/** Tiles apart along the longer axis; tiles on different maps or floors are far apart. */
	static int distance(WorldPoint a, WorldPoint b)
	{
		int apart = Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY()));
		if (MapArea.of(a.getY()) != MapArea.of(b.getY()))
		{
			return apart + 100_000;
		}
		return a.getPlane() == b.getPlane() ? apart : apart + 1_000;
	}

	/** The spawns grouped into areas, nearest to {@code from} first. Never empty for spawns. */
	static List<Area> areas(List<WorldPoint> spawns, WorldPoint from, List<MapLabels.Label> labels)
	{
		return areas(spawns, from, labels, Collections.emptyMap());
	}

	/** As above, with the floors some spawns are really on ({@link Spawns.Creature#getFloors}). */
	static List<Area> areas(List<WorldPoint> spawns, WorldPoint from, List<MapLabels.Label> labels,
		Map<WorldPoint, Integer> floors)
	{
		int n = spawns.size();
		int[] group = new int[n];
		for (int i = 0; i < n; i++)
		{
			group[i] = i;
		}
		for (int i = 0; i < n; i++)
		{
			for (int j = i + 1; j < n; j++)
			{
				// on the floor each is really on: one moved down from upstairs is not in the area of those below it
				WorldPoint a = spawns.get(i);
				WorldPoint b = spawns.get(j);
				if (distance(a, b) <= NEAR && floors.getOrDefault(a, a.getPlane()).equals(floors.getOrDefault(b, b.getPlane())))
				{
					group[root(group, i)] = root(group, j);
				}
			}
		}
		List<List<WorldPoint>> groups = new ArrayList<>();
		int[] index = new int[n];
		java.util.Arrays.fill(index, -1);
		for (int i = 0; i < n; i++)
		{
			int r = root(group, i);
			if (index[r] < 0)
			{
				index[r] = groups.size();
				groups.add(new ArrayList<>());
			}
			groups.get(index[r]).add(spawns.get(i));
		}
		// groups nearest the same place name, on one floor, are one area: spawns a little apart in one town
		Map<String, List<WorldPoint>> named = new LinkedHashMap<>();
		Map<String, String> names = new HashMap<>();
		for (List<WorldPoint> tiles : groups)
		{
			WorldPoint middle = middle(tiles);
			MapLabels.Label label = nearestLabel(middle, labels);
			String key = label == null ? middle.toString() : label.getX() + "," + label.getY() + ","
				+ floors.getOrDefault(middle, middle.getPlane());
			named.computeIfAbsent(key, k -> new ArrayList<>()).addAll(tiles);
			names.put(key, label == null ? middle.getX() + ", " + middle.getY() : String.join(" ", label.getLines()));
		}
		List<Area> out = new ArrayList<>();
		named.forEach((key, tiles) ->
		{
			WorldPoint middle = middle(tiles);
			out.add(new Area(names.get(key), middle, tiles.size(), distance(middle, from),
				floors.getOrDefault(middle, middle.getPlane())));
		});
		out.sort(Comparator.comparingInt(Area::getDistance));
		return out;
	}

	private static int root(int[] group, int i)
	{
		while (group[i] != i)
		{
			group[i] = group[group[i]];
			i = group[i];
		}
		return i;
	}

	/** The spawn nearest the tiles' average. */
	private static WorldPoint middle(List<WorldPoint> tiles)
	{
		double x = 0;
		double y = 0;
		for (WorldPoint tile : tiles)
		{
			x += tile.getX();
			y += tile.getY();
		}
		x /= tiles.size();
		y /= tiles.size();
		WorldPoint best = tiles.get(0);
		double bestDistance = Double.MAX_VALUE;
		for (WorldPoint tile : tiles)
		{
			double d = Math.abs(tile.getX() - x) + Math.abs(tile.getY() - y);
			if (d < bestDistance)
			{
				best = tile;
				bestDistance = d;
			}
		}
		return best;
	}

	/** The game's nearest place name on the same map, or the tile when none is near. */
	static String name(WorldPoint at, List<MapLabels.Label> labels)
	{
		MapLabels.Label label = nearestLabel(at, labels);
		return label != null ? String.join(" ", label.getLines()) : at.getX() + ", " + at.getY();
	}

	/** The game's place name nearest a tile on the same map, within {@link #NAME_REACH}; null when none is. */
	private static MapLabels.Label nearestLabel(WorldPoint at, List<MapLabels.Label> labels)
	{
		MapLabels.Label best = null;
		int bestDistance = NAME_REACH + 1;
		MapArea area = MapArea.of(at.getY());
		for (MapLabels.Label label : labels)
		{
			if (MapArea.of(label.getY()) != area)
			{
				continue;
			}
			int d = Math.max(Math.abs(label.getX() - at.getX()), Math.abs(label.getY() - at.getY()));
			if (d < bestDistance)
			{
				best = label;
				bestDistance = d;
			}
		}
		return best;
	}

	/** The areas as list rows: the name, how many spawns, the floor when it is not the ground. */
	static List<SideList.Row> rows(List<Area> areas)
	{
		return rows(areas, "spawn");
	}

	/** As {@link #rows(List)}, counting {@code what} ("spawn", "spot"). */
	static List<SideList.Row> rows(List<Area> areas, String what)
	{
		List<SideList.Row> rows = new ArrayList<>();
		for (Area area : areas)
		{
			String count = area.getCount() + " " + what + (area.getCount() == 1 ? "" : "s");
			// a spawn upstairs where the map draws no ground is shown on the floor below, and said to be upstairs
			String floor = area.getFloor() > area.getPoint().getPlane() ? "Upstairs, floor up " + area.getFloor()
				: area.getFloor() > 0 ? "Floor up " + area.getFloor() : null;
			rows.add(new SideList.Row(area.getName(), count, floor, null, area.getPoint(), null));
		}
		return rows;
	}

	/** Every spawn marked, for the layer named {@code id}. */
	static MapLayer layer(String id, List<WorldPoint> spawns)
	{
		List<MapLayer.Marker> marks = new ArrayList<>();
		for (WorldPoint spawn : spawns)
		{
			marks.add(new MapLayer.Marker(spawn, MARK, MapLayer.Shape.CIRCLE, 10, null));
		}
		return new MapLayer(id, 80, Collections.emptyList(), Collections.unmodifiableList(marks));
	}
}
