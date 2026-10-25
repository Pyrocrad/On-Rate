package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/**
 * The dry-to-spooned scale with a dot where you are: five coloured bands in the Luck gauge view, or
 * (in lists, where the scale repeats on every row) one neutral track with a tick at the middle.
 * The dot's place and the verdict word carry the meaning, the colours only echo it.
 */
class LuckStrip extends JComponent
{
	static final double[] BANDS = {0, 0.1, 0.35, 0.65, 0.9, 1};
	private static final Color TRACK = new Color(72, 72, 72);
	private static final Color MIDDLE = new Color(120, 120, 120);
	private double luck = Double.NaN;
	private boolean bands = true;

	LuckStrip()
	{
		setPreferredSize(new Dimension(80, 14));
		setOpaque(false);
	}

	/** False for the neutral track used in lists. */
	void setBands(boolean bands)
	{
		this.bands = bands;
		repaint();
	}

	void setLuck(double luck)
	{
		this.luck = luck;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth() - 8;
		int y = getHeight() / 2;
		if (!bands)
		{
			g.setColor(TRACK);
			g.fillRect(4, y - 1, w, 2);
			g.setColor(MIDDLE);
			g.fillRect(4 + w / 2, y - 4, 1, 8);
		}
		for (int i = 0; bands && i < BANDS.length - 1; i++)
		{
			double mid = (BANDS[i] + BANDS[i + 1]) / 2;
			g.setColor(Palette.blend(Palette.luck(mid), ColorScheme.DARKER_GRAY_COLOR, 0.35));
			int x0 = 4 + (int) Math.round(BANDS[i] * w);
			int x1 = 4 + (int) Math.round(BANDS[i + 1] * w);
			g.fillRect(x0, y - 2, x1 - x0, 4);
		}
		if (!Double.isNaN(luck))
		{
			int x = 4 + (int) Math.round(luck * w);
			g.setColor(Palette.luck(luck).brighter());
			g.fillOval(x - 4, y - 4, 9, 9);
			g.setColor(Color.WHITE);
			g.drawOval(x - 4, y - 4, 9, 9);
		}
		g.dispose();
	}
}
