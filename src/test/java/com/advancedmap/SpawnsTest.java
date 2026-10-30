/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.ArrayList;
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
}
