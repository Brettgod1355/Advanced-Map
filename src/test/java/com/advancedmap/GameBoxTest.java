/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GameBoxTest
{
	private static final int W = 1600;
	private static final int H = 900;

	@Test
	public void theFirstTimeItIsTwoThirdsOfTheViewInItsMiddle()
	{
		Rectangle outer = new GameBox("", false).outer(W, H);
		assertEquals(new Rectangle(267, 150, 1066, 600), outer);
	}

	@Test
	public void theMapIsTheBoxLessItsFrameAndMaximizedTheWholeView()
	{
		GameBox box = new GameBox("100,50,800,500", false);
		assertEquals(new Rectangle(100 + GameBox.SIDE, 50 + GameBox.BAR, 800 - GameBox.SIDE * 2, 500 - GameBox.BAR * 2),
			box.map(W, H));
		box.setMaximized(true);
		assertEquals(new Rectangle(0, 0, W, H), box.map(W, H));
		// maximized, everything is the map: no frame to grab
		assertEquals(GameBox.Part.MAP, box.partAt(2, 2, W, H));
		assertFalse(box.grab(2, 2, W, H));
	}

	@Test
	public void theFrameHasItsParts()
	{
		GameBox box = new GameBox("100,50,800,500", false);
		assertEquals(GameBox.Part.OUTSIDE, box.partAt(50, 50, W, H));
		assertEquals(GameBox.Part.MAP, box.partAt(500, 300, W, H));
		assertEquals(GameBox.Part.MOVE, box.partAt(500, 55, W, H));
		assertEquals(GameBox.Part.MOVE, box.partAt(500, 545, W, H));
		assertEquals(GameBox.Part.TOP_LEFT, box.partAt(102, 52, W, H));
		assertEquals(GameBox.Part.BOTTOM_RIGHT, box.partAt(897, 547, W, H));
		assertEquals(GameBox.Part.LEFT, box.partAt(102, 300, W, H));
		assertEquals(GameBox.Part.RIGHT, box.partAt(897, 300, W, H));
	}

	@Test
	public void theTopBarMovesItAndItStaysInTheView()
	{
		GameBox box = new GameBox("100,50,800,500", false);
		assertTrue(box.grab(500, 55, W, H));
		box.dragTo(600, 155, W, H);
		assertEquals(new Rectangle(200, 150, 800, 500), box.outer(W, H));
		box.dragTo(5000, 5000, W, H);
		assertEquals(new Rectangle(W - 800, H - 500, 800, 500), box.outer(W, H));
		assertEquals((W - 800) + "," + (H - 500) + ",800,500", box.release());
		assertNull(box.release());
	}

	@Test
	public void aCornerSizesItNoSmallerThanTheLeast()
	{
		GameBox box = new GameBox("100,50,800,500", false);
		assertTrue(box.grab(897, 547, W, H));
		box.dragTo(997, 597, W, H);
		assertEquals(new Rectangle(100, 50, 900, 550), box.outer(W, H));
		box.dragTo(0, 0, W, H);
		assertEquals(new Rectangle(100, 50, GameBox.MIN_WIDTH, GameBox.MIN_HEIGHT), box.outer(W, H));
		box.release();
		// the top-left corner keeps the bottom-right where it is
		assertTrue(box.grab(102, 52, W, H));
		box.dragTo(52, 22, W, H);
		Rectangle grown = box.outer(W, H);
		assertEquals(50, grown.x);
		assertEquals(20, grown.y);
		assertEquals(100 + GameBox.MIN_WIDTH, grown.x + grown.width);
	}

	@Test
	public void aSavedPlaceOffASmallerViewIsBroughtBackIn()
	{
		GameBox box = new GameBox("1500,800,800,500", false);
		Rectangle outer = box.outer(1000, 700);
		assertEquals(new Rectangle(200, 200, 800, 500), outer);
		assertNull(GameBox.parse("not,a,box"));
		assertEquals(new Rectangle(1, 2, 3, 4), GameBox.parse("1,2,3,4"));
	}

	@Test
	public void theFrameDrawsAroundTheMapAndNotOverIt()
	{
		GameBox box = new GameBox("100,50,800,500", false);
		BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		box.draw(g, W, H);
		g.dispose();
		assertTrue(image.getRGB(500, 55) != 0);
		assertEquals(0, image.getRGB(500, 300) & 0xFFFFFF);
	}
}
