/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;

/**
 * The box the map takes over the game: moved by the bars along its top and bottom (grip dots), sized by its corners
 * and sides as a window is, or maximized to fill the game view. Outside it the game takes the mouse as usual. Its
 * place and size are kept as "x,y,width,height" on the game view; maximized, it fills the view and has no frame.
 */
final class GameBox
{
	/** The bars along the top and bottom that move the box, and the sides that size it, in pixels. */
	static final int BAR = 12;
	static final int SIDE = 6;
	/** The corners that size the box. */
	static final int CORNER = 18;
	/** The box is never made smaller than this (unless the game view itself is smaller). */
	static final int MIN_WIDTH = 520;
	static final int MIN_HEIGHT = 320;

	private static final Color FRAME = new Color(0x10, 0x18, 0x22, 235);
	private static final Color EDGE = new Color(0x5A5040);
	private static final Color GRIP = new Color(0xC8C2B0);

	/** What a point is on: outside the box, the map inside it, a bar that moves it, or a side or corner that sizes it. */
	enum Part
	{
		OUTSIDE,
		MAP,
		MOVE,
		LEFT,
		RIGHT,
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT
	}

	/** Where the box is (its frame included), or null until first placed. */
	private volatile Rectangle bounds;
	private volatile boolean maximized;
	private Part grabbed;
	private int grabX;
	private int grabY;
	private Rectangle grabBounds;

	GameBox(String saved, boolean maximized)
	{
		this.bounds = parse(saved);
		this.maximized = maximized;
	}

	boolean isMaximized()
	{
		return maximized;
	}

	void setMaximized(boolean on)
	{
		maximized = on;
	}

	/** The box on a game view of that size, frame included: all of it when maximized; the saved place, kept inside. */
	Rectangle outer(int width, int height)
	{
		if (maximized)
		{
			return new Rectangle(0, 0, width, height);
		}
		Rectangle at = bounds;
		if (at == null)
		{
			// first time: two thirds of the view, in its middle
			int w = Math.max(MIN_WIDTH, width * 2 / 3);
			int h = Math.max(MIN_HEIGHT, height * 2 / 3);
			at = new Rectangle((width - w) / 2, (height - h) / 2, w, h);
		}
		return keepInside(at, width, height);
	}

	/** Where the map is drawn: the box less its frame. */
	Rectangle map(int width, int height)
	{
		Rectangle outer = outer(width, height);
		if (maximized)
		{
			return outer;
		}
		return new Rectangle(outer.x + SIDE, outer.y + BAR, Math.max(1, outer.width - SIDE * 2),
			Math.max(1, outer.height - BAR * 2));
	}

	static Rectangle keepInside(Rectangle at, int width, int height)
	{
		int w = Math.min(width, Math.max(Math.min(MIN_WIDTH, width), at.width));
		int h = Math.min(height, Math.max(Math.min(MIN_HEIGHT, height), at.height));
		int x = Math.max(0, Math.min(width - w, at.x));
		int y = Math.max(0, Math.min(height - h, at.y));
		return new Rectangle(x, y, w, h);
	}

	/** What a point of the game view is on. */
	Part partAt(int x, int y, int width, int height)
	{
		Rectangle outer = outer(width, height);
		if (!outer.contains(x, y))
		{
			return Part.OUTSIDE;
		}
		if (maximized || map(width, height).contains(x, y))
		{
			return Part.MAP;
		}
		boolean left = x < outer.x + CORNER;
		boolean right = x >= outer.x + outer.width - CORNER;
		boolean top = y < outer.y + CORNER;
		boolean bottom = y >= outer.y + outer.height - CORNER;
		if (top && left)
		{
			return Part.TOP_LEFT;
		}
		if (top && right)
		{
			return Part.TOP_RIGHT;
		}
		if (bottom && left)
		{
			return Part.BOTTOM_LEFT;
		}
		if (bottom && right)
		{
			return Part.BOTTOM_RIGHT;
		}
		if (y < outer.y + BAR || y >= outer.y + outer.height - BAR)
		{
			return Part.MOVE;
		}
		return x < outer.x + SIDE ? Part.LEFT : Part.RIGHT;
	}

	/** A press on the frame: starts moving or sizing the box. False when the point is not on the frame. */
	boolean grab(int x, int y, int width, int height)
	{
		Part part = partAt(x, y, width, height);
		if (part == Part.OUTSIDE || part == Part.MAP)
		{
			return false;
		}
		grabbed = part;
		grabX = x;
		grabY = y;
		grabBounds = outer(width, height);
		return true;
	}

	boolean isGrabbed()
	{
		return grabbed != null;
	}

