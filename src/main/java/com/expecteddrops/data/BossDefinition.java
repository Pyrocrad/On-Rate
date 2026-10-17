package com.expecteddrops.data;

import java.util.Collections;
import java.util.List;

/**
 * A boss (or raid) with its unique drops, as read from bosses.json.
 */
public class BossDefinition
{
	private String name;
	private List<String> kc;
	private List<String> loot;
	private List<String> clog;
	private String raid;
	private String kcMode;
	private Boolean delve;
	private String note;
	private List<DropDefinition> drops;

	public BossDefinition()
	{
	}

	public BossDefinition(String name, List<String> kc, List<String> loot, List<String> clog, List<DropDefinition> drops)
	{
		this.name = name;
		this.kc = kc;
		this.loot = loot;
		this.clog = clog;
		this.drops = drops;
	}

	public String getName()
	{
		return name;
	}

	public List<String> getKcNames()
	{
		return kc == null ? Collections.emptyList() : kc;
	}

	public List<String> getLootNames()
	{
		return loot == null ? Collections.emptyList() : loot;
	}

	public List<String> getClogPages()
	{
		return clog == null ? Collections.emptyList() : clog;
	}

	/** "cox", "tob", "toa" or null for a normal boss. */
	public String getRaid()
	{
		return raid;
	}

	public void setRaid(String raid)
	{
		this.raid = raid;
	}

	/** Delve levels tracked for delve bosses: 1-8, plus 9 for "8+" (deep delves). */
	public static final int DELVE_LEVELS = 9;

	/** True for delve bosses (Doom of Mokhaiotl), whose rates depend on the delve level. */
	public boolean isDelve()
	{
		return delve != null && delve;
	}

	public void setDelve(boolean delve)
	{
		this.delve = delve;
	}

	public static String delveLevelLabel(int level)
	{
		return level >= DELVE_LEVELS ? "8+" : String.valueOf(level);
	}

	/** True when several kill counts should be combined with max instead of being added up. */
	public boolean combineKcWithMax()
	{
		return "max".equalsIgnoreCase(kcMode);
	}

	public String getNote()
	{
		return note;
	}

	public List<DropDefinition> getDrops()
	{
		return drops == null ? Collections.emptyList() : drops;
	}

	public double getTotalWeight()
	{
		double total = 0;
		for (DropDefinition drop : getDrops())
		{
			if (drop.getWeight() != null)
			{
				total += drop.getWeight();
			}
		}
		return total;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
