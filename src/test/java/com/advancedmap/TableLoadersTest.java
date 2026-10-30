/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.AreaDefinition;
import com.advancedmap.cache.AreaLoader;
import com.advancedmap.cache.OverlayDefinition;
import com.advancedmap.cache.OverlayLoader;
import com.advancedmap.cache.UnderlayDefinition;
import com.advancedmap.cache.UnderlayLoader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The map areas, ground and overlay tables are read for every region: an opcode a game update adds to one of them
 * must end that record's read, as it does for an object, instead of reading its payload as more opcodes until the
 * buffer runs out, which failed the whole table and left every region of the session blank.
 */
public class TableLoadersTest
{
	/** Bytes read so far, then an unknown opcode and a payload of bytes that are not opcodes. */
	private static byte[] thenUnknown(byte[] known)
	{
		byte[] data = new byte[known.length + 7];
		System.arraycopy(known, 0, data, 0, known.length);
		data[known.length] = (byte) 200;
		for (int i = known.length + 1; i < data.length; i++)
		{
			data[i] = 7;
		}
		return data;
	}

	@Test
	public void anAreaKeepsWhatWasReadBeforeAnOpcodeItDoesNotKnow()
	{
		byte[] name = "Bank".getBytes(StandardCharsets.UTF_8);
		byte[] known = new byte[name.length + 2];
		known[0] = 3;
		System.arraycopy(name, 0, known, 1, name.length);
		AreaDefinition def = new AreaLoader().load(thenUnknown(known), 5);
		assertEquals(5, def.id);
		assertEquals("Bank", def.name);
	}

	@Test
	public void aGroundTypeKeepsItsColorBeforeAnOpcodeItDoesNotKnow()
	{
		UnderlayDefinition def = new UnderlayLoader().load(3, thenUnknown(new byte[]{1, 0x10, 0x20, 0x30}));
		assertEquals(3, def.getId());
		assertEquals(0x102030, def.getColor());
	}

	@Test
	public void anOverlayKeepsItsColorBeforeAnOpcodeItDoesNotKnow()
	{
		OverlayDefinition def = new OverlayLoader().load(4, thenUnknown(new byte[]{1, 0x10, 0x20, 0x30}));
		assertEquals(4, def.getId());
		assertEquals(0x102030, def.getRgbColor());
	}
}
