package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The thin bar under a Time row: longer grinds fill more of it, on a scale of tens. */
public class EffortBarTest
{
	private static double hours(double h)
	{
		return EffortBar.fraction(h * 3600);
	}

	@Test
	public void noTimeIsAnEmptyBar()
	{
		assertEquals(0, EffortBar.fraction(Double.NaN), 0);
		assertEquals(0, EffortBar.fraction(0), 0);
		assertEquals(0, EffortBar.fraction(-5), 0);
	}

	@Test
	public void longerGrindsFillMore()
	{
		assertTrue(hours(0.25) < hours(3));
		assertTrue(hours(3) < hours(30));
		assertTrue(hours(30) < hours(300));
		assertTrue(hours(300) < hours(900));
	}

	@Test
	public void eachTenTimesLongerAddsAboutTheSame()
	{
		// 9 h, 99 h and 999 h are 1, 2 and 3 steps of ten on the scale of 1 + hours
		assertEquals(hours(99) - hours(9), hours(999) - hours(99), 1e-9);
		assertEquals(1.0, hours(999), 1e-3);
	}

	@Test
	public void theBarIsFullAtAThousandHoursAndStaysThere()
	{
		assertEquals(1.0, hours(EffortBar.FULL_HOURS), 1e-12);
		assertEquals(1.0, hours(5000), 0);
		assertTrue(hours(0.5) > 0 && hours(0.5) < 0.2);
	}
}
