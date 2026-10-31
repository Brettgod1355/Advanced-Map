/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
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
		forgetOlderVersions(pluginFolder);
		store.folder.createDirectories();
		SafeFiles.cleanUp(store.folder);
		try (Stream<Filepath> files = store.folder.walk(1))
		{
			files.forEach(store::remember);
		}
		store.forgetCityCopy();
		return store;
	}

	/**
	 * Deletes what an older format of the saved map left (a whole map, 100 MB or more), which nothing reads again. A
	 * folder that cannot be deleted (a file in it open in another client, or read-only, on Windows) is left for next
	 * time: it must not keep the current one from opening.
	 */
	private static void forgetOlderVersions(Filepath pluginFolder) throws IOException
	{
		if (!pluginFolder.exists())
		{
			return;
		}
		List<Filepath> older = new ArrayList<>();
		try (Stream<Filepath> files = pluginFolder.walk(1))
		{
			files.filter(file -> file.getFileName().matches("map-v\\d+") && !file.getFileName().equals("map-v" + VERSION))
				.forEach(older::add);
		}
		for (Filepath old : older)
		{
			try
			{
				old.deleteRecursively();
			}
			catch (IOException e)
			{
				log.warn("could not delete the older saved map {}", old, e);
			}
		}
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

	private void remember(Filepath file)
	{
		String[] parts = file.getFileName().replace(".png", "").split("_");
		if (parts.length != 4)
		{
			return;
		}
		try
		{
			int key = RegionImages.key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
			long hash = Long.parseUnsignedLong(parts[2], 16);
			Long known = saved.get(key);
			if (known != null && known != hash)
			{
				// two clients on different game data each saved this floor: one of them is enough here (checked
				// against the game's data when shown, and drawn again if it is the old one), the other would stay
				// on disk for good
				file.deleteIfExists();
				return;
			}
			saved.put(key, hash);
			if (Kind.EMPTY.suffix.equals(parts[3]))
			{
				empty.put(key, true);
			}
		}
		catch (NumberFormatException e)
		{
			// not one of ours
		}
		catch (IOException e)
		{
			log.debug("could not delete {}", file, e);
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
		if (known == null && lookFor(key, hash, Kind.EMPTY))
		{
			return true;
		}
		return known != null && known == hash && empty.containsKey(key);
	}

	/** How many floors of regions are saved. */
	int count()
	{
		return saved.size();
	}

	/**
	 * A floor the index has nothing for may have been saved by another client running alongside this one (two
	 * accounts open, both preparing the map) since the folder was read: a file of that kind for this game data is
	 * looked for and, when it is there, remembered. Render threads.
	 */
	private boolean lookFor(int key, long hash, Kind kind)
	{
		Filepath file = folder.joinSegment(name(RegionImages.plane(key), RegionImages.region(key), hash, kind));
		if (!file.exists())
		{
			return false;
		}
		saved.put(key, hash);
		if (kind == Kind.EMPTY)
		{
			empty.put(key, true);
		}
		return true;
	}

	/** A saved picture drawn from this game data, or null. */
	BufferedImage load(int key, long hash, Kind kind)
	{
		Long known = saved.get(key);
		if (known == null && lookFor(key, hash, kind))
		{
			known = hash;
		}
		if (known == null || known != hash || empty.containsKey(key))
		{
			return null;
		}
		Filepath file = folder.joinSegment(name(RegionImages.plane(key), RegionImages.region(key), hash, kind));
		try (java.io.InputStream in = file.openInputStream())
		{
			// read through memory: handed a plain stream, the reader copies each picture to a temporary file first,
			// ten times slower, and a far view reads thousands of them
			BufferedImage read = ImageIO.read(new MemoryCacheImageInputStream(in));
			if (read == null)
			{
				// not a picture at all (a file zeroed on disk): the reader gives null, not an exception
				forget(key);
				return null;
			}
			if (read.getType() == BufferedImage.TYPE_INT_ARGB)
			{
				return read;
			}
			// the PNG reader gives bytes per channel, which Java2D scales slowly; the renderer's own pictures are
			// packed ints, so the saved ones are made the same (a quarter off every paint of them)
			BufferedImage packed = new BufferedImage(read.getWidth(), read.getHeight(), BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D g = packed.createGraphics();
			g.setComposite(java.awt.AlphaComposite.Src);
			g.drawImage(read, 0, 0, null);
			g.dispose();
			return packed;
		}
		catch (IOException | RuntimeException e)
		{
			// a damaged file (most damage is an IOException from the PNG reader): both of the floor's files go, so it
			// is drawn and saved again, even when only the small one was damaged (the big one would have served it
			// through a double shrink every time, and the damaged file would have stayed)
			log.debug("could not read {}", file, e);
			forget(key);
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
		// through memory, as pictures are read (load): a plain stream would go through a temporary file
		try (MemoryCacheImageOutputStream png = new MemoryCacheImageOutputStream(out))
		{
			if (!ImageIO.write(image, "png", png))
			{
				throw new IOException("no PNG writer");
			}
		}
		SafeFiles.write(file, out.toByteArray());
	}
}
