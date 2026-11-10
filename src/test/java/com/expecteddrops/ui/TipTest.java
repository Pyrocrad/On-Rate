package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TipTest
{
	private static int count(String text, String part)
	{
		return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
	}

	@Test
	public void rowsNextToEachOtherShareOneTable()
	{
		String tip = new Tip("Vorkath's head").row("Drop rate", "1/50 per kill").row("Got / expected", "0 / 1.2").wide();
		assertEquals(1, count(tip, "<table"));
		assertEquals(2, count(tip, "<tr>"));
		assertTrue(tip.contains("Drop rate"));
		assertTrue(tip.contains("1/50 per kill"));
	}

	@Test
	public void aLineBetweenRowsStartsANewTable()
	{
		String tip = new Tip("Item").row("Got / expected", "0 / 0.7").line("Rate per delve level").row("Level 2", "1/900").wide();
		assertEquals(2, count(tip, "<table"));
		assertEquals(2, count(tip, "</table>"));
		// a table ends its own line: no blank line before the text after it
		assertFalse(tip.contains("</table><br>"));
	}

	@Test
	public void emptyValuesAreLeftOut()
	{
		String tip = new Tip("Item").row("Without one", null).row("Drop rate", "").row("Got / expected", "1 / 1.0").wide();
		assertEquals(1, count(tip, "<tr>"));
		assertFalse(tip.contains("Without one"));
	}

	@Test
	public void noteAfterRowsHasNoBlankLine()
	{
		String tip = new Tip("Item").row("Got / expected", "1 / 1.0").note("Counted from your collection log.").wide();
		assertTrue(tip.contains("</table><font"));
	}

	@Test
	public void noteAfterTextStartsOnItsOwnLine()
	{
		String tip = new Tip("Item").line("1/50 per kill").note("A caveat.").wide();
		assertTrue(tip.contains("1/50 per kill<br><font"));
	}

	@Test
	public void lessThanSignBeforeADigitIsSafeInRows()
	{
		String tip = new Tip("Item").row("Chance", "<1%").wide();
		assertTrue(tip.contains("&lt;1%"));
	}

	@Test
	public void tipWithoutRowsHasNoTable()
	{
		String tip = new Tip("Change the look").line("Now: bars").small();
		assertFalse(tip.contains("<table"));
		assertTrue(tip.contains("<b>Change the look</b><br>Now: bars"));
	}
}
