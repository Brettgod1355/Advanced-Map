/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Where monsters and other NPCs are, with monsters' combat levels, from {@code spawns.tsv} (the OSRS Wiki's spawn
 * and monster tables; see THIRD_PARTY_NOTICES.md), for search.
 */
@Slf4j
final class Spawns
{
	@Value
	static class Creature
	{
		String name;
		boolean monster;
		/** Combat levels, "28" or "2, 5, 13"; empty for an NPC. */
		String levels;
		List<WorldPoint> tiles;
		/**
		 * The floor a tile is really on, for the tiles put on another floor: the game's map draws no ground on theirs
		 * there (a platform up a tree), so they are shown on the floor that has it.
		 */
		Map<WorldPoint, Integer> floors;

		Creature(String name, boolean monster, String levels, List<WorldPoint> tiles)
		{
			this(name, monster, levels, tiles, Collections.emptyMap());
		}

		Creature(String name, boolean monster, String levels, List<WorldPoint> tiles, Map<WorldPoint, Integer> floors)
		{
			this.name = name;
			this.monster = monster;
			this.levels = levels;
			this.tiles = tiles;
			this.floors = floors;
		}
	}

	private static volatile List<Creature> creatures = Collections.emptyList();

	private Spawns()
	{
	}

	static List<Creature> all()
	{
		return creatures;
	}

	/** Reads the list from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream in = Spawns.class.getResourceAsStream("spawns.tsv"))
		{
			if (in != null)
			{
				creatures = parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the spawns", e);
		}
	}

	static List<Creature> parse(InputStream in) throws IOException
	{
		List<Creature> out = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			List<WorldPoint> tiles = new ArrayList<>();
			Map<WorldPoint, Integer> floors = new HashMap<>();
			for (String tile : f[3].split(";"))
			{
				// x,y,plane, and the floor it is really on when that has no ground in the game's map
				String[] xyz = tile.split(",");
				if (xyz.length >= 3)
				{
					WorldPoint point = Prifddinas.toSurface(new WorldPoint(Integer.parseInt(xyz[0]),
						Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
					tiles.add(point);
					if (xyz.length > 3)
					{
						floors.put(point, Integer.parseInt(xyz[3]));
					}
				}
			}
			if (!tiles.isEmpty())
			{
				out.add(new Creature(f[1], "M".equals(f[0]), f[2].replace(",", ", "),
					Collections.unmodifiableList(tiles), floors.isEmpty() ? Collections.emptyMap() : floors));
			}
		}
		return Collections.unmodifiableList(out);
	}
}
