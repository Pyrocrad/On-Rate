package com.onrate.engine;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.tracking.TripStats;
import com.onrate.engine.tracking.Tracker;
import java.io.Reader;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** The finished trips the Recent tab lists: when a trip counts as finished, the cap, and what clears them. */
public class TripListTest
{
	private static final long MIN = 60_000L;
	private Tracker tracker;
	private final long start = 1_000_000;

	@Before
	public void setUp() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		tracker = new Tracker(catalog);
		tracker.setSettings(raid -> 0.0);
	}

	/** Kills kc .. kc + count - 1 of the boss, a minute apart from {@code at}; returns the time of the last one. */
	private long kills(String boss, int kc, int count, long at)
	{
		long t = at;
		for (int i = 0; i < count; i++)
		{
			t = at + i * MIN;
			tracker.onChatMessage("Your " + boss + " kill count is: <col=ff0000>" + (kc + i) + "</col>.", t);
		}
		return t;
	}

	@Test
	public void anotherBossEndsTheTripAndItIsListedOnce()
	{
		long last = kills("Vorkath", 100, 5, start);
		assertTrue(tracker.recentTrips(last).isEmpty());
		TripStats live = tracker.tripStats();
		assertNotNull(live);
		kills("Zulrah", 50, 1, last + 5 * MIN);
		List<TripStats> list = tracker.recentTrips(last + 6 * MIN);
		assertEquals(1, list.size());
		assertEquals("Vorkath", list.get(0).getBoss());
		// the numbers are the trip's own, not one kill longer
		assertEquals(live.getKills(), list.get(0).getKills());
		assertEquals(live.getExpected(), list.get(0).getExpected(), 1e-9);
		// more kills elsewhere do not list it again
		kills("Zulrah", 51, 3, last + 7 * MIN);
		assertEquals(1, tracker.recentTrips(last + 20 * MIN).stream().filter(t -> t.getBoss().equals("Vorkath")).count());
	}

	@Test
	public void theIdleLimitIsInclusive()
	{
		long last = kills("Vorkath", 100, 4, start);
		// exactly 30 minutes later the trip goes on
		kills("Vorkath", 104, 1, last + 30 * MIN);
		assertTrue(tracker.recentTrips(last + 30 * MIN).isEmpty());
		long again = last + 30 * MIN;
		// one minute over the limit ends it
		kills("Vorkath", 105, 1, again + 31 * MIN);
		List<TripStats> list = tracker.recentTrips(again + 31 * MIN);
		assertEquals(1, list.size());
		assertEquals("Vorkath", list.get(0).getBoss());
	}

	@Test
	public void anIdleTripIsListedWithoutWaitingForTheNextKill()
	{
		long last = kills("Vorkath", 100, 4, start);
		assertTrue(tracker.recentTrips(last + 30 * MIN).isEmpty());
		assertEquals(1, tracker.recentTrips(last + 31 * MIN).size());
		// the next kill then archives that same trip: still once
		kills("Vorkath", 104, 1, last + 40 * MIN);
		assertEquals(1, tracker.recentTrips(last + 41 * MIN).size());
	}

	@Test
	public void theNewTripButtonListsTheTripOnce()
	{
		long last = kills("Vorkath", 100, 4, start);
		tracker.resetTrip();
		assertEquals(1, tracker.recentTrips(last).size());
		tracker.resetTrip();
		kills("Vorkath", 104, 1, last + MIN);
		assertEquals(1, tracker.recentTrips(last + MIN).size());
	}

	@Test
	public void veryShortTripsAreNotListed()
	{
		long last = kills("Vorkath", 100, 2, start);
		kills("Zulrah", 50, 1, last + MIN);
		assertTrue(tracker.recentTrips(last + 2 * MIN).isEmpty());
	}

	@Test
	public void keepsTheLastFiveNewestFirst()
	{
		long t = start;
		int vorkath = 100;
		int zulrah = 50;
		for (int i = 0; i < 7; i++)
		{
			if (i % 2 == 0)
			{
				t = kills("Vorkath", vorkath, 3, t) + MIN;
				vorkath += 3;
			}
			else
			{
				t = kills("Zulrah", zulrah, 3, t) + MIN;
				zulrah += 3;
			}
		}
		// seven trips were started; six are finished, the seventh (Vorkath) is still going
		List<TripStats> list = tracker.recentTrips(t);
		assertEquals(5, list.size());
		assertEquals("Zulrah", list.get(0).getBoss());
		assertEquals("Vorkath", list.get(1).getBoss());
	}

	@Test
	public void resettingABossDropsItsTrips()
	{
		long last = kills("Vorkath", 100, 4, start);
		last = kills("Zulrah", 50, 4, last + MIN);
		kills("Vorkath", 104, 1, last + MIN);
		assertEquals(2, tracker.recentTrips(last + MIN).size());
		tracker.reset("Vorkath");
		List<TripStats> list = tracker.recentTrips(last + MIN);
		assertEquals(1, list.size());
		assertEquals("Zulrah", list.get(0).getBoss());
	}

	@Test
	public void switchingAccountsClearsTheList()
	{
		long last = kills("Vorkath", 100, 4, start);
		kills("Zulrah", 50, 1, last + MIN);
		assertEquals(1, tracker.recentTrips(last + MIN).size());
		tracker.setState(null);
		assertTrue(tracker.recentTrips(last + 2 * MIN).isEmpty());
		assertTrue(tracker.recentTrips(last + 500 * MIN).isEmpty());
	}
}
