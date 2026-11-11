package com.onrate.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/**
 * The account's GP value simulates thousands of accounts. That runs after the tracker's lock is let go,
 * so a kill or loot event on the game thread never waits for it.
 */
public class AccountLockTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;

	/** Every boss with a log page, with kills and one of each item: a full account. */
	private static Tracker fullAccount(int kills)
	{
		BossCatalog catalog = Bundled.catalog(new Gson());
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		for (BossDefinition boss : catalog.getBosses())
		{
			Map<String, Integer> items = new HashMap<>();
			for (DropDefinition d : boss.getDrops())
			{
				items.put(d.getName(), 1);
			}
			for (String page : boss.getClogPages())
			{
				tracker.onClogPage(page, Collections.singletonList(page + " kills: " + kills), items);
			}
		}
		return tracker;
	}

	@Test
	public void theTrackerIsFreeWhileTheGpValueIsWorkedOut() throws Exception
	{
		// around 1,500 kills a boss the simulation is at its slowest (a dozen of each item expected)
		Tracker tracker = fullAccount(1_500);
		// the part that does run under the lock, warmed up so it is as short as it gets
		for (int i = 0; i < 20; i++)
		{
			tracker.accountStats(RAIDS, null);
		}
		for (int attempt = 1; attempt <= 3; attempt++)
		{
			// another price each time, so the simulation runs again instead of being remembered
			long price = 1_000_000L + attempt;
			AtomicBoolean running = new AtomicBoolean(true);
			AtomicLong longestWait = new AtomicLong();
			// this thread plays the game thread: it asks the tracker something as often as it can and
			// notes the longest it had to wait for its turn
			Thread game = new Thread(() ->
			{
				while (running.get())
				{
					long asked = System.nanoTime();
					tracker.getLastBoss();
					long waited = System.nanoTime() - asked;
					if (waited > longestWait.get())
					{
						longestWait.set(waited);
					}
				}
			});
			game.start();
			long start = System.nanoTime();
			AccountStats account = tracker.accountStats(RAIDS, name -> price);
			long took = System.nanoTime() - start;
			running.set(false);
			game.join();
			assertFalse(Double.isNaN(account.getValueLuck()));
			if (took < 40_000_000L || longestWait.get() < took / 2)
			{
				// too quick on this machine to tell apart, or the game thread never waited out the simulation
				return;
			}
			if (attempt == 3)
			{
				fail(String.format("the account took %d ms and the game thread waited %d ms of it",
					took / 1_000_000, longestWait.get() / 1_000_000));
			}
		}
	}

	@Test
	public void theNumbersAreTheSameEachTimeAndThereIsNoValueWithoutPrices()
	{
		Tracker tracker = fullAccount(977);
		AccountStats first = tracker.accountStats(RAIDS, name -> 250_000L);
		AccountStats again = tracker.accountStats(RAIDS, name -> 250_000L);
		assertTrue(first.getExpectedValue() > 0);
		assertTrue(first.getReceivedValue() > 0);
		assertFalse(Double.isNaN(first.getValueLuck()));
		assertEquals(first.getValueLuck(), again.getValueLuck(), 0);
		assertEquals(first.getExpectedValue(), again.getExpectedValue(), 0);
		assertEquals(first.getReceivedValue(), again.getReceivedValue());
		AccountStats plain = tracker.accountStats(RAIDS, null);
		assertTrue(Double.isNaN(plain.getValueLuck()));
		assertEquals(0, plain.getReceivedValue());
		assertEquals(first.getTotal().getExpected(), plain.getTotal().getExpected(), 0);
	}
}
