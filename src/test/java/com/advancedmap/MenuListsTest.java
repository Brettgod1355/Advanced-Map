/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The rows of the menu's "Your markers" and "Hidden icons". */
public class MenuListsTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);

	private static MapPins.Marker marker(String id, int x, int y, String name)
	{
		return new MapPins.Marker(id, new WorldPoint(x, y, 0), MarkerIcons.Design.values()[0], 0, name, "");
	}

	@Test
	public void yourMarkersListsEachMarkerNearestFirstWithHowFarAndWhere()
	{
		List<MapPins.Marker> markers = Arrays.asList(marker("a", 3222 + 1200, 3218, "Slayer cave"),
			marker("b", 3234, 3218, "Bank stash"), marker("c", 3222, 3258, ""));
		List<SideList.Row> rows = AdvancedMapPlugin.markerRows(markers, LUMBRIDGE, Collections.emptyList());
		assertEquals(3, rows.size());
		assertEquals("Bank stash", rows.get(0).getText());
		assertEquals("12 tiles", rows.get(0).getNote());
		assertEquals("a marker with no name", "Marker", rows.get(1).getText());
		assertEquals("40 tiles", rows.get(1).getNote());
		assertEquals("Slayer cave", rows.get(2).getText());
		assertEquals("past a thousand tiles", "far", rows.get(2).getNote());
		assertEquals("a click goes there", new WorldPoint(3234, 3218, 0), rows.get(0).getPoint());
		assertEquals("where, with no place name loaded: the tile", "3234, 3218", rows.get(0).getSub());
		// none yet: how to add one, and nothing to click
		List<SideList.Row> none = AdvancedMapPlugin.markerRows(Collections.emptyList(), LUMBRIDGE,
			Collections.emptyList());
		assertEquals(1, none.size());
		assertTrue(none.get(0).getText().startsWith("None yet."));
		assertNull(none.get(0).getPoint());
	}

	@Test
	public void hiddenIconsListsTheHiddenOnesFoundNearestFirstAndCountsTheRest()
	{
		MapSpot near = MapSpot.icon(3230, 3218, 0, 1, 1059);
		MapSpot far = MapSpot.icon(3400, 3218, 0, 1, 1059);
		MapSpot shown = MapSpot.icon(3223, 3218, 0, 1, 1059);
		java.util.Set<String> hidden = new HashSet<>(Arrays.asList(near.getKey(), far.getKey(), "a1@9000,9000"));
		List<SideList.Row> rows = AdvancedMapPlugin.hiddenRows(hidden, Arrays.asList(far, shown, near, near),
			LUMBRIDGE, Collections.emptyList(), Progress.NONE);
		assertEquals("a note, two found, one not found yet", 4, rows.size());
		assertTrue(rows.get(0).getText().startsWith("Click one to go there"));
		assertNull(rows.get(0).getPoint());
		assertEquals(new WorldPoint(3230, 3218, 0), rows.get(1).getPoint());
		assertEquals("8 tiles", rows.get(1).getNote());
		assertSame("a click shows the hidden icons too", AdvancedMapPlugin.HIDDEN_LIST, rows.get(1).getTarget());
		assertEquals(new WorldPoint(3400, 3218, 0), rows.get(2).getPoint());
		assertTrue(rows.get(3).getText().startsWith("1 more not found yet"));
		assertNull(rows.get(3).getPoint());
		// nothing hidden: how to hide one
		List<SideList.Row> none = AdvancedMapPlugin.hiddenRows(Collections.emptySet(), Arrays.asList(near), LUMBRIDGE,
			Collections.emptyList(), Progress.NONE);
		assertEquals(1, none.size());
		assertTrue(none.get(0).getText().startsWith("None."));
	}
}
