package com.onrate.engine.tracking;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.DropDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.onrate.engine.tracking.Tracker.DELVE_SCOREBOARD;

/**
 * The collection log: which pages On Rate covers, reading a page (kill counts, received counts, the
 * kill count tracking starts from, shared item totals) without ever zeroing drops already saved,
 * pages that could not be read this session, the boss with the most kills, and the checklist of pages
 * still to open.
 * <p>
 * Package-private and unsynchronized: reached only through {@link Tracker}, under its lock.
 */
final class ClogSync
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;
	private final Statistics statistics;

	/**
	 * Collection log pages we cover that were opened but could not be read this session (normalized
	 * title to the title shown). Never saved: it says nothing about the data, only about the last read.
	 */
	private final Map<String, String> unreadablePages = new LinkedHashMap<>();
	/** A log page was opened whose header could not be read, so we don't know which page it was. */
	private boolean headerUnreadable;

	ClogSync(TrackerContext ctx, Statistics statistics)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
		this.statistics = statistics;
	}

	/** Another account was loaded: what could not be read was about the last one's log. */
	void clear()
	{
		unreadablePages.clear();
		headerUnreadable = false;
	}

	/** This boss was reset: its pages count as not tried yet. */
	void forget(String bossName)
	{
		BossDefinition resetBoss = catalog.byName(bossName);
		if (resetBoss != null)
		{
			for (String page : resetBoss.getClogPages())
			{
				unreadablePages.remove(BossCatalog.normalize(page));
			}
		}
	}

	/** True when the collection log page with this title is one On Rate reads. */
	boolean coversPage(String pageTitle)
	{
		return !catalog.byClogPage(pageTitle).isEmpty();
	}

	/**
	 * A collection log page was opened but could not be read. Changes no saved data. Pass a null or
	 * empty title when not even the title could be read.
	 *
	 * @return true when this is worth telling the player: the page is one we cover, or the title is unknown
	 */
	boolean onClogPageUnreadable(String pageTitle)
	{
		if (pageTitle == null || pageTitle.isEmpty())
		{
			headerUnreadable = true;
			return true;
		}
		if (!coversPage(pageTitle))
		{
			return false;
		}
		unreadablePages.put(BossCatalog.normalize(pageTitle), pageTitle);
		return true;
	}

	/** True when a log page of this boss was opened this session but could not be read. */
	boolean logPageUnreadable(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return false;
		}
		for (String page : boss.getClogPages())
		{
			if (unreadablePages.containsKey(BossCatalog.normalize(page)))
			{
				return true;
			}
		}
		return false;
	}

	/** Titles of the pages we cover that were opened but could not be read this session. */
	List<String> unreadablePages()
	{
		return new ArrayList<>(unreadablePages.values());
	}

	/** True when a log page was opened whose title could not be read. */
	boolean isHeaderUnreadable()
	{
		return headerUnreadable;
	}

	/**
	 * True when applying this page would set a count we already hold above zero back to zero: every
	 * item reads as not obtained, but a drop on the page is saved as received. The log never loses
	 * items, so this is a page that was misread, not a player who lost drops. A fresh account whose
	 * page is truly all faded has nothing saved and is not flagged.
	 */
	boolean wouldZeroSaved(String pageTitle, Map<String, Integer> items)
	{
		if (items.isEmpty())
		{
			return false;
		}
		for (int quantity : items.values())
		{
			if (quantity > 0)
			{
				return false;
			}
		}
		for (BossDefinition boss : catalog.byClogPage(pageTitle))
		{
			BossProgress progress = ctx.state().peek(boss.getName());
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					String key = BossCatalog.normalize(name);
					if (findQuantity(items, key) == null)
					{
						continue;
					}
					Integer saved = catalog.isClogShared(key) ? ctx.state().sharedClog().get(key)
						: progress == null ? null : progress.received.get(key);
					if (saved != null && saved > 0)
					{
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Handles an opened collection log page.
	 *
	 * @param pageTitle page title, e.g. "Vorkath"
	 * @param counters  header lines like "Vorkath kills: 123", tags already removed
	 * @param items     every item on the page with its obtained quantity (0 if not obtained)
	 * @return bosses whose numbers changed
	 */
	Set<String> onClogPage(String pageTitle, List<String> counters, Map<String, Integer> items)
	{
		Set<String> changed = new LinkedHashSet<>();
		List<BossDefinition> bosses = catalog.byClogPage(pageTitle);
		for (BossDefinition boss : bosses)
		{
			BossProgress progress = ctx.state().get(boss.getName());

			// Kill counts: use the counters that name this boss; if none do and the page
			// belongs to this boss alone, use all of them.
			List<String[]> parsed = new ArrayList<>();
			for (String line : counters)
			{
				int colon = line.lastIndexOf(':');
				if (colon < 0)
				{
					continue;
				}
				String label = line.substring(0, colon).trim();
				String value = line.substring(colon + 1).trim().replace(",", "");
				if (!value.matches("\\d+"))
				{
					continue;
				}
				parsed.add(new String[]{label, value});
			}
			if (boss.hasParts())
			{
				// Combined page: one counter per boss, e.g. "Dagannoth Rex kills: 436".
				for (String[] p : parsed)
				{
					String part = Statistics.partForName(boss, p[0], true);
					if (part == null)
					{
						// the game can leave "The" off a boss's name ("Fractured Archive completions")
						part = Statistics.partForName(boss, "The " + p[0], true);
					}
					if (part != null)
					{
						progress.clogKc.put(BossCatalog.normalize(part), Integer.parseInt(p[1]));
					}
				}
				parsed.clear();
			}
			List<String[]> matching = new ArrayList<>();
			for (String[] p : parsed)
			{
				if (labelNamesBoss(p[0], boss))
				{
					matching.add(p);
				}
			}
			if (matching.isEmpty() && bosses.size() == 1)
			{
				matching = parsed;
			}
			if (boss.getRollsPerKill() > 0)
			{
				// "Rewards claimed" doesn't name the boss but is its exact roll count
				for (String[] p : parsed)
				{
					if (BossCatalog.normalize(p[0]).contains("reward") && !matching.contains(p))
					{
						matching.add(p);
					}
				}
			}
			if (!matching.isEmpty())
			{
				progress.clogKc.clear();
				for (String[] p : matching)
				{
					progress.clogKc.put(BossCatalog.normalize(p[0]), Integer.parseInt(p[1]));
				}
				progress.lootKillsAtSync = progress.lootKills;
				if (boss.getWaves() > 0)
				{
					progress.lastWaveClaimsAtSync = progress.waveClaims == null ? 0
						: progress.waveClaims.getOrDefault(boss.getWaves(), 0);
				}
			}

			// Items several bosses drop: the log total is the same on every page that shows it.
			for (Map.Entry<String, DropDefinition> e : catalog.poolsOf(boss).entrySet())
			{
				Integer total = null;
				for (String name : e.getValue().getAllNames())
				{
					Integer q = findQuantity(items, BossCatalog.normalize(name));
					if (q != null)
					{
						total = (total == null ? 0 : total) + q;
					}
				}
				if (total != null)
				{
					ctx.state().sharedClog().put(e.getKey(), e.getValue().dropsFromQuantity(total));
				}
			}

			// Received counts: the collection log is the full history, so it replaces
			// what we have, except for items several bosses share.
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					String key = BossCatalog.normalize(name);
					if (catalog.isClogShared(key))
					{
						// Can't tell which boss these came from, but the total is right for the account.
						Integer total = findQuantity(items, key);
						if (total != null && !drop.isClogSharedFlag())
						{
							ctx.state().sharedClog().put(key, total);
						}
						continue;
					}
					Integer qty = findQuantity(items, key);
					if (qty != null)
					{
						// stackable items: the log counts items, we count drops
						qty = drop.dropsFromQuantity(qty);
						Integer before = progress.received.get(key);
						if (before != null && qty > before && progress.trackingStartKc != null)
						{
							// a new copy since the last look. The log doesn't say when or where it
							// came from, so it gets no History entry or day count (they'd show a date
							// and kill that aren't known); only its dry streak restarts here
							progress.lastDropKc.put(key, statistics.currentKc(boss));
							progress.milestone.remove(key);
						}
						progress.received.put(key, qty);
					}
				}
			}
			progress.clogSynced = true;
			statistics.startTracking(boss);
			changed.add(boss.getName());
		}
		if (!bosses.isEmpty())
		{
			// a page we cover was read: it is no longer unreadable, and the header works
			unreadablePages.remove(BossCatalog.normalize(pageTitle));
			headerUnreadable = false;
		}
		return changed;
	}

	static Integer findQuantity(Map<String, Integer> items, String normalizedName)
	{
		for (Map.Entry<String, Integer> e : items.entrySet())
		{
			if (BossCatalog.normalize(e.getKey()).equals(normalizedName))
			{
				return e.getValue();
			}
		}
		return null;
	}

	static boolean labelNamesBoss(String label, BossDefinition boss)
	{
		String l = BossCatalog.normalize(label);
		if (l.contains(BossCatalog.normalize(boss.getName())))
		{
			return true;
		}
		for (String kc : boss.getKcNames())
		{
			if (l.contains(BossCatalog.normalize(kc)))
			{
				return true;
			}
		}
		return false;
	}

	/** The boss with the highest known kill count, used to pick what to show on login. */
	String bossWithMostKills()
	{
		String best = null;
		int bestKc = 0;
		if (ctx.state().bosses == null)
		{
			return null;
		}
		for (String name : ctx.state().bosses.keySet())
		{
			BossDefinition boss = catalog.byName(name);
			if (boss == null)
			{
				continue;
			}
			BossProgress p = ctx.state().peek(name);
			int kc = boss.isDelve() ? Statistics.sum(p.levelKc.values()) : boss.hasParts() ? Statistics.sum(Statistics.partKills(boss, p).values()) : Math.max(p.lootKills, Math.max(Statistics.combine(p.chatKc.values(), boss.combineKcWithMax()),
				Statistics.combine(p.clogKc.values(), boss.combineKcWithMax())));
			if (kc > bestKc)
			{
				bestKc = kc;
				best = boss.getName();
			}
		}
		return best;
	}

	/**
	 * The setup checklist: every collection log page the plugin can read, in alphabetical
	 * order, plus the Doom scoreboard. A page counts as synced once every boss on it is.
	 */
	List<SyncStatus> syncChecklist()
	{
		Map<String, List<String>> bossesByPage = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, Boolean> syncedByPage = new LinkedHashMap<>();
		Set<String> killedPages = new HashSet<>();
		List<SyncStatus> result = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = ctx.state().peek(boss.getName());
			for (String page : boss.getClogPages())
			{
				bossesByPage.computeIfAbsent(page, k -> new ArrayList<>()).add(boss.getName());
				boolean synced = p != null && p.clogSynced;
				syncedByPage.merge(page.toLowerCase(Locale.ROOT), synced, Boolean::logicalAnd);
				if (p != null && statistics.currentKc(boss) > 0)
				{
					killedPages.add(page.toLowerCase(Locale.ROOT));
				}
			}
			if (boss.isDelve())
			{
				result.add(new SyncStatus(DELVE_SCOREBOARD, Collections.singletonList(boss.getName()),
					p != null && p.levelsSynced, p != null && statistics.currentKc(boss) > 0));
			}
		}
		List<SyncStatus> pages = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : bossesByPage.entrySet())
		{
			pages.add(new SyncStatus(e.getKey(), e.getValue(),
				syncedByPage.getOrDefault(e.getKey().toLowerCase(Locale.ROOT), false),
				killedPages.contains(e.getKey().toLowerCase(Locale.ROOT))));
		}
		pages.addAll(result);
		return pages;
	}
}
