/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.Location;
import com.advancedmap.cache.MapLinks;
import com.advancedmap.cache.Region;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;

/**
 * Prifddinas on the surface map. On the surface, inside Tirannwn's city walls, the game has only an empty forest; the
 * city is a copy of those 4x4 regions (33-36 x 50-53) far to the north-east (49-52 x 93-96), with the city built inside
 * the walls, and a player in the city stands on the copy's tiles. The game shows it on a world map of its own. Advanced
 * Map draws the copy's 8x8 zones in and around the walls into the surface wherever they differ, leaves the copy off the
 * underground map, and moves anything placed in the copy (the player, icons, transports, teleports, map links, routes)
 * onto the surface: a tile of the copy is the surface tile plus ({@link #SHIFT_X}, {@link #SHIFT_Y}).
 */
final class Prifddinas
{
	static final int SHIFT_X = 1024;
	static final int SHIFT_Y = 2752;
	/** The copy's regions, in region coordinates. */
	static final Rectangle COPY_REGIONS = new Rectangle(49, 93, 4, 4);
	/** The copy's tiles. */
	static final Rectangle COPY = new Rectangle(49 << 6, 93 << 6, 4 << 6, 4 << 6);
	/**
	 * Inside the walls, on the surface: where the copy has the city and the surface its forest. A tile here is one in
	 * the city, so a route asked for here goes into the copy.
	 */
	static final Rectangle INSIDE = new Rectangle(2176, 3264, 128, 128);
	/** The surface tiles that take the copy's zones where they differ: inside the walls, the walls and gates. */
	static final Rectangle DRAWN = new Rectangle(2168, 3256, 144, 144);

	private Prifddinas()
	{
	}

	/** True for a region of the copy, which is drawn on the surface and left off the underground map. */
	static boolean isCopyRegion(int regionX, int regionY)
	{
		return COPY_REGIONS.contains(regionX, regionY);
	}

	/** True for a surface region that takes some of the copy's zones. */
	static boolean takesCity(int regionX, int regionY)
	{
		return DRAWN.intersects(regionX << 6, regionY << 6, 64, 64);
	}

	/** The surface tile of a tile in the copy; any other tile as it is. Null stays null. */
	static WorldPoint toSurface(WorldPoint point)
	{
		if (point == null || !COPY.contains(point.getX(), point.getY()))
		{
			return point;
		}
		return new WorldPoint(point.getX() - SHIFT_X, point.getY() - SHIFT_Y, point.getPlane());
	}

	/** The tile in the copy of a surface tile inside the walls, where a player in the city stands; any other as it is. */
	static WorldPoint toCity(WorldPoint point)
	{
		if (point == null || !INSIDE.contains(point.getX(), point.getY()))
		{
			return point;
		}
		return new WorldPoint(point.getX() + SHIFT_X, point.getY() + SHIFT_Y, point.getPlane());
	}

	/** The game's map links with their tiles in the copy moved to the surface, at both ends. */
	static Map<Integer, Integer> moveLinks(Map<Integer, Integer> links)
	{
		Map<Integer, Integer> out = new HashMap<>();
		links.forEach((from, to) -> out.put(moved(from), moved(to)));
		return Collections.unmodifiableMap(out);
	}

	private static int moved(int packed)
	{
		int x = MapLinks.x(packed);
		int y = MapLinks.y(packed);
		return COPY.contains(x, y) ? MapLinks.pack(x - SHIFT_X, y - SHIFT_Y, MapLinks.plane(packed)) : packed;
	}

	/**
	 * A surface region with the copy's zones in it, where they lie within {@link #DRAWN} and differ from the surface's
	 * (ground, on any floor, or objects); the surface region itself when it takes none or the copy is missing.
	 */
	static Region merge(Region surface, Region copy)
	{
		if (surface == null || copy == null)
		{
			return surface;
		}
		Region merged = null;
		for (int zoneX = 0; zoneX < 8; zoneX++)
		{
			for (int zoneY = 0; zoneY < 8; zoneY++)
			{
				int x = surface.getBaseX() + zoneX * 8;
				int y = surface.getBaseY() + zoneY * 8;
				if (DRAWN.contains(x, y) && differs(surface, copy, zoneX, zoneY))
				{
					if (merged == null)
					{
						merged = new Region(surface);
					}
					merged.takeZone(copy, zoneX, zoneY);
				}
			}
		}
		return merged == null ? surface : merged;
	}

	/** True when an 8x8 zone of the copy differs from the surface's: its ground on any floor, or its objects. */
	static boolean differs(Region surface, Region copy, int zoneX, int zoneY)
	{
		for (int z = 0; z < Region.Z; z++)
		{
			for (int x = zoneX * 8; x < zoneX * 8 + 8; x++)
			{
				for (int y = zoneY * 8; y < zoneY * 8 + 8; y++)
				{
					if (surface.getUnderlayId(z, x, y) != copy.getUnderlayId(z, x, y)
						|| surface.getOverlayId(z, x, y) != copy.getOverlayId(z, x, y)
						|| surface.getOverlayPath(z, x, y) != copy.getOverlayPath(z, x, y)
						|| surface.getOverlayRotation(z, x, y) != copy.getOverlayRotation(z, x, y)
						|| surface.getTileSetting(z, x, y) != copy.getTileSetting(z, x, y))
					{
						return true;
					}
				}
			}
		}
		return !objects(surface, zoneX, zoneY).equals(objects(copy, zoneX, zoneY));
	}

	/** A zone's objects, by their local tile, so the surface's and the copy's compare. */
	private static Set<String> objects(Region region, int zoneX, int zoneY)
	{
		Set<String> out = new HashSet<>();
		for (Location location : region.getLocations())
		{
			int x = location.getPosition().getX() - region.getBaseX();
			int y = location.getPosition().getY() - region.getBaseY();
			if (x >> 3 == zoneX && y >> 3 == zoneY)
			{
				out.add(location.getId() + "@" + x + "," + y + "," + location.getPosition().getZ() + ","
					+ location.getType() + "," + location.getOrientation());
			}
		}
		return out;
	}
}
