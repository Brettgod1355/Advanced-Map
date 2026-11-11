/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PlayerMarkerTest
{
	/** The box round what a marker draws in red on an empty picture, centred on (100, 100). */
	private static Rectangle drawn(PlayerMarker marker, double scale)
	{
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		marker.draw(g, 100, 100, scale, Color.RED, 2.5f);
		g.dispose();
		Rectangle box = null;
		for (int y = 0; y < 200; y++)
		{
			for (int x = 0; x < 200; x++)
			{
				if (image.getRGB(x, y) >>> 24 != 0)
				{
					box = box == null ? new Rectangle(x, y, 1, 1) : box.union(new Rectangle(x, y, 1, 1));
				}
			}
		}
		return box;
	}

	@Test
	public void everyMarkerIsOnItsSpotAndGrowsWithItsSize()
	{
		// a player's request (issue #3): the marker's size and color, and others to choose from
		for (PlayerMarker marker : PlayerMarker.values())
		{
			Rectangle small = drawn(marker, 1);
			Rectangle big = drawn(marker, 2);
			assertTrue(marker + " draws nothing", small != null);
			assertTrue(marker + " is not on its spot", small.contains(100, 100) || small.contains(100, 99));
			assertTrue(marker + " does not grow", big.width > small.width && big.height > small.height);
		}
		// a pin stands on the spot, the rest are centred on it
		assertTrue(drawn(PlayerMarker.PIN, 1).y + drawn(PlayerMarker.PIN, 1).height <= 103);
		Rectangle dot = drawn(PlayerMarker.DOT, 1);
		assertTrue(Math.abs(dot.getCenterY() - 100) <= 1.5);
	}
}
