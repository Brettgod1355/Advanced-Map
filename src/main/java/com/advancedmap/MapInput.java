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
 * Mouse and keys while the map is open over the game: drag to move, the wheel to zoom about the pointer, the buttons,
 * a click on an icon for its info box and a right click for its menu, Escape or the close button to close. Every
 * event is kept from the game while the map covers it, so a click on the map never walks the player or opens the
 * game's menu.
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

	MapInput(AdvancedMapPlugin plugin, IntSupplier width, IntSupplier height)
	{
		this.plugin = plugin;
		this.width = width;
		this.height = height;
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
		int w = width.getAsInt();
		int h = height.getAsInt();
		if (SwingUtilities.isLeftMouseButton(e) && plugin.pressPanels(e.getX(), e.getY(), w, h, false))
		{
			e.consume();
			return e;
		}
		if (SwingUtilities.isRightMouseButton(e))
		{
			plugin.rightClick(e.getX(), e.getY(), w, h);
			e.consume();
			return e;
		}
		MapPainter.Button button = SwingUtilities.isLeftMouseButton(e)
			? MapPainter.buttonAt(e.getX(), e.getY(), w, h, false, plugin.settings()) : null;
		if (button != null)
		{
			plugin.pressButton(button, false);
		}
		else if (!plugin.pressMenu(e.getX(), e.getY(), w, h) && SwingUtilities.isLeftMouseButton(e))
		{
			dragging = true;
			lastX = e.getX();
			lastY = e.getY();
			pressX = e.getX();
			pressY = e.getY();
		}
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (!plugin.isMapOpen())
		{
			return e;
		}
		if (dragging)
		{
			plugin.viewport().drag(e.getX() - lastX, e.getY() - lastY);
			lastX = e.getX();
			lastY = e.getY();
		}
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		// whatever the mouse or keys do, the map over the game is drawn again at once
		plugin.touched();
		if (!plugin.isMapOpen())
		{
			dragging = false;
			return e;
		}
		if (dragging && Math.abs(e.getX() - pressX) <= AdvancedMapPlugin.CLICK_SLOP
			&& Math.abs(e.getY() - pressY) <= AdvancedMapPlugin.CLICK_SLOP)
		{
			plugin.leftClick(e.getX(), e.getY(), width.getAsInt(), height.getAsInt());
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
		if (!plugin.wheelPanels(e.getX(), e.getY(), e.getPreciseWheelRotation(), width.getAsInt(),
			height.getAsInt(), false))
		{
			double factor = Math.pow(WHEEL_STEP, -e.getPreciseWheelRotation());
			plugin.viewport().zoomAt(factor, e.getX(), e.getY(), width.getAsInt(), height.getAsInt());
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

	private MouseEvent swallow(MouseEvent e)
	{
		if (plugin.isMapOpen())
		{
			e.consume();
		}
		return e;
	}
}
