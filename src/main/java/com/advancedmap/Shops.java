/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * The game's shops and what they sell, with prices, from {@code shops.tsv} (the OSRS Wiki's shop and stock tables;
 * see THIRD_PARTY_NOTICES.md). Each shop is placed where the wiki's map shows it: its building, else its owner.
 */
@Slf4j
final class Shops
{
	/** How far a shop icon may be from where the list places its shop. */
	static final int REACH = 10;

	@Value
	static class Item
	{
		String name;
		String price;
		String currency;
		String stock;

		/** "100,000 gp", "60 Tokkul", or the price as the wiki writes it. */
		String priceText()
		{
			try
			{
				long value = Long.parseLong(price);
				if (value == 0)
				{
					return "Free";
				}
				return "Coins".equalsIgnoreCase(currency) ? String.format("%,d gp", value)
					: String.format("%,d %s", value, currency);
			}
			catch (NumberFormatException e)
			{
				return price.isEmpty() ? "" : price + " " + currency;
			}
		}
	}

	@Value
	static class Shop
	{
		String name;
		WorldPoint point;
		String owner;
		List<Item> items;
	}

	private static volatile List<Shop> shops = Collections.emptyList();

	private Shops()
	{
	}

	static List<Shop> all()
	{
		return shops;
	}

	/** Reads the list from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream in = Shops.class.getResourceAsStream("shops.tsv"))
		{
			if (in != null)
			{
				shops = parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the shops", e);
		}
	}

	static List<Shop> parse(InputStream in) throws IOException
	{
		List<Shop> out = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		List<Item> items = null;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			if ("S".equals(f[0]))
			{
				items = new ArrayList<>();
				WorldPoint at = Prifddinas.toSurface(new WorldPoint(Integer.parseInt(f[2]), Integer.parseInt(f[3]),
					Integer.parseInt(f[4])));
				out.add(new Shop(f[1], at, f[5], items));
			}
			else if ("I".equals(f[0]) && items != null)
			{
				items.add(new Item(f[1], f[2], f[3], f[4]));
			}
		}
		return Collections.unmodifiableList(out);
	}

	/** Every shop within {@link #REACH} of a tile on its floor, nearest first. */
	static List<Shop> within(int x, int y, int plane)
	{
		List<Shop> out = new ArrayList<>();
		for (Shop shop : shops)
		{
			WorldPoint at = shop.getPoint();
			if (at.getPlane() == plane && Math.max(Math.abs(at.getX() - x), Math.abs(at.getY() - y)) <= REACH)
			{
				out.add(shop);
			}
		}
		out.sort(java.util.Comparator.comparingInt(shop -> Math.max(Math.abs(shop.getPoint().getX() - x),
			Math.abs(shop.getPoint().getY() - y))));
		return out;
	}

	/** The shop at a map icon: the nearest placed within {@link #REACH} tiles on its floor, or null. */
	static Shop near(int x, int y, int plane)
	{
		Shop best = null;
		int bestDistance = REACH + 1;
		for (Shop shop : shops)
		{
			WorldPoint at = shop.getPoint();
			int distance = Math.max(Math.abs(at.getX() - x), Math.abs(at.getY() - y));
			if (at.getPlane() == plane && distance < bestDistance)
			{
				best = shop;
				bestDistance = distance;
			}
		}
		return best;
	}
}
