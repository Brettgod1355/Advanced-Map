/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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

	MapInput(AdvancedMapPlugin plugin, IntSupplier width, IntSupplier height)
	{
		this.plugin = plugin;
		this.width = width;
		this.height = height;
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
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (!plugin.isMapOpen())
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
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (!plugin.isMapOpen() || !held)
		{
			return e;
		}
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
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
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
		if (!plugin.isMapOpen() || !wasHeld)
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
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		return swallow(e);
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		return swallow(e);
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (!plugin.isMapOpen())
		{
			return e;
		}
		GameBox.Part part = partAt(e);
		if (part == GameBox.Part.OUTSIDE)
		{
			// the game's own camera zoom
			return e;
		}
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
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (plugin.isMapOpen() && plugin.typing() && !hasShortcutKey(e))
		{
			plugin.type(e.getKeyChar());
			e.consume();
		}
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (plugin.isMapOpen() && e.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			plugin.escape();
			e.consume();
		}
		else if (plugin.isMapOpen() && plugin.typing() && !hasShortcutKey(e))
		{
			// typing in a search box: keep the keys from the game's chat
			plugin.typingKey(e.getKeyCode());
			e.consume();
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (plugin.isMapOpen() && plugin.typing() && !hasShortcutKey(e))
		{
			e.consume();
		}
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
		if (plugin.isMapOpen() && (held || partAt(e) != GameBox.Part.OUTSIDE))
		{
			e.consume();
		}
		return e;
	}
}
