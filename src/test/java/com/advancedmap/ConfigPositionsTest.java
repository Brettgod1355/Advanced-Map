/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertNull;

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
}
