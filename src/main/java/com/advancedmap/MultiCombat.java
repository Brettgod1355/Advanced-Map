/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Where combat is multi-way (many may attack one), from {@code multi-combat.tsv}: outside the Wilderness the Multi
 * Lines plugin's data, in it the Wilderness Lines plugin's (see THIRD_PARTY_NOTICES.md). Drawn on every floor as a
 * red tint with a solid edge, so it reads apart from the dashed teleport-limit lines.
 */
@Slf4j
final class MultiCombat
{
	static final Color FILL = new Color(255, 64, 64, 46);
	static final Color EDGE = new Color(255, 90, 80, 200);
	private static final Stroke EDGE_STROKE = new BasicStroke(1.5f);

	/** One named area: its tiles as a shape in world tiles (a tile x, y covers x..x+1, y..y+1). */
	@Value
	static class Zone
	{
		String name;
		Area shape;
		Rectangle bounds;
	}

	private static volatile List<Zone> zones = Collections.emptyList();
	/** Every area as one shape, so areas side by side are drawn without a seam between them. */
	private static volatile Area all = new Area();

	private MultiCombat()
	{
	}

	static List<Zone> zones()
	{
		return zones;
	}

	/** Reads the areas from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream in = MultiCombat.class.getResourceAsStream("multi-combat.tsv"))
		{
			if (in != null)
			{
				List<Zone> read = parse(in);
				Area merged = new Area();
				for (Zone zone : read)
				{
					merged.add(zone.getShape());
				}
				zones = read;
				all = merged;
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the multi-combat areas", e);
		}
	}

	static List<Zone> parse(InputStream in) throws IOException
	{
		Map<String, Area> byName = new LinkedHashMap<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t");
			byName.computeIfAbsent(f[0], k -> new Area()).add(new Area(new Rectangle(Integer.parseInt(f[1]),
				Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]))));
		}
		List<Zone> out = new ArrayList<>();
		byName.forEach((name, shape) -> out.add(new Zone(name, shape, shape.getBounds())));
		return Collections.unmodifiableList(out);
	}

	/** True when a tile is in a multi-combat area. */
	static boolean contains(int x, int y)
	{
		for (Zone zone : zones)
		{
			if (zone.getBounds().contains(x, y) && zone.getShape().contains(x + 0.5, y + 0.5))
			{
				return true;
			}
		}
		return false;
	}

	/** Draws the areas in view, from {@link MapPainter#WILDERNESS_ZOOM} in. */
	static void draw(Graphics2D g, MapViewport.View view)
	{
		if (view.zoom < MapPainter.WILDERNESS_ZOOM)
		{
			return;
		}
		Rectangle seen = new Rectangle((int) Math.floor(view.worldX(0)) - 1, (int) Math.floor(view.worldY(view.height)) - 1,
			(int) Math.ceil(view.width / view.zoom) + 2, (int) Math.ceil(view.height / view.zoom) + 2);
		// world tiles to screen pixels: y grows north in the world and down on the screen
		AffineTransform toScreen = new AffineTransform(view.zoom, 0, 0, -view.zoom, view.screenX(0), view.screenY(0));
		Area shape = all;
		if (!shape.getBounds().intersects(seen))
		{
			return;
		}
		Shape onScreen = toScreen.createTransformedShape(shape);
		Stroke stroke = g.getStroke();
		g.setStroke(EDGE_STROKE);
		g.setColor(FILL);
		g.fill(onScreen);
		g.setColor(EDGE);
		g.draw(onScreen);
		g.setStroke(stroke);
	}
}
