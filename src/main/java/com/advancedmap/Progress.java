/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.DBTableID;

/**
 * What the map needs to know about the player and the game's own names, read on the client thread and kept as an
 * unchanging copy that the overlay and the window read from any thread: skill levels, quest states, each quest's
 * details by its start icon (from the game's quest table), and the names of the icon categories.
 */
@Value
class Progress
{
	/** The game's names of its map icon categories, as on its world map's Key. */
	static final int CATEGORY_NAMES = 1713;
	static final String[] DIFFICULTY = {"Novice", "Intermediate", "Experienced", "Master", "Grandmaster", "Special"};
	static final String[] LENGTH = {"Very short", "Short", "Medium", "Long", "Very long"};

	static final Progress NONE = new Progress(false, new int[Skill.values().length], Collections.emptyMap(),
		Collections.emptyMap(), Collections.emptyMap());

	/** A quest as its start icon shows it. */
	@Value
	static class QuestInfo
	{
		String name;
		QuestState state;
		int difficulty;
		int length;
		boolean members;
		/** Pairs of skill (in {@link Skill} order) and level. */
		int[] requiredStats;
	}

	boolean loggedIn;
	/** Levels by {@link Skill} ordinal, boosts included (shortcuts and spells can be boosted). */
	int[] levels;
	Map<Quest, QuestState> questStates;
	/** Quests by the map area of their start icon. */
	Map<Integer, QuestInfo> questsByIcon;
	Map<Integer, String> categoryNames;

	int level(Skill skill)
	{
		return levels[skill.ordinal()];
	}

	QuestState state(Quest quest)
	{
		return questStates.get(quest);
	}

	String categoryName(int category)
	{
		return categoryNames.get(category);
	}

	/** Reads it all. Client thread. */
	static Progress read(Client client)
	{
		Map<Integer, String> names = new HashMap<>();
		EnumComposition categories = client.getEnum(CATEGORY_NAMES);
		if (categories != null)
		{
			for (int key : categories.getKeys())
			{
				names.put(key, categories.getStringValue(key));
			}
		}
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		int[] levels = new int[Skill.values().length];
		Map<Quest, QuestState> states = new EnumMap<>(Quest.class);
		if (loggedIn)
		{
			for (Skill skill : Skill.values())
			{
				levels[skill.ordinal()] = client.getBoostedSkillLevel(skill);
			}
			for (Quest quest : Quest.values())
			{
				states.put(quest, quest.getState(client));
			}
		}
		Map<Integer, Quest> byRow = new HashMap<>();
		for (Quest quest : Quest.values())
		{
			byRow.put(quest.getId(), quest);
		}
		Map<Integer, QuestInfo> byIcon = new HashMap<>();
		for (int row : client.getDBTableRows(DBTableID.Quest.ID))
		{
			if (field(client, row, DBTableID.Quest.COL_PARENT_QUEST) != null)
			{
				// a part of a bigger quest, sharing its start icon
				continue;
			}
			Object icon = field(client, row, DBTableID.Quest.COL_MAPELEMENT);
			Object name = field(client, row, DBTableID.Quest.COL_DISPLAYNAME);
			if (!(icon instanceof Integer) || !(name instanceof String))
			{
				continue;
			}
			Quest quest = byRow.get(row);
			byIcon.put((Integer) icon, new QuestInfo((String) name, quest == null ? null : states.get(quest),
				number(field(client, row, DBTableID.Quest.COL_DIFFICULTY)),
				number(field(client, row, DBTableID.Quest.COL_LENGTH)),
				number(field(client, row, DBTableID.Quest.COL_MEMBERS)) == 1,
				tuples(client, row, DBTableID.Quest.COL_REQUIREMENT_STATS)));
		}
		return new Progress(loggedIn, levels, states, byIcon, names);
	}

	private static Object field(Client client, int row, int column)
	{
		Object[] values = client.getDBTableField(row, column, 0);
		return values == null || values.length == 0 ? null : values[0];
	}

	private static int number(Object value)
	{
		return value instanceof Integer ? (Integer) value : -1;
	}

	/** Every number in every tuple of a column: a column can hold several (skill, level) pairs. */
	private static int[] tuples(Client client, int row, int column)
	{
		List<Integer> out = new ArrayList<>();
		for (int tuple = 0; tuple < 64; tuple++)
		{
			Object[] values;
			try
			{
				values = client.getDBTableField(row, column, tuple);
			}
			catch (RuntimeException e)
			{
				break;
			}
			if (values == null || values.length == 0)
			{
				break;
			}
			for (Object value : values)
			{
				if (value instanceof Integer)
				{
					out.add((Integer) value);
				}
			}
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}
}
