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
