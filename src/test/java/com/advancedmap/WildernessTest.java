/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WildernessTest
{
	/**
	 * Tiles from a run through every level on 2026-10-25, recorded by Black Box (format 13): x, y, and the level the
	 * game showed there. Each is the first tile the run reached at that level.
	 */
	private static final int[][] RUN = {
		{3100, 3527, 1}, {3100, 3535, 2}, {3102, 3591, 9}, {3102, 3599, 10}, {3059, 3664, 19}, {3061, 3673, 20},
		{3062, 3681, 21}, {3071, 3744, 29}, {3069, 3753, 30}, {3069, 3761, 31}, {3007, 3873, 45}, {2968, 3953, 55},
		{2974, 3961, 56}};

	@Test
	public void theLevelMatchesWhatTheGameShowedOnTheRun()
	{
		for (int[] tile : RUN)
		{
			assertEquals("at " + tile[0] + ", " + tile[1], tile[2], Wilderness.levelAt(tile[0], tile[1]));
		}
	}

	@Test
	public void outsideTheWildernessThereIsNoLevel()
	{
		// Lumbridge, and Edgeville just south of the ditch's row
		assertEquals(0, Wilderness.levelAt(3222, 3218));
		assertEquals(0, Wilderness.levelAt(3094, 3491));
		// west of the Wilderness, beside it
		assertEquals(0, Wilderness.levelAt(2943, 3700));
	}

	@Test
	public void theNorthEastPatchIsLevelFiveButNotItsHole()
	{
		assertEquals(5, Wilderness.levelAt(3340, 4000));
		assertEquals(0, Wilderness.levelAt(3420, 4060));
	}

	@Test
	public void underTheWildernessTheLevelCountsFromY9920()
	{
		// as the game's script reads; not yet checked in game
		assertEquals(1, Wilderness.levelAt(3100, 9920));
		assertEquals(11, Wilderness.levelAt(3100, 10000));
		// a region with a level of its own
		assertEquals(35, Wilderness.levelAt(51 * 64 + 10, 159 * 64 + 10));
		int[] counted = {52, 160};
		assertTrue(Wilderness.levelAt(counted[0] * 64, counted[1] * 64 + 63) <= 40);
	}

	@Test
	public void linesThinOutWhenZoomedOut()
	{
		assertEquals(1, Wilderness.lineStep(4));
		assertEquals(5, Wilderness.lineStep(1));
		assertEquals(10, Wilderness.lineStep(0.25));
		assertTrue(Wilderness.isTeleportLimit(20));
		assertTrue(Wilderness.isTeleportLimit(30));
	}

	@Test
	public void eachLineSitsWhereTheLevelChanges()
	{
		for (MapArea area : MapArea.values())
		{
			java.awt.Rectangle counted = Wilderness.counted(area);
			// west of the patches and regions with levels of their own
			int x = counted.x + 2;
			for (int level = 2; level <= Wilderness.topLevel(counted); level++)
			{
				int y = Wilderness.levelStartY(counted, level);
				assertEquals(area + " row " + y, level, Wilderness.levelAt(x, y));
				assertEquals(area + " row " + (y - 1), level - 1, Wilderness.levelAt(x, y - 1));
			}
			assertEquals(0, Wilderness.levelAt(x, counted.y - 1));
		}
		assertEquals(56, Wilderness.topLevel(Wilderness.SURFACE));
		// the run reached 56 at 3961, the last row of the surface count
		assertEquals(56, Wilderness.levelAt(2974, Wilderness.SURFACE.y + Wilderness.SURFACE.height - 1));
	}

	@Test
	public void theTeleportLimitLinesSitAboveLevels20And30()
	{
		// level 21 starts where the run first showed it, and 31 likewise
		assertEquals(3681, Wilderness.levelStartY(Wilderness.SURFACE, 21) + 1);
		assertEquals(21, Wilderness.levelAt(3062, Wilderness.levelStartY(Wilderness.SURFACE, 21)));
		assertEquals(20, Wilderness.levelAt(3062, Wilderness.levelStartY(Wilderness.SURFACE, 21) - 1));
		assertEquals(31, Wilderness.levelAt(3069, Wilderness.levelStartY(Wilderness.SURFACE, 31)));
		assertEquals(30, Wilderness.levelAt(3069, Wilderness.levelStartY(Wilderness.SURFACE, 31) - 1));
	}

	@Test
	public void onlyLevelsOnTheStepGetALine()
	{
		assertTrue(Wilderness.drawsLine(2, 1));
		assertTrue(!Wilderness.drawsLine(1, 1));
		assertTrue(Wilderness.drawsLine(15, 5));
		assertTrue(!Wilderness.drawsLine(16, 5));
		assertTrue(!Wilderness.drawsLine(15, 10));
	}

	@Test
	public void regionsWithALevelOfTheirOwnAreLabeledWithTheirRange()
	{
		assertEquals("Level 35", Wilderness.ownLevelLabel(51, 159));
		assertEquals("Level 40", Wilderness.ownLevelLabel(52, 161));
		assertEquals("Level 33-40", Wilderness.ownLevelLabel(52, 160));
		assertEquals("Level 23-30", Wilderness.ownLevelLabel(47, 158));
		assertEquals("Level 29", Wilderness.ownLevelLabel(25, 180));
	}
}
