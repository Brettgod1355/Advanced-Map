/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarkerStoreTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private static MapPins.Marker marker(String id, String name)
	{
		return new MapPins.Marker(id, new WorldPoint(3222, 3218, 0), MarkerIcons.Design.STAR, 2, name, "");
	}

	@Test
	public void twoClientsAddingMarkersKeepBoth() throws IOException
	{
		Filepath folder = Filepath.Unchecked.getRooted(temp.getRoot().toPath());
		// two clients (two accounts) open the same folder, each adds a marker without knowing of the other's
		MarkerStore first = MarkerStore.open(folder);
		MarkerStore second = MarkerStore.open(folder);
		MapPins.Marker a = marker(MarkerStore.newId(), "From the main");
		MapPins.Marker b = marker(MarkerStore.newId(), "From the alt");
		first.save(a);
		second.save(b);
		assertEquals(2, first.read().size());
		// one is changed and the other removed: the change and the removal both hold
		MapPins.Marker renamed = marker(a.getId(), "Renamed");
		second.save(renamed);
		first.remove(b);
		assertEquals(Collections.singletonList(renamed), second.read());
		// no temporary file is left behind
		try (Stream<Path> files = Files.list(temp.getRoot().toPath().resolve("markers")))
		{
			assertTrue(files.noneMatch(file -> file.toString().endsWith(SafeFiles.TEMP_SUFFIX)));
		}
	}

	@Test
	public void aDamagedFileIsSkipped() throws IOException
	{
		Filepath folder = Filepath.Unchecked.getRooted(temp.getRoot().toPath());
		MarkerStore store = MarkerStore.open(folder);
		MapPins.Marker good = marker("1-1", "Good");
		store.save(good);
		Files.write(temp.getRoot().toPath().resolve("markers").resolve("2-2" + MarkerStore.SUFFIX), "garbage".getBytes());
		assertEquals(Arrays.asList(good), store.read());
	}
}
