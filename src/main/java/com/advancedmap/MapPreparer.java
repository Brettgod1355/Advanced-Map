/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Draws the regions not saved yet in the background and saves them, from the moment the plugin starts (installed, or
 * the client launched), so the map is ready when it is first opened. Nearest the player first (Lumbridge before
 * logging in), the ground of the player's map, then the other map's, then the floors above; the order follows the
 * player as they move. One region after another on a thread of its own at the lowest priority, so the game and the
 * map's own drawing come first, and paused while the map is drawing what is on screen. After a game update that
 * changed a region, it is drawn again when next looked at; one that changed the tables every region is drawn from
 * leaves every saved floor stale, and they are all built again here in the background.
 */
@Slf4j
final class MapPreparer
{
	/** Waits this long after the plugin starts, so the client's own start comes first. */
	private static final long START_DELAY_MILLIS = 10_000;
	/** A breath between regions, or between runs of them when the preparing goes fast. */
	private static final long STEP_MILLIS = 25;
	/** How long a fast step keeps drawing before its breath. */
	static final long FAST_NANOS = 250_000_000L;
	/**
	 * Four cores or more: the game keeps one or two busy, so the preparing need not breathe after each region. At the
	 * lowest priority still, so on Windows the game comes first; on fewer cores it breathes after each one. Java's
	 * common pool has one thread fewer than there are cores. (A higher priority was tried, and gained nothing: the
	 * reads from the game's cache, a few milliseconds of each frame, set the pace.)
	 */
	private static final boolean MANY_CORES = ForkJoinPool.getCommonPoolParallelism() >= 3;

	private final RegionImages images;
	private final MapSource source;
	private final BooleanSupplier enabled;
	private final Supplier<WorldPoint> focus;
	private final ScheduledExecutorService worker;
	/** Floors of regions still to prepare, in order; ticker thread, and {@link #restart} and {@link #remaining}. */
	private final List<Integer> todo = new ArrayList<>();
	/** The floor taken from the list and being built now, or null. */
	private Integer building;
	/** Regions whose icons are still to be found, nearest the player first; null until listed. */
	private volatile List<Integer> iconTodo;
	private volatile boolean listed;
	/** The region the list was last sorted around. */
	private int sortedFor = -1;
	/** What the tables every region is drawn from hashed to when the list was made: a saved floor with another is stale. */
	private long stamp;

