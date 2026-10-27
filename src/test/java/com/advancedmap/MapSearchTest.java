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
		MapSearch.Entry goblin = MapSearch.search(index, "goblin").get(0);
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
		assertEquals(MapSearch.MAX_RESULTS, search.results().size());
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
		assertTrue(MapSearch.search(index, "fairy ring").size() == MapSearch.MAX_RESULTS);
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
}
