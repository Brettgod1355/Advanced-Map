/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;

@ConfigGroup(AdvancedMapConfig.GROUP)
public interface AdvancedMapConfig extends Config
{
	String GROUP = "advancedmap";
	String HIDDEN_ICONS = "hiddenIcons";
	String SHOW_HIDDEN = "showHidden";
	String ICONS_OFF = "iconsOff";

	@ConfigSection(
		name = "Icons",
		description = "Which icons the map shows. Click an icon for its details; right-click it to hide just that one.",
		position = 10
	)
	String ICONS = "icons";

	@ConfigItem(
		keyName = "toggleKey",
		name = "Open or close the map",
		description = "The key that opens the map, and closes it again. Escape closes it too.",
		position = 0
	)
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "sidebarButton",
		name = "Sidebar button",
		description = "Show Advanced Map's tab in the sidebar, with a button that opens the map and how much is saved.",
		position = 1
	)
	default boolean sidebarButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "popOut",
		name = "Open in its own window",
		description = "Open the map in a window of its own instead of over the game, to keep beside the game's own"
			+ " world map. The Pop out and Over the game buttons on the map change this too.",
		position = 2
	)
	default boolean popOut()
	{
		return false;
	}

	@ConfigItem(
		keyName = "wildernessLines",
		name = "Wilderness lines",
		description = "Draw the Wilderness levels on the map: a line where each level starts, stronger lines at the"
			+ " level 20 and 30 teleport limits, and the areas with a level of their own. They follow the game's level"
			+ " count, which starts a few tiles south of the ditch; Ferox Enclave is not marked.",
		position = 3
	)
	default boolean wildernessLines()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showBanks",
		name = "Banks",
		description = "Banks, the Grand Exchange and its pricing experts.",
		section = ICONS,
		position = 0
	)
	default boolean showBanks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showQuests",
		name = "Quests",
		description = "Where each quest starts.",
		section = ICONS,
		position = 1
	)
	default boolean showQuests()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAgility",
		name = "Agility",
		description = "Agility shortcuts and courses.",
		section = ICONS,
		position = 2
	)
	default boolean showAgility()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTeleports",
		name = "Teleports",
		description = "Where spells, jewellery, scrolls and other items teleport you.",
		section = ICONS,
		position = 3
	)
	default boolean showTeleports()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTransportation",
		name = "Transportation",
		description = "Boats, carts, gliders, spirit trees and other ways to travel, and house portals.",
		section = ICONS,
		position = 4
	)
	default boolean showTransportation()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDungeons",
		name = "Dungeons",
		description = "Dungeon entrances.",
		section = ICONS,
		position = 5
	)
	default boolean showDungeons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showAltars",
		name = "Altars",
		description = "Prayer altars.",
		section = ICONS,
		position = 6
	)
	default boolean showAltars()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showShops",
		name = "Shops",
		description = "Shops, traders and pubs.",
		section = ICONS,
		position = 7
	)
	default boolean showShops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showResources",
		name = "Skilling spots",
		description = "Mining sites, fishing spots, rare trees, hunter areas and other places to train a gathering skill.",
		section = ICONS,
		position = 8
	)
	default boolean showResources()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFarming",
		name = "Farming patches",
		description = "Farming patches.",
		section = ICONS,
		position = 9
	)
	default boolean showFarming()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showCrafting",
		name = "Crafting and cooking",
		description = "Anvils, furnaces, cooking ranges, spinning wheels, water sources and other places to make things.",
		section = ICONS,
		position = 10
	)
	default boolean showCrafting()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMinigames",
		name = "Minigames and raids",
		description = "Minigames, raids and combat training.",
		section = ICONS,
		position = 11
	)
	default boolean showMinigames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSlayer",
		name = "Slayer masters",
		description = "Slayer masters and task masters.",
		section = ICONS,
		position = 12
	)
	default boolean showSlayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSailing",
		name = "Sailing",
		description = "Docks, salvaging spots, shipwrights and other Sailing places.",
		section = ICONS,
		position = 13
	)
	default boolean showSailing()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showServices",
		name = "Services",
		description = "Hairdressers, estate agents, Death's Office and other services.",
		section = ICONS,
		position = 14
	)
	default boolean showServices()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTutors",
		name = "Tutors and guides",
		description = "Tutors and guides for new players.",
		section = ICONS,
		position = 15
	)
	default boolean showTutors()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOtherIcons",
		name = "Other icons",
		description = "Any other icon on the game's map.",
		section = ICONS,
		position = 16
	)
	default boolean showOtherIcons()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideCompletedQuests",
		name = "Hide completed quests",
		description = "Leave out the start icons of quests you have finished.",
		section = ICONS,
		position = 17
	)
	default boolean hideCompletedQuests()
	{
		return false;
	}

	@ConfigItem(
		keyName = "markLocked",
		name = "Mark what you can't use yet",
		description = "Show agility shortcuts above your Agility level, and teleport spells you can't cast yet, grayed"
			+ " out with a red mark.",
		section = ICONS,
		position = 18
	)
	default boolean markLocked()
	{
		return true;
	}

	@ConfigItem(
		keyName = "placeNames",
		name = "Place names",
		description = "Show the names of kingdoms when zoomed far out, then towns and places as you zoom in.",
		position = 4
	)
	default boolean placeNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "routing",
		name = "Route with",
		description = "The plugin that right-click \"Route here\" asks for a route: Shortest Path or GPS, whichever"
			+ " you have installed. With None the menu has no route entries.",
		position = 5
	)
	default RoutingPlugin routing()
	{
		return RoutingPlugin.SHORTEST_PATH;
	}

	@ConfigItem(
		keyName = "saveMap",
		name = "Save the map on this computer",
		description = "Keep the drawn map in RuneLite's plugin data folder, shared by all your characters and profiles,"
			+ " so it opens at once next time. A region is drawn again only when a game update changes it.",
		position = 6
	)
	default boolean saveMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prepareMap",
		name = "Prepare the whole map",
		description = "Draw the regions you haven't looked at yet in the background, a few a second, and save them, so"
			+ " the whole map is ready. Uses some processor time for a few minutes the first time and after updates.",
		position = 7
	)
	default boolean prepareMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = HIDDEN_ICONS,
		name = "",
		description = "",
		hidden = true
	)
	default String hiddenIcons()
	{
		return "";
	}

	@ConfigItem(
		keyName = SHOW_HIDDEN,
		name = "",
		description = "",
		hidden = true
	)
	default boolean showHidden()
	{
		return false;
	}

	@ConfigItem(
		keyName = ICONS_OFF,
		name = "",
		description = "",
		hidden = true
	)
	default boolean iconsOff()
	{
		return false;
	}
}
