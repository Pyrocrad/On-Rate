package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.awt.Color;
import org.junit.After;
import org.junit.Test;

/** Higher-contrast bars: off changes nothing; on lifts a fill to 3:1 against its track and leaves good fills alone. */
public class PaletteBarContrastTest
{
	@After
	public void back()
	{
		Palette.setHigherContrast(false);
		Palette.setScheme(LuckColours.CLASSIC, null, null);
	}

	@Test
	public void offByDefaultAndChangesNothing()
	{
		assertFalse(Palette.isHigherContrast());
		Color dry = Palette.luck(0);
		assertEquals(dry, Palette.barFill(dry, Theme.TRACK));
	}

	@Test
	public void onLiftsEveryLuckFillToThreeToOne()
	{
		Palette.setHigherContrast(true);
		for (LuckColours scheme : new LuckColours[] {LuckColours.CLASSIC, LuckColours.PURPLE_AMBER, LuckColours.MONO,
			LuckColours.COLOUR_BLIND})
		{
			Palette.setScheme(scheme, null, null);
			for (double luck = 0; luck <= 1.0001; luck += 0.25)
			{
				assertTrue(scheme + " " + luck, Palette.contrast(Palette.barFill(Palette.luck(luck), Theme.TRACK), Theme.TRACK) >= 3.0);
				assertTrue(scheme + " " + luck, Palette.contrast(Palette.barFill(Palette.luck(luck), Theme.TRACK_DARK), Theme.TRACK_DARK) >= 3.0);
			}
		}
	}

	@Test
	public void aFillThatAlreadyContrastsIsKept()
	{
		Palette.setHigherContrast(true);
		assertEquals(Color.WHITE, Palette.barFill(Color.WHITE, Theme.TRACK));
	}

	@Test
	public void theTimeBarFillContrastsWithItsTrack()
	{
		assertTrue(Palette.contrast(Palette.PROGRESS_HIGH, Theme.TRACK) >= 3.0);
		assertTrue(Palette.contrast(Palette.PROGRESS_HIGH, Theme.TRACK) > Palette.contrast(Theme.PROGRESS, Theme.TRACK));
	}

	@Test
	public void theWhiteMarkerStaysVisibleOnALiftedFill()
	{
		Palette.setHigherContrast(true);
		Palette.setScheme(LuckColours.CLASSIC, null, null);
		assertTrue(Palette.contrast(Color.WHITE, Palette.barFill(Palette.luck(1), Theme.TRACK)) >= 2.0);
	}
}
