/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.MapSprite;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.AgilityShortcut;

/**
 * The icons on the map: the game's own (found as each region is drawn, no longer drawn into its picture) and the
 * teleports. Works out which are on screen with the player's settings, how each looks (usable, locked, done, hidden)
 * and what its info box says. Regions are added from the render threads; everything else may run on any thread.
 */
final class MapIcons
{
	/** Icons show from this zoom (screen pixels per tile) in; further out there would be too many to read. */
	static final double ICON_ZOOM = 1.25;
	/**
	 * Zoomed in this far, each icon shows on its own floor only (Lumbridge Castle's bank on the floor it is on, not on
	 * the ground); further out, the ground shows every floor's icons, as the game's own map does.
	 */
	static final double OWN_FLOOR_ZOOM = 3;
	/** Size of a teleport's mark, and the most a game icon is assumed to be, in pixels. */
	static final int TELEPORT_SIZE = 15;

	enum Look
	{
		NORMAL,
		/** An agility shortcut above the player's level, or a teleport spell they can't cast yet. */
		LOCKED,
		/** A quest the player has started. */
		STARTED,
		/** A quest the player has finished. */
		DONE,
		/** Hidden by the player, shown faded while "Show hidden icons" is on. */
		HIDDEN
	}

	/** An icon on screen: where it is drawn and how. */
	@Value
	static class Shown
	{
		MapSpot spot;
		Rectangle bounds;
		Look look;
	}

	/** One line of an info box. */
	@Value
	static class Line
	{
		String text;
		Color color;
	}

	static final Color GOOD = new Color(0x7FD36B);
	static final Color BAD = new Color(0xFF6B5B);
	static final Color PLAIN = new Color(0xE8E2D0);
	static final Color QUIET = new Color(0xA8A090);

	private static final List<MapSpot> TELEPORTS;

	static
	{
		List<MapSpot> teleports = new ArrayList<>();
		for (Teleport teleport : Teleport.values())
		{
			teleports.add(MapSpot.teleport(teleport));
		}
		TELEPORTS = Collections.unmodifiableList(teleports);
	}

	private final Map<Integer, List<MapSpot>> byRegion = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> images = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> grayImages = new ConcurrentHashMap<>();
	/** The variables of the unlocks found so far, for {@link Progress} to read. */
	private final Set<Integer> watchedVars = ConcurrentHashMap.newKeySet();

	/** Keeps a drawn region's icons. Render threads. */
	void addRegion(int regionId, List<MapSpot> spots)
	{
		for (MapSpot spot : spots)
		{
			if (spot.getUnlock() != null)
			{
				watchedVars.add(spot.getUnlock().getVariable());
			}
		}
		byRegion.put(regionId, Collections.unmodifiableList(new ArrayList<>(spots)));
	}

	/** Every icon found so far, on every map and floor (not the teleports). */
	List<MapSpot> allSpots()
	{
		List<MapSpot> out = new ArrayList<>();
		for (List<MapSpot> spots : byRegion.values())
		{
			out.addAll(spots);
		}
		return out;
	}

	/** The variables of the unlocks found so far. */
	Set<Integer> watchedVars()
	{
		return Collections.unmodifiableSet(watchedVars);
	}

	/** Keeps the picture of a map area's icon. Render threads. */
	void addSprite(int areaId, MapSprite sprite)
	{
		if (sprite != null && !images.containsKey(areaId))
		{
			images.put(areaId, toImage(sprite));
		}
	}

	/** The picture of a game icon, gray when {@code gray}; null while it is not known. */
	BufferedImage image(int areaId, boolean gray)
	{
		BufferedImage image = images.get(areaId);
		if (image == null || !gray)
		{
			return image;
		}
		return grayImages.computeIfAbsent(areaId, k -> grayed(image));
	}

