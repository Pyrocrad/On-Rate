package com.expecteddrops.stats;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The whole account at once: one row per boss with kills, plus a total.
 */
public class AccountStats
{
	private final RowStats total;
	private final List<RowStats> rows;
	private final Map<String, Integer> killCounts;
	private double expectedValue;
	private long receivedValue;
	private double valueLuck = Double.NaN;
	private List<ItemHighlight> items = Collections.emptyList();
	private List<String> unsynced = Collections.emptyList();

	public AccountStats(RowStats total, List<RowStats> rows, Map<String, Integer> killCounts)
	{
		this.total = total;
		this.rows = Collections.unmodifiableList(rows);
		this.killCounts = Collections.unmodifiableMap(killCounts);
	}

	/** All uniques of all bosses added together. */
	public RowStats getTotal()
	{
		return total;
	}

	/** One row per boss (label = boss name), most expected uniques first. */
	public List<RowStats> getRows()
	{
		return rows;
	}

	/** GE value of the uniques you should have had by now (untradeables count as 0). */
	public double getExpectedValue()
	{
		return expectedValue;
	}

	/** GE value of the uniques you got. */
	public long getReceivedValue()
	{
		return receivedValue;
	}

	/** Luck percentile by value, NaN when no prices are known yet. */
	public double getValueLuck()
	{
		return valueLuck;
	}

	public void setValue(double expectedValue, long receivedValue, double valueLuck)
	{
		this.expectedValue = expectedValue;
		this.receivedValue = receivedValue;
		this.valueLuck = valueLuck;
	}

	/** Every item of every boss with kills, for the Items tab. */
	public List<ItemHighlight> getItems()
	{
		return items;
	}

	public void setItems(List<ItemHighlight> items)
	{
		this.items = Collections.unmodifiableList(items);
	}

	public int getKillCount(String boss)
	{
		Integer kc = killCounts.get(boss);
		return kc == null ? 0 : kc;
	}

	/** Bosses with kills whose collection log page isn't loaded yet: not in the totals. */
	public List<String> getUnsynced()
	{
		return unsynced;
	}

	public void setUnsynced(List<String> unsynced)
	{
		this.unsynced = Collections.unmodifiableList(unsynced);
	}
}
