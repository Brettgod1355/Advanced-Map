/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/**
 * The place names of the game's own world map, from the kingdoms down to single buildings, in world coordinates.
 * Which show depends on the zoom: kingdoms (and seas) only when far out, then towns, then smaller places close in.
 */
final class MapLabels
{
	/** The game's three label sizes. */
	static final int PLACE = 0;
	static final int TOWN = 1;
	static final int KINGDOM = 2;

	/** Kingdoms show while zoomed further out than this (screen pixels per tile). */
	static final double KINGDOM_ZOOM_MAX = 1.25;
	/** Towns show from this zoom in. */
	static final double TOWN_ZOOM = 0.75;
	/** Smaller places show from this zoom in. */
	static final double PLACE_ZOOM = 2.5;

	@Value
	static class Label
	{
		/** One string per line. */
		List<String> lines;
		int x;
		int y;
		int size;
		Color color;
	}

	private volatile List<Label> labels = Collections.emptyList();

	List<Label> all()
	{
		return labels;
	}

	void set(List<Label> loaded)
	{
		labels = Collections.unmodifiableList(loaded);
	}

	/** True when labels of a size show at a zoom. */
	static boolean shows(int size, double zoom)
	{
		switch (size)
		{
			case KINGDOM:
				return zoom < KINGDOM_ZOOM_MAX;
			case TOWN:
				return zoom >= TOWN_ZOOM;
			default:
				return zoom >= PLACE_ZOOM;
		}
	}
}
