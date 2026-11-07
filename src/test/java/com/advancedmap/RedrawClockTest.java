/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Point;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RedrawClockTest
{
	private static final long MS = 1_000_000L;

	@Test
	public void aStillMapIsPaintedOnTheHeartbeatAndAtOnceWhenSomethingHappens()
	{
		RedrawClock clock = new RedrawClock();
		assertTrue("the first paint", clock.due(0, 0, null, 0));
		clock.painted(0, 0, 0);
		assertFalse(clock.due(0, 0, null, 40 * MS));
		assertFalse(clock.due(0, 0, null, 200 * MS));
		assertTrue("the heartbeat", clock.due(0, 0, null, 250 * MS));
		clock.painted(0, 0, 250 * MS);
		assertTrue("input", clock.due(1, 0, null, 260 * MS));
		clock.painted(1, 0, 260 * MS);
		assertTrue("a picture arrived", clock.due(1, 1, null, 270 * MS));
		clock.painted(1, 1, 270 * MS);
		assertFalse(clock.due(1, 1, null, 300 * MS));
	}

	@Test
	public void aMovingPointerIsPaintedAtASteadyRateAndSoIsOneThatJustStopped()
	{
		RedrawClock clock = new RedrawClock();
		clock.painted(0, 0, 0);
		// moved 10 ms after a paint: not yet; 50 ms after it: yes, so a moving pointer costs a paint per 50 ms, not per frame
		assertFalse(clock.due(0, 0, new Point(10, 10), 10 * MS));
		assertTrue(clock.due(0, 0, new Point(12, 10), 50 * MS));
		clock.painted(0, 0, 50 * MS);
		// resting since 50 ms: the hover box and the tip come due within the settling time, so still the steady rate
		assertTrue(clock.due(0, 0, new Point(12, 10), 110 * MS));
		clock.painted(0, 0, 110 * MS);
		assertFalse(clock.due(0, 0, new Point(12, 10), 130 * MS));
		assertTrue(clock.due(0, 0, new Point(12, 10), 170 * MS));
		clock.painted(0, 0, 170 * MS);
		// settled: back to the heartbeat
		assertTrue(clock.due(0, 0, new Point(12, 10), 760 * MS));
		clock.painted(0, 0, 760 * MS);
		assertFalse(clock.due(0, 0, new Point(12, 10), 900 * MS));
		assertTrue(clock.due(0, 0, new Point(12, 10), 1010 * MS));
	}
}
