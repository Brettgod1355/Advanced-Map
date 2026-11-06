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
import static org.junit.Assert.assertNull;
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

	@Test
	public void anUpdateIsSaidOnceAndAFreshInstallHearsNothing()
	{
		assertEquals("Advanced Map updated to " + WhatsNew.VERSION + ": icons say more.",
			WhatsNew.message("0.9.0", "icons say more"));
		assertNull("the same version again", WhatsNew.message(WhatsNew.VERSION, "icons say more"));
		assertNull("a fresh install", WhatsNew.message("", "icons say more"));
		assertNull("a fresh install", WhatsNew.message(null, "icons say more"));
		assertNull("nothing worth a line", WhatsNew.message("0.9.0", ""));
		// a line still waiting from an earlier start, whose version has been noted since, is said all the same
		assertEquals("Advanced Map updated to " + WhatsNew.VERSION + ": icons say more.",
			WhatsNew.message(WhatsNew.VERSION, "icons say more", true));
		assertNull(WhatsNew.message(WhatsNew.VERSION, "", true));
	}

	private static List<String> lines(String name) throws Exception
	{
		String text = new String(Files.readAllBytes(new File(name).toPath()), StandardCharsets.UTF_8);
		return Arrays.asList(text.split("\\R"));
	}

	@Test
	public void aStartTellsAnUpdateFromAFreshInstallAndTheSameVersionAgain()
	{
		// an update from another version brings news when the note has any; a fresh install and a restart bring none
		assertTrue(WhatsNew.start("1.0.0").isUpdated());
		assertEquals(!WhatsNew.NOTE.isEmpty(), WhatsNew.start("1.0.0").isNews());
		assertFalse("a fresh install", WhatsNew.start("").isUpdated() || WhatsNew.start("").isNews());
		assertFalse("a fresh install", WhatsNew.start(null).isUpdated());
		assertFalse("the same version again", WhatsNew.start(WhatsNew.VERSION).isUpdated()
			|| WhatsNew.start(WhatsNew.VERSION).isNews());
	}
}
