/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/**
 * The route Shortest Path is showing, drawn on the map. Shortest Path keeps its route to itself, but answers two
 * plugin messages (read at its Hub pin {@code 50f3043}, {@code docs/Cross-plugin-communication.md}): {@code getTarget}
 * is answered with {@code currentTarget} (whether a route is shown, and its targets), and {@code query} with
 * {@code result} (the path, tile by tile). So the map asks what is shown every few seconds while it is open, and asks
 * for the path when the targets change or the player has walked away from where it was last asked. GPS answers
 * neither, so its routes cannot be drawn here.
 */
class ShortestPathRoute
{
	static final String NAMESPACE = "shortestpath";
	/** Marks the questions this plugin asks, so other plugins' answers are left alone. */
	static final String ID = "advancedmap";
	static final String LAYER = "advancedmap:route";
	/** How far the player walks before the path is asked for again from where they are. */
	static final int REQUERY_DISTANCE = 15;
	static final Color LINE = new Color(0x00E5FF);
	static final Color JUMP = new Color(0xFFD23F);

	private Object targets;
	private WorldPoint askedFrom;
	private boolean shown;

	/** The data of a {@code getTarget} message. */
	static Map<String, Object> getTarget()
	{
		return Collections.singletonMap("id", ID);
	}

	/**
	 * Handles a message from Shortest Path. Returns the data of a {@code query} to send, or null; the route layer is
	 * put on or taken off the map.
	 */
	Map<String, Object> onMessage(String name, Map<String, Object> data, WorldPoint player, MapMessages layers)
	{
		if (data == null || !ID.equals(data.get("id")))
		{
			return null;
		}
		if ("currentTarget".equals(name))
		{
			if (!Boolean.TRUE.equals(data.get("set")))
			{
				clear(layers);
				return null;
			}
			Object target = data.get("target");
			Object start = player != null ? player : data.get("start");
			boolean moved = player != null && askedFrom != null && (player.getPlane() != askedFrom.getPlane()
				|| player.distanceTo2D(askedFrom) >= REQUERY_DISTANCE);
			if (target == null || start == null || Objects.equals(target, targets) && !moved && shown)
			{
				return null;
			}
			targets = target;
			askedFrom = player;
			Map<String, Object> query = new HashMap<>();
			query.put("id", ID);
			query.put("start", start);
			query.put("target", target);
			return query;
		}
		if ("result".equals(name))
		{
			Object path = data.get("path");
			List<WorldPoint> points = new ArrayList<>();
			if (path instanceof List)
			{
				for (Object point : (List<?>) path)
				{
					if (point instanceof WorldPoint)
					{
						points.add((WorldPoint) point);
					}
				}
			}
			if (points.size() < 2)
			{
				clear(layers);
				return null;
			}
			layers.put(layer(points));
			shown = true;
		}
		return null;
	}

	/** Takes the route off the map, and forgets it so it is asked for again. */
	void clear(MapMessages layers)
	{
		if (shown)
		{
			layers.remove(LAYER);
		}
		shown = false;
		targets = null;
		askedFrom = null;
	}

	/**
	 * The route as lines: one line per stretch walked tile by tile, broken where the path jumps (a teleport, a boat, a
	 * ladder to another floor), with a mark where each jump leaves and lands.
	 */
	static MapLayer layer(List<WorldPoint> path)
	{
		List<MapLayer.Line> lines = new ArrayList<>();
		List<MapLayer.Marker> marks = new ArrayList<>();
		List<WorldPoint> stretch = new ArrayList<>();
		WorldPoint last = null;
		for (WorldPoint point : path)
		{
			if (last != null && isJump(last, point))
			{
				addLine(lines, stretch);
				stretch = new ArrayList<>();
				marks.add(new MapLayer.Marker(last, JUMP, MapLayer.Shape.DIAMOND, 9, null));
				marks.add(new MapLayer.Marker(point, JUMP, MapLayer.Shape.DIAMOND, 9, null));
			}
			stretch.add(point);
			last = point;
		}
		addLine(lines, stretch);
		marks.add(new MapLayer.Marker(last, LINE, MapLayer.Shape.CIRCLE, 11, null));
		return new MapLayer(LAYER, 100, Collections.unmodifiableList(lines), Collections.unmodifiableList(marks));
	}

	private static void addLine(List<MapLayer.Line> lines, List<WorldPoint> stretch)
	{
		if (stretch.size() >= 2)
		{
			lines.add(new MapLayer.Line(Collections.unmodifiableList(stretch), LINE, 3, false));
		}
	}

	/** True when two steps of a path are not neighboring tiles on one floor. */
	static boolean isJump(WorldPoint from, WorldPoint to)
	{
		return from.getPlane() != to.getPlane() || Math.abs(from.getX() - to.getX()) > 1
			|| Math.abs(from.getY() - to.getY()) > 1;
	}
}
