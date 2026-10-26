/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.Location;
import com.advancedmap.cache.MapImageRenderer;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.Region;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;

/**
 * The drawn pictures of map regions, one per floor, made on background threads as the map needs them and kept at
 * three sizes: 4, 2 and 1 pixels per tile. The biggest are made first and shrunk for the others; only the smallest are
 * kept in memory for the whole world, the bigger ones for the regions seen last. With a {@link MapStore}, pictures are
 * read from disk when the game data they were drawn from has not changed, and saved once drawn. The game's map icons
 * are left out of the pictures: each region's are handed to {@link MapIcons}, which draws them so they can be clicked.
 * <p>
 * A region's floor is a key: {@code plane << 16 | regionId}.
 */
@Slf4j
class RegionImages
{
	static final int PLANES = 4;

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
	private final MapIcons icons;
	private final Set<Integer> iconsFound = ConcurrentHashMap.newKeySet();
	private final Map<Level, Map<Integer, BufferedImage>> images = new ConcurrentHashMap<>();
	private final Set<Integer> absent = ConcurrentHashMap.newKeySet();
	private final Set<Long> queued = ConcurrentHashMap.newKeySet();
	private volatile Set<Integer> wanted = Collections.emptySet();
	private volatile MapStore store;

	RegionImages(MapSource source, ExecutorService executor, MapIcons icons)
	{
		this.source = source;
		this.renderer = new MapImageRenderer(source);
		this.executor = executor;
		this.icons = icons;
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

	static int key(int plane, int regionId)
	{
		return plane << 16 | regionId;
	}

	static int plane(int key)
	{
		return key >>> 16;
	}

	static int region(int key)
	{
		return key & 0xFFFF;
	}

	/** From now on, read pictures from disk and save new ones there. */
	void setStore(MapStore store)
	{
		this.store = store;
	}

	MapStore store()
	{
		return store;
	}

	/** The picture of a region's floor at a level, or the nearest size there is, or null while none is made yet. */
	BufferedImage get(int plane, int regionId, Level level)
	{
		int key = key(plane, regionId);
		BufferedImage image = images.get(level).get(key);
		if (image != null)
		{
			return image;
		}
		for (Level other : Level.values())
		{
			image = images.get(other).get(key);
			if (image != null)
			{
				return image;
			}
		}
		return null;
	}

	/** True when the game has nothing for a region's floor (open sea beyond the map's edge, a floor never built). */
	boolean isAbsent(int plane, int regionId)
	{
		return absent.contains(key(plane, regionId)) || absent.contains(key(0, regionId));
	}

	/** True while pictures asked for are still being made. */
	boolean isBusy()
	{
		return !queued.isEmpty();
	}

	/**
	 * Asks for the pictures of the regions on screen, most important first: the floor shown, then each floor below it
	 * (drawn dimmed underneath). Regions asked for earlier but no longer on screen are skipped when their turn comes.
	 */
	void request(int plane, Collection<Integer> regionIds, Level level)
	{
		Set<Integer> keys = new HashSet<>();
		for (int id : regionIds)
		{
			for (int p = plane; p >= 0; p--)
			{
				keys.add(key(p, id));
			}
		}
		wanted = keys;
		for (int id : regionIds)
		{
			for (int p = plane; p >= 0; p--)
			{
				int key = key(p, id);
				if (isAbsent(p, id) || images.get(level).containsKey(key))
				{
					continue;
				}
				long job = (long) level.ordinal() << 32 | key;
				if (queued.add(job))
				{
					try
					{
						executor.execute(() -> make(key, level, job));
					}
					catch (RejectedExecutionException e)
					{
						// the plugin is stopping; a window may still repaint once before it closes
						queued.remove(job);
						return;
					}
				}
			}
		}
	}

	/** Lets the biggest pictures go when the map closes; the smaller ones stay, so it opens again at once. */
	void trim()
	{
		images.get(Level.FULL).clear();
	}

	private void make(int key, Level level, long job)
	{
		try
		{
			if (!wanted.contains(key) || images.get(level).containsKey(key))
			{
				return;
			}
			Drawn drawn = draw(key, level);
			if (drawn == null)
			{
				return;
			}
			if (drawn.full != null && level == Level.FULL)
			{
				images.get(Level.FULL).put(key, drawn.full);
			}
			if (drawn.full != null)
			{
				images.get(Level.HALF).put(key, shrink(drawn.full, 2));
			}
			images.get(Level.SMALL).put(key, drawn.small);
		}
		catch (CancellationException | CacheNotReadyException e)
		{
			// the plugin stopped, or the client is still downloading this part of the cache: asked again later
		}
		catch (RuntimeException e)
		{
			log.debug("could not draw region {} floor {}", region(key), plane(key), e);
			absent.add(key);
		}
		finally
		{
			queued.remove(job);
		}
	}

	/**
	 * Draws a region's floor ahead of time, for the disk only: nothing is kept in memory. Does nothing when it is
	 * saved already. Render threads.
	 */
	void prepare(int key)
	{
		MapStore saved = store;
		if (saved == null || saved.has(key))
		{
			return;
		}
		try
		{
			draw(key, Level.SMALL);
		}
		catch (CancellationException | CacheNotReadyException e)
		{
			// tried again on a later pass
		}
		catch (RuntimeException e)
		{
			log.debug("could not prepare region {} floor {}", region(key), plane(key), e);
		}
	}

	/** A floor's pictures: the full one only when drawn or wanted, the smallest always. */
	private static final class Drawn
	{
		final BufferedImage full;
		final BufferedImage small;

		Drawn(BufferedImage full, BufferedImage small)
		{
			this.full = full;
			this.small = small;
		}
	}

	/**
	 * The pictures of a region's floor, from disk when they were saved from the same game data, else drawn (and
	 * saved). Null when the game has nothing there.
	 */
	private Drawn draw(int key, Level level)
	{
		int plane = plane(key);
		int id = region(key);
		Region region = source.region(id >> 8, id & 0xFF);
		if (region == null)
		{
			absent.add(key(0, id));
			return null;
		}
		if (!iconsFound.contains(id))
		{
			findIcons(region);
			iconsFound.add(id);
		}
		long hash = source.regionHash(id >> 8, id & 0xFF);
		MapStore saved = store;
		if (saved != null && saved.isEmpty(key, hash))
		{
			absent.add(key);
			return null;
		}
		if (plane > 0 && !hasFloor(region, plane))
		{
			absent.add(key);
			if (saved != null)
			{
				saved.saveEmpty(key, hash);
			}
			return null;
		}
		if (saved != null)
		{
			BufferedImage small = level == Level.SMALL ? saved.load(key, hash, MapStore.Kind.SMALL) : null;
			if (small != null)
			{
				return new Drawn(null, small);
			}
			BufferedImage full = saved.load(key, hash, MapStore.Kind.FULL);
			if (full != null)
			{
				return new Drawn(full, shrink(shrink(full, 2), 2));
			}
		}
		BufferedImage full = renderer.drawRegion(region, plane);
		BufferedImage small = shrink(shrink(full, 2), 2);
		if (saved != null)
		{
			saved.save(key, hash, full, small);
		}
		return new Drawn(full, small);
	}

	/** True when a region has ground or anything placed on a floor above the ground. */
	static boolean hasFloor(Region region, int plane)
	{
		for (int x = 0; x < Region.X; x++)
		{
			for (int y = 0; y < Region.Y; y++)
			{
				if (region.getUnderlayId(plane, x, y) > 0 || region.getOverlayId(plane, x, y) > 0)
				{
					return true;
				}
			}
		}
		for (Location location : region.getLocations())
		{
			if (location.getPosition().getZ() == plane)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Hands the icons a region's objects put on the game's map, on every floor, to {@link MapIcons}. An icon is on the
	 * floor it is drawn on: an object over a bridge tile is stored a floor up but drawn a floor lower, as the map is.
	 */
	private void findIcons(Region region)
	{
		List<MapSpot> spots = new ArrayList<>();
		for (Location location : region.getLocations())
		{
			int areaId = source.object(location.getId()).getMapAreaId();
			if (areaId == -1)
			{
				continue;
			}
			int category = source.areaCategory(areaId);
			if (category == IconGroup.MAP_LINK)
			{
				continue;
			}
			icons.addSprite(areaId, source.areaIcon(areaId));
			int x = location.getPosition().getX();
			int y = location.getPosition().getY();
			spots.add(MapSpot.icon(x, y, floorOf(region, x, y, location.getPosition().getZ()), areaId, category));
		}
		icons.addRegion(region.getRegionID(), spots);
	}

	/** The floor an object on a tile is drawn on: a floor lower over a bridge tile. */
	static int floorOf(Region region, int x, int y, int z)
	{
		boolean bridge = (region.getTileSetting(1, x - region.getBaseX(), y - region.getBaseY()) & 2) != 0;
		return bridge && z > 0 ? z - 1 : z;
	}

	/** Averages each square of {@code by} x {@code by} pixels into one, see-through parts included. */
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
				int a = 0;
				int r = 0;
				int g = 0;
				int b = 0;
				for (int dy = 0; dy < by; dy++)
				{
					int row = (y * by + dy) * image.getWidth() + x * by;
					for (int dx = 0; dx < by; dx++)
					{
						int argb = in[row + dx];
						int alpha = argb >>> 24;
						a += alpha;
						r += (argb >> 16 & 0xFF) * alpha;
						g += (argb >> 8 & 0xFF) * alpha;
						b += (argb & 0xFF) * alpha;
					}
				}
				out[y * w + x] = a == 0 ? 0 : a / n << 24 | r / a << 16 | g / a << 8 | b / a;
			}
		}
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		small.setRGB(0, 0, w, h, out, 0, w);
		return small;
	}
}
