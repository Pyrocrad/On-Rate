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
	/** Items flagged clogShared in the data (dragon 2h sword, thrownaxes...): not tracked anywhere. */
	private final Set<String> untrackedItems = new HashSet<>();
	/** Display names of the shared items that were left out, for the panel. */
	private final Set<String> excludedItems = new java.util.TreeSet<>();
	private final Set<String> allItemNames = new HashSet<>();
	private final Set<String> excludedBosses = new java.util.TreeSet<>();
	private final Map<String, List<PoolSource>> pools = new java.util.TreeMap<>();
	private final Set<String> minionLootNames = new HashSet<>();

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
						untrackedItems.add(key);
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

		// Leave those items out of the boss pages: their log total can't be split per boss.
		// Items several bosses drop are pooled for the account instead (all those bosses'
		// expected added up against the one log total); flagged items that also come from
		// outside the bosses (dragon 2h sword) can't be pooled and are left out entirely.
		List<BossDefinition> sorted = new ArrayList<>();
		for (BossDefinition boss : bosses)
		{
			boss.removeDrops(drop ->
			{
				for (String item : drop.getAllNames())
				{
					String key = normalize(item);
					if (clogSharedItems.contains(key))
					{
						excludedItems.add(drop.getDisplayName());
						if (!drop.isClogSharedFlag() && itemBossCount.getOrDefault(key, 0) > 1)
						{
							pools.computeIfAbsent(normalize(drop.getName()), k -> new ArrayList<>())
								.add(new PoolSource(boss, drop));
							// shared items show in the panel too: they need their pictures
							for (String name : drop.getAllNames())
							{
								allItemNames.add(normalize(name));
							}
						}
						return true;
					}
				}
				return false;
			});
			if (boss.getDrops().isEmpty())
			{
				// still tracked for its kill count (shared items need it), but has no page of its own
				excludedBosses.add(boss.getName());
			}
			sorted.add(boss);
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
			for (String minion : boss.getMinionLoot())
			{
				byLootName.put(normalize(minion), boss);
				minionLootNames.add(normalize(minion));
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

	/** One boss that drops a pooled (shared) item, with its drop definition. */
	public static final class PoolSource
	{
		private final BossDefinition boss;
		private final DropDefinition drop;

		PoolSource(BossDefinition boss, DropDefinition drop)
		{
			this.boss = boss;
			this.drop = drop;
		}

		public BossDefinition getBoss()
		{
			return boss;
		}

		public DropDefinition getDrop()
		{
			return drop;
		}
	}

	/** Items several bosses drop, keyed by normalized name, with every boss that drops them. */
	public Map<String, List<PoolSource>> getPools()
	{
		return Collections.unmodifiableMap(pools);
	}

	/** The pooled item (normalized) this boss drops under {@code itemName}, or null. */
	public String poolOf(BossDefinition boss, String itemName)
	{
		String key = normalize(itemName);
		for (Map.Entry<String, List<PoolSource>> e : pools.entrySet())
		{
			for (PoolSource src : e.getValue())
			{
				if (src.boss == boss)
				{
					for (String n : src.drop.getAllNames())
					{
						if (normalize(n).equals(key))
						{
							return e.getKey();
						}
					}
				}
			}
		}
		return null;
	}

	/** The pooled items this boss drops: pool key (normalized item name) to its drop row. */
	public Map<String, DropDefinition> poolsOf(BossDefinition boss)
	{
		Map<String, DropDefinition> result = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, List<PoolSource>> e : pools.entrySet())
		{
			for (PoolSource src : e.getValue())
			{
				if (src.boss == boss)
				{
					result.put(e.getKey(), src.drop);
				}
			}
		}
		return result;
	}

	/** True for a minion's loot tracker name (its loot counts for the boss, not as a kill). */
	public boolean isMinionLoot(String lootName)
	{
		return minionLootNames.contains(normalize(lootName));
	}

	/**
	 * Bosses with nothing left to track once shared items are left out (Chaos Elemental). They
	 * still get a page, with only their shared items on it.
	 */
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

	/**
	 * True for an item flagged clogShared in the data: it also drops from monsters outside the
	 * bosses, so it isn't tracked at any boss (not pooled either). Items several bosses drop
	 * (pooled, like the dragon axe) aren't untracked.
	 */
	public boolean isUntracked(String itemName)
	{
		return untrackedItems.contains(normalize(itemName));
	}

	public Set<String> getAllItemNames()
	{
		return Collections.unmodifiableSet(allItemNames);
	}
}
