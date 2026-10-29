/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.AreaDefinition;
import com.advancedmap.cache.AreaLoader;
import com.advancedmap.cache.InputStream;
import com.advancedmap.cache.Location;
import com.advancedmap.cache.LocationsLoader;
import com.advancedmap.cache.MapLinks;
import com.advancedmap.cache.MapLoader;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.MapSprite;
import com.advancedmap.cache.ObjectDefinition;
import com.advancedmap.cache.ObjectLoader;
import com.advancedmap.cache.OverlayDefinition;
import com.advancedmap.cache.OverlayLoader;
import com.advancedmap.cache.Region;
import com.advancedmap.cache.UnderlayDefinition;
import com.advancedmap.cache.UnderlayLoader;
import com.advancedmap.cache.WorldMapElements;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.SpritePixels;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpriteID;

/**
 * The map data, read from the client's own game cache through the RuneLite API. Nothing is downloaded: every
 * player's client already holds the whole map. In the current cache each map region is one group of the maps
 * index, numbered by its region id, with the terrain in file 0 and the objects in file 1.
 */
@Slf4j
class ClientMapSource implements MapSource
{
	private static final int MAPS = 5;
	private static final int TEXTURES = 9;
	private static final int UNDERLAYS = 1;
	private static final int OVERLAYS = 4;
	private static final int OBJECTS = 6;
	private static final int AREAS = 35;
	/** The world map's index, and its group of composite maps: one file per map, each with its labels and icons. */
	private static final int WORLD_MAP = 19;
	private static final int COMPOSITE_MAPS = 1;
	/** The world map index's group of each map's name and opening tile. */
	private static final int MAP_DETAILS = 0;
	/** Regions kept decoded; each is read again for its neighbors' edges. */
	private static final int REGIONS_KEPT = 160;
	/** How far around a missing region its neighbors are read with it: 1 is a 3x3 block. */
	private static final int BLOCK_REACH = 1;

