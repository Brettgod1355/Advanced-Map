/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** What a floor does when the game's cache is not ready, or a read of it fails, part way through. */
public class RegionImagesRetryTest
{
	private static final int REGION = 50 << 8 | 50;
	private static final int KEY = RegionImages.key(0, REGION);

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final ExecutorService executor = Executors.newFixedThreadPool(2);

	@After
	public void stop()
	{
		executor.shutdownNow();
	}

	private MapStore store() throws Exception
	{
		return MapStore.open(Filepath.Unchecked.getRooted(folder.getRoot().toPath()));
	}

	/** Paints until the drawing has rested, as the overlay would; false when it never did. */
	private static boolean settle(RegionImages images, RegionImages.Level level, int millis) throws InterruptedException
	{
		int rested = 0;
		for (int i = 0; i < millis / 10 && rested < 10; i++)
		{
			images.request(0, Collections.singletonList(REGION), level);
			images.get(0, REGION, level);
			rested = images.isBusy() ? 0 : rested + 1;
			TimeUnit.MILLISECONDS.sleep(10);
		}
		return rested >= 10;
	}

	@Test
	public void theCacheNotReadyIsPassedOnByPrepareSoThePreparerKeepsTheRegion()
	{
		RegionImagesCacheTest.Flat source = new RegionImagesCacheTest.Flat();
		source.notReady = true;
		RegionImages images = new RegionImages(source, executor, new MapIcons());
		try
		{
			images.setStore(store());
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
		try
		{
			images.prepare(KEY);
			fail("the preparer must hear that the region is not there yet, else it drops it as done");
		}
		catch (CacheNotReadyException expected)
		{
			// put back in the queue
		}
	}
}
