/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The kinds of icon a player can turn on and off, each a setting. The game sorts its map icons into categories (the
 * names on its world map's Key, enum 1713 in the game cache); each category belongs to one group here, and a
 * category the game adds later falls under {@link #OTHER}. Teleports are Advanced Map's own.
 */
enum IconGroup
{
	BANKS(AdvancedMapConfig::showBanks, 1055, 1125, 1126),
	QUESTS(AdvancedMapConfig::showQuests, 1056),
	AGILITY(AdvancedMapConfig::showAgility, 1117, 2505, 1098),
	TELEPORTS(AdvancedMapConfig::showTeleports),
	TRANSPORTATION(AdvancedMapConfig::showTransportation, 1104, 1105),
	DUNGEONS(AdvancedMapConfig::showDungeons, 1062),
	ALTARS(AdvancedMapConfig::showAltars, 1070),
	SHOPS(AdvancedMapConfig::showShops, 1049, 1050, 1052, 1053, 1054, 1057, 1064, 1065, 1066, 1067, 1068, 1069, 1071,
		1072, 1073, 1074, 1075, 1076, 1078, 1079, 1080, 1081, 1082, 1083, 1090, 1093, 1094, 1095, 1096, 1097, 1106,
		1113, 1122, 1123, 1128, 1266, 1267, 1269, 1270, 1272, 1273, 1274, 1790),
	RESOURCES(AdvancedMapConfig::showResources, 1058, 1077, 1085, 1111, 1118, 1425, 2002, 2261),
	FARMING(AdvancedMapConfig::showFarming, 1101),
	CRAFTING(AdvancedMapConfig::showCrafting, 1059, 1060, 1084, 1086, 1088, 1089, 1091, 1092, 1107, 1108, 1109, 1110,
		1115, 1116, 1120, 1271, 1396, 1641, 1642, 1643),
	MINIGAMES(AdvancedMapConfig::showMinigames, 1061, 1087, 1499, 1579, 1716, 2387),
	SLAYER(AdvancedMapConfig::showSlayer, 1099, 1121),
	SAILING(AdvancedMapConfig::showSailing, 2386, 2388, 2389, 2390, 2391, 2392, 2393, 2394),
	SERVICES(AdvancedMapConfig::showServices, 1100, 1102, 1112, 1114, 1119, 1268, 1275, 1459, 1551),
	TUTORS(AdvancedMapConfig::showTutors, 176, 1250, 1251, 1252, 1253, 1254, 1255, 1256, 1257, 1258, 1259, 1260, 1261,
		1262, 1263, 1264, 1265, 1317),
	OTHER(AdvancedMapConfig::showOtherIcons);

	/** The game's category for the arrows that jump to another map; left out, as the map has no such jumps. */
	static final int MAP_LINK = 1063;
	static final int QUEST = 1056;
	static final int AGILITY_SHORTCUT = 1117;
	static final int AGILITY_SHORTCUT_ONE_WAY = 2505;

	private static final Map<Integer, IconGroup> BY_CATEGORY = new HashMap<>();

	static
	{
		for (IconGroup group : values())
		{
			for (int category : group.categories)
			{
				BY_CATEGORY.put(category, group);
			}
		}
	}

	private final Predicate<AdvancedMapConfig> shown;
	private final int[] categories;

	IconGroup(Predicate<AdvancedMapConfig> shown, int... categories)
	{
		this.shown = shown;
		this.categories = categories;
	}

	/** The group of one of the game's icon categories. */
	static IconGroup of(int category)
	{
		return BY_CATEGORY.getOrDefault(category, OTHER);
	}

	boolean isShown(AdvancedMapConfig config)
	{
		return shown.test(config);
	}
}
