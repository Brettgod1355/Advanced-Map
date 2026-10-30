/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The far view asks for the small picture of every region of the world; AM-02 keeps every floor on screen, so the
 * half-size pictures made beside them had nothing to bound them (700 regions: 43 MB of pictures the far view never
 * reads, held for the session).
 */
public class RegionImagesHalfCacheTest
{
	private final ExecutorService executor = Executors.newFixedThreadPool(4);

	@After
	public void stop()
	{
		executor.shutdownNow();
	}

	private void drawAll(RegionImages images, RegionImagesCacheTest.Flat source, List<Integer> onScreen, int expectedDraws) throws Exception
	{
		int rested = 0;
		for (int i = 0; i < 6000 && rested < 10; i++)
		{
			images.request(0, onScreen, RegionImages.Level.SMALL);
			for (int id : onScreen)
			{
				images.get(0, id, RegionImages.Level.SMALL);
			}
			rested = source.draws.get() >= expectedDraws && !images.isBusy() ? rested + 1 : 0;
			TimeUnit.MILLISECONDS.sleep(10);
		}
	}

	private static int halfPictures(RegionImages images, List<Integer> ids)
	{
		int n = 0;
		for (int id : ids)
		{
			BufferedImage image = images.get(0, id, RegionImages.Level.HALF);
			if (image != null && image.getWidth() == 64 * RegionImages.Level.HALF.pixelsPerTile)
			{
				n++;
			}
		}
		return n;
	}

	@Test
	public void theFarViewKeepsOnlyTheSmallPictures() throws Exception
	{
		// zoom 0.25 shows the whole surface (52 x 36 slots); here 35 x 20 = 700 drawn regions, more than HALF keeps (512)
		List<Integer> onScreen = new ArrayList<>();
		for (int rx = 20; rx < 55; rx++)
		{
			for (int ry = 40; ry < 60; ry++)
			{
				onScreen.add(rx << 8 | ry);
			}
		}
		assertEquals(700, onScreen.size());
		RegionImagesCacheTest.Flat source = new RegionImagesCacheTest.Flat();
		RegionImages images = new RegionImages(source, executor, new MapIcons());
		drawAll(images, source, onScreen, onScreen.size());
		int half = halfPictures(images, onScreen);
		assertTrue("half-size pictures past the level's bound: " + half, half <= RegionImages.Level.HALF.kept);
	}
}
