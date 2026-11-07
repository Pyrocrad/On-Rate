package com.expecteddrops.stats;

import java.util.Locale;

/**
 * How long it takes, on average, to get one copy of an item at one boss: the time of a kill (or
 * raid, or delve) divided by the chance of the item in it. See {@link ItemTimes}.
 */
public class ItemTime
{
	private final String boss;
	private final String label;
	private final String iconItem;
	private final boolean pet;
	private final boolean have;
	private final double secondsPerDrop;
	private final double secondsPerKill;
	private final double chancePerKill;
	private final boolean estimated;
	private final boolean lowConfidence;
	private final boolean usesOwn;
	private final boolean raid;
	private final String basis;
	private final String unit;
	private String hint;

	ItemTime(String boss, String label, String iconItem, boolean pet, boolean have, double secondsPerDrop,
		double secondsPerKill, double chancePerKill, boolean estimated, boolean usesOwn, boolean lowConfidence, boolean raid, String basis, String unit)
	{
		this.boss = boss;
		this.label = label;
		this.iconItem = iconItem;
		this.pet = pet;
		this.have = have;
		this.secondsPerDrop = secondsPerDrop;
		this.secondsPerKill = secondsPerKill;
		this.chancePerKill = chancePerKill;
		this.estimated = estimated;
		this.usesOwn = usesOwn;
		this.lowConfidence = lowConfidence;
		this.raid = raid;
		this.basis = basis;
		this.unit = unit;
	}

	/** An extra line for the hover about another way to get the item (Oathplate shards), or null. */
	public String getHint()
	{
		return hint;
	}

	ItemTime withHint(String hint)
	{
		this.hint = hint;
		return this;
	}

	public String getBoss()
	{
		return boss;
	}

	public String getLabel()
	{
		return label;
	}

	public String getIconItem()
	{
		return iconItem;
	}

	public boolean isPet()
	{
		return pet;
	}

	/** True when you already have at least one copy of the item. */
	public boolean isHave()
	{
		return have;
	}

	/** Average seconds to get one copy. */
	public double getSecondsPerDrop()
	{
		return secondsPerDrop;
	}

	/** Seconds one kill (raid, delve) takes in this calculation. */
	public double getSecondsPerKill()
	{
		return secondsPerKill;
	}

	/** Your chance of the item in one kill (raid, delve): from your points for raids, your depths for Doom. */
	public double getChancePerKill()
	{
		return chancePerKill;
	}

	/** True when the time per kill is the plugin's estimate, not an average of your own timed kills. */
	public boolean isEstimated()
	{
		return estimated;
	}

	/** True when some of the time per kill is your own timed kills (all of it when {@link #isEstimated()} is false). */
	public boolean usesOwnTimes()
	{
		return usesOwn;
	}

	/** True for a raid item: Chambers, Tombs, Theatre of Blood, the Fractured Archive. */
	public boolean isRaid()
	{
		return raid;
	}

	/** Where the time per kill comes from, short: "your times", "wiki rate" or "wiki + yours". */
	public String getSourceTag()
	{
		return !estimated ? "your times" : usesOwn ? "wiki + yours" : "wiki rate";
	}

	/** True when the estimate rests on a thin source. */
	public boolean isLowConfidence()
	{
		return lowConfidence;
	}

	/** One sentence on where the time per kill comes from. */
	public String getBasis()
	{
		return basis;
	}

	/** "kill", "raid" or "delve". */
	public String getUnit()
	{
		return unit;
	}

	/** "45 min", "3.3 h", "145 h": a long time in the units that read best. */
	public static String formatLong(double seconds)
	{
		if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0)
		{
			return "-";
		}
		double minutes = seconds / 60;
		if (minutes < 1)
		{
			return "<1 min";
		}
		if (minutes < 60)
		{
			return String.format(Locale.ROOT, "%.0f min", minutes);
		}
		double hours = minutes / 60;
		if (hours < 10)
		{
			return String.format(Locale.ROOT, "%.1f h", hours);
		}
		if (hours < 10_000)
		{
			return String.format(Locale.ROOT, "%,.0f h", hours);
		}
		return String.format(Locale.ROOT, "%,.0f h", Math.round(hours / 1000) * 1000.0);
	}
}
