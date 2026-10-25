package com.expecteddrops.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;
import net.runelite.client.ui.ColorScheme;

/**
 * Every interface colour, spacing step and text width in one place. Luck colours are not here:
 * they live in {@link Palette} and are used for luck and nothing else.
 *
 * <ul>
 * <li>{@link #TEXT}: names, labels and headings</li>
 * <li>{@link #TEXT_MUTED}: secondary text, and the lowest grey any text may use</li>
 * <li>{@link #ACCENT}: selected or clickable things (tabs, the trip card, the sync line)</li>
 * </ul>
 */
public final class Theme
{
	public static final Color TEXT = Color.WHITE;
	public static final Color TEXT_MUTED = ColorScheme.LIGHT_GRAY_COLOR;
	public static final Color ACCENT = ColorScheme.BRAND_ORANGE;

	public static final Color PANEL = ColorScheme.DARK_GRAY_COLOR;
	public static final Color SURFACE = ColorScheme.DARKER_GRAY_COLOR;
	public static final Color SURFACE_RAISED = new Color(45, 45, 45);
	public static final Color SURFACE_SELECTED = new Color(61, 61, 61);
	public static final Color TRACK = new Color(55, 55, 55);
	public static final Color TRACK_DARK = new Color(26, 26, 26);
	public static final Color OUTLINE = new Color(70, 70, 70);
	/** The thin line between label / value rows in a tooltip. */
	public static final Color DIVIDER = new Color(60, 60, 60);
	/** Bars that show progress, not luck: no verdict colours on them. */
	public static final Color PROGRESS = new Color(90, 106, 122);
	/** Raid "purples": the game's own colour for raid uniques. */
	public static final Color RAID_PURPLE = new Color(192, 112, 255);

	/** Spacing: a hairline gap between rows, a small and a medium step. */
	public static final int HAIR = 2;
	public static final int S = 4;
	public static final int M = 8;

	/** Wrapped text inside the panel, and the two tooltip widths. */
	public static final int TEXT_WIDTH = 165;
	public static final int TIP_SHORT = 180;
	public static final int TIP_WIDE = 240;

	private Theme()
	{
	}

	/** HTML that wraps at {@code width} pixels. */
	public static String wrap(String html, int width)
	{
		return "<html><body style='width: " + width + "px'>" + html + "</body></html>";
	}

	/** {@code #rrggbb} for a colour, for use inside HTML text. */
	public static String hex(Color c)
	{
		return String.format("#%06x", c.getRGB() & 0xFFFFFF);
	}

	/** A small "i" in a circle, in front of information lines (not warnings, not luck). */
	public static Icon infoIcon()
	{
		return new Icon()
		{
			@Override
			public void paintIcon(Component c, Graphics graphics, int x, int y)
			{
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(TEXT_MUTED);
				g.setStroke(new BasicStroke(1f));
				g.drawOval(x, y + 1, 10, 10);
				g.fillRect(x + 5, y + 5, 1, 4);
				g.fillRect(x + 5, y + 3, 1, 1);
				g.dispose();
			}

			@Override
			public int getIconWidth()
			{
				return 11;
			}

			@Override
			public int getIconHeight()
			{
				return 12;
			}
		};
	}

	/** The font of the verdicts at the top ("On rate") and of the names and numbers in the lists. */
	static java.awt.Font rowFont()
	{
		return net.runelite.client.ui.FontManager.getRunescapeBoldFont().deriveFont(java.awt.Font.PLAIN, 16f);
	}

	/** The verdict in the tiles at the top ("Very spooned"): bold and a little bigger than the lists. */
	static java.awt.Font verdictFont()
	{
		return net.runelite.client.ui.FontManager.getRunescapeBoldFont().deriveFont(18f);
	}
}
