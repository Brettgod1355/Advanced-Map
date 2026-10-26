/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Rectangle;
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
	/** The floor: 0 on the ground. */
	private final int plane;
	/** The game's map area of the icon, or -1 for a teleport. */
	private final int areaId;
	/** The game's icon category, or -1 for a teleport. */
	private final int category;
	private final IconGroup group;
	/** For a teleport, else null. */
	private final Teleport teleport;
	/** For an agility shortcut RuneLite knows, else null. */
	private final AgilityShortcut shortcut;
	/** For an icon of something the player builds or unlocks, how the game switches it; else null. */
	private final Unlock unlock;
	/** What that needs, when known; null for one shown only while usable (a chest of a game event). */
	private final Unlockable unlockable;
	/** For an icon the game's map leaves out, the object it stands for ("Bank booth"); else null. */
	private final String detail;
	/**
	 * For a bank the game's map leaves out, the tiles of the bank objects it stands for: the icon is left out after
	 * all where the game has a bank icon near them (found once the region with that icon is drawn). Else null.
	 */
	private final Rectangle cluster;

	private MapSpot(String key, int x, int y, int plane, int areaId, int category, IconGroup group, Teleport teleport,
		AgilityShortcut shortcut, Unlock unlock, Unlockable unlockable, String detail, Rectangle cluster)
	{
		this.key = key;
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.areaId = areaId;
		this.category = category;
		this.group = group;
		this.teleport = teleport;
		this.shortcut = shortcut;
		this.unlock = unlock;
		this.unlockable = unlockable;
		this.detail = detail;
		this.cluster = cluster;
	}

	/** An icon of the game's map at a world tile. */
	static MapSpot icon(int x, int y, int plane, int areaId, int category)
	{
		boolean isShortcut = category == IconGroup.AGILITY_SHORTCUT || category == IconGroup.AGILITY_SHORTCUT_ONE_WAY;
		return new MapSpot("a" + areaId + "@" + x + "," + y, x, y, plane, areaId, category, IconGroup.of(category),
			null, isShortcut ? nearestShortcut(x, y) : null, null, null, null, null);
	}

	/**
	 * An icon of Advanced Map's own where the game's map has none: a bank (with the bank objects it stands for), or
	 * something the player unlocks.
	 */
	static MapSpot found(int x, int y, int plane, int areaId, int category, Unlock unlock, Unlockable unlockable,
		String detail, Rectangle cluster)
	{
		return new MapSpot("a" + areaId + "@" + x + "," + y, x, y, plane, areaId, category, IconGroup.of(category),
			null, null, unlock, unlockable, detail, cluster);
	}

	/** The same icon of the game's, for something the player builds or unlocks. */
	MapSpot unlockedBy(Unlock unlock, Unlockable unlockable)
	{
		return new MapSpot(key, x, y, plane, areaId, category, group, teleport, shortcut, unlock, unlockable, detail,
			cluster);
	}

	static MapSpot teleport(Teleport teleport)
	{
		return new MapSpot("t" + teleport.name(), teleport.getLocation().getX(), teleport.getLocation().getY(),
			teleport.getLocation().getPlane(), -1, -1, IconGroup.TELEPORTS, teleport, null, null, null, null, null);
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
