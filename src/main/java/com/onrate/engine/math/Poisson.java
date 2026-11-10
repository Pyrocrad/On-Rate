package com.onrate.engine.math;

import java.util.List;

/**
 * Probability of drop counts. Every kill is an independent roll, so the number of drops after
 * n kills is binomial; with the small rates bosses use a Poisson with the same mean is an
 * excellent approximation, and it lets several items be added into one "any unique" row.
 *
 * <p>A "shares" result is {share of players with fewer than you, share with exactly as many};
 * the rest have more.
 */
public final class Poisson
{
	private Poisson()
	{
	}

	/** Chance of at least one drop in {@code kc} kills at {@code rate} per kill (binomial). */
	public static double chanceAtLeastOne(double rate, long kc)
	{
		if (kc <= 0 || rate <= 0)
		{
			return 0;
		}
		if (rate >= 1)
		{
			return 1;
		}
		return -Math.expm1(kc * Math.log1p(-rate));
	}

	/** Chance of at least one event when {@code expected} events are expected (Poisson). */
	public static double chanceAtLeastOne(double expected)
	{
		if (expected <= 0)
		{
			return 0;
		}
		return -Math.expm1(-expected);
	}

	/** Kills needed to reach {@code probability} chance of at least one drop. -1 if never. */
	public static long killsForChance(double rate, double probability)
	{
		if (rate <= 0 || probability <= 0)
		{
			return -1;
		}
		if (rate >= 1)
		{
			return 1;
		}
		return (long) Math.ceil(Math.log1p(-probability) / Math.log1p(-rate));
	}

	/** Expected number of copies that count when at most {@code cap} can: E[min(X, cap)], X Poisson. */
	public static double expectedCapped(double expected, int cap)
	{
		if (expected <= 0 || cap <= 0)
		{
			return 0;
		}
		double sum = 0;
		double p = Math.exp(-expected);
		double cdf = 0;
		for (int k = 0; k < cap; k++)
		{
			cdf += p;
			sum += 1 - cdf;
			p *= expected / (k + 1);
		}
		return sum;
	}

	/** Expected copies that count: the plain expected, or E[min(X, cap)] when at most {@code cap} count. */
	public static double expectedCounted(double expected, int cap)
	{
		return cap > 0 ? expectedCapped(expected, cap) : expected;
	}

	/** Shares for a plain (uncapped) count; null when nothing is expected. */
	public static double[] shares(int received, double expected)
	{
		if (expected <= 0 || Double.isNaN(expected))
		{
			return null;
		}
		double[] raw = rawShares(Math.max(received, 0), expected);
		return new double[]{Math.min(1, raw[0]), Math.min(1, raw[1])};
	}

	/**
	 * Unclamped {fewer, same} for {@code received} drops when {@code expected} are expected.
	 * Shared by {@link #shares} and {@link Luck#midP} so both use the very same arithmetic.
	 */
	static double[] rawShares(int received, double expected)
	{
		double logLambda = Math.log(expected);
		double logFactorial = 0;
		double below = 0;
		for (int j = 0; j < received; j++)
		{
			if (j > 0)
			{
				logFactorial += Math.log(j);
			}
			below += Math.exp(-expected + j * logLambda - logFactorial);
		}
		if (received > 0)
		{
			logFactorial += Math.log(received);
		}
		double equal = Math.exp(-expected + received * logLambda - logFactorial);
		return new double[]{below, equal};
	}

	/**
	 * Shares when at most {@code cap} copies count: everyone with {@code cap} or more is level with
	 * you. Null when nothing is expected.
	 */
	public static double[] sharesCapped(int received, double expected, int cap)
	{
		if (expected <= 0 || Double.isNaN(expected))
		{
			return null;
		}
		int r = Math.min(Math.max(received, 0), cap);
		double p = Math.exp(-expected);
		double below = 0;
		for (int k = 0; k < r; k++)
		{
			below += p;
			p *= expected / (k + 1);
		}
		double same = r < cap ? p : 1 - below;
		return new double[]{below, Math.max(0, same)};
	}

	/** {@link #sharesSum(int, int, double, double[], int[], List)} without guaranteed items. */
	public static double[] sharesSum(int received, int sure, double plain, double[] cappedMeans, int[] caps)
	{
		return sharesSum(received, sure, plain, cappedMeans, caps, null);
	}

