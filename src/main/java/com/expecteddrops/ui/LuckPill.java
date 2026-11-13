package com.expecteddrops.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/**
 * A luck line as a pill that grows with its text: a faint tint of the luck colour, a small dot in it, and the
 * text in the colour lifted a little so it stays readable. Under the verdict tiles, and wherever else the panel
 * tags something with a luck colour.
 */
class LuckPill extends JLabel
{
	private Color hue = Theme.TEXT_MUTED;
	/** A fixed width (a column of pills), or 0 to fit the text. */
	private final int width;

	LuckPill()
	{
		this(0);
	}

	LuckPill(int width)
	{
		this.width = width;
		setFont(FontManager.getRunescapeSmallFont());
		setBorder(new EmptyBorder(2, 11, 2, 6));
	}

	LuckPill(String text, Color hue, int width)
	{
		this(width);
		setText(text);
		setHue(hue);
	}

	void setHue(Color hue)
	{
		this.hue = hue;
		setForeground(Palette.onTint(hue));
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension d = super.getPreferredSize();
		return width > 0 ? new Dimension(width, d.height) : d;
	}

	@Override
	public Dimension getMaximumSize()
	{
		// as wide as its text, never stretched across the tile
		return getPreferredSize();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		if (getText() == null || getText().isEmpty())
		{
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Palette.tint(hue));
		int arc = getHeight() > 20 ? 12 : getHeight();
		g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
		g.setColor(hue);
		g.fillOval(4, getInsets().top + getFontMetrics(getFont()).getHeight() / 2 - 1, 4, 4);
		g.dispose();
		super.paintComponent(graphics);
	}
}

