/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The plugin's own state between opens and closes, on an instance with nothing injected. */
public class PluginStateTest
{
	@Test
	public void closingGivesTheKeysBackWhateverWasOpen()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.filters().setOpen(true);
		assertTrue("the Filters list takes the keys", plugin.typing());
		plugin.giveKeysBack();
		assertFalse("closed with the map: keys to the game on the next open", plugin.typing());
		assertFalse(plugin.filters().isOpen());

		plugin.search().setFocused(true);
		plugin.editor().create(new WorldPoint(3222, 3218, 0));
		assertTrue(plugin.typing());
		plugin.giveKeysBack();
		assertFalse(plugin.typing());
		assertFalse(plugin.editor().isOpen());
		assertFalse(plugin.search().isFocused());
	}

	@Test
	public void aMapAlreadyInItsWindowIsShownThereAgainNeverOverTheGameAsWell()
	{
		// opened on the login screen (so in the window), then asked for again after logging in by the orb or another
		// plugin: the window stays the one map; two maps painted by one painter from two threads is what AM-26 rules out
		assertTrue(AdvancedMapPlugin.opensInWindow(true, false, true));
		assertTrue(AdvancedMapPlugin.opensInWindow(false, true, true));
		assertTrue("no overlay is drawn on the login screen", AdvancedMapPlugin.opensInWindow(false, false, false));
		assertFalse(AdvancedMapPlugin.opensInWindow(false, false, true));
		// and the other way: the box open, "Pop out" ticked meanwhile, the orb clicked: the window replaces the box
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.showIn(false);
		assertTrue(plugin.isMapOpen());
		assertFalse(plugin.isWindowOpen());
		plugin.showIn(true);
		assertFalse("never both at once", plugin.isMapOpen());
		assertTrue(plugin.isWindowOpen());
	}

	@Test
	public void followingIsOnlyEverSetThroughSetFollowingSoThePainterAgrees() throws IOException
	{
		// the painter draws the locate button lit from its own copy of the flag: a bare write to the plugin's left the
		// button lit after another plugin opened the map on a point
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "com", "advancedmap",
			"AdvancedMapPlugin.java")), StandardCharsets.UTF_8);
		Matcher writes = Pattern.compile("\\bfollowing = ").matcher(source);
		int count = 0;
		while (writes.find())
		{
			count++;
		}
		assertEquals("one in startUp, before the painter exists, and the one in setFollowing", 2, count);
	}

	@Test
	public void aStartForgetsTheListSelectionAndSearchOfBeforeTheStop()
	{
		// RuneLite keeps the plugin object: a fairy ring's list, a selected marker and a typed search survived a stop,
		// while the lines and marks they went with did not
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.sideList().open("Fairy ring", java.util.Collections.singletonList(
			new SideList.Row("AIQ", null, null, null, new WorldPoint(2996, 3114, 0), null)), "ring");
		plugin.pins().select(new MapPins.Pin(new WorldPoint(3222, 3218, 0), null, null, "Home", "Your marker", null,
			false));
		plugin.search().type('v', java.util.Collections.emptyList(), null);
		assertTrue(plugin.sideListOpen());
		assertEquals("v", plugin.search().query());
		plugin.forgetWhatWasShown();
		assertFalse(plugin.sideListOpen());
		assertEquals("", plugin.search().query());
		assertTrue(plugin.pins().selected() == null);
	}

	@Test
	public void theLinesInTheSearchBoxOpenTheSearchInListAndEscapeClosesItFirst()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		java.awt.Rectangle lines = plugin.search().layout(MapPainter.buttons(1200, 800, false), 1200).getScopes();
		assertTrue(plugin.pressPanels(lines.x + 4, lines.y + 4, 1200, 800, false));
		assertTrue(plugin.search().isScopesOpen());
		assertTrue("the bar keeps the keys while the list is open", plugin.typing());
		plugin.escape();
		assertFalse("Escape closes the list first", plugin.search().isScopesOpen());
		assertTrue(plugin.search().isFocused());
		plugin.escape();
		assertFalse(plugin.search().isFocused());
		// the wheel over the open list neither scrolls it nor zooms the map under it
		plugin.pressPanels(lines.x + 4, lines.y + 4, 1200, 800, false);
		java.awt.Rectangle list = plugin.search().layout(MapPainter.buttons(1200, 800, false), 1200).getResults();
		assertTrue(plugin.wheelPanels(list.x + 10, list.y + 10, 1, 1200, 800, false));
		plugin.escape();
		// a click on the lines again closes it
		plugin.pressPanels(lines.x + 4, lines.y + 4, 1200, 800, false);
		plugin.pressPanels(lines.x + 4, lines.y + 4, 1200, 800, false);
		assertFalse(plugin.search().isScopesOpen());
	}

	@Test
	public void theMenuOpensFromItsButtonAndEscapeClosesItsPageThenTheMenu()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.search().setFocused(true);
		plugin.pressButton(MapPainter.Button.MENU, false);
		assertTrue(plugin.menu().isOpen());
		assertFalse("the search gives the keys up for the menu", plugin.search().isFocused());
		assertTrue("Escape goes to the map while it is open", plugin.sideListOpen());
		java.util.Map<MapPainter.Button, java.awt.Rectangle> buttons = MapPainter.buttons(1200, 800, false);
		MapMenu.Layout closed = MapMenu.layout(buttons, 1200, 800, null);
		java.awt.Rectangle panel = closed.getMenu();
		// "Build the map again" opens its page; Cancel closes the page and keeps the menu
		assertTrue(plugin.pressPanels(panel.x + 20, panel.y + 8 + MapMenu.Page.REDRAW.ordinal() * MapMenu.ROW + 5,
			1200, 800, false));
		assertEquals(MapMenu.Page.REDRAW, plugin.menu().page());
		MapMenu.Layout open = plugin.menu().layout(buttons, 1200, 800);
		assertTrue(plugin.pressPanels(open.getButton2().x + 3, open.getButton2().y + 3, 1200, 800, false));
		assertNull(plugin.menu().page());
		assertTrue(plugin.menu().isOpen());
		// a press on the page is the page's; the wheel over it is too
		plugin.menu().setPage(MapMenu.Page.HELP);
		assertTrue(plugin.pressPanels(open.getContent().x + 50, open.getContent().y + 100, 1200, 800, false));
		assertTrue(plugin.wheelPanels(open.getContent().x + 50, open.getContent().y + 100, 1, 1200, 800, false));
		plugin.escape();
		assertNull("Escape closes the page first", plugin.menu().page());
		assertTrue(plugin.menu().isOpen());
		plugin.escape();
		assertFalse("then the menu", plugin.menu().isOpen());
		// a press on the map beside the menu closes it and goes on to the map
		plugin.pressButton(MapPainter.Button.MENU, false);
		assertFalse(plugin.pressPanels(1000, 700, 1200, 800, false));
		assertFalse(plugin.menu().isOpen());
		// closing the map closes the menu
		plugin.pressButton(MapPainter.Button.MENU, false);
		plugin.giveKeysBack();
		assertFalse(plugin.menu().isOpen());
		// a page that opened by itself goes with one Escape, menu and all
		plugin.menu().popUp(MapMenu.Page.WHATS_NEW);
		plugin.escape();
		assertFalse(plugin.menu().isOpen());
	}

	@Test
	public void theMenusListsCloseWithTheMapAndOtherListsStay()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		java.util.List<SideList.Row> rows = java.util.Collections.singletonList(
			new SideList.Row("Bank stash", "12 tiles", null, null, null, null));
		for (Object list : new Object[]{AdvancedMapPlugin.MARKERS_LIST, AdvancedMapPlugin.HIDDEN_LIST})
		{
			plugin.sideList().open("Your markers", rows, list);
			plugin.closeMenuLists();
			assertFalse("made for the account and settings of then", plugin.sideList().isOpen());
		}
		// a shop's stock or a search's results stay, as before
		Object shop = new Object();
		plugin.sideList().open("General store", rows, shop);
		plugin.closeMenuLists();
		assertTrue(plugin.sideList().isOpen());
	}

	@Test
	public void aRightPressOnTheMenuOrItsPageOpensNoMenuUnderneath()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.pressButton(MapPainter.Button.MENU, false);
		plugin.menu().setPage(MapMenu.Page.HELP);
		java.util.Map<MapPainter.Button, java.awt.Rectangle> buttons = MapPainter.buttons(1200, 800, false);
		MapMenu.Layout open = plugin.menu().layout(buttons, 1200, 800);
		java.awt.Rectangle page = open.getContent();
		plugin.rightClick(page.x + 50, page.y + 100, 1200, 800);
		assertNull("the page is the menu's: no tile menu beneath it", plugin.selection());
		plugin.rightClick(open.getMenu().x + 20, open.getMenu().y + 20, 1200, 800);
		assertNull(plugin.selection());
		assertTrue("and the menu stays as it was", plugin.menu().isOpen() && plugin.menu().page() == MapMenu.Page.HELP);
		// so does a button: the right press on it is not a press on the map under it
		java.awt.Rectangle lines = buttons.get(MapPainter.Button.MENU);
		plugin.rightClick(lines.x + 4, lines.y + 4, 1200, 800);
		assertNull(plugin.selection());
	}
}
