/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.Quest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SailingDocksTest
{
	private static MapLabels.Label label(int x, int y, String... lines)
	{
		return new MapLabels.Label(Arrays.asList(lines), x, y, MapLabels.TOWN, Color.WHITE);
	}

	@Test
	public void everyDockHasItsLevelAndQuest()
	{
		assertEquals(57, SailingDocks.all().size());
		SailingDocks.Dock corsairCove = SailingDocks.all().stream().filter(d -> d.getName().equals("Corsair Cove"))
			.findFirst().orElse(null);
		assertEquals(40, corsairCove.getLevel());
		assertEquals(Quest.THE_CORSAIR_CURSE, corsairCove.getQuest());
		// every quest named is one RuneLite knows
		for (SailingDocks.Dock dock : SailingDocks.all())
		{
			assertEquals(dock.getName(), dock.getQuest() == null, !Arrays.asList("Civitas illa Fortis", "Corsair Cove",
				"Sunset Coast", "The Summer Shore", "The Little Pearl", "Aldarin", "Rellekka", "Etceteria", "Port Tyras",
				"Jatizso", "Neitiznot", "Prifddinas", "Waterbirth Island", "Piscatoris", "Lunar Isle", "Weiss")
				.contains(dock.getName()));
		}
	}

	@Test
	public void aDockingIconTakesTheNearestDocksLabel()
	{
		// Port Sarim's dock lies nearer its own label than Rimmington's, a label on two lines matches whole
		SailingDocks.place(Arrays.asList(label(3025, 3220, "Port Sarim"), label(2957, 3214, "Rimmington"),
			label(2580, 2850, "Deepfin", "Point"), label(3222, 3218, "Lumbridge")));
		assertEquals("Port Sarim", SailingDocks.near(3051, 3193).getName());
		assertEquals("Rimmington", SailingDocks.near(2906, 3225).getName());
		assertEquals(67, SailingDocks.near(2600, 2860).getLevel());
		// no dock's label within reach
		assertNull(SailingDocks.near(1200, 1200));
		SailingDocks.place(Collections.emptyList());
		assertNull(SailingDocks.near(3051, 3193));
	}
}
