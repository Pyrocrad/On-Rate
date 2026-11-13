package com.expecteddrops.ui;

import javax.swing.SwingUtilities;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JPanel;
import net.runelite.client.ui.FontManager;

/**
 * A small on/off slider with the word "Pets": whether pets count in "Closest to green log". Quiet on
 * purpose, like {@link ViewChip}: the word is grey until it is on, and the knob sits left (off) or right (on).
 */
final class PetsSwitch extends JPanel
{
	private static final int HEIGHT = 22;
	private static final int TRACK_W = 22;
	private static final int TRACK_H = 12;
	private static final int GAP = 5;
	private static final int AFTER = 6;

	private final Consumer<Boolean> onChange;
	private boolean on;

	PetsSwitch(Consumer<Boolean> onChange)
	{
		this.onChange = onChange;
		setOpaque(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setFont(FontManager.getRunescapeSmallFont());
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					toggle();
				}
			}
		});
		Activatable.install(this, "Pets", this::toggle);
		update();
	}

	boolean isOn()
	{
		return on;
	}

	void setOn(boolean on)
	{
		if (on != this.on)
		{
			this.on = on;
			update();
		}
	}

	private void toggle()
	{
		on = !on;
		update();
		onChange.accept(on);
	}

	private void update()
	{
		setToolTipText(on ? "Pets count in the time" : "Pets are left out of the time");
		if (getAccessibleContext() != null)
		{
			getAccessibleContext().setAccessibleName("Pets: " + (on ? "on" : "off"));
		}
		FontMetrics fm = getFontMetrics(getFont());
		// the trailing gap to the View button is part of the slider, so nothing shifts while it is hidden
		Dimension size = new Dimension(fm.stringWidth("Pets") + GAP + TRACK_W + 2 + AFTER, HEIGHT);
		setPreferredSize(size);
		setMinimumSize(size);
		setMaximumSize(size);
		revalidate();
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int h = getHeight();
		g.setFont(getFont());
		FontMetrics fm = g.getFontMetrics();
		g.setColor(on ? Theme.TEXT : Theme.TEXT_MUTED);
		g.drawString("Pets", 0, (h - fm.getHeight()) / 2 + fm.getAscent());
		int x = fm.stringWidth("Pets") + GAP;
		int y = (h - TRACK_H) / 2;
		g.setColor(on ? Theme.PROGRESS : Theme.TRACK);
		g.fillRoundRect(x, y, TRACK_W - 1, TRACK_H - 1, TRACK_H, TRACK_H);
		g.setColor(on ? Theme.PROGRESS.brighter() : Theme.OUTLINE);
		g.setStroke(new BasicStroke(1f));
		g.drawRoundRect(x, y, TRACK_W - 1, TRACK_H - 1, TRACK_H, TRACK_H);
		int knob = TRACK_H - 4;
		g.setColor(on ? Theme.TEXT : Theme.QUIET_MARK);
		g.fillOval(on ? x + TRACK_W - knob - 3 : x + 2, y + 2, knob, knob);
		g.dispose();
	}
}

