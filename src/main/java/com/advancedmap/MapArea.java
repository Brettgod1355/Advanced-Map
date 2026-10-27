/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * The two maps the game's world coordinates hold: the surface, and everything else (dungeons and caves around y 9,000
 * to 12,800, and the instances and minigame areas the game keeps beside the surface, such as those just north of it).
 * Each is its own view, as on the game's world map, so a player in a dungeon sees the dungeon.
 * <p>
 * The surface is the rectangle the game fills with regions (OpenRS2 cache 2746, 2026-10-20: region columns 15-62, rows
 * 32-65), with two columns and rows to spare for new land except to the north, where the instances start at row 66.
 * Everything outside it is on the other map, whose bounds are where the game has regions there (columns 18-98, rows
 * 66-199) with a few to spare. The views stop at the bounds.
 */
enum MapArea
{
	SURFACE("Surface", 13, 64, 30, 65),
	UNDERGROUND("Underground", 15, 101, 66, 202);

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

	/** How far north of the ground most dungeons are: Lumbridge Castle's cellar lies 6,400 tiles north of the castle. */
	static final int UNDERGROUND_SHIFT = 6400;

	/**
	 * True for an underground tile that lies under the surface map (a dungeon under a town); false for the scattered
	 * places that are under nothing (Zanaris, minigames).
	 */
	static boolean underTheGround(double x, double y)
	{
		int regionX = (int) Math.floor(x) >> 6;
		int regionY = (int) Math.floor(y - UNDERGROUND_SHIFT) >> 6;
		return of((int) Math.floor(y)) == UNDERGROUND && regionX >= SURFACE.minRegionX && regionX <= SURFACE.maxRegionX
			&& regionY >= SURFACE.minRegionY && regionY <= SURFACE.maxRegionY;
	}

	/** The map that holds a world tile's row. */
	static MapArea of(int worldY)
	{
		int row = worldY >> 6;
		return row > SURFACE.maxRegionY || row < SURFACE.minRegionY ? UNDERGROUND : SURFACE;
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
