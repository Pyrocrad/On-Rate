package com.onrate.engine.stats;

/**
 * How many more raids it takes to get every item you're missing, from each item's chance a raid.
 * Each item is taken as its own roll, which is very close for raid uniques: at most one purple a
 * raid at most raids, and each item is a small share of it. A forecast, not a promise: the raids
 * you already did don't change it.
 */
public final class LogForecast
{
	/** No answer: an item can't drop at these chances, or it would take more raids than this. */
	public static final long NEVER = -1;
	private static final long MAX_RAIDS = 100_000_000L;

	private LogForecast()
	{
	}

	/** The chance to have every item after {@code raids} more raids (0 to 1). */
	public static double chanceDone(double[] perRaid, long raids)
	{
		double done = 1;
		for (double p : perRaid)
		{
			if (!(p > 0))
			{
				return 0;
			}
			// 1 - (1 - p)^n, kept accurate for tiny chances
			done *= -Math.expm1(raids * Math.log1p(-Math.min(p, 1)));
		}
		return done;
	}

	/**
	 * The fewest raids after which you'd have every item with this chance (0.5: half of players with
	 * these chances would be done by then). 0 when nothing is missing, {@link #NEVER} when it can't.
	 */
	public static long raidsFor(double[] perRaid, double chance)
	{
		if (perRaid.length == 0)
		{
			return 0;
		}
		if (chanceDone(perRaid, MAX_RAIDS) < chance)
		{
			return NEVER;
		}
		long lo = 0;
		long hi = MAX_RAIDS;
		while (lo < hi)
		{
			long mid = (lo + hi) >>> 1;
			if (chanceDone(perRaid, mid) >= chance)
			{
				hi = mid;
			}
			else
			{
				lo = mid + 1;
			}
		}
		return lo;
	}
}
