/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LinkIconTest
{
	@Test
	public void eachBrandLogoLoadsAndFitsItsSquareWithoutStretching()
	{
		for (String file : new String[]{"discord_white.png", "discord_blurple.png", "github_white.png", "github_green.png"})
		{
			BufferedImage logo = AdvancedMapPanel.logo(file);
			assertTrue(file, logo.getWidth() <= AdvancedMapPanel.LINK_ICON_SIZE
				&& logo.getHeight() <= AdvancedMapPanel.LINK_ICON_SIZE);
			assertEquals(file + " fills the square one way", AdvancedMapPanel.LINK_ICON_SIZE,
				Math.max(logo.getWidth(), logo.getHeight()), 1);
		}
		// Discord's symbol is wider than tall (528 x 400); it keeps that shape
		assertEquals(12, AdvancedMapPanel.logo("discord_white.png").getHeight());
		// each colored version is drawn at its white version's size, so hovering does not shift anything
		BufferedImage white = AdvancedMapPanel.logo("github_white.png");
		BufferedImage green = AdvancedMapPanel.logoLike("github_green.png", white);
		assertEquals(white.getWidth(), green.getWidth());
		assertEquals(white.getHeight(), green.getHeight());
	}

	@Test
	public void theButtonsGoToTheDiscordServerAndThisRepository()
	{
		assertEquals("https://discord.gg/c85DK83jWx", AdvancedMapPanel.DISCORD_URL);
		assertEquals("https://github.com/Brettgod1355/Advanced-Map", AdvancedMapPanel.GITHUB_URL);
	}
}
