/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CaretTest
{
	@Test
	public void theCursorStaysOnWhileTypingThenBlinks()
	{
		assertTrue(Caret.on(0));
		assertTrue(Caret.on(Caret.BLINK_NANOS - 1));
		assertFalse(Caret.on(Caret.BLINK_NANOS));
		assertTrue(Caret.on(Caret.BLINK_NANOS * 2));
		assertFalse(Caret.on(Caret.BLINK_NANOS * 3 + 5));
	}

	@Test
	public void theTextDoesNotMoveAsItBlinks()
	{
		// only the cursor at the end goes; text without one is left as it is
		Caret.typed();
		assertEquals("varr|", Caret.draw("varr|"));
		assertEquals("Name", Caret.draw("Name"));
	}
}
