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
	/** Tombs of Amascut: lowest raid level where this unique drops normally (below it, 1 in 50 is kept). */
	private Integer minRaidLevel;
	private List<String> match;
	private Boolean any;
	private Boolean pet;
	private Boolean clogShared;
	/** True for items you can only get once (a prayer scroll you read). Pets count as once too. */
	private Boolean once;
	/** True when this drop does not scale with damage share at team bosses (e.g. Bran). */
	private Boolean noShare;
	/** Per-level rates for delve bosses, keyed "1".."9" (9 = level 8+). */
	private Map<String, String> levelRates;
	/** Per-part rates for combined pages, keyed by part name. */
	private Map<String, String> partRates;
	/** Raid modes whose unique table differs: weight per part name. */
	private Map<String, Double> partWeights;

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

	/** Weight of this item in one raid mode (falls back to the normal weight). */
	public Double getWeight(String part)
	{
		if (partWeights != null && part != null && partWeights.containsKey(part))
		{
			return partWeights.get(part);
		}
		return weight;
	}

	public Integer getMinRaidLevel()
	{
		return minRaidLevel;
	}

	public void setMinRaidLevel(Integer minRaidLevel)
	{
		this.minRaidLevel = minRaidLevel;
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

	/** Rate text for one part of a combined page, or null if that part doesn't drop it. */
	public String getPartRate(String part)
	{
		return partRates == null ? null : partRates.get(part);
	}

	public boolean hasPartRates()
	{
		return partRates != null && !partRates.isEmpty();
	}

	public boolean countsTowardAny()
	{
		return any == null || any;
	}

	public boolean isPet()
	{
		return pet != null && pet;
	}

	/**
	 * Items the collection log counts at most once: pets (duplicates aren't logged) and
	 * items that stop dropping after the first, like Royal Titans prayer scrolls.
	 */
	public boolean isOnce()
	{
		return isPet() || (once != null && once);
	}

	public boolean isNoShare()
	{
		return noShare != null && noShare;
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
