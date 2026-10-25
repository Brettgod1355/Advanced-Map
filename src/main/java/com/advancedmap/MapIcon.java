/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** The plugin's icon, drawn in code: a folded paper map with a dotted route and a pin. */
final class MapIcon
{
	private static final Color PAPER_LIGHT = new Color(0xE9D9AE);
	private static final Color PAPER_DARK = new Color(0xCDB680);
	private static final Color EDGE = new Color(0x4A3A22);
	private static final Color ROUTE = new Color(0xB3261E);
	private static final Color PIN = new Color(0xD93A2B);

	private MapIcon()
	{
	}

	static BufferedImage draw(int size)
	{
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		double s = size / 16.0;

		// three panels of a folded map, the middle one lower
		double[][] panels = {{1, 3, 5.67, 2, 5.67, 14, 1, 15}, {5.67, 2, 10.33, 3, 10.33, 15, 5.67, 14},
			{10.33, 3, 15, 2, 15, 14, 10.33, 15}};
		for (int i = 0; i < panels.length; i++)
		{
			double[] p = panels[i];
			Polygon panel = new Polygon(
				new int[]{px(p[0], s), px(p[2], s), px(p[4], s), px(p[6], s)},
				new int[]{px(p[1], s), px(p[3], s), px(p[5], s), px(p[7], s)}, 4);
			g.setColor(i == 1 ? PAPER_DARK : PAPER_LIGHT);
			g.fill(panel);
			g.setColor(EDGE);
			g.setStroke(new BasicStroke((float) Math.max(1, 0.9 * s)));
			g.draw(panel);
		}

		Path2D route = new Path2D.Double();
		route.moveTo(2.8 * s, 12.5 * s);
		route.curveTo(5 * s, 9 * s, 8 * s, 12.5 * s, 11 * s, 7.5 * s);
		g.setColor(ROUTE);
		g.setStroke(new BasicStroke((float) Math.max(1, 1.1 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10,
			new float[]{(float) (1.6 * s), (float) (1.4 * s)}, 0));
		g.draw(route);

		double r = 2.2 * s;
		double cx = 11.6 * s;
		double cy = 5.6 * s;
		Path2D pin = new Path2D.Double();
		pin.moveTo(cx - r * 0.85, cy + r * 0.5);
		pin.lineTo(cx, cy + r * 2.2);
		pin.lineTo(cx + r * 0.85, cy + r * 0.5);
		pin.closePath();
		g.setColor(PIN);
		g.fill(pin);
		g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
		g.setColor(EDGE);
		g.setStroke(new BasicStroke((float) Math.max(1, 0.7 * s)));
		g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
		g.setColor(PAPER_LIGHT);
		double dot = r * 0.75;
		g.fill(new Ellipse2D.Double(cx - dot / 2, cy - dot / 2, dot, dot));
		g.dispose();
		return image;
	}

	private static int px(double v, double scale)
	{
		return (int) Math.round(v * scale);
	}
}
