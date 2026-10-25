/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.bettermap;

/**
 * Where the map is looking: the world point at the middle of the screen and how many screen pixels one tile
 * takes. Everything is in world coordinates (tiles, x east and y north); screen y grows downwards. A tile
 * {@code (x, y)} covers the world square from {@code (x, y)} to {@code (x + 1, y + 1)}. Thread safe: input
 * changes it on the Swing thread while the overlay draws it on the client thread.
 */
final class MapViewport
{
	static final double MIN_ZOOM = 0.125;
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

	private double centerX;
	private double centerY;
	private double zoom = 4;

	synchronized View view(int width, int height)
	{
		return new View(centerX, centerY, zoom, width, height);
	}

	/** Puts the middle of a tile in the middle of the screen. */
	synchronized void centerOn(int tileX, int tileY)
	{
		centerX = tileX + 0.5;
		centerY = tileY + 0.5;
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
	}

	private static double clamp(double zoom)
	{
		return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
	}
}
