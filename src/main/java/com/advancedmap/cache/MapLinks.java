/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap.cache;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The game's map links: which spot on the world map leads to which (a dungeon's entrance to the dungeon, its exit back
 * out). The game keeps them in one client script, {@code worldmap_intermap_op} ({@link #SCRIPT}): a switch on the
 * clicked tile whose every case pushes the destination tile. This reads that script's compiled form: the switch table
 * at its end, then the instructions, to find the constant each case pushes. Anything that does not decode exactly
 * gives no links, never wrong ones.
 */
public final class MapLinks
{
	/** The game's client script that maps a clicked map link to its destination. */
	public static final int SCRIPT = 1705;

	private static final int ICONST = 0;
	private static final int SCONST = 3;
	private static final int RETURN = 21;
	private static final int POP_INT = 38;
	private static final int POP_OBJECT = 39;
	private static final int SWITCH = 60;
	private static final int LCONST = 61;
	private static final int PUSH_NULL = 63;

	private MapLinks()
	{
	}

	/** A tile packed as the game packs one: plane, x and y. */
	public static int pack(int x, int y, int plane)
	{
		return plane << 28 | x << 14 | y;
	}

	public static int x(int packed)
	{
		return packed >> 14 & 0x3FFF;
	}

	public static int y(int packed)
	{
		return packed & 0x3FFF;
	}

	public static int plane(int packed)
	{
		return packed >>> 28;
	}

	/** The links in a compiled script, packed tile to packed tile; empty when it does not decode. */
	public static Map<Integer, Integer> decode(byte[] script)
	{
		if (script == null)
		{
			return Collections.emptyMap();
		}
		// the trailer is 16 bytes in current scripts, 12 in older ones: the one that decodes exactly is right
		for (int trailer : new int[]{16, 12})
		{
			Map<Integer, Integer> links = decode(script, trailer);
			if (links != null)
			{
				return links;
			}
		}
		return Collections.emptyMap();
	}

	private static Map<Integer, Integer> decode(byte[] script, int trailer)
	{
		try
		{
			ByteBuffer in = ByteBuffer.wrap(script);
			int length = script.length;
			int switchLength = in.getShort(length - 2) & 0xFFFF;
			int end = length - 2 - switchLength - trailer;
			if (end < 1)
			{
				return null;
			}
			int count = in.getInt(end);
			in.position(end + trailer);
			if ((in.get() & 0xFF) < 1)
			{
				return null;
			}
			Map<Integer, Integer> cases = new LinkedHashMap<>();
			for (int n = in.getShort() & 0xFFFF; n > 0; n--)
			{
				cases.put(in.getInt(), in.getInt());
			}

			int[] opcodes = new int[count];
			int[] operands = new int[count];
			in.position(0);
			skipString(in);
			int i = 0;
			while (in.position() < end)
			{
				if (i >= count)
				{
					return null;
				}
				int opcode = in.getShort() & 0xFFFF;
				opcodes[i] = opcode;
				switch (opcode)
				{
					case SCONST:
						skipString(in);
						break;
					case RETURN:
					case POP_INT:
					case POP_OBJECT:
					case PUSH_NULL:
						in.get();
						break;
					case LCONST:
						in.getLong();
						break;
					default:
						operands[i] = opcode < 100 ? in.getInt() : in.get() & 0xFF;
						break;
				}
				i++;
			}
			if (i != count || in.position() != end)
			{
				return null;
			}

			int switchAt = -1;
			for (int pc = 0; pc < count && switchAt < 0; pc++)
			{
				if (opcodes[pc] == SWITCH)
				{
					switchAt = pc;
				}
			}
			if (switchAt < 0)
			{
				return null;
			}
			Map<Integer, Integer> links = new HashMap<>();
			for (Map.Entry<Integer, Integer> entry : cases.entrySet())
			{
				int pc = switchAt + 1 + entry.getValue();
				if (pc >= 0 && pc < count && opcodes[pc] == ICONST)
				{
					links.put(entry.getKey(), operands[pc]);
				}
			}
			return Collections.unmodifiableMap(links);
		}
		catch (BufferUnderflowException | IndexOutOfBoundsException e)
		{
			return null;
		}
	}

	/** Skips a string: a 0-ended run of bytes, or a lone 0 for none. */
	private static void skipString(ByteBuffer in)
	{
		while (in.get() != 0)
		{
			// to the end of the string
		}
	}

	/**
	 * Where a link at or near a tile leads (within {@code reach} tiles on its floor, the nearest), packed, or -1. An
	 * icon sits a tile or two off the spot the game's script names.
	 */
	public static int destinationNear(Map<Integer, Integer> links, int x, int y, int plane, int reach)
	{
		int best = -1;
		int bestDistance = reach + 1;
		for (Map.Entry<Integer, Integer> link : links.entrySet())
		{
			int from = link.getKey();
			int distance = Math.max(Math.abs(x(from) - x), Math.abs(y(from) - y));
			if (plane(from) == plane && distance < bestDistance)
			{
				best = link.getValue();
				bestDistance = distance;
			}
		}
		return best;
	}
}
