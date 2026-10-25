/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapImageRenderer;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.Region;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;

/**
 * The drawn pictures of map regions, made on background threads as the map needs them and kept at three sizes:
 * 4, 2 and 1 pixels per tile. The biggest are made first and shrunk for the others; only the smallest are kept
 * for the whole world, the bigger ones for the regions seen last.
 */
@Slf4j
class RegionImages
{
	enum Level
	{
		FULL(MapImageRenderer.MAP_SCALE, 128),
		HALF(2, 512),
		SMALL(1, Integer.MAX_VALUE);

		final int pixelsPerTile;
		final int kept;

		Level(int pixelsPerTile, int kept)
		{
			this.pixelsPerTile = pixelsPerTile;
			this.kept = kept;
		}

		/** The smallest pictures that still look sharp at a zoom (screen pixels per tile). */
		static Level forZoom(double zoom)
		{
			if (zoom >= 3)
			{
				return FULL;
			}
			return zoom >= 1.25 ? HALF : SMALL;
		}
	}

	private final MapSource source;
	private final MapImageRenderer renderer;
	private final ExecutorService executor;
	private final Map<Level, Map<Integer, BufferedImage>> images = new ConcurrentHashMap<>();
	private final Set<Integer> absent = ConcurrentHashMap.newKeySet();
	private final Set<Long> queued = ConcurrentHashMap.newKeySet();
	private volatile Set<Integer> wanted = Collections.emptySet();

	RegionImages(MapSource source, ExecutorService executor)
	{
		this.source = source;
		this.renderer = new MapImageRenderer(source);
		this.executor = executor;
		for (Level level : Level.values())
		{
			images.put(level, Collections.synchronizedMap(
				new LinkedHashMap<Integer, BufferedImage>(64, 0.75f, true)
				{
					@Override
					protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest)
					{
						return size() > level.kept;
					}
				}));
		}
	}

	/** The picture of a region at a level, or the nearest size there is, or null while none is made yet. */
	BufferedImage get(int regionId, Level level)
	{
		BufferedImage image = images.get(level).get(regionId);
		if (image != null)
		{
			return image;
		}
		for (Level other : Level.values())
		{
			image = images.get(other).get(regionId);
			if (image != null)
			{
				return image;
			}
		}
		return null;
	}

	/** True when the game has no map for a region (open sea beyond the map's edge, for one). */
	boolean isAbsent(int regionId)
	{
		return absent.contains(regionId);
	}

	/**
	 * Asks for the pictures of the regions on screen, most important first. Regions asked for earlier but no
	 * longer on screen are skipped when their turn comes.
	 */
	void request(Collection<Integer> regionIds, Level level)
	{
		wanted = new HashSet<>(regionIds);
		for (int id : regionIds)
		{
			if (absent.contains(id) || images.get(level).containsKey(id))
			{
				continue;
			}
			long key = (long) level.ordinal() << 32 | id;
			if (queued.add(key))
			{
				executor.execute(() -> make(id, level, key));
			}
		}
	}

	/** Lets the big pictures go; the map is closed. */
	void trim()
	{
		images.get(Level.FULL).clear();
		images.get(Level.HALF).clear();
	}

	private void make(int id, Level level, long key)
	{
		try
		{
			if (!wanted.contains(id) || images.get(level).containsKey(id))
			{
				return;
			}
			Region region = source.region(id >> 8, id & 0xFF);
			if (region == null)
			{
				absent.add(id);
				return;
			}
			BufferedImage full = renderer.drawRegion(region, 0);
			BufferedImage half = shrink(full, 2);
			if (level == Level.FULL)
			{
				images.get(Level.FULL).put(id, full);
			}
			images.get(Level.HALF).put(id, half);
			images.get(Level.SMALL).put(id, shrink(half, 2));
		}
		catch (CancellationException | CacheNotReadyException e)
		{
			// the plugin stopped, or the client is still downloading this part of the cache: asked again later
		}
		catch (RuntimeException e)
		{
			log.debug("could not draw region {}", id, e);
			absent.add(id);
		}
		finally
		{
			queued.remove(key);
		}
	}

	/** Averages each square of {@code by} x {@code by} pixels into one. */
	static BufferedImage shrink(BufferedImage image, int by)
	{
		int w = image.getWidth() / by;
		int h = image.getHeight() / by;
		int[] in = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		int[] out = new int[w * h];
		int n = by * by;
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int r = 0;
				int g = 0;
				int b = 0;
				for (int dy = 0; dy < by; dy++)
				{
					int row = (y * by + dy) * image.getWidth() + x * by;
					for (int dx = 0; dx < by; dx++)
					{
						int rgb = in[row + dx];
						r += rgb >> 16 & 0xFF;
						g += rgb >> 8 & 0xFF;
						b += rgb & 0xFF;
					}
				}
				out[y * w + x] = r / n << 16 | g / n << 8 | b / n;
			}
		}
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		small.setRGB(0, 0, w, h, out, 0, w);
		return small;
	}
}
