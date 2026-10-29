/*
 * Copyright (c) 2018, Morgan Lewis <https://github.com/MESLewis>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.advancedmap;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * Teleport destinations, transcribed from RuneLite's world map plugin ({@code TeleportLocationData} at
 * runelite-parent-1.13.4) without its icon file names. The game's own map has no teleport icons, so these are drawn
 * with Advanced Map's own marks. A spell has its Magic level; everything else names the item or thing that
 * teleports there.
 */
@Getter
enum Teleport
{
	VARROCK(Type.NORMAL_MAGIC, "Varrock", 25, 3213, 3424, 0),
	VARROCK_GE(Type.NORMAL_MAGIC, "Varrock GE", 25, 3164, 3478, 0),
	LUMBRIDGE(Type.NORMAL_MAGIC, "Lumbridge", 31, 3222, 3218, 0),
	FALADOR(Type.NORMAL_MAGIC, "Falador", 37, 2965, 3381, 0),
	CAMELOT(Type.NORMAL_MAGIC, "Camelot", 45, 2757, 3477, 0),
	CAMELOT_BANK(Type.NORMAL_MAGIC, "Camelot Bank", 45, 2726, 3485, 0),
	KOUREND(Type.NORMAL_MAGIC, "Kourend", 48, 1643, 3672, 0),
	ARDOUGNE(Type.NORMAL_MAGIC, "Ardougne", 51, 2664, 3306, 0),
	CIVITAS_ILLA_FORTIS(Type.NORMAL_MAGIC, "Civitas illa Fortis", 54, 1681, 3133, 0),
	WATCHTOWER(Type.NORMAL_MAGIC, "Watchtower", 58, 2547, 3114, 0),
	WATCHTOWER_YANILLE(Type.NORMAL_MAGIC, "Watchtower Yanille", 58, 2584, 3097, 0),
	TROLLHEIM(Type.NORMAL_MAGIC, "Trollheim", 61, 2891, 3678, 0),
	APE_ATOLL(Type.NORMAL_MAGIC, "Ape Atoll", 64, 2796, 2791, 0),
	PADDEWWA(Type.ANCIENT_MAGICKS, "Paddewwa", 54, 3097, 9880, 0),
	SENNTISTEN(Type.ANCIENT_MAGICKS, "Senntisten", 60, 3319, 3336, 0),
	KHARYRLL(Type.ANCIENT_MAGICKS, "Kharyrll", 66, 3494, 3473, 0),
	LASSAR(Type.ANCIENT_MAGICKS, "Lassar", 72, 3002, 3472, 0),
	DAREEYAK(Type.ANCIENT_MAGICKS, "Dareeyak", 78, 2969, 3695, 0),
	CARRALLANGER(Type.ANCIENT_MAGICKS, "Carrallanger", 84, 3157, 3667, 0),
	ANNAKARL(Type.ANCIENT_MAGICKS, "Annakarl", 90, 3288, 3888, 0),
	GHORROCK(Type.ANCIENT_MAGICKS, "Ghorrock", 96, 2977, 3872, 0),
	MOONCLAN(Type.LUNAR_MAGIC, "Moonclan", 69, 2113, 3915, 0),
	OURANIA(Type.LUNAR_MAGIC, "Ourania", 71, 2468, 3246, 0),
	WATERBIRTH(Type.LUNAR_MAGIC, "Waterbirth", 72, 2546, 3755, 0),
	BARBARIAN(Type.LUNAR_MAGIC, "Barbarian", 75, 2543, 3568, 0),
	KHAZARD(Type.LUNAR_MAGIC, "Khazard", 78, 2636, 3167, 0),
	FISHING_GUILD(Type.LUNAR_MAGIC, "Fishing Guild", 85, 2612, 3391, 0),
	CATHERBY(Type.LUNAR_MAGIC, "Catherby", 87, 2802, 3449, 0),
	ICE_PLATEAU(Type.LUNAR_MAGIC, "Ice Plateau", 89, 2973, 3939, 0),
	ARCEUUS_LIBRARY(Type.ARCEUUS_MAGIC, "Arceuus Library", 6, 1632, 3838, 0),
	DRAYNOR_MANOR(Type.ARCEUUS_MAGIC, "Draynor Manor", 17, 3108, 3352, 0),
	BATTLEFRONT(Type.ARCEUUS_MAGIC, "Battlefront", 23, 1349, 3739, 0),
	MIND_ALTAR(Type.ARCEUUS_MAGIC, "Mind Altar", 28, 2979, 3509, 0),
	SALVE_GRAVEYARD(Type.ARCEUUS_MAGIC, "Salve Graveyard", 40, 3433, 3461, 0),
	FENKENSTRAINS_CASTLE(Type.ARCEUUS_MAGIC, "Fenkenstrain's Castle", 48, 3548, 3528, 0),
	WEST_ARDOUGNE(Type.ARCEUUS_MAGIC, "West Ardougne", 61, 2500, 3291, 0),
	HARMONY_ISLAND(Type.ARCEUUS_MAGIC, "Harmony Island", 65, 3797, 2866, 0),
	CEMETERY(Type.ARCEUUS_MAGIC, "Cemetery", 71, 2978, 3763, 0),
	BARROWS(Type.ARCEUUS_MAGIC, "Barrows", 83, 3565, 3315, 0),
	APE_ATOLL_ARCEUUS(Type.ARCEUUS_MAGIC, "Ape Atoll", 90, 2770, 2703, 0),