	MapPreparer(RegionImages images, MapSource source, BooleanSupplier enabled, Supplier<WorldPoint> focus)
	{
		this.images = images;
		this.source = source;
		this.enabled = enabled;
		this.focus = focus;
		this.worker = Executors.newSingleThreadScheduledExecutor(task ->
		{
			Thread thread = new Thread(task, "Advanced Map preparer");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY);
			return thread;
		});
	}

	void start()
	{
		worker.scheduleWithFixedDelay(this::step, START_DELAY_MILLIS, STEP_MILLIS, TimeUnit.MILLISECONDS);
	}

	void stop()
	{
		worker.shutdownNow();
	}

	/** Lists the regions again, after the saved map was deleted. */
	synchronized void restart()
	{
		todo.clear();
		building = null;
		halted = false;
		failed = 0;
		restUntil = 0;
		listed = false;
		iconTodo = null;
	}

	/** When a floor of a region the client had not downloaded was put back: the next steps rest a while after it. */
	private volatile long restUntil;
	/** How long the preparer rests after a region the client has not got, so the client thread is not asked 40 times a second. */
	static final long REST_NANOS = 1_000_000_000L;
	/**
	 * The game's map data could not be read (a game update changed its format): nothing can be built until the plugin
	 * is updated. The list is kept, so the count says what is left, and a step is tried again now and then in case.
	 */
	private volatile boolean halted;
	static final long HALT_NANOS = 60_000_000_000L;

	/** True while the game's map data cannot be read: the building has stopped, nothing was lost. */
	boolean isHalted()
	{
		return halted;
	}

	/** Floors left out because they could not be drawn (the log says why); counted so "ready" can say so. */
	private volatile int failed;

	int failed()
	{
		return failed;
	}

	private synchronized void halt(int key)
	{
		requeue(key);
		halted = true;
		restUntil = System.nanoTime() + HALT_NANOS;
	}

	synchronized void requeue(int key)
	{
		// back in the list and no longer being built, in one step: never counted twice
		if (building != null && building == key)
		{
			building = null;
		}
		todo.add(key);
		restUntil = System.nanoTime() + REST_NANOS;
	}

	/**
	 * A floor taken from the list is done (drawn, or put back): it no longer counts as being built. A region's ground
	 * saves its empty floors above with it (RegionImages.markEmptyFloors), so they leave the list now rather than
	 * when their turn comes, and the count says what is really left.
	 */
	synchronized void finished(int key)
	{
		if (building != null && building == key)
		{
			building = null;
		}
		MapStore store = images.store();
		if (RegionImages.plane(key) != 0 || store == null)
		{
			return;
		}
		for (int up = 1; up < RegionImages.PLANES; up++)
		{
			Integer floor = RegionImages.key(up, RegionImages.region(key));
			if (store.isCurrent(floor, stamp))
			{
				todo.remove(floor);
			}
		}
	}

	/**
	 * How many regions' floors are still to prepare, the one being built included; -1 before the list is made. Never
	 * 0 while the last floor is still being built, or about to be put back for want of its data.
	 */
	synchronized int remaining()
	{
		return listed ? todo.size() + (building != null ? 1 : 0) : -1;
	}

	private void step()
	{
		long until = System.nanoTime() + FAST_NANOS;
		while (stepOnce() && MANY_CORES && System.nanoTime() - until < 0)
		{
			// the next region at once; the map's own drawing still stops it (stepOnce)
		}
	}

	/** Draws and saves one region's floor, or finds one region's icons: true if it did. */
	boolean stepOnce()
	{
		try
		{
			MapStore store = images.store();
			if (images.isBusy() || System.nanoTime() - restUntil < 0)
			{
				return false;
			}
			Integer key = enabled.getAsBoolean() && store != null ? next(store) : null;
			if (key != null)
			{
				try
				{
					if (!images.prepare(key))
					{
						// left out, not tried again: the log says what went wrong with it
						failed++;
					}
					return true;
				}
				catch (CacheNotReadyException e)
				{
					// the client has not downloaded this part of the cache yet: last in the queue, not forgotten
					requeue(key);
					return false;
				}
				catch (MapDataException e)
				{
					// nothing of the map can be read: stopped, with the floor and the rest of the list kept
					halt(key);
					return false;
				}
				finally
				{
					finished(key);
				}
			}
			// with nothing (more) to draw, the icons of the regions not drawn in this session, for search
			Integer region = nextIconRegion();
			if (region != null)
			{
				try
				{
					images.scanIcons(region);
					return true;
				}
				catch (CacheNotReadyException e)
				{
					requeueIcons(region);
				}
			}
		}
		catch (CancellationException e)
		{
			// the plugin is stopping
		}
		catch (RuntimeException e)
		{
			log.debug("could not prepare the map", e);
		}
		return false;
	}

	/** The next region whose icons to find, listing the regions first. */
	private Integer nextIconRegion()
	{
		// the listing waits for the client thread: outside the monitor, which the sidebar's refresh takes each second
		List<Integer> regions = iconTodo == null ? source.regionIds() : null;
		if (regions != null)
		{
			images.setRegionsInGame(regions);
		}
		synchronized (this)
		{
			if (iconTodo == null)
			{
				if (regions == null || regions.isEmpty())
				{
					return null;
				}
				WorldPoint around = focus.get();
				List<Integer> sorted = new ArrayList<>(regions);
				sorted.sort(Comparator.comparingLong(region -> order(RegionImages.key(0, region), around)));
				iconTodo = sorted;
			}
			return iconTodo.isEmpty() ? null : iconTodo.remove(0);
		}
	}

	private synchronized void requeueIcons(int region)
	{
		if (iconTodo != null)
		{
			iconTodo.add(region);
		}
	}

	/** The next floor of a region to prepare, listing them first and sorting again when the player has moved. */
	private Integer next(MapStore store)
	{
		// the listing waits for the client thread: outside the monitor, which the sidebar's refresh takes each second
		List<Integer> regions = listed ? null : source.regionIds();
		if (regions != null)
		{
			images.setRegionsInGame(regions);
			if (!regions.isEmpty())
			{
				try
				{
					stamp = source.tablesStamp();
				}
				catch (CacheNotReadyException e)
				{
					// the tables are not downloaded yet: listed on a later step
					restUntil = System.nanoTime() + REST_NANOS;
					return null;
				}
				catch (MapDataException e)
				{
					synchronized (this)
					{
						halted = true;
						restUntil = System.nanoTime() + HALT_NANOS;
					}
					return null;
				}
			}
		}
		synchronized (this)
		{
			return next(store, regions);
		}
	}

	synchronized Integer next(MapStore store, List<Integer> regions)
	{
		if (regions != null && !listed)
		{
			stamp = source.tablesStamp();
		}
		if (!listed)
		{
			if (regions == null || regions.isEmpty())
			{
				// the client has not loaded its map index yet: listed on a later step
				return null;
			}
			for (int plane = 0; plane < RegionImages.PLANES; plane++)
			{
				for (int region : regions)
				{
					int key = RegionImages.key(plane, region);
					// not saved, or saved before a game update changed the tables every region is drawn from
					if (!store.isCurrent(key, stamp))
					{
						todo.add(key);
					}
				}
			}
			listed = true;
			sortedFor = -1;
		}
		WorldPoint around = focus.get();
		int aroundRegion = around.getRegionID();
		if (aroundRegion != sortedFor)
		{
			todo.sort(Comparator.comparingLong(key -> order(key, around)));
			sortedFor = aroundRegion;
		}
		building = todo.isEmpty() ? null : todo.remove(0);
		return building;
	}

	/**
	 * A floor of a region's place in the queue: by floor, then the player's own map before the other, then distance
	 * from the player. Distances on the other map are measured from where its regions lie under (or over) the player:
	 * most dungeons are 6,400 tiles north of what is above them.
	 */
	static long order(int key, WorldPoint around)
	{
		int region = RegionImages.region(key);
		MapArea area = MapArea.of((region & 0xFF) << 6);
		MapArea aroundArea = MapArea.of(around.getY());
		int x = around.getX();
		int y = around.getY();
		if (area != aroundArea)
		{
			y += area == MapArea.UNDERGROUND ? MapArea.UNDERGROUND_SHIFT : -MapArea.UNDERGROUND_SHIFT;
		}
		long dx = ((region >> 8) << 6) + 32 - x;
		long dy = ((region & 0xFF) << 6) + 32 - y;
		long distance = Math.min(dx * dx + dy * dy, (1L << 40) - 1);
		return (long) RegionImages.plane(key) << 42 | (area == aroundArea ? 0L : 1L << 41) | distance;
	}
}
