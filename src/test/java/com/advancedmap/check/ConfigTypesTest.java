/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap.check;

import com.advancedmap.FloorNumbers;
import com.advancedmap.MonsterOrder;
import com.advancedmap.RoutingPlugin;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * RuneLite reads settings through a proxy of the config interface, and a proxy cannot reach a type that is not
 * public: a setting returning a package-private enum once stopped the plugin from starting. This test lives in
 * another package, so it compiles only while every type a setting returns is public; no reflection is needed. A new
 * enum used by a setting is added here.
 */
public class ConfigTypesTest
{
	@Test
	public void everyTypeASettingReturnsIsPublic()
	{
		assertTrue(MonsterOrder.values().length > 0);
		assertTrue(FloorNumbers.values().length > 0);
		assertTrue(RoutingPlugin.values().length > 0);
	}
}
