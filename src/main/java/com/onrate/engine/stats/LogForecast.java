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
		return chanceDone(perRaid, null, raids);
	}

	/**
	 * The chance to have every item after {@code raids} more raids, when item {@code i} needs {@code copies[i]}
	 * more drops (the Hydra's ring pieces: one roll gives the next piece, so the row needs three drops).
	 * Null copies means one of each.
	 */
	public static double chanceDone(double[] perRaid, int[] copies, long raids)
	{
		double done = 1;
		for (int i = 0; i < perRaid.length; i++)
		{
			double p = perRaid[i];
			if (!(p > 0))
			{
				return 0;
			}
			int need = copies == null ? 1 : copies[i];
			if (need <= 0)
			{
				continue;
			}
			done *= need == 1
				// 1 - (1 - p)^n, kept accurate for tiny chances
				? -Math.expm1(raids * Math.log1p(-Math.min(p, 1)))
				: atLeast(need, raids, p);
		}
		return done;
	}

	/** The chance of at least {@code need} drops in {@code n} rolls at chance {@code p} each. */
	static double atLeast(int need, long n, double p)
	{
		if (p >= 1)
		{
			return n >= need ? 1 : 0;
		}
		if (n < need)
		{
			return 0;
		}
		// 1 minus the chance of 0, 1, ... need - 1 drops; each term from the one before
		double term = Math.exp(n * Math.log1p(-p));
		double fewer = 0;
		for (int j = 0; j < need; j++)
		{
			fewer += term;
			term *= (double) (n - j) / (j + 1) * p / (1 - p);
		}
		return Math.max(0, Math.min(1, 1 - fewer));
	}

	/**
	 * The fewest raids after which you'd have every item with this chance (0.5: half of players with
	 * these chances would be done by then). 0 when nothing is missing, {@link #NEVER} when it can't.
	 */
	public static long raidsFor(double[] perRaid, double chance)
	{
		return raidsFor(perRaid, null, chance);
	}

	/** As {@link #raidsFor(double[], double)}, when item {@code i} needs {@code copies[i]} more drops (null: one each). */
	public static long raidsFor(double[] perRaid, int[] copies, double chance)
	{
		if (perRaid.length == 0)
		{
			return 0;
		}
		if (chanceDone(perRaid, copies, MAX_RAIDS) < chance)
		{
			return NEVER;
		}
		long lo = 0;
		long hi = MAX_RAIDS;
		while (lo < hi)
		{
			long mid = (lo + hi) >>> 1;
			if (chanceDone(perRaid, copies, mid) >= chance)
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
