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

	/**
	 * Luck when at most {@code cap} copies count: you can't do better than having them all,
	 * so everyone with {@code cap} or more is level with you. Tie-aware like {@link #luck}.
	 */
	public static double luckCapped(int received, double expected, int cap)
	{
		double[] s = sharesCapped(received, expected, cap);
		return s == null ? Double.NaN : tieAware(s[0], s[1]);
	}

	/**
	 * Of all players at the same kill count: {share with fewer than you, share with exactly as
	 * many}, the rest have more. Null when nothing is expected. With at most {@code cap} counting,
	 * everyone at the cap is level with you.
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

	/** {share with fewer, share with the same} for a plain (uncapped) count; null when nothing is expected. */
	public static double[] shares(int received, double expected)
	{
		if (expected <= 0 || Double.isNaN(expected))
		{
			return null;
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
		return new double[]{Math.min(1, below), Math.min(1, equal)};
	}

	/**
	 * Luck that doesn't call the most common result dry: of the range from "share with fewer"
	 * to "share with this many or fewer", the point closest to the middle. So you only count as
	 * dry when few players have this many or fewer, and as spooned when few have this many or
	 * more. With big counts ties are rare and this is the usual percentile.
	 */
	public static double tieAware(double fewer, double same)
	{
		return Math.max(0, Math.min(1, Math.min(Math.max(0.5, fewer), fewer + same)));
	}

	/** The luck behind every verdict: tie-aware, see {@link #tieAware}. NaN when nothing is expected. */
	public static double luck(int received, double expected)
	{
		double[] s = shares(received, expected);
		return s == null ? Double.NaN : tieAware(s[0], s[1]);
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
		// Only items with a price and a chance take part; keeping them in the same order means
		// the random numbers, and so the result, are the same as drawing for every item.
		int n = 0;
		double total = 0;
		for (int i = 0; i < expected.length; i++)
		{
			if (prices[i] > 0)
			{
				total += expected[i] * prices[i];
				if (expected[i] > 0)
				{
					n++;
				}
			}
		}
		if (total <= 0)
		{
			return Double.NaN;
		}
		double[] means = new double[n];
		double[] limits = new double[n];
		long[] values = new long[n];
		int j = 0;
		for (int i = 0; i < expected.length; i++)
		{
			if (prices[i] > 0 && expected[i] > 0)
			{
				means[j] = expected[i];
				limits[j] = Math.exp(-expected[i]);
				values[j] = prices[i];
				j++;
			}
		}
		ValueKey key = new ValueKey(means, values, received, samples, seed);
		synchronized (VALUE_CACHE)
		{
			Double cached = VALUE_CACHE.get(key);
			if (cached != null)
			{
				return cached;
			}
		}
		java.util.Random random = new java.util.Random(seed);
		double below = 0;
		double equal = 0;
		for (int s = 0; s < samples; s++)
		{
			long value = 0;
			for (int i = 0; i < n; i++)
			{
				value += poisson(random, means[i], limits[i]) * values[i];
			}
			if (value < received)
			{
				below += 1;
			}
			else if (value == received)
			{
				equal += 1;
			}
		}
		// ties (often: nothing tradeable yet, like most players) handled like the counts
		double luck = tieAware(below / samples, equal / samples);
		synchronized (VALUE_CACHE)
		{
			VALUE_CACHE.put(key, luck);
		}
		return luck;
	}

	/**
	 * The last few GE value results. Switching tabs, sorting or opening the same boss again
	 * asks for the same numbers, and the simulation is the slowest part of a refresh.
	 */
	private static final java.util.Map<ValueKey, Double> VALUE_CACHE = new java.util.LinkedHashMap<ValueKey, Double>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<ValueKey, Double> eldest)
		{
			return size() > 32;
		}
	};

	private static final class ValueKey
	{
		private final double[] means;
		private final long[] values;
		private final long received;
		private final int samples;
		private final long seed;
		private final int hash;

		ValueKey(double[] means, long[] values, long received, int samples, long seed)
		{
			this.means = means;
			this.values = values;
			this.received = received;
			this.samples = samples;
			this.seed = seed;
			this.hash = java.util.Objects.hash(java.util.Arrays.hashCode(means), java.util.Arrays.hashCode(values), received, samples, seed);
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof ValueKey))
			{
				return false;
			}
			ValueKey k = (ValueKey) o;
			return hash == k.hash && received == k.received && samples == k.samples && seed == k.seed
				&& java.util.Arrays.equals(means, k.means) && java.util.Arrays.equals(values, k.values);
		}

		@Override
		public int hashCode()
		{
			return hash;
		}
	}

	/** A random number of drops when {@code mean} are expected. */
	static long poisson(java.util.Random random, double mean)
	{
		return poisson(random, mean, Math.exp(-mean));
	}

	/** Same, with {@code limit = Math.exp(-mean)} worked out beforehand. */
	private static long poisson(java.util.Random random, double mean, double limit)
	{
		if (mean > 30)
		{
			// normal approximation is accurate here and much faster
			return Math.max(0, Math.round(mean + Math.sqrt(mean) * random.nextGaussian()));
		}
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
