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
}
