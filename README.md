# Advanced Map

A full-screen world map for RuneLite, drawn from the game's own map data with smooth pan and zoom.

- **Open it** from its sidebar tab or with **Ctrl+M** (change the key in the settings). **Escape**, Ctrl+M or the
  close button close it. "How the map works" in the sidebar tab explains each part of the map.
- **Drag** to move, **scroll** to zoom around the pointer. It opens on your character, on the surface or the
  underground map, whichever you are on; the button at the top switches between them.
- **Pop it out** into a window of its own with the **Pop out** button (or the setting), to keep it beside the game's
  own world map or on another screen. **Over the game** brings it back.
- **Wilderness teleport limits**: zoomed in, red dashed lines show where the Wilderness passes level 20 and level 30,
  above which most teleports stop working. Turn them off with the "Wilderness teleport limits" setting.
- **Place names** from the game's own map: kingdoms when zoomed far out, then towns, then smaller places.
- **Icons you can click**: banks, quests, shops, shortcuts and every other icon of the game's map, plus teleport
  destinations. Click one for its details (a quest's difficulty and the levels it needs, a shortcut's Agility level,
  a spell's Magic level and quest). Agility shortcuts and teleport spells you can't use yet are grayed out with a
  red mark, and finished quests can be left out.
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
running client. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Advanced Map is not affiliated with Jagex or RuneLite.
