package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.BasicStroke;
import javax.swing.JComponent;

/**
 * A thin bar: the fill is what you received, the white line is what you should have by now.
 */
class TargetBar extends JComponent
{
	private static final Color TRACK = Theme.TRACK;
	private double fill;
	private double marker = -1;
	private Color color = Color.GRAY;
	private boolean quiet;

	TargetBar()
	{
		setPreferredSize(new Dimension(100, 10));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
		setOpaque(false);
	}

	/** A quiet bar (nothing expected yet) has a slimmer track and a grey tick instead of a white one. */
	void setQuiet(boolean quiet)
	{
		this.quiet = quiet;
		int height = quiet ? 8 : 10;
		setPreferredSize(new Dimension(100, height));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		repaint();
	}

	/** Fractions of the bar width; marker below 0 hides the line. */
	void set(double fill, double marker, Color color)
	{
		this.fill = Math.max(0, Math.min(1, fill));
		this.marker = marker;
		this.color = color;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		int w = getWidth();
		int h = getHeight();
		g.setColor(TRACK);
		g.fillRect(0, 2, w, h - 4);
		g.setColor(color);
		g.fillRect(0, 2, (int) Math.round(w * fill), h - 4);
		if (marker >= 0)
		{
			int x = (int) Math.round(Math.min(1, marker) * (w - 2));
			g.setColor(quiet ? Theme.QUIET_MARK : Color.WHITE);
			g.setStroke(new BasicStroke(2));
			g.drawLine(x + 1, 0, x + 1, h);
		}
		g.dispose();
	}
}
