/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapLinks;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Dungeon entrances the game's own map links leave out (its icon there leads nowhere, on RuneLite's map too), with
 * where they lead, from {@code dungeon-links.tsv}: Shortest Path's entrance rows and the OSRS Wiki's map coordinates.
 * Added to the game's links, never in place of one.
 */
@Slf4j
final class DungeonLinks
{
	private static final Map<Integer, Integer> EXTRA = read();

	private DungeonLinks()
	{
	}

	/** The game's links with the ones it leaves out added, both packed as {@link MapLinks#pack}. */
	static Map<Integer, Integer> withExtra(Map<Integer, Integer> links)
	{
		Map<Integer, Integer> out = new HashMap<>(EXTRA);
		out.putAll(links);
		return Collections.unmodifiableMap(out);
	}

	/** The links the list adds. */
	static Map<Integer, Integer> extra()
	{
		return EXTRA;
	}

	private static Map<Integer, Integer> read()
	{
		Map<Integer, Integer> out = new HashMap<>();
		try (InputStream in = DungeonLinks.class.getResourceAsStream("dungeon-links.tsv"))
		{
			if (in == null)
			{
				return Collections.emptyMap();
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] f = line.split("\t");
				out.put(MapLinks.pack(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])),
					MapLinks.pack(Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5])));
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the dungeon links", e);
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(out);
	}
}
