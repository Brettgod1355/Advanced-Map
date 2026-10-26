/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.WorldMapElements;
import java.nio.ByteBuffer;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapLabelsTest
{
	@Test
	public void farOutOnlyKingdomsThenTownsThenPlaces()
	{
		assertTrue(MapLabels.shows(MapLabels.KINGDOM, 0.3));
		assertFalse(MapLabels.shows(MapLabels.TOWN, 0.3));
		assertFalse(MapLabels.shows(MapLabels.PLACE, 0.3));
		assertTrue(MapLabels.shows(MapLabels.TOWN, 1));
		assertFalse(MapLabels.shows(MapLabels.PLACE, 1));
		assertFalse(MapLabels.shows(MapLabels.KINGDOM, 4));
		assertTrue(MapLabels.shows(MapLabels.TOWN, 4));
		assertTrue(MapLabels.shows(MapLabels.PLACE, 4));
		// no icons while only kingdoms show
		assertTrue(MapIcons.ICON_ZOOM >= MapLabels.KINGDOM_ZOOM_MAX);
	}

	@Test
	public void elementsAreMovedBackToWhereTheyAreInTheWorld()
	{
		ByteBuffer data = ByteBuffer.allocate(64);
		// one square shown at (50, 50), drawn from (48, 150) on level 0
		data.putShort((short) 1);
		data.put((byte) 0).put((byte) 0).put((byte) 1);
		data.putShort((short) 48).putShort((short) 150).putShort((short) 50).putShort((short) 50);
		// no zones
		data.putShort((short) 0);
		// two elements: one in the moved square, one elsewhere
		data.putShort((short) 2);
		data.putShort((short) 7).putInt(50 * 64 + 10 << 14 | 50 * 64 + 20).put((byte) 0);
		data.putShort((short) 8).putInt(3100 << 14 | 3500).put((byte) 1);
		List<WorldMapElements.Element> elements = WorldMapElements.load(data.array());
		assertEquals(2, elements.size());
		assertEquals(new WorldMapElements.Element(7, 48 * 64 + 10, 150 * 64 + 20, 0), elements.get(0));
		assertEquals(new WorldMapElements.Element(8, 3100, 3500, 0), elements.get(1));
	}
}
