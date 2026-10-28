/*
 * Copyright (c) 2024, Christopher Brown <christoabrown@gmail.com>
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

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * The elements (labels and icons) the game's world map places, read from one file of the world map index's
 * composite map group, with each position moved back from where the world map shows it to where it is in the
 * world. Adapted from RuneLite's {@code WorldMapCompositeLoader}, {@code WorldMapDataLoader},
 * {@code WorldMapElementLoader} and {@code WorldMapCompositeDefinition}, current (revision 238 and later) format only.
 */
public final class WorldMapElements
{
	/** One placed element: a map area (see {@link AreaDefinition}) at a world tile. */
	@Value
	public static class Element
	{
		int areaId;
		int x;
		int y;
		int plane;
	}

	/**
	 * Part of the world a map shows: a 64-tile square or an 8-tile zone, from its south-west tile, on the floors from
	 * {@code minLevel} up.
	 */
	@Value
	public static class Piece
	{
		int x;
		int y;
		int size;
		int minLevel;

		public boolean contains(int tileX, int tileY, int plane)
		{
			return plane >= minLevel && tileX >= x && tileY >= y && tileX < x + size && tileY < y + size;
		}
	}

	/** A square or zone of the world map drawn from somewhere else in the world. */
	private static final class Moved
	{
		int minLevel;
		int sourceX;
		int sourceY;
		int displayX;
		int displayY;
		/** -1 for a whole 64-tile square, else the 8-tile zone in it. */
		int displayZoneX = -1;
		int displayZoneY = -1;
		int sourceZoneX;
		int sourceZoneY;
	}

	private WorldMapElements()
	{
	}

	public static List<Element> load(byte[] data)
	{
		return read(data, null);
	}

	/** The parts of the world one map shows (each square and zone it is drawn from), from the same file. */
	public static List<Piece> pieces(byte[] data)
	{
		List<Piece> pieces = new ArrayList<>();
		read(data, pieces);
		return pieces;
	}

	private static List<Element> read(byte[] data, List<Piece> pieces)
	{
		InputStream in = new InputStream(data);
		List<Moved> squares = new ArrayList<>();
		int count = in.readUnsignedShort();
		for (int i = 0; i < count; i++)
		{
			expect(in.readUnsignedByte(), 0);
			Moved square = new Moved();
			square.minLevel = in.readUnsignedByte();
			in.readUnsignedByte();
			square.sourceX = in.readUnsignedShort();
			square.sourceY = in.readUnsignedShort();
			square.displayX = in.readUnsignedShort();
			square.displayY = in.readUnsignedShort();
			squares.add(square);
		}
		List<Moved> zones = new ArrayList<>();
		count = in.readUnsignedShort();
		for (int i = 0; i < count; i++)
		{
			expect(in.readUnsignedByte(), 1);
			Moved zone = new Moved();
			zone.minLevel = in.readUnsignedByte();
			in.readUnsignedByte();
			zone.sourceX = in.readUnsignedShort();
			zone.sourceY = in.readUnsignedShort();
			zone.sourceZoneX = in.readUnsignedByte();
			zone.sourceZoneY = in.readUnsignedByte();
			zone.displayX = in.readUnsignedShort();
			zone.displayY = in.readUnsignedShort();
			zone.displayZoneX = in.readUnsignedByte();
			zone.displayZoneY = in.readUnsignedByte();
			zones.add(zone);
		}
		if (pieces != null)
		{
			for (Moved square : squares)
			{
				pieces.add(new Piece(square.sourceX * 64, square.sourceY * 64, 64, square.minLevel));
			}
			for (Moved zone : zones)
			{
				pieces.add(new Piece(zone.sourceX * 64 + zone.sourceZoneX * 8, zone.sourceY * 64 + zone.sourceZoneY * 8, 8,
					zone.minLevel));
			}
			return null;
		}
		List<Element> out = new ArrayList<>();
		count = in.readUnsignedShort();
		for (int i = 0; i < count; i++)
		{
			int areaId = in.readBigSmart2();
			int packed = in.readInt();
			in.readUnsignedByte();
			int x = packed >> 14 & 16383;
			int y = packed & 16383;
			Moved from = movedFrom(squares, zones, x, y);
			if (from == null)
			{
				out.add(new Element(areaId, x, y, 0));
			}
			else
			{
				int shiftX = (from.sourceX - from.displayX) * 64;
				int shiftY = (from.sourceY - from.displayY) * 64;
				if (from.displayZoneX >= 0)
				{
					shiftX += (from.sourceZoneX - from.displayZoneX) * 8;
					shiftY += (from.sourceZoneY - from.displayZoneY) * 8;
				}
				out.add(new Element(areaId, x + shiftX, y + shiftY, from.minLevel));
			}
		}
		return out;
	}

	/** The square or zone a shown position was drawn from; a zone wins over its square, the last one listed wins. */
	private static Moved movedFrom(List<Moved> squares, List<Moved> zones, int x, int y)
	{
		int squareX = x / 64;
		int squareY = y / 64;
		int zoneX = (x & 63) / 8;
		int zoneY = (y & 63) / 8;
		Moved found = null;
		for (Moved square : squares)
		{
			if (square.displayX == squareX && square.displayY == squareY)
			{
				found = square;
			}
		}
		for (Moved zone : zones)
		{
			if (zone.displayX == squareX && zone.displayY == squareY && zone.displayZoneX == zoneX
				&& zone.displayZoneY == zoneY)
			{
				found = zone;
			}
		}
		return found;
	}

	private static void expect(int type, int wanted)
	{
		if (type != wanted)
		{
			throw new IllegalStateException("world map data type " + type + ", expected " + wanted);
		}
	}
}
