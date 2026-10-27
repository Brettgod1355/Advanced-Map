/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FarmingTest
{
	/** A time on a 20-minute farming tick plus 800 seconds. */
	private static final long SAVED = 1_700_000_000L;

	@BeforeClass
	public static void load()
	{
		Farming.load();
	}

	private static Farming.Site falador()
	{
		Farming.Site site = Farming.siteNear(3052, 3309);
		assertNotNull(site);
		return site;
	}

	@Test
	public void faladorsSiteHasItsPatches()
	{
		assertEquals(60, Farming.sites().size());
		assertEquals(5, falador().getPatches().size());
		assertNull(Farming.siteNear(3222, 3218));
	}

	@Test
	public void aGrowingHerbIsReadyAfterItsLastTick()
	{
		Farming.Patch herb = falador().getPatches().stream().filter(p -> p.getKind().equals("HERB")).findFirst().get();
		Map<String, String> saved = new HashMap<>();
		// Ranarr at its third of five stages, 20-minute ticks: two ticks to go from the tick before the save
		saved.put("12083.4774", "34:" + SAVED);
		Farming.Reading early = Farming.read(herb, saved::get, SAVED + 100);
		assertEquals("Ranarr", early.getProduce());
		assertEquals(Farming.Status.GROWING, early.getStatus());
		assertEquals(SAVED - 800 + 2 * 1200, early.getDone());
		assertEquals(Farming.Status.READY, Farming.read(herb, saved::get, SAVED + 2000).getStatus());
		saved.put("12083.4774", "37:" + SAVED);
		assertEquals(Farming.Status.READY, Farming.read(herb, saved::get, SAVED).getStatus());
		saved.put("12083.4774", "2:" + SAVED);
		assertEquals(Farming.Status.EMPTY, Farming.read(herb, saved::get, SAVED).getStatus());
		saved.remove("12083.4774");
		assertNull(Farming.read(herb, saved::get, SAVED));
	}

	@Test
	public void aSiteIsReadyWhenAnyPatchIs()
	{
		Map<String, String> saved = new HashMap<>();
		saved.put("12083.4774", "37:" + SAVED);
		saved.put("12083.4773", "2:" + SAVED);
		List<Farming.Reading> readings = Farming.readings(falador(), saved::get, SAVED);
		assertEquals(2, readings.size());
		assertEquals(Farming.Status.READY, Farming.summary(readings));
		assertTrue(Farming.timeLeft(5400).startsWith("1h 30m"));
		assertEquals("45m", Farming.timeLeft(2700));
	}
}
