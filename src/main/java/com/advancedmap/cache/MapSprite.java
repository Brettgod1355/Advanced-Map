/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap.cache;

import lombok.Value;

/**
 * A sprite placed on its full canvas (offsets already applied), as RGB pixels where 0 is transparent.
 */
@Value
public class MapSprite
{
	int width;
	int height;
	int[] pixels;

	/** Places a trimmed sprite on its full canvas. */
	public static MapSprite of(int maxWidth, int maxHeight, int offsetX, int offsetY, int width, int height,
		int[] trimmed)
	{
		int w = Math.max(maxWidth, offsetX + width);
		int h = Math.max(maxHeight, offsetY + height);
		int[] pixels = new int[w * h];
		for (int y = 0; y < height; y++)
		{
			System.arraycopy(trimmed, y * width, pixels, (y + offsetY) * w + offsetX, width);
		}
		return new MapSprite(w, h, pixels);
	}
}
