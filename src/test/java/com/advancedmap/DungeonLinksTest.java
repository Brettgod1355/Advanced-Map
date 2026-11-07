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
		assertEquals(45, extra.size());
		// Tonali Cavern's two entrances, from the wiki
		int cavern = MapLinks.pack(1311, 9382, 0);
		assertEquals(cavern, MapLinks.destinationNear(extra, 1306, 3035, 0, RegionImages.LINK_REACH));
		assertEquals(cavern, MapLinks.destinationNear(extra, 1310, 3104, 0, RegionImages.LINK_REACH));
		// the Smoke Dungeon's well, from Shortest Path
		assertEquals(MapLinks.pack(3206, 9379, 0), MapLinks.destinationNear(extra, 3310, 2962, 0,
			RegionImages.LINK_REACH));
		// a way in from the surface leads underground (a way out, from a dungeon, may lead up to it)
		for (Map.Entry<Integer, Integer> link : extra.entrySet())
		{
			assertTrue(MapArea.of(MapLinks.y(link.getKey())) == MapArea.UNDERGROUND
				|| MapArea.of(MapLinks.y(link.getValue())) == MapArea.UNDERGROUND);
		}
	}

	@Test
	public void theKalphiteLairAndTheQueensChamberLinkBothWays()
	{
		// the owner, 2026-11-07, in game: the lair's tunnel (floor 3) down to the chamber, and the rope back up; each
		// lands on the tile the player stood on after the climb (Black Box), the chamber's icon on its own landing
		Map<Integer, Integer> extra = DungeonLinks.extra();
		int lair = MapLinks.pack(3508, 9497, 2);
		int chamber = MapLinks.pack(3508, 9493, 0);
		assertEquals(chamber, MapLinks.destinationNear(extra, 3509, 9497, 2, RegionImages.LINK_REACH));
		assertEquals(lair, MapLinks.destinationNear(extra, 3508, 9493, 0, RegionImages.LINK_REACH));
		// on top of each other but a floor apart: each icon leads only its own way
		assertEquals(-1, MapLinks.destinationNear(extra, 3508, 9493, 1, RegionImages.LINK_REACH));
		// and the lair's way out, to its entrance in the desert, and in again to where the climb down lands
		int entrance = MapLinks.pack(3227, 3108, 0);
		int landing = MapLinks.pack(3483, 9510, 2);
		assertEquals(entrance, MapLinks.destinationNear(extra, 3483, 9510, 2, RegionImages.LINK_REACH));
		assertEquals(landing, MapLinks.destinationNear(extra, 3227, 3108, 0, RegionImages.LINK_REACH));
		// a rope into the lair and down to the Queen (OSRS Wiki), none for the ropes back up; the game's icon may sit
		// beside the listed tile
		String rope = "Needs a rope, unless the Hard Desert Diary is done";
		assertEquals(rope, DungeonLinks.needs(3226, 3108, 0));
		assertEquals(rope, DungeonLinks.needs(3509, 9497, 2));
		assertEquals(null, DungeonLinks.needs(3508, 9493, 0));
		assertEquals(null, DungeonLinks.needs(3483, 9510, 2));
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
