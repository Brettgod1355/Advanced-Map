/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Preparing a region's ground floor saves its empty upper floors too, so they are not read again floor by floor. */
public class PrepareEmptyFloorsTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void preparingTheGroundSavesTheEmptyFloorsAboveItAndTheirPassesReadNothing() throws Exception
	{
		// flat grass: ground only, nothing on the floors above
		RegionImagesCacheTest.Flat flat = new RegionImagesCacheTest.Flat();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try
		{
			RegionImages images = new RegionImages(flat, executor, new MapIcons());
			MapStore store = MapStore.open(Filepath.Unchecked.getRooted(folder.getRoot().toPath()));
			images.setStore(store);
			int id = 50 << 8 | 50;
			images.prepare(RegionImages.key(0, id));
			int readsForTheGround = flat.draws.get();
			assertTrue(store.has(RegionImages.key(0, id)));
			for (int up = 1; up < RegionImages.PLANES; up++)
			{
				assertTrue("floor " + up + " saved as empty with the ground", store.isEmpty(RegionImages.key(up, id),
					flat.hash));
				assertTrue(images.isAbsent(up, id));
			}
			assertEquals("the ground's pictures and three empty markers", 4, store.count());
			// the preparer's later passes find them saved and read nothing
			for (int up = 1; up < RegionImages.PLANES; up++)
			{
				images.prepare(RegionImages.key(up, id));
			}
			assertEquals(readsForTheGround, flat.draws.get());
		}
		finally
		{
			executor.shutdownNow();
		}
	}
}
