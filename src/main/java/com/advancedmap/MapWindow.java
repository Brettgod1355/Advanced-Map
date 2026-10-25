/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/**
 * The map in a window of its own, to keep beside the game (on another screen, say) while the game's own world map is
 * open. RuneLite's window is never touched. Swing thread only.
 */
final class MapWindow extends JFrame
{
	/** How often the window redraws, to show regions as they are drawn and the player as they move. */
	private static final int REDRAW_MS = 50;

	private final Timer redraw;

	MapWindow(AdvancedMapPlugin plugin, MapPainter painter, Consumer<MapWindow> onClosed)
	{
		super("Advanced Map");
		setIconImage(MapIcon.draw(32));
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		MapPanel panel = new MapPanel(plugin, painter);
		setContentPane(panel);
		setMinimumSize(new Dimension(400, 300));
		setSize(1100, 750);
		setLocationByPlatform(true);
		redraw = new Timer(REDRAW_MS, e -> panel.repaint());
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				redraw.stop();
				onClosed.accept(MapWindow.this);
			}
		});
		panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
		panel.getActionMap().put("close", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				dispose();
			}
		});
		redraw.start();
	}

	/** The drawing surface: the shared painter, and the mouse for dragging, zooming and the buttons. */
	private static final class MapPanel extends JPanel
	{
		private final AdvancedMapPlugin plugin;
		private final MapPainter painter;
		private Point mouse;
		private Point dragFrom;

		MapPanel(AdvancedMapPlugin plugin, MapPainter painter)
		{
			this.plugin = plugin;
			this.painter = painter;
			setOpaque(true);
			MouseAdapter input = new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					MapPainter.Button button = SwingUtilities.isLeftMouseButton(e)
						? MapPainter.buttonAt(e.getX(), e.getY(), getWidth(), true) : null;
					if (button == MapPainter.Button.AREA)
					{
						plugin.switchArea();
					}
					else if (button == MapPainter.Button.MODE)
					{
						plugin.dock();
					}
					else
					{
						dragFrom = e.getPoint();
					}
					repaint();
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					mouse = e.getPoint();
					if (dragFrom != null)
					{
						plugin.viewport().drag(e.getX() - dragFrom.x, e.getY() - dragFrom.y);
						dragFrom = e.getPoint();
					}
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					dragFrom = null;
				}

				@Override
				public void mouseMoved(MouseEvent e)
				{
					mouse = e.getPoint();
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					mouse = null;
					repaint();
				}

				@Override
				public void mouseWheelMoved(MouseWheelEvent e)
				{
					double factor = Math.pow(MapInput.WHEEL_STEP, -e.getPreciseWheelRotation());
					plugin.viewport().zoomAt(factor, e.getX(), e.getY(), getWidth(), getHeight());
					repaint();
				}
			};
			addMouseListener(input);
			addMouseMotionListener(input);
			addMouseWheelListener(input);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			painter.paint((Graphics2D) g, plugin.viewport().view(getWidth(), getHeight()), plugin.area(),
				plugin.playerLocation(), mouse, true);
		}
	}
}
