/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapLinks;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DungeonLinksTest
{
	@Test
	public void theEntrancesTheGameLeavesOutLeadIntoTheirDungeons()
	{
		Map<Integer, Integer> extra = DungeonLinks.extra();
		assertEquals(43, extra.size());
		// Tonali Cavern's two entrances, from the wiki
		int cavern = MapLinks.pack(1311, 9382, 0);
		assertEquals(cavern, MapLinks.destinationNear(extra, 1306, 3035, 0, RegionImages.LINK_REACH));
		assertEquals(cavern, MapLinks.destinationNear(extra, 1310, 3104, 0, RegionImages.LINK_REACH));
		// the Smoke Dungeon's well, from Shortest Path
		assertEquals(MapLinks.pack(3206, 9379, 0), MapLinks.destinationNear(extra, 3310, 2962, 0,
			RegionImages.LINK_REACH));
		for (int to : extra.values())
		{
			assertTrue(MapArea.of(MapLinks.y(to)) == MapArea.UNDERGROUND);
		}
	}

	@Test
	public void theKalphiteLairAndTheQueensChamberLinkBothWays()
	{
		// the owner, 2026-11-07, in game: the lair's tunnel (floor 3) down to the chamber, and the rope back up
		Map<Integer, Integer> extra = DungeonLinks.extra();
		int lair = MapLinks.pack(3509, 9497, 2);
		int chamber = MapLinks.pack(3510, 9492, 0);
		assertEquals(chamber, MapLinks.destinationNear(extra, 3509, 9497, 2, RegionImages.LINK_REACH));
		assertEquals(lair, MapLinks.destinationNear(extra, 3510, 9492, 0, RegionImages.LINK_REACH));
		// on top of each other but a floor apart: each icon leads only its own way
		assertEquals(-1, MapLinks.destinationNear(extra, 3510, 9492, 1, RegionImages.LINK_REACH));
	}

	@Test
	public void theGamesOwnLinkWinsOverTheList()
	{
		int smokeyWell = MapLinks.pack(3310, 2962, 0);
		int elsewhere = MapLinks.pack(3000, 9000, 0);
		assertEquals(Integer.valueOf(elsewhere),
			DungeonLinks.withExtra(Collections.singletonMap(smokeyWell, elsewhere)).get(smokeyWell));
	}
}
