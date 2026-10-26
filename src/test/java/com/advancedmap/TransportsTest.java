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
		assertFalse(labels(to).contains("A I Q"));
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
	public void theLayerHasALineAndANamedMarkPerDestination()
	{
		List<Transports.Destination> to = Transports.from(1455, 2968, 0);
		MapLayer layer = Transports.layer(new WorldPoint(1455, 2968, 0), to);
		assertEquals(to.size(), layer.getLines().size());
		assertEquals(to.size(), layer.getMarkers().size());
	}
}
