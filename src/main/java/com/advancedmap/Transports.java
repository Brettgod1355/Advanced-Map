/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
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
import net.runelite.api.coords.WorldPoint;

/**
 * Where a transport goes: spirit trees, gnome gliders, fairy rings, balloons, carpets, mushtrees, quetzals, minecarts,
 * canoes, boats, ships and charter ships, from {@code transports.tsv} (derived from Shortest Path's transport lists;
 * see THIRD_PARTY_NOTICES.md). A network's every boarding spot reaches every one of its destinations; the others list
 * each spot's own.
 */
@Slf4j
final class Transports
{
	/** How far from a transport icon its boarding spot may be. */
	static final int REACH = 12;
	static final String LAYER = "advancedmap:transport";
	static final Color LINE = new Color(0xFFD23F);
	/** A spirit tree a player plants shows the game's farming patch icon, not a transport one. */
	static final String SPIRIT_TREE = "Spirit tree";

	@Value
	static class Destination
	{
		WorldPoint point;
		String kind;
		String name;

		String label()
		{
			return name.isEmpty() ? kind : name;
		}
	}

	private static final class Data
	{
		final Map<String, List<WorldPoint>> networkSpots = new LinkedHashMap<>();
		final Map<String, List<Destination>> networkDestinations = new LinkedHashMap<>();
		final List<WorldPoint> pairFrom = new ArrayList<>();
		final List<Destination> pairTo = new ArrayList<>();
	}

	private static volatile Data data = new Data();

	private Transports()
	{
	}

	/** Reads the list from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream in = Transports.class.getResourceAsStream("transports.tsv"))
		{
			if (in != null)
			{
				data = parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the transports", e);
		}
	}

	private static Data parse(InputStream in) throws IOException
	{
		Data out = new Data();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			String kind = f[1];
			WorldPoint first = new WorldPoint(Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]));
			switch (f[0])
			{
				case "O":
					out.networkSpots.computeIfAbsent(kind, k -> new ArrayList<>()).add(first);
					break;
				case "D":
					out.networkDestinations.computeIfAbsent(kind, k -> new ArrayList<>())
						.add(new Destination(first, kind, f[5]));
					break;
				case "P":
					out.pairFrom.add(first);
					out.pairTo.add(new Destination(new WorldPoint(Integer.parseInt(f[5]), Integer.parseInt(f[6]),
						Integer.parseInt(f[7])), kind, f[8]));
					break;
				default:
					break;
			}
		}
		return out;
	}

	/**
	 * Where the transport at or near a map icon goes: every destination of a network whose boarding spot is within
	 * {@link #REACH} tiles (but not back to the spot clicked), and every place a nearby boat, ship, canoe or carpet
	 * sails to. Empty when no transport is near.
	 */
	static List<Destination> from(int x, int y, int plane)
	{
		return from(x, y, plane, null);
	}

	/** As {@link #from(int, int, int)}, only one kind of network when {@code onlyKind} is set. */
	static List<Destination> from(int x, int y, int plane, String onlyKind)
	{
		Data known = data;
		List<Destination> out = new ArrayList<>();
		known.networkSpots.forEach((kind, spots) ->
		{
			if (onlyKind != null && !onlyKind.equals(kind))
			{
				return;
			}
			for (WorldPoint spot : spots)
			{
				if (near(spot, x, y, plane))
				{
					for (Destination to : known.networkDestinations.getOrDefault(kind, Collections.emptyList()))
					{
						if (!near(to.point, x, y, plane) && !out.contains(to))
						{
							out.add(to);
						}
					}
					return;
				}
			}
		});
		for (int i = 0; i < known.pairFrom.size() && onlyKind == null; i++)
		{
			Destination to = known.pairTo.get(i);
			if (near(known.pairFrom.get(i), x, y, plane) && !out.contains(to))
			{
				out.add(to);
			}
		}
		return out;
	}

	private static boolean near(WorldPoint point, int x, int y, int plane)
	{
		return point.getPlane() == plane && Math.abs(point.getX() - x) <= REACH && Math.abs(point.getY() - y) <= REACH;
	}

	/** Lines from a transport to each place it goes, each place marked with its name. */
	static MapLayer layer(WorldPoint from, List<Destination> destinations)
	{
		List<MapLayer.Line> lines = new ArrayList<>();
		List<MapLayer.Marker> marks = new ArrayList<>();
		for (Destination to : destinations)
		{
			lines.add(new MapLayer.Line(java.util.Arrays.asList(from, to.point), LINE, 2, true));
			marks.add(new MapLayer.Marker(to.point, LINE, MapLayer.Shape.CIRCLE, 12, to.label()));
		}
		return new MapLayer(LAYER, 90, Collections.unmodifiableList(lines), Collections.unmodifiableList(marks));
	}
}
