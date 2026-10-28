package com.expecteddrops.ui;

import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RowStats;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import org.junit.Test;

/** Lists keep every row, but draw the ones with nothing to read quietly, and say "have it" the same way everywhere. */
public class QuietRowsTest
{
	private static RowStats row(double expected, int received)
	{
		return new RowStats("Twisted buckler", "Twisted buckler", expected / 100, expected, received, false);
	}

	@Test
	public void aRowIsQuietOnlyWhenNoneAreGotAndLessThanHalfIsExpected()
	{
		assertTrue(Texts.isQuiet(row(0.37, 0)));
		assertTrue(Texts.isQuiet(row(0.0, 0)));
		assertTrue(Texts.isQuiet(row(Texts.TOO_EARLY_BELOW - 0.001, 0)));
		// the same line as the boss page's "Too early": half a unique is the first row with something to read
		assertFalse(Texts.isQuiet(row(Texts.TOO_EARLY_BELOW, 0)));
		assertFalse(Texts.isQuiet(row(1.52, 0)));
		// a drop is always worth reading, however little was expected
		assertFalse(Texts.isQuiet(row(0.19, 1)));
		assertFalse(Texts.isQuiet(row(Double.NaN, 0)));
	}

	@Test
	public void anyUniqueIsNeverQuiet()
	{
		assertFalse(Texts.isQuiet(new RowStats("Any", null, 0, 0.1, 0, true)));
	}

	@Test
	public void petsAndCappedItemsFollowTheSameRule()
	{
		assertTrue(Texts.isQuiet(row(0.11, 0).once(0)));
		assertFalse(Texts.isQuiet(row(0.11, 1).once(1)));
		// Wintertodt's warm gloves: up to 3 copies
		assertTrue(Texts.isQuiet(row(0.2, 0).capped(3, 0)));
		assertFalse(Texts.isQuiet(row(0.2, 2).capped(3, 2)));
	}

	@Test
	public void aQuietRowIsNeverDryOrLucky()
	{
		// this is why muting hides nothing: with none got and under half expected, every verdict is "On rate"
		for (double expected = 0.01; expected < Texts.TOO_EARLY_BELOW; expected += 0.01)
		{
			RowStats r = row(expected, 0);
			assertTrue(Texts.isQuiet(r));
			// ordinary items, pets (have it or not) and capped items (warm gloves, titan scrolls) alike
			for (RowStats each : new RowStats[]{r, r.once(0), r.capped(3, 0)})
			{
				assertEquals("expected " + expected, DropMath.ON_RATE, DropMath.verdict(each.getLuck()));
			}
		}
	}

	@Test
	public void haveItMatchesTheCopiesThatCount()
	{
		assertEquals("have it", Texts.haveText(row(0.5, 1).once(1)));
		assertEquals("have both", Texts.haveText(row(2, 2).capped(2, 2)));
		// warm gloves and bruma torches stop at 3: "have both" would be wrong
		assertEquals("have all 3", Texts.haveText(row(2, 3).capped(3, 3)));
		assertTrue(row(2, 3).capped(3, 3).isComplete());
	}

	@Test
	public void theMiddleOfTheScaleIsWhiteAndTheRestKeepAColour()
	{
		assertEquals(Theme.TEXT, ExpectedDropsPanel.verdictWordColor(0.5));
		assertEquals(Theme.TEXT, ExpectedDropsPanel.verdictWordColor(0.46));
		for (double luck : new double[]{0.02, 0.2, 0.4, 0.6, 0.8, 0.97})
		{
			assertNotEquals("luck " + luck, Theme.TEXT, ExpectedDropsPanel.verdictWordColor(luck));
		}
		// no luck known: no colour claim either way
		assertNotEquals(null, ExpectedDropsPanel.verdictWordColor(Double.NaN));
	}

	@Test
	public void verdictColoursStayApartInColourBlindMode()
	{
		try
		{
			Palette.setColourBlind(true);
			// orange, grey and blue: the dry and spooned ends differ in hue as well as lightness
			java.awt.Color dry = ExpectedDropsPanel.verdictWordColor(0.02);
			java.awt.Color spooned = ExpectedDropsPanel.verdictWordColor(0.97);
			assertTrue(dry.getRed() > dry.getBlue());
			assertTrue(spooned.getBlue() > spooned.getRed());
		}
		finally
		{
			Palette.setColourBlind(false);
		}
	}

