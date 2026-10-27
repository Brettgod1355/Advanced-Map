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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Where a transport goes: spirit trees, gnome gliders, fairy rings, balloons, carpets, mushtrees, quetzals, minecarts,
 * canoes, boats, ships and charter ships, from {@code transports.tsv} (derived from Shortest Path's transport lists),
 * and what each transport icon is called, from {@code transport-points.tsv} (transcribed from RuneLite's world map
 * plugin); see THIRD_PARTY_NOTICES.md. A network's every boarding spot reaches every one of its destinations; the
 * others list each spot's own.
 */
@Slf4j
final class Transports
{
	/** How far from a transport icon its boarding spot may be. */
	static final int REACH = 12;
	/** How far from an icon a network's stop may be to give the icon its name. */
	static final int NAME_REACH = 6;
	static final String LAYER = "advancedmap:transport";
	static final Color LINE = new Color(0xFFD23F);
	/** A spirit tree a player plants shows the game's farming patch icon, not a transport one. */
	static final String SPIRIT_TREE = "Spirit tree";

	/** RuneLite's list names the Keldagrim carts but not where they go: the minecart station in Keldagrim. */
	private static final String TO_KELDAGRIM = "Minecart to Keldagrim";
	private static final String KELDAGRIM = "Keldagrim Minecart System";

	/** A menu key before a stop's name: "1: Tree Gnome Village", "A: Port Piscarilius", "2. Verdant Valley". */
	private static final Pattern MENU_KEY = Pattern.compile("^[0-9A-Z][:.] ");
	/** The spaces of a fairy ring code: "D K R". */
	private static final Pattern CODE_SPACE = Pattern.compile("(?<=\\b[A-Z]) (?=[A-Z]\\b)");

	@Value
	static class Destination
	{
		WorldPoint point;
		String kind;
		String name;
		/** The fare in coins ("2500"), or empty when free or not known. */
		String fare;
		/** What the trip needs (quests, levels, things to bring), or empty. */
		String needs;

		Destination(WorldPoint point, String kind, String name)
		{
			this(point, kind, name, "", "");
		}

		Destination(WorldPoint point, String kind, String name, String fare, String needs)
		{
			this.point = point;
			this.kind = kind;
			this.name = name;
			this.fare = fare;
			this.needs = needs;
		}

		String label()
		{
			return name.isEmpty() ? kind : name;
		}

		/** The fare as shown: "2,500 gp", or empty. */
		String fareText()
		{
			try
			{
				return fare.isEmpty() ? "" : String.format("%,d gp", Long.parseLong(fare));
			}
			catch (NumberFormatException e)
			{
				return fare;
			}
		}
	}

	/** A trip's fare and needs from {@code transport-costs.tsv}: from a boarding tile (null for any) to a place. */
	@Value
	private static class Cost
	{
		WorldPoint from;
		WorldPoint to;
		String fare;
		String needs;
	}

	/** A place to board: a network's spot, a boat's or ship's spot, or a RuneLite transport icon that names its stop. */
	@Value
	private static class Station
	{
		WorldPoint point;
		String kind;
		/** Null for a network's spot: it reaches its kind's every destination. */
		List<Destination> destinations;
	}

	/** A transport icon's name in RuneLite's list. */
	@Value
	private static class Named
	{
		WorldPoint point;
		String name;
	}

	private static final class Data
	{
		final List<Station> stations = new ArrayList<>();
		final Map<String, List<Destination>> networks = new LinkedHashMap<>();
		final List<Named> names = new ArrayList<>();
		final List<Cost> costs = new ArrayList<>();
	}

	private static volatile Data data = new Data();

	private Transports()
	{
	}

	/** Reads the lists from the plugin's resources. Called once at start-up, off the client thread. */
	static void load()
	{
		try (InputStream transports = Transports.class.getResourceAsStream("transports.tsv");
			InputStream points = Transports.class.getResourceAsStream("transport-points.tsv");
			InputStream costs = Transports.class.getResourceAsStream("transport-costs.tsv"))
		{
			if (transports != null && points != null)
			{
				Data out = new Data();
				if (costs != null)
				{
					parseCosts(costs, out);
				}
				parseTransports(transports, out);
				parsePoints(points, out);
				data = out;
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the transports", e);
		}
	}

	private static List<String[]> rows(InputStream in) throws IOException
	{
		List<String[]> rows = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (!line.isEmpty() && !line.startsWith("#"))
			{
				rows.add(line.split("\t", -1));
			}
		}
		return rows;
	}

	/** A tile of the lists; one in Prifddinas on the surface, where the map draws the city. */
	private static WorldPoint point(String[] f, int at)
	{
		return Prifddinas.toSurface(new WorldPoint(Integer.parseInt(f[at]), Integer.parseInt(f[at + 1]),
			Integer.parseInt(f[at + 2])));
	}

	private static WorldPoint spacedPoint(String text)
	{
		if (text.trim().isEmpty())
		{
			return null;
		}
		String[] f = text.trim().split(" ");
		return Prifddinas.toSurface(new WorldPoint(Integer.parseInt(f[0]), Integer.parseInt(f[1]),
			Integer.parseInt(f[2])));
	}

	private static void parseCosts(InputStream in, Data out) throws IOException
	{
		for (String[] f : rows(in))
		{
			out.costs.add(new Cost(spacedPoint(f[1]), spacedPoint(f[2]), f.length > 3 ? f[3] : "",
				f.length > 4 ? f[4] : ""));
		}
	}

	/** A trip's fare and needs: boarding near {@code from} (or anywhere, for a network) to {@code to}. */
	private static Destination priced(Data data, WorldPoint from, WorldPoint to, String kind, String name)
	{
		Cost best = null;
		for (Cost cost : data.costs)
		{
			if (distance(cost.to, to.getX(), to.getY(), to.getPlane()) > 1)
			{
				continue;
			}
			if (cost.from == null || from != null && distance(cost.from, from.getX(), from.getY(), from.getPlane())
				<= REACH)
			{
				best = cost;
				if (cost.from != null)
				{
					break;
				}
			}
		}
		return best == null ? new Destination(to, kind, name)
			: new Destination(to, kind, name, best.fare, best.needs);
	}

	private static void parseTransports(InputStream in, Data out) throws IOException
	{
		// a boat's or ship's spot sails to each of its own places: one station per spot and kind
		Map<String, List<Destination>> pairs = new LinkedHashMap<>();
		Map<String, WorldPoint> pairSpots = new LinkedHashMap<>();
		for (String[] f : rows(in))
		{
			String kind = f[1];
			WorldPoint first = point(f, 2);
			switch (f[0])
			{
				case "O":
					out.stations.add(new Station(first, kind, null));
					break;
				case "D":
					out.networks.computeIfAbsent(kind, k -> new ArrayList<>()).add(priced(out, null, first, kind,
						clean(f[5])));
					break;
				case "P":
					String key = kind + "@" + first;
					pairSpots.put(key, first);
					pairs.computeIfAbsent(key, k -> new ArrayList<>()).add(priced(out, first, point(f, 5), kind,
						clean(f[8])));
					break;
				default:
					break;
			}
		}
		pairs.forEach((key, to) -> out.stations.add(new Station(pairSpots.get(key), to.get(0).getKind(),
			Collections.unmodifiableList(to))));
	}

	private static void parsePoints(InputStream in, Data out) throws IOException
	{
		List<String[]> rows = rows(in);
		WorldPoint keldagrim = null;
		for (String[] f : rows)
		{
			if (KELDAGRIM.equals(f[0]))
			{
				keldagrim = point(f, 1);
			}
		}
		List<Destination> fromKeldagrim = new ArrayList<>();
		for (String[] f : rows)
		{
			String name = f[0];
			WorldPoint at = point(f, 1);
			out.names.add(new Named(at, name));
			WorldPoint to = f.length >= 7 ? point(f, 4) : TO_KELDAGRIM.equals(name) ? keldagrim : null;
			if (to != null)
			{
				int split = name.indexOf(" to ");
				String place = split < 0 ? "" : name.substring(split + 4);
				out.stations.add(new Station(at, name, Collections.singletonList(priced(out, at, to, name, place))));
			}
			if (TO_KELDAGRIM.equals(name))
			{
				fromKeldagrim.add(new Destination(at, KELDAGRIM, ""));
			}
		}
		if (keldagrim != null && !fromKeldagrim.isEmpty())
		{
			out.stations.add(new Station(keldagrim, KELDAGRIM, Collections.unmodifiableList(fromKeldagrim)));
		}
	}

	/** A stop's name as shown: without its menu key or trailing stop, a fairy ring code without spaces. */
	static String clean(String name)
	{
		String out = MENU_KEY.matcher(name.trim()).replaceFirst("");
		if (out.endsWith("."))
		{
			out = out.substring(0, out.length() - 1);
		}
		return CODE_SPACE.matcher(out).replaceAll("").replace(" - ", ", ");
	}

	/**
	 * Where the transport at or near a map icon goes: the one whose boarding spot is nearest, within {@link #REACH}
	 * tiles. A network's every other stop, or every place a boat, ship, canoe or carpet there sails to. Empty when no
	 * transport is near.
	 */
	static List<Destination> from(int x, int y, int plane)
	{
		return from(x, y, plane, null);
	}

	/** As {@link #from(int, int, int)}, only one kind of transport when {@code onlyKind} is set. */
	static List<Destination> from(int x, int y, int plane, String onlyKind)
	{
		Data known = data;
		Station best = null;
		int bestDistance = REACH + 1;
		for (Station station : known.stations)
		{
			int distance = distance(station.point, x, y, plane);
			if (distance < bestDistance && (onlyKind == null || onlyKind.equals(station.kind)))
			{
				best = station;
				bestDistance = distance;
			}
		}
		if (best == null)
		{
			return Collections.emptyList();
		}
		if (best.destinations != null)
		{
			return best.destinations;
		}
		List<Destination> out = new ArrayList<>();
		for (Destination to : known.networks.getOrDefault(best.kind, Collections.emptyList()))
		{
			// not back to the stop clicked
			if (distance(to.point, best.point.getX(), best.point.getY(), best.point.getPlane()) > NAME_REACH
				&& !out.contains(to))
			{
				out.add(to);
			}
		}
		return out;
	}

	/**
	 * The name of the network stop a map icon stands for, for a label beside it: a fairy ring's code ("DKR"), or the
	 * place of a spirit tree, balloon, quetzal, glider, mushtree or cart. Null for an icon that is no such stop.
	 */
	static String stopName(MapSpot spot)
	{
		Destination stop = stopAt(spot);
		return stop == null ? null : stop.name;
	}

	/** A stop's name with its kind, for an info box: "Fairy ring DKR", "Spirit tree: Grand Exchange"; or null. */
	static String stopTitle(MapSpot spot)
	{
		Destination stop = stopAt(spot);
		if (stop == null)
		{
			return null;
		}
		return "Fairy ring".equals(stop.kind) ? "Fairy ring " + stop.name : stop.kind + ": " + stop.name;
	}

	/** What RuneLite's list calls a transport icon ("Minecart to Keldagrim"), or null. */
	static String iconName(MapSpot spot)
	{
		if (spot.getGroup() != IconGroup.TRANSPORTATION)
		{
			return null;
		}
		Named best = null;
		int bestDistance = NAME_REACH + 1;
		for (Named named : data.names)
		{
			int distance = distance(named.point, spot.getX(), spot.getY(), spot.getPlane());
			if (distance < bestDistance)
			{
				best = named;
				bestDistance = distance;
			}
		}
		return best == null ? null : best.name;
	}

	/** The kind of transport an icon may be: any for a transport icon, a spirit tree for a farming patch, else none. */
	static boolean mayBeTransport(MapSpot spot)
	{
		return spot.getGroup() == IconGroup.TRANSPORTATION || spot.getGroup() == IconGroup.FARMING;
	}

	/** Where a transport icon leads; a farming patch only as a spirit tree's, never a nearby boat's. */
	static List<Destination> from(MapSpot spot)
	{
		if (!mayBeTransport(spot))
		{
			return Collections.emptyList();
		}
		return from(spot.getX(), spot.getY(), spot.getPlane(),
			spot.getGroup() == IconGroup.FARMING ? SPIRIT_TREE : null);
	}

	private static Destination stopAt(MapSpot spot)
	{
		if (!mayBeTransport(spot))
		{
			return null;
		}
		String onlyKind = spot.getGroup() == IconGroup.FARMING ? SPIRIT_TREE : null;
		Destination best = null;
		int bestDistance = NAME_REACH + 1;
		for (List<Destination> network : data.networks.values())
		{
			for (Destination stop : network)
			{
				int distance = distance(stop.point, spot.getX(), spot.getY(), spot.getPlane());
				if (distance < bestDistance && !stop.name.isEmpty() && (onlyKind == null || onlyKind.equals(stop.kind)))
				{
					best = stop;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	/** Tiles between two spots, on one floor; a floor apart counts as out of reach. */
	private static int distance(WorldPoint point, int x, int y, int plane)
	{
		return point.getPlane() != plane ? Integer.MAX_VALUE
			: Math.max(Math.abs(point.getX() - x), Math.abs(point.getY() - y));
	}

	/**
	 * Where a transport icon leads when that is one place on the other map (the Grand Exchange's cart to Keldagrim,
	 * underground): the map opens there instead of drawing a line. Null otherwise.
	 */
	static WorldPoint elsewhere(MapSpot spot)
	{
		List<Destination> to = from(spot);
		if (to.size() != 1 || MapArea.of(to.get(0).point.getY()) == MapArea.of(spot.getY()))
		{
			return null;
		}
		return to.get(0).point;
	}

	/**
	 * Lines from a transport to each place it goes on the same map, each place marked with its name. A place on the
	 * other map (Keldagrim, a player's house) gets no line: it would run off the map's edge. Prifddinas is on the
	 * surface ({@link Prifddinas}).
	 */
	static MapLayer layer(WorldPoint from, List<Destination> destinations)
	{
		List<MapLayer.Line> lines = new ArrayList<>();
		List<MapLayer.Marker> marks = new ArrayList<>();
		for (Destination to : destinations)
		{
			if (MapArea.of(to.point.getY()) != MapArea.of(from.getY()))
			{
				continue;
			}
			lines.add(new MapLayer.Line(Arrays.asList(from, to.point), LINE, 2, true));
			marks.add(new MapLayer.Marker(to.point, LINE, MapLayer.Shape.CIRCLE, 12, to.label()));
		}
		return new MapLayer(LAYER, 90, Collections.unmodifiableList(lines), Collections.unmodifiableList(marks));
	}
}
