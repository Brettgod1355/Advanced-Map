/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MapPreparerTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	private static long order(int plane, int worldX, int worldY, WorldPoint around)
	{
		return MapPreparer.order(RegionImages.key(plane, (worldX >> 6) << 8 | worldY >> 6), around);
	}

	@Test
	public void nearerRegionsComeFirst()
	{
		long lumbridge = order(0, 3222, 3218, LUMBRIDGE);
		long varrock = order(0, 3213, 3424, LUMBRIDGE);
		long ardougne = order(0, 2662, 3305, LUMBRIDGE);
		assertTrue(lumbridge < varrock && varrock < ardougne);
	}

	@Test
	public void theGroundOfThePlayersMapComesBeforeTheOtherMapAndTheFloors()
	{
		// even the far side of the surface before the nearest dungeon, and every ground before any floor
		long farSurface = order(0, 1300, 3600, LUMBRIDGE);
		long nearDungeon = order(0, 3222, 3218 + 6400, LUMBRIDGE);
		long nearFloor = order(1, 3222, 3218, LUMBRIDGE);
		assertTrue(farSurface < nearDungeon && nearDungeon < nearFloor);
	}

	@Test
	public void underTheSurfaceTheRegionsBelowThePlayerComeFirst()
	{
		// in Lumbridge, the swamp caves under it (about 3200, 9550) before Taverley Dungeon (about 2884, 9800)
		assertTrue(order(0, 3200, 9550, LUMBRIDGE) < order(0, 2884, 9800, LUMBRIDGE));
		// and in a dungeon, the dungeon's map first
		WorldPoint inDungeon = new WorldPoint(3096, 9867, 0);
		assertTrue(order(0, 3096, 9867, inDungeon) < order(0, 3096, 3467, inDungeon));
	}
}
