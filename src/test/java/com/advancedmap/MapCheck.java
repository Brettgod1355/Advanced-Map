/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;

/**
 * The development client's map check (never shipped: the test folder is not built into the plugin): the dungeon
 * icons whose data is not finished, as lists the owner clicks through in game. Three lists: an icon a click on which
 * leads nowhere; a way into a dungeon that has no name at all; a way into one whose name is only the nearest of the
 * game's map labels, which may be the wrong one. Each list is also written to a file with a column to fill in.
 */
final class MapCheck
{
	enum Kind
	{
		NO_LINK("Dungeon icons that lead nowhere", "no-link"),
		NO_NAME("Dungeons with no name", "no-name"),
		LABEL_NAME("Dungeons named by the nearest map label", "label-name");

		final String title;
		final String file;

		Kind(String title, String file)
		{
			this.title = title;
			this.file = file;
		}
	}

	/** A dungeon's name as the map gives it: from all its sources, or from all but the game's map labels. */
	interface Names
	{
		String name(MapSpot spot, WorldPoint to, boolean withLabels);
	}

	/** One icon to look at: where it is, where it leads (null: nowhere), the name the map gives it (null: none). */
	@Value
	static class Found
	{
		Kind kind;
		MapSpot spot;
		WorldPoint to;
		String name;

		WorldPoint point()
		{
			return new WorldPoint(spot.getX(), spot.getY(), spot.getPlane());
		}

		String key()
		{
			return spot.getX() + "\t" + spot.getY() + "\t" + spot.getPlane();
		}
	}

	private MapCheck()
	{
	}

	/**
	 * Every dungeon icon among {@code spots} that belongs on a list, nearest {@code from} first. An icon that leads
	 * nowhere is on NO_LINK; one that leads underground is on NO_NAME when nothing names it, on LABEL_NAME when only
	 * a map label does. A way up to the surface is named after the town it comes out in, which is no dungeon's name.
	 */
	static List<Found> find(List<MapSpot> spots, Function<MapSpot, WorldPoint> leadsTo, Names names, WorldPoint from)
	{
		List<Found> out = new ArrayList<>();
		for (MapSpot spot : spots)
		{
			if (spot.getGroup() != IconGroup.DUNGEONS)
			{
				continue;
			}
			WorldPoint to = leadsTo.apply(spot);
			if (to == null)
			{
				out.add(new Found(Kind.NO_LINK, spot, null, null));
			}
			else if (MapArea.of(to.getY()) == MapArea.UNDERGROUND)
			{
				String name = names.name(spot, to, true);
				if (name == null)
				{
					out.add(new Found(Kind.NO_NAME, spot, to, null));
				}
				else if (names.name(spot, to, false) == null)
				{
					out.add(new Found(Kind.LABEL_NAME, spot, to, name));
				}
			}
		}
		out.sort(Comparator.comparingInt(found -> SearchSpots.distance(found.point(), from)));
		return out;
	}

	static List<Found> of(List<Found> all, Kind kind)
	{
		List<Found> out = new ArrayList<>();
		for (Found found : all)
		{
			if (found.kind == kind)
			{
				out.add(found);
			}
		}
		return out;
	}

	/** The list's rows: what a click does, then one row per icon; a click on a row moves the map to the icon. */
	static List<SideList.Row> rows(List<Found> list, Kind kind, List<MapLabels.Label> labels, int icons, Path file)
	{
		List<SideList.Row> rows = new ArrayList<>();
		rows.add(new SideList.Row(list.size() + " of the " + icons + " dungeon icons found so far (the map finds more"
			+ " as it scans). Click one to go there. Also written to " + file, null, null, null, null, null));
		for (Found found : list)
		{
			WorldPoint at = found.point();
			String near = SearchSpots.name(at, labels);
			String where = "icon " + tile(at) + (found.to == null ? "" : "; leads to " + tile(found.to));
			String text = kind == Kind.LABEL_NAME ? found.name : near;
			rows.add(new SideList.Row(text, MapArea.of(at.getY()) == MapArea.SURFACE ? "surface" : "underground",
				kind == Kind.LABEL_NAME ? "near " + near + "; " + where : where, null, at, null));
		}
		return rows;
	}

	private static String tile(WorldPoint at)
	{
		return at.getX() + ", " + at.getY() + ", " + at.getPlane();
	}

	static final String HEADER = "# x\ty\tplane\tleads to x\ty\tplane\tname now\tnearest label\tfill in: what it is,"
		+ " or where it leads";

	/**
	 * The list as a file's lines: one row per icon, the last column for the owner to fill in. What was filled in
	 * before ({@code old}, the file's lines as they are) is kept for the icons still on the list.
	 */
	static List<String> lines(List<Found> list, List<MapLabels.Label> labels, List<String> old)
	{
		Map<String, String> filled = new HashMap<>();
		for (String line : old)
		{
			String[] cells = line.split("\t", -1);
			if (!line.startsWith("#") && cells.length >= 9 && !cells[8].trim().isEmpty())
			{
				filled.put(cells[0] + "\t" + cells[1] + "\t" + cells[2], cells[8]);
			}
		}
		List<String> out = new ArrayList<>();
		out.add(HEADER);
		for (Found found : list)
		{
			WorldPoint to = found.to;
			out.add(found.key() + "\t" + (to == null ? "\t\t" : to.getX() + "\t" + to.getY() + "\t" + to.getPlane())
				+ "\t" + (found.name == null ? "" : found.name) + "\t" + SearchSpots.name(found.point(), labels)
				+ "\t" + filled.getOrDefault(found.key(), ""));
		}
		return out;
	}

	/** Writes the list's file, keeping what was filled in. */
	static void write(Path file, List<Found> list, List<MapLabels.Label> labels) throws IOException
	{
		List<String> old = Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8)
			: Collections.emptyList();
		Files.createDirectories(file.getParent());
		Files.write(file, lines(list, labels, old), StandardCharsets.UTF_8);
	}
}
