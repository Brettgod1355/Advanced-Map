/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * The typing cursor of the map's text boxes (the search bar, the Filters list's search, a marker's name): it blinks, and
 * stays on while keys are being typed, as in most text boxes.
 */
final class Caret
{
	/** How long the cursor is on, then off. */
	static final long BLINK_NANOS = 530_000_000L;

	private static volatile long typedAt;

	private Caret()
	{
	}

	/** A key was typed or deleted: the cursor shows at once, and blinks again a moment later. */
	static void typed()
	{
		typedAt = System.nanoTime();
	}

	static boolean on()
	{
		return on(System.nanoTime() - typedAt);
	}

	/** On for the first blink after typing, then on and off by turns. */
	static boolean on(long sinceTyped)
	{
		return sinceTyped < BLINK_NANOS || (sinceTyped / BLINK_NANOS) % 2 == 0;
	}

	/**
	 * A box's text as drawn: {@code shown} ends with the cursor "|" (kept in it when the text is cut to fit, so the text
	 * does not move as it blinks); without it while it is off.
	 */
	static String draw(String shown)
	{
		return on() || !shown.endsWith("|") ? shown : shown.substring(0, shown.length() - 1);
	}
}