	// Jewellery
	BARBARIAN_ASSAULT(Type.JEWELLERY, "Games Necklace", "Barbarian Assault", 2520, 3571, 0),
	BURTHORPE_GAMES_ROOM(Type.JEWELLERY, "Games Necklace", "Burthorpe Games Room", 2898, 3554, 0),
	TEARS_OF_GUTHIX(Type.JEWELLERY, "Games Necklace", "Tears of Guthix", 3245, 9500, 0),
	CORPOREAL_BEAST(Type.JEWELLERY, "Games Necklace", "Corporeal Beast", 2967, 4384, 0),
	WINTERTODT_CAMP(Type.JEWELLERY, "Games Necklace", "Wintertodt Camp", 1624, 3938, 0),
	EMIRS_ARENA(Type.JEWELLERY, "Ring of Dueling", "Emir's Arena", 3315, 3235, 0),
	FEROX_ENCLAVE(Type.JEWELLERY, "Ring of Dueling", "Ferox Enclave", 3151, 3636, 0),
	CASTLE_WARS(Type.JEWELLERY, "Ring of Dueling", "Castle Wars", 2441, 3091, 0),
	FORTIS_COLOSSEUM(Type.JEWELLERY, "Ring of Dueling", "Fortis Colosseum", 1793, 3107, 0),
	WARRIORS_GUILD(Type.JEWELLERY, "Combat Bracelet", "Warriors' Guild", 2883, 3549, 0),
	CHAMPIONS_GUILD(Type.JEWELLERY, "Combat Bracelet", "Champions' Guild", 3189, 3368, 0),
	EDGEVILLE_MONASTERY(Type.JEWELLERY, "Combat Bracelet", "Edgeville Monastery", 3053, 3487, 0),
	RANGING_GUILD(Type.JEWELLERY, "Combat Bracelet", "Ranging Guild", 2654, 3441, 0),
	FISHING_GUILD_NECK(Type.JEWELLERY, "Skills Necklace", "Fishing Guild", 2613, 3390, 0),
	MINING_GUILD(Type.JEWELLERY, "Skills Necklace", "Mining Guild", 3049, 9762, 0),
	CRAFTING_GUILD(Type.JEWELLERY, "Skills Necklace", "Crafting Guild", 2934, 3294, 0),
	COOKING_GUILD(Type.JEWELLERY, "Skills Necklace", "Cooking Guild", 3145, 3438, 0),
	WOODCUTTING_GUILD(Type.JEWELLERY, "Skills Necklace", "Woodcutting Guild", 1662, 3505, 0),
	FARMING_GUILD(Type.JEWELLERY, "Skills Necklace", "Farming Guild", 1249, 3717, 0),
	EDGEVILLE(Type.JEWELLERY, "Amulet of Glory", "Edgeville", 3087, 3496, 0),
	KARAMJA(Type.JEWELLERY, "Amulet of Glory", "Karamja", 2918, 3176, 0),
	DRAYNOR_VILLAGE(Type.JEWELLERY, "Amulet of Glory", "Draynor Village", 3105, 3251, 0),
	AL_KHARID(Type.JEWELLERY, "Amulet of Glory", "Al-Kharid", 3293, 3163, 0),
	MISCELLANIA(Type.JEWELLERY, "Ring of Wealth", "Miscellania", 2535, 3862, 0),
	GRAND_EXCHANGE(Type.JEWELLERY, "Ring of Wealth", "Grand Exchange", 3162, 3480, 0),
	FALADOR_PARK(Type.JEWELLERY, "Ring of Wealth", "Falador Park", 2995, 3375, 0),
	DONDAKAN(Type.JEWELLERY, "Ring of Wealth", "Dondakan", 2831, 10165, 0),
	SLAYER_TOWER(Type.JEWELLERY, "Slayer Ring", "Slayer Tower", 3423, 3536, 0),
	FREMENNIK_SLAYER_DUNGEON(Type.JEWELLERY, "Slayer Ring", "Fremennik Slayer Dungeon", 2800, 9998, 0),
	FREMENNIK_SLAYER_DUNGEON_OUTSIDE(Type.JEWELLERY, "Slayer Ring", "Fremennik Slayer Dungeon (inside)", 2800, 3615, 0),
	TARNS_LAIR(Type.JEWELLERY, "Slayer Ring", "Tarn's Lair", 3187, 4601, 0),
	STRONGHOLD_SLAYER_CAVE(Type.JEWELLERY, "Slayer Ring", "Stronghold Slayer Cave", 2433, 3421, 0),
	DARK_BEASTS(Type.JEWELLERY, "Slayer Ring", "Dark Beasts", 2028, 4638, 0),
	WYRMSCRAIG_CAVERN(Type.JEWELLERY, "Slayer Ring", "Wyrmscraig Cavern", 2581 + 64 * 3, 8633, 0),
	WYRMSCRAIG_CAVERN_OUTSIDE(Type.JEWELLERY, "Slayer Ring", "Wyrmscraig Cavern (inside)", 2533, 2206, 0),
	DIGSITE(Type.JEWELLERY, "Digsite Pendant", "Digsite", 3339, 3445, 0),
	HOUSE_ON_THE_HILL(Type.JEWELLERY, "Digsite Pendant", "House on the Hill", 3763, 3869, 0),
	LITHKREN(Type.JEWELLERY, "Digsite Pendant", "Lithkren", 3547, 10456, 0),
	WIZARDS_TOWER(Type.JEWELLERY, "Necklace of Passage", "Wizards' Tower", 3114, 3181, 0),
	JORRALS_OUTPOST(Type.JEWELLERY, "Necklace of Passage", "Jorral's Outpost", 2431, 3348, 0),
	DESERT_EAGLE_STATION(Type.JEWELLERY, "Necklace of Passage", "Desert eagle station of the eagle transport system",
		3406, 3157, 0),
	WYRMSCRAIG(Type.JEWELLERY, "Necklace of Passage", "Wyrmscraig", 2591, 2221, 0),
	CHAOS_TEMPLE(Type.JEWELLERY, "Burning Amulet", "Chaos Temple (lvl 15)", 3234, 3637, 0),
	BANDIT_CAMP(Type.JEWELLERY, "Burning Amulet", "Bandit Camp (lvl 17)", 3038, 3651, 0),
	LAVA_MAZE(Type.JEWELLERY, "Burning Amulet", "Lava Maze (lvl 41)", 3028, 3840, 0),
	NORTH_ALDARIN(Type.JEWELLERY, "Pendant of Ates", "North Aldarin", 1425, 2995, 0),
	DARKFROST(Type.JEWELLERY, "Pendant of Ates", "The Darkfrost", 1490, 3284, 0),
	RALOS_RISE(Type.JEWELLERY, "Pendant of Ates", "Ralos' Rise", 1459, 3137, 0),
	TWILIGHT_TEMPLE(Type.JEWELLERY, "Pendant of Ates", "Twilight Temple", 1666, 3223, 0),
	KASTORI(Type.JEWELLERY, "Pendant of Ates", "Kastori", 1366, 3086, 0),
	NEMUS_RETREAT(Type.JEWELLERY, "Pendant of Ates", "Nemus Retreat", 1364, 3277, 0),
	BRYOPHYTA_LAIR(Type.JEWELLERY, "Giantsoul Amulet", "Bryophyta's Lair", 3174, 9898, 0),
	OBOR_LAIR(Type.JEWELLERY, "Giantsoul Amulet", "Obor's Lair", 3096, 9833, 0),
	BRANDA_AND_ELDRIC_LAIR(Type.JEWELLERY, "Giantsoul Amulet", "Branda and Eldric's Lair", 2952, 9574, 0),
	COWBELL_AMULET(Type.JEWELLERY, "Cowbell Amulet", "Cow field", 3259, 3276, 0),
	PANDEMONIUM(Type.JEWELLERY, "Sailors' Amulet", "The Pandemonium", 3060, 2974, 0),
	PORT_ROBERTS(Type.JEWELLERY, "Sailors' Amulet", "Port Roberts", 1889, 3292, 0),
	RED_ROCK(Type.JEWELLERY, "Sailors' Amulet", "Red Rock", 2797, 2521, 0),
	DEEPFIN_POINT(Type.JEWELLERY, "Sailors' Amulet", "Deepfin Point", 1943, 2758, 0),

