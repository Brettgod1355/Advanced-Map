/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * The player's Slayer task on the map: the task RuneLite's Slayer plugin saved for the player (read only, under its
 * own setting names), and every spawn of the monsters it covers, matched as that plugin matches them: the task's name
 * without its last "s", or one of its other targets ({@code slayer-tasks.tsv}, from that plugin's task list), as whole
 * words of a monster's name.
 */
@Slf4j
final class SlayerTask
{
	static final String GROUP = "slayer";
	static final String TASK_NAME = "taskName";
	static final String AMOUNT = "amount";
	static final String LOCATION = "taskLocation";
	static final String LAYER = "advancedmap:slayer";
	static final Color MARK = new Color(0xC35BFF);

	private static volatile Map<String, List<String>> targets = Collections.emptyMap();

	private SlayerTask()
	{
	}

	static void load()
	{
		try (InputStream in = SlayerTask.class.getResourceAsStream("slayer-tasks.tsv"))
		{
			if (in != null)
			{
				targets = parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the slayer tasks", e);
		}
	}

	static Map<String, List<String>> parse(InputStream in) throws IOException
	{
		Map<String, List<String>> out = new HashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			out.put(f[0].toLowerCase(Locale.ROOT), f.length > 1 && !f[1].isEmpty() ? Arrays.asList(f[1].split(";"))
				: Collections.emptyList());
		}
		return out;
	}

	/** The monsters a task covers, from the spawns Advanced Map knows. */
	static List<Spawns.Creature> creatures(String task)
	{
		List<Pattern> wanted = new ArrayList<>();
		wanted.add(word(task.replaceAll("s$", "")));
		for (String other : targets.getOrDefault(task.toLowerCase(Locale.ROOT), Collections.emptyList()))
		{
			wanted.add(word(other));
		}
		List<Spawns.Creature> out = new ArrayList<>();
		for (Spawns.Creature creature : Spawns.all())
		{
			if (!creature.isMonster())
			{
				continue;
			}
			for (Pattern pattern : wanted)
			{
				if (pattern.matcher(creature.getName()).find())
				{
					out.add(creature);
					break;
				}
			}
		}
		return out;
	}

	/** Every spawn of a task's monsters. */
	static List<WorldPoint> spawns(String task)
	{
		List<WorldPoint> out = new ArrayList<>();
		for (Spawns.Creature creature : creatures(task))
		{
			out.addAll(creature.getTiles());
		}
		return out;
	}

	private static Pattern word(String name)
	{
		return Pattern.compile("(?:\\s|^)" + Pattern.quote(name) + "(?:\\s|$)", Pattern.CASE_INSENSITIVE);
	}

	/** The task's spawns marked, each area's middle bigger, the nearest area's named with the task and what is left. */
	static MapLayer layer(List<SearchSpots.Area> areas, List<WorldPoint> spawns, String label)
	{
		List<MapLayer.Marker> marks = new ArrayList<>();
		for (WorldPoint spawn : spawns)
		{
			marks.add(new MapLayer.Marker(spawn, MARK, MapLayer.Shape.DIAMOND, 8, null));
		}
		for (int i = 0; i < areas.size(); i++)
		{
			// the task named once, at the nearest area; the others get a bigger mark only
			marks.add(new MapLayer.Marker(areas.get(i).getPoint(), MARK, MapLayer.Shape.DIAMOND, 12, i == 0 ? label : null));
		}
		return new MapLayer(LAYER, 70, Collections.emptyList(), Collections.unmodifiableList(marks));
	}
}
