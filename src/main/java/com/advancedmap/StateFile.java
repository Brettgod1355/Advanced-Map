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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * What the plugin remembers about this computer, in a small file beside the saved map rather than in a RuneLite
 * profile: the version last run, whether the Welcome page has opened once, whether the whole map has been seen
 * built, and which version's What's new page is still to show. RuneLite switches to a profile
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
	/** Where the file is written: off the client thread, which sets values on a game tick and as the map opens. */
	private final Executor writes;

	StateFile(Filepath folder)
	{
		this(folder, Runnable::run);
	}

	/** Reads the file now; each change is written by {@code writes}, in order when it runs one task at a time. */
	StateFile(Filepath folder, Executor writes)
	{
		this.writes = writes;
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

	/**
	 * One line per value, in order: the same values make the same file. The text is made now, under the lock, and
	 * written by the executor; once that has stopped (the plugin stopping), here.
	 */
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
		byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
		Runnable write = () ->
		{
			try
			{
				SafeFiles.write(file, bytes);
			}
			catch (IOException e)
			{
				log.debug("could not write {}", file, e);
			}
		};
		try
		{
			writes.execute(write);
		}
		catch (RejectedExecutionException e)
		{
			write.run();
		}
	}
}