	private final CacheReader reader;
	private final Map<Integer, Optional<Region>> regions = Collections.synchronizedMap(
		new LinkedHashMap<Integer, Optional<Region>>(REGIONS_KEPT, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<Integer, Optional<Region>> eldest)
			{
				return size() > REGIONS_KEPT;
			}
		});
	private final Map<Integer, UnderlayDefinition> underlays = new ConcurrentHashMap<>();
	private final Map<Integer, OverlayDefinition> overlays = new ConcurrentHashMap<>();
	private final Map<Integer, ObjectDefinition> objects = new ConcurrentHashMap<>();
	/** {@link #categoryIcons()}, kept once read. */
	private volatile Map<Integer, Integer> categoryAreas;
	private final Map<Integer, Integer> textures = new ConcurrentHashMap<>();
	private final Map<Integer, Optional<MapSprite>> areaIcons = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> areaCategories = new ConcurrentHashMap<>();
	/** CRC32 of each region's map files, read with the region. */
	private final Map<Integer, Long> regionCrcs = new ConcurrentHashMap<>();
	/** The surface regions at Prifddinas's walls, with the city drawn in. */
	private final Map<Integer, Region> withCity = new ConcurrentHashMap<>();
	/** CRC32 of the ground and texture tables, which every region's picture depends on. */
	private volatile long tablesCrc;
	/** CRC32 of the map scenery sprite sheet (trees, rocks and the like drawn on the map). */
	private volatile long spritesCrc;
	/** CRC32 of each object definition read, by object id: a region's walls and scenery come from them. */
	private final Map<Integer, Long> objectCrcs = new ConcurrentHashMap<>();
	/** The map areas that are place names: a name and no icon. */
	private final Map<Integer, AreaDefinition> labelAreas = new ConcurrentHashMap<>();
	private volatile MapSprite[] mapScenes;
	private volatile boolean tablesLoaded;
	private volatile Map<Integer, Integer> mapLinks;

	ClientMapSource(CacheReader reader)
	{
		this.reader = reader;
	}

	/**
	 * A region as the map draws it: Prifddinas's copy left out, and the surface regions at its walls with the city in
	 * them ({@link Prifddinas}).
	 */
	@Override
	public Region region(int regionX, int regionY)
	{
		if (Prifddinas.isCopyRegion(regionX, regionY))
		{
			return null;
		}
		Region region = loaded(regionX, regionY);
		if (region == null || !Prifddinas.takesCity(regionX, regionY))
		{
			return region;
		}
		Region merged = withCity.get(region.getRegionID());
		if (merged == null)
		{
			merged = Prifddinas.merge(region, loaded(regionX + (Prifddinas.SHIFT_X >> 6),
				regionY + (Prifddinas.SHIFT_Y >> 6)));
			withCity.put(region.getRegionID(), merged);
		}
		return merged;
	}

	/** A region as the game has it, read once. */
	private Region loaded(int regionX, int regionY)
	{
		if (regionX < 0 || regionY < 0 || regionX > 255 || regionY > 255)
		{
			return null;
		}
		loadTables();
		int id = regionX << 8 | regionY;
		Optional<Region> known = regions.get(id);
		if (known == null)
		{
			loadBlock(regionX, regionY);
			known = regions.get(id);
			if (known == null)
			{
				throw new CacheNotReadyException();
			}
		}
		return known.orElse(null);
	}

	/**
	 * Reads a region and the regions around it that are not known yet, in one wait for the client thread: drawing a
	 * region needs its neighbors' edges, and the next regions drawn are usually next door.
	 */
	private void loadBlock(int centerX, int centerY)
	{
		List<Integer> ids = new ArrayList<>();
		for (int dx = -BLOCK_REACH; dx <= BLOCK_REACH; dx++)
		{
			for (int dy = -BLOCK_REACH; dy <= BLOCK_REACH; dy++)
			{
				int rx = centerX + dx;
				int ry = centerY + dy;
				if (rx >= 0 && ry >= 0 && rx <= 255 && ry <= 255 && !regions.containsKey(rx << 8 | ry))
				{
					ids.add(rx << 8 | ry);
				}
			}
		}
		Map<Integer, byte[][]> files = reader.read(client ->
		{
			IndexDataBase maps = client.getIndex(MAPS);
			Map<Integer, byte[][]> out = new HashMap<>();
			if (maps == null)
			{
				return out;
			}
			for (int id : ids)
			{
				// no such group: no region there; empty arrays say so
				out.put(id, maps.getFileIds(id) == null ? new byte[0][] : new byte[][]{maps.loadData(id, 0), maps.loadData(id, 1)});
			}
			return out;
		});
		List<Location> placed = new ArrayList<>();
		Map<Integer, Region> loaded = new HashMap<>();
		for (Map.Entry<Integer, byte[][]> entry : files.entrySet())
		{
			int id = entry.getKey();
			byte[][] data = entry.getValue();
			if (data.length == 0)
			{
				regions.put(id, Optional.empty());
			}
			else if (data[0] != null)
			{
				// a region whose terrain the client has not got yet stays unknown, to be asked again
				CRC32 crc = new CRC32();
				crc.update(data[0]);
				if (data[1] != null)
				{
					crc.update(data[1]);
				}
				regionCrcs.put(id, crc.getValue());
				Region region = new Region(id);
				try
				{
					region.loadTerrain(new MapLoader().load(id >> 8, id & 0xFF, data[0]));
					if (data[1] != null)
					{
						region.loadLocations(new LocationsLoader().load(id >> 8, id & 0xFF, data[1]));
					}
				}
				catch (RuntimeException e)
				{
					// one region the readers cannot make sense of (seen: 25287, at the underground's far edge) is left
					// off the map, instead of taking its neighbors with it and being asked again every few seconds
					log.debug("could not read region {}", id, e);
					regions.put(id, Optional.empty());
					continue;
				}
				placed.addAll(region.getLocations());
				loaded.put(id, region);
			}
		}
		loadObjects(placed);
		loaded.forEach((id, region) -> regions.put(id, Optional.of(region)));
	}

	/**
	 * Reads the small tables the drawing looks up all the time (ground types, overlays, textures, map areas and
	 * their icons, the map scenery sheet) in two waits for the client thread, before the first region is drawn.
	 */
	private void loadTables()
	{
		if (tablesLoaded)
		{
			return;
		}
		synchronized (this)
		{
			if (tablesLoaded)
			{
				return;
			}
			Map<Integer, Map<Integer, byte[]>> tables = reader.read(client ->
			{
				Map<Integer, Map<Integer, byte[]>> out = new HashMap<>();
				for (int group : new int[]{UNDERLAYS, OVERLAYS, AREAS})
				{
					out.put(group, files(client.getIndexConfig(), group));
				}
				IndexDataBase textureIndex = client.getIndex(TEXTURES);
				out.put(-TEXTURES, textureIndex == null ? new HashMap<>() : files(textureIndex, 0));
				return out;
			});
			tablesCrc = crc(tables.get(UNDERLAYS), tables.get(OVERLAYS), tables.get(-TEXTURES));
			tables.get(UNDERLAYS).forEach((id, bytes) -> underlays.put(id, new UnderlayLoader().load(id, bytes)));
			tables.get(OVERLAYS).forEach((id, bytes) -> overlays.put(id, new OverlayLoader().load(id, bytes)));
			tables.get(-TEXTURES).forEach((id, bytes) -> textures.put(id, textureColor(bytes)));
			Map<Integer, Integer> areaSprites = new HashMap<>();
			tables.get(AREAS).forEach((id, bytes) ->
			{
				AreaDefinition area = new AreaLoader().load(bytes, id);
				areaCategories.put(id, area.category);
				if (area.spriteId < 0)
				{
					areaIcons.put(id, Optional.empty());
					if (area.name != null && !area.name.isEmpty())
					{
						labelAreas.put(id, area);
					}
				}
				else
				{
					areaSprites.put(id, area.spriteId);
				}
			});
			Map<Integer, MapSprite[]> sprites = reader.read(client ->
			{
				Map<Integer, MapSprite[]> out = new HashMap<>();
				out.put(SpriteID.MAPSCENE, sprites(client, SpriteID.MAPSCENE));
				for (int sprite : new TreeSet<>(areaSprites.values()))
				{
					out.put(sprite, sprites(client, sprite));
				}
				return out;
			});
			if (sprites.values().stream().anyMatch(frames -> frames == null))
			{
				// the client has not loaded these sprites yet: draw nothing now rather than pictures without them
				throw new CacheNotReadyException();
			}
			mapScenes = sprites.get(SpriteID.MAPSCENE);
			spritesCrc = crcOf(mapScenes);
			areaSprites.forEach((id, sprite) ->
			{
				MapSprite[] frames = sprites.get(sprite);
				areaIcons.put(id, frames == null || frames.length == 0 ? Optional.empty() : Optional.ofNullable(frames[0]));
			});
			tablesLoaded = true;
		}
	}

	/** Every file of a group, by id. Client thread. */
	private static Map<Integer, byte[]> files(IndexDataBase index, int group)
	{
		Map<Integer, byte[]> out = new HashMap<>();
		int[] ids = index.getFileIds(group);
		if (ids != null)
		{
			for (int id : ids)
			{
				byte[] bytes = index.loadData(group, id);
				if (bytes != null)
				{
					out.put(id, bytes);
				}
			}
		}
		return out;
	}

	/** Reads every object a region places that is not known yet, in one go. */
	private void loadObjects(List<Location> locations)
	{
		Set<Integer> missing = new TreeSet<>();
		for (Location location : locations)
		{
			if (!objects.containsKey(location.getId()))
			{
				missing.add(location.getId());
			}
		}
		if (missing.isEmpty())
		{
			return;
		}
		Map<Integer, byte[]> data = reader.read(client ->
		{
			Map<Integer, byte[]> out = new HashMap<>();
			for (int id : missing)
			{
				out.put(id, client.getIndexConfig().loadData(OBJECTS, id));
			}
			return out;
		});
		data.forEach((id, bytes) ->
		{
			objectCrcs.put(id, crcOf(bytes));
			objects.put(id, decodeObject(id, bytes));
		});
	}

	private static ObjectDefinition decodeObject(int id, byte[] bytes)
	{
		if (bytes == null)
		{
			ObjectDefinition none = new ObjectDefinition();
			none.setId(id);
			return none;
		}
		return new ObjectLoader().load(id, bytes);
	}

	@Override
	public UnderlayDefinition underlay(int id)
	{
		loadTables();
		return underlays.computeIfAbsent(id, k -> new UnderlayLoader().load(k, config(UNDERLAYS, k)));
	}

	@Override
	public OverlayDefinition overlay(int id)
	{
		loadTables();
		return overlays.computeIfAbsent(id, k -> new OverlayLoader().load(k, config(OVERLAYS, k)));
	}

	@Override
	public ObjectDefinition object(int id)
	{
		ObjectDefinition known = objects.get(id);
		if (known != null)
		{
			return known;
		}
		byte[] bytes = config(OBJECTS, id);
		objectCrcs.put(id, crcOf(bytes));
		ObjectDefinition object = decodeObject(id, bytes);
		objects.put(id, object);
		return object;
	}

	@Override
	public int textureColor(int textureId)
	{
		loadTables();
		return textures.computeIfAbsent(textureId, k -> textureColor(reader.read(client ->
		{
			IndexDataBase index = client.getIndex(TEXTURES);
			return index == null ? null : index.loadData(0, k);
		})));
	}

	/** A texture's average color: it starts with its sprite's id, then the color. */
	private static int textureColor(byte[] data)
	{
		return data == null || data.length < 4 ? 0 : (data[2] & 0xFF) << 8 | data[3] & 0xFF;
	}

	@Override
	public MapSprite mapScene(int index)
	{
		loadTables();
		MapSprite[] scenes = mapScenes;
		return scenes != null && index >= 0 && index < scenes.length ? scenes[index] : null;
	}

	@Override
	public MapSprite areaIcon(int areaId)
	{
		loadTables();
		return areaIcons.computeIfAbsent(areaId, k ->
		{
			byte[] data = config(AREAS, k);
			if (data == null)
			{
				return Optional.empty();
			}
			AreaDefinition area = new AreaLoader().load(data, k);
			if (area.spriteId < 0)
			{
				return Optional.empty();
			}
			MapSprite[] frames = reader.read(client -> sprites(client, area.spriteId));
			if (frames == null)
			{
				// not loaded yet: nothing is kept, so it is asked again
				throw new CacheNotReadyException();
			}
			return frames.length == 0 ? Optional.empty() : Optional.ofNullable(frames[0]);
		}).orElse(null);
	}

	/**
	 * For each icon category the game has, the lowest map area with an icon, whose picture the
	 * Filters list shows for the category.
	 */
	Map<Integer, Integer> categoryIcons()
	{
		loadTables();
		Map<Integer, Integer> out = new java.util.TreeMap<>();
		areaCategories.forEach((id, category) ->
		{
			Optional<MapSprite> icon = areaIcons.get(id);
			if (category >= 0 && icon != null && icon.isPresent())
			{
				out.merge(category, id, Math::min);
			}
		});
		return out;
	}

	/** The map links if they have been read, without waiting for the client: for input threads. */
	Map<Integer, Integer> knownMapLinks()
	{
		Map<Integer, Integer> links = mapLinks;
		return links == null ? java.util.Collections.emptyMap() : links;
	}

	@Override
	public Map<Integer, Integer> mapLinks()
	{
		loadTables();
		Map<Integer, Integer> links = mapLinks;
		if (links == null)
		{
			byte[] script = reader.read(client -> client.getIndexScripts().loadData(MapLinks.SCRIPT, 0));
			links = DungeonLinks.withExtra(Prifddinas.moveLinks(MapLinks.decode(script)));
			// kept once read; a script the client has not got yet is asked for again
			mapLinks = script == null ? null : links;
		}
		return links;
	}

	@Override
	public int categoryArea(int category)
	{
		Map<Integer, Integer> icons = categoryAreas;
		if (icons == null)
		{
			icons = categoryIcons();
			// kept only once the game's tables gave something, so an early ask is asked again
			categoryAreas = icons.isEmpty() ? null : icons;
		}
		return icons.getOrDefault(category, -1);
	}

	/** Every region the game has on either map, by region id. One wait for the client thread. */
	List<Integer> regionIds()
	{
		return reader.read(client ->
		{
			List<Integer> out = new ArrayList<>();
			IndexDataBase maps = client.getIndex(MAPS);
			if (maps == null)
			{
				return out;
			}
			for (MapArea area : MapArea.values())
			{
				for (int rx = area.minRegionX; rx <= area.maxRegionX; rx++)
				{
					for (int ry = area.minRegionY; ry <= area.maxRegionY; ry++)
					{
						if (maps.getFileIds(rx << 8 | ry) != null)
						{
							out.add(rx << 8 | ry);
						}
					}
				}
			}
			return out;
		});
	}

	/**
	 * Everything a region's picture is drawn from: its own map files and its 8 neighbors' (their edges are blended
	 * in, and their walls and scenery can reach across), every object placed in those 9 regions, and the ground,
	 * texture and map scenery tables. When a game update changes any of it, the saved picture no longer matches.
	 */
	@Override
	public long regionHash(int regionX, int regionY)
	{
		if (region(regionX, regionY) == null || !regionCrcs.containsKey(regionX << 8 | regionY))
		{
			return 0;
		}
		ByteBuffer data = ByteBuffer.allocate(8);
		CRC32 crc = new CRC32();
		Set<Integer> placed = new TreeSet<>();
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				Region near = region(regionX + dx, regionY + dy);
				Long nearCrc = regionCrcs.get(regionX + dx << 8 | regionY + dy);
				update(crc, data, near == null || nearCrc == null ? -1 : nearCrc);
				if (near != null && Prifddinas.takesCity(regionX + dx, regionY + dy))
				{
					// drawn with Prifddinas's copy in it: a change to the copy changes the picture too
					Long copyCrc = regionCrcs.get(regionX + dx + (Prifddinas.SHIFT_X >> 6) << 8
						| regionY + dy + (Prifddinas.SHIFT_Y >> 6));
					update(crc, data, copyCrc == null ? -1 : copyCrc);
				}
				if (near != null)
				{
					for (Location location : near.getLocations())
					{
						placed.add(location.getId());
					}
				}
			}
		}
		for (int id : placed)
		{
			if (!objectCrcs.containsKey(id))
			{
				object(id);
			}
			update(crc, data, id);
			update(crc, data, objectCrcs.getOrDefault(id, 0L));
		}
		CRC32 tables = new CRC32();
		update(tables, data, tablesCrc);
		update(tables, data, spritesCrc);
		return tables.getValue() << 32 | crc.getValue();
	}

	private static void update(CRC32 crc, ByteBuffer data, long value)
	{
		data.clear();
		data.putLong(value);
		crc.update(data.array());
	}

	private static long crcOf(byte[] bytes)
	{
		if (bytes == null)
		{
			return 0;
		}
		CRC32 crc = new CRC32();
		crc.update(bytes);
		return crc.getValue();
	}

	private static long crcOf(MapSprite[] sprites)
	{
		CRC32 crc = new CRC32();
		ByteBuffer data = ByteBuffer.allocate(8);
		for (MapSprite sprite : sprites)
		{
			if (sprite == null)
			{
				update(crc, data, -1);
				continue;
			}
			update(crc, data, (long) sprite.getWidth() << 32 | sprite.getHeight());
			for (int pixel : sprite.getPixels())
			{
				update(crc, data, pixel);
			}
		}
		return crc.getValue();
	}

	/** One CRC32 over tables of files, in id order. */
	@SafeVarargs
	private static long crc(Map<Integer, byte[]>... tables)
	{
		CRC32 crc = new CRC32();
		for (Map<Integer, byte[]> table : tables)
		{
			new java.util.TreeMap<>(table).forEach((id, bytes) ->
			{
				crc.update(id);
				crc.update(bytes);
			});
		}
		return crc.getValue();
	}

	@Override
	public int areaCategory(int areaId)
	{
		loadTables();
		return areaCategories.getOrDefault(areaId, -1);
	}

	/**
	 * The place names of the game's world map, on the ground floor, each once (several of the game's maps show the
	 * same place). Read in one wait for the client thread; empty while the client has no world map data yet.
	 */
	List<MapLabels.Label> labels()
	{
		loadTables();
		// Last Man Standing's copies of the world name their own places ("Wild Varrock"): not the world's
		List<byte[]> maps = worldMapFiles(COMPOSITE_MAPS, false, true);
		List<MapLabels.Label> labels = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (byte[] map : maps)
		{
			for (WorldMapElements.Element element : WorldMapElements.load(map))
			{
				AreaDefinition area = labelAreas.get(element.getAreaId());
				// Prifddinas's own map repeats the surface's names around it, at the copy's tiles
				if (area == null || element.getPlane() != 0 || Prifddinas.COPY.contains(element.getX(), element.getY())
					|| !seen.add(area.id + "@" + element.getX() + "," + element.getY()))
				{
					continue;
				}
				labels.add(new MapLabels.Label(Arrays.asList(area.name.split("<br>")), element.getX(), element.getY(),
					area.textScale, new Color(area.textColor)));
			}
		}
		return labels;
	}

	/**
	 * The game's own world maps other than the surface, by name, each at the tile it opens on: "Zanaris",
	 * "Keldagrim", "Ancient Cavern". Read from the world map's details (each file: a short name, the name, then the
	 * packed tile). Read in one wait for the client thread; empty while the client has no world map data yet.
	 */
	Map<String, WorldPoint> gameMaps()
	{
		// file 0 is the surface, the map Advanced Map shows already
		List<byte[]> details = worldMapFiles(MAP_DETAILS, true, false);
		Map<String, WorldPoint> maps = new LinkedHashMap<>();
		for (byte[] data : details)
		{
			try
			{
				InputStream in = new InputStream(data);
				in.readString();
				String name = in.readString();
				int packed = in.readInt();
				maps.putIfAbsent(name, Prifddinas.toSurface(new WorldPoint(packed >> 14 & 0x3FFF, packed & 0x3FFF,
					packed >>> 28)));
			}
			catch (RuntimeException e)
			{
				// a file in a format not known yet is skipped
			}
		}
		return maps;
	}

	/**
	 * The parts of the world each of the game's maps other than the surface shows (a list of squares and zones a map:
	 * "Taverley Underground", "Dwarven Mines"), for telling one dungeon from the next. Read in one wait for the client
	 * thread; empty while the client has no world map data yet.
	 */
	List<List<WorldMapElements.Piece>> mapPieces()
	{
		// file 0 is the surface
		List<byte[]> maps = worldMapFiles(COMPOSITE_MAPS, true, false);
		List<List<WorldMapElements.Piece>> out = new ArrayList<>();
		for (byte[] map : maps)
		{
			try
			{
				out.add(WorldMapElements.pieces(map));
			}
			catch (RuntimeException e)
			{
				// a file in a format not known yet is skipped
			}
		}
		return out;
	}

	/**
	 * The files of one group of the world map index, read in one wait for the client thread; empty while the client
	 * has no world map data yet. Skips the surface (file 0) and the Last Man Standing maps when asked.
	 */
	private List<byte[]> worldMapFiles(int group, boolean skipSurface, boolean skipLastManStanding)
	{
		return reader.read(client ->
		{
			List<byte[]> out = new ArrayList<>();
			IndexDataBase index = client.getIndex(WORLD_MAP);
			int[] files = index == null ? null : index.getFileIds(group);
			if (files != null)
			{
				for (int file : files)
				{
					if (skipSurface && file == 0
						|| skipLastManStanding && isLastManStanding(index.loadData(MAP_DETAILS, file)))
					{
						continue;
					}
					byte[] data = index.loadData(group, file);
					if (data != null)
					{
						out.add(data);
					}
				}
			}
			return out;
		});
	}

	/** True for the details of one of the game's Last Man Standing maps ("LMS Wild Varrock", "LMS Desert Island"). */
	static boolean isLastManStanding(byte[] details)
	{
		if (details == null)
		{
			return false;
		}
		try
		{
			InputStream in = new InputStream(details);
			in.readString();
			return in.readString().startsWith("LMS ");
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	private byte[] config(int group, int id)
	{
		return reader.read(client -> client.getIndexConfig().loadData(group, id));
	}

	/** The frames of a sprite group, placed on their full canvas, or null while the client has not loaded it. */
	private static MapSprite[] sprites(Client client, int group)
	{
		SpritePixels[] frames = client.getSprites(client.getIndexSprites(), group, 0);
		if (frames == null)
		{
			return null;
		}
		List<MapSprite> out = new ArrayList<>(frames.length);
		for (SpritePixels frame : frames)
		{
			out.add(frame == null ? null : MapSprite.of(frame.getMaxWidth(), frame.getMaxHeight(),
				frame.getOffsetX(), frame.getOffsetY(), frame.getWidth(), frame.getHeight(),
				frame.getPixels().clone()));
		}
		return out.toArray(new MapSprite[0]);
	}
}
