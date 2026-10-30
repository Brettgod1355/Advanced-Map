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
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(AdvancedMapConfig.GROUP)
public interface AdvancedMapConfig extends Config
{
	String GROUP = "advancedmap";
	String HIDDEN_ICONS = "hiddenIcons";
	String WINDOW_BOUNDS = "windowBounds";
	String GAME_BOUNDS = "gameBounds";
	String GAME_MAXIMIZED = "gameMaximized";
	String SHOW_HIDDEN = "showHidden";
	String ICONS_OFF = "iconsOff";
	String HIDDEN_TYPES = "hiddenTypes";
	String MARKERS = "markers";
	String MULTI_COMBAT = "multiCombat";
	String FARMING_STATUS = "farmingStatus";
	String PLUGIN_MARKERS = "pluginMarkers";
	String SLAYER_TASK = "slayerTask";
	String SLAYER_SHOWN = "slayerShown";
	String SHARE_MARKERS = "shareMarkers";
	String SEARCH_SHOPS = "searchShops";
	String INFO_ON_HOVER = "infoOnHover";
	/** Before each hidden icon's own setting: "hidden.a1234@3222,3218" = true. */
	String HIDDEN_PREFIX = "hidden.";

	@ConfigSection(
		name = "Icons",
		description = "Which icons the map shows is chosen with Filters at the top left of the map. Point at or click an"
			+ " icon for its details; right-click it to hide just that one.",
		position = 18
	)
	String ICONS = "icons";

