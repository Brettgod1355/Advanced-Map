/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.bettermap;

import java.awt.Color;
import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * Lines and markers another plugin asked the map to draw, all in world coordinates. A layer is replaced as a
 * whole; layers with a higher {@code order} are drawn on top.
 */
@Value
class MapLayer
{
	String id;
	int order;
	List<Line> lines;
	List<Marker> markers;

	/** A line through world points, joined in order. */
	@Value
	static class Line
	{
		List<WorldPoint> points;
		Color color;
		/** Thickness in screen pixels, whatever the zoom. */
		float width;
		boolean dashed;
	}

	/** A dot on a world point, with an optional label beside it. */
	@Value
	static class Marker
	{
		WorldPoint point;
		Color color;
		Shape shape;
		/** Size in screen pixels, whatever the zoom. */
		int size;
		String label;
	}

	enum Shape
	{
		CIRCLE,
		SQUARE,
		DIAMOND
	}
}
