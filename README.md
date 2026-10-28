# Advanced Map

A full-screen world map for RuneLite, drawn from the game's own map data with smooth pan and zoom.

- **Open it** from its sidebar tab or with **Ctrl+M** (change the key in the settings). **Escape**, Ctrl+M or the
  close button close it. "How the map works" in the sidebar tab explains each part of the map.
- **Drag** to move, **scroll** to zoom around the pointer. It opens on your character, on the surface or the
  underground map, whichever you are on; the button at the top switches between them.
- **A box over the game**: the map opens in a box you move by its top or bottom bar and size by its corners, while
  the game goes on around it (a click outside the box is the game's). The square button next to the close button
  fills the game view with it, and back.
- **Pop it out** into a window of its own with the **Pop out** button (or the setting), to keep it beside the game's
  own world map or on another screen. **Over the game** brings it back.
- **Wilderness teleport limits**: zoomed in, red dashed lines show where the Wilderness passes level 20 and level 30,
  above which most teleports stop working. Turn them off with the "Wilderness teleport limits" setting.
- **Search** with the box at the top: places, dungeon and minigame entrances, everything shops sell and monsters,
  matched loosely as the Grand Exchange does ("d scim" and "dragon sim" find the dragon scimitar, "kbd" the King
  Black Dragon). Places come first, the bigger first: "var" lists Varlamore and Varrock before Varrock armour, and
  nicknames work ("ardy", "fally", "zeah"). A place moves the map there; an item lists the shops that sell it and
  the price, nearest first; a monster or NPC marks every spawn and lists them by area, a monster under its
  hitpoints, max hit, weakness, Slayer level and masters, sure and rare drops, and a button to its wiki page. Fairy
  ring codes ("cks"),
  fishing spots by catch ("lobster"), hunter areas by creature ("chinchompa") and kinds of icon ("bank", "anvil",
  "altar": every one, nearest first) are found too, and among equally good matches the nearest to you comes first.
  Shops are found through what they sell; the setting "Search finds shops by name" lists them by name too.
- **Your own markers**: right-click anywhere, "Add marker here", give it a name and pick one of 12 pictures and 9
  colors. Right-click a marker to edit or remove it; markers are found by search too. Markers are shared by every
  account you play on this computer, two at once included (each is its own small file in RuneLite's plugin data
  folder, read again whenever the map opens); turn "Share markers between accounts" off to keep each account's own.
- **Other plugins' markers**: a clue scroll's spot, Quest Helper's next step and other plugins' world map markers
  show on this map as well (RuneLite's own World Map plugin's are left out: Advanced Map has those icons itself).
- **Multi-combat areas**, in the Wilderness and everywhere else, tinted red with a solid edge when zoomed in.
- **Farming patches**: a dot on each patch icon, green ready, yellow growing, red diseased or dead, gray empty; click
  one for each patch's crop and when it is ready. Read from what RuneLite's Time Tracking plugin saved.
- **Slayer task** (off by default): every spawn of your task's monsters marked, with how many are left, from the
  task RuneLite's Slayer plugin keeps.
- **Shops**: click a shop, general stores included, for what it sells and for how much.
- **Fares**: click a charter ship, the Shilo Village cart or another transport for where it goes, the fare and what
  the trip needs (a quest, a level, the Karamja elite diary that makes the cart free).
- **Place names** from the game's own map: kingdoms when zoomed far out, then towns, then smaller places.
- **Prifddinas in Tirannwn**: the game keeps the elven city on a map of its own; Advanced Map draws it inside its
  walls on the main map, with its icons, and puts you, its teleports and the transports that go there (the spirit
  tree, the teleport crystal) there too.
- **Find and follow you**: the round button at the bottom right brings the map back to you and keeps it on you as you
  walk, in the pop-out window too, until you drag the map. When you are out of view, an arrow at the edge points to
  you with how many tiles away; click it to go back ("Arrow to you" turns it off).
- **Dungeon preview**: point at a dungeon's entrance for a look inside before going in: the dungeon's name, a small
  map of where you come out, and every monster inside, down its ladders and ropes to the lower floors too, the
  highest level first ("Dungeon monsters" turns it round). Click the entrance to go in.
- **Icons you can point at**: banks, quests, shops, shortcuts and every other icon of the game's map, plus teleport
  destinations. Rest the mouse on one for its details, or click it to keep them open (a quest's difficulty and the
  levels it needs, a shortcut's Agility level, a spell's Magic level and quest); the setting "Info on hover" makes
  it click only. Agility shortcuts and teleport spells you can't use yet are grayed out with a
  red mark, and finished quests are left out (a setting turns them back on).
- **Banks**: banks the game's map leaves out get an icon too (the Culinaromancer's Chest in Lumbridge Castle's
  cellar, the Lovakengj blast mine's other chests ...). The bank chests you build yourself (Fossil Island, the
  Sailing islands, Vampyrium) and the banks a quest opens (Corsair Cove, Ape Atoll, Burgh de Rott) stay grayed out
  with a red mark until you have built or opened them; click one for what it needs. The hole from Lumbridge Castle's
  cellar to the Lumbridge Swamp Caves shows the same way until The Lost Tribe opens it.
