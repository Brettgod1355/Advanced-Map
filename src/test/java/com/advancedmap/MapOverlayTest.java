/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MapOverlayTest
{
	@Test
	public void thePictureGrowsInStepsAndNeverShrinks()
	{
		assertEquals(new Dimension(768, 512), MapOverlay.frameSize(null, 700, 500));
		BufferedImage frame = new BufferedImage(768, 512, BufferedImage.TYPE_INT_RGB);
		// the box dragged a pixel bigger or smaller, frame after frame: the picture there still fits
		assertNull(MapOverlay.frameSize(frame, 701, 500));
		assertNull(MapOverlay.frameSize(frame, 768, 512));
		assertNull(MapOverlay.frameSize(frame, 520, 320));
		// past the picture's edge: a step bigger in that direction only
		assertEquals(new Dimension(1024, 512), MapOverlay.frameSize(frame, 769, 500));
		assertEquals(new Dimension(768, 768), MapOverlay.frameSize(frame, 600, 513));
	}
}
