package com.expecteddrops.stats;

import java.util.Collections;
import java.util.List;

/**
 * An item several bosses drop (dragon pickaxe, uncut onyx...). The collection log has one
 * total for all of them, so its luck is the bosses' expected added up against that total.
 */
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

	public String getLabel()
	{
		return label;
	}

	public String getItemName()
	{
		return itemName;
	}

	/** Expected drops from all the bosses together, uncapped (for a pet: its expected, not the chance of one). */
	public double getExpected()
	{
		return expected;
	}

	/** The copies that count: for an item the log counts once (a pet), the chance of having one by now. */
	public double getCountedExpected()
	{
		return once ? DropMath.chanceAtLeastOne(expected) : expected;
	}

	/** True for an item the collection log counts at most once (the Chaos elemental pet). */
	public boolean isOnce()
	{
		return once;
	}

	public SharedPool withOnce(boolean once)
	{
		this.once = once;
		return this;
	}

	/** "dust devils and smoke devils": monsters outside the bosses whose copies the log counts too, or null. */
	public String getAlsoFrom()
	{
		return alsoFrom;
	}

	public SharedPool withAlsoFrom(String alsoFrom)
	{
		this.alsoFrom = alsoFrom;
		return this;
	}

	/** The pool's key (its normalized item name), for choosing to judge it per boss. */
	public String getKey()
	{
		return key;
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
	public boolean isSplittable()
	{
		return splittable;
	}

	/** Copies in the log On Rate didn't see drop (from before it, or played elsewhere). */
	public int getOlderCopies()
	{
		return olderCopies;
	}

	/** True when it was judged per boss, but the log has gained copies On Rate didn't see since. */
	public boolean isSplitNeedsAnswer()
	{
		return splitNeedsAnswer;
	}

	public SharedPool withSplit(boolean splittable, int olderCopies, boolean needsAnswer)
	{
		this.splittable = splittable;
		this.olderCopies = olderCopies;
		this.splitNeedsAnswer = needsAnswer;
		return this;
	}

	/** The collection log total. */
	public int getReceived()
	{
		return received;
	}

	/** Every boss that drops it. */
	public List<String> getBosses()
	{
		return bosses;
	}

	/** Collection log pages still to open before this item can be counted; empty when complete. */
	public List<String> getMissingPages()
	{
		return missingPages;
	}

	/** False until a collection log page showing this item has been opened (received unknown). */
	public boolean isLogKnown()
	{
		return logKnown;
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
