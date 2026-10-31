/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class OrbMenuTest
{
	@Test
	public void theLeftClickGoesToAdvancedMapAndEveryOtherEntryKeepsItsPlace()
	{
		// the orb's menu, bottom first: the last entry is the left click, here another plugin's put on top, ours under it
		String[] menu = {"Cancel", "Floating World Map", "Open Advanced Map", "Reimagined Map"};
		assertArrayEquals(new String[]{"Cancel", "Floating World Map", "Reimagined Map", "Open Advanced Map"},
			AdvancedMapPlugin.toTop(menu, 2));
		// already on top: unchanged
		assertArrayEquals(menu, AdvancedMapPlugin.toTop(menu, 3));
		assertArrayEquals(new String[]{"Floating World Map", "Cancel"}, AdvancedMapPlugin.toTop(new String[]{"Cancel", "Floating World Map"}, 0));
	}
}
