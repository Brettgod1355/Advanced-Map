/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

	@org.junit.Rule
	public org.junit.rules.TemporaryFolder folder = new org.junit.rules.TemporaryFolder();

	@Test
	public void theCountLeavesOutFloorsSavedWithTheirGroundAndKeepsTheOneBeingBuilt() throws Exception
	{
		// two regions of flat ground, nothing above: each ground saves its three empty floors with it
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
		{
			RegionImagesCacheTest.Flat flat = new RegionImagesCacheTest.Flat();
			RegionImages images = new RegionImages(flat, executor, new MapIcons());
			MapStore store = MapStore.open(net.runelite.client.util.Filepath.Unchecked.getRooted(
				folder.getRoot().toPath()));
			images.setStore(store);
			MapPreparer preparer = new MapPreparer(images, flat, () -> true, () -> LUMBRIDGE);
			java.util.List<Integer> regions = java.util.Arrays.asList(50 << 8 | 50, 51 << 8 | 50);
			int first = preparer.next(store, regions);
			assertEquals(0, RegionImages.plane(first));
			assertEquals("eight floors, the one being built among them", 8, preparer.remaining());
			images.prepare(first);
			preparer.finished(first);
			// its ground and its three empty floors are saved; only the other region's four are left
			assertEquals(4, store.count());
			assertEquals(4, preparer.remaining());
			int last = preparer.next(store, regions);
			images.prepare(last);
			// built, but not yet finished (its data may be missing and it may go back): it still counts
			assertEquals(4, preparer.remaining());
			preparer.finished(last);
			assertEquals(0, preparer.remaining());
			assertEquals(8, store.count());
			assertEquals("nothing left to take", null, preparer.next(store, regions));
			// a floor put back for want of its data counts once, never twice, while it goes back
			preparer.restart();
			java.util.List<Integer> fresh = java.util.Collections.singletonList(52 << 8 | 50);
			int ground = preparer.next(store, fresh);
			assertEquals(4, preparer.remaining());
			preparer.requeue(ground);
			assertEquals(4, preparer.remaining());
			preparer.finished(ground);
			assertEquals(4, preparer.remaining());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void unreadableMapDataStopsTheBuildingWithTheListKept() throws Exception
	{
		// a game update changed a format the plugin reads: nothing can be built until the plugin is updated, so the
		// preparer stops with its list whole (the count says what is left), rather than run through it saving nothing
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
		{
			RegionImagesCacheTest.Flat flat = new RegionImagesCacheTest.Flat();
			flat.regionIds = java.util.Arrays.asList(50 << 8 | 50, 51 << 8 | 50);
			RegionImages images = new RegionImages(flat, executor, new MapIcons());
			MapStore store = MapStore.open(net.runelite.client.util.Filepath.Unchecked.getRooted(
				folder.getRoot().toPath()));
			images.setStore(store);
			MapPreparer preparer = new MapPreparer(images, flat, () -> true, () -> LUMBRIDGE);
			flat.failWith = new MapDataException(new RuntimeException("a changed format"));
			assertFalse(preparer.stepOnce());
			assertTrue(preparer.isHalted());
			assertEquals("eight floors listed, the failed one back among them", 8, preparer.remaining());
			assertEquals(0, store.count());
			assertFalse("resting: nothing is tried for a while", preparer.stepOnce());
			assertEquals(8, preparer.remaining());
			// "Build the map again" (or a plugin that reads the data again) starts afresh
			flat.failWith = null;
			preparer.restart();
			assertFalse(preparer.isHalted());
			assertTrue(preparer.stepOnce());
			assertEquals(4, store.count());
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
