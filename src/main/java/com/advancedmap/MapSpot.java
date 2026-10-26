/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.client.game.AgilityShortcut;

/**
 * One thing on the map a player can click: an icon the game's map has (a bank, a quest start, a shortcut), found on
 * the object the game puts it on, or a teleport destination. In world coordinates.
 */
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
final class MapSpot
{
	/** How far a shortcut's icon may be from where RuneLite's shortcut list puts it, in tiles. */
	static final int SHORTCUT_REACH = 4;

	@EqualsAndHashCode.Include
	private final String key;
	private final int x;
	private final int y;
	/** The game's map area of the icon, or -1 for a teleport. */
	private final int areaId;
	/** The game's icon category, or -1 for a teleport. */
	private final int category;
	private final IconGroup group;
	/** For a teleport, else null. */
	private final Teleport teleport;
	/** For an agility shortcut RuneLite knows, else null. */
	private final AgilityShortcut shortcut;

	private MapSpot(String key, int x, int y, int areaId, int category, IconGroup group, Teleport teleport,
		AgilityShortcut shortcut)
	{
		this.key = key;
		this.x = x;
		this.y = y;
		this.areaId = areaId;
		this.category = category;
		this.group = group;
		this.teleport = teleport;
		this.shortcut = shortcut;
	}

	/** An icon of the game's map at a world tile. */
	static MapSpot icon(int x, int y, int areaId, int category)
	{
		boolean isShortcut = category == IconGroup.AGILITY_SHORTCUT || category == IconGroup.AGILITY_SHORTCUT_ONE_WAY;
		return new MapSpot("a" + areaId + "@" + x + "," + y, x, y, areaId, category, IconGroup.of(category), null,
			isShortcut ? nearestShortcut(x, y) : null);
	}

	static MapSpot teleport(Teleport teleport)
	{
		return new MapSpot("t" + teleport.name(), teleport.getLocation().getX(), teleport.getLocation().getY(), -1, -1,
			IconGroup.TELEPORTS, teleport, null);
	}

	/** The shortcut in RuneLite's list placed nearest a map icon, the lowest level on a tie; null if none is close. */
	static AgilityShortcut nearestShortcut(int x, int y)
	{
		AgilityShortcut best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (AgilityShortcut shortcut : AgilityShortcut.values())
		{
			if (shortcut.getWorldMapLocation() == null)
			{
				continue;
			}
			int distance = Math.max(Math.abs(shortcut.getWorldMapLocation().getX() - x),
				Math.abs(shortcut.getWorldMapLocation().getY() - y));
			if (distance > SHORTCUT_REACH)
			{
				continue;
			}
			if (distance < bestDistance || distance == bestDistance && shortcut.getLevel() < best.getLevel())
			{
				best = shortcut;
				bestDistance = distance;
			}
		}
		return best;
	}
}