	/**
	 * The icons to draw on a view of a floor, bottom first, with their looks: those of the groups turned on, minus
	 * hidden ones (shown faded instead while {@code showHidden}) and finished quests when the player hides them. A
	 * floor shows its own icons; the ground also the floors' above while zoomed out past {@link #OWN_FLOOR_ZOOM}. None
	 * while zoomed out past {@link #ICON_ZOOM} or with icons turned off.
	 */
	List<Shown> visible(MapViewport.View view, MapArea area, int plane, MapSettings settings, Progress progress)
	{
		List<Shown> out = new ArrayList<>();
		if (settings.isIconsOff() || view.zoom < ICON_ZOOM)
		{
			return out;
		}
		boolean everyFloor = plane == 0 && view.zoom < OWN_FLOOR_ZOOM;
		// icons a little off screen can still reach into it; more so when drawn bigger
		int margin = TELEPORT_SIZE * MapSettings.MAX_ICON_SCALE / 100;
		int rx0 = (int) Math.floor(view.worldX(-margin) / 64);
		int rx1 = (int) Math.floor(view.worldX(view.width + margin) / 64);
		int ry0 = (int) Math.floor(view.worldY(view.height + margin) / 64);
		int ry1 = (int) Math.floor(view.worldY(-margin) / 64);
		for (int rx = rx0; rx <= rx1; rx++)
		{
			for (int ry = ry0; ry <= ry1; ry++)
			{
				List<MapSpot> spots = byRegion.get(rx << 8 | ry);
				if (spots != null)
				{
					for (MapSpot spot : spots)
					{
						if ((everyFloor || spot.getPlane() == plane) && !markedNearby(spot, rx, ry))
						{
							add(out, spot, view, area, settings, progress);
						}
					}
				}
			}
		}
		for (MapSpot spot : TELEPORTS)
		{
			if (everyFloor || spot.getPlane() == plane)
			{
				add(out, spot, view, area, settings, progress);
			}
		}
		return out;
	}

	private void add(List<Shown> out, MapSpot spot, MapViewport.View view, MapArea area, MapSettings settings,
		Progress progress)
	{
		if (!settings.shows(spot) || MapArea.of(spot.getY()) != area)
		{
			return;
		}
		Look look = look(spot, settings, progress);
		if (look == null)
		{
			return;
		}
		int width = TELEPORT_SIZE;
		int height = TELEPORT_SIZE;
		if (spot.getTeleport() == null)
		{
			BufferedImage image = images.get(spot.getAreaId());
			if (image == null)
			{
				return;
			}
			width = image.getWidth();
			height = image.getHeight();
		}
		width = Math.max(1, Math.round(width * settings.getIconScale() / 100f));
		height = Math.max(1, Math.round(height * settings.getIconScale() / 100f));
		int cx = (int) Math.round(view.screenX(spot.getX() + 0.5));
		int cy = (int) Math.round(view.screenY(spot.getY() + 0.5));
		if (cx < -width || cy < -height || cx > view.width + width || cy > view.height + height)
		{
			return;
		}
		out.add(new Shown(spot, new Rectangle(cx - width / 2, cy - height / 2, width, height), look));
	}