	// Misc
	XERICS_LOOKOUT(Type.OTHER, "Xeric's Talisman", "Xeric's Lookout", 1576, 3528, 0),
	XERICS_GLADE(Type.OTHER, "Xeric's Talisman", "Xeric's Glade", 1754, 3564, 0),
	XERICS_INFERNO(Type.OTHER, "Xeric's Talisman", "Xeric's Inferno", 1504, 3819, 0),
	XERICS_HEART(Type.OTHER, "Xeric's Talisman", "Xeric's Heart", 1641, 3670, 0),
	XERICS_HONOUR(Type.OTHER, "Xeric's Talisman", "Xeric's Honour", 1254, 3559, 0),
	STRONGHOLD_OF_SECURITY(Type.OTHER, "Skull Sceptre", "Stronghold of Security", 3081, 3421, 0),
	MYTHS_GUILD(Type.OTHER, "Mythical Cape", "Myth's Guild", 2458, 2851, 0),
	ECTOFUNTUS(Type.OTHER, "Ectophial", "Ectofuntus", 3660, 3522, 0),
	CHAMPIONS_GUILD_CHRONICLE(Type.OTHER, "Chronicle", "Champions' Guild", 3202, 3357, 0),
	GRAND_TREE(Type.OTHER, "Royal Seed Pod", "Grand Tree", 2465, 3495, 0),
	RELLEKKKA_LYRE(Type.OTHER, "Enchanted Lyre", "Rellekka", 2664, 3643, 0),
	WATERBIRTH_ISLAND_LYRE(Type.OTHER, "Enchanted Lyre", "Waterbirth Island", 2550, 3756, 0),
	NEITIZNOT_LYRE(Type.OTHER, "Enchanted Lyre", "Neitiznot", 2336, 3801, 0),
	JATIZSO_LYRE(Type.OTHER, "Enchanted Lyre", "Jatizso", 2409, 3809, 0),
	WEISS_ICY_BASALT(Type.OTHER, "Icy Basalt", "Weiss", 2846, 3940, 0),
	TROLL_STRONGHOLD_STONY_BASALT(Type.OTHER, "Stony Basalt", "Troll Stronghold (with 73 Agility)", 2838, 3693, 0),
	TROLL_STRONGHOLD_STONY_BASALT_OUTSIDE(Type.OTHER, "Stony Basalt", "Troll Stronghold", 2844, 3693, 0),
	KHAREDSTS_MEMOIRS_HOSIDIUS(Type.OTHER, "Kharedst's Memoirs", "Lunch by the Lancalliums (Hosidius)", 1713, 3612, 0),
	KHAREDSTS_MEMOIRS_PISCARILIUS(Type.OTHER, "Kharedst's Memoirs", "The Fisher's Flute (Piscarilius)", 1802, 3748, 0),
	KHAREDSTS_MEMOIRS_SHAYZIEN(Type.OTHER, "Kharedst's Memoirs", "History and Hearsay (Shayzien)", 1478, 3576, 0),
	KHAREDSTS_MEMOIRS_LOVAKENGJ(Type.OTHER, "Kharedst's Memoirs", "Jewellery of Jubilation (Lovakengj)", 1544, 3762, 0),
	KHAREDSTS_MEMOIRS_ARCEUUS(Type.OTHER, "Kharedst's Memoirs", "A Dark Disposition (Arceuus)", 1680, 3746, 0),
	PHARAOHS_SCEPTRE_JALSAVRAH(Type.OTHER, "Pharaoh's Sceptre", "Jalsavrah (Pyramid Plunder)", 3288, 2795, 0),
	PHARAOHS_SCEPTRE_JALEUSTROPHOS(Type.OTHER, "Pharaoh's Sceptre", "Jaleustrophos (Agility Pyramid)", 3341, 2827, 0),
	PHARAOHS_SCEPTRE_JALDRAOCHT(Type.OTHER, "Pharaoh's Sceptre", "Jaldraocht (Desert Treasure Pyramid)", 3232, 2897, 0),
	PHARAOHS_SCEPTRE_JALTEVAS(Type.OTHER, "Pharaoh's Sceptre", "Jaltevas (Necropolis Obelisk)", 3313, 2720, 0),
	CAMULET_TEMPLE(Type.OTHER, "Camulet", "Enakhra's Temple", 3190, 2923, 0),
	TELEPORT_CRYSTAL_LLETYA(Type.OTHER, "Teleport crystal", "Lletya", 2330, 3172, 0),
	TELEPORT_CRYSTAL_PRIFDDINAS(Type.OTHER, "Teleport crystal", "Prifddinas", 3264, 6065, 0),
	DRAKANS_MEDALLION_VER_SINHAZA(Type.OTHER, "Drakan's medallion", "Ver Sinhaza", 3649, 3230, 0),
	DRAKANS_MEDALLION_DARKMEYER(Type.OTHER, "Drakan's medallion", "Darkmeyer", 3592, 3337, 0),
	DRAKANS_MEDALLION_SLEPE(Type.OTHER, "Drakan's medallion", "Slepe", 3808, 9700, 0),
	DRAKANS_MEDALLION_CASTLE_DRAKAN(Type.OTHER, "Drakan's medallion", "Castle Drakan", 3557, 3358, 0),
	RING_OF_THE_ELEMENTS_AIR(Type.OTHER, "Ring of the elements", "Air Altar", 2981, 3276, 0),
	RING_OF_THE_ELEMENTS_WATER(Type.OTHER, "Ring of the elements", "Water Altar", 3166, 3158, 0),
	RING_OF_THE_ELEMENTS_EARTH(Type.OTHER, "Ring of the elements", "Earth Altar", 3288, 3468, 0),
	RING_OF_THE_ELEMENTS_FIRE(Type.OTHER, "Ring of the elements", "Fire Altar", 3315, 3278, 0),
	RING_OF_SHADOWS_LASSAR_UNDERCITY(Type.OTHER, "Ring of shadows", "Lassar Undercity", 2588, 6435, 0),
	RING_OF_SHADOWS_THE_STRANGLEWOOD(Type.OTHER, "Ring of shadows", "The Stranglewood", 1175, 3422, 0),

