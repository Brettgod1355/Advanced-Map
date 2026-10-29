/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The map over the game view, in its box ({@link GameBox}: moved and sized by its frame, or maximized over the whole
 * view), while it is open there rather than in its own window. The map is drawn into a picture that each of the
 * game's frames copies: drawn again at once when the mouse or keys do something, else every {@link #REDRAW_NANOS}
 * (as the pop-out window is), not for every one of the game's hundreds of frames a second.
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
		// the map in its box (the whole view when maximized); the game goes on around it
		GameBox box = plugin.gameBox();
		Rectangle map = box.map(width, height);
		// the mouse as the map's input last saw it: the game's own position stands still while the box has it
		java.awt.Point canvasMouse = plugin.gameMouse();
		java.awt.Point mouse = canvasMouse == null || !map.contains(canvasMouse) ? null
			: new java.awt.Point(canvasMouse.x - map.x, canvasMouse.y - map.y);
		long now = System.nanoTime();
		int touches = plugin.touches();
		boolean resized = frame == null || frame.getWidth() != map.width || frame.getHeight() != map.height;
		// drawn again at once after a press, a drag, the wheel or a key on the map; a moved pointer waits for the next
		// redraw (hover boxes and tips take longer than that to appear), so a moving mouse does not cost the game a
		// whole map paint every frame
		if (resized || touches != drawnTouches || now - drawnAt >= REDRAW_NANOS)
		{
			if (resized)
			{
				frame = new BufferedImage(map.width, map.height, BufferedImage.TYPE_INT_RGB);
			}
			Graphics2D into = frame.createGraphics();
			try
			{
				// the same font and smoothing RuneLite gives its overlays
				into.setRenderingHints(g.getRenderingHints());
				into.setFont(g.getFont());
				painter.paint(into, plugin.viewport().view(map.width, map.height), plugin.area(), plugin.plane(),
					plugin.playerLocation(), mouse, false, plugin.settings(), plugin.progress(), plugin.selection());
			}
			finally
			{
				into.dispose();
			}
			drawnAt = now;
			drawnTouches = touches;
		}
		g.drawImage(frame, map.x, map.y, null);
		box.draw(g, width, height);
		// no size claimed: RuneLite would make the whole view this overlay's, and Shift+right-click on the game beside
		// the box would offer to move it
		return null;
	}
}
