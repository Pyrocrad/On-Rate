package com.expecteddrops.data;

import java.util.Collections;
import java.util.List;

/**
 * One boss inside a combined collection log page, e.g. Dagannoth Rex on the
 * Dagannoth Kings page. It has its own kill count and its own drop rates.
 */
public class BossPart
{
	private String name;
	private List<String> kc;
	private List<String> loot;
	/** For raid modes: which purple-chance setting this mode uses (e.g. "toaExpert"). */
	private String raid;
	/** Words that identify this part in any label, e.g. "expert" or "(cm)". */
	private List<String> keywords;

	public BossPart()
	{
	}

	public BossPart(String name, List<String> kc, List<String> loot)
	{
		this.name = name;
		this.kc = kc;
		this.loot = loot;
	}

	public String getName()
	{
		return name;
	}

	public List<String> getKcNames()
	{
		return kc == null ? Collections.emptyList() : kc;
	}

	public List<String> getKeywords()
	{
		return keywords == null ? Collections.emptyList() : keywords;
	}

	public String getRaid()
	{
		return raid;
	}

	public List<String> getLootNames()
	{
		return loot == null ? Collections.emptyList() : loot;
	}
}
