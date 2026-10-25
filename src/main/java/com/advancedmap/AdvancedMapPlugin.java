/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.google.inject.Provides;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
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
	name = "Advanced Map",
	description = "A full-screen world map drawn from the game's own map data, with smooth pan and zoom",
	tags = {"map", "world map", "worldmap", "full screen", "fullscreen", "zoom", "atlas"}
)
public class AdvancedMapPlugin extends Plugin
{
	/** Where the map opens when there is no player to center on (the login screen). */
	static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	/** Where the underground map opens when the player is not underground (Edgeville Dungeon). */
	static final WorldPoint EDGEVILLE_DUNGEON = new WorldPoint(3096, 9867, 0);
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
	private AdvancedMapConfig config;

	@Inject
	private ConfigManager configManager;

	/** One view per map, each remembering where it was left. */
	private final Map<MapArea, MapViewport> viewports = new EnumMap<>(MapArea.class);
	private volatile MapArea area = MapArea.SURFACE;
	private ExecutorService executor;
	private CacheReader reader;
	private RegionImages images;
	private MapMessages messages;
	private MapPainter painter;
	private MapOverlay overlay;
	private MapInput input;
	private HotkeyListener hotkey;
	private NavigationButton button;
	/** Open over the game view. */
	private volatile boolean mapOpen;
	/** Open in its own window; the window itself is touched on the Swing thread only. */
	private volatile boolean windowOpen;
	private MapWindow window;
	/** Where the player stands, kept by the client thread for the window to draw. */
	private volatile WorldPoint player;

	@Provides
	AdvancedMapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AdvancedMapConfig.class);
	}

	@Override
	protected void startUp()
	{
		executor = Executors.newFixedThreadPool(RENDER_THREADS, task ->
		{
			Thread thread = new Thread(task, "Advanced Map renderer");
			thread.setDaemon(true);
			return thread;
		});
		viewports.put(MapArea.SURFACE, MapArea.SURFACE.newViewport());
		viewports.put(MapArea.UNDERGROUND, MapArea.UNDERGROUND.newViewport());
		viewports.get(MapArea.SURFACE).centerOn(LUMBRIDGE.getX(), LUMBRIDGE.getY());
		viewports.get(MapArea.UNDERGROUND).centerOn(EDGEVILLE_DUNGEON.getX(), EDGEVILLE_DUNGEON.getY());
		area = MapArea.SURFACE;
		reader = new CacheReader(client);
		images = new RegionImages(new ClientMapSource(reader), executor);
		messages = new MapMessages(this::openMap);
		painter = new MapPainter(images, messages);
		overlay = new MapOverlay(client, this, painter);
		input = new MapInput(this, client::getCanvasWidth, client::getCanvasHeight);
		hotkey = new HotkeyListener(() -> config.toggleKey())
		{
			@Override
			public void hotkeyPressed()
			{
				toggleMap();
			}
		};
		button = NavigationButton.builder()
			.tooltip("Advanced Map")
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
		windowOpen = false;
		SwingUtilities.invokeLater(() ->
		{
			MapWindow open = window;
			window = null;
			if (open != null)
			{
				open.dispose();
			}
		});
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
		painter = null;
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
		if (mapOpen || windowOpen)
		{
			player = currentPlayerLocation();
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
		if (!AdvancedMapConfig.GROUP.equals(event.getGroup()) || !"sidebarButton".equals(event.getKey()))
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

	/** True while the map is open over the game view (not in its own window). */
	boolean isMapOpen()
	{
		return mapOpen;
	}

	/** Where the player stands, or null when logged out. */
	WorldPoint playerLocation()
	{
		return player;
	}

	/** Which map is shown: the surface or underground. */
	MapArea area()
	{
		return area;
	}

	/** The view of the map that is shown. */
	MapViewport viewport()
	{
		return viewports.get(area);
	}

	/** Switches between the surface and underground maps; each keeps its own place and zoom. */
	void switchArea()
	{
		area = area.other();
	}

	void toggleMap()
	{
		if (mapOpen || windowOpen)
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
			player = currentPlayerLocation();
			WorldPoint center = at != null ? at : player != null ? player : LUMBRIDGE;
			area = MapArea.of(center.getY());
			MapViewport viewport = viewports.get(area);
			viewport.centerOn(center.getX(), center.getY());
			if (zoom != null)
			{
				viewport.setZoom(zoom);
			}
			boolean wasOpen = mapOpen || windowOpen;
			if (config.popOut())
			{
				windowOpen = true;
				SwingUtilities.invokeLater(this::showWindow);
			}
			else
			{
				mapOpen = true;
			}
			if (!wasOpen)
			{
				postState(true);
			}
		});
	}

	/** Closes the map, over the game or in its window. */
	void closeMap()
	{
		if (windowOpen)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (window != null)
				{
					window.dispose();
				}
			});
			return;
		}
		if (!mapOpen)
		{
			return;
		}
		mapOpen = false;
		closed();
	}

	/** Moves the map from over the game into its own window, and keeps it there next time (the setting). */
	void popOut()
	{
		configManager.setConfiguration(AdvancedMapConfig.GROUP, "popOut", true);
		windowOpen = true;
		mapOpen = false;
		SwingUtilities.invokeLater(this::showWindow);
	}

	/** Moves the map from its window back over the game, and keeps it there next time. Swing thread. */
	void dock()
	{
		configManager.setConfiguration(AdvancedMapConfig.GROUP, "popOut", false);
		MapWindow open = window;
		window = null;
		windowOpen = false;
		mapOpen = true;
		if (open != null)
		{
			open.dispose();
		}
	}

	/** Swing thread. */
	private void showWindow()
	{
		if (!windowOpen || painter == null)
		{
			return;
		}
		if (window == null)
		{
			window = new MapWindow(this, painter, this::windowClosed);
		}
		window.setVisible(true);
	}

	/** The player closed the window (or the map was closed). Swing thread. */
	private void windowClosed(MapWindow closedWindow)
	{
		if (closedWindow != window)
		{
			// moved back over the game, or replaced
			return;
		}
		window = null;
		windowOpen = false;
		closed();
	}

	private void closed()
	{
		RegionImages current = images;
		if (current != null)
		{
			current.trim();
		}
		clientThread.invokeLater(() -> postState(false));
	}

	private WorldPoint currentPlayerLocation()
	{
		Player local = client.getLocalPlayer();
		return local == null ? null : WorldPoint.fromLocalInstance(client, local.getLocalLocation());
	}

	private void postState(boolean open)
	{
		eventBus.post(new PluginMessage(MapMessages.NAMESPACE, "state", Collections.<String, Object>singletonMap("open", open)));
	}
}
