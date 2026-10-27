/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * The player's markers on disk: one small file a marker, in the plugin's data folder, which every account and RuneLite
 * profile on the computer shares. Adding a marker makes a file, changing one replaces its file whole
 * ({@link SafeFiles}), removing one deletes it, so two clients open at once (two accounts) never undo each other's
 * markers. Read again each time the map opens, so a marker made on one account shows on the other. Never the client
 * thread.
 */
@Slf4j
final class MarkerStore
{
	static final String SUFFIX = ".marker";

	private final Filepath folder;

	private MarkerStore(Filepath folder)
	{
		this.folder = folder;
	}

	static MarkerStore open(Filepath pluginFolder) throws IOException
	{
		MarkerStore store = new MarkerStore(pluginFolder.joinSegment("markers"));
		store.folder.createDirectories();
		SafeFiles.cleanUp(store.folder);
		return store;
	}

	/** A new marker's id: the time it was made and a random part, so two clients never pick the same one. */
	static String newId()
	{
		return Long.toHexString(System.currentTimeMillis()) + "-" + Integer.toHexString(ThreadLocalRandom.current()
			.nextInt(0x10000000, Integer.MAX_VALUE));
	}

	/** Every marker saved, oldest first. */
	List<MapPins.Marker> read()
	{
		List<MapPins.Marker> out = new ArrayList<>();
		List<Filepath> files;
		try (Stream<Filepath> found = folder.walk(1))
		{
			files = found.filter(file -> file.getFileName().endsWith(SUFFIX)).sorted().collect(Collectors.toList());
		}
		catch (IOException e)
		{
			log.debug("could not list the markers", e);
			return out;
		}
		for (Filepath file : files)
		{
			String name = file.getFileName();
			try (BufferedReader reader = file.openBufferedReader())
			{
				String line = reader.readLine();
				MapPins.Marker marker = line == null ? null
					: MapPins.fromLine(name.substring(0, name.length() - SUFFIX.length()), line);
				if (marker != null)
				{
					out.add(marker);
				}
			}
			catch (IOException e)
			{
				// removed by another client meanwhile
			}
		}
		return out;
	}

	void save(MapPins.Marker marker)
	{
		try
		{
			SafeFiles.write(file(marker), MapPins.line(marker).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("Advanced Map could not save a marker", e);
		}
	}

	void remove(MapPins.Marker marker)
	{
		try
		{
			file(marker).deleteIfExists();
		}
		catch (IOException e)
		{
			log.warn("Advanced Map could not remove a marker", e);
		}
	}

	private Filepath file(MapPins.Marker marker)
	{
		return folder.joinSegment(marker.getId() + SUFFIX);
	}
}
