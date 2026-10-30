/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * The tables are read in the background as the plugin starts, while a search can be typed: an index whose build
 * straddled the load must not be kept, or the session's searches find no place, shop or monster.
 */
public class MapSearchRaceTest
{
	/** A label list whose second size() call made directly from MapSearch.index (the cache write) waits for a latch. */
	static final class LabelsThatPauseBeforeTheCacheWrite extends AbstractList<MapLabels.Label>
	{
		private final List<MapLabels.Label> inner;
		private final CountDownLatch reachedWrite;
		private final CountDownLatch mayWrite;
		private int callsFromIndex;

		LabelsThatPauseBeforeTheCacheWrite(List<MapLabels.Label> inner, CountDownLatch reachedWrite, CountDownLatch mayWrite)
		{
			this.inner = inner;
			this.reachedWrite = reachedWrite;
			this.mayWrite = mayWrite;
		}

		@Override
		public MapLabels.Label get(int i)
		{
			return inner.get(i);
		}

		@Override
		public int size()
		{
			StackTraceElement[] stack = Thread.currentThread().getStackTrace();
			// stack[0] = getStackTrace, [1] = this size(), [2] = the caller
			if (stack.length > 2 && "index".equals(stack[2].getMethodName()) && stack[2].getClassName().endsWith("MapSearch"))
			{
				callsFromIndex++;
				if (callsFromIndex == 2)
				{
					reachedWrite.countDown();
					try
					{
						mayWrite.await();
					}
					catch (InterruptedException e)
					{
						Thread.currentThread().interrupt();
					}
				}
			}
			return inner.size();
		}
	}

	@Test(timeout = 20000)
	public void anIndexBuiltWhileTheTablesAreBeingReadIsKeptAfterwards() throws Exception
	{
		MapSearch fresh = new MapSearch();
		List<MapLabels.Label> plain = Collections.singletonList(new MapLabels.Label(
			Collections.singletonList("Lumbridge"), 3239, 3234, 2, Color.WHITE));
		CountDownLatch reachedWrite = new CountDownLatch(1);
		CountDownLatch mayWrite = new CountDownLatch(1);
		List<MapLabels.Label> labels = new LabelsThatPauseBeforeTheCacheWrite(plain, reachedWrite, mayWrite);
		AtomicReference<List<MapSearch.Entry>> built = new AtomicReference<>();
		// the "EDT": a search typed as the plugin starts; its build reads `places` while still empty
		Thread typing = new Thread(() -> built.set(fresh.index(labels)));
		typing.start();
		assertTrue(reachedWrite.await(10, java.util.concurrent.TimeUnit.SECONDS));
		// the executor: the tables finish, `places` is published and the index invalidated
		fresh.load();
		assertTrue(fresh.places().stream().anyMatch(e -> e.getName().equals("Edgeville Dungeon")));
		// the EDT finishes its build and writes indexedLabels = labels.size() after the invalidation
		mayWrite.countDown();
		typing.join();
		assertTrue("the build started before load() lacks the places",
			built.get().stream().noneMatch(e -> e.getName().equals("Edgeville Dungeon")));
		// a later keystroke with the same (now loaded, unchanged) labels: should see the places
		List<MapSearch.Entry> after = fresh.index(plain);
		assertTrue("the stale index is kept for the session",
			after.stream().anyMatch(e -> e.getName().equals("Edgeville Dungeon")));
	}
}
