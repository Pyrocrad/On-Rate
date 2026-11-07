package com.expecteddrops.stats;

import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.BossPart;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.data.KillTimes;
import com.expecteddrops.tracking.TimeStats;
import com.expecteddrops.tracking.Tracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Average time to get each item: the time of one kill divided by the chance of the item in it.
 *
 * <p>The time of a kill is the average of your own timed kills once you have enough of them (5
 * raids, 20 of anything else), otherwise the bundled estimate (see {@link KillTimes}). The chance
 * is your own: for raids it comes from your points (the expected uniques On Rate already counts
 * from them), for Doom from the delve levels you really did.
 *
 * <p>Doom is a run: each delve level you complete rolls once at that level's rates, and the loot
 * of the whole run is claimed at the end (dying forfeits it). So the chance per delve depends on
 * how deep your runs go, and the time per delve on how long each level takes. Both come from your
 * own level counts and level times, level by level.
 */
public final class ItemTimes
{
	/** Timed raids needed before your own average replaces the estimate. */
	public static final int MIN_TIMED_RAIDS = 5;
	/** Timed kills needed before your own average replaces the estimate (any boss that isn't a raid). */
	public static final int MIN_TIMED_KILLS = 20;
	/** Timed delves of one level needed before your own average replaces the estimate for that level. */
	public static final int MIN_TIMED_DELVES = 20;

	private ItemTimes()
	{
	}

	/** The time per kill that goes into a boss, and where it comes from. */
	private static final class PerKill
	{
		double seconds;
		boolean estimated;
		boolean usesOwn;
		boolean lowConfidence;
		String basis;
		/** The part of a combined page the chances are taken from when there are no kills, else null. */
		BossPart assumedPart;
	}

	/** All items with a time, for every boss that has one; items you have are included (see {@link ItemTime#isHave()}). */
	public static List<ItemTime> compute(Tracker tracker, KillTimes web, RaidSettings raids)
	{
		List<ItemTime> result = new ArrayList<>();
		for (BossDefinition boss : tracker.getCatalog().getBosses())
		{
			if (boss.getWaves() > 0)
			{
				// a Colosseum run is claimed wave by wave: no time per kill to speak of
				continue;
			}
			BossStats stats = tracker.stats(boss.getName(), raids);
			if (stats == null)
			{
				continue;
			}
			if (boss.isDelve())
			{
				addDoom(result, tracker, web, boss, stats);
			}
			else
			{
				addBoss(result, tracker, web, raids, boss, stats);
			}
		}
		return result;
	}

	/** You have the item: at least one copy (or every copy that counts, for a capped item). */
	private static boolean hasAny(RowStats row)
	{
		return row.getReceived() > 0 || row.isComplete();
	}

	private static String unit(BossDefinition boss)
	{
		return boss.getRaid() != null ? "raid" : "kill";
	}

	private static void addBoss(List<ItemTime> out, Tracker tracker, KillTimes web, RaidSettings raids,
		BossDefinition boss, BossStats stats)
	{
		PerKill time = perKill(tracker, web, boss, stats);
		if (time == null)
		{
			return;
		}
		int kills = stats.getKillCount();
		List<RowStats> rows = stats.getRows();
		List<DropDefinition> drops = stats.getDrops();
		for (int i = 0; i < rows.size(); i++)
		{
			RowStats row = rows.get(i);
			if (row.isAny())
			{
				continue;
			}
			DropDefinition drop = drops != null && drops.size() == rows.size() ? drops.get(i) : null;
			double chance = 0;
			if (kills > 0 && row.getExpected() > 0)
			{
				// what your kills (your raid points) really gave, per kill
				chance = row.getExpected() / kills;
			}
			else if (drop != null)
			{
				chance = staticChance(boss, drop, time.assumedPart, raids);
			}
			else
			{
				chance = row.getRate();
			}
			if (!(chance > 0))
			{
				continue;
			}
			out.add(new ItemTime(boss.getName(), row.getShownLabel(), row.getIconItem(), drop != null && drop.isPet(),
				hasAny(row), time.seconds / chance, time.seconds, chance, time.estimated, time.usesOwn, time.lowConfidence,
				boss.getRaid() != null, time.basis, unit(boss)));
		}
	}

