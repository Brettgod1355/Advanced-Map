/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.Region;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.SpritePixels;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * The saved map's fingerprint of a region: it must change with anything the region's picture is drawn from (its
 * neighbors, the objects placed in them, the map scenery sprites) and stay the same otherwise. The game cache is
 * faked with proxies: test code only, the plugin itself uses no reflection.
 */
public class RegionHashTest
{
	private static final int MAPS = 5;
	private static final int TEXTURES = 9;
	private static final int UNDERLAYS = 1;
	private static final int OBJECTS = 6;
	private static final int TREE = 5;
	private static final int FAR_TREE = 7;

	/** A fake game cache: index, then group, then file. */
	private static final class Cache
	{
		final Map<Integer, Map<Integer, Map<Integer, byte[]>>> indexes = new HashMap<>();
		int[] scenery = {0xFF00FF00};

		void put(int index, int group, int file, byte[] bytes)
		{
			indexes.computeIfAbsent(index, k -> new HashMap<>()).computeIfAbsent(group, k -> new HashMap<>())
				.put(file, bytes);
		}

		IndexDataBase index(int index)
		{
			Map<Integer, Map<Integer, byte[]>> groups = indexes.getOrDefault(index, new HashMap<>());
			return (IndexDataBase) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{IndexDataBase.class}, (proxy, method, args) ->
				{
					switch (method.getName())
					{
						case "getFileIds":
						{
							Map<Integer, byte[]> files = groups.get((Integer) args[0]);
							return files == null ? null : files.keySet().stream().mapToInt(Integer::intValue).toArray();
						}
						case "loadData":
						{
							Map<Integer, byte[]> files = groups.get((Integer) args[0]);
							return files == null ? null : files.get((Integer) args[1]);
						}
						default:
							return false;
					}
				});
		}

		SpritePixels sprite()
		{
			int[] pixels = scenery.clone();
			return (SpritePixels) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{SpritePixels.class}, (proxy, method, args) ->
				{
					switch (method.getName())
					{
						case "getWidth":
						case "getHeight":
						case "getMaxWidth":
						case "getMaxHeight":
							return 1;
						case "getOffsetX":
						case "getOffsetY":
							return 0;
						case "getPixels":
							return pixels;
						default:
							return null;
					}
				});
		}

		ClientMapSource source()
		{
			Client client = (Client) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Client.class},
				(proxy, method, args) ->
				{
					switch (method.getName())
					{
						case "isClientThread":
							return true;
						case "getIndex":
							return index((Integer) args[0]);
						case "getIndexConfig":
							return index(2);
						case "getIndexSprites":
							return index(8);
						case "getSprites":
							return new SpritePixels[]{sprite()};
						default:
							return null;
					}
				});
			return new ClientMapSource(new CacheReader(client));
		}
	}

	/** Every tile one ground type; {@code underlay} picks which, so two regions can differ. */
	private static byte[] terrain(int underlay)
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int z = 0; z < Region.Z; z++)
		{
			for (int i = 0; i < Region.X * Region.Y; i++)
			{
				if (z == 0)
				{
					out.write(0);
					out.write(81 + underlay);
				}
				out.write(0);
				out.write(0);
			}
		}
		return out.toByteArray();
	}

	/** One object of an id at the region's corner. */
	private static byte[] oneObject(int id)
	{
		return new byte[]{(byte) (id + 1), 1, 10 << 2, 0, 0};
	}

	/** Regions 49 to 52 by 50, flat ground, a tree in (51, 50) and another in (52, 50), two columns away. */
	private static Cache world()
	{
		Cache cache = new Cache();
		cache.put(2, UNDERLAYS, 0, new byte[]{1, 0x40, 0x70, 0x20, 0});
		cache.put(2, UNDERLAYS, 1, new byte[]{1, 0x30, 0x60, 0x20, 0});
		cache.put(TEXTURES, 0, 0, new byte[]{0, 0, 0, 0});
		for (int rx = 49; rx <= 52; rx++)
		{
			cache.put(MAPS, rx << 8 | 50, 0, terrain(1));
		}
		cache.put(MAPS, 51 << 8 | 50, 1, oneObject(TREE));
		cache.put(MAPS, 52 << 8 | 50, 1, oneObject(FAR_TREE));
		cache.put(2, OBJECTS, TREE, new byte[]{0});
		cache.put(2, OBJECTS, FAR_TREE, new byte[]{0});
		return cache;
	}

	private static long hash(Consumer<Cache> change)
	{
		Cache cache = world();
		change.accept(cache);
		return cache.source().regionHash(50, 50);
	}

	@Test
	public void theSameDataGivesTheSameFingerprint()
	{
		assertNotEquals(0, hash(cache -> { }));
		assertEquals(hash(cache -> { }), hash(cache -> { }));
	}

	@Test
	public void anObjectChangedInANeighborIsNoticed()
	{
		// the tree stands in (51, 50), next door; a game update gives it a size
		assertNotEquals(hash(cache -> { }), hash(cache -> cache.put(2, OBJECTS, TREE, new byte[]{14, 2, 0})));
	}

	@Test
	public void aNeighborsGroundChangedIsNoticed()
	{
		assertNotEquals(hash(cache -> { }), hash(cache -> cache.put(MAPS, 49 << 8 | 50, 0, terrain(2))));
	}

	@Test
	public void aChangedScenerySpriteIsNoticed()
	{
		assertNotEquals(hash(cache -> { }), hash(cache -> cache.scenery = new int[]{0xFF0000FF}));
	}

	@Test
	public void changesTwoRegionsAwayAreNot()
	{
		assertEquals(hash(cache -> { }), hash(cache -> cache.put(2, OBJECTS, FAR_TREE, new byte[]{14, 2, 0})));
		assertEquals(hash(cache -> { }), hash(cache -> cache.put(MAPS, 52 << 8 | 50, 0, terrain(2))));
	}
}