	/** The mouse moved while the frame is held: the box follows it, moved or sized. */
	void dragTo(int x, int y, int width, int height)
	{
		Part part = grabbed;
		Rectangle from = grabBounds;
		if (part == null || from == null)
		{
			return;
		}
		int dx = x - grabX;
		int dy = y - grabY;
		int left = from.x;
		int top = from.y;
		int right = from.x + from.width;
		int bottom = from.y + from.height;
		switch (part)
		{
			case MOVE:
				bounds = keepInside(new Rectangle(from.x + dx, from.y + dy, from.width, from.height), width, height);
				return;
			case LEFT:
				left = Math.min(right - MIN_WIDTH, left + dx);
				break;
			case RIGHT:
				right = Math.max(left + MIN_WIDTH, right + dx);
				break;
			case TOP_LEFT:
				left = Math.min(right - MIN_WIDTH, left + dx);
				top = Math.min(bottom - MIN_HEIGHT, top + dy);
				break;
			case TOP_RIGHT:
				right = Math.max(left + MIN_WIDTH, right + dx);
				top = Math.min(bottom - MIN_HEIGHT, top + dy);
				break;
			case BOTTOM_LEFT:
				left = Math.min(right - MIN_WIDTH, left + dx);
				bottom = Math.max(top + MIN_HEIGHT, bottom + dy);
				break;
			default:
				right = Math.max(left + MIN_WIDTH, right + dx);
				bottom = Math.max(top + MIN_HEIGHT, bottom + dy);
				break;
		}
		// sized past the view's edge: stopped at it, not moved back in
		left = Math.max(0, left);
		top = Math.max(0, top);
		right = Math.min(width, right);
		bottom = Math.min(height, bottom);
		bounds = keepInside(new Rectangle(left, top, right - left, bottom - top), width, height);
	}

	/** The frame let go: the box's place to save ("x,y,width,height"), or null when it was not held. */
	String release()
	{
		if (grabbed == null)
		{
			return null;
		}
		grabbed = null;
		grabBounds = null;
		return format(bounds);
	}

	static String format(Rectangle at)
	{
		return at == null ? "" : at.x + "," + at.y + "," + at.width + "," + at.height;
	}

	static Rectangle parse(String saved)
	{
		if (saved == null)
		{
			return null;
		}
		String[] f = saved.split(",");
		if (f.length != 4)
		{
			return null;
		}
		try
		{
			return new Rectangle(Integer.parseInt(f[0].trim()), Integer.parseInt(f[1].trim()), Integer.parseInt(f[2].trim()),
				Integer.parseInt(f[3].trim()));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Draws the frame round the map: the bars along the top and bottom with grip dots in their middle (they move the
	 * box), and a corner mark at each corner (they size it), as windows show. Nothing when maximized.
	 */
	void draw(Graphics2D g, int width, int height)
	{
		if (maximized)
		{
			return;
		}
		Rectangle outer = outer(width, height);
		Rectangle map = map(width, height);
		Object antialiasing = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(FRAME);
		g.fillRect(outer.x, outer.y, outer.width, BAR);
		g.fillRect(outer.x, outer.y + outer.height - BAR, outer.width, BAR);
		g.fillRect(outer.x, map.y, SIDE, map.height);
		g.fillRect(map.x + map.width, map.y, outer.x + outer.width - map.x - map.width, map.height);
		g.setColor(EDGE);
		g.drawRect(outer.x, outer.y, outer.width - 1, outer.height - 1);
		g.drawRect(map.x - 1, map.y - 1, map.width + 1, map.height + 1);
		g.setColor(GRIP);
		for (int bar : new int[]{outer.y + BAR / 2, outer.y + outer.height - BAR / 2})
		{
			int middle = outer.x + outer.width / 2;
			for (int i = -3; i <= 3; i++)
			{
				g.fillOval(middle + i * 7 - 2, bar - 2, 4, 4);
			}
		}
		Stroke stroke = g.getStroke();
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int in = 3;
		int arm = CORNER - 6;
		int x0 = outer.x + in;
		int y0 = outer.y + in;
		int x1 = outer.x + outer.width - 1 - in;
		int y1 = outer.y + outer.height - 1 - in;
		g.drawPolyline(new int[]{x0, x0, x0 + arm}, new int[]{y0 + arm, y0, y0}, 3);
		g.drawPolyline(new int[]{x1 - arm, x1, x1}, new int[]{y0, y0, y0 + arm}, 3);
		g.drawPolyline(new int[]{x0, x0, x0 + arm}, new int[]{y1 - arm, y1, y1}, 3);
		g.drawPolyline(new int[]{x1 - arm, x1, x1}, new int[]{y1, y1, y1 - arm}, 3);
		g.setStroke(stroke);
		if (antialiasing != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
		}
	}
}
