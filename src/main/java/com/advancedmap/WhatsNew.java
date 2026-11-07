/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * The one chat line said after the Plugin Hub updates the plugin. The Hub swaps the jar and restarts the plugin, and
 * the plugin also restarts with the client, so the only way to tell an update from a restart is to remember the last
 * version seen. Bump {@link #VERSION} and rewrite {@link #NOTE} with every release; a test keeps the version in step
 * with the build.
 */
final class WhatsNew
{
	/** The version being run. Must match {@code version} in build.gradle and runelite-plugin.properties. */
	static final String VERSION = "1.0.0";

	/**
	 * One line on what this version brings, no links; a full stop is added when it is said. Empty for a release not
	 * worth a line (a fix, a wording change), and for the first release: nothing is said.
	 */
	static final String NOTE = "a left click on the world map orb now opens Advanced Map (the game's map is in the"
		+ " orb's right-click menu, and a setting turns it back), a menu at the top left (a welcome page, help, your"
		+ " markers and hidden icons, settings, what's new, where to write), \"Report a problem here\" on the map's"
		+ " right-click menu, and the whole map built for the first time with half as many reads of the game's data";

	/** What a start of the plugin does about the version last run: see {@link #start}. */
	@lombok.Value
	static class Start
	{
		/** The plugin was updated: a version was run before, and it was another. */
		boolean updated;
		/** The update line is to be said on a game tick, and What's new to open with the map, if wanted. */
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

	/** The line to say for this version's {@link #NOTE}, or null. */
	static String message(String lastVersion)
	{
		return message(lastVersion, NOTE);
	}

	/**
	 * The line to say, or null: nothing to say, the same version, or a fresh install (no version remembered; every
	 * version from the first release on remembers itself).
	 */
	static String message(String lastVersion, String note)
	{
		return message(lastVersion, note, false);
	}

	/** As above; {@code known} when the update is already known to have happened (the line waits to be said). */
	static String message(String lastVersion, String note, boolean known)
	{
		boolean updated = known || lastVersion != null && !lastVersion.isEmpty() && !VERSION.equals(lastVersion);
		if (!updated || note.isEmpty())
		{
			return null;
		}
		return "Advanced Map updated to " + VERSION + ": " + note + ".";
	}
}
