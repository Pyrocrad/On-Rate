package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import org.junit.After;
import org.junit.Test;

/** The luck colour sets: Classic is unchanged, spooned stays green, and dry, on rate and spooned stay apart. */
public class PaletteSchemeTest
{
	private static final Color GREEN = new Color(46, 160, 67);
	private static final Color BLUE = new Color(40, 110, 200);

	@After
	public void back()
	{
		Palette.setScheme(LuckColours.CLASSIC, null, null);
	}

	private static double apart(Color a, Color b)
	{
		int r = a.getRed() - b.getRed();
		int g = a.getGreen() - b.getGreen();
		int bl = a.getBlue() - b.getBlue();
		return Math.sqrt(r * r + g * g + bl * bl);
	}

	@Test
	public void classicIsTheUsualRedYellowGreen()
	{
		Palette.setScheme(LuckColours.CLASSIC, new Color(1, 2, 3), new Color(4, 5, 6));
		assertEquals(new Color(170, 40, 30), Palette.luck(0));
		assertEquals(new Color(170, 150, 20), Palette.luck(0.5));
		assertEquals(GREEN, Palette.luck(1));
	}

	@Test
	public void colourBlindIsTheUsualOrangeGreyBlue()
	{
		Palette.setScheme(LuckColours.COLOUR_BLIND, null, null);
		assertEquals(new Color(220, 120, 20), Palette.luck(0));
		assertEquals(BLUE, Palette.luck(1));
		assertEquals(BLUE, Palette.good());
	}

	@Test
	public void everyReadySetKeepsDryOnRateAndSpoonedApart()
	{
		for (LuckColours s : LuckColours.values())
		{
			if (s == LuckColours.CUSTOM)
			{
				continue;
			}
			Palette.setScheme(s, null, null);
			Color dry = Palette.luck(0);
			Color mid = Palette.luck(0.5);
			Color spooned = Palette.luck(1);
			assertTrue(s + " dry vs on rate", apart(dry, mid) > 40);
			assertTrue(s + " on rate vs spooned", apart(mid, spooned) > 40);
			assertTrue(s + " dry vs spooned", apart(dry, spooned) > 40);
		}
	}

	@Test
	public void spoonedStaysGreenInEverySetExceptColourBlind()
	{
		for (LuckColours s : LuckColours.values())
		{
			Palette.setScheme(s, new Color(10, 10, 10), new Color(100, 100, 100));
			assertEquals(s.name(), s == LuckColours.COLOUR_BLIND ? BLUE : GREEN, Palette.luck(1));
		}
	}

	@Test
	public void customTakesYourDryAndOnRateColours()
	{
		Color dry = new Color(200, 0, 200);
		Color mid = new Color(0, 200, 200);
		Palette.setScheme(LuckColours.CUSTOM, dry, mid);
		assertEquals(dry, Palette.luck(0));
		assertEquals(mid, Palette.luck(0.5));
		assertEquals(GREEN, Palette.luck(1));
	}

	@Test
	public void unknownLuckIsStillGrey()
	{
		Palette.setScheme(LuckColours.MONO, null, null);
		assertNotEquals(GREEN, Palette.luck(Double.NaN));
	}

	@Test
	public void customTextIsLiftedToReadableContrastButNotTheColour()
	{
		Color darkDry = new Color(40, 0, 80);
		Palette.setScheme(LuckColours.CUSTOM, darkDry, new Color(60, 60, 0));
		assertEquals(darkDry, Palette.luck(0));
		Color text = Palette.text(Palette.luck(0));
		assertTrue(Palette.contrast(text, Theme.SURFACE_RAISED) >= Palette.TEXT_CONTRAST);
		assertNotEquals(darkDry, text);
	}

	@Test
	public void customTextThatAlreadyReadsIsLeftAlone()
	{
		Palette.setScheme(LuckColours.CUSTOM, Color.WHITE, Color.WHITE);
		assertEquals(Color.WHITE, Palette.text(Color.WHITE));
	}

	@Test
	public void readySetsKeepTheirTextColour()
	{
		for (LuckColours s : LuckColours.values())
		{
			if (s != LuckColours.CUSTOM)
			{
				Palette.setScheme(s, null, null);
				Color dry = Palette.luck(0).brighter();
				assertEquals(dry, Palette.text(dry));
			}
		}
	}
}