	@ConfigItem(
		keyName = "toggleKey",
		name = "Open or close the map",
		description = "The key that opens the map, and closes it again. Escape closes it too. Ctrl+M takes the place"
			+ " of the game's own world map hotkey; pick another key here to keep the game's.",
		position = 0
	)
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK);
	}

	@ConfigItem(
		keyName = "orbMenu",
		name = "World map orb menu entry",
		description = "Add \"Open Advanced Map\" to the right-click menu of the world map orb by the minimap, right"
			+ " under the game's own world map.",
		position = -2
	)
	default boolean orbMenu()
	{
		return true;
	}

	@ConfigItem(
		keyName = "orbLeftClick",
		name = "World map orb opens Advanced Map",
		description = "Left-click the world map orb to open Advanced Map. The game's own world map stays in the orb's"
			+ " right-click menu.",
		position = -1
	)
	default boolean orbLeftClick()
	{
		return false;
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
			+ " world map. The pop out button at the map's top right changes this too.",
		position = 2
	)
	default boolean popOut()
	{
		return false;
	}

	@ConfigItem(
		keyName = INFO_ON_HOVER,
		name = "Info on hover",
		description = "Show an icon's info box when the mouse rests on it, without a click; a click keeps it open. A"
			+ " dungeon's entrance shows the dungeon: a small map of it and the monsters inside. Off: click for info.",
		position = 3
	)
	default boolean infoOnHover()
	{
		return true;
	}

	@ConfigItem(
		keyName = "monsterOrder",
		name = "Dungeon monsters",
		description = "The order a dungeon's preview (point at its entrance) lists the monsters inside in.",
		position = 4
	)
	default MonsterOrder monsterOrder()
	{
		return MonsterOrder.HIGHEST_FIRST;
	}

	@ConfigItem(
		keyName = "playerArrow",
		name = "Arrow to you",
		description = "When you are off the part of the map in view, an arrow at its edge points to where you are, with"
			+ " how far; click it to go back to you.",
		position = 5
	)
	default boolean playerArrow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "wildernessLines",
		name = "Wilderness teleport limits",
		description = "When zoomed in, show red dashed lines where the Wilderness passes level 20 and level 30, the levels"
			+ " most teleports (and a few more) stop working above.",
		position = 6
	)
	default boolean wildernessLines()
	{
		return true;
	}

	@ConfigItem(
		keyName = MULTI_COMBAT,
		name = "Multi-combat areas",
		description = "When zoomed in, tint the areas where combat is multi-way (several can attack one), in the Wilderness"
			+ " and everywhere else, with a red edge.",
		position = 7
	)
	default boolean multiCombat()
	{
		return false;
	}

	@ConfigItem(
		keyName = FARMING_STATUS,
		name = "Farming patch status",
		description = "Put a dot on farming patch icons: green ready, yellow growing, red diseased or dead, gray empty."
			+ " Read from what RuneLite's Time Tracking plugin saved; click a patch for what is growing and when it is"
			+ " ready.",
		position = 8
	)
	default boolean farmingStatus()
	{
		return true;
	}

	@ConfigItem(
		keyName = PLUGIN_MARKERS,
		name = "Other plugins' markers",
		description = "Show the markers other plugins put on the world map, such as a clue scroll's spot or Quest"
			+ " Helper's next step. RuneLite's own World Map plugin's icons are left out (Advanced Map has its own).",
		position = 9
	)
	default boolean pluginMarkers()
	{
		return true;
	}

	@ConfigItem(
		keyName = SEARCH_SHOPS,
		name = "Search finds shops by name",
		description = "List shops in the search bar's results by their names (\"Varrock Swordshop\"). Off, a shop is"
			+ " still found through what it sells, and by clicking its icon.",
		position = 10
	)
	default boolean searchShops()
	{
		return false;
	}

	@ConfigItem(
		keyName = SHARE_MARKERS,
		name = "Share markers between accounts",
		description = "Show the markers you make on every account you play on this computer. Off: each account shows only"
			+ " the markers made on it.",
		position = 11
	)
	default boolean shareMarkers()
	{
		return true;
	}

	@ConfigItem(
		keyName = SLAYER_TASK,
		name = "Slayer task",
		description = "Mark every spawn of your Slayer task's monsters, with what is left of the task. Uses the task"
			+ " RuneLite's Slayer plugin keeps, so that plugin must be on.",
		position = 12
	)
	default boolean slayerTask()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showBanks",
		name = "Banks",
		description = "Banks, the Grand Exchange and its pricing experts.",
		section = ICONS,
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		hidden = true,
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
		return true;
	}

	@ConfigItem(
		keyName = "markLocked",
		name = "Mark what you can't use yet",
		description = "Show agility shortcuts above your Agility level, teleport spells you can't cast yet, and banks"
			+ " you haven't built or unlocked yet, grayed out with a red mark.",
		section = ICONS,
		position = 18
	)
	default boolean markLocked()
	{
		return true;
	}

	@Range(min = MapSettings.MIN_ICON_SCALE, max = MapSettings.MAX_ICON_SCALE)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "iconSize",
		name = "Icon size",
		description = "How big the icons on the map are, as a share of the game's own size.",
		section = ICONS,
		position = 19
	)
	default int iconSize()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "transportNames",
		name = "Transport names",
		description = "When zoomed in, show each fairy ring's code beside it, and the place of each spirit tree, hot air"
			+ " balloon, quetzal, gnome glider, mushtree and Lovakengj minecart.",
		section = ICONS,
		position = 20
	)
	default boolean transportNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "placeNames",
		name = "Place names",
		description = "Show the names of kingdoms when zoomed far out, then towns and places as you zoom in.",
		position = 13
	)
	default boolean placeNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "floorNumbers",
		name = "Floor numbers",
		description = "How the floors above the ground are numbered: \"Ground, 2, 3, 4\" counts the ground floor as the"
			+ " first; \"Ground, 1, 2, 3\" counts the floor above it as the first, as in the UK and on the wiki.",
		position = 14
	)
	default FloorNumbers floorNumbers()
	{
		return FloorNumbers.GROUND_IS_FIRST;
	}

	@ConfigItem(
		keyName = "routing",
		name = "Route with",
		description = "The plugin that right-click \"Route here\" asks for a route: Shortest Path or GPS. \"Route"
			+ " here\" shows only while that plugin is installed and on. With None the menu has no route entries.",
		position = 15
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
		position = 16
	)
	default boolean saveMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "prepareMap",
		name = "Prepare the whole map",
		description = "From the moment the client starts, draw the regions not saved yet in the background, nearest you"
			+ " first, so the map is ready when you open it. Uses spare processor time for a few minutes the first time"
			+ " and after updates.",
		position = 17
	)
	default boolean prepareMap()
	{
		return true;
	}

	/** Where the pop-out window was last, and its size: "x,y,width,height" on the desktop, or empty. */
	@ConfigItem(
		keyName = WINDOW_BOUNDS,
		name = "",
		description = "",
		hidden = true
	)
	default String windowBounds()
	{
		return "";
	}

	/** Where the map's box over the game was last, and its size: "x,y,width,height" on the game view, or empty. */
	@ConfigItem(
		keyName = GAME_BOUNDS,
		name = "",
		description = "",
		hidden = true
	)
	default String gameBounds()
	{
		return "";
	}

	/** The map's box over the game fills the whole game view. */
	@ConfigItem(
		keyName = GAME_MAXIMIZED,
		name = "",
		description = "",
		hidden = true
	)
	default boolean gameMaximized()
	{
		return false;
	}

	/** An earlier version's hidden icons in one setting; moved to one setting each ({@link #HIDDEN_PREFIX}). */
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

	/** An earlier test version's markers, one a line; moved to their own files ({@link MarkerStore}) on start-up. */
	@ConfigItem(
		keyName = MARKERS,
		name = "",
		description = "",
		hidden = true
	)
	default String markers()
	{
		return "";
	}

	@ConfigItem(
		keyName = HIDDEN_TYPES,
		name = "",
		description = "",
		hidden = true
	)
	default String hiddenTypes()
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

	/** The map's Slayer button: the task's spawns shown (on) or not, while "Slayer task" is on. */
	@ConfigItem(
		keyName = SLAYER_SHOWN,
		name = "",
		description = "",
		hidden = true
	)
	default boolean slayerShown()
	{
		return true;
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
