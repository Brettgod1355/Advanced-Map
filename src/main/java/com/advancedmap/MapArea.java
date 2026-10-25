/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * The two maps the game's world coordinates hold: the surface, and everything the game keeps further north
 * (dungeons and caves around y 9,000 to 12,800, and other areas such as Zanaris above the surface). Each is its own
 * view, as on the game's world map, so a player in a dungeon sees the dungeon.
 * <p>
 * The region bounds are where the game cache has map regions (read from the live cache, 2026-10-25: surface
 * regions x 15-65, y 19-68; the rest x 18-98, y 69-199), with a few regions to spare for new content. The view
 * stops at them.
 */
enum MapArea
{
	SURFACE("Surface", 12, 68, 18, 68),
	UNDERGROUND("Underground", 15, 101, 69, 202);

	final String label;
	final int minRegionX;
	final int maxRegionX;
	final int minRegionY;
	final int maxRegionY;

	MapArea(String label, int minRegionX, int maxRegionX, int minRegionY, int maxRegionY)
	{
		this.label = label;
		this.minRegionX = minRegionX;
		this.maxRegionX = maxRegionX;
		this.minRegionY = minRegionY;
		this.maxRegionY = maxRegionY;
	}

	/** The map that holds a world tile's row. */
	static MapArea of(int worldY)
	{
		return (worldY >> 6) > SURFACE.maxRegionY ? UNDERGROUND : SURFACE;
	}

	MapArea other()
	{
		return this == SURFACE ? UNDERGROUND : SURFACE;
	}

	/** A view of this map that cannot be moved past its edges. */
	MapViewport newViewport()
	{
		return new MapViewport(minRegionX * 64, minRegionY * 64, (maxRegionX + 1) * 64, (maxRegionY + 1) * 64);
	}
}
