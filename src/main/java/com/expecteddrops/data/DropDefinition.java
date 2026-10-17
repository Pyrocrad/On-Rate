package com.expecteddrops.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One row in a boss's unique list, as read from bosses.json.
 */
public class DropDefinition
{
	private String name;
	private String label;
	private String rate;
	private Double weight;
	private String perUnique;
	private List<String> match;
	private Boolean any;
	private Boolean pet;
	private Boolean clogShared;
	/** Per-level rates for delve bosses, keyed "1".."9" (9 = level 8+). */
	private Map<String, String> levelRates;

	public DropDefinition()
	{
	}

	public DropDefinition(String name, String rate)
	{
		this.name = name;
		this.rate = rate;
	}

	public String getName()
	{
		return name;
	}

	/** Text shown in the panel: the label if set, otherwise the item name. */
	public String getDisplayName()
	{
		return label != null && !label.isEmpty() ? label : name;
	}

	public String getRate()
	{
		return rate;
	}

	public Double getWeight()
	{
		return weight;
	}

	public String getPerUnique()
	{
		return perUnique;
	}

	/** The item name plus every extra name that counts for this row. */
	public List<String> getAllNames()
	{
		List<String> names = new ArrayList<>();
		names.add(name);
		if (match != null)
		{
			names.addAll(match);
		}
		return Collections.unmodifiableList(names);
	}

	public boolean hasLevelRates()
	{
		return levelRates != null && !levelRates.isEmpty();
	}

	/** Rate text for a delve level, or null if the item can't drop there. */
	public String getLevelRate(int level)
	{
		return levelRates == null ? null : levelRates.get(String.valueOf(level));
	}

	public void setLevelRates(Map<String, String> levelRates)
	{
		this.levelRates = levelRates;
	}

	public boolean countsTowardAny()
	{
		return any == null || any;
	}

	public boolean isPet()
	{
		return pet != null && pet;
	}

	public boolean isClogSharedFlag()
	{
		return clogShared != null && clogShared;
	}

	public void setWeight(Double weight)
	{
		this.weight = weight;
	}

	public void setPerUnique(String perUnique)
	{
		this.perUnique = perUnique;
	}

	public void setPet(boolean pet)
	{
		this.pet = pet;
	}

	public void setAny(boolean any)
	{
		this.any = any;
	}

	public void setMatch(List<String> match)
	{
		this.match = match;
	}
}
