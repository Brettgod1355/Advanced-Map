/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.ObjectDefinition;
import com.advancedmap.cache.ObjectLoader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ObjectLoaderTest
{
	@Test
	public void anOpcodeAddedByAGameUpdateEndsTheReadWithWhatWasReadSoFar()
	{
		// name (opcode 2), then an opcode this loader does not know followed by bytes it cannot make sense of: before,
		// each of those bytes was read as another opcode (one warning each) until the buffer ran out
		byte[] name = "Door".getBytes(StandardCharsets.UTF_8);
		byte[] data = new byte[name.length + 8];
		data[0] = 2;
		System.arraycopy(name, 0, data, 1, name.length);
		int at = name.length + 1;
		data[at] = 0;
		data[at + 1] = (byte) 200;
		data[at + 2] = 7;
		data[at + 3] = 7;
		data[at + 4] = 7;
		data[at + 5] = 7;
		data[at + 6] = 7;
		ObjectDefinition def = new ObjectLoader().load(12, data);
		assertEquals(12, def.getId());
		assertEquals("Door", def.getName());
	}
}
