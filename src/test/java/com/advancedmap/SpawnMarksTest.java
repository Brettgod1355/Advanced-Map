/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpawnMarksTest
{
	@BeforeClass
	public static void load()
	{
		Spawns.load();
		SpawnMarks.load();
	}

	private static MapSettings settings(boolean monsters, boolean items)
	{
		return sized(100, 100, 100, monsters, items);
	}

	private static MapSettings sized(int icons, int monsterSize, int itemSize, boolean monsters, boolean items)
	{
		return new MapSettings(EnumSet.allOf(IconGroup.class), Collections.emptySet(), Collections.emptySet(), false,
			true, false, false, true, true, RoutingPlugin.SHORTEST_PATH, FloorNumbers.GROUND_IS_FIRST, icons, true, true,
			monsters, items, monsterSize, itemSize);
	}

	/** The size of the first monster's or item's mark shown round Lumbridge. */
	private static int shownSize(MapSettings settings, boolean monster)
	{
		return SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0, settings).stream()
			.filter(s -> s.getMark().isMonster() == monster).findFirst().get().getBounds().width;
	}

	@Test
	public void aMarkCanBeHiddenWithARightClickAndShownFadedAgain()
	{
		// owner, 2026-11-09: monsters and ground items "need to be able to be right clicked hide this icon"
		SpawnMarks.Shown first = SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0, settings(true, true)).get(0);
		SpawnMarks.Mark mark = first.getMark();
		assertFalse(first.isHidden());
		MapSpot spot = MapSpot.spawn(mark);
		assertEquals(mark.getKey(), spot.getKey());
		assertTrue(spot.getKey().startsWith("s" + mark.getName() + "@"));
		assertEquals(mark.getName(), MapIcons.name(spot, Progress.NONE));
		assertTrue(MapIcons.wikiUrl(spot, Progress.NONE).endsWith("search="
			+ mark.getName().replace(' ', '+')));
		List<MapPainter.MenuEntry> menu = MapPainter.menuEntries(new MapPainter.Selection(spot, true, 10, 10,
			mark.getPoint()), settings(true, true));
		assertEquals(MapPainter.Action.HIDE, menu.get(0).getAction());
		assertTrue(menu.stream().anyMatch(e -> e.getAction() == MapPainter.Action.WIKI));
		// hidden: gone from the map, and its menu offers Unhide
		MapSettings hiding = hiddenSettings(Collections.singleton(spot.getKey()), false);
		assertTrue(hiding.isHidden(spot));
		assertTrue(SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0, hiding).stream()
			.noneMatch(s -> s.getMark().equals(mark)));
		assertEquals(MapPainter.Action.UNHIDE, MapPainter.menuEntries(new MapPainter.Selection(spot, true, 10, 10,
			mark.getPoint()), hiding).get(0).getAction());
		// "Show hidden icons": back, faded, so it can be unhidden
		SpawnMarks.Shown faded = SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0,
			hiddenSettings(Collections.singleton(spot.getKey()), true)).stream().filter(s -> s.getMark().equals(mark))
			.findFirst().get();
		assertTrue(faded.isHidden());
		// and the Hidden icons list finds it by name
		assertTrue(SpawnMarks.spots().stream().anyMatch(s -> s.getKey().equals(spot.getKey())));
	}

	private static MapSettings hiddenSettings(java.util.Set<String> hidden, boolean showHidden)
	{
		return new MapSettings(EnumSet.allOf(IconGroup.class), hidden, Collections.emptySet(), false, true, false,
			showHidden, true, true, RoutingPlugin.SHORTEST_PATH, FloorNumbers.GROUND_IS_FIRST, 100, true, true, true,
			true, 100, 100);
	}

	@Test
	public void aHoverAndAClickShowTheSameList()
	{
		// owner, 2026-11-09: "hovering to always show the same stuff on the screen as clicking"; a click keeps it
		MonsterInfo.load();
		WorldPoint a = new WorldPoint(3200, 3200, 0);
		WorldPoint b = new WorldPoint(3203, 3201, 0);
		List<SpawnMarks.Mark> marks = new java.util.ArrayList<>();
		SpawnMarks.build(Arrays.asList(new Spawns.Creature("Goblin", true, "2", Arrays.asList(a, b)),
			new Spawns.Creature("Egg", false, true, "", Arrays.asList(a, b), Collections.emptyMap())),
			Collections.emptyMap()).values().forEach(marks::addAll);
		SpawnMarks.Mark goblin = marks.stream().filter(SpawnMarks.Mark::isMonster).findFirst().get();
		SpawnMarks.Mark egg = marks.stream().filter(m -> !m.isMonster()).findFirst().get();
		List<SideList.Row> monster = SpawnMarks.rows(goblin);
		assertTrue(monster.size() > 1);
		assertEquals("2 spawns here", monster.get(monster.size() - 1).getText());
		assertEquals("2 spots here", SpawnMarks.rows(egg).get(0).getText());
	}

	@Test
	public void monsterAndGroundItemSizesAreTheirOwnSettings()
	{
		// owner, 2026-11-09: 16 px at 100%, the game's icons' size, each scaled on its own; Icon size leaves them be
		assertEquals(16, shownSize(settings(true, true), true));
		assertEquals(16, shownSize(settings(true, true), false));
		assertEquals(32, shownSize(sized(100, 200, 100, true, true), true));
		assertEquals(16, shownSize(sized(100, 200, 100, true, true), false));
		assertEquals(8, shownSize(sized(100, 100, 50, true, true), false));
		assertEquals(16, shownSize(sized(300, 100, 100, true, true), true));
	}

	private static MapViewport.View lumbridge(double zoom)
	{
		return new MapViewport.View(3222, 3218, zoom, 800, 600);
	}

	@Test
	public void aMonsterHasOneMarkForEachAreaItLivesIn()
	{
		WorldPoint a = new WorldPoint(3200, 3200, 0);
		WorldPoint b = new WorldPoint(3205, 3203, 0);
		WorldPoint far = new WorldPoint(3300, 3300, 0);
		Spawns.Creature goblin = new Spawns.Creature("Goblin", true, "2", Arrays.asList(a, b, far));
		Map<Integer, List<SpawnMarks.Mark>> index = SpawnMarks.build(Collections.singletonList(goblin),
			Collections.emptyMap());
		List<SpawnMarks.Mark> marks = new java.util.ArrayList<>();
		index.values().forEach(marks::addAll);
		assertEquals(2, marks.size());
		for (SpawnMarks.Mark mark : marks)
		{
			assertTrue(mark.isMonster());
			// the dots a hover shows are that area's spawns only
			assertEquals(mark.getTiles().contains(far) ? 1 : 2, mark.getTiles().size());
			assertTrue(mark.getTiles().contains(mark.getPoint()));
		}
		assertEquals("Goblin (level 2)", SpawnMarks.label(marks.get(0)));
	}

	@Test
	public void anItemHasOneMarkForEachAreaItLiesInWithItsGameId()
	{
		Spawns.Creature knife = new Spawns.Creature("Knife", false, true, "", Arrays.asList(
			new WorldPoint(3200, 3200, 0), new WorldPoint(3400, 3400, 1)), Collections.emptyMap());
		Map<String, Integer> ids = new HashMap<>();
		ids.put("Knife", 946);
		Map<Integer, List<SpawnMarks.Mark>> index = SpawnMarks.build(Collections.singletonList(knife), ids);
		assertEquals(2, index.size());
		SpawnMarks.Mark upstairs = index.get(1 << 16 | new WorldPoint(3400, 3400, 1).getRegionID()).get(0);
		assertFalse(upstairs.isMonster());
		assertEquals(946, upstairs.getItemId());
		assertEquals("Knife", SpawnMarks.label(upstairs));
		// owner, 2026-11-09: the eggs round a chicken pen are one mark, its hover showing every tile, as a monster's
		Spawns.Creature egg = new Spawns.Creature("Egg", false, true, "", Arrays.asList(new WorldPoint(3229, 3299, 0),
			new WorldPoint(3226, 3295, 0), new WorldPoint(3233, 3293, 0), new WorldPoint(3500, 3500, 0)),
			Collections.emptyMap());
		List<SpawnMarks.Mark> eggs = new java.util.ArrayList<>();
		SpawnMarks.build(Collections.singletonList(egg), ids).values().forEach(eggs::addAll);
		assertEquals(2, eggs.size());
		SpawnMarks.Mark pen = eggs.stream().filter(m -> m.getTiles().size() == 3).findFirst().get();
		assertTrue(pen.getTiles().contains(pen.getPoint()));
		assertEquals("Egg (3 spots)", SpawnMarks.label(pen));
		assertEquals("Egg", SpawnMarks.label(eggs.stream().filter(m -> m.getTiles().size() == 1).findFirst().get()));
	}

	@Test
	public void showsOnlyZoomedInAndAsTheFiltersSay()
	{
		List<SpawnMarks.Shown> both = SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0, settings(true, true));
		assertTrue(both.stream().anyMatch(s -> s.getMark().isMonster()));
		assertTrue(both.stream().anyMatch(s -> !s.getMark().isMonster()));
		// items under monsters: monsters are drawn last, and found first under the mouse
		int lastItem = -1;
		int firstMonster = Integer.MAX_VALUE;
		for (int i = 0; i < both.size(); i++)
		{
			if (both.get(i).getMark().isMonster())
			{
				firstMonster = Math.min(firstMonster, i);
			}
			else
			{
				lastItem = i;
			}
		}
		assertTrue(lastItem < firstMonster);
		for (SpawnMarks.Shown shown : both)
		{
			assertEquals(0, shown.getMark().getPoint().getPlane());
			SpawnMarks.Shown found = SpawnMarks.at(both, shown.getBounds().x + 1, shown.getBounds().y + 1);
			assertTrue(found != null);
		}
		assertTrue(both.stream().noneMatch(s -> s.getMark().getName().contains("Deadman")));
		assertTrue(SpawnMarks.visible(lumbridge(SpawnMarks.ZOOM - 0.5), MapArea.SURFACE, 0, settings(true, true))
			.isEmpty());
		assertTrue(SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0, settings(false, true)).stream()
			.noneMatch(s -> s.getMark().isMonster()));
		assertTrue(SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0, settings(true, false)).stream()
			.allMatch(s -> s.getMark().isMonster()));
		assertTrue(SpawnMarks.visible(lumbridge(6), MapArea.SURFACE, 0, settings(false, false)).isEmpty());
	}

	@Test
	public void everyGroundItemHasAGameItemId()
	{
		Set<String> named = new HashSet<>();
		for (String[] row : DataFiles.rows("item-ids.tsv"))
		{
			assertEquals(2, row.length);
			assertTrue(Integer.parseInt(row[1]) >= 0);
			named.add(row[0]);
		}
		int items = 0;
		int missing = 0;
		for (Spawns.Creature creature : Spawns.all())
		{
			if (creature.isItem())
			{
				items++;
				missing += named.contains(creature.getName()) ? 0 : 1;
			}
		}
		assertTrue(items > 300);
		// one the game has under no such name ("Bug lantern") is drawn as a dot
		assertTrue("missing " + missing, missing <= 2);
	}

	@Test
	public void aMonsterWithoutAPictureHasNone()
	{
		assertNull(SpawnMarks.picture("No such monster at all"));
		assertEquals("Monster_ what_", SpawnMarks.fileName("Monster: what?"));
	}

	@Test
	public void anItemPictureIsCutToTheItem()
	{
		BufferedImage item = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		item.setRGB(10, 5, 0xFFFFFFFF);
		item.setRGB(20, 25, 0xFFFFFFFF);
		BufferedImage cut = SpawnMarks.trim(item);
		assertEquals(11, cut.getWidth());
		assertEquals(21, cut.getHeight());
	}
}
