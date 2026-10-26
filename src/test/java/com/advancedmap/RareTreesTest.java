/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RareTreesTest
{
	@BeforeClass
	public static void load()
	{
		RareTrees.load();
	}

	@Test
	public void theListIsComplete() throws IOException
	{
		// 183 tiles of 21 kinds in RuneLite's list at the pinned release
		try (InputStream in = RareTrees.class.getResourceAsStream("rare-trees.tsv"))
		{
			List<RareTrees.Tree> trees = RareTrees.parse(in);
			assertEquals(183, trees.size());
			assertEquals(21, trees.stream().map(RareTrees.Tree::getName).distinct().count());
		}
	}

	@Test
	public void aRareTreesIconSaysWhichTrees()
	{
		// the willow by Seers' Village bank, RuneLite's only listed willow
		List<RareTrees.Tree> trees = RareTrees.near(2711, 3509, 0);
		assertEquals(1, trees.size());
		assertEquals("Willow tree", trees.get(0).getName());
		assertEquals(30, trees.get(0).getLevel());
	}

	@Test
	public void nothingFarAwayOrOnAnotherFloor()
	{
		assertTrue(RareTrees.near(2710 + RareTrees.REACH + 1, 3510, 0).isEmpty());
		assertTrue(RareTrees.near(2710, 3510, 1).isEmpty());
	}
}
