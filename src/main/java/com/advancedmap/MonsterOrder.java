/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import lombok.RequiredArgsConstructor;

/** The order a dungeon preview lists the monsters inside in. */
@RequiredArgsConstructor
public enum MonsterOrder
{
	HIGHEST_FIRST("Highest level first"),
	LOWEST_FIRST("Lowest level first");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
