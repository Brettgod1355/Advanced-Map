/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * Teleport destinations, from {@code teleports.tsv}: transcribed from RuneLite's world map plugin
 * ({@code TeleportLocationData} at runelite-parent-1.13.4) without its icon file names. The game's own map has no
 * teleport icons, so these are drawn with Advanced Map's own marks. A spell has its Magic level; everything else names
 * the item or thing that teleports there. Each has a key (its old enum name) by which a hidden icon is saved.
 */
@Getter
@Slf4j
final class Teleport
{
	enum Type
	{
		NORMAL_MAGIC("Standard spellbook"),
		ANCIENT_MAGICKS("Ancient Magicks"),
		LUNAR_MAGIC("Lunar spellbook"),
		ARCEUUS_MAGIC("Arceuus spellbook"),
		JEWELLERY("Jewellery"),
		SCROLL("Teleport scroll"),
		OTHER("Item or object");

		final String label;

		Type(String label)
		{
			this.label = label;
		}

		boolean isSpell()
		{
			return ordinal() <= ARCEUUS_MAGIC.ordinal();
		}
	}

	private static final List<Teleport> ALL = load();

	private final String name;
	private final Type type;
	/** The item or object that teleports here, or null for a spell. */
	private final String item;
	private final String destination;
	/** The Magic level a spell needs, or 0. */
	private final int magicLevel;
	private final WorldPoint location;
	/** Its picture's file in {@code teleports/}, as RuneLite's world map shows it; null when it has none. */
	private final String icon;

	/** The pictures read so far, by file; empty when one could not be read. Shared by the teleports that use it. */
	private static final Map<String, Optional<BufferedImage>> PICTURES = new ConcurrentHashMap<>();

	private Teleport(String name, Type type, String item, String destination, int magicLevel, WorldPoint location,
		String icon)
	{
		this.icon = icon;
		this.name = name;
		this.type = type;
		this.item = item;
		this.destination = destination;
		this.magicLevel = magicLevel;
		this.location = location;
	}

	/** Every teleport, in the file's order. */
	static List<Teleport> values()
	{
		return ALL;
	}

	/** The teleport with that key, or null. */
	static Teleport named(String name)
	{
		for (Teleport teleport : ALL)
		{
			if (teleport.name.equals(name))
			{
				return teleport;
			}
		}
		return null;
	}

	private static List<Teleport> load()
	{
		try (InputStream in = Teleport.class.getResourceAsStream("teleports.tsv"))
		{
			if (in != null)
			{
				return parse(in);
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the teleports", e);
		}
		return Collections.emptyList();
	}

	static List<Teleport> parse(InputStream in) throws IOException
	{
		List<Teleport> out = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] f = line.split("\t", -1);
			out.add(new Teleport(f[0], Type.valueOf(f[1]), f[2].isEmpty() ? null : f[2], f[3].isEmpty() ? null : f[3],
				Integer.parseInt(f[4]), new WorldPoint(Integer.parseInt(f[5]), Integer.parseInt(f[6]), Integer.parseInt(f[7])),
				f.length > 8 && !f[8].isEmpty() ? f[8] : null));
		}
		return Collections.unmodifiableList(out);
	}

	/**
	 * Its picture as RuneLite's world map draws it: the spell's own icon from the spellbook, or the item on a disc.
	 * Null when it has none or it could not be read (Advanced Map's own mark is drawn instead).
	 */
	BufferedImage picture()
	{
		return icon == null ? null : PICTURES.computeIfAbsent(icon, Teleport::readPicture).orElse(null);
	}

	private static Optional<BufferedImage> readPicture(String file)
	{
		try (InputStream in = Teleport.class.getResourceAsStream("teleports/" + file))
		{
			BufferedImage read = in == null ? null : ImageIO.read(new MemoryCacheImageInputStream(in));
			if (read == null)
			{
				return Optional.empty();
			}
			// packed ints, as the map's other pictures, which Java2D draws fastest
			BufferedImage packed = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = packed.createGraphics();
			g.drawImage(read, 0, 0, null);
			g.dispose();
			return Optional.of(packed);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read the teleport picture {}", file, e);
			return Optional.empty();
		}
	}

	/** What the info box calls it: "Varrock Teleport", "Games Necklace: Barbarian Assault". */
	String title()
	{
		if (item == null)
		{
			return destination + " Teleport";
		}
		return destination == null ? item : item + ": " + destination;
	}
}
