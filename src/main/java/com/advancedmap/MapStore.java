/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * The drawn map regions saved on disk, in the plugin's data folder ({@code .runelite/plugin-data/advanced-map}), which
 * every character and RuneLite profile on the computer shares. Each region is kept at full size and at the smallest
 * size, one picture per floor, named with a hash of the game data it was drawn from: when a game update changes the
 * region, the hash no longer matches and the region is drawn again. A floor the region has nothing on is kept as an
 * empty marker. Render threads only; never the client thread.
 */
@Slf4j
final class MapStore
{
	/** Raised when the drawing changes, so pictures drawn the old way are drawn again. */
	static final int VERSION = 1;

	enum Kind
	{
		FULL("f"),
		SMALL("s"),
		EMPTY("e");

		final String suffix;

		Kind(String suffix)
		{
			this.suffix = suffix;
		}
	}

	private final Filepath folder;
	/** The hash each saved region (floor and region id) was drawn from. */
	private final Map<Integer, Long> saved = new ConcurrentHashMap<>();
	private final Map<Integer, Boolean> empty = new ConcurrentHashMap<>();

	private MapStore(Filepath folder)
	{
		this.folder = folder;
	}

	/** Opens the store in a plugin's data folder and reads what is saved there. */
	static MapStore open(Filepath pluginFolder) throws IOException
	{
		MapStore store = new MapStore(pluginFolder.joinSegment("map-v" + VERSION));
		store.folder.createDirectories();
		SafeFiles.cleanUp(store.folder);
		try (Stream<Filepath> files = store.folder.walk(1))
		{
			files.forEach(file -> store.remember(file.getFileName()));
		}
		store.forgetCityCopy();
		return store;
	}

	/**
	 * Deletes what an older version saved of Prifddinas's copy (the city is drawn into Tirannwn now and its copy left
	 * off the map), which would otherwise show for a moment on the underground map every session.
	 */
	private void forgetCityCopy()
	{
		for (int key : new ArrayList<>(saved.keySet()))
		{
			int region = RegionImages.region(key);
			if (Prifddinas.isCopyRegion(region >> 8, region & 0xFF))
			{
				forget(key);
			}
		}
	}

	/** A saved file's name: floor, region id, hash and kind, for example {@code 0_12850_3f2a...e1_f.png}. */
	static String name(int plane, int regionId, long hash, Kind kind)
	{
		String name = plane + "_" + regionId + "_" + Long.toHexString(hash) + "_" + kind.suffix;
		return kind == Kind.EMPTY ? name : name + ".png";
	}

	private void remember(String fileName)
	{
		String[] parts = fileName.replace(".png", "").split("_");
		if (parts.length != 4)
		{
			return;
		}
		try
		{
			int key = RegionImages.key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
			saved.put(key, Long.parseUnsignedLong(parts[2], 16));
			if (Kind.EMPTY.suffix.equals(parts[3]))
			{
				empty.put(key, true);
			}
		}
		catch (NumberFormatException e)
		{
			// not one of ours
		}
	}

	/** The fingerprint of the game data a floor of a region was saved from, or null when it is not saved. */
	Long savedHash(int key)
	{
		return saved.get(key);
	}

	/** True when a floor of a region is saved, whatever game data it was drawn from. */
	boolean has(int key)
	{
		return saved.containsKey(key);
	}

	/** True when a floor of a region was saved as having nothing on it, from this game data. */
	boolean isEmpty(int key, long hash)
	{
		Long known = saved.get(key);
		return known != null && known == hash && empty.containsKey(key);
	}

	/** How many floors of regions are saved. */
	int count()
	{
		return saved.size();
	}

	/** A saved picture drawn from this game data, or null. */
	BufferedImage load(int key, long hash, Kind kind)
	{
		Long known = saved.get(key);
		if (known == null || known != hash || empty.containsKey(key))
		{
			return null;
		}
		Filepath file = folder.joinSegment(name(RegionImages.plane(key), RegionImages.region(key), hash, kind));
		try (java.io.InputStream in = file.openInputStream())
		{
			return ImageIO.read(in);
		}
		catch (IOException e)
		{
			log.debug("could not read {}", file, e);
			return null;
		}
	}

	/** Saves a region's floor at both sizes, replacing what was saved from older game data. */
	void save(int key, long hash, BufferedImage full, BufferedImage small)
	{
		forget(key);
		int plane = RegionImages.plane(key);
		int region = RegionImages.region(key);
		try
		{
			write(folder.joinSegment(name(plane, region, hash, Kind.FULL)), full);
			write(folder.joinSegment(name(plane, region, hash, Kind.SMALL)), small);
			saved.put(key, hash);
		}
		catch (IOException e)
		{
			log.debug("could not save region {} floor {}", region, plane, e);
		}
	}

	/** Saves that a region has nothing on a floor. */
	void saveEmpty(int key, long hash)
	{
		forget(key);
		try
		{
			folder.joinSegment(name(RegionImages.plane(key), RegionImages.region(key), hash, Kind.EMPTY))
				.write(new byte[0]);
			saved.put(key, hash);
			empty.put(key, true);
		}
		catch (IOException e)
		{
			log.debug("could not save region {}", RegionImages.region(key), e);
		}
	}

	/** Deletes everything saved, so every region is drawn again. */
	void clear()
	{
		for (int key : saved.keySet())
		{
			forget(key);
		}
	}

	private void forget(int key)
	{
		Long old = saved.remove(key);
		boolean wasEmpty = empty.remove(key) != null;
		if (old == null)
		{
			return;
		}
		int plane = RegionImages.plane(key);
		int region = RegionImages.region(key);
		try
		{
			for (Kind kind : wasEmpty ? new Kind[]{Kind.EMPTY} : new Kind[]{Kind.FULL, Kind.SMALL})
			{
				folder.joinSegment(name(plane, region, old, kind)).deleteIfExists();
			}
		}
		catch (IOException e)
		{
			log.debug("could not delete region {} floor {}", region, plane, e);
		}
	}

	/** Writes a picture whole or not at all ({@link SafeFiles}): another client never reads half a picture. */
	private static void write(Filepath file, BufferedImage image) throws IOException
	{
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", out))
		{
			throw new IOException("no PNG writer");
		}
		SafeFiles.write(file, out.toByteArray());
	}
}
