/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * The game's map data could not be read in step (a game update changed a format this plugin reads): nothing is
 * drawn until the plugin is updated, and nothing is marked as missing from the game. Thrown by every read once.
 */
class MapDataException extends RuntimeException
{
	MapDataException(RuntimeException cause)
	{
		super("the game's map data could not be read: a game update may have changed its format", cause);
	}
}
