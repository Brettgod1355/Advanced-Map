/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import com.advancedmap.cache.Location;
import com.advancedmap.cache.MapImageRenderer;
import com.advancedmap.cache.MapLinks;
import com.advancedmap.cache.MapSource;
import com.advancedmap.cache.Region;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * The drawn pictures of map regions, one per floor, made on background threads as the map needs them and kept at
 * three sizes: 4, 2 and 1 pixels per tile. The biggest are made first and shrunk for the others; only the smallest are
 * kept in memory for the whole world, the bigger ones for the regions seen last. With a {@link MapStore}, pictures are
 * read from disk when the game data they were drawn from has not changed, and saved once drawn. The game's map icons
 * are left out of the pictures: each region's are handed to {@link MapIcons}, which draws them so they can be clicked.
 * <p>
 * A region's floor is a key: {@code plane << 16 | regionId}.
 */
@Slf4j
class RegionImages
{
	static final int PLANES = 4;

	enum Level
	{
		FULL(MapImageRenderer.MAP_SCALE, 128),
		HALF(2, 512),
		SMALL(1, Integer.MAX_VALUE);

		final int pixelsPerTile;
		final int kept;

		Level(int pixelsPerTile, int kept)
		{
			this.pixelsPerTile = pixelsPerTile;
			this.kept = kept;
		}

		/** The smallest pictures that still look sharp at a zoom (screen pixels per tile). */
		static Level forZoom(double zoom)
		{
			if (zoom >= 3)
			{
				return FULL;
			}
			return zoom >= 1.25 ? HALF : SMALL;
		}
	}

	private final MapSource source;
	private final MapImageRenderer renderer;
	private final ExecutorService executor;
	private final MapIcons icons;
	private final Set<Integer> iconsFound = ConcurrentHashMap.newKeySet();
	/**
	 * Whether the bundled tables a region's icons come from have been read: they load in the background as the plugin
	 * starts, and a region scanned before then is scanned again (a test stands in its own answer).
	 */
	private volatile java.util.function.BooleanSupplier tablesRead = Transports::isLoaded;
	/** How far a link spot may be from an icon of the game's that stands for it. */
	static final int LINK_REACH = 2;
	/**
	 * How near a transport icon of the game's (or a Sailing dock's, which sits on the jetty rather than the boarding tile)
	 * must be to a place to board for that place to need none of its own.
	 */
	static final int TRANSPORT_REACH = 24;
	/** Floors whose saved picture has been checked against the game's data this session. */
	private final Set<Integer> checked = ConcurrentHashMap.newKeySet();
	/** Floors waiting to be checked. */
	private final Set<Integer> checking = ConcurrentHashMap.newKeySet();
	/** A saved floor with nothing on it, shown from disk. */
	private static final Drawn NOTHING = new Drawn(null, null);
	private final Map<Level, Map<Integer, BufferedImage>> images = new ConcurrentHashMap<>();
	private final Set<Integer> absent = ConcurrentHashMap.newKeySet();
	/** Floors a draw failed on while a smaller picture of them exists: that picture stays, and they are not asked again. */
	private final Set<Integer> failed = ConcurrentHashMap.newKeySet();
	/** Floors shown from disk whose check could not read the game's data yet: checked again, one a second, while shown. */
	private final Set<Integer> recheck = ConcurrentHashMap.newKeySet();
	private volatile long recheckedAt;
	/** How often a floor whose check found the cache not ready is checked again. */
	static final long RECHECK_NANOS = 1_000_000_000L;
	private final Set<Long> queued = ConcurrentHashMap.newKeySet();
	private volatile Set<Integer> wanted = Collections.emptySet();
	/** Asked for besides what is on screen: the regions a dungeon preview shows or looks through. */
	private volatile Set<Integer> wantedBesides = Collections.emptySet();
	private volatile MapStore store;

