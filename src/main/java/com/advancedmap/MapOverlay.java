/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The map over the whole game view, while it is open there rather than in its own window. The map is drawn into a
 * picture that each of the game's frames copies: drawn again at once when the mouse or keys do something, else every
 * {@link #REDRAW_NANOS} (as the pop-out window is), not for every one of the game's hundreds of frames a second.
 */
class MapOverlay extends Overlay
{
	/** How often the map is drawn again while nothing is done to it: regions drawn meanwhile, the player moving. */
	private static final long REDRAW_NANOS = 50_000_000L;

	private final Client client;
	private final AdvancedMapPlugin plugin;
	private final MapPainter painter;
	private BufferedImage frame;
	private long drawnAt;
	private int drawnTouches = -1;
	private java.awt.Point drawnMouse;

	MapOverlay(Client client, AdvancedMapPlugin plugin, MapPainter painter)
	{
		this.client = client;
		this.plugin = plugin;
		this.painter = painter;
		setPosition(OverlayPosition.DYNAMIC);
		// above everything the game draws, its mouse-over text too: the map takes the mouse while open, so the game's
		// text would stay stuck on whatever was under the cursor when the map opened ("Bank Grand Exchange booth")
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
		setPriority(PRIORITY_HIGHEST);
		setMovable(false);
		setSnappable(false);
		setResizable(false);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!plugin.isMapOpen())
		{
			// the picture is not kept while the map is closed
			frame = null;
			return null;
		}
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		if (width <= 0 || height <= 0)
		{
			return null;
		}
		Point canvasMouse = client.getMouseCanvasPosition();
		java.awt.Point mouse = canvasMouse == null ? null : new java.awt.Point(canvasMouse.getX(), canvasMouse.getY());
		long now = System.nanoTime();
		int touches = plugin.touches();
		boolean resized = frame == null || frame.getWidth() != width || frame.getHeight() != height;
		if (resized || touches != drawnTouches || !Objects.equals(mouse, drawnMouse) || now - drawnAt >= REDRAW_NANOS)
		{
			if (resized)
			{
				frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			}
			Graphics2D into = frame.createGraphics();
			try
			{
				// the same font and smoothing RuneLite gives its overlays
				into.setRenderingHints(g.getRenderingHints());
				into.setFont(g.getFont());
				painter.paint(into, plugin.viewport().view(width, height), plugin.area(), plugin.plane(),
					plugin.playerLocation(), mouse, false, plugin.settings(), plugin.progress(), plugin.selection());
			}
			finally
			{
				into.dispose();
			}
			drawnAt = now;
			drawnTouches = touches;
			drawnMouse = mouse;
		}
		g.drawImage(frame, 0, 0, null);
		return new Dimension(width, height);
	}
}
