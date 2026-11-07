/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarbitID;

/**
 * The player's own places, read from the game: the portal of their house and the docks where their boats are moored,
 * as pins on the map.
 */
final class YourPlaces
{
	/** How far a dock's icon may be from the game's label of its name. */
	static final int DOCK_REACH = 100;

	/**
	 * Each value of the game's house location (the town its house board names for it) and the portal there: Shortest
	 * Path's "Teleport to House" rows, outside the house.
	 */
	static final Map<Integer, House> HOUSES = new HashMap<>();

	static
	{
		house(1, "Rimmington", 2953, 3224);
		house(2, "Taverley", 2893, 3465);
		house(3, "Pollnivneach", 3340, 3003);
		house(4, "Rellekka", 2670, 3631);
		house(5, "Brimhaven", 2757, 3178);
		house(6, "Yanille", 2544, 3096);
		house(8, "Hosidius", 1743, 3517);
		house(9, "Prifddinas", 3239, 6076);
		house(13, "Aldarin", 1422, 2963);
	}

	/** Each boat's owned, type and dock values. */
	private static final int[][] BOATS = {
		{VarbitID.SAILING_BOAT_1_OWNED, VarbitID.SAILING_BOAT_1_TYPE, VarbitID.SAILING_BOAT_1_PORT},
		{VarbitID.SAILING_BOAT_2_OWNED, VarbitID.SAILING_BOAT_2_TYPE, VarbitID.SAILING_BOAT_2_PORT},
		{VarbitID.SAILING_BOAT_3_OWNED, VarbitID.SAILING_BOAT_3_TYPE, VarbitID.SAILING_BOAT_3_PORT},
		{VarbitID.SAILING_BOAT_4_OWNED, VarbitID.SAILING_BOAT_4_TYPE, VarbitID.SAILING_BOAT_4_PORT},
		{VarbitID.SAILING_BOAT_5_OWNED, VarbitID.SAILING_BOAT_5_TYPE, VarbitID.SAILING_BOAT_5_PORT},
	};

	private static final BufferedImage HOUSE = picture(MarkerIcons.Design.HOUSE, 2);
	private static final BufferedImage BOAT = picture(MarkerIcons.Design.BOAT, 4);

	/** The Sailing icons of each region the map has read, as x, y and category. */
	private static final Map<Integer, List<int[]>> SAILING_ICONS = new ConcurrentHashMap<>();

	private YourPlaces()
	{
	}

	@Value
	static class House
	{
		String town;
		WorldPoint portal;
	}

	private static void house(int value, String town, int x, int y)
	{
		HOUSES.put(value, new House(town, new WorldPoint(x, y, 0)));
	}

	/** Whether the plugin is running: a step still finishing after the stop keeps nothing here. */
	private static volatile boolean running;

	/** Keeps a region's Sailing icons, where a boat's dock is looked for. */
	static void sawIcons(int region, List<MapSpot> spots)
	{
		if (!running)
		{
			return;
		}
		List<int[]> sailing = new ArrayList<>();
		for (MapSpot spot : spots)
		{
			if (spot.getGroup() == IconGroup.SAILING && spot.getPlane() == 0)
			{
				sailing.add(new int[]{spot.getX(), spot.getY(), spot.getCategory()});
			}
		}
		// only regions with some: the icon scan covers every region in the game, and read() walks them every second
		if (sailing.isEmpty())
		{
			SAILING_ICONS.remove(region);
		}
		else
		{
			SAILING_ICONS.put(region, sailing);
		}
	}

	/** The plugin starts: Sailing icons are kept from now on, none from an earlier run. */
	static void start()
	{
		SAILING_ICONS.clear();
		running = true;
	}

	/** Forgets the Sailing icons seen: the plugin stops, and its next start finds them again. */
	static void forget()
	{
		running = false;
		SAILING_ICONS.clear();
	}

	/** The pins of the player's house and boats. Client thread. */
	static List<MapPins.Pin> read(Client client, List<MapLabels.Label> labels, Map<Integer, String> categoryNames)
	{
		List<MapPins.Pin> out = new ArrayList<>();
		House house = HOUSES.get(client.getVarbitValue(VarbitID.POH_HOUSE_LOCATION));
		if (house != null)
		{
			out.add(new MapPins.Pin(house.portal, HOUSE, null, "Your house", "House portal, " + house.town, null, false));
		}
		List<int[]> docks = new ArrayList<>();
		for (List<int[]> region : SAILING_ICONS.values())
		{
			for (int[] icon : region)
			{
				String category = categoryNames.get(icon[2]);
				if (MapIcons.DOCKING_POINT.equalsIgnoreCase(category) || MapIcons.PORT.equalsIgnoreCase(category))
				{
					docks.add(icon);
				}
			}
		}
		for (int[] boat : BOATS)
		{
			if (client.getVarbitValue(boat[0]) == 0)
			{
				continue;
			}
			String dock = text(client, DBTableID.SailingDock.ID, DBTableID.SailingDock.COL_DOCK_ID,
				client.getVarbitValue(boat[2]), DBTableID.SailingDock.COL_NICE_NAME);
			WorldPoint at = dock == null ? null : dockSpot(dock, labels, docks);
			if (at != null)
			{
				String type = text(client, DBTableID.SailingBoat.ID, DBTableID.SailingBoat.COL_TYPE_ID,
					client.getVarbitValue(boat[1]), DBTableID.SailingBoat.COL_NAME);
				out.add(new MapPins.Pin(at, BOAT, null, "Your boat", (type == null ? "" : type + ", ")
					+ "moored at " + dock, null, false));
			}
		}
		return out;
	}

	/**
	 * Where a dock is: the docking icon nearest the game's label of its name, within {@link #DOCK_REACH} tiles, else the
	 * label; null when the map has no such label.
	 */
	static WorldPoint dockSpot(String name, List<MapLabels.Label> labels, List<int[]> docks)
	{
		String wanted = name.trim().toLowerCase(Locale.ROOT);
		for (MapLabels.Label label : labels)
		{
			if (!String.join(" ", label.getLines()).trim().toLowerCase(Locale.ROOT).equals(wanted))
			{
				continue;
			}
			int[] best = null;
			long bestDistance = (long) DOCK_REACH * DOCK_REACH + 1;
			for (int[] dock : docks)
			{
				long dx = dock[0] - label.getX();
				long dy = dock[1] - label.getY();
				if (dx * dx + dy * dy < bestDistance)
				{
					best = dock;
					bestDistance = dx * dx + dy * dy;
				}
			}
			return best != null ? new WorldPoint(best[0], best[1], 0) : new WorldPoint(label.getX(), label.getY(), 0);
		}
		return null;
	}

	/** A text field of the game's table row whose {@code key} column holds {@code value}, or null. */
	private static String text(Client client, int table, int key, int value, int column)
	{
		List<Integer> rows = client.getDBRowsByValue(table, key, 0, value);
		if (rows == null || rows.isEmpty())
		{
			return null;
		}
		Object[] field = client.getDBTableField(rows.get(0), column, 0);
		return field != null && field.length > 0 && field[0] instanceof String ? (String) field[0] : null;
	}

	private static BufferedImage picture(MarkerIcons.Design design, int color)
	{
		BufferedImage image = new BufferedImage(MapPins.MARKER_SIZE, MapPins.MARKER_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		MarkerIcons.draw(g, design, MarkerIcons.color(color), 1, 1, MapPins.MARKER_SIZE - 2);
		g.dispose();
		return image;
	}
}
