package com.expecteddrops.stats;

/**
 * Probability helpers. Each kill is an independent roll, so the number of drops after
 * n kills is binomial; with the small rates bosses use, Poisson with the same mean is
 * an excellent approximation and lets us combine several items into one "any" row.
 */
public final class DropMath
{
	private DropMath()
	{
	}

	/** Parses "1/50", "3/512" or a plain decimal like "0.02". Returns 0 for null or blank. */
	public static double parseRate(String rate)
	{
		if (rate == null || rate.trim().isEmpty())
		{
			return 0;
		}
		String s = rate.replace(",", "").trim();
		int slash = s.indexOf('/');
		if (slash < 0)
		{
			return Double.parseDouble(s);
		}
		double num = Double.parseDouble(s.substring(0, slash).trim());
		double den = Double.parseDouble(s.substring(slash + 1).trim());
		if (den <= 0)
		{
			throw new IllegalArgumentException("Bad rate: " + rate);
		}
		return num / den;
	}

	/** Chance of at least one drop in {@code kc} kills at {@code rate} per kill. */
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

	/**
	 * How lucky a result is: the share of players who would have fewer drops than you,
	 * counting half of those with exactly as many (the "mid-p" percentile). 50% means
	 * perfectly average, above 50% means luckier than average. NaN when nothing is expected yet.
	 */
	public static double luckPercentile(int received, double expected)
	{
		if (expected <= 0 || Double.isNaN(expected))
		{
			return Double.NaN;
		}
		if (received < 0)
		{
			received = 0;
		}
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
		double p = below + 0.5 * equal;
		return Math.max(0, Math.min(1, p));
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

	/** "1/181" style text for a per-kill rate. */
	public static String formatRate(double rate)
	{
		if (rate <= 0)
		{
			return "unknown";
		}
		double inverse = 1.0 / rate;
		if (inverse < 10)
		{
			return String.format("1/%.2f", inverse);
		}
		if (Math.abs(inverse - Math.rint(inverse)) < 0.05 || inverse >= 1000)
		{
			return String.format("1/%,d", Math.round(inverse));
		}
		return String.format("1/%,.1f", inverse);
	}
}
