/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

/**
 * Advanced Map's sidebar tab: the title with buttons to the Discord server and the GitHub repository, a button that
 * opens and closes the map, a button that shows or hides how the map works, and how much of the map is saved on this
 * computer, with a button to draw it all again. Scrolls when the explanation is taller than the sidebar. Swing thread
 * only.
 */
class AdvancedMapPanel extends PluginPanel
{
	/** How often the panel updates its button and the saved map's progress while it is shown. */
	private static final int REFRESH_MS = 1000;
	/**
	 * Wide enough for the sidebar, so the text wraps by whole words. In points: Swing's HTML takes a point as one
	 * screen pixel and a CSS pixel as 1.3.
	 */
	private static final int TEXT_WIDTH = 190;
	static final String DISCORD_URL = "https://discord.gg/c85DK83jWx";
	static final String GITHUB_URL = "https://github.com/Brettgod1355/Advanced-Map";
	/** The link buttons beside the title, and the logos in them, as in the owner's other plugins. */
	static final int LINK_ICON_SIZE = 16;
	private static final int LINK_BUTTON_SIZE = 24;

	private final AdvancedMapPlugin plugin;
	private final JButton open = new JButton();
	private final JButton help = new JButton();
	private final JLabel helpText = text(explanation());
	private final JLabel saved = new JLabel();	private final Timer refresh;

	AdvancedMapPanel(AdvancedMapPlugin plugin, String keyName)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Advanced Map");
		title.setFont(FontManager.getRunescapeBoldFont());
		// the community and the code, one click from the title
		JPanel links = new JPanel(new GridLayout(1, 2, 4, 0));
		links.setOpaque(false);
		links.add(link("discord_white.png", "discord_blurple.png",
			"Advanced Map on Discord: ask a question, make a suggestion or report a bug", DISCORD_URL));
		links.add(link("github_white.png", "github_green.png", "Advanced Map on GitHub: code, bug reports and requests",
			GITHUB_URL));
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.add(title, BorderLayout.WEST);
		titleRow.add(links, BorderLayout.EAST);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, LINK_BUTTON_SIZE));
		content.add(left(titleRow));
		content.add(Box.createVerticalStrut(8));

		open.addActionListener(e ->
		{
			plugin.toggleMap();
			update();
		});
		content.add(left(open));
		content.add(Box.createVerticalStrut(8));
		content.add(left(text(keyName + " also opens and closes it, and Escape closes it.")));
		content.add(Box.createVerticalStrut(8));

		help.addActionListener(e -> showHelp(!helpText.isVisible()));
		content.add(left(help));
		content.add(Box.createVerticalStrut(6));
		content.add(left(helpText));
		showHelp(false);
		content.add(Box.createVerticalStrut(14));

		JLabel savedTitle = new JLabel("Saved map");
		savedTitle.setFont(FontManager.getRunescapeBoldFont());
		content.add(left(savedTitle));
		content.add(Box.createVerticalStrut(4));
		content.add(left(saved));
		content.add(Box.createVerticalStrut(8));
		JButton redraw = new JButton("Draw the map again");
		redraw.setToolTipText("Delete the map saved on this computer and save every region again from the game's data");
		redraw.addActionListener(e ->
		{
			int answer = JOptionPane.showConfirmDialog(this,
				"Delete the map saved on this computer and save every region again from the game's data?\n"
					+ "What this client has already drawn stays on the map until it restarts;\n"
					+ "everything else is drawn again as you look around.",
				"Draw the map again", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer == JOptionPane.OK_OPTION)
			{
				plugin.clearSavedMap();
				update();
			}
		});
		content.add(left(redraw));

		add(content, BorderLayout.NORTH);
		refresh = new Timer(REFRESH_MS, e -> update());
		update();
	}

	@Override
	public void onActivate()
	{
		update();
		refresh.start();
	}

	@Override
	public void onDeactivate()
	{
		refresh.stop();
	}

	void stop()
	{
		refresh.stop();
	}

	/**
	 * A square black button that opens a web page in the browser, showing the brand's white logo and, while the mouse
	 * is over it, the brand's colored logo on the same black. Both are the brands' own files, only scaled: neither
	 * Discord nor GitHub allows recoloring their logos, so the button swaps between their versions instead (see
	 * THIRD_PARTY_NOTICES).
	 */
	private static JLabel link(String restingFile, String hoverFile, String tooltip, String url)
	{
		BufferedImage restingLogo = logo(restingFile);
		ImageIcon resting = new ImageIcon(restingLogo);
		ImageIcon hovered = new ImageIcon(logoLike(hoverFile, restingLogo));
		JLabel label = new JLabel(resting);
		label.setOpaque(true);
		label.setBackground(Color.BLACK);
		label.setHorizontalAlignment(JLabel.CENTER);
		Dimension size = new Dimension(LINK_BUTTON_SIZE, LINK_BUTTON_SIZE);
		label.setPreferredSize(size);
		label.setMinimumSize(size);
		label.setMaximumSize(size);
		label.setToolTipText(tooltip);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(url);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setIcon(hovered);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setIcon(resting);
			}
		});
		return label;
	}

	/** A brand logo scaled to fit the link button's square, keeping its proportions. */
	static BufferedImage logo(String file)
	{
		BufferedImage image = ImageUtil.loadImageResource(AdvancedMapPanel.class, file);
		double scale = Math.min((double) LINK_ICON_SIZE / image.getWidth(), (double) LINK_ICON_SIZE / image.getHeight());
		return ImageUtil.resizeImage(image, Math.max(1, (int) Math.round(image.getWidth() * scale)),
			Math.max(1, (int) Math.round(image.getHeight() * scale)));
	}

	/** The other version of a logo at exactly the size of the first, so hovering never shifts it by a pixel. */
	static BufferedImage logoLike(String file, BufferedImage like)
	{
		return ImageUtil.resizeImage(ImageUtil.loadImageResource(AdvancedMapPanel.class, file), like.getWidth(),
			like.getHeight());
	}

	private void showHelp(boolean shown)
	{
		helpText.setVisible(shown);
		help.setText(shown ? "Hide how the map works" : "How the map works");
		revalidate();
	}

	/** What each part of the map does, a short paragraph each ({@link MapHelp}). */
	static String explanation()
	{
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < MapHelp.SECTIONS.length; i++)
		{
			out.append("<b>").append(MapHelp.SECTIONS[i][0]).append("</b><br>").append(MapHelp.SECTIONS[i][1]);
			if (i < MapHelp.SECTIONS.length - 1)
			{
				out.append("<br><br>");
			}
		}
		return out.toString();
	}

	private void update()
	{
		open.setText(plugin.isOpen() ? "Close the map" : "Open the map");
		saved.setText(wrap(plugin.savedMapStatus()));
	}

	private static JLabel text(String text)
	{
		JLabel label = new JLabel(wrap(text));
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static String wrap(String text)
	{
		return "<html><body style='width: " + TEXT_WIDTH + "pt'>" + text + "</body></html>";
	}

	/**
	 * A row of its own, as wide as the sidebar and as tall as what is in it now, also after its text changes (a button
	 * or label sized once would keep the height of its first text, or none).
	 */
	private static Component left(javax.swing.JComponent component)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(component);
		return row;
	}
}
