/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * What the plugin remembers about this computer, in a small file beside the saved map rather than in a RuneLite
 * profile: the version last run, whether the Welcome page has opened once, whether the whole map has been seen
 * built, and which version's update line and What's new page are still to show. RuneLite switches to a profile
 * bound to an account at login, so a value written into one profile at startUp would be read from another later;
 * the saved map these things are about is per computer anyway. Without a folder (a test), kept in memory.
 */
@Slf4j
final class StateFile
{
	static final String FILE = "state.properties";
	static final String LAST_VERSION = "lastVersion";
	static final String WELCOMED = "welcomed";
	static final String MAP_BUILT = "mapBuilt";
	static final String WHATS_NEW_FOR = "whatsNewFor";

	private final Filepath file;
	private final Properties values = new Properties();

	StateFile(Filepath folder)
	{
		file = folder == null ? null : folder.joinSegment(FILE);
		if (file != null && file.exists())
		{
			try (BufferedReader reader = file.openBufferedReader())
			{
				values.load(reader);
			}
			catch (IOException | IllegalArgumentException e)
			{
				log.debug("could not read {}", file, e);
			}
		}
	}

	synchronized String get(String key)
	{
		return values.getProperty(key, "");
	}

	synchronized boolean is(String key)
	{
		return "true".equals(values.getProperty(key));
	}

	synchronized void set(String key, String value)
	{
		values.setProperty(key, value);
		save();
	}

	void set(String key, boolean value)
	{
		set(key, Boolean.toString(value));
	}

	synchronized void unset(String key)
	{
		values.remove(key);
		save();
	}

	/** One line per value, in order: the same values make the same file. */
	private void save()
	{
		if (file == null)
		{
			return;
		}
		StringBuilder out = new StringBuilder();
		for (java.util.Map.Entry<Object, Object> entry : new TreeMap<>(values).entrySet())
		{
			out.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
		}
		try
		{
			SafeFiles.write(file, out.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.debug("could not write {}", file, e);
		}
	}
}
