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
import java.util.List;
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
			for (String tile : f[3].split(";"))
			{
				String[] xyz = tile.split(",");
				if (xyz.length == 3)
				{
					tiles.add(Prifddinas.toSurface(new WorldPoint(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]),
						Integer.parseInt(xyz[2]))));
				}
			}
			if (!tiles.isEmpty())
			{
				out.add(new Creature(f[1], "M".equals(f[0]), f[2].replace(",", ", "),
					Collections.unmodifiableList(tiles)));
			}
		}
		return Collections.unmodifiableList(out);
	}
}
