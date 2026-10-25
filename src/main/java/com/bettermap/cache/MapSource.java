/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.bettermap.cache;

/**
 * Everything {@link MapImageRenderer} reads, one lookup at a time. Implementations may block while the data is
 * fetched, so the renderer runs off the client thread.
 */
public interface MapSource
{
	/** The region at region coordinates (world coordinates divided by 64), or null where the game has none. */
	Region region(int regionX, int regionY);

	/** The region holding a world tile, or null. */
	default Region regionAt(int worldX, int worldY)
	{
		if (worldX < 0 || worldY < 0)
		{
			return null;
		}
		return region(worldX >> 6, worldY >> 6);
	}

	UnderlayDefinition underlay(int id);

	OverlayDefinition overlay(int id);

	ObjectDefinition object(int id);

	/** A texture's average color, packed HSL as the game stores it. */
	int textureColor(int textureId);

	/** A frame of the map scenery sprite sheet (trees, rocks and so on), or null. */
	MapSprite mapScene(int index);

	/** The map icon of a map area (bank, altar and so on), or null when it has none. */
	MapSprite areaIcon(int areaId);
}
