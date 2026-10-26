/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import lombok.RequiredArgsConstructor;

/**
 * How the map numbers the floors above the ground: the ground floor counted as the first (Ground, 2, 3, 4), or the
 * floor above it as the first, as in the UK and on the Old School wiki (Ground, 1, 2, 3).
 */
@RequiredArgsConstructor
enum FloorNumbers
{
	GROUND_IS_FIRST("Ground, 2, 3, 4", 1),
	FIRST_ABOVE_GROUND("Ground, 1, 2, 3", 0);

	private final String label;
	/** The number the ground floor would have. */
	private final int ground;

	/** A floor's name: "Ground floor", then "Floor 2" (or "Floor 1") and up. */
	String name(int plane)
	{
		return plane == 0 ? "Ground floor" : "Floor " + (plane + ground);
	}

	@Override
	public String toString()
	{
		return label;
	}
}
