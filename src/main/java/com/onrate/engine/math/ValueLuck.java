package com.onrate.engine.math;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Luck by GP value. Rare, expensive items dominate value, so there is no formula: the kills are
 * simulated many times with a fixed seed (the number doesn't jump around between refreshes) and
 * your value is placed among the simulated players, ties counted like the counts do.
 */
public final class ValueLuck
{
	/** Players simulated for the account and boss GP verdicts. */
	public static final int SAMPLES = 5000;
	/** The fixed seed the account GP verdict has always used. */
	public static final long SEED = 1234567L;

	private ValueLuck()
	{
	}

	/**
	 * @param expected expected number of drops per item
	 * @param prices   price per item (0 for untradeables, which are skipped)
	 * @param caps     at most {@code caps[i]} copies of an item count (0 for no limit); may be null
	 * @param received total value actually received
	 * @return luck between 0 and 1, or NaN when nothing of value is expected yet
	 */
	public static double of(double[] expected, long[] prices, int[] caps, long received, int samples, long seed)
	{
		// Only items with a price and a chance take part; keeping them in the same order means
		// the random numbers, and so the result, are the same as drawing for every item.
		int n = 0;
		double total = 0;
		for (int i = 0; i < expected.length; i++)
		{
			if (prices[i] > 0)
			{
				total += Poisson.expectedCounted(expected[i], caps == null ? 0 : caps[i]) * prices[i];
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
		int[] itemCaps = new int[n];
		int j = 0;
		for (int i = 0; i < expected.length; i++)
		{
			if (prices[i] > 0 && expected[i] > 0)
			{
				means[j] = expected[i];
				limits[j] = Math.exp(-expected[i]);
				values[j] = prices[i];
				itemCaps[j] = caps == null ? 0 : Math.max(0, caps[i]);
				j++;
			}
		}
		Key key = new Key(means, values, itemCaps, received, samples, seed);
		synchronized (CACHE)
		{
			Double cached = CACHE.get(key);
			if (cached != null)
			{
				return cached;
			}
		}
		Random random = new Random(seed);
		double below = 0;
		double equal = 0;
		for (int s = 0; s < samples; s++)
		{
			long value = 0;
			for (int i = 0; i < n; i++)
			{
				long copies = poisson(random, means[i], limits[i]);
				if (itemCaps[i] > 0)
				{
					copies = Math.min(copies, itemCaps[i]);
				}
				value += copies * values[i];
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
		double luck = Luck.tieAware(below / samples, equal / samples);
		synchronized (CACHE)
		{
			CACHE.put(key, luck);
		}
		return luck;
	}

	/** Without caps. */
	public static double of(double[] expected, long[] prices, long received, int samples, long seed)
	{
		return of(expected, prices, null, received, samples, seed);
	}

	/** A random number of drops when {@code mean} are expected. */
	public static long poisson(Random random, double mean)
	{
		return poisson(random, mean, Math.exp(-mean));
	}

	/** The last few results: switching tabs or re-opening a boss asks for the same numbers. */
	private static final Map<Key, Double> CACHE = new LinkedHashMap<Key, Double>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, Double> eldest)
		{
			return size() > 32;
		}
	};

	private static long poisson(Random random, double mean, double limit)
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

	private static final class Key
	{
		private final double[] means;
		private final long[] values;
		private final int[] caps;
		private final long received;
		private final int samples;
		private final long seed;
		private final int hash;

		Key(double[] means, long[] values, int[] caps, long received, int samples, long seed)
		{
			this.means = means;
			this.values = values;
			this.caps = caps;
			this.received = received;
			this.samples = samples;
			this.seed = seed;
			this.hash = Objects.hash(Arrays.hashCode(means), Arrays.hashCode(values), Arrays.hashCode(caps),
				received, samples, seed);
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Key))
			{
				return false;
			}
			Key k = (Key) o;
			return hash == k.hash && received == k.received && samples == k.samples && seed == k.seed
				&& Arrays.equals(means, k.means) && Arrays.equals(values, k.values) && Arrays.equals(caps, k.caps);
		}

		@Override
		public int hashCode()
		{
			return hash;
		}
	}
}
