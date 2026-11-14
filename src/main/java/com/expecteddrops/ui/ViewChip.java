package com.expecteddrops.ui;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import java.awt.Color;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.LineBorder;
import net.runelite.client.ui.FontManager;

/**
 * A small outlined button that shows the current item view and opens a list of all five to pick from.
 * Quiet on purpose: muted outline, no fill, no accent colour (the orange is only the keyboard focus ring).
 * The same button sits on the boss pages and on the account overview; the view it changes is one setting.
 */
final class ViewChip extends JPanel
{
	private static final int HEIGHT = 22;
	private static final int PAD = 6;
	private static final int GAP = 4;
	private static final int ICON = 14;

	private final Consumer<ItemView> onPick;
	private ItemView view = ItemView.TARGET_BARS;
	private boolean compact;
	private boolean hover;

	ViewChip(Consumer<ItemView> onPick)
	{
		this.onPick = onPick;
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setFont(FontManager.getRunescapeSmallFont());
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				hover = true;
				repaint();
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				hover = false;
				repaint();
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					open();
				}
			}
		});
		Activatable.install(this, "View", this::open);
		update();
	}

	void setView(ItemView view)
	{
		ItemView next = view == null ? ItemView.TARGET_BARS : view;
		if (next != this.view)
		{
			this.view = next;
			update();
		}
	}

	/** Icon and arrow only, for a row that is short of width (the name stays in the tooltip). */
	void setCompact(boolean compact)
	{
		if (compact != this.compact)
		{
			this.compact = compact;
			update();
		}
	}

	private void update()
	{
		setToolTipText("View: " + view.shortName());
		if (getAccessibleContext() != null)
		{
			getAccessibleContext().setAccessibleName("View: " + view.shortName());
		}
		Dimension size = new Dimension(width(), HEIGHT);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		revalidate();
		repaint();
	}

	private int width()
	{
		FontMetrics fm = getFontMetrics(getFont());
		int w = PAD + ICON + GAP + ChevronIcon.WIDTH + PAD;
		if (!compact)
		{
			w += fm.stringWidth("View") + GAP + fm.stringWidth(view.shortName()) + GAP;
		}
		return w + 2;
	}

	private void open()
	{
		JPopupMenu menu = new JPopupMenu();
		menu.setBorder(new LineBorder(Theme.OUTLINE, 1));
		menu.setBackground(Theme.SURFACE);
		int width = Math.max(150, getWidth());
		for (ItemView v : ItemView.values())
		{
			menu.add(new Entry(v, v == view, width));
		}
		menu.show(this, getWidth() - width - 1, getHeight() + 2);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int w = getWidth();
		int h = getHeight();
		if (hover)
		{
			g.setColor(Theme.SURFACE_RAISED);
			g.fillRoundRect(0, 0, w - 1, h - 1, 4, 4);
		}
		g.setColor(Theme.OUTLINE);
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(0, 0, w - 1, h - 1, 4, 4);
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		int baseline = (h - fm.getHeight()) / 2 + fm.getAscent();
		int x = PAD + 1;
		if (!compact)
		{
			g.setColor(Theme.TEXT_MUTED);
			g.drawString("View", x, baseline);
			x += fm.stringWidth("View") + GAP;
		}
		new ViewIcon(view, Theme.TEXT_MUTED).paintIcon(this, g, x, (h - ICON) / 2);
		x += ICON + GAP;
		if (!compact)
		{
			g.setColor(Theme.TEXT);
			g.drawString(view.shortName(), x, baseline);
			x += fm.stringWidth(view.shortName()) + GAP;
		}
		new ChevronIcon().paintIcon(this, g, x, (h - ChevronIcon.HEIGHT) / 2);
		g.dispose();
	}

	/** One line of the list: icon, name, and a small tick on the current view. */
	private final class Entry extends JMenuItem
	{
		private final ItemView entryView;
		private final boolean current;

		Entry(ItemView view, boolean current, int width)
		{
			super(view.shortName());
			this.entryView = view;
			this.current = current;
			setFont(FontManager.getRunescapeSmallFont());
			setOpaque(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setPreferredSize(new Dimension(width - 4, 22));
			getAccessibleContext().setAccessibleName(view.shortName() + (current ? ", current view" : ""));
			addActionListener(e ->
			{
				if (entryView != ViewChip.this.view)
				{
					onPick.accept(entryView);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			int w = getWidth();
			int h = getHeight();
			if (current)
			{
				g.setColor(Theme.SURFACE_SELECTED);
				g.fillRect(0, 0, w, h);
			}
			else if (isArmed())
			{
				g.setColor(Theme.SURFACE_RAISED);
				g.fillRect(0, 0, w, h);
			}
			new ViewIcon(entryView, Theme.TEXT_MUTED).paintIcon(this, g, 6, (h - ICON) / 2);
			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			g.setColor(Theme.TEXT);
			g.drawString(getText(), 6 + ICON + 6, (h - fm.getHeight()) / 2 + fm.getAscent());
			if (current)
			{
				g.setColor(Theme.TEXT);
				g.setStroke(new BasicStroke(1.4f));
				int cx = w - 16;
				int cy = h / 2;
				g.drawLine(cx, cy, cx + 3, cy + 3);
				g.drawLine(cx + 3, cy + 3, cx + 8, cy - 3);
			}
			if (isArmed() && hasFocus())
			{
				g.setColor(Theme.ACCENT);
				g.setStroke(new BasicStroke(1f));
				g.drawRect(0, 0, w - 1, h - 1);
			}
			g.dispose();
		}
	}

	/** A small arrow pointing down: this opens a list. */
	private static final class ChevronIcon implements Icon
	{
		static final int WIDTH = 7;
		static final int HEIGHT = 4;

		@Override
		public void paintIcon(Component c, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(Theme.TEXT_MUTED);
			g.setStroke(new BasicStroke(1.2f));
			g.drawLine(x, y, x + 3, y + 3);
			g.drawLine(x + 3, y + 3, x + 6, y);
			g.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return WIDTH;
		}

		@Override
		public int getIconHeight()
		{
			return HEIGHT;
		}
	}

	/** A 14 x 14 sketch of each view, drawn in one muted colour. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class ViewIcon implements Icon
	{
		private final ItemView view;
		private final Color color;

		@Override
		public void paintIcon(Component c, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.translate(x, y);
			g.setColor(color);
			g.setStroke(new BasicStroke(1.2f));
			switch (view)
			{
				case DELVE_BARS:
					g.drawLine(1, 2, 6, 2);
					g.drawLine(8, 2, 13, 2);
					g.fillRect(1, 6, 9, 2);
					g.fillRect(1, 10, 6, 2);
					break;
				case COMPACT:
					g.drawRect(1, 2, 11, 10);
					g.drawLine(1, 6, 12, 6);
					g.drawLine(1, 9, 12, 9);
					g.drawLine(6, 2, 6, 12);
					break;
				case RINGS:
					g.drawOval(2, 2, 10, 10);
					g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
					g.drawArc(2, 2, 10, 10, 90, -200);
					break;
				case GAUGE:
					g.drawArc(1, 3, 12, 12, 0, 180);
					g.drawLine(7, 9, 10, 5);
					break;
				default:
					g.fillRect(1, 3, 10, 2);
					g.fillRect(1, 8, 6, 2);
					g.drawLine(8, 1, 8, 12);
			}
			g.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return ICON;
		}

		@Override
		public int getIconHeight()
		{
			return ICON;
		}
	}
}

