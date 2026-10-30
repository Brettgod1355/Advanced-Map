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

		final StringBuilder typed = new StringBuilder();
		final java.util.List<Integer> typingKeys = new java.util.ArrayList<>();

		@Override
		void type(char c)
		{
			typed.append(c);
		}

		@Override
		void typingKey(int keyCode)
		{
			typingKeys.add(keyCode);
		}

		@Override
		void setDragging(boolean on)
		{
		}

		@Override
		void saveGameBox(String bounds)
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

	/**
	 * A press of a key that Key Remapping turned into another: the key code changed, the character dropped; the
	 * extended key code, which only the platform's own code sets on a real event, still names the key.
	 */
	static KeyEvent remapped(int key, int modifiers, int mappedTo)
	{
		KeyEvent e = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, modifiers, mappedTo, KeyEvent.CHAR_UNDEFINED)
		{
			@Override
			public int getExtendedKeyCode()
			{
				return key;
			}
		};
		return e;
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

	@Test
	public void aKeyReleaseIsTheMapsOnlyWhenItsPressWas()
	{
		Stub plugin = new Stub();
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// an arrow held to turn the camera, then the search bar clicked, then the arrow let go: the game had the
		// press, so it must get the release, or it keeps turning
		KeyEvent pressed = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, 0);
		input.keyPressed(pressed);
		assertFalse(pressed.isConsumed());
		plugin.typing = true;
		KeyEvent released = key(KeyEvent.KEY_RELEASED, KeyEvent.VK_LEFT, 0);
		input.keyReleased(released);
		assertFalse("the release goes where the press went", released.isConsumed());

		// typed into the search bar: both are the map's, even when the bar lost the keys in between
		KeyEvent typedPress = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_V, 0);
		input.keyPressed(typedPress);
		assertTrue(typedPress.isConsumed());
		plugin.typing = false;
		KeyEvent typedRelease = key(KeyEvent.KEY_RELEASED, KeyEvent.VK_V, 0);
		input.keyReleased(typedRelease);
		assertTrue("the release of a key the map took is the map's", typedRelease.isConsumed());
		KeyEvent again = key(KeyEvent.KEY_RELEASED, KeyEvent.VK_V, 0);
		input.keyReleased(again);
		assertFalse("once", again.isConsumed());
	}

	@Test
	public void altGrTypingLeavesTheGameItsCtrlPressAndRelease()
	{
		Stub plugin = new Stub();
		plugin.typing = true;
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// Windows reports AltGr as Ctrl then Alt: the Ctrl press is a shortcut (the game's), so its release is too
		KeyEvent ctrl = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_CONTROL, KeyEvent.CTRL_DOWN_MASK);
		input.keyPressed(ctrl);
		assertFalse(ctrl.isConsumed());
		KeyEvent ctrlUp = key(KeyEvent.KEY_RELEASED, KeyEvent.VK_CONTROL, 0);
		input.keyReleased(ctrlUp);
		assertFalse(ctrlUp.isConsumed());
	}

	@Test
	public void onlyWhatHappensOnTheMapHasItDrawnAgainAtOnce()
	{
		Stub plugin = new Stub();
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		input.mouseMoved(move(10, 10));
		input.mouseMoved(move(400, 300));
		input.mouseEntered(move(400, 300));
		input.mouseExited(move(400, 300));
		input.keyPressed(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_F2, 0));
		input.keyReleased(key(KeyEvent.KEY_RELEASED, KeyEvent.VK_F2, 0));
		input.mousePressed(press(10, 10));
		assertEquals("moves, keys the game keeps and clicks beside the box draw nothing", 0, plugin.touches);
		input.mousePressed(press(400, 300));
		assertEquals(1, plugin.touches);
		plugin.typing = true;
		input.keyPressed(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_A, 0));
		assertEquals(2, plugin.touches);
	}

	@Test
	public void aLetterTheKeyRemappingPluginTurnedIntoACameraKeyIsStillTypedIntoTheSearch()
	{
		Stub plugin = new Stub();
		plugin.typing = true;
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// RuneLite's Key Remapping plugin runs first: "a" becomes Left with no character, and the typed event never comes
		KeyEvent a = remapped(KeyEvent.VK_A, 0, KeyEvent.VK_LEFT);
		input.keyPressed(a);
		assertTrue(a.isConsumed());
		input.keyPressed(remapped(KeyEvent.VK_D, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_RIGHT));
		input.keyPressed(remapped(KeyEvent.VK_1, 0, KeyEvent.VK_F1));
		assertEquals("aD1", plugin.typed.toString());
		assertTrue("the arrow it was turned into does not move the highlight", plugin.typingKeys.isEmpty());

		// a real arrow, and a letter nobody remapped, are handled as before
		input.keyPressed(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, 0));
		assertEquals(java.util.Collections.singletonList(KeyEvent.VK_LEFT), plugin.typingKeys);
		KeyEvent plain = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a');
		input.keyPressed(plain);
		assertEquals("typed by the typed event that follows, not twice", "aD1", plugin.typed.toString());
		assertEquals(KeyEvent.CHAR_UNDEFINED, MapInput.remappedChar(plain));
		// a key of another layout with no plain key code, which keeps its character: typed once, by its typed event
		KeyEvent umlaut = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_UNDEFINED, '\u00e4');
		assertEquals(KeyEvent.CHAR_UNDEFINED, MapInput.remappedChar(umlaut));
	}

	@Test
	public void aHeldKeyStaysWhereItsFirstPressWentThroughItsRepeats()
	{
		Stub plugin = new Stub();
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// Left held to turn the camera, the search bar clicked meanwhile: the repeats the OS sends while it is held are
		// the game's too, and so is the release, else the camera keeps turning
		input.keyPressed(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, 0));
		plugin.typing = true;
		KeyEvent repeat = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_LEFT, 0);
		input.keyPressed(repeat);
		assertFalse("a repeat of the game's key is the game's", repeat.isConsumed());
		assertTrue("and does not move the search highlight", plugin.typingKeys.isEmpty());
		KeyEvent released = key(KeyEvent.KEY_RELEASED, KeyEvent.VK_LEFT, 0);
		input.keyReleased(released);
		assertFalse(released.isConsumed());

		// Shift held for a capital in the search bar, then a click beside the box (the keys go back to the game) while
		// it is still held: its repeats and release are the map's, else the game is left with Shift down (plain clicks
		// on items drop them)
		KeyEvent shift = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, KeyEvent.SHIFT_DOWN_MASK);
		input.keyPressed(shift);
		assertTrue(shift.isConsumed());
		plugin.typing = false;
		KeyEvent shiftAgain = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, KeyEvent.SHIFT_DOWN_MASK);
		input.keyPressed(shiftAgain);
		assertTrue("a repeat of the map's key is the map's", shiftAgain.isConsumed());
		KeyEvent shiftUp = key(KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT, 0);
		input.keyReleased(shiftUp);
		assertTrue(shiftUp.isConsumed());
		// pressed anew: the game's
		KeyEvent shiftLater = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, KeyEvent.SHIFT_DOWN_MASK);
		input.keyPressed(shiftLater);
		assertFalse(shiftLater.isConsumed());
	}

	@Test
	public void aReleaseThatWentToAnotherWindowIsNotWaitedFor()
	{
		Stub plugin = new Stub();
		plugin.typing = true;
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// Shift held for a capital, another window takes the focus, Shift released there: the map would otherwise eat
		// the release of the next Shift the game is given
		input.keyPressed(key(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, KeyEvent.SHIFT_DOWN_MASK));
		input.focusLost();
		plugin.typing = false;
		KeyEvent pressed = key(KeyEvent.KEY_PRESSED, KeyEvent.VK_SHIFT, KeyEvent.SHIFT_DOWN_MASK);
		input.keyPressed(pressed);
		assertFalse(pressed.isConsumed());
		KeyEvent released = key(KeyEvent.KEY_RELEASED, KeyEvent.VK_SHIFT, 0);
		input.keyReleased(released);
		assertFalse("the game had the press, it gets the release", released.isConsumed());
	}

	@Test
	public void theReleaseOfAPressTheMapTookIsTheMapsEvenAfterTheMapClosed()
	{
		Stub plugin = new Stub();
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		input.mousePressed(press(400, 300));
		plugin.open = false;
		MouseEvent released = new MouseEvent(SOURCE, MouseEvent.MOUSE_RELEASED, 0, 0, 400, 300, 1, false,
			MouseEvent.BUTTON1);
		input.mouseReleased(released);
		assertTrue("the game had no press to release", released.isConsumed());
		// a release with no press on the map behind it is the game's
		MouseEvent stray = new MouseEvent(SOURCE, MouseEvent.MOUSE_RELEASED, 0, 0, 400, 300, 1, false,
			MouseEvent.BUTTON1);
		input.mouseReleased(stray);
		assertFalse(stray.isConsumed());
	}

	@Test
	public void punctuationTheKeyRemappingPluginTurnedIntoOtherKeysIsTypedToo()
	{
		Stub plugin = new Stub();
		plugin.typing = true;
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// camera on ";" and "'" (IJKL layouts), Key Remapping's own default F11 and F12 on "-" and "=", Control on "`",
		// Space on Space: all typed; an accented letter of another layout by its character; Shift and a key with
		// no character (a numpad key, an F key) are not guessed at
		input.keyPressed(remapped(KeyEvent.VK_SEMICOLON, 0, KeyEvent.VK_DOWN));
		input.keyPressed(remapped(KeyEvent.VK_QUOTE, 0, KeyEvent.VK_RIGHT));
		input.keyPressed(remapped(KeyEvent.VK_MINUS, 0, KeyEvent.VK_F11));
		input.keyPressed(remapped(KeyEvent.VK_EQUALS, 0, KeyEvent.VK_F12));
		input.keyPressed(remapped(KeyEvent.VK_BACK_QUOTE, 0, KeyEvent.VK_CONTROL));
		input.keyPressed(remapped(KeyEvent.VK_SPACE, 0, KeyEvent.VK_CONTROL));
		input.keyPressed(remapped(0x01000000 | '\u00e4', 0, KeyEvent.VK_UP));
		input.keyPressed(remapped(KeyEvent.VK_SEMICOLON, KeyEvent.SHIFT_DOWN_MASK, KeyEvent.VK_DOWN));
		input.keyPressed(remapped(KeyEvent.VK_NUMPAD4, 0, KeyEvent.VK_LEFT));
		input.keyPressed(remapped(KeyEvent.VK_F5, 0, KeyEvent.VK_LEFT));
		assertEquals(";'-=` \u00e4", plugin.typed.toString());
	}

	@Test
	public void aHeldKeysTypedRepeatsFollowItsPress()
	{
		Stub plugin = new Stub();
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// Space held in the game (a dialogue), then the Filters list opened while it is held
		KeyEvent space = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_SPACE, ' ');
		input.keyPressed(space);
		assertFalse(space.isConsumed());
		input.keyTyped(new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, ' '));
		plugin.typing = true;
		KeyEvent repeat = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_SPACE, ' ');
		input.keyPressed(repeat);
		assertFalse(repeat.isConsumed());
		KeyEvent typed = new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, ' ');
		input.keyTyped(typed);
		assertEquals("the game's held key types nothing into the map", "", plugin.typed.toString());
		assertFalse(typed.isConsumed());
		input.keyReleased(key(KeyEvent.KEY_RELEASED, KeyEvent.VK_SPACE, 0));

		// a letter held while typing in the search bar, then a click beside the box while it is held
		KeyEvent a = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a');
		input.keyPressed(a);
		assertTrue(a.isConsumed());
		input.keyTyped(new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a'));
		assertEquals("a", plugin.typed.toString());
		plugin.typing = false;
		KeyEvent aRepeat = new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_A, 'a');
		input.keyPressed(aRepeat);
		assertTrue(aRepeat.isConsumed());
		KeyEvent aTyped = new KeyEvent(SOURCE, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a');
		input.keyTyped(aTyped);
		assertTrue("the typed event of a press the map took is the map's", aTyped.isConsumed());
		assertEquals("a", plugin.typed.toString());
	}

	static MouseEvent pressWith(int x, int y, int button, int modifiers)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_PRESSED, 0, modifiers, x, y, 1, false, button);
	}

	static MouseEvent releaseWith(int x, int y, int button)
	{
		return new MouseEvent(SOURCE, MouseEvent.MOUSE_RELEASED, 0, 0, x, y, 1, false, button);
	}

	@Test
	public void aButtonPressedOnTheGameWhileAnotherHoldsTheMapKeepsItsOwnRelease()
	{
		Stub plugin = new Stub();
		MapInput input = new MapInput(plugin, () -> 1280, () -> 800);
		// the left button drags the map; the right button clicks an inventory item beside the box meanwhile: the
		// right release is the game's, the left one the map's, whichever comes first
		assertTrue(input.mousePressed(pressWith(400, 300, MouseEvent.BUTTON1, MouseEvent.BUTTON1_DOWN_MASK)).isConsumed());
		MouseEvent rightPress = pressWith(10, 10, MouseEvent.BUTTON3, MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK);
		input.mousePressed(rightPress);
		assertFalse(rightPress.isConsumed());
		MouseEvent rightRelease = releaseWith(10, 10, MouseEvent.BUTTON3);
		input.mouseReleased(rightRelease);
		assertFalse("the game had the press, it gets the release", rightRelease.isConsumed());
		// let go after a drag (farther than a click's wobble), so no click is made of it
		MouseEvent leftRelease = releaseWith(440, 340, MouseEvent.BUTTON1);
		input.mouseReleased(leftRelease);
		assertTrue(leftRelease.isConsumed());

		// the frame dragged with the left button is not let go of by the right button's release on the game
		assertTrue(input.mousePressed(pressWith(400, 102, MouseEvent.BUTTON1, MouseEvent.BUTTON1_DOWN_MASK)).isConsumed());
		assertTrue(plugin.box.isGrabbed());
		input.mousePressed(pressWith(10, 10, MouseEvent.BUTTON3, MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK));
		input.mouseReleased(releaseWith(10, 10, MouseEvent.BUTTON3));
		assertTrue("the frame stays grabbed", plugin.box.isGrabbed());
		MouseEvent frameRelease = releaseWith(400, 102, MouseEvent.BUTTON1);
		input.mouseReleased(frameRelease);
		assertTrue(frameRelease.isConsumed());
		assertFalse(plugin.box.isGrabbed());
	}
}
