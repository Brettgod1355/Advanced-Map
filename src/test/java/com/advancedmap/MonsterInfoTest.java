/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.runelite.client.ui.FontManager;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MonsterInfoTest
{
	private static final String LINE = "Black dragon\t190-250\t21 (Melee), 50 (Dragonfire)\tSlash, Dragonfire\tAggressive"
		+ "\t\tKonar, Nieve, Duradel, Krystilia\tWater spells 50%\tDragon bones; Black dragonhide\tDraconic visage 1/10,000\n";

	private static Map<String, MonsterInfo.Monster> parse(String text) throws Exception
	{
		return MonsterInfo.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	public void aLineIsReadIntoEachField() throws Exception
	{
		MonsterInfo.Monster dragon = parse("# header\n" + LINE).get("Black dragon");
		assertEquals("190-250", dragon.getHitpoints());
		assertEquals("Konar, Nieve, Duradel, Krystilia", dragon.getMasters());
		assertEquals(2, dragon.getAlways().size());
		assertEquals("Draconic visage 1/10,000", dragon.getRare().get(0));
		assertEquals("", dragon.getSlayerLevel());
	}

	@Test
	public void aShortLineStillReads() throws Exception
	{
		MonsterInfo.Monster cow = parse("Cow\t8\t1\n").get("Cow");
		assertEquals("8", cow.getHitpoints());
		assertTrue(cow.getAlways().isEmpty() && cow.getRare().isEmpty() && cow.getMasters().isEmpty());
	}

	@Test
	public void theRowsTellTheStatsSlayerDropsThenTheWikiButton() throws Exception
	{
		List<SideList.Row> rows = MonsterInfo.rows(parse(LINE).get("Black dragon"));
		assertEquals(4, rows.size());
		assertEquals("Hitpoints 190-250", rows.get(0).getText());
		assertTrue(rows.get(0).subLines().contains("Weak to water spells 50%"));
		assertEquals("Slayer task", rows.get(1).getText());
		assertEquals("Drops", rows.get(2).getText());
		assertTrue(rows.get(2).subLines().contains("Draconic visage 1/10,000"));
		SideList.Row wiki = rows.get(3);
		assertNull(wiki.getPoint());
		assertEquals("https://oldschool.runescape.wiki/w/Black_dragon",
			((MonsterInfo.WikiPage) wiki.getTarget()).getUrl());
	}

	@Test
	public void longLinesWrapBetweenWordsAndNothingIsCut() throws Exception
	{
		String vorkath = "Vorkath\t750\t30 (Magic), 32 (Ranged), 32 (Melee), 80 (Dragonfire), 121 (Dragonfire Bomb/Special),"
			+ " 41 (Rapid Fire/Special)\tSlash\t\t\t\t\t\t\n";
		SideList.Row stats = MonsterInfo.rows(parse(vorkath).get("Vorkath")).get(0);
		FontMetrics metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
			.getFontMetrics(FontManager.getRunescapeSmallFont());
		StringBuilder joined = new StringBuilder();
		for (String line : stats.subLines())
		{
			assertTrue(line, metrics.stringWidth(line) <= SideList.WIDTH - 24);
			joined.append(joined.length() > 0 ? " " : "").append(line);
		}
		assertTrue(joined.toString().startsWith("Max hit 30 (Magic), 32 (Ranged), 32 (Melee), 80 (Dragonfire), 121"
			+ " (Dragonfire Bomb/Special), 41 (Rapid Fire/Special)"));
		assertTrue(stats.subLines().size() > 2);
	}

	@Test
	public void aNameWithAnApostropheMakesAWorkingLink()
	{
		assertEquals("https://oldschool.runescape.wiki/w/Kree%27arra", MonsterInfo.wikiUrl("Kree'arra"));
	}

	@Test
	public void everySearchableMonsterHasALine() throws Exception
	{
		Map<String, MonsterInfo.Monster> all;
		try (InputStream in = MonsterInfo.class.getResourceAsStream("monsters.tsv"))
		{
			assertNotNull(in);
			all = MonsterInfo.parse(in);
		}
		List<Spawns.Creature> creatures;
		try (InputStream in = Spawns.class.getResourceAsStream("spawns.tsv"))
		{
			creatures = Spawns.parse(in);
		}
		for (Spawns.Creature creature : creatures)
		{
			if (creature.isMonster())
			{
				assertNotNull(creature.getName(), all.get(creature.getName()));
			}
		}
		assertEquals("85", all.get("Abyssal demon").getSlayerLevel());
	}
}
