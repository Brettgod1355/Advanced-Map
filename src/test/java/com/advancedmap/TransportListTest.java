/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TransportListTest
{
	private static List<Transports.Destination> places(int count)
	{
		List<Transports.Destination> out = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			out.add(new Transports.Destination(new WorldPoint(3000 + i, 3000, 0), "Fairy ring", "P" + i));
		}
		return out;
	}

	@Test
	public void theListSitsOnTheRightBetweenTheButtons()
	{
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1200, 800, false);
		TransportList.Layout layout = TransportList.layout(buttons, 55);
		Rectangle panel = layout.getPanel();
		assertTrue(panel.x + panel.width <= 1200);
		assertTrue(panel.y > buttons.get(MapPainter.Button.AREA).y + buttons.get(MapPainter.Button.AREA).height);
		assertTrue(panel.y + panel.height < buttons.get(MapPainter.Button.FLOOR_UP).y);
		for (Rectangle button : buttons.values())
		{
			assertFalse(button.intersects(panel));
		}
		// a short list is only as tall as its rows
		assertTrue(TransportList.layout(buttons, 3).getPanel().height < panel.height);
	}

	@Test
	public void aPressFindsThePlaceUnderIt()
	{
		TransportList list = new TransportList();
		list.open("Fairy ring DKR", new WorldPoint(3129, 3496, 0), places(5));
		TransportList.Layout layout = TransportList.layout(MapPainter.buttons(1200, 800, true), 5);
		Rectangle rows = layout.getList();
		assertEquals(0, list.hitAt(rows.x + 10, rows.y + 2, layout).getIndex());
		assertEquals(2, list.hitAt(rows.x + 10, rows.y + TransportList.ROW * 2 + 5, layout).getIndex());
		Rectangle close = layout.getClose();
		assertEquals(TransportList.Hit.CLOSE, list.hitAt(close.x + 2, close.y + 2, layout).getIndex());
		assertEquals(TransportList.Hit.OUTSIDE, list.hitAt(5, 400, layout).getIndex());
	}

	@Test
	public void scrollingStopsAtTheEnds()
	{
		TransportList list = new TransportList();
		list.open("Fairy ring DKR", new WorldPoint(3129, 3496, 0), places(55));
		TransportList.Layout layout = TransportList.layout(MapPainter.buttons(1200, 500, true), 55);
		Rectangle rows = layout.getList();
		list.scroll(-5, layout);
		assertEquals(0, list.hitAt(rows.x + 10, rows.y + 2, layout).getIndex());
		list.scroll(1000, layout);
		// the last place shows at the bottom
		assertEquals(54, list.hitAt(rows.x + 10, rows.y + rows.height - 2, layout).getIndex());
	}

	@Test
	public void closingEmptiesIt()
	{
		TransportList list = new TransportList();
		list.open("Spirit tree", new WorldPoint(3184, 3510, 0), places(3));
		assertTrue(list.isOpen());
		list.close();
		assertFalse(list.isOpen());
	}
}
