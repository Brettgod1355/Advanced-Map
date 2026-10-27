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

public class SideListTest
{
	private static List<SideList.Row> places(int count)
	{
		List<Transports.Destination> out = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			out.add(new Transports.Destination(new WorldPoint(3000 + i, 3000, 0), "Fairy ring", "P" + i));
		}
		return AdvancedMapPlugin.transportRows(new WorldPoint(3129, 3496, 0), out);
	}

	@Test
	public void theListSitsOnTheRightBetweenTheButtons()
	{
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1200, 800, false);
		SideList.Layout layout = SideList.layout(buttons, 55 * SideList.ROW);
		Rectangle panel = layout.getPanel();
		assertTrue(panel.x + panel.width <= 1200);
		assertTrue(panel.y > buttons.get(MapPainter.Button.AREA).y + buttons.get(MapPainter.Button.AREA).height);
		assertTrue(panel.y + panel.height < buttons.get(MapPainter.Button.FLOOR_UP).y);
		for (Rectangle button : buttons.values())
		{
			assertFalse(button.intersects(panel));
		}
		// a short list is only as tall as its rows
		assertTrue(SideList.layout(buttons, 3 * SideList.ROW).getPanel().height < panel.height);
	}

	@Test
	public void aPressFindsThePlaceUnderIt()
	{
		SideList list = new SideList();
		list.open("Fairy ring DKR", places(5), null);
		SideList.Layout layout = list.layout(MapPainter.buttons(1200, 800, true));
		Rectangle rows = layout.getList();
		assertEquals(0, list.hitAt(rows.x + 10, rows.y + 2, layout).getIndex());
		assertEquals(2, list.hitAt(rows.x + 10, rows.y + SideList.ROW * 2 + 5, layout).getIndex());
		Rectangle close = layout.getClose();
		assertEquals(SideList.Hit.CLOSE, list.hitAt(close.x + 2, close.y + 2, layout).getIndex());
		assertEquals(SideList.Hit.OUTSIDE, list.hitAt(5, 400, layout).getIndex());
	}

	@Test
	public void scrollingStopsAtTheEnds()
	{
		SideList list = new SideList();
		list.open("Fairy ring DKR", places(55), null);
		SideList.Layout layout = list.layout(MapPainter.buttons(1200, 500, true));
		Rectangle rows = layout.getList();
		list.scroll(-5, layout);
		assertEquals(0, list.hitAt(rows.x + 10, rows.y + 2, layout).getIndex());
		list.scroll(1000, layout);
		// the last place shows at the bottom
		assertEquals(54, list.hitAt(rows.x + 10, rows.y + rows.height - 2, layout).getIndex());
	}

	@Test
	public void aRowIsTallerByALinePerNeed()
	{
		SideList list = new SideList();
		List<SideList.Row> rows = new ArrayList<>();
		rows.add(new SideList.Row("Shilo Village", "200 gp", "Shilo Village; Free with the Karamja elite diary", null,
			new WorldPoint(2833, 2958, 0), null));
		rows.add(new SideList.Row("Brimhaven", "200 gp", null, null, new WorldPoint(2780, 3214, 0), null));
		list.open("Cart", rows, null);
		assertEquals(SideList.ROW * 2 + SideList.SUB * 2, list.contentHeight());
		SideList.Layout layout = list.layout(MapPainter.buttons(1200, 800, true));
		Rectangle top = layout.getList();
		assertEquals(0, list.hitAt(top.x + 10, top.y + SideList.ROW + SideList.SUB * 2 - 2, layout).getIndex());
		assertEquals(1, list.hitAt(top.x + 10, top.y + SideList.ROW + SideList.SUB * 2 + 2, layout).getIndex());
	}

	@Test
	public void closingEmptiesIt()
	{
		SideList list = new SideList();
		list.open("Spirit tree", places(3), null);
		assertTrue(list.isOpen());
		list.close();
		assertFalse(list.isOpen());
	}
}
