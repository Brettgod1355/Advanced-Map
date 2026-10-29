/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.FontManager;

/**
 * The box on the map's right for making or changing a marker: its name (typed while the box is open), one of the
 * {@link MarkerIcons.Design pictures}, one of the colors; Save, Remove (for a marker already made) and Cancel.
 */
final class MarkerEditor
{
	static final int WIDTH = SideList.WIDTH;
	static final int CELL = 34;
	static final int PER_ROW = 6;
	static final int SWATCH = 20;
	private static final int PAD = 8;
	private static final int HEADER = 26;
	private static final int FIELD = 24;
	private static final int BUTTON = 24;

	private static final Color PANEL = new Color(0x10, 0x18, 0x22, 240);
	private static final Color EDGE = new Color(0x5A5040);
	private static final Color FOCUS = new Color(0xC8AA6E);
	private static final Color TEXT = new Color(0xE8E2D0);
	private static final Color QUIET = new Color(0x8C8676);
	private static final Color CHOSEN = new Color(255, 255, 255, 50);
	private static final Color BUTTON_FILL = new Color(0x2A3440);

	enum Part
	{
		OUTSIDE,
		PANEL,
		NAME,
		DESIGN,
		COLOR,
		SAVE,
		REMOVE,
		CANCEL
	}

	@Value
	static class Hit
	{
		Part part;
		int index;
	}

	@Value
	static class Layout
	{
		Rectangle panel;
		Rectangle name;
		List<Rectangle> designs;
		List<Rectangle> colors;
		Rectangle save;
		Rectangle remove;
		Rectangle cancel;
	}

	private volatile boolean open;
	/** The marker being changed, or null for a new one. */
	private volatile MapPins.Marker editing;
	private volatile WorldPoint point;
	private volatile String name = "";
	private volatile MarkerIcons.Design design = MarkerIcons.Design.PIN;
	private volatile int color;

	boolean isOpen()
	{
		return open;
	}

	/** Opens the box for a new marker at a tile, with the last picture and color chosen. */
	void create(WorldPoint at)
	{
		editing = null;
		point = at;
		name = "";
		open = true;
	}

	void edit(MapPins.Marker marker)
	{
		editing = marker;
		point = marker.getPoint();
		name = marker.getName();
		design = marker.getDesign();
		color = marker.getColor();
		open = true;
	}

	void close()
	{
		open = false;
		editing = null;
	}

	MapPins.Marker editing()
	{
		return editing;
	}

	/** The marker as the box has it now: a changed marker keeps its id and account; a new one is made on {@code account}. */
	MapPins.Marker result(String account)
	{
		MapPins.Marker old = editing;
		return new MapPins.Marker(old != null ? old.getId() : MarkerStore.newId(), point, design, color,
			MapPins.cleanName(name), old != null ? old.getAccount() : account == null ? "" : account);
	}

	String name()
	{
		return name;
	}

	void type(char c)
	{
		if (name.length() < 40)
		{
			name = name + c;
		}
	}

	void backspace()
	{
		String now = name;
		name = now.isEmpty() ? now : now.substring(0, now.length() - 1);
	}

	void choose(Hit hit)
	{
		if (hit.getPart() == Part.DESIGN)
		{
			design = MarkerIcons.Design.values()[hit.getIndex()];
		}
		else if (hit.getPart() == Part.COLOR)
		{
			color = hit.getIndex();
		}
	}

	/** Where the box and its parts are: on the right, where the side list goes. */
	static Layout layout(Map<MapPainter.Button, Rectangle> buttons)
	{
		int designRows = (MarkerIcons.Design.values().length + PER_ROW - 1) / PER_ROW;
		int content = PAD + FIELD + PAD + designRows * CELL + PAD + SWATCH + PAD * 2 + BUTTON + PAD;
		SideList.Layout around = SideList.layout(buttons, content);
		Rectangle panel = new Rectangle(around.getPanel().x, around.getPanel().y, WIDTH, HEADER + content);
		int x = panel.x + PAD;
		int y = panel.y + HEADER + PAD;
		Rectangle field = new Rectangle(x, y, WIDTH - PAD * 2, FIELD);
		y += FIELD + PAD;
		List<Rectangle> designs = new ArrayList<>();
		int gridX = panel.x + (WIDTH - PER_ROW * CELL) / 2;
		for (int i = 0; i < MarkerIcons.Design.values().length; i++)
		{
			designs.add(new Rectangle(gridX + i % PER_ROW * CELL, y + i / PER_ROW * CELL, CELL, CELL));
		}
		y += designRows * CELL + PAD;
		List<Rectangle> colors = new ArrayList<>();
		int gap = (WIDTH - PAD * 2 - MarkerIcons.COLORS.length * SWATCH) / (MarkerIcons.COLORS.length - 1);
		for (int i = 0; i < MarkerIcons.COLORS.length; i++)
		{
			colors.add(new Rectangle(x + i * (SWATCH + gap), y, SWATCH, SWATCH));
		}
		y += SWATCH + PAD * 2;
		int third = (WIDTH - PAD * 4) / 3;
		Rectangle save = new Rectangle(x, y, third, BUTTON);
		Rectangle remove = new Rectangle(x + third + PAD, y, third, BUTTON);
		Rectangle cancel = new Rectangle(x + (third + PAD) * 2, y, third, BUTTON);
		return new Layout(panel, field, designs, colors, save, remove, cancel);
	}

