/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MapAreaTest
{
	@Test
	public void eachPlaceOpensOnItsOwnMap()
	{
		// Lumbridge
		assertEquals(MapArea.SURFACE, MapArea.of(3218));
		// the top of the surface, north of the Wilderness
		assertEquals(MapArea.SURFACE, MapArea.of(4415));
		// Grimstone Dungeon (the region 117 HD calls GRIMSTONE_DUNGEON, about 2900, 10450)
		assertEquals(MapArea.UNDERGROUND, MapArea.of(10450));
		// Edgeville Dungeon
		assertEquals(MapArea.UNDERGROUND, MapArea.of(9867));
		assertEquals(MapArea.UNDERGROUND, MapArea.of(4416));
	}

	@Test
	public void theViewStopsAtTheMapsEdges()
	{
		MapViewport surface = MapArea.SURFACE.newViewport();
		surface.centerOn(3222, 3218);
		// dragging the mouse left and down a long way looks far past the east and north edges
		surface.setZoom(1);
		surface.drag(-100_000, 100_000);
		MapViewport.View view = surface.view(800, 600);
		assertEquals((MapArea.SURFACE.maxRegionX + 1) * 64, view.centerX, 0);
		assertEquals((MapArea.SURFACE.maxRegionY + 1) * 64, view.centerY, 0);
		// and right and up, past the west and south ones
		surface.drag(1_000_000, -1_000_000);
		view = surface.view(800, 600);
		assertEquals(MapArea.SURFACE.minRegionX * 64, view.centerX, 0);
		assertEquals(MapArea.SURFACE.minRegionY * 64, view.centerY, 0);
	}

	@Test
	public void everyRegionTheCacheHasIsInsideItsMap()
	{
		// the extremes of the live cache, 2026-10-25
		assertTrue(MapArea.SURFACE.minRegionX <= 15 && MapArea.SURFACE.maxRegionX >= 65);
		assertTrue(MapArea.SURFACE.minRegionY <= 19);
		assertTrue(MapArea.UNDERGROUND.minRegionX <= 18 && MapArea.UNDERGROUND.maxRegionX >= 98);
		assertTrue(MapArea.UNDERGROUND.maxRegionY >= 199);
	}

	@Test
	public void theMapsDoNotOverlapAndSwitchBothWays()
	{
		assertEquals(MapArea.SURFACE.maxRegionY + 1, MapArea.UNDERGROUND.minRegionY);
		assertEquals(MapArea.UNDERGROUND, MapArea.SURFACE.other());
		assertEquals(MapArea.SURFACE, MapArea.UNDERGROUND.other());
	}
}
