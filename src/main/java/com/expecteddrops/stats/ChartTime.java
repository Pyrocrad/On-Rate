package com.expecteddrops.stats;

import java.util.Collections;
import java.util.List;

/**
 * One item of the Ladlor chart you don't have yet, and how long it takes to get all of what it
 * needs. See {@link ChartTimes}.
 */
public class ChartTime
{
	/** One drop (or set of kills) the chart item needs. */
	public static final class Part
	{
		private final String label;
		private final String boss;
		private final boolean have;
		private final double seconds;
		private final double chancePerKill;
		private final double secondsPerKill;
		private final String dropKey;
		private final int missing;

		Part(String label, String boss, boolean have, double seconds, double chancePerKill, double secondsPerKill)
		{
			this(label, boss, have, seconds, chancePerKill, secondsPerKill, null, 0);
		}

		Part(String label, String boss, boolean have, double seconds, double chancePerKill, double secondsPerKill, String dropKey, int missing)
		{
			this.dropKey = dropKey;
			this.missing = missing;
			this.label = label;
			this.boss = boss;
			this.have = have;
			this.seconds = seconds;
			this.chancePerKill = chancePerKill;
			this.secondsPerKill = secondsPerKill;
		}

		/** Which drop this is (boss and drop), or null for kills of a boss: the same drop in two chart items shares kills. */
		public String getDropKey()
		{
			return dropKey;
		}

		/** How many copies of the drop are still missing (0 when you have them), 0 for a kill-count part. */
		public int getMissing()
		{
			return missing;
		}

		/** Your chance of this drop in one kill, NaN for a reward that is sure at a kill count. */
		public double getChancePerKill()
		{
			return chancePerKill;
		}

		/** Seconds one kill takes at this part's boss, NaN when unknown. */
		public double getSecondsPerKill()
		{
			return secondsPerKill;
		}

		public String getLabel()
		{
			return label;
		}

		public String getBoss()
		{
			return boss;
		}

		/** True when you already have this part. */
		public boolean isHave()
		{
			return have;
		}

		/** Average seconds for this part alone, NaN when it has no time. */
		public double getSeconds()
		{
			return seconds;
		}
	}

	private final int step;
	private final String name;
	private final String icon;
	private final String note;
	private final List<Part> parts;
	private final double seconds;
	private final boolean estimated;
	private final boolean usesOwn;
	private String route;

	ChartTime(int step, String name, String icon, String note, List<Part> parts, double seconds, boolean estimated, boolean usesOwn)
	{
		this.step = step;
		this.name = name;
		this.icon = icon;
		this.note = note;
		this.parts = Collections.unmodifiableList(parts);
		this.seconds = seconds;
		this.estimated = estimated;
		this.usesOwn = usesOwn;
	}

	/** A second way to get the item, explained for the hover (Oathplate shards), or null. */
	public String getRoute()
	{
		return route;
	}

	ChartTime withRoute(String route)
	{
		this.route = route;
		return this;
	}

	/** Its place in the chart. */
	public int getStep()
	{
		return step;
	}

	public String getName()
	{
		return name;
	}

	public String getIcon()
	{
		return icon;
	}

	/** What the chart item needs besides its drops and isn't counted, or null. */
	public String getNote()
	{
		return note;
	}

	public List<Part> getParts()
	{
		return parts;
	}

	/** Average seconds to get everything still missing, NaN when some part has no time. */
	public double getSeconds()
	{
		return seconds;
	}

	public boolean hasTime()
	{
		return !Double.isNaN(seconds);
	}

	/** True when some of the time per kill is the wiki rate, not your own kills. */
	public boolean isEstimated()
	{
		return estimated;
	}

	public boolean usesOwnTimes()
	{
		return usesOwn;
	}

	/** Where the time comes from, short: "your times", "wiki rate" or "wiki + yours". */
	public String getSourceTag()
	{
		return !estimated ? "your times" : usesOwn ? "wiki + yours" : "wiki rate";
	}
}
