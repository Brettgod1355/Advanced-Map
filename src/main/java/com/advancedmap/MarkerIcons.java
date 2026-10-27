/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The pictures a player's own marker can have, drawn in code (no game or wiki artwork): each a shape filled with the
 * marker's color and outlined dark, so it stands out on any part of the map.
 */
final class MarkerIcons
{
	enum Design
	{
		PIN("Pin"),
		FLAG("Flag"),
		STAR("Star"),
		HEART("Heart"),
		SKULL("Skull"),
		DIAMOND("Diamond"),
		TARGET("Target"),
		HOUSE("House"),
		SWORDS("Swords"),
		CHEST("Chest"),
		QUESTION("Question"),
		EXCLAMATION("Alert");

		final String label;

		Design(String label)
		{
			this.label = label;
		}

		static Design of(int ordinal)
		{
			Design[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : PIN;
		}
	}

	/** The colors a marker can have; the first is the default. */
	static final Color[] COLORS = {
		new Color(0xE8423A), new Color(0xF28C28), new Color(0xF2D13A), new Color(0x4CC255), new Color(0x3CC8D8),
		new Color(0x3F7FE8), new Color(0x9B5CE6), new Color(0xEC6FB3), new Color(0xF2F2F2)
	};
	static final String[] COLOR_NAMES = {"Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "White"};

	private static final Color OUTLINE = new Color(0x1A1A1A);
	private static final Color DARK = new Color(0, 0, 0, 150);

	private MarkerIcons()
	{
	}

	static Color color(int index)
	{
		return COLORS[index >= 0 && index < COLORS.length ? index : 0];
	}

	/** Draws a marker's picture in the square of side {@code size} at {@code x}, {@code y} (its top left). */
	static void draw(Graphics2D g, Design design, Color color, double x, double y, double size)
	{
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Stroke stroke = g.getStroke();
		Font font = g.getFont();
		double s = size / 16.0;
		Shape shape = shape(design, x, y, s);
		g.setColor(color);
		g.fill(shape);
		g.setStroke(new BasicStroke((float) Math.max(1, s * 1.2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(OUTLINE);
		g.draw(shape);
		details(g, design, x, y, s);
		g.setStroke(stroke);
		g.setFont(font);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}

	/** A design's outline on a 16-unit grid scaled by {@code s}, at {@code x}, {@code y}. */
	private static Shape shape(Design design, double x, double y, double s)
	{
		switch (design)
		{
			case FLAG:
			{
				Area flag = new Area(new Rectangle2D.Double(x + 3 * s, y + 1 * s, 1.6 * s, 14 * s));
				Path2D cloth = new Path2D.Double();
				cloth.moveTo(x + 4.6 * s, y + 1.5 * s);
				cloth.lineTo(x + 14 * s, y + 4.5 * s);
				cloth.lineTo(x + 4.6 * s, y + 8.5 * s);
				cloth.closePath();
				flag.add(new Area(cloth));
				return flag;
			}
			case STAR:
				return star(x + 8 * s, y + 8.4 * s, 7.6 * s, 3.2 * s, 5);
			case HEART:
			{
				Area heart = new Area(new Ellipse2D.Double(x + 1 * s, y + 2 * s, 7.4 * s, 7.4 * s));
				heart.add(new Area(new Ellipse2D.Double(x + 7.6 * s, y + 2 * s, 7.4 * s, 7.4 * s)));
				Path2D tip = new Path2D.Double();
				tip.moveTo(x + 1.4 * s, y + 7.2 * s);
				tip.lineTo(x + 8 * s, y + 15 * s);
				tip.lineTo(x + 14.6 * s, y + 7.2 * s);
				tip.closePath();
				heart.add(new Area(tip));
				return heart;
			}
			case SKULL:
			{
				Area skull = new Area(new Ellipse2D.Double(x + 2 * s, y + 1 * s, 12 * s, 11 * s));
				skull.add(new Area(new RoundRectangle2D.Double(x + 4.5 * s, y + 9 * s, 7 * s, 6 * s, 2 * s, 2 * s)));
				return skull;
			}
			case DIAMOND:
			{
				Path2D gem = new Path2D.Double();
				gem.moveTo(x + 4 * s, y + 2 * s);
				gem.lineTo(x + 12 * s, y + 2 * s);
				gem.lineTo(x + 15 * s, y + 6 * s);
				gem.lineTo(x + 8 * s, y + 15 * s);
				gem.lineTo(x + 1 * s, y + 6 * s);
				gem.closePath();
				return gem;
			}
			case TARGET:
				return new Ellipse2D.Double(x + 1 * s, y + 1 * s, 14 * s, 14 * s);
			case HOUSE:
			{
				Path2D house = new Path2D.Double();
				house.moveTo(x + 8 * s, y + 1 * s);
				house.lineTo(x + 15 * s, y + 7.5 * s);
				house.lineTo(x + 13 * s, y + 7.5 * s);
				house.lineTo(x + 13 * s, y + 15 * s);
				house.lineTo(x + 3 * s, y + 15 * s);
				house.lineTo(x + 3 * s, y + 7.5 * s);
				house.lineTo(x + 1 * s, y + 7.5 * s);
				house.closePath();
				return house;
			}
			case SWORDS:
			{
				Area swords = new Area(blade(x, y, s, false));
				swords.add(new Area(blade(x, y, s, true)));
				return swords;
			}
			case CHEST:
				return new RoundRectangle2D.Double(x + 1 * s, y + 3.5 * s, 14 * s, 11 * s, 3 * s, 3 * s);
			case QUESTION:
			case EXCLAMATION:
				return new Ellipse2D.Double(x + 0.5 * s, y + 0.5 * s, 15 * s, 15 * s);
			default:
			{
				// a map pin: a round head over a point at the tile
				Area pin = new Area(new Ellipse2D.Double(x + 2.5 * s, y + 0.5 * s, 11 * s, 11 * s));
				Path2D point = new Path2D.Double();
				point.moveTo(x + 3.2 * s, y + 8 * s);
				point.lineTo(x + 8 * s, y + 15.5 * s);
				point.lineTo(x + 12.8 * s, y + 8 * s);
				point.closePath();
				pin.add(new Area(point));
				return pin;
			}
		}
	}

	/** The marks drawn over a design's fill: a pin's hole, a skull's eyes, a target's rings, a chest's lid. */
	private static void details(Graphics2D g, Design design, double x, double y, double s)
	{
		switch (design)
		{
			case PIN:
				g.setColor(DARK);
				g.fill(new Ellipse2D.Double(x + 6 * s, y + 4 * s, 4 * s, 4 * s));
				break;
			case SKULL:
				g.setColor(OUTLINE);
				g.fill(new Ellipse2D.Double(x + 4.3 * s, y + 5 * s, 3 * s, 3.2 * s));
				g.fill(new Ellipse2D.Double(x + 8.7 * s, y + 5 * s, 3 * s, 3.2 * s));
				g.draw(new java.awt.geom.Line2D.Double(x + 7 * s, y + 12 * s, x + 7 * s, y + 15 * s));
				g.draw(new java.awt.geom.Line2D.Double(x + 9 * s, y + 12 * s, x + 9 * s, y + 15 * s));
				break;
			case TARGET:
				g.setColor(OUTLINE);
				g.draw(new Ellipse2D.Double(x + 4.5 * s, y + 4.5 * s, 7 * s, 7 * s));
				g.fill(new Ellipse2D.Double(x + 7 * s, y + 7 * s, 2 * s, 2 * s));
				break;
			case HOUSE:
				g.setColor(OUTLINE);
				g.fill(new Rectangle2D.Double(x + 6.5 * s, y + 10 * s, 3 * s, 5 * s));
				break;
			case CHEST:
				g.setColor(OUTLINE);
				g.draw(new java.awt.geom.Line2D.Double(x + 1 * s, y + 7.5 * s, x + 15 * s, y + 7.5 * s));
				g.fill(new Rectangle2D.Double(x + 7 * s, y + 6.5 * s, 2 * s, 3 * s));
				break;
			case QUESTION:
			case EXCLAMATION:
				g.setColor(OUTLINE);
				g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(12 * s)));
				FontMetrics metrics = g.getFontMetrics();
				String mark = design == Design.QUESTION ? "?" : "!";
				g.drawString(mark, (float) (x + 8 * s - metrics.stringWidth(mark) / 2.0),
					(float) (y + 8 * s + (metrics.getAscent() - metrics.getDescent()) / 2.0));
				break;
			default:
				break;
		}
	}

	private static Shape blade(double x, double y, double s, boolean mirrored)
	{
		GeneralPath blade = new GeneralPath();
		double[][] points = {{2, 1}, {4, 1}, {13, 10}, {14.5, 9}, {15, 11}, {12.5, 12.5}, {14, 14.5}, {12.5, 15}, {11,
			13}, {9, 15}, {7.5, 14}, {9, 12}, {1, 3}};
		for (int i = 0; i < points.length; i++)
		{
			double px = x + (mirrored ? 16 - points[i][0] : points[i][0]) * s;
			double py = y + points[i][1] * s;
			if (i == 0)
			{
				blade.moveTo(px, py);
			}
			else
			{
				blade.lineTo(px, py);
			}
		}
		blade.closePath();
		return blade;
	}

	private static Shape star(double cx, double cy, double outer, double inner, int points)
	{
		Path2D path = new Path2D.Double();
		for (int i = 0; i < points * 2; i++)
		{
			double r = i % 2 == 0 ? outer : inner;
			double angle = Math.PI * i / points - Math.PI / 2;
			double px = cx + Math.cos(angle) * r;
			double py = cy + Math.sin(angle) * r;
			if (i == 0)
			{
				path.moveTo(px, py);
			}
			else
			{
				path.lineTo(px, py);
			}
		}
		path.closePath();
		return path;
	}
}
