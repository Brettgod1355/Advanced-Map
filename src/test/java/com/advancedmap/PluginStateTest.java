/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The plugin's own state between opens and closes, on an instance with nothing injected. */
public class PluginStateTest
{
	@Test
	public void closingGivesTheKeysBackWhateverWasOpen()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.filters().setOpen(true);
		assertTrue("the Filters list takes the keys", plugin.typing());
		plugin.giveKeysBack();
		assertFalse("closed with the map: keys to the game on the next open", plugin.typing());
		assertFalse(plugin.filters().isOpen());

		plugin.search().setFocused(true);
		plugin.editor().create(new WorldPoint(3222, 3218, 0));
		assertTrue(plugin.typing());
		plugin.giveKeysBack();
		assertFalse(plugin.typing());
		assertFalse(plugin.editor().isOpen());
		assertFalse(plugin.search().isFocused());
	}
}
