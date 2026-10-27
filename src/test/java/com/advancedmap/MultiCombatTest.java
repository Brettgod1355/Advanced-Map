/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiCombatTest
{
	@BeforeClass
	public static void load()
	{
		MultiCombat.load();
	}

	@Test
	public void theBundledAreasCoverTheKnownMultiZones()
	{
		List<MultiCombat.Zone> zones = MultiCombat.zones();
		assertTrue(zones.size() > 90);
		// the Wilderness: the Dark warrior's palace, and the north-east of the free-to-play Wilderness
		assertTrue(MultiCombat.contains(3030, 3630));
		assertTrue(MultiCombat.contains(3200, 3870));
		// not multi: Lumbridge, and Edgeville just south of the Wilderness
		assertFalse(MultiCombat.contains(3222, 3218));
		assertFalse(MultiCombat.contains(3094, 3491));
	}
}
