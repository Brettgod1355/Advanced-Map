/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.WorldMapElements;
import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DungeonPreviewTest
{
	/** Two caves side by side on one floor, x 100-109 and x 120-129 (y 100-109), with nothing between. */
	private static final DungeonPreview.Floor TWO_CAVES = (x, y) ->
		y >= 100 && y < 110 && (x >= 100 && x < 110 || x >= 120 && x < 130) ? DungeonPreview.OPEN
			: DungeonPreview.CLOSED;

	@Test
	public void theFillStopsWhereTheGroundStops()
	{
		DungeonPreview.Fill fill = DungeonPreview.flood(105, 105, TWO_CAVES);
		assertEquals(100, fill.getTiles().cardinality());
		assertTrue(fill.contains(100, 100) && fill.contains(109, 109));
		assertFalse(fill.contains(120, 105));
		assertTrue(fill.isComplete());
	}

	@Test
	public void anEntranceOnAWallsEdgeStartsFromTheGroundBeside()
	{
		DungeonPreview.Fill fill = DungeonPreview.flood(111, 105, TWO_CAVES);
		assertEquals(100, fill.getTiles().cardinality());
		assertTrue(fill.contains(109, 105));
	}

	@Test
	public void groundNotDrawnYetLeavesTheFillIncomplete()
	{
		DungeonPreview.Floor partly = (x, y) -> x >= 108 ? DungeonPreview.UNKNOWN : TWO_CAVES.at(x, y);
		DungeonPreview.Fill fill = DungeonPreview.flood(105, 105, partly);
		assertFalse(fill.isComplete());
		assertEquals(80, fill.getTiles().cardinality());
	}

	@Test
	public void noGroundYetButSomeStillBeingDrawnIsNotComplete()
	{
		// the arrival tile is wall, the ground beside it in a region not drawn yet: not "no monsters" for good
		DungeonPreview.Floor wallThenUnknown = (x, y) -> x >= 101 ? DungeonPreview.UNKNOWN : DungeonPreview.CLOSED;
		DungeonPreview.Fill fill = DungeonPreview.flood(100, 100, wallThenUnknown);
		assertEquals(0, fill.getTiles().cardinality());
		assertFalse(fill.isComplete());
		// all known and no ground: complete
		org.junit.Assert.assertTrue(DungeonPreview.flood(100, 100, (x, y) -> DungeonPreview.CLOSED).isComplete());
	}

	@Test
	public void aMapPieceHoldsOnlyTheFloorsItLists()
	{
		// the Grand Library's map holds its squares on floor 1 only
		WorldMapElements.Piece library = new WorldMapElements.Piece(2560, 6080, 64, 1, 1);
		assertFalse(library.contains(2570, 6090, 0));
		org.junit.Assert.assertTrue(library.contains(2570, 6090, 1));
		assertFalse(library.contains(2570, 6090, 2));
		// without a count, every floor from its lowest up
		org.junit.Assert.assertTrue(new WorldMapElements.Piece(2560, 6080, 64, 1).contains(2570, 6090, 3));
	}

	@Test
	public void theMonstersInsideAreThoseWithASpawnInTheFillOnItsFloor()
	{
		DungeonPreview.Fill fill = DungeonPreview.flood(105, 105, TWO_CAVES);
		List<Spawns.Creature> creatures = Arrays.asList(
			new Spawns.Creature("Hill Giant", true, "28", Arrays.asList(new WorldPoint(101, 101, 0),
				new WorldPoint(102, 101, 0))),
			new Spawns.Creature("Rat", true, "1", Collections.singletonList(new WorldPoint(108, 108, 0))),
			new Spawns.Creature("Next door", true, "5", Collections.singletonList(new WorldPoint(125, 105, 0))),
			new Spawns.Creature("Upstairs", true, "5", Collections.singletonList(new WorldPoint(105, 105, 1))),
			new Spawns.Creature("Banker", false, "", Collections.singletonList(new WorldPoint(104, 104, 0))));
		List<DungeonPreview.Inside> inside = DungeonPreview.monsters(fill, 0, creatures);
		assertEquals(2, inside.size());
		// the lowest level first
		assertEquals("Rat (1)", inside.get(0).text());
		assertEquals("Hill Giant", inside.get(1).getName());
		assertEquals(2, inside.get(1).getCount());
	}

	@Test
	public void theGamesMapOfAPlaceFencesTheFill()
	{
		// the game's map shows the first cave and the zone of ground joining it to the second, not the second
		DungeonPreview.Floor joined = (x, y) -> y >= 100 && y < 110 && x >= 100 && x < 130 ? DungeonPreview.OPEN
			: DungeonPreview.CLOSED;
		List<WorldMapElements.Piece> map = Arrays.asList(new WorldMapElements.Piece(64, 64, 64, 0),
			new WorldMapElements.Piece(128, 104, 8, 0));
		DungeonPreview.Fill fill = DungeonPreview.flood(105, 105, DungeonPreview.fence(map, 0, joined));
		assertTrue(fill.contains(127, 105));
		assertTrue(fill.contains(129, 105));
		assertFalse(fill.contains(129, 102));
		// the map's floors start at 1: on the ground it fences everything off
		assertEquals(0, DungeonPreview.flood(105, 105, DungeonPreview.fence(Collections.singletonList(
			new WorldMapElements.Piece(64, 64, 64, 1)), 0, joined)).getTiles().cardinality());
	}

	@Test
	public void theMostParticularMapOfATileIsChosen()
	{
		List<WorldMapElements.Piece> big = Arrays.asList(new WorldMapElements.Piece(0, 0, 64, 0),
			new WorldMapElements.Piece(64, 0, 64, 0), new WorldMapElements.Piece(128, 0, 64, 0));
		List<WorldMapElements.Piece> small = Collections.singletonList(new WorldMapElements.Piece(64, 0, 64, 0));
		assertSame(small, DungeonPreview.mapOf(new WorldPoint(70, 10, 0), Arrays.asList(big, small)));
		assertSame(big, DungeonPreview.mapOf(new WorldPoint(10, 10, 0), Arrays.asList(big, small)));
		assertNull(DungeonPreview.mapOf(new WorldPoint(500, 10, 0), Arrays.asList(big, small)));
	}

	@Test
	public void aRopeDownOnTheSameMapAddsTheFloorBelowAndOneOffItDoesNot()
	{
		// the tunnels on floor 2 (the first cave), a rope down to a chamber on the ground floor (the second cave), and
		// a tunnel out to another map's cave
		java.util.function.IntFunction<DungeonPreview.Floor> floors = plane -> plane == 2
			? (x, y) -> x >= 100 && x < 110 && y >= 100 && y < 110 ? DungeonPreview.OPEN : DungeonPreview.CLOSED
			: plane == 0 ? TWO_CAVES : (x, y) -> DungeonPreview.CLOSED;
		List<WorldMapElements.Piece> map = Collections.singletonList(new WorldMapElements.Piece(64, 64, 64, 0));
		List<DungeonPreview.Way> ways = Arrays.asList(
			new DungeonPreview.Way(new WorldPoint(108, 104, 2), new WorldPoint(122, 104, 0)),
			new DungeonPreview.Way(new WorldPoint(101, 101, 2), new WorldPoint(5000, 5000, 0)));
		List<Spawns.Creature> creatures = Arrays.asList(
			new Spawns.Creature("Kalphite Worker", true, "28", Collections.singletonList(new WorldPoint(105, 105, 2))),
			new Spawns.Creature("Kalphite Queen", true, "333", Collections.singletonList(new WorldPoint(125, 105, 0))),
			new Spawns.Creature("Elsewhere", true, "5", Collections.singletonList(new WorldPoint(5000, 5000, 0))),
			new Spawns.Creature("Under the tunnels", true, "9", Collections.singletonList(new WorldPoint(105, 105, 0))));
		DungeonPreview.Look look = DungeonPreview.look(new WorldPoint(103, 103, 2), Collections.singletonList(map), ways,
			floors, creatures);
		assertEquals(2, look.getFloors().size());
		assertEquals(new WorldPoint(122, 104, 0), look.getFloors().get(1));
		assertEquals(Arrays.asList("Kalphite Worker", "Kalphite Queen"), names(look.getMonsters()));
		// without the game's maps read yet: the first floor only
		assertEquals(1, DungeonPreview.look(new WorldPoint(103, 103, 2), Collections.emptyList(), ways, floors,
			creatures).getFloors().size());
	}

	@Test
	public void monstersAreListedTheHighestFirstOrTheLowestFirst()
	{
		List<DungeonPreview.Inside> inside = Arrays.asList(new DungeonPreview.Inside("Skeleton", "21, 22, 45", 3),
			new DungeonPreview.Inside("Hill Giant", "28", 2), new DungeonPreview.Inside("Banshee", "", 1),
			new DungeonPreview.Inside("Black demon", "172, 188", 1));
		assertEquals(Arrays.asList("Black demon", "Skeleton", "Hill Giant", "Banshee"),
			names(DungeonPreview.sorted(inside, true)));
		assertEquals(Arrays.asList("Skeleton", "Hill Giant", "Black demon", "Banshee"),
			names(DungeonPreview.sorted(inside, false)));
	}

	@Test
	public void theWaysInsideDungeonsAreRead() throws Exception
	{
		try (java.io.InputStream in = DungeonPreview.class.getResourceAsStream("inner-links.tsv"))
		{
			List<DungeonPreview.Way> ways = DungeonPreview.parseWays(in);
			assertTrue(ways.size() > 500);
			// the rope down to the Kalphite Queen
			assertTrue(ways.contains(new DungeonPreview.Way(new WorldPoint(3508, 9498, 2), new WorldPoint(3508, 9493, 0))));
		}
	}

	@Test
	public void theSmallMapShowsTheFloorNotTheDarkAroundIt()
	{
		WorldPoint to = new WorldPoint(1000, 1000, 0);
		// a floor that fits, east and north of the arrival: centered on it, zoomed in to fill the box (220 / 84 wide,
		// 150 / 54 high: the width decides), the arrival still well inside
		MapViewport.View fits = DungeonPreview.framing(to, new java.awt.Rectangle(990, 990, 80, 50));
		assertEquals(1030, fits.centerX, 0.01);
		assertEquals(1015, fits.centerY, 0.01);
		assertEquals(220.0 / 84, fits.zoom, 0.001);
		// a tiny one: no further in than the most zoom
		assertEquals(DungeonPreview.MAX_MAP_ZOOM, DungeonPreview.framing(to, new java.awt.Rectangle(995, 995, 10, 10))
			.zoom, 0.001);
		assertTrue(fits.screenX(1000.5) >= DungeonPreview.ARRIVAL_MARGIN);
		// a floor bigger than the box: zoomed out, but no further than the least zoom
		MapViewport.View big = DungeonPreview.framing(to, new java.awt.Rectangle(800, 900, 400, 200));
		assertEquals(DungeonPreview.MIN_MAP_ZOOM, big.zoom, 0.001);
		// the arrival far to one side of a wide floor: the view stops where the arrival would leave it
		MapViewport.View side = DungeonPreview.framing(to, new java.awt.Rectangle(1000, 995, 300, 10));
		double x = side.screenX(1000.5);
		assertTrue(x >= DungeonPreview.ARRIVAL_MARGIN - 1 && x <= DungeonPreview.MAP_WIDTH);
		// no floor known yet: on the arrival
		assertEquals(1000.5, DungeonPreview.framing(to, null).centerX, 0.001);
	}

	private static List<String> names(List<DungeonPreview.Inside> inside)
	{
		List<String> out = new java.util.ArrayList<>();
		for (DungeonPreview.Inside each : inside)
		{
			out.add(each.getName());
		}
		return out;
	}

	@Test
	public void theNameIsTheNamedEntranceElseTheNearestPlaceName()
	{
		MapSpot icon = MapSpot.icon(3096, 3468, 0, 1, IconGroup.DUNGEON);
		WorldPoint to = new WorldPoint(3097, 9867, 0);
		List<MapSearch.Entry> places = Collections.singletonList(new MapSearch.Entry(MapSearch.Kind.DUNGEON,
			"Edgeville Dungeon", "Dungeon", Collections.singletonList(new WorldPoint(3097, 3468, 0)),
			Collections.emptyList(), null));
		List<MapLabels.Label> labels = Collections.singletonList(new MapLabels.Label(
			Arrays.asList("Misthalin", "Underground"), 3100, 9880, 1, Color.WHITE));
		assertEquals("Edgeville Dungeon", DungeonPreview.name(icon, to, places, labels));
		assertEquals("Misthalin Underground", DungeonPreview.name(icon, to, Collections.emptyList(), labels));
		assertNull(DungeonPreview.name(icon, to, Collections.emptyList(), Collections.emptyList()));
	}

	@Test
	public void theQueensChamberCountsFromTheDesertButNotFromItsOwnRopeUp() throws Exception
	{
		List<DungeonPreview.Way> all;
		try (java.io.InputStream in = DungeonPreview.class.getResourceAsStream("inner-links.tsv"))
		{
			all = DungeonPreview.parseWays(in);
		}
		WorldPoint chamber = new WorldPoint(3508, 9493, 0);
		// the desert's entrance: the lair's ways down to the chamber are followed, so its Queen is listed
		assertTrue(downToChamber(DungeonPreview.notBackTo(all, new WorldPoint(3227, 3108, 0)), chamber) > 0);
		// the chamber's rope up to the lair: the ways back down to where the player stands are not
		assertEquals(0, downToChamber(DungeonPreview.notBackTo(all, chamber), chamber));
	}

	private static int downToChamber(List<DungeonPreview.Way> ways, WorldPoint chamber)
	{
		int count = 0;
		for (DungeonPreview.Way way : ways)
		{
			count += way.getTo().getPlane() == chamber.getPlane() && Math.abs(way.getTo().getX() - chamber.getX()) <= 2
				&& Math.abs(way.getTo().getY() - chamber.getY()) <= 2 ? 1 : 0;
		}
		return count;
	}

	@Test
	public void aLinksOwnNameWinsAndAWayOutNamedForItsDungeonGoesUpToTheSurface()
	{
		// the Queen's chamber's rope up: the label nearest where it lands is the chamber's own, a floor below
		MapSpot rope = MapSpot.icon(3508, 9493, 0, 1, IconGroup.MAP_LINK);
		List<MapLabels.Label> chamber = Collections.singletonList(new MapLabels.Label(
			Arrays.asList("Queen's", "Chamber"), 3496, 9497, 1, Color.WHITE));
		assertEquals("Kalphite Lair", DungeonPreview.name(rope, new WorldPoint(3508, 9497, 2), Collections.emptyList(),
			chamber));

		// the lair's way out: the label beside its entrance is the lair's own name, not where it leads
		List<MapSearch.Entry> places = Collections.singletonList(new MapSearch.Entry(MapSearch.Kind.DUNGEON,
			"Kalphite Lair", "Dungeon", Collections.singletonList(new WorldPoint(3226, 3108, 0)),
			Collections.emptyList(), null));
		assertEquals("Up to the surface", DungeonPreview.wayOutTitle("Kalphite Lair", places));
		assertEquals("Up to Lumbridge", DungeonPreview.wayOutTitle("Lumbridge", places));
		assertEquals("Up to the surface", DungeonPreview.wayOutTitle(null, places));
		// a minigame's name is a place's own name too (Giants' Foundry is listed as one)
		List<MapSearch.Entry> foundry = Collections.singletonList(new MapSearch.Entry(MapSearch.Kind.MINIGAME,
			"Giants' Foundry", "Minigame", Collections.singletonList(new WorldPoint(3360, 3150, 0)),
			Collections.emptyList(), null));
		assertEquals("Up to the surface", DungeonPreview.wayOutTitle("Giants' Foundry", foundry));
	}

	@Test
	public void theGrandExchangesWayIntoKeldagrimIsNamedForKeldagrim() throws Exception
	{
		// its preview read "Brewery", the label nearest where it leads (owner, 2026-11-07)
		List<MapSearch.Entry> places;
		try (java.io.InputStream in = MapSearch.class.getResourceAsStream("places.tsv"))
		{
			places = MapSearch.parsePlaces(in);
		}
		List<MapLabels.Label> brewery = Collections.singletonList(new MapLabels.Label(
			Collections.singletonList("Brewery"), 2915, 10195, 0, Color.WHITE));
		assertEquals("Keldagrim", DungeonPreview.name(MapSpot.icon(3141, 3505, 0, 1, IconGroup.DUNGEON),
			new WorldPoint(2911, 10190, 0), places, brewery));
	}

	@Test
	public void aLookIsKeptForTheIconItIsLookedFromToo()
	{
		// the lair's landing looked at from the Queen's chamber's rope up, then from the desert's entrance: two looks,
		// as their ways differ (the way back to each icon is not followed)
		java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
		try
		{
			DungeonPreview preview = new DungeonPreview(new RegionImages(new RegionImagesCacheTest.Flat(), executor,
				new MapIcons()));
			WorldPoint landing = new WorldPoint(3508, 9497, 2);
			DungeonPreview.Look fromRope = preview.look(landing, new WorldPoint(3508, 9493, 0));
			assertSame(fromRope, preview.look(landing, new WorldPoint(3508, 9493, 0)));
			org.junit.Assert.assertNotSame(fromRope, preview.look(landing, new WorldPoint(3227, 3108, 0)));
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	public void aDungeonsEntranceNamesThePreviewBeforeAMinigameSpotNearer()
	{
		// a wiki minigame spot (Chompy bird hunting) two tiles from a dungeon icon, the dungeon's entrance five away
		MapSpot icon = MapSpot.icon(2340, 3061, 0, 1, IconGroup.DUNGEON);
		List<MapSearch.Entry> places = Arrays.asList(
			new MapSearch.Entry(MapSearch.Kind.MINIGAME, "Chompy bird hunting", "Minigame",
				Collections.singletonList(new WorldPoint(2337, 3061, 0)), Collections.emptyList(), null),
			new MapSearch.Entry(MapSearch.Kind.DUNGEON, "Some Dungeon", "Dungeon",
				Collections.singletonList(new WorldPoint(2345, 3061, 0)), Collections.emptyList(), null));
		assertEquals("Some Dungeon", DungeonPreview.name(icon, new WorldPoint(2340, 9461, 0), places,
			Collections.emptyList()));
		// with no dungeon near, the minigame still names it
		assertEquals("Chompy bird hunting", DungeonPreview.name(icon, new WorldPoint(2340, 9461, 0),
			places.subList(0, 1), Collections.emptyList()));
	}
}
