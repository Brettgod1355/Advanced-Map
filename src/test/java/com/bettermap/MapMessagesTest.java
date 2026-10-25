/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.bettermap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapMessagesTest
{
	private final List<Object[]> shown = new ArrayList<>();
	private final MapMessages messages = new MapMessages((point, zoom) -> shown.add(new Object[]{point, zoom}));

	private static Map<String, Object> map(Object... keysAndValues)
	{
		Map<String, Object> out = new HashMap<>();
		for (int i = 0; i < keysAndValues.length; i += 2)
		{
			out.put((String) keysAndValues[i], keysAndValues[i + 1]);
		}
		return out;
	}

	@Test
	public void aLayerKeepsItsLinesAndMarkersInWorldCoordinates()
	{
		WorldPoint a = new WorldPoint(3222, 3218, 0);
		WorldPoint b = new WorldPoint(3230, 3240, 0);
		messages.handle("layer", map(
			"id", "betterroutes:route",
			"order", 2,
			"lines", Collections.singletonList(map("points", Arrays.asList(a, b), "color", Color.RED, "width", 5,
				"dashed", true)),
			"markers", Collections.singletonList(map("point", b, "shape", "diamond", "label", "Varrock",
				"color", 0x00FF00))));

		List<MapLayer> layers = messages.layers();
		assertEquals(1, layers.size());
		MapLayer layer = layers.get(0);
		assertEquals("betterroutes:route", layer.getId());
		assertEquals(2, layer.getOrder());
		MapLayer.Line line = layer.getLines().get(0);
		assertEquals(Arrays.asList(a, b), line.getPoints());
		assertEquals(Color.RED, line.getColor());
		assertEquals(5f, line.getWidth(), 0f);
		assertTrue(line.isDashed());
		MapLayer.Marker marker = layer.getMarkers().get(0);
		assertEquals(b, marker.getPoint());
		assertEquals(MapLayer.Shape.DIAMOND, marker.getShape());
		assertEquals("Varrock", marker.getLabel());
		// a plain 0xRRGGBB number is an opaque color
		assertEquals(new Color(0, 255, 0), marker.getColor());
	}

	@Test
	public void aLayerWithTheSameIdIsReplacedAndRemoveTakesItAway()
	{
		messages.handle("layer", map("id", "x", "markers",
			Collections.singletonList(map("point", new WorldPoint(1, 1, 0)))));
		messages.handle("layer", map("id", "x", "markers", Collections.emptyList()));
		assertEquals(1, messages.layers().size());
		assertTrue(messages.layers().get(0).getMarkers().isEmpty());
		messages.handle("removeLayer", map("id", "x"));
		assertTrue(messages.layers().isEmpty());
	}

	@Test
	public void layersAreDrawnInOrder()
	{
		messages.handle("layer", map("id", "top", "order", 5));
		messages.handle("layer", map("id", "bottom", "order", -1));
		messages.handle("layer", map("id", "middle"));
		assertEquals("bottom", messages.layers().get(0).getId());
		assertEquals("middle", messages.layers().get(1).getId());
		assertEquals("top", messages.layers().get(2).getId());
	}

	@Test
	public void malformedInputIsIgnoredNotThrown()
	{
		messages.handle("layer", null);
		messages.handle("layer", map("order", 1));
		messages.handle("layer", map("id", "bad", "lines", "not a list", "markers",
			Arrays.asList("nope", map("point", "not a point"))));
		messages.handle("layer", map("id", "one point", "lines",
			Collections.singletonList(map("points", Collections.singletonList(new WorldPoint(1, 1, 0)))))) ;
		messages.handle("unknown", map());
		assertEquals(2, messages.layers().size());
		for (MapLayer layer : messages.layers())
		{
			assertTrue(layer.getLines().isEmpty());
			assertTrue(layer.getMarkers().isEmpty());
		}
	}

	@Test
	public void showOpensTheMapOnAPoint()
	{
		WorldPoint point = new WorldPoint(2964, 3378, 0);
		messages.handle("show", map("point", point, "zoom", 8));
		messages.handle("show", map("point", point));
		messages.handle("show", map());
		assertEquals(2, shown.size());
		assertEquals(point, shown.get(0)[0]);
		assertEquals(8.0, (Double) shown.get(0)[1], 0);
		assertNull(shown.get(1)[1]);
	}

	@Test
	public void theNumberOfLayersIsCapped()
	{
		for (int i = 0; i < MapMessages.MAX_LAYERS + 5; i++)
		{
			messages.handle("layer", map("id", "layer " + i));
		}
		assertEquals(MapMessages.MAX_LAYERS, messages.layers().size());
		messages.clear();
		assertFalse(messages.layers().iterator().hasNext());
	}
}