	// Wilderness
	OBELISK_13(Type.OTHER, "Obelisk", "13", 3156, 3620, 0),
	OBELISK_19(Type.OTHER, "Obelisk", "19", 3227, 3667, 0),
	OBELISK_27(Type.OTHER, "Obelisk", "27", 3035, 3732, 0),
	OBELISK_35(Type.OTHER, "Obelisk", "35", 3106, 3794, 0),
	OBELISK_44(Type.OTHER, "Obelisk", "44", 2980, 3866, 0),
	OBELISK_50(Type.OTHER, "Obelisk", "50", 3307, 3916, 0),
	WILDERNESS_CRABS_TELEPORT(Type.OTHER, "Wilderness crabs teleport", 3348, 3783, 0),
	CANOE_WILDERNESS(Type.OTHER, "Canoe (No departure)", "35", 3141, 3796, 0),

	// Achievement Diary and Combat Achievements
	ARDOUGNE_CLOAK_MONASTERY(Type.OTHER, "Ardougne Cloak", "Monastery", 2606, 3222, 0),
	ARDOUGNE_CLOAK_FARM(Type.OTHER, "Ardougne Cloak", "Farm", 2673, 3375, 0),
	EXPLORERS_RING(Type.OTHER, "Explorer's Ring", 3052, 3292, 0),
	KARAMJA_GLOVES_GEM_MINE(Type.OTHER, "Karamja Gloves", "Gem Mine (Underground)", 2827, 2997, 0),
	KARAMJA_GLOVES_DURADEL(Type.OTHER, "Karamja Gloves", "Duradel", 2870, 2981, 0),
	DESERT_AMULET_NARDAH(Type.OTHER, "Desert Amulet", "Nardah", 3425, 2928, 0),
	DESERT_AMULKT_KALPHITE_CAVE(Type.OTHER, "Desert Amulet", "Kalphite Cave", 3322, 3122, 0),
	MORYTANIA_LEGS_SLIME_PIT(Type.OTHER, "Morytania Legs", "Slime Pit (Underground)", 3654, 3516, 0),
	MORYTANIA_LEGS_BURGH_DE_ROTT(Type.OTHER, "Morytania Legs", "Burgh de Rott", 3482, 3231, 0),
	FREMENNIK_SEA_BOOTS(Type.OTHER, "Fremennik Sea Boots", 2640, 3675, 0),
	KANDARIN_HEADGEAR(Type.OTHER, "Kandarin Headgear", 2729, 3411, 0),
	WILDERNESS_SWORD(Type.OTHER, "Wilderness Sword", 3377, 3891, 0),
	WESTERN_BANNER(Type.OTHER, "Western Banner", 2329, 3685, 0),
	RADAS_BLESSING_MOUNT_KARUULM(Type.OTHER, "Rada's Blessing", 1311, 3795, 0),
	RADAS_BLESSING_WOODLANG(Type.OTHER, "Rada's Blessing", 1553, 3454, 0),
	GHOMMALS_HILT_GWD(Type.OTHER, "Ghommal's Hilt", "God Wars Dungeon", 2898, 3709, 0),
	GHOMMALS_HILT_MORULREK(Type.OTHER, "Ghommal's Hilt", "Mor Ul Rek", 2554, 5130, 0),

