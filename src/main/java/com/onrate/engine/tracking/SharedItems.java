package com.onrate.engine.tracking;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.tracking.Tracker.LootItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.onrate.engine.tracking.Tracker.NOT_FROM_A_BOSS;

/**
 * Items several bosses share (dragon pickaxe, draconic visage...): how many copies On Rate saw each
 * boss drop and from other monsters, how many older copies the log holds, and the player's choice to
 * judge the item on each boss separately (with older copies given to bosses or "not from a boss").
 * <p>
 * Package-private and unsynchronized: reached only through {@link Tracker}, under its lock.
 */
final class SharedItems
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;

	SharedItems(TrackerContext ctx)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
	}

	/** Copies of each shared item On Rate has seen this boss drop so far, by pool key. */
	Map<String, Integer> poolSeenSnapshot(BossDefinition boss)
	{
		Map<String, Integer> seen = new HashMap<>();
		for (String key : catalog.poolsOf(boss).keySet())
		{
			seen.put(key, poolSeenAt(boss.getName(), key));
		}
		return seen;
	}

	/** Copies of a shared item On Rate saw this boss drop. */
	int poolSeenAt(String bossName, String key)
	{
		BossProgress p = ctx.state().peek(bossName);
		Integer n = p == null || p.poolSeen == null ? null : p.poolSeen.get(key);
		return n == null ? 0 : n;
	}

	boolean poolOnce(String key)
	{
		for (BossCatalog.PoolSource src : catalog.getPools().getOrDefault(key, Collections.emptyList()))
		{
			if (src.getDrop().isOnce())
			{
				return true;
			}
		}
		return false;
	}

	/** True when the log also counts this shared item from monsters other than its bosses. */
	boolean poolAlsoFrom(String key)
	{
		for (BossCatalog.PoolSource src : catalog.getPools().getOrDefault(key, Collections.emptyList()))
		{
			if (src.getDrop().getAlsoFrom() != null)
			{
				return true;
			}
		}
		return false;
	}

	/** Copies of a shared item On Rate saw drop from other monsters (a visage from a regular dragon). */
	int elsewhereSeen(String key)
	{
		Integer n = ctx.state().poolElsewhereSeen().get(key);
		return n == null ? 0 : n;
	}

	/**
	 * Loot from a monster that isn't one of the item's bosses: for a shared item the log also
	 * counts from other monsters (draconic visage, dragon chainbody, uncut onyx), it is noted as
	 * not from a boss and kept in the log total. True when something was noted.
	 */
	boolean recordElsewhere(BossDefinition boss, String itemName)
	{
		String item = BossCatalog.normalize(itemName);
		for (Map.Entry<String, List<BossCatalog.PoolSource>> e : catalog.getPools().entrySet())
		{
			if (!poolAlsoFrom(e.getKey()))
			{
				continue;
			}
			boolean named = false;
			for (BossCatalog.PoolSource src : e.getValue())
			{
				if (src.getBoss() == boss)
				{
					// one of its own bosses: counted there, not here
					named = false;
					break;
				}
				for (String n : src.getDrop().getAllNames())
				{
					named |= BossCatalog.normalize(n).equals(item);
				}
			}
			if (named)
			{
				ctx.state().poolElsewhereSeen().merge(e.getKey(), 1, Integer::sum);
				if (ctx.state().sharedClog().containsKey(e.getKey()))
				{
					ctx.state().sharedClog().merge(e.getKey(), 1, Integer::sum);
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Loot from a monster On Rate doesn't track as a boss (a blue dragon, a smoke devil): only
	 * looked at for the shared items the log also counts from them. True when the saved data changed.
	 */
	boolean onOtherLoot(String source, Collection<LootItem> items)
	{
		if (catalog.byLootName(source) != null)
		{
			return false;
		}
		boolean changed = false;
		for (LootItem item : items)
		{
			if (item.quantity > 0)
			{
				changed |= recordElsewhere(null, item.name);
			}
		}
		return changed;
	}

	/** Copies of a shared item in the log that On Rate didn't see drop: from before it, or played elsewhere. */
	int olderCopies(String key)
	{
		Integer total = ctx.state().sharedClog().get(key);
		if (total == null)
		{
			return 0;
		}
		int seen = elsewhereSeen(key);
		for (BossCatalog.PoolSource src : catalog.getPools().getOrDefault(key, Collections.emptyList()))
		{
			seen += poolSeenAt(src.getBoss().getName(), key);
		}
		boolean once = poolOnce(key);
		return Math.max(0, (once ? Math.min(total, 1) : total) - (once ? Math.min(seen, 1) : seen));
	}

	/** True when a shared item can be judged on each boss separately: its log total is known. */
	boolean canSplit(String key)
	{
		return catalog.getPools().containsKey(key) && ctx.state().sharedClog().containsKey(key);
	}

	/** Where older copies of this item can go: its bosses, and "not from a boss" when other monsters drop it too. */
	boolean validWhere(String key, String where)
	{
		return poolBosses(key).contains(where) || (NOT_FROM_A_BOSS.equals(where) && poolAlsoFrom(key));
	}

	/**
	 * The player's choice for a shared item while it still covers every copy, else null: a copy
	 * the log gained without On Rate seeing it makes the item shared again until they answer.
	 */
	PoolSplit activeSplit(String key)
	{
		PoolSplit split = ctx.state().poolSplits().get(key);
		if (split == null || split.older == null || !canSplit(key) || split.olderCopies() != olderCopies(key))
		{
			return null;
		}
		for (Map.Entry<String, Integer> e : split.older.entrySet())
		{
			if (e.getValue() == null || e.getValue() < 0 || (e.getValue() > 0 && !validWhere(key, e.getKey())))
			{
				return null;
			}
		}
		return split;
	}

	List<String> poolBosses(String key)
	{
		List<String> names = new ArrayList<>();
		for (BossCatalog.PoolSource src : catalog.getPools().getOrDefault(key, Collections.emptyList()))
		{
			names.add(src.getBoss().getName());
		}
		return names;
	}

	/** See {@link #splitPool(String, Map)}, with every older copy from one place. */
	boolean splitPool(String key, String olderFrom)
	{
		int older = olderCopies(key);
		return splitPool(key, older == 0 ? Collections.emptyMap() : Collections.singletonMap(olderFrom, older));
	}

	/**
	 * Judges a shared item on each boss separately: the copies On Rate saw go to the boss that
	 * dropped them, and the older ones to the places given (boss names, or {@link #NOT_FROM_A_BOSS}
	 * for an item other monsters drop too). They must add up to the older copies there are now.
	 * False, and nothing changes, when they don't or the item can't be split yet.
	 */
	boolean splitPool(String key, Map<String, Integer> older)
	{
		if (!canSplit(key) || older == null)
		{
			return false;
		}
		Map<String, Integer> kept = new TreeMap<>();
		int sum = 0;
		for (Map.Entry<String, Integer> e : older.entrySet())
		{
			int n = e.getValue() == null ? 0 : e.getValue();
			if (n < 0 || (n > 0 && !validWhere(key, e.getKey())))
			{
				return false;
			}
			if (n > 0)
			{
				kept.put(e.getKey(), n);
				sum += n;
			}
		}
		if (sum != olderCopies(key))
		{
			return false;
		}
		ctx.state().poolSplits().put(key, new PoolSplit(kept));
		return true;
	}

	/** Judges a shared item on all its bosses together again. */
	void unsplitPool(String key)
	{
		ctx.state().poolSplits().remove(key);
	}

	/** The pool key of a shared item this boss's page judges on its own (the player split it), or null. */
	String splitPoolOf(String bossName, String itemName)
	{
		BossDefinition boss = catalog.byName(bossName);
		String key = boss == null || itemName == null ? null : catalog.poolOf(boss, itemName);
		return key != null && activeSplit(key) != null ? key : null;
	}
}
