package com.expecteddrops.data;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * All bosses, with lookups by kill-count name, loot name and collection log page.
 */
public class BossCatalog
{
	private final List<BossDefinition> bosses;
	private final Map<String, BossDefinition> byName = new HashMap<>();
	private final Map<String, BossDefinition> byKcName = new HashMap<>();
	private final Map<String, BossDefinition> byLootName = new HashMap<>();
	private final Map<String, List<BossDefinition>> byClogPage = new HashMap<>();
	private final Set<String> clogSharedItems = new HashSet<>();
	/** Display names of the shared items that were left out, for the panel. */
	private final Set<String> excludedItems = new java.util.TreeSet<>();
	private final Set<String> allItemNames = new HashSet<>();
	private final Set<String> excludedBosses = new java.util.TreeSet<>();

	private static class FileFormat
	{
		List<BossDefinition> bosses;
	}

	public BossCatalog(List<BossDefinition> bosses)
	{
		// An item that several bosses drop has one collection log total for all of them,
		// so that total can't be credited to any single boss.
		Map<String, Integer> itemBossCount = new HashMap<>();
		for (BossDefinition boss : bosses)
		{
			Set<String> namesForBoss = new HashSet<>();
			for (DropDefinition drop : boss.getDrops())
			{
				for (String item : drop.getAllNames())
				{
					String key = normalize(item);
					namesForBoss.add(key);
					if (drop.isClogSharedFlag())
					{
						clogSharedItems.add(key);
					}
				}
			}
			for (String key : namesForBoss)
			{
				itemBossCount.merge(key, 1, Integer::sum);
			}
		}
		for (Map.Entry<String, Integer> e : itemBossCount.entrySet())
		{
			if (e.getValue() > 1)
			{
				clogSharedItems.add(e.getKey());
			}
		}

		// Leave those items out altogether: counting them would add expected drops without
		// the matching received ones. A boss left with nothing to track is left out too.
		List<BossDefinition> sorted = new ArrayList<>();
		for (BossDefinition boss : bosses)
		{
			boss.removeDrops(drop ->
			{
				for (String item : drop.getAllNames())
				{
					if (clogSharedItems.contains(normalize(item)))
					{
						excludedItems.add(drop.getDisplayName());
						return true;
					}
				}
				return false;
			});
			if (boss.getDrops().isEmpty())
			{
				excludedBosses.add(boss.getName());
			}
			else
			{
				sorted.add(boss);
			}
		}
		sorted.sort(Comparator.comparing(b -> b.getName().toLowerCase(Locale.ROOT)));
		this.bosses = Collections.unmodifiableList(sorted);

		for (BossDefinition boss : sorted)
		{
			byName.put(normalize(boss.getName()), boss);
			for (String kc : boss.getKcNames())
			{
				byKcName.put(normalize(kc), boss);
			}
			for (String loot : boss.getLootNames())
			{
				byLootName.put(normalize(loot), boss);
			}
			for (String page : boss.getClogPages())
			{
				byClogPage.computeIfAbsent(normalize(page), k -> new ArrayList<>()).add(boss);
			}
			for (DropDefinition drop : boss.getDrops())
			{
				for (String item : drop.getAllNames())
				{
					allItemNames.add(normalize(item));
				}
			}
		}
	}

	/** Bosses with nothing left to track once shared items are left out (Chaos Elemental). */
	public Set<String> getExcludedBosses()
	{
		return Collections.unmodifiableSet(excludedBosses);
	}

	public static BossCatalog load(Gson gson, Reader reader)
	{
		FileFormat file = gson.fromJson(reader, FileFormat.class);
		if (file == null || file.bosses == null)
		{
			throw new IllegalArgumentException("No bosses found in drop data");
		}
		return new BossCatalog(file.bosses);
	}

	public static String normalize(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace('’', '\'')
			.replace(' ', ' ')
			.trim()
			.toLowerCase(Locale.ROOT);
	}

	public List<BossDefinition> getBosses()
	{
		return bosses;
	}

	public BossDefinition byName(String name)
	{
		return byName.get(normalize(name));
	}

	public BossDefinition byKcName(String kcName)
	{
		return byKcName.get(normalize(kcName));
	}

	public BossDefinition byLootName(String lootName)
	{
		return byLootName.get(normalize(lootName));
	}

	public List<BossDefinition> byClogPage(String pageTitle)
	{
		List<BossDefinition> list = byClogPage.get(normalize(pageTitle));
		return list == null ? Collections.emptyList() : list;
	}

	/** Items several bosses drop, which On Rate leaves out (dragon pickaxe, uncut onyx...). */
	public Set<String> getExcludedItems()
	{
		return Collections.unmodifiableSet(excludedItems);
	}

	public boolean isClogShared(String itemName)
	{
		return clogSharedItems.contains(normalize(itemName));
	}

	public Set<String> getAllItemNames()
	{
		return Collections.unmodifiableSet(allItemNames);
	}
}
