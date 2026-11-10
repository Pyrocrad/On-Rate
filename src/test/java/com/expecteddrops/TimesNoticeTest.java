package com.expecteddrops;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The one-time notice about the times feature is only for players who had On Rate before it. */
public class TimesNoticeTest
{
	@Test
	public void aNewInstallHadNothingBefore()
	{
		assertFalse(ExpectedDropsPlugin.hadOnRateBefore(null, true));
	}

	@Test
	public void aPlayerWhoseWelcomeGuideOpenedHadItBefore()
	{
		assertTrue(ExpectedDropsPlugin.hadOnRateBefore("true", true));
	}

	@Test
	public void aPlayerWhoTurnedTheWelcomeGuideOffHadItBefore()
	{
		assertTrue(ExpectedDropsPlugin.hadOnRateBefore(null, false));
		assertTrue(ExpectedDropsPlugin.hadOnRateBefore("true", false));
	}

	@Test
	public void theNewsNoticeHasItsOwnKeySoPlayersWhoSawTheFirstOneGetItOnce()
	{
		assertTrue(com.onrate.store.Keys.NEWS_NOTICE.equals(ExpectedDropsPlugin.TIMES_NOTICE_KEY));
		assertFalse(com.onrate.store.Keys.TIMES_NOTICE.equals(ExpectedDropsPlugin.TIMES_NOTICE_KEY));
	}
}
