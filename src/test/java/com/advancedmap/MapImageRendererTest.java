/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapImageRenderer;
import com.advancedmap.cache.MapLoader;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.MapSprite;
import com.advancedmap.cache.ObjectDefinition;
import com.advancedmap.cache.OverlayDefinition;
import com.advancedmap.cache.Region;
import com.advancedmap.cache.UnderlayDefinition;
import com.advancedmap.cache.UnderlayLoader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * The renderer on a made-up region. Its output on real game data was compared with RuneLite's own MapImageDumper
 * on a 5x5 region area around Lumbridge: equal apart from the inter-map link icons, which Advanced Map leaves out
 * for now.
 */
public class MapImageRendererTest
{
	private static final int GRASS = 0x4C7A2B;

	/** A region whose surface is one underlay everywhere and whose upper planes are empty. */
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

	private static final class FlatSource implements MapSource
	{
		final Region region = new Region(50, 50);
		final UnderlayDefinition grass = new UnderlayLoader().load(0,
			new byte[]{1, (byte) (GRASS >> 16), (byte) (GRASS >> 8), (byte) GRASS, 0});

		FlatSource()
		{
			region.loadTerrain(new MapLoader().load(50, 50, flatTerrain()));
		}

		@Override
		public Region region(int regionX, int regionY)
		{
			return regionX == 50 && regionY == 50 ? region : null;
		}

		@Override
		public UnderlayDefinition underlay(int id)
		{
			return grass;
		}

		@Override
		public OverlayDefinition overlay(int id)
		{
			throw new AssertionError("no overlays here");
		}

		@Override
		public ObjectDefinition object(int id)
		{
			throw new AssertionError("no objects here");
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
	public void aFlatRegionIsOneGroundColorAtFourPixelsPerTile()
	{
		FlatSource source = new FlatSource();
		BufferedImage image = new MapImageRenderer(source).drawRegion(source.region, 0);
		assertEquals(64 * MapImageRenderer.MAP_SCALE, image.getWidth());
		assertEquals(64 * MapImageRenderer.MAP_SCALE, image.getHeight());
		int color = image.getRGB(0, 0);
		assertNotEquals("the ground is drawn", 0xFF000000, color);
		for (int y = 0; y < image.getHeight(); y += 7)
		{
			for (int x = 0; x < image.getWidth(); x += 7)
			{
				assertEquals(color, image.getRGB(x, y));
			}
		}
	}

	@Test
	public void shrinkingAveragesEachSquare()
	{
		BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, 0x000000);
		image.setRGB(1, 0, 0xFFFFFF);
		image.setRGB(0, 1, 0x000000);
		image.setRGB(1, 1, 0xFFFFFF);
		for (int y = 0; y < 2; y++)
		{
			for (int x = 2; x < 4; x++)
			{
				image.setRGB(x, y, 0x204060);
			}
		}
		BufferedImage small = RegionImages.shrink(image, 2);
		assertEquals(2, small.getWidth());
		assertEquals(1, small.getHeight());
		assertEquals(0x7F7F7F, small.getRGB(0, 0) & 0xFFFFFF);
		assertEquals(0x204060, small.getRGB(1, 0) & 0xFFFFFF);
	}
}
