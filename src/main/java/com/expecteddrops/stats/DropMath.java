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

	/**
	 * A word for a luck percentile, from the player's point of view.
	 * Empty when there's nothing to judge yet.
	 */
	public static String verdict(double luck)
	{
		if (Double.isNaN(luck))
		{
			return "";
		}
		if (luck < 0.10)
		{
			return "Very dry";
		}
		if (luck < 0.35)
		{
			return "Dry";
		}
		if (luck <= 0.65)
		{
			return "On rate";
		}
		if (luck <= 0.90)
		{
			return "Spooned";
		}
		return "Very spooned";
	}

	/**
	 * Luck percentile for total value: the share of players with the same kills who got
	 * less GP from these items than you (ties count half). Rare, expensive items dominate
	 * value, so a formula would be misleading; instead we simulate the kills many times.
	 * Uses a fixed seed so the number doesn't jump around between refreshes.
	 *
	 * @param expected expected number of drops per item
	 * @param prices   price per item (0 for untradeables, which are skipped)
	 * @param received total value actually received
	 * @return percentile between 0 and 1, or NaN when nothing of value is expected yet
	 */
	public static double valueLuck(double[] expected, long[] prices, long received, int samples, long seed)
	{
		double total = 0;
		for (int i = 0; i < expected.length; i++)
		{
			if (prices[i] > 0)
			{
				total += expected[i] * prices[i];
			}
		}
		if (total <= 0)
		{
			return Double.NaN;
		}
		java.util.Random random = new java.util.Random(seed);
		double below = 0;
		for (int s = 0; s < samples; s++)
		{
			long value = 0;
			for (int i = 0; i < expected.length; i++)
			{
				if (prices[i] > 0 && expected[i] > 0)
				{
					value += poisson(random, expected[i]) * prices[i];
				}
			}
			if (value < received)
			{
				below += 1;
			}
			else if (value == received)
			{
				below += 0.5;
			}
		}
		return below / samples;
	}

	/** A random number of drops when {@code mean} are expected. */
	static long poisson(java.util.Random random, double mean)
	{
		if (mean > 30)
		{
			// normal approximation is accurate here and much faster
			return Math.max(0, Math.round(mean + Math.sqrt(mean) * random.nextGaussian()));
		}
		double limit = Math.exp(-mean);
		double product = random.nextDouble();
		long count = 0;
		while (product > limit)
		{
			product *= random.nextDouble();
			count++;
		}
		return count;
	}

	/** "1.24b", "345.6m", "12.3k" or "950" coins. */
	public static String formatGp(double gp)
	{
		double abs = Math.abs(gp);
		if (abs >= 1_000_000_000)
		{
			return String.format("%.2fb", gp / 1_000_000_000);
		}
		if (abs >= 1_000_000)
		{
			return String.format("%.1fm", gp / 1_000_000);
		}
		if (abs >= 1_000)
		{
			return String.format("%.1fk", gp / 1_000);
		}
		return String.format("%.0f", gp);
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
