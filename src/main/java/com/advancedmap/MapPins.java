/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * Pins on the map that are not the game's icons: the player's own markers (saved in Advanced Map's settings) and
 * other plugins' world map markers (a clue scroll's spot, Quest Helper's next step), read from RuneLite's
 * {@code WorldMapPointManager}. Drawn over the icons; a click shows a pin's name, a right click edits or removes the
 * player's own.
 */
final class MapPins
{
	/** How big a marker is drawn, in pixels, at icon size 100%. */
	static final int MARKER_SIZE = 20;
	/** How far in from the edge a pin that keeps to the edge (a clue's arrow) is drawn when its spot is off screen. */
	static final int EDGE = 16;
	private static final int MAX_NAME = 40;
	private static final Color NAME = new Color(0xFFF2C4);
	private static final Color SHADOW = new Color(0, 0, 0, 200);

	/** The player's own marker. */
	@Value
	static class Marker
	{
		WorldPoint point;
		MarkerIcons.Design design;
		int color;
		String name;
	}

	/** Something drawn at a tile: a marker, or another plugin's picture with its name and what it says on hover. */
	@Value
	static class Pin
	{
		WorldPoint point;
		/** Another plugin's picture; null for a marker. */
		BufferedImage image;
		/** The player's marker; null for another plugin's pin. */
		Marker marker;
		String name;
		String detail;
		/** Where a click moves the map (another plugin's "jump on click"), or null. */
		WorldPoint jump;
		/** Kept at the screen's edge when its tile is off screen. */
		boolean snap;
	}

	@Value
	static class Shown
	{
		Pin pin;
		Rectangle bounds;
		boolean atEdge;
	}

	private volatile List<Marker> markers = Collections.emptyList();
	private volatile List<Pin> others = Collections.emptyList();
	private volatile Pin selected;

	List<Marker> markers()
	{
		return markers;
	}

	void setMarkers(List<Marker> markers)
	{
		this.markers = Collections.unmodifiableList(new ArrayList<>(markers));
	}

	void setOthers(List<Pin> others)
	{
		this.others = Collections.unmodifiableList(new ArrayList<>(others));
	}

	List<Pin> others()
	{
		return others;
	}

	/** The pin whose name box is open, or null. */
	Pin selected()
	{
		return selected;
	}

	void select(Pin pin)
	{
		selected = pin;
	}

	/** The markers as pins, then the other plugins'. */
	List<Pin> all()
	{
		List<Pin> out = new ArrayList<>();
		for (Marker marker : markers)
		{
			out.add(new Pin(marker.getPoint(), null, marker, marker.getName(), "Your marker", null, false));
		}
		out.addAll(others);
		return out;
	}

	/** The markers as Advanced Map saves them: one a line, "x y plane design color name". */
	static String save(List<Marker> markers)
	{
		StringBuilder out = new StringBuilder();
		for (Marker marker : markers)
		{
			WorldPoint at = marker.getPoint();
			out.append(at.getX()).append(' ').append(at.getY()).append(' ').append(at.getPlane()).append(' ')
				.append(marker.getDesign().ordinal()).append(' ').append(marker.getColor()).append(' ')
				.append(cleanName(marker.getName())).append('\n');
		}
		return out.toString();
	}

	static List<Marker> parse(String saved)
	{
		List<Marker> out = new ArrayList<>();
		if (saved == null)
		{
			return out;
		}
		for (String line : saved.split("\n"))
		{
			String[] f = line.split(" ", 6);
			if (f.length < 5)
			{
				continue;
			}
			try
			{
				out.add(new Marker(new WorldPoint(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])),
					MarkerIcons.Design.of(Integer.parseInt(f[3])), Integer.parseInt(f[4]), f.length > 5 ? f[5] : ""));
			}
			catch (NumberFormatException e)
			{
				// a damaged line is skipped, the others kept
			}
		}
		return out;
	}

	/** A name on one line, without the line breaks the saved list uses, and not too long. */
	static String cleanName(String name)
	{
		String one = name == null ? "" : name.replaceAll("[\\r\\n\\t]+", " ").trim();
		return one.length() > MAX_NAME ? one.substring(0, MAX_NAME) : one;
	}

	/**
	 * The pins to draw on a view of a floor, with where: those on this map and floor, the ground showing every floor's
	 * while zoomed out as the icons do. A pin that keeps to the edge is drawn at the edge, pointing the way, when off
	 * screen.
	 */
	List<Shown> visible(MapViewport.View view, MapArea area, int plane, int iconScale)
	{
		List<Shown> out = new ArrayList<>();
		boolean allFloors = plane == 0 && view.zoom < MapIcons.OWN_FLOOR_ZOOM;
		for (Pin pin : all())
		{
			WorldPoint at = Prifddinas.toSurface(pin.getPoint());
			if (MapArea.of(at.getY()) != area || at.getPlane() != plane && !allFloors)
			{
				continue;
			}
			int width;
			int height;
			if (pin.getImage() != null)
			{
				width = pin.getImage().getWidth();
				height = pin.getImage().getHeight();
			}
			else
			{
				width = Math.max(10, MARKER_SIZE * iconScale / 100);
				height = width;
			}
			double sx = view.screenX(at.getX() + 0.5);
			double sy = view.screenY(at.getY() + 0.5);
			boolean off = sx < 0 || sy < 0 || sx > view.width || sy > view.height;
			if (off && !pin.isSnap())
			{
				if (sx < -width || sy < -height || sx > view.width + width || sy > view.height + height)
				{
					continue;
				}
			}
			if (off && pin.isSnap())
			{
				sx = Math.max(EDGE, Math.min(view.width - EDGE, sx));
				sy = Math.max(EDGE, Math.min(view.height - EDGE, sy));
			}
			// a marker's tip (a pin's point, a flag's pole) stands on the tile; another plugin's picture is centered
			int x = (int) Math.round(sx - width / 2.0);
			int y = (int) Math.round(pin.getMarker() != null ? sy - height : sy - height / 2.0);
			out.add(new Shown(pin, new Rectangle(x, y, width, height), off));
		}
		return out;
	}

	/** The pin under a point (the one drawn last on top), or null. */
	static Pin at(List<Shown> shown, int x, int y)
	{
		for (int i = shown.size() - 1; i >= 0; i--)
		{
			if (shown.get(i).getBounds().contains(x, y))
			{
				return shown.get(i).getPin();
			}
		}
		return null;
	}

	/** Draws the pins, a marker's name under it when zoomed in to place names. */
	static void draw(Graphics2D g, List<Shown> shown, double zoom)
	{
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		for (Shown each : shown)
		{
			Rectangle at = each.getBounds();
			Pin pin = each.getPin();
			Marker marker = pin.getMarker();
			if (marker == null)
			{
				g.drawImage(pin.getImage(), at.x, at.y, null);
				continue;
			}
			MarkerIcons.draw(g, marker.getDesign(), MarkerIcons.color(marker.getColor()), at.x, at.y, at.width);
			if (zoom >= MapLabels.PLACE_ZOOM && !marker.getName().isEmpty())
			{
				int x = at.x + at.width / 2 - metrics.stringWidth(marker.getName()) / 2;
				int y = at.y + at.height + metrics.getAscent() + 1;
				g.setColor(SHADOW);
				g.drawString(marker.getName(), x + 1, y + 1);
				g.setColor(NAME);
				g.drawString(marker.getName(), x, y);
			}
		}
	}
}
