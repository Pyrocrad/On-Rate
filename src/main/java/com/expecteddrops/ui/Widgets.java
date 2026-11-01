package com.expecteddrops.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Small Swing building blocks the panel pages share (labels, tips, borders, cards). Split out of {@link ExpectedDropsPanel}. */
final class Widgets
{
	private Widgets()
	{
	}

	/** A link at the top right of the title row: hand cursor, accent on hover, works from the keyboard. */
	static void installTitleLink(JLabel link, String tip, Runnable action)
	{
		link.setFont(FontManager.getRunescapeBoldFont());
		link.setForeground(Theme.TEXT);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setToolTipText(tip);
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(Theme.ACCENT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(Theme.TEXT);
			}
		});
		Activatable.install(link, tip, action);
	}

	/** The quiet dot between footer links. */
	static JLabel footerDot(JLabel dot)
	{
		dot.setText("  \u00b7  ");
		dot.setFont(FontManager.getRunescapeSmallFont());
		dot.setForeground(Theme.TEXT_MUTED);
		return dot;
	}

	/** Shows the menu on a right-click anywhere on the component, its labels included. */
	static void addPopup(java.awt.Component c, JPopupMenu menu)
	{
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				show(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				show(e);
			}

			private void show(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		if (c instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) c).getComponents())
			{
				addPopup(child, menu);
			}
		}
	}

	/** A label in the bold font: the "Any unique" row and section titles. */
	static JLabel boldLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Theme.TEXT);
		return label;
	}

	static JLabel smallLabel(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	/** A row's name or number, in the same font as the verdicts at the top ("On rate"). */
	static JLabel rowLabel(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(Theme.rowFont());
		l.setForeground(color);
		return l;
	}

	/** Tooltip (and click) on a row and everything in it. */
	static void setTip(JPanel panel, String tip, Runnable onClick)
	{
		setTip(panel, tip, onClick, false);
	}

	/**
	 * {@link #setTip(JPanel, String, Runnable)}, where only a left click runs {@code onClick}
	 * when {@code leftOnly}: for rows whose right click opens a menu.
	 */
	static void setTip(JPanel panel, String tip, Runnable onClick, boolean leftOnly)
	{
		Color normal = panel.getBackground();
		MouseAdapter click = onClick == null ? null : new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!leftOnly || javax.swing.SwingUtilities.isLeftMouseButton(e))
				{
					onClick.run();
				}
			}

			// a clickable row lights up under the mouse, so "click to open" shows without a tooltip
			@Override
			public void mouseEntered(MouseEvent e)
			{
				panel.setBackground(Theme.SURFACE_RAISED);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (panel.getMousePosition(true) == null)
				{
					panel.setBackground(normal);
				}
			}
		};
		applyTip(panel, tip, click);
	}

	static void applyTip(java.awt.Component c, String tip, MouseAdapter click)
	{
		if (c instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) c).setToolTipText(tip);
		}
		if (click != null)
		{
			c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			c.addMouseListener(click);
		}
		if (c instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) c).getComponents())
			{
				applyTip(child, tip, click);
			}
		}
	}

	/** A thin bar filled to {@code fraction} (0 to 1), for the trip card. */
	static JPanel progressBar(double fraction, Color color)
	{
		JPanel bar = new JPanel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				super.paintComponent(g);
				g.setColor(color);
				g.fillRect(0, 0, (int) Math.round(getWidth() * Math.max(0, Math.min(1, fraction))), getHeight());
			}
		};
		bar.setBackground(Theme.TRACK);
		bar.setPreferredSize(new Dimension(10, 6));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		bar.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		return bar;
	}

	/**
	 * A grey label on the left and its value on the right, with a thin line above: the same rows
	 * as in the hovers ({@link Tip#row}). For the trip card.
	 */
	static JPanel cardRow(String label, String value, Color valueColor, String tip)
	{
		JPanel row = new JPanel(new BorderLayout(Theme.M, 0));
		row.setOpaque(false);
		row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.HAIR, 0, Theme.HAIR, 0)));
		JLabel name = smallLabel(label, Theme.TEXT_MUTED);
		name.setVerticalAlignment(SwingConstants.TOP);
		row.add(name, BorderLayout.WEST);
		JLabel shown = smallLabel(value, valueColor);
		shown.setHorizontalAlignment(SwingConstants.RIGHT);
		shown.setVerticalAlignment(SwingConstants.TOP);
		row.add(shown, BorderLayout.CENTER);
		for (javax.swing.JComponent c : new javax.swing.JComponent[]{row, name, shown})
		{
			c.setToolTipText(tip);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	static JPanel tile(String title, String value)
	{
		JPanel t = new JPanel(new BorderLayout());
		t.setBackground(Theme.SURFACE_RAISED);
		t.setBorder(new EmptyBorder(Theme.HAIR, Theme.HAIR, Theme.S, Theme.HAIR));
		JLabel a = smallLabel(title, ColorScheme.LIGHT_GRAY_COLOR);
		a.setHorizontalAlignment(SwingConstants.CENTER);
		JLabel b = new JLabel(value);
		b.setFont(FontManager.getRunescapeBoldFont());
		b.setForeground(Color.WHITE);
		b.setHorizontalAlignment(SwingConstants.CENTER);
		t.add(a, BorderLayout.NORTH);
		t.add(b, BorderLayout.CENTER);
		return t;
	}

	static javax.swing.border.Border tabBorder(boolean selected)
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? Theme.ACCENT : ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(Theme.S, 0, Theme.HAIR, 0));
	}

	/** A folding heading lights up under the mouse, like the other clickable lines. */
	static void brightenOnHover(JLabel heading)
	{
		heading.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				heading.setForeground(Theme.TEXT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				heading.setForeground(Theme.TEXT_MUTED);
			}
		});
	}

	static JPanel spacer(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		p.setPreferredSize(new Dimension(1, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return p;
	}
}
