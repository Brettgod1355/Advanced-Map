/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.EntityOpsDefinition;
import com.advancedmap.cache.Location;
import com.advancedmap.cache.LocationsDefinition;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.MapSprite;
import com.advancedmap.cache.ObjectDefinition;
import com.advancedmap.cache.OverlayDefinition;
import com.advancedmap.cache.Position;
import com.advancedmap.cache.Region;
import com.advancedmap.cache.UnderlayDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FoundIconsTest
{
	/** The game's map area with the bank icon, as in the game's data. */
	private static final int BANK_AREA = 5;
	private static final int DUNGEON_AREA = 13;
	private static final int BOOTH = 100;
	private static final int CHEST_SPACE = 400;
	private static final int BUILT_CHEST = 401;
	private static final int EVENT_CHEST = 500;
	private static final int BUSH = 501;
	/** Charred Island's bank chest space, as the game's data has it: varbit 18353, unbuilt at 0, built at 1. */
	private static final int CHARRED_ISLAND = 58627;
	private static final int CHARRED_ISLAND_VARBIT = 18353;
	private static final int CULINAROMANCER = 12308;

	private static final Map<Integer, ObjectDefinition> OBJECTS = new HashMap<>();

	static
	{
		object(BOOTH, "Bank booth", "Bank", "Collect");
		object(CHEST_SPACE, "Bank chest space", "Build", "Inspect");
		object(BUILT_CHEST, "Bank chest", "Bank", "Collect");
		object(BUSH, "Bush");
		object(EVENT_CHEST, "Bank chest", "Use", "Collect");
		switching(CHARRED_ISLAND, CHARRED_ISLAND_VARBIT, CHEST_SPACE, BUILT_CHEST, -1);
		switching(CULINAROMANCER, 1850, -1, -1, -1, BUILT_CHEST, BUILT_CHEST, BUILT_CHEST, -1);
		switching(600, 3984, BUSH, EVENT_CHEST, -1);
	}

	private static void object(int id, String name, String... ops)
	{
		ObjectDefinition object = new ObjectDefinition();
		object.setId(id);
		object.setName(name);
		EntityOpsDefinition definition = new EntityOpsDefinition();
		for (int i = 0; i < ops.length; i++)
		{
			definition.setOp(i, ops[i]);
		}
		object.setOps(definition);
		OBJECTS.put(id, object);
	}

	private static void switching(int id, int varbit, int... states)
	{
		object(id, "null");
		OBJECTS.get(id).setVarbitID(varbit);
		OBJECTS.get(id).setConfigChangeDest(states);
	}

	private static final MapSource SOURCE = new MapSource()
	{
		@Override
		public Region region(int regionX, int regionY)
		{
			return null;
		}

		@Override
		public UnderlayDefinition underlay(int id)
		{
			return null;
		}

		@Override
		public OverlayDefinition overlay(int id)
		{
			return null;
		}

		@Override
		public ObjectDefinition object(int id)
		{
			return OBJECTS.getOrDefault(id, new ObjectDefinition());
		}

		@Override
		public int textureColor(int textureId)
		{
			return 0;
		}

		@Override
		public MapSprite mapScene(int index)
		{
			return null;
		}

		@Override
		public MapSprite areaIcon(int areaId)
		{
			return null;
		}

		@Override
		public int categoryArea(int category)
		{
			return category == IconGroup.BANK ? BANK_AREA : category == IconGroup.DUNGEON ? DUNGEON_AREA : -1;
		}
	};

	/** A region near Lumbridge with objects at local tiles: {id, x, y} each. */
	private static Region region(int[]... objects)
	{
		Region region = new Region(50, 50);
		LocationsDefinition locations = new LocationsDefinition();
		List<Location> list = new ArrayList<>();
		for (int[] object : objects)
		{
			list.add(new Location(object[0], 10, 0, new Position(object[1], object[2], 0)));
		}
		locations.setLocations(list);
		region.loadLocations(locations);
		return region;
	}

	private static MapSpot bankIcon(int localX, int localY)
	{
		return MapSpot.icon(3200 + localX, 3200 + localY, 0, BANK_AREA, IconGroup.BANK);
	}

	@Test
	public void boothsWithNoBankIconOfTheGamesNearGetOneIcon()
	{
		List<MapSpot> found = FoundIcons.find(region(new int[]{BOOTH, 10, 10}, new int[]{BOOTH, 10, 13}), SOURCE,
			Collections.emptyList());
		assertEquals(1, found.size());
		assertEquals("Bank booth", found.get(0).getDetail());
		assertEquals(IconGroup.BANKS, found.get(0).getGroup());
		assertEquals(4, found.get(0).getCluster().height);
	}

	@Test
	public void boothsNearABankIconOfTheGamesGetNone()
	{
		MapSpot icon = bankIcon(20, 10);
		List<MapSpot> found = FoundIcons.find(region(new int[]{BOOTH, 10, 10}, new int[]{BOOTH, 13, 10}), SOURCE,
			Collections.singletonList(icon));
		assertEquals(Collections.singletonList(icon), found);
	}

	@Test
	public void aBankWithOnlyTheBankTutorsIconGetsNoOtherIcon()
	{
		// The Node: a bank chest and booth, and the game's bank tutor icon two tiles south (area 576)
		MapSpot tutor = MapSpot.icon(3225, 3223, 0, 576, IconGroup.BANK_TUTOR);
		List<MapSpot> found = FoundIcons.find(region(new int[]{BOOTH, 25, 25}, new int[]{BOOTH, 25, 26}), SOURCE,
			Collections.singletonList(tutor));
		assertEquals(Collections.singletonList(tutor), found);
	}

	@Test
	public void aBuildableChestMarksTheGamesIconOnIt()
	{
		MapSpot icon = bankIcon(31, 31);
		List<MapSpot> found = FoundIcons.find(region(new int[]{CHARRED_ISLAND, 30, 30}), SOURCE,
			Collections.singletonList(icon));
		assertEquals(1, found.size());
		assertEquals(icon.getKey(), found.get(0).getKey());
		assertEquals(Unlockable.CHARRED_ISLAND, found.get(0).getUnlockable());
		assertEquals(CHARRED_ISLAND_VARBIT, found.get(0).getUnlock().getVariable());
	}

	@Test
	public void theCulinaromancersChestGetsAnIconOfItsOwn()
	{
		List<MapSpot> found = FoundIcons.find(region(new int[]{CULINAROMANCER, 19, 23}), SOURCE,
			Collections.emptyList());
		assertEquals(1, found.size());
		assertEquals(BANK_AREA, found.get(0).getAreaId());
		assertEquals(Unlockable.CULINAROMANCERS_CHEST, found.get(0).getUnlockable());
		Unlock unlock = found.get(0).getUnlock();
		assertFalse(unlock.isOpen(0));
		assertTrue(unlock.isOpen(3));
		assertTrue(unlock.isOpen(5));
		assertFalse(unlock.isOpen(6));
		assertFalse(unlock.isOpen(-1));
	}

	@Test
	public void anUnbuiltChestIsGrayAndSaysWhatItNeeds()
	{
		MapSpot chest = FoundIcons.find(region(new int[]{CHARRED_ISLAND, 30, 30}), SOURCE,
			Collections.singletonList(bankIcon(30, 30))).get(0);
		MapSettings settings = new MapSettings(EnumSet.allOf(IconGroup.class), Collections.emptySet(),
			Collections.emptySet(), false, true, false, false, true, true, RoutingPlugin.SHORTEST_PATH,
			FloorNumbers.GROUND_IS_FIRST, 100);
		Progress unbuilt = progress(true, 0);
		Progress built = progress(true, 1);
		assertEquals(MapIcons.Look.LOCKED, MapIcons.look(chest, settings, unbuilt));
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(chest, settings, built));
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(chest, settings, Progress.NONE));
		assertEquals("Bank chest, Charred Island", MapIcons.name(chest, unbuilt));

		List<String> lines = MapIcons.info(chest, unbuilt, false, FloorNumbers.GROUND_IS_FIRST).stream()
			.map(MapIcons.Line::getText).collect(Collectors.toList());
		assertTrue(lines.contains("You have not built it yet"));
		assertTrue(lines.contains("Needs 70 Construction (you have 50)"));
		assertTrue(lines.contains("Needs 60 Sailing (you have 50)"));
		assertTrue(lines.contains("  4 ironwood planks"));
		assertTrue(MapIcons.info(chest, built, false, FloorNumbers.GROUND_IS_FIRST).stream()
			.anyMatch(line -> line.getText().equals("You have built it") && line.getColor() == MapIcons.GOOD));
	}

	@Test
	public void anEventChestShowsOnlyWhileItIsABank()
	{
		List<MapSpot> found = FoundIcons.find(region(new int[]{600, 20, 17}), SOURCE, Collections.emptyList());
		assertEquals(1, found.size());
		MapSpot chest = found.get(0);
		assertNull(chest.getUnlockable());
		MapSettings settings = new MapSettings(EnumSet.allOf(IconGroup.class), Collections.emptySet(),
			Collections.emptySet(), false, true, false, false, true, true, RoutingPlugin.SHORTEST_PATH,
			FloorNumbers.GROUND_IS_FIRST, 100);
		Map<Integer, Integer> on = Collections.singletonMap(3984, 1);
		assertNull(MapIcons.look(chest, settings, Progress.NONE));
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(chest, settings, new Progress(true,
			new int[Skill.values().length], new EnumMap<>(Quest.class), Collections.emptyMap(), Collections.emptyMap(),
			on)));
	}

	private static Progress progress(boolean loggedIn, int charredIsland)
	{
		int[] levels = new int[Skill.values().length];
		levels[Skill.CONSTRUCTION.ordinal()] = 50;
		levels[Skill.SAILING.ordinal()] = 50;
		return new Progress(loggedIn, levels, new EnumMap<>(Quest.class), Collections.emptyMap(),
			Collections.emptyMap(), Collections.singletonMap(CHARRED_ISLAND_VARBIT, charredIsland));
	}
}
