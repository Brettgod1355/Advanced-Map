/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * An unchanging copy of the settings the drawing reads, made again whenever one changes, so a frame reads one
 * consistent set without asking the config for each icon.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class MapSettings
{
	/** Between hidden icons' keys in the saved setting. */
	static final String SEPARATOR = ";";

	Set<IconGroup> groups;
	Set<String> hidden;
	/** Kinds of icon turned off on their own in the Filters list ({@link #typeKey}). */
	Set<String> hiddenTypes;
	boolean hideCompletedQuests;
	boolean markLocked;
	boolean iconsOff;
	boolean showHidden;
	boolean placeNames;
	boolean wildernessLines;
	RoutingPlugin routing;
	FloorNumbers floorNumbers;

	static MapSettings of(AdvancedMapConfig config)
	{
		Set<IconGroup> groups = EnumSet.noneOf(IconGroup.class);
		for (IconGroup group : IconGroup.values())
		{
			if (group.isShown(config))
			{
				groups.add(group);
			}
		}
		return new MapSettings(Collections.unmodifiableSet(groups), parseHidden(config.hiddenIcons()),
			parseHidden(config.hiddenTypes()),
			config.hideCompletedQuests(), config.markLocked(), config.iconsOff(), config.showHidden(),
			config.placeNames(), config.wildernessLines(), config.routing(), config.floorNumbers());
	}

	boolean shows(IconGroup group)
	{
		return groups.contains(group);
	}

	/** True when a spot's group is on and its own kind is not turned off. */
	boolean shows(MapSpot spot)
	{
		return groups.contains(spot.getGroup()) && !hiddenTypes.contains(typeKey(spot));
	}

	/** A kind of icon, as the Filters list turns it off: {@code c1055} for a game category, {@code tJEWELLERY}. */
	static String typeKey(MapSpot spot)
	{
		return spot.getTeleport() != null ? typeKey(spot.getTeleport().getType()) : typeKey(spot.getCategory());
	}

	/** The group a kind of icon ({@link #typeKey}) belongs to. */
	static IconGroup groupOf(String typeKey)
	{
		if (typeKey.startsWith("t"))
		{
			return IconGroup.TELEPORTS;
		}
		try
		{
			return IconGroup.of(Integer.parseInt(typeKey.substring(1)));
		}
		catch (NumberFormatException e)
		{
			return IconGroup.OTHER;
		}
	}

	static String typeKey(int category)
	{
		return "c" + category;
	}

	static String typeKey(Teleport.Type type)
	{
		return "t" + type.name();
	}

	boolean isHidden(MapSpot spot)
	{
		return hidden.contains(spot.getKey());
	}

	static Set<String> parseHidden(String saved)
	{
		if (saved == null || saved.isEmpty())
		{
			return Collections.emptySet();
		}
		Set<String> keys = Arrays.stream(saved.split(SEPARATOR))
			.filter(key -> !key.isEmpty())
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return Collections.unmodifiableSet(keys);
	}

	/** The saved setting with one icon (or kind of icon) hidden or shown again. */
	static String withHidden(Set<String> hidden, String key, boolean hide)
	{
		Set<String> out = new LinkedHashSet<>(hidden);
		if (hide)
		{
			out.add(key);
		}
		else
		{
			out.remove(key);
		}
		return String.join(SEPARATOR, out);
	}
}
