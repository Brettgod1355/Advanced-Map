/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
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
 * map's own drawing come first, and paused while the map is drawing what is on screen. After a game update only the
 * changed regions are drawn again, when they are next looked at.
 */
@Slf4j
final class MapPreparer
{
	/** Waits this long after the plugin starts, so the client's own start comes first. */
	private static final long START_DELAY_MILLIS = 10_000;
	/** A breath between regions. */
	private static final long STEP_MILLIS = 25;

	private final RegionImages images;
	private final ClientMapSource source;
	private final BooleanSupplier enabled;
	private final Supplier<WorldPoint> focus;
	private final ScheduledExecutorService worker;
	/** Floors of regions still to prepare, in order; ticker thread, and {@link #restart} and {@link #remaining}. */
	private final List<Integer> todo = new ArrayList<>();
	private boolean listed;
	/** The region the list was last sorted around. */
	private int sortedFor = -1;

	MapPreparer(RegionImages images, ClientMapSource source, BooleanSupplier enabled, Supplier<WorldPoint> focus)
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
		listed = false;
	}

	/** How many regions' floors are still to prepare; -1 before the list is made. */
	synchronized int remaining()
	{
		return listed ? todo.size() : -1;
	}

	private void step()
	{
		try
		{
			MapStore store = images.store();
			if (!enabled.getAsBoolean() || store == null || images.isBusy())
			{
				return;
			}
			Integer key = next(store);
			if (key != null)
			{
				images.prepare(key);
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
	}

	/** The next floor of a region to prepare, listing them first and sorting again when the player has moved. */
	private synchronized Integer next(MapStore store)
	{
		if (!listed)
		{
			List<Integer> regions = source.regionIds();
			if (regions.isEmpty())
			{
				// the client has not loaded its map index yet: listed on a later step
				return null;
			}
			for (int plane = 0; plane < RegionImages.PLANES; plane++)
			{
				for (int region : regions)
				{
					int key = RegionImages.key(plane, region);
					if (!store.has(key))
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
		return todo.isEmpty() ? null : todo.remove(0);
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
			y += area == MapArea.UNDERGROUND ? 6400 : -6400;
		}
		long dx = ((region >> 8) << 6) + 32 - x;
		long dy = ((region & 0xFF) << 6) + 32 - y;
		long distance = Math.min(dx * dx + dy * dy, (1L << 40) - 1);
		return (long) RegionImages.plane(key) << 42 | (area == aroundArea ? 0L : 1L << 41) | distance;
	}
}
