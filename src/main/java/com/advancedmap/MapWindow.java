/*
 * SPDX-License-Identifier: BSD-2-Clause
 * Copyright (c) 2026, Brettgod1355 <github.com/Brettgod1355>
 * See LICENSE for redistribution conditions and disclaimer.
 */
package com.advancedmap;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
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
	static final int MIN_WIDTH = 400;
	static final int MIN_HEIGHT = 300;
	/** How much of a saved window's top must be on a screen to put it back there: enough to take hold of it. */
	private static final int GRIP_WIDTH = 120;
	private static final int GRIP_HEIGHT = 30;

	/**
	 * The saved place and size, or null when there is none, it does not read, or its title bar is no longer on any
	 * screen (a monitor unplugged since, a lower resolution).
	 */
	static Rectangle restoredBounds(String saved, List<Rectangle> screens)
	{
		if (saved == null || saved.isEmpty())
		{
			return null;
		}
		String[] parts = saved.split(",");
		if (parts.length != 4)
		{
			return null;
		}
		Rectangle bounds;
		try
		{
			bounds = new Rectangle(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()),
				Math.max(MIN_WIDTH, Integer.parseInt(parts[2].trim())),
				Math.max(MIN_HEIGHT, Integer.parseInt(parts[3].trim())));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		Rectangle grip = new Rectangle(bounds.x, bounds.y, bounds.width, GRIP_HEIGHT);
		for (Rectangle screen : screens)
		{
			Rectangle shown = screen.intersection(grip);
			if (!shown.isEmpty() && shown.width >= GRIP_WIDTH && shown.height >= GRIP_HEIGHT / 2)
			{
				return bounds;
			}
		}
		return null;
	}

	private void save(Consumer<String> saveBounds)
	{
		Rectangle at = getBounds();
		saveBounds.accept(at.x + "," + at.y + "," + at.width + "," + at.height);
	}

	/** The desktop's screens, where each one sits. */
	private static List<Rectangle> screens()
	{
		List<Rectangle> out = new ArrayList<>();
		for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			out.add(device.getDefaultConfiguration().getBounds());
		}
		return out;
	}

	/** How often the window redraws, to show regions as they are drawn and the player as they move. */
	private static final int REDRAW_MS = 50;

	private final Timer redraw;
	private final Timer saveSoon;
	private static final int SAVE_DELAY_MS = 1000;
	/** Set while the player has the window minimized, from the window's own events. */
	private volatile boolean minimized;

	/**
	 * Opens where the window was last ({@code savedBounds}, "x,y,width,height") when that is still on a screen, else
	 * at the default size where the system puts new windows; {@code saveBounds} gets its place and size on closing.
	 */
	MapWindow(AdvancedMapPlugin plugin, MapPainter painter, Consumer<MapWindow> onClosed, String savedBounds,
		Consumer<String> saveBounds)
	{
		super("Advanced Map");
		setIconImage(MapIcon.draw(32));
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		MapPanel panel = new MapPanel(plugin, painter);
		setContentPane(panel);
		setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
		Rectangle restored = restoredBounds(savedBounds, screens());
		if (restored != null)
		{
			setBounds(restored);
		}
		else
		{
			setSize(1100, 750);
			setLocationByPlatform(true);
		}
		redraw = new Timer(REDRAW_MS, e -> panel.repaint());
		// saved a moment after the last move or resize too, in case RuneLite is quit with the window still open
		saveSoon = new Timer(SAVE_DELAY_MS, e -> save(saveBounds));
		saveSoon.setRepeats(false);
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentMoved(ComponentEvent e)
			{
				saveSoon.restart();
			}

			@Override
			public void componentResized(ComponentEvent e)
			{
				saveSoon.restart();
			}
		});
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowIconified(WindowEvent e)
			{
				minimized = true;
			}

			@Override
			public void windowDeiconified(WindowEvent e)
			{
				minimized = false;
			}

			@Override
			public void windowClosed(WindowEvent e)
			{
				redraw.stop();
				saveSoon.stop();
				save(saveBounds);
				onClosed.accept(MapWindow.this);
			}
		});
		panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
		panel.getActionMap().put("close", new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				if (plugin.selection() != null || plugin.typing() || plugin.sideListOpen())
				{
					plugin.escape();
				}
				else
				{
					dispose();
				}
			}
		});
		redraw.start();
	}

	boolean isMinimized()
	{
		return minimized;
	}

	/** The drawing surface: the shared painter, and the mouse for dragging, zooming and the buttons. */
	private static final class MapPanel extends JPanel
	{
		private final AdvancedMapPlugin plugin;
		private final MapPainter painter;
		private Point mouse;
		private Point dragFrom;
		private Point pressedAt;

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
					requestFocusInWindow();
					if (SwingUtilities.isLeftMouseButton(e)
						&& plugin.pressPanels(x(e), y(e), width(), height(), true))
					{
						repaint();
						return;
					}
					if (SwingUtilities.isRightMouseButton(e))
					{
						plugin.rightClick(x(e), y(e), width(), height());
						repaint();
						return;
					}
					MapPainter.Button button = SwingUtilities.isLeftMouseButton(e)
						? MapPainter.buttonAt(x(e), y(e), width(), height(), true, plugin.settings()) : null;
					if (button != null)
					{
						plugin.pressButton(button, true);
					}
					else if (!plugin.pressMenu(x(e), y(e), width(), height())
						&& SwingUtilities.isLeftMouseButton(e))
					{
						dragFrom = point(e);
						pressedAt = point(e);
					}
					repaint();
				}

				@Override
				public void mouseDragged(MouseEvent e)
				{
					mouse = point(e);
					if (dragFrom != null)
					{
						plugin.viewport().drag(x(e) - dragFrom.x, y(e) - dragFrom.y);
						dragFrom = point(e);
					}
					repaint();
				}

				@Override
				public void mouseReleased(MouseEvent e)
				{
					if (pressedAt != null && Math.abs(x(e) - pressedAt.x) <= AdvancedMapPlugin.CLICK_SLOP
						&& Math.abs(y(e) - pressedAt.y) <= AdvancedMapPlugin.CLICK_SLOP)
					{
						plugin.leftClick(x(e), y(e), width(), height());
					}
					dragFrom = null;
					pressedAt = null;
					repaint();
				}

				@Override
				public void mouseMoved(MouseEvent e)
				{
					mouse = point(e);
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
					if (!plugin.wheelPanels(x(e), y(e), e.getPreciseWheelRotation(), width(), height(), true))
					{
						double factor = Math.pow(MapInput.WHEEL_STEP, -e.getPreciseWheelRotation());
						plugin.viewport().zoomAt(factor, x(e), y(e), width(), height());
					}
					repaint();
				}
			};
			addMouseListener(input);
			addMouseMotionListener(input);
			addMouseWheelListener(input);
			// the search bar and the Filters list's search box type here
			setFocusable(true);
			addKeyListener(new java.awt.event.KeyAdapter()
			{
				@Override
				public void keyTyped(KeyEvent e)
				{
					if (plugin.typing())
					{
						plugin.type(e.getKeyChar());
						repaint();
					}
				}

				@Override
				public void keyPressed(KeyEvent e)
				{
					if (plugin.typing() && e.getKeyCode() != KeyEvent.VK_ESCAPE)
					{
						plugin.typingKey(e.getKeyCode());
						repaint();
					}
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			// drawn bigger by Stretched Mode's factor, as the map over the game is
			Graphics2D scaled = (Graphics2D) g.create();
			double scale = plugin.windowScale();
			scaled.scale(scale, scale);
			painter.paint(scaled, plugin.viewport().view(width(), height()), plugin.area(), plugin.plane(),
				plugin.playerLocation(), mouse, true, plugin.settings(), plugin.progress(), plugin.selection());
			scaled.dispose();
		}

		/** The panel's size and the mouse, in the map's units: real pixels divided by the scale it is drawn at. */
		private int width()
		{
			return (int) Math.round(getWidth() / plugin.windowScale());
		}

		private int height()
		{
			return (int) Math.round(getHeight() / plugin.windowScale());
		}

		private int x(MouseEvent e)
		{
			return (int) Math.round(e.getX() / plugin.windowScale());
		}

		private int y(MouseEvent e)
		{
			return (int) Math.round(e.getY() / plugin.windowScale());
		}

		private Point point(MouseEvent e)
		{
			return new Point(x(e), y(e));
		}
	}
}
