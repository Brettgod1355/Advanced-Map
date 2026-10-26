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
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapPainterTest
{
	@Test
	public void overTheGameThereIsACloseButtonAndInAWindowThereIsNot()
	{
		assertTrue(MapPainter.buttons(1200, false).containsKey(MapPainter.Button.CLOSE));
		assertFalse(MapPainter.buttons(1200, true).containsKey(MapPainter.Button.CLOSE));
		assertEquals(3, MapPainter.buttons(1200, false).size());
		assertEquals(2, MapPainter.buttons(1200, true).size());
	}

	@Test
	public void buttonsSitInsideTheTopRightAndDoNotOverlap()
	{
		for (boolean window : new boolean[]{false, true})
		{
			Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1200, window);
			for (Rectangle a : buttons.values())
			{
				assertTrue(a.x >= 0 && a.x + a.width <= 1200 && a.y >= 0);
				for (Rectangle b : buttons.values())
				{
					assertTrue(a == b || !a.intersects(b));
				}
			}
		}
	}

	@Test
	public void aClickFindsItsButton()
	{
		for (Map.Entry<MapPainter.Button, Rectangle> button : MapPainter.buttons(800, false).entrySet())
		{
			Rectangle r = button.getValue();
			assertEquals(button.getKey(), MapPainter.buttonAt(r.x + r.width / 2, r.y + r.height / 2, 800, false));
		}
		assertNull(MapPainter.buttonAt(400, 400, 800, false));
	}

	@Test
	public void theWildernessLinesAreDrawnOnTheirRows()
	{
		// level 10 to 13 in the middle of a 400 x 300 view, 4 px a tile
		MapViewport.View view = new MapViewport.View(3100, 3600, 4, 400, 300);
		BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		MapPainter.drawWilderness(g, view, MapArea.SURFACE);
		g.dispose();

		int x = 200;
		int lineRow = (int) Math.round(view.screenY(Wilderness.levelStartY(Wilderness.SURFACE, 11)));
		assertTrue("a line where level 11 starts", isReddish(image.getRGB(x, lineRow)));
		assertEquals("nothing inside the level", Color.BLACK.getRGB(), image.getRGB(x, lineRow + 10));
	}

	@Test
	public void theWildernessIsNotDrawnFarFromIt()
	{
		MapViewport.View view = new MapViewport.View(3222, 3218, 4, 400, 300);
		BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		MapPainter.drawWilderness(g, view, MapArea.SURFACE);
		g.dispose();
		for (int y = 0; y < 300; y++)
		{
			for (int x = 0; x < 400; x++)
			{
				assertEquals(Color.BLACK.getRGB(), image.getRGB(x, y));
			}
		}
	}

	private static boolean isReddish(int rgb)
	{
		Color c = new Color(rgb);
		return c.getRed() > 60 && c.getRed() > 2 * c.getGreen();
	}
}
