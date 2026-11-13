package com.onrate.engine.math;

/**
 * The nine words a luck value can earn, driest first. One definition shared by every list,
 * tile, overlay, trip card and graph.
 *
 * <p>Luck is a percentile in 0..1 (0.5 = average). A value exactly on a line goes to the side
 * nearer the middle: 1% is Very dry, 35% is A bit dry, 45% and 55% are On rate, 65% is A bit
 * spooned, 99% is Very spooned.
 */
public enum Verdict
{
	EXTREMELY_DRY("Extremely dry"),
	VERY_DRY("Very dry"),
	DRY("Dry"),
	A_BIT_DRY("A bit dry"),
	ON_RATE("On rate"),
	A_BIT_SPOONED("A bit spooned"),
	SPOONED("Spooned"),
	VERY_SPOONED("Very spooned"),
	EXTREMELY_SPOONED("Extremely spooned");

	/** Below this luck it is dry; at or above it the middle band starts. */
	public static final double DRY_BELOW = 0.35;
	/** Above this luck it is spooned. */
	public static final double SPOONED_ABOVE = 0.65;
	/** Under this many expected uniques there is nothing to read yet ("Too early"). */
	public static final double TOO_EARLY_BELOW = 0.5;
	/**
	 * The smaller cut-off for one trip (the trip card and the overlay): under this many expected uniques a trip
	 * says "Too early" instead of a verdict. A trip is short by nature, so it keeps a lower bar than a boss page.
	 */
	public static final double TRIP_TOO_EARLY_BELOW = 0.05;

	/** Whether a trip with this many uniques expected is too small to judge ({@code NaN} is not: it has its own dash). */
	public static boolean tripTooEarly(double expected)
	{
		return expected < TRIP_TOO_EARLY_BELOW;
	}

	private final String label;

	Verdict(String label)
	{
		this.label = label;
	}

	public String label()
	{
		return label;
	}

	/** 0 (extremely dry) to 8 (extremely spooned). */
	public int band()
	{
		return ordinal();
	}

	public boolean isDry()
	{
		return ordinal() < ON_RATE.ordinal();
	}

	public boolean isSpooned()
	{
		return ordinal() > ON_RATE.ordinal();
	}

	/** Every word, driest first. */
	public static java.util.List<String> labels()
	{
		java.util.List<String> labels = new java.util.ArrayList<>();
		for (Verdict v : values())
		{
			labels.add(v.label);
		}
		return java.util.Collections.unmodifiableList(labels);
	}

	/** The verdict for a luck percentile, or null when there is nothing to judge (NaN). */
	public static Verdict of(double luck)
	{
		if (Double.isNaN(luck))
		{
			return null;
		}
		int band;
		if (luck < 0.01)
		{
			band = 0;
		}
		else if (luck < 0.10)
		{
			band = 1;
		}
		else if (luck < DRY_BELOW)
		{
			band = 2;
		}
		else if (luck < 0.45)
		{
			band = 3;
		}
		else if (luck <= 0.55)
		{
			band = 4;
		}
		else if (luck <= SPOONED_ABOVE)
		{
			band = 5;
		}
		else if (luck <= 0.90)
		{
			band = 6;
		}
		else if (luck <= 0.99)
		{
			band = 7;
		}
		else
		{
			band = 8;
		}
		return values()[band];
	}

	/** The word for a luck percentile, or "" when there is nothing to judge. */
	public static String labelOf(double luck)
	{
		Verdict v = of(luck);
		return v == null ? "" : v.label;
	}

	/** True when {@code luck} reads "On rate" (false for NaN). */
	public static boolean isOnRate(double luck)
	{
		return of(luck) == ON_RATE;
	}

	/**
	 * Where a luck value sits on the nine-step Rate Line, 0 (far left, driest) to 1 (far right).
	 * Every verdict gets an equal ninth, and the value moves along inside its own band, so the
	 * 1% and 99% bands stay readable. Null luck (NaN) gives NaN.
	 */
	public static double position(double luck)
	{
		Verdict v = of(luck);
		if (v == null)
		{
			return Double.NaN;
		}
		double lo = LOWER[v.ordinal()];
		double hi = LOWER[v.ordinal() + 1];
		double within = hi > lo ? Math.max(0, Math.min(1, (luck - lo) / (hi - lo))) : 0.5;
		return (v.ordinal() + within) / 9.0;
	}

	/** Lower edge of each band, plus 1 at the end. */
	private static final double[] LOWER = {0, 0.01, 0.10, 0.35, 0.45, 0.55, 0.65, 0.90, 0.99, 1};
}
