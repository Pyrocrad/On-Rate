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
	/** The two small lines as set, before any wrapping (see {@link #doLayout}). */
	private String numbersText = "";
	private String luckText = "";

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

	/** Verdict size by default; smaller only when a long word ("Very spooned") doesn't fit the tile. */
	private static final float WORD_SIZE = 18f;
	private static final float WORD_MIN_SIZE = 14f;

	/**
	 * The tile is about 98px wide for text when the panel has its scrollbar. Two things in it can be
	 * wider: "Very spooned" in the full size needs 102, and "only 1 in 10,000 has none" needs 127. The
	 * verdict word steps down a point at a time until it fits (and back up when it does); the small
	 * lines wrap onto a second line instead of ending in "...".
	 */
	@Override
	public void doLayout()
	{
		super.doLayout();
		int room = getWidth() - getInsets().left - getInsets().right;
		if (room <= 0)
		{
			return;
		}
		String text = word.getText();
		if (text != null && !text.isEmpty())
		{
			float size = WORD_SIZE;
			java.awt.Font base = Theme.verdictFont();
			while (size > WORD_MIN_SIZE && word.getFontMetrics(base.deriveFont(size)).stringWidth(text) > room)
			{
				size -= 1f;
			}
			if (word.getFont().getSize2D() != size)
			{
				word.setFont(base.deriveFont(size));
			}
		}
		wrapIfLong(numbers, numbersText, room);
		wrapIfLong(luck, luckText, room);
	}

	/**
	 * Shows {@code plain} as it is, or broken into lines of at most {@code room} pixels when it would be cut off.
	 * The breaks are made here, from the label's own font: a CSS width in an HTML label is not in pixels
	 * (it comes out about a third wider and varies with the screen).
	 */
	private static void wrapIfLong(JLabel label, String plain, int room)
	{
		if (plain == null || plain.isEmpty())
		{
			return;
		}
		java.awt.FontMetrics fm = label.getFontMetrics(label.getFont());
		StringBuilder shown = new StringBuilder();
		StringBuilder line = new StringBuilder();
		for (String word : plain.split(" "))
		{
			String longer = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && fm.stringWidth(longer) > room)
			{
				shown.append(Texts.html(line.toString())).append("<br>");
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(longer);
			}
		}
		shown.append(Texts.html(line.toString()));
		String text = shown.indexOf("<br>") < 0 ? plain : "<html>" + shown + "</html>";
		if (!text.equals(label.getText()))
		{
			label.setText(text);
		}
	}

	void set(String verdict, Color color, String numbersText, String luckText, String tip)
	{
		word.setText(verdict);
		word.setForeground(color);
		this.numbersText = numbersText;
		this.luckText = luckText;
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
