/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapLinks;
import com.google.inject.Provides;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
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
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.LinkBrowser;

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
	/** How often, while the map is open, Shortest Path is asked what route it is showing (3 ticks, about 2 s). */
	private static final int ROUTE_TICKS = 3;
	/** How far the mouse may move between press and release for it to count as a click, in pixels. */
	static final int CLICK_SLOP = 4;
	/** The layer marking a searched monster's spawns, or a place's spots when it has several. */
	static final String SEARCH_LAYER = "advancedmap:search";
	/** A search result is shown at least this close (pixels per tile). */
	static final double SEARCH_ZOOM = 4;
	static final long EXTRAS_MILLIS = 1000;
	static final long FARMING_MILLIS = 10_000;
	/** The package of RuneLite's World Map plugin, whose world map markers are the game's icons again. */
	static final String CORE_WORLD_MAP = "net.runelite.client.plugins.worldmap.";

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

	@Inject
	private WorldMapPointManager worldMapPointManager;

	/** One view per map, each remembering where it was left. */
	private final Map<MapArea, MapViewport> viewports = new EnumMap<>(MapArea.class);
	private volatile MapArea area = MapArea.SURFACE;
	private ExecutorService executor;
	/** The markers' file work, one task after another, so a read never runs ahead of a save made before it. */
	private ExecutorService markerFiles;
	private CacheReader reader;
	private ClientMapSource source;
	private MapIcons icons;
	private MapLabels labels;
	private MapFilters filters;
	private final SideList sideList = new SideList();
	private final MapSearch search = new MapSearch();
	private final MapPins pins = new MapPins();
	private final MarkerEditor editor = new MarkerEditor();
	private final java.util.concurrent.atomic.AtomicInteger touches = new java.util.concurrent.atomic.AtomicInteger();
	private volatile MarkerStore markerStore;
	/** When the pins, farming patches and Slayer task were last read, in milliseconds. */
	private long extrasRead;
	private long farmingRead;
	/** The Slayer task drawn now ("Goblins:24"), or null. */
	private String slayerShown;
	/** The search's extra entries and what they were built from. */
	private List<MapSearch.Entry> extraEntries = Collections.emptyList();
	/** The game's own world maps by name (Zanaris, Keldagrim), for the search; read with the place names. */
	private volatile Map<String, WorldPoint> gameMaps = Collections.emptyMap();
	private Object extraKey;
	/** The pin a right-click menu is open on, or null. */
	private volatile MapPins.Pin menuPin;
	private volatile boolean labelsLoading;
	private RegionImages images;
	private MapPreparer preparer;
	private AdvancedMapPanel panel;
	private MapMessages messages;
	private ShortestPathRoute shortestPathRoute;
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
	/** Stretched Mode's enlargement, for the pop-out window to match. */
	private volatile double windowScale = 1;
	private static final double MAX_WINDOW_SCALE = 4;
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
		MiningSites.load();
		RareTrees.load();
		Transports.load();
		Shops.load();
		Spawns.load();
		search.load();
		MultiCombat.load();
		Farming.load();
		SlayerTask.load();
		MonsterInfo.load();
		reader = new CacheReader(client);
		shortestPathRoute = new ShortestPathRoute();
		source = new ClientMapSource(reader);
		icons = new MapIcons();
		labels = new MapLabels();
		filters = new MapFilters();
		labelsLoading = false;
		moveHiddenIcons();
		settings = readSettings();
		progress = Progress.NONE;
		selection = null;
		plane = 0;
		images = new RegionImages(source, executor, icons);
		if (config.saveMap())
		{
			executor.execute(this::openStore);
		}
		markerFiles = Executors.newSingleThreadExecutor(task ->
		{
			Thread thread = new Thread(task, "Advanced Map markers");
			thread.setDaemon(true);
			return thread;
		});
		markerFiles.execute(this::openMarkers);
		lastSeen = null;
		preparer = new MapPreparer(images, source, () -> config.saveMap() && config.prepareMap(),
			() -> lastSeen != null ? lastSeen : LUMBRIDGE);
		preparer.start();
		messages = new MapMessages(this::openMap);
		painter = new MapPainter(images, messages, icons, labels, filters, sideList, search);
		painter.setPins(pins, editor);
		painter.setMultiCombat(config.multiCombat());
		painter.setInfoOnHover(config.infoOnHover());
		painter.setLinks(this::leadsTo);
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
		if (markerFiles != null)
		{
			// saves already asked for still finish
			markerFiles.shutdown();
		}
		markerFiles = null;
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
			player = Prifddinas.toSurface(currentPlayerLocation());
		}
		if (windowOpen)
		{
			windowScale = stretchedScale();
		}
		long now = System.currentTimeMillis();
		if ((mapOpen || windowOpen) && now - extrasRead >= EXTRAS_MILLIS)
		{
			extrasRead = now;
			readExtras(now);
		}
	}

	/**
	 * Reads what other plugins and RuneLite keep that the map shows: other plugins' world map markers, the farming
	 * patches Time Tracking saved (every {@link #FARMING_MILLIS}), the Slayer task. Client thread.
	 */
	private void readExtras(long now)
	{
		List<MapPins.Pin> others = new ArrayList<>();
		if (config.pluginMarkers())
		{
			// RuneLite lists the markers only to plugins of its own; a check that keeps every marker reads them all
			worldMapPointManager.removeIf(point ->
			{
				MapPins.Pin pin = pinOf(point);
				if (pin != null)
				{
					others.add(pin);
				}
				return false;
			});
		}
		pins.setOthers(others);
		pins.setFilter(config.shareMarkers(), account());
		MapPainter drawing = painter;
		if (drawing != null && now - farmingRead >= FARMING_MILLIS)
		{
			farmingRead = now;
			drawing.setFarming(config.farmingStatus() ? readFarming(now / 1000) : null);
		}
		showSlayerTask();
	}

	/**
	 * Another plugin's world map marker as a pin; null for RuneLite's World Map plugin's (Advanced Map has those icons
	 * itself) and for a marker with no picture.
	 */
	static MapPins.Pin pinOf(WorldMapPoint point)
	{
		if (point.getImage() == null || point.getWorldPoint() == null
			|| point.getClass().getName().startsWith(CORE_WORLD_MAP))
		{
			return null;
		}
		String name = point.getName() != null ? point.getName() : point.getTooltip() != null ? point.getTooltip() : "";
		return new MapPins.Pin(point.getWorldPoint(), point.getImage(), null, name, point.getTooltip(),
			point.isJumpOnClick() ? point.getTarget() : null, point.isSnapToEdge());
	}

	/** Each farming site's patches as Time Tracking saved them for this account. */
	private Map<Farming.Site, List<Farming.Reading>> readFarming(long now)
	{
		Map<Farming.Site, List<Farming.Reading>> out = new java.util.HashMap<>();
		java.util.function.Function<String, String> saved = key ->
			configManager.getRSProfileConfiguration(Farming.GROUP, key);
		for (Farming.Site site : Farming.sites())
		{
			out.put(site, Farming.readings(site, saved, now));
		}
		return out;
	}

	/** Marks the Slayer task's spawns while the setting is on and RuneLite's Slayer plugin has a task saved. */
	private void showSlayerTask()
	{
		MapMessages layers = messages;
		if (layers == null)
		{
			return;
		}
		String task = config.slayerTask() ? configManager.getRSProfileConfiguration(SlayerTask.GROUP,
			SlayerTask.TASK_NAME) : null;
		String amount = task == null ? null : configManager.getRSProfileConfiguration(SlayerTask.GROUP,
			SlayerTask.AMOUNT);
		// the map's Slayer button hides the spawns without turning the setting off
		boolean active = task != null && !task.isEmpty() && amount != null && !"0".equals(amount)
			&& config.slayerShown();
		// the place names narrow a Konar task to its area: drawn again once they have loaded
		String key = active ? task + ":" + amount + ":" + !labelsNow().isEmpty() : null;
		if (java.util.Objects.equals(key, slayerShown))
		{
			return;
		}
		slayerShown = key;
		if (!active)
		{
			layers.remove(SlayerTask.LAYER);
			return;
		}
		List<WorldPoint> spawns = SlayerTask.spawns(task);
		if (spawns.isEmpty())
		{
			layers.remove(SlayerTask.LAYER);
			return;
		}
		String location = configManager.getRSProfileConfiguration(SlayerTask.GROUP, SlayerTask.LOCATION);
		List<SearchSpots.Area> areas = SearchSpots.areas(spawns, player != null ? player : LUMBRIDGE, labelsNow());
		if (location != null && !location.isEmpty())
		{
			// a Konar task names its place: only areas named for it, when any are
			List<SearchSpots.Area> there = new ArrayList<>(areas);
			there.removeIf(area -> !area.getName().toLowerCase(java.util.Locale.ROOT)
				.contains(location.toLowerCase(java.util.Locale.ROOT)));
			if (!there.isEmpty())
			{
				areas = there;
			}
		}
		layers.put(SlayerTask.layer(areas, spawns, task + " (" + amount + " left)"));
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
		return MapSettings.of(config, routingRunning(config.routing()), readHiddenIcons());
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
			lastSeen = Prifddinas.toSurface(here);
		}
		if ((mapOpen || windowOpen) && ++ticks % PROGRESS_TICKS == 0)
		{
			readProgress();
		}
		if ((mapOpen || windowOpen) && ticks % ROUTE_TICKS == 0 && messages != null)
		{
			askForRoute();
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
		else if (messages != null && ShortestPathRoute.NAMESPACE.equals(event.getNamespace()))
		{
			Map<String, Object> query = shortestPathRoute.onMessage(event.getName(), event.getData(),
				currentPlayerLocation(), messages);
			if (query != null)
			{
				clientThread.invokeLater(() -> eventBus.post(new PluginMessage(ShortestPathRoute.NAMESPACE, "query",
					query)));
			}
		}
	}

	/**
	 * Asks Shortest Path what route it is showing, so the map can draw it, while the map is open and Shortest Path
	 * is the routing plugin that is on. Client thread.
	 */
	private void askForRoute()
	{
		if (settings.getRouting() != RoutingPlugin.SHORTEST_PATH)
		{
			shortestPathRoute.clear(messages);
			return;
		}
		eventBus.post(new PluginMessage(ShortestPathRoute.NAMESPACE, "getTarget", ShortestPathRoute.getTarget()));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AdvancedMapConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		settings = readSettings();
		MapPainter drawing = painter;
		if (drawing != null)
		{
			drawing.setMultiCombat(config.multiCombat());
			drawing.setInfoOnHover(config.infoOnHover());
			farmingRead = 0;
			slayerShown = slayerShown == null ? null : "";
		}
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

	/** A mouse button is held on the map (dragging it), or let go: no info box shows on hover meanwhile. */
	void setDragging(boolean on)
	{
		MapPainter drawing = painter;
		if (drawing != null)
		{
			drawing.setDragging(on);
		}
	}

	/** Counts the mouse and key events on the map over the game, for {@link MapOverlay} to draw again at once. */
	void touched()
	{
		touches.incrementAndGet();
	}

	/** How many mouse and key events there have been: a change means the map over the game is drawn again. */
	int touches()
	{
		return touches.get();
	}

	/**
	 * How much Stretched Mode enlarges the game view (1 when it is off), so the pop-out window can draw at the same
	 * size as the map over the game. Client thread.
	 */
	private double stretchedScale()
	{
		if (!client.isStretchedEnabled())
		{
			return 1;
		}
		java.awt.Dimension real = client.getRealDimensions();
		java.awt.Dimension stretched = client.getStretchedDimensions();
		if (real == null || stretched == null || real.width <= 0)
		{
			return 1;
		}
		return Math.max(1, Math.min(MAX_WINDOW_SCALE, stretched.width / (double) real.width));
	}

	/** The scale the pop-out window draws at, from Stretched Mode. */
	double windowScale()
	{
		return windowScale;
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

	/**
	 * Opens the markers' folder, moves an earlier test version's markers (one setting) into it, and reads them. Off the
	 * client thread.
	 */
	private void openMarkers()
	{
		try
		{
			MarkerStore store = MarkerStore.open(getPluginDirectory());
			String old = config.markers();
			if (old != null && !old.isEmpty())
			{
				boolean allSaved = true;
				for (MapPins.Marker marker : MapPins.parseOld(old))
				{
					allSaved &= store.save(marker);
				}
				// the old list stays until every marker has its own file, so none is lost when a save fails
				if (allSaved)
				{
					configManager.unsetConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.MARKERS);
				}
			}
			markerStore = store;
			pins.setMarkers(store.read());
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Advanced Map could not open its markers", e);
		}
	}

	/** Reads the markers again, with those another client (another account) added or changed. Off the client thread. */
	private void reloadMarkers()
	{
		MarkerStore store = markerStore;
		if (store != null)
		{
			pins.setMarkers(store.read());
		}
	}

	/** Moves an earlier version's hidden icons (one setting holding them all) to a setting each. */
	private void moveHiddenIcons()
	{
		Set<String> old = MapSettings.parseHidden(config.hiddenIcons());
		if (old.isEmpty())
		{
			return;
		}
		for (String key : old)
		{
			configManager.setConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.HIDDEN_PREFIX + key, true);
		}
		configManager.unsetConfiguration(AdvancedMapConfig.GROUP, AdvancedMapConfig.HIDDEN_ICONS);
	}

	/**
	 * The hidden icons' keys, each its own setting: two clients open at once (two accounts) hiding icons never undo each
	 * other's.
	 */
	private Set<String> readHiddenIcons()
	{
		Set<String> out = new java.util.LinkedHashSet<>();
		String prefix = AdvancedMapConfig.HIDDEN_PREFIX;
		for (String key : configManager.getConfigurationKeys(AdvancedMapConfig.GROUP + "." + prefix))
		{
			// the whole key, "advancedmap.hidden.<icon>"; an icon's key has no dot
			int at = key.lastIndexOf(prefix);
			if (at >= 0)
			{
				out.add(key.substring(at + prefix.length()));
			}
		}
		return out;
	}

	/** The account logged in (RuneLite's profile key for it), or null. */
	private String account()
	{
		return configManager.getRSProfileKey();
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
			case SLAYER:
				setSwitch(AdvancedMapConfig.SLAYER_SHOWN, !settings.isSlayerShown());
				clientThread.invokeLater(this::showSlayerTask);
				break;
			case FILTERS:
				filters.setOpen(!filters.isOpen());
				selection = null;
				break;
			case FLOOR_UP:
				MapViewport.View under = viewports.get(MapArea.UNDERGROUND).view(1, 1);
				if (area == MapArea.UNDERGROUND && MapArea.underTheGround(under.centerX, under.centerY)
					&& highestFloor(under) <= plane)
				{
					// up from a dungeon's highest floor: out to the ground above it, as down from the ground leads in
					viewports.get(MapArea.SURFACE).follow(viewports.get(MapArea.UNDERGROUND), -MapArea.UNDERGROUND_SHIFT);
					area = MapArea.SURFACE;
					plane = 0;
				}
				else
				{
					plane = Math.min(RegionImages.PLANES - 1, plane + 1);
				}
				selection = null;
				break;
			case FLOOR_DOWN:
				if (area == MapArea.SURFACE && plane == 0)
				{
					// below the ground: the underground map where it lies under this spot (a castle's cellar)
					viewports.get(MapArea.UNDERGROUND).follow(viewports.get(MapArea.SURFACE), MapArea.UNDERGROUND_SHIFT);
					area = MapArea.UNDERGROUND;
					// into the dungeon on its lowest floor, where its entrance usually leads; up climbs its other floors
					plane = 0;
				}
				else
				{
					plane = Math.max(0, plane - 1);
				}
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
		MapPins.Pin pin = menuPin;
		setMenuPin(null);
		List<MapPainter.MenuEntry> entries = MapPainter.menuEntries(open, settings, pin);
		java.awt.Rectangle menu = MapPainter.menuBounds(open, MapPainter.menuTitle(open, progress,
			settings.getFloorNumbers(), pin), entries, width, height);
		for (int i = 0; i < entries.size(); i++)
		{
			if (MapPainter.menuEntryBounds(menu, i).contains(x, y))
			{
				act(entries.get(i).getAction(), open, pin);
			}
		}
		return menu.contains(x, y);
	}

	private void act(MapPainter.Action action, MapPainter.Selection on, MapPins.Pin pin)
	{
		switch (action)
		{
			case ADD_MARKER:
				search.setFocused(false);
				filters.setOpen(false);
				editor.create(on.getPoint());
				break;
			case EDIT_MARKER:
				search.setFocused(false);
				filters.setOpen(false);
				editor.edit(pin.getMarker());
				break;
			case REMOVE_MARKER:
				saveMarkers(pin.getMarker(), null);
				pins.select(null);
				break;
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
			clientThread.invokeLater(() ->
			{
				eventBus.post(new PluginMessage(namespace, "path", Collections.<String, Object>singletonMap("target",
					Prifddinas.toCity(target))));
				// Shortest Path's new route, drawn here as soon as it says what it is showing
				clientThread.invokeLater(this::askForRoute);
			});
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
		MapPins.Pin pin = pinAt(x, y, width, height);
		pins.select(pin);
		if (pin != null)
		{
			selection = null;
			if (pin.getJump() != null)
			{
				goTo(Prifddinas.toSurface(pin.getJump()));
			}
			return;
		}
		MapIcons.Shown icon = iconAt(x, y, width, height);
		showTransport(null);
		if (icon != null && (followLink(icon.getSpot()) || followTransport(icon.getSpot())))
		{
			selection = null;
			return;
		}
		selection = icon == null ? null : new MapPainter.Selection(icon.getSpot(), false, x, y, null);
		if (icon != null && Transports.mayBeTransport(icon.getSpot()))
		{
			showTransport(icon.getSpot());
		}
		else if (icon != null && icon.getSpot().getGroup() == IconGroup.SHOPS)
		{
			MapSpot spot = icon.getSpot();
			Shops.Shop shop = Shops.near(spot.getX(), spot.getY(), spot.getPlane());
			if (shop != null)
			{
				showShop(shop);
			}
		}
	}

	/** Lists what a shop sells and for how much, and any other shops beside it, which a click opens in turn. */
	private void showShop(Shops.Shop shop)
	{
		List<SideList.Row> rows = new ArrayList<>();
		for (Shops.Item item : shop.getItems())
		{
			rows.add(new SideList.Row(item.getName(), item.priceText(), null, null, null, null));
		}
		WorldPoint at = shop.getPoint();
		for (Shops.Shop other : Shops.within(at.getX(), at.getY(), at.getPlane()))
		{
			if (other != shop)
			{
				rows.add(new SideList.Row("Also here: " + other.getName(), "", null, null, other.getPoint(), other));
			}
		}
		sideList.open(shop.getName(), rows, shop);
	}

	/**
	 * Draws lines from a transport icon to every place it goes, each marked with its name, and lists the places when
	 * there are several; null takes them away. They stay until another click or Escape.
	 */
	private void showTransport(MapSpot spot)
	{
		MapMessages layers = messages;
		if (layers == null)
		{
			return;
		}
		if (spot == null)
		{
			layers.remove(Transports.LAYER);
			layers.remove(SEARCH_LAYER);
			sideList.close();
			return;
		}
		List<Transports.Destination> destinations = Transports.from(spot);
		WorldPoint from = new WorldPoint(spot.getX(), spot.getY(), spot.getPlane());
		if (!destinations.isEmpty())
		{
			layers.put(Transports.layer(from, destinations));
			sideList.open(MapIcons.name(spot, progress), transportRows(from, destinations), spot);
		}
	}

	/** A transport's places as list rows: the place, its fare (or the other map it is on), what the trip needs. */
	static List<SideList.Row> transportRows(WorldPoint from, List<Transports.Destination> destinations)
	{
		List<SideList.Row> rows = new ArrayList<>();
		for (Transports.Destination to : destinations)
		{
			String note = MapArea.of(to.getPoint().getY()) != MapArea.of(from.getY())
				? MapArea.of(to.getPoint().getY()).label : to.fareText();
			rows.add(new SideList.Row(to.label(), note, to.getNeeds().isEmpty() ? null : to.getNeeds(), null,
				to.getPoint(), null));
		}
		return rows;
	}

	/**
	 * A click on a dungeon's entrance or exit (a spot in the game's map links) moves the map to where it leads, on
	 * that map and floor, as the game's own map does. False when the icon is no link.
	 */
	private boolean followLink(MapSpot spot)
	{
		WorldPoint to = linkDestination(spot);
		if (to == null)
		{
			return false;
		}
		goTo(to);
		return true;
	}

	/** Where a dungeon's entrance or exit (a spot in the game's map links) leads, or null when the icon is no link. */
	WorldPoint linkDestination(MapSpot spot)
	{
		ClientMapSource links = source;
		if (links == null)
		{
			return null;
		}
		int to = MapLinks.destinationNear(links.knownMapLinks(), spot.getX(), spot.getY(), spot.getPlane(),
			RegionImages.LINK_REACH);
		for (int floor = 0; to < 0 && floor < RegionImages.PLANES; floor++)
		{
			// a few of the game's links name the entrance a floor up from its icon (Slepe's trapdoor)
			to = MapLinks.destinationNear(links.knownMapLinks(), spot.getX(), spot.getY(), floor, 0);
		}
		return to < 0 ? null : new WorldPoint(MapLinks.x(to), MapLinks.y(to), MapLinks.plane(to));
	}

	/**
	 * Where a click on an icon would move the map to the other map (a dungeon's entrance, a transport down or up), or
	 * null: what the dungeon preview shows.
	 */
	WorldPoint leadsTo(MapSpot spot)
	{
		WorldPoint to = linkDestination(spot);
		return to != null ? to : Transports.elsewhere(spot);
	}

	/** A click on a transport that goes to one place on the other map moves the map there. False otherwise. */
	private boolean followTransport(MapSpot spot)
	{
		WorldPoint to = Transports.elsewhere(spot);
		if (to == null)
		{
			return false;
		}
		goTo(to);
		return true;
	}

	/**
	 * The highest floor known to have something in the region in the middle of a view: 0 when only the lowest does, or
	 * nothing is known yet (the floors are known once drawn, or saved by "Save the map").
	 */
	private int highestFloor(MapViewport.View view)
	{
		RegionImages known = images;
		int regionId = ((int) Math.floor(view.centerX) >> 6) << 8 | (int) Math.floor(view.centerY) >> 6;
		for (int floor = RegionImages.PLANES - 1; floor > 0 && known != null; floor--)
		{
			if (known.hasFloor(floor, regionId))
			{
				return floor;
			}
		}
		return 0;
	}

	/** Moves the map to a tile: onto its map and floor, centered there. */
	private void goTo(WorldPoint to)
	{
		area = MapArea.of(to.getY());
		plane = to.getPlane();
		viewports.get(area).centerOn(to.getX(), to.getY());
	}

	/**
	 * A right click: opens the menu of the icon under it, or of the tile when a routing plugin is chosen (to route
	 * there), or closes any.
	 */
	void rightClick(int x, int y, int width, int height)
	{
		pins.select(null);
		MapPins.Pin pin = pinAt(x, y, width, height);
		setMenuPin(pin);
		if (pin != null)
		{
			selection = new MapPainter.Selection(null, true, x, y, pin.getPoint());
			return;
		}
		MapIcons.Shown icon = iconAt(x, y, width, height);
		if (icon != null)
		{
			MapSpot spot = icon.getSpot();
			selection = new MapPainter.Selection(spot, true, x, y, new WorldPoint(spot.getX(), spot.getY(),
				spot.getPlane()));
			return;
		}
		// a tile's menu: Add marker here, and the route entries when a routing plugin is chosen
		MapViewport.View view = viewport().view(width, height);
		selection = new MapPainter.Selection(null, true, x, y, new WorldPoint((int) Math.floor(view.worldX(x)),
			(int) Math.floor(view.worldY(y)), plane));
	}

	/** Escape: leaves the search bar, closes the Filters list, an info box or a menu first, then the map. */
	void escape()
	{
		if (editor.isOpen())
		{
			editor.close();
		}
		else if (search.isFocused())
		{
			search.setFocused(false);
		}
		else if (pins.selected() != null)
		{
			pins.select(null);
		}
		else if (filters.isOpen())
		{
			filters.setOpen(false);
		}
		else if (selection != null || sideList.isOpen())
		{
			selection = null;
			showTransport(null);
		}
		else
		{
			closeMap();
		}
	}

	/** True while the list on the right is open, so Escape closes it before the map. */
	boolean sideListOpen()
	{
		return sideList.isOpen() || editor.isOpen() || pins.selected() != null;
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
	boolean pressPanels(int x, int y, int width, int height, boolean window)
	{
		// while the marker box is open it is drawn over the side list and takes the keys: neither the list nor the
		// search box under it takes a press
		return pressEditor(x, y, width, height, window) || !editor.isOpen() && (pressSearch(x, y, width, height, window)
			|| pressSideList(x, y, width, height, window)) || pressFilters(x, y, width, height, window);
	}

	/** A left press on the marker box: a picture or color is chosen, Save, Remove or Cancel. True when on the box. */
	private boolean pressEditor(int x, int y, int width, int height, boolean window)
	{
		if (!editor.isOpen())
		{
			return false;
		}
		MarkerEditor.Hit hit = editor.hitAt(x, y, MarkerEditor.layout(MapPainter.buttons(width, height, window)));
		switch (hit.getPart())
		{
			case OUTSIDE:
				return false;
			case DESIGN:
			case COLOR:
				editor.choose(hit);
				break;
			case SAVE:
				finishMarker();
				break;
			case REMOVE:
				saveMarkers(editor.editing(), null);
				editor.close();
				break;
			case CANCEL:
				editor.close();
				break;
			default:
				break;
		}
		return true;
	}

	/** Saves the marker in the box (in place of the one changed) and closes it. */
	private void finishMarker()
	{
		saveMarkers(editor.editing(), editor.result(account()));
		editor.close();
	}

	/**
	 * Replaces a marker ({@code old} null to add one) with another ({@code fresh} null to remove it), at once on the map
	 * and then on disk.
	 */
	private void saveMarkers(MapPins.Marker old, MapPins.Marker fresh)
	{
		List<MapPins.Marker> markers = new ArrayList<>(pins.markers());
		// by id: the list may have been read again meanwhile, with another client's change to this marker
		int at = -1;
		for (int i = 0; old != null && i < markers.size() && at < 0; i++)
		{
			if (markers.get(i).getId().equals(old.getId()))
			{
				at = i;
			}
		}
		if (at >= 0)
		{
			markers.remove(at);
		}
		if (fresh != null)
		{
			markers.add(at >= 0 ? at : markers.size(), fresh);
		}
		pins.setMarkers(markers);
		ExecutorService files = markerFiles;
		if (files != null)
		{
			files.execute(() ->
			{
				// the store is read here, after its opening (queued first) has run
				MarkerStore store = markerStore;
				if (store == null)
				{
					return;
				}
				if (fresh != null)
				{
					store.save(fresh);
				}
				else if (old != null)
				{
					store.remove(old);
				}
			});
		}
	}

	private void setMenuPin(MapPins.Pin pin)
	{
		menuPin = pin;
		MapPainter drawing = painter;
		if (drawing != null)
		{
			drawing.setMenuPin(pin);
		}
	}

	private MapPins.Pin pinAt(int x, int y, int width, int height)
	{
		MapViewport.View view = viewport().view(width, height);
		return MapPins.at(pins.visible(view, area, plane, settings.getIconScale()), x, y);
	}

	/**
	 * A left press on the search bar takes the keys (and closes the Filters list), its x empties it, a result is
	 * chosen; a press anywhere else leaves the bar. True when the press was on the bar or its results.
	 */
	private boolean pressSearch(int x, int y, int width, int height, boolean window)
	{
		MapSearch.Layout layout = search.layout(MapPainter.buttons(width, height, window), width);
		MapSearch.Hit hit = search.hitAt(x, y, layout);
		switch (hit.getPart())
		{
			case BOX:
				search.setFocused(true);
				filters.setOpen(false);
				selection = null;
				return true;
			case CLEAR:
				search.clear();
				search.setFocused(true);
				return true;
			case RESULT:
				choose(search.results().get(hit.getIndex()));
				return true;
			default:
				search.setFocused(false);
				return false;
		}
	}

	/** Everything the search bar finds, built again once the place names are read. */
	private List<MapSearch.Entry> searchIndex()
	{
		List<MapSearch.Entry> all = search.index(labelsNow(), extraEntries());
		if (config.searchShops())
		{
			return all;
		}
		// shops only through what they sell (an item's sellers) and their icons, unless the setting says otherwise
		List<MapSearch.Entry> out = new ArrayList<>(all.size());
		for (MapSearch.Entry entry : all)
		{
			if (entry.getKind() != MapSearch.Kind.SHOP)
			{
				out.add(entry);
			}
		}
		return out;
	}

	/** Where search measures "nearest" from: the player, or the middle of the map while logged out. */
	private WorldPoint here()
	{
		return player != null ? player : center();
	}

	/**
	 * The search entries that change while the plugin runs: the player's markers, and each kind of map icon found so
	 * far ("Bank", "Anvil": every one of them, to list the nearest). Built again when either changes.
	 */
	private List<MapSearch.Entry> extraEntries()
	{
		MapIcons known = icons;
		List<MapPins.Marker> markers = pins.shown();
		List<MapSpot> spots = known == null ? Collections.emptyList() : known.allSpots();
		Map<String, WorldPoint> maps = gameMaps;
		Object key = Arrays.asList(markers, spots.size(), progress, maps);
		if (key.equals(extraKey))
		{
			return extraEntries;
		}
		List<MapSearch.Entry> out = new ArrayList<>();
		for (MapPins.Marker marker : markers)
		{
			out.add(new MapSearch.Entry(MapSearch.Kind.MARKER, marker.getName().isEmpty() ? "Marker"
				: marker.getName(), MapSearch.Kind.MARKER.label, Collections.singletonList(marker.getPoint()),
				Collections.emptyList(), null));
		}
		// the game's own maps by name: Zanaris has no place name of its own on any map; a name that is one already is
		// found that way
		java.util.Set<String> placeNames = new java.util.HashSet<>();
		for (MapLabels.Label label : labelsNow())
		{
			placeNames.add(MapSearch.normalize(String.join(" ", label.getLines())));
		}
		maps.forEach((name, at) ->
		{
			if (!placeNames.contains(MapSearch.normalize(name)))
			{
				out.add(new MapSearch.Entry(MapSearch.Kind.PLACE, name, "The game's " + name + " map",
					Collections.singletonList(at), Collections.emptyList(), null));
			}
		});
		Map<Integer, List<WorldPoint>> byCategory = new java.util.TreeMap<>();
		for (MapSpot spot : spots)
		{
			if (spot.getTeleport() == null && spot.getCategory() >= 0 && spot.getCategory() != IconGroup.MAP_LINK)
			{
				byCategory.computeIfAbsent(spot.getCategory(), k -> new ArrayList<>())
					.add(new WorldPoint(spot.getX(), spot.getY(), spot.getPlane()));
			}
		}
		byCategory.forEach((category, points) ->
		{
			String name = progress.categoryName(category);
			if (name != null)
			{
				out.add(new MapSearch.Entry(MapSearch.Kind.ICON, name, points.size() == 1 ? "Map icon"
					: points.size() + " on the map", points, Collections.emptyList(), null));
			}
		});
		extraEntries = Collections.unmodifiableList(out);
		extraKey = key;
		return extraEntries;
	}

	/**
	 * Shows a search result: moves the map to the nearest of its places (to the player, or the map's middle) and, for
	 * a shop, lists its stock; for an item, its sellers, nearest first; for a monster or NPC, marks every spawn and
	 * lists them by area, nearest first.
	 */
	private void choose(MapSearch.Entry entry)
	{
		if (entry == null || entry.getPoints().isEmpty())
		{
			return;
		}
		search.setFocused(false);
		selection = null;
		showTransport(null);
		WorldPoint from = player != null ? player : center();
		WorldPoint nearest = nearest(entry.getPoints(), from);
		switch (entry.getKind())
		{
			case SHOP:
				showShop(entry.getShops().get(0));
				break;
			case ITEM:
				sideList.open(entry.getName(), sellerRows(entry, from), entry);
				break;
			case FAIRY_RING:
			{
				List<Transports.Destination> to = Transports.from(nearest.getX(), nearest.getY(), nearest.getPlane(),
					"Fairy ring");
				if (!to.isEmpty())
				{
					messages.put(Transports.layer(nearest, to));
					sideList.open(entry.getName(), transportRows(nearest, to), entry);
				}
				break;
			}
			case ICON:
				sideList.open("Nearest: " + entry.getName(), nearestRows(entry.getPoints(), from), entry);
				break;
			case MONSTER:
			case NPC:
			case FISHING:
			case HUNTER:
				List<SearchSpots.Area> areas = SearchSpots.areas(entry.getPoints(), from, labelsNow());
				nearest = areas.get(0).getPoint();
				messages.put(SearchSpots.layer(SEARCH_LAYER, entry.getPoints()));
				List<SideList.Row> rows = new ArrayList<>();
				MonsterInfo.Monster monster = entry.getKind() == MapSearch.Kind.MONSTER ? MonsterInfo.of(entry.getName())
					: null;
				if (monster != null)
				{
					// what the monster is like first, then where it is
					rows.addAll(MonsterInfo.rows(monster));
					rows.add(new SideList.Row("Where, nearest first", null, null, null, null, null));
				}
				rows.addAll(SearchSpots.rows(areas, entry.getKind() == MapSearch.Kind.FISHING ? "spot"
					: entry.getKind() == MapSearch.Kind.HUNTER ? "area" : "spawn"));
				sideList.open(entry.getName() + (entry.getKind() == MapSearch.Kind.MONSTER
					? " (" + entry.getNote().toLowerCase(java.util.Locale.ROOT) + ")" : ""), rows, entry);
				break;
			default:
				if (entry.getPoints().size() > 1)
				{
					messages.put(SearchSpots.layer(SEARCH_LAYER, entry.getPoints()));
				}
				break;
		}
		goTo(nearest);
		MapViewport viewport = viewports.get(area);
		if (viewport.view(1, 1).zoom < SEARCH_ZOOM)
		{
			viewport.setZoom(SEARCH_ZOOM);
		}
	}

	/** Every place of a kind of map icon, nearest first: named by the game's nearest place name, with how far. */
	private List<SideList.Row> nearestRows(List<WorldPoint> points, WorldPoint from)
	{
		List<WorldPoint> sorted = new ArrayList<>(points);
		sorted.sort(java.util.Comparator.comparingInt(point -> SearchSpots.distance(point, from)));
		List<SideList.Row> rows = new ArrayList<>();
		for (WorldPoint point : sorted)
		{
			int tiles = SearchSpots.distance(point, from);
			rows.add(new SideList.Row(SearchSpots.name(point, labelsNow()), tiles >= 1000 ? "far" : tiles + " tiles",
				null, null, point, null));
		}
		return rows;
	}

	/** An item's sellers, nearest first: the shop, the price there; a click opens the shop's stock. */
	private static List<SideList.Row> sellerRows(MapSearch.Entry item, WorldPoint from)
	{
		List<Shops.Shop> shops = new ArrayList<>(item.getShops());
		shops.sort(java.util.Comparator.comparingInt(shop -> SearchSpots.distance(shop.getPoint(), from)));
		List<SideList.Row> rows = new ArrayList<>();
		for (Shops.Shop shop : shops)
		{
			String price = "";
			for (Shops.Item sold : shop.getItems())
			{
				if (sold.getName().equals(item.getName()))
				{
					price = sold.priceText();
					break;
				}
			}
			rows.add(new SideList.Row(shop.getName(), price, null, null, shop.getPoint(), shop));
		}
		return rows;
	}

	private static WorldPoint nearest(List<WorldPoint> points, WorldPoint from)
	{
		WorldPoint best = points.get(0);
		for (WorldPoint point : points)
		{
			if (SearchSpots.distance(point, from) < SearchSpots.distance(best, from))
			{
				best = point;
			}
		}
		return best;
	}

	/** The tile at the middle of the map as it is shown now. */
	private WorldPoint center()
	{
		MapViewport.View view = viewport().view(2, 2);
		return new WorldPoint((int) view.worldX(1), (int) view.worldY(1), plane);
	}

	private List<MapLabels.Label> labelsNow()
	{
		MapLabels names = labels;
		return names == null ? Collections.emptyList() : names.all();
	}

	/** True while a search box has the keys: the search bar's, or the open Filters list's. */
	boolean typing()
	{
		return editor.isOpen() || search.isFocused() || filtersOpen();
	}

	/** A key typed while {@link #typing}: into the search bar, else the Filters list's search box. */
	void type(char c)
	{
		if (Character.isISOControl(c))
		{
			return;
		}
		if (editor.isOpen())
		{
			editor.type(c);
		}
		else if (search.isFocused())
		{
			search.type(c, searchIndex(), here());
		}
		else
		{
			typeFilters(c);
		}
	}

	/** A key pressed while {@link #typing}: Backspace, and in the search bar Up, Down and Enter to pick a result. */
	void typingKey(int keyCode)
	{
		if (editor.isOpen())
		{
			if (keyCode == KeyEvent.VK_BACK_SPACE)
			{
				editor.backspace();
			}
			else if (keyCode == KeyEvent.VK_ENTER)
			{
				finishMarker();
			}
			return;
		}
		if (!search.isFocused())
		{
			if (keyCode == KeyEvent.VK_BACK_SPACE)
			{
				backspaceFilters();
			}
			return;
		}
		switch (keyCode)
		{
			case KeyEvent.VK_BACK_SPACE:
				search.backspace(searchIndex(), here());
				break;
			case KeyEvent.VK_UP:
				search.move(-1);
				break;
			case KeyEvent.VK_DOWN:
				search.move(1);
				break;
			case KeyEvent.VK_ENTER:
				choose(search.chosen());
				break;
			default:
				break;
		}
	}

	/**
	 * A left press while a transport's places are listed: a place moves the map there, the close box closes the list
	 * and the lines. True when the press was on the list.
	 */
	private boolean pressSideList(int x, int y, int width, int height, boolean window)
	{
		if (!sideList.isOpen())
		{
			return false;
		}
		SideList.Layout layout = sideList.layout(MapPainter.buttons(width, height, window));
		SideList.Hit hit = sideList.hitAt(x, y, layout);
		if (hit.getIndex() == SideList.Hit.OUTSIDE)
		{
			return false;
		}
		if (hit.getIndex() == SideList.Hit.CLOSE)
		{
			selection = null;
			showTransport(null);
		}
		else if (hit.getIndex() >= 0)
		{
			SideList.Row row = sideList.rows().get(hit.getIndex());
			if (row.getPoint() != null)
			{
				selection = null;
				goTo(row.getPoint());
			}
			if (row.getTarget() instanceof Shops.Shop)
			{
				showShop((Shops.Shop) row.getTarget());
			}
			else if (row.getTarget() instanceof MonsterInfo.WikiPage)
			{
				LinkBrowser.browse(((MonsterInfo.WikiPage) row.getTarget()).getUrl());
			}
		}
		return true;
	}

	private boolean pressFilters(int x, int y, int width, int height, boolean window)
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

	/** The mouse wheel over the Filters list or a transport's places scrolls it. True when it did. */
	boolean wheelPanels(int x, int y, double notches, int width, int height, boolean window)
	{
		if (sideList.isOpen() && !editor.isOpen())
		{
			SideList.Layout places = sideList.layout(MapPainter.buttons(width, height, window));
			if (places.getPanel().contains(x, y))
			{
				sideList.scroll(notches, places);
				return true;
			}
		}
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
	private void typeFilters(char c)
	{
		MapFilters list = filters;
		if (list != null && !Character.isISOControl(c))
		{
			list.type(c);
		}
	}

	private void backspaceFilters()
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
		String key = AdvancedMapConfig.HIDDEN_PREFIX + spot.getKey();
		if (hide)
		{
			configManager.setConfiguration(AdvancedMapConfig.GROUP, key, true);
		}
		else
		{
			configManager.unsetConfiguration(AdvancedMapConfig.GROUP, key);
		}
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
		if (windowOpen)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (window != null && window.isMinimized())
				{
					showWindow();
				}
				else
				{
					closeMap();
				}
			});
			return;
		}
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
		// markers another client (another account) added, changed or removed since
		ExecutorService files = markerFiles;
		if (files != null && !isOpen())
		{
			files.execute(this::reloadMarkers);
		}
		clientThread.invokeLater(() ->
		{
			if (reader == null)
			{
				return;
			}
			player = Prifddinas.toSurface(currentPlayerLocation());
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
				gameMaps = from.gameMaps();
				MapPainter drawing = painter;
				if (drawing != null)
				{
					drawing.setDungeonMaps(from.mapPieces());
				}
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
		if (window != null && window.isMinimized())
		{
			// the Plugin Hub allows no window manipulation, so a minimized window is not brought back up: it closes
			// and a new one opens in its place, where it was
			MapWindow minimized = window;
			window = null;
			minimized.dispose();
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
		// keys go back to the game: an open search box or marker box would otherwise take them when the map opens again
		search.setFocused(false);
		editor.close();
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
