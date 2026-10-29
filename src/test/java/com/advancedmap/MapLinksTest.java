/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapLinks;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The map link script's decoding, on a script built here in the game's compiled form (the real one, read from the
 * live cache, decoded to the same 406 links as RuneLite's own script loader).
 */
public class MapLinksTest
{
	private static final int ENTRANCE = MapLinks.pack(2512, 3508, 0);
	private static final int CAVERN = MapLinks.pack(1764, 5367, 1);
	private static final int EXIT = MapLinks.pack(1763, 5361, 0);
	private static final int SURFACE = MapLinks.pack(2531, 3446, 0);

	/**
	 * switch (coord) { case ENTRANCE: link(CAVERN); case EXIT: link(SURFACE); } in the game's compiled form: no name,
	 * the instructions, then the trailer (instruction count, locals, arguments) and the switch table.
	 */
	private static byte[] script()
	{
		ByteBuffer code = ByteBuffer.allocate(256);
		code.put((byte) 0); // no name
		int[][] instructions = {
			{60, 0}, // switch 0
			{6, 6}, // jump to the end
			{0, CAVERN}, // case ENTRANCE: push the destination
			{40, 1706}, // gosub the link procedure
			{6, 3},
			{0, SURFACE}, // case EXIT
			{40, 1706},
			{6, 0},
			{21, 0}, // return
		};
		for (int[] instruction : instructions)
		{
			code.putShort((short) instruction[0]);
			if (instruction[0] == 21)
			{
				code.put((byte) instruction[1]);
			}
			else
			{
				code.putInt(instruction[1]);
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(code.array(), 0, code.position());
		ByteBuffer trailer = ByteBuffer.allocate(16);
		trailer.putInt(instructions.length).putShort((short) 0).putShort((short) 0).putShort((short) 0)
			.putShort((short) 1).putShort((short) 0).putShort((short) 0);
		out.write(trailer.array(), 0, 16);
		ByteBuffer table = ByteBuffer.allocate(1 + 2 + 2 * 8);
		table.put((byte) 1).putShort((short) 2).putInt(ENTRANCE).putInt(1).putInt(EXIT).putInt(4);
		out.write(table.array(), 0, table.position());
		out.write(0);
		out.write(table.position());
		return out.toByteArray();
	}

	@Test
	public void eachCaseLeadsWhereItPushes()
	{
		Map<Integer, Integer> links = MapLinks.decode(script());
		assertEquals(2, links.size());
		assertEquals(Integer.valueOf(CAVERN), links.get(ENTRANCE));
		assertEquals(Integer.valueOf(SURFACE), links.get(EXIT));
	}

	@Test
	public void anIconATileOffTheSpotFollowsTheLink()
	{
		Map<Integer, Integer> links = MapLinks.decode(script());
		// RuneLite's map has the Ancient Cavern entrance at 2511, 3508; the game's script names 2512, 3508
		assertEquals(CAVERN, MapLinks.destinationNear(links, 2511, 3508, 0, 2));
		assertEquals(-1, MapLinks.destinationNear(links, 2520, 3508, 0, 2));
		assertEquals(-1, MapLinks.destinationNear(links, 2512, 3508, 1, 2));
		assertEquals(1, MapLinks.plane(CAVERN));
		assertEquals(1764, MapLinks.x(CAVERN));
		assertEquals(5367, MapLinks.y(CAVERN));
	}

	@Test
	public void aDamagedScriptGivesNoLinksNotWrongOnes()
	{
		byte[] bytes = script();
		bytes[5] ^= 0x7F;
		assertTrue(MapLinks.decode(bytes).isEmpty() || MapLinks.decode(bytes).equals(MapLinks.decode(script())));
		assertTrue(MapLinks.decode(new byte[]{1, 2, 3}).isEmpty());
		assertTrue(MapLinks.decode(null).isEmpty());
	}
}