	Hit hitAt(int x, int y, Layout layout)
	{
		if (!layout.getPanel().contains(x, y))
		{
			return new Hit(Part.OUTSIDE, -1);
		}
		if (layout.getName().contains(x, y))
		{
			return new Hit(Part.NAME, -1);
		}
		for (int i = 0; i < layout.getDesigns().size(); i++)
		{
			if (layout.getDesigns().get(i).contains(x, y))
			{
				return new Hit(Part.DESIGN, i);
			}
		}
		for (int i = 0; i < layout.getColors().size(); i++)
		{
			if (layout.getColors().get(i).contains(x, y))
			{
				return new Hit(Part.COLOR, i);
			}
		}
		if (layout.getSave().contains(x, y))
		{
			return new Hit(Part.SAVE, -1);
		}
		if (editing != null && layout.getRemove().contains(x, y))
		{
			return new Hit(Part.REMOVE, -1);
		}
		if (layout.getCancel().contains(x, y))
		{
			return new Hit(Part.CANCEL, -1);
		}
		return new Hit(Part.PANEL, -1);
	}

	void draw(Graphics2D g, Layout layout, Point mouse)
	{
		Rectangle panel = layout.getPanel();
		g.setColor(PANEL);
		g.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 8, 8);
		g.setColor(EDGE);
		g.drawRoundRect(panel.x, panel.y, panel.width - 1, panel.height - 1, 8, 8);
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics bold = g.getFontMetrics();
		g.setColor(TEXT);
		g.drawString(editing == null ? "New marker" : "Edit marker", panel.x + PAD,
			panel.y + (HEADER + bold.getAscent() - bold.getDescent()) / 2);

		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = g.getFontMetrics();
		Rectangle field = layout.getName();
		g.setColor(new Color(0, 0, 0, 170));
		g.fillRoundRect(field.x, field.y, field.width, field.height, 6, 6);
		g.setColor(FOCUS);
		g.drawRoundRect(field.x, field.y, field.width - 1, field.height - 1, 6, 6);
		int baseline = field.y + (field.height + metrics.getAscent() - metrics.getDescent()) / 2;
		if (name.isEmpty())
		{
			g.setColor(QUIET);
			g.drawString("Name (optional)", field.x + PAD, baseline);
			g.setColor(TEXT);
			g.drawString(Caret.draw("|"), field.x + PAD - 3, baseline);
		}
		else
		{
			String shown = name + "|";
			while (metrics.stringWidth(shown) > field.width - PAD * 2 && shown.length() > 1)
			{
				shown = shown.substring(1);
			}
			shown = Caret.draw(shown);
			g.setColor(TEXT);
			g.drawString(shown, field.x + PAD, baseline);
		}

		MarkerIcons.Design[] designs = MarkerIcons.Design.values();
		for (int i = 0; i < designs.length; i++)
		{
			Rectangle cell = layout.getDesigns().get(i);
			if (designs[i] == design || mouse != null && cell.contains(mouse))
			{
				g.setColor(designs[i] == design ? CHOSEN : new Color(255, 255, 255, 22));
				g.fillRoundRect(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2, 6, 6);
			}
			MarkerIcons.draw(g, designs[i], MarkerIcons.color(color), cell.x + (CELL - 22) / 2.0, cell.y + (CELL - 22) / 2.0,
				22);
		}
		for (int i = 0; i < MarkerIcons.COLORS.length; i++)
		{
			Rectangle swatch = layout.getColors().get(i);
			g.setColor(MarkerIcons.COLORS[i]);
			g.fillOval(swatch.x, swatch.y, swatch.width, swatch.height);
			g.setColor(i == color ? TEXT : EDGE);
			g.drawOval(swatch.x - (i == color ? 2 : 0), swatch.y - (i == color ? 2 : 0), swatch.width + (i == color ? 3 : -1),
				swatch.height + (i == color ? 3 : -1));
		}
		button(g, metrics, layout.getSave(), "Save", true, mouse);
		button(g, metrics, layout.getRemove(), "Remove", editing != null, mouse);
		button(g, metrics, layout.getCancel(), "Cancel", true, mouse);
	}

	private static void button(Graphics2D g, FontMetrics metrics, Rectangle at, String text, boolean enabled, Point mouse)
	{
		g.setColor(enabled && mouse != null && at.contains(mouse) ? new Color(0x3A4654) : BUTTON_FILL);
		g.fillRoundRect(at.x, at.y, at.width, at.height, 6, 6);
		g.setColor(EDGE);
		g.drawRoundRect(at.x, at.y, at.width - 1, at.height - 1, 6, 6);
		g.setColor(enabled ? TEXT : QUIET);
		g.drawString(text, at.x + (at.width - metrics.stringWidth(text)) / 2,
			at.y + (at.height + metrics.getAscent() - metrics.getDescent()) / 2);
	}
}
