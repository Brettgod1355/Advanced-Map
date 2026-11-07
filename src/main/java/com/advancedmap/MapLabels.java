/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
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

	/** How far from a {@code label-names.tsv} row's tile the game's label may be. */
	static final int RENAME_REACH = 16;
	/**
	 * The game's labels Advanced Map names otherwise, from {@code label-names.tsv}: x, y, the game's text, the name
	 * instead ("Lower level" over the Motherlode Mine; owner, 2026-11-07). In a data file, so a rename costs no tokens.
	 */
	private static final List<String[]> RENAMES = DataFiles.rows("label-names.tsv");

	private volatile List<Label> labels = Collections.emptyList();

	List<Label> all()
	{
		return labels;
	}

	void set(List<Label> loaded)
	{
		List<Label> named = new ArrayList<>(loaded.size());
		for (Label label : loaded)
		{
			named.add(renamed(label));
		}
		labels = Collections.unmodifiableList(named);
	}

	/** A label as Advanced Map names it: the list's name for the game's text near its tile, "|" breaking lines. */
	static Label renamed(Label label)
	{
		String text = String.join(" ", label.getLines());
		for (String[] row : RENAMES)
		{
			if (row[2].equalsIgnoreCase(text) && Math.abs(Integer.parseInt(row[0]) - label.getX()) <= RENAME_REACH
				&& Math.abs(Integer.parseInt(row[1]) - label.getY()) <= RENAME_REACH)
			{
				return new Label(Arrays.asList(row[3].split("\\|")), label.getX(), label.getY(), label.getSize(),
					label.getColor());
			}
		}
		return label;
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
