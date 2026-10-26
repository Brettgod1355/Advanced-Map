/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * Draws the regions not saved yet in the background and saves them, so the whole map opens at once: every region of
 * both maps, the ground first, then each floor above it. One region at a time, a few a second, and only while the map
 * is not busy drawing what the player is looking at. After a game update only the changed regions are drawn again,
 * when they are next looked at.
 */
@Slf4j
final class MapPreparer
{
	/** Waits this long after the plugin starts, to stay out of the way while the client logs in. */
	private static final long START_DELAY_SECONDS = 30;
	private static final long STEP_MILLIS = 150;

	private final RegionImages images;
	private final ClientMapSource source;
	private final ExecutorService renderer;
	private final BooleanSupplier enabled;
	private final ScheduledExecutorService ticker;
	private final Queue<Integer> todo = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean working = new AtomicBoolean();
	private volatile boolean listed;

	MapPreparer(RegionImages images, ClientMapSource source, ExecutorService renderer, BooleanSupplier enabled)
	{
		this.images = images;
		this.source = source;
		this.renderer = renderer;
		this.enabled = enabled;
		this.ticker = Executors.newSingleThreadScheduledExecutor(task ->
		{
			Thread thread = new Thread(task, "Advanced Map preparer");
			thread.setDaemon(true);
			return thread;
		});
	}

	void start()
	{
		ticker.scheduleWithFixedDelay(this::step, START_DELAY_SECONDS * 1000, STEP_MILLIS, TimeUnit.MILLISECONDS);
	}

	void stop()
	{
		ticker.shutdownNow();
	}

	/** Lists the regions again, after the saved map was deleted. */
	void restart()
	{
		todo.clear();
		listed = false;
	}

	/** How many regions' floors are still to prepare; -1 before the list is made. */
	int remaining()
	{
		return listed ? todo.size() : -1;
	}

	private void step()
	{
		try
		{
			MapStore store = images.store();
			if (!enabled.getAsBoolean() || store == null || images.isBusy() || working.get())
			{
				return;
			}
			if (!listed)
			{
				list(store);
				return;
			}
			Integer key = todo.poll();
			if (key == null)
			{
				return;
			}
			working.set(true);
			renderer.execute(() ->
			{
				try
				{
					images.prepare(key);
				}
				finally
				{
					working.set(false);
				}
			});
		}
		catch (RejectedExecutionException | CancellationException e)
		{
			// the plugin is stopping
			working.set(false);
		}
		catch (RuntimeException e)
		{
			log.debug("could not prepare the map", e);
			working.set(false);
		}
	}

	private void list(MapStore store)
	{
		List<Integer> regions = source.regionIds();
		if (regions.isEmpty())
		{
			// the client has not loaded its map index yet: listed on a later step
			return;
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
	}
}
