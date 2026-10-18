package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/** The dry-to-spooned scale in five bands, with a dot where you are. */
class LuckStrip extends JComponent
{
	static final double[] BANDS = {0, 0.1, 0.35, 0.65, 0.9, 1};
	private double luck = Double.NaN;

	LuckStrip()
	{
		setPreferredSize(new Dimension(80, 14));
		setOpaque(false);
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
		for (int i = 0; i < BANDS.length - 1; i++)
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
