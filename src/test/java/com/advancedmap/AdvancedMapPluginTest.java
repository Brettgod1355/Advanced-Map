/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Starts a development RuneLite client with this plugin loaded, and beside it the map check and the map watch
 * ({@link MapCheckPlugin}, {@link MapWatchPlugin}), which are only in the test folder and so never in the plugin the
 * Plugin Hub ships.
 * Run with {@code ./gradlew run} or from IntelliJ.
 */
public final class AdvancedMapPluginTest
{
	@SuppressWarnings("unchecked") // RuneLite's loadBuiltin uses generic varargs.
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(AdvancedMapPlugin.class, MapCheckPlugin.class, MapWatchPlugin.class);
		RuneLite.main(args);
	}
}
