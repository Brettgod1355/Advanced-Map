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
import java.util.EnumMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.AgilityShortcut;

/**
 * What {@code shortcuts.tsv} adds to RuneLite's agility shortcuts: where the map's icon is, for a shortcut RuneLite
 * places where the game's own map draws its dungeon elsewhere (the Kalphite Lair's crevice), and what else it needs.
 */
@Slf4j
final class Shortcuts
{
	private static final Map<AgilityShortcut, WorldPoint> PLACES = new EnumMap<>(AgilityShortcut.class);
	private static final Map<AgilityShortcut, String> NEEDS = new EnumMap<>(AgilityShortcut.class);

	static
	{
		try (InputStream in = Shortcuts.class.getResourceAsStream("shortcuts.tsv"))
		{
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] f = line.split("\t", -1);
				AgilityShortcut shortcut = AgilityShortcut.valueOf(f[0]);
				if (!f[1].isEmpty())
				{
					PLACES.put(shortcut, new WorldPoint(Integer.parseInt(f[1]), Integer.parseInt(f[2]), 0));
				}
				if (!f[3].isEmpty())
				{
					NEEDS.put(shortcut, f[3]);
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the shortcuts", e);
		}
	}

	private Shortcuts()
	{
	}

	/** Where a shortcut's icon is on the map: the list's place, else RuneLite's; null when neither knows. */
	static WorldPoint place(AgilityShortcut shortcut)
	{
		return PLACES.getOrDefault(shortcut, shortcut.getWorldMapLocation());
	}

	/** What else a shortcut needs ("Needs the Elite Desert Diary"), or null. */
	static String needs(AgilityShortcut shortcut)
	{
		return NEEDS.get(shortcut);
	}
}
