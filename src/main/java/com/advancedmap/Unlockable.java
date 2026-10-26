/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Quest;

/**
 * The banks and passages a player has to build or unlock, with what they need, by the object the game switches
 * ({@link Unlock}). Whether the player has unlocked one comes from the game; what it needs is written here, checked
 * on the Old School RuneScape Wiki on 2026-10-26 ("Buildable bank chests", "Lumbridge Castle", "Corsair Cove", "Ape
 * Atoll bank"). The icon is gray with a red mark until the player has unlocked it.
 */
enum Unlockable
{
	FOSSIL_ISLAND("Bank chest, Fossil Island", 21, 0, Quest.BONE_VOYAGE,
		new String[]{"2 oak planks", "5 bronze nails", "1 iron bar"}, 31427),
	ONYX_CREST("Bank chest, The Onyx Crest", 70, 47, null, Materials.SAILING_CHEST, 58631),
	CHARRED_ISLAND("Bank chest, Charred Island", 70, 60, null, Materials.SAILING_CHEST, 58627),
	WYRMSCRAIG("Bank chest, Wyrmscraig", 48, 62, Quest.FALLEN_FROM_GRACE,
		new String[]{"1 sunstone crystal", "32 mithril nails", "2 lead bars", "8 mahogany planks"}, 62390),
	DEEPFIN_MINE("Bank chest, Deepfin Mine", 70, 67, null, Materials.SAILING_CHEST, 58629, 58630),
	SUNBLEAK_ISLAND("Bank chest, Sunbleak Island", 70, 72, null, Materials.SAILING_CHEST, 58628),
	BUCCANEERS_HAVEN("Bank chest, Buccaneers' Haven", 70, 76, null, Materials.SAILING_CHEST, 58632),
	VAMPYRIUM("Bank chest, Vampyrium", 70, 0, Quest.THE_BLOOD_MOON_RISES, Materials.SAILING_CHEST, 62185),

	CULINAROMANCERS_CHEST("Culinaromancer's Chest", IconGroup.BANK, Quest.RECIPE_FOR_DISASTER,
		"Opens once you start Recipe for Disaster", 12308),
	CORSAIR_COVE("Corsair Cove bank", IconGroup.BANK, Quest.THE_CORSAIR_CURSE, "Opens after The Corsair Curse",
		11394),
	APE_ATOLL("Ape Atoll bank", IconGroup.BANK, Quest.MONKEY_MADNESS_II, "Opens after Monkey Madness II", 28803),
	BURGH_DE_ROTT("Burgh de Rott bank", IconGroup.BANK, Quest.IN_AID_OF_THE_MYREQUE,
		"Built during In Aid of the Myreque", 12759),
	LUMBRIDGE_CELLAR_HOLE("Hole to the Lumbridge Swamp Caves", IconGroup.DUNGEON, Quest.THE_LOST_TRIBE,
		"Opens during The Lost Tribe", 6898);

	private static final Map<Integer, Unlockable> BY_OBJECT = new HashMap<>();

	static
	{
		for (Unlockable unlockable : values())
		{
			for (int object : unlockable.objects)
			{
				BY_OBJECT.put(object, unlockable);
			}
		}
	}

	/** The materials of the bank chests on the Sailing islands. */
	private static final class Materials
	{
		static final String[] SAILING_CHEST = {"1 magic stone", "2 cupronickel bars", "2 adamantite bars",
			"4 ironwood planks"};
	}

	final String label;
	/** The icon category: a bank, or a dungeon entrance. */
	final int category;
	/** True for a bank chest the player builds. */
	final boolean built;
	/** For a built one, the levels it needs; 0 for none. */
	final int construction;
	final int sailing;
	/** A quest it needs (built), or that unlocks it; null for none. */
	final Quest quest;
	/** For a built one, what it is built from, one item each. */
	final String[] materials;
	/** For one a quest unlocks, how. */
	final String note;
	private final int[] objects;

	/** A bank chest the player builds with a hammer and saw. */
	Unlockable(String label, int construction, int sailing, Quest quest, String[] materials, int... objects)
	{
		this.label = label;
		this.category = IconGroup.BANK;
		this.built = true;
		this.construction = construction;
		this.sailing = sailing;
		this.quest = quest;
		this.materials = materials;
		this.note = "Built by the player with a hammer and saw";
		this.objects = objects;
	}

	/** A bank or passage a quest opens. */
	Unlockable(String label, int category, Quest quest, String note, int... objects)
	{
		this.label = label;
		this.category = category;
		this.built = false;
		this.construction = 0;
		this.sailing = 0;
		this.quest = quest;
		this.materials = new String[0];
		this.note = note;
		this.objects = objects;
	}

	/** The unlockable an object of the game's map is, or null. */
	static Unlockable of(int objectId)
	{
		return BY_OBJECT.get(objectId);
	}
}
