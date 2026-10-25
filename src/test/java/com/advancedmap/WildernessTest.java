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
}
