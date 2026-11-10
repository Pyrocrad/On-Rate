package com.onrate.service;

import com.onrate.engine.data.KillTimes;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.ItemTime;
import com.onrate.engine.stats.ItemTimes;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Works out the whole account (and every item's time) on a worker thread after a kill, so the
 * panel's next draw doesn't have to do it on the Swing thread. A burst of events (a raid ends with
 * several) runs one calculation.
 *
 * <p>The result is lent to the panel for exactly one draw: the one this class starts. Any other
 * draw (a click, a setting, the panel opening) finds nothing here and calculates as it always did.
 * So the numbers on screen are the same as before; only the thread that works them out changes.
 *
 * <p>A request can also move the panel to a boss (auto-follow after a kill). The newest such move is
 * kept until a draw runs, even when a later request that doesn't move is the one that draws, so the
 * panel ends on the newest boss as it did when every draw ran.
 *
 * <p>Nothing is worked out for a panel that isn't on screen: its draw is handed to the Swing thread
 * straight away, as the original did, and the panel itself decides there is nothing to draw.
 */
public final class AccountPrecompute
{
	private static final Logger log = LoggerFactory.getLogger(AccountPrecompute.class);

	/** One calculation and the draw to run when it is ready. */
	private static final class Job
	{
		final RaidSettings raids;
		final ToLongFunction<String> prices;
		final Runnable draw;
		final long seq;

		Job(RaidSettings raids, ToLongFunction<String> prices, Runnable draw, long seq)
		{
			this.raids = raids;
			this.prices = prices;
			this.draw = draw;
			this.seq = seq;
		}
	}

	/** A request's move to a boss, and the number of that request. */
	private static final class Nav
	{
		final Runnable show;
		final long seq;

		Nav(Runnable show, long seq)
		{
			this.show = show;
			this.seq = seq;
		}
	}

	private static final class Result
	{
		final boolean withPrices;
		final AccountStats account;
		final List<ItemTime> times;

		Result(boolean withPrices, AccountStats account, List<ItemTime> times)
		{
			this.withPrices = withPrices;
			this.account = account;
			this.times = times;
		}
	}

	private final Tracker tracker;
	private final Executor worker;
	private final Consumer<Runnable> onSwing;
	private final BooleanSupplier onScreen;
	private final AtomicReference<Job> next = new AtomicReference<>();
	/** The newest move to a boss that no draw has made yet. */
	private final AtomicReference<Nav> nav = new AtomicReference<>();
	private final AtomicBoolean running = new AtomicBoolean();
	private volatile boolean stopped;
	/** Number of the latest request: only its draw runs. */
	private final java.util.concurrent.atomic.AtomicLong latest = new java.util.concurrent.atomic.AtomicLong();
	/** Only set on the Swing thread, during the draw this class started. */
	private Result lent;

	/**
	 * @param onSwing runs a task on the Swing thread (SwingUtilities::invokeLater)
	 */
	public AccountPrecompute(Tracker tracker, Executor worker, Consumer<Runnable> onSwing)
	{
		this(tracker, worker, onSwing, () -> true);
	}

	/**
	 * @param onSwing runs a task on the Swing thread (SwingUtilities::invokeLater)
	 * @param onScreen whether the panel is on screen now; asked on the calling thread at each request
	 */
	public AccountPrecompute(Tracker tracker, Executor worker, Consumer<Runnable> onSwing, BooleanSupplier onScreen)
	{
		this.tracker = tracker;
		this.worker = worker;
		this.onSwing = onSwing;
		this.onScreen = onScreen;
	}

	/**
	 * Asks for the account to be worked out, then {@code draw} to run on the Swing thread with the
	 * result lent to it. Only the latest request's draw runs when requests pile up.
	 *
	 * @param prices GE prices, or null when the GP value setting is off (as the panel asks)
	 */
	public void request(RaidSettings raids, ToLongFunction<String> prices, Runnable draw)
	{
		request(raids, prices, null, draw);
	}

	/**
	 * As {@link #request(RaidSettings, ToLongFunction, Runnable)}; {@code show}, when not null, moves
	 * the panel to a boss instead of {@code draw}. The newest move survives later requests that don't
	 * move, so a merged burst still ends on the newest boss.
	 */
	public void request(RaidSettings raids, ToLongFunction<String> prices, Runnable show, Runnable draw)
	{
		if (stopped)
		{
			return;
		}
		long seq = latest.incrementAndGet();
		if (show != null)
		{
			nav.set(new Nav(show, seq));
		}
		if (!onScreen.getAsBoolean())
		{
			// a closed panel: nothing is worked out here. The draw goes to the Swing thread as it always
			// did (every one runs, in order), and anything still being worked out is never used.
			Runnable run = pick(draw);
			dropUpTo(seq);
			onSwing.accept(run);
			return;
		}
		next.set(new Job(raids, prices, draw, seq));
		if (running.compareAndSet(false, true))
		{
			worker.execute(this::loop);
		}
	}

	private void loop()
	{
		try
		{
			Job job;
			while (!stopped && (job = next.getAndSet(null)) != null)
			{
				Result result = null;
				try
				{
					AccountStats account = tracker.accountStats(job.raids, job.prices);
					List<ItemTime> times = ItemTimes.compute(tracker, KillTimes.bundled(), job.raids);
					result = new Result(job.prices != null, account, times);
				}
				catch (RuntimeException e)
				{
					// the panel works it out itself, as it always did
					log.debug("On Rate could not work out the account in the background", e);
				}
				Result ready = result;
				Job done = job;
				onSwing.accept(() -> deliver(ready, done));
			}
		}
		finally
		{
			running.set(false);
			if (next.get() != null && !stopped)
			{
				if (running.compareAndSet(false, true))
				{
					worker.execute(this::loop);
				}
			}
		}
	}

	private void deliver(Result result, Job job)
	{
		if (stopped || job.seq != latest.get())
		{
			// newer numbers are on their way (draw once, with those), or the data changed since
			// (see supersede): either way these are out of date
			return;
		}
		Runnable run = pick(job.draw);
		lent = result;
		try
		{
			run.run();
		}
		finally
		{
			lent = null;
		}
	}

	/** The newest waiting move to a boss (taken, so it runs once), or else {@code draw}. */
	private Runnable pick(Runnable draw)
	{
		Nav n = nav.getAndSet(null);
		return n == null ? draw : n.show;
	}

	/**
	 * The account for the draw in progress, or null when this draw wasn't started here (the panel then
	 * calculates it) or the GP value setting doesn't match what was worked out. Swing thread only.
	 */
	public AccountStats account(boolean withPrices)
	{
		Result r = lent;
		return r == null || r.withPrices != withPrices ? null : r.account;
	}

	/** Every item's time for the draw in progress, or null (the panel then calculates them). Swing thread only. */
	public List<ItemTime> itemTimes()
	{
		Result r = lent;
		return r == null ? null : r.times;
	}

	/**
	 * The data changed without a request (a click in the panel, another account loaded, a setting): any
	 * result worked out before now is never drawn, and its draw never runs, and a waiting move to a
	 * boss is dropped too (use this one when another account is loaded). The caller redraws itself.
	 * Safe from any thread.
	 */
	public void supersede()
	{
		dropUpTo(latest.incrementAndGet());
	}

	/**
	 * As {@link #supersede()}, for a change on the same account (a setting, a click that changes the
	 * data): the numbers worked out before it are still never drawn, but a waiting move to a boss is
	 * still made, once, on the Swing thread. The original moved to the boss at the kill itself, so a
	 * later setting or click left the panel on that boss. Safe from any thread.
	 */
	public void supersedeKeepingMove()
	{
		long seq = latest.incrementAndGet();
		Nav move = nav.get();
		if (move != null && move.seq < seq && nav.compareAndSet(move, null))
		{
			onSwing.accept(move.show);
		}
		dropUpTo(seq);
	}

	/**
	 * Takes the waiting job off the queue if it is older than {@code seq}. Never one that came in since:
	 * a request racing with this one from another thread keeps its place, and its draw still runs.
	 */
	private void dropUpTo(long seq)
	{
		Nav move = nav.get();
		if (move != null && move.seq < seq)
		{
			nav.compareAndSet(move, null);
		}
		Job waiting = next.get();
		if (waiting != null && waiting.seq < seq)
		{
			next.compareAndSet(waiting, null);
		}
	}

	public void stop()
	{
		stopped = true;
		next.set(null);
		nav.set(null);
	}
}
