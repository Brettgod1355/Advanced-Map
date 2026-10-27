/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

/**
 * Files written whole or not at all: written to a temporary file beside the target, then renamed to it. Another client
 * open at the same time (a second account) never reads half a file: it finds the old one, the new one, or (for the
 * moment a file is replaced) none.
 */
final class SafeFiles
{
	static final String TEMP_SUFFIX = ".tmp";
	/** A temporary file older than this was left by a client that stopped mid-write, and is deleted. */
	private static final long STALE_MILLIS = 60 * 60 * 1000;

	private SafeFiles()
	{
	}

	static void write(Filepath target, byte[] data) throws IOException
	{
		Filepath temp = target.getParent().createTempFile("w", TEMP_SUFFIX);
		try
		{
			temp.write(data);
			try
			{
				temp.moveTo(target);
			}
			catch (IOException e)
			{
				if (!target.exists())
				{
					throw e;
				}
				// a file already there (a marker changed, a picture another client saved first): replaced
				target.deleteIfExists();
				temp.moveTo(target);
			}
		}
		finally
		{
			temp.deleteIfExists();
		}
	}

	/** Deletes the temporary files in a folder that a stopped client left behind. */
	static void cleanUp(Filepath folder)
	{
		long before = System.currentTimeMillis() - STALE_MILLIS;
		try (Stream<Filepath> files = folder.walk(1))
		{
			files.filter(file -> file.getFileName().endsWith(TEMP_SUFFIX)).forEach(file ->
			{
				try
				{
					if (file.getLastModifiedTime().toMillis() < before)
					{
						file.deleteIfExists();
					}
				}
				catch (IOException e)
				{
					// another client may be using it; tried again next start
				}
			});
		}
		catch (IOException e)
		{
			// nothing to clean
		}
	}
}
