/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.MapSprite;
import com.advancedmap.cache.ObjectDefinition;
import com.advancedmap.cache.OverlayDefinition;
import com.advancedmap.cache.Region;
import com.advancedmap.cache.UnderlayDefinition;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.runelite.client.util.Filepath;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;

/**
 * After a restart the saved map shows at once, before the game's data is read; a picture the data no longer matches
 * is drawn again once it has been checked.
 */
public class SavedMapFirstTest
{
	private static final int REGION = 50 << 8 | 50;
	private static final long SAVED = 0x1234L;

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final ExecutorService executor = Executors.newSingleThreadExecutor();

	@After
	public void stop()
	{
		executor.shutdownNow();
	}

	/** Game data that is not there yet (the client still loading), or there with a fingerprint. */
	private static final class Source implements MapSource
	{
		final boolean ready;
		final long hash;

		Source(boolean ready, long hash)
		{
			this.ready = ready;
			this.hash = hash;
		}

		@Override
		public Region region(int regionX, int regionY)
		{
			if (!ready)
			{
				throw new CacheNotReadyException();
			}
			return new Region(regionX, regionY);
		}

		@Override
		public long regionHash(int regionX, int regionY)
		{
			return hash;
		}

		@Override
		public UnderlayDefinition underlay(int id)
		{
			throw new AssertionError("nothing is drawn here");
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

	private RegionImages images(Source source) throws Exception
	{
		MapStore store = MapStore.open(Filepath.Unchecked.getRooted(folder.getRoot().toPath()));
		BufferedImage full = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		full.setRGB(5, 5, 0xFF336699);
		store.save(RegionImages.key(0, REGION), SAVED, full, RegionImages.shrink(RegionImages.shrink(full, 2), 2));
		RegionImages images = new RegionImages(source, executor, new MapIcons());
		images.setStore(MapStore.open(Filepath.Unchecked.getRooted(folder.getRoot().toPath())));
		return images;
	}

	private static BufferedImage await(RegionImages images) throws Exception
	{
		for (int i = 0; i < 100; i++)
		{
			images.request(0, Collections.singletonList(REGION), RegionImages.Level.FULL);
			BufferedImage image = images.get(0, REGION, RegionImages.Level.FULL);
			if (image != null)
			{
				return image;
			}
			TimeUnit.MILLISECONDS.sleep(20);
		}
		return null;
	}

	@Test
	public void theSavedPictureShowsBeforeTheGameDataIsRead() throws Exception
	{
		// the client cannot answer yet: before, nothing showed until it could
		BufferedImage image = await(images(new Source(false, 0)));
		assertNotNull("the saved picture shows", image);
		assertEquals(0xFF336699, image.getRGB(5, 5));
	}

	@Test
	public void aPictureFromOlderGameDataIsDrawnAgainOnceChecked() throws Exception
	{
		RegionImages images = images(new Source(true, SAVED + 1));
		// the saved picture may already be gone by the time it is looked at (the check is quick here); the map keeps
		// asking, as the overlay does each frame, until the picture is the one drawn again
		BufferedImage now = await(images);
		for (int i = 0; i < 100 && (now == null || now.getRGB(5, 5) == 0xFF336699); i++)
		{
			TimeUnit.MILLISECONDS.sleep(20);
			now = await(images);
		}
		assertNotNull(now);
		assertNotEquals("drawn again from the game's data", 0xFF336699, now.getRGB(5, 5));
	}
}
