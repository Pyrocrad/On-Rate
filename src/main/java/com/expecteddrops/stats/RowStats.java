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
	private Integer streak;
	private boolean streakExact;
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
		RowStats copy = new RowStats(label, iconItem, rate, expected, receivedCapped, any);
		copy.once = true;
		copy.streak = streak;
		copy.streakExact = streakExact;
		return copy;
	}

	/** Sets the dry streak: kills since the last copy (or since tracking began when not exact). */
	public RowStats withStreak(Integer kills, boolean exact)
	{
		this.streak = kills;
		this.streakExact = exact;
		return this;
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

	/** Luck percentile (0..1, 0.5 = average), NaN if nothing expected yet. */
	public double getLuck()
	{
		if (once)
		{
			if (expected <= 0)
			{
				return Double.NaN;
			}
			double chance = getChanceAtLeastOne();
			// share of players without one, plus half of those level with you
			return received > 0 ? (1 - chance) + chance / 2 : (1 - chance) / 2;
		}
		return DropMath.luckPercentile(received, expected);
	}
}
