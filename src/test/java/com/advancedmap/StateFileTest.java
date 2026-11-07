/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StateFileTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void whatIsSetIsThereAgainWhenTheFileIsOpenedAgain() throws Exception
	{
		Filepath root = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
		StateFile state = new StateFile(root);
		assertEquals("", state.get(StateFile.LAST_VERSION));
		assertFalse(state.is(StateFile.WELCOMED));
		state.set(StateFile.LAST_VERSION, "1.0.0");
		state.set(StateFile.WELCOMED, true);
		state.set(StateFile.WHATS_NEW_FOR, "1.1.0");
		state.unset(StateFile.WHATS_NEW_FOR);
		StateFile again = new StateFile(root);
		assertEquals("1.0.0", again.get(StateFile.LAST_VERSION));
		assertTrue(again.is(StateFile.WELCOMED));
		assertEquals("unset is gone", "", again.get(StateFile.WHATS_NEW_FOR));
		// one line per value, in order: the file reads plainly and the same values make the same file
		assertEquals("lastVersion=1.0.0\nwelcomed=true\n", new String(Files.readAllBytes(new File(folder.getRoot(),
			StateFile.FILE).toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void withoutAFolderItIsKeptInMemory()
	{
		StateFile state = new StateFile(null);
		state.set(StateFile.MAP_BUILT, true);
		assertTrue(state.is(StateFile.MAP_BUILT));
	}

	@Test
	public void theFileIsWrittenOffTheCallersThreadAndHereOnceThatHasStopped() throws Exception
	{
		// the client thread sets values on a game tick and as the map opens: the disk is left to another thread (the
		// PR audit, 2026-11-07), in order, and to the caller only once that thread has stopped with the plugin
		Filepath root = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
		java.util.List<Runnable> queued = new java.util.ArrayList<>();
		StateFile state = new StateFile(root, queued::add);
		state.set(StateFile.WELCOMED, true);
		assertTrue("the value is there at once", state.is(StateFile.WELCOMED));
		File file = new File(folder.getRoot(), StateFile.FILE);
		assertFalse("not written by the caller", file.exists());
		queued.forEach(Runnable::run);
		assertEquals("welcomed=true\n", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
		java.util.concurrent.ExecutorService stopped = java.util.concurrent.Executors.newSingleThreadExecutor();
		stopped.shutdown();
		new StateFile(root, stopped).set(StateFile.MAP_BUILT, true);
		assertEquals("mapBuilt=true\nwelcomed=true\n", new String(Files.readAllBytes(file.toPath()),
			StandardCharsets.UTF_8));
	}
}
