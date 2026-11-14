/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

/**
 * The development client's "Map check" side panel: a button per {@link MapCheck.Kind} opens that list in Advanced
 * Map's own side list, so a click on a row moves the map to the icon. Loaded beside Advanced Map by
 * {@link AdvancedMapPluginTest}; in the test folder, so it is never built into the plugin the Plugin Hub ships.
 */
@Slf4j
@PluginDescriptor(name = "Advanced Map check (dev)",
	description = "Lists dungeon icons with no link or no name, and shops with no icon")
public class MapCheckPlugin extends Plugin
{
	static final Path FOLDER = RuneLite.RUNELITE_DIR.toPath().resolve("advanced-map-check");
	private static final Object LIST = new Object();

	@Inject
	private PluginManager plugins;
	@Inject
	private ClientToolbar toolbar;
	@Inject
	private ClientThread clientThread;

	private NavigationButton button;
	private final JLabel status = new JLabel(" ");

	@Override
	protected void startUp()
	{
		PluginPanel panel = new PluginPanel()
		{
		};
		JPanel rows = new JPanel(new GridLayout(0, 1, 0, 6));
		rows.add(new JLabel("Advanced Map check"));
		for (MapCheck.Kind kind : MapCheck.Kind.values())
		{
			JButton open = new JButton(kind.title);
			open.addActionListener(e -> show(kind));
			rows.add(open);
		}
		JButton folder = new JButton("Open the lists' folder");
		folder.addActionListener(e ->
		{
			// made first: nothing is written there until a list is opened or the map watch finds something, and
			// opening a folder that is not there threw (owner's Black Box, 2026-11-14)
			try
			{
				Files.createDirectories(FOLDER);
				LinkBrowser.open(FOLDER.toString());
			}
			catch (IOException ex)
			{
				status.setText("Could not make " + FOLDER);
			}
		});
		rows.add(folder);
		rows.add(status);
		panel.add(rows);
		button = NavigationButton.builder().tooltip("Advanced Map check").icon(icon()).priority(99).panel(panel)
			.build();
		toolbar.addNavigation(button);
	}

	@Override
	protected void shutDown()
	{
		toolbar.removeNavigation(button);
	}

	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(0xE8, 0x9A, 0x2B));
		g.fillOval(1, 1, 14, 14);
		g.setColor(Color.BLACK);
		g.fillRect(7, 4, 2, 5);
		g.fillRect(7, 11, 2, 2);
		g.dispose();
		return image;
	}

	/** Opens a list in the map (opening the map first), and writes its file. */
	private void show(MapCheck.Kind kind)
	{
		AdvancedMapPlugin map = map();
		if (map == null)
		{
			status.setText("Advanced Map is not running");
			return;
		}
		try
		{
			ClientMapSource source = map.source;
			MapIcons found = map.icons;
			if (source == null || found == null || source.knownMapLinks().isEmpty())
			{
				// before the game's links are read every icon would look like it leads nowhere
				status.setText("Log in, open the map, wait, try again");
				return;
			}
			List<MapSpot> spots = found.allSpots();
			MapLabels names = map.labels;
			List<MapLabels.Label> labels = names == null ? Collections.emptyList() : names.all();
			List<MapSearch.Entry> places = map.search().places();
			WorldPoint player = map.playerLocation();
			WorldPoint from = player != null ? player : new WorldPoint(3222, 3218, 0);
			List<MapCheck.Found> list;
			int icons = 0;
			if (kind == MapCheck.Kind.SHOP_NO_ICON)
			{
				List<Shops.Shop> shops = Shops.all();
				list = MapCheck.shopsWithoutIcon(shops, spots, from);
				icons = shops.size();
			}
			else
			{
				list = MapCheck.of(MapCheck.find(spots, map::leadsTo,
					(spot, to, withLabels) -> DungeonPreview.name(spot, to, places,
						withLabels ? labels : Collections.emptyList()), from), kind);
				for (MapSpot spot : new ArrayList<>(spots))
				{
					icons += spot.getGroup() == IconGroup.DUNGEONS ? 1 : 0;
				}
			}
			Path file = FOLDER.resolve(kind.file + ".tsv");
			MapCheck.write(file, list, labels);
			List<SideList.Row> rows = MapCheck.rows(list, kind, labels, icons, file);
			clientThread.invoke(() ->
			{
				if (!map.isMapOpen())
				{
					map.openMap(null, null);
				}
				map.sideList().open(kind.title + " (" + list.size() + "), nearest first", rows, LIST);
				map.touched();
			});
			status.setText(list.size() + " listed");
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("map check failed", e);
			status.setText("Failed: " + e);
		}
	}

	private AdvancedMapPlugin map()
	{
		for (Plugin plugin : plugins.getPlugins())
		{
			if (plugin instanceof AdvancedMapPlugin && plugins.isPluginEnabled(plugin))
			{
				return (AdvancedMapPlugin) plugin;
			}
		}
		return null;
	}
}
