/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.api.coords.WorldPoint;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapSearchTest
{
	private static final MapSearch SEARCH = new MapSearch();
	private static List<MapSearch.Entry> index;

	@BeforeClass
	public static void load()
	{
		Shops.load();
		Spawns.load();
		Transports.load();
		SEARCH.load();
		index = SEARCH.index(Collections.singletonList(new MapLabels.Label(Collections.singletonList("Lumbridge"),
			3239, 3234, 2, Color.WHITE)));
	}

	private static List<String> names(String query)
	{
		return MapSearch.search(index, query).stream().map(MapSearch.Entry::getName).collect(Collectors.toList());
	}

	@Test
	public void shortcutsFindTheDragonScimitar()
	{
		assertEquals("Dragon scimitar", names("dragon sim").get(0));
		assertEquals("Dragon scimitar", names("d scim").get(0));
		assertTrue(names("dscim").contains("Dragon scimitar"));
	}

	@Test
	public void aNameOfNoLatinLettersNeverMatchesAndBreaksNothing()
	{
		// a marker named in Cyrillic ("Home" in Russian here) or only symbols normalizes to nothing; scoring it threw
		// for every query
		String home = "\u0414\u043e\u043c\u043e\u0439";
		assertEquals("", MapSearch.normalize(home));
		assertEquals(0, MapSearch.score("bank", MapSearch.normalize(home)));
		assertEquals(0, MapSearch.score("bank", MapSearch.normalize("\u2605")));
		assertEquals(0, MapSearch.score("ab", ""));
	}

	@Test
	public void oneLetterWrongStillFindsAPlace()
	{
		// "zenaris" for Zanaris: one letter changed
		assertTrue(MapSearch.score("zenaris", "zanaris") > 0);
		assertTrue(MapSearch.oneEdit("zenaris", "zanaris"));
		// one left out, one added
		assertTrue(MapSearch.oneEdit("zanris", "zanaris"));
		assertTrue(MapSearch.oneEdit("zanariss", "zanaris"));
		// two wrong is too far, and short words are not guessed at
		assertFalse(MapSearch.oneEdit("zenarus", "zanaris"));
		assertEquals(0, MapSearch.score("bonk", "bank"));
		// the exact name still beats a near one
		assertTrue(MapSearch.score("zanaris", "zanaris") > MapSearch.score("zenaris", "zanaris"));
	}

	@Test
	public void theDragonScimitarIsSoldAtDagasSmithy()
	{
		MapSearch.Entry scimitar = MapSearch.search(index, "dragon scimitar").get(0);
		assertEquals(MapSearch.Kind.ITEM, scimitar.getKind());
		assertTrue(scimitar.getShops().stream().anyMatch(shop -> shop.getName().startsWith("Daga's")));
	}

	@Test
	public void placesShopsAndMonstersAreFound()
	{
		assertEquals(MapSearch.Kind.PLACE, MapSearch.search(index, "lumbridge").get(0).getKind());
		assertTrue(MapSearch.search(index, "lumbridge general").stream()
			.anyMatch(e -> e.getKind() == MapSearch.Kind.SHOP && e.getName().equals("Lumbridge General Store")));
		// places come first ("Goblin Cave"), then the monsters, the best named first
		MapSearch.Entry goblin = MapSearch.search(index, "goblin").stream()
			.filter(e -> e.getKind() == MapSearch.Kind.MONSTER).findFirst().orElse(null);
		assertEquals("Goblin", goblin.getName());
		assertEquals(MapSearch.Kind.MONSTER, goblin.getKind());
		assertTrue(goblin.getPoints().size() > 50);
		assertTrue(names("edgeville dungeon").contains("Edgeville Dungeon"));
	}

	@Test
	public void scoresRankWholeThenStartThenWords()
	{
		int whole = MapSearch.score("rune", "rune");
		int start = MapSearch.score("rune", "rune platebody");
		int word = MapSearch.score("rune", "chaos rune");
		int initials = MapSearch.score("kbd", "king black dragon");
		int letters = MapSearch.score("rne", "rune");
		assertTrue(whole > start);
		assertTrue(start > word);
		assertTrue(word > initials);
		assertTrue(initials > letters);
		assertTrue(letters > 0);
		assertEquals(0, MapSearch.score("xyz", "rune"));
		// letters in order, but strewn over a long name, are no match
		assertEquals(0, MapSearch.score("zanaris", "lizard man realm of memories"));
		// a word's start but its last letter: singular for plural
		assertTrue(MapSearch.score("anchovy", "anchovies") > 0);
		assertEquals(0, MapSearch.score("kbd", "blackbird red"));
	}

	@Test
	public void namesAreComparedWithoutCaseOrMarks()
	{
		assertEquals("dagas scimitar smithy", MapSearch.normalize("Daga's Scimitar  Smithy"));
		assertEquals("", MapSearch.normalize("  ' "));
		assertTrue(MapSearch.search(index, "  ").isEmpty());
	}

	@Test
	public void typingPicksAndEnterChoosesTheLitResult()
	{
		MapSearch search = new MapSearch();
		search.setFocused(true);
		for (char c : "rune".toCharArray())
		{
			search.type(c, index, null);
		}
		// several groups match "rune": all of them listed, at most MAX_RESULTS a group
		assertTrue(search.results().size() > MapSearch.Group.values().length);
		assertTrue(search.results().size() <= MapSearch.MAX_RESULTS * MapSearch.Group.values().length);
		search.move(1);
		assertEquals(search.results().get(1), search.chosen());
		search.move(-5);
		assertEquals(search.results().get(0), search.chosen());
		search.setFocused(false);
		assertNull(search.chosen());
		search.clear();
		assertTrue(search.query().isEmpty());
	}

	@Test
	public void theBoxSitsBetweenTheButtonsAndItsResultsUnderIt()
	{
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1200, 800, false);
		MapSearch.Layout layout = MapSearch.layout(buttons, 1200, 3);
		for (Rectangle button : buttons.values())
		{
			assertFalse(button.intersects(layout.getBox()));
		}
		assertEquals(3 * MapSearch.ROW + 4, layout.getResults().height);
		MapSearch search = new MapSearch();
		Rectangle box = layout.getBox();
		assertEquals(MapSearch.Part.BOX, search.hitAt(box.x + 4, box.y + 4, layout).getPart());
		assertEquals(MapSearch.Part.OUTSIDE, search.hitAt(600, 600, layout).getPart());
		// a window too narrow for it between the buttons: under the left ones
		Map<MapPainter.Button, Rectangle> narrow = MapPainter.buttons(400, 300, true);
		Rectangle small = MapSearch.layout(narrow, 400, 0).getBox();
		for (Rectangle button : narrow.values())
		{
			assertFalse(button.intersects(small));
		}
		assertTrue(small.x + small.width <= 400);
	}

	@Test
	public void entrancesWithTheSameNameAreOneEntry() throws IOException
	{
		String tsv = "D\tSmoke Dungeon\t3309\t2962\t0\nD\tSmoke Dungeon\t3206\t9379\t0\nG\tCastle Wars\t2440\t3090\t0\n";
		List<MapSearch.Entry> places = MapSearch.parsePlaces(new ByteArrayInputStream(tsv.getBytes(
			StandardCharsets.UTF_8)));
		assertEquals(2, places.size());
		assertEquals(2, places.get(0).getPoints().size());
		assertEquals(MapSearch.Kind.MINIGAME, places.get(1).getKind());
	}

	@Test
	public void spawnsGroupIntoNamedAreasNearestFirst()
	{
		List<WorldPoint> spawns = Arrays.asList(new WorldPoint(3240, 3230, 0), new WorldPoint(3245, 3236, 0),
			new WorldPoint(3255, 3240, 0), new WorldPoint(2990, 3350, 0));
		List<MapLabels.Label> labels = Arrays.asList(
			new MapLabels.Label(Collections.singletonList("Lumbridge"), 3239, 3234, 2, Color.WHITE),
			new MapLabels.Label(Collections.singletonList("Falador"), 2989, 3355, 2, Color.WHITE));
		List<SearchSpots.Area> areas = SearchSpots.areas(spawns, new WorldPoint(3000, 3360, 0), labels);
		assertEquals(2, areas.size());
		assertEquals("Falador", areas.get(0).getName());
		assertEquals(1, areas.get(0).getCount());
		assertEquals("Lumbridge", areas.get(1).getName());
		assertEquals(3, areas.get(1).getCount());
		assertEquals("2 spawns", SearchSpots.rows(Collections.singletonList(new SearchSpots.Area("X",
			new WorldPoint(1, 1, 1), 2, 0))).get(0).getNote());
		// far from any name: the tile
		assertEquals("100, 100", SearchSpots.name(new WorldPoint(100, 100, 0), labels));
	}

	@Test
	public void fairyRingsAreFoundByTheirCodes()
	{
		MapSearch.Entry cks = MapSearch.search(index, "cks").get(0);
		assertEquals(MapSearch.Kind.FAIRY_RING, cks.getKind());
		assertEquals("Fairy ring CKS", cks.getName());
		// every ring listed (the list scrolls)
		assertTrue(MapSearch.search(index, "fairy ring").size() > 40);
	}

	@Test
	public void fishingAndHunterSpotsAreFoundByWhatTheyCatch()
	{
		assertTrue(names("lobster").contains("Lobster fishing spots"));
		MapSearch.Entry chins = MapSearch.search(index, "carnivorous chinchompa hunting").get(0);
		assertEquals(MapSearch.Kind.HUNTER, chins.getKind());
		assertEquals("Lvl 63 Hunter", chins.getNote());
	}

	@Test
	public void asGoodMatchesListTheNearestFirst()
	{
		MapSearch.Entry far = new MapSearch.Entry(MapSearch.Kind.ICON, "Bank", "", Collections.singletonList(
			new WorldPoint(2000, 3000, 0)), Collections.emptyList(), null);
		MapSearch.Entry near = new MapSearch.Entry(MapSearch.Kind.ICON, "Banker", "", Collections.singletonList(
			new WorldPoint(3220, 3220, 0)), Collections.emptyList(), null);
		List<MapSearch.Entry> both = Arrays.asList(far, near);
		WorldPoint lumbridge = new WorldPoint(3222, 3218, 0);
		// "ban": both start with it, so the nearer comes first; "bank" is the whole of one name, which wins
		assertEquals(near, MapSearch.search(both, "ban", lumbridge).get(0));
		assertEquals(far, MapSearch.search(both, "bank", lumbridge).get(0));
		assertEquals(far, MapSearch.search(both, "ban", null).get(0));
	}

	private static MapSearch.Entry entry(MapSearch.Kind kind, String name, int importance)
	{
		return new MapSearch.Entry(kind, name, "", Collections.singletonList(new WorldPoint(2600, 3300, 0)),
			Collections.emptyList(), null, importance);
	}

	@Test
	public void aMarkerNamedLikeATownStillShows()
	{
		// the player's marker "Varrock" is theirs, not a second name for the town
		List<MapSearch.Entry> both = Arrays.asList(entry(MapSearch.Kind.PLACE, "Varrock", 3),
			entry(MapSearch.Kind.MARKER, "Varrock", 1));
		List<MapSearch.Entry> found = MapSearch.search(both, "varrock", null);
		assertEquals(2, found.size());
	}

	@Test
	public void theMapsNamesForMonstersAreNotPlaces()
	{
		List<Spawns.Creature> creatures = Arrays.asList(
			new Spawns.Creature("Blue dragon", true, "111", Collections.emptyList()),
			new Spawns.Creature("Brutal black dragon", true, "318", Collections.emptyList()),
			new Spawns.Creature("Brutal blue dragon", true, "271", Collections.emptyList()),
			new Spawns.Creature("Red dragon", true, "152", Collections.emptyList()),
			new Spawns.Creature("Chaos dwarf", true, "48", Collections.emptyList()),
			new Spawns.Creature("Goblin", true, "2", Collections.emptyList()),
			new Spawns.Creature("Lost soul", true, "5", Collections.emptyList()),
			new Spawns.Creature("Sea snake", true, "51", Collections.emptyList()),
			new Spawns.Creature("Iceberg (scenery)", false, "", Collections.emptyList()));
		MapSearch.CreatureNames names = new MapSearch.CreatureNames(creatures);
		assertTrue(names.named("Blue dragons"));
		assertTrue(names.named("Brutal black, blue & red dragons"));
		assertTrue(names.named("Chaos dwarves"));
		assertTrue(names.named("Goblin"));
		// places, and an NPC's
		assertFalse(names.named("Goblin Village"));
		assertFalse(names.named("Dragon Forge"));
		assertFalse(names.named("Sea of Souls"));
		assertFalse(names.named("Iceberg"));
		assertFalse(names.named("Varrock"));
	}

	@Test
	public void noMonsterOrShopTheGameRemovedIsBundled() throws IOException
	{
		// the wiki's discontinued content (the Realm of Memories, past holiday events) is left out of the data
		Spawns.load();
		assertTrue(Spawns.all().stream().noneMatch(c -> c.getName().contains("(Realm of Memories)")));
		assertTrue(Spawns.all().stream().anyMatch(c -> c.getName().equals("Blue dragon")));
	}

	@Test
	public void resultsAreGroupedPlacesMonstersItemsThenTheRest()
	{
		List<MapSearch.Entry> found = MapSearch.search(index, "dragon");
		int last = -1;
		for (MapSearch.Entry e : found)
		{
			int group = MapSearch.groupOf(e).ordinal();
			assertTrue("in group order: " + found, group >= last);
			last = group;
		}
		// NPCs (characters that cannot be attacked) are not listed with the monsters
		assertEquals(MapSearch.Group.OTHER, MapSearch.groupOf(MapSearch.Kind.NPC));
		assertEquals(MapSearch.Group.MONSTERS, MapSearch.groupOf(MapSearch.Kind.MONSTER));
		// a heading where each group starts, and rows below their heading
		int[] tops = MapSearch.rowTops(found);
		assertEquals(MapSearch.HEADING_ROW, tops[0]);
		assertEquals(MapSearch.headings(found) * MapSearch.HEADING_ROW + (found.size() - 1) * MapSearch.ROW,
			tops[found.size() - 1]);
	}

	@Test
	public void theResultsFitTheMapAndScroll()
	{
		MapSearch search = new MapSearch();
		search.setFocused(true);
		for (char c : "dragon".toCharArray())
		{
			search.type(c, index, null);
		}
		// a small map: the list stops above its bar and scrolls
		java.util.Map<MapPainter.Button, Rectangle> small = MapPainter.buttons(600, 400, false);
		MapSearch.Layout layout = search.layout(small, 600);
		Rectangle list = layout.getResults();
		Rectangle locate = small.get(MapPainter.Button.LOCATE);
		assertTrue(list.y + list.height <= locate.y + locate.height);
		assertTrue(list.height < search.results().size() * MapSearch.ROW);
		// the arrow keys keep the lit result in view
		search.move(search.results().size());
		int last = search.results().size() - 1;
		MapSearch.Hit hit = search.hitAt(list.x + 10, list.y + list.height - 6, layout);
		assertEquals(last, hit.getIndex());
		// a big map shows them all
		Rectangle tall = search.layout(MapPainter.buttons(1200, 3000, false), 1200).getResults();
		assertEquals(search.results().size() * MapSearch.ROW + MapSearch.headings(search.results()) * MapSearch.HEADING_ROW
			+ 4, tall.height);
	}

	@Test
	public void theBoxStaysOnTheTopRowOfASmallMap()
	{
		// the smallest box over the game (520 wide) and a small pop-out: shorter buttons, the box between them
		for (int width : new int[]{520, 600})
		{
			for (boolean window : new boolean[]{false, true})
			{
				java.util.Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(width, 400, window);
				Rectangle box = MapSearch.layout(buttons, width, 0).getBox();
				Rectangle filters = buttons.get(MapPainter.Button.FILTERS);
				assertEquals(width + " " + window, filters.y, box.y);
				for (Rectangle button : buttons.values())
				{
					assertFalse(width + " " + window, button.intersects(box));
				}
			}
		}
		// a wide map keeps the full buttons
		assertTrue(MapPainter.buttons(1200, 800, false).get(MapPainter.Button.FILTERS).width
			> MapPainter.buttons(520, 400, false).get(MapPainter.Button.FILTERS).width);
	}

	@Test
	public void placesComeFirstTheBiggerFirst()
	{
		List<MapSearch.Entry> entries = Arrays.asList(
			entry(MapSearch.Kind.ITEM, "Ardougne cloak", 0),
			entry(MapSearch.Kind.SHOP, "Ardougne Gem Stall", 0),
			entry(MapSearch.Kind.PLACE, "Ardougne Zoo", 1),
			entry(MapSearch.Kind.PLACE, "East Ardougne", 2),
			entry(MapSearch.Kind.DUNGEON, "Ardougne Sewers", 1),
			entry(MapSearch.Kind.PLACE, "Ardougne Sewers", 1),
			entry(MapSearch.Kind.PLACE, "Nardah", 2),
			entry(MapSearch.Kind.PLACE, "Kingdom of Varlamore", 3),
			entry(MapSearch.Kind.PLACE, "Varrock", 2),
			entry(MapSearch.Kind.ITEM, "Varrock armour", 0),
			entry(MapSearch.Kind.PLACE, "Silvarea", 1));
		List<String> ardougne = MapSearch.search(entries, "ardougne", null).stream().map(MapSearch.Entry::getName)
			.collect(Collectors.toList());
		// the town before the smaller places, places before the cloak and the stall; the sewers once
		assertEquals(Arrays.asList("East Ardougne", "Ardougne Zoo", "Ardougne Sewers", "Ardougne cloak",
			"Ardougne Gem Stall"), ardougne);
		List<String> var = MapSearch.search(entries, "var", null).stream().map(MapSearch.Entry::getName)
			.collect(Collectors.toList());
		// the start of both names: the kingdom, then the town, then the armour; "var" inside a name comes last
		// grouped: the places (bigger first), then the items
		assertEquals(Arrays.asList("Kingdom of Varlamore", "Varrock", "Silvarea", "Varrock armour"), var);
		// a name typed in full still comes first
		assertEquals("Ardougne cloak", MapSearch.search(entries, "ardougne cloak", null).get(0).getName());
	}

	@Test
	public void nicknamesFindTheirPlaces()
	{
		List<MapSearch.Entry> entries = Arrays.asList(
			entry(MapSearch.Kind.ITEM, "Armadyl halo", 0),
			entry(MapSearch.Kind.PLACE, "West Ardougne", 2),
			entry(MapSearch.Kind.PLACE, "East Ardougne", 2),
			entry(MapSearch.Kind.PLACE, "Falador", 2));
		List<String> ardy = MapSearch.search(entries, "ardy", null).stream().map(MapSearch.Entry::getName)
			.collect(Collectors.toList());
		assertTrue(ardy.indexOf("East Ardougne") < 2 && ardy.indexOf("West Ardougne") < 2);
		assertEquals("Falador", MapSearch.search(entries, "Fally", null).get(0).getName());
	}

	@Test
	public void anIndexBuiltBeforeTheTablesWereReadIsBuiltAgainAfter()
	{
		// the tables are read in the background as the plugin starts: a search typed before they are read sees an
		// index without places, which must not be kept once they are
		MapSearch fresh = new MapSearch();
		List<MapLabels.Label> labels = Collections.singletonList(new MapLabels.Label(
			Collections.singletonList("Lumbridge"), 3239, 3234, 2, Color.WHITE));
		List<MapSearch.Entry> before = fresh.index(labels);
		assertTrue(before.stream().noneMatch(e -> e.getName().equals("Edgeville Dungeon")));
		fresh.load();
		List<MapSearch.Entry> after = fresh.index(labels);
		assertTrue(after.stream().anyMatch(e -> e.getName().equals("Edgeville Dungeon")));
	}

	@Test
	public void anItemIsFoundInShopsAndOnTheGround()
	{
		// a knife: sold in shops, and lying on tables (42 spots); both under Items, each its own result
		List<MapSearch.Entry> knife = MapSearch.search(index, "knife", new WorldPoint(3222, 3218, 0)).stream()
			.filter(entry -> entry.getName().equals("Knife")).collect(Collectors.toList());
		assertEquals(2, knife.size());
		MapSearch.Entry ground = knife.stream().filter(entry -> entry.getKind() == MapSearch.Kind.GROUND_ITEM)
			.findFirst().orElse(null);
		assertEquals("On the ground", ground.getNote());
		assertEquals(42, ground.getPoints().size());
		assertEquals(MapSearch.Group.ITEMS, MapSearch.groupOf(ground));
		assertTrue(ground.getCreature().isItem());
		assertTrue(knife.stream().anyMatch(entry -> entry.getKind() == MapSearch.Kind.ITEM));
		// an item on the ground is no creature: it names no place away ("Bucket" is not a monster's name)
		assertTrue(!new MapSearch.CreatureNames(Spawns.all()).named("Buckets"));
	}

	@Test
	public void theSearchInListLeavesKindsOfResultOut()
	{
		MapSearch search = new MapSearch();
		search.setFocused(true);
		for (char c : "knife".toCharArray())
		{
			search.type(c, index, null);
		}
		assertTrue(search.results().stream().anyMatch(entry -> entry.getKind() == MapSearch.Kind.GROUND_ITEM));

		// the lines beside the glass open the list, in place of the results
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1200, 800, false);
		MapSearch.Layout closed = search.layout(buttons, 1200);
		Rectangle lines = closed.getScopes();
		assertTrue(closed.getBox().contains(lines));
		assertEquals(MapSearch.Part.SCOPES, search.hitAt(lines.x + 4, lines.y + 4, closed).getPart());
		search.setScopesOpen(true);
		assertTrue("no results while the list is open", search.results().isEmpty());
		MapSearch.Layout open = search.layout(buttons, 1200);
		Rectangle list = open.getResults();
		assertEquals((MapSearch.Scope.values().length + 1) * MapSearch.ROW + 4, list.height);
		MapSearch.Hit first = search.hitAt(list.x + 10, list.y + 2 + 5, open);
		assertEquals(MapSearch.Part.SCOPE, first.getPart());
		assertEquals("Search everything", 0, first.getIndex());
		int ground = MapSearch.Scope.GROUND_ITEMS.ordinal() + 1;
		assertEquals(ground, search.hitAt(list.x + 10, list.y + 2 + ground * MapSearch.ROW + 5, open).getIndex());

		// items on the ground left out: the plugin searches what the search still looks in
		search.setSkipped(java.util.EnumSet.of(MapSearch.Scope.GROUND_ITEMS));
		List<MapSearch.Entry> looked = index.stream().filter(search::looksIn).collect(Collectors.toList());
		search.searchAgain(looked, null);
		search.setScopesOpen(false);
		assertFalse(search.results().isEmpty());
		assertTrue(search.results().stream().noneMatch(entry -> entry.getKind() == MapSearch.Kind.GROUND_ITEM));

		// typing closes the list and shows what it finds; leaving the bar closes it too
		search.setScopesOpen(true);
		search.type('s', looked, null);
		assertFalse(search.isScopesOpen());
		search.setScopesOpen(true);
		search.setFocused(false);
		assertFalse(search.isScopesOpen());
	}

	@Test
	public void everyKindOfResultCanBeLeftOutAndTheChoiceIsSaved()
	{
		for (MapSearch.Kind kind : MapSearch.Kind.values())
		{
			assertTrue(kind.name(), kind.scope != null);
		}
		java.util.Set<MapSearch.Scope> off = java.util.EnumSet.of(MapSearch.Scope.MONSTERS, MapSearch.Scope.NPCS);
		assertEquals("MONSTERS;NPCS", MapSearch.Scope.save(off));
		assertEquals(off, MapSearch.Scope.parse("MONSTERS;NPCS"));
		assertEquals("a name no longer known is left out", java.util.EnumSet.of(MapSearch.Scope.NPCS),
			MapSearch.Scope.parse("GONE;NPCS"));
		assertTrue(MapSearch.Scope.parse("").isEmpty());
	}
}
