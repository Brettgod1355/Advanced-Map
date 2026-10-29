/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiCombatTest
{
	@BeforeClass
	public static void load()
	{
		MultiCombat.load();
	}

	@Test
	public void theBundledAreasCoverTheKnownMultiZones()
	{
		List<MultiCombat.Zone> zones = MultiCombat.zones();
		assertTrue(zones.size() > 90);
		// the Wilderness: the Dark warrior's palace, and the north-east of the free-to-play Wilderness
		assertTrue(MultiCombat.contains(3030, 3630));
		assertTrue(MultiCombat.contains(3200, 3870));
		// not multi: Lumbridge, and Edgeville just south of the Wilderness
		assertFalse(MultiCombat.contains(3222, 3218));
		assertFalse(MultiCombat.contains(3094, 3491));
	}

	@Test
	public void everyAreaIsInOneGroupAndGroupsKeepApart()
	{
		List<MultiCombat.Zone> groups = MultiCombat.groups();
		assertTrue(groups.size() > 1 && groups.size() <= MultiCombat.zones().size());
		for (MultiCombat.Zone zone : MultiCombat.zones())
		{
			int in = 0;
			for (MultiCombat.Zone group : groups)
			{
				Area both = new Area(group.getShape());
				both.intersect(zone.getShape());
				if (!both.isEmpty())
				{
					in++;
				}
			}
			assertEquals(zone.getName() + " is in one group", 1, in);
		}
		for (int i = 0; i < groups.size(); i++)
		{
			for (int j = i + 1; j < groups.size(); j++)
			{
				Rectangle near = new Rectangle(groups.get(i).getBounds());
				near.grow(2, 2);
				assertFalse("groups " + i + " and " + j + " are apart", near.intersects(groups.get(j).getBounds()));
			}
		}
	}

	@Test
	public void theGroupsInViewFillTheSameTilesAsEveryAreaAsOneShape()
	{
		Area all = new Area();
		for (MultiCombat.Zone zone : MultiCombat.zones())
		{
			all.add(zone.getShape());
		}
		// the Wilderness, Falador's edge with the God Wars Dungeon's entrance, a view with no area at all
		for (double[] at : Arrays.asList(new double[]{3150, 3700, 1.5}, new double[]{2980, 3360, 3},
			new double[]{3222, 3218, 4}, new double[]{3100, 3900, 1.25}))
		{
			MapViewport.View view = new MapViewport.View(at[0], at[1], at[2], 640, 400);
			BufferedImage grouped = fill(view, MultiCombat.groups());
			BufferedImage whole = fill(view, Collections.singletonList(new MultiCombat.Zone("all", all, all.getBounds())));
			for (int y = 0; y < 400; y++)
			{
				for (int x = 0; x < 640; x++)
				{
					assertEquals("pixel " + x + "," + y + " at " + Arrays.toString(at), whole.getRGB(x, y),
						grouped.getRGB(x, y));
				}
			}
		}
	}

	/**
	 * The fill of shapes as {@link MultiCombat#draw} fills them. (The edge is left out: one shape of every area, as the
	 * map drew before, carried seams inside areas built from several rectangles, which its edge then drew as lines
	 * across them; the groups have none at those places, so the edges differ there on purpose.)
	 */
	private static BufferedImage fill(MapViewport.View view, List<MultiCombat.Zone> shapes)
	{
		BufferedImage image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.DARK_GRAY);
		g.fillRect(0, 0, 640, 400);
		AffineTransform toScreen = new AffineTransform(view.zoom, 0, 0, -view.zoom, view.screenX(0), view.screenY(0));
		g.setColor(MultiCombat.FILL);
		for (MultiCombat.Zone shape : shapes)
		{
			g.fill(toScreen.createTransformedShape(shape.getShape()));
		}
		g.dispose();
		return image;
	}
}
