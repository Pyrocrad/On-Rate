package com.expecteddrops.stats;

/**
 * Numbers for one row in the panel.
 */
public class RowStats
{
	private final String label;
	private final String iconItem;
	private final double rate;
	private final double expected;
	private final int received;
	private final boolean any;

	public RowStats(String label, String iconItem, double rate, double expected, int received, boolean any)
	{
		this.label = label;
		this.iconItem = iconItem;
		this.rate = rate;
		this.expected = expected;
		this.received = received;
		this.any = any;
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
		return DropMath.luckPercentile(received, expected);
	}
}
