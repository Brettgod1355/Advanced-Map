/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WhatsNewTest
{
	@Test
	public void theVersionMatchesTheBuildFiles() throws Exception
	{
		assertTrue("build.gradle", lines("build.gradle").contains("version = '" + WhatsNew.VERSION + "'"));
		assertTrue("runelite-plugin.properties", lines("runelite-plugin.properties").contains("version="
			+ WhatsNew.VERSION));
	}

	private static List<String> lines(String name) throws Exception
	{
		String text = new String(Files.readAllBytes(new File(name).toPath()), StandardCharsets.UTF_8);
		return Arrays.asList(text.split("\\R"));
	}

	@Test
	public void thePageKeepsEveryVersionNewestFirstAndTheChangesAreTheRunningOnes()
	{
		// owner, 2026-11-08: "previous versions would remain in the history ... newest at the top", each with the day
		// it went to the Plugin Hub ("TBD" until then)
		List<String> headings = new java.util.ArrayList<>();
		for (String[] block : MapHelp.page("WHATS_NEW"))
		{
			if (block[0].equals("#"))
			{
				headings.add(block[1]);
			}
		}
		assertTrue(headings.get(0), headings.get(0).startsWith("Version " + WhatsNew.VERSION + " - "));
		java.util.regex.Pattern heading = java.util.regex.Pattern.compile("Version (\\d+(\\.\\d+)+) - (TBD|\\d{1,2}/\\d{1,2}/\\d{2})");
		double last = Double.MAX_VALUE;
		for (String text : headings)
		{
			java.util.regex.Matcher m = heading.matcher(text);
			assertTrue(text, m.matches());
			double version = Double.parseDouble(m.group(1).replaceAll("^(\\d+\\.\\d+).*", "$1"));
			assertTrue("newest first: " + text, version <= last);
			last = version;
		}
		assertTrue(headings.contains("Version 1.1 - 11/8/26"));
		assertTrue(headings.contains("Version 1.0 - 10/31/26"));
		// only the running version's lines count as its changes: an older version's are history
		assertFalse(WhatsNew.CHANGES.isEmpty());
		assertFalse(WhatsNew.CHANGES.contains("Hover over a shop to see what it sells"));
	}

	@Test
	public void aStartTellsAnUpdateFromAFreshInstallAndTheSameVersionAgain()
	{
		// an update from another version brings news when the note has any; a fresh install and a restart bring none
		assertTrue(WhatsNew.start("0.9.0").isUpdated());
		assertEquals(!WhatsNew.CHANGES.isEmpty(), WhatsNew.start("0.9.0").isNews());
		assertFalse("a fresh install", WhatsNew.start("").isUpdated() || WhatsNew.start("").isNews());
		assertFalse("a fresh install", WhatsNew.start(null).isUpdated());
		assertFalse("the same version again", WhatsNew.start(WhatsNew.VERSION).isUpdated()
			|| WhatsNew.start(WhatsNew.VERSION).isNews());
	}
}
