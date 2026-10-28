/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Advanced Map's sidebar tab: a button that opens and closes the map, a button that shows or hides how the map works,
 * and how much of the map is saved on this computer, with a button to draw it all again. Scrolls when the explanation
 * is taller than the sidebar. Swing thread only.
 */
class AdvancedMapPanel extends PluginPanel
{
	/** How often the panel updates its button and the saved map's progress while it is shown. */
	private static final int REFRESH_MS = 1000;
	/**
	 * Wide enough for the sidebar, so the text wraps by whole words. In points: Swing's HTML takes a point as one
	 * screen pixel and a CSS pixel as 1.3.
	 */
	private static final int TEXT_WIDTH = 190;

	private final AdvancedMapPlugin plugin;
	private final JButton open = new JButton();
	private final JButton help = new JButton();
	private final JLabel helpText = text(explanation());
	private final JLabel saved = new JLabel();
	private final Timer refresh;

	AdvancedMapPanel(AdvancedMapPlugin plugin, String keyName)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Advanced Map");
		title.setFont(FontManager.getRunescapeBoldFont());
		content.add(left(title));
		content.add(Box.createVerticalStrut(8));

		open.addActionListener(e ->
		{
			plugin.toggleMap();
			update();
		});
		content.add(left(open));
		content.add(Box.createVerticalStrut(8));
		content.add(left(text(keyName + " also opens and closes it, and Escape closes it.")));
		content.add(Box.createVerticalStrut(8));

		help.addActionListener(e -> showHelp(!helpText.isVisible()));
		content.add(left(help));
		content.add(Box.createVerticalStrut(6));
		content.add(left(helpText));
		showHelp(false);
		content.add(Box.createVerticalStrut(14));

		JLabel savedTitle = new JLabel("Saved map");
		savedTitle.setFont(FontManager.getRunescapeBoldFont());
		content.add(left(savedTitle));
		content.add(Box.createVerticalStrut(4));
		content.add(left(saved));
		content.add(Box.createVerticalStrut(8));
		JButton redraw = new JButton("Draw the map again");
		redraw.setToolTipText("Delete the saved map, so every region is drawn again from the game's data");
		redraw.addActionListener(e ->
		{
			int answer = JOptionPane.showConfirmDialog(this,
				"Delete the saved map and draw every region again from the game's data?\n"
					+ "Until it is drawn again, the map fills in as you look around.",
				"Draw the map again", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.OK_OPTION)
			{
				plugin.clearSavedMap();
				update();
			}
		});
		content.add(left(redraw));

