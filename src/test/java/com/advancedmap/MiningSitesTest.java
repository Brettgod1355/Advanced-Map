/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MiningSitesTest
{
	@Test
	public void theBundledListReadsAndFindsTheSiteNearAnIcon()
	{
		MiningSites.load();
		// the Varrock mine by the Wilderness ditch in the owner's screenshot: RuneLite's map says 2 Iron, 3 Coal
		// there; its icon is at 3105, 3569
		assertEquals(Arrays.asList("2 Iron", "3 Coal"), MiningSites.rocksNear(3105, 3569, 0));
		// Al Kharid's north mine, an entry RuneLite writes over two lines
		assertEquals(7, MiningSites.rocksNear(3298, 3312, 0).size());
		// nowhere near a mine
		assertEquals(Collections.emptyList(), MiningSites.rocksNear(3222, 3218, 0));
	}
}