	RegionImages(MapSource source, ExecutorService executor, MapIcons icons)
	{
		this.source = source;
		this.renderer = new MapImageRenderer(source);
		this.executor = executor;
		this.icons = icons;
		for (Level level : Level.values())
		{
			images.put(level, Collections.synchronizedMap(
				new LinkedHashMap<Integer, BufferedImage>(64, 0.75f, true)
				{
					@Override
					protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest)
					{
						// never a floor that is on screen: the next paint would ask for it again, and with more floors
						// in view than are kept the drawing would never end
						return size() > level.kept && !wanted.contains(eldest.getKey())
							&& !wantedBesides.contains(eldest.getKey());
					}
				}));
		}
	}

	static int key(int plane, int regionId)
	{
		return plane << 16 | regionId;
	}

	static int plane(int key)
	{
		return key >>> 16;
	}

	static int region(int key)
	{
		return key & 0xFFFF;
	}

	/** From now on, read pictures from disk and save new ones there. */
	void setStore(MapStore store)
	{
		this.store = store;
	}

	MapStore store()
	{
		return store;
	}

	/** The picture of a region's floor at a level, or the nearest size there is, or null while none is made yet. */
	BufferedImage get(int plane, int regionId, Level level)
	{
		int key = key(plane, regionId);
		BufferedImage image = images.get(level).get(key);
		if (image != null)
		{
			return image;
		}
		for (Level other : Level.values())
		{
			image = images.get(other).get(key);
			if (image != null)
			{
				return image;
			}
		}
		return null;
	}

	/** True when the game has nothing for a region's floor (open sea beyond the map's edge, a floor never built). */
	boolean isAbsent(int plane, int regionId)
	{
		return absent.contains(key(plane, regionId)) || absent.contains(key(0, regionId));
	}

	/**
	 * True when a region's floor is known to have something on it: drawn, or saved with something on it. False while
	 * nothing is known yet, as for a floor known to be empty.
	 */
	boolean hasFloor(int plane, int regionId)
	{
		int key = key(plane, regionId);
		if (isAbsent(plane, regionId))
		{
			return false;
		}
		if (get(plane, regionId, Level.SMALL) != null)
		{
			return true;
		}
		MapStore saved = store;
		Long hash = saved == null ? null : saved.savedHash(key);
		return hash != null && !saved.isEmpty(key, hash);
	}

	/** True while pictures asked for are still being made. */
	boolean isBusy()
	{
		return !queued.isEmpty();
	}

	/**
	 * Asks for the pictures of the regions on screen, most important first: the floor shown, then each floor below it
	 * (drawn dimmed underneath). Regions asked for earlier but no longer on screen are skipped when their turn comes.
	 */
	void request(int plane, Collection<Integer> regionIds, Level level)
	{
		Set<Integer> keys = keys(plane, regionIds);
		wanted = keys;
		queue(plane, regionIds, level);
		retryCheck(keys);
	}

	/**
	 * Checks again one floor on screen whose check found the client's cache not ready: a floor shown from disk (or
	 * saved as empty) is otherwise never compared with the game's data again this session, since its picture stays
	 * in memory and nothing draws it anew. One a second, so a cache still downloading is not asked every paint.
	 */
	private void retryCheck(Set<Integer> onScreen)
	{
		if (recheck.isEmpty())
		{
			return;
		}
		long now = System.nanoTime();
		if (now - recheckedAt < RECHECK_NANOS)
		{
			return;
		}
		for (int key : onScreen)
		{
			if (recheck.remove(key))
			{
				recheckedAt = now;
				checkLater(key);
				return;
			}
		}
	}

	/**
	 * Asks for pictures of regions off the screen, as well as those on it: a dungeon preview's {@code shown} on a
	 * floor at a level, and the smallest of the floors it {@code looks} through (keys, {@link #key}). They replace the
	 * last ones asked for this way, never the screen's.
	 */
	void requestBesides(int plane, Collection<Integer> shown, Level level, Collection<Integer> looks)
	{
		Set<Integer> keys = keys(plane, shown);
		keys.addAll(looks);
		wantedBesides = keys;
		queue(plane, shown, level);
		for (int key : looks)
		{
			queue(key, Level.SMALL);
		}
	}

	/** No preview shows any more: the pictures only it wanted are not drawn after all (queued ones are skipped). */
	void clearBesides()
	{
		wantedBesides = Collections.emptySet();
	}

	private Set<Integer> keys(int plane, Collection<Integer> regionIds)
	{
		Set<Integer> keys = new HashSet<>();
		for (int id : regionIds)
		{
			if (absent.contains(key(0, id)))
			{
				// nothing of the region exists: nothing of it is wanted
				continue;
			}
			for (int p = plane; p >= 0; p--)
			{
				keys.add(key(p, id));
			}
		}
		return keys;
	}

	private void queue(int plane, Collection<Integer> regionIds, Level level)
	{
		for (int id : regionIds)
		{
			for (int p = plane; p >= 0; p--)
			{
				if (!queue(key(p, id), level))
				{
					return;
				}
			}
		}
	}

	/** Asks for one floor's picture unless it is there or asked for. False when the plugin is stopping. */
	private boolean queue(int key, Level level)
	{
		if (dataFailed.get() || isAbsent(plane(key), region(key)) || failed.contains(key) || images.get(level).containsKey(key))
		{
			return true;
		}
		long job = (long) level.ordinal() << 32 | key;
		if (queued.add(job))
		{
			try
			{
				executor.execute(() -> make(key, level, job));
			}
			catch (RejectedExecutionException e)
			{
				// the plugin is stopping; a window may still repaint once before it closes
				queued.remove(job);
				return false;
			}
		}
		return true;
	}

	/** Lets the biggest pictures go when the map closes; the smaller ones stay, so it opens again at once. */
	void trim()
	{
		images.get(Level.FULL).clear();
	}

	private void make(int key, Level level, long job)
	{
		try
		{
			if (!wanted.contains(key) && !wantedBesides.contains(key) || images.get(level).containsKey(key))
			{
				return;
			}
			Drawn drawn = draw(key, level);
			if (drawn == null)
			{
				return;
			}
			keep(key, level, drawn);
		}
		catch (CancellationException | CacheNotReadyException e)
		{
			// the plugin stopped, or the client is still downloading this part of the cache: asked again later
		}
		catch (MapDataException e)
		{
			// a format this plugin cannot read: nothing of the game is missing, nothing is drawn until it is updated
			warnOnce(e);
		}
		catch (RuntimeException e)
		{
			log.debug("could not draw region {} floor {}", region(key), plane(key), e);
			// "the game has nothing here" only when nothing of it was ever drawn: a picture there is stays on screen
			if (get(plane(key), region(key), Level.SMALL) == null)
			{
				absent.add(key);
			}
			else
			{
				failed.add(key);
			}
		}
		finally
		{
			queued.remove(job);
		}
	}

	/**
	 * Draws a region's floor ahead of time, for the disk only: nothing is kept in memory. Does nothing when it is
	 * saved already. Render threads.
	 */
	/** Draws and saves a floor of a region. Throws {@link CacheNotReadyException} when it must be tried again later. */
	void prepare(int key)
	{
		MapStore saved = store;
		if (saved == null || saved.has(key))
		{
			return;
		}
		try
		{
			draw(key, Level.SMALL);
		}
		catch (CancellationException e)
		{
			// the plugin is stopping
		}
		catch (CacheNotReadyException e)
		{
			// the client has not downloaded this part of the cache yet: the preparer puts it back in its queue
			throw e;
		}
		catch (MapDataException e)
		{
			warnOnce(e);
		}
		catch (RuntimeException e)
		{
			log.debug("could not prepare region {} floor {}", region(key), plane(key), e);
		}
	}

	/** A floor's pictures: the full one only when drawn or wanted, the smallest always. */
	private static final class Drawn
	{
		final BufferedImage full;
		final BufferedImage small;

		Drawn(BufferedImage full, BufferedImage small)
		{
			this.full = full;
			this.small = small;
		}
	}

	/**
	 * The pictures of a region's floor, from disk when they were saved from the same game data, else drawn (and
	 * saved). Null when the game has nothing there.
	 */
	private Drawn draw(int key, Level level)
	{
		MapStore saved = store;
		if (saved != null && !checked.contains(key))
		{
			// what was saved shows at once, without reading the game's data first; it is checked against that data
			// afterwards, and drawn again if a game update changed it
			Long savedHash = saved.savedHash(key);
			if (savedHash != null)
			{
				Drawn quick = saved.isEmpty(key, savedHash) ? NOTHING : loadSaved(saved, key, savedHash, level);
				if (quick != null)
				{
					checkLater(key);
					if (quick == NOTHING)
					{
						absent.add(key);
						return null;
					}
					return quick;
				}
			}
		}
		checked.add(key);
		return drawChecked(key, level);
	}

	/** Puts a floor's pictures in memory, replacing what was there. */
	private void keep(int key, Level level, Drawn drawn)
	{
		if (drawn.full != null && level == Level.FULL)
		{
			images.get(Level.FULL).put(key, drawn.full);
		}
		if (drawn.full != null && level != Level.SMALL)
		{
			// a far view asks for the small pictures of the whole world: kept, as the class says, only at that size
			// (every floor on screen is held, so the half-size ones would pile up past their bound)
			images.get(Level.HALF).put(key, shrink(drawn.full, 2));
		}
		images.get(Level.SMALL).put(key, drawn.small);
	}

	private static Drawn loadSaved(MapStore saved, int key, long hash, Level level)
	{
		BufferedImage small = level == Level.SMALL ? saved.load(key, hash, MapStore.Kind.SMALL) : null;
		if (small != null)
		{
			return new Drawn(null, small);
		}
		BufferedImage full = saved.load(key, hash, MapStore.Kind.FULL);
		return full == null ? null : new Drawn(full, shrink(shrink(full, 2), 2));
	}

	/** True once the game's data could not be read: nothing is asked for again (every read would fail the same way). */
	private final java.util.concurrent.atomic.AtomicBoolean dataFailed = new java.util.concurrent.atomic.AtomicBoolean();

	/** Says once, where the player can find it, that the game's data cannot be read; every region would say it else. */
	private void warnOnce(MapDataException e)
	{
		// both render threads can fail at once: one of them says it
		if (dataFailed.compareAndSet(false, true))
		{
			log.warn("Advanced Map cannot read the game's map data (a game update may have changed it): the map stays"
				+ " blank until the plugin is updated", e);
		}
	}

	/** Checks a floor shown from disk against the game's data, once, behind the pictures waiting to be shown. */
	private void checkLater(int key)
	{
		if (checking.add(key))
		{
			try
			{
				executor.execute(() -> check(key));
			}
			catch (RejectedExecutionException e)
			{
				checking.remove(key);
			}
		}
	}

	/**
	 * Reads a region's game data, finds its icons, and when the saved picture was drawn from older data, draws it
	 * again (and saves it), keeping the old one on screen until the new one is ready. Render threads.
	 */
	private void check(int key)
	{
		try
		{
			int id = region(key);
			Region region = source.region(id >> 8, id & 0xFF);
			if (region != null && !iconsFound.contains(id) && findIcons(region))
			{
				iconsFound.add(id);
			}
			MapStore saved = store;
			Long savedHash = saved == null ? null : saved.savedHash(key);
			long hash = region == null ? 0 : source.regionHash(id >> 8, id & 0xFF);
			checked.add(key);
			if (savedHash == null || savedHash != hash)
			{
				// drawn again from the game's data while the old picture stays on screen, then swapped in
				Level level = images.get(Level.FULL).containsKey(key) ? Level.FULL
					: images.get(Level.HALF).containsKey(key) ? Level.HALF : Level.SMALL;
				Drawn fresh = drawChecked(key, level);
				if (fresh == null)
				{
					for (Level each : Level.values())
					{
						images.get(each).remove(key);
					}
				}
				else
				{
					absent.remove(key);
					keep(key, level, fresh);
				}
			}
		}
		catch (CancellationException e)
		{
			// the plugin stopped
		}
		catch (CacheNotReadyException e)
		{
			// checked again while the floor is on screen (retryCheck)
			recheck.add(key);
		}
		catch (MapDataException e)
		{
			warnOnce(e);
		}
		catch (RuntimeException e)
		{
			log.debug("could not check region {} floor {}", region(key), plane(key), e);
		}
		finally
		{
			checking.remove(key);
		}
	}

	/** The pictures of a region's floor drawn or loaded after reading the game's data to see they match. */
	private Drawn drawChecked(int key, Level level)
	{
		int plane = plane(key);
		int id = region(key);
		Region region = source.region(id >> 8, id & 0xFF);
		if (region == null)
		{
			absent.add(key(0, id));
			return null;
		}
		if (!iconsFound.contains(id) && findIcons(region))
		{
			iconsFound.add(id);
		}
		long hash = source.regionHash(id >> 8, id & 0xFF);
		MapStore saved = store;
		if (saved != null && saved.isEmpty(key, hash))
		{
			absent.add(key);
			return null;
		}
		if (plane > 0 && !hasFloor(region, plane))
		{
			absent.add(key);
			if (saved != null)
			{
				saved.saveEmpty(key, hash);
			}
			return null;
		}
		if (saved != null)
		{
			BufferedImage small = level == Level.SMALL ? saved.load(key, hash, MapStore.Kind.SMALL) : null;
			if (small != null)
			{
				return new Drawn(null, small);
			}
			BufferedImage full = saved.load(key, hash, MapStore.Kind.FULL);
			if (full != null)
			{
				return new Drawn(full, shrink(shrink(full, 2), 2));
			}
		}
		BufferedImage full = renderer.drawRegion(region, plane);
		BufferedImage small = shrink(shrink(full, 2), 2);
		if (saved != null)
		{
			saved.save(key, hash, full, small);
		}
		return new Drawn(full, small);
	}

	/** True when a region has ground or anything placed on a floor above the ground. */
	static boolean hasFloor(Region region, int plane)
	{
		for (int x = 0; x < Region.X; x++)
		{
			for (int y = 0; y < Region.Y; y++)
			{
				if (region.getUnderlayId(plane, x, y) > 0 || region.getOverlayId(plane, x, y) > 0)
				{
					return true;
				}
			}
		}
		for (Location location : region.getLocations())
		{
			if (location.getPosition().getZ() == plane)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds a region's icons without drawing it, once: search lists every bank or anvil, also of regions not looked
	 * at yet. Low-priority thread.
	 */
	void scanIcons(int regionId)
	{
		if (iconsFound.contains(regionId))
		{
			return;
		}
		Region region = source.region(regionId >> 8, regionId & 0xFF);
		if (region == null || findIcons(region))
		{
			iconsFound.add(regionId);
		}
	}

	/** True once a region's icons were found with every table read, so they need not be found again. */
	boolean iconsFound(int regionId)
	{
		return iconsFound.contains(regionId);
	}

	void setTablesRead(java.util.function.BooleanSupplier read)
	{
		tablesRead = read;
	}

	/**
	 * Hands the icons a region's objects put on the game's map, on every floor, to {@link MapIcons}, with Advanced
	 * Map's own ({@link FoundIcons}). An icon is on the floor it is drawn on: an object over a bridge tile is stored a
	 * floor up but drawn a floor lower, as the map is.
	 */
	/** Finds a region's icons; false when the tables were not all read yet, so that it is done again later. */
	private boolean findIcons(Region region)
	{
		boolean complete = tablesRead.getAsBoolean();
		List<MapSpot> spots = new ArrayList<>();
		for (Location location : region.getLocations())
		{
			int areaId = source.object(location.getId()).getMapAreaId();
			if (areaId == -1)
			{
				continue;
			}
			int category = source.areaCategory(areaId);
			if (category == IconGroup.MAP_LINK)
			{
				continue;
			}
			icons.addSprite(areaId, source.areaIcon(areaId));
			int x = location.getPosition().getX();
			int y = location.getPosition().getY();
			spots.add(MapSpot.icon(x, y, floorOf(region, x, y, location.getPosition().getZ()), areaId, category));
		}
		List<MapSpot> all = new ArrayList<>(FoundIcons.find(region, source, spots));
		for (int i = spots.size(); i < all.size(); i++)
		{
			icons.addSprite(all.get(i).getAreaId(), source.areaIcon(all.get(i).getAreaId()));
		}
		addLinkIcons(region, all);
		addTransportIcons(region, all);
		icons.addRegion(region.getRegionID(), all);
		return complete;
	}

	/**
	 * The game's map link icon on each link spot in a region that has no icon of its own within {@link #LINK_REACH}
	 * tiles: mostly the ways out of dungeons. A dungeon's entrance usually has its dungeon icon already.
	 */
	private void addLinkIcons(Region region, List<MapSpot> all)
	{
		Map<Integer, Integer> links = source.mapLinks();
		int linkArea = source.categoryArea(IconGroup.MAP_LINK);
		if (linkArea < 0)
		{
			return;
		}
		for (int from : links.keySet())
		{
			int x = MapLinks.x(from);
			int y = MapLinks.y(from);
			if ((x >> 6) != region.getRegionX() || (y >> 6) != region.getRegionY())
			{
				continue;
			}
			boolean marked = false;
			for (MapSpot spot : all)
			{
				marked |= spot.getPlane() == MapLinks.plane(from) && Math.abs(spot.getX() - x) <= LINK_REACH
					&& Math.abs(spot.getY() - y) <= LINK_REACH;
			}
			if (!marked)
			{
				icons.addSprite(linkArea, source.areaIcon(linkArea));
				all.add(MapSpot.icon(x, y, MapLinks.plane(from), linkArea, IconGroup.MAP_LINK));
			}
		}
	}

	/**
	 * A Transportation icon on each place to board a transport that the game's map shows none near (the Keldagrim
	 * ferry's jetties on the River Kelda): a click on it draws where it goes, as on any transport icon.
	 */
	private void addTransportIcons(Region region, List<MapSpot> all)
	{
		int transportArea = source.categoryArea(IconGroup.TRANSPORT);
		if (transportArea < 0)
		{
			return;
		}
		for (WorldPoint at : Transports.boardingSpots())
		{
			if ((at.getX() >> 6) != region.getRegionX() || (at.getY() >> 6) != region.getRegionY())
			{
				continue;
			}
			boolean marked = false;
			for (MapSpot spot : all)
			{
				marked |= marksTransport(spot.getGroup()) && spot.getPlane() == at.getPlane()
					&& Math.abs(spot.getX() - at.getX()) <= TRANSPORT_REACH && Math.abs(spot.getY() - at.getY()) <= TRANSPORT_REACH;
			}
			if (!marked && !transportIconBeside(region, at))
			{
				icons.addSprite(transportArea, source.areaIcon(transportArea));
				all.add(MapSpot.icon(at.getX(), at.getY(), at.getPlane(), transportArea, IconGroup.TRANSPORT));
			}
		}
	}

	/** True for an icon that already shows a place to board: a transport, a farming patch (a planted spirit tree), a dock. */
	private static boolean marksTransport(IconGroup group)
	{
		return group == IconGroup.TRANSPORTATION || group == IconGroup.FARMING || group == IconGroup.SAILING;
	}

	/** True when a neighboring region's objects put such an icon within {@link #TRANSPORT_REACH} of a place to board. */
	private boolean transportIconBeside(Region own, WorldPoint at)
	{
		for (int rx = (at.getX() - TRANSPORT_REACH) >> 6; rx <= (at.getX() + TRANSPORT_REACH) >> 6; rx++)
		{
			for (int ry = (at.getY() - TRANSPORT_REACH) >> 6; ry <= (at.getY() + TRANSPORT_REACH) >> 6; ry++)
			{
				Region near = rx == own.getRegionX() && ry == own.getRegionY() ? null : source.region(rx, ry);
				if (near == null)
				{
					continue;
				}
				for (Location location : near.getLocations())
				{
					int areaId = source.object(location.getId()).getMapAreaId();
					int x = location.getPosition().getX();
					int y = location.getPosition().getY();
					if (areaId != -1 && marksTransport(IconGroup.of(source.areaCategory(areaId)))
						&& Math.abs(x - at.getX()) <= TRANSPORT_REACH && Math.abs(y - at.getY()) <= TRANSPORT_REACH
						&& floorOf(near, x, y, location.getPosition().getZ()) == at.getPlane())
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The floor an object on a tile is drawn on: a floor lower over a bridge tile. */
	static int floorOf(Region region, int x, int y, int z)
	{
		boolean bridge = (region.getTileSetting(1, x - region.getBaseX(), y - region.getBaseY()) & 2) != 0;
		return bridge && z > 0 ? z - 1 : z;
	}

	/** Averages each square of {@code by} x {@code by} pixels into one, see-through parts included. */
	static BufferedImage shrink(BufferedImage image, int by)
	{
		int w = image.getWidth() / by;
		int h = image.getHeight() / by;
		int[] in = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
		int[] out = new int[w * h];
		int n = by * by;
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				int a = 0;
				int r = 0;
				int g = 0;
				int b = 0;
				for (int dy = 0; dy < by; dy++)
				{
					int row = (y * by + dy) * image.getWidth() + x * by;
					for (int dx = 0; dx < by; dx++)
					{
						int argb = in[row + dx];
						int alpha = argb >>> 24;
						a += alpha;
						r += (argb >> 16 & 0xFF) * alpha;
						g += (argb >> 8 & 0xFF) * alpha;
						b += (argb & 0xFF) * alpha;
					}
				}
				out[y * w + x] = a == 0 ? 0 : a / n << 24 | r / a << 16 | g / a << 8 | b / a;
			}
		}
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		small.setRGB(0, 0, w, h, out, 0, w);
		return small;
	}
}
