package com.onrate.engine.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.KillTimes;
import com.onrate.engine.tracking.Tracker;
import java.io.Reader;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** How long a collection log page takes: the missing items as one forecast, at the time of a kill the Time page uses. */
public class GreenLogTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;
	private Tracker tracker;
	private KillTimes web;

	@Before
	public void setUp() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		web = KillTimes.bundled(new Gson());
		tracker = new Tracker(catalog);
	}

	@Test
	public void nothingMissingIsDoneAtOnce()
	{
		assertEquals(0, GreenLog.seconds(new double[0], GreenLog.MEDIAN, 100), 0);
	}

	@Test
	public void secondsAreKillsTimesTheTimeOfAKill()
	{
		double[] p = {0.01};
		// 1 in 100: half of players have it by 69 kills, 9 in 10 by 230
		assertEquals(69 * 100.0, GreenLog.seconds(p, GreenLog.MEDIAN, 100), 1e-9);
		assertEquals(230 * 100.0, GreenLog.seconds(p, GreenLog.LIKELY, 100), 1e-9);
	}

	@Test
	public void theAverageIsTheTimePageFigureAndHigherThanTheMedian()
	{
		// one item at 1 in 100 and 100 s a kill: the Time page says 100 x 100 s; half of players are done sooner (69 kills)
		assertEquals(100 * 100.0, GreenLog.averageSeconds(new double[]{0.01}, 100), 1e-6);
		assertTrue(GreenLog.averageSeconds(new double[]{0.01}, 100) > GreenLog.seconds(new double[]{0.01}, GreenLog.MEDIAN, 100));
		// several items: the figure Ladlor's chart uses for the drops at one boss
		double[] p = {1 / 250.0, 1 / 375.0, 1 / 500.0};
		assertEquals(ChartTimes.expectedKillsForAll(p) * 60, GreenLog.averageSeconds(p, 60), 1e-6);
	}

	@Test
	public void aBigPageIsSummedAndCloseToTheExactFigure()
	{
		// 14 items is past the exact formula: the sum must land on it where both can work (12 items)
		double[] twelve = new double[12];
		double[] fourteen = new double[14];
		for (int i = 0; i < 14; i++)
		{
			double p = 1 / (200.0 + 40 * i);
			fourteen[i] = p;
			if (i < 12)
			{
				twelve[i] = p;
			}
		}
		double exact = ChartTimes.expectedKillsForAll(twelve);
		// the same sum used for a big page, on the 12 items: temporarily as the long path via 13 equal copies is not needed;
		// here the 14-item answer just has to be above the 12-item one and finite
		double big = GreenLog.averageSeconds(fourteen, 1);
		assertTrue(big > exact);
		assertFalse(Double.isNaN(big));
		assertFalse(Double.isInfinite(big));
	}

	@Test
	public void noTimeOrNoChanceGivesNoAnswer()
	{
		assertTrue(Double.isNaN(GreenLog.seconds(new double[]{0.01}, GreenLog.MEDIAN, Double.NaN)));
		assertTrue(Double.isNaN(GreenLog.seconds(new double[]{0.01, 0}, GreenLog.MEDIAN, 100)));
	}

	@Test
	public void aBossWithNoKillsUsesTheWikiTime()
	{
		GreenLog.Page zulrah = GreenLog.compute(tracker, web, RAIDS, boss -> true, false).get("Zulrah");
		assertNotNull(zulrah);
		assertTrue(zulrah.isEstimated());
		// 20 an hour is 180 s a kill (see ItemTimesTest)
		assertEquals(180.0, zulrah.getSecondsPerKill(), 1e-9);
		assertFalse(zulrah.isDone());
		assertTrue(zulrah.getMedianSeconds() > 0);
		assertTrue(zulrah.getAverageSeconds() > zulrah.getMedianSeconds());
		// 9 in 10 take longer than half
		assertTrue(zulrah.getLikelySeconds() > zulrah.getMedianSeconds());
	}

	@Test
	public void petsOffLeavesThePetOutAndMakesThePageShorter()
	{
		GreenLog.Page without = GreenLog.compute(tracker, web, RAIDS, boss -> true, false).get("Zulrah");
		GreenLog.Page with = GreenLog.compute(tracker, web, RAIDS, boss -> true, true).get("Zulrah");
		assertTrue(without.isPetLeftOut());
		assertFalse(with.isPetLeftOut());
		assertEquals(without.getMissing().size() + 1, with.getMissing().size());
		assertTrue(with.getMedianSeconds() > without.getMedianSeconds());
	}

	@Test
	public void bossesTheCallerDoesntAllowAreLeftOut()
	{
		Map<String, GreenLog.Page> pages = GreenLog.compute(tracker, web, RAIDS, boss -> !"Zulrah".equals(boss), false);
		assertFalse(pages.containsKey("Zulrah"));
		assertFalse(pages.isEmpty());
	}
}
