/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlayerTaskTest
{
	@BeforeClass
	public static void load()
	{
		Spawns.load();
		SlayerTask.load();
	}

	private static List<String> names(String task)
	{
		return SlayerTask.creatures(task).stream().map(Spawns.Creature::getName).collect(Collectors.toList());
	}

	@Test
	public void aTaskCoversItsMonstersAsWholeWords()
	{
		List<String> goblins = names("Goblins");
		assertTrue(goblins.contains("Goblin"));
		// "hobgoblin" is one word: not a goblin
		assertFalse(goblins.contains("Hobgoblin"));
		assertTrue(names("Kalphite").stream().anyMatch(name -> name.startsWith("Kalphite")));
		// a task's other targets: birds are chickens too
		assertTrue(names("Birds").contains("Chicken"));
		assertFalse(SlayerTask.spawns("Goblins").isEmpty());
		assertTrue(names("No such task").isEmpty());
	}
}
