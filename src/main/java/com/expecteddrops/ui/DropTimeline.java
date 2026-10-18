package com.expecteddrops.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;

/**
 * One item's drops along your kill count: yellow marks at the kills it dropped, grey ticks
 * at the average spacing, a shaded part before On Rate was tracking and a coloured line
 * for the current dry streak.
 */
class DropTimeline extends JComponent
{
	private static final Color DROP = new Color(255, 200, 0);
	private int killCount;
	private int trackingStart;
	private double rate;
	private List<Integer> drops = Collections.emptyList();
	private Color streakColor = Color.GRAY;

	DropTimeline()
	{
		setPreferredSize(new Dimension(100, 14));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 14));
		setOpaque(false);
	}

	void set(int killCount, int trackingStart, double rate, List<Integer> drops, Color streakColor)
	{
		this.killCount = killCount;
		this.trackingStart = Math.max(0, Math.min(trackingStart, killCount));
		this.rate = rate;
		this.drops = drops;
		this.streakColor = streakColor;
		repaint();
	}

	private int x(int kc)
	{
		return killCount <= 0 ? 0 : (int) Math.round((getWidth() - 1) * (double) kc / killCount);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int y = getHeight() - 4;
		if (trackingStart > 0)
		{
			// before On Rate: drops there aren't known
			g.setColor(new Color(50, 50, 50));
			g.fillRect(0, y - 8, x(trackingStart), 10);
		}
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.drawLine(0, y, getWidth(), y);
		if (rate > 0 && killCount > 0)
		{
			double spacing = 1 / rate;
			if ((getWidth() * spacing / killCount) >= 4)
			{
				g.setColor(new Color(100, 100, 100));
				for (double k = spacing; k < killCount; k += spacing)
				{
					int tx = x((int) Math.round(k));
					g.drawLine(tx, y - 2, tx, y + 2);
				}
			}
		}
		int last = drops.isEmpty() ? trackingStart : drops.get(drops.size() - 1);
		g.setColor(streakColor);
		g.setStroke(new BasicStroke(2));
		g.drawLine(x(last), y + 2, getWidth(), y + 2);
		g.setColor(DROP);
		for (int kc : drops)
		{
			int dx = x(kc);
			g.fillPolygon(new int[]{dx - 4, dx + 4, dx}, new int[]{y - 1, y - 1, y - 8}, 3);
		}
		g.dispose();
	}
}
