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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
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
		assertTrue(MapPainter.buttons(1200, 800, false).containsKey(MapPainter.Button.CLOSE));
		assertFalse(MapPainter.buttons(1200, 800, true).containsKey(MapPainter.Button.CLOSE));
		assertEquals(8, MapPainter.buttons(1200, 800, false).size());
		assertEquals(7, MapPainter.buttons(1200, 800, true).size());
	}

	@Test
	public void buttonsSitInsideTheViewAndDoNotOverlap()
	{
		for (boolean window : new boolean[]{false, true})
		{
			Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(800, 500, window);
			for (Rectangle a : buttons.values())
			{
				assertTrue(a.x >= 0 && a.x + a.width <= 800 && a.y >= 0 && a.y + a.height <= 500);
				for (Rectangle b : buttons.values())
				{
					assertTrue(a == b || !a.intersects(b));
				}
			}
		}
	}

	@Test
	public void theFindMeButtonIsInTheBottomRightCorner()
	{
		Rectangle locate = MapPainter.buttons(1000, 700, false).get(MapPainter.Button.LOCATE);
		assertTrue(locate.x > 900 && locate.y > 600);
		assertEquals(MapPainter.Button.LOCATE, MapPainter.buttonAt(985, 685 - 10, 1000, 700, false));
	}

	@Test
	public void aClickFindsItsButton()
	{
		for (Map.Entry<MapPainter.Button, Rectangle> button : MapPainter.buttons(800, 600, false).entrySet())
		{
			Rectangle r = button.getValue();
			assertEquals(button.getKey(), MapPainter.buttonAt(r.x + r.width / 2, r.y + r.height / 2, 800, 600, false));
		}
		assertNull(MapPainter.buttonAt(400, 300, 800, 600, false));
	}

	@Test
	public void theFloorButtonsSitAboveTheFindMeButton()
	{
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1000, 700, false);
		Rectangle locate = buttons.get(MapPainter.Button.LOCATE);
		Rectangle up = buttons.get(MapPainter.Button.FLOOR_UP);
		Rectangle down = buttons.get(MapPainter.Button.FLOOR_DOWN);
		assertTrue(up.y < down.y && down.y + down.height <= locate.y);
		assertEquals(locate.x + locate.width, down.x + down.width);
	}

	@Test
	public void theMenuStaysOnScreenWithOneRowPerEntry()
	{
		MapSpot varrock = MapSpot.teleport(Teleport.VARROCK);
		MapPainter.Selection near = new MapPainter.Selection(varrock, true, 790, 590, Teleport.VARROCK.getLocation());
		List<MapPainter.MenuEntry> entries = MapPainter.menuEntries(near, settings(RoutingPlugin.SHORTEST_PATH));
		assertEquals(3, entries.size());
		assertEquals(MapPainter.Action.HIDE, entries.get(0).getAction());
		assertEquals("Route here (Shortest Path)", entries.get(1).getText());
		Rectangle menu = MapPainter.menuBounds(near, "Varrock Teleport", entries, 800, 600);
		assertTrue(menu.x + menu.width <= 800 && menu.y + menu.height <= 600);
		for (int i = 0; i < entries.size(); i++)
		{
			Rectangle row = MapPainter.menuEntryBounds(menu, i);
			assertTrue(menu.contains(row));
			assertTrue(row.y > menu.y);
		}
	}

	@Test
	public void aTileWithNoIconOnlyRoutes()
	{
		MapPainter.Selection tile = new MapPainter.Selection(null, true, 10, 10, new WorldPoint(3222, 3218, 1));
		List<MapPainter.MenuEntry> entries = MapPainter.menuEntries(tile, settings(RoutingPlugin.GPS));
		assertEquals(2, entries.size());
		assertEquals(MapPainter.Action.ROUTE, entries.get(0).getAction());
		assertEquals("Route here (GPS)", entries.get(0).getText());
		assertEquals("3222, 3218, Floor 1", MapPainter.menuTitle(tile, Progress.NONE));
		assertTrue(MapPainter.menuEntries(tile, settings(RoutingPlugin.NONE)).isEmpty());
	}

	private static MapSettings settings(RoutingPlugin routing)
	{
		return new MapSettings(EnumSet.allOf(IconGroup.class), Collections.emptySet(), false, true, false, false, true,
			true, routing);
	}

	@Test
	public void theTeleportLimitIsARedDashedLineOnlyWhenZoomedIn()
	{
		// the level 20 limit across the middle of a 400 x 300 view, 4 px a tile
		MapViewport.View view = new MapViewport.View(3100, 3680, 4, 400, 300);
		BufferedImage image = draw(view);
		int lineRow = (int) Math.round(view.screenY(Wilderness.teleportLimitY(Wilderness.SURFACE, 20)));
		int red = 0;
		int gaps = 0;
		for (int x = 0; x < 300; x++)
		{
			if (isReddish(image.getRGB(x, lineRow)))
			{
				red++;
			}
			else
			{
				gaps++;
			}
		}
		assertTrue("a red line", red > 100);
		assertTrue("with gaps: dashed", gaps > 50);
		// no line where an ordinary level starts
		int levelRow = (int) Math.round(view.screenY(Wilderness.levelStartY(Wilderness.SURFACE, 19)));
		assertEquals(Color.BLACK.getRGB(), image.getRGB(200, levelRow));

		// zoomed out, nothing
		MapViewport.View far = new MapViewport.View(3100, 3680, MapPainter.WILDERNESS_ZOOM / 2, 400, 300);
		BufferedImage farImage = draw(far);
		for (int y = 0; y < 300; y++)
		{
			for (int x = 0; x < 400; x++)
			{
				assertEquals(Color.BLACK.getRGB(), farImage.getRGB(x, y));
			}
		}
	}

	private static BufferedImage draw(MapViewport.View view)
	{
		BufferedImage image = new BufferedImage(view.width, view.height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		MapPainter.drawWilderness(g, view, MapArea.SURFACE);
		g.dispose();
		return image;
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
