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
	private static final MapMenu.Page[] PAGES = {MapMenu.Page.WELCOME, MapMenu.Page.HELP, MapMenu.Page.WHATS_NEW,
		MapMenu.Page.IMPROVE, MapMenu.Page.REDRAW};

	private static MapMenu.Build built()
	{
		return new MapMenu.Build(11716, 0, true);
	}

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
		MapMenu menu = new MapMenu(() -> "saved", MapMenuTest::built);
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1280, 800, false);
		MapMenu.Layout closed = MapMenu.layout(buttons, 1280, 800, null);
		Rectangle panel = closed.getMenu();
		for (MapMenu.Page page : MapMenu.Page.values())
		{
			MapMenu.Hit hit = menu.hitAt(panel.x + 20, panel.y + 8 + page.ordinal() * MapMenu.ROW + 5, closed);
			assertEquals(MapMenu.Part.ENTRY, hit.getPart());
			assertEquals(page, hit.getPage());
		}
		// between the last entry and the links
		assertEquals(MapMenu.Part.MENU, menu.hitAt(panel.x + 20, panel.y + 8 + MapMenu.Page.values().length
			* MapMenu.ROW + 3, closed).getPart());
		assertEquals(MapMenu.Part.GITHUB, menu.hitAt(closed.getGithub().x + 3, closed.getGithub().y + 3, closed).getPart());
		assertEquals(MapMenu.Part.DISCORD, menu.hitAt(closed.getDiscord().x + 3, closed.getDiscord().y + 3, closed)
			.getPart());
		assertEquals(MapMenu.Part.OUTSIDE, menu.hitAt(1000, 500, closed).getPart());

		menu.setPage(MapMenu.Page.REDRAW);
		MapMenu.Layout open = MapMenu.layout(buttons, 1280, 800, MapMenu.Page.REDRAW);
		Rectangle page = open.getContent();
		assertEquals(MapMenu.Part.CONTENT, menu.hitAt(page.x + 50, page.y + 50, open).getPart());
		assertEquals(MapMenu.Part.BUTTON1, menu.hitAt(open.getButton1().x + 3, open.getButton1().y + 3, open).getPart());
		assertEquals(MapMenu.Part.BUTTON2, menu.hitAt(open.getButton2().x + 3, open.getButton2().y + 3, open).getPart());
		// the page is drawn over the map, so a press there is the page's, not the map's
		assertEquals(MapMenu.Page.REDRAW, menu.hitAt(page.x + 50, page.y + 50, open).getPage());
	}

	@Test
	public void theHelpPageScrollsAndClosingTheMenuClosesItsPage()
	{
		MapMenu menu = new MapMenu(() -> "saved", MapMenuTest::built);
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

	@Test
	public void aPageIsAsWideAndAsTallAsItsTextNeeds()
	{
		// a wide map: no page runs to the map's right edge; the short pages end under their buttons
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1920, 1080, false);
		MapMenu menu = new MapMenu(() -> "11,716 regions and floors saved on this computer.", MapMenuTest::built);
		menu.setOpen(true);
		for (MapMenu.Page page : PAGES)
		{
			menu.setPage(page);
			MapMenu.Layout fitted = menu.layout(buttons, 1920, 1080);
			Rectangle content = fitted.getContent();
			assertTrue(page + " no wider than its text column", content.width <= 640 + 8 * 2 + 6);
			assertTrue(page + " leaves the map's right side free", content.x + content.width < 1920 - 500);
			if (page == MapMenu.Page.HELP)
			{
				assertTrue("the help is long, but ends on the map", content.height > 600
					&& content.y + content.height <= 1080);
			}
			else if (page == MapMenu.Page.WELCOME)
			{
				assertTrue("the welcome ends on the map, under its buttons", content.y + content.height <= 1080
					&& fitted.getButton2().y == content.y + content.height - 8 - 24);
			}
			else
			{
				assertTrue(page + " is short", content.height < 260);
				assertTrue(content.contains(fitted.getButton1()));
				// the buttons right under the text, at the page's foot
				assertEquals(content.y + content.height - 8 - 24, fitted.getButton1().y);
			}
		}
		// the same layout is what the plugin hit-tests and the painter draws: one method for both
		menu.setPage(MapMenu.Page.REDRAW);
		MapMenu.Layout fitted = menu.layout(buttons, 1920, 1080);
		Rectangle cancel = fitted.getButton2();
		assertEquals(MapMenu.Part.BUTTON2, menu.hitAt(cancel.x + 3, cancel.y + 3, fitted).getPart());
		// a narrow map: the page still fits beside or over the menu
		Map<MapPainter.Button, Rectangle> small = MapPainter.buttons(GameBox.MIN_WIDTH, GameBox.MIN_HEIGHT, false);
		MapMenu.Layout tight = menu.layout(small, GameBox.MIN_WIDTH, GameBox.MIN_HEIGHT);
		assertTrue(new Rectangle(0, 0, GameBox.MIN_WIDTH, GameBox.MIN_HEIGHT).contains(tight.getContent()));
	}

	@Test
	public void onAShortMapTheHelpIsAsTallAsTheMenuAndScrolls()
	{
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(800, 400, false);
		MapMenu menu = new MapMenu(() -> "saved", MapMenuTest::built);
		menu.setOpen(true);
		menu.setPage(MapMenu.Page.HELP);
		MapMenu.Layout fitted = menu.layout(buttons, 800, 400);
		// down to the map's bottom edge, a padding above it
		assertEquals(400 - 8, fitted.getContent().y + fitted.getContent().height);
		// and the list itself only as tall as its entries and links
		assertEquals(MapMenu.ROW * MapMenu.Page.values().length + 24 + 8 * 3, fitted.getMenu().height);
	}

	@Test
	public void theWelcomeShowsEachStageOfTheBuilding()
	{
		// not counted yet, building (with its bar), built, and built only as looked at: each draws, its title on top
		MapMenu.Build[] stages = {new MapMenu.Build(-1, -1, true), new MapMenu.Build(120, -1, true),
			new MapMenu.Build(4000, 7716, true), new MapMenu.Build(11716, 0, true), new MapMenu.Build(300, -1, false)};
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1280, 800, false);
		for (MapMenu.Build stage : stages)
		{
			MapMenu menu = new MapMenu(() -> "saved", () -> stage);
			menu.popUp(MapMenu.Page.WELCOME);
			assertTrue(menu.isOpen() && menu.popped());
			MapMenu.Layout layout = menu.layout(buttons, 1280, 800);
			BufferedImage image = new BufferedImage(1280, 800, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			menu.draw(g, layout, null);
			g.dispose();
			assertTrue(new Rectangle(0, 0, 1280, 800).contains(layout.getContent()));
			// chosen from the menu, it is an ordinary page again
			menu.setPage(MapMenu.Page.WELCOME);
			assertTrue(!menu.popped());
		}
	}

	@Test
	public void whatsNewOpensOnceAfterAnUpdateAndTheWelcomeForNewPlayersAndWhileTheMapIsFirstBuilt()
	{
		// what's new, after an update, comes first
		assertEquals(MapMenu.Page.WHATS_NEW, MapMenu.popUp(true, true, false, true, 0));
		assertEquals(MapMenu.Page.WHATS_NEW, MapMenu.popUp(true, false, false, false, 500));
		// the very first opening welcomes, built or not
		assertEquals(MapMenu.Page.WELCOME, MapMenu.popUp(false, false, false, true, 0));
		assertEquals(MapMenu.Page.WELCOME, MapMenu.popUp(false, false, true, false, -1));
		// later, once a run while the map is still first being built
		assertEquals(MapMenu.Page.WELCOME, MapMenu.popUp(false, true, false, false, 500));
		assertNull("not twice a run", MapMenu.popUp(false, true, true, false, 500));
		assertNull("not before the preparer has counted", MapMenu.popUp(false, true, false, false, -1));
		assertNull("not once it is built", MapMenu.popUp(false, true, false, false, 0));
		assertNull("not for a game update's few new regions", MapMenu.popUp(false, true, false, true, 12));
	}
}
