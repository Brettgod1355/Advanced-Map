/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Where the Wilderness levels are, as the game works them out for the level it shows on screen. The rules were read
 * from the game's client script that sets that text (RuneLite's published cs2 scripts, {@code wilderness_level}, at
 * runelite/cs2-scripts {@code 1d418ff}) and written here from scratch; they match all 471 ticks of a run through
 * levels 1 to 56 recorded by Black Box on 2026-10-25. Where the Wilderness starts (the ditch) and the Ferox Enclave's
 * "Level: --" come from the game's server, not from these rules, so they are not drawn.
 */
final class Wilderness
{
	/** The surface area the level counts up in, one level every 8 tiles north of y 3520. Max ends exclusive. */
	static final Rectangle SURFACE = new Rectangle(2944, 3520, 3392 - 2944, 3968 - 3520);
	/** The underground area the level counts up in, one level every 8 tiles north of y 9920. */
	static final Rectangle UNDERGROUND = new Rectangle(2944, 9920, 3456 - 2944, 10880 - 9920);
	/** The patch north-east of the surface Wilderness that is level 5 throughout. */
	static final Rectangle FLAT_FIVE = new Rectangle(3328, 3968, 3520 - 3328, 4160 - 3968);
	/** A square inside {@link #FLAT_FIVE} that is not part of it. */
	static final Rectangle FLAT_FIVE_HOLE = new Rectangle(3413, 4053, 22, 22);

	/**
	 * Dungeon regions whose level the game sets on its own rather than by the 8-tile count, as {region x, region y}.
	 * One of them (52, 160) counts 33 to 40 across the region (read as {@code scale(y % 64 - 6, 50, 7)}, that is
	 * times 7 over 50).
	 */
	static final List<int[]> OWN_LEVEL = Collections.unmodifiableList(Arrays.asList(
		new int[]{47, 158}, new int[]{51, 159}, new int[]{53, 159}, new int[]{52, 161},
		new int[]{27, 180}, new int[]{29, 180}, new int[]{25, 180}, new int[]{52, 160}));

	private Wilderness()
	{
	}

	/**
	 * The Wilderness level the game would show on a tile, or 0 outside the areas it counts in. Whether the tile is in
	 * the Wilderness at all is the server's call (the level shows only there).
	 */
	static int levelAt(int x, int y)
	{
		if (FLAT_FIVE.contains(x, y) && !FLAT_FIVE_HOLE.contains(x, y))
		{
			return 5;
		}
		if (x >= 2944 && x < 3392 && y >= 3520 && y < 4352)
		{
			return (y - 3520) / 8 + 1;
		}
		int regionX = x >> 6;
		int regionY = y >> 6;
		if (regionX == 47 && regionY == 158)
		{
			return (y - 9920) / 8 - 1;
		}
		if (regionY == 159 && (regionX == 51 || regionX == 53))
		{
			return 35;
		}
		if (regionX == 52 && regionY == 161)
		{
			return 40;
		}
		if (regionY == 180 && (regionX == 27 || regionX == 29))
		{
			return 21;
		}
		if (regionX == 25 && regionY == 180)
		{
			return 29;
		}
		if (regionX == 52 && regionY == 160)
		{
			return 33 + ((y & 63) - 6) * 7 / 50;
		}
		if (UNDERGROUND.contains(x, y))
		{
			return (y - 9920) / 8 + 1;
		}
		return 0;
	}

	/**
	 * Which level lines to draw at a zoom (screen pixels per tile): every level when they are far enough apart to
	 * read, else every 5th, else every 10th. Levels 20 and 30 (the teleport limits) are always drawn.
	 */
	static int lineStep(double zoom)
	{
		if (zoom >= 2)
		{
			return 1;
		}
		return zoom >= 0.75 ? 5 : 10;
	}

	/** True for the levels most teleports stop working above: 20, and 30 for the few that go further. */
	static boolean isTeleportLimit(int level)
	{
		return level == 20 || level == 30;
	}

	/** The area a map's level lines count in: {@link #SURFACE} or {@link #UNDERGROUND}. */
	static Rectangle counted(MapArea area)
	{
		return area == MapArea.SURFACE ? SURFACE : UNDERGROUND;
	}

	/** The first tile row of a level in an area's count; level 1 starts on the area's south edge. */
	static int levelStartY(Rectangle counted, int level)
	{
		return counted.y + 8 * (level - 1);
	}

	/** The highest level an area's count reaches before its north edge. */
	static int topLevel(Rectangle counted)
	{
		return (counted.height + 7) / 8;
	}

	/** True when a level's start gets a line at a step. Level 1 starts on the area's edge, drawn as the outline. */
	static boolean drawsLine(int level, int step)
	{
		return level > 1 && level % step == 0;
	}

	/**
	 * The text for a region in {@link #OWN_LEVEL}: "Level N", or "Level N-M" where it counts across the region.
	 * The rules only ever count up northwards, so the bottom and top rows give the range.
	 */
	static String ownLevelLabel(int regionX, int regionY)
	{
		int low = levelAt(regionX << 6, regionY << 6);
		int high = levelAt(regionX << 6, (regionY << 6) + 63);
		return low == high ? "Level " + low : "Level " + low + "-" + high;
	}
}
