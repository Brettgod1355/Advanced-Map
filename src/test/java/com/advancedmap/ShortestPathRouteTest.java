/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ShortestPathRouteTest
{
	private final MapMessages layers = new MapMessages((point, zoom) -> { });
	private final ShortestPathRoute route = new ShortestPathRoute();
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint VARROCK = new WorldPoint(3212, 3424, 0);

	private static Map<String, Object> message(Object... keysAndValues)
	{
		Map<String, Object> out = new HashMap<>();
		out.put("id", ShortestPathRoute.ID);
		for (int i = 0; i < keysAndValues.length; i += 2)
		{
			out.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return out;
	}

	@Test
	public void aShownRouteIsAskedForThenDrawn()
	{
		List<Object> targets = Collections.singletonList(VARROCK);
		Map<String, Object> query = route.onMessage("currentTarget", message("set", true, "target", targets), LUMBRIDGE,
			layers);
		assertNotNull(query);
		assertEquals(ShortestPathRoute.ID, query.get("id"));
		assertEquals(LUMBRIDGE, query.get("start"));
		assertEquals(targets, query.get("target"));

		route.onMessage("result", message("path", Arrays.asList(LUMBRIDGE, new WorldPoint(3223, 3219, 0),
			new WorldPoint(3224, 3220, 0))), LUMBRIDGE, layers);
		assertEquals(1, layers.layers().size());
		assertEquals(ShortestPathRoute.LAYER, layers.layers().get(0).getId());
		assertEquals(3, layers.layers().get(0).getLines().get(0).getPoints().size());

		// asked again with nothing changed: no new query
		assertNull(route.onMessage("currentTarget", message("set", true, "target", targets), LUMBRIDGE, layers));
		// walked away: asked again from where the player is
		WorldPoint walked = new WorldPoint(3222, 3240, 0);
		Map<String, Object> again = route.onMessage("currentTarget", message("set", true, "target", targets), walked,
			layers);
		assertNotNull(again);
		assertEquals(walked, again.get("start"));
	}

	@Test
	public void aClearedRouteLeavesTheMap()
	{
		route.onMessage("currentTarget", message("set", true, "target", VARROCK), LUMBRIDGE, layers);
		route.onMessage("result", message("path", Arrays.asList(LUMBRIDGE, new WorldPoint(3223, 3218, 0))), LUMBRIDGE,
			layers);
		assertEquals(1, layers.layers().size());
		route.onMessage("currentTarget", message("set", false), LUMBRIDGE, layers);
		assertTrue(layers.layers().isEmpty());
	}

	@Test
	public void otherPluginsAnswersAreLeftAlone()
	{
		Map<String, Object> theirs = new HashMap<>();
		theirs.put("id", "someone-else");
		theirs.put("set", true);
		theirs.put("target", VARROCK);
		assertNull(route.onMessage("currentTarget", theirs, LUMBRIDGE, layers));
		assertNull(route.onMessage("currentTarget", null, LUMBRIDGE, layers));
	}

	@Test
	public void aTeleportBreaksTheLineAndIsMarked()
	{
		// walk two tiles, teleport to Varrock, walk one
		MapLayer layer = ShortestPathRoute.layer(Arrays.asList(LUMBRIDGE, new WorldPoint(3223, 3218, 0),
			new WorldPoint(3224, 3218, 0), VARROCK, new WorldPoint(3213, 3424, 0)));
		assertEquals(2, layer.getLines().size());
		assertEquals(3, layer.getLines().get(0).getPoints().size());
		assertEquals(2, layer.getLines().get(1).getPoints().size());
		// where the jump leaves and lands, and the end of the route
		assertEquals(3, layer.getMarkers().size());
		assertTrue(ShortestPathRoute.isJump(new WorldPoint(1, 1, 0), new WorldPoint(1, 1, 1)));
	}
}