	/**
	 * Shares for a total made of several items: {@code sure} copies that drop for certain, a part
	 * with no limit ({@code plain} expected), items of which at most {@code caps[i]} count (each
	 * with its uncapped mean {@code cappedMeans[i]}), and items that are sure on one kill only for a
	 * player with none yet ({@code guaranteed}: {kills, rate per kill, kill with the guarantee}).
	 * A capped item's count isn't Poisson, so the total isn't either: this adds the exact
	 * distributions. Null when nothing is expected.
	 */
	public static double[] sharesSum(int received, int sure, double plain, double[] cappedMeans, int[] caps,
		List<double[]> guaranteed)
	{
		int n = cappedMeans == null ? 0 : cappedMeans.length;
		int g = guaranteed == null ? 0 : guaranteed.size();
		if (n == 0 && g == 0 && sure <= 0)
		{
			return shares(received, plain);
		}
		double total = Math.max(0, sure) + (Double.isNaN(plain) ? 0 : Math.max(0, plain));
		for (int i = 0; i < n; i++)
		{
			total += expectedCapped(cappedMeans[i], caps[i]);
		}
		for (int i = 0; i < g; i++)
		{
			double[] item = guaranteed.get(i);
			total += guaranteedExpected(item[0], item[1], (int) item[2]);
		}
		if (!(total > 0))
		{
			return null;
		}
		int r = Math.max(received, 0) - Math.max(0, sure);
		if (r < 0)
		{
			// fewer than the sure copies: nobody has as few
			return new double[]{0, 0};
		}
		// only counts up to r matter for "fewer" and "the same"
		double[] pmf = poissonPmf(Double.isNaN(plain) ? 0 : plain, r);
		for (int i = 0; i < n; i++)
		{
			if (!(cappedMeans[i] > 0) || caps[i] <= 0)
			{
				continue;
			}
			double[] item = poissonPmf(cappedMeans[i], caps[i]);
			double rest = 1;
			for (int k = 0; k < caps[i]; k++)
			{
				rest -= item[k];
			}
			item[caps[i]] = Math.max(0, rest);
			pmf = convolve(pmf, item, caps[i], r, true);
		}
		for (int i = 0; i < g; i++)
		{
			double[] item = guaranteed.get(i);
			double[] own = guaranteedPmf(item[0], item[1], (int) item[2], r);
			pmf = convolve(pmf, own, r, r, false);
		}
		double below = 0;
		for (int k = 0; k < r; k++)
		{
			below += pmf[k];
		}
		return new double[]{Math.min(1, below), Math.min(1, pmf[r])};
	}

	/** Adds two independent counts, keeping totals up to {@code r}. */
	private static double[] convolve(double[] left, double[] right, int rightMax, int r, boolean skipEmptyLeft)
	{
		double[] next = new double[r + 1];
		for (int a = 0; a <= r; a++)
		{
			if (skipEmptyLeft && left[a] == 0)
			{
				continue;
			}
			for (int b = 0; b <= rightMax && a + b <= r; b++)
			{
				next[a + b] += left[a] * right[b];
			}
		}
		return next;
	}

	/**
	 * Expected copies of an item rolled at {@code rate} per kill that is also sure on kill
	 * {@code at} for a player who has none yet (Vorkath's head on the 50th): kills * rate, plus the
	 * extra copy that kill gives, (1 - rate)^at, from then on.
	 */
	public static double guaranteedExpected(double kills, double rate, int at)
	{
		double expected = kills * rate;
		return kills >= at ? expected + Math.pow(1 - rate, at) : expected;
	}

	/**
	 * Exact probabilities of 0..{@code max} copies after {@code kills} kills at {@code rate}, when
	 * kill {@code at} is a sure drop for a player with none from kills 1 to at - 1 and a plain roll
	 * otherwise. Nobody who has been through kill {@code at} has none.
	 */
	public static double[] guaranteedPmf(double kills, double rate, int at, int max)
	{
		int total = (int) Math.round(kills);
		if (total < at)
		{
			return binomialPmf(total, rate, max);
		}
		double miss = 1 - rate;
		double[] before = binomialPmf(at - 1, rate, max);
		// kills 1 to at - 1, then kill at: sure with none so far, a plain roll with one or more
		double[] upTo = new double[max + 1];
		for (int k = 0; k <= max; k++)
		{
			if (k == 0)
			{
				if (max >= 1)
				{
					upTo[1] += before[0];
				}
				continue;
			}
			upTo[k] += before[k] * miss;
			if (k + 1 <= max)
			{
				upTo[k + 1] += before[k] * rate;
			}
		}
		double[] after = binomialPmf(total - at, rate, max);
		return convolve(upTo, after, max, max, false);
	}

	private static double[] binomialPmf(int n, double p, int max)
	{
		double[] pmf = new double[max + 1];
		if (p <= 0)
		{
			pmf[0] = 1;
			return pmf;
		}
		if (p >= 1)
		{
			if (n <= max)
			{
				pmf[n] = 1;
			}
			return pmf;
		}
		double logChoose = 0;
		for (int k = 0; k <= Math.min(n, max); k++)
		{
			if (k > 0)
			{
				logChoose += Math.log((double) (n - k + 1) / k);
			}
			pmf[k] = Math.exp(logChoose + k * Math.log(p) + (n - k) * Math.log1p(-p));
		}
		return pmf;
	}

	private static double[] poissonPmf(double mean, int max)
	{
		double[] pmf = new double[max + 1];
		if (!(mean > 0))
		{
			pmf[0] = 1;
			return pmf;
		}
		double logMean = Math.log(mean);
		double logFactorial = 0;
		for (int k = 0; k <= max; k++)
		{
			if (k > 0)
			{
				logFactorial += Math.log(k);
			}
			pmf[k] = Math.exp(-mean + k * logMean - logFactorial);
		}
		return pmf;
	}
}
