/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The settings panel orders items by their position, ties by name, so two items at one position show out of order.
 * Read from the source text, so that no reflection is needed.
 */
public class ConfigPositionsTest
{
	@Test
	public void noTwoShownItemsOfASectionShareAPosition() throws IOException
	{
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"AdvancedMapConfig.java")), StandardCharsets.UTF_8);
		Pattern item = Pattern.compile("@Config(Item|Section)\\((.*?)\\)\\s*\\n", Pattern.DOTALL);
		Map<String, String> seen = new HashMap<>();
		Matcher m = item.matcher(source);
		while (m.find())
		{
			String body = m.group(2);
			if (body.contains("hidden = true"))
			{
				continue;
			}
			Matcher position = Pattern.compile("position = (-?\\d+)").matcher(body);
			if (!position.find())
			{
				continue;
			}
			Matcher section = Pattern.compile("section = (\\w+)").matcher(body);
			String key = (section.find() ? section.group(1) : "top") + ":" + position.group(1);
			String name = body.replaceAll("\\s+", " ").substring(0, Math.min(60, body.length()));
			assertNull("two items at " + key + ":\n" + seen.get(key) + "\n" + name, seen.put(key, name));
		}
	}

	/**
	 * RuneLite reads settings through a proxy of the config interface, which cannot reach a type that is not public:
	 * every plugin type a setting returns must be public, and must be in {@code check/ConfigTypesTest}, the test in
	 * another package that proves it at compile time, so that a setting added later cannot slip past either.
	 */
	@Test
	public void everyPluginTypeASettingReturnsIsPublicAndCheckedAtCompileTime() throws IOException
	{
		Path main = Paths.get("src", "main", "java", "com", "advancedmap");
		String config = new String(Files.readAllBytes(main.resolve("AdvancedMapConfig.java")), StandardCharsets.UTF_8);
		String check = new String(Files.readAllBytes(Paths.get("src", "test", "java", "com", "advancedmap", "check",
			"ConfigTypesTest.java")), StandardCharsets.UTF_8);
		Matcher setting = Pattern.compile("\\n\\tdefault (\\w+) \\w+\\(\\)").matcher(config);
		int pluginTypes = 0;
		while (setting.find())
		{
			String type = setting.group(1);
			Path declared = main.resolve(type + ".java");
			if (!Files.exists(declared))
			{
				// boolean, int, String, Keybind: RuneLite's and Java's own
				continue;
			}
			pluginTypes++;
			String source = new String(Files.readAllBytes(declared), StandardCharsets.UTF_8);
			assertTrue(type + " is returned by a setting, so it must be public", source.contains("\npublic enum " + type)
				|| source.contains("\npublic class " + type) || source.contains("\npublic interface " + type));
			assertTrue(type + " is returned by a setting, so check/ConfigTypesTest must name it", check.contains(type + ".values()")
				|| check.contains(type + ".class"));
		}
		assertTrue("the three enums today", pluginTypes >= 3);
	}
}
