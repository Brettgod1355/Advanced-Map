/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The defaults a new install gets. Every method of the config has a default, so an empty implementation reads them
 * without reflection.
 */
public class ConfigDefaultsTest
{
	private final AdvancedMapConfig defaults = new AdvancedMapConfig()
	{
	};

	@Test
	public void overlaysThatPaintOverTheMapStartOff()
	{
		assertFalse("multi-combat tint is opt-in (owner, 2026-10-30)", defaults.multiCombat());
		assertFalse(defaults.slayerTask());
	}

	@Test
	public void theMapItselfStartsOn()
	{
		assertTrue(defaults.wildernessLines());
		assertTrue(defaults.placeNames());
	}
}
