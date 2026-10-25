/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.bettermap;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.function.IntSupplier;
import javax.swing.SwingUtilities;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;

/**
 * Mouse and keys while the map is open: drag to move, the wheel to zoom about the pointer, Escape or the close
 * button to close. Every event is kept from the game while the map covers it, so a click on the map never walks
 * the player or opens a menu.
 */
class MapInput implements MouseListener, MouseWheelListener, KeyListener
{
	/** How much one notch of the wheel zooms. */
	static final double WHEEL_STEP = 1.25;

	private final BetterMapPlugin plugin;
	private final MapViewport viewport;
	private final IntSupplier width;
	private final IntSupplier height;
	private int lastX;
	private int lastY;
	private boolean dragging;

	MapInput(BetterMapPlugin plugin, MapViewport viewport, IntSupplier width, IntSupplier height)
	{
		this.plugin = plugin;
		this.viewport = viewport;
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
		if (SwingUtilities.isLeftMouseButton(e) && MapOverlay.closeButton(width.getAsInt()).contains(e.getPoint()))
		{
			plugin.closeMap();
		}
		else
		{
			dragging = true;
			lastX = e.getX();
			lastY = e.getY();
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
			viewport.drag(e.getX() - lastX, e.getY() - lastY);
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
		viewport.zoomAt(factor, e.getX(), e.getY(), width.getAsInt(), height.getAsInt());
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
			plugin.closeMap();
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
