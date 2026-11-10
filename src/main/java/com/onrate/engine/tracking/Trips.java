package com.onrate.engine.tracking;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.RowStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The current trip: the kills of one boss without a long break (longer for raids and other long
 * runs), the numbers just before the latest kill and what the trip has given so far.
 * <p>
 * Package-private and unsynchronized: reached only through {@link Tracker}, under its lock.
 */
final class Trips
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;
	private final Statistics statistics;
	private final SharedItems sharedItems;
	private final List<Note> outbox;

	/** A trip ends after this long without a kill, or when you kill another boss. */
	static final long TRIP_IDLE_MILLIS = 30 * 60_000;
	/**
	 * The same for content where one completion takes long: a raid, the Inferno or the Fight
	 * Caves often takes more than 30 minutes, which would start a new trip every time. Two hours
	 * leaves room for a slow raid plus a break; another boss or the button still end it sooner.
	 */
	static final long LONG_TRIP_IDLE_MILLIS = 120 * 60_000;
	private Trip trip;
	/** This boss's numbers just before the kill being counted (for the trip card and Recent). */
	private Trip beforeThisKill;

	Trips(TrackerContext ctx, Statistics statistics, SharedItems sharedItems, List<Note> outbox)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
		this.statistics = statistics;
		this.sharedItems = sharedItems;
		this.outbox = outbox;
	}

	/** The numbers just before the kill being counted now, or null outside a kill. */
	Trip beforeThisKill()
	{
		return beforeThisKill;
	}

	/** Another account was loaded: no trip carries over. */
	void clear()
	{
		trip = null;
		beforeThisKill = null;
	}

	/** This boss was reset: its trip ends. */
	void forget(String bossName)
	{
		if (trip != null && trip.boss.equals(bossName))
		{
			trip = null;
			beforeThisKill = null;
		}
	}

	/**
	 * GE value the current trip's own kills should give (their delve level, raid points, team),
	 * or NaN without a trip or when History lacks one of its kills.
	 */
	double tripValue(java.util.function.ToLongFunction<String> prices)
	{
		if (trip == null)
		{
			return Double.NaN;
		}
		BossDefinition boss = catalog.byName(trip.boss);
		BossProgress p = boss == null ? null : ctx.state().peek(boss.getName());
		BossStats s = boss == null ? null : statistics.stats(boss.getName(), ctx.settings());
		if (p == null || s == null)
		{
			return Double.NaN;
		}
		int kills = Math.max(0, s.getKillCount() - trip.startKc);
		double sum = 0;
		int found = 0;
		for (int i = p.kills.size() - 1; i >= 0 && found < kills; i--)
		{
			KillEntry k = p.kills.get(i);
			if (!k.isDateKnown())
			{
				// a drop added by hand, not a kill of this trip
				continue;
			}
			if (k.time < trip.startMillis)
			{
				break;
			}
			sum += statistics.perKill(boss, k, drop -> Math.max(0, prices.applyAsLong(drop.getName())));
			found++;
		}
		return found == kills && kills > 0 ? sum : Double.NaN;
	}

	/** True for content where one completion takes long: a raid, the Inferno or the Fight Caves. */
	static boolean isLongRun(BossDefinition boss)
	{
		String name = boss.getName();
		return boss.getRaid() != null || "TzKal-Zuk".equals(name) || "TzTok-Jad".equals(name);
	}

	static long tripIdleMillis(BossDefinition boss)
	{
		return isLongRun(boss) ? LONG_TRIP_IDLE_MILLIS : TRIP_IDLE_MILLIS;
	}

	boolean continuesTrip(BossDefinition boss, long nowMillis)
	{
		return trip != null && trip.boss.equals(boss.getName()) && nowMillis - trip.lastMillis <= tripIdleMillis(boss);
	}

	/**
	 * Called just before a kill is counted: remembers the numbers as they are now, so the trip
	 * card and Recent get exactly what this kill added (also for raids, whose points come in a
	 * moment after the kill).
	 */
	void beforeKill(BossDefinition boss, long nowMillis)
	{
		beforeThisKill = null;
		BossStats s = statistics.stats(boss.getName(), ctx.settings());
		if (s == null)
		{
			return;
		}
		Map<String, Integer> received = new HashMap<>();
		for (RowStats row : s.getRows())
		{
			received.put(row.getLabel(), row.getReceived());
		}
		RowStats any = s.getAnyRow();
		beforeThisKill = new Trip(boss.getName(), nowMillis, s.getKillCount(), any.getExpected(), any.getReceived(), received);
		beforeThisKill.poolSeenStart = sharedItems.poolSeenSnapshot(boss);
	}

	/**
	 * Called after each kill: continues the current trip, or starts a new one when this is
	 * another boss or the last kill was a while ago. The new trip includes this kill.
	 */
	void afterKill(BossDefinition boss, long nowMillis)
	{
		Trip before = beforeThisKill;
		beforeThisKill = null;
		if (continuesTrip(boss, nowMillis))
		{
			trip.lastMillis = nowMillis;
			return;
		}
		BossStats s = statistics.stats(boss.getName(), ctx.settings());
		if (s == null)
		{
			return;
		}
		if (before != null && before.boss.equals(boss.getName()) && s.getKillCount() == before.startKc + 1)
		{
			trip = before;
			return;
		}
		// no snapshot of exactly one kill ago (e.g. the first kill count after a long break
		// jumped by more than one): start from this kill at the average rate
		Map<String, Integer> received = new HashMap<>();
		for (RowStats row : s.getRows())
		{
			received.put(row.getLabel(), row.getReceived());
		}
		RowStats any = s.getAnyRow();
		trip = new Trip(boss.getName(), nowMillis, s.getKillCount() - 1,
			any.getExpected() - any.getRate(), any.getReceived(), received);
		trip.poolSeenStart = sharedItems.poolSeenSnapshot(boss);
	}

	/** How long without a kill ends the current trip (0 when there is none). */
	long tripIdleLimitMillis()
	{
		BossDefinition boss = trip == null ? null : catalog.byName(trip.boss);
		return boss == null ? 0 : tripIdleMillis(boss);
	}

	/** Forget the current trip; the next kill starts a new one. */
	void resetTrip()
	{
		trip = null;
	}

	/** The current trip's numbers, or null when there is no trip. */
	TripStats tripStats()
	{
		if (trip == null)
		{
			return null;
		}
		BossStats s = statistics.stats(trip.boss, ctx.settings());
		if (s == null)
		{
			return null;
		}
		List<String> drops = new ArrayList<>();
		for (RowStats row : s.getRows())
		{
			Integer before = trip.receivedPerRow.get(row.getLabel());
			int got = row.getReceived() - (before == null ? 0 : before);
			for (int i = 0; i < got; i++)
			{
				drops.add(row.getLabel());
			}
		}
		RowStats any = s.getAnyRow();
		int kills = Math.max(0, s.getKillCount() - trip.startKc);
		int received = Math.max(0, any.getReceived() - trip.startReceived);
		double expected = Math.max(0, any.getExpected() - trip.startExpected);
		// Shared items (godsword shards...) can drop here too, whether or not the player assigned
		// them to this boss: the trip counts them as this boss's drops, expected and received.
		BossDefinition tripBoss = catalog.byName(trip.boss);
		BossProgress progress = ctx.state().peek(trip.boss);
		double sharedExpected = 0;
		int sharedReceived = 0;
		List<String> sharedNames = new ArrayList<>();
		if (tripBoss != null)
		{
			// shared items the player assigned to this boss are rows after the boss's own
			List<DropDefinition> shown = s.getDrops();
			for (int i = tripBoss.getDrops().size(); i < s.getRows().size() && i < shown.size(); i++)
			{
				RowStats row = s.getRows().get(i);
				if (!statistics.counts(tripBoss, shown.get(i), progress))
				{
					continue;
				}
				Integer before = trip.receivedPerRow.get(row.getLabel());
				sharedExpected += row.getRate() * kills;
				sharedReceived += Math.max(0, row.getReceived() - (before == null ? 0 : before));
				sharedNames.add(row.getLabel());
			}
			for (Map.Entry<String, DropDefinition> e : catalog.poolsOf(tripBoss).entrySet())
			{
				DropDefinition drop = e.getValue();
				if (sharedItems.activeSplit(e.getKey()) != null || !statistics.counts(tripBoss, drop, progress))
				{
					continue; // already one of the boss's rows above, or left out of the total
				}
				double poolExpected = RateModel.effectiveRate(tripBoss, drop, ctx.settings()) * kills;
				expected += poolExpected;
				sharedExpected += poolExpected;
				sharedNames.add(drop.getDisplayName());
				Integer start = trip.poolSeenStart.get(e.getKey());
				int got = Math.max(0, sharedItems.poolSeenAt(trip.boss, e.getKey()) - (start == null ? 0 : start));
				received += got;
				sharedReceived += got;
				for (int i = 0; i < got; i++)
				{
					drops.add(drop.getDisplayName());
				}
			}
		}
		return new TripStats(trip.boss, trip.startMillis, trip.lastMillis, kills, received, expected, drops,
			sharedExpected, sharedReceived, sharedNames);
	}
}
