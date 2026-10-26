/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.google.inject.Provides;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;

@Slf4j
@PluginDescriptor(
	name = "Advanced Map",
	internalName = "advanced-map",
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
	/** How often the player's levels and quests are read again while the map is open, in game ticks (3 s). */
	private static final int PROGRESS_TICKS = 5;
	/** How far the mouse may move between press and release for it to count as a click, in pixels. */
	static final int CLICK_SLOP = 4;

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

	@Inject
	private PluginManager pluginManager;

	/** One view per map, each remembering where it was left. */
	private final Map<MapArea, MapViewport> viewports = new EnumMap<>(MapArea.class);
	private volatile MapArea area = MapArea.SURFACE;
	private ExecutorService executor;
	private CacheReader reader;
	private ClientMapSource source;
	private MapIcons icons;
	private MapLabels labels;
	private MapFilters filters;
	private volatile boolean labelsLoading;
	private RegionImages images;
	private MapPreparer preparer;
	private AdvancedMapPanel panel;
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
	/** Where the player was last seen, map open or not, for the background drawing to start near them. */
	private volatile WorldPoint lastSeen;
	private volatile MapSettings settings;
	private volatile Progress progress = Progress.NONE;
	private volatile MapPainter.Selection selection;
	/** The floor shown: 0 for the ground, up to 3. */
	private volatile int plane;
	private int ticks;

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
		source = new ClientMapSource(reader);
		icons = new MapIcons();
		labels = new MapLabels();
		filters = new MapFilters();
		labelsLoading = false;
		settings = readSettings();
		progress = Progress.NONE;
		selection = null;
		plane = 0;
		images = new RegionImages(source, executor, icons);
		if (config.saveMap())
		{
			executor.execute(this::openStore);
		}
		lastSeen = null;
		preparer = new MapPreparer(images, source, () -> config.saveMap() && config.prepareMap(),
			() -> lastSeen != null ? lastSeen : LUMBRIDGE);
		preparer.start();
		messages = new MapMessages(this::openMap);
		painter = new MapPainter(images, messages, icons, labels, filters);
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
		// a tab in the sidebar: a button with no panel would go in the client's title bar instead
		panel = new AdvancedMapPanel(this, config.toggleKey().toString());
		button = NavigationButton.builder()
			.tooltip("Advanced Map")
			.icon(MapIcon.draw(16))
			.priority(7)
			.panel(panel)
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
		// startUp may have stopped part way (it threw): undo only what it got to
		if (button != null)
		{
			clientToolbar.removeNavigation(button);
		}
		AdvancedMapPanel stoppedPanel = panel;
		if (stoppedPanel != null)
		{
			SwingUtilities.invokeLater(stoppedPanel::stop);
		}
		if (preparer != null)
		{
			preparer.stop();
		}
		if (hotkey != null)
		{
			keyManager.unregisterKeyListener(hotkey);
		}
		if (input != null)
		{
			keyManager.unregisterKeyListener(input);
			mouseManager.unregisterMouseWheelListener(input);
			mouseManager.unregisterMouseListener(input);
		}
		if (overlay != null)
		{
			overlayManager.remove(overlay);
		}
		if (reader != null)
		{
			reader.close();
		}
		if (executor != null)
		{
			executor.shutdownNow();
		}
		if (messages != null)
		{
			messages.clear();
		}
		executor = null;
		reader = null;
		source = null;
		icons = null;
		labels = null;
		filters = null;
		images = null;
		preparer = null;
		panel = null;
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

	/** The world map orb, in each of the game's minimap layouts. */
	private static final Set<Integer> ORB = new HashSet<>(Arrays.asList(
		InterfaceID.Orbs.ORB_WORLDMAP, InterfaceID.Orbs.WORLDMAP,
		InterfaceID.OrbsNomap.ORB_WORLDMAP, InterfaceID.OrbsNomap.WORLDMAP,
		InterfaceID.OrbsOsm.ORB_WORLDMAP, InterfaceID.OrbsOsm.WORLDMAP,
		InterfaceID.OrbsOsmNomap.ORB_WORLDMAP, InterfaceID.OrbsOsmNomap.WORLDMAP));
	private static final String ORB_OPTION = "Open";
	private static final String ORB_TARGET = ColorUtil.wrapWithColorTag("Advanced Map", JagexColors.MENU_TARGET);

	/**
	 * Adds "Open Advanced Map" to the world map orb's menu, once the game has sorted it: second, right under the
	 * game's left-click option (the menu's top entry is always the left click), or at the top when it is to be the
	 * left click. The game's own entries stay as they are. It only opens
	 * this plugin's map; nothing is sent to the game.
	 */
	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		boolean left = config.orbLeftClick();
		if (reader == null || !left && !config.orbMenu() || client.isMenuOpen())
		{
			return;
		}
		Menu menu = client.getMenu();
		boolean orb = false;
		for (MenuEntry entry : menu.getMenuEntries())
		{
			if (ORB_TARGET.equals(entry.getTarget()))
			{
				return;
			}
			orb |= ORB.contains(entry.getParam1());
		}
		if (!orb)
		{
			return;
		}
		menu.createMenuEntry(left ? -1 : -2)
			.setOption(ORB_OPTION)
			.setTarget(ORB_TARGET)
			.setType(MenuAction.RUNELITE)
			.onClick(entry -> openMap(null, null));
	}

	/** A plugin was turned on or off: "Route here" follows whether the chosen routing plugin is running. */
	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (reader != null)
		{
			settings = readSettings();
		}
	}

	private MapSettings readSettings()
	{
		return MapSettings.of(config, routingRunning(config.routing()));
	}

	/**
	 * Whether a routing plugin is installed and running, found by name in RuneLite's own plugin list (read only;
	 * nothing of the other plugin is touched). Without it "Route here" would send a message nobody answers.
	 */
	private boolean routingRunning(RoutingPlugin routing)
	{
		if (routing == null || routing.getPluginName() == null)
		{
			return false;
		}
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin != null && routing.getPluginName().equals(plugin.getName()))
			{
				return pluginManager.isPluginActive(plugin);
			}
		}
		return false;
	}

	/**
	 * Leaving the game (logout, a disconnect, a world hop) closes the map over the game view: RuneLite stops drawing
	 * overlays there, and an invisible map would still take every click on the login screen. The window stays.
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (mapOpen && (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING
			|| state == GameState.CONNECTION_LOST))
		{
			closeMap();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		WorldPoint here = currentPlayerLocation();
		if (here != null)
		{
			lastSeen = here;
		}
		if ((mapOpen || windowOpen) && ++ticks % PROGRESS_TICKS == 0)
		{
			readProgress();
		}
	}

	/** Reads the player's levels and quests again, keeping the last copy if the game cannot answer now. */
	private void readProgress()
	{
		try
		{
			MapIcons current = icons;
			progress = Progress.read(client, current == null ? Collections.emptySet() : current.watchedVars());
		}
		catch (RuntimeException e)
		{
			log.debug("could not read quests and levels", e);
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
		if (!AdvancedMapConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		settings = readSettings();
		if ("saveMap".equals(event.getKey()) && images != null)
		{
			if (config.saveMap())
			{
				executor.execute(this::openStore);
			}
			else
			{
				images.setStore(null);
			}
		}
		if (!"sidebarButton".equals(event.getKey()))
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

	/** Opens the saved map in the plugin's data folder. Render thread. */
	private void openStore()
	{
		RegionImages current = images;
		if (current == null || current.store() != null)
		{
			return;
		}
		try
		{
			current.setStore(MapStore.open(getPluginDirectory()));
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Advanced Map could not open its saved map; it will be drawn each time", e);
		}
	}

	/** Deletes the saved map, so every region is drawn again. */
	void clearSavedMap()
	{
		RegionImages current = images;
		MapPreparer currentPreparer = preparer;
		if (current == null || current.store() == null)
		{
			return;
		}
		executor.execute(() ->
		{
			current.store().clear();
			currentPreparer.restart();
		});
	}

	/** For the sidebar: how much of the map is saved on this computer. */
	String savedMapStatus()
	{
		RegionImages current = images;
		MapStore store = current == null ? null : current.store();
		if (!config.saveMap())
		{
			return "Off: the map is drawn again each time the client starts (Advanced Map's settings).";
		}
		if (store == null)
		{
			return "Not ready yet.";
		}
		int remaining = preparer == null ? -1 : preparer.remaining();
		String count = store.count() + " regions and floors saved on this computer, for every character and profile.";
		if (!config.prepareMap() || remaining < 0)
		{
			return count;
		}
		return remaining == 0 ? count + " The whole map is ready." : count + " Preparing the rest: " + remaining
			+ " to go.";
	}

	/** True while the map is open, over the game or in its window. */
	boolean isOpen()
	{
		return mapOpen || windowOpen;
	}

	MapSettings settings()
	{
		return settings;
	}

	int plane()
	{
		return plane;
	}

	Progress progress()
	{
		return progress;
	}

	MapPainter.Selection selection()
	{
		return selection;
	}

	/** Does what a map button does. {@code window} when pressed in the pop-out window. */
	void pressButton(MapPainter.Button button, boolean window)
	{
		switch (button)
		{
			case CLOSE:
				closeMap();
				break;
			case AREA:
				switchArea();
				break;
			case MODE:
				if (window)
				{
					dock();
				}
				else
				{
					popOut();
				}
				break;
			case ICONS:
				setSwitch(AdvancedMapConfig.ICONS_OFF, !settings.isIconsOff());
				selection = null;
				break;
			case FILTERS:
				filters.setOpen(!filters.isOpen());
				selection = null;
				break;
			case FLOOR_UP:
				plane = Math.min(RegionImages.PLANES - 1, plane + 1);
				selection = null;
				break;
			case FLOOR_DOWN:
				plane = Math.max(0, plane - 1);
				selection = null;
				break;
			default:
				locate();
				break;
		}
	}

	/**
	 * A left press while the right-click menu is open: on an entry it does what the entry says. The menu closes either
	 * way. True when the press was on the menu, so it does not start a drag.
	 */
	boolean pressMenu(int x, int y, int width, int height)
	{
		MapPainter.Selection open = selection;
		if (open == null || !open.isMenu())
		{
			return false;
		}
		selection = null;
		List<MapPainter.MenuEntry> entries = MapPainter.menuEntries(open, settings);
		java.awt.Rectangle menu = MapPainter.menuBounds(open, MapPainter.menuTitle(open, progress, settings.getFloorNumbers()), entries, width,
			height);
		for (int i = 0; i < entries.size(); i++)
		{
			if (MapPainter.menuEntryBounds(menu, i).contains(x, y))
			{
				act(entries.get(i).getAction(), open);
			}
		}
		return menu.contains(x, y);
	}

	private void act(MapPainter.Action action, MapPainter.Selection on)
	{
		switch (action)
		{
			case HIDE:
				setHidden(on.getSpot(), true);
				break;
			case UNHIDE:
				setHidden(on.getSpot(), false);
				break;
			case ROUTE:
				route(on.getPoint());
				break;
			default:
				clearRoute();
				break;
		}
	}

	/** Asks the chosen routing plugin for a route from the player to a tile. */
	private void route(WorldPoint target)
	{
		String namespace = settings.getRouting().getNamespace();
		if (namespace != null)
		{
			clientThread.invokeLater(() -> eventBus.post(new PluginMessage(namespace, "path",
				Collections.<String, Object>singletonMap("target", target))));
		}
	}

	private void clearRoute()
	{
		String namespace = settings.getRouting().getNamespace();
		if (namespace != null)
		{
			clientThread.invokeLater(() -> eventBus.post(new PluginMessage(namespace, "clear")));
		}
	}

	/** A left click (pressed and released in place): opens the info box of the icon under it, or closes it. */
	void leftClick(int x, int y, int width, int height)
	{
		MapIcons.Shown icon = iconAt(x, y, width, height);
		selection = icon == null ? null : new MapPainter.Selection(icon.getSpot(), false, x, y, null);
	}

	/**
	 * A right click: opens the menu of the icon under it, or of the tile when a routing plugin is chosen (to route
	 * there), or closes any.
	 */
	void rightClick(int x, int y, int width, int height)
	{
		MapIcons.Shown icon = iconAt(x, y, width, height);
		if (icon != null)
		{
			MapSpot spot = icon.getSpot();
			selection = new MapPainter.Selection(spot, true, x, y, new WorldPoint(spot.getX(), spot.getY(),
				spot.getPlane()));
			return;
		}
		if (settings.getRouting() == RoutingPlugin.NONE)
		{
			selection = null;
			return;
		}
		MapViewport.View view = viewport().view(width, height);
		selection = new MapPainter.Selection(null, true, x, y, new WorldPoint((int) Math.floor(view.worldX(x)),
			(int) Math.floor(view.worldY(y)), plane));
	}

	/** Escape: closes the Filters list, an info box or a menu first, then the map. */
	void escape()
	{
		if (filters.isOpen())
		{
			filters.setOpen(false);
		}
		else if (selection != null)
		{
			selection = null;
		}
		else
		{
			closeMap();
		}
	}

	/** True while the Filters list is open, so keys go to its search box. */
	boolean filtersOpen()
	{
		MapFilters current = filters;
		return current != null && current.isOpen();
	}

	/**
	 * A left press while the Filters list is open: a box turns its group or kind on or off, "All on" and "All off" do
	 * so for everything. A press elsewhere closes the list (the Filters button itself toggles it). True when the press
	 * was on the list, so it does nothing else.
	 */
	boolean pressFilters(int x, int y, int width, int height, boolean window)
	{
		MapFilters list = filters;
		if (list == null || !list.isOpen())
		{
			return false;
		}
		java.awt.Rectangle button = MapPainter.buttons(width, height, window).get(MapPainter.Button.FILTERS);
		List<MapFilters.Entry> entries = list.entries(progress);
		MapFilters.Hit hit = list.hitAt(x, y, MapFilters.layout(button, height), entries);
		switch (hit.getPart())
		{
			case OUTSIDE:
				if (!button.contains(x, y))
				{
					list.setOpen(false);
				}
				return false;
			case ALL_ON:
				for (IconGroup group : IconGroup.values())
				{
					configManager.setConfiguration(AdvancedMapConfig.GROUP, group.key, true);
				}
				configManager.setConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.HIDDEN_TYPES, "");
				break;
			case ALL_OFF:
				for (IconGroup group : IconGroup.values())
				{
					configManager.setConfiguration(AdvancedMapConfig.GROUP, group.key, false);
				}
				break;
			case SHOW_HIDDEN:
				configManager.setConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.SHOW_HIDDEN,
					!settings.isShowHidden());
				break;
			case ENTRY:
				toggle(hit.getEntry());
				break;
			default:
				break;
		}
		settings = readSettings();
		return true;
	}

	/**
	 * Turns a group or kind on or off. A group turned on shows all its kinds again; a kind turned on while its group is
	 * off turns the group on with only that kind.
	 */
	private void toggle(MapFilters.Entry entry)
	{
		IconGroup group = entry.getGroup();
		java.util.Set<String> hidden = new java.util.LinkedHashSet<>(settings.getHiddenTypes());
		if (entry.isGroup())
		{
			boolean on = !settings.shows(group);
			if (on)
			{
				hidden.removeIf(key -> MapSettings.groupOf(key) == group);
			}
			configManager.setConfiguration(AdvancedMapConfig.GROUP, group.key, on);
		}
		else if (!settings.shows(group))
		{
			hidden.addAll(filters.keysOf(group));
			hidden.remove(entry.getKey());
			configManager.setConfiguration(AdvancedMapConfig.GROUP, group.key, true);
		}
		else if (!hidden.remove(entry.getKey()))
		{
			hidden.add(entry.getKey());
		}
		configManager.setConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.HIDDEN_TYPES,
			String.join(MapSettings.SEPARATOR, hidden));
	}

	/** The mouse wheel over the Filters list scrolls it. True when it did. */
	boolean wheelFilters(int x, int y, double notches, int width, int height, boolean window)
	{
		MapFilters list = filters;
		if (list == null || !list.isOpen())
		{
			return false;
		}
		java.awt.Rectangle button = MapPainter.buttons(width, height, window).get(MapPainter.Button.FILTERS);
		MapFilters.Layout layout = MapFilters.layout(button, height);
		if (!layout.getPanel().contains(x, y))
		{
			return false;
		}
		list.scroll(notches, layout, list.entries(progress).size());
		return true;
	}

	/** A key typed while the Filters list is open: into its search box. */
	void typeFilters(char c)
	{
		MapFilters list = filters;
		if (list != null && !Character.isISOControl(c))
		{
			list.type(c);
		}
	}

	void backspaceFilters()
	{
		MapFilters list = filters;
		if (list != null)
		{
			list.backspace();
		}
	}

	private MapIcons.Shown iconAt(int x, int y, int width, int height)
	{
		MapIcons current = icons;
		if (current == null)
		{
			// the plugin is stopping; the window may still pass on a click
			return null;
		}
		List<MapIcons.Shown> shown = current.visible(viewport().view(width, height), area, plane, settings, progress);
		return MapIcons.at(shown, x, y);
	}

	/** Moves the map back to the player, on whichever map they are. */
	void locate()
	{
		WorldPoint at = player;
		if (at == null)
		{
			return;
		}
		area = MapArea.of(at.getY());
		plane = at.getPlane();
		viewports.get(area).centerOn(at.getX(), at.getY());
	}

	private void setHidden(MapSpot spot, boolean hide)
	{
		configManager.setConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.HIDDEN_ICONS,
			MapSettings.withHidden(settings.getHidden(), spot.getKey(), hide));
		settings = readSettings();
	}

	private void setSwitch(String key, boolean on)
	{
		configManager.setConfiguration(AdvancedMapConfig.GROUP, key, on);
		settings = readSettings();
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
			readProgress();
			loadLabels();
			WorldPoint center = at != null ? at : player != null ? player : LUMBRIDGE;
			area = MapArea.of(center.getY());
			plane = center.getPlane();
			MapViewport viewport = viewports.get(area);
			viewport.centerOn(center.getX(), center.getY());
			if (zoom != null)
			{
				viewport.setZoom(zoom);
			}
			boolean wasOpen = mapOpen || windowOpen;
			// RuneLite draws overlays only while logged in, so on the login screen the map opens in its window
			if (config.popOut() || client.getGameState() != GameState.LOGGED_IN)
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

	/** Reads the place names in the background, once; again next time if the client had no world map data yet. */
	private void loadLabels()
	{
		if (labelsLoading || !labels.all().isEmpty() && filters.hasCategories())
		{
			return;
		}
		labelsLoading = true;
		ClientMapSource from = source;
		MapLabels into = labels;
		MapFilters list = filters;
		MapIcons pictures = icons;
		executor.execute(() ->
		{
			try
			{
				Map<Integer, Integer> categories = from.categoryIcons();
				categories.values().forEach(areaId -> pictures.addSprite(areaId, from.areaIcon(areaId)));
				list.setCategoryIcons(categories);
				into.set(from.labels());
			}
			catch (RuntimeException e)
			{
				log.debug("could not read the place names", e);
			}
			finally
			{
				labelsLoading = false;
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
			window = new MapWindow(this, painter, this::windowClosed, config.windowBounds(),
				bounds -> configManager.setConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.WINDOW_BOUNDS, bounds));
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
		selection = null;
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
