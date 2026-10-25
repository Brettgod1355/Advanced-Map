/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * Where the map is looking: the world point at the middle of the screen and how many screen pixels one tile
 * takes. Everything is in world coordinates (tiles, x east and y north); screen y grows downwards. A tile
 * {@code (x, y)} covers the world square from {@code (x, y)} to {@code (x + 1, y + 1)}. Thread safe: input
 * changes it on the Swing thread while the overlay draws it on the client thread.
 */
final class MapViewport
{
	/** Far enough out to see the whole surface on one screen. */
	static final double MIN_ZOOM = 0.25;
	static final double MAX_ZOOM = 24;

	/** An unchanging copy, for one frame's drawing. */
	static final class View
	{
		final double centerX;
		final double centerY;
		final double zoom;
		final int width;
		final int height;

		View(double centerX, double centerY, double zoom, int width, int height)
		{
			this.centerX = centerX;
			this.centerY = centerY;
			this.zoom = zoom;
			this.width = width;
			this.height = height;
		}

		double screenX(double worldX)
		{
			return width / 2.0 + (worldX - centerX) * zoom;
		}

		double screenY(double worldY)
		{
			return height / 2.0 - (worldY - centerY) * zoom;
		}

		double worldX(double screenX)
		{
			return centerX + (screenX - width / 2.0) / zoom;
		}

		double worldY(double screenY)
		{
			return centerY - (screenY - height / 2.0) / zoom;
		}
	}

	/** The world square the middle of the screen may move within: the map's edge can reach the middle, no further. */
	private final double minX;
	private final double minY;
	private final double maxX;
	private final double maxY;
	private double centerX;
	private double centerY;
	private double zoom = 4;

	/** A view free to move over the whole coordinate space. */
	MapViewport()
	{
		this(0, 0, 1 << 14, 1 << 14);
	}

	/** A view kept within a world square (tiles, the max ends exclusive). */
	MapViewport(int minX, int minY, int maxX, int maxY)
	{
		this.minX = minX;
		this.minY = minY;
		this.maxX = maxX;
		this.maxY = maxY;
		this.centerX = (minX + maxX) / 2.0;
		this.centerY = (minY + maxY) / 2.0;
	}

	synchronized View view(int width, int height)
	{
		return new View(centerX, centerY, zoom, width, height);
	}

	/** Puts the middle of a tile in the middle of the screen. */
	synchronized void centerOn(int tileX, int tileY)
	{
		centerX = tileX + 0.5;
		centerY = tileY + 0.5;
		keepInside();
	}

	synchronized void setZoom(double zoom)
	{
		this.zoom = clamp(zoom);
	}

	/** Moves the map with the mouse: a drag of (dx, dy) screen pixels. */
	synchronized void drag(int dx, int dy)
	{
		centerX -= dx / zoom;
		centerY += dy / zoom;
		keepInside();
	}

	/** Zooms by a factor, keeping the world point under the screen point (sx, sy) where it is. */
	synchronized void zoomAt(double factor, int sx, int sy, int width, int height)
	{
		View before = new View(centerX, centerY, zoom, width, height);
		double wx = before.worldX(sx);
		double wy = before.worldY(sy);
		zoom = clamp(zoom * factor);
		centerX = wx - (sx - width / 2.0) / zoom;
		centerY = wy + (sy - height / 2.0) / zoom;
		keepInside();
	}

	private void keepInside()
	{
		centerX = Math.max(minX, Math.min(maxX, centerX));
		centerY = Math.max(minY, Math.min(maxY, centerY));
	}

	private static double clamp(double zoom)
	{
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
	}
}
