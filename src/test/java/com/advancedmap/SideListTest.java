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
import static org.junit.Assert.assertNull;
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
	@Test
	public void aHeadingIsTallerAndOpensNothing()
	{
		SideList.Row heading = SideList.Row.heading("Locations (nearest first)");
		assertTrue(heading.isHeading());
		assertTrue(heading.height() > new SideList.Row("Fountain of Heroes", "1 spawn", null, null, null, null).height());
		assertNull(heading.getPoint());
		assertFalse(new SideList.Row("Fountain of Heroes", "1 spawn", null, null, null, null).isHeading());
	}

	@Test
	public void longTitlesAndRowsWrapWholeWordsInsteadOfBeingCutShort()
	{
		SideList list = new SideList();
		String title = "King Black Dragon Lair (dungeon entrance, Wilderness)";
		List<SideList.Row> rows = new ArrayList<>();
		rows.add(new SideList.Row("A row with a very long name that cannot fit beside its note", "14 spawns", null, null,
			null, null));
		rows.add(new SideList.Row("Short", null, null, null, null, null));
		list.open(title, rows, null);
		assertTrue("the title takes more than one line", list.titleLines().size() > 1);
		assertEquals(title, String.join(" ", list.titleLines()));
		assertTrue(list.headerHeight() > SideList.layout(MapPainter.buttons(1200, 800, true), 0).getList().y
			- SideList.layout(MapPainter.buttons(1200, 800, true), 0).getPanel().y);
		List<String> lines = rows.get(0).textLines();
		assertTrue(lines.size() > 1);
		assertEquals(rows.get(0).getText(), String.join(" ", lines));
		assertEquals("a line per extra line of text", SideList.ROW + SideList.SUB * (lines.size() - 1),
			rows.get(0).height());
		assertEquals(SideList.ROW, rows.get(1).height());
		SideList.Layout layout = list.layout(MapPainter.buttons(1200, 800, true));
		assertEquals("the rows start under the whole title", layout.getPanel().y + list.headerHeight(),
			layout.getList().y);
		assertEquals(new SideList.Hit(0), list.hitAt(layout.getList().x + 10, layout.getList().y + 5, layout));
		assertEquals(new SideList.Hit(1),
			list.hitAt(layout.getList().x + 10, layout.getList().y + rows.get(0).height() + 5, layout));
	}

	@Test
	public void theRowsAreWrappedOnceWhenTheListOpensAndDrawnFromThat()
	{
		// a long list repaints 20 times a second: measuring every word of every row each time cost milliseconds
		SideList list = new SideList();
		List<SideList.Row> rows = new ArrayList<>();
		rows.add(SideList.Row.heading("Locations (nearest first)"));
		for (int i = 0; i < 150; i++)
		{
			rows.add(new SideList.Row("Lesser demon spawn area " + i, i + " tiles",
				"needs 40 Agility; needs Dragon Slayer II to enter", null, new WorldPoint(3200 + i, 3200, 0), null));
		}
		list.open("Nearest: Lesser demon", rows, "a search");
		int expected = 0;
		for (SideList.Row row : rows)
		{
			expected += row.height();
		}
		assertEquals("the height kept is the rows' own", expected, list.contentHeight());
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1280, 800, false);
		SideList.Layout layout = list.layout(buttons);
		// hits still land on the right row from the kept heights, and a rendering of the list reads nothing from
		// the rows themselves (the wrap below would trip on a row that is no longer in the list)
		int y = layout.getList().y + rows.get(0).height() + rows.get(1).height() + 2;
		assertEquals(new SideList.Hit(2), list.hitAt(layout.getList().x + 10, y, layout));
		rows.clear();
		assertEquals(151, list.rows().size());
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1280, 800,
			java.awt.image.BufferedImage.TYPE_INT_ARGB);
		list.draw(image.createGraphics(), layout, null);
		assertEquals(expected, list.contentHeight());
		list.close();
		assertFalse(list.isOpen());
		assertEquals(0, list.contentHeight());
		assertEquals(1, list.titleLines().size());
	}

	@Test
	public void aWrappedHeadingStepsItsBoldLinesByTheBoldFont()
	{
		SideList.Row heading = SideList.Row.heading("Locations of this monster, nearest to you first, on every floor");
		List<String> lines = heading.textLines();
		assertTrue("the heading wraps", lines.size() > 1);
		assertTrue("the bold font is taller than a sub line", SideList.BOLD_LINE > SideList.SUB);
		// stepped by the small font's line, two bold lines ran into each other
		assertEquals(heading.height(), heading.height(lines, java.util.Collections.emptyList()));
		assertEquals(SideList.Row.heading("Short").height() + SideList.BOLD_LINE * (lines.size() - 1), heading.height());
	}
}
