/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapLoader;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.MapSprite;
import com.advancedmap.cache.ObjectDefinition;
import com.advancedmap.cache.OverlayDefinition;
import com.advancedmap.cache.Region;
import com.advancedmap.cache.UnderlayDefinition;
import com.advancedmap.cache.UnderlayLoader;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RegionImagesCacheTest
{
	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	@After
	public void stop()
	{
		executor.shutdownNow();
	}

	/** Game data where every region is flat grass, so each draws a picture. */
	/** Flat grass everywhere, with switches for what the game's cache can do at a given moment. */
	static final class Flat implements MapSource
	{
		/** The client has not downloaded the regions yet: every read throws {@link CacheNotReadyException}. */
		volatile boolean notReady;
		/** A failure reading a region (thrown by the hash read), or null. */
		volatile RuntimeException failWith;
		/** What the game data hashes to, as if a game update changed it. */
		volatile long hash = 1;

		private static final byte[] TERRAIN = flatTerrain();
		private final UnderlayDefinition grass = new UnderlayLoader().load(0, new byte[]{1, 0x4C, 0x7A, 0x2B, 0});

		private static byte[] flatTerrain()
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			for (int z = 0; z < Region.Z; z++)
			{
				for (int i = 0; i < Region.X * Region.Y; i++)
				{
					if (z == 0)
					{
						// attribute 82: underlay 1
						out.write(0);
						out.write(82);
					}
					out.write(0);
					out.write(0);
				}
			}
			return out.toByteArray();
		}

		private final java.util.Map<Integer, Region> regions = new java.util.concurrent.ConcurrentHashMap<>();

		@Override
		public Region region(int regionX, int regionY)
		{
			if (notReady)
			{
				throw new CacheNotReadyException();
			}
			return regions.computeIfAbsent(regionX << 8 | regionY, id ->
			{
				Region region = new Region(regionX, regionY);
				region.loadTerrain(new MapLoader().load(regionX, regionY, TERRAIN));
				return region;
			});
		}

		/** How many times a region's floor was drawn from this data. */
		final java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();

		@Override
		public long regionHash(int regionX, int regionY)
		{
			if (notReady)
			{
				throw new CacheNotReadyException();
			}
			if (failWith != null)
			{
				throw failWith;
			}
			draws.incrementAndGet();
			return hash;
		}

		@Override
		public UnderlayDefinition underlay(int id)
		{
			return grass;
		}

		@Override
		public OverlayDefinition overlay(int id)
		{
			throw new AssertionError("nothing is drawn here");
		}

		@Override
		public ObjectDefinition object(int id)
		{
			throw new AssertionError("nothing is drawn here");
		}

		@Override
		public int textureColor(int textureId)
		{
			return 0;
		}

		@Override
		public MapSprite mapScene(int index)
		{
			return null;
		}

		@Override
		public MapSprite areaIcon(int areaId)
		{
			return null;
		}
	}

	@Test
	public void everyFloorOnScreenStaysDrawnEvenWhenThereAreMoreThanTheCacheKeeps() throws Exception
	{
		// a big view at the default zoom: more regions than the FULL level keeps (128)
		List<Integer> onScreen = new ArrayList<>();
		for (int rx = 40; rx < 56; rx++)
		{
			for (int ry = 50; ry < 59; ry++)
			{
				onScreen.add(rx << 8 | ry);
			}
		}
		assertEquals(144, onScreen.size());
		Flat source = new Flat();
		RegionImages images = new RegionImages(source, executor, new MapIcons());
		// as the overlay does each paint: asks for every region in view, then reads each one's picture; on until
		// every floor has been drawn and the drawing has rested (a slow machine takes longer, so a long limit)
		int rested = 0;
		for (int i = 0; i < 3000 && rested < 20; i++)
		{
			images.request(0, onScreen, RegionImages.Level.FULL);
			for (int id : onScreen)
			{
				images.get(0, id, RegionImages.Level.FULL);
			}
			rested = source.draws.get() >= onScreen.size() && !images.isBusy() ? rested + 1 : 0;
			TimeUnit.MILLISECONDS.sleep(10);
		}
		// each floor drawn once; before, the pictures pushed out of the cache were drawn again and again
		assertEquals(onScreen.size(), source.draws.get());
	}
}
