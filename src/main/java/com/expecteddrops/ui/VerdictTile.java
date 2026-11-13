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
	private final JLabel key = new FullWidthLabel();
	private final JLabel word = new FullWidthLabel();
	private final JLabel numbers = new FullWidthLabel();
	private final LuckPill luck = new LuckPill();
	/** The colour of the top edge: the verdict's colour. */
	private Color accent = Theme.TEXT_MUTED;
	/** The verdict word and the two small lines as set, before any wrapping (see {@link #doLayout}). */
	private String wordText = "";
	private String numbersText = "";
	private String luckText = "";

	VerdictTile(String title)
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setBorder(new EmptyBorder(Theme.M, Theme.M, Theme.M - 1, Theme.S + 1));
		key.setText(title);
		key.setFont(FontManager.getRunescapeSmallFont());
		key.setForeground(Theme.TEXT_MUTED);
		// the verdict is what On Rate is about: a size up from the lists
		word.setFont(Theme.verdictFont());
		numbers.setFont(FontManager.getRunescapeSmallFont());
		numbers.setForeground(Theme.TEXT_MUTED);
		numbers.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		for (JLabel l : new JLabel[]{key, word, numbers})
		{
			l.setAlignmentX(LEFT_ALIGNMENT);
		}
		luck.setAlignmentX(LEFT_ALIGNMENT);
		add(key);
		add(word);
		add(numbers);
		add(luck);
	}

	/** The card: a quiet gradient, a hairline outline and a coloured top edge. */
	@Override
	protected void paintComponent(java.awt.Graphics graphics)
	{
		java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		int w = getWidth();
		int h = getHeight();
		g.setPaint(new java.awt.GradientPaint(0, 0, Theme.SURFACE_RAISED, w, h, Theme.SURFACE));
		g.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
		g.setColor(Theme.OUTLINE);
		g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
		// the coloured edge, clipped to the card's rounded corners
		g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, w - 1, h - 1, 8, 8));
		g.setColor(accent);
		g.fillRect(0, 0, w, 2);
		g.dispose();
	}

	/**
	 * A label that takes the whole width of the tile. A plain label is never wider than its text was when it
	 * was last measured, which is too narrow once the verdict word grows or the line wraps in {@link #doLayout}.
	 */
	private static final class FullWidthLabel extends JLabel
	{
		@Override
		public java.awt.Dimension getMaximumSize()
		{
			return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}

	/**
	 * Verdict size by default; smaller only when a long word ("Very spooned") doesn't fit the tile.
	 * A word too wide even at the smallest size ("Extremely spooned") goes back to full size on two lines.
	 */
	private static final float WORD_SIZE = 18f;
	private static final float WORD_MIN_SIZE = 14f;

	/**
	 * The tile is about 98px wide for text when the panel has its scrollbar. Two things in it can be
	 * wider: "Very spooned" in the full size needs 102 ("Extremely spooned" 141, still 113 at the
	 * smallest size, so it takes two lines), and "only 1 in 10,000 has none" needs 127. The
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
		boolean changed = false;
		String text = wordText;
		if (text != null && !text.isEmpty())
		{
			float size = WORD_SIZE;
			java.awt.Font base = Theme.verdictFont();
			while (size > WORD_MIN_SIZE && word.getFontMetrics(base.deriveFont(size)).stringWidth(text) > room)
			{
				size -= 1f;
			}
			boolean twoLines = word.getFontMetrics(base.deriveFont(size)).stringWidth(text) > room;
			if (twoLines)
			{
				size = WORD_SIZE;
			}
			if (word.getFont().getSize2D() != size)
			{
				word.setFont(base.deriveFont(size));
				changed = true;
			}
			if (twoLines)
			{
				changed |= wrapIfLong(word, text, room);
			}
			else if (!text.equals(word.getText()))
			{
				word.setText(text);
				changed = true;
			}
		}
		changed |= wrapIfLong(numbers, numbersText, room);
		changed |= wrapIfLong(luck, luckText, room - 17);
		if (changed)
		{
			// a label that wrapped (or shrank) needs a different height: lay the tile and its row out again,
			// or the second line is cut off. Later, not now: a revalidate made while the tile is being laid
			// out is wiped when that layout pass ends (Swing marks the tile valid again afterwards)
			javax.swing.SwingUtilities.invokeLater(this::revalidate);
		}
	}

	/**
	 * Shows {@code plain} as it is, or broken into lines of at most {@code room} pixels when it would be cut off.
	 * The breaks are made here, from the label's own font: a CSS width in an HTML label is not in pixels
	 * (it comes out about a third wider and varies with the screen).
	 */
	private static boolean wrapIfLong(JLabel label, String plain, int room)
	{
		if (plain == null || plain.isEmpty())
		{
			return false;
		}
		String text = wrapIfLong(plain, label.getFontMetrics(label.getFont()), room, 0);
		if (!text.equals(label.getText()))
		{
			label.setText(text);
			return true;
		}
		return false;
	}

	/**
	 * {@code plain} as it is when it fits {@code room} pixels in the font of {@code fm}, otherwise broken at spaces into
	 * lines of at most {@code room} pixels, as an HTML string (only then: text that needs no break stays plain).
	 * Measured with the real font, because a CSS width in an HTML label is not in pixels.
	 *
	 * @param maxLines 0 for as many lines as it takes; otherwise at most this many, the last one ending in "..."
	 *     when the rest of the name does not fit (and a single word wider than the room is cut the same way)
	 */
	static String wrapIfLong(String plain, java.awt.FontMetrics fm, int room, int maxLines)
	{
		if (plain == null || plain.isEmpty() || room <= 0)
		{
			return plain;
		}
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : plain.split(" "))
		{
			String longer = line.length() == 0 ? word : line + " " + word;
			if (line.length() > 0 && fm.stringWidth(longer) > room)
			{
				lines.add(line.toString());
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(longer);
			}
		}
		lines.add(line.toString());
		if (maxLines > 0)
		{
			while (lines.size() > maxLines)
			{
				// the rest of the name goes onto the last line, which is then cut short
				String last = lines.remove(lines.size() - 1);
				lines.set(lines.size() - 1, lines.get(lines.size() - 1) + " " + last);
			}
			for (int i = 0; i < lines.size(); i++)
			{
				boolean overflow = fm.stringWidth(lines.get(i)) > room;
				if (overflow)
				{
					lines.set(i, ellipsize(lines.get(i), fm, room));
				}
			}
		}
		if (lines.size() == 1)
		{
			return lines.get(0);
		}
		StringBuilder shown = new StringBuilder("<html>");
		for (int i = 0; i < lines.size(); i++)
		{
			shown.append(i > 0 ? "<br>" : "").append(Texts.html(lines.get(i).replace("&", "&amp;")));
		}
		return shown.append("</html>").toString();
	}

	/** {@code text} cut from the end, with "..." added, until it fits {@code room} pixels. */
	private static String ellipsize(String text, java.awt.FontMetrics fm, int room)
	{
		String cut = text;
		while (cut.length() > 1 && fm.stringWidth(cut + "...") > room)
		{
			cut = cut.substring(0, cut.length() - 1);
		}
		return cut.trim() + "...";
	}

	void set(String verdict, Color color, String numbersText, String luckText, String tip)
	{
		word.setText(verdict);
		this.wordText = verdict;
		word.setForeground(color);
		this.accent = color;
		luck.setHue(color);
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
		repaint();
	}
}
