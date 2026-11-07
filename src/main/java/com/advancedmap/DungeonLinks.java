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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Dungeon entrances the game's own map links leave out (its icon there leads nowhere, on RuneLite's map too), with
 * where they lead, from {@code dungeon-links.tsv}: Shortest Path's entrance rows, the OSRS Wiki's map coordinates, and
 * ways between a dungeon's floors the owner marked in game (the Kalphite Queen's chamber). Added to the game's links,
 * never in place of one; a link with no icon near gets the game's link icon.
 */
@Slf4j
final class DungeonLinks
{
	/** A row's own name for where it leads, when the nearest place name is wrong there; filled before {@link #EXTRA}. */
	private static final Map<Integer, String> NAMES = new HashMap<>();
	/** What going through needs ("Needs a rope..."), shown in the preview; filled before {@link #EXTRA} too. */
	private static final Map<Integer, Shortcuts.Need> NEEDS = new HashMap<>();
	private static final Set<Integer> VARS = new HashSet<>();
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

	/** The name the list gives where a link from this exact tile leads, or null. */
	static String name(int x, int y, int plane)
	{
		return NAMES.get(MapLinks.pack(x, y, plane));
	}

	/** What a link from within {@link RegionImages#LINK_REACH} of a tile needs, or null: an icon may sit beside it. */
	static Shortcuts.Need needs(int x, int y, int plane)
	{
		for (Map.Entry<Integer, Shortcuts.Need> need : NEEDS.entrySet())
		{
			int at = need.getKey();
			if (MapLinks.plane(at) == plane && Math.abs(MapLinks.x(at) - x) <= RegionImages.LINK_REACH
				&& Math.abs(MapLinks.y(at) - y) <= RegionImages.LINK_REACH)
			{
				return need.getValue();
			}
		}
		return null;
	}

	/** The varbits the links' needs are told by, for the player's progress to read. */
	static Set<Integer> needVars()
	{
		return VARS;
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
				int from = MapLinks.pack(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
				out.put(from, MapLinks.pack(Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5])));
				if (f.length > 7 && !f[7].isEmpty())
				{
					NAMES.put(from, f[7]);
				}
				Shortcuts.Need need = f.length > 8 ? Shortcuts.need(f[8], f.length > 9 ? f[9] : "", VARS) : null;
				if (need != null)
				{
					NEEDS.put(from, need);
				}
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
