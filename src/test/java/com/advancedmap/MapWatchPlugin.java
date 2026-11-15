/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapLinks;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

/**
 * The development client's map watch: feeds {@link MapWatch} from the game as the owner plays, and keeps what it
 * finds in {@code advanced-map-check/watch.tsv} beside the map check's lists. Loaded beside Advanced Map by
 * {@link AdvancedMapPluginTest}; in the test folder, so it is never built into the plugin the Plugin Hub ships.
 * Everything here runs on the client thread but the writing of the file.
 */
@Slf4j
@PluginDescriptor(name = "Advanced Map watch (dev)", description = "Logs where the map and the game disagree")
public class MapWatchPlugin extends Plugin
{
	static final Path FILE = MapCheckPlugin.FOLDER.resolve("watch.tsv");
	/** A jump this many ticks or fewer after an object or NPC was used, and begun this near it, is that use's. */
	private static final int USED_FOR = 50;
	private static final int USED_REACH = 5;
	/** The marker this many ticks in a row away from the player is off. */
	private static final int MARKER_TICKS = 5;
	private static final int SAVE_EVERY = 50;
	/** The game's picture for an empty shop slot. */
	private static final int EMPTY_SLOT = 6512;

	@Inject
	private Client client;
	@Inject
	private PluginManager plugins;
	@Inject
	private ScheduledExecutorService executor;

	private final MapWatch.Log found = new MapWatch.Log();
	private final MapWatch.Stuck stuck = new MapWatch.Stuck();
	private final MapWatch.Fps fps = new MapWatch.Fps();
	private final MapWatch.Memory memory = new MapWatch.Memory();
	private Map<String, List<WorldPoint>> monsters = Collections.emptyMap();
	private Map<String, List<WorldPoint>> items = Collections.emptyMap();
	private List<String[]> innerLinks = Collections.emptyList();
	private int listedFrom;
	private WorldPoint last;
	/** Where the player was last tick as the game has it, not moved onto the surface as {@link #last} is. */
	private WorldPoint lastRaw;
	private String used;
	private WorldPoint usedAt;
	private int usedTick;
	/** The option of {@link #used} alone ("Travel"), to tell an Agility obstacle from a transport. */
	private String usedOption;
	/**
	 * What was used as the reports' receiver takes it ({@code "k":"o","id":14880,"op":1}), as the plugin's own "Report
	 * missing transports" would send it; null when the option is no first to fifth (an examine).
	 */
	private String usedReport;
	/** Each way sent once a client session, as the plugin would. */
	private final java.util.Set<String> sent = new java.util.HashSet<>();

	/**
	 * Where the dev client sends its missing-transport findings, to see the public page as it would be with the
	 * setting shipped (owner, 2026-11-14: "I want these findings to also show on the website"). The Worker of the
	 * advanced-map-reports repository; it answers 404 until deployed, which is only logged.
	 */
	static final String REPORTS = "https://advanced-map-reports.brettgodfrey96.workers.dev/report";
	private static final List<String> OPTIONS = java.util.Arrays.asList("FIRST", "SECOND", "THIRD", "FOURTH", "FIFTH");

	@Inject
	private okhttp3.OkHttpClient http;
	private int markerOff;
	private boolean shopOpened;

	@Override
	protected void startUp()
	{
		try
		{
			if (Files.exists(FILE))
			{
				found.read(Files.readAllLines(FILE, StandardCharsets.UTF_8));
			}
		}
		catch (IOException e)
		{
			log.warn("could not read {}", FILE, e);
		}
		innerLinks = DataFiles.rows("inner-links.tsv");
	}

	@Override
	protected void shutDown()
	{
		last = null;
		save();
	}

