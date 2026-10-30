/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntSupplier;
import javax.swing.SwingUtilities;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;

/**
 * Mouse and keys while the map is open over the game, in its box ({@link GameBox}): inside, drag to move, the wheel
 * to zoom about the pointer, the buttons, a click on an icon for its info box and a right click for its menu; the
 * box's frame moves and sizes it; Escape or the close button closes the map. Events inside the box are kept from the
 * game, so a click on the map never walks the player or opens the game's menu; outside it the game has the mouse.
 */
class MapInput implements MouseListener, MouseWheelListener, KeyListener
{
	/** How much one notch of the wheel zooms. */
	static final double WHEEL_STEP = 1.25;

	private final AdvancedMapPlugin plugin;
	private final IntSupplier width;
	private final IntSupplier height;
	private int lastX;
	private int lastY;
	private int pressX;
	private int pressY;
	private boolean dragging;
	/** A press began on the box (the map or its frame): its drags and release are the map's too. */
	private boolean held;
	/**
	 * The keys whose press the map took: their repeats (a held key is pressed again and again, with no release
	 * between) and their release are the map's too, whatever is typed into by then, so the game never gets a release
	 * without its press (it would keep the key as held: a camera key, Shift) or the reverse.
	 */
	private final Set<Integer> pressedHere = new HashSet<>();
	/** The keys whose press went to the game: their repeats and release are the game's, even once a box is typed into. */
	private final Set<Integer> pressedInGame = new HashSet<>();

	MapInput(AdvancedMapPlugin plugin, IntSupplier width, IntSupplier height)
	{
		this.plugin = plugin;
		this.width = width;
		this.height = height;
	}

	/**
	 * Where the mouse is on the game view, or null when it is off it: the game is not told of moves over the box (they
	 * are the map's), so its own idea of where the mouse is would stay where it left the game.
	 */
	private volatile java.awt.Point mouse;

	java.awt.Point mouse()
	{
		return mouse;
	}

	/** True while the mouse is on the box (the map or its frame). */
	private boolean mouseOnBox()
	{
		java.awt.Point at = mouse;
		return at != null && plugin.gameBox().partAt(at.x, at.y, width.getAsInt(), height.getAsInt())
			!= GameBox.Part.OUTSIDE;
	}

	private GameBox.Part partAt(MouseEvent e)
	{
		return plugin.gameBox().partAt(e.getX(), e.getY(), width.getAsInt(), height.getAsInt());
	}

	/** Where the map is drawn on the game view: its box less the frame. */
	private java.awt.Rectangle map()
	{
		return plugin.gameBox().map(width.getAsInt(), height.getAsInt());
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (!plugin.mapTakesInput())
		{
			return e;
		}
		GameBox.Part part = partAt(e);
		if (part == GameBox.Part.OUTSIDE)
		{
			// a click in the game: the map stops taking the keys
			plugin.clickedOutside();
			return e;
		}
		// whatever the mouse or keys do on the map, it is drawn again at once; what happens on the game beside it
		// is the game's and draws nothing
		plugin.touched();
		held = true;
		e.consume();
		if (part != GameBox.Part.MAP)
		{
			if (SwingUtilities.isLeftMouseButton(e))
			{
				plugin.gameBox().grab(e.getX(), e.getY(), width.getAsInt(), height.getAsInt());
			}
			return e;
		}
		java.awt.Rectangle map = map();
		int x = e.getX() - map.x;
		int y = e.getY() - map.y;
		int w = map.width;
		int h = map.height;
		if (SwingUtilities.isLeftMouseButton(e) && plugin.pressPanels(x, y, w, h, false))
		{
			return e;
		}
		if (SwingUtilities.isRightMouseButton(e))
		{
			plugin.rightClick(x, y, w, h);
			return e;
		}
		MapPainter.Button button = SwingUtilities.isLeftMouseButton(e)
			? MapPainter.buttonAt(x, y, w, h, false, plugin.settings()) : null;
		if (button != null)
		{
			plugin.pressButton(button, false);
		}
		else if (!plugin.pressMenu(x, y, w, h) && SwingUtilities.isLeftMouseButton(e))
		{
			dragging = true;
			plugin.setDragging(true);
			lastX = x;
			lastY = y;
			pressX = x;
			pressY = y;
		}
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		mouse = e.getPoint();
		if (!plugin.mapTakesInput() || !held)
		{
			return e;
		}
		plugin.touched();
		GameBox box = plugin.gameBox();
		if (box.isGrabbed())
		{
			box.dragTo(e.getX(), e.getY(), width.getAsInt(), height.getAsInt());
		}
		else if (dragging)
		{
			java.awt.Rectangle map = map();
			int x = e.getX() - map.x;
			int y = e.getY() - map.y;
			if (Math.abs(x - pressX) > AdvancedMapPlugin.CLICK_SLOP || Math.abs(y - pressY) > AdvancedMapPlugin.CLICK_SLOP)
			{
				// moved by hand: the map stops following the player (a click that wobbles does not count)
				plugin.setFollowing(false);
			}
			plugin.viewport().drag(x - lastX, y - lastY);
			lastX = x;
			lastY = y;
		}
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		if (held)
		{
			plugin.touched();
		}
		if (dragging)
		{
			plugin.setDragging(false);
		}
		String moved = plugin.gameBox().release();
		if (moved != null)
		{
			plugin.saveGameBox(moved);
		}
		boolean wasHeld = held;
		held = false;
		if (!plugin.mapTakesInput() || !wasHeld)
		{
			dragging = false;
			return e;
		}
		java.awt.Rectangle map = map();
		int x = e.getX() - map.x;
		int y = e.getY() - map.y;
		if (dragging && Math.abs(x - pressX) <= AdvancedMapPlugin.CLICK_SLOP
			&& Math.abs(y - pressY) <= AdvancedMapPlugin.CLICK_SLOP)
		{
			plugin.leftClick(x, y, map.width, map.height);
		}
		dragging = false;
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		return swallow(e);
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		mouse = null;
		return e;
	}

