/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.game.AgilityShortcut;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapIconsTest
{
	private static final int QUEST_ICON = 715;

	private static MapSettings settings(Set<String> hidden, boolean hideCompleted, boolean showHidden, boolean iconsOff)
	{
		return new MapSettings(EnumSet.allOf(IconGroup.class), hidden, Collections.emptySet(), hideCompleted, true,
			iconsOff, showHidden, true, true, RoutingPlugin.SHORTEST_PATH, FloorNumbers.GROUND_IS_FIRST, 100, true, false, true);
	}

	private static Progress progress(int agility, int magic, QuestState cooksAssistant)
	{
		int[] levels = new int[Skill.values().length];
		levels[Skill.AGILITY.ordinal()] = agility;
		levels[Skill.MAGIC.ordinal()] = magic;
		Map<Quest, QuestState> states = new EnumMap<>(Quest.class);
		for (Quest quest : Quest.values())
		{
			states.put(quest, QuestState.NOT_STARTED);
		}
		Map<Integer, Progress.QuestInfo> quests = new HashMap<>();
		quests.put(QUEST_ICON, new Progress.QuestInfo("Cook's Assistant", cooksAssistant, 0, 0, false, new int[0]));
		return new Progress(true, levels, states, quests, Collections.singletonMap(IconGroup.QUEST, "Quest"),
			Collections.emptyMap());
	}

	@Test
	public void aHiddenIconIsLeftOutUnlessHiddenIconsAreShown()
	{
		MapSpot bank = MapSpot.icon(3208, 3220, 0, 0, 1055);
		Set<String> hidden = Collections.singleton(bank.getKey());
		assertNull(MapIcons.look(bank, settings(hidden, false, false, false), Progress.NONE));
		assertEquals(MapIcons.Look.HIDDEN, MapIcons.look(bank, settings(hidden, false, true, false), Progress.NONE));
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(bank, settings(Collections.emptySet(), false, false, false),
			Progress.NONE));
	}

	@Test
	public void hidingOneIconLeavesTheSameKindElsewhere()
	{
		MapSpot here = MapSpot.icon(3208, 3220, 0, 0, 1055);
		MapSpot there = MapSpot.icon(3092, 3243, 0, 0, 1055);
		// each hidden icon is its own setting, keyed by the icon: one bank hidden, not the other
		MapSettings hidden = settings(Collections.singleton(here.getKey()), false, false, false);
		assertTrue(hidden.isHidden(here));
		assertFalse(hidden.isHidden(there));
		// the key has no dot, so "hidden.<key>" reads back whole from the settings' names
		assertFalse(here.getKey().contains("."));
		// an earlier version's one setting reads as before, for the move to a setting each
		assertEquals(2, MapSettings.parseHidden(here.getKey() + MapSettings.SEPARATOR + there.getKey()).size());
	}

	@Test
	public void finishedQuestsAreMarkedOrLeftOut()
	{
		MapSpot quest = MapSpot.icon(3208, 3214, 0, QUEST_ICON, IconGroup.QUEST);
		Progress done = progress(1, 1, QuestState.FINISHED);
		assertEquals(MapIcons.Look.DONE, MapIcons.look(quest, settings(Collections.emptySet(), false, false, false), done));
		assertNull(MapIcons.look(quest, settings(Collections.emptySet(), true, false, false), done));
		assertEquals(MapIcons.Look.STARTED, MapIcons.look(quest, settings(Collections.emptySet(), true, false, false),
			progress(1, 1, QuestState.IN_PROGRESS)));
		assertEquals("Cook's Assistant", MapIcons.name(quest, done));
	}

	@Test
	public void aShortcutAboveTheAgilityLevelIsLocked()
	{
		AgilityShortcut shortcut = null;
		for (AgilityShortcut candidate : AgilityShortcut.values())
		{
			if (candidate.getWorldMapLocation() != null && candidate.getLevel() > 30)
			{
				shortcut = candidate;
				break;
			}
		}
		assertNotNull(shortcut);
		MapSpot spot = MapSpot.icon(shortcut.getWorldMapLocation().getX(), shortcut.getWorldMapLocation().getY(), 0, 0,
			IconGroup.AGILITY_SHORTCUT);
		assertNotNull(spot.getShortcut());
		MapSettings shown = settings(Collections.emptySet(), false, false, false);
		assertEquals(MapIcons.Look.LOCKED, MapIcons.look(spot, shown, progress(spot.getShortcut().getLevel() - 1, 1,
			QuestState.NOT_STARTED)));
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(spot, shown, progress(spot.getShortcut().getLevel(), 1,
			QuestState.NOT_STARTED)));
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(spot, shown, Progress.NONE));
	}

	@Test
	public void aShortcutIconFarFromAnyKnownShortcutHasNoLevel()
	{
		assertNull(MapSpot.nearestShortcut(10, 10));
	}

	@Test
	public void teleportSpellsNeedTheirLevelAndQuest()
	{
		MapSettings shown = settings(Collections.emptySet(), false, false, false);
		MapSpot varrock = MapSpot.teleport(Teleport.named("VARROCK"));
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(varrock, shown, progress(1, 25, QuestState.NOT_STARTED)));
		assertEquals(MapIcons.Look.LOCKED, MapIcons.look(varrock, shown, progress(1, 24, QuestState.NOT_STARTED)));
		// Ancient Magicks need Desert Treasure I, not started here
		assertEquals(MapIcons.Look.LOCKED, MapIcons.look(MapSpot.teleport(Teleport.named("PADDEWWA")), shown,
			progress(1, 99, QuestState.NOT_STARTED)));
		assertEquals(Quest.PLAGUE_CITY, MapIcons.requiredQuest(Teleport.named("ARDOUGNE")));
		assertNull(MapIcons.requiredQuest(Teleport.named("LUMBRIDGE")));
		// a teleport by item is never marked: whether the player has the item is not known
		assertEquals(MapIcons.Look.NORMAL, MapIcons.look(MapSpot.teleport(Teleport.named("BARBARIAN_ASSAULT")), shown,
			progress(1, 1, QuestState.NOT_STARTED)));
	}

	@Test
	public void theInfoBoxSaysWhatIsMissing()
	{
		List<MapIcons.Line> lines = MapIcons.info(MapSpot.teleport(Teleport.named("VARROCK")), progress(1, 20, QuestState.NOT_STARTED),
			false, FloorNumbers.GROUND_IS_FIRST);
		assertTrue(lines.stream().anyMatch(line -> line.getText().equals("Needs 25 Magic (you have 20)")
			&& line.getColor().equals(MapIcons.BAD)));
		assertEquals("Varrock Teleport", Teleport.named("VARROCK").title());
		assertEquals("Games Necklace: Barbarian Assault", Teleport.named("BARBARIAN_ASSAULT").title());
		// the game's own spelling of its names, as the wiki page of Kharedst's memoirs writes it
		assertEquals("Kharedst's Memoirs: Jewellery of Jubilation (Lovakengj)", Teleport.named("KHAREDSTS_MEMOIRS_LOVAKENGJ").title());
	}

	@Test
	public void noIconsWhenZoomedOutOrTurnedOff()
	{
		MapIcons icons = new MapIcons();
		MapSettings on = settings(Collections.emptySet(), false, false, false);
		MapViewport.View near = new MapViewport.View(3213, 3424, 4, 800, 600);
		assertFalse(icons.visible(near, MapArea.SURFACE, 0, on, Progress.NONE).isEmpty());
		MapViewport.View far = new MapViewport.View(3213, 3424, MapIcons.ICON_ZOOM / 2, 800, 600);
		assertTrue(icons.visible(far, MapArea.SURFACE, 0, on, Progress.NONE).isEmpty());
		assertTrue(icons.visible(near, MapArea.SURFACE, 0, settings(Collections.emptySet(), false, false, true),
			Progress.NONE).isEmpty());
	}

	@Test
	public void aClickFindsTheIconUnderIt()
	{
		MapIcons icons = new MapIcons();
		MapViewport.View view = new MapViewport.View(3213.5, 3424.5, 4, 800, 600);
		List<MapIcons.Shown> shown = icons.visible(view, MapArea.SURFACE, 0, settings(Collections.emptySet(), false, false,
			false), Progress.NONE);
		MapIcons.Shown varrock = MapIcons.at(shown, 400, 300);
		assertNotNull(varrock);
		assertEquals(Teleport.named("VARROCK"), varrock.getSpot().getTeleport());
		assertNull(MapIcons.at(shown, 5, 5));
	}

	@Test
	public void everyCategoryBelongsToAGroup()
	{
		assertEquals(IconGroup.BANKS, IconGroup.of(1055));
		assertEquals(IconGroup.QUESTS, IconGroup.of(IconGroup.QUEST));
		assertEquals(IconGroup.AGILITY, IconGroup.of(IconGroup.AGILITY_SHORTCUT));
		assertEquals(IconGroup.OTHER, IconGroup.of(99999));
	}

	@Test
	public void zoomedOutTheGroundShowsEveryFloorsIconsAndZoomedInEachFloorOnlyItsOwn()
	{
		MapIcons icons = new MapIcons();
		icons.addSprite(0, com.advancedmap.cache.MapSprite.of(15, 15, 0, 0, 1, 1, new int[]{0xFFFFFF}));
		// Lumbridge Castle's bank is on its top floor
		MapSpot bank = MapSpot.icon(3208, 3220, 2, 0, 1055);
		icons.addRegion(3208 >> 6 << 8 | 3220 >> 6, Collections.singletonList(bank));
		MapSettings on = settings(Collections.emptySet(), false, false, false);
		MapViewport.View out = new MapViewport.View(3208, 3220, MapIcons.OWN_FLOOR_ZOOM - 0.5, 800, 600);
		assertTrue(has(icons.visible(out, MapArea.SURFACE, 0, on, Progress.NONE), bank));
		assertTrue(has(icons.visible(out, MapArea.SURFACE, 2, on, Progress.NONE), bank));
		assertFalse(has(icons.visible(out, MapArea.SURFACE, 1, on, Progress.NONE), bank));
		MapViewport.View in = new MapViewport.View(3208, 3220, MapIcons.OWN_FLOOR_ZOOM, 800, 600);
		assertFalse(has(icons.visible(in, MapArea.SURFACE, 0, on, Progress.NONE), bank));
		assertTrue(has(icons.visible(in, MapArea.SURFACE, 2, on, Progress.NONE), bank));
		assertFalse(has(icons.visible(in, MapArea.SURFACE, 1, on, Progress.NONE), bank));
	}

	@Test
	public void floorsAreNumberedFromTheGroundOrFromTheFloorAboveIt()
	{
		assertEquals("Ground floor", FloorNumbers.GROUND_IS_FIRST.name(0));
		assertEquals("Floor 2", FloorNumbers.GROUND_IS_FIRST.name(1));
		assertEquals("Floor 4", FloorNumbers.GROUND_IS_FIRST.name(3));
		assertEquals("Ground floor", FloorNumbers.FIRST_ABOVE_GROUND.name(0));
		assertEquals("Floor 1", FloorNumbers.FIRST_ABOVE_GROUND.name(1));
		// the floor buttons' short names
		assertEquals("G", FloorNumbers.GROUND_IS_FIRST.shortName(0));
		assertEquals("2", FloorNumbers.GROUND_IS_FIRST.shortName(1));
		assertEquals("1", FloorNumbers.FIRST_ABOVE_GROUND.shortName(1));
	}

	private static boolean has(List<MapIcons.Shown> shown, MapSpot spot)
	{
		return shown.stream().anyMatch(icon -> icon.getSpot().equals(spot));
	}
}
