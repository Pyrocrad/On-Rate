package com.expecteddrops.stats;

/**
 * Numbers for one row in the panel.
 */
public class RowStats
{
	private final String label;
	private final String iconItem;
	private double rate;
	private final double expected;
	private final int received;
	private final boolean any;
	private boolean once;
	private int cap;
	private Integer streak;
	private boolean streakExact;
	/** Fewest and most kills between two drops On Rate saw, or null. */
	private Integer fastest;
	private Integer longest;
	private double driestMultiple = Double.NaN;
	private String driestItem;
	private String mark = "";
	/** How the expected count is made up (see {@link Parts}), or null for a single plain count. */
	private Parts parts;

	/**
	 * What a total (the "Any" row, the account, a drop with a sure copy) is made of: copies that
	 * drop for certain, a part with no limit, and items of which only a few copies count, each
	 * with its uncapped expected. Its luck and chance come from these, not from a Poisson of the
	 * total: a pet's "have it or not" isn't Poisson, nor is a sure copy.
	 */
	public static final class Parts
	{
		private int sure;
		private double plain;
		private final java.util.List<Double> cappedMeans = new java.util.ArrayList<>();
		private final java.util.List<Integer> caps = new java.util.ArrayList<>();

		/** {@code count} copies that drop for certain. */
		public Parts addSure(int count)
		{
			sure += count;
			return this;
		}

		/** Expected copies with no limit. */
		public Parts addPlain(double expected)
		{
			plain += expected;
			return this;
		}

		/** An item of which at most {@code cap} copies count, with its uncapped expected. */
		public Parts addCapped(double expected, int cap)
		{
			cappedMeans.add(expected);
			caps.add(cap);
			return this;
		}

		/** Everything a row stands for: its own parts, or else its plain or capped count. */
		public Parts add(RowStats row)
		{
			if (row.parts != null)
			{
				sure += row.parts.sure;
				plain += row.parts.plain;
				cappedMeans.addAll(row.parts.cappedMeans);
				caps.addAll(row.parts.caps);
			}
			else if (row.cap > 0)
			{
				addCapped(row.expected, row.cap);
			}
			else
			{
				addPlain(row.expected);
			}
			return this;
		}

		/** Expected copies that count. */
		public double expected()
		{
			double total = sure + plain;
			for (int i = 0; i < caps.size(); i++)
			{
				total += DropMath.expectedCapped(cappedMeans.get(i), caps.get(i));
			}
			return total;
		}

		/** Expected copies if nothing were capped: what the drop rates add up to. */
		double uncapped()
		{
			double total = sure + plain;
			for (double m : cappedMeans)
			{
				total += m;
			}
			return total;
		}

		double[] shares(int received)
		{
			double[] means = new double[cappedMeans.size()];
			int[] limits = new int[caps.size()];
			for (int i = 0; i < means.length; i++)
			{
				means[i] = cappedMeans.get(i);
				limits[i] = caps.get(i);
			}
			return DropMath.sharesSum(received, sure, plain, means, limits);
		}
	}

	public RowStats(String label, String iconItem, double rate, double expected, int received, boolean any)
	{
		this.label = label;
		this.iconItem = iconItem;
		this.rate = rate;
		this.expected = expected;
		this.received = received;
		this.any = any;
	}

	/**
	 * Marks this row as an item the collection log counts at most once (a pet, a prayer
	 * scroll). The expected number stays as is; luck becomes "have it or not".
	 */
	public RowStats once(int receivedCapped)
	{
		return capped(1, receivedCapped);
	}

	/**
	 * Marks this row as counting at most {@code cap} copies (the two Royal Titans prayer
	 * scrolls in one row). Luck tops out once you have them all.
	 */
	public RowStats capped(int cap, int receivedCapped)
	{
		RowStats copy = new RowStats(label, iconItem, rate, expected, receivedCapped, any);
		copy.once = cap == 1;
		copy.cap = cap;
		copy.streak = streak;
		copy.streakExact = streakExact;
		copy.fastest = fastest;
		copy.longest = longest;
		copy.parts = parts;
		return copy;
	}