		add(content, BorderLayout.NORTH);
		refresh = new Timer(REFRESH_MS, e -> update());
		update();
	}

	@Override
	public void onActivate()
	{
		update();
		refresh.start();
	}

	@Override
	public void onDeactivate()
	{
		refresh.stop();
	}

	void stop()
	{
		refresh.stop();
	}

	private void showHelp(boolean shown)
	{
		helpText.setVisible(shown);
		help.setText(shown ? "Hide how the map works" : "How the map works");
		revalidate();
	}

	/** What each part of the map does, a short paragraph each. */
	static String explanation()
	{
		return section("Moving around", "Drag to move, scroll to zoom around the pointer. The map opens on you, on"
			+ " the surface or the underground map, whichever you are on. The round button at the bottom right brings"
			+ " you back into view.")
			+ section("Search", "Click the box at the top and type: a place, a dungeon, anything a shop sells or a"
			+ " monster. Places come first, the bigger first (\"var\": Varlamore, Varrock); nicknames work (\"ardy\","
			+ " \"fally\"); loose spelling too (\"d scim\", \"kbd\"). Up, Down and Enter pick a result, or click"
			+ " one. A place moves the map there; an item lists the shops that sell it, nearest first; a monster marks"
			+ " every spawn and lists them by area, under its hitpoints, Slayer level and masters, drops and a Wiki"
			+ " button. Fairy ring codes (\"cks\"), fishing spots (\"lobster\"), hunter areas and kinds of icon"
			+ " (\"bank\": every one, nearest first) are found too. Shops by name only with the setting.")
			+ section("Markers", "Right-click anywhere and choose \"Add marker here\": name it, pick a picture and a"
			+ " color, Save. Right-click a marker to edit or remove it. Markers show on all your accounts (a setting"
			+ " keeps them apart). Other plugins' markers (a clue scroll's spot,"
			+ " Quest Helper's next step) show too; click one for what it says.")
			+ section("Farming, multi-combat, Slayer", "A dot on each farming patch icon says how it is doing (from"
			+ " RuneLite's Time Tracking plugin); click it for each crop and when it is ready. Zoomed in, multi-combat"
			+ " areas are tinted red. The Slayer task setting marks every spawn of your task's monsters.")
			+ section("Surface and underground", "The button at the top right switches between the two maps."
			+ " Basements, caves and dungeons are on the underground map.")
			+ section("Floors", "The arrows at the bottom right go up and down a floor. The floors below are shown"
			+ " darkened underneath, so you can see where you are. Zoomed out, the ground floor shows every floor's"
			+ " icons; zoomed in, each icon is on its own floor (Lumbridge Castle's bank is at the top). How floors"
			+ " are numbered is a setting.")
			+ section("Dungeons", "Point at a dungeon's entrance for a look inside: its name, a small map of where"
			+ " you come out and every monster inside, on its lower floors too (the Kalphite Queen, a rope down), the"
			+ " highest level first (a setting turns it round). Click the entrance to go in.")
			+ section("Icons", "Point at an icon, or click it to keep its box open, for its details: a quest's"
			+ " difficulty and the levels it needs, a shortcut's Agility level, a spell's Magic level. Shortcuts and"
			+ " teleports you can't use yet are grayed out with a red mark, and so are bank chests you haven't built"
			+ " yet and banks a quest hasn't opened for you; point at one for what it needs. \"Icons\" at the top left turns every icon off or on at once;"
			+ " place names stay.")
			+ section("Shops and transports", "Click a shop for what it sells and for how much, in a list on the"
			+ " right. Click a spirit tree, fairy ring, charter ship or other transport for lines to where it goes and"
			+ " a list of the places with each fare and what the trip needs; click a place to go there.")
			+ section("Filters", "\"Filters\" at the top left drops down a list of every kind of icon with its"
			+ " picture and name, grouped (Banks, Quests, Shops ...). Tick or untick a whole group or a single kind, or"
			+ " type to search; the mouse wheel scrolls it. An orange dot on the button means some icons are"
			+ " filtered out.")
			+ section("Hidden icons", "Right-click one icon and choose Hide to hide just that one. \"Show hidden"
			+ " icons\" at the top of the Filters list shows them again, faded, so you can right-click one and choose"
			+ " Unhide.")
			+ section("Route here", "Right-click anywhere, or on an icon, and choose \"Route here\" to have the"
			+ " Shortest Path or GPS plugin show the way. Choose which in the settings; it needs that plugin"
			+ " installed.")
			+ section("Wilderness", "Zoomed in, red dashed lines show where the Wilderness passes level 20 and level"
			+ " 30, above which most teleports stop working.")
			+ section("Pop out", "\"Pop out\" at the top right moves the map into a window of its own; \"Over"
			+ " the game\" brings it back. Escape closes it in either.", false);
	}

	private static String section(String title, String text)
	{
		return section(title, text, true);
	}

	private static String section(String title, String text, boolean more)
	{
		return "<b>" + title + "</b><br>" + text + (more ? "<br><br>" : "");
	}

	private void update()
	{
		open.setText(plugin.isOpen() ? "Close the map" : "Open the map");
		saved.setText(wrap(plugin.savedMapStatus()));
	}

	private static JLabel text(String text)
	{
		JLabel label = new JLabel(wrap(text));
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static String wrap(String text)
	{
		return "<html><body style='width: " + TEXT_WIDTH + "pt'>" + text + "</body></html>";
	}

	/**
	 * A row of its own, as wide as the sidebar and as tall as what is in it now, also after its text changes (a button
	 * or label sized once would keep the height of its first text, or none).
	 */
	private static Component left(javax.swing.JComponent component)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(component);
		return row;
	}
}
