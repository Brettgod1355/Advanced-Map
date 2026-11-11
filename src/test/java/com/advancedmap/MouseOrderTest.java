/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MouseOrderTest
{
	@Test
	public void theMapHearsTheMouseFirstAndTurnsStretchedPositionsIntoTheGamesItself() throws Exception
	{
		// issue #3: RuneLite hands a click to the plugins in order and stops at the first that keeps it, so a plugin
		// whose clickable area lay under the map (over the game's tabs) took the floor-down button's clicks
		String plugin = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"AdvancedMapPlugin.java")), StandardCharsets.UTF_8);
		assertTrue(plugin.contains("mouseManager.registerMouseListener(0, input);"));
		assertTrue(plugin.contains("mouseManager.registerMouseWheelListener(0, input);"));
		// Stretched Mode registers at 0 too, so it is ahead again once started after the map: the map goes back in front
		// whenever a plugin starts, and reads the stretched window's positions as the game's itself (owner, 2026-11-11:
		// ahead of Stretched Mode without that, its clicks and zoom landed in the wrong place)
		assertTrue(plugin.contains("if (started && event.isLoaded() && input != null)"));
		assertTrue(plugin.contains("MapInput.unstretched(at, client.getStretchedDimensions()"));
		// and it lets every click outside its box go on to the game and the other plugins
		String input = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"MapInput.java")), StandardCharsets.UTF_8);
		assertTrue(input.contains("if (part == GameBox.Part.OUTSIDE)"));
	}
}
