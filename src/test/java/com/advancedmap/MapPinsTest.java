/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class MapPinsTest
{
	private static final MapPins.Marker HOME = new MapPins.Marker("18f-1", new WorldPoint(3222, 3218, 0),
		MarkerIcons.Design.HOUSE, 3, "Home base", "rsprofile.abc");

	@Test
	public void aMarkersFileReadsBack()
	{
		MapPins.Marker unnamed = new MapPins.Marker("18f-2", new WorldPoint(2440, 3090, 1), MarkerIcons.Design.SKULL, 0,
			"", "");
		assertEquals(HOME, MapPins.fromLine("18f-1", MapPins.line(HOME)));
		assertEquals(unnamed, MapPins.fromLine("18f-2", MapPins.line(unnamed)));
		assertNull(MapPins.fromLine("x", "3222 3218"));
		assertEquals("a b", MapPins.cleanName("a\nb"));
	}

	@Test
	public void anEarlierVersionsMarkersMoveWithNewIds()
	{
		List<MapPins.Marker> old = MapPins.parseOld("3222 3218 0 7 3 Home base\nx y\n2440 3090 1 4 0");
		assertEquals(2, old.size());
		assertEquals("Home base", old.get(0).getName());
		assertEquals(MarkerIcons.Design.HOUSE, old.get(0).getDesign());
		assertEquals("", old.get(0).getAccount());
		assertFalse(old.get(0).getId().equals(old.get(1).getId()));
	}

	@Test
	public void unsharedMarkersShowOnlyOnTheirAccount()
	{
		MapPins pins = new MapPins();
		MapPins.Marker other = new MapPins.Marker("18f-3", new WorldPoint(3000, 3000, 0), MarkerIcons.Design.PIN, 0,
			"Alt's", "rsprofile.alt");
		MapPins.Marker old = new MapPins.Marker("18f-4", new WorldPoint(3001, 3000, 0), MarkerIcons.Design.PIN, 0,
			"Before", "");
		pins.setMarkers(Arrays.asList(HOME, other, old));
		assertEquals(3, pins.shown().size());
		pins.setFilter(false, "rsprofile.abc");
		assertEquals(Arrays.asList(HOME, old), pins.shown());
		// logged out: no account to go by, so all
		pins.setFilter(false, null);
		assertEquals(3, pins.shown().size());
	}

	@Test
	public void aMarkersTipStandsOnItsTileAndAClickFindsIt()
	{
		MapPins pins = new MapPins();
		pins.setMarkers(Collections.singletonList(HOME));
		MapViewport.View view = new MapViewport.View(3222.5, 3218.5, 4, 800, 600);
		List<MapPins.Shown> shown = pins.visible(view, MapArea.SURFACE, 0, 100);
		assertEquals(1, shown.size());
		Rectangle at = shown.get(0).getBounds();
		assertEquals(300, at.y + at.height);
		assertEquals(HOME, MapPins.at(shown, at.x + 2, at.y + 2).getMarker());
		assertNull(MapPins.at(shown, 10, 10));
		// another floor: not shown when zoomed in
		assertTrue(pins.visible(view, MapArea.SURFACE, 1, 100).isEmpty());
	}

	@Test
	public void anotherPluginsPlacesGoWithTheIconsWhenZoomedFarOut()
	{
		// the Sailing plugin marks hundreds of charting spots: zoomed out to the whole world they would cover it
		MapPins pins = new MapPins();
		BufferedImage picture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		MapPins.Pin chart = new MapPins.Pin(new WorldPoint(3222, 3218, 0), picture, null, "Charting spot", "", null, false);
		MapPins.Pin clue = new MapPins.Pin(new WorldPoint(3222, 3218, 0), picture, null, "Clue", "", null, true);
		pins.setOthers(Arrays.asList(chart, clue));
		MapViewport.View far = new MapViewport.View(3222.5, 3218.5, MapIcons.ICON_ZOOM / 2, 800, 600);
		List<MapPins.Shown> shown = pins.visible(far, MapArea.SURFACE, 0, 100);
		assertEquals(1, shown.size());
		assertSame(clue, shown.get(0).getPin());
		MapViewport.View near = new MapViewport.View(3222.5, 3218.5, MapIcons.ICON_ZOOM, 800, 600);
		assertEquals(2, pins.visible(near, MapArea.SURFACE, 0, 100).size());
	}

	@Test
	public void anotherPluginsPinKeepsToTheEdgeWhenAsked()
	{
		MapPins pins = new MapPins();
		BufferedImage arrow = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		MapPins.Pin clue = new MapPins.Pin(new WorldPoint(3300, 3218, 0), arrow, null, "Clue", "Dig here", null, true);
		MapPins.Pin other = new MapPins.Pin(new WorldPoint(3300, 3218, 0), arrow, null, "Far", "", null, false);
		pins.setOthers(Arrays.asList(clue, other));
		MapViewport.View view = new MapViewport.View(3222.5, 3218.5, 4, 200, 200);
		List<MapPins.Shown> shown = pins.visible(view, MapArea.SURFACE, 0, 100);
		assertEquals(1, shown.size());
		assertSame(clue, shown.get(0).getPin());
		assertTrue(shown.get(0).isAtEdge());
		assertTrue(shown.get(0).getBounds().x + shown.get(0).getBounds().width <= 200);
	}

	@Test
	public void anotherPluginsWorldMapPointBecomesAPin()
	{
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
		WorldMapPoint point = new WorldMapPoint(new WorldPoint(3000, 3000, 0), image);
		point.setName("Clue scroll");
		point.setJumpOnClick(true);
		point.setTarget(new WorldPoint(3001, 3001, 0));
		MapPins.Pin pin = AdvancedMapPlugin.pinOf(point);
		assertEquals("Clue scroll", pin.getName());
		assertEquals(new WorldPoint(3001, 3001, 0), pin.getJump());
		assertNull(AdvancedMapPlugin.pinOf(new WorldMapPoint(new WorldPoint(3000, 3000, 0), null)));
	}

	@Test
	public void theMarkerBoxChoosesAndSaves()
	{
		MarkerEditor editor = new MarkerEditor();
		editor.create(new WorldPoint(3000, 3000, 0));
		for (char c : "Spot".toCharArray())
		{
			editor.type(c);
		}
		editor.backspace();
		MarkerEditor.Layout layout = MarkerEditor.layout(MapPainter.buttons(1200, 800, false));
		Rectangle star = layout.getDesigns().get(MarkerIcons.Design.STAR.ordinal());
		MarkerEditor.Hit hit = editor.hitAt(star.x + 3, star.y + 3, layout);
		assertEquals(MarkerEditor.Part.DESIGN, hit.getPart());
		editor.choose(hit);
		Rectangle blue = layout.getColors().get(5);
		editor.choose(editor.hitAt(blue.x + 3, blue.y + 3, layout));
		MapPins.Marker made = editor.result("rsprofile.abc");
		assertEquals(new MapPins.Marker(made.getId(), new WorldPoint(3000, 3000, 0), MarkerIcons.Design.STAR, 5, "Spo",
			"rsprofile.abc"), made);
		// a changed marker keeps its id and account
		editor.edit(HOME);
		assertEquals(HOME, editor.result("rsprofile.other"));
		editor.close();
		editor.create(new WorldPoint(3000, 3000, 0));
		// a new marker has no Remove
		Rectangle remove = layout.getRemove();
		assertEquals(MarkerEditor.Part.PANEL, editor.hitAt(remove.x + 3, remove.y + 3, layout).getPart());
		Rectangle panel = layout.getPanel();
		for (Rectangle button : MapPainter.buttons(1200, 800, false).values())
		{
			assertFalse(button.intersects(panel));
		}
		assertEquals(MarkerEditor.Part.OUTSIDE, editor.hitAt(5, 400, layout).getPart());
	}

	@Test
	public void aMarkerChangeSurvivesAReadingThatMissedIt()
	{
		MapPins pins = new MapPins();
		MapPins.Marker spot = new MapPins.Marker("18f-9", new WorldPoint(3000, 3000, 0), MarkerIcons.Design.PIN, 0,
			"Spot", "");
		MapPins.Marker renamed = new MapPins.Marker("18f-9", new WorldPoint(3000, 3000, 0), MarkerIcons.Design.PIN, 0,
			"Spot 2", "");
		pins.setMarkers(Collections.singletonList(HOME));

		// added on the client thread; a reading of the folder from before its save then lands; the save puts it back
		pins.replace(null, spot);
		assertEquals(Arrays.asList(HOME, spot), pins.markers());
		pins.setMarkers(Collections.singletonList(HOME));
		pins.replace(null, spot);
		pins.replace(null, spot);
		assertEquals("done twice is done once", Arrays.asList(HOME, spot), pins.markers());

		// changed in place, by id
		pins.replace(spot, renamed);
		pins.setMarkers(Arrays.asList(HOME, spot));
		pins.replace(spot, renamed);
		assertEquals(Arrays.asList(HOME, renamed), pins.markers());

		// removed: a reading from before the removal does not bring it back once the removal is done again
		pins.replace(renamed, null);
		pins.setMarkers(Arrays.asList(HOME, renamed));
		pins.replace(renamed, null);
		pins.replace(renamed, null);
		assertEquals(Collections.singletonList(HOME), pins.markers());
	}
}
