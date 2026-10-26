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
 * Which trees a rare trees icon stands for ("Yew tree", Woodcutting 60), as RuneLite's own world map shows them: the
 * list in {@code rare-trees.tsv}, transcribed from RuneLite's world map plugin (see THIRD_PARTY_NOTICES.md). The game's
 * map only says "Rare trees".
 */
@Slf4j
final class RareTrees
{
	/** How far from a rare trees icon the trees' listed spot may be. */
	static final int REACH = 8;

	@Value
	static class Tree
	{
		String name;
		int level;
		int x;
		int y;
		int plane;
	}

	private static volatile List<Tree> trees = Collections.emptyList();

	private RareTrees()
	{
	}

	/** Reads the list from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream in = RareTrees.class.getResourceAsStream("rare-trees.tsv"))
		{
			trees = in == null ? Collections.emptyList() : parse(in);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the rare trees", e);
		}
	}

	static List<Tree> parse(InputStream in) throws IOException
	{
		List<Tree> out = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] parts = line.split("\t");
			if (parts.length == 5)
			{
				out.add(new Tree(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
					Integer.parseInt(parts[3]), Integer.parseInt(parts[4])));
			}
		}
		return Collections.unmodifiableList(out);
	}

	/**
	 * The trees nearest a rare trees icon (all listed at that nearest spot), or an empty list when none is within
	 * reach.
	 */
	static List<Tree> near(int x, int y, int plane)
	{
		List<Tree> best = new ArrayList<>();
		int bestDistance = Integer.MAX_VALUE;
		for (Tree tree : trees)
		{
			int distance = Math.max(Math.abs(tree.x - x), Math.abs(tree.y - y));
			if (tree.plane != plane || distance > REACH || distance > bestDistance)
			{
				continue;
			}
			if (distance < bestDistance)
			{
				best.clear();
				bestDistance = distance;
			}
			best.add(tree);
		}
		return best;
	}
}
