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

	/** The player's own marker: its own file in the shared folder ({@link MarkerStore}), named by its id. */
	@Value
	static class Marker
	{
		String id;
		WorldPoint point;
		MarkerIcons.Design design;
		int color;
		String name;
		/** The account (RuneLite's profile key for it) the marker was made on; empty when not known. */
		String account;
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
	private volatile boolean shared = true;
	private volatile String account;

	/** Every marker saved, on any account. */
	List<Marker> markers()
	{
		return markers;
	}

	/**
	 * Which markers show: all when markers are shared between accounts; else those made on {@code account} (and those
	 * of no known account). All while no account is known (logged out).
	 */
	void setFilter(boolean shared, String account)
	{
		this.shared = shared;
		this.account = account;
	}

	/** The markers that show now ({@link #setFilter}). */
	List<Marker> shown()
	{
		String now = account;
		if (shared || now == null)
		{
			return markers;
		}
		List<Marker> out = new ArrayList<>();
		for (Marker marker : markers)
		{
			if (marker.getAccount().isEmpty() || marker.getAccount().equals(now))
			{
				out.add(marker);
			}
		}
		return out;
	}

	synchronized void setMarkers(List<Marker> markers)
	{
		this.markers = Collections.unmodifiableList(new ArrayList<>(markers));
	}

	/**
	 * Replaces a marker ({@code old} null to add one) with another ({@code fresh} null to remove it), found by id: the
	 * list may have been read again since {@code old} was, with another client's change to it. Doing it twice is
	 * doing it once, so it can be done again after a reading that missed it.
	 */
	synchronized void replace(Marker old, Marker fresh)
	{
		String id = old != null ? old.getId() : fresh != null ? fresh.getId() : null;
		if (id == null)
		{
			return;
		}
		List<Marker> out = new ArrayList<>(markers);
		int at = -1;
		for (int i = 0; i < out.size() && at < 0; i++)
		{
			if (out.get(i).getId().equals(id))
			{
				at = i;
			}
		}
		if (at >= 0)
		{
			out.remove(at);
		}
		if (fresh != null)
		{
			out.add(at >= 0 ? at : out.size(), fresh);
		}
		markers = Collections.unmodifiableList(out);
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
		for (Marker marker : shown())
		{
			out.add(new Pin(marker.getPoint(), null, marker, marker.getName(), "Your marker", null, false));
		}
		out.addAll(others);
		return out;
	}

	/** A marker as its file holds it: one line, "x y plane design color account name" ("-" for no account). */
	static String line(Marker marker)
	{
		WorldPoint at = marker.getPoint();
		return at.getX() + " " + at.getY() + " " + at.getPlane() + " " + marker.getDesign().ordinal() + " "
			+ marker.getColor() + " " + (marker.getAccount().isEmpty() ? "-" : marker.getAccount()) + " "
			+ cleanName(marker.getName()) + "\n";
	}

	/** The marker a file holds, or null when the file is damaged. */
	static Marker fromLine(String id, String line)
	{
		String[] f = line.trim().split(" ", 7);
		if (f.length < 6)
		{
			return null;
		}
		try
		{
			return new Marker(id, new WorldPoint(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])),
				MarkerIcons.Design.of(Integer.parseInt(f[3])), Integer.parseInt(f[4]), f.length > 6 ? f[6] : "",
				"-".equals(f[5]) ? "" : f[5]);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * The markers of an earlier test version, saved in one setting as "x y plane design color name" lines; each gets a
	 * new id and no account. Read once, to move them to their files.
	 */
	static List<Marker> parseOld(String saved)
	{
		List<Marker> out = new ArrayList<>();
		if (saved == null)
		{
			return out;
		}
		String[] lines = saved.split("\n");
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i];
			String[] f = line.split(" ", 6);
			if (f.length < 5)
			{
				continue;
			}
			try
			{
				out.add(new Marker(MarkerStore.movedId(i, line), new WorldPoint(Integer.parseInt(f[0]), Integer.parseInt(f[1]),
					Integer.parseInt(f[2])), MarkerIcons.Design.of(Integer.parseInt(f[3])), Integer.parseInt(f[4]),
					f.length > 5 ? f[5] : "", ""));
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
		boolean iconsHidden = view.zoom < MapIcons.ICON_ZOOM;
		for (Pin pin : all())
		{
			// another plugin's pins of places (the Sailing plugin's hundreds of charting spots) go with the game's icons
			// when zoomed far out; one it keeps to the edge or jumps to (a clue's dig spot) is where the player is
			// headed, and stays, as do the player's own markers
			if (iconsHidden && pin.getMarker() == null && !pin.isSnap() && pin.getJump() == null)
			{
				continue;
			}
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
