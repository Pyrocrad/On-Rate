package com.onrate.engine.stats;

import lombok.Getter;
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
		@Getter private final String label;
		@Getter private final String boss;
		@Getter private final boolean have;
		@Getter private final double seconds;
		@Getter private final double chancePerKill;
		@Getter private final double secondsPerKill;
		@Getter private final String dropKey;
		@Getter private final int missing;

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
	}

	@Getter private final int step;
	@Getter private final String name;
	@Getter private final String icon;
	@Getter private final String note;
	@Getter private final List<Part> parts;
	@Getter private final double seconds;
	@Getter private final boolean estimated;
	private final boolean usesOwn;
	@Getter private String route;

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

	ChartTime withRoute(String route)
	{
		this.route = route;
		return this;
	}

	public boolean hasTime()
	{
		return !Double.isNaN(seconds);
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
