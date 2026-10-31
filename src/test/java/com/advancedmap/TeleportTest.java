/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TeleportTest
{
	@Test
	public void theBundledListLoadsWholeWithUniqueKeys()
	{
		assertEquals(218, Teleport.values().size());
		Set<String> keys = new HashSet<>();
		for (Teleport teleport : Teleport.values())
		{
			assertTrue("key " + teleport.getName(), teleport.getName().matches("[A-Z][A-Z0-9_]*"));
			assertTrue("key " + teleport.getName() + " repeats", keys.add(teleport.getName()));
			assertTrue(teleport.getDestination() != null || teleport.getItem() != null);
			assertEquals(teleport.getType().isSpell(), teleport.getItem() == null);
			assertEquals(teleport.getType().isSpell(), teleport.getMagicLevel() > 0);
		}
	}

	@Test
	public void everyTeleportHasRuneLitesPictureForIt()
	{
		for (Teleport teleport : Teleport.values())
		{
			java.awt.image.BufferedImage picture = teleport.picture();
			assertTrue("no picture for " + teleport.getName(), picture != null);
			// the map's size for a teleport: RuneLite's are 14 or 15 pixels
			assertTrue(teleport.getName(), picture.getWidth() >= 13 && picture.getWidth() <= 16);
		}
		// each spell its own spellbook icon, each item its own
		assertEquals("lumbridge_teleport_icon.png", Teleport.named("LUMBRIDGE").getIcon());
		assertEquals("games_necklace_teleport_icon.png", Teleport.named("WINTERTODT_CAMP").getIcon());
		// one file read once, shared by the teleports that use it
		assertTrue(Teleport.named("VARROCK").picture() == Teleport.named("VARROCK_GE").picture());
	}

	@Test
	public void rowsReadAsTheEnumDid()
	{
		Teleport varrock = Teleport.named("VARROCK");
		assertEquals(Teleport.Type.NORMAL_MAGIC, varrock.getType());
		assertEquals(25, varrock.getMagicLevel());
		assertEquals(new WorldPoint(3213, 3424, 0), varrock.getLocation());
		assertEquals("tVARROCK", MapSpot.teleport(varrock).getKey());
		Teleport wyrmscraig = Teleport.named("WYRMSCRAIG_CAVERN");
		assertEquals(new WorldPoint(2773, 8633, 0), wyrmscraig.getLocation());
		assertEquals("Slayer Ring: Wyrmscraig Cavern", wyrmscraig.title());
		assertEquals("Wilderness crabs teleport", Teleport.named("WILDERNESS_CRABS_TELEPORT").title());
		assertEquals(1, Teleport.named("ACHIEVEMENT_CAPE_KALEB_PARAMAYA").getLocation().getPlane());
		assertNull(Teleport.named("NOWHERE"));
	}
}
