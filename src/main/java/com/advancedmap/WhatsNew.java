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
 * rewrite the WHATS_NEW page of {@code menu-pages.txt} with every release; a test keeps the version in step with the
 * build.
 */
final class WhatsNew
{
	/** The version being run. Must match {@code version} in build.gradle and runelite-plugin.properties. */
	static final String VERSION = "1.2";

	/**
	 * What this version brings: the WHATS_NEW page's list in {@code menu-pages.txt}, one short change each, drawn on
	 * its own line after a dash (owner, 2026-11-07: "each change to be its own bullet point ... not jammed up into a
	 * paragraph"). Empty for a release not worth a line (a fix, a wording change): then the page does not open by
	 * itself.
	 */
	static final java.util.List<String> CHANGES = MapHelp.items("WHATS_NEW");

	/** What a start of the plugin does about the version last run: see {@link #start}. */
	@lombok.Value
	static class Start
	{
		/** The plugin was updated: a version was run before, and it was another. */
		boolean updated;
		/** What's new is to open with the map: an update whose changes list has something to tell. */
		boolean news;
	}

	/**
	 * What a start does about {@code lastVersion}, the version run before this one ("" or null: none): an update is
	 * one from another version, and brings news when it lists changes. A fresh install and the same version
	 * again bring nothing.
	 */
	static Start start(String lastVersion)
	{
		boolean updated = lastVersion != null && !lastVersion.isEmpty() && !VERSION.equals(lastVersion);
		return new Start(updated, updated && !CHANGES.isEmpty());
	}

	private WhatsNew()
	{
	}
}
