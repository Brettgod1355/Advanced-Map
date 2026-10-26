/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.EntityOpsDefinition;
import com.advancedmap.cache.Location;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.ObjectDefinition;
import com.advancedmap.cache.Region;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;

/**
 * Advanced Map's own icons, found in a region's objects as it is drawn. A bank the game's map leaves out gets a bank
 * icon: its booths or chests (within a few tiles of each other) count as one bank, and a bank with a bank icon of the
 * game's near it already has one. A bank chest of a game event, which is something else on other days, shows only
 * while it is a bank. What a player builds or unlocks ({@link Unlockable}) marks the game's own icon on it, or gets an
 * icon where the game has none (the Culinaromancer's Chest, the hole from Lumbridge Castle's cellar).
 */
final class FoundIcons
{
	/** Bank objects this close to each other, in tiles on any floor, are one bank. */
	static final int SAME_BANK = 4;
	/** A bank icon of the game's this close to a bank's objects, or to something unlocked, is its own. */
	static final int ICON_REACH = 8;

	/** A bank object, or a group of them as one bank. */
	@AllArgsConstructor
	private static final class Bank
	{
		final Rectangle tiles;
		final int plane;
		final String name;
		final Unlock unlock;
	}

	private FoundIcons()
	{
	}

	/** The region's icons: the game's ({@code icons}, as found), marked where unlocked, and Advanced Map's own. */
	static List<MapSpot> find(Region region, MapSource source, List<MapSpot> icons)
	{
		List<MapSpot> out = new ArrayList<>(icons);
		List<Bank> banks = new ArrayList<>();
		for (Location location : region.getLocations())
		{
			ObjectDefinition object = source.object(location.getId());
			if (object.getMapAreaId() != -1)
			{
				continue;
			}
			int x = location.getPosition().getX();
			int y = location.getPosition().getY();
			int plane = RegionImages.floorOf(region, x, y, location.getPosition().getZ());
			Unlockable unlockable = Unlockable.of(location.getId());
			if (unlockable != null)
			{
				Unlock unlock = Unlock.of(object, source::object, usable(unlockable.category));
				if (unlock != null)
				{
					mark(out, x, y, plane, unlock, unlockable, source);
				}
				continue;
			}
			if (isBank(object))
			{
				banks.add(new Bank(new Rectangle(x, y, 1, 1), plane, plain(object.getName()), null));
				continue;
			}
			Unlock unlock = Unlock.of(object, source::object, FoundIcons::isBank);
			if (unlock != null)
			{
				banks.add(new Bank(new Rectangle(x, y, 1, 1), plane, bankName(object, source),
					unlock.always() ? null : unlock));
			}
		}
		int bankArea = source.categoryArea(IconGroup.BANK);
		if (bankArea != -1)
		{
			for (Bank bank : group(banks))
			{
				if (!nearGameBank(out, bank.tiles))
				{
					int x = bank.tiles.x + bank.tiles.width / 2;
					int y = bank.tiles.y + bank.tiles.height / 2;
					out.add(MapSpot.found(x, y, bank.plane, bankArea, IconGroup.BANK, bank.unlock, null, bank.name,
						bank.tiles));
				}
			}
		}
		return out;
	}

	/**
	 * Marks the game's icon of something unlocked, the nearest of its kind within {@link #ICON_REACH}, or adds one
	 * where the game has none.
	 */
	private static void mark(List<MapSpot> out, int x, int y, int plane, Unlock unlock, Unlockable unlockable,
		MapSource source)
	{
		int best = -1;
		int bestDistance = ICON_REACH + 1;
		for (int i = 0; i < out.size(); i++)
		{
			MapSpot spot = out.get(i);
			int distance = Math.max(Math.abs(spot.getX() - x), Math.abs(spot.getY() - y));
			if (spot.getCategory() == unlockable.category && spot.getUnlock() == null && distance < bestDistance)
			{
				best = i;
				bestDistance = distance;
			}
		}
		if (best != -1)
		{
			out.set(best, out.get(best).unlockedBy(unlock, unlockable));
			return;
		}
		int area = source.categoryArea(unlockable.category);
		if (area != -1)
		{
			out.add(MapSpot.found(x, y, plane, area, unlockable.category, unlock, unlockable, null, null));
		}
	}

	/** Bank objects as banks: those within {@link #SAME_BANK} of each other, the same way unlocked or not, as one. */
	private static List<Bank> group(List<Bank> banks)
	{
		List<Bank> out = new ArrayList<>();
		for (Bank bank : banks)
		{
			Bank joined = bank;
			for (int i = out.size() - 1; i >= 0; i--)
			{
				Bank other = out.get(i);
				Rectangle reach = new Rectangle(other.tiles);
				reach.grow(SAME_BANK, SAME_BANK);
				if (reach.intersects(joined.tiles) && (other.unlock == null) == (joined.unlock == null))
				{
					joined = new Bank(other.tiles.union(joined.tiles), Math.min(other.plane, joined.plane), other.name,
						other.unlock);
					out.remove(i);
				}
			}
			out.add(joined);
		}
		return out;
	}

	/** True when a bank icon of the game's is within {@link #ICON_REACH} of a bank's tiles. */
	static boolean nearGameBank(List<MapSpot> spots, Rectangle tiles)
	{
		Rectangle reach = new Rectangle(tiles);
		reach.grow(ICON_REACH, ICON_REACH);
		for (MapSpot spot : spots)
		{
			if (spot.getGroup() == IconGroup.BANKS && spot.getCluster() == null && spot.getUnlockable() == null
				&& reach.contains(spot.getX(), spot.getY()))
			{
				return true;
			}
		}
		return false;
	}

	/** True for a bank: a booth, chest or counter with "Bank", or a bank chest with "Use"; not a deposit box. */
	static boolean isBank(ObjectDefinition object)
	{
		String name = object.getName().toLowerCase(Locale.ROOT);
		if (name.contains("deposit"))
		{
			return false;
		}
		for (EntityOpsDefinition.Op op : object.getOps().getOps())
		{
			if (op != null && op.text != null && (op.text.equalsIgnoreCase("bank")
				|| op.text.equalsIgnoreCase("use") && name.contains("bank")))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasOps(ObjectDefinition object)
	{
		return object.getOps().getOps().stream().anyMatch(op -> op != null && op.text != null);
	}

	/** The name of the bank an object stands for. */
	private static String bankName(ObjectDefinition object, MapSource source)
	{
		for (int state : object.getConfigChangeDest())
		{
			ObjectDefinition other = state == -1 ? null : source.object(state);
			if (other != null && isBank(other))
			{
				return plain(other.getName());
			}
		}
		return "Bank";
	}

	/** A name without the game's color tags ("<col=ffff00>Bank Camel</col>"). */
	private static String plain(String name)
	{
		return name.replaceAll("<[^>]*>", "");
	}

	/** What makes something unlocked usable: a bank, or a passage with something to do on it. */
	static Predicate<ObjectDefinition> usable(int category)
	{
		return category == IconGroup.BANK ? FoundIcons::isBank : FoundIcons::hasOps;
	}
}