- **Filters**: the Filters button at the top left drops down a list of every kind of icon, with its picture and name,
  grouped (Banks, Quests, Shops ...) and searchable: tick or untick a whole group or a single kind, or all at once.
- **Hide just one icon** by right-clicking it. "Show hidden icons" at the top of the Filters list shows the hidden
  ones again, faded, so they can be unhidden the same way; "Icons" turns every icon off or on at once and keeps the
  place names.
- **Back to you**: the round button at the bottom right moves the map to your character.
- **Floors**: the arrows above it go up and down a floor. The floors below are shown darkened underneath, so you can
  see where you are. Zoomed out, the ground floor shows every floor's icons, as the game's map does; zoomed in, each
  icon is on its own floor. Floors are numbered Ground, 2, 3, 4, or Ground, 1, 2, 3 ("Floor numbers" setting).
  Basements and dungeons are on the underground map ("Show underground" at the top).
- **Route here**: right-click anywhere on the map, or on an icon, and choose "Route here" to have
  [Shortest Path](https://github.com/Skretzo/shortest-path) or GPS show the way ("Route with" in the settings).
- **Saved on your computer**: once drawn, the map is kept in RuneLite's plugin data folder, shared by every character
  and profile, so it opens at once. From the moment the client starts (or the plugin is installed) it draws the rest in
  the background, nearest you first, using spare processor time, and draws a region again only when a game update
  changes it. The sidebar tab shows how far along it is.
- **No downloads.** The map is drawn on your computer from the game data your client already has, so it is
  always up to date with the game, and nothing is sent anywhere.

## For plugin authors

Other plugins can draw lines and markers on the map through RuneLite's `PluginMessage`, in world coordinates
(`WorldPoint`). Namespace `advancedmap`:

| Message | Data | Does |
|---|---|---|
| `layer` | `id` (String, required, start it with your plugin's name), `order` (Number, higher on top), `lines` (List of Maps: `points` List of `WorldPoint`, `color` `Color`, `width` Number, `dashed` Boolean), `markers` (List of Maps: `point` `WorldPoint`, `color` `Color`, `shape` `"circle"`, `"square"` or `"diamond"`, `size` Number, `label` String) | Draws a layer, replacing one with the same `id` |
| `removeLayer` | `id` | Removes a layer |
| `show` | `point` (`WorldPoint`), `zoom` (Number, optional: screen pixels per tile) | Opens the map on a point |

The map posts `state` with `open` (Boolean) when it opens or closes.

```java
Map<String, Object> line = new HashMap<>();
line.put("points", List.of(new WorldPoint(3222, 3218, 0), new WorldPoint(3210, 3424, 0)));
line.put("color", Color.CYAN);

Map<String, Object> layer = new HashMap<>();
layer.put("id", "myplugin:route");
layer.put("lines", List.of(line));
eventBus.post(new PluginMessage("advancedmap", "layer", layer));
```

## Credits

The map drawing is RuneLite's own map renderer from its cache library (BSD 2-Clause), adapted to read the
running client. Shops, their stock and prices, monster spawns, stats and drops come from the
[Old School RuneScape Wiki](https://oldschool.runescape.wiki/) (CC BY-NC-SA 3.0); transport routes and fares from
[Shortest Path](https://github.com/Skretzo/shortest-path) (BSD 2-Clause). See
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Advanced Map is not affiliated with Jagex or RuneLite.
