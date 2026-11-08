/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Value;
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
	/**
	 * A line of what something needs ("Needs the Elite Desert Diary"), and the game's varbit that is 1 once the player
	 * has it (a diary's "complete" varbit), or -1 when the line is only told.
	 */
	@Value
	static class Need
	{
		String text;
		int varbit;
	}

	private static final Map<AgilityShortcut, WorldPoint> PLACES = new EnumMap<>(AgilityShortcut.class);
	private static final Map<AgilityShortcut, Need> NEEDS = new EnumMap<>(AgilityShortcut.class);
	private static final Set<Integer> VARS = new HashSet<>();

	static
	{
		try (InputStream in = Shortcuts.class.getResourceAsStream("shortcuts.tsv"))
		{
			for (String[] f : DataFiles.rows(in))
			{
				// a row at a time: a shortcut a RuneLite update renames or drops loses only its own row
				try
				{
					AgilityShortcut shortcut = AgilityShortcut.valueOf(f[0]);
					if (!f[1].isEmpty())
					{
						PLACES.put(shortcut, new WorldPoint(Integer.parseInt(f[1]), Integer.parseInt(f[2]), 0));
					}
					Need need = need(f[3], f.length > 4 ? f[4] : "", VARS);
					if (need != null)
					{
						NEEDS.put(shortcut, need);
					}
				}
				catch (RuntimeException e)
				{
					log.warn("Advanced Map: skipped the shortcut {} in shortcuts.tsv", f[0], e);
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

	/** A need from a list's two columns, its varbit added to {@code vars}; null for no text. */
	static Need need(String text, String varbit, Set<Integer> vars)
	{
		if (text.isEmpty())
		{
			return null;
		}
		int var = varbit.isEmpty() ? -1 : Integer.parseInt(varbit);
		if (var >= 0)
		{
			vars.add(var);
		}
		return new Need(text, var);
	}

	/** Where a shortcut's icon is on the map: the list's place, else RuneLite's; null when neither knows. */
	static WorldPoint place(AgilityShortcut shortcut)
	{
		return PLACES.getOrDefault(shortcut, shortcut.getWorldMapLocation());
	}

	/** What else a shortcut needs, or null. */
	static Need needs(AgilityShortcut shortcut)
	{
		return NEEDS.get(shortcut);
	}

	/** The varbits the shortcuts' needs are told by, for the player's progress to read. */
	static Set<Integer> needVars()
	{
		return java.util.Collections.unmodifiableSet(VARS);
	}
}
