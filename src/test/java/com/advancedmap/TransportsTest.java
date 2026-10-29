/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
	public void theGrandExchangeMinecartOpensKeldagrimInsteadOfALine()
	{
		MapSpot cart = MapSpot.icon(3140, 3504, 0, 0, 1104);
		assertEquals(new WorldPoint(2908, 10170, 0), Transports.elsewhere(cart));
		// the spirit tree there has many places: lines, not a jump
		assertNull(Transports.elsewhere(MapSpot.icon(3184, 3510, 0, 0, 1104)));
	}

	@Test
	public void theSpiritTreeToPrifddinasLandsOnTheSurface()
	{
		// the Grand Exchange spirit tree reaches Prifddinas: its tree at 3274, 6123 in the city's copy, drawn in Tirannwn
		List<Transports.Destination> to = Transports.from(3185, 3508, 0);
		assertTrue(labels(to).contains("Prifddinas"));
		MapLayer layer = Transports.layer(new WorldPoint(3185, 3508, 0), to);
		assertTrue(layer.getMarkers().stream().anyMatch(m -> "Prifddinas".equals(m.getLabel())
			&& m.getPoint().equals(new WorldPoint(2250, 3371, 0))));
	}

	@Test
	public void placesOnTheOtherMapGetNoLine()
	{
		Transports.Destination keldagrim = new Transports.Destination(new WorldPoint(2908, 10170, 0), "Minecart",
			"Keldagrim");
		Transports.Destination falador = new Transports.Destination(new WorldPoint(2965, 3380, 0), "Minecart", "");
		MapLayer layer = Transports.layer(new WorldPoint(3140, 3504, 0), Arrays.asList(keldagrim, falador));
		assertEquals(1, layer.getLines().size());
		assertTrue(layer.getMarkers().stream().noneMatch(m -> "Keldagrim".equals(m.getLabel())));
	}

	@Test
	public void theHousesFairyRingDoesNotTakeTheHousesSpiritTreeNeeds()
	{
		// the house's fairy ring (DIQ) and spirit tree land on the same tile, 1858, 7051; the tree's row needs Tree
		// Gnome Village, the ring's trip does not
		Transports.Destination ring = Transports.from(2996, 3114, 0).stream()
			.filter(d -> d.getPoint().getX() == 1858 && d.getPoint().getY() == 7051).findFirst().orElse(null);
		assertNotNull(ring);
		assertFalse(ring.getNeeds().contains("Tree Gnome Village"));
		Transports.Destination tree = Transports.from(3185, 3508, 0).stream()
			.filter(d -> d.getPoint().getX() == 1858 && d.getPoint().getY() == 7051).findFirst().orElse(null);
		assertNotNull(tree);
		assertTrue(tree.getNeeds().contains("Tree Gnome Village"));
	}

	@Test
	public void theKeldagrimFerryCrossesTheRiverKelda()
	{
		// its south dock has no icon on the game's map; Advanced Map gives it one, and a click shows both crossings
		org.junit.Assert.assertTrue(Transports.boardingSpots().contains(new WorldPoint(2839, 10128, 0)));
		List<Transports.Destination> to = Transports.from(2839, 10128, 0);
		org.junit.Assert.assertTrue(to.stream().anyMatch(d -> d.getPoint().equals(new WorldPoint(2892, 10225, 0))));
		org.junit.Assert.assertTrue(to.stream().anyMatch(d -> d.getPoint().equals(new WorldPoint(2837, 10143, 0))));
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

	@Test
	public void faresAndNeedsComeWithTheTrip()
	{
		// Aldarin's charter crew: Brimhaven for 2,500 coins; the Sunset Coast needs Children of the Sun
		List<Transports.Destination> to = Transports.from(1455, 2968, 0);
		Transports.Destination brimhaven = to.stream().filter(d -> d.label().equals("Brimhaven")).findFirst().get();
		assertEquals("2,500 gp", brimhaven.fareText());
		assertTrue(to.stream().anyMatch(d -> d.getNeeds().contains("Children of the Sun")));
	}

	@Test
	public void theShiloCartCostsUnlessTheDiaryIsDone()
	{
		List<Transports.Destination> to = Transports.from(2780, 3214, 0);
		assertEquals(1, to.size());
		assertEquals("200 gp", to.get(0).fareText());
		assertTrue(to.get(0).getNeeds().contains("Karamja elite diary"));
		assertEquals("200 gp", Transports.from(2833, 2958, 0).get(0).fareText());
	}
}
