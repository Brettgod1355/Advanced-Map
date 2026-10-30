/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MapFiltersTest
{
	/** Bank (1055), General store (1049), Sword shop (1050), Quest (1056), each with a sample map area. */
	private static final Map<Integer, Integer> CATEGORIES = new HashMap<>();
	private static final Progress NAMES;

	static
	{
		CATEGORIES.put(1055, 6);
		CATEGORIES.put(1049, 0);
		CATEGORIES.put(1050, 1);
		CATEGORIES.put(1056, 700);
		Map<Integer, String> names = new HashMap<>();
		names.put(1055, "Bank");
		names.put(1049, "General store");
		names.put(1050, "Sword shop");
		names.put(1056, "Quest");
		NAMES = new Progress(false, new int[Skill.values().length], new EnumMap<>(Quest.class), Collections.emptyMap(),
			names, Collections.emptyMap());
	}

	private static MapSettings settings(Set<IconGroup> groups, Set<String> hiddenTypes)
	{
		return new MapSettings(groups, Collections.emptySet(), hiddenTypes, false, true, false, false, true, true,
			RoutingPlugin.SHORTEST_PATH, FloorNumbers.GROUND_IS_FIRST, 100, true, false, true);
	}

	@Test
	public void everyGroupIsListedWithItsKindsByName()
	{
		List<MapFilters.Entry> entries = MapFilters.entries(CATEGORIES, NAMES, "");
		assertEquals("Banks", entries.get(0).getName());
		assertTrue(entries.get(0).isGroup());
		assertEquals("Bank", entries.get(1).getName());
		int shops = indexOf(entries, "Shops");
		assertEquals("General store", entries.get(shops + 1).getName());
		assertEquals("Sword shop", entries.get(shops + 2).getName());
		// the teleports' kinds are Advanced Map's own
		int teleports = indexOf(entries, "Teleports");
		assertEquals(Teleport.Type.NORMAL_MAGIC, entries.get(teleports + 1).getTeleportType());
		// every group is there, kinds or not
		assertEquals(IconGroup.values().length, entries.stream().filter(MapFilters.Entry::isGroup).count());
	}

	@Test
	public void searchingKeepsMatchingKindsAndWholeMatchingGroups()
	{
		List<MapFilters.Entry> sword = MapFilters.entries(CATEGORIES, NAMES, "SWORD");
		assertEquals(2, sword.size());
		assertEquals("Shops", sword.get(0).getName());
		assertEquals("Sword shop", sword.get(1).getName());
		List<MapFilters.Entry> shops = MapFilters.entries(CATEGORIES, NAMES, "shops");
		assertEquals(3, shops.size());
		assertTrue(MapFilters.entries(CATEGORIES, NAMES, "zzz").isEmpty());
	}

	@Test
	public void aClickFindsTheRowUnderIt()
	{
		MapFilters filters = new MapFilters();
		filters.setCategoryIcons(CATEGORIES);
		filters.setOpen(true);
		Rectangle button = MapPainter.buttons(1000, 700, false).get(MapPainter.Button.FILTERS);
		MapFilters.Layout layout = MapFilters.layout(button, 700);
		assertTrue(layout.getPanel().y > button.y + button.height - 1);
		assertTrue(layout.getPanel().y + layout.getPanel().height <= 700);
		List<MapFilters.Entry> entries = filters.entries(NAMES);
		Rectangle list = layout.getList();
		MapFilters.Hit second = filters.hitAt(list.x + 50, list.y + MapFilters.ROW + 5, layout, entries);
		assertEquals(MapFilters.Part.ENTRY, second.getPart());
		assertEquals("Bank", second.getEntry().getName());
		assertEquals(MapFilters.Part.ALL_OFF, filters.hitAt(layout.getAllOff().x + 5, layout.getAllOff().y + 5, layout,
			entries).getPart());
		assertEquals(MapFilters.Part.OUTSIDE, filters.hitAt(5, 690, layout, entries).getPart());
		Rectangle showHidden = layout.getShowHidden();
		assertEquals(MapFilters.Part.SHOW_HIDDEN, filters.hitAt(showHidden.x + 5, showHidden.y + 5, layout, entries)
			.getPart());
		assertTrue(showHidden.y + showHidden.height < layout.getSearch().y);
	}

	@Test
	public void aKindTurnedOffHidesOnlyThatKind()
	{
		MapSpot sword = MapSpot.icon(3208, 3220, 0, 1, 1050);
		MapSpot general = MapSpot.icon(3210, 3220, 0, 0, 1049);
		MapSettings settings = settings(EnumSet.allOf(IconGroup.class), Collections.singleton("c1050"));
		assertFalse(settings.shows(sword));
		assertTrue(settings.shows(general));
		assertEquals(IconGroup.SHOPS, MapSettings.groupOf("c1050"));
		assertEquals(IconGroup.TELEPORTS, MapSettings.groupOf(MapSettings.typeKey(Teleport.Type.JEWELLERY)));
		assertFalse(MapFilters.isOn(MapFilters.entries(CATEGORIES, NAMES, "sword").get(1), settings));
		// a group turned off turns all its kinds off
		MapSettings noShops = settings(EnumSet.complementOf(EnumSet.of(IconGroup.SHOPS)), Collections.emptySet());
		assertFalse(noShops.shows(general));
		assertFalse(MapFilters.isOn(MapFilters.entries(CATEGORIES, NAMES, "general").get(1), noShops));
	}

	@Test
	public void aGroupKnowsItsKinds()
	{
		MapFilters filters = new MapFilters();
		filters.setCategoryIcons(CATEGORIES);
		assertEquals(2, filters.keysOf(IconGroup.SHOPS).size());
		assertEquals(Teleport.Type.values().length, filters.keysOf(IconGroup.TELEPORTS).size());
	}

	@Test
	public void typingSearchesAndBackspaceTakesALetterBack()
	{
		MapFilters filters = new MapFilters();
		filters.type('b');
		filters.type('a');
		filters.backspace();
		assertEquals("b", filters.search());
		filters.backspace();
		filters.backspace();
		assertEquals("", filters.search());
	}

	private static int indexOf(List<MapFilters.Entry> entries, String name)
	{
		for (int i = 0; i < entries.size(); i++)
		{
			if (entries.get(i).getName().equals(name))
			{
				return i;
			}
		}
		return -1;
	}

	@Test
	public void theTypedFilterGoesWithTheListWhenItCloses()
	{
		MapFilters filters = new MapFilters();
		filters.setOpen(true);
		filters.type('f');
		filters.type('i');
		assertEquals("fi", filters.search());
		// closed by Escape, a click elsewhere, or the plugin stopping: the text would otherwise filter the next opening
		// (and the next session, since RuneLite keeps the plugin object)
		filters.setOpen(false);
		assertEquals("", filters.search());
		filters.setOpen(true);
		assertEquals("", filters.search());
	}
}