	@Test
	public void aLongVerdictWordShrinksToFitTheTileAndGrowsBack()
	{
		VerdictTile tile = new VerdictTile("UNIQUES");
		tile.set("Very spooned", java.awt.Color.GREEN, "9 / 1.2", "1 in 3 as lucky", null);
		// the tile is 104px wide at the panel's 225px (scrollbar showing), 111px at 239px
		java.awt.Font normal = Theme.verdictFont();
		tile.setSize(new Dimension(104, 80));
		tile.doLayout();
		java.awt.Font narrow = tile.getComponent(1).getFont();
		assertTrue("shrunk " + narrow.getSize2D(), narrow.getSize2D() < normal.getSize2D());
		int room = 104 - tile.getInsets().left - tile.getInsets().right;
		assertTrue(tile.getComponent(1).getFontMetrics(narrow).stringWidth("Very spooned") <= room);
		tile.setSize(new Dimension(111, 80));
		tile.doLayout();
		assertEquals(normal.getSize2D(), tile.getComponent(1).getFont().getSize2D(), 0.001);
		// the longest words fit too, scrollbar showing
		for (String word : new String[]{"Mega dry", "Mega spooned"})
		{
			tile.set(word, java.awt.Color.GREEN, "9 / 1.2", "1 in 300 as lucky", null);
			tile.setSize(new Dimension(104, 80));
			tile.doLayout();
			javax.swing.JLabel shown = (javax.swing.JLabel) tile.getComponent(1);
			java.awt.FontMetrics fm = shown.getFontMetrics(shown.getFont());
			// every line of it fits: one line shrunk, or two at full size, never cut off
			for (String line : shown.getText().replaceAll("</?html>", "").split("<br>"))
			{
				assertTrue(word + ": " + line, fm.stringWidth(line) <= room);
			}
			assertEquals(word, shown.getText().replaceAll("</?html>", "").replace("<br>", " "));
			assertTrue(word, shown.getPreferredSize().width <= room);
		}
		// the word always takes two lines of room, so a short and a long verdict make the same tile
		int tall = tile.getComponent(1).getPreferredSize().height;
		tile.set("Spooned", java.awt.Color.GREEN, "9 / 1.2", "1 in 3 as lucky", null);
		tile.doLayout();
		assertEquals("Spooned", ((javax.swing.JLabel) tile.getComponent(1)).getText());
		assertEquals(tall, tile.getComponent(1).getPreferredSize().height);
		// a short word never changes
		tile.set("Dry", java.awt.Color.ORANGE, "1 / 3", "1 in 3 as dry", null);
		tile.setSize(new Dimension(104, 80));
		tile.doLayout();
		assertEquals(normal.getSize2D(), tile.getComponent(1).getFont().getSize2D(), 0.001);
	}

	@Test
	public void aLongSmallLineWrapsInsteadOfBeingCutOff()
	{
		VerdictTile tile = new VerdictTile("UNIQUES");
		tile.set("Very dry", java.awt.Color.RED, "0 / 3.4", "only 1 in 10,000 has none", null);
		tile.setSize(new Dimension(104, 80));
		tile.doLayout();
		javax.swing.JLabel luck = (javax.swing.JLabel) tile.getComponent(3);
		assertTrue(luck.getText(), luck.getText().startsWith("<html>") && luck.getText().contains("<br>"));
		// every line fits the 98px the tile has, and the words are all still there
		java.awt.FontMetrics fm = luck.getFontMetrics(luck.getFont());
		String[] lines = luck.getText().replace("<html>", "").replace("</html>", "").split("<br>");
		for (String line : lines)
		{
			assertTrue(line, fm.stringWidth(line) <= 98);
		}
		assertEquals("only 1 in 10,000 has none", String.join(" ", lines));
		// a "<1 in ..." line keeps its less-than sign as text
		tile.set("Very dry", java.awt.Color.RED, "0 / 3.4", "<1 in 10,000 as lucky", null);
		tile.doLayout();
		assertTrue(luck.getText(), luck.getText().contains("&lt;1 in 10,000 as") && luck.getText().contains("lucky")
			&& !luck.getText().contains("<1"));
		// with room it is plain text again, and a short line never changes
		tile.setSize(new Dimension(300, 80));
		tile.doLayout();
		assertEquals("<1 in 10,000 as lucky", luck.getText());
		tile.set("Dry", java.awt.Color.ORANGE, "1 / 3", "1 in 3 as dry", null);
		tile.setSize(new Dimension(104, 80));
		tile.doLayout();
		assertEquals("1 in 3 as dry", luck.getText());
	}
}
