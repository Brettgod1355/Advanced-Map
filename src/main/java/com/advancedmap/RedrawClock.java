/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Point;
import java.util.Objects;

/**
 * When the map is painted again, over the game and in its window alike: at once after input (a press, a drag, the
 * wheel, a key), a picture or a layer arriving, or the player moving; at a steady rate while the pointer moves and
 * for a moment after it stops, when hover boxes and tips come due; else a slow heartbeat, for what nothing reports
 * (the build's count, the bar). A map that sits still is no longer repainted twenty times a second. One per painter,
 * used on that painter's thread.
 */
final class RedrawClock
{
	/** While the pointer moves or settles: no more often than this, whatever the frame rate. */
	static final long FAST_NANOS = 50_000_000L;
	/** The heartbeat while nothing moves. */
	static final long SLOW_NANOS = 250_000_000L;
	/** After the pointer last moved: a hover box (300 ms) or a tip (400 ms) comes due in this time. */
	static final long SETTLE_NANOS = 700_000_000L;

	private int touches = -1;
	private int changes = -1;
	private Point mouse;
	private long movedAt = Long.MIN_VALUE / 2;
	private long paintedAt = Long.MIN_VALUE / 2;

	/**
	 * Whether to paint now: {@code touches} counts the input, {@code changes} the pictures and layers that arrived,
	 * {@code mouse} is the pointer on the map (null when off it), {@code now} the clock's nanoseconds.
	 */
	boolean due(int touches, int changes, Point mouse, long now)
	{
		if (touches != this.touches || changes != this.changes)
		{
			return true;
		}
		if (!Objects.equals(mouse, this.mouse))
		{
			this.mouse = mouse == null ? null : new Point(mouse);
			movedAt = now;
		}
		long rate = now - movedAt < SETTLE_NANOS ? FAST_NANOS : SLOW_NANOS;
		return now - paintedAt >= rate;
	}

	/** Painted now, with the input and arrivals counted so far. */
	void painted(int touches, int changes, long now)
	{
		this.touches = touches;
		this.changes = changes;
		paintedAt = now;
	}
}
