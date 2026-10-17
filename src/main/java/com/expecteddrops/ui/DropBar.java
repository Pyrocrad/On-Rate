package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * A horizontal bar with a coloured fill and a centred label, like the bars in the
 * Doom of Mokhaiotl plugin.
 */
class DropBar extends JComponent
{
	private static final Color TRACK = new Color(26, 26, 26);
	private static final Color OUTLINE = new Color(70, 70, 70);

	private double fraction;
	private Color fill = ColorScheme.MEDIUM_GRAY_COLOR;
	private String text = "";

	DropBar()
	{
		setFont(FontManager.getRunescapeSmallFont());
		setPreferredSize(new Dimension(100, 22));
		setMinimumSize(new Dimension(40, 22));
	}

	void set(double fraction, Color fill, String text)
	{
		this.fraction = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
		this.fill = fill;
		this.text = text == null ? "" : text;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			int w = getWidth();
			int h = getHeight();
			g2.setColor(TRACK);
			g2.fillRect(0, 0, w, h);

			int filled = (int) Math.round((w - 2) * fraction);
			if (filled > 0)
			{
				g2.setColor(fill);
				g2.fillRect(1, 1, filled, h - 2);
				// a lighter strip on top gives the fill a bit of depth
				g2.setColor(new Color(255, 255, 255, 28));
				g2.fillRect(1, 1, filled, Math.max(1, (h - 2) / 3));
			}

			g2.setColor(OUTLINE);
			g2.drawRect(0, 0, w - 1, h - 1);

			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();
			int tx = (w - fm.stringWidth(text)) / 2;
			int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
			g2.setColor(Color.BLACK);
			g2.drawString(text, tx + 1, ty + 1);
			g2.setColor(Color.WHITE);
			g2.drawString(text, tx, ty);
		}
		finally
		{
			g2.dispose();
		}
	}
}
