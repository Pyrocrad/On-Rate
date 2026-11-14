package com.onrate.engine.stats;

import lombok.Getter;
import lombok.Setter;
import com.onrate.engine.data.DropDefinition;

import com.onrate.engine.data.BossDefinition;
import java.util.List;
import java.util.Map;

/**
 * Everything the panel needs to draw one boss.
 */
public class BossStats
{
	@Getter private final BossDefinition boss;
	@Getter private final int killCount;
	@Getter private final boolean killCountKnown;
	@Getter private final boolean clogSynced;
	@Getter private final RowStats anyRow;
	@Getter private final List<RowStats> rows;
	@Getter private final double raidUniqueChance;
	@Getter private final Map<Integer, Integer> levelCounts;
	@Getter @Setter private Map<String, Integer> partCounts;
	@Setter private List<DropDefinition> drops;

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

	/**
	 * The drop of each row, in the same order: the boss's own uniques, then any shared items
	 * the player chose to judge on each boss separately.
	 */
	public List<DropDefinition> getDrops()
	{
		return drops != null ? drops : boss.getDrops();
	}
}

