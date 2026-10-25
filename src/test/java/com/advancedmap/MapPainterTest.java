/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Rectangle;
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
}
