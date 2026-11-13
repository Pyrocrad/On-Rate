package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Luck-coloured text stays readable (4.5:1) on the tile, row and pill backgrounds in every colour set. */
public class PaletteContrastTest
{
	@Before
	public void on()
	{
		Palette.readableText = true;
	}

	@After
	public void back()
	{
		Palette.setScheme(LuckColours.CLASSIC, null, null);
	}

	private static void check(String what, Color text, Color bg)
	{
		double ratio = Palette.contrast(text, bg);
		assertTrue(what + " " + ratio, ratio >= 4.5 - 1e-9);
	}

	@Test
	public void contrastOfBlackAndWhiteIs21()
	{
		assertEquals(21.0, Palette.contrast(Color.WHITE, Color.BLACK), 1e-9);
		assertEquals(1.0, Palette.contrast(Color.GRAY, Color.GRAY), 1e-9);
	}

	@Test
	public void readableLeavesGoodColoursAlone()
	{
		assertEquals(Color.WHITE, Palette.readable(Color.WHITE, Theme.SURFACE));
	}

	@Test
	public void readableLiftsADarkColourTowardWhiteOnly()
	{
		Color dark = new Color(170, 40, 30);
		Color lifted = Palette.readable(dark, Theme.SURFACE);
		check("lifted", lifted, Theme.SURFACE);
		assertTrue(lifted.getRed() >= dark.getRed() && lifted.getGreen() >= dark.getGreen() && lifted.getBlue() >= dark.getBlue());
	}

	@Test
	public void rowTextIsReadableAcrossEverySetAndLuck()
	{
		for (LuckColours s : LuckColours.values())
		{
			Palette.setScheme(s, new Color(120, 20, 20), new Color(120, 120, 20));
			for (int i = 0; i <= 100; i++)
			{
				double luck = i / 100.0;
				check(s + " row " + luck, Palette.onRow(Palette.luck(luck).brighter()), Theme.SURFACE);
				check(s + " verdict row " + luck, Palette.onRow(Palette.verdict(luck).brighter()), Theme.SURFACE);
				Color hue = Palette.luck(luck);
				check(s + " pill " + luck, Palette.onTint(hue), Palette.tint(hue));
			}
		}
	}

	@Test
	public void presetColoursAreNotChanged()
	{
		Palette.setScheme(LuckColours.CLASSIC, null, null);
		assertEquals(new Color(170, 40, 30), Palette.luck(0));
	}

	@Test
	public void hintGreyMeetsTheMinimum()
	{
		check("hint", Theme.TEXT_MUTED, Theme.SURFACE);
	}
}
