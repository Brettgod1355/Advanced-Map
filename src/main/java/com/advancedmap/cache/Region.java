/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.advancedmap.cache;

import com.advancedmap.cache.MapDefinition.Tile;

import java.util.ArrayList;
import java.util.List;

public class Region
{

	public static final int X = 64;
	public static final int Y = 64;
	public static final int Z = 4;

	private final int regionID;
	private final int baseX;
	private final int baseY;

	private final int[][][] tileHeights = new int[Z][X][Y];
	private final byte[][][] tileSettings = new byte[Z][X][Y];
	private final short[][][] overlayIds = new short[Z][X][Y];
	private final byte[][][] overlayPaths = new byte[Z][X][Y];
	private final byte[][][] overlayRotations = new byte[Z][X][Y];
	private final short[][][] underlayIds = new short[Z][X][Y];

	private final List<Location> locations = new ArrayList<>();

	public Region(int id)
	{
		this.regionID = id;
		this.baseX = ((id >> 8) & 0xFF) << 6; // local coords are in bottom 6 bits (64*64)
		this.baseY = (id & 0xFF) << 6;
	}

	public Region(int x, int y)
	{
		this.regionID = x << 8 | y;
		this.baseX = x << 6;
		this.baseY = y << 6;
	}

	/** A copy of another region, to change without changing it. */
	public Region(Region other)
	{
		this(other.regionID);
		for (int z = 0; z < Z; z++)
		{
			for (int x = 0; x < X; x++)
			{
				tileHeights[z][x] = other.tileHeights[z][x].clone();
				tileSettings[z][x] = other.tileSettings[z][x].clone();
				overlayIds[z][x] = other.overlayIds[z][x].clone();
				overlayPaths[z][x] = other.overlayPaths[z][x].clone();
				overlayRotations[z][x] = other.overlayRotations[z][x].clone();
				underlayIds[z][x] = other.underlayIds[z][x].clone();
			}
		}
		locations.addAll(other.locations);
	}

	/**
	 * Takes an 8x8 zone, on every floor, from another region: its ground at the same local tiles, and its objects in
	 * place of this region's there, moved to this region's tiles.
	 */
	public void takeZone(Region from, int zoneX, int zoneY)
	{
		int x0 = zoneX * 8;
		int y0 = zoneY * 8;
		for (int z = 0; z < Z; z++)
		{
			for (int x = x0; x < x0 + 8; x++)
			{
				for (int y = y0; y < y0 + 8; y++)
				{
					tileHeights[z][x][y] = from.tileHeights[z][x][y];
					tileSettings[z][x][y] = from.tileSettings[z][x][y];
					overlayIds[z][x][y] = from.overlayIds[z][x][y];
					overlayPaths[z][x][y] = from.overlayPaths[z][x][y];
					overlayRotations[z][x][y] = from.overlayRotations[z][x][y];
					underlayIds[z][x][y] = from.underlayIds[z][x][y];
				}
			}
		}
		locations.removeIf(loc -> inZone(loc, baseX, baseY, x0, y0));
		for (Location loc : from.locations)
		{
			if (inZone(loc, from.baseX, from.baseY, x0, y0))
			{
				Position at = loc.getPosition();
				locations.add(new Location(loc.getId(), loc.getType(), loc.getOrientation(),
					new Position(at.getX() - from.baseX + baseX, at.getY() - from.baseY + baseY, at.getZ())));
			}
		}
	}

	private static boolean inZone(Location loc, int baseX, int baseY, int x0, int y0)
	{
		int x = loc.getPosition().getX() - baseX;
		int y = loc.getPosition().getY() - baseY;
		return x >= x0 && x < x0 + 8 && y >= y0 && y < y0 + 8;
	}

	public void loadTerrain(MapDefinition map)
	{
		Tile[][][] tiles = map.getTiles();
		for (int z = 0; z < Z; z++)
		{
			for (int x = 0; x < X; x++)
			{
				for (int y = 0; y < Y; y++)
				{
					Tile tile = tiles[z][x][y];

					if (tile.height == null)
					{
						if (z == 0)
						{
							tileHeights[0][x][y] = -HeightCalc.calculate(baseX + x + 0xe3b7b, baseY + y + 0x87cce) * 8;
						}
						else
						{
							tileHeights[z][x][y] = tileHeights[z - 1][x][y] - 240;
						}
					}
					else
					{
						int height = tile.getHeight();
						if (height == 1)
						{
							height = 0;
						}

						if (z == 0)
						{
							tileHeights[0][x][y] = -height * 8;
						}
						else
						{
							tileHeights[z][x][y] = tileHeights[z - 1][x][y] - height * 8;
						}
					}

					overlayIds[z][x][y] = tile.getOverlayId();
					overlayPaths[z][x][y] = tile.getOverlayPath();
					overlayRotations[z][x][y] = tile.getOverlayRotation();

					tileSettings[z][x][y] = tile.getSettings();
					underlayIds[z][x][y] = tile.getUnderlayId();
				}
			}
		}
	}

	public void loadLocations(LocationsDefinition locs)
	{
		for (Location loc : locs.getLocations())
		{
			Location newLoc = new Location(loc.getId(), loc.getType(), loc.getOrientation(),
				new Position(getBaseX() + loc.getPosition().getX(),
					getBaseY() + loc.getPosition().getY(),
					loc.getPosition().getZ()));
			locations.add(newLoc);
		}
	}

	public int getRegionID()
	{
		return regionID;
	}

	public int getBaseX()
	{
		return baseX;
	}

	public int getBaseY()
	{
		return baseY;
	}

	public int getTileHeight(int z, int x, int y)
	{
		return tileHeights[z][x][y];
	}

	public byte getTileSetting(int z, int x, int y)
	{
		return tileSettings[z][x][y];
	}

	public int getOverlayId(int z, int x, int y)
	{
		return overlayIds[z][x][y] & 0x7FFF;
	}

	public byte getOverlayPath(int z, int x, int y)
	{
		return overlayPaths[z][x][y];
	}

	public byte getOverlayRotation(int z, int x, int y)
	{
		return overlayRotations[z][x][y];
	}

	public int getUnderlayId(int z, int x, int y)
	{
		return underlayIds[z][x][y] & 0x7FFF;
	}

	public List<Location> getLocations()
	{
		return locations;
	}

	public int getRegionX()
	{
		return baseX >> 6;
	}

	public int getRegionY()
	{
		return baseY >> 6;
	}
}
