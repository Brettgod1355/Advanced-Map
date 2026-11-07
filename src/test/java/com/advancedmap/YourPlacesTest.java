/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class YourPlacesTest
{
	private static MapLabels.Label label(int x, int y, String... lines)
	{
		return new MapLabels.Label(Arrays.asList(lines), x, y, MapLabels.TOWN, Color.WHITE);
	}

	@Test
	public void eachHouseValueIsItsTownsPortal()
	{
		// the values the game's house board names, 7 and 10 to 12 unused
		assertEquals(9, YourPlaces.HOUSES.size());
		assertEquals("Rimmington", YourPlaces.HOUSES.get(1).getTown());
		assertEquals(new WorldPoint(1743, 3517, 0), YourPlaces.HOUSES.get(8).getPortal());
		assertEquals("Prifddinas", YourPlaces.HOUSES.get(9).getTown());
		assertEquals("Aldarin", YourPlaces.HOUSES.get(13).getTown());
		assertNull(YourPlaces.HOUSES.get(0));
		assertNull(YourPlaces.HOUSES.get(7));
	}

	@Test
	public void aBoatIsAtTheDockingIconNearestItsDocksLabel()
	{
		List<MapLabels.Label> labels = Arrays.asList(label(3025, 3220, "Port Sarim"), label(2580, 2850, "Deepfin",
			"Point"));
		List<int[]> docks = Arrays.asList(new int[]{3051, 3193, 0}, new int[]{2906, 3225, 0}, new int[]{2600, 2860, 0});
		assertEquals(new WorldPoint(3051, 3193, 0), YourPlaces.dockSpot("Port Sarim", labels, docks));
		// a name on two lines matches whole
		assertEquals(new WorldPoint(2600, 2860, 0), YourPlaces.dockSpot("Deepfin Point", labels, docks));
		// no docking icon read near it yet: the label
		assertEquals(new WorldPoint(3025, 3220, 0), YourPlaces.dockSpot("Port Sarim", labels, Collections.emptyList()));
		assertNull(YourPlaces.dockSpot("Lunar Isle", labels, docks));
	}

	@Test
	public void theBoatDesignIsAddedLastSoSavedMarkersKeepTheirs()
	{
		MarkerIcons.Design[] designs = MarkerIcons.Design.values();
		assertEquals(MarkerIcons.Design.BOAT, designs[designs.length - 1]);
		assertEquals(MarkerIcons.Design.HOUSE, MarkerIcons.Design.of(7));
		BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		MarkerIcons.draw(image.createGraphics(), MarkerIcons.Design.BOAT, Color.CYAN, 1, 1, 18);
		boolean drawn = false;
		for (int x = 0; x < 20 && !drawn; x++)
		{
			for (int y = 0; y < 20 && !drawn; y++)
			{
				drawn = (image.getRGB(x, y) >>> 24) != 0;
			}
		}
		assertTrue(drawn);
	}
}
