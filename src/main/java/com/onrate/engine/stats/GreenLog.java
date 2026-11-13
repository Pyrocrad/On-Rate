package com.onrate.engine.stats;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.data.KillTimes;
import com.onrate.engine.tracking.RateModel;
import com.onrate.engine.tracking.TimeStats;
import com.onrate.engine.tracking.Tracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * How long each boss's collection log page takes to finish: the items you don't have, each its own
 * roll (see {@link LogForecast}), at the time of one kill the Time page uses (your own average once you
 * have enough timed kills, else the OSRS Wiki's rate).
 *
 * <p>An item the log counts once for several bosses (dragon pickaxe) is on every page that lists it:
 * it is missing on all of them until you have it from anywhere, and each page uses its own boss's chance.
 * A shared item whose log page hasn't been opened yet is left out, as On Rate can't tell if you have it.
 */
public final class GreenLog
{
	/** Half of players with these chances are done by then. */
	public static final double MEDIAN = 0.5;
	/** Nine in ten players with these chances are done by then. */
	public static final double LIKELY = 0.9;

	private GreenLog()
	{
	}

	/** One boss's page. */
	public static final class Page
	{
		private final String boss;
		private final List<String> missing;
		private final boolean petLeftOut;
		private final double secondsPerKill;
		private final boolean estimated;
		private final String unit;
		private final double averageSeconds;
		private final double medianSeconds;
		private final double likelySeconds;

		Page(String boss, List<String> missing, boolean petLeftOut, double secondsPerKill, boolean estimated, String unit,
			double averageSeconds, double medianSeconds, double likelySeconds)
		{
			this.averageSeconds = averageSeconds;
			this.boss = boss;
			this.missing = Collections.unmodifiableList(missing);
			this.petLeftOut = petLeftOut;
			this.secondsPerKill = secondsPerKill;
			this.estimated = estimated;
			this.unit = unit;
			this.medianSeconds = medianSeconds;
			this.likelySeconds = likelySeconds;
		}

		public String getBoss()
		{
			return boss;
		}

		/** The items still missing, as the boss lists them. Empty when the page is done (pets aside). */
		public List<String> getMissing()
		{
			return missing;
		}

		public boolean isDone()
		{
			return missing.isEmpty();
		}

		/** True when pets are left out and the page's pet is still missing. */
		public boolean isPetLeftOut()
		{
			return petLeftOut;
		}

		/** Seconds of one kill (raid, delve) in this forecast; NaN when unknown. */
		public double getSecondsPerKill()
		{
			return secondsPerKill;
		}

		/** True when the time per kill is the wiki's rate, not your own timed kills. */
		public boolean isEstimated()
		{
			return estimated;
		}

		/** "kill", "raid" or "delve". */
		public String getUnit()
		{
			return unit;
		}

		/**
		 * The average time to get everything missing, the figure the Time page and Ladlor's chart give (for one
		 * item, its time per kill over its chance). 0 when done, NaN when it can't be said.
		 */
		public double getAverageSeconds()
		{
			return averageSeconds;
		}

		/** Seconds until half of players are done: 0 when done, NaN when it can't be said. */
		public double getMedianSeconds()
		{
			return medianSeconds;
		}

		/** Seconds until 9 in 10 players are done: 0 when done, NaN when it can't be said. */
		public double getLikelySeconds()
		{
			return likelySeconds;
		}
	}

	/** One missing item: its label, its chance in a kill and whether it's a pet. */
	private static final class Missing
	{
		final String label;
		final double chance;
		final boolean pet;

		Missing(String label, double chance, boolean pet)
		{
			this.label = label;
			this.chance = chance;
			this.pet = pet;
		}
	}

	/**
	 * The page of every boss {@code allowed} lets through that has something to go on, by boss name.
	 * A boss with no items counted (nothing known about its drops) isn't in the map.
	 *
	 * @param withPets false: pets are left out, so a page missing only its pet is done
	 */
	public static Map<String, Page> compute(Tracker tracker, KillTimes web, RaidSettings raids, Predicate<String> allowed,
		boolean withPets)
	{
		Map<String, List<ItemTime>> timed = new LinkedHashMap<>();
		for (ItemTime t : ItemTimes.compute(tracker, web, raids, allowed))
		{
			timed.computeIfAbsent(t.getBoss(), k -> new ArrayList<>()).add(t);
		}
		BossCatalog catalog = tracker.getCatalog();
		List<SharedPool> pools = tracker.sharedPools(raids);
		Map<String, Page> result = new LinkedHashMap<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			if (!allowed.test(boss.getName()) || boss.getWaves() > 0)
			{
				continue;
			}
			List<Missing> missing = new ArrayList<>();
			boolean petLeftOut = false;
			int known = 0;
			double seconds = Double.NaN;
			boolean estimated = true;
			String unit = Tracker.unit(boss);
			for (ItemTime t : timed.getOrDefault(boss.getName(), Collections.emptyList()))
			{
				known++;
				seconds = t.getSecondsPerKill();
				estimated = t.isEstimated();
				unit = t.getUnit();
				if (t.isHave())
				{
					continue;
				}
				if (t.isPet() && !withPets)
				{
					petLeftOut = true;
					continue;
				}
				missing.add(new Missing(t.getLabel(), t.getChancePerKill(), t.isPet()));
			}
			BossStats stats = null;
			for (SharedPool pool : pools)
			{
				if (!pool.isLogKnown() || !pool.getBosses().contains(boss.getName()))
				{
					continue;
				}
				for (BossCatalog.PoolSource src : catalog.getPools().getOrDefault(pool.getKey(), Collections.emptyList()))
				{
					if (!src.getBoss().getName().equals(boss.getName()))
					{
						continue;
					}
					known++;
					if (pool.getReceived() > 0)
					{
						continue;
					}
					DropDefinition drop = src.getDrop();
					if (drop.isPet() && !withPets)
					{
						petLeftOut = true;
						continue;
					}
					stats = stats != null ? stats : tracker.stats(boss.getName(), raids);
					missing.add(new Missing(pool.getLabel(), sharedChance(boss, drop, stats, raids), drop.isPet()));
				}
			}
			if (known == 0)
			{
				continue;
			}
			if (Double.isNaN(seconds))
			{
				// only shared items: the boss's time per kill is not on any item
				stats = stats != null ? stats : tracker.stats(boss.getName(), raids);
				seconds = ItemTimes.secondsPerKill(tracker, web, boss, stats);
				estimated = !ownTimes(tracker, boss, stats);
			}
			result.put(boss.getName(), page(boss.getName(), missing, petLeftOut, seconds, estimated, unit));
		}
		return result;
	}

	private static Page page(String boss, List<Missing> missing, boolean petLeftOut, double seconds, boolean estimated, String unit)
	{
		List<String> labels = new ArrayList<>();
		double[] chances = new double[missing.size()];
		for (int i = 0; i < chances.length; i++)
		{
			labels.add(missing.get(i).label);
			chances[i] = missing.get(i).chance;
		}
		return new Page(boss, labels, petLeftOut, seconds, estimated, unit,
			averageSeconds(chances, seconds), seconds(chances, MEDIAN, seconds), seconds(chances, LIKELY, seconds));
	}

	/** At most this many items are worked out exactly (see {@link ChartTimes#expectedKillsForAll}); more are summed kill by kill. */
	private static final int EXACT_ITEMS = 12;

	/**
	 * The average seconds to get every item: the same figure the Time page and Ladlor's chart give. It is higher
	 * than the median (half of players are done by then) because a few players wait very long.
	 */
	static double averageSeconds(double[] chances, double secondsPerKill)
	{
		if (chances.length == 0)
		{
			return 0;
		}
		for (double c : chances)
		{
			if (!(c > 0))
			{
				return Double.NaN;
			}
		}
		if (!(secondsPerKill > 0))
		{
			return Double.NaN;
		}
		if (chances.length <= EXACT_ITEMS)
		{
			return ChartTimes.expectedKillsForAll(chances) * secondsPerKill;
		}
		// a big page: the average wait is the sum over kills of the chance that something is still missing
		double kills = 0;
		double k = 0;
		while (k < 1e9)
		{
			double step = Math.max(1, Math.floor(k * 0.01));
			double still = 1 - LogForecast.chanceDone(chances, (long) k);
			if (still < 1e-12)
			{
				break;
			}
			kills += still * step;
			k += step;
		}
		return kills * secondsPerKill;
	}

	/** Seconds to finish with this chance, or NaN when an item can't drop or the time of a kill isn't known. */
	static double seconds(double[] chances, double chance, double secondsPerKill)
	{
		if (chances.length == 0)
		{
			return 0;
		}
		long kills = LogForecast.raidsFor(chances, chance);
		return kills == LogForecast.NEVER || !(secondsPerKill > 0) ? Double.NaN : kills * secondsPerKill;
	}

	/** The chance of a shared drop in one kill at this boss; for a combined page, the mode you did most. */
	private static double sharedChance(BossDefinition boss, DropDefinition drop, BossStats stats, RaidSettings raids)
	{
		if (!boss.hasParts())
		{
			return RateModel.effectiveRate(boss, drop, raids);
		}
		BossPart best = boss.getParts().get(0);
		Map<String, Integer> counts = stats == null ? null : stats.getPartCounts();
		int most = 0;
		for (BossPart part : boss.getParts())
		{
			int n = counts == null ? 0 : counts.getOrDefault(part.getName(), 0);
			if (n > most)
			{
				most = n;
				best = part;
			}
		}
		return RateModel.partRate(boss, best, drop, raids);
	}

	/** True when the boss's time per kill is from your own timed kills (enough of them), not the wiki. */
	private static boolean ownTimes(Tracker tracker, BossDefinition boss, BossStats stats)
	{
		int need = boss.getRaid() != null ? ItemTimes.MIN_TIMED_RAIDS : ItemTimes.MIN_TIMED_KILLS;
		if (boss.hasParts())
		{
			// parts: own times only when every part you did has enough
			Map<String, Integer> counts = stats == null ? null : stats.getPartCounts();
			boolean any = false;
			for (BossPart part : boss.getParts())
			{
				if (counts == null || counts.getOrDefault(part.getName(), 0) <= 0)
				{
					continue;
				}
				TimeStats own = tracker.killTimes(boss.getName(), part.getName());
				if (own == null || own.getCount() < need)
				{
					return false;
				}
				any = true;
			}
			return any;
		}
		TimeStats own = tracker.killTimes(boss.getName(), null);
		return own != null && own.getCount() >= need;
	}
}
