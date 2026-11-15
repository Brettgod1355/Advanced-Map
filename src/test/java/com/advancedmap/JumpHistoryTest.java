/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The mouse's back and forward buttons after a jump (a dungeon's way in, a search, a pin), as a browser's. */
public class JumpHistoryTest
{
	private static JumpHistory.Place at(int x)
	{
		return new JumpHistory.Place(MapArea.SURFACE, 0, x, 3218, 4);
	}

	@Test
	public void backReturnsFromADungeonAndForwardGoesInAgain()
	{
		JumpHistory history = new JumpHistory();
		JumpHistory.Place lumbridge = at(3222);
		JumpHistory.Place dungeon = new JumpHistory.Place(MapArea.UNDERGROUND, 0, 3210, 9616, 6);
		assertNull("nothing to go back to before a jump", history.back(lumbridge));

		history.jumped(lumbridge);
		assertSame(lumbridge, history.back(dungeon));
		assertSame("forward goes back into the dungeon", dungeon, history.forward(lumbridge));
		assertNull(history.forward(dungeon));
		assertSame(lumbridge, history.back(dungeon));
	}

	@Test
	public void aNewJumpForgetsForwardAndBackWalksEveryJump()
	{
		JumpHistory history = new JumpHistory();
		history.jumped(at(1));
		history.jumped(at(2));
		assertEquals(2, history.back(at(3)).centerX, 0);
		history.jumped(at(4));
		assertNull("forward is forgotten by a new jump, as a browser's", history.forward(at(5)));
		assertEquals(4, history.back(at(5)).centerX, 0);
		assertEquals(1, history.back(at(4)).centerX, 0);
		assertNull(history.back(at(1)));
	}

	@Test
	public void onlyTheLastJumpsAreKept()
	{
		JumpHistory history = new JumpHistory();
		for (int x = 0; x < JumpHistory.KEPT + 5; x++)
		{
			history.jumped(at(x));
		}
		int steps = 0;
		JumpHistory.Place here = at(-1);
		for (JumpHistory.Place back = history.back(here); back != null; back = history.back(here))
		{
			here = back;
			steps++;
		}
		assertEquals(JumpHistory.KEPT, steps);
		assertEquals("the oldest are forgotten first", 5, here.centerX, 0);
	}

	@Test
	public void aRestoredViewLooksExactlyWhereItLooked()
	{
		MapViewport viewport = MapArea.SURFACE.newViewport();
		viewport.centerOn(3222, 3218);
		viewport.setZoom(3);
		MapViewport.View before = viewport.view(1, 1);
		viewport.centerOn(2759, 2774);
		viewport.setZoom(8);
		viewport.restore(before.centerX, before.centerY, before.zoom);
		MapViewport.View after = viewport.view(1, 1);
		assertEquals(before.centerX, after.centerX, 0);
		assertEquals(before.centerY, after.centerY, 0);
		assertEquals(before.zoom, after.zoom, 0);
	}

	@Test
	public void aDungeonsWayInKeepsTheZoomAndBackReturnsToTheSurface()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.newViews();
		plugin.switchArea();
		plugin.viewport().setZoom(2);
		plugin.switchArea();
		plugin.viewport().setZoom(8);
		MapViewport.View surface = plugin.viewport().view(1, 1);

		// the Edgeville dungeon's ladder: the underground map, as close as the surface was, not its own 2 of before
		plugin.goTo(new net.runelite.api.coords.WorldPoint(3097, 9868, 0));
		assertEquals(MapArea.UNDERGROUND, plugin.area());
		assertEquals(8, plugin.viewport().view(1, 1).zoom, 0);

