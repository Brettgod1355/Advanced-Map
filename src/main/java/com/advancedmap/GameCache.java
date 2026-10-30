/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.SpritePixels;

/** The few calls the map makes on the client's game cache, so a test can stand in a cache of its own. */
interface GameCache
{
	boolean isClientThread();

	IndexDataBase getIndex(int id);

	IndexDataBase getIndexConfig();

	IndexDataBase getIndexScripts();

	IndexDataBase getIndexSprites();

	SpritePixels[] getSprites(IndexDataBase index, int group, int file);

	/** The running client's cache. */
	static GameCache of(Client client)
	{
		return new GameCache()
		{
			@Override
			public boolean isClientThread()
			{
				return client.isClientThread();
			}

			@Override
			public IndexDataBase getIndex(int id)
			{
				return client.getIndex(id);
			}

			@Override
			public IndexDataBase getIndexConfig()
			{
				return client.getIndexConfig();
			}

			@Override
			public IndexDataBase getIndexScripts()
			{
				return client.getIndexScripts();
			}

			@Override
			public IndexDataBase getIndexSprites()
			{
				return client.getIndexSprites();
			}

			@Override
			public SpritePixels[] getSprites(IndexDataBase index, int group, int file)
			{
				return client.getSprites(index, group, file);
			}
		};
	}
}
