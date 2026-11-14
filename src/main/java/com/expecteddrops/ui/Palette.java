package com.expecteddrops.ui;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/**
 * Luck colours: red (dry) to yellow to green (spooned), or orange to grey to blue for
 * colour-blind players.
 */
public final class Palette
{
	static final Color BAD = new Color(170, 40, 30);
	static final Color MID = new Color(170, 150, 20);
	static final Color GOOD = new Color(46, 160, 67);

	static final Color CB_BAD = new Color(220, 120, 20);
	static final Color CB_MID = new Color(140, 140, 140);
	static final Color CB_GOOD = new Color(40, 110, 200);

	private static volatile boolean colourBlind;

	private Palette()
	{
	}

	private static volatile Color customBad;
	private static volatile Color customMid;
	/** True while the Custom scheme is picked: its colours are the player's own, so text on them is lifted. */
	private static volatile boolean customScheme;


	/** WCAG contrast ratio between two colours (1 to 21). */
	static double contrast(Color a, Color b)
	{
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	private static double luminance(Color c)
	{
		return 0.2126 * linear(c.getRed()) + 0.7152 * linear(c.getGreen()) + 0.0722 * linear(c.getBlue());
	}

	private static double linear(int v)
	{
		double s = v / 255.0;
		return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
	}

	public static void setColourBlind(boolean on)
	{
		colourBlind = on;
	}

	/**
	 * Picks the colours for dry to spooned. The ready-made sets keep spooned green (the
	 * colour-blind one has its own blue); Custom takes the player's dry and on rate colours.
	 */
	public static void setScheme(LuckColours scheme, Color dry, Color onRate)
	{
		LuckColours s = scheme == null ? LuckColours.CLASSIC : scheme;
		colourBlind = s == LuckColours.COLOUR_BLIND;
		customScheme = s == LuckColours.CUSTOM;
		switch (s)
		{
			case PURPLE_AMBER:
				customBad = new Color(110, 60, 170);
				customMid = new Color(200, 150, 30);
				break;
			case PINK_GREY:
				customBad = new Color(210, 90, 140);
				customMid = new Color(140, 140, 140);
				break;
			case MONO:
				customBad = new Color(110, 110, 110);
				customMid = new Color(150, 150, 150);
				break;
			case CUSTOM:
				customBad = dry;
				customMid = onRate;
				break;
			default:
				customBad = null;
				customMid = null;
				break;
		}
	}

	/** Colour for a luck percentile 0..1 (grey when unknown). */
	public static Color luck(double luck)
	{
		if (Double.isNaN(luck))
		{
			return ColorScheme.MEDIUM_GRAY_COLOR;
		}
		Color bad = customBad != null ? customBad : colourBlind ? CB_BAD : BAD;
		Color mid = customMid != null ? customMid : colourBlind ? CB_MID : MID;
		Color good = colourBlind ? CB_GOOD : GOOD;
		if (luck < 0.5)
		{
			return blend(bad, mid, luck / 0.5);
		}
		return blend(mid, good, (luck - 0.5) / 0.5);
	}

	/**
	 * The verdict colour on a boss page: the dry and spooned bands (a third of players each) are
	 * toned down, so only very dry and very spooned get the full colour.
	 */
	static Color verdict(double luck)
	{
		Color c = luck(luck);
		boolean mild = !Double.isNaN(luck) && (luck >= 0.10 && luck < 0.35 || luck > 0.65 && luck <= 0.90);
		return mild ? blend(c, new Color(170, 170, 170), 0.3) : c;
	}

	/** The weakest text contrast (WCAG AA) the Custom scheme's text is lifted to. */
	static final double TEXT_CONTRAST = 4.5;

	/**
	 * A luck colour for text. The ready-made sets are returned as they are; a Custom colour is
	 * lifted towards white until it reads at {@link #TEXT_CONTRAST}:1 on the panel's lightest
	 * card, so a dark pick stays legible. Only text uses this: bars, rings and dots keep the
	 * exact colour the player chose.
	 */
	public static Color text(Color c)
	{
		if (!customScheme || c == null)
		{
			return c;
		}
		Color lifted = c;
		for (int i = 1; i <= 20 && contrast(lifted, Theme.SURFACE_RAISED) < TEXT_CONTRAST; i++)
		{
			lifted = blend(c, Color.WHITE, i / 20.0);
		}
		return lifted;
	}

	/** The "you have it" colour. */
	public static Color good()
	{
		return colourBlind ? CB_GOOD : GOOD;
	}

	static Color blend(Color a, Color b, double t)
	{
		t = Math.max(0, Math.min(1, t));
		return new Color(
			(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}

	/** A quiet background in the hue of a luck colour (the pill under a verdict). */
	static Color tint(Color c)
	{
		return blend(Theme.SURFACE, c, 0.2);
	}

	/** Text on a {@link #tint}: the luck colour, lifted a little so it stays readable. */
	static Color onTint(Color c)
	{
		return readable(text(blend(c, Color.WHITE, 0.22)), tint(c));
	}

	/** False draws text colours as before the contrast fix; only the pixel comparison with the original panel turns it off. */
	static volatile boolean readableText = true;

	/**
	 * Text in {@code fg} that can be read on {@code bg}: {@code fg} itself when it already has a 4.5:1 contrast,
	 * otherwise lifted toward white as little as needed. Text only: the luck colours themselves (bars, dots,
	 * graph lines) are not changed.
	 */
	static Color readable(Color fg, Color bg)
	{
		if (!readableText || contrast(fg, bg) >= MIN_TEXT_CONTRAST)
		{
			return fg;
		}
		for (int step = 1; step <= 100; step++)
		{
			Color c = blend(fg, Color.WHITE, step / 100.0);
			if (contrast(c, bg) >= MIN_TEXT_CONTRAST)
			{
				return c;
			}
		}
		return Color.WHITE;
	}

	static final double MIN_TEXT_CONTRAST = 4.5;

	/** Luck-coloured text on a list row or tile (the panel's {@link Theme#SURFACE}). */
	static Color onRow(Color c)
	{
		return readable(text(c), Theme.SURFACE);
	}

	static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}
