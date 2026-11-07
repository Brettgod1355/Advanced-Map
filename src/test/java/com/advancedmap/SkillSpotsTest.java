/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkillSpotsTest
{
	@Test
	public void aFishingSpotOrHunterIconSaysWhatIsCaughtThere()
	{
		new MapSearch().load();
		// a few tiles from the listed spot, as the game's icon may be
		MapSearch.SkillSpot fish = MapSearch.skillNear(true, 2467, 2709, 0);
		assertEquals(Arrays.asList("Lobster", "Swordfish", "Tuna"), fish.getWhat());
		MapSearch.SkillSpot hunter = MapSearch.skillNear(false, 1359, 2979, 0);
		assertEquals(Collections.singletonList("Copper longtail"), hunter.getWhat());
		assertEquals(9, hunter.getLevel());
		// the Pandemonium's crab trapping, which RuneLite's list leaves out: from the wiki's list
		MapSearch.SkillSpot crabs = MapSearch.skillNear(false, 3036, 2974, 0);
		assertEquals(Collections.singletonList("Red crab"), crabs.getWhat());
		assertEquals(21, crabs.getLevel());
		// a hunter area is not a fishing spot, and nothing is listed in the middle of Lumbridge
		assertNull(MapSearch.skillNear(true, 1357, 2977, 0));
		assertNull(MapSearch.skillNear(true, 3222, 3218, 0));
	}

	@Test
	public void aMinigameIconIsNamedForItsMinigame()
	{
		new MapSearch().load();
		// the game's map calls them all "Minigame"; RuneLite's list names the one beside it
		assertEquals("Castle Wars", MapSearch.minigameNear(2441, 3090));
		assertEquals("Barbarian Assault", MapSearch.minigameNear(2533, 3571));
		assertNull(MapSearch.minigameNear(3222, 3218));
		// Tempoross, which the game's map has no icon for, gets one of Advanced Map's at its boat
		assertEquals("Tempoross", MapSearch.minigameNear(3137, 2840));
		// the game's icon on the ground floor; RuneLite's list had the way in a floor up
		assertEquals("Hallowed Sepulchre", MapSearch.minigameNear(3654, 3386));
		assertEquals(1, MapSearch.minigameIcons().stream().filter(entry -> entry.getName().equals("Tempoross")).count());
		// and Wintertodt, at its doors
		assertEquals("Wintertodt", MapSearch.minigameNear(1630, 3963));
		assertEquals(1, MapSearch.minigameIcons().stream().filter(entry -> entry.getName().equals("Wintertodt")).count());
	}

	@Test
	public void aSkillingIconBesideADungeonsWayInKeepsItsOwnDetails()
	{
		// the game's mining site icon (a skilling spot) two tiles from a trapdoor showed the dungeon's preview
		assertFalse(IconGroup.mayBeEntrance(1058));
		assertFalse(IconGroup.mayBeEntrance(1055));
		// a dungeon's icon, a transport's
		assertTrue(IconGroup.mayBeEntrance(1062));
		assertTrue(IconGroup.mayBeEntrance(1104));
	}
}
