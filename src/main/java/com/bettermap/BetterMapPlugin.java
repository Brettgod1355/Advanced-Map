/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.bettermap;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Better Map",
	description = "A full-screen world map drawn from the game's own map data, with smooth pan and zoom",
	tags = {"map", "world map", "worldmap", "full screen", "fullscreen", "zoom", "atlas"}
)
public class BetterMapPlugin extends Plugin
{
	/** Where the map opens when there is no player to center on (the login screen). */
	static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	/** Two render threads: a fixed count, as the Plugin Hub asks. */
	private static final int RENDER_THREADS = 2;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private EventBus eventBus;

	@Inject
	private BetterMapConfig config;

	private final MapViewport viewport = new MapViewport();
	private ExecutorService executor;
	private CacheReader reader;
	private RegionImages images;
	private MapMessages messages;
	private MapOverlay overlay;
	private MapInput input;
	private HotkeyListener hotkey;
	private NavigationButton button;
	private volatile boolean mapOpen;

	@Provides
	BetterMapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BetterMapConfig.class);
	}

	@Override
	protected void startUp()
	{
		executor = Executors.newFixedThreadPool(RENDER_THREADS, task ->
		{
			Thread thread = new Thread(task, "Better Map renderer");
			thread.setDaemon(true);
			return thread;
		});
		reader = new CacheReader(client);
		images = new RegionImages(new ClientMapSource(reader), executor);
		messages = new MapMessages(this::openMap);
		overlay = new MapOverlay(client, this, viewport, images, messages);
		input = new MapInput(this, viewport, client::getCanvasWidth, client::getCanvasHeight);
		hotkey = new HotkeyListener(() -> config.toggleKey())
		{
			@Override
			public void hotkeyPressed()
			{
				toggleMap();
			}
		};
		button = NavigationButton.builder()
			.tooltip("Better Map")
			.icon(MapIcon.draw(16))
			.priority(7)
			.onClick(this::toggleMap)
			.build();

		overlayManager.add(overlay);
		mouseManager.registerMouseListener(input);
		mouseManager.registerMouseWheelListener(input);
		keyManager.registerKeyListener(input);
		keyManager.registerKeyListener(hotkey);
		if (config.sidebarButton())
		{
			clientToolbar.addNavigation(button);
		}
	}

	@Override
	protected void shutDown()
	{
		mapOpen = false;
		clientToolbar.removeNavigation(button);
		keyManager.unregisterKeyListener(hotkey);
		keyManager.unregisterKeyListener(input);
		mouseManager.unregisterMouseWheelListener(input);
		mouseManager.unregisterMouseListener(input);
		overlayManager.remove(overlay);
		reader.close();
		executor.shutdownNow();
		messages.clear();
		executor = null;
		reader = null;
		images = null;
		messages = null;
		overlay = null;
		input = null;
		hotkey = null;
		button = null;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (reader != null)
		{
			reader.service();
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (messages != null && MapMessages.NAMESPACE.equals(event.getNamespace()))
		{
			messages.handle(event.getName(), event.getData());
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!BetterMapConfig.GROUP.equals(event.getGroup()) || !"sidebarButton".equals(event.getKey()))
		{
			return;
		}
		if (config.sidebarButton())
		{
			clientToolbar.addNavigation(button);
		}
		else
		{
			clientToolbar.removeNavigation(button);
		}
	}

	boolean isMapOpen()
	{
		return mapOpen;
	}

	void toggleMap()
	{
		if (mapOpen)
		{
			closeMap();
		}
		else
		{
			openMap(null, null);
		}
	}

	/** Opens the map on a point, or on the player when {@code at} is null; at a zoom, or the last one. */
	void openMap(WorldPoint at, Double zoom)
	{
		clientThread.invokeLater(() ->
		{
			if (reader == null)
			{
				return;
			}
			WorldPoint center = at != null ? at : playerLocation();
			viewport.centerOn(center.getX(), center.getY());
			if (zoom != null)
			{
				viewport.setZoom(zoom);
			}
			if (!mapOpen)
			{
				mapOpen = true;
				postState(true);
			}
		});
	}

	void closeMap()
	{
		if (!mapOpen)
		{
			return;
		}
		mapOpen = false;
		RegionImages current = images;
		if (current != null)
		{
			current.trim();
		}
		clientThread.invokeLater(() -> postState(false));
	}

	private WorldPoint playerLocation()
	{
		Player player = client.getLocalPlayer();
		WorldPoint point = player == null ? null : WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		return point == null ? LUMBRIDGE : point;
	}

	private void postState(boolean open)
	{
		eventBus.post(new PluginMessage(MapMessages.NAMESPACE, "state", Collections.<String, Object>singletonMap("open", open)));
	}
}
