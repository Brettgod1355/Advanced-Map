/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * What a searched monster is like, from {@code monsters.tsv} and {@code drops.tsv} (the OSRS Wiki's monster and drop
 * tables; see THIRD_PARTY_NOTICES.md): hitpoints, max hit, how it attacks, the Slayer level and masters, what it is
 * weak to, and every drop with its rate.
 */
@Slf4j
final class MonsterInfo
{
	static final String WIKI = "https://oldschool.runescape.wiki/w/";
	private static final Color STATS = new Color(0xC8C2B0);
	private static final Color SLAYER = new Color(0xE0B060);
	/** The headings over a monster's drops, by the group {@code drops.tsv} gives each (in that order). */
	private static final Map<String, String> GROUPS = Map.of("A", "Always", "S", "Its own drops", "R", "Rare drops",
		"C", "Other drops");
	/** The widest a line under a row may be: the list's width less its margins. */
	private static final int LINE_WIDTH = SideList.WIDTH - 24;

	@Value
	static class Monster
	{
		String name;
		/** "150", or "190-250" over the monster's versions; empty when not known. */
		String hitpoints;
		String maxHit;
		String styles;
		/** "Aggressive", "Poisonous", "Aggressive Venomous" or empty. */
		String traits;
		String slayerLevel;
		String masters;
		String weakTo;
	}

	/** A row's target: a click opens this wiki page in the browser. */
	@Value
	static class WikiPage
	{
		String url;
	}

	private static volatile Map<String, Monster> monsters = Collections.emptyMap();
	/** Each monster's drops in the order shown: group, item, rate. */
	private static volatile Map<String, List<String[]>> drops = Collections.emptyMap();

	private MonsterInfo()
	{
	}

	/** Reads the list from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream in = MonsterInfo.class.getResourceAsStream("monsters.tsv"))
		{
			if (in != null)
			{
				monsters = parse(in);
			}
			Map<String, List<String[]>> read = new HashMap<>();
			for (String[] row : DataFiles.rows("drops.tsv"))
			{
				if (row.length > 3)
				{
					read.computeIfAbsent(row[0], k -> new ArrayList<>()).add(row);
				}
			}
			drops = read;
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the monster info", e);
		}
	}

	static Map<String, Monster> parse(InputStream in) throws IOException
	{
		Map<String, Monster> out = new HashMap<>();
		for (String[] row : DataFiles.rows(in))
		{
			String[] f = Arrays.copyOf(row, 8);
			for (int i = 0; i < f.length; i++)
			{
				f[i] = f[i] == null ? "" : f[i];
			}
			out.put(f[0], new Monster(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7]));
		}
		return Collections.unmodifiableMap(out);
	}

	/** The monster of that name, or null when there is nothing known of it. */
	static Monster of(String name)
	{
		return monsters.get(name);
	}

	static String wikiUrl(String name)
	{
		try
		{
			return WIKI + URLEncoder.encode(name.replace(' ', '_'), StandardCharsets.UTF_8.name());
		}
		catch (UnsupportedEncodingException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/**
	 * The rows that tell of a monster at the top of its list: its stats, its Slayer level and masters, its drops, then
	 * the button to its wiki page. Every line is wrapped whole-word to the list's width; nothing is cut short.
	 */
	static List<SideList.Row> rows(Monster monster)
	{
		List<SideList.Row> rows = new ArrayList<>();
		List<String> stats = new ArrayList<>();
		if (!monster.getMaxHit().isEmpty())
		{
			stats.add("Max hit " + monster.getMaxHit());
		}
		if (!monster.getStyles().isEmpty())
		{
			stats.add("Attacks with " + monster.getStyles().toLowerCase(Locale.ROOT));
		}
		if (!monster.getTraits().isEmpty())
		{
			stats.add(monster.getTraits().replace(" ", ", "));
		}
		if (!monster.getWeakTo().isEmpty())
		{
			stats.add("Weak to " + monster.getWeakTo().toLowerCase(Locale.ROOT));
		}
		rows.add(row(monster.getHitpoints().isEmpty() ? "Stats" : "Hitpoints " + monster.getHitpoints(), stats, STATS));
		if (!monster.getSlayerLevel().isEmpty() || !monster.getMasters().isEmpty())
		{
			rows.add(row(monster.getSlayerLevel().isEmpty() ? "Slayer task" : "Slayer level " + monster.getSlayerLevel(),
				monster.getMasters().isEmpty() ? Collections.emptyList()
					: Collections.singletonList("Assigned by " + monster.getMasters()), SLAYER));
		}
		return rows;
	}

	/**
	 * Every drop of a monster with its rate, under a heading for each group (owner, 2026-11-10): what it always drops,
	 * what only it and a few others drop, the rare ones, then the rest, most common first.
	 */
	static List<SideList.Row> drops(String name)
	{
		List<SideList.Row> rows = new ArrayList<>();
		String group = null;
		for (String[] drop : drops.getOrDefault(name, Collections.emptyList()))
		{
			if (!drop[1].equals(group))
			{
				group = drop[1];
				rows.add(SideList.Row.heading(GROUPS.getOrDefault(group, "Drops")));
			}
			rows.add(new SideList.Row(drop[2], drop[3], null, null, null, null));
		}
		return rows;
	}

	private static SideList.Row row(String text, List<String> lines, Color color)
	{
		List<String> wrapped = new ArrayList<>();
		for (String line : lines)
		{
			wrapped.addAll(SideList.wrap(line, LINE_WIDTH));
		}
		return new SideList.Row(text, null, wrapped.isEmpty() ? null : String.join("; ", wrapped), color, null, null);
	}
}
