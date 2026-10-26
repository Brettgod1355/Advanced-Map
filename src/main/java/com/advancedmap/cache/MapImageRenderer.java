/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * Advanced Map: adapted from RuneLite's MapImageDumper (net.runelite.cache, runelite-parent-1.13.4). The drawing is
 * unchanged; what changed is where the data comes from (a MapSource over the running client's cache instead of a
 * cache folder on disk), one region at a time, with the command-line tool, whole-world images, world map labels
 * and the inter-map link icons left out. See THIRD_PARTY_NOTICES.md.
 */
package com.advancedmap.cache;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Draws one 64x64 map region the way the OSRS Wiki's map is drawn: blended ground colors, tile shapes, walls,
 * map scenery and map icons, at {@link #MAP_SCALE} pixels per tile.
 */
public class MapImageRenderer
{
	public static final int MAP_SCALE = 4; // this squared is the number of pixels per map square
	private static final int BLEND = 5; // number of surrounding tiles for ground blending

	private static byte[][][] TILE_SHAPE_2D;

	private final int wallColor = (238 + (int) (random() * 20.0D) - 10 << 16) + (238 + (int) (random() * 20.0D) - 10 << 8) + (238 + (int) (random() * 20.0D) - 10);
	private final int doorColor = 238 + (int) (random() * 20.0D) - 10 << 16;

	private final MapSource source;

	public MapImageRenderer(MapSource source)
	{
		this.source = source;
	}

	protected double random()
	{
		// the client would use a random value here, but we prefer determinism
		return -1.2;
	}

	private void drawNeighborObjects(BufferedImage image, int rx, int ry, int dx, int dy, int z)
	{
		Region neighbor = source.region(rx + dx, ry + dy);
		if (neighbor == null)
		{
			return;
		}

		drawObjects(image, Region.X * dx, Region.Y * -dy, neighbor, z);
	}

	public BufferedImage drawRegion(Region region, int z)
	{
		int pixelsX = Region.X * MAP_SCALE;
		int pixelsY = Region.Y * MAP_SCALE;

		// transparent where a floor has no tile, so a floor can be drawn over the ones below it
		BufferedImage image = new BufferedImage(pixelsX, pixelsY, BufferedImage.TYPE_INT_ARGB);

		drawMap(image, 0, 0, z, region);

		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), -1, -1, z);
		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), -1, 0, z);
		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), -1, 1, z);
		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), 0, -1, z);
		drawObjects(image, 0, 0, region, z);
		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), 0, 1, z);
		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), 1, -1, z);
		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), 1, 0, z);
		drawNeighborObjects(image, region.getRegionX(), region.getRegionY(), 1, 1, z);

		return image;
	}

	private void drawMap(BufferedImage image, int drawBaseX, int drawBaseY, int z, Region region)
	{

		int[][][] map = new int[4][][];

		for (int x = 0; x < Region.X; ++x)
		{
			for (int y = 0; y < Region.Y; ++y)
			{
				boolean isBridge = (region.getTileSetting(1, x, Region.Y - y - 1) & 2) != 0;
				int tileZ = z + (isBridge ? 1 : 0);
				if (tileZ >= Region.Z)
				{
					continue;
				}

				int tileSetting = region.getTileSetting(z, x, Region.Y - y - 1);
				if ((tileSetting & 24) == 0)
				{
					if (z == 0 && isBridge)
					{
						drawTile(image, map, region, drawBaseX, drawBaseY, 0, x, y);
					}
					drawTile(image, map, region, drawBaseX, drawBaseY, tileZ, x, y);
				}

				if (tileZ < 3)
				{
					int upTileSetting = region.getTileSetting(z + 1, x, Region.Y - y - 1);
					if ((upTileSetting & 8) != 0)
					{
						drawTile(image, map, region, drawBaseX, drawBaseY, tileZ + 1, x, y);
					}
				}
			}
		}
	}

	private void drawTile(BufferedImage to, int[][][] planes, Region region, int drawBaseX, int drawBaseY, int z, int x, int y)
	{
		int[][] pixels = planes[z];

		if (pixels == null)
		{
			pixels = planes[z] = new int[Region.X * MAP_SCALE][Region.Y * MAP_SCALE];
			drawMap(pixels, region, z);
		}

		for (int i = 0; i < MAP_SCALE; ++i)
		{
			for (int j = 0; j < MAP_SCALE; ++j)
			{
				int argb = pixels[x * MAP_SCALE + i][y * MAP_SCALE + j];
				if (argb != 0)
				{
					to.setRGB(drawBaseX * MAP_SCALE + x * MAP_SCALE + i,
						drawBaseY * MAP_SCALE + y * MAP_SCALE + j,
						argb | 0xFF000000);
				}
			}
		}
	}

	private void drawMap(int[][] pixels, Region region, int z)
	{
		synchronized (MapImageRenderer.class)
		{
			if (TILE_SHAPE_2D == null)
			{
				generateTileShapes();
			}
		}

		int baseX = region.getBaseX();
		int baseY = region.getBaseY();

		int len = Region.X + BLEND * 2;
		int[] hues = new int[len];
		int[] sats = new int[len];
		int[] light = new int[len];
		int[] mul = new int[len];
		int[] num = new int[len];

		boolean hasLeftRegion = source.regionAt(baseX - 1, baseY) != null;
		boolean hasRightRegion = source.regionAt(baseX + Region.X, baseY) != null;
		boolean hasUpRegion = source.regionAt(baseX, baseY + Region.Y) != null;
		boolean hasDownRegion = source.regionAt(baseX, baseY - 1) != null;

		for (int xi = (hasLeftRegion ? -BLEND * 2 : -BLEND); xi < Region.X + (hasRightRegion ? BLEND * 2 : BLEND); ++xi)
		{
			for (int yi = (hasDownRegion ? -BLEND : 0); yi < Region.Y + (hasUpRegion ? BLEND : 0); ++yi)
			{
				int xr = xi + BLEND;
				if (xr >= (hasLeftRegion ? -BLEND : 0) && xr < Region.X + (hasRightRegion ? BLEND : 0))
				{
					Region r = source.regionAt(baseX + xr, baseY + yi);
					if (r != null)
					{
						int underlayId = r.getUnderlayId(z, convert(xr), convert(yi));
						if (underlayId > 0)
						{
							UnderlayDefinition underlay = source.underlay(underlayId - 1);
							hues[yi + BLEND] += underlay.getHue() * 256 / underlay.getHueMultiplier();
							sats[yi + BLEND] += underlay.getSaturation();
							light[yi + BLEND] += underlay.getLightness();
							mul[yi + BLEND] += underlay.getHueMultiplier();
							num[yi + BLEND]++;
						}
					}
				}

				int xl = xi - BLEND;
				if (xl >= (hasLeftRegion ? -BLEND : 0) && xl < Region.X + (hasRightRegion ? BLEND : 0))
				{
					Region r = source.regionAt(baseX + xl, baseY + yi);
					if (r != null)
					{
						int underlayId = r.getUnderlayId(z, convert(xl), convert(yi));
						if (underlayId > 0)
						{
							UnderlayDefinition underlay = source.underlay(underlayId - 1);
							hues[yi + BLEND] -= underlay.getHue() * 256 / underlay.getHueMultiplier();
							sats[yi + BLEND] -= underlay.getSaturation();
							light[yi + BLEND] -= underlay.getLightness();
							mul[yi + BLEND] -= underlay.getHueMultiplier();
							num[yi + BLEND]--;
						}
					}
				}
			}

			if (xi >= 0 && xi < Region.X)
			{
				int runningHues = 0;
				int runningSat = 0;
				int runningLight = 0;
				int runningMultiplier = 0;
				int runningNumber = 0;

				for (int yi = (hasDownRegion ? -BLEND * 2 : -BLEND); yi < Region.Y + (hasUpRegion ? BLEND * 2 : BLEND); ++yi)
				{
					int yu = yi + BLEND;
					if (yu >= (hasDownRegion ? -BLEND : 0) && yu < Region.Y + (hasUpRegion ? BLEND : 0))
					{
						runningHues += hues[yu + BLEND];
						runningSat += sats[yu + BLEND];
						runningLight += light[yu + BLEND];
						runningMultiplier += mul[yu + BLEND];
						runningNumber += num[yu + BLEND];
					}

					int yd = yi - BLEND;
					if (yd >= (hasDownRegion ? -BLEND : 0) && yd < Region.Y + (hasUpRegion ? BLEND : 0))
					{
						runningHues -= hues[yd + BLEND];
						runningSat -= sats[yd + BLEND];
						runningLight -= light[yd + BLEND];
						runningMultiplier -= mul[yd + BLEND];
						runningNumber -= num[yd + BLEND];
					}

					if (yi >= 0 && yi < Region.Y)
					{
						Region r = source.regionAt(baseX + xi, baseY + yi);
						if (r != null)
						{
							int underlayId = r.getUnderlayId(z, convert(xi), convert(yi));
							int overlayId = r.getOverlayId(z, convert(xi), convert(yi));

							if (underlayId > 0 || overlayId > 0)
							{
								int underlayHsl = -1;
								if (underlayId > 0)
								{
									int avgHue = runningHues / runningNumber;
									int avgSat = runningSat / runningNumber;
									int avgLight = runningLight / runningNumber;
									// randomness is added to avgHue here

									//Doesn't seem to get hit
									if (avgLight < 0)
									{
										avgLight = 0;
									}
									else if (avgLight > 255)
									{
										avgLight = 255;
									}

									underlayHsl = packHslFull(avgHue, avgSat, avgLight);
								}

								int underlayRgb = 0;
								if (underlayHsl != -1)
								{
									underlayRgb = JagexColor.getRGBFull(underlayHsl);
								}

								int shape, rotation;
								int overlayRgb = 0;
								if (overlayId == 0)
								{
									shape = rotation = 0;
								}
								else
								{
									shape = r.getOverlayPath(z, convert(xi), convert(yi)) + 1;
									rotation = r.getOverlayRotation(z, convert(xi), convert(yi));

									OverlayDefinition overlayDefinition = source.overlay(overlayId - 1);
									int overlayTexture = overlayDefinition.getTexture();
									int hsl;

									if (overlayTexture >= 0)
									{
										hsl = source.textureColor(overlayTexture);
										//Repack it into full hsl
										hsl = packHslFull(JagexColor.unpackHue((short) hsl) * 4, JagexColor.unpackSaturation((short) hsl) * 32, JagexColor.unpackLuminance((short) hsl) * 2);
									}
									else if (overlayDefinition.getRgbColor() == 0xFF_00FF)
									{
										hsl = -2;
									}
									else
									{
										// randomness added here
										//Overlay Hsl
										hsl = packHslFull(overlayDefinition.getHue(), overlayDefinition.getSaturation(), overlayDefinition.getLightness());
									}

									if (hsl != -2)
									{
										hsl = adjustHSLListness0(hsl);
										overlayRgb = JagexColor.getRGBFull(hsl);
									}

									//Water, some cliffs, some buildings
									if (overlayDefinition.getSecondaryRgbColor() != -1)
									{
										int hue = overlayDefinition.getOtherHue();
										int sat = overlayDefinition.getOtherSaturation();
										int olight = overlayDefinition.getOtherLightness();
										hsl = packHslFull(hue, sat, olight);
										overlayRgb = JagexColor.getRGBFull(hsl);
									}
								}


								if (shape == 0)
								{
									int drawX = xi;
									int drawY = Region.Y - 1 - yi;
									if (underlayRgb != 0)
									{
										drawMapSquare(pixels, drawX, drawY, underlayRgb);
									}
								}
								else if (shape == 1)
								{
									int drawX = xi;
									int drawY = Region.Y - 1 - yi;
									drawMapSquare(pixels, drawX, drawY, overlayRgb);
								}
								else
								{
									shape--; //This is how jagex does it.
									int drawX = xi * MAP_SCALE;
									int drawY = (Region.Y - 1 - yi) * MAP_SCALE;
									rotation = convertTileRotation(rotation, shape);
									shape = convertTileShape(shape);
									if (underlayRgb != 0)
									{
										//blending between ground types
										int idx = 0;
										for (int iy = 0; iy < MAP_SCALE; ++iy)
										{
											for (int ix = 0; ix < MAP_SCALE; ++ix)
											{
												int p = TILE_SHAPE_2D[shape - 1][rotation][idx++] == 0 ? underlayRgb : overlayRgb;
												setPixels(pixels, drawX + ix, drawY + iy, p);
											}
										}
									}
									else
									{
										//Only draw the overlay portion of the tile. Used extensively for the coastlines of tutorial island.
										int rotIdx = 0;
										for (int iy = 0; iy < MAP_SCALE; ++iy)
										{
											for (int ix = 0; ix < MAP_SCALE; ++ix)
											{
												if (TILE_SHAPE_2D[shape - 1][rotation][rotIdx++] != 0)
												{
													setPixels(pixels, drawX + ix, drawY + iy, overlayRgb);
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private void setPixels(int[][] pixels, int x, int y, int value)
	{
		if (x >= 0 && y >= 0 && x < pixels.length && y < pixels[x].length)
		{
			pixels[x][y] = value;
		}
	}

	private int convertTileRotation(int rotation, int shape)
	{
		if (shape == 9)
		{
			rotation = rotation + 1 & 3;
		}

		if (shape == 10)
		{
			rotation = rotation + 3 & 3;
		}

		if (shape == 11)
		{
			rotation = rotation + 3 & 3;
		}

		return rotation;
	}

	int convertTileShape(int shape)
	{
		return shape != 9 && shape != 10 ? (shape == 11 ? 8 : shape) : 1;
	}

	private void generateTileShapes()
	{
		TILE_SHAPE_2D = new byte[8][4][];
		genTileShapes1();
		genTileShapes2();
		genTileShapes3();
		genTileShapes4();
		genTileShapes5();
		genTileShapes6();
		genTileShapes7();
		genTileShapes8();
	}

	private void genTileShapes1()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		int index = 0;

		int x;
		int y;
		for (x = 0; x < MAP_SCALE; ++x)
		{
			for (y = 0; y < MAP_SCALE; ++y)
			{
				if (y <= x)
				{
					shape[index] = -1;
				}

				++index;
			}
		}

		TILE_SHAPE_2D[0][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		index = 0;

		for (x = MAP_SCALE - 1; x >= 0; --x)
		{
			for (y = 0; y < MAP_SCALE; ++y)
			{
				if (y <= x)
				{
					shape[index] = -1;
				}

				++index;
			}
		}

		TILE_SHAPE_2D[0][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		index = 0;

		for (x = 0; x < MAP_SCALE; ++x)
		{
			for (y = 0; y < MAP_SCALE; ++y)
			{
				if (y >= x)
				{
					shape[index] = -1;
				}

				++index;
			}
		}

		TILE_SHAPE_2D[0][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		index = 0;

		for (x = MAP_SCALE - 1; x >= 0; --x)
		{
			for (y = 0; y < MAP_SCALE; ++y)
			{
				if (y >= x)
				{
					shape[index] = -1;
				}

				++index;
			}
		}

		TILE_SHAPE_2D[0][3] = shape;
	}

	void genTileShapes2()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		int var2 = 0;

		int var3;
		int var4;
		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var4 <= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[1][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var2 >= 0 && var2 < shape.length)
				{
					if (var4 >= var3 << 1)
					{
						shape[var2] = -1;
					}

					++var2;
				}
				else
				{
					++var2;
				}
			}
		}

		TILE_SHAPE_2D[1][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 <= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[1][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 >= var3 << 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[1][3] = shape;
	}

	void genTileShapes3()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		int var2 = 0;

		int var3;
		int var4;
		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 <= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[2][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var4 >= var3 << 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[2][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var4 <= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[2][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 >= var3 << 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[2][3] = shape;
	}

	void genTileShapes4()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		int var2 = 0;

		int var3;
		int var4;
		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var4 >= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[3][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var4 <= var3 << 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[3][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 >= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[3][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 <= var3 << 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[3][3] = shape;
	}

	void genTileShapes5()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		int var2 = 0;

		int var3;
		int var4;
		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 >= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[4][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = MAP_SCALE - 1; var3 >= 0; --var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var4 <= var3 << 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[4][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = 0; var4 < MAP_SCALE; ++var4)
			{
				if (var4 >= var3 >> 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[4][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var2 = 0;

		for (var3 = 0; var3 < MAP_SCALE; ++var3)
		{
			for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
			{
				if (var4 <= var3 << 1)
				{
					shape[var2] = -1;
				}

				++var2;
			}
		}

		TILE_SHAPE_2D[4][3] = shape;
	}

	void genTileShapes6()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		boolean var2 = false;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		int var3 = 0;

		int var4;
		int var5;
		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var5 <= MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[5][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var4 <= MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[5][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var5 >= MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[5][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var4 >= MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[5][3] = shape;
	}

	void genTileShapes7()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		boolean var2 = false;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		int var3 = 0;

		int var4;
		int var5;
		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var5 <= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[6][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var5 <= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[6][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
		{
			for (var5 = MAP_SCALE - 1; var5 >= 0; --var5)
			{
				if (var5 <= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[6][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = MAP_SCALE - 1; var5 >= 0; --var5)
			{
				if (var5 <= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[6][3] = shape;
	}

	void genTileShapes8()
	{
		byte[] shape = new byte[MAP_SCALE * MAP_SCALE];
		boolean var2 = false;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		int var3 = 0;

		int var4;
		int var5;
		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var5 >= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[7][0] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
		{
			for (var5 = 0; var5 < MAP_SCALE; ++var5)
			{
				if (var5 >= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[7][1] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = MAP_SCALE - 1; var4 >= 0; --var4)
		{
			for (var5 = MAP_SCALE - 1; var5 >= 0; --var5)
			{
				if (var5 >= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[7][2] = shape;
		shape = new byte[MAP_SCALE * MAP_SCALE];
		var3 = 0;

		for (var4 = 0; var4 < MAP_SCALE; ++var4)
		{
			for (var5 = MAP_SCALE - 1; var5 >= 0; --var5)
			{
				if (var5 >= var4 - MAP_SCALE / 2)
				{
					shape[var3] = -1;
				}

				++var3;
			}
		}

		TILE_SHAPE_2D[7][3] = shape;
	}

	private static int convert(int d)
	{
		if (d >= 0)
		{
			return d % 64;
		}
		else
		{
			return 64 - -(d % 64) - 1;
		}
	}

	private void drawObjects(BufferedImage image, int drawBaseX, int drawBaseY, Region region, int z)
	{

		List<Location> planeLocs = new ArrayList<>();
		List<Location> pushDownLocs = new ArrayList<>();
		List<List<Location>> layers = Arrays.asList(planeLocs, pushDownLocs);
		for (int localX = 0; localX < Region.X; localX++)
		{
			int regionX = localX + region.getBaseX();
			for (int localY = 0; localY < Region.Y; localY++)
			{
				int regionY = localY + region.getBaseY();

				planeLocs.clear();
				pushDownLocs.clear();
				boolean isBridge = (region.getTileSetting(1, localX, localY) & 2) != 0;
				int tileZ = z + (isBridge ? 1 : 0);

				for (Location loc : region.getLocations())
				{
					Position pos = loc.getPosition();
					if (pos.getX() != regionX || pos.getY() != regionY)
					{
						continue;
					}

					if (pos.getZ() == tileZ && (region.getTileSetting(z, localX, localY) & 24) == 0)
					{
						planeLocs.add(loc);
					}
					else if (z < 3 && pos.getZ() == tileZ + 1 && (region.getTileSetting(z + 1, localX, localY) & 8) != 0)
					{
						pushDownLocs.add(loc);
					}
				}

				for (List<Location> locs : layers)
				{

					// walls, doors and fences
					{
						for (Location location : locs)
						{
							int type = location.getType();
							if (type >= 0 && type <= 3)
							{
								int rotation = location.getOrientation();

								ObjectDefinition object = source.object(location.getId());

								int drawX = (drawBaseX + localX) * MAP_SCALE;
								int drawY = (drawBaseY + (Region.Y - object.getSizeY() - localY)) * MAP_SCALE;

								int rgb = wallColor;
								if (object.getWallOrDoor() != 0)
								{
									rgb = doorColor;
								}
								rgb |= 0xFF000000;

								if (object.getMapSceneID() != -1)
								{
									blitMapDecoration(image, drawX, drawY, object);
								}
								else if (drawX >= 0 && drawY >= 0 && drawX < image.getWidth() && drawY < image.getHeight())
								{
									//Straight walls/doors
									if (type == 0 || type == 2)
									{
										int xOffset = rotation == 2 ? MAP_SCALE - 1 : 0;
										int yOffset = rotation == 3 ? MAP_SCALE - 1 : 0;
										do
										{
											image.setRGB(drawX + xOffset, drawY + yOffset, rgb);
											switch (rotation)
											{
												case 0:
												case 2:
													yOffset++;
													break;
												case 1:
												case 3:
													xOffset++;
													break;
											}
										}
										while (xOffset < MAP_SCALE && yOffset < MAP_SCALE);
									}

									//Single dots/pillars
									if (type == 3)
									{
										if (rotation == 0)
										{
											image.setRGB(drawX + 0, drawY + 0, rgb);
										}
										else if (rotation == 1)
										{
											image.setRGB(drawX + MAP_SCALE - 1, drawY + 0, rgb);
										}
										else if (rotation == 2)
										{
											image.setRGB(drawX + MAP_SCALE - 1, drawY + MAP_SCALE - 1, rgb);
										}
										else if (rotation == 3)
										{
											image.setRGB(drawX + 0, drawY + MAP_SCALE - 1, rgb);
										}
									}

									//Corners
									if (type == 2)
									{
										int xOffset = rotation == 1 ? MAP_SCALE - 1 : 0;
										int yOffset = rotation == 2 ? MAP_SCALE - 1 : 0;
										do
										{
											image.setRGB(drawX + xOffset, drawY + yOffset, rgb);
											switch (rotation)
											{
												case 1:
												case 3:
													yOffset++;
													break;
												case 0:
												case 2:
													xOffset++;
													break;
											}
										}
										while (xOffset < MAP_SCALE && yOffset < MAP_SCALE);
									}
								}
							}
						}

						for (Location location : locs)
						{
							int type = location.getType();
							if (type == 9)
							{
								int rotation = location.getOrientation();

								ObjectDefinition object = source.object(location.getId());

								int drawX = (drawBaseX + localX) * MAP_SCALE;
								int drawY = (drawBaseY + (Region.Y - object.getSizeY() - localY)) * MAP_SCALE;

								if (object.getMapSceneID() != -1)
								{
									blitMapDecoration(image, drawX, drawY, object);
									continue;
								}

								if (drawX >= 0 && drawY >= 0 && drawX < image.getWidth() && drawY < image.getHeight())
								{
									int rgb = this.wallColor;
									if (object.getWallOrDoor() != 0)
									{
										rgb = this.doorColor;
									}
									rgb |= 0xFF000000;

									if (rotation != 0 && rotation != 2)
									{
										int xOffset = 0;
										int yOffset = 0;
										do
										{
											image.setRGB(drawX + xOffset, drawY + yOffset, rgb);
											yOffset++;
											xOffset++;
										}
										while (xOffset < MAP_SCALE && yOffset < MAP_SCALE);
									}
									else
									{
										int xOffset = 0;
										int yOffset = MAP_SCALE - 1;
										do
										{
											image.setRGB(drawX + xOffset, drawY + yOffset, rgb);
											yOffset--;
											xOffset++;
										}
										while (xOffset < MAP_SCALE && yOffset >= 0);
									}
								}
							}
						}
					}

					//Drawing map sprites
					for (Location location : locs)
					{
						int type = location.getType();
						if (type == 22 || (type >= 9 && type <= 11))
						{
							ObjectDefinition object = source.object(location.getId());

							int drawX = (drawBaseX + localX) * MAP_SCALE;
							//What is offsetY?
							int objSizeOffset = Math.max(2, object.getOffsetY());
							int drawY = (drawBaseY + (Region.Y - objSizeOffset - localY)) * MAP_SCALE;

							if (object.getMapSceneID() != -1)
							{
								blitMapDecoration(image, drawX, drawY, object);
							}
						}
					}
				}
			}
		}
	}

	private int packHslFull(int hue, int saturation, int light)
	{
		return JagexColor.packHSLFull(hue, saturation, light);
	}

	static final int adjustHSLListness0(int hsl)
	{
		double multiplier = 0.898D;
		int hue = JagexColor.unpackHueFull(hsl);
		int saturation = JagexColor.unpackSaturationFull(hsl);
		int light = JagexColor.unpackLuminanceFull(hsl);
		int constant = (int) (light * multiplier);
		if (constant < 2)
		{
			constant = 2;
		}
		else if (constant > 255)
		{
			constant = 255;
		}

		return JagexColor.packHSLFull(hue, saturation, constant);
	}

	private void drawMapSquare(int[][] pixels, int x, int y, int rgb)
	{
		x *= MAP_SCALE;
		y *= MAP_SCALE;

		for (int i = 0; i < MAP_SCALE; ++i)
		{
			for (int j = 0; j < MAP_SCALE; ++j)
			{
				pixels[x + i][y + j] = rgb;
			}
		}
	}

	private void blitMapDecoration(BufferedImage dst, int x, int y, ObjectDefinition object)
	{
		MapSprite sprite = source.mapScene(object.getMapSceneID());
		if (sprite == null)
		{
			return;
		}
		float scale = MAP_SCALE / (float) 4;
		blitIcon(dst, x, y + MAP_SCALE, sprite, scale);
	}

	private void blitIcon(BufferedImage dst, int x, int y, MapSprite sprite, float scale)
	{
		int displayHeight = (int) (sprite.getHeight() * scale);
		int displayWidth = (int) (sprite.getWidth() * scale);
		float stepSizeHeight = 1 + 1 - scale;
		float stepSizeWidth = 1 + 1 - scale;

		int ymin = Math.max(0, -y);
		int ymax = Math.min(displayHeight, dst.getHeight() - y);

		int xmin = Math.max(0, -x);
		int xmax = Math.min(displayWidth, dst.getWidth() - x);

		float indexX = 0;
		float indexY = 0;
		for (int yo = ymin; yo < ymax; yo++)
		{
			for (int xo = xmin; xo < xmax; xo++)
			{
				int index = (int) (indexX) + ((int) (indexY) * (sprite.getWidth()));
				int argb = sprite.getPixels()[index];
				if (argb != 0)
				{
					dst.setRGB(x + xo, y + yo, argb | 0xFF000000);
				}
				indexX += stepSizeWidth;
			}
			indexY += stepSizeHeight;
			indexX = 0;
		}
	}
}
