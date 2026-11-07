/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * What a version brings, shown on the map menu's What's new page, which opens by itself once after the Plugin Hub
 * updates the plugin. The Hub swaps the jar and restarts the plugin, and the plugin also restarts with the client, so
 * the only way to tell an update from a restart is to remember the last version seen. Bump {@link #VERSION} and
 * rewrite {@link #NOTE} with every release; a test keeps the version in step with the build.
 */
final class WhatsNew
{
	/** The version being run. Must match {@code version} in build.gradle and runelite-plugin.properties. */
	static final String VERSION = "1.1";

	/**
	 * One line on what this version brings, no links; a full stop is added on the page. Empty for a release not worth
	 * a line (a fix, a wording change): then the page does not open by itself. The first release says only that it is
	 * one (a fresh install never opens the page by itself, there being no earlier version to update from).
	 */
	static final String NOTE = "the first release of Advanced Map";

	/** What a start of the plugin does about the version last run: see {@link #start}. */
	@lombok.Value
	static class Start
	{
		/** The plugin was updated: a version was run before, and it was another. */
		boolean updated;
		/** What's new is to open with the map: an update whose note has something to tell. */
		boolean news;
	}

	/**
	 * What a start does about {@code lastVersion}, the version run before this one ("" or null: none): an update is
	 * one from another version, and brings news when the note is not empty. A fresh install and the same version
	 * again bring nothing.
	 */
	static Start start(String lastVersion)
	{
		boolean updated = lastVersion != null && !lastVersion.isEmpty() && !VERSION.equals(lastVersion);
		return new Start(updated, updated && !NOTE.isEmpty());
	}

	private WhatsNew()
	{
	}
}
