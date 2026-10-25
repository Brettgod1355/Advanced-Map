/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.AreaDefinition;
import com.advancedmap.cache.AreaLoader;
import com.advancedmap.cache.Location;
import com.advancedmap.cache.LocationsLoader;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.IndexDataBase;
import net.runelite.api.SpritePixels;
import net.runelite.api.gameval.SpriteID;

/**
 * The map data, read from the client's own game cache through the RuneLite API. Nothing is downloaded: every
 * player's client already holds the whole map. In the current cache each map region is one group of the maps
 * index, numbered by its region id, with the terrain in file 0 and the objects in file 1.
 */
class ClientMapSource implements MapSource
{
	private static final int MAPS = 5;
	private static final int TEXTURES = 9;
	private static final int UNDERLAYS = 1;
	private static final int OVERLAYS = 4;
	private static final int OBJECTS = 6;
	private static final int AREAS = 35;
	/** Regions kept decoded; each is read again for its neighbors' edges. */
	private static final int REGIONS_KEPT = 96;

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
	private final Map<Integer, Integer> textures = new ConcurrentHashMap<>();
	private final Map<Integer, Optional<MapSprite>> areaIcons = new ConcurrentHashMap<>();
	private volatile MapSprite[] mapScenes;

	ClientMapSource(CacheReader reader)
	{
		this.reader = reader;
	}

	@Override
	public Region region(int regionX, int regionY)
	{
		if (regionX < 0 || regionY < 0 || regionX > 255 || regionY > 255)
		{
			return null;
		}
		int id = regionX << 8 | regionY;
		Optional<Region> known = regions.get(id);
		if (known != null)
		{
			return known.orElse(null);
		}

		byte[][] files = reader.read(client ->
		{
			IndexDataBase maps = client.getIndex(MAPS);
			if (maps == null)
			{
				return null;
			}
			if (maps.getFileIds(id) == null)
			{
				return new byte[0][];
			}
			return new byte[][]{maps.loadData(id, 0), maps.loadData(id, 1)};
		});
		if (files == null || files.length > 0 && files[0] == null)
		{
			throw new CacheNotReadyException();
		}
		if (files.length == 0)
		{
			regions.put(id, Optional.empty());
			return null;
		}

		Region region = new Region(id);
		region.loadTerrain(new MapLoader().load(regionX, regionY, files[0]));
		if (files[1] != null)
		{
			region.loadLocations(new LocationsLoader().load(regionX, regionY, files[1]));
			loadObjects(region.getLocations());
		}
		regions.put(id, Optional.of(region));
		return region;
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
		data.forEach((id, bytes) -> objects.put(id, decodeObject(id, bytes)));
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
		return underlays.computeIfAbsent(id, k -> new UnderlayLoader().load(k, config(UNDERLAYS, k)));
	}

	@Override
	public OverlayDefinition overlay(int id)
	{
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
		ObjectDefinition object = decodeObject(id, config(OBJECTS, id));
		objects.put(id, object);
		return object;
	}

	@Override
	public int textureColor(int textureId)
	{
		return textures.computeIfAbsent(textureId, k ->
		{
			byte[] data = reader.read(client ->
			{
				IndexDataBase index = client.getIndex(TEXTURES);
				return index == null ? null : index.loadData(0, k);
			});
			// a texture starts with its sprite's id, then its average color
			return data == null || data.length < 4 ? 0 : (data[2] & 0xFF) << 8 | data[3] & 0xFF;
		});
	}

	@Override
	public MapSprite mapScene(int index)
	{
		MapSprite[] scenes = mapScenes;
		if (scenes == null)
		{
			scenes = sprites(SpriteID.MAPSCENE);
			mapScenes = scenes;
		}
		return index >= 0 && index < scenes.length ? scenes[index] : null;
	}

	@Override
	public MapSprite areaIcon(int areaId)
	{
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
			MapSprite[] frames = sprites(area.spriteId);
			return frames.length == 0 ? Optional.empty() : Optional.ofNullable(frames[0]);
		}).orElse(null);
	}

	private byte[] config(int group, int id)
	{
		return reader.read(client -> client.getIndexConfig().loadData(group, id));
	}

	private MapSprite[] sprites(int group)
	{
		return reader.read(client ->
		{
			SpritePixels[] frames = client.getSprites(client.getIndexSprites(), group, 0);
			if (frames == null)
			{
				return new MapSprite[0];
			}
			List<MapSprite> out = new ArrayList<>(frames.length);
			for (SpritePixels frame : frames)
			{
				out.add(frame == null ? null : MapSprite.of(frame.getMaxWidth(), frame.getMaxHeight(),
					frame.getOffsetX(), frame.getOffsetY(), frame.getWidth(), frame.getHeight(),
					frame.getPixels().clone()));
			}
			return out.toArray(new MapSprite[0]);
		});
	}
}
