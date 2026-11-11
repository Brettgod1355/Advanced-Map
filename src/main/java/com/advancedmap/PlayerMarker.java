/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import lombok.RequiredArgsConstructor;

/**
 * How the map marks where you are, and the locate button's picture (a player's request, issue #3: the crosshair's
 * size and color, and others to choose from). Each is drawn twice on the map: wide in a dark edge, then in its color.
 */
@RequiredArgsConstructor
public enum PlayerMarker
{
	CROSSHAIR("Crosshair"),
	DOT("Dot"),
	RING("Ring"),
	PIN("Pin"),
	X("X");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}

	/**
	 * Draws the marker on a point, {@code scale} times its size (1 on the locate button), its lines {@code width}
	 * wide. A pin's point is on the spot; the others are centred on it.
	 */
	void draw(Graphics2D g, double cx, double cy, double scale, Color color, float width)
	{
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(color);
		// the dot in the middle grows with the line, so the dark edge pass rims it too
		double dot = 3.5 * scale + (width - 2.5) / 2;
		switch (this)
		{
			case DOT:
				double r = 6 * scale;
				g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
				g.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
				break;
			case RING:
				double outer = 8 * scale;
				g.draw(new Ellipse2D.Double(cx - outer, cy - outer, 2 * outer, 2 * outer));
				g.fill(new Ellipse2D.Double(cx - dot, cy - dot, 2 * dot, 2 * dot));
				break;
			case PIN:
				// a teardrop: its head above the spot, its point on it
				double head = 6 * scale;
				double top = cy - 16 * scale;
				Path2D pin = new Path2D.Double();
				pin.moveTo(cx, cy);
				pin.lineTo(cx - head * 0.85, top + head * 1.5);
				pin.append(new java.awt.geom.Arc2D.Double(cx - head, top, 2 * head, 2 * head, 210, -240,
					java.awt.geom.Arc2D.OPEN), true);
				pin.closePath();
				g.fill(pin);
				g.draw(pin);
				break;
			case X:
				// two thick crossed bars, their middle on the spot
				double arm = 7 * scale;
				g.setStroke(new BasicStroke(width + 2.5f * (float) scale, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(new Line2D.Double(cx - arm, cy - arm, cx + arm, cy + arm));
				g.draw(new Line2D.Double(cx - arm, cy + arm, cx + arm, cy - arm));
				break;
			default:
				double ring = 8 * scale;
				double tick = 13 * scale;
				g.draw(new Ellipse2D.Double(cx - ring, cy - ring, 2 * ring, 2 * ring));
				g.draw(new Line2D.Double(cx, cy - tick, cx, cy - ring));
				g.draw(new Line2D.Double(cx, cy + ring, cx, cy + tick));
				g.draw(new Line2D.Double(cx - tick, cy, cx - ring, cy));
				g.draw(new Line2D.Double(cx + ring, cy, cx + tick, cy));
				g.fill(new Ellipse2D.Double(cx - dot, cy - dot, 2 * dot, 2 * dot));
				break;
		}
		g.setStroke(stroke);
	}
}
