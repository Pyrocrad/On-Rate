package com.expecteddrops.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * A thin bar under a Time row: how long the grind is, on a scale where each step is ten times
 * longer, so a quarter-hour item and a five-hundred-hour one both read at a glance. It is
 * progress-grey, not a verdict colour: length is not luck.
 */
class EffortBar extends JComponent
{
	/** The hours at which the bar is full. */
	static final double FULL_HOURS = 1000;

	private final double fraction;

	EffortBar(double seconds)
	{
		this.fraction = fraction(seconds);
		setPreferredSize(new Dimension(100, 3));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 3));
		setOpaque(false);
	}

	/** How full the bar is for this time (0 to 1; nothing for an unknown time). */
	static double fraction(double seconds)
	{
		if (Double.isNaN(seconds) || seconds <= 0)
		{
			return 0;
		}
		double hours = seconds / 3600;
		return Math.max(0, Math.min(1, Math.log10(1 + hours) / Math.log10(1 + FULL_HOURS)));
	}

	double getFraction()
	{
		return fraction;
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		int w = getWidth();
		int h = getHeight();
		g.setColor(Theme.TRACK);
		g.fillRect(0, 0, w, h);
		g.setColor(Palette.isHigherContrast() ? Palette.PROGRESS_HIGH : Theme.PROGRESS);
		g.fillRect(0, 0, (int) Math.round(w * fraction), h);
		g.dispose();
	}
}
