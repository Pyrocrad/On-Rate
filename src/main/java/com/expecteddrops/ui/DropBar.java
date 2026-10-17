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
	/** When set, drawn on the left (shortened if needed) with {@link #text} on the right. */
	private String leftText;

	DropBar()
	{
		setFont(FontManager.getRunescapeSmallFont());
		setPreferredSize(new Dimension(100, 22));
		setMinimumSize(new Dimension(40, 22));
	}

	/** Bar with a name on the left and a value on the right, e.g. "Vorkath ... 64%". */
	void set(double fraction, Color fill, String left, String right)
	{
		set(fraction, fill, right);
		this.leftText = left;
	}

	void set(double fraction, Color fill, String text)
	{
		this.leftText = null;
		this.fraction = Double.isNaN(fraction) ? 0 : Math.max(0, Math.min(1, fraction));
		this.fill = fill;
		this.text = text == null ? "" : text;
		repaint();
	}

	private static void drawShadowed(Graphics2D g2, String s, int x, int y)
	{
		g2.setColor(Color.BLACK);
		g2.drawString(s, x + 1, y + 1);
		g2.setColor(Color.WHITE);
		g2.drawString(s, x, y);
	}

	/** Shortens text with "..." until it fits in the given width. */
	private static String fit(FontMetrics fm, String s, int width)
	{
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		String dots = "...";
		int end = s.length();
		while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > width)
		{
			end--;
		}
		return s.substring(0, end).trim() + dots;
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
			int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
			if (leftText == null)
			{
				drawShadowed(g2, text, (w - fm.stringWidth(text)) / 2, ty);
			}
			else
			{
				int rightWidth = fm.stringWidth(text);
				drawShadowed(g2, text, w - 5 - rightWidth, ty);
				drawShadowed(g2, fit(fm, leftText, w - 5 - rightWidth - 6 - 5), 5, ty);
			}
		}
		finally
		{
			g2.dispose();
		}
	}
}
