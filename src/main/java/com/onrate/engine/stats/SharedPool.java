package com.onrate.engine.stats;

import lombok.Getter;
import com.onrate.engine.math.Poisson;

import java.util.Collections;
import java.util.List;

/**
 * An item several bosses drop (dragon pickaxe, uncut onyx...). The collection log has one
 * total for all of them, so its luck is the bosses' expected added up against that total.
 */
@Getter
public class SharedPool
{
	private final String label;
	private final String itemName;
	private final double expected;
	private final int received;
	private final List<String> bosses;
	private final List<String> missingPages;
	private boolean logKnown = true;
	private boolean once;
	private String alsoFrom;
	private String key;
	private boolean splittable;
	private int olderCopies;
	private boolean splitNeedsAnswer;

	public SharedPool(String label, String itemName, double expected, int received, List<String> bosses, List<String> missingPages)
	{
		this.label = label;
		this.itemName = itemName;
		this.expected = expected;
		this.received = received;
		this.bosses = Collections.unmodifiableList(bosses);
		this.missingPages = Collections.unmodifiableList(missingPages);
	}

	/** The copies that count: for an item the log counts once (a pet), the chance of having one by now. */
	public double getCountedExpected()
	{
		return once ? Poisson.chanceAtLeastOne(expected) : expected;
	}

	public SharedPool withOnce(boolean once)
	{
		this.once = once;
		return this;
	}

	public SharedPool withAlsoFrom(String alsoFrom)
	{
		this.alsoFrom = alsoFrom;
		return this;
	}

	public SharedPool withKey(String key)
	{
		this.key = key;
		return this;
	}

	/**
	 * True when it can be judged on each boss separately: only the bosses drop it (nothing like
	 * devils or other dragons the log also counts) and the log total is known.
	 */

	public SharedPool withSplit(boolean splittable, int olderCopies, boolean needsAnswer)
	{
		this.splittable = splittable;
		this.olderCopies = olderCopies;
		this.splitNeedsAnswer = needsAnswer;
		return this;
	}

	public SharedPool withLogKnown(boolean logKnown)
	{
		this.logKnown = logKnown;
		return this;
	}

	public boolean isComplete()
	{
		return missingPages.isEmpty();
	}

	/** A row for the panel: "have it or not" for an item counted once, like every pet row. */
	public RowStats toRow()
	{
		RowStats row = new RowStats(label, itemName, 0, expected, received, false);
		return once ? row.capped(1, Math.min(1, received)) : row;
	}
}
