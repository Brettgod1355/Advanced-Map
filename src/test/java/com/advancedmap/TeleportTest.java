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
		assertEquals(243, Teleport.values().size());
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
		int pictured = 0;
		for (Teleport teleport : Teleport.values())
		{
			// the rest are items the game draws, and the minigame teleports
			if (teleport.iconItem() >= 0 || Teleport.MINIGAME_ICON.equals(teleport.getIcon()))
			{
				assertNull(teleport.picture());
				continue;
			}
			pictured++;
			java.awt.image.BufferedImage picture = teleport.picture();
			assertTrue("no picture for " + teleport.getName(), picture != null);
			// the map's size for a teleport: RuneLite's are 14 or 15 pixels
			assertTrue(teleport.getName(), picture.getWidth() >= 13 && picture.getWidth() <= 16);
		}
		assertEquals(218, pictured);
		// each spell its own spellbook icon, each item its own
		assertEquals("lumbridge_teleport_icon.png", Teleport.named("LUMBRIDGE").getIcon());
		assertEquals("games_necklace_teleport_icon.png", Teleport.named("WINTERTODT_CAMP").getIcon());
		// one file read once, shared by the teleports that use it
		assertTrue(Teleport.named("VARROCK").picture() == Teleport.named("VARROCK_GE").picture());
	}

	@Test
	public void theTeleportsRuneLitesListLacksHaveTheGamesItemOrMinigameIcon()
	{
		Teleport sphere = Teleport.named("DORGESH_KAAN_SPHERE");
		assertEquals(10972, sphere.iconItem());
		assertEquals("Dorgesh-kaan sphere: Dorgesh-Kaan", sphere.title());
		Teleport nightmareZone = Teleport.named("MINIGAME_NIGHTMARE_ZONE");
		assertEquals(-1, nightmareZone.iconItem());
		assertEquals(Teleport.MINIGAME_ICON, nightmareZone.getIcon());
		assertEquals("Minigame teleport: Nightmare Zone", nightmareZone.title());
		assertEquals(-1, Teleport.named("VARROCK").iconItem());
	}

	@Test
	public void anItemsPictureGoesOnADiscAsRuneLitesDo()
	{
		// an item picture as the client gives it: 36 by 32, the item in its middle, the rest clear
		java.awt.image.BufferedImage item = new java.awt.image.BufferedImage(36, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		for (int y = 6; y < 26; y++)
		{
			for (int x = 8; x < 28; x++)
			{
				item.setRGB(x, y, 0xFFD02020);
			}
		}
		java.awt.image.BufferedImage disc = MapIcons.onDisc(item);
		assertEquals(15, disc.getWidth());
		// the rim and the gray, where RuneLite's Games necklace icon has them
		assertEquals(0xFF000001, disc.getRGB(7, 0));
		assertEquals(0xFF000001, disc.getRGB(0, 7));
		assertEquals(0xFFBFBFBF, disc.getRGB(7, 1));
		assertEquals(0, disc.getRGB(0, 0) >>> 24);
		// the item shrunk into the middle
		assertEquals(0xFFD02020, disc.getRGB(7, 7));
		assertEquals(0xFFBFBFBF, disc.getRGB(2, 7));
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
