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

/**
 * The game's map icons Advanced Map names itself ({@code named-icons.tsv}): the Ancient Furnace, which the game's map
 * calls only "Furnace". Each has the wiki page about it.
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
	}

	private static final List<Named> ALL = load();

	private NamedIcons()
	{
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
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			out.add(new Named(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4]));
		}
		return Collections.unmodifiableList(out);
	}
}
