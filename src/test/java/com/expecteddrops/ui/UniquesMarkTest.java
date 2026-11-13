package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The triangle after a coloured unique count on the Recent tab. */
public class UniquesMarkTest
{
	@Test
	public void markOnlyOutsideTheMiddleBand()
	{
		assertEquals(Texts.MARK_MORE, Texts.uniquesMark(0.66, 1.0, true));
		assertEquals(Texts.MARK_FEWER, Texts.uniquesMark(0.34, 1.0, true));
		assertEquals("", Texts.uniquesMark(0.35, 1.0, true));
		assertEquals("", Texts.uniquesMark(0.5, 1.0, true));
		assertEquals("", Texts.uniquesMark(0.65, 1.0, true));
	}

	@Test
	public void noMarkWhenNothingToCompare()
	{
		assertEquals("", Texts.uniquesMark(Double.NaN, 1.0, true));
		assertEquals("", Texts.uniquesMark(0.1, 0.0, true));
	}

	@Test
	public void plainSignsWhenTheFontHasNoTriangles()
	{
		assertEquals("+", Texts.uniquesMark(0.9, 1.0, false));
		assertEquals("-", Texts.uniquesMark(0.1, 1.0, false));
	}
}
