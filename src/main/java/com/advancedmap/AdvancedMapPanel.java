/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Advanced Map's sidebar tab: a button that opens and closes the map, how to use it, and how much of the map is saved
 * on this computer, with a button to draw it all again. Swing thread only.
 */
class AdvancedMapPanel extends PluginPanel
{
	/** How often the panel updates its button and the saved map's progress while it is shown. */
	private static final int REFRESH_MS = 1000;
	/** Wide enough for the sidebar, so the text wraps by whole words. */
	private static final int TEXT_WIDTH = 190;

	private final AdvancedMapPlugin plugin;
	private final JButton open = new JButton();
	private final JLabel saved = new JLabel();
	private final Timer refresh;

	AdvancedMapPanel(AdvancedMapPlugin plugin, String keyName)
	{
		super(false);
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
		content.add(left(text(keyName + " also opens and closes it, and Escape closes it. Drag to move, scroll to"
			+ " zoom. Click an icon for its details; right-click an icon to hide it, or anywhere to route there."
			+ " The arrows at the bottom right change floors.")));
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
			plugin.clearSavedMap();
			update();
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
		return "<html><body style='width: " + TEXT_WIDTH + "px'>" + text + "</body></html>";
	}

	private static Component left(javax.swing.JComponent component)
	{
		component.setAlignmentX(Component.LEFT_ALIGNMENT);
		component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
		return component;
	}
}