	/** Sets what this row's expected count is made of; its luck and chance then follow from it. */
	public RowStats withParts(Parts parts)
	{
		this.parts = parts;
		return this;
	}

	public Parts getParts()
	{
		return parts;
	}

	/** Sets the dry streak: kills since the last copy (or since tracking began when not exact). */
	public RowStats withStreak(Integer kills, boolean exact)
	{
		this.streak = kills;
		this.streakExact = exact;
		return this;
	}

	/** Records since On Rate started: fewest and most kills between two drops (null if none). */
	public RowStats withRecords(Integer fastest, Integer longest)
	{
		this.fastest = fastest;
		this.longest = longest;
		return this;
	}

	public Integer getFastest()
	{
		return fastest;
	}

	public Integer getLongest()
	{
		return longest;
	}

	/** For the "Any" row: the summed rate of all its items, so its streak can be expressed in rates. */
	public RowStats withRate(double rate)
	{
		this.rate = rate;
		return this;
	}

	/** For a boss row in the account overview: its driest item right now. */
	public RowStats withDriest(double multiple, String item)
	{
		this.driestMultiple = multiple;
		this.driestItem = item;
		return this;
	}

	/** Adds a mark shown after the label (" *" for estimates); the label itself stays the same. */
	public RowStats withMark(String mark)
	{
		this.mark = mark == null ? "" : mark;
		return this;
	}

	/** The label as shown in the panel, with its mark. */
	public String getShownLabel()
	{
		return label + mark;
	}

	public double getDriestMultiple()
	{
		return driestMultiple;
	}

	public String getDriestItem()
	{
		return driestItem;
	}

	/** Kills since the last copy, or null when unknown (or the item is a pet you already have). */
	public Integer getStreak()
	{
		return streak;
	}

	/** False when the last copy came before On Rate started tracking: the streak is a minimum. */
	public boolean isStreakExact()
	{
		return streakExact;
	}

	/** The streak in multiples of the drop rate: 1.0 means one full drop rate without a copy. */
	public double getStreakMultiple()
	{
		return streak == null ? Double.NaN : streak * rate;
	}

	public boolean isOnce()
	{
		return once;
	}

	/** At most this many copies count, 0 for no limit. */
	public int getCap()
	{
		return cap;
	}

	/** True when you have every copy that counts (a pet you own, both titan scrolls). */
	public boolean isComplete()
	{
		return cap > 0 && received >= cap;
	}

	public String getLabel()
	{
		return label;
	}

	/** Item name to show as the icon, or null for the "Any" row. */
	public String getIconItem()
	{
		return iconItem;
	}

	/** Per-kill rate (0 for the "Any" row). */
	public double getRate()
	{
		return rate;
	}

	public double getExpected()
	{
		return expected;
	}

	public int getReceived()
	{
		return received;
	}

	public boolean isAny()
	{
		return any;
	}

	/**
	 * Expected copies if none were capped: kills times the drop rates. For a pet that's the
	 * pet's expected; for the "Any" row it's what "1x the unique rate" counts.
	 */
	public double getUncappedExpected()
	{
		return parts == null ? expected : parts.uncapped();
	}

	/**
	 * Chance of having had at least one by now. A cap doesn't change the chance of none, so
	 * this goes by the uncapped expected; a sure copy makes it certain.
	 */
	public double getChanceAtLeastOne()
	{
		if (parts != null && parts.sure > 0)
		{
			return 1;
		}
		return DropMath.chanceAtLeastOne(getUncappedExpected());
	}

	/**
	 * Luck (0..1, 0.5 = average) behind the verdict, tie-aware (see {@link DropMath#tieAware}).
	 * NaN if nothing expected yet.
	 */
	public double getLuck()
	{
		double[] s = getShares();
		return s == null ? Double.NaN : DropMath.tieAware(s[0], s[1]);
	}

	/**
	 * Of players at the same kill count: {share with fewer, share with the same}; the rest have
	 * more. Null if nothing expected yet.
	 */
	public double[] getShares()
	{
		if (parts != null)
		{
			return parts.shares(received);
		}
		return cap > 0 ? DropMath.sharesCapped(received, expected, cap) : DropMath.shares(received, expected);
	}
}
