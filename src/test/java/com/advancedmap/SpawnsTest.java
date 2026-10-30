/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SpawnsTest
{
	/** True for a tile one of the two maps shows. */
	static boolean onAMap(WorldPoint tile)
	{
		MapArea area = MapArea.of(tile.getY());
		int rx = tile.getX() >> 6;
		int ry = tile.getY() >> 6;
		return rx >= area.minRegionX && rx <= area.maxRegionX && ry >= area.minRegionY && ry <= area.maxRegionY;
	}

	@Test
	public void everySpawnIsSomewhereTheMapsShow()
	{
		// a spawn off both maps (Cerberus's lair, an instance far south of the surface) sent the search to the
		// underground map's clamped edge and blank sea; the generator places such a one at the door it is entered by
		Spawns.load();
		List<String> off = new ArrayList<>();
		for (Spawns.Creature creature : Spawns.all())
		{
			for (WorldPoint tile : creature.getTiles())
			{
				if (!onAMap(tile))
				{
					off.add(creature.getName() + " " + tile.getX() + "," + tile.getY());
				}
			}
		}
		assertTrue("spawns off both maps: " + off, off.isEmpty());
		Spawns.Creature cerberus = Spawns.all().stream().filter(c -> c.getName().equals("Cerberus")).findFirst().orElse(null);
		assertEquals(1, cerberus.getTiles().size());
		assertEquals(new WorldPoint(2873, 9848, 0), cerberus.getTiles().get(0));
	}

	@Test
	public void aSpawnUpstairsWhereTheMapDrawsNoGroundIsShownBelowAndSaidToBeUpstairs() throws Exception
	{
		// the generator puts a tile whose floor has no drawn ground on the floor below, keeping its own as a fourth number
		String tsv = "M\tDagannoth\t74\t2500,3700,0,1;2501,3700,1\n";
		Spawns.Creature dagannoth = Spawns.parse(new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8))).get(0);
		WorldPoint shown = new WorldPoint(2500, 3700, 0);
		assertEquals(shown, dagannoth.getTiles().get(0));
		assertEquals(Integer.valueOf(1), dagannoth.getFloors().get(shown));
		assertEquals("a tile on its own floor has no entry", 1, dagannoth.getFloors().size());

		List<SearchSpots.Area> areas = SearchSpots.areas(Collections.singletonList(shown), shown,
			Collections.emptyList(), dagannoth.getFloors());
		assertEquals(1, areas.get(0).getFloor());
		assertEquals("Upstairs, floor up 1", SearchSpots.rows(areas).get(0).getSub());
		assertEquals("Floor up 1", SearchSpots.rows(Collections.singletonList(new SearchSpots.Area("X",
			new WorldPoint(1, 1, 1), 1, 0))).get(0).getSub());
	}

	@Test
	public void everyBundledTileIsOnAFloorWithGroundOrSaysWhereItReallyIs()
	{
		// the moved tiles keep a floor above the one they are shown on (the generator moves them down only): 228 of the
		// 388 monster and NPC tiles the 2026-10-29 check found over no ground; the others have ground only above, or none
		Spawns.load();
		int moved = 0;
		for (Spawns.Creature creature : Spawns.all())
		{
			for (java.util.Map.Entry<WorldPoint, Integer> floor : creature.getFloors().entrySet())
			{
				assertTrue(creature.getName(), floor.getValue() > floor.getKey().getPlane());
				moved++;
			}
		}
		assertEquals(228, moved);
	}
}
