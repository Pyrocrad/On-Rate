package com.onrate.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.math.Verdict;
import org.junit.Test;

/** The trip card and the overlay keep a smaller "Too early" cut-off than the boss page. */
public class TripTooEarlyTest
{
	@Test
	public void aTripIsTooEarlyOnlyUnderOneTwentiethOfAUnique()
	{
		assertEquals(0.05, Verdict.TRIP_TOO_EARLY_BELOW, 0);
		assertTrue(Verdict.tripTooEarly(0.0));
		assertTrue(Verdict.tripTooEarly(0.02));
		assertTrue(Verdict.tripTooEarly(Verdict.TRIP_TOO_EARLY_BELOW - 0.0001));
		assertFalse(Verdict.tripTooEarly(Verdict.TRIP_TOO_EARLY_BELOW));
		assertFalse(Verdict.tripTooEarly(0.3));
		// nothing expected has its own dash, not "Too early"
		assertFalse(Verdict.tripTooEarly(Double.NaN));
	}

	@Test
	public void theTripCutOffIsSmallerThanTheBossPageCutOff()
	{
		assertTrue(Verdict.TRIP_TOO_EARLY_BELOW < Verdict.TOO_EARLY_BELOW);
	}
}
