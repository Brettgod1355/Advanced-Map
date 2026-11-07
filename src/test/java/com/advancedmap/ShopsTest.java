/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ShopsTest
{
	private static ByteArrayInputStream tsv(String text)
	{
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void aShopListsItsItemsAndPrices() throws IOException
	{
		List<Shops.Shop> shops = Shops.parse(tsv("# a comment\n"
			+ "S\tDaga's Scimitar Smithy\t2759\t2774\t0\tDaga\n"
			+ "I\tDragon scimitar\t100000\tCoins\t5\n"
			+ "S\tTzHaar-Hur-Tel's Equipment Store\t2479\t5146\t0\tTzHaar-Hur-Tel\n"
			+ "I\tObsidian cape\t90000\tTokkul\t10\n"
			+ "I\tFree thing\t\tCoins\t1\n"));
		assertEquals(2, shops.size());
		assertEquals(new WorldPoint(2759, 2774, 0), shops.get(0).getPoint());
		assertEquals("100,000 gp", shops.get(0).getItems().get(0).priceText());
		assertEquals("90,000 Tokkul", shops.get(1).getItems().get(0).priceText());
		assertEquals("", shops.get(1).getItems().get(1).priceText());
	}

	@Test
	public void theBundledShopsAreFoundByTheirIcons()
	{
		Shops.load();
		assertTrue(Shops.all().size() > 500);
		Shops.Shop daga = Shops.near(2759, 2774, 0);
		assertTrue(daga.getName().startsWith("Daga"));
		assertTrue(daga.getItems().stream().anyMatch(item -> item.getName().equals("Dragon scimitar")));
		assertNull(Shops.near(2759, 2774 + Shops.REACH + 50, 0));
		assertEquals(daga, Shops.within(2759, 2774, 0).get(0));
	}

	@Test
	public void spawnsReadTheirTilesAndLevels() throws IOException
	{
		List<Spawns.Creature> creatures = Spawns.parse(tsv("M\tGoblin\t2,5\t3250,3240,0;3251,3241,0\n"
			+ "N\tHans\t\t3221,3218,0\n"));
		assertEquals(2, creatures.size());
		assertTrue(creatures.get(0).isMonster());
		assertEquals("2, 5", creatures.get(0).getLevels());
		assertEquals(2, creatures.get(0).getTiles().size());
		assertEquals(new WorldPoint(3221, 3218, 0), creatures.get(1).getTiles().get(0));
		Spawns.load();
		assertTrue(Spawns.all().size() > 800);
	}

	@Test
	public void aShopUpstairsWhereTheMapDrawsNoGroundIsShownBelow() throws IOException
	{
		// Brother Jered, upstairs in the Edgeville Monastery: the game's map draws no ground on that floor there
		String tsv = "S\tBrother Jered\t3052\t3491\t0\tBrother Jered\t1\nS\tBob's Brilliant Axes\t3230\t3203\t0\tBob\n";
		List<Shops.Shop> shops = Shops.parse(new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8)));
		assertEquals(new WorldPoint(3052, 3491, 0), shops.get(0).getPoint());
		assertEquals(1, shops.get(0).getFloor());
		assertEquals("a shop on its own floor", 0, shops.get(1).getFloor());
		Shops.load();
		assertTrue(Shops.all().stream().anyMatch(shop -> shop.getName().equals("Brother Jered") && shop.getFloor() == 1
			&& shop.getPoint().getPlane() == 0));
	}

	@Test
	public void aShopsInfoBoxListsItsFirstItemsAndSaysHowManyMore()
	{
		// pointing at a shop shows what it sells without a click (owner, 2026-11-07); a click still lists it all
		Shops.load();
		Shops.Shop big = Shops.all().stream().filter(shop -> shop.getItems().size() > MapIcons.SHOP_ITEMS + 2)
			.findFirst().orElse(null);
		List<MapIcons.Line> lines = new java.util.ArrayList<>();
		MapIcons.shopInfo(lines, big);
		assertEquals(big.getName(), lines.get(0).getText());
		Shops.Item first = big.getItems().get(0);
		assertEquals(first.getName() + ": " + first.priceText(), lines.get(1).getText());
		assertEquals(1 + MapIcons.SHOP_ITEMS + 1, lines.size());
		assertEquals("and " + (big.getItems().size() - MapIcons.SHOP_ITEMS) + " more: click for them all",
			lines.get(lines.size() - 1).getText());
		// a small shop lists all it sells, and no "more" line
		Shops.Shop small = Shops.all().stream().filter(shop -> !shop.getItems().isEmpty()
			&& shop.getItems().size() <= MapIcons.SHOP_ITEMS).findFirst().orElse(null);
		lines.clear();
		MapIcons.shopInfo(lines, small);
		assertEquals(1 + small.getItems().size(), lines.size());
		lines.clear();
		MapIcons.shopInfo(lines, null);
		assertTrue(lines.isEmpty());
	}
}