	private void save()
	{
		if (!found.takeChanged())
		{
			return;
		}
		List<String> lines = found.lines();
		executor.execute(() ->
		{
			try
			{
				Files.createDirectories(FILE.getParent());
				Files.write(FILE, lines, StandardCharsets.UTF_8);
			}
			catch (IOException e)
			{
				log.warn("could not write {}", FILE, e);
			}
		});
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

	private void note(String[] finding)
	{
		if (found.note(finding, System.currentTimeMillis()))
		{
			log.debug("map watch: {} | {} | {}", finding[0], finding[1], finding[2]);
		}
	}

	/** The map's lists of monsters and items on the ground, taken up once its tables are read. */
	private void takeLists()
	{
		int size = Spawns.all().size();
		if (size != listedFrom)
		{
			monsters = MapWatch.listed(Spawns.all(), false);
			items = MapWatch.listed(Spawns.all(), true);
			listedFrom = size;
		}
	}

	private boolean instanced()
	{
		return client.getTopLevelWorldView() == null || client.getTopLevelWorldView().isInstance();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (MapWatch.forgets(event.getGameState()))
		{
			// a login or a world hop is no jump
			last = null;
			lastRaw = null;
			used = null;
		}
	}

	/**
	 * The object or NPC used last, and where it is: a jump soon after, begun beside it, is its doing. A walk
	 * elsewhere, a spell or a teleporting item forgets it, so a teleport cast beside a banker is not the banker's.
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String action = event.getMenuAction().name();
		String option = event.getMenuOption() == null ? "" : event.getMenuOption();
		if (action.startsWith("GAME_OBJECT_") || action.startsWith("NPC_"))
		{
			NPC npc = event.getMenuEntry().getNpc();
			usedAt = npc != null ? npc.getWorldLocation() : client.getTopLevelWorldView() == null ? null
				: WorldPoint.fromScene(client.getTopLevelWorldView(), event.getParam0(), event.getParam1(),
					client.getTopLevelWorldView().getPlane());
			used = "\"" + option + " " + Text.removeTags(event.getMenuTarget()) + "\" (id " + event.getId() + ")";
			usedOption = option;
			usedTick = client.getTickCount();
			usedReport = report(action, npc, event.getId());
		}
		else if (action.equals("WALK") || option.startsWith("Cast") || option.contains("eleport")
			|| option.equals("Rub") || option.equals("Break"))
		{
			used = null;
		}
	}

	/**
	 * What was used, as the receiver takes it: the kind, its id (an NPC's own, not its slot; an object as this player
	 * sees it), and which of its first five options. Null for another option. As TransportReports does.
	 */
	private String report(String action, NPC npc, int id)
	{
		String[] parts = action.split("_");
		boolean object = parts[0].equals("GAME") && parts.length == 4;
		int option = OPTIONS.indexOf(parts[object ? 2 : 1]) + 1;
		if (option == 0 || !object && npc == null)
		{
			return null;
		}
		int kind = object ? id : npc.getId();
		if (object)
		{
			net.runelite.api.ObjectComposition seen = client.getObjectDefinition(id);
			seen = seen == null || seen.getImpostorIds() == null ? seen : seen.getImpostor();
			kind = seen == null ? id : seen.getId();
		}
		return "\"k\":\"" + (object ? 'o' : 'n') + "\",\"id\":" + kind + ",\"op\":" + option;
	}

	/**
	 * Sends a missing way to the reports' receiver, once a session: what was used, its tile, where the player stood
	 * and arrived. Only a trip the receiver takes (onto the other map, or over its 3 tiles): short ones too, like the
	 * Baxtorian Falls log raft's 13 tiles (owner, 2026-11-15). The answer is logged ("map watch report: 204").
	 */
	private void send(WorldPoint at, WorldPoint from, WorldPoint to)
	{
		String what = usedReport;
		if (what == null || at == null || from == null || to == null
			|| MapArea.of(from.getY()) == MapArea.of(to.getY()) && MapWatch.flat(from, to) <= MapWatch.STEP
			|| !sent.add(what + tile(at) + (to.getX() >> 3) + "," + (to.getY() >> 3)))
		{
			return;
		}
		String body = "{\"v\":\"" + WhatsNew.VERSION + "\"," + what + ",\"at\":" + json(at) + ",\"from\":" + json(from)
			+ ",\"to\":" + json(to) + "}";
		http.newCall(new okhttp3.Request.Builder().url(REPORTS)
			.post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json"), body)).build())
			.enqueue(new okhttp3.Callback()
			{
				@Override
				public void onFailure(okhttp3.Call call, IOException e)
				{
					log.debug("map watch report: not sent ({}) {}", e.getMessage(), body);
				}

				@Override
				public void onResponse(okhttp3.Call call, okhttp3.Response response)
				{
					log.debug("map watch report: {} {}", response.code(), body);
					response.close();
				}
			});
	}

	private static String json(WorldPoint at)
	{
		return "[" + at.getX() + "," + at.getY() + "," + at.getPlane() + "]";
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (npc.getName() == null || npc.getCombatLevel() <= 0 || monsters.isEmpty() || instanced())
		{
			return;
		}
		WorldPoint at = npc.getWorldLocation();
		if (at != null)
		{
			note(MapWatch.monster(Text.removeTags(npc.getName()), npc.getCombatLevel(), at, monsters));
		}
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		TileItem item = event.getItem();
		if (item.getOwnership() != TileItem.OWNERSHIP_NONE || item.isPrivate() || items.isEmpty() || instanced())
		{
			return;
		}
		String name = client.getItemDefinition(item.getId()).getName();
		// what a player or a monster dropped has a timer to vanish by; what the game sets out has none (as
		// RuneLite's Ground Items shows no timer on it). Each is logged, so the rule can be checked in client.log
		int timer = item.getDespawnTime() - client.getTickCount();
		log.debug("map watch item: {} at {} timer {} ticks", name, event.getTile().getWorldLocation(), timer);
		if (timer > 0)
		{
			return;
		}
		if (name != null && !"null".equals(name))
		{
			note(MapWatch.groundItem(name, event.getTile().getWorldLocation(), items));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.SHOPMAIN)
		{
			// its items are there a tick later
			shopOpened = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		AdvancedMapPlugin map = map();
		if (map == null || client.getGameState() != GameState.LOGGED_IN)
		{
			if (map == null || MapWatch.forgets(client.getGameState()))
			{
				last = null;
			}
			return;
		}
		takeLists();
		WorldPoint raw = map.currentPlayerLocation();
		WorldPoint at = Prifddinas.toSurface(raw);
		if (at == null)
		{
			last = null;
			return;
		}
		// a long jump, or a short one right after using an object or NPC that is no Agility obstacle (a ferry)
		boolean afterUse = used != null && client.getTickCount() - usedTick <= USED_FOR && !MapWatch.agility(usedOption);
		if (last != null && (MapWatch.jumped(last, at) || afterUse && MapWatch.stepped(last, at)))
		{
			jumped(map, last, at, raw);
		}
		last = at;
		lastRaw = raw;
		if (shopOpened)
		{
			shopOpened = false;
			shop(at);
		}
		boolean open = map.isMapOpen() || map.isWindowOpen();
		if (open)
		{
			ground(map, at);
			marker(map, at);
		}
		else
		{
			markerOff = 0;
		}
		RegionImages images = map.images;
		note(stuck.add(open && images != null && images.isBusy(), System.currentTimeMillis()));
		note(fps.add(open, client.getFPS()));
		Runtime runtime = Runtime.getRuntime();
		note(memory.add(open, (runtime.totalMemory() - runtime.freeMemory()) >> 20, System.currentTimeMillis()));
		if (client.getTickCount() % SAVE_EVERY == 0)
		{
			save();
		}
	}

	/** A jump: by a dungeon icon it must lead where the player arrived; elsewhere the map must know the way. */
	private void jumped(AdvancedMapPlugin map, WorldPoint from, WorldPoint to, WorldPoint toRaw)
	{
		String with = used != null && client.getTickCount() - usedTick <= USED_FOR && usedAt != null
			&& lastRaw != null && MapWatch.flat(usedAt, lastRaw) <= USED_REACH ? used : null;
		used = null;
		MapIcons icons = map.icons;
		ClientMapSource source = map.source;
		// every trip says how it was judged, so a quiet one is told from one never looked at (owner, 2026-11-14)
		if (with == null || icons == null || source == null || source.knownMapLinks().isEmpty() || instanced())
		{
			log.debug("map watch trip: {} -> {} not judged: {}", tile(from), tile(to), with == null
				? "no object or NPC used near, just before" : instanced() ? "an instance" : "the map's links not read yet");
			return;
		}
		MapSpot icon = MapWatch.dungeonIconNear(icons.allSpots(map.progress()), from);
		WorldPoint leads = icon == null ? null : map.leadsTo(icon);
		boolean known = known(source.knownMapLinks(), from, to);
		String[] finding = MapWatch.jump(from, to, with, icon, leads, known);
		log.debug("map watch trip: {} {} -> {}: {}", with, tile(from), tile(to), finding != null ? "finding " + finding[0]
			: icon != null ? "the dungeon icon at " + tile(new WorldPoint(icon.getX(), icon.getY(), icon.getPlane()))
			+ " leads there" : known ? "the map knows the way" : "nothing to report");
		note(finding);
		// a way the map lacks goes to the public reports page too, as the plugin's setting would send it
		if (finding != null && (finding[0].equals("transport-unlisted") || finding[0].startsWith("link-")))
		{
			send(usedAt, lastRaw, toRaw);
		}
	}

	private static String tile(WorldPoint at)
	{
		return at.getX() + "," + at.getY() + "," + at.getPlane();
	}

	/** True when the map has a way from near one tile to near the other: a transport, a link, a way in a dungeon. */
	private boolean known(Map<Integer, Integer> links, WorldPoint from, WorldPoint to)
	{
		for (Transports.Destination destination : Transports.from(from.getX(), from.getY(), from.getPlane()))
		{
			if (arrives(destination.getPoint(), to))
			{
				return true;
			}
		}
		for (int plane = 0; plane < RegionImages.PLANES; plane++)
		{
			int link = MapLinks.destinationNear(links, from.getX(), from.getY(), plane, MapWatch.ICON_REACH);
			if (link >= 0 && arrives(new WorldPoint(MapLinks.x(link), MapLinks.y(link), MapLinks.plane(link)), to))
			{
				return true;
			}
		}
		for (String[] row : innerLinks)
		{
			try
			{
				WorldPoint start = new WorldPoint(Integer.parseInt(row[0]), Integer.parseInt(row[1]), 0);
				WorldPoint end = new WorldPoint(Integer.parseInt(row[3]), Integer.parseInt(row[4]), 0);
				if (!MapWatch.jumped(start, new WorldPoint(from.getX(), from.getY(), 0))
					&& MapWatch.flat(start, from) <= MapWatch.ICON_REACH && arrives(end, to))
				{
					return true;
				}
			}
			catch (RuntimeException e)
			{
				// a row that is no way
			}
		}
		return false;
	}

	private static boolean arrives(WorldPoint leads, WorldPoint to)
	{
		return leads != null && MapArea.of(leads.getY()) == MapArea.of(to.getY())
			&& MapWatch.flat(leads, to) <= MapWatch.ARRIVE;
	}

	/** The shop window just opened: its stock against the nearest shop's on the map. */
	private void shop(WorldPoint at)
	{
		Widget stock = client.getWidget(InterfaceID.Shopmain.ITEMS);
		Widget[] slots = stock == null ? null : stock.getDynamicChildren();
		if (slots == null)
		{
			return;
		}
		List<String> names = new ArrayList<>();
		for (Widget slot : slots)
		{
			int id = slot.getItemId();
			if (id > 0 && id != EMPTY_SLOT)
			{
				String name = client.getItemDefinition(id).getName();
				if (name != null && !"null".equals(name))
				{
					names.add(name);
				}
			}
		}
		if (!names.isEmpty())
		{
			// the shop near whose stock is most like the window's: two shops may stand side by side
			Shops.Shop shop = MapWatch.shopFor(MapWatch.shopsNear(at), names);
			for (String[] finding : MapWatch.shop(shop, at, names))
			{
				if (found.note(finding, client.getWorld(), System.currentTimeMillis()))
				{
					log.debug("map watch: {} | {} | {}", finding[0], finding[1], finding[2]);
				}
			}
		}
	}

	/** While the map shows the player's own floor: the tile they stand on must be drawn on it. */
	private void ground(AdvancedMapPlugin map, WorldPoint at)
	{
		RegionImages images = map.images;
		if (images == null || map.area() != MapArea.of(at.getY()) || map.plane() != at.getPlane() || instanced())
		{
			return;
		}
		BufferedImage picture = images.get(at.getPlane(), at.getRegionID(), RegionImages.Level.SMALL);
		if (picture == null)
		{
			if (images.isAbsent(at.getPlane(), at.getRegionID()))
			{
				note(new String[]{"no-picture", "region " + at.getRegionID() + ", floor " + at.getPlane(),
					"the map has nothing for where the player stands, " + MapWatch.tile(at)});
			}
			return;
		}
		int perTile = Math.max(1, picture.getWidth() / 64);
		if (picture.getRGB((at.getX() & 63) * perTile, (63 - (at.getY() & 63)) * perTile) >>> 24 == 0)
		{
			note(new String[]{"empty-tile", MapWatch.cell(at),
				"the player stands at " + MapWatch.tile(at) + ", which the map draws as nothing"});
		}
	}

	/** While the map is open its marker must be where the player is. */
	private void marker(AdvancedMapPlugin map, WorldPoint at)
	{
		WorldPoint marker = map.playerLocation();
		boolean off = marker == null || MapWatch.jumped(marker, at) || MapWatch.flat(marker, at) > 2
			|| marker.getPlane() != at.getPlane();
		markerOff = off ? markerOff + 1 : 0;
		if (markerOff == MARKER_TICKS)
		{
			note(new String[]{"marker-off", "region " + at.getRegionID(), "the marker is at "
				+ (marker == null ? "nowhere" : MapWatch.tile(marker)) + ", the player at " + MapWatch.tile(at)});
		}
	}
}
