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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MouseOrderTest
{
	@Test
	public void theMapHearsTheMouseAfterStretchedModeTurnsItIntoTheGamesPosition() throws Exception
	{
		// RuneLite's Stretched Mode registers its listeners at position 0, to turn the window's mouse position into the
		// game's for every listener after it; a map registered at 0 too came ahead of it at each client start, and its
		// clicks and zoom landed in the wrong place (owner, 2026-11-11)
		String plugin = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"AdvancedMapPlugin.java")), StandardCharsets.UTF_8);
		assertTrue(plugin.contains("mouseManager.registerMouseListener(input);"));
		assertTrue(plugin.contains("mouseManager.registerMouseWheelListener(input);"));
		assertFalse(plugin.contains("registerMouseListener(0,"));
		assertFalse(plugin.contains("registerMouseWheelListener(0,"));
		// and it lets every click outside its box go on to the game and the other plugins
		String input = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"MapInput.java")), StandardCharsets.UTF_8);
		assertTrue(input.contains("if (part == GameBox.Part.OUTSIDE)"));
	}
}
