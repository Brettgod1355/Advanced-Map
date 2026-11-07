/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The places the map jumped away from (a dungeon's way in, a search, a pin), for the mouse's back and forward
 * buttons, as a browser's. Thread safe: input and the client thread both reach it.
 */
final class JumpHistory
{
	/** How many jumps back are kept; the oldest is forgotten first. */
	static final int KEPT = 20;

	/** One view of the map: which map, which floor, where its middle is and how close. */
	static final class Place
	{
		final MapArea area;
		final int plane;
		final double centerX;
		final double centerY;
		final double zoom;

		Place(MapArea area, int plane, double centerX, double centerY, double zoom)
		{
			this.area = area;
			this.plane = plane;
			this.centerX = centerX;
			this.centerY = centerY;
			this.zoom = zoom;
		}
	}

	private final Deque<Place> back = new ArrayDeque<>();
	private final Deque<Place> forward = new ArrayDeque<>();

	/** The map is about to jump away from {@code from}: back returns there, and forward is forgotten. */
	synchronized void jumped(Place from)
	{
		back.push(from);
		while (back.size() > KEPT)
		{
			back.removeLast();
		}
		forward.clear();
	}

	/** Where back goes from {@code here}, which forward then returns to; null when there is nowhere back. */
	synchronized Place back(Place here)
	{
		return step(back, forward, here);
	}

	/** Where forward goes from {@code here}, which back then returns to; null when there is nowhere forward. */
	synchronized Place forward(Place here)
	{
		return step(forward, back, here);
	}

	private static Place step(Deque<Place> from, Deque<Place> to, Place here)
	{
		Place there = from.poll();
		if (there != null)
		{
			to.push(here);
		}
		return there;
	}
}
