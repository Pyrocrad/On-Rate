package com.expecteddrops.ui;

import com.onrate.engine.math.Rates;

import static org.junit.Assert.assertEquals;

import java.util.Locale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** The panel's text is English, so its numbers are too, whatever language the computer is set to. */
public class LocaleTest
{
	private Locale before;

	@Before
	public void useDutch()
	{
		before = Locale.getDefault();
		Locale.setDefault(new Locale("nl", "NL"));
	}

	@After
	public void restore()
	{
		Locale.setDefault(before);
	}

	@Test
	public void numbersKeepEnglishPointsAndCommas()
	{
		assertEquals("1.20b", Rates.gp(1_200_000_000));
		assertEquals("1.5k", Rates.gp(1_500));
		assertEquals("1/5,000", Rates.format(1 / 5000.0));
		assertEquals("2.60", Texts.formatExpected(2.6));
		assertEquals("2.1×", Texts.formatMultiple(2.1));
		assertEquals("99.5%", Texts.sharePct(0.995));
		assertEquals("1,234 / 2.60", String.format(Locale.ROOT, "%,d / %s", 1234, Texts.formatExpected(2.6)));
	}
}
