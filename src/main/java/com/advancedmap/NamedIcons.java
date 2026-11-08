/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;

/**
 * The game's map icons Advanced Map names itself ({@code named-icons.tsv}): the Ancient Furnace, which the game's map
 * calls only "Furnace", the Gemstone Crab's caves, only "Dungeon", and each Agility course, only "Agility training",
 * with the level it needs. Each has the wiki page about it.
 */
@Slf4j
final class NamedIcons
{
	/** How far the game's icon may be from the listed tile. */
	static final int REACH = 2;

	@Value
	static class Named
	{
		String name;
		int x;
		int y;
		int plane;
		/** The wiki page's title, with underscores ("Ancient_Furnace"). */
		String wikiPage;
		/** The skill levels the place needs, shown in its info box (an Agility course's); none for most. */
		List<Need> needs;
	}

	/** A skill level a named place needs, for one part of it ("Advanced course") or the whole (no label). */
	@Value
	static class Need
	{
		/** The part it is for, or "" for the whole place. */
		String label;
		Skill skill;
		int level;
	}

	private static final List<Named> ALL = load();

	private NamedIcons()
	{
	}

	/** Every icon Advanced Map names, for the search. */
	static List<Named> all()
	{
		return ALL;
	}

	/** The name Advanced Map gives the icon on a tile, or null. */
	static Named at(int x, int y, int plane)
	{
		for (Named named : ALL)
		{
			if (named.plane == plane && Math.abs(named.x - x) <= REACH && Math.abs(named.y - y) <= REACH)
			{
				return named;
			}
		}
		return null;
	}

	private static List<Named> load()
	{
		try (InputStream in = NamedIcons.class.getResourceAsStream("named-icons.tsv"))
		{
			if (in != null)
			{
				return parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the named icons", e);
		}
		return Collections.emptyList();
	}

	static List<Named> parse(InputStream in) throws IOException
	{
		List<Named> out = new ArrayList<>();
		for (String[] f : DataFiles.rows(in))
		{
			out.add(new Named(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4],
				f.length > 5 ? needs(f[5]) : Collections.emptyList()));
		}
		return Collections.unmodifiableList(out);
	}

	/**
	 * The needs column: "Agility 75" for the whole place, or parts with their own, "Basic course: Agility 1;Advanced
	 * course: Agility 45". Empty for none.
	 */
	static List<Need> needs(String column)
	{
		List<Need> out = new ArrayList<>();
		for (String part : column.split(";"))
		{
			part = part.trim();
			if (part.isEmpty())
			{
				continue;
			}
			int colon = part.indexOf(':');
			String label = colon < 0 ? "" : part.substring(0, colon).trim();
			String[] skillLevel = part.substring(colon + 1).trim().split(" ");
			out.add(new Need(label, Skill.valueOf(skillLevel[0].toUpperCase()), Integer.parseInt(skillLevel[1])));
		}
		return Collections.unmodifiableList(out);
	}
}
