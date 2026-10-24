package com.expecteddrops.ui;

import java.awt.Color;
import java.awt.Component;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/** One verdict tile: a small title, the verdict word, and the numbers behind it. */
class VerdictTile extends JPanel
{
	private final JLabel key = new JLabel();
	private final JLabel word = new JLabel();
	private final JLabel numbers = new JLabel();
	private final JLabel luck = new JLabel();

	VerdictTile(String title)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Theme.SURFACE);
		setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.HAIR));
		key.setText(title);
		key.setFont(FontManager.getRunescapeSmallFont());
		key.setForeground(Theme.TEXT_MUTED);
		// the verdict is what On Rate is about: a size up from the lists
		word.setFont(Theme.verdictFont());
		for (JLabel l : new JLabel[]{numbers, luck})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(Theme.TEXT_MUTED);
		}
		add(key);
		add(word);
		add(numbers);
		add(luck);
	}

	void set(String verdict, Color color, String numbersText, String luckText, String tip)
	{
		word.setText(verdict);
		word.setForeground(color);
		numbers.setText(numbersText);
		luck.setText(luckText);
		setToolTipText(tip);
		for (Component c : getComponents())
		{
			((JLabel) c).setToolTipText(tip);
		}
		// the tile was taken out of its row while its text changed: drop the size it remembers
		invalidate();
	}
}
