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
import java.util.Locale;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;

/**
 * Where a boat can dock and the Sailing level (and quest) it needs, from {@code sailing-docks.tsv}. The list has names
 * only: a dock is placed at the game's map label of its name, and a docking or port icon takes the nearest one.
 */
@Slf4j
final class SailingDocks
{
	/** How far a docking icon may lie from its place's label: a label stands in the middle of a town or island. */
	static final int REACH = 250;

	@Value
	static class Dock
	{
		String name;
		int level;
		/** The quest it also needs, or null. */
		Quest quest;
	}

	@Value
	private static class Placed
	{
		Dock dock;
		int x;
		int y;
	}

	private static final List<Dock> DOCKS = load();
	private static volatile List<Placed> placed = Collections.emptyList();

	private SailingDocks()
	{
	}

	static List<Dock> all()
	{
		return DOCKS;
	}

	/** Places each dock at the game's labels of its name; called again whenever the labels are read. */
	static void place(List<MapLabels.Label> labels)
	{
		List<Placed> out = new ArrayList<>();
		for (MapLabels.Label label : labels)
		{
			String text = String.join(" ", label.getLines()).trim().toLowerCase(Locale.ROOT);
			for (Dock dock : DOCKS)
			{
				if (dock.name.toLowerCase(Locale.ROOT).equals(text))
				{
					out.add(new Placed(dock, label.getX(), label.getY()));
				}
			}
		}
		placed = Collections.unmodifiableList(out);
	}

	/** The dock whose label is nearest a docking or port icon, within {@link #REACH} tiles; null when none is. */
	static Dock near(int x, int y)
	{
		Placed best = null;
		long bestDistance = (long) REACH * REACH + 1;
		for (Placed at : placed)
		{
			long dx = at.x - x;
			long dy = at.y - y;
			if (dx * dx + dy * dy < bestDistance)
			{
				best = at;
				bestDistance = dx * dx + dy * dy;
			}
		}
		return best == null ? null : best.dock;
	}

	private static List<Dock> load()
	{
		try (InputStream in = SailingDocks.class.getResourceAsStream("sailing-docks.tsv"))
		{
			if (in != null)
			{
				return parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the Sailing docks", e);
		}
		return Collections.emptyList();
	}

	static List<Dock> parse(InputStream in) throws IOException
	{
		List<Dock> out = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			out.add(new Dock(f[0], Integer.parseInt(f[1]), f.length > 2 ? quest(f[2]) : null));
		}
		return Collections.unmodifiableList(out);
	}

	private static Quest quest(String name)
	{
		for (Quest quest : Quest.values())
		{
			if (quest.getName().equals(name))
			{
				return quest;
			}
		}
		return null;
	}
}
