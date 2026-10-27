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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * How the farming patches are doing, from what RuneLite's Time Tracking plugin saved for the player (read only, under
 * its own setting names) and the patch tables in {@code farming.tsv} (from Time Tracking and the world map plugin;
 * see THIRD_PARTY_NOTICES.md). A site (Falador's allotments, herb and flower patches) gets one state: ready when any
 * patch is, else diseased or dead when one is, else growing (with when the first is ready), else empty.
 */
@Slf4j
final class Farming
{
	/** Time Tracking's settings group and keys: a patch is saved under "region.varbit" as "value:unix seconds". */
	static final String GROUP = "timetracking";
	static final String AUTOWEED = "autoweed";
	static final String TICK_OFFSET = "farmTickOffset";
	static final String TICK_OFFSET_PRECISION = "farmTickOffsetPrecision";
	/** The autoweed setting's value for "on". */
	static final String AUTOWEED_ON = "2";
	/** How far a farming icon may be from a site. */
	static final int REACH = 12;

	enum Status
	{
		READY("Ready", new Color(0x5FD35B)),
		DISEASED("Diseased or dead", new Color(0xFF5B4B)),
		GROWING("Growing", new Color(0xF2C14E)),
		EMPTY("Empty", new Color(0x9A958A));

		final String label;
		final Color color;

		Status(String label, Color color)
		{
			this.label = label;
			this.color = color;
		}
	}

	@Value
	static class Patch
	{
		int region;
		String place;
		Set<Integer> regions;
		String name;
		int varbit;
		String kind;
	}

	/** Game values {@code from..to} of a kind of patch: what grows, its state, and its growth stage. */
	@Value
	static class Values
	{
		String kind;
		int from;
		int to;
		String produce;
		String state;
		int stage;
		int step;
		int stages;
		int tickRate;
	}

	@Value
	static class Site
	{
		String label;
		WorldPoint point;
		List<Patch> patches;
	}

	/** One patch now: what is in it, its state, and when it is done (unix seconds, 0 when not growing). */
	@Value
	static class Reading
	{
		Patch patch;
		String produce;
		Status status;
		long done;
	}

	@Value
	static class Data
	{
		List<Site> sites;
		Map<String, List<Values>> values;
	}

	private static volatile Data data = new Data(Collections.emptyList(), Collections.emptyMap());

	private Farming()
	{
	}

	static List<Site> sites()
	{
		return data.sites;
	}

	static void load()
	{
		try (InputStream in = Farming.class.getResourceAsStream("farming.tsv"))
		{
			if (in != null)
			{
				data = parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the farming patches", e);
		}
	}

	static Data parse(InputStream in) throws IOException
	{
		List<Patch> patches = new ArrayList<>();
		Map<String, List<Values>> values = new HashMap<>();
		List<String[]> sites = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			switch (f[0])
			{
				case "P":
					Set<Integer> regions = new HashSet<>();
					for (String region : f[3].split(","))
					{
						regions.add(Integer.parseInt(region));
					}
					patches.add(new Patch(Integer.parseInt(f[1]), f[2], regions, f[4], Integer.parseInt(f[5]), f[6]));
					break;
				case "V":
					values.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new Values(f[1], Integer.parseInt(f[2]),
						Integer.parseInt(f[3]), f[4], f[5], Integer.parseInt(f[6]), Integer.parseInt(f[7]),
						Integer.parseInt(f[8]), Integer.parseInt(f[9])));
					break;
				case "S":
					sites.add(f);
					break;
				default:
					break;
			}
		}
		List<Site> out = new ArrayList<>();
		for (String[] f : sites)
		{
			WorldPoint at = new WorldPoint(Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]));
			// the patches of the site's own region; a region Time Tracking also reads a patch from (Port Sarim's spirit
			// tree from Falador) only when the site has none of its own
			List<Patch> here = new ArrayList<>();
			for (Patch patch : patches)
			{
				if (patch.getRegion() == at.getRegionID() && isOfSite(patch, f[1], true))
				{
					here.add(patch);
				}
			}
			if (here.isEmpty())
			{
				for (Patch patch : patches)
				{
					if (patch.getRegions().contains(at.getRegionID()) && isOfSite(patch, f[1], false))
					{
						here.add(patch);
					}
				}
			}
			out.add(new Site(f[1], Prifddinas.toSurface(at), Collections.unmodifiableList(here)));
		}
		return new Data(Collections.unmodifiableList(out), values);
	}

	/**
	 * True when a patch is of a kind a site's label names ("Allotment/Herb/Flower", "Fruit Tree", "Hardwood"); a
	 * compost bin goes with the allotments of its own region.
	 */
	static boolean isOfSite(Patch patch, String label, boolean ownRegion)
	{
		if (patch.getKind().contains("COMPOST"))
		{
			return ownRegion && label.contains("Allotment");
		}
		for (String part : label.split("/"))
		{
			if (patch.getKind().startsWith(part.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_')))
			{
				return true;
			}
		}
		return false;
	}

	/** The values row of a kind of patch for a game value, or null. */
	static Values lookup(String kind, int value)
	{
		for (Values row : data.values.getOrDefault(kind, Collections.emptyList()))
		{
			if (value >= row.from && value <= row.to)
			{
				return row;
			}
		}
		return null;
	}

	/**
	 * A patch's state now, from what Time Tracking saved ({@code saved} reads its settings by key), or null when it
	 * saved nothing for it. Growth goes on by the patch's tick rate since the time saved, as Time Tracking predicts it.
	 */
	static Reading read(Patch patch, Function<String, String> saved, long now)
	{
		String stored = saved.apply(patch.getRegion() + "." + patch.getVarbit());
		if (stored == null)
		{
			return null;
		}
		String[] parts = stored.split(":");
		int value;
		long time;
		try
		{
			value = Integer.parseInt(parts[0]);
			time = parts.length == 2 ? Long.parseLong(parts[1]) : 0;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		Values row = lookup(patch.getKind(), value);
		if (row == null || time <= 0)
		{
			return null;
		}
		boolean weeds = "Weeds".equals(row.produce);
		if (weeds && AUTOWEED_ON.equals(saved.apply(AUTOWEED)))
		{
			return new Reading(patch, row.produce, Status.EMPTY, 0);
		}
		int stage = row.stage + row.step * (value - row.from);
		long done = 0;
		if (row.tickRate > 0)
		{
			long offset = tickOffset(saved, row.tickRate);
			long tickTime = tickTime(row.tickRate, 0, time, offset);
			done = tickTime(row.tickRate, row.stages - 1 - stage, tickTime, offset);
		}
		Status status;
		switch (row.state)
		{
			case "HARVESTABLE":
				status = weeds ? Status.EMPTY : Status.READY;
				break;
			case "DISEASED":
			case "DEAD":
				status = Status.DISEASED;
				break;
			case "EMPTY":
			case "FILLING":
				status = Status.EMPTY;
				break;
			default:
				status = weeds ? Status.EMPTY : done > 0 && done <= now ? Status.READY : Status.GROWING;
				break;
		}
		return new Reading(patch, row.produce, status, status == Status.GROWING ? done : 0);
	}

	/** Time Tracking's estimate of how far its farming ticks are off the clock, in seconds; 0 when not known. */
	private static long tickOffset(Function<String, String> saved, int tickRate)
	{
		try
		{
			String precision = saved.apply(TICK_OFFSET_PRECISION);
			String offset = saved.apply(TICK_OFFSET);
			if (precision == null || offset == null)
			{
				return 0;
			}
			int minutes = Integer.parseInt(precision);
			return minutes >= tickRate || minutes >= 40 ? (Integer.parseInt(offset) % tickRate) * 60L : 0;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** The time of the farming tick {@code ticks} after the one at {@code time}. */
	static long tickTime(int tickRate, int ticks, long time, long offset)
	{
		long shifted = time + offset;
		long current = shifted - shifted % (tickRate * 60L);
		return current + ticks * tickRate * 60L - offset;
	}

	/** A site's patches now, those Time Tracking knows. */
	static List<Reading> readings(Site site, Function<String, String> saved, long now)
	{
		List<Reading> out = new ArrayList<>();
		for (Patch patch : site.getPatches())
		{
			Reading reading = read(patch, saved, now);
			if (reading != null)
			{
				out.add(reading);
			}
		}
		return out;
	}

	/** A site's one state: ready, else diseased, else growing, else empty; null when nothing is known. */
	static Status summary(List<Reading> readings)
	{
		Status best = null;
		for (Reading reading : readings)
		{
			if (best == null || reading.getStatus().ordinal() < best.ordinal())
			{
				best = reading.getStatus();
			}
		}
		return best;
	}

	/** The site a farming icon belongs to: the nearest within {@link #REACH} tiles, or null. */
	static Site siteNear(int x, int y)
	{
		Site best = null;
		int bestDistance = REACH + 1;
		for (Site site : data.sites)
		{
			int d = Math.max(Math.abs(site.getPoint().getX() - x), Math.abs(site.getPoint().getY() - y));
			if (d < bestDistance)
			{
				best = site;
				bestDistance = d;
			}
		}
		return best;
	}

	/** "2h 05m", "45m", "under a minute". */
	static String timeLeft(long seconds)
	{
		if (seconds < 60)
		{
			return "under a minute";
		}
		long minutes = seconds / 60;
		return minutes >= 60 ? String.format("%dh %02dm", minutes / 60, minutes % 60) : minutes + "m";
	}
}
