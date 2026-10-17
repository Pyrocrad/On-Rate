package com.expecteddrops.stats;

import com.expecteddrops.data.BossDefinition;
import java.util.List;
import java.util.Map;

/**
 * Everything the panel needs to draw one boss.
 */
public class BossStats
{
	private final BossDefinition boss;
	private final int killCount;
	private final boolean killCountKnown;
	private final boolean clogSynced;
	private final RowStats anyRow;
	private final List<RowStats> rows;
	private final double raidUniqueChance;
	private final Map<Integer, Integer> levelCounts;
	private Map<String, Integer> partCounts;

	public BossStats(BossDefinition boss, int killCount, boolean killCountKnown, boolean clogSynced,
		RowStats anyRow, List<RowStats> rows, double raidUniqueChance, Map<Integer, Integer> levelCounts)
	{
		this.levelCounts = levelCounts;
		this.boss = boss;
		this.killCount = killCount;
		this.killCountKnown = killCountKnown;
		this.clogSynced = clogSynced;
		this.anyRow = anyRow;
		this.rows = rows;
		this.raidUniqueChance = raidUniqueChance;
	}

	public BossDefinition getBoss()
	{
		return boss;
	}

	public int getKillCount()
	{
		return killCount;
	}

	/** False when the kill count was only counted from loot since the plugin was installed. */
	public boolean isKillCountKnown()
	{
		return killCountKnown;
	}

	public boolean isClogSynced()
	{
		return clogSynced;
	}

	public RowStats getAnyRow()
	{
		return anyRow;
	}

	public List<RowStats> getRows()
	{
		return rows;
	}

	/** Kill count per part for combined pages, otherwise null. */
	public Map<String, Integer> getPartCounts()
	{
		return partCounts;
	}

	public void setPartCounts(Map<String, Integer> partCounts)
	{
		this.partCounts = partCounts;
	}

	/** Completions per delve level for delve bosses, otherwise null. */
	public Map<Integer, Integer> getLevelCounts()
	{
		return levelCounts;
	}

	/** Unique chance per raid used for the numbers, or NaN for a normal boss. */
	public double getRaidUniqueChance()
	{
		return raidUniqueChance;
	}
}
