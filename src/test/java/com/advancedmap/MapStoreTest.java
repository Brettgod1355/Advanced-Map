/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.image.BufferedImage;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapStoreTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Filepath root() throws Exception
	{
		// the plugin gets its folder from getPluginDirectory(); a test makes one in a temporary folder
		return Filepath.Unchecked.getRooted(folder.getRoot().toPath());
	}

	private static BufferedImage picture(int argb, int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < size; y++)
		{
			for (int x = 0; x < size; x++)
			{
				image.setRGB(x, y, argb);
			}
		}
		return image;
	}

	@Test
	public void aSavedRegionIsReadBackAfterARestart() throws Exception
	{
		int key = RegionImages.key(1, 12850);
		MapStore store = MapStore.open(root());
		store.save(key, 0xABCDEF12345L, picture(0xFF336699, 256), picture(0x80336699, 64));
		MapStore again = MapStore.open(root());
		assertTrue(again.has(key));
		BufferedImage full = again.load(key, 0xABCDEF12345L, MapStore.Kind.FULL);
		assertNotNull(full);
		assertEquals(256, full.getWidth());
		assertEquals(0xFF336699, full.getRGB(10, 10));
		assertEquals(0x80336699, again.load(key, 0xABCDEF12345L, MapStore.Kind.SMALL).getRGB(3, 3));
	}

	@Test
	public void aRegionTheGameChangedIsNotRead() throws Exception
	{
		int key = RegionImages.key(0, 12850);
		MapStore store = MapStore.open(root());
		store.save(key, 1, picture(0xFF000000, 8), picture(0xFF000000, 2));
		assertNull(store.load(key, 2, MapStore.Kind.FULL));
		// saving the new drawing replaces the old files
		store.save(key, 2, picture(0xFFFFFFFF, 8), picture(0xFFFFFFFF, 2));
		assertEquals(2, folder.getRoot().toPath().resolve("map-v" + MapStore.VERSION).toFile().list().length);
	}

	@Test
	public void picturesOfPrifddinassCopyAreDeletedOnOpening() throws Exception
	{
		// region 50, 94: inside the city's copy, which is drawn into Tirannwn now and left off the underground map
		int copy = RegionImages.key(0, 50 << 8 | 94);
		int surface = RegionImages.key(0, 12850);
		MapStore store = MapStore.open(root());
		store.save(copy, 5, picture(0xFF000000, 8), picture(0xFF000000, 2));
		store.save(surface, 5, picture(0xFF000000, 8), picture(0xFF000000, 2));
		MapStore again = MapStore.open(root());
		assertFalse(again.has(copy));
		assertTrue(again.has(surface));
		assertEquals(2, folder.getRoot().toPath().resolve("map-v" + MapStore.VERSION).toFile().list().length);
	}

	@Test
	public void anEmptyFloorIsRemembered() throws Exception
	{
		int key = RegionImages.key(3, 12850);
		MapStore store = MapStore.open(root());
		store.saveEmpty(key, 7);
		MapStore again = MapStore.open(root());
		assertTrue(again.isEmpty(key, 7));
		assertFalse(again.isEmpty(key, 8));
		assertNull(again.load(key, 7, MapStore.Kind.FULL));
	}

	@Test
	public void clearingDeletesEverything() throws Exception
	{
		MapStore store = MapStore.open(root());
		store.save(RegionImages.key(0, 1), 1, picture(0xFF000000, 8), picture(0xFF000000, 2));
		store.saveEmpty(RegionImages.key(2, 1), 1);
		store.clear();
		assertEquals(0, store.count());
		assertEquals(0, folder.getRoot().toPath().resolve("map-v" + MapStore.VERSION).toFile().list().length);
	}

	@Test
	public void shrinkingKeepsSeeThroughPartsSeeThrough()
	{
		BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
		// left half solid red, right half nothing
		image.setRGB(0, 0, 0xFFFF0000);
		image.setRGB(1, 0, 0xFFFF0000);
		image.setRGB(0, 1, 0xFFFF0000);
		image.setRGB(1, 1, 0xFFFF0000);
		BufferedImage small = RegionImages.shrink(image, 2);
		assertEquals(0xFFFF0000, small.getRGB(0, 0));
		assertEquals(0, small.getRGB(1, 0) >>> 24);
		// a square half see-through keeps its color, at half strength
		image.setRGB(2, 0, 0xFF00FF00);
		image.setRGB(3, 1, 0xFF00FF00);
		assertEquals(0x7F00FF00, RegionImages.shrink(image, 2).getRGB(1, 0));
	}

	@Test
	public void keysHoldTheFloorAndTheRegion()
	{
		int key = RegionImages.key(3, 12850);
		assertEquals(3, RegionImages.plane(key));
		assertEquals(12850, RegionImages.region(key));
	}

	@Test
	public void aFloorTwoClientsSavedFromDifferentGameDataKeepsOneSetOfFiles() throws Exception
	{
		int key = RegionImages.key(0, 12850);
		// both clients opened their store before either saved, so neither knew of the other's files
		MapStore first = MapStore.open(root());
		MapStore second = MapStore.open(root());
		first.save(key, 0x1111L, picture(0xFF336699, 256), picture(0x80336699, 64));
		second.save(key, 0x2222L, picture(0xFF996633, 256), picture(0x80996633, 64));
		assertEquals("both clients' pictures are on disk", 4, folder.getRoot().listFiles()[0].listFiles().length);
		MapStore again = MapStore.open(root());
		assertTrue(again.has(key));
		assertEquals("the next start keeps one of them", 2, folder.getRoot().listFiles()[0].listFiles().length);
		long kept = again.savedHash(key);
		assertNotNull(again.load(key, kept, MapStore.Kind.FULL));
		assertNotNull(again.load(key, kept, MapStore.Kind.SMALL));
	}

	@Test
	public void aFloorAnotherClientSavedMeanwhileIsFoundOnDisk() throws Exception
	{
		int key = RegionImages.key(0, 12850);
		MapStore other = MapStore.open(root());
		MapStore mine = MapStore.open(root());
		// the other client (another account, open at the same time) saves the floor after my store read the folder
		other.save(key, 0x1111L, picture(0xFF336699, 256), picture(0x80336699, 64));
		assertNotNull("read from disk, not drawn and saved again", mine.load(key, 0x1111L, MapStore.Kind.FULL));
		assertTrue(mine.has(key));
		assertNull("other game data: drawn afresh", mine.load(key, 0x2222L, MapStore.Kind.FULL));
		int emptyKey = RegionImages.key(2, 12850);
		other.saveEmpty(emptyKey, 0x1111L);
		assertTrue(mine.isEmpty(emptyKey, 0x1111L));
	}
}
