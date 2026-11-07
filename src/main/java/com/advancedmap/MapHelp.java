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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The fixed text of the map's menu pages (help, how the map is built, what's new, where to write), from
 * {@code menu-pages.txt}: in a data file, where it costs no Plugin Hub tokens (owner, 2026-11-07: "can we move the
 * text to data files instead of java?").
 */
@Slf4j
final class MapHelp
{
	private static final Map<String, List<String[]>> PAGES = read();

	private MapHelp()
	{
	}

	/** A page's blocks in order, each {kind, text}: "#" a heading, "-" an item of a list, "" a paragraph. */
	static List<String[]> page(String name)
	{
		return PAGES.getOrDefault(name, Collections.emptyList());
	}

	/** A page's list items, in order. */
	static List<String> items(String name)
	{
		List<String> out = new ArrayList<>();
		for (String[] block : page(name))
		{
			if (block[0].equals("-"))
			{
				out.add(block[1]);
			}
		}
		return out;
	}

	private static Map<String, List<String[]>> read()
	{
		Map<String, List<String[]>> out = new HashMap<>();
		try (InputStream in = MapHelp.class.getResourceAsStream("menu-pages.txt"))
		{
			List<String[]> page = new ArrayList<>();
			for (String line : DataFiles.lines(in, "# "))
			{
				if (line.startsWith("= "))
				{
					page = new ArrayList<>();
					out.put(line.substring(2), page);
				}
				else if (line.startsWith("## ") || line.startsWith("- "))
				{
					page.add(new String[]{line.substring(0, 1), line.substring(line.indexOf(' ') + 1)});
				}
				else
				{
					page.add(new String[]{"", line});
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the menu pages", e);
		}
		return out;
	}
}
