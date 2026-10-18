package com.expecteddrops.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/** A ring that fills up (clockwise from the top) around an item picture or a short text. */
class LuckRing extends JLabel
{
	private static final Color TRACK = new Color(55, 55, 55);
	private double fraction;
	private Color color = Color.GRAY;

	LuckRing()
	{
		setPreferredSize(new Dimension(56, 56));
		setMinimumSize(new Dimension(56, 56));
		setHorizontalAlignment(SwingConstants.CENTER);
		setVerticalAlignment(SwingConstants.CENTER);
	}

	void set(double fraction, Color color)
	{
		this.fraction = Math.max(0, Math.min(1, fraction));
		this.color = color;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int size = Math.min(getWidth(), getHeight()) - 6;
		int x = (getWidth() - size) / 2;
		int y = (getHeight() - size) / 2;
		g.setStroke(new BasicStroke(5, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
		g.setColor(TRACK);
		g.drawOval(x, y, size, size);
		g.setColor(color);
		g.drawArc(x, y, size, size, 90, -(int) Math.round(360 * fraction));
		g.dispose();
		super.paintComponent(graphics);
	}
}
