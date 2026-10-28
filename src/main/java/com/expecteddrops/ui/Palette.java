package com.expecteddrops.ui;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;

/**
 * Luck colours: orange (dry) to grey to blue (spooned), which colour-blind players can tell apart
 * too, or the classic red to yellow to green when the setting asks for it.
 */
public final class Palette
{
	static final Color BAD = new Color(170, 40, 30);
	static final Color MID = new Color(170, 150, 20);
	static final Color GOOD = new Color(46, 160, 67);

	static final Color CB_BAD = new Color(220, 120, 20);
	static final Color CB_MID = new Color(140, 140, 140);
	static final Color CB_GOOD = new Color(40, 110, 200);

	private static volatile boolean colourBlind = true;

	private Palette()
	{
	}

	public static void setColourBlind(boolean on)
	{
		colourBlind = on;
	}

	/** Colour for a luck percentile 0..1 (grey when unknown). */
	public static Color luck(double luck)
	{
		if (Double.isNaN(luck))
		{
			return ColorScheme.MEDIUM_GRAY_COLOR;
		}
		Color bad = colourBlind ? CB_BAD : BAD;
		Color mid = colourBlind ? CB_MID : MID;
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

	static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}
