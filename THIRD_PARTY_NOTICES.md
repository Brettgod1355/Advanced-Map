# Third-party notices

Advanced Map is an independent project. It is not an official RuneLite product and is not affiliated with
Jagex.

## RuneLite (BSD 2-Clause)

The build and launcher follow the official [example plugin](https://github.com/runelite/example-plugin),
commit `46e14f2c448fab6cbc599df1015b9631dcf1fc52`. RuneLite is a build/runtime dependency and is not bundled
in the plugin JAR.

`src/main/resources/com/advancedmap/places.tsv`, the dungeon and minigame entrances the search finds by name, is the
names and tiles of RuneLite's world map plugin's `DungeonLocation` and `MinigameLocation` lists
(`runelite-client/src/main/java/net/runelite/client/plugins/worldmap/`, release 1.13.4, the same commit as below).

More data from the same RuneLite release (BSD 2-Clause), kept as tables in `src/main/resources/com/advancedmap/`:

- `fishing-hunter.tsv`: the fishing spots and hunter areas of the world map plugin's `FishingSpotLocation` and
  `HunterAreaLocation` (names, tiles, creatures and Hunter levels), with what each spot catches from
  `net.runelite.client.game.FishingSpot`.
- `farming.tsv`: the farming patches of the Time Tracking plugin's `FarmingWorld` (region, patch, varbit, kind), what a
  patch's game value means from its `PatchImplementation` and `Produce` (what grows, its state, stage and tick rate,
  written out once by a tool run against the 1.13.4 client; no RuneLite code is included), and where each farming site
  is from the world map plugin's `FarmingPatchLocation`. How `Farming.java` estimates when a patch is ready follows
  Time Tracking's `FarmingTracker` (`predictPatch`, `getTickTime`), written anew.
