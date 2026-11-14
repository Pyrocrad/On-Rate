package com.onrate.engine.stats;

import lombok.Getter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The whole account at once: one row per boss with kills, plus a total.
 */
public class AccountStats
{
	@Getter private final RowStats total;
	@Getter private final List<RowStats> rows;
	private final Map<String, Integer> killCounts;
	@Getter private double expectedValue;
	@Getter private long receivedValue;
	@Getter private double valueLuck = Double.NaN;
	@Getter private List<ItemHighlight> items = Collections.emptyList();
	@Getter private List<String> unsynced = Collections.emptyList();

	public AccountStats(RowStats total, List<RowStats> rows, Map<String, Integer> killCounts)
	{
		this.total = total;
		this.rows = Collections.unmodifiableList(rows);
		this.killCounts = Collections.unmodifiableMap(killCounts);
	}

	public void setValue(double expectedValue, long receivedValue, double valueLuck)
	{
		this.expectedValue = expectedValue;
		this.receivedValue = receivedValue;
		this.valueLuck = valueLuck;
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

	public void setUnsynced(List<String> unsynced)
	{
		this.unsynced = Collections.unmodifiableList(unsynced);
	}
}
