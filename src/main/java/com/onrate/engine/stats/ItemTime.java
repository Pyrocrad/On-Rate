package com.onrate.engine.stats;

import lombok.Getter;
import java.util.ArrayList;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How long it takes, on average, to get one copy of an item at one boss: the time of a kill (or
 * raid, or delve) divided by the chance of the item in it. See {@link ItemTimes}.
 */
public class ItemTime
{
	@Getter private final String boss;
	@Getter private final String label;
	@Getter private final String iconItem;
	@Getter private final boolean pet;
	@Getter private final boolean have;
	@Getter private final double secondsPerDrop;
	@Getter private final double secondsPerKill;
	@Getter private final double chancePerKill;
	@Getter private final boolean estimated;
	@Getter private final boolean lowConfidence;
	private final boolean usesOwn;
	@Getter private final boolean raid;
	@Getter private final String basis;
	@Getter private final String unit;
	@Getter private String hint;
	private List<String> guides = Collections.emptyList();
	@Getter private int received;

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

	ItemTime withHint(String hint)
	{
		this.hint = hint;
		return this;
	}

	/**
	 * Titles of the OSRS Wiki guides the wiki part of the time per kill comes from; empty when none of it does
	 * (your own times only, Doom). Named without a get prefix on purpose: see EngineParityTest.
	 */
	public List<String> guideTitles()
	{
		return guides;
	}

	ItemTime withGuides(List<String> guides)
	{
		this.guides = guides == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(guides));
		return this;
	}

	/**
	 * The same boss's time for a unique of any kind: this time per kill over the chance of some unique
	 * in a kill. A copy; this item stays as it is.
	 */
	public ItemTime forAnyUnique(double chancePerKill)
	{
		return new ItemTime(boss, "Any unique", iconItem, false, have, secondsPerKill / chancePerKill, secondsPerKill, chancePerKill,
			estimated, usesOwn, lowConfidence, raid, basis, unit).withHint(hint).withGuides(guides);
	}

	ItemTime withReceived(int received)
	{
		this.received = received;
		return this;
	}

	/** True when some of the time per kill is your own timed kills (all of it when {@link #isEstimated()} is false). */
	public boolean usesOwnTimes()
	{
		return usesOwn;
	}

	/** Where the time per kill comes from, short: "your times", "wiki rate" or "wiki + yours". */
	public String getSourceTag()
	{
		return !estimated ? "your times" : usesOwn ? "wiki + yours" : "wiki rate";
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

