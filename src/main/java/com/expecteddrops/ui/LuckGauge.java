package com.expecteddrops.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** A speedometer from Dry (left) to Spooned (right) with a needle at your luck. */
class LuckGauge extends JComponent
{
	private double luck = Double.NaN;

	LuckGauge()
	{
		setPreferredSize(new Dimension(160, 104));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
		setAlignmentX(LEFT_ALIGNMENT);
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
		int r = Math.min(getWidth() / 2 - 24, getHeight() - 26);
		int cx = getWidth() / 2;
		int cy = r + 6;
		double[] bands = LuckStrip.BANDS;
		g.setStroke(new BasicStroke(14, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		int inset = 7;
		for (int i = 0; i < bands.length - 1; i++)
		{
			double mid = (bands[i] + bands[i + 1]) / 2;
			g.setColor(Palette.luck(mid));
			int start = 180 - (int) Math.round(bands[i] * 180);
			int extent = -(int) Math.round((bands[i + 1] - bands[i]) * 180);
			g.drawArc(cx - r + inset, cy - r + inset, 2 * (r - inset), 2 * (r - inset), start, extent);
		}
		if (!Double.isNaN(luck))
		{
			double angle = Math.PI * (1 - luck);
			int nx = cx + (int) Math.round(Math.cos(angle) * (r - 10));
			int ny = cy - (int) Math.round(Math.sin(angle) * (r - 10));
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(cx, cy, nx, ny);
			g.fillOval(cx - 5, cy - 5, 10, 10);
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		g.drawString("Dry", cx - r, cy + 16);
		String sp = "Spooned";
		g.drawString(sp, cx + r - g.getFontMetrics().stringWidth(sp), cy + 16);
		g.dispose();
	}
}
