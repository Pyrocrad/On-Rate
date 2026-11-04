package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/**
 * A small pill in the hue of a colour: a quiet tinted background, a dot and the text in that colour
 * (e.g. "52%"). Wraps onto several lines when its text is long ({@link #setText} with HTML).
 */
class Chip extends JLabel
{
	private final Color hue;
	private final boolean dot;

	/** A pill for a luck colour. */
	Chip(String text, Color hue)
	{
		this(text, hue, true);
	}

	Chip(String text, Color hue, boolean dot)
	{
		super(text);
		this.hue = hue;
		this.dot = dot;
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(Palette.onTint(hue));
		setHorizontalAlignment(dot ? SwingConstants.LEFT : SwingConstants.CENTER);
		setBorder(new javax.swing.border.EmptyBorder(1, dot ? 9 : 4, 1, 3));
		setPreferredSize(new Dimension(42, 16));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Palette.tint(hue));
		int arc = Math.min(getHeight() - 2, 16);
		g.fillRoundRect(0, 1, getWidth() - 1, getHeight() - 2, arc, arc);
		if (dot)
		{
			g.setColor(hue);
			int line = getFontMetrics(getFont()).getHeight();
			g.fillOval(3, 1 + (getInsets().top + line / 2) - 2, 4, 4);
		}
		g.dispose();
		super.paintComponent(graphics);
	}
}
