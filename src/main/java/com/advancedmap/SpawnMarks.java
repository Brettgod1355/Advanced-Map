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
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import javax.imageio.ImageIO;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Monsters' and ground items' spawns on the map when zoomed in: one icon for each area a monster lives in (its
 * spawns within {@link SearchSpots#NEAR} tiles of each other), and each ground item's own picture on its tile. A
 * monster's icon is {@code monsters/<name>.png} among the plugin's resources when there is one, else a plain marker.
 */
@Slf4j
final class SpawnMarks
{
	/** Shown from this zoom in (pixels a tile): near the closest, where a town fills the map. */
	static final double ZOOM = 4;
	/**
	 * A monster's icon and an item's picture, in pixels, at Monster size and Ground item size 100%: the game's own map
	 * icons' size (owner, 2026-11-09: 24 looked "quite a bit larger than the default icons").
	 */
	private static final int MONSTER = 16;
	private static final int ITEM = 16;
	private static final Color MARKER = new Color(0xC8372D);

	/** A monster's area (its icon, the spawns it stands for) or a ground item's tile. */
	@Value
	static class Mark
	{
		String name;
		/** Combat levels for a monster, "2, 5"; empty for an item. */
		String levels;
		/** The item's id, -1 when the game has none of that name; {@link Integer#MIN_VALUE} for a monster. */
		int itemId;
		WorldPoint point;
		List<WorldPoint> tiles;

		/** Its key among the hidden icons (owner, 2026-11-09: right-click to hide one), made on first use. */
		@lombok.Getter(lazy = true)
		private final String key = "s" + name + "@" + point.getX() + "," + point.getY();

		boolean isMonster()
		{
			return itemId == Integer.MIN_VALUE;
		}
	}

	@Value
	static class Shown
	{
		Mark mark;
		Rectangle bounds;
		/** Hidden with a right-click and shown faded while "Show hidden icons" is ticked. */
		boolean hidden;
	}

	/** The marks by the floor and region of their point: {@code plane << 16 | region id}. */
	private static volatile Map<Integer, List<Mark>> index = Collections.emptyMap();
	/** Monsters' pictures by name, empty when the plugin has none; painter thread. */
	private static final Map<String, Optional<BufferedImage>> pictures = new ConcurrentHashMap<>();

	private SpawnMarks()
	{
	}

	/** Builds the marks from what {@link Spawns} read and {@code item-ids.tsv}. Off the client thread. */
	static void load()
	{
		Map<String, Integer> ids = new HashMap<>();
		for (String[] row : DataFiles.rows("item-ids.tsv"))
		{
			if (row.length > 1)
			{
				ids.put(row[0], Integer.parseInt(row[1].trim()));
			}
		}
		index = build(Spawns.all(), ids);
	}

	static Map<Integer, List<Mark>> build(List<Spawns.Creature> creatures, Map<String, Integer> itemIds)
	{
		Map<Integer, List<Mark>> out = new HashMap<>();
		for (Spawns.Creature creature : creatures)
		{
			if (creature.getName().endsWith("(Deadman Mode)"))
			{
				// in that game mode only, not on the worlds the map is for
				continue;
			}
			if (creature.isMonster())
			{
				for (List<WorldPoint> area : SearchSpots.groups(creature.getTiles(), creature.getFloors()))
				{
					add(out, new Mark(creature.getName(), creature.getLevels(), Integer.MIN_VALUE,
						SearchSpots.middle(area), Collections.unmodifiableList(area)));
				}
			}
			else if (creature.isItem())
			{
				int id = itemIds.getOrDefault(creature.getName(), -1);
				for (WorldPoint tile : creature.getTiles())
				{
					add(out, new Mark(creature.getName(), "", id, tile, Collections.singletonList(tile)));
				}
			}
		}
		return out;
	}

	private static void add(Map<Integer, List<Mark>> index, Mark mark)
	{
		WorldPoint at = mark.getPoint();
		index.computeIfAbsent(at.getPlane() << 16 | at.getRegionID(), k -> new ArrayList<>()).add(mark);
	}

	/** The marks on the screen, on this map and floor, items under monsters; none zoomed out or both turned off. */
	static List<Shown> visible(MapViewport.View view, MapArea area, int plane, MapSettings settings)
	{
		if (view.zoom < ZOOM || settings.isIconsOff() || !settings.isMonsterSpawns() && !settings.isItemSpawns())
		{
			return Collections.emptyList();
		}
		List<Shown> items = new ArrayList<>();
		List<Shown> monsters = new ArrayList<>();
		int west = (int) Math.floor(view.worldX(0)) >> 6;
		int east = (int) Math.floor(view.worldX(view.width)) >> 6;
		int south = (int) Math.floor(view.worldY(view.height)) >> 6;
		int north = (int) Math.floor(view.worldY(0)) >> 6;
		Map<Integer, List<Mark>> marks = index;
		for (int rx = west; rx <= east; rx++)
		{
			for (int ry = south; ry <= north; ry++)
			{
				for (Mark mark : marks.getOrDefault(plane << 16 | rx << 8 | ry, Collections.emptyList()))
				{
					boolean hidden = !settings.getHidden().isEmpty() && settings.getHidden().contains(mark.getKey());
					if (MapArea.of(mark.getPoint().getY()) != area
						|| !(mark.isMonster() ? settings.isMonsterSpawns() : settings.isItemSpawns())
						|| hidden && !settings.isShowHidden())
					{
						continue;
					}
					int size = mark.isMonster() ? MONSTER * settings.getMonsterScale() / 100
						: ITEM * settings.getItemScale() / 100;
					int x = (int) Math.round(view.screenX(mark.getPoint().getX() + 0.5)) - size / 2;
					int y = (int) Math.round(view.screenY(mark.getPoint().getY() + 0.5)) - size / 2;
					if (x + size >= 0 && y + size >= 0 && x <= view.width && y <= view.height)
					{
						(mark.isMonster() ? monsters : items).add(new Shown(mark, new Rectangle(x, y, size, size), hidden));
					}
				}
			}
		}
		items.addAll(monsters);
		return items;
	}

	/** The mark drawn on top at a point, or null. */
	static Shown at(List<Shown> shown, int x, int y)
	{
		for (int i = shown.size() - 1; i >= 0; i--)
		{
			if (shown.get(i).getBounds().contains(x, y))
			{
				return shown.get(i);
			}
		}
		return null;
	}

	/** Draws the marks: items' pictures (a dot while one loads or the game has none), monsters' icons. */
	static void draw(Graphics2D g, List<Shown> shown, IntFunction<BufferedImage> itemPictures)
	{
		Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		java.awt.Stroke stroke = g.getStroke();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		java.awt.Composite composite = g.getComposite();
		for (Shown each : shown)
		{
			Mark mark = each.getMark();
			Rectangle at = each.getBounds();
			g.setComposite(each.isHidden() ? java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.35f)
				: composite);
			BufferedImage image = mark.isMonster() ? picture(mark.getName())
				: mark.getItemId() >= 0 ? itemPictures.apply(mark.getItemId()) : null;
			if (image != null)
			{
				// kept in shape, as big as fits the box
				double scale = Math.min((double) at.width / image.getWidth(), (double) at.height / image.getHeight());
				int w = (int) Math.round(image.getWidth() * scale);
				int h = (int) Math.round(image.getHeight() * scale);
				g.drawImage(image, at.x + (at.width - w) / 2, at.y + (at.height - h) / 2, w, h, null);
			}
			else
			{
				int inset = mark.isMonster() ? 2 : at.width / 3;
				g.setColor(mark.isMonster() ? MARKER : new Color(0xE8D27A));
				g.fillOval(at.x + inset, at.y + inset, at.width - inset * 2, at.height - inset * 2);
				g.setStroke(new BasicStroke(mark.isMonster() ? 2 : 1));
				g.setColor(mark.isMonster() ? Color.WHITE : Color.BLACK);
				g.drawOval(at.x + inset, at.y + inset, at.width - inset * 2, at.height - inset * 2);
			}
		}
		g.setComposite(composite);
		g.setStroke(stroke);
		if (interpolation != null)
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		}
	}

	/** Every mark as a spot, among which the Hidden icons list finds the hidden ones. */
	static List<MapSpot> spots()
	{
		List<MapSpot> out = new ArrayList<>();
		index.values().forEach(marks -> marks.forEach(mark -> out.add(MapSpot.spawn(mark))));
		return out;
	}

	/** A monster's own picture from the plugin's resources, read once; null when it has none. */
	static BufferedImage picture(String name)
	{
		return pictures.computeIfAbsent(name, n ->
		{
			try (InputStream in = SpawnMarks.class.getResourceAsStream("monsters/" + fileName(n) + ".png"))
			{
				return Optional.ofNullable(in == null ? null : ImageIO.read(in));
			}
			catch (Exception e)
			{
				log.debug("could not read the picture of {}", n, e);
				return Optional.empty();
			}
		}).orElse(null);
	}

	/** A monster's name as its picture's file is named: characters a file name can't have become "_". */
	static String fileName(String name)
	{
		return name.replaceAll("[\\\\/:*?\"<>|]", "_");
	}

	/** An item's picture cut to the item, without the empty room round it, so it fills its box on the map. */
	static BufferedImage trim(BufferedImage item)
	{
		int x0 = item.getWidth();
		int y0 = item.getHeight();
		int x1 = -1;
		int y1 = -1;
		for (int y = 0; y < item.getHeight(); y++)
		{
			for (int x = 0; x < item.getWidth(); x++)
			{
				if (item.getRGB(x, y) >>> 24 != 0)
				{
					x0 = Math.min(x0, x);
					y0 = Math.min(y0, y);
					x1 = Math.max(x1, x);
					y1 = Math.max(y1, y);
				}
			}
		}
		return x1 < 0 ? item : item.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1);
	}

	/** What a mark's name tag says: a monster with its levels. */
	static String label(Mark mark)
	{
		return mark.isMonster() && !mark.getLevels().isEmpty() ? mark.getName() + " (level " + mark.getLevels() + ")"
			: mark.getName();
	}
}
