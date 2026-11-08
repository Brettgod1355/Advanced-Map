/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the plugin's data files ({@code shops.tsv}, {@code dungeon-links.tsv} ...), one shared reader for them all, so
 * a new table costs few Plugin Hub tokens (owner, 2026-11-07).
 */
@Slf4j
final class DataFiles
{
	private DataFiles()
	{
	}

	/** A data file's lines, but blank ones and comments (starting with {@code comment}). */
	static List<String> lines(InputStream in, String comment) throws IOException
	{
		List<String> out = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		String line;
		while ((line = reader.readLine()) != null)
		{
			if (!line.isEmpty() && !line.startsWith(comment))
			{
				out.add(line);
			}
		}
		return out;
	}

	/** A data file's rows: its lines but blank ones and "#" comments, split at tabs, empty fields kept. */
	static List<String[]> rows(InputStream in) throws IOException
	{
		List<String[]> out = new ArrayList<>();
		for (String line : lines(in, "#"))
		{
			out.add(line.split("\t", -1));
		}
		return out;
	}

	/** A resource of the plugin's, as {@link #rows(InputStream)}; none when it can't be read. */
	static List<String[]> rows(String resource)
	{
		try (InputStream in = DataFiles.class.getResourceAsStream(resource))
		{
			return rows(in);
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("could not read {}", resource, e);
			return Collections.emptyList();
		}
	}
}
