/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MapPainterTest
{
	@Test
	public void overTheGameThereIsACloseButtonAndInAWindowThereIsNot()
	{
		assertTrue(MapPainter.buttons(1200, 800, false).containsKey(MapPainter.Button.CLOSE));
		assertFalse(MapPainter.buttons(1200, 800, true).containsKey(MapPainter.Button.CLOSE));
		// with the Slayer button's place, kept whether the button shows or not
		assertEquals(10, MapPainter.buttons(1200, 800, false).size());
		// maximize, over the game only
		assertTrue(MapPainter.buttons(1200, 800, false).containsKey(MapPainter.Button.MAXIMIZE));
		assertFalse(MapPainter.buttons(1200, 800, true).containsKey(MapPainter.Button.MAXIMIZE));
		assertEquals(8, MapPainter.buttons(1200, 800, true).size());
	}

	@Test
	public void buttonsSitInsideTheViewAndDoNotOverlap()
	{
		for (boolean window : new boolean[]{false, true})
		{
			Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(800, 500, window);
			for (Rectangle a : buttons.values())
			{
				assertTrue(a.x >= 0 && a.x + a.width <= 800 && a.y >= 0 && a.y + a.height <= 500);
				for (Rectangle b : buttons.values())
				{
					assertTrue(a == b || !a.intersects(b));
				}
			}
		}
	}

	@Test
	public void theFindMeButtonIsInTheBottomRightCorner()
	{
		Rectangle locate = MapPainter.buttons(1000, 700, false).get(MapPainter.Button.LOCATE);
		assertTrue(locate.x > 900 && locate.y > 600);
		assertEquals(MapPainter.Button.LOCATE, MapPainter.buttonAt(985, 685 - 10, 1000, 700, false, null));
	}

	@Test
	public void aClickFindsItsButton()
	{
		for (Map.Entry<MapPainter.Button, Rectangle> button : MapPainter.buttons(800, 600, false).entrySet())
		{
			Rectangle r = button.getValue();
			MapPainter.Button found = MapPainter.buttonAt(r.x + r.width / 2, r.y + r.height / 2, 800, 600, false, null);
			// the Slayer button is only there while the Slayer task setting is on
			assertEquals(button.getKey() == MapPainter.Button.SLAYER ? null : button.getKey(), found);
		}
		assertNull(MapPainter.buttonAt(400, 300, 800, 600, false, null));
		Rectangle slayer = MapPainter.buttons(800, 600, false).get(MapPainter.Button.SLAYER);
		MapSettings on = new MapSettings(EnumSet.allOf(IconGroup.class), Collections.emptySet(), Collections.emptySet(),
			false, true, false, false, true, true, RoutingPlugin.NONE, FloorNumbers.GROUND_IS_FIRST, 100, true, true, true);
		assertEquals(MapPainter.Button.SLAYER, MapPainter.buttonAt(slayer.x + 5, slayer.y + 5, 800, 600, false, on));
		// it sits at the top, right of Filters
		Rectangle filters = MapPainter.buttons(800, 600, false).get(MapPainter.Button.FILTERS);
		assertEquals(filters.y, slayer.y);
		assertTrue(slayer.x > filters.x + filters.width);
		// the pop out button is a square beside the window's own buttons
		Rectangle mode = MapPainter.buttons(800, 600, false).get(MapPainter.Button.MODE);
		assertEquals(mode.width, mode.height);
		assertTrue(mode.x < MapPainter.buttons(800, 600, false).get(MapPainter.Button.MAXIMIZE).x);
	}

	@Test
	public void theFloorButtonsSitAboveTheFindMeButton()
	{
		Map<MapPainter.Button, Rectangle> buttons = MapPainter.buttons(1000, 700, false);
		Rectangle locate = buttons.get(MapPainter.Button.LOCATE);
		Rectangle up = buttons.get(MapPainter.Button.FLOOR_UP);
		Rectangle down = buttons.get(MapPainter.Button.FLOOR_DOWN);
		assertTrue(up.y < down.y && down.y + down.height <= locate.y);
		assertEquals(locate.x + locate.width, down.x + down.width);
	}

	@Test
	public void theMenuStaysOnScreenWithOneRowPerEntry()
	{
		MapSpot varrock = MapSpot.teleport(Teleport.VARROCK);
		MapPainter.Selection near = new MapPainter.Selection(varrock, true, 790, 590, Teleport.VARROCK.getLocation());
		List<MapPainter.MenuEntry> entries = MapPainter.menuEntries(near, settings(RoutingPlugin.SHORTEST_PATH));
		assertEquals(4, entries.size());
		assertEquals(MapPainter.Action.HIDE, entries.get(0).getAction());
		assertEquals("Route here (Shortest Path)", entries.get(1).getText());
		assertEquals(MapPainter.Action.ADD_MARKER, entries.get(3).getAction());
		Rectangle menu = MapPainter.menuBounds(near, "Varrock Teleport", entries, 800, 600);
		assertTrue(menu.x + menu.width <= 800 && menu.y + menu.height <= 600);
		for (int i = 0; i < entries.size(); i++)
		{
			Rectangle row = MapPainter.menuEntryBounds(menu, i);
			assertTrue(menu.contains(row));
			assertTrue(row.y > menu.y);
		}
	}

	@Test
	public void aTileWithNoIconRoutesAndTakesAMarker()
	{
		MapPainter.Selection tile = new MapPainter.Selection(null, true, 10, 10, new WorldPoint(3222, 3218, 1));
		List<MapPainter.MenuEntry> entries = MapPainter.menuEntries(tile, settings(RoutingPlugin.GPS));
		assertEquals(3, entries.size());
		assertEquals(MapPainter.Action.ROUTE, entries.get(0).getAction());
		assertEquals("Route here (GPS)", entries.get(0).getText());
		assertEquals("3222, 3218, Floor 2", MapPainter.menuTitle(tile, Progress.NONE, FloorNumbers.GROUND_IS_FIRST));
		assertEquals("3222, 3218, Floor 1", MapPainter.menuTitle(tile, Progress.NONE,
			FloorNumbers.FIRST_ABOVE_GROUND));
		List<MapPainter.MenuEntry> plain = MapPainter.menuEntries(tile, settings(RoutingPlugin.NONE));
		assertEquals(1, plain.size());
		assertEquals("Add marker here", plain.get(0).getText());
	}

	@Test
	public void aRightClickedTileDrawsWithIconsOnScreen()
	{
		// a tile's selection has no icon; drawing the icons beside it once threw on every frame
		MapPainter painter = new MapPainter(null, null, new MapIcons(), null, null, new SideList(), new MapSearch());
		MapPainter.Selection tile = new MapPainter.Selection(null, true, 10, 10, new WorldPoint(3222, 3218, 0));
		List<MapIcons.Shown> shown = Collections.singletonList(new MapIcons.Shown(MapSpot.teleport(Teleport.VARROCK),
			new Rectangle(100, 100, 12, 12), MapIcons.Look.NORMAL));
		BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		painter.drawIcons(g, shown, tile);
		g.dispose();
	}

	/** Game data with nothing in it. */
	static final class NoSource implements com.advancedmap.cache.MapSource
	{
		@Override
		public com.advancedmap.cache.Region region(int regionX, int regionY)
		{
			return null;
		}

		@Override
		public long regionHash(int regionX, int regionY)
		{
			return 0;
		}

		@Override
		public com.advancedmap.cache.UnderlayDefinition underlay(int id)
		{
			return null;
		}

		@Override
		public com.advancedmap.cache.OverlayDefinition overlay(int id)
		{
			return null;
		}

		@Override
		public com.advancedmap.cache.ObjectDefinition object(int id)
		{
			return null;
		}

		@Override
		public int textureColor(int textureId)
		{
			return 0;
		}

		@Override
		public com.advancedmap.cache.MapSprite mapScene(int index)
		{
			return null;
		}

		@Override
		public com.advancedmap.cache.MapSprite areaIcon(int areaId)
		{
			return null;
		}
	}

	/** A painter over a world with no regions drawn, its pictures made on the calling thread. */
	private static MapPainter blankPainter(MapIcons icons)
	{
		com.advancedmap.cache.MapSource none = new NoSource();
		java.util.concurrent.ExecutorService direct = new java.util.concurrent.AbstractExecutorService()
		{
			@Override
			public void execute(Runnable command)
			{
				command.run();
			}

			@Override
			public void shutdown()
			{
			}

			@Override
			public List<Runnable> shutdownNow()
			{
				return Collections.emptyList();
			}

			@Override
			public boolean isShutdown()
			{
				return false;
			}

			@Override
			public boolean isTerminated()
			{
				return false;
			}

			@Override
			public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit)
			{
				return true;
			}
		};
		return new MapPainter(new RegionImages(none, direct, icons), new MapMessages((at, zoom) ->
		{
		}), icons, new MapLabels(), new MapFilters(), new SideList(), new MapSearch());
	}

	/** Paints a frame with the mouse at a point, twice, the hover delay apart, and returns the second. */
	private static BufferedImage restOn(MapPainter painter, MapViewport.View view, java.awt.Point mouse)
		throws InterruptedException
	{
		BufferedImage image = new BufferedImage(view.width, view.height, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < 2; i++)
		{
			Graphics2D g = image.createGraphics();
			painter.paint(g, view, MapArea.SURFACE, 0, null, mouse, true, settings(RoutingPlugin.NONE), Progress.NONE,
				null);
			g.dispose();
			if (i == 0)
			{
				Thread.sleep(MapPainter.HOVER_NANOS / 1_000_000 + 50);
			}
		}
		return image;
	}

	private static int differences(BufferedImage a, BufferedImage b, Rectangle where)
	{
		int count = 0;
		for (int y = where.y; y < where.y + where.height; y++)
		{
			for (int x = where.x; x < where.x + where.width; x++)
			{
				count += a.getRGB(x, y) != b.getRGB(x, y) ? 1 : 0;
			}
		}
		return count;
	}

	@Test
	public void restingOnAnIconShowsItsBoxUnlessOffOrDragging() throws InterruptedException
	{
		MapIcons icons = new MapIcons();
		MapViewport.View view = new MapViewport.View(Teleport.VARROCK.getLocation().getX(),
			Teleport.VARROCK.getLocation().getY(), 4, 500, 400);
		MapIcons.Shown varrock = null;
		for (MapIcons.Shown shown : icons.visible(view, MapArea.SURFACE, 0, settings(RoutingPlugin.NONE), Progress.NONE))
		{
			if (shown.getSpot().getTeleport() == Teleport.VARROCK)
			{
				varrock = shown;
			}
		}
		java.awt.Point on = new java.awt.Point((int) varrock.getBounds().getCenterX(),
			(int) varrock.getBounds().getCenterY());
		// right of the icon, where its box goes, above the bar along the bottom
		Rectangle beside = new Rectangle(on.x + 12, 50, 120, 250);

		MapPainter off = blankPainter(icons);
		BufferedImage plain = restOn(off, view, on);

		MapPainter hover = blankPainter(icons);
		hover.setInfoOnHover(true);
		assertTrue(differences(plain, restOn(hover, view, on), beside) > 500);

		MapPainter dragging = blankPainter(icons);
		dragging.setInfoOnHover(true);
		dragging.setDragging(true);
		assertEquals(0, differences(plain, restOn(dragging, view, on), beside));

		// the mouse on a button: no box, though an icon may be under it
		MapPainter onButton = blankPainter(icons);
		onButton.setInfoOnHover(true);
		Rectangle locate = MapPainter.buttons(500, 400, true).get(MapPainter.Button.LOCATE);
		BufferedImage away = restOn(off, view, new java.awt.Point(locate.x + 4, locate.y + 4));
		assertEquals(0, differences(away, restOn(onButton, view, new java.awt.Point(locate.x + 4, locate.y + 4)),
			new Rectangle(0, 50, 500, 250)));
	}

	@Test
	public void theArrowToThePlayerSitsAtTheEdgeTowardThemOnlyWhenTheyAreOutOfView()
	{
		MapViewport.View view = new MapViewport.View(3222, 3218, 4, 800, 600);
		// in view: no arrow
		assertNull(MapPainter.playerArrow(view, MapArea.SURFACE, new WorldPoint(3230, 3220, 0), true));
		// far east: on the right edge, about level with the middle
		Rectangle east = MapPainter.playerArrow(view, MapArea.SURFACE, new WorldPoint(3500, 3218, 0), true);
		assertTrue(east.getCenterX() > 750 && east.getCenterX() < 800);
		assertTrue(Math.abs(east.getCenterY() - 300) < 30);
		// far north-west: the top-left corner, below the buttons along the top
		Rectangle northWest = MapPainter.playerArrow(view, MapArea.SURFACE, new WorldPoint(2900, 3600, 0), true);
		assertTrue(northWest.getCenterX() < 400 && northWest.getCenterY() >= MapPainter.BUTTON_HEIGHT + 8);
		// off, on the other map, or not logged in: none
		assertNull(MapPainter.playerArrow(view, MapArea.SURFACE, new WorldPoint(3500, 3218, 0), false));
		assertNull(MapPainter.playerArrow(view, MapArea.SURFACE, new WorldPoint(3500, 9800, 0), true));
		assertNull(MapPainter.playerArrow(view, MapArea.SURFACE, null, true));
	}

	private static MapSettings settings(RoutingPlugin routing)
	{
		return new MapSettings(EnumSet.allOf(IconGroup.class), Collections.emptySet(), Collections.emptySet(), false,
			true, false, false, true, true, routing, FloorNumbers.GROUND_IS_FIRST, 100, true, false, true);
	}

	@Test
	public void theTeleportLimitIsARedDashedLineOnlyWhenZoomedIn()
	{
		// the level 20 limit across the middle of a 400 x 300 view, 4 px a tile
		MapViewport.View view = new MapViewport.View(3100, 3680, 4, 400, 300);
		BufferedImage image = draw(view);
		int lineRow = (int) Math.round(view.screenY(Wilderness.teleportLimitY(Wilderness.SURFACE, 20)));
		int red = 0;
		int gaps = 0;
		for (int x = 0; x < 300; x++)
		{
			if (isReddish(image.getRGB(x, lineRow)))
			{
				red++;
			}
			else
			{
				gaps++;
			}
		}
		assertTrue("a red line", red > 100);
		assertTrue("with gaps: dashed", gaps > 50);
		// no line where an ordinary level starts
		int levelRow = (int) Math.round(view.screenY(Wilderness.levelStartY(Wilderness.SURFACE, 19)));
		assertEquals(Color.BLACK.getRGB(), image.getRGB(200, levelRow));

		// zoomed out, nothing
		MapViewport.View far = new MapViewport.View(3100, 3680, MapPainter.WILDERNESS_ZOOM / 2, 400, 300);
		BufferedImage farImage = draw(far);
		for (int y = 0; y < 300; y++)
		{
			for (int x = 0; x < 400; x++)
			{
				assertEquals(Color.BLACK.getRGB(), farImage.getRGB(x, y));
			}
		}
	}

	private static BufferedImage draw(MapViewport.View view)
	{
		BufferedImage image = new BufferedImage(view.width, view.height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		MapPainter.drawWilderness(g, view, MapArea.SURFACE, id -> false);
		g.dispose();
		return image;
	}

	@Test
	public void theWildernessIsNotDrawnFarFromIt()
	{
		MapViewport.View view = new MapViewport.View(3222, 3218, 4, 400, 300);
		BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		MapPainter.drawWilderness(g, view, MapArea.SURFACE, id -> false);
		g.dispose();
		for (int y = 0; y < 300; y++)
		{
			for (int x = 0; x < 400; x++)
			{
				assertEquals(Color.BLACK.getRGB(), image.getRGB(x, y));
			}
		}
	}

	private static boolean isReddish(int rgb)
	{
		Color c = new Color(rgb);
		return c.getRed() > 60 && c.getRed() > 2 * c.getGreen();
	}

	@Test
	public void inTheCavesTheLineCrossesOnlyRegionsTheGameHas()
	{
		// the Revenant Caves' level 20 limit (y 10080), the caves in region column 49 only
		MapViewport.View view = new MapViewport.View(3170, 10080, 2, 600, 200);
		int caves = 49;
		BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		MapPainter.drawWilderness(g, view, MapArea.UNDERGROUND, id -> (id >> 8) != caves);
		g.dispose();
		int row = (int) Math.round(view.screenY(Wilderness.teleportLimitY(Wilderness.UNDERGROUND, 20)));
		int inside = (int) Math.round(view.screenX(caves * 64 + 20));
		int outside = (int) Math.round(view.screenX(caves * 64 - 40));
		boolean red = false;
		for (int x = inside; x < inside + 20; x++)
		{
			red |= isReddish(image.getRGB(x, row));
		}
		assertTrue("a line across the caves", red);
		assertEquals("none over empty space", Color.BLACK.getRGB(), image.getRGB(outside, row));
	}

	@Test
	public void noCaveLineWestOfTheWildernessGodWarsDungeon()
	{
		// the level 30 line's row runs through the God Wars Dungeon (47, 158); every region there has a map here
		MapViewport.View view = new MapViewport.View(3008, 10160, 2, 600, 200);
		BufferedImage image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		MapPainter.drawWilderness(g, view, MapArea.UNDERGROUND, id -> false);
		g.dispose();
		int row = (int) Math.round(view.screenY(Wilderness.teleportLimitY(Wilderness.UNDERGROUND, 30)));
		for (int x = 0; x < (int) view.screenX(48 * 64); x++)
		{
			assertFalse("no line at x " + x, isReddish(image.getRGB(x, row)));
		}
		boolean east = false;
		for (int x = (int) view.screenX(48 * 64) + 2; x < 600; x++)
		{
			east |= isReddish(image.getRGB(x, row));
		}
		assertTrue("the line still runs east of it", east);
	}
	@Test
	public void everyButtonSaysWhatItDoes()
	{
		MapSettings settings = settings(RoutingPlugin.NONE);
		for (MapPainter.Button button : MapPainter.Button.values())
		{
			String tip = MapPainter.tip(button, MapArea.SURFACE, 0, false, settings, false, false);
			assertTrue(button + " has a tip", tip != null && !tip.isEmpty());
		}
		assertEquals("Show the underground map", MapPainter.tip(MapPainter.Button.AREA, MapArea.SURFACE, 0, false,
			settings, false, false));
		assertEquals("Back over the game", MapPainter.tip(MapPainter.Button.MODE, MapArea.SURFACE, 0, true, settings,
			false, false));
		assertEquals("Underground, below here", MapPainter.tip(MapPainter.Button.FLOOR_DOWN, MapArea.SURFACE, 0, false,
			settings, false, false));
		assertEquals("Down a floor", MapPainter.tip(MapPainter.Button.FLOOR_DOWN, MapArea.SURFACE, 1, false, settings,
			false, false));
	}

	@Test
	public void noPlaceNameIsDrawnUnderTheBottomBarOrASpaceAlreadyTaken()
	{
		MapViewport.View view = new MapViewport.View(3222, 3218, 2, 500, 400);
		BufferedImage image = new BufferedImage(500, 400, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setFont(FontManager.getRunescapeSmallFont());
		Rectangle bar = MapPainter.barBounds(g, view, "Drag to move, scroll to zoom, Esc to close");
		assertEquals(0, bar.x);
		assertTrue("as wide as its text, not the view", bar.width > 100 && bar.width < 400);
		assertEquals(400, bar.y + bar.height);
		// one name in the middle of the view, one on the bar's text along the bottom
		java.util.List<MapLabels.Label> labels = java.util.Arrays.asList(
			new MapLabels.Label(java.util.Collections.singletonList("Lumbridge"), 3222, 3218, MapLabels.TOWN, Color.WHITE),
			new MapLabels.Label(java.util.Collections.singletonList("Swamp"), (int) view.worldX(bar.width / 2),
				(int) view.worldY(bar.y + bar.height / 2), MapLabels.TOWN, Color.WHITE));
		MapPainter.drawLabels(g, view, MapArea.SURFACE, labels, java.util.Collections.singletonList(bar));
		g.dispose();
		boolean middle = false;
		boolean onBar = false;
		for (int y = 0; y < 400; y++)
		{
			for (int x = 0; x < 500; x++)
			{
				if (image.getRGB(x, y) != 0xFF000000)
				{
					if (y >= bar.y)
					{
						onBar = true;
					}
					else
					{
						middle = true;
					}
				}
			}
		}
		assertTrue("the name in the open is drawn", middle);
		assertFalse("the name under the bar's strip is not", onBar);
	}
}
