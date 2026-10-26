/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransportsTest
{
	@BeforeClass
	public static void load()
	{
		Transports.load();
	}

	private static List<String> labels(List<Transports.Destination> destinations)
	{
		return destinations.stream().map(Transports.Destination::label).collect(Collectors.toList());
	}

	@Test
	public void theGrandExchangeSpiritTreeReachesTheOtherTreesButNotItself()
	{
		// the spirit tree by the Grand Exchange stands near 3185, 3508
		List<Transports.Destination> to = Transports.from(3185, 3508, 0);
		assertTrue("a network: many trees", to.size() >= 8);
		assertTrue(labels(to).stream().anyMatch(l -> l.contains("Tree Gnome Village")));
		assertFalse(to.stream().anyMatch(d -> Math.abs(d.getPoint().getX() - 3185) <= Transports.REACH
			&& Math.abs(d.getPoint().getY() - 3508) <= Transports.REACH));
	}

	@Test
	public void aFairyRingReachesTheOthersWithTheirCodes()
	{
		// the fairy ring south of Draynor Manor's swamp, code A I Q, at 2996, 3114
		List<Transports.Destination> to = Transports.from(2996, 3114, 0);
		assertTrue(to.size() > 40);
		assertTrue(labels(to).contains("ZANARIS"));
		assertTrue(labels(to).contains("DKR"));
		assertFalse(labels(to).contains("AIQ"));
	}

	@Test
	public void aCharterShipListsItsPorts()
	{
		// Aldarin's charter crew, 1455, 2968: Brimhaven, Catherby and more
		List<String> to = labels(Transports.from(1455, 2968, 0));
		assertTrue(to.contains("Brimhaven"));
		assertTrue(to.contains("Catherby"));
	}

	@Test
	public void aFarmingPatchShowsOnlySpiritTrees()
	{
		for (Transports.Destination d : Transports.from(3185, 3508, 0, Transports.SPIRIT_TREE))
		{
			assertEquals(Transports.SPIRIT_TREE, d.getKind());
		}
		assertTrue(Transports.from(3222, 3218, 0).isEmpty());
	}

	@Test
	public void theGrandExchangeMinecartGoesToKeldagrimAlone()
	{
		// the trapdoor in the Grand Exchange, 3139, 3504; a fairy ring (DKR) and a canoe are within reach too
		List<Transports.Destination> to = Transports.from(3140, 3504, 0);
		assertEquals(1, to.size());
		assertEquals(new WorldPoint(2908, 10170, 0), to.get(0).getPoint());
		assertEquals("Keldagrim", to.get(0).label());
	}

	@Test
	public void theEdgevilleCanoeShowsOnlyCanoeTrips()
	{
		List<Transports.Destination> to = Transports.from(3131, 3509, 0);
		assertFalse(to.isEmpty());
		for (Transports.Destination d : to)
		{
			assertEquals("Canoe", d.getKind());
		}
	}

	@Test
	public void stopNamesLoseTheirMenuKeysAndCodeSpaces()
	{
		assertEquals("Tree Gnome Village", Transports.clean("1: Tree Gnome Village"));
		assertEquals("Verdant Valley", Transports.clean("2. Verdant Valley"));
		assertEquals("Meiyerditch", Transports.clean("1: Meiyerditch."));
		assertEquals("DKR", Transports.clean("D K R"));
		assertEquals("AIR, DLR, DJQ, AJS", Transports.clean("A I R - D L R - D J Q - A J S"));
		assertEquals("Port Piscarilius", Transports.clean("A: Port Piscarilius"));
	}

	@Test
	public void transportIconsAreNamedByTheirStop()
	{
		MapSpot ring = MapSpot.icon(3129, 3496, 0, 0, 1104);
		assertEquals("DKR", Transports.stopName(ring));
		assertEquals("Fairy ring DKR", Transports.stopTitle(ring));
		MapSpot balloon = MapSpot.icon(2921, 3301, 0, 0, 1104);
		assertEquals("Crafting Guild", Transports.stopName(balloon));
		assertEquals("Minecart to Keldagrim", Transports.iconName(MapSpot.icon(3140, 3504, 0, 0, 1104)));
		// a bank by a fairy ring is no stop
		assertNull(Transports.stopName(MapSpot.icon(3129, 3496, 0, 0, 1055)));
	}

	@Test
	public void aFarmingPatchIsNamedOnlyAsASpiritTree()
	{
		assertEquals("Spirit tree: Port Sarim", Transports.stopTitle(MapSpot.icon(3059, 3257, 0, 0, 1101)));
		assertNull(Transports.stopName(MapSpot.icon(3129, 3496, 0, 0, 1101)));
	}

	@Test
	public void theLayerHasALineAndANamedMarkPerDestination()
	{
		List<Transports.Destination> to = Transports.from(1455, 2968, 0);
		MapLayer layer = Transports.layer(new WorldPoint(1455, 2968, 0), to);
		assertEquals(to.size(), layer.getLines().size());
		assertEquals(to.size(), layer.getMarkers().size());
	}
}
