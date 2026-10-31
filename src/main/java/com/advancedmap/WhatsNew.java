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
	static final String NOTE = "";

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
		if (lastVersion == null || lastVersion.isEmpty() || VERSION.equals(lastVersion) || note.isEmpty())
		{
			return null;
		}
		return "Advanced Map updated to " + VERSION + ": " + note + ".";
	}
}
