/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Plugin Hub builds its own jar from the resources output, so a `jar {}` block in this
 * build never reaches the distributed artifact. These copies are what BSD 2-Clause requires
 * a binary redistribution to carry, so they must stay identical to the originals.
 */
public class DistributionNoticesTest
{
	@Test
	public void distributedNoticesMatchTheRepositoryRoot() throws Exception
	{
		for (String name : new String[]{"LICENSE", "THIRD_PARTY_NOTICES.md"})
		{
			File root = new File(name);
			assertNotNull(name + " is missing from the repository root", root.exists() ? root : null);
			String expected = new String(Files.readAllBytes(root.toPath()), StandardCharsets.UTF_8);

			try (InputStream packaged = getClass().getResourceAsStream("/META-INF/" + name))
			{
				assertNotNull("META-INF/" + name + " is not on the classpath, so the Plugin Hub jar "
					+ "would ship without it", packaged);
				try (Scanner scanner = new Scanner(packaged, StandardCharsets.UTF_8.name()))
				{
					scanner.useDelimiter("\\A");
					assertEquals("src/main/resources/META-INF/" + name + " has drifted from the root "
						+ name, expected, scanner.hasNext() ? scanner.next() : "");
				}
			}
		}
	}

	/**
	 * BSD 2-Clause asks a binary redistribution to reproduce each holder's copyright notice, and Hub reviewers stall a
	 * submission that leaves the authors of transcribed data out: every file a bundled table was transcribed from has
	 * its holder named here (the holders as the 1.13.4 headers write them).
	 */
	@Test
	public void everyHolderOfATranscribedTableIsNamed() throws Exception
	{
		// a holder's line may wrap
		String notices = new String(Files.readAllBytes(new File("THIRD_PARTY_NOTICES.md").toPath()), StandardCharsets.UTF_8)
			.replaceAll("\\s+", " ");
		for (String holder : new String[]{
			"Morgan Lewis <https://github.com/MESLewis>", "dekvall <https://github.com/dekvall>",
			"Spedwards <https://github.com/Spedwards>", "Kyle Sergio <https://github.com/ksergio39>",
			"Bryce Altomare <https://github.com/Twinkiel0ver>", "Kyle Stead <http://github.com/kyle1elyk>",
			"Arman S <https://github.com/Rman887>", "Magic fTail", "melky <https://github.com/melkypie>",
			"Seth <Sethtroll3@gmail.com>", "NotFoxtrot <https://github.com/NotFoxtrot>", "2018 Abex", "2019 Abex",
			"Torkel Velure <https://github.com/TorkelV>", "Tyler <https://github.com/tylerthardy>",
			"Shaun Dreclin <shaundreclin@gmail.com>"})
		{
			assertTrue(holder + " is not named in THIRD_PARTY_NOTICES.md", notices.contains(holder));
		}
	}
}
