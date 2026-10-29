/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

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
}
