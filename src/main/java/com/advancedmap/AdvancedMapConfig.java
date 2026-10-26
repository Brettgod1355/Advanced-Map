/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(AdvancedMapConfig.GROUP)
public interface AdvancedMapConfig extends Config
{
	String GROUP = "advancedmap";

	@ConfigItem(
		keyName = "toggleKey",
		name = "Open or close the map",
		description = "The key that opens the map, and closes it again. Escape closes it too.",
		position = 0
	)
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "sidebarButton",
		name = "Sidebar button",
		description = "Show a button in the sidebar that opens the map.",
		position = 1
	)
	default boolean sidebarButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "popOut",
		name = "Open in its own window",
		description = "Open the map in a window of its own instead of over the game, to keep beside the game's own"
			+ " world map. The Pop out and Over the game buttons on the map change this too.",
		position = 2
	)
	default boolean popOut()
	{
		return false;
	}

	@ConfigItem(
		keyName = "wildernessLines",
		name = "Wilderness lines",
		description = "Draw the Wilderness levels on the map: a line where each level starts, stronger lines at the"
			+ " level 20 and 30 teleport limits, and the areas with a level of their own. They follow the game's level"
			+ " count, which starts a few tiles south of the ditch; Ferox Enclave is not marked.",
		position = 3
	)
	default boolean wildernessLines()
	{
		return true;
	}
}
