package com.onrate.engine.data;

import lombok.Getter;
import java.util.Collections;
import java.util.List;

/**
 * One boss inside a combined collection log page, e.g. Dagannoth Rex on the
 * Dagannoth Kings page. It has its own kill count and its own drop rates.
 */
public class BossPart
{
	@Getter private String name;
	private List<String> kc;
	private List<String> loot;
	/** For raid modes: which purple-chance setting this mode uses (e.g. "toaExpert"). */
	@Getter private String raid;
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

	public List<String> getKcNames()
	{
		return kc == null ? Collections.emptyList() : kc;
	}

	public List<String> getKeywords()
	{
		return keywords == null ? Collections.emptyList() : keywords;
	}

	public List<String> getLootNames()
	{
		return loot == null ? Collections.emptyList() : loot;
	}
}
