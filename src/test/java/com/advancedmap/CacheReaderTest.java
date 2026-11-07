/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CacheReaderTest
{
	@Test
	public void aReadThatThrowsAnErrorStillWakesItsWaiter()
	{
		// a client update leaves a class missing: the error used to escape into the game's frame and the render thread
		// waiting on the read never woke up
		CacheReader.Pending<Object> pending = new CacheReader.Pending<>(client ->
		{
			throw new NoSuchMethodError("gone");
		});
		pending.run(null);
		assertTrue(pending.result.isCompletedExceptionally());
	}

	@Test
	public void anOutOfMemoryErrorWakesTheWaiterAndGoesOn()
	{
		CacheReader.Pending<Object> pending = new CacheReader.Pending<>(client ->
		{
			throw new OutOfMemoryError("test");
		});
		try
		{
			pending.run(null);
			fail("the virtual machine's own error is not swallowed");
		}
		catch (OutOfMemoryError expected)
		{
			assertTrue(pending.result.isCompletedExceptionally());
		}
	}

	@Test
	public void aReadFromAnotherThreadIsCountedForThatThreadWithTheFramesThatServedIt() throws Exception
	{
		// for the build's timing in the log: the preparer's waits, not the renderer's, and the frames meanwhile
		Thread clientThread = Thread.currentThread();
		CacheReader reader = new CacheReader(new GameCache()
		{
			@Override
			public boolean isClientThread()
			{
				return Thread.currentThread() == clientThread;
			}

			@Override
			public net.runelite.api.IndexDataBase getIndex(int id)
			{
				return null;
			}

			@Override
			public net.runelite.api.IndexDataBase getIndexConfig()
			{
				return null;
			}

			@Override
			public net.runelite.api.IndexDataBase getIndexScripts()
			{
				return null;
			}

			@Override
			public net.runelite.api.IndexDataBase getIndexSprites()
			{
				return null;
			}

			@Override
			public net.runelite.api.SpritePixels[] getSprites(net.runelite.api.IndexDataBase index, int group, int file)
			{
				return null;
			}
		});
		long[] seen = new long[2];
		Thread preparer = new Thread(() ->
		{
			reader.read(client -> "region");
			long[] waited = reader.waitedHere();
			seen[0] = waited[0];
			seen[1] = waited[1];
		});
		preparer.start();
		while (preparer.isAlive())
		{
			reader.service();
			preparer.join(5);
		}
		assertEquals(1, seen[0]);
		assertTrue(seen[1] > 0);
		assertTrue(reader.frames() >= 1);
		assertEquals("the client thread's own reads run at once and are not counted", 0, reader.waitedHere()[0]);
	}
}
