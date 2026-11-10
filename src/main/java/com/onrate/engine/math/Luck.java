package com.onrate.engine.math;

/**
 * Luck as a percentile in 0..1 (0.5 = average). Built from the {@link Poisson} shares, and
 * deliberately tie-aware: the most common result is never called dry.
 */
public final class Luck
{
	private Luck()
	{
	}

	/**
	 * Of the range from "share with fewer" to "share with this many or fewer", the point closest to
	 * the middle. So you only count as dry when few players have this many or fewer, and as spooned
	 * when few have this many or more. With big counts ties are rare and this is the usual percentile.
	 */
	public static double tieAware(double fewer, double same)
	{
		return Math.max(0, Math.min(1, Math.min(Math.max(0.5, fewer), fewer + same)));
	}

	/** Tie-aware luck behind every verdict. NaN when nothing is expected. */
	public static double of(int received, double expected)
	{
		double[] s = Poisson.shares(received, expected);
		return s == null ? Double.NaN : tieAware(s[0], s[1]);
	}

	/** Tie-aware luck when at most {@code cap} copies count. NaN when nothing is expected. */
	public static double ofCapped(int received, double expected, int cap)
	{
		double[] s = Poisson.sharesCapped(received, expected, cap);
		return s == null ? Double.NaN : tieAware(s[0], s[1]);
	}

	/**
	 * The mid-p percentile: players with fewer than you, plus half of those with exactly as many.
	 * NaN when nothing is expected yet.
	 */
	public static double midP(int received, double expected)
	{
		if (expected <= 0 || Double.isNaN(expected))
		{
			return Double.NaN;
		}
		double[] raw = Poisson.rawShares(Math.max(received, 0), expected);
		double p = raw[0] + 0.5 * raw[1];
		return Math.max(0, Math.min(1, p));
	}
}
