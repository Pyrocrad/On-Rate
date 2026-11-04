package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		int track = quiet ? 4 : 5;
		int y = (h - track) / 2;
		g.setColor(TRACK);
		g.fillRoundRect(0, y, w, track, track, track);
		int filled = (int) Math.round(w * fill);
		if (filled > 0)
		{
			g.setColor(color);
			g.fillRoundRect(0, y, Math.max(track, filled), track, track, track);
		}
		if (marker >= 0)
		{
			int x = (int) Math.round(Math.min(1, marker) * (w - 2));
			g.setStroke(new BasicStroke(1f));
			// a dark edge keeps the line visible on top of the fill
			g.setColor(new Color(0, 0, 0, 90));
			g.fillRoundRect(x - 1, 0, 4, h, 2, 2);
			g.setColor(quiet ? Theme.QUIET_MARK : new Color(245, 245, 245));
			g.fillRoundRect(x, 1, 2, h - 2, 2, 2);
		}
		g.dispose();
	}
}
