# Better Map

A full-screen world map for RuneLite, drawn from the game's own map data with smooth pan and zoom.

- **Open it** with the sidebar button or **Ctrl+M** (change the key in the settings). **Escape**, Ctrl+M or the
  close button close it.
- **Drag** to move, **scroll** to zoom around the pointer. It opens on your character.
- **No downloads.** The map is drawn on your computer from the game data your client already has, so it is
  always up to date with the game, and nothing is sent anywhere.

## For plugin authors

Other plugins can draw lines and markers on the map through RuneLite's `PluginMessage`, in world coordinates
(`WorldPoint`). Namespace `betterworldmap`:

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
eventBus.post(new PluginMessage("betterworldmap", "layer", layer));
```

## Credits

The map drawing is RuneLite's own map renderer from its cache library (BSD 2-Clause), adapted to read the
running client. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Better Map is not affiliated with Jagex or RuneLite.
