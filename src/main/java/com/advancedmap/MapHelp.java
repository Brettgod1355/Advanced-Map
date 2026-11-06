/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

/**
 * What each part of the map does and how the map is built, a short paragraph each: the sidebar tab's text, the map's
 * own help page and its Welcome page.
 */
final class MapHelp
{
	/** Each section's title and text. */
	static final String[][] SECTIONS = {
		{"Moving around", "Drag to move, scroll to zoom around the pointer. The map opens on you, on the surface or"
			+ " the underground map, whichever you are on. The round button at the bottom right brings you back into"
			+ " view and keeps you there as you move (lit blue) until you drag the map. When you are out of view, an"
			+ " arrow at the edge points to you; click it to go back."},
		{"Search", "Click the box at the top and type: a place, a dungeon, anything a shop sells or a monster."
			+ " Places come first, the bigger first (\"var\": Varlamore, Varrock); nicknames work (\"ardy\","
			+ " \"fally\"); loose spelling too (\"d scim\", \"kbd\"). Up, Down and Enter pick a result, or click one."
			+ " A place moves the map there; an item lists the shops that sell it, nearest first, and where it lies"
			+ " on the ground; a monster marks every spawn and lists them by area, under its hitpoints, Slayer level"
			+ " and masters, drops and a Wiki button. Fairy ring codes (\"cks\"), fishing spots (\"lobster\"), hunter"
			+ " areas and kinds of icon (\"bank\": every one, nearest first) are found too. The three lines beside the"
			+ " magnifying glass choose what the search looks in. Shops by name only with the setting."},
		{"Markers", "Right-click anywhere and choose \"Add marker here\": name it, pick a picture and a color, Save."
			+ " Right-click a marker to edit or remove it. Markers show on all your accounts (a setting keeps them"
			+ " apart). Other plugins' markers (a clue scroll's spot, Quest Helper's next step) show too; click one"
			+ " for what it says. \"Your markers\" in the menu lists them all, nearest first."},
		{"Farming, multi-combat, Slayer", "A dot on each farming patch icon says how it is doing (from RuneLite's"
			+ " Time Tracking plugin); click it for each crop and when it is ready. Zoomed in, multi-combat areas are"
			+ " tinted red. \"Your Slayer task\" at the top of the Filters list marks every spawn of your task's"
			+ " monsters, with how many are left."},
		{"Surface and underground", "The button at the top right switches between the two maps. Basements, caves and"
			+ " dungeons are on the underground map."},
		{"Floors", "The arrows at the bottom right go up and down a floor. The floors below are shown darkened"
			+ " underneath, so you can see where you are. Zoomed out, the ground floor shows every floor's icons;"
			+ " zoomed in, each icon is on its own floor (Lumbridge Castle's bank is at the top). How floors are"
			+ " numbered is a setting."},
		{"Dungeons", "Point at a dungeon's entrance for a look inside: its name, a small map of where you come out"
			+ " and every monster inside, on its lower floors too (the Kalphite Queen, a rope down), the highest level"
			+ " first (a setting turns it round). Click the entrance to go in."},
		{"Icons", "Point at an icon, or click it to keep its box open, for its details: a quest's difficulty and the"
			+ " levels it needs, a shortcut's Agility level, a spell's Magic level. Shortcuts and teleports you can't"
			+ " use yet are grayed out with a red mark, and so are bank chests you haven't built yet and banks a quest"
			+ " hasn't opened for you; point at one for what it needs. \"Icons\" at the top left turns every icon off"
			+ " or on at once; place names stay."},
		{"Shops and transports", "Click a shop for what it sells and for how much, in a list on the right. Click a"
			+ " spirit tree, fairy ring, charter ship or other transport for lines to where it goes and a list of the"
			+ " places with each fare and what the trip needs; click a place to go there."},
		{"Filters", "\"Filters\" at the top left drops down a list of every kind of icon with its picture and name,"
			+ " grouped (Banks, Quests, Shops ...). Tick or untick a whole group or a single kind, or type to search;"
			+ " the mouse wheel scrolls it. An orange dot on the button means some icons are filtered out."},
		{"Hidden icons", "Right-click one icon and choose Hide to hide just that one. \"Show hidden icons\" at the top"
			+ " of the Filters list shows them again, faded, so you can right-click one and choose Unhide. \"Hidden"
			+ " icons\" in the menu lists them all; click one to go there."},
		{"Route here", "Right-click anywhere, or on an icon, and choose \"Route here\" to have the Shortest Path or"
			+ " GPS plugin show the way. Choose which in the settings; it needs that plugin installed."},
		{"Wilderness", "Zoomed in, red dashed lines show where the Wilderness passes level 20 and level 30, above"
			+ " which most teleports stop working."},
		{"Over the game", "The map opens in a box over the game; the game goes on around it, and a click outside the"
			+ " box is the game's. Drag the bar along its top or bottom (the dots) to move it, a corner or side to"
			+ " size it. The square button at the top right fills the game view with it; press it again to go back."},
		{"Pop out", "The pop out button at the top right (a window with an arrow leaving it) moves the map into a"
			+ " window of its own; the same button there brings it back. Escape closes it in either."},
		{"Menu", "The three lines at the top left open the menu: a welcome (how far the map is built), this help,"
			+ " lists of your markers and of the icons you hid, the plugin's settings, what's new in this version,"
			+ " where to send ideas and bug reports, and building the map again. Escape closes what is open, one step"
			+ " at a time."},
	};

	/** How the map is built and kept up to date: the end of the help, and the Welcome page. */
	static final String[][] BUILDING = {
		{"Once, then almost instant", "The first time, Advanced Map builds the map on this computer from the game data"
			+ " your client already has, and saves it (about 60 MB). This happens once: from then on the map opens"
			+ " almost instantly, on every character and RuneLite profile on this computer. Nothing is downloaded."},
		{"How the map is built", "In the background from the moment the client starts, one region after another: the"
			+ " ground near you first, then farther out, then the floors above. It uses spare processor time, so the"
			+ " game comes first, and takes up to about 15 minutes for the whole world. You can play and use the map"
			+ " meanwhile: places not built yet are blank or blurry, and their icons are missing from the map and the"
			+ " search. The sidebar tab shows how far along it is. With \"Build the whole map\" off, only what you"
			+ " look at is built."},
		{"Game updates and plugin updates", "Game updates need nothing from you. The map, its place names and the"
			+ " game's own map icons come from your client, so when an update changes the world (a new island, a"
			+ " moved wall), the regions it changed are built again the first time you look at them, and new ones in"
			+ " the background. Shops and what they sell, prices, monsters with their stats and drops, items on the"
			+ " ground, and teleport and transport details come from the OSRS Wiki with the plugin: they change when"
			+ " the Plugin Hub updates Advanced Map, which RuneLite does by itself when it starts."},
	};

	/** Every section: how to use the map, then how it is built. */
	static final String[][] ALL = java.util.stream.Stream.concat(java.util.Arrays.stream(SECTIONS),
		java.util.Arrays.stream(BUILDING)).toArray(String[][]::new);

	private MapHelp()
	{
	}
}
