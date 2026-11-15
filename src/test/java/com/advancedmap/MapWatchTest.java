/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class MapWatchTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint CELLAR = new WorldPoint(3209, 9616, 0);
	private static final MapSpot TRAPDOOR = MapSpot.icon(3220, 3216, 0, 1, IconGroup.DUNGEON);

	@Test
	public void aFindingIsCountedOncePerSightingAndComesBackFromItsFile()
	{
		MapWatch.Log log = new MapWatch.Log();
		long start = 1_800_000_000_000L;
		assertTrue(log.note(new String[]{"monster-unlisted", "Goblin", "level 2,\tseen here"}, start));
		assertTrue(log.takeChanged());
		// seen again a minute later: the same sighting, its detail brought up to date
		assertFalse(log.note(new String[]{"monster-unlisted", "Goblin", "level 5"}, start + 60_000));
		assertFalse(log.takeChanged());
		assertTrue(log.note(new String[]{"monster-unlisted", "Goblin", "level 5"}, start + MapWatch.AGAIN));
		assertTrue(log.note(new String[]{"fps", "map open", "slow"}, start));
		assertFalse(log.note(null, start));

		List<String> lines = log.lines();
		assertEquals(3, lines.size());
		assertEquals(MapWatch.HEADER, lines.get(0));
		assertTrue(lines.get(1), lines.get(1).startsWith("fps\t1\t"));
		assertTrue(lines.get(2), lines.get(2).startsWith("monster-unlisted\t2\t"));
		assertTrue(lines.get(2), lines.get(2).endsWith("\tGoblin\tlevel 5"));

		MapWatch.Log again = new MapWatch.Log();
		again.read(lines);
		again.read(Collections.singletonList("a line somebody typed"));
		assertEquals(lines, again.lines());
		// and goes on counting from the file's count
		assertTrue(again.note(new String[]{"monster-unlisted", "Goblin", "level 5"}, start + 3 * MapWatch.AGAIN));
		assertTrue(again.lines().get(2).startsWith("monster-unlisted\t3\t"));
	}

	@Test
	public void aJumpIsJudgedByTheDungeonIconItBeganAt()
	{
		assertFalse(MapWatch.jumped(LUMBRIDGE, new WorldPoint(3230, 3225, 1)));
		assertTrue(MapWatch.jumped(LUMBRIDGE, CELLAR));
		assertTrue(MapWatch.jumped(LUMBRIDGE, new WorldPoint(3222 + MapWatch.JUMP + 1, 3218, 0)));

		List<MapSpot> spots = Arrays.asList(MapSpot.icon(3221, 3217, 0, 2, IconGroup.BANK), TRAPDOOR,
			MapSpot.icon(3230, 3230, 0, 1, IconGroup.DUNGEON));
		assertEquals(TRAPDOOR, MapWatch.dungeonIconNear(spots, LUMBRIDGE));
		assertNull(MapWatch.dungeonIconNear(spots, new WorldPoint(3100, 3100, 0)));

		String used = "\"Climb-down Trapdoor\" (id 14880)";
		// a spell or an item: never a finding
		assertNull(MapWatch.jump(LUMBRIDGE, CELLAR, null, TRAPDOOR, null, false));
		// the icon leads where the player arrived
		assertNull(MapWatch.jump(LUMBRIDGE, CELLAR, used, TRAPDOOR, new WorldPoint(3210, 9620, 0), false));
		String[] missing = MapWatch.jump(LUMBRIDGE, CELLAR, used, TRAPDOOR, null, false);
		assertEquals("link-missing", missing[0]);
		assertEquals("dungeon icon 3220, 3216, 0", missing[1]);
		assertTrue(missing[2], missing[2].contains("arrived at 3209, 9616, 0"));
		// no link, but the map knows a transport from there to there
		assertNull(MapWatch.jump(LUMBRIDGE, CELLAR, used, TRAPDOOR, null, true));
		String[] wrong = MapWatch.jump(LUMBRIDGE, CELLAR, used, TRAPDOOR, new WorldPoint(2900, 9800, 0), false);
		assertEquals("link-wrong", wrong[0]);
		assertTrue(wrong[2], wrong[2].contains("leads to 2900, 9800, 0"));
		// away from any dungeon icon: the map must know the way
		assertNull(MapWatch.jump(LUMBRIDGE, CELLAR, used, null, null, true));
		String[] unlisted = MapWatch.jump(LUMBRIDGE, CELLAR, used, null, null, false);
		assertEquals("transport-unlisted", unlisted[0]);
		assertTrue(unlisted[1], unlisted[1].startsWith(used + " in region 12850 near 3216, 3216, 0"));
	}

	@Test
	public void aMonsterOrAnItemIsJudgedAgainstTheMapsLists()
	{
		List<Spawns.Creature> all = Arrays.asList(
			new Spawns.Creature("Goblin (Lumbridge)", true, false, "2, 5", Collections.singletonList(LUMBRIDGE),
				Collections.emptyMap()),
			new Spawns.Creature("Goblin", true, false, "2", Collections.singletonList(new WorldPoint(2950, 3500, 0)),
				Collections.emptyMap()),
			new Spawns.Creature("Bronze dagger", false, true, "", Collections.singletonList(LUMBRIDGE),
				Collections.emptyMap()));
		Map<String, List<WorldPoint>> monsters = MapWatch.listed(all, false);
		Map<String, List<WorldPoint>> items = MapWatch.listed(all, true);
		assertEquals(Collections.singleton("goblin"), monsters.keySet());
		assertEquals(2, monsters.get("goblin").size());
		assertEquals(Collections.singleton("bronze dagger"), items.keySet());

		assertNull(MapWatch.monster("Goblin", 2, new WorldPoint(3222 + MapWatch.AWAY, 3218, 0), monsters));
		String[] away = MapWatch.monster("Goblin", 2, new WorldPoint(3400, 3218, 0), monsters);
		assertEquals("monster-away", away[0]);
		assertTrue(away[2], away[2].contains("178 tiles from the nearest listed spot"));
		// listed only on the surface: underground there is none
		assertTrue(MapWatch.monster("Goblin", 2, CELLAR, monsters)[2].contains("none listed on this map"));
		String[] unlisted = MapWatch.monster("Imp", 7, LUMBRIDGE, monsters);
		assertEquals("monster-unlisted", unlisted[0]);
		assertEquals("Imp", unlisted[1]);

		// beside its listed tile: the same spawn, noted as off its tile (no longer nothing; owner, 2026-11-15)
		assertEquals("item-off", MapWatch.groundItem("Bronze dagger", new WorldPoint(3224, 3220, 0), items)[0]);
		assertEquals("item-unlisted", MapWatch.groundItem("Bronze dagger", new WorldPoint(3240, 3218, 0), items)[0]);
		String[] bones = MapWatch.groundItem("Bones", LUMBRIDGE, items);
		assertEquals("Bones at 3222, 3218, 0", bones[1]);
		assertTrue(bones[2], bones[2].contains("no spawn of it anywhere"));
	}

	@Test
	public void aShopsStockIsComparedItemByItemAndAPlayersSaleIsToldApart()
	{
		Shops.Shop axes = new Shops.Shop("Bob's Axes", LUMBRIDGE, "Bob", Arrays.asList(
			new Shops.Item("Bronze axe", "16", "", "10"), new Shops.Item("Iron axe", "56", "", "5")), 0);
		Shops.Shop general = new Shops.Shop("Lumbridge General Store", LUMBRIDGE, "Shop keeper", Arrays.asList(
			new Shops.Item("Pot", "1", "", "5"), new Shops.Item("Jug", "1", "", "2"),
			new Shops.Item("Bucket", "2", "", "3")), 0);
		// the shop whose stock is most like the window's, not merely the first near
		assertEquals(general, MapWatch.shopFor(Arrays.asList(axes, general), Arrays.asList("Pot", "Jug")));
		assertEquals(axes, MapWatch.shopFor(Arrays.asList(axes, general), Arrays.asList("iron axe")));
		assertNull(MapWatch.shopFor(Collections.emptyList(), Arrays.asList("Pot")));

		assertTrue(MapWatch.shop(axes, LUMBRIDGE, Arrays.asList("iron axe", "Bronze axe")).isEmpty());
		// a shop that is no general store buys only what it sells: an item the map lacks is its own
		List<String[]> differs = MapWatch.shop(axes, LUMBRIDGE, Arrays.asList("Bronze axe", "Steel axe"));
		assertEquals(2, differs.size());
		assertEquals("shop-item-unlisted", differs.get(0)[0]);
		assertEquals("Bob's Axes: Steel axe", differs.get(0)[1]);
		assertEquals("shop-item-absent", differs.get(1)[0]);
		assertEquals("Bob's Axes: Iron axe", differs.get(1)[1]);

		// a general store: among its own stock is its own, after it is most likely a player's
		List<String[]> store = MapWatch.shop(general, LUMBRIDGE,
			Arrays.asList("Pot", "Tinderbox", "Jug", "Bucket", "Rune scimitar"));
		assertEquals(2, store.size());
		assertEquals("shop-item-unlisted", store.get(0)[0]);
		assertEquals("Lumbridge General Store: Tinderbox", store.get(0)[1]);
		assertEquals("shop-item-sold?", store.get(1)[0]);
		assertEquals("Lumbridge General Store: Rune scimitar", store.get(1)[1]);

		assertEquals("shop-unlisted", MapWatch.shop(null, LUMBRIDGE, Collections.singletonList("Pot")).get(0)[0]);

		// the worlds a shop finding was seen on are kept
		MapWatch.Log log = new MapWatch.Log();
		long start = 1_800_000_000_000L;
		assertTrue(log.note(store.get(1), 302, start));
		assertFalse(log.note(store.get(1), 301, start + 1000));
		assertTrue(log.note(store.get(1), 302, start + MapWatch.AGAIN));
		String line = log.lines().get(1);
		assertTrue(line, line.startsWith("shop-item-sold?\t2\t"));
		assertTrue(line, line.endsWith("; worlds [301, 302]"));
		assertEquals(new java.util.TreeSet<>(Arrays.asList(301, 302)), MapWatch.worlds(line));
		assertTrue(MapWatch.worlds("no worlds here").isEmpty());
	}

	@Test
	public void theHealthChecksSpeakOnceForEachTimeTheMapIsOpen()
	{
		MapWatch.Stuck stuck = new MapWatch.Stuck();
		assertNull(stuck.add(true, 0));
		assertNull(stuck.add(true, MapWatch.Stuck.TOO_LONG - 1));
		assertNotNull(stuck.add(true, MapWatch.Stuck.TOO_LONG));
		assertNull(stuck.add(true, MapWatch.Stuck.TOO_LONG * 2));
		assertNull(stuck.add(false, MapWatch.Stuck.TOO_LONG * 2 + 1));
		assertNull(stuck.add(true, MapWatch.Stuck.TOO_LONG * 2 + 2));

		MapWatch.Fps fps = new MapWatch.Fps();
		for (int i = 0; i < MapWatch.Fps.SAMPLES; i++)
		{
			assertNull(fps.add(false, 50));
		}
		String[] slow = null;
		for (int i = 0; i < MapWatch.Fps.SAMPLES; i++)
		{
			slow = fps.add(true, 30);
		}
		assertNotNull(slow);
		assertEquals("about 50 fps with the map closed, 30 with it open", slow[2]);
		assertNull(fps.add(true, 30));
		// 40 of 50 is no drop worth a line
		MapWatch.Fps fine = new MapWatch.Fps();
		for (int i = 0; i < MapWatch.Fps.SAMPLES; i++)
		{
			fine.add(false, 50);
		}
		for (int i = 0; i < MapWatch.Fps.SAMPLES * 2; i++)
		{
			assertNull(fine.add(true, 40));
		}

		MapWatch.Memory memory = new MapWatch.Memory();
		String[] grew = null;
		int told = 0;
		for (int minute = 0; minute <= 15; minute++)
		{
			// three samples a minute; the least climbs 30 MB a minute
			for (String[] now : Arrays.asList(
				memory.add(true, 900 + minute * 30, minute * MapWatch.Memory.MINUTE + 1000),
				memory.add(true, 500 + minute * 30, minute * MapWatch.Memory.MINUTE + 2000),
				memory.add(true, 800 + minute * 30, (minute + 1) * MapWatch.Memory.MINUTE)))
			{
				if (now != null)
				{
					grew = now;
					told++;
				}
			}
		}
		assertEquals(1, told);
		assertNotNull(grew);
		assertTrue(grew[2], grew[2].startsWith("least in use went from 500 MB to "));
		// closed and opened again: nothing until it has grown again
		assertNull(memory.add(false, 2000, 0));
		assertNull(memory.add(true, 2000, 1));
	}

	@Test
	public void aShortMoveRightAfterAFerryIsATripButNotAfterAnAgilityObstacle()
	{
		// owner, 2026-11-14: Keldagrim's ferry moved 16 tiles in a tick, under the 24 of a jump, and was not judged
		WorldPoint ferry = new WorldPoint(2841, 10127, 0);
		WorldPoint across = new WorldPoint(2837, 10143, 0);
		assertTrue(!MapWatch.jumped(ferry, across) && MapWatch.stepped(ferry, across));
		// a run covers 2 tiles a tick: no trip
		assertTrue(!MapWatch.stepped(ferry, new WorldPoint(2843, 10129, 0)));
		// the option tells a transport from an Agility obstacle
		assertTrue(!MapWatch.agility("Travel") && !MapWatch.agility("Board"));
		assertTrue(MapWatch.agility("Jump") && MapWatch.agility("Cross") && MapWatch.agility("Climb-over")
			&& MapWatch.agility("Balance-across"));
	}

	@Test
	public void aMonsterOfTheIgnoreListIsNoFinding()
	{
		// owner, 2026-11-15: the Witch's experiment, a one-time quest fight, was reported once for each of its forms
		WorldPoint shed = new WorldPoint(2936, 3463, 0);
		java.util.Map<String, List<WorldPoint>> listed = java.util.Collections.emptyMap();
		assertNull(MapWatch.monster("Witch's experiment", 19, shed, listed));
		assertNull(MapWatch.monster("Witch's experiment (second form)", 30, shed, listed));
		// a monster on neither list is still one
		assertEquals("monster-unlisted", MapWatch.monster("Made-up beast", 5, shed, listed)[0]);
	}

	@Test
	public void anItemNearItsListedSpawnIsThatSpawnAndItsRealTileIsNoted()
	{
		// owner, 2026-11-15: the Lumbridge farms' eggs lie 4 and 5 tiles from where the wiki puts them
		java.util.Map<String, List<WorldPoint>> listed = Collections.singletonMap("egg",
			Arrays.asList(new WorldPoint(3229, 3299, 0), new WorldPoint(3175, 3305, 0)));
		// on its tile: nothing
		assertNull(MapWatch.groundItem("Egg", new WorldPoint(3229, 3299, 0), listed));
		// a few tiles off: the same spawn, with the tile it is really on
		String[] off = MapWatch.groundItem("Egg", new WorldPoint(3233, 3300, 0), listed);
		assertEquals("item-off", off[0]);
		assertEquals("Egg at 3233, 3300, 0", off[1]);
		assertTrue(off[2], off[2].contains("3229, 3299, 0") && off[2].contains("4 tiles off"));
		// far from every listed one, or of no list at all: a spawn the map lacks
		assertEquals("item-unlisted", MapWatch.groundItem("Egg", new WorldPoint(3185, 3278, 0), listed)[0]);
		assertEquals("item-unlisted", MapWatch.groundItem("Hammer", new WorldPoint(3223, 3252, 0), listed)[0]);
		// the quest's Outlaw, far from the camp, is no finding
		assertNull(MapWatch.monster("Outlaw", 32, new WorldPoint(3218, 3246, 0),
			Collections.singletonMap("outlaw", Collections.singletonList(new WorldPoint(3118, 3474, 0)))));
	}
}