	private static double staticChance(BossDefinition boss, DropDefinition drop, BossPart part, RaidSettings raids)
	{
		if (boss.hasParts())
		{
			BossPart p = part != null ? part : boss.getParts().get(0);
			return Tracker.partRate(boss, p, drop, raids);
		}
		return Tracker.effectiveRate(boss, drop, raids);
	}

	/** Time per kill for a boss: kills (or each part, weighted by your kills of it) at your average or the estimate. */
	private static PerKill perKill(Tracker tracker, KillTimes web, BossDefinition boss, BossStats stats)
	{
		boolean raid = boss.getRaid() != null;
		int need = raid ? MIN_TIMED_RAIDS : MIN_TIMED_KILLS;
		String unit = unit(boss);
		if (!boss.hasParts())
		{
			return resolve(tracker, web, boss.getName(), null, need, unit);
		}
		Map<String, Integer> counts = stats.getPartCounts();
		double weighted = 0;
		double weights = 0;
		boolean estimated = false;
		boolean usesOwn = false;
		boolean low = false;
		List<String> bases = new ArrayList<>();
		for (BossPart part : boss.getParts())
		{
			int n = counts == null ? 0 : counts.getOrDefault(part.getName(), 0);
			if (n <= 0)
			{
				continue;
			}
			PerKill p = resolve(tracker, web, boss.getName(), part.getName(), need, unit);
			if (p == null)
			{
				continue;
			}
			weighted += p.seconds * n;
			weights += n;
			estimated |= p.estimated;
			usesOwn |= p.usesOwn;
			low |= p.lowConfidence;
			bases.add(part.getName() + ": " + p.basis);
		}
		if (weights > 0)
		{
			PerKill result = new PerKill();
			result.seconds = weighted / weights;
			result.estimated = estimated;
			result.usesOwn = usesOwn;
			result.lowConfidence = low;
			result.basis = String.join(" ", bases);
			return result;
		}
		// nothing done here yet: the first mode that has a time, with its own chances
		for (BossPart part : boss.getParts())
		{
			PerKill p = resolve(tracker, web, boss.getName(), part.getName(), need, unit);
			if (p != null)
			{
				p.assumedPart = part;
				p.basis = "No " + unit + "s of yours yet, so " + part.getName() + " is assumed. " + p.basis;
				return p;
			}
		}
		return null;
	}

	/** One boss or one part: your average when you have enough timed kills, else the estimate, else null. */
	private static PerKill resolve(Tracker tracker, KillTimes web, String boss, String part, int need, String unit)
	{
		TimeStats own = tracker.killTimes(boss, part);
		int have = own == null ? 0 : own.getCount();
		PerKill result = new PerKill();
		if (own != null && have >= need)
		{
			result.seconds = own.getAverageMillis() / 1000.0;
			result.usesOwn = true;
			result.basis = String.format(Locale.ROOT, "Your times: the average of your %,d timed %ss. It is fight time only, so banking and walking aren't in it.",
				have, unit);
			return result;
		}
		KillTimes.Estimate e = web.forBoss(boss, part);
		if (e == null)
		{
			return null;
		}
		result.seconds = e.getSecondsPerKill();
		result.estimated = true;
		result.lowConfidence = e.isLowConfidence();
		result.basis = String.format(Locale.ROOT, "Wiki rate: %s %ss an hour, from the OSRS Wiki's money-making guide. It includes banking and assumes strong gear and stats, "
				+ "so most players are slower. Your own average takes over at %d timed %ss; you have %d.",
			trim(e.getKillsPerHour()), unit, need, unit, have);
		return result;
	}

	private static String trim(double v)
	{
		return v == Math.rint(v) ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
	}

	// ---- Doom of Mokhaiotl