	// Scrolls
	DIGSITE_SCROLL(Type.SCROLL, "Digsite Teleport", 3324, 3412, 0),
	IORWERTH_CAMP_SCROLL(Type.SCROLL, "Iorwerth Camp Teleport", 2193, 3257, 0),
	FELDIP_HILLS_SCROLL(Type.SCROLL, "Feldip Hills Teleport", 2542, 2925, 0),
	LUMBERYARD_SCROLL(Type.SCROLL, "Lumberyard Teleport", 3303, 3487, 0),
	LUNAR_ISLE_SCROLL(Type.SCROLL, "Lunar Isle Teleport", 2093, 3912, 0),
	MORTTON_SCROLL(Type.SCROLL, "Mort'ton", 3489, 3288, 0),
	MOS_LEHARMLESS_SCROLL(Type.SCROLL, "Mos Le'Harmless Teleport", 3701, 2996, 0),
	NARDAH_SCROLL(Type.SCROLL, "Nardah Teleport", 3421, 2917, 0),
	PEST_CONTROL_SCROLL(Type.SCROLL, "Pest Control Teleport", 2657, 2660, 0),
	PISCATORIS_SCROLL(Type.SCROLL, "Piscatoris Teleport", 2339, 3648, 0),
	TAI_BWO_WANNAI_SCROLL(Type.SCROLL, "Tai Bwo Wannai Teleport", 2788, 3066, 0),
	ZULANDRA_SCROLL(Type.SCROLL, "Zul-Andra Teleport", 2197, 3056, 0),
	KEY_MASTER_SCROLL(Type.SCROLL, "Key Master Teleport", 2686, 9882, 0),
	REVENANT_CAVE_SCROLL(Type.SCROLL, "Revenant Cave Teleport", 3127, 3833, 0),
	WATSON_SCROLL(Type.SCROLL, "Watson Teleport", 1645, 3579, 0),
	SPIDER_CAVE_SCROLL(Type.SCROLL, "Spider cave teleport", 3658, 3403, 0),
	COLOSSAL_WYRM_SCROLL(Type.SCROLL, "Colossal Wyrm Teleport", 1641, 2921, 0),
	CHASM_TELEPORT_SCROLL(Type.SCROLL, "Chasm of Fire teleport", 1311, 9882, 0),
	ARDEAGLAIS_SCROLL(Type.SCROLL, "Ardeaglais teleport", 2543, 2216, 0),

