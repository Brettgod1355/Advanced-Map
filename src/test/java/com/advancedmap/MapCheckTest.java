/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Color;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class MapCheckTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final MapSpot NOWHERE = MapSpot.icon(3100, 3200, 0, 1, IconGroup.DUNGEON);
	private static final MapSpot UNNAMED = MapSpot.icon(3200, 3200, 0, 1, IconGroup.DUNGEON);
	private static final MapSpot BY_LABEL = MapSpot.icon(3300, 3200, 0, 1, IconGroup.DUNGEON);
	private static final MapSpot NAMED = MapSpot.icon(3400, 3200, 0, 1, IconGroup.DUNGEON);
	private static final MapSpot WAY_UP = MapSpot.icon(3200, 9600, 0, 1, IconGroup.DUNGEON);
	private static final MapSpot BANK = MapSpot.icon(3210, 3210, 0, 2, IconGroup.BANK);

	private static List<MapCheck.Found> find()
	{
		return MapCheck.find(Arrays.asList(BANK, NAMED, WAY_UP, BY_LABEL, UNNAMED, NOWHERE),
			spot -> spot == NOWHERE || spot == BANK ? null
				: spot == WAY_UP ? new WorldPoint(3200, 3200, 0) : new WorldPoint(spot.getX(), 9600, 0),
			(spot, to, withLabels) -> spot == NAMED ? "Named Dungeon"
				: spot == BY_LABEL && withLabels ? "Some Label" : null,
			LUMBRIDGE);
	}

	@Test
	public void anIconIsListedByWhatItLacks()
	{
		List<MapCheck.Found> all = find();
		// a bank is no dungeon icon, a named dungeon lacks nothing, a way up is named after its town
		assertEquals(3, all.size());
		assertEquals(Collections.singletonList(NOWHERE), spots(MapCheck.of(all, MapCheck.Kind.NO_LINK)));
		assertEquals(Collections.singletonList(UNNAMED), spots(MapCheck.of(all, MapCheck.Kind.NO_NAME)));
		List<MapCheck.Found> labeled = MapCheck.of(all, MapCheck.Kind.LABEL_NAME);
		assertEquals(Collections.singletonList(BY_LABEL), spots(labeled));
		assertEquals("Some Label", labeled.get(0).getName());
		assertNull(MapCheck.of(all, MapCheck.Kind.NO_LINK).get(0).getTo());
		// nearest Lumbridge first
		assertEquals(UNNAMED, all.get(0).getSpot());
	}

	@Test
	public void aRowGoesToItsIconAndTheFileKeepsWhatWasFilledIn()
	{
		List<MapCheck.Found> all = find();
		List<MapLabels.Label> labels = Collections.singletonList(
			new MapLabels.Label(Collections.singletonList("Lumbridge"), 3222, 3218, MapLabels.TOWN, Color.WHITE));
		List<MapCheck.Found> unnamed = MapCheck.of(all, MapCheck.Kind.NO_NAME);
		List<SideList.Row> rows = MapCheck.rows(unnamed, MapCheck.Kind.NO_NAME, labels, 5, Paths.get("no-name.tsv"));
		assertEquals(2, rows.size());
		assertNull(rows.get(0).getPoint());
		assertEquals(new WorldPoint(3200, 3200, 0), rows.get(1).getPoint());
		assertEquals("Lumbridge", rows.get(1).getText());
		assertTrue(rows.get(1).getSub(), rows.get(1).getSub().contains("leads to 3200, 9600, 0"));

		List<String> first = MapCheck.lines(unnamed, labels, Collections.emptyList());
		assertEquals(Arrays.asList(MapCheck.HEADER, "3200\t3200\t0\t3200\t9600\t0\t\tLumbridge\t"), first);
		List<String> edited = Arrays.asList(MapCheck.HEADER, first.get(1) + "Lumbridge Swamp Caves",
			"1\t2\t0\t\t\t\t\tGone\tno longer listed");
		assertEquals(Arrays.asList(MapCheck.HEADER, first.get(1) + "Lumbridge Swamp Caves"),
			MapCheck.lines(unnamed, labels, edited));
		// an icon that leads nowhere has no "leads to" cells
		assertEquals("3100\t3200\t0\t\t\t\t\tLumbridge\t",
			MapCheck.lines(MapCheck.of(all, MapCheck.Kind.NO_LINK), labels, Collections.emptyList()).get(1));
	}

	@Test
	public void aShopWithNoShopIconNearOnItsFloorIsListed()
	{
		// owner, 2026-11-14: Ordan's and Jorzik's shops in the Blast Furnace have no icon on the game's map
		Shops.Shop furnace = new Shops.Shop("Armour store", new WorldPoint(1939, 4968, 0), "Jorzik",
			Collections.emptyList(), 0);
		Shops.Shop marked = new Shops.Shop("General Store", new WorldPoint(3212, 3246, 0), "Shop keeper",
			Collections.emptyList(), 0);
		Shops.Shop upstairs = new Shops.Shop("Upstairs shop", new WorldPoint(3215, 3250, 1), "",
			Collections.emptyList(), 1);
		// a shop icon 4 tiles from the general store, on the ground floor; a bank icon by the Blast Furnace counts not
		MapSpot generalIcon = MapSpot.icon(3215, 3249, 0, 1, 1049);
		MapSpot bankIcon = MapSpot.icon(1940, 4966, 0, 2, IconGroup.BANK);
		List<MapCheck.Found> found = MapCheck.shopsWithoutIcon(Arrays.asList(furnace, marked, upstairs),
			Arrays.asList(generalIcon, bankIcon), LUMBRIDGE);
		// the general store has its icon; the upstairs shop is on another floor than that icon, and the furnace's has none
		assertEquals(2, found.size());
		assertEquals("Upstairs shop", found.get(0).getName());
		assertEquals("Armour store (Jorzik)", found.get(1).getName());
		assertEquals(new WorldPoint(1939, 4968, 0), found.get(1).point());
		List<SideList.Row> rows = MapCheck.rows(found, MapCheck.Kind.SHOP_NO_ICON, Collections.emptyList(), 3,
			Paths.get("shop-no-icon.tsv"));
		assertTrue(rows.get(0).getText(), rows.get(0).getText().startsWith("2 of the 3 shops have no shop icon"));
		assertEquals("Armour store (Jorzik)", rows.get(2).getText());
		assertEquals(new WorldPoint(1939, 4968, 0), rows.get(2).getPoint());
	}

	private static List<MapSpot> spots(List<MapCheck.Found> found)
	{
		List<MapSpot> out = new java.util.ArrayList<>();
		for (MapCheck.Found one : found)
		{
			out.add(one.getSpot());
		}
		return out;
	}
}