		assertTrue(plugin.historyButton(AdvancedMapPlugin.BACK_BUTTON));
		assertEquals(MapArea.SURFACE, plugin.area());
		MapViewport.View back = plugin.viewport().view(1, 1);
		assertEquals(surface.centerX, back.centerX, 0);
		assertEquals(surface.centerY, back.centerY, 0);
		assertEquals(8, back.zoom, 0);
		assertTrue(plugin.historyButton(AdvancedMapPlugin.FORWARD_BUTTON));
		assertEquals(MapArea.UNDERGROUND, plugin.area());
	}

	@Test
	public void theSameJumpTwiceIsOneStepBack()
	{
		// the same search result clicked twice: one press of Back returns to where the map was before
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.newViews();
		MapViewport.View start = plugin.viewport().view(1, 1);
		WorldPoint varrock = new WorldPoint(3213, 3424, 0);
		plugin.goTo(varrock);
		plugin.goTo(varrock);
		assertTrue(plugin.historyButton(AdvancedMapPlugin.BACK_BUTTON));
		assertEquals(start.centerX, plugin.viewport().view(1, 1).centerX, 0);
		assertEquals(start.centerY, plugin.viewport().view(1, 1).centerY, 0);
	}

	@Test
	public void theSameTileZoomedInAgainIsAStepBack()
	{
		// a search result, zoomed out without moving, then the same result again: Back returns to the zoomed-out view
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.newViews();
		WorldPoint varrock = new WorldPoint(3213, 3424, 0);
		plugin.goTo(varrock, AdvancedMapPlugin.SEARCH_ZOOM);
		plugin.viewport().setZoom(1);
		plugin.goTo(varrock, AdvancedMapPlugin.SEARCH_ZOOM);
		assertEquals(AdvancedMapPlugin.SEARCH_ZOOM, plugin.viewport().view(1, 1).zoom, 0);
		assertTrue(plugin.historyButton(AdvancedMapPlugin.BACK_BUTTON));
		assertEquals(1, plugin.viewport().view(1, 1).zoom, 0);
	}

	@Test
	public void aRestartForgetsTheJumps()
	{
		// the plugin turned off and on: Back has nowhere to go, not a place from before
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.newViews();
		plugin.goTo(new WorldPoint(3097, 9868, 0));
		plugin.newViews();
		MapViewport.View start = plugin.viewport().view(1, 1);
		assertTrue(plugin.historyButton(AdvancedMapPlugin.BACK_BUTTON));
		assertEquals(start.centerX, plugin.viewport().view(1, 1).centerX, 0);
		assertEquals(MapArea.SURFACE, plugin.area());
	}

	@Test
	public void theSideButtonsAreTheMapsAndNoOtherButtonIs()
	{
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		assertFalse(plugin.historyButton(java.awt.event.MouseEvent.BUTTON1));
		assertFalse(plugin.historyButton(java.awt.event.MouseEvent.BUTTON3));
		// nothing jumped yet: the button is still the map's, and does nothing
		assertTrue(plugin.historyButton(AdvancedMapPlugin.BACK_BUTTON));
		assertTrue(plugin.historyButton(AdvancedMapPlugin.FORWARD_BUTTON));
	}

	@Test
	public void aMovedMapOpensWhereItWasLeftUntilTheLocateButton()
	{
		// owner, 2026-11-12 (issue #3): "retain its last known position upon closing"; the locate button takes it back
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.newViews();
		// never moved: an opening is on the player (Lumbridge while there is none)
		plugin.lookAt(null, true);
		MapViewport.View home = plugin.viewport().view(1, 1);
		assertEquals(3222.5, home.centerX, 0);
		// dragged away, as the map's input does it: the next opening stays there
		plugin.setFollowing(false);
		plugin.viewport().drag(400, 0);
		MapViewport.View dragged = plugin.viewport().view(1, 1);
		assertTrue(dragged.centerX != home.centerX);
		plugin.lookAt(null, true);
		assertEquals(dragged.centerX, plugin.viewport().view(1, 1).centerX, 0);
		// a place to show (a search, another plugin) is shown, and kept at the next opening
		plugin.lookAt(new net.runelite.api.coords.WorldPoint(2965, 3380, 0), true);
		assertEquals(2965.5, plugin.viewport().view(1, 1).centerX, 0);
		plugin.lookAt(null, true);
		assertEquals(2965.5, plugin.viewport().view(1, 1).centerX, 0);
		// the locate button: on the player again, at this opening and the next
		plugin.setFollowing(true);
		plugin.lookAt(null, true);
		assertEquals(home.centerX, plugin.viewport().view(1, 1).centerX, 0);
	}

	@Test
	public void withOpenWhereYouLeftItOffAMovedMapOpensOnThePlayer()
	{
		// owner, 2026-11-15: a setting between opening where it was left (the default) and on you
		AdvancedMapPlugin plugin = new AdvancedMapPlugin();
		plugin.newViews();
		plugin.lookAt(null, false);
		MapViewport.View home = plugin.viewport().view(1, 1);
		// dragged away: the next opening is back on the player (Lumbridge while there is none), on their floor
		plugin.setFollowing(false);
		plugin.viewport().drag(400, 0);
		assertTrue(plugin.viewport().view(1, 1).centerX != home.centerX);
		plugin.lookAt(null, false);
		assertEquals(home.centerX, plugin.viewport().view(1, 1).centerX, 0);
		// a place to show is still shown; the opening after it is on the player again
		plugin.lookAt(new net.runelite.api.coords.WorldPoint(2965, 3380, 0), false);
		assertEquals(2965.5, plugin.viewport().view(1, 1).centerX, 0);
		plugin.lookAt(null, false);
		assertEquals(home.centerX, plugin.viewport().view(1, 1).centerX, 0);
	}
}
