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
	private static final MapPins.Marker HOME = new MapPins.Marker(new WorldPoint(3222, 3218, 0),
		MarkerIcons.Design.HOUSE, 3, "Home base");

	@Test
	public void markersSaveAndReadBack()
	{
		MapPins.Marker unnamed = new MapPins.Marker(new WorldPoint(2440, 3090, 1), MarkerIcons.Design.SKULL, 0, "");
		String saved = MapPins.save(Arrays.asList(HOME, unnamed));
		assertEquals(Arrays.asList(HOME, unnamed), MapPins.parse(saved));
		// a damaged line is skipped, not the others
		assertEquals(Collections.singletonList(HOME), MapPins.parse("x y\n" + MapPins.save(Collections.singletonList(HOME))));
		assertEquals("a b", MapPins.cleanName("a\nb"));
		assertTrue(MapPins.parse("").isEmpty());
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
		assertEquals(new MapPins.Marker(new WorldPoint(3000, 3000, 0), MarkerIcons.Design.STAR, 5, "Spo"),
			editor.result());
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
}
