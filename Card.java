package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * The box the verdict tiles are drawn in, for the rest of the panel: a rounded card with a hairline outline and,
 * only when it shows a verdict, a coloured top edge. A list of rows goes into one card with a thin line between
 * rows (see {@link #box}). With the look turned off ({@link #setBoxed}) it is a plain panel again, so the pixel
 * test can compare the page with the original.
 */
class Card extends JPanel
{
	/** Client property on a list row: rows next to each other go into one card. */
	private static final String ROW = "onrate.row";
	/** A grid of cells (the chance rings) shown as one row, with room around it. */
	private static final String GRID = "grid";
	private static final int ARC = 8;

	private boolean boxed = true;
	/** The coloured top edge, or null for none. */
	private Color edge;

	Card()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(LEFT_ALIGNMENT);
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setOpaque(false);
		// the rows start inside the outline
		setBorder(new EmptyBorder(1, 1, 1, 1));
	}

	/** On: the rounded card. Off: a plain panel in the background colour. */
	void setBoxed(boolean boxed)
	{
		this.boxed = boxed;
		setOpaque(!boxed);
		repaint();
	}

	boolean isBoxed()
	{
		return boxed;
	}

	/** The coloured top edge, as on the verdict tiles; null for none. */
	void setEdge(Color edge)
	{
		this.edge = edge;
		repaint();
	}

	@Override
	public Dimension getMaximumSize()
	{
		// as tall as its rows, as wide as the panel
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		if (!boxed)
		{
			super.paintComponent(graphics);
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(getBackground());
		g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
		g.dispose();
	}

	/** The rows are square: after they are drawn, round the corners off again and draw the outline and edge. */
	@Override
	public void paint(Graphics graphics)
	{
		super.paint(graphics);
		if (!boxed)
		{
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, ARC, ARC);
		Area outside = new Area(new Rectangle2D.Float(0, 0, w, h));
		outside.subtract(new Area(shape));
		g.setColor(behind());
		g.fill(outside);
		if (edge != null)
		{
			g.setClip(shape);
			g.setColor(edge);
			g.fillRect(0, 0, w, 2);
			g.setClip(null);
		}
		g.setColor(Theme.OUTLINE);
		g.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
		g.dispose();
	}

	/** The colour around the card: the first opaque panel it sits on. */
	private Color behind()
	{
		for (Container c = getParent(); c != null; c = c.getParent())
		{
			if (c.isOpaque() && c.getBackground() != null)
			{
				return c.getBackground();
			}
		}
		return ColorScheme.DARK_GRAY_COLOR;
	}

	/** Adds a list row, with a thin line above it when it isn't the first. */
	void addRow(JComponent row)
	{
		Component last = getComponentCount() == 0 ? null : getComponent(getComponentCount() - 1);
		boolean grid = GRID.equals(row.getClientProperty(ROW)) && last instanceof JComponent
			&& GRID.equals(((JComponent) last).getClientProperty(ROW));
		if (last != null && !grid)
		{
			add(divider());
		}
		// the card draws the lines between rows: a row's own separator line would double it
		Border b = row.getBorder();
		if (b instanceof CompoundBorder && ((CompoundBorder) b).getOutsideBorder() instanceof MatteBorder)
		{
			row.setBorder(((CompoundBorder) b).getInsideBorder());
		}
		// rows drawn straight on the panel have no side padding: keep their text off the outline
		Insets in = row.getInsets();
		if (GRID.equals(row.getClientProperty(ROW)))
		{
			// a grid of cells (rings) is one block: room on every side
			row.setBorder(new CompoundBorder(new EmptyBorder(Theme.M, Theme.S, Theme.M, Theme.S), row.getBorder()));
		}
		else if (in.left < Theme.S)
		{
			row.setBorder(new CompoundBorder(new EmptyBorder(0, Theme.S - in.left, 0, Math.max(0, Theme.S - in.right)), row.getBorder()));
		}
		row.setAlignmentX(LEFT_ALIGNMENT);
		add(row);
	}

	private static JComponent divider()
	{
		JPanel line = new JPanel();
		line.setBackground(Theme.DIVIDER);
		line.setOpaque(true);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setPreferredSize(new Dimension(1, 1));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		return line;
	}

	/** Marks a component as a list row (see {@link #box}). Returns it. */
	static <T extends JComponent> T row(T c)
	{
		c.putClientProperty(ROW, Boolean.TRUE);
		return c;
	}

	/** Marks a grid of cells (the chance rings) as one row, padded on every side inside its card. */
	static <T extends JComponent> T gridRow(T c)
	{
		c.putClientProperty(ROW, GRID);
		return c;
	}

	private static boolean isRow(Component c)
	{
		return c instanceof JComponent && ((JComponent) c).getClientProperty(ROW) != null;
	}

	/** The small gap the panel puts between two rows. */
	private static boolean isGap(Component c)
	{
		return c instanceof JPanel && !isRow(c) && ((JPanel) c).getComponentCount() == 0
			&& c.getPreferredSize().height <= Theme.HAIR;
	}

	/**
	 * Puts every run of list rows in {@code host} into one card. The small gaps between the rows of a run go
	 * (the card draws a thin line instead); everything else stays where it was.
	 */
	static void box(JPanel host)
	{
		Component[] all = host.getComponents();
		host.removeAll();
		Card card = null;
		Component gap = null;
		for (Component c : all)
		{
			if (isRow(c))
			{
				if (card == null)
				{
					if (gap != null)
					{
						host.add(gap);
					}
					card = new Card();
					host.add(card);
				}
				gap = null;
				card.addRow((JComponent) c);
			}
			else if (isGap(c))
			{
				if (gap != null)
				{
					host.add(gap);
				}
				gap = c;
			}
			else
			{
				if (gap != null)
				{
					host.add(gap);
					gap = null;
				}
				card = null;
				host.add(c);
			}
		}
		if (gap != null)
		{
			host.add(gap);
		}
	}
}
