# Third-party notices

Advanced Map is an independent project. It is not an official RuneLite product and is not affiliated with
Jagex.

## RuneLite (BSD 2-Clause)

The build and launcher follow the official [example plugin](https://github.com/runelite/example-plugin),
commit `46e14f2c448fab6cbc599df1015b9631dcf1fc52`. RuneLite is a build/runtime dependency and is not bundled
in the plugin JAR.

The map is drawn with code from RuneLite's cache library (`net.runelite:cache`), taken from the
`runelite-parent-1.13.4` release of [runelite/runelite](https://github.com/runelite/runelite) (commit
`ad137adad1cb5052a5fef6e391888a8895d09830`, the published `cache-1.13.4-sources.jar`). The files keep their
original copyright notices and are in `src/main/java/com/advancedmap/cache/`:

| File here | From `cache/src/main/java/net/runelite/cache/` | Changes |
|---|---|---|
| `MapImageRenderer.java` | `MapImageDumper.java` | Draws one region at a time from a `MapSource` instead of a cache folder on disk; the command-line tool, whole-world images, world map labels, inter-map link icons, the render switches and the out-of-bounds print are left out; map scenery comes as ready-made `MapSprite`s; tile shapes are generated under a lock; map icons are left out of the pictures (Advanced Map draws them itself, so they can be clicked and turned off); pictures are see-through where a floor has no tile (ARGB), so a floor can be drawn over the floors below it |
| `Region.java`, `Location.java`, `Position.java`, `HeightCalc.java` | `region/` | Package only |
| `MapDefinition.java`, `LocationsDefinition.java`, `UnderlayDefinition.java`, `OverlayDefinition.java`, `ObjectDefinition.java`, `AreaDefinition.java` | `definitions/` | Package only |
| `MapLoader.java`, `LocationsLoader.java`, `UnderlayLoader.java`, `OverlayLoader.java`, `ObjectLoader.java`, `AreaLoader.java`, `EntityOpsLoader.java` | `definitions/loaders/` | Package only |
| `EntityOpsDefinition.java` | `EntityOpsDefinition.java` | Package only |
| `JagexColor.java` | `models/JagexColor.java` | Package only |
| `InputStream.java` | `io/InputStream.java` | Package only, and no longer extends `java.io.InputStream` |
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

The teleport destinations in `src/main/java/com/advancedmap/Teleport.java` are transcribed from RuneLite's world map
plugin, `runelite-client/src/main/java/net/runelite/client/plugins/worldmap/TeleportLocationData.java` at the same
release (the published `client-1.13.4-sources.jar`): every destination, its kind, item, Magic level and tile, without
the icon file names (Advanced Map draws its own teleport marks). The agility shortcuts' levels come from RuneLite's
`net.runelite.client.game.AgilityShortcut`, used through the client at runtime and not copied.

The rocks at each mining site in `src/main/resources/com/advancedmap/mining-sites.tsv` (167 sites, as "2 Iron", "3
Coal") are transcribed from the same plugin's `MiningSiteLocation.java` at the same release (runelite/runelite
`ad137adad1cb5052a5fef6e391888a8895d09830`): each site's tile and its rock counts, nothing else. That file carries the
license above under "Copyright (c) 2020, dekvall <https://github.com/dekvall>".

The other copied files carry the same license under their own holders, named in each file's header: Adam
<Adam@sigterm.info> (2016-2017 and 2017), Kyle Friz (2015), Abex (2020 and 2026), Christopher Brown
<christoabrown@gmail.com> (2024, `WorldMapElements.java`) and Morgan Lewis <https://github.com/MESLewis> (2018,
`Teleport.java`).

The test-only `logback-test.xml` comes from the example plugin (same license, Adam's notice kept in the file).

## Shortest Path's transport lists (BSD 2-Clause)

`src/main/resources/com/advancedmap/transports.tsv`, where spirit trees, gnome gliders, fairy rings, hot air balloons,
magic carpets, magic mushtrees, quetzals, minecarts, canoes, boats, ships and charter ships go, is derived from
[Shortest Path](https://github.com/Skretzo/shortest-path)'s `src/main/resources/transports/` files of those names, at
its Plugin Hub pin `50f30435f37d1d5c5e2af790925fa7aa70b7840d` (authors on the Plugin Hub: Skretzo, FIrgolitsch,
wvanderp, mpickering). Kept: each origin and destination tile and its name; boarding tiles within 4 tiles of each other
merged into one spot; everything else (items, quests, skills, durations, varbits) left out. Shortest Path's license,
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
the rest of the project under the BSD 2-Clause License. No game artwork or wiki image is included.

## Facts from the Old School RuneScape Wiki

What the buildable bank chests need (Construction and Sailing levels, quests, materials) and which quest opens the
Corsair Cove and Ape Atoll banks, the Culinaromancer's Chest and the hole from Lumbridge Castle's cellar were checked
on the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/) on 2026-10-26 (pages "Buildable bank chests",
"Lumbridge Castle", "Corsair Cove" and "Ape Atoll bank"; the wiki's text is licensed CC BY-NC-SA 3.0). Only those
game facts are written in `Unlockable.java`; no wiki text or image is included, and nothing is fetched while the
plugin runs. Which chests and banks there are, and whether a player has built or opened one, come from the game.

## Behavior references (no code copied)

- Where each map link leads (a dungeon's entrance to the dungeon and back) is read at runtime from the game's own
  client script for it, `worldmap_intermap_op` (script 1705), found in RuneLite's published decompiled scripts
  ([runelite/cs2-scripts](https://github.com/runelite/cs2-scripts), commit `1d418ff`). How a compiled script is laid
  out was learned from RuneLite's cache library (`ScriptLoader`, BSD 2-Clause, release 1.13.4); `MapLinks.java` is
  this project's own decoder, and no code or data from either is included.
- [Shortest Path](https://github.com/Skretzo/shortest-path) (BSD 2-Clause) was also read, at its Plugin Hub version
  1.22.1 (commit `50f30435f37d1d5c5e2af790925fa7aa70b7840d`), to learn which plugin messages it sends and takes, so
  its route can be drawn. Its transport data above is the only part of it included.
- [Extended Render Distance](https://github.com/Conrad33/extended-render-distance) was read to confirm that a
  plugin can read map regions from the client's cache through `Client.getIndex`. Nothing from it is included.
