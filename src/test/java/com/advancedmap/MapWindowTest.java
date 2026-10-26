/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MapWindowTest
{
	private static final List<Rectangle> ONE_SCREEN = Collections.singletonList(new Rectangle(0, 0, 1920, 1080));
	private static final List<Rectangle> TWO_SCREENS = Arrays.asList(new Rectangle(0, 0, 1920, 1080),
		new Rectangle(1920, 0, 2560, 1440));

	@Test
	public void theWindowComesBackWhereItWas()
	{
		assertEquals(new Rectangle(100, 80, 1200, 800), MapWindow.restoredBounds("100,80,1200,800", ONE_SCREEN));
		// on the second monitor
		assertEquals(new Rectangle(2200, 100, 1600, 1000), MapWindow.restoredBounds("2200,100,1600,1000", TWO_SCREENS));
	}

	@Test
	public void aWindowOffEveryScreenOpensInTheDefaultPlace()
	{
		// the second monitor was unplugged
		assertNull(MapWindow.restoredBounds("2200,100,1600,1000", ONE_SCREEN));
		// only a sliver of its title bar would show
		assertNull(MapWindow.restoredBounds("1880,100,800,600", ONE_SCREEN));
		assertNull(MapWindow.restoredBounds("100,1070,800,600", ONE_SCREEN));
	}

	@Test
	public void nothingSavedOrNonsenseOpensInTheDefaultPlace()
	{
		assertNull(MapWindow.restoredBounds("", ONE_SCREEN));
		assertNull(MapWindow.restoredBounds(null, ONE_SCREEN));
		assertNull(MapWindow.restoredBounds("1,2,3", ONE_SCREEN));
		assertNull(MapWindow.restoredBounds("a,b,c,d", ONE_SCREEN));
	}

	@Test
	public void aTinySavedSizeGrowsToTheSmallest()
	{
		assertEquals(new Rectangle(10, 10, MapWindow.MIN_WIDTH, MapWindow.MIN_HEIGHT),
			MapWindow.restoredBounds("10,10,50,40", ONE_SCREEN));
	}
}
