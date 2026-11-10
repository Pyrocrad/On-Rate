package com.onrate.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToLongFunction;
import org.junit.Test;

public class AccountPrecomputeTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;
	private static final ToLongFunction<String> PRICES = n -> 1000L + Math.abs(n.hashCode() % 5000);

	private static Tracker tracker()
	{
		Tracker t = new Tracker(Bundled.catalog(new Gson()));
		t.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 500"), Collections.singletonMap("Vorkath's head", 9));
		t.onClogPage("Zulrah", Collections.singletonList("Zulrah kills: 900"), Collections.singletonMap("Tanzanite fang", 1));
		return t;
	}

	/** Swing stand-in: runs handed-over tasks when asked, on the test thread. */
	private static final class Queue
	{
		final List<Runnable> tasks = new ArrayList<>();

		synchronized void add(Runnable r)
		{
			tasks.add(r);
		}

		void runAll()
		{
			List<Runnable> now;
			synchronized (this)
			{
				now = new ArrayList<>(tasks);
				tasks.clear();
			}
			now.forEach(Runnable::run);
		}
	}

	@Test
	public void theDrawItStartsGetsTheSameNumbersThePanelWouldWorkOut() throws Exception
	{
		Tracker t = tracker();
		ExecutorService worker = Executors.newSingleThreadExecutor();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add);
		AccountStats[] seen = new AccountStats[1];
		pre.request(RAIDS, PRICES, () -> seen[0] = pre.account(true));
		worker.shutdown();
		assertTrue(worker.awaitTermination(20, TimeUnit.SECONDS));
		swing.runAll();
		assertNotNull(seen[0]);
		AccountStats direct = t.accountStats(RAIDS, PRICES);
		assertEquals(direct.getTotal().getExpected(), seen[0].getTotal().getExpected(), 0);
		assertEquals(direct.getTotal().getReceived(), seen[0].getTotal().getReceived());
		assertEquals(direct.getTotal().getLuck(), seen[0].getTotal().getLuck(), 0);
		assertEquals(direct.getValueLuck(), seen[0].getValueLuck(), 0);
		assertEquals(direct.getRows().size(), seen[0].getRows().size());
		// lent to that draw only: any other draw works it out itself
		assertNull(pre.account(true));
		assertNull(pre.itemTimes());
	}

	@Test
	public void aMismatchedGpSettingIsNotUsed() throws Exception
	{
		Tracker t = tracker();
		ExecutorService worker = Executors.newSingleThreadExecutor();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add);
		Object[] seen = new Object[2];
		pre.request(RAIDS, null, () ->
		{
			seen[0] = pre.account(true);
			seen[1] = pre.account(false);
		});
		worker.shutdown();
		worker.awaitTermination(20, TimeUnit.SECONDS);
		swing.runAll();
		assertNull(seen[0]);
		assertNotNull(seen[1]);
	}

	@Test
	public void aBurstDrawsOnceWithTheLatestRequest() throws Exception
	{
		Tracker t = tracker();
		ExecutorService worker = Executors.newSingleThreadExecutor();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add);
		List<Integer> drawn = new ArrayList<>();
		for (int i = 0; i < 50; i++)
		{
			int n = i;
			pre.request(RAIDS, PRICES, () -> drawn.add(n));
		}
		worker.shutdown();
		assertTrue(worker.awaitTermination(20, TimeUnit.SECONDS));
		swing.runAll();
		assertEquals(Collections.singletonList(49), drawn);
	}

	@Test
	public void stoppedDoesNothing() throws Exception
	{
		ExecutorService worker = Executors.newSingleThreadExecutor();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(tracker(), worker, swing::add);
		pre.stop();
		List<Integer> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add(1));
		worker.shutdown();
		worker.awaitTermination(5, TimeUnit.SECONDS);
		swing.runAll();
		assertTrue(drawn.isEmpty());
	}

	/** A tracker that counts how often the whole account is worked out. */
	private static final class CountingTracker extends Tracker
	{
		final AtomicInteger accounts = new AtomicInteger();

		CountingTracker()
		{
			super(Bundled.catalog(new Gson()));
			onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 500"), Collections.singletonMap("Vorkath's head", 9));
		}

		@Override
		public synchronized AccountStats accountStats(RaidSettings raids, ToLongFunction<String> prices)
		{
			accounts.incrementAndGet();
			return super.accountStats(raids, prices);
		}
	}

	/** Worker stand-in: keeps the tasks it is handed until the test runs them. */
	private static final class ManualWorker implements java.util.concurrent.Executor
	{
		final List<Runnable> tasks = new ArrayList<>();

		@Override
		public synchronized void execute(Runnable r)
		{
			tasks.add(r);
		}

		void runAll()
		{
			List<Runnable> now;
			synchronized (this)
			{
				now = new ArrayList<>(tasks);
				tasks.clear();
			}
			now.forEach(Runnable::run);
		}
	}

	@Test
	public void aHiddenPanelGetsNoBackgroundWork()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, () -> false);
		List<Object> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add(pre.account(true)));
		assertTrue("nothing goes to the worker", worker.tasks.isEmpty());
		swing.runAll();
		// the draw runs as the original's did, with nothing lent: the panel decides for itself
		assertEquals(Collections.singletonList(null), drawn);
		assertEquals(0, t.accounts.get());
	}

	@Test
	public void eachHiddenDrawRunsInOrderAsTheOriginalDid()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(new CountingTracker(), worker, swing::add, () -> false);
		List<Integer> drawn = new ArrayList<>();
		for (int i = 0; i < 3; i++)
		{
			int n = i;
			pre.request(RAIDS, PRICES, () -> drawn.add(n));
		}
		swing.runAll();
		// a kill's "follow this boss" draw is never dropped for a later one
		assertEquals(java.util.Arrays.asList(0, 1, 2), drawn);
	}

	@Test
	public void aVisiblePanelUsesTheBackgroundPath()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, () -> true);
		List<Object> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add(pre.account(true)));
		assertEquals(1, worker.tasks.size());
		swing.runAll();
		assertTrue("nothing drawn before the numbers are ready", drawn.isEmpty());
		worker.runAll();
		assertEquals(1, t.accounts.get());
		swing.runAll();
		assertEquals(1, drawn.size());
		assertNotNull(drawn.get(0));
	}

	@Test
	public void hidingThePanelWhileWorkIsPendingDropsThatWork()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AtomicBoolean visible = new AtomicBoolean(true);
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, visible::get);
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("background"));
		visible.set(false);
		pre.request(RAIDS, PRICES, () -> drawn.add("hidden"));
		swing.runAll();
		worker.runAll();
		swing.runAll();
		// the pending calculation is never run, and its draw never comes after the newer one
		assertEquals(Collections.singletonList("hidden"), drawn);
		assertEquals(0, t.accounts.get());
	}

	@Test
	public void hidingAfterTheWorkIsDoneStillNeverDrawsItLate()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AtomicBoolean visible = new AtomicBoolean(true);
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, visible::get);
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("background"));
		worker.runAll();
		// worked out and waiting for the Swing thread when the panel closes and another change comes
		visible.set(false);
		pre.request(RAIDS, PRICES, () -> drawn.add("hidden"));
		swing.runAll();
		assertEquals(Collections.singletonList("hidden"), drawn);
	}

	@Test
	public void showingThePanelAgainRestoresBackgroundUpdates()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AtomicBoolean visible = new AtomicBoolean(false);
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, visible::get);
		List<Object> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("hidden"));
		swing.runAll();
		visible.set(true);
		pre.request(RAIDS, PRICES, () -> drawn.add(pre.account(true)));
		assertEquals(1, worker.tasks.size());
		worker.runAll();
		swing.runAll();
		assertEquals(2, drawn.size());
		assertEquals("hidden", drawn.get(0));
		assertNotNull(drawn.get(1));
		assertEquals(1, t.accounts.get());
		assertFalse(drawn.get(1) instanceof String);
	}

	// audit F2: results worked out before the data changed are never drawn

	@Test
	public void aResultFromAnEarlierRequestIsIgnoredAfterANewerOne()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, () -> true);
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("first"));
		worker.runAll();
		pre.request(RAIDS, PRICES, () -> drawn.add("second"));
		swing.runAll();
		assertTrue("the first result is out of date once a newer request came", drawn.isEmpty());
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("second"), drawn);
	}

	@Test
	public void aWorkedOutResultIsIgnoredAfterSupersede()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(new CountingTracker(), worker, swing::add, () -> true);
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("stale"));
		worker.runAll();
		// a click changed the data and redrew the panel before the result reached the Swing thread
		pre.supersede();
		swing.runAll();
		assertTrue("the draw callback of an invalidated result never runs", drawn.isEmpty());
		assertNull(pre.account(true));
	}

	@Test
	public void supersedeBeforeTheWorkStartsSkipsTheWork()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, () -> true);
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("stale"));
		pre.supersede();
		worker.runAll();
		swing.runAll();
		assertTrue(drawn.isEmpty());
		assertEquals("nothing is worked out for a request that is already out of date", 0, t.accounts.get());
	}

	@Test
	public void anotherAccountLoadedInvalidatesWhatWasWorkedOutForTheLastOne()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, () -> true);
		List<AccountStats> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add(pre.account(true)));
		worker.runAll();
		int rowsOfOld = t.accountStats(RAIDS, PRICES).getRows().size();
		assertTrue(rowsOfOld > 0);
		// what the plugin does on a profile switch: load the other account, then supersede
		t.setState(new com.onrate.engine.tracking.TrackerState());
		pre.supersede();
		swing.runAll();
		assertTrue("the last account's numbers are never drawn", drawn.isEmpty());
		// the next request is for the account loaded now
		pre.request(RAIDS, PRICES, () -> drawn.add(pre.account(true)));
		worker.runAll();
		swing.runAll();
		assertEquals(1, drawn.size());
		assertEquals(0, drawn.get(0).getRows().size());
	}

	@Test
	public void aCurrentResultIsStillDeliveredAfterAnEarlierSupersede()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(new CountingTracker(), worker, swing::add, () -> true);
		List<AccountStats> drawn = new ArrayList<>();
		pre.supersede();
		pre.request(RAIDS, PRICES, () -> drawn.add(pre.account(true)));
		worker.runAll();
		swing.runAll();
		assertEquals(1, drawn.size());
		assertNotNull(drawn.get(0));
	}

	@Test
	public void supersedeFromOtherThreadsNeverLetsAnOldDrawThrough() throws Exception
	{
		Tracker t = tracker();
		ExecutorService worker = Executors.newSingleThreadExecutor();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, () -> true);
		java.util.concurrent.atomic.AtomicInteger oldDraws = new java.util.concurrent.atomic.AtomicInteger();
		Thread clicks = new Thread(() ->
		{
			for (int i = 0; i < 2000; i++)
			{
				pre.supersede();
			}
		});
		clicks.start();
		for (int i = 0; i < 200; i++)
		{
			pre.request(RAIDS, PRICES, oldDraws::incrementAndGet);
		}
		clicks.join();
		// every request so far came before (or raced with) the clicks; this one is the newest
		boolean[] last = new boolean[1];
		pre.supersede();
		pre.request(RAIDS, PRICES, () -> last[0] = pre.account(true) != null);
		worker.shutdown();
		assertTrue(worker.awaitTermination(30, TimeUnit.SECONDS));
		swing.runAll();
		assertEquals("only the newest request draws", 0, oldDraws.get());
		assertTrue(last[0]);
	}

	// --- auto-follow across merged requests: the panel must end where the original's would ---

	private static AccountPrecompute visible(ManualWorker worker, Queue swing, AtomicBoolean shown)
	{
		return new AccountPrecompute(new CountingTracker(), worker, swing::add, shown::get);
	}

	@Test
	public void severalBossEventsInOneBurstEndOnTheNewestBoss()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = visible(worker, swing, new AtomicBoolean(true));
		List<String> drawn = new ArrayList<>();
		for (String boss : new String[]{"Vorkath", "Zulrah", "Kraken"})
		{
			pre.request(RAIDS, PRICES, () -> drawn.add("show " + boss), () -> drawn.add("refresh"));
		}
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("show Kraken"), drawn);
	}

	@Test
	public void aFollowArrivingWhileTheLastIsWorkedOutIsTheOneShown()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = visible(worker, swing, new AtomicBoolean(true));
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("show Vorkath"), () -> drawn.add("refresh"));
		// the first is being worked out when the next kill comes in
		Runnable loop = worker.tasks.remove(0);
		pre.request(RAIDS, PRICES, () -> drawn.add("show Zulrah"), () -> drawn.add("refresh"));
		loop.run();
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("show Zulrah"), drawn);
	}

	@Test
	public void aNonFollowingEventLastInTheBurstStillEndsOnTheBoss()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = visible(worker, swing, new AtomicBoolean(true));
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("show Vorkath"), () -> drawn.add("refresh"));
		Runnable loop = worker.tasks.remove(0);
		// raid points or an item update after the kill: no move of its own
		pre.request(RAIDS, PRICES, null, () -> drawn.add("refresh"));
		loop.run();
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("show Vorkath"), drawn);
		// the move is made once: the next plain update only refreshes
		pre.request(RAIDS, PRICES, null, () -> drawn.add("refresh"));
		worker.runAll();
		swing.runAll();
		assertEquals(java.util.Arrays.asList("show Vorkath", "refresh"), drawn);
	}

	@Test
	public void aSupersededRequestNeverDrawsItsNumbers()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = visible(worker, swing, new AtomicBoolean(true));
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("show Vorkath"), () -> drawn.add("old"));
		worker.runAll();
		// a newer request comes in before the old result reaches the Swing thread
		pre.request(RAIDS, PRICES, null, () -> drawn.add("new " + (pre.account(true) != null)));
		swing.runAll();
		assertTrue("the old result draws nothing", drawn.isEmpty());
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("show Vorkath"), drawn);

		// another account loaded: neither the old numbers nor the old boss move are used
		drawn.clear();
		pre.request(RAIDS, PRICES, () -> drawn.add("show Zulrah"), () -> drawn.add("refresh"));
		worker.runAll();
		pre.supersede();
		swing.runAll();
		pre.request(RAIDS, PRICES, null, () -> drawn.add("refresh"));
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("refresh"), drawn);
	}

	@Test
	public void aSingleKillFollowsWithTheWorkedOutNumbers()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = visible(worker, swing, new AtomicBoolean(true));
		List<Object> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add(pre.account(true)), () -> drawn.add("refresh"));
		worker.runAll();
		swing.runAll();
		assertEquals(1, drawn.size());
		assertNotNull("the move draws with the numbers lent to it", drawn.get(0));
		assertNull(pre.account(true));
		// no move asked: a plain refresh
		drawn.clear();
		pre.request(RAIDS, PRICES, null, () -> drawn.add("refresh"));
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("refresh"), drawn);
	}

	@Test
	public void closingAndReopeningThePanelWhileWorkIsPendingKeepsTheNewestBoss()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AtomicBoolean shown = new AtomicBoolean(true);
		AccountPrecompute pre = visible(worker, swing, shown);
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("show Vorkath"), () -> drawn.add("refresh"));
		shown.set(false);
		// closed: each draw goes straight to the Swing thread; the pending move goes with the first
		pre.request(RAIDS, PRICES, null, () -> drawn.add("refresh"));
		pre.request(RAIDS, PRICES, () -> drawn.add("show Zulrah"), () -> drawn.add("refresh"));
		worker.runAll();
		swing.runAll();
		assertEquals(java.util.Arrays.asList("show Vorkath", "show Zulrah"), drawn);
		// reopened: back on the background path, with nothing stale waiting
		shown.set(true);
		drawn.clear();
		pre.request(RAIDS, PRICES, null, () -> drawn.add("refresh"));
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("refresh"), drawn);
	}

	@Test
	public void aSettingChangedWhileAFollowIsPendingStillEndsOnTheBoss()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = visible(worker, swing, new AtomicBoolean(true));
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("show Vorkath " + (pre.account(true) != null)), () -> drawn.add("old"));
		worker.runAll();
		// a setting changes before the result reaches the Swing thread; the plugin then refreshes
		pre.supersedeKeepingMove();
		swing.add(() -> drawn.add("refresh"));
		swing.runAll();
		assertEquals("the move is made, without the out-of-date numbers, then the refresh",
			java.util.Arrays.asList("show Vorkath false", "refresh"), drawn);
		// made once: the next plain update only refreshes
		drawn.clear();
		pre.request(RAIDS, PRICES, null, () -> drawn.add("refresh"));
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("refresh"), drawn);
	}

	@Test
	public void aSettingChangedBeforeTheWorkStartsKeepsTheMoveAndSkipsTheWork()
	{
		CountingTracker t = new CountingTracker();
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = new AccountPrecompute(t, worker, swing::add, () -> true);
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, () -> drawn.add("show Zulrah"), () -> drawn.add("old"));
		pre.supersedeKeepingMove();
		worker.runAll();
		swing.runAll();
		assertEquals(Collections.singletonList("show Zulrah"), drawn);
		assertEquals(0, t.accounts.get());
	}

	@Test
	public void aSettingChangeWithNoFollowPendingMovesNothing()
	{
		ManualWorker worker = new ManualWorker();
		Queue swing = new Queue();
		AccountPrecompute pre = visible(worker, swing, new AtomicBoolean(true));
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, PRICES, null, () -> drawn.add("stale"));
		worker.runAll();
		pre.supersedeKeepingMove();
		swing.runAll();
		assertTrue(drawn.isEmpty());
	}
}