	// Skillcapes
	ACHIEVEMENT_CAPE_TWO_PINTS(Type.OTHER, "Achievement Cape", "Two-pints", 2574, 3324, 0),
	ACHIEVEMENT_CAPE_JARR(Type.OTHER, "Achievement Cape", "Jarr", 3300, 3121, 0),
	ACHIEVEMENT_CAPE_SIR_REBRAL(Type.OTHER, "Achievement Cape", "Sir Rebral", 2977, 3345, 0),
	ACHIEVEMENT_CAPE_THORODIN(Type.OTHER, "Achievement Cape", "Thorodin", 2659, 3627, 0),
	ACHIEVEMENT_CAPE_FLAX_KEEPER(Type.OTHER, "Achievement Cape", "Flax Keeper", 2744, 3444, 0),
	ACHIEVEMENT_CAPE_JACKIE_THE_FRUIT(Type.OTHER, "Achievement Cape", "Pirate Jackie the Fruit", 2810, 3191, 0),
	ACHIEVEMENT_CAPE_KALEB_PARAMAYA(Type.OTHER, "Achievement Cape", "Kaleb Paramaya", 2861, 2995, 1),
	ACHIEVEMENT_CAPE_JUNGLE_FORESTER(Type.OTHER, "Achievement Cape", "Jungle forester", 2795, 2945, 0),
	ACHIEVEMENT_CAPE_TZHAAR_MEJ(Type.OTHER, "Achievement Cape", "TzHaar-Mej", 2455, 5133, 0),
	ACHIEVEMENT_CAPE_ELISE(Type.OTHER, "Achievement Cape", "Elise", 1647, 3666, 0),
	ACHIEVEMENT_CAPE_HATIUS_COSAINTUS(Type.OTHER, "Achievement Cape", "Hatius Cosaintus", 3239, 3220, 0),
	ACHIEVEMENT_CAPE_LE_SABRE(Type.OTHER, "Achievement Cape", "Le-sabrè", 3465, 3477, 0),
	ACHIEVEMENT_CAPE_TOBY(Type.OTHER, "Achievement Cape", "Toby", 3224, 3414, 0),
	ACHIEVEMENT_CAPE_LESSER_FANATIC(Type.OTHER, "Achievement Cape", "Lesser Fanatic", 3122, 3516, 0),
	ACHIEVEMENT_CAPE_ELDER_GNOME_CHILD(Type.OTHER, "Achievement Cape", "Elder Gnome child", 2467, 3459, 0),
	ACHIEVEMENT_CAPE_TWIGGY_OKORN(Type.OTHER, "Achievement Cape", "Twiggy O'Korn", 3097, 3226, 0),
	CONSTRUCTION_CAPE_RIMMINGTON(Type.OTHER, "Construction Cape", "Rimmington", 2954, 3224, 0),
	CONSTRUCTION_CAPE_TAVERLEY(Type.OTHER, "Construction Cape", "Taverley", 2894, 3465, 0),
	CONSTRUCTION_CAPE_POLLNIVNEACH(Type.OTHER, "Construction Cape", "Pollnivneach", 3340, 3004, 0),
	CONSTRUCTION_CAPE_HOSIDIUS(Type.OTHER, "Construction Cape", "Hosidius", 1744, 3517, 0),
	CONSTRUCTION_CAPE_RELLEKKA(Type.OTHER, "Construction Cape", "Rellekka", 2670, 3632, 0),
	CONSTRUCTION_CAPE_ALDARIN(Type.OTHER, "Construction Cape", "Aldarin", 1422, 2966, 0),
	CONSTRUCTION_CAPE_BRIMHAVEN(Type.OTHER, "Construction Cape", "Brimhaven", 2758, 3178, 0),
	CONSTRUCTION_CAPE_YANILLE(Type.OTHER, "Construction Cape", "Yanille", 2544, 3095, 0),
	CONSTRUCTION_CAPE_PRIFDDINAS(Type.OTHER, "Construction Cape", "Prifddinas", 3239, 6076, 0),
	CRAFTING_CAPE(Type.OTHER, "Crafting Cape", 2931, 3286, 0),
	FARMING_CAPE(Type.OTHER, "Farming Cape", 1248, 3725, 0),
	FISHING_CAPE_FISHING_GUILD(Type.OTHER, "Fishing Cape", "Fishing Guild", 2604, 3401, 0),
	FISHING_CAPE_OTTOS_GROTTO(Type.OTHER, "Fishing Cape", "Otto's Grotto", 2504, 3484, 0),
	HUNTER_CAPE_CARNIVOROUS(Type.OTHER, "Hunter Cape", "Carnivorous Chinchompa Area", 2556, 2916, 0),
	HUNTER_CAPE_BLACK(Type.OTHER, "Hunter Cape", "Black Chinchompa Area", 3144, 3772, 0),
	HUNTER_CAPE_HUNTER_GUILD(Type.OTHER, "Hunter Cape", "Hunter Guild", 1558, 3046, 0),
	SAILING_CAPE(Type.OTHER, "Sailing Cape", "The Pandemonium", 3048, 2972, 0),
	STRENGTH_CAPE(Type.OTHER, "Strength Cape", 2865, 3546, 0),
	QUEST_CAPE(Type.OTHER, "Quest Cape", 2729, 3348, 0),
	MUSIC_CAPE(Type.OTHER, "Music Cape", "Falo the Bard", 2689, 3547, 0);

