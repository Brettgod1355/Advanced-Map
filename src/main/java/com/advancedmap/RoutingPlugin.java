/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The plugin a right-click "Route here" asks for a route, through RuneLite's {@code PluginMessage}: both take a
 * {@code path} message with a {@code target} world point (and start from the player), and a {@code clear} message.
 * Read at Shortest Path's Hub pin {@code 50f3043} and GPS's {@code 5fa29ba}. GPS also answers on Shortest Path's
 * namespace, so with both installed, asking Shortest Path asks GPS too.
 */
@Getter
@RequiredArgsConstructor
public enum RoutingPlugin
{
	NONE("None", null, null),
	SHORTEST_PATH("Shortest Path", "shortestpath", "Shortest Path"),
	GPS("GPS", "gps", "GPS");

	private final String label;
	private final String namespace;
	/** The name on the plugin's descriptor, as RuneLite's plugin list shows it (GPS read at its Hub pin {@code 5fa29ba}). */
	private final String pluginName;

	@Override
	public String toString()
	{
		return label;
	}
}