	/**
	 * True for a bank Advanced Map found that a bank icon of the game's in a neighboring region turns out to stand
	 * for (the region's own icons were checked when it was found).
	 */
	private boolean markedNearby(MapSpot spot, int regionX, int regionY)
	{
		if (spot.getCluster() == null)
		{
			return false;
		}
		for (int rx = regionX - 1; rx <= regionX + 1; rx++)
		{
			for (int ry = regionY - 1; ry <= regionY + 1; ry++)
			{
				List<MapSpot> spots = rx == regionX && ry == regionY ? null : byRegion.get(rx << 8 | ry);
				if (spots != null && FoundIcons.nearGameBank(spots, spot.getCluster()))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** How a spot looks, or null when it is not drawn at all. */
	static Look look(MapSpot spot, MapSettings settings, Progress progress)
	{
		if (settings.isHidden(spot))
		{
			return settings.isShowHidden() ? Look.HIDDEN : null;
		}
		Unlock unlock = spot.getUnlock();
		if (unlock != null)
		{
			int value = progress.value(unlock.getVariable());
			if (spot.getUnlockable() == null)
			{
				// a chest of a game event: a bank only on some days, and nothing to say about it on others
				return unlock.isOpen(value) ? Look.NORMAL : null;
			}
			// grayed only once the player's value is read (a few ticks after its region is first drawn)
			if (value >= 0 && !unlock.isOpen(value) && settings.isMarkLocked() && progress.isLoggedIn())
			{
				return Look.LOCKED;
			}
		}
		Progress.QuestInfo quest = spot.getCategory() == IconGroup.QUEST ? progress.getQuestsByIcon().get(spot.getAreaId())
			: null;
		if (quest != null && quest.getState() == QuestState.FINISHED)
		{
			return settings.isHideCompletedQuests() ? null : Look.DONE;
		}
		if (quest != null && quest.getState() == QuestState.IN_PROGRESS)
		{
			return Look.STARTED;
		}
		if (settings.isMarkLocked() && progress.isLoggedIn() && isLocked(spot, progress))
		{
			return Look.LOCKED;
		}
		return Look.NORMAL;
	}

	/** True for a shortcut above the player's Agility or a teleport spell they can't cast. Needs the player's levels. */
	static boolean isLocked(MapSpot spot, Progress progress)
	{
		if (spot.getShortcut() != null)
		{
			return progress.level(Skill.AGILITY) < spot.getShortcut().getLevel();
		}
		Teleport teleport = spot.getTeleport();
		if (teleport != null && teleport.getType().isSpell())
		{
			Quest quest = requiredQuest(teleport);
			return progress.level(Skill.MAGIC) < teleport.getMagicLevel()
				|| quest != null && progress.state(quest) != QuestState.FINISHED;
		}
		return false;
	}

	/** The quest a teleport spell needs, or null: the spellbook's quest, or the spell's own. */
	static Quest requiredQuest(Teleport teleport)
	{
		switch (teleport.getType())
		{
			case ANCIENT_MAGICKS:
				return Quest.DESERT_TREASURE_I;
			case LUNAR_MAGIC:
				return Quest.LUNAR_DIPLOMACY;
			default:
				break;
		}
		switch (teleport)
		{
			case ARDOUGNE:
				return Quest.PLAGUE_CITY;
			case WATCHTOWER:
			case WATCHTOWER_YANILLE:
				return Quest.WATCHTOWER;
			case TROLLHEIM:
				return Quest.EADGARS_RUSE;
			case APE_ATOLL:
				return Quest.RECIPE_FOR_DISASTER__KING_AWOWOGEI;
			case KOUREND:
				return Quest.CLIENT_OF_KOUREND;
			case CIVITAS_ILLA_FORTIS:
				return Quest.TWILIGHTS_PROMISE;
			default:
				return null;
		}
	}

	/** The topmost icon under a screen point, or null. */
	static Shown at(List<Shown> shown, int x, int y)
	{
		for (int i = shown.size() - 1; i >= 0; i--)
		{
			if (shown.get(i).getBounds().contains(x, y))
			{
				return shown.get(i);
			}
		}
		return null;
	}

	/** An icon's name: the quest, the shortcut, the teleport, or the game's name for its kind. */
	static String name(MapSpot spot, Progress progress)
	{
		if (spot.getTeleport() != null)
		{
			return spot.getTeleport().title();
		}
		if (spot.getUnlockable() != null)
		{
			return spot.getUnlockable().label;
		}
		Progress.QuestInfo quest = spot.getCategory() == IconGroup.QUEST ? progress.getQuestsByIcon().get(spot.getAreaId())
			: null;
		if (quest != null)
		{
			return quest.getName();
		}
		if (spot.getShortcut() != null)
		{
			return spot.getShortcut().getDescription();
		}
		// which fairy ring, spirit tree or cart: the game's map calls them all "Transportation"
		String transport = Transports.stopTitle(spot);
		if (transport == null)
		{
			transport = Transports.iconName(spot);
		}
		if (transport != null)
		{
			return transport;
		}
		String name = progress.categoryName(spot.getCategory());
		return name != null ? name : "Map icon";
	}

	/** The game's name for the mining site icons' category. */
	static final String MINING_SITE = "Mining site";
	/** The game's name for the rare trees icons' category. */
	static final String RARE_TREES = "Rare trees";

	/** The lines of an icon's info box, after its name. */

	static List<Line> info(MapSpot spot, Progress progress, boolean hidden, FloorNumbers floors)
	{
		List<Line> lines = new ArrayList<>();
		Progress.QuestInfo quest = spot.getCategory() == IconGroup.QUEST ? progress.getQuestsByIcon().get(spot.getAreaId())
			: null;
		if (quest != null)
		{
			questInfo(lines, quest, progress);
		}
		else if (spot.getShortcut() != null)
		{
			AgilityShortcut shortcut = spot.getShortcut();
			lines.add(new Line(spot.getCategory() == IconGroup.AGILITY_SHORTCUT_ONE_WAY ? "Agility shortcut, one way"
				: "Agility shortcut", QUIET));
			levelLine(lines, Skill.AGILITY, shortcut.getLevel(), progress);
		}
		else if (spot.getTeleport() != null)
		{
			teleportInfo(lines, spot.getTeleport(), progress);
		}
		else if (spot.getUnlockable() != null)
		{
			unlockInfo(lines, spot, progress);
		}
		else if (spot.getDetail() != null)
		{
			lines.add(new Line(spot.getDetail() + ", not on the game's own map", QUIET));
		}
		if (MINING_SITE.equals(progress.categoryName(spot.getCategory())))
		{
			// what RuneLite's own map shows here, which the game's map leaves out
			for (String rocks : MiningSites.rocksNear(spot.getX(), spot.getY(), spot.getPlane()))
			{
				lines.add(new Line(rocks, PLAIN));
			}
		}
		if (RARE_TREES.equalsIgnoreCase(progress.categoryName(spot.getCategory())))
		{
			// which trees, as RuneLite's own map says; the game's map leaves it out too
			for (RareTrees.Tree tree : RareTrees.near(spot.getX(), spot.getY(), spot.getPlane()))
			{
				lines.add(new Line(tree.getName(), PLAIN));
				levelLine(lines, Skill.WOODCUTTING, tree.getLevel(), progress);
			}
		}
		// the game's own tile, in Prifddinas the city's
		WorldPoint tile = Prifddinas.toCity(new WorldPoint(spot.getX(), spot.getY(), spot.getPlane()));
		lines.add(new Line(tile.getPlane() > 0 ? tile.getX() + ", " + tile.getY() + ", " + floors.name(tile.getPlane())
			: tile.getX() + ", " + tile.getY(), QUIET));
		lines.add(new Line(hidden ? "Hidden: right-click to unhide" : "Right-click to hide this icon", QUIET));
		return lines;
	}

	private static void questInfo(List<Line> lines, Progress.QuestInfo quest, Progress progress)
	{
		if (quest.getState() == QuestState.FINISHED)
		{
			lines.add(new Line("Completed", GOOD));
		}
		else if (quest.getState() == QuestState.IN_PROGRESS)
		{
			lines.add(new Line("In progress", new Color(0xFFD75E)));
		}
		else if (quest.getState() == QuestState.NOT_STARTED)
		{
			lines.add(new Line("Not started", PLAIN));
		}
		StringBuilder about = new StringBuilder();
		if (quest.getDifficulty() >= 0 && quest.getDifficulty() < Progress.DIFFICULTY.length)
		{
			about.append(Progress.DIFFICULTY[quest.getDifficulty()]);
		}
		if (quest.getLength() >= 0 && quest.getLength() < Progress.LENGTH.length)
		{
			about.append(about.length() > 0 ? ", " : "").append(Progress.LENGTH[quest.getLength()].toLowerCase());
		}
		about.append(about.length() > 0 ? ", " : "").append(quest.isMembers() ? "members" : "free to play");
		lines.add(new Line(about.toString(), QUIET));
		int[] stats = quest.getRequiredStats();
		for (int i = 0; i + 1 < stats.length; i += 2)
		{
			if (stats[i] >= 0 && stats[i] < Skill.values().length)
			{
				levelLine(lines, Skill.values()[stats[i]], stats[i + 1], progress);
			}
		}
	}

	private static void teleportInfo(List<Line> lines, Teleport teleport, Progress progress)
	{
		lines.add(new Line(teleport.getType().label, QUIET));
		if (!teleport.getType().isSpell())
		{
			return;
		}
		levelLine(lines, Skill.MAGIC, teleport.getMagicLevel(), progress);
		Quest quest = requiredQuest(teleport);
		if (quest != null)
		{
			QuestState state = progress.state(quest);
			Color color = !progress.isLoggedIn() ? PLAIN : state == QuestState.FINISHED ? GOOD : BAD;
			lines.add(new Line("Needs " + quest.getName(), color));
		}
	}

	/**
	 * What something built or unlocked needs, and whether the player has it: built or not, and until it is, the levels,
	 * the quest and the materials of a bank chest; how a quest opens the others.
	 */
	private static void unlockInfo(List<Line> lines, MapSpot spot, Progress progress)
	{
		Unlockable unlockable = spot.getUnlockable();
		lines.add(new Line(unlockable.note, QUIET));
		if (!progress.isLoggedIn())
		{
			lines.add(new Line(unlockable.built ? "Log in to see whether you have built it"
				: "Log in to see whether it is open to you", QUIET));
		}
		else if (spot.getUnlock().isOpen(progress.value(spot.getUnlock().getVariable())))
		{
			lines.add(new Line(unlockable.built ? "You have built it" : "Open to you", GOOD));
			return;
		}
		else
		{
			lines.add(new Line(unlockable.built ? "You have not built it yet" : "Not open to you yet", BAD));
		}
		if (unlockable.construction > 0)
		{
			levelLine(lines, Skill.CONSTRUCTION, unlockable.construction, progress);
		}
		if (unlockable.sailing > 0)
		{
			levelLine(lines, Skill.SAILING, unlockable.sailing, progress);
		}
		if (unlockable.built && unlockable.quest != null)
		{
			QuestState state = progress.state(unlockable.quest);
			Color color = !progress.isLoggedIn() ? PLAIN : state == QuestState.FINISHED ? GOOD : BAD;
			lines.add(new Line("Needs " + unlockable.quest.getName(), color));
		}
		if (unlockable.materials.length > 0)
		{
			lines.add(new Line("Built from:", PLAIN));
			for (String material : unlockable.materials)
			{
				lines.add(new Line("  " + material, PLAIN));
			}
		}
	}

	/** "Needs 33 Agility (you have 20)", green when met, red when not, plain when logged out. */
	private static void levelLine(List<Line> lines, Skill skill, int level, Progress progress)
	{
		if (!progress.isLoggedIn())
		{
			lines.add(new Line("Needs " + level + " " + skill.getName(), PLAIN));
			return;
		}
		int have = progress.level(skill);
		lines.add(new Line("Needs " + level + " " + skill.getName() + " (you have " + have + ")",
			have >= level ? GOOD : BAD));
	}

	static BufferedImage toImage(MapSprite sprite)
	{
		BufferedImage image = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int[] pixels = sprite.getPixels();
		for (int i = 0; i < pixels.length; i++)
		{
			if (pixels[i] != 0)
			{
				image.setRGB(i % sprite.getWidth(), i / sprite.getWidth(), 0xFF000000 | pixels[i]);
			}
		}
		return image;
	}

	/** A darker gray copy, for something the player can't use yet. */
	static BufferedImage grayed(BufferedImage image)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				int gray = ((argb >> 16 & 0xFF) * 30 + (argb >> 8 & 0xFF) * 59 + (argb & 0xFF) * 11) / 100 * 3 / 4;
				out.setRGB(x, y, argb & 0xFF000000 | gray << 16 | gray << 8 | gray);
			}
		}
		return out;
	}
}
