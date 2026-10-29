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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Which rocks each mining site has ("2 Iron", "3 Coal"), as RuneLite's own world map shows them: the list in
 * {@code mining-sites.tsv}, transcribed from RuneLite's world map plugin (see THIRD_PARTY_NOTICES.md). The game's map
 * only says "Mining site".
 */
@Slf4j
final class MiningSites
{
	/** How far from a mining site icon the site's listed spot may be. */
	static final int REACH = 8;

	@Value
	static class Site
	{
		int x;
		int y;
		int plane;
		List<String> rocks;
	}

	private static volatile List<Site> sites = Collections.emptyList();

	private MiningSites()
	{
	}

	/** Reads the list from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream in = MiningSites.class.getResourceAsStream("mining-sites.tsv"))
		{
			sites = in == null ? Collections.emptyList() : parse(in);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the mining sites", e);
		}
	}

	static List<Site> parse(InputStream in) throws IOException
	{
		List<Site> out = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] parts = line.split("\t");
			if (parts.length == 4)
			{
				out.add(new Site(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
					Collections.unmodifiableList(Arrays.asList(parts[3].split(";")))));
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** The rocks of the site nearest a mining site icon, or an empty list when none is within reach. */
	static List<String> rocksNear(int x, int y, int plane)
	{
		Site best = null;
		int bestDistance = REACH + 1;
		for (Site site : sites)
		{
			int distance = Math.max(Math.abs(site.x - x), Math.abs(site.y - y));
			if (site.plane == plane && distance < bestDistance)
			{
				best = site;
				bestDistance = distance;
			}
		}
		return best == null ? Collections.emptyList() : best.rocks;
	}
}
