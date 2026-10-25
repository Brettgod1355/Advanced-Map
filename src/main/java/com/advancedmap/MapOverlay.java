/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The map over the whole game view, while it is open there rather than in its own window. */
class MapOverlay extends Overlay
{
	private final Client client;
	private final AdvancedMapPlugin plugin;
	private final MapPainter painter;

	MapOverlay(Client client, AdvancedMapPlugin plugin, MapPainter painter)
	{
		this.client = client;
		this.plugin = plugin;
		this.painter = painter;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
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
			return null;
		}
		int width = client.getCanvasWidth();
		int height = client.getCanvasHeight();
		Point mouse = client.getMouseCanvasPosition();
		painter.paint(g, plugin.viewport().view(width, height), plugin.area(), plugin.playerLocation(),
			mouse == null ? null : new java.awt.Point(mouse.getX(), mouse.getY()), false);
		return new Dimension(width, height);
	}
}
