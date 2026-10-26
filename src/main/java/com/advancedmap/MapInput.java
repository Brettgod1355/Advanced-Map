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
		if (!plugin.isMapOpen())
		{
			return e;
		}
		int w = width.getAsInt();
		int h = height.getAsInt();
		if (SwingUtilities.isRightMouseButton(e))
		{
			plugin.rightClick(e.getX(), e.getY(), w, h);
			e.consume();
			return e;
		}
		MapPainter.Button button = SwingUtilities.isLeftMouseButton(e)
			? MapPainter.buttonAt(e.getX(), e.getY(), w, h, false) : null;
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
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		return swallow(e);
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		if (!plugin.isMapOpen())
		{
			return e;
		}
		double factor = Math.pow(WHEEL_STEP, -e.getPreciseWheelRotation());
		plugin.viewport().zoomAt(factor, e.getX(), e.getY(), width.getAsInt(), height.getAsInt());
		e.consume();
		return e;
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (plugin.isMapOpen() && e.getKeyCode() == KeyEvent.VK_ESCAPE)
		{
			plugin.escape();
			e.consume();
		}
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
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
