/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * What other plugins can ask of the map, through RuneLite's {@code PluginMessage} on the event bus (Plugin Hub
 * plugins cannot share classes, so everything is plain JDK and RuneLite types). Namespace {@value #NAMESPACE}:
 * <ul>
 * <li>{@code layer}: draw a layer, replacing one with the same {@code id}. Keys: {@code id} (String, required;
 * prefix it with your plugin's name), {@code order} (Number, higher on top), {@code lines} (List of Maps with
 * {@code points}: List of WorldPoint, {@code color}: Color, {@code width}: Number, {@code dashed}: Boolean) and
 * {@code markers} (List of Maps with {@code point}: WorldPoint, {@code color}: Color, {@code shape}: "circle",
 * "square" or "diamond", {@code size}: Number, {@code label}: String).</li>
 * <li>{@code removeLayer}: {@code id}.</li>
 * <li>{@code show}: open the map on {@code point} (WorldPoint), optionally at {@code zoom} (screen pixels per
 * tile).</li>
 * </ul>
 * The map tells others when it opens or closes with a {@code state} message carrying {@code open} (Boolean).
 */
@Slf4j
class MapMessages
{
	static final String NAMESPACE = AdvancedMapConfig.GROUP;
	static final int MAX_LAYERS = 64;
	static final int MAX_POINTS = 50_000;

	static final Color DEFAULT_LINE = new Color(0x00E5FF);
	static final Color DEFAULT_MARKER = new Color(0xFFD23F);

	private final Map<String, MapLayer> layers = new ConcurrentHashMap<>();
	private final BiConsumer<WorldPoint, Double> show;

	MapMessages(BiConsumer<WorldPoint, Double> show)
	{
		this.show = show;
	}

	/** The layers to draw, bottom first. */
	List<MapLayer> layers()
	{
		List<MapLayer> sorted = new ArrayList<>(layers.values());
		sorted.sort(Comparator.comparingInt(MapLayer::getOrder).thenComparing(MapLayer::getId));
		return sorted;
	}

	/** Puts a layer of this plugin's own on the map, replacing one with the same id. */
	void put(MapLayer layer)
	{
		layers.put(layer.getId(), layer);
	}

	void remove(String id)
	{
		layers.remove(id);
	}

	void clear()
	{
		layers.clear();
	}

	void handle(String name, Map<String, Object> data)
	{
		Map<String, Object> in = data == null ? Collections.emptyMap() : data;
		switch (name)
		{
			case "layer":
				putLayer(in);
				break;
			case "removeLayer":
				if (in.get("id") instanceof String)
				{
					layers.remove(in.get("id"));
				}
				break;
			case "show":
				if (in.get("point") instanceof WorldPoint)
				{
					Object zoom = in.get("zoom");
					show.accept((WorldPoint) in.get("point"),
						zoom instanceof Number ? ((Number) zoom).doubleValue() : null);
				}
				break;
			default:
				break;
		}
	}

	private void putLayer(Map<String, Object> in)
	{
		Object id = in.get("id");
		if (!(id instanceof String) || ((String) id).isEmpty())
		{
			log.debug("ignoring a layer without an id");
			return;
		}
		if (!layers.containsKey(id) && layers.size() >= MAX_LAYERS)
		{
			log.debug("ignoring layer {}: {} layers already", id, MAX_LAYERS);
			return;
		}
		int[] budget = {MAX_POINTS};
		List<MapLayer.Line> lines = new ArrayList<>();
		for (Map<?, ?> line : maps(in.get("lines")))
		{
			List<WorldPoint> points = points(line.get("points"), budget);
			if (points.size() >= 2)
			{
				lines.add(new MapLayer.Line(points, color(line.get("color"), DEFAULT_LINE),
					(float) clamp(number(line.get("width"), 3), 1, 24), Boolean.TRUE.equals(line.get("dashed"))));
			}
		}
		List<MapLayer.Marker> markers = new ArrayList<>();
		for (Map<?, ?> marker : maps(in.get("markers")))
		{
			Object point = marker.get("point");
			if (point instanceof WorldPoint && budget[0]-- > 0)
			{
				Object label = marker.get("label");
				markers.add(new MapLayer.Marker((WorldPoint) point, color(marker.get("color"), DEFAULT_MARKER),
					shape(marker.get("shape")), (int) clamp(number(marker.get("size"), 10), 4, 48),
					label instanceof String ? (String) label : null));
			}
		}
		layers.put((String) id, new MapLayer((String) id, (int) number(in.get("order"), 0),
			Collections.unmodifiableList(lines), Collections.unmodifiableList(markers)));
	}

	private static List<Map<?, ?>> maps(Object value)
	{
		List<Map<?, ?>> out = new ArrayList<>();
		if (value instanceof Collection)
		{
			for (Object item : (Collection<?>) value)
			{
				if (item instanceof Map)
				{
					out.add((Map<?, ?>) item);
				}
			}
		}
		return out;
	}

	private static List<WorldPoint> points(Object value, int[] budget)
	{
		List<WorldPoint> out = new ArrayList<>();
		if (value instanceof Collection)
		{
			for (Object item : (Collection<?>) value)
			{
				if (item instanceof WorldPoint && budget[0]-- > 0)
				{
					out.add((WorldPoint) item);
				}
			}
		}
		return Collections.unmodifiableList(out);
	}

	private static Color color(Object value, Color fallback)
	{
		if (value instanceof Color)
		{
			return (Color) value;
		}
		if (value instanceof Integer)
		{
			// 0xRRGGBB, or 0xAARRGGBB when the top byte is set
			int rgb = (Integer) value;
			return new Color(rgb, rgb >>> 24 != 0);
		}
		return fallback;
	}

	private static MapLayer.Shape shape(Object value)
	{
		if (value instanceof String)
		{
			for (MapLayer.Shape shape : MapLayer.Shape.values())
			{
				if (shape.name().equals(((String) value).toUpperCase(Locale.ROOT)))
				{
					return shape;
				}
			}
		}
		return MapLayer.Shape.CIRCLE;
	}

	private static double number(Object value, double fallback)
	{
		return value instanceof Number ? ((Number) value).doubleValue() : fallback;
	}

	private static double clamp(double value, double min, double max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
