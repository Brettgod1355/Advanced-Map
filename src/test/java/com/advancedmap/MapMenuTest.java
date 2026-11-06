/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapMenuTest
{
	private static final MapMenu.Page[] PAGES = {MapMenu.Page.HELP, MapMenu.Page.WHATS_NEW, MapMenu.Page.IMPROVE,
		MapMenu.Page.REDRAW};

	@Test
	public void theMenuAndItsPageFitTheMapDownToTheSmallestBox()
	{
		for (int[] size : new int[][]{{1280, 800}, {GameBox.MIN_WIDTH, GameBox.MIN_HEIGHT}})
		{
			Rectangle view = new Rectangle(0, 0, size[0], size[1]);
			Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(size[0], size[1], false);
			Rectangle button = buttons.get(MapPainter.Button.MENU);
			assertEquals("first on the top row", MapPainter.buttons(size[0], size[1], false).values().stream()
				.mapToInt(r -> r.x).min().getAsInt(), button.x);
			MapMenu.Layout closed = MapMenu.layout(buttons, size[0], size[1], null);
			assertTrue(view.contains(closed.getMenu()));
			assertTrue(closed.getMenu().y > button.y + button.height);
			assertNull(closed.getContent());
			assertTrue(closed.getMenu().contains(closed.getGithub()) && closed.getMenu().contains(closed.getDiscord()));
			for (MapMenu.Page page : PAGES)
			{
				MapMenu.Layout open = MapMenu.layout(buttons, size[0], size[1], page);
				Rectangle content = open.getContent();
				assertTrue(page + " at " + size[0], view.contains(content));
				assertTrue(content.width >= 160);
				assertTrue("beside the menu on a wide map", size[0] < 600 || content.x > open.getMenu().x
					+ open.getMenu().width);
				if (open.getButton1() != null)
				{
					assertTrue(content.contains(open.getButton1()));
				}
				if (open.getButton2() != null)
				{
					assertTrue(content.contains(open.getButton2()));
				}
			}
			assertNull("Settings is no page: it opens RuneLite's", MapMenu.layout(buttons, size[0], size[1],
				MapMenu.Page.SETTINGS).getContent());
		}
	}

	@Test
	public void aClickFindsTheEntryLinkOrButtonUnderIt()
	{
		MapMenu menu = new MapMenu(() -> "saved");
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1280, 800, false);
		MapMenu.Layout closed = MapMenu.layout(buttons, 1280, 800, null);
		Rectangle panel = closed.getMenu();
		for (MapMenu.Page page : MapMenu.Page.values())
		{
			MapMenu.Hit hit = menu.hitAt(panel.x + 20, panel.y + 8 + page.ordinal() * MapMenu.ROW + 5, closed);
			assertEquals(MapMenu.Part.ENTRY, hit.getPart());
			assertEquals(page, hit.getPage());
		}
		assertEquals(MapMenu.Part.MENU, menu.hitAt(panel.x + 20, panel.y + panel.height / 2, closed).getPart());
		assertEquals(MapMenu.Part.GITHUB, menu.hitAt(closed.getGithub().x + 3, closed.getGithub().y + 3, closed).getPart());
		assertEquals(MapMenu.Part.DISCORD, menu.hitAt(closed.getDiscord().x + 3, closed.getDiscord().y + 3, closed)
			.getPart());
		assertEquals(MapMenu.Part.OUTSIDE, menu.hitAt(1000, 500, closed).getPart());

		menu.setPage(MapMenu.Page.REDRAW);
		MapMenu.Layout open = MapMenu.layout(buttons, 1280, 800, MapMenu.Page.REDRAW);
		assertEquals(MapMenu.Part.CONTENT, menu.hitAt(1000, 300, open).getPart());
		assertEquals(MapMenu.Part.BUTTON1, menu.hitAt(open.getButton1().x + 3, open.getButton1().y + 3, open).getPart());
		assertEquals(MapMenu.Part.BUTTON2, menu.hitAt(open.getButton2().x + 3, open.getButton2().y + 3, open).getPart());
		// the page is drawn over the map, so a press there is the page's, not the map's
		assertEquals(MapMenu.Page.REDRAW, menu.hitAt(1000, 300, open).getPage());
	}

	@Test
	public void theHelpPageScrollsAndClosingTheMenuClosesItsPage()
	{
		MapMenu menu = new MapMenu(() -> "saved");
		menu.setOpen(true);
		menu.setPage(MapMenu.Page.HELP);
		// a short map: the help does not fit, and the wheel scrolls it, no further than its end
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(800, 400, false);
		MapMenu.Layout layout = MapMenu.layout(buttons, 800, 400, MapMenu.Page.HELP);
		BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		menu.draw(g, layout, null);
		menu.scroll(-1, layout);
		menu.draw(g, layout, null);
		menu.scroll(1, layout);
		menu.scroll(1000, layout);
		menu.draw(g, layout, null);
		g.dispose();
		assertTrue(menu.isOpen());
		assertEquals(MapMenu.Page.HELP, menu.page());
		menu.setOpen(false);
		assertNull("closing the menu closes its page", menu.page());
	}
}
