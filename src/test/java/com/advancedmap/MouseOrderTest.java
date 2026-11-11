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
	public void theMapHearsTheMouseBeforeOtherPlugins() throws Exception
	{
		// issue #3: RuneLite hands a click to the plugins in order and stops at the first that keeps it, so a plugin
		// whose clickable area lies under the map took most of the floor-down button while the map came last
		String plugin = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"AdvancedMapPlugin.java")), StandardCharsets.UTF_8);
		assertTrue(plugin.contains("mouseManager.registerMouseListener(0, input);"));
		assertTrue(plugin.contains("mouseManager.registerMouseWheelListener(0, input);"));
		// and it lets every click outside its box go on to the game and the other plugins
		String input = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"MapInput.java")), StandardCharsets.UTF_8);
		assertTrue(input.contains("if (part == GameBox.Part.OUTSIDE)"));
	}
}
