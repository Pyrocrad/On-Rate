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

	public int getKillCount(String boss)
	{
		Integer kc = killCounts.get(boss);
		return kc == null ? 0 : kc;
	}
}
