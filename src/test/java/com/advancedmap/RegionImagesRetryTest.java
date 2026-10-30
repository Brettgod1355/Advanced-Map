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

	@Test
	public void aFloorWithAPictureKeepsItWhenABiggerDrawFails() throws Exception
	{
		RegionImagesCacheTest.Flat source = new RegionImagesCacheTest.Flat();
		RegionImages images = new RegionImages(source, executor, new MapIcons());
		assertTrue(settle(images, RegionImages.Level.SMALL, 10_000));
		assertNotNull(images.get(0, REGION, RegionImages.Level.SMALL));
		// zoomed in: the bigger picture's read fails (an error inside the client's read, a damaged file): the small
		// picture must stay on screen, not give way to sea for the session, and the failure is not retried each paint
		source.failWith = new IllegalStateException("the client's read failed");
		assertTrue(settle(images, RegionImages.Level.FULL, 10_000));
		assertFalse("a floor that was drawn is not one the game has nothing for", images.isAbsent(0, REGION));
		assertNotNull(images.get(0, REGION, RegionImages.Level.FULL));
		int draws = source.draws.get();
		assertTrue(settle(images, RegionImages.Level.FULL, 500));
		assertTrue("not asked again every paint", source.draws.get() == draws);
	}

	@Test
	public void aSavedFloorWhoseCheckFoundTheCacheNotReadyIsCheckedAgainWhileShown() throws Exception
	{
		// a picture saved from older game data (hash 1); the game's data has changed since (hash 2)
		MapStore store = store();
		java.awt.image.BufferedImage full = new java.awt.image.BufferedImage(256, 256,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		store.save(KEY, 1L, full, RegionImages.shrink(full, 4));
		RegionImagesCacheTest.Flat source = new RegionImagesCacheTest.Flat();
		source.hash = 2;
		source.notReady = true;
		RegionImages images = new RegionImages(source, executor, new MapIcons());
		images.setStore(store);
		// shown from disk at once; the check behind it finds the client has not got the region yet
		assertTrue(settle(images, RegionImages.Level.SMALL, 10_000));
		assertNotNull(images.get(0, REGION, RegionImages.Level.SMALL));
		assertTrue(source.draws.get() == 0);
		// the client downloads it: while the floor is on screen, the check runs again and the stale picture is redrawn
		source.notReady = false;
		boolean redrawn = false;
		for (int i = 0; i < 600 && !redrawn; i++)
		{
			images.request(0, Collections.singletonList(REGION), RegionImages.Level.SMALL);
			redrawn = source.draws.get() > 0;
			TimeUnit.MILLISECONDS.sleep(10);
		}
		assertTrue("the saved picture must be compared with the game's data once it can be", redrawn);
	}
}
