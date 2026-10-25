/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
	public void theMapsDoNotOverlapAndSwitchBothWays()
	{
		assertEquals(MapArea.SURFACE.maxRegionY + 1, MapArea.UNDERGROUND.minRegionY);
		assertEquals(MapArea.UNDERGROUND, MapArea.SURFACE.other());
		assertEquals(MapArea.SURFACE, MapArea.UNDERGROUND.other());
	}
}
