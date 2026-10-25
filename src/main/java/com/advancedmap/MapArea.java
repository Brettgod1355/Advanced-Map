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
 */
enum MapArea
{
	SURFACE("Surface", 0, 68),
	UNDERGROUND("Underground", 69, 200);

	/** Region columns either map looks at; the game's map lies well inside. */
	static final int MAX_REGION_X = 80;

	final String label;
	final int minRegionY;
	final int maxRegionY;

	MapArea(String label, int minRegionY, int maxRegionY)
	{
		this.label = label;
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
}