	enum Type
	{
		NORMAL_MAGIC("Standard spellbook"),
		ANCIENT_MAGICKS("Ancient Magicks"),
		LUNAR_MAGIC("Lunar spellbook"),
		ARCEUUS_MAGIC("Arceuus spellbook"),
		JEWELLERY("Jewellery"),
		SCROLL("Teleport scroll"),
		OTHER("Item or object");

		final String label;

		Type(String label)
		{
			this.label = label;
		}

		boolean isSpell()
		{
			return ordinal() <= ARCEUUS_MAGIC.ordinal();
		}
	}

	private final Type type;
	/** The item or object that teleports here, or null for a spell. */
	private final String item;
	private final String destination;
	/** The Magic level a spell needs, or 0. */
	private final int magicLevel;
	private final WorldPoint location;

	Teleport(Type type, String destination, int magicLevel, int x, int y, int plane)
	{
		this(type, null, destination, magicLevel, new WorldPoint(x, y, plane));
	}

	Teleport(Type type, String item, String destination, int x, int y, int plane)
	{
		this(type, item, destination, 0, new WorldPoint(x, y, plane));
	}

	Teleport(Type type, String item, int x, int y, int plane)
	{
		this(type, item, null, 0, new WorldPoint(x, y, plane));
	}

	Teleport(Type type, String item, String destination, int magicLevel, WorldPoint location)
	{
		this.type = type;
		this.item = item;
		this.destination = destination;
		this.magicLevel = magicLevel;
		this.location = location;
	}

	/** What the info box calls it: "Varrock Teleport", "Games Necklace: Barbarian Assault". */
	String title()
	{
		if (item == null)
		{
			return destination + " Teleport";
		}
		return destination == null ? item : item + ": " + destination;
	}
}
