/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LastManStandingMapsTest
{
	/** A world map's details as the game keeps them: a short name and the name, each ended by a zero byte. */
	private static byte[] details(String safeName, String name)
	{
		return (safeName + "\0" + name + "\0").getBytes(StandardCharsets.ISO_8859_1);
	}

	@Test
	public void lastManStandingsMapsAreKnownByTheirName()
	{
		// their place names ("Wild Varrock") are not the world's, and put Varrock second when searching "varr"
		assertTrue(ClientMapSource.isLastManStanding(details("br_dark_varrock", "LMS Wild Varrock")));
		assertTrue(ClientMapSource.isLastManStanding(details("br_default", "LMS Desert Island")));
		assertFalse(ClientMapSource.isLastManStanding(details("zanaris", "Zanaris")));
		assertFalse(ClientMapSource.isLastManStanding(null));
		assertFalse(ClientMapSource.isLastManStanding(new byte[0]));
	}
}