	/** A move only moves the pointer the map draws with at its next redraw (every 50 ms): nothing is drawn at once. */
	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		mouse = e.getPoint();
		return swallow(e);
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		if (!plugin.mapTakesInput())
		{
			return e;
		}
		GameBox.Part part = partAt(e);
		if (part == GameBox.Part.OUTSIDE)
		{
			// the game's own camera zoom
			return e;
		}
		plugin.touched();
		java.awt.Rectangle map = map();
		int x = e.getX() - map.x;
		int y = e.getY() - map.y;
		if (part == GameBox.Part.MAP && !plugin.wheelPanels(x, y, e.getPreciseWheelRotation(), map.width, map.height,
			false))
		{
			double factor = Math.pow(WHEEL_STEP, -e.getPreciseWheelRotation());
			plugin.viewport().zoomAt(factor, x, y, map.width, map.height);
		}
		e.consume();
		return e;
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
		if (plugin.mapTakesInput() && plugin.typing() && !hasShortcutKey(e))
		{
			plugin.touched();
			plugin.type(e.getKeyChar());
			e.consume();
		}
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		int code = e.getKeyCode();
		if (pressedInGame.contains(code))
		{
			// held in the game since before a box was typed into (an arrow turning the camera): the game's still
			return;
		}
		boolean typing = plugin.mapTakesInput() && plugin.typing() && !hasShortcutKey(e);
		char remapped = typing ? remappedChar(e) : KeyEvent.CHAR_UNDEFINED;
		if (remapped != KeyEvent.CHAR_UNDEFINED)
		{
			plugin.type(remapped);
		}
		// Escape is the map's while the mouse is on its box or something of it is open or typed into; else it is the
		// game's (closing the bank beside a small map)
		else if (plugin.mapTakesInput() && code == KeyEvent.VK_ESCAPE
			&& (mouseOnBox() || plugin.typing() || plugin.sideListOpen() || plugin.selection() != null))
		{
			plugin.escape();
		}
		else if (typing)
		{
			// typing in a search box: keep the keys from the game's chat
			plugin.typingKey(code);
		}
		else if (!pressedHere.contains(code))
		{
			pressedInGame.add(code);
			return;
		}
		// else a repeat of a key the map took, now that it no longer takes keys (Escape held past the close, Shift
		// held past a click beside the box): still the map's, as its release will be
		plugin.touched();
		pressedHere.add(code);
		e.consume();
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		if (pressedHere.remove(e.getKeyCode()))
		{
			plugin.touched();
			e.consume();
		}
		else
		{
			pressedInGame.remove(e.getKeyCode());
		}
	}

	/**
	 * The letter or digit a key was pressed as when RuneLite's Key Remapping plugin, whose listener runs before this
	 * one, had turned it into a camera, F or other key: it changes the key code, drops the character and eats the
	 * typed event that follows, since the game's chat is not typed into; the extended key code still names the key.
	 * {@link KeyEvent#CHAR_UNDEFINED} for a key pressed as itself.
	 */
	static char remappedChar(KeyEvent e)
	{
		int key = e.getExtendedKeyCode();
		boolean letter = key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z;
		boolean digit = key >= KeyEvent.VK_0 && key <= KeyEvent.VK_9;
		if (key == e.getKeyCode() || !(letter || digit) || e.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
		{
			return KeyEvent.CHAR_UNDEFINED;
		}
		return letter && !e.isShiftDown() ? Character.toLowerCase((char) key) : (char) key;
	}

	/**
	 * Keys held with Ctrl, Alt or Meta stay shortcuts (Ctrl+M closes the map) even while typing a search. AltGr, which
	 * Windows reports as Ctrl and Alt together, types "@" and accented letters on many keyboards: that is typing.
	 */
	private static boolean hasShortcutKey(KeyEvent e)
	{
		boolean altGraph = e.isAltGraphDown() || e.isControlDown() && e.isAltDown();
		return !altGraph && (e.isControlDown() || e.isAltDown()) || e.isMetaDown();
	}

	/** Keeps an event on the box (or of a press that began there) from the game. */
	private MouseEvent swallow(MouseEvent e)
	{
		if (plugin.mapTakesInput() && (held || partAt(e) != GameBox.Part.OUTSIDE))
		{
			e.consume();
		}
		return e;
	}
}
