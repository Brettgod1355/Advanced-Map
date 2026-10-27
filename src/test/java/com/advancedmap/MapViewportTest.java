/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MapViewportTest
{
	private static final double EPSILON = 1e-9;

	@Test
	public void downFromLumbridgeCastleLooksAtItsCellar()
	{
		MapViewport ground = MapArea.SURFACE.newViewport();
		ground.centerOn(3210, 3218);
		ground.setZoom(6);
		MapViewport under = MapArea.UNDERGROUND.newViewport();
		under.follow(ground, MapArea.UNDERGROUND_SHIFT);
		MapViewport.View view = under.view(800, 600);
		// the cellar under the castle, 3210, 9618
		assertEquals(400, view.screenX(3210.5), EPSILON);
		assertEquals(300, view.screenY(9618.5), EPSILON);
		assertEquals(6, view.zoom, EPSILON);
	}

	@Test
	public void onlyADungeonUnderTheGroundLeadsBackUp()
	{
		// Lumbridge Castle's cellar lies under the castle
		org.junit.Assert.assertTrue(MapArea.underTheGround(3210.5, 9618.5));
		// Zanaris (2400, 4440) is under nothing
		org.junit.Assert.assertFalse(MapArea.underTheGround(2400.5, 4440.5));
		// a surface tile is not underground at all
		org.junit.Assert.assertFalse(MapArea.underTheGround(3210.5, 3218.5));
	}

	@Test
	public void theCenteredTileIsInTheMiddleOfTheScreen()
	{
		MapViewport viewport = new MapViewport();
		viewport.centerOn(3222, 3218);
		viewport.setZoom(4);
		MapViewport.View view = viewport.view(800, 600);
		assertEquals(400, view.screenX(3222.5), EPSILON);
		assertEquals(300, view.screenY(3218.5), EPSILON);
		// north is up: a tile further north is higher on screen
		assertEquals(300 - 4, view.screenY(3219.5), EPSILON);
		assertEquals(400 + 4, view.screenX(3223.5), EPSILON);
	}

	@Test
	public void screenAndWorldConvertBothWays()
	{
		MapViewport viewport = new MapViewport();
		viewport.centerOn(1000, 2000);
		viewport.setZoom(2.5);
		MapViewport.View view = viewport.view(1024, 768);
		for (double x : new double[]{0, 13.25, 511, 1023})
		{
			assertEquals(x, view.screenX(view.worldX(x)), EPSILON);
			assertEquals(x, view.screenY(view.worldY(x)), EPSILON);
		}
	}

	@Test
	public void draggingMovesTheMapWithTheMouse()
	{
		MapViewport viewport = new MapViewport();
		viewport.centerOn(3000, 3000);
		viewport.setZoom(2);
		MapViewport.View before = viewport.view(800, 600);
		double wx = before.worldX(100);
		double wy = before.worldY(100);
		viewport.drag(30, -20);
		MapViewport.View after = viewport.view(800, 600);
		// the world point that was under the mouse is still under it
		assertEquals(130, after.screenX(wx), EPSILON);
		assertEquals(80, after.screenY(wy), EPSILON);
	}

	@Test
	public void zoomKeepsThePointUnderTheMouseStill()
	{
		MapViewport viewport = new MapViewport();
		viewport.centerOn(3200, 3200);
		viewport.setZoom(1);
		MapViewport.View before = viewport.view(800, 600);
		double wx = before.worldX(650);
		double wy = before.worldY(120);
		viewport.zoomAt(3, 650, 120, 800, 600);
		MapViewport.View after = viewport.view(800, 600);
		assertEquals(3, after.zoom, EPSILON);
		assertEquals(650, after.screenX(wx), EPSILON);
		assertEquals(120, after.screenY(wy), EPSILON);
	}

	@Test
	public void zoomStaysWithinItsLimits()
	{
		MapViewport viewport = new MapViewport();
		viewport.setZoom(1000);
		assertEquals(MapViewport.MAX_ZOOM, viewport.view(1, 1).zoom, EPSILON);
		viewport.zoomAt(1e-6, 0, 0, 1, 1);
		assertEquals(MapViewport.MIN_ZOOM, viewport.view(1, 1).zoom, EPSILON);
	}
}