	private static void addDoom(List<ItemTime> out, Tracker tracker, KillTimes web, BossDefinition boss, BossStats stats)
	{
		KillTimes.Doom doom = web.doom();
		Map<Integer, Integer> levels = stats.getLevelCounts();
		int levelCount = BossDefinition.DELVE_LEVELS;
		double[] seconds = new double[levelCount + 1];
		boolean[] own = new boolean[levelCount + 1];
		for (int l = 1; l <= levelCount; l++)
		{
			TimeStats t = tracker.levelTimes(boss.getName(), l);
			own[l] = t != null && t.getCount() >= MIN_TIMED_DELVES;
			seconds[l] = own[l] ? t.getAverageMillis() / 1000.0 : doom.isAvailable() ? doom.secondsFor(l) : Double.NaN;
		}
		int total = 0;
		int runs = 0;
		if (levels != null)
		{
			for (Map.Entry<Integer, Integer> e : levels.entrySet())
			{
				total += e.getValue();
			}
			runs = levels.getOrDefault(1, 0);
		}
		List<RowStats> rows = stats.getRows();
		List<DropDefinition> drops = stats.getDrops();
		for (int i = 0; i < rows.size(); i++)
		{
			RowStats row = rows.get(i);
			DropDefinition drop = drops != null && drops.size() == rows.size() ? drops.get(i) : null;
			if (row.isAny() || drop == null)
			{
				continue;
			}
			double perDelve;
			double chance;
			double secondsPerDrop;
			boolean estimated = false;
			boolean usesOwn = false;
			String unit;
			String basis;
			if (total > 0)
			{
				// the depths you really went to: levels 1 to 8 and the deep delves, each with its own time
				double sum = 0;
				for (int l = 1; l <= levelCount; l++)
				{
					int n = levels.getOrDefault(l, 0);
					if (n > 0)
					{
						if (Double.isNaN(seconds[l]))
						{
							sum = Double.NaN;
							break;
						}
						sum += n * seconds[l];
						estimated |= !own[l];
						usesOwn |= own[l];
					}
				}
				if (Double.isNaN(sum))
				{
					continue;
				}
				// banking after each run is in the estimate's times, not in the game's fight times
				perDelve = sum / total + (estimated ? doom.getBankingSecondsPerRun() * runs / total : 0);
				chance = row.getExpected() / total;
				unit = "delve";
				basis = String.format(Locale.ROOT, "Your own depths: %,d delves in %,d runs, about %.1f levels a run, each level at its own time "
						+ "(your times once you have %d timed delves of that level, else the wiki rate). A delve's chance depends on its level, "
						+ "and only a claimed run keeps its loot.", total, Math.max(runs, 1), (double) total / Math.max(runs, 1), MIN_TIMED_DELVES);
				if (!(chance > 0))
				{
					continue;
				}
				secondsPerDrop = perDelve / chance;
			}
			else
			{
				// no delves of yours yet: a run to the depth most people farm
				int depth = Math.min(levelCount, Math.max(1, doom.getAssumedDepthWithoutHistory()));
				double run = 0;
				double chancePerRun = 0;
				for (int l = 1; l <= depth; l++)
				{
					if (Double.isNaN(seconds[l]))
					{
						run = Double.NaN;
						break;
					}
					run += seconds[l];
					chancePerRun += com.expecteddrops.stats.DropMath.parseRate(drop.getLevelRate(l));
				}
				if (Double.isNaN(run) || !(chancePerRun > 0))
				{
					continue;
				}
				estimated = true;
				run += doom.getBankingSecondsPerRun();
				perDelve = run;
				chance = chancePerRun;
				unit = "run";
				secondsPerDrop = run / chancePerRun;
				basis = String.format(Locale.ROOT, "Wiki rate: no delves of yours yet, so runs claimed at delve %d are assumed, at the OSRS Wiki guides' pace with banking. "
					+ "Deeper runs take longer and roll better rates.", depth);
			}
			out.add(new ItemTime(boss.getName(), row.getShownLabel(), row.getIconItem(), drop.isPet(), hasAny(row),
				secondsPerDrop, perDelve, chance, estimated, usesOwn, false, false, basis, unit));
		}
	}
}
