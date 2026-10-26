/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.Location;
import com.advancedmap.cache.LocationsDefinition;
import com.advancedmap.cache.MapLinks;
import com.advancedmap.cache.Position;
import com.advancedmap.cache.Region;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PrifddinasTest
{
	@Test
	public void theCitysSpiritTreeIsInTirannwn()
	{
		assertEquals(new WorldPoint(2250, 3371, 0), Prifddinas.toSurface(new WorldPoint(3274, 6123, 0)));
		assertEquals(new WorldPoint(3274, 6123, 0), Prifddinas.toCity(new WorldPoint(2250, 3371, 0)));
	}

	@Test
	public void otherTilesStayWhereTheyAre()
	{
		WorldPoint lumbridge = new WorldPoint(3222, 3218, 0);
		assertSame(lumbridge, Prifddinas.toSurface(lumbridge));
		assertSame(lumbridge, Prifddinas.toCity(lumbridge));
		// Gwenith, outside the walls: players walk the surface there
		WorldPoint gwenith = new WorldPoint(2199, 3409, 0);
		assertSame(gwenith, Prifddinas.toCity(gwenith));
		assertNull(Prifddinas.toSurface(null));
	}

	@Test
	public void theCopyIsLeftOffTheUndergroundMapAndTheWallsTakeIt()
	{
		assertTrue(Prifddinas.isCopyRegion(49, 93));
		assertTrue(Prifddinas.isCopyRegion(52, 96));
		assertFalse(Prifddinas.isCopyRegion(53, 96));
		assertTrue(Prifddinas.takesCity(34, 51));
		assertTrue(Prifddinas.takesCity(36, 53));
		// Tirannwn Bight, west of the walls, stays the surface's own
		assertFalse(Prifddinas.takesCity(32, 52));
	}

	@Test
	public void onlyDifferingZonesInsideTheWallsAreTaken()
	{
		// surface region 36, 53 (Gorlah's corner) and its copy 52, 96: only its south-west zone is at the walls; the
		// copy has an object there (local 2, 2) and another outside the walls (local 30, 30)
		Region surface = region(36, 53);
		Region copy = region(52, 96, new int[]{100, 2, 2}, new int[]{200, 30, 30});
		Region merged = Prifddinas.merge(surface, copy);
		List<String> objects = new ArrayList<>();
		for (Location location : merged.getLocations())
		{
			objects.add(location.getId() + "@" + location.getPosition().getX() + "," + location.getPosition().getY());
		}
		assertEquals(Collections.singletonList("100@" + (36 * 64 + 2) + "," + (53 * 64 + 2)), objects);
		// the cached surface region is not changed
		assertTrue(surface.getLocations().isEmpty());
		// nothing differs: the surface region itself
		assertSame(surface, Prifddinas.merge(surface, region(52, 96)));
	}

	@Test
	public void mapLinksInTheCopyMoveToTheSurface()
	{
		int cityDoor = MapLinks.pack(3264, 6100, 0);
		int library = MapLinks.pack(2600, 6100, 0);
		Map<Integer, Integer> moved = Prifddinas.moveLinks(Collections.singletonMap(cityDoor, library));
		assertEquals(Integer.valueOf(library), moved.get(MapLinks.pack(2240, 3348, 0)));
	}

	/** A region with objects at local tiles: {id, x, y} each. */
	private static Region region(int regionX, int regionY, int[]... objects)
	{
		Region region = new Region(regionX, regionY);
		LocationsDefinition locations = new LocationsDefinition();
		List<Location> list = new ArrayList<>();
		for (int[] object : objects)
		{
			list.add(new Location(object[0], 10, 0, new Position(object[1], object[2], 0)));
		}
		locations.setLocations(list);
		region.loadLocations(locations);
		return region;
	}
}
