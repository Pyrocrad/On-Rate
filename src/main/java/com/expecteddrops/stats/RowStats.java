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
		return copy;
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

	/** Chance of having had at least one by now. */
	public double getChanceAtLeastOne()
	{
		return DropMath.chanceAtLeastOne(expected);
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
		return cap > 0 ? DropMath.sharesCapped(received, expected, cap) : DropMath.shares(received, expected);
	}
}
