/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Component;
import java.awt.Panel;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapInputTest
{
	/** The plugin as the input sees it: an open map in a box, with nothing behind the calls it makes. */
	static class Stub extends AdvancedMapPlugin
	{
		boolean open = true;
		boolean loggedIn = true;
		boolean typing;
		int outsideClicks;
		int touches;
		final GameBox box = new GameBox("100,100,600,400", false);

		@Override
		boolean isMapOpen()
		{
			return open;
		}

		@Override
		boolean mapTakesInput()
		{
			return open && loggedIn;
		}

		@Override
		GameBox gameBox()
		{
			return box;
		}

		@Override
		void clickedOutside()
		{
			outsideClicks++;
		}

		@Override
		void touched()
		{
			touches++;
		}

		@Override
		boolean pressPanels(int x, int y, int width, int height, boolean window)
		{
			return false;
		}

		@Override
		boolean pressMenu(int x, int y, int width, int height)
		{
			return false;
		}

		@Override
		MapSettings settings()
		{
			return null;
		}

		@Override
		boolean typing()
		{
			return typing;
		}

		@Override
		boolean sideListOpen()
		{
			return false;
		}

		@Override
		MapPainter.Selection selection()
		{
			return null;
		}

		@Override
		void type(char c)
		{
		}

		@Override
		void typingKey(int keyCode)
		{
		}

		@Override
		void setDragging(boolean on)
		{
		}

		@Override
		MapViewport viewport()
		{
			return MapArea.SURFACE.newViewport();
		}
	}

	private static final Component SOURCE = new Panel();

	static MouseEvent press(int x, int y)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_PRESSED, 0, MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
			MouseEvent.BUTTON1);
	}

	static MouseEvent move(int x, int y)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false, MouseEvent.NOBUTTON);
	}

	static KeyEvent key(int id, int code, int modifiers)
	{
		return new KeyEvent(SOURCE, id, 0, modifiers, code, code == KeyEvent.VK_UNDEFINED ? 'a' : KeyEvent.CHAR_UNDEFINED);
	}

	@Test
	public void anOpenMapTakesTheClicksInItsBoxOnlyWhileTheGameIsLoggedIn()
	{
		Stub plugin = new Stub();
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		MouseEvent inside = press(400, 300);
		input.mousePressed(inside);
		assertTrue("a press on the map is the map's", inside.isConsumed());

		// the login screen: RuneLite draws no overlay, so what the player sees must get the clicks
		plugin.loggedIn = false;
		MouseEvent onLoginScreen = press(400, 300);
		input.mousePressed(onLoginScreen);
		assertFalse("nothing is drawn: the press is the login screen's", onLoginScreen.isConsumed());
		MouseEvent movedThere = move(400, 300);
		input.mouseMoved(movedThere);
		assertFalse(movedThere.isConsumed());
		assertEquals(0, plugin.outsideClicks);

		plugin.loggedIn = true;
		MouseEvent outside = press(10, 10);
		input.mousePressed(outside);
		assertFalse("a press on the game beside the box is the game's", outside.isConsumed());
		assertEquals(1, plugin.outsideClicks);
	}
}
