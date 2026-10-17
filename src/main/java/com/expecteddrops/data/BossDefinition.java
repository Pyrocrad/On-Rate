package com.expecteddrops.data;

import java.util.ArrayList;
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
	/** Team boss whose uniques scale with your damage share (setting key), or null. */
	private String team;
	private String note;
	private List<DropDefinition> drops;
	private List<BossPart> parts;

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

	/** Chat kill-count names, including those of every part. */
	public List<String> getKcNames()
	{
		List<String> names = new ArrayList<>(kc == null ? Collections.emptyList() : kc);
		for (BossPart part : getParts())
		{
			names.addAll(part.getKcNames());
		}
		return names;
	}

	/** Loot tracker names, including those of every part. */
	public List<String> getLootNames()
	{
		List<String> names = new ArrayList<>(loot == null ? Collections.emptyList() : loot);
		for (BossPart part : getParts())
		{
			names.addAll(part.getLootNames());
		}
		return names;
	}

	/** The bosses on a combined page (Dagannoth Kings, Callisto and Artio...), or empty. */
	public List<BossPart> getParts()
	{
		return parts == null ? Collections.emptyList() : parts;
	}

	public String getTeam()
	{
		return team;
	}

	public boolean hasParts()
	{
		return parts != null && !parts.isEmpty();
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

	/** Total unique weight in one raid mode. */
	public double getTotalWeight(String part)
	{
		double total = 0;
		for (DropDefinition drop : getDrops())
		{
			Double w = drop.getWeight(part);
			if (w != null)
			{
				total += w;
			}
		}
		return total;
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