- `slayer-tasks.tsv`: the Slayer plugin's `Task` names and the other monsters each covers. `SlayerTask.java` matches
  monsters the way that plugin does (`targetNamePattern`: whole words, the task's name without its last "s"),
  written anew.

The files those four tables are transcribed from carry the license above under their own holders, whose notices
are reproduced here: `places.tsv` from "Copyright (c) 2020, Arman S <https://github.com/Rman887>"
(`DungeonLocation.java`) and "Copyright (c) 2018, Magic fTail" (`MinigameLocation.java`); `fishing-hunter.tsv` from
"Copyright (c) 2020, melky <https://github.com/melkypie>" (`FishingSpotLocation.java`, `HunterAreaLocation.java`)
and "Copyright (c) 2017, Seth <Sethtroll3@gmail.com>" (`FishingSpot.java`); `farming.tsv` from "Copyright (c) 2018,
NotFoxtrot <https://github.com/NotFoxtrot>" and "Copyright (c) 2018 Abex" (`FarmingWorld.java`, `Produce.java`),
"Copyright (c) 2019 Abex" (`PatchImplementation.java`) and "Copyright (c) 2018, Torkel Velure
<https://github.com/TorkelV>" (`FarmingPatchLocation.java`); `slayer-tasks.tsv` from "Copyright (c) 2017, Tyler
<https://github.com/tylerthardy>" and "Copyright (c) 2018, Shaun Dreclin <shaundreclin@gmail.com>" (`Task.java`).

Advanced Map reads, and never changes, what the Time Tracking and Slayer plugins saved for the player, under their
own setting names, and the markers other plugins put on the world map (through `WorldMapPointManager`). RuneLite's
Key Remapping plugin was read (release 1.13.4) to learn how it rewrites a key event it remaps, so that a letter typed
into the map's search while it is on can be recovered (`MapInput.java`); nothing of it is copied.

The map is drawn with code from RuneLite's cache library (`net.runelite:cache`), taken from the
`runelite-parent-1.13.4` release of [runelite/runelite](https://github.com/runelite/runelite) (commit
`ad137adad1cb5052a5fef6e391888a8895d09830`, the published `cache-1.13.4-sources.jar`). The files keep their
original copyright notices and are in `src/main/java/com/advancedmap/cache/`:

| File here | From `cache/src/main/java/net/runelite/cache/` | Changes |
|---|---|---|
| `MapImageRenderer.java` | `MapImageDumper.java` | Draws one region at a time from a `MapSource` instead of a cache folder on disk; the command-line tool, whole-world images, world map labels, inter-map link icons, the render switches and the out-of-bounds print are left out; map scenery comes as ready-made `MapSprite`s; tile shapes are generated under a lock; map icons are left out of the pictures (Advanced Map draws them itself, so they can be clicked and turned off); pictures are see-through where a floor has no tile (ARGB), so a floor can be drawn over the floors below it |
| `Region.java`, `Location.java`, `Position.java`, `HeightCalc.java` | `region/` | Package only; a method nothing here calls removed from `Region.java` and from `Position.java` |
| `MapDefinition.java`, `LocationsDefinition.java`, `UnderlayDefinition.java`, `OverlayDefinition.java`, `ObjectDefinition.java`, `AreaDefinition.java` | `definitions/` | Package only |
| `MapLoader.java`, `LocationsLoader.java`, `UnderlayLoader.java`, `OverlayLoader.java`, `ObjectLoader.java`, `AreaLoader.java`, `EntityOpsLoader.java` | `definitions/loaders/` | Package only; the object, area, underlay and overlay loaders end a record at an opcode they do not know (a game update's) instead of reading on; a method nothing here calls removed from `ObjectLoader.java` |
| `EntityOpsDefinition.java` | `EntityOpsDefinition.java` | Package only |
| `JagexColor.java` | `models/JagexColor.java` | Package only; methods nothing here calls removed |
| `InputStream.java` | `io/InputStream.java` | Package only, no longer extends `java.io.InputStream`, and readers nothing here calls removed |
| `WorldMapElements.java` | `definitions/loaders/WorldMapCompositeLoader.java`, `definitions/loaders/WorldMapDataLoader.java`, `definitions/loaders/WorldMapElementLoader.java`, `definitions/WorldMapCompositeDefinition.java` | Combined into one reader of a composite map file that returns its elements at their world positions; the current format only; the definition classes are left out |

The license text, as it appears with the first holder's notice:

```
Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

The teleport destinations in `src/main/resources/com/advancedmap/teleports.tsv` (read by `Teleport.java`) are
transcribed from RuneLite's world map plugin,
`runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java` at the same release (the published `client-1.13.4-sources.jar`): every destination, its kind, item, Magic level and tile, without
the icon file names (Advanced Map draws its own teleport marks). The agility shortcuts' levels come from RuneLite's
`net.runelite.client.game.AgilityShortcut`, used through the client at runtime and not copied.

The rocks at each mining site in `src/main/resources/com/advancedmap/mining-sites.tsv` (167 sites, as "2 Iron", "3
Coal") are transcribed from the same plugin's `MiningSiteLocation.java` at the same release (runelite/runelite
`ad137adad1cb5052a5fef6e391888a8895d09830`): each site's tile and its rock counts, nothing else. That file carries the
license above under "Copyright (c) 2020, dekvall <https://github.com/dekvall>".

The trees at each rare trees icon in `src/main/resources/com/advancedmap/rare-trees.tsv` (183 tiles, as "Yew tree",
Woodcutting 60) are transcribed from the same plugin's `RareTreeLocation.java` at the same release: each tree's name,
level and tiles, nothing else. That file carries the license above under "Copyright (c) 2018, Spedwards
<https://github.com/Spedwards>".

The names of the transport icons in `src/main/resources/com/advancedmap/transport-points.tsv` (207 icons, as
"Minecart to Keldagrim", and where 77 of them go) are transcribed from the same plugin's
`TransportationPointLocation.java` at the same release: each icon's name, tile and destination, nothing else. That
file carries the license above under "Copyright (c) 2019, Kyle Sergio <https://github.com/ksergio39>", "Copyright (c)
2019, Bryce Altomare <https://github.com/Twinkiel0ver>" and "Copyright (c) 2019, Kyle Stead
<http://github.com/kyle1elyk>".

The other copied files carry the same license under their own holders, named in each file's header: Adam
<Adam@sigterm.info> (2016-2017 and 2017), Abex (2020 and 2026), Christopher Brown <christoabrown@gmail.com> (2024,
`WorldMapElements.java`) and Morgan Lewis <https://github.com/MESLewis> (2018, `teleports.tsv`); `HeightCalc.java`
(Kyle Friz, 2015) is under the MIT license instead, and its header, which carries that license whole, is kept as it
is.

The test-only `logback-test.xml` comes from the example plugin (same license, Adam's notice kept in the file).

## Shortest Path's transport lists (BSD 2-Clause)

`src/main/resources/com/advancedmap/transports.tsv`, where spirit trees, gnome gliders, fairy rings, hot air balloons,
magic carpets, magic mushtrees, quetzals, minecarts, canoes, boats, ships and charter ships go, is derived from
[Shortest Path](https://github.com/Skretzo/shortest-path)'s `src/main/resources/transports/` files of those names, at
its Plugin Hub pin `50f30435f37d1d5c5e2af790925fa7aa70b7840d` (authors on the Plugin Hub: Skretzo, FIrgolitsch,
wvanderp, mpickering). Kept: each origin and destination tile and its name; boarding tiles within 4 tiles of each other
merged into one spot; everything else (items, quests, skills, durations, varbits) left out.
`src/main/resources/com/advancedmap/transport-costs.tsv` takes from the same files at the same pin each trip's fare
(the coins it needs) and what it needs to be taken (quests, skill levels, other items), by origin and destination
tile; durations and varbits are left out. 28 rows of
`src/main/resources/com/advancedmap/dungeon-links.tsv`, where surface dungeon entrances the game's own map does not
link lead, are taken from its `transports.tsv` at the same pin (origin and destination tiles only), and so are the
ways between two underground places (ladders, ropes, stairs, tunnels) in `src/main/resources/com/advancedmap/inner-links.tsv`,
which a dungeon's preview follows to its lower floors (origin and destination tiles only). Shortest Path's license,
as published in that repository (its copyright line is the license template's, unfilled):

```
BSD 2-Clause License

Copyright (c) <YEAR>, <COPYRIGHT HOLDER>
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Multi Lines and Wilderness Lines (BSD 2-Clause)

`src/main/resources/com/advancedmap/multi-combat.tsv`, where combat is multi-way, is taken from two Plugin Hub plugins:
outside the Wilderness, the areas of [Multi Lines](https://github.com/tsbreuer/Multi-Lines) (its
`MultiLinesData.json` on the `geoJSON` branch at `694b258744a4dcb2c9d19b18a7aae38875eb9113`, 2026-06-08: the areas
it marks enabled and not removed); in the Wilderness, the rectangles of
[Wilderness Lines](https://github.com/Nightfirecat/plugin-hub-plugins) (`WILDERNESS_MULTI_AREAS` in
`WildernessLinesPlugin.java` at its Plugin Hub pin `d81bb4c444c3772b1563be8a8fcc2252c84a0a4f`). Only the areas' names
and tiles are kept; no code is included, and nothing is downloaded while the plugin runs. `WildernessLinesPlugin.java`
itself carries the license under its own holders, reproduced here: "Copyright (c) 2018, Woox
<https://github.com/wooxsolo>", "Copyright (c) 2021, Jordan <nightfirecat@protonmail.com>" and "Copyright (c) 2024,
tsbreuer <tsbreuer@gmail.com>". Both repositories publish the same license, with the same copyright line:

```
BSD 2-Clause License

Copyright (c) 2022, Jordan
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

## Gradle wrapper

The wrapper scripts, JAR, and properties come from the RuneLite example plugin, commit
`46e14f2c448fab6cbc599df1015b9631dcf1fc52`. Gradle is licensed under Apache 2.0; see
`licenses/Apache-2.0.txt` and the notices retained in the scripts and wrapper. This development tooling is not
included in the plugin JAR.

## The map itself

Nothing of the map is bundled or downloaded. Advanced Map draws it, while you play, from the game data your own
client has already downloaded from Jagex, the same way the game's world map and RuneLite's own tools read it.
The game and its data are Jagex's; this plugin is not affiliated with or endorsed by Jagex.

## Images

`icon.png` and the sidebar icon are this project's own drawing, made in code (`MapIcon.java`) and licensed with
the rest of the project under the BSD 2-Clause License. So are the pictures a player's markers can have
(`MarkerIcons.java`). No game artwork or wiki image is included.

## Facts from the Old School RuneScape Wiki

What the buildable bank chests need (Construction and Sailing levels, quests, materials) and which quest opens the
Corsair Cove and Ape Atoll banks, the Culinaromancer's Chest and the hole from Lumbridge Castle's cellar were checked
on the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) on 2026-10-26 (pages "Buildable bank chests",
"Lumbridge Castle", "Corsair Cove" and "Ape Atoll bank"; the wiki's text is licensed CC BY-NC-SA 3.0). The
underground tiles of the Tonali Cavern, Shayzien Prison, Scabaras Dungeon, Ogre Enclave and Skavid caves in
`dungeon-links.tsv` are those pages' map coordinates, checked on 2026-10-27. Only those
game facts are written in `Unlockable.java`; no wiki text or image is included, and nothing is fetched while the
plugin runs. Which chests and banks there are, and whether a player has built or opened one, come from the game.

## Data from the Old School RuneScape Wiki (CC BY-NC-SA 3.0)

`src/main/resources/com/advancedmap/shops.tsv` (shops, where they are, what they sell, the price, currency and
stock) and `src/main/resources/com/advancedmap/spawns.tsv` (where monsters and NPCs are, and monsters' combat
levels) are adapted from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/)'s structured data
(its Bucket tables `infobox_shop`, `storeline`, `map`, `locline`, `infobox_monster` and `infobox_npc`), read on
2026-10-27. The wiki's content is licensed under
[CC BY-NC-SA 3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/); these two files are adaptations and are
shared under the same license, credited to the Old School RuneScape Wiki's contributors. Changes: only names,
tiles, prices and levels kept; each shop placed by its page's map, else its owner's spawn; tiles the wiki gives on
one of the game's other world maps (a dungeon that map draws somewhere else, such as the Kalphite Queen's chamber)
moved back to where they are in the world, using that map's squares as the game's cache lists them; pages in the
wiki's "Discontinued content" category (removed from the game, such as the Realm of Memories) left out; a tile on a
floor where the game's own map draws no ground (a platform up a tree, an upstairs room it leaves out) put on the
nearest floor below that has ground, with the floor the wiki gives kept beside it; prices and stock
as the wiki gave them that day (they can be out of date). The Shilo Village cart's fare, and that the Karamja elite
diary makes it free, are from the wiki's "Shilo Village cart system", "Hajedy" and "Vigroy" pages, checked the same day.
Nothing is fetched from the wiki while the plugin runs.

`src/main/resources/com/advancedmap/monsters.tsv` (each searchable monster's hitpoints, max hit, attack styles, whether
it is aggressive or poisonous, Slayer level, the Slayer masters who assign it, what it is weak to, the drops it always
gives and up to three rare ones) is adapted from the same wiki's Bucket tables `infobox_monster` and `dropsline`, read
on 2026-10-28, under the same license and credit. Changes: one line a monster, the versions of a monster merged
(hitpoints and max hit as a range), weakness taken as its lowest defence bonus and its elemental weakness, drops cut
to "Always" and the three rarest at 1/256 or rarer that are not shared tables (clue scrolls, herbs, seeds,
Wilderness loot). The Wiki button beside a monster opens its wiki page in the browser; nothing is fetched.

## Discord and GitHub logos (trademarks, used as link buttons)

The two buttons beside the title of the sidebar tab show the brands' own logos, taken from their official
brand pages on 2026-10-04 and only scaled down, never recolored or redrawn:

- Discord: `discord_white.png` and `discord_blurple.png` are `Discord-Symbol-White.png` and
  `Discord-Symbol-Blurple.png` from the white and color "Symbol" downloads at
  <https://discord.com/branding>, which lists white and Blurple among the logo's colors.
- GitHub: `github_white.png` is `GitHub_Invertocat_White.png` from `GitHub_Logos.zip` at
  <https://brand.github.com/foundations/logo>. The pack has no green file, so `github_green.png` is
  the green Invertocat cut from the same page's color illustration
  (<https://brand.github.com/_next/static/media/logo-04.c5edeefa.png>), with the black around it made
  transparent and its pixels otherwise unchanged; the page allows the mark "in white, black, or in
  few cases grey or green", and its use "as a social button to link to your GitHub profile or project".

Discord and the Discord logo are trademarks of Discord Inc.; GitHub and the Invertocat are
trademarks of GitHub, Inc. They are used only to link to this plugin's own Discord server and GitHub
repository, and imply no endorsement by either company.

## Behavior references (no code copied)

- Where each map link leads (a dungeon's entrance to the dungeon and back) is read at runtime from the game's own
  client script for it, `worldmap_intermap_op` (script 1705), found in RuneLite's published decompiled scripts
  ([runelite/cs2-scripts](https://github.com/runelite/cs2-scripts), commit `1d418ff`); the Wilderness level rules
  `Wilderness.java` re-derives were read from the same scripts' `wilderness_level`. How a compiled script is laid
  out was learned from RuneLite's cache library (`ScriptLoader`, BSD 2-Clause, release 1.13.4); `MapLinks.java` is
  this project's own decoder, and no code or data from either is included.
- [Shortest Path](https://github.com/Skretzo/shortest-path) (BSD 2-Clause) was also read, at its Plugin Hub version
  1.22.1 (commit `50f30435f37d1d5c5e2af790925fa7aa70b7840d`), to learn which plugin messages it sends and takes, so
  its route can be drawn. Its transport data above is the only part of it included.
- [Extended Render Distance](https://github.com/Conrad33/extended-render-distance) was read to confirm that a
  plugin can read map regions from the client's cache through `Client.getIndex`. Nothing from it is included.
