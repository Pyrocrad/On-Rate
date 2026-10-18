package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.FontManager;

/** A small rounded label with a coloured background, e.g. "52%". */
class Chip extends JLabel
{
	private final Color background;

	Chip(String text, Color background)
	{
		super(text);
		this.background = background;
		setFont(FontManager.getRunescapeSmallFont());
		setForeground(Color.WHITE);
		setHorizontalAlignment(SwingConstants.CENTER);
		setPreferredSize(new Dimension(38, 16));
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(background);
		g.fillRoundRect(0, 1, getWidth() - 1, getHeight() - 2, getHeight() - 2, getHeight() - 2);
		g.dispose();
		super.paintComponent(graphics);
	}
}
