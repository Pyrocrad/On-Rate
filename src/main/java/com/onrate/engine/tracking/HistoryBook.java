package com.onrate.engine.tracking;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import com.onrate.engine.tracking.Tracker.LastUnique;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.onrate.engine.tracking.Tracker.MAX_KILLS;
import static com.onrate.engine.tracking.Tracker.UNKNOWN_PART_LIMIT;
import static com.onrate.engine.tracking.Tracker.roundMultiple;

/**
 * What happened over time: the kills kept per boss for History (the oldest go once there are too
 * many), the drops in them and the queries on them (recent drops, a drop's kills, the luckiest and
 * driest), removing a kill or drop, past drops the player adds by hand (with the rules on when one
 * can be placed and ranked), and the luck graph's one point per day.
 * <p>
 * Package-private and unsynchronized: reached only through {@link Tracker}, under its lock.
 */
final class HistoryBook
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;
	private final Statistics statistics;
	private final RaidRecords raidData;

	/** Most points the luck graph keeps (about three years of daily play). */
	private static final int MAX_HISTORY = 1000;

	HistoryBook(TrackerContext ctx, Statistics statistics, RaidRecords raidData)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
		this.statistics = statistics;
		this.raidData = raidData;
	}

	/** Saves a kill for the History section (the oldest ones go once there are too many). */
	KillEntry addKill(BossDefinition boss, BossProgress progress, long nowMillis)
	{
		KillEntry entry = new KillEntry();
		entry.kc = statistics.currentKc(boss);
		entry.time = nowMillis;
		progress.kills.add(entry);
		ctx.killsChanged.add(boss.getName());
		while (progress.kills.size() > MAX_KILLS)
		{
			progress.kills.remove(0);
		}
		return entry;
	}

	/** This boss's saved kills, newest first, with each raid's points and purple chance. */
	List<HistoryRow> history(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = ctx.state().peek(bossName);
		List<HistoryRow> rows = new ArrayList<>();
		if (boss == null || p == null)
		{
			return rows;
		}
		for (int i = p.kills.size() - 1; i >= 0; i--)
		{
			KillEntry k = p.kills.get(i);
			RaidRecord match = null;
			double chance = Double.NaN;
			if (boss.getRaid() != null && k.partKc != null)
			{
				for (RaidRecord r : p.raids)
				{
					if (r.kc == k.partKc && (k.part == null || k.part.equals(r.part)))
					{
						match = r;
					}
				}
				if (match != null)
				{
					for (BossPart part : boss.getParts())
					{
						if (part.getName().equals(match.part))
						{
							chance = RateModel.recordChance(match, part, ctx.settings());
						}
					}
				}
			}
			rows.add(new HistoryRow(k, match, chance));
		}
		return rows;
	}

	/** Kills saved for this boss (at most {@link #MAX_KILLS}). */
	int killsSeen(String bossName)
	{
		BossProgress p = ctx.state().peek(bossName);
		return p == null ? 0 : p.kills.size();
	}

	/** The latest uniques across every boss, newest first. */
	List<RecentDrop> recentDrops(int limit)
	{
		List<RecentDrop> all = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = ctx.state().peek(boss.getName());
			if (p == null)
			{
				continue;
			}
			for (KillEntry k : p.kills)
			{
				if (!k.isDateKnown())
				{
					// added by hand from memory: it has a kill count but no date, so it isn't "recent"
					continue;
				}
				for (KillEntry.DropEntry d : k.getDrops())
				{
					// saved before the item was flagged clogShared (dragon thrownaxes and knives): kept,
					// not shown. Pooled items saved when they were a row (a Kings dragon axe) still show.
					if (!catalog.isUntracked(d.getName()))
					{
						all.add(new RecentDrop(boss.getName(), k, d));
					}
				}
			}
		}
		all.sort((a, b) -> Long.compare(b.getKill().getTime(), a.getKill().getTime()));
		return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
	}

	/**
	 * Removes one unique from History: exactly this saved drop of this kill, as picked in Recent
	 * or History (by the entries themselves, so two copies of the same item stay apart). Only
	 * what is read from History changes: History, Recent and Last unique. The received count,
	 * kill count, expected and dry streak stay as they are, and the kill itself stays in History.
	 * Today and Last 7 days keep their counts: the day a unique was counted on isn't saved (it
	 * can differ from its kill's day), so a removed unique stays in them until that day expires.
	 * Returns false when the entry isn't there any more (trimmed, or another account loaded since
	 * it was shown).
	 */
	boolean removeFromHistory(String bossName, KillEntry kill, KillEntry.DropEntry drop)
	{
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = ctx.state().peek(bossName);
		if (boss == null || p == null || kill == null || drop == null || kill.drops == null)
		{
			return false;
		}
		boolean listed = false;
		for (KillEntry k : p.kills)
		{
			if (k == kill)
			{
				listed = true;
				break;
			}
		}
		boolean removed = false;
		for (java.util.Iterator<KillEntry.DropEntry> it = kill.drops.iterator(); listed && it.hasNext(); )
		{
			if (it.next() == drop)
			{
				it.remove();
				removed = true;
				break;
			}
		}
		if (!removed)
		{
			return false;
		}
		if (kill.drops.isEmpty())
		{
			// saved like a kill that never had a unique
			kill.drops = null;
		}
		if (drop.isEntered() && kill.part == null)
		{
			// a drop added by hand also moved the item's dry streak: put it back to the latest one left
			DropDefinition def = Statistics.rowOfSaved(boss, drop.getName());
			String key = def == null ? null : BossCatalog.normalize(def.getName());
			if (key != null && Integer.valueOf(kill.kc).equals(p.lastDropKc.get(key)))
			{
				Integer latest = null;
				for (KillEntry k : p.kills)
				{
					for (KillEntry.DropEntry d : k.getDrops())
					{
						if (d.getName().equals(drop.getName()) && !(d.isEntered() && k.part != null)
							&& k.kc < kill.kc && (latest == null || k.kc > latest))
						{
							latest = k.kc;
						}
					}
				}
				if (latest == null)
				{
					p.lastDropKc.remove(key);
				}
				else
				{
					p.lastDropKc.put(key, latest);
				}
			}
		}
		ctx.killsChanged.add(bossName);
		return true;
	}

	/**
	 * Why a drop added by hand can't be ranked in Luckiest drops / Longest waits, or null when it can
	 * (or isn't in History): its wait isn't known, or the boss's drop rate isn't.
	 */
	String pastDropNotRankedReason(String bossName, String itemName, int kc)
	{
		return pastDropNotRankedReason(bossName, itemName, kc, null);
	}

	/** As above for a drop entered at a part of a combined page. */
	String pastDropNotRankedReason(String bossName, String itemName, int kc, String partName)
	{
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = ctx.state().peek(bossName);
		if (boss == null || p == null)
		{
			return null;
		}
		for (KillEntry k : p.kills)
		{
			if (!inCounting(k, partName) || countOf(k, partName) != kc)
			{
				continue;
			}
			for (KillEntry.DropEntry d : k.getDrops())
			{
				DropDefinition drop = Statistics.rowOfSaved(boss, d.getName());
				if (d.isEntered() && drop != null && drop.getName().equals(itemName))
				{
					if (d.getSince() == null)
					{
						return "On Rate can't tell how long it took: it doesn't know this was your first one. "
							+ "Open this boss's collection log page, or remove it and add it again with \"This was my first one\" ticked.";
					}
					return d.getMultiple() == null ? "On Rate doesn't know this item's drop rate yet." : null;
				}
			}
		}
		return null;
	}

	/**
	 * The drops of one item at one boss in History (seen or added by hand) whose wait is known
	 * exactly, luckiest first, for the item's hover.
	 */
	List<RecentDrop> dropsOf(String bossName, String itemName)
	{
		List<RecentDrop> list = new ArrayList<>();
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = ctx.state().peek(bossName);
		if (boss == null || p == null)
		{
			return list;
		}
		for (KillEntry k : p.kills)
		{
			for (KillEntry.DropEntry d : k.getDrops())
			{
				DropDefinition drop = Statistics.rowOfSaved(boss, d.getName());
				if (drop != null && drop.getName().equals(itemName) && d.getMultiple() != null && !d.isAtLeast())
				{
					list.add(new RecentDrop(bossName, k, d));
				}
			}
		}
		list.sort((a, b) -> Double.compare(a.getDrop().getMultiple(), b.getDrop().getMultiple()));
		return list;
	}

	/**
	 * Drops across every boss whose wait is known exactly and was lucky (shorter than the drop rate
	 * gives on average) or dry (longer), best first: luckiest are ranked by the smallest multiple of
	 * the drop rate, dry ones by the largest. Drops added by hand count; undated ones are fine.
	 */
	List<RecentDrop> dropsByLuck(boolean lucky, int limit)
	{
		List<RecentDrop> all = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = ctx.state().peek(boss.getName());
			if (p == null)
			{
				continue;
			}
			for (KillEntry k : p.kills)
			{
				for (KillEntry.DropEntry d : k.getDrops())
				{
					Double m = d.getMultiple();
					if (m == null || d.isAtLeast() || catalog.isUntracked(d.getName()) || (lucky ? m >= 1 : m <= 1))
					{
						continue;
					}
					DropDefinition drop = Statistics.rowOfSaved(boss, d.getName());
					if (drop != null && statistics.counts(boss, drop, p))
					{
						all.add(new RecentDrop(boss.getName(), k, d));
					}
				}
			}
		}
		all.sort((a, b) ->
		{
			int byLuck = lucky ? Double.compare(a.getDrop().getMultiple(), b.getDrop().getMultiple())
				: Double.compare(b.getDrop().getMultiple(), a.getDrop().getMultiple());
			return byLuck != 0 ? byLuck : Long.compare(b.getKill().getTime(), a.getKill().getTime());
		});
		return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
	}

	/**
	 * Adds a unique the player remembers getting at a known kill count (before On Rate, or one it
	 * missed) to History, e.g. "Twisted bow at raid 115". The entry has no date. Only History,
	 * the item's dry streak and its "after N kills" text change: the received count already comes
	 * from the collection log, so nothing is counted twice. Returns false when the boss or item is
	 * unknown, the kill count is not between 1 and the current one, the item is already listed at
	 * that kill count, or History is full of newer kills.
	 */
	boolean addPastDrop(String bossName, String itemName, int kc)
	{
		return addPastDrop(bossName, itemName, kc, false);
	}

	/**
	 * As {@link #addPastDrop(String, String, int)}, with {@code first} true when the player says
	 * this was their first copy: its wait is then its kill count, even when the collection log
	 * shows more copies. Ignored when an earlier copy is already listed (the gap since it is used).
	 */
	boolean addPastDrop(String bossName, String itemName, int kc, boolean first)
	{
		return addPastDrop(bossName, itemName, kc, first, null);
	}

	/** Kills in the counting a past drop is entered in: the part's own count, or the page's when it has no parts. */
	static boolean inCounting(KillEntry k, String part)
	{
		return part == null || part.equals(k.part) && k.partKc != null;
	}

	static int countOf(KillEntry k, String part)
	{
		return part == null ? k.kc : k.partKc;
	}

	/** Most kills a past drop can be entered at: the page's kill count, or the part's own. 0 for a page whose kills are unknown. */
	int pastDropLimit(String bossName, String partName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return 0;
		}
		if (partName == null)
		{
			return boss.hasParts() ? 0 : statistics.currentKc(boss);
		}
		BossStats s = statistics.stats(bossName, ctx.settings());
		Integer n = s == null || s.getPartCounts() == null ? null : s.getPartCounts().get(partName);
		// the part's count isn't known (not in the log yet, no kills seen): the player knows better, so don't block them
		return n == null || n < 1 ? UNKNOWN_PART_LIMIT : n;
	}

	/**
	 * The parts of a combined page a drop can be entered at: the ones the item drops from (all of
	 * them when none has a known rate). Empty for a page without parts.
	 */
	List<String> pastDropParts(String bossName, String itemName)
	{
		List<String> names = new ArrayList<>();
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null || !boss.hasParts())
		{
			return names;
		}
		DropDefinition drop = null;
		for (DropDefinition d : boss.getDrops())
		{
			if (d.getName().equals(itemName))
			{
				drop = d;
			}
		}
		List<String> fallback = new ArrayList<>();
		for (BossPart part : boss.getParts())
		{
			fallback.add(part.getName());
			if (drop != null && RateModel.partRate(boss, part, drop, ctx.settings()) > 0)
			{
				names.add(part.getName());
			}
		}
		return names.isEmpty() ? fallback : names;
	}

	/**
	 * As above for a page with parts: {@code partName} is the mode or boss ("Challenge Mode",
	 * "Artio") and {@code kc} that part's own kill count, as the game's chat shows it. The wait and
	 * its luck use that part's kills and drop rate. The item's dry streak counts the page's kills,
	 * which aren't known for that moment, so it stays as it is. Null for a page without parts.
	 */
	boolean addPastDrop(String bossName, String itemName, int kc, boolean first, String partName)
	{
		return tryAddPastDrop(bossName, itemName, kc, first, partName) == PastDropResult.ADDED;
	}

	/** As {@link #addPastDrop(String, String, int, boolean, String)}, saying why a refused drop was refused. */
	PastDropResult tryAddPastDrop(String bossName, String itemName, int kc, boolean first, String partName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return PastDropResult.UNKNOWN;
		}
		if (kc < 1)
		{
			return PastDropResult.COUNT_TOO_LOW;
		}
		BossPart part = null;
		for (BossPart candidate : boss.getParts())
		{
			if (candidate.getName().equals(partName))
			{
				part = candidate;
			}
		}
		if (boss.hasParts() != (part != null) || (partName != null && part == null))
		{
			return PastDropResult.UNKNOWN;
		}
		if (kc > pastDropLimit(bossName, partName))
		{
			return PastDropResult.COUNT_TOO_HIGH;
		}
		DropDefinition drop = null;
		for (DropDefinition d : boss.getDrops())
		{
			if (d.getName().equals(itemName))
			{
				drop = d;
			}
		}
		if (drop == null)
		{
			return PastDropResult.UNKNOWN;
		}
		BossProgress progress = ctx.state().get(boss.getName());
		String shown = drop.getDisplayName();
		// check everything before changing anything: a drop On Rate saw at that count can't be replaced, and
		// History can't list more copies than the collection log shows
		int listed = 0;
		int replaceable = 0;
		List<Object[]> enteredElsewhere = new ArrayList<>();
		for (KillEntry k : progress.kills)
		{
			boolean sameSpot = inCounting(k, partName) && countOf(k, partName) == kc;
			for (KillEntry.DropEntry d : k.getDrops())
			{
				if (!d.getName().equals(shown))
				{
					continue;
				}
				listed++;
				if (sameSpot && d.isEntered())
				{
					replaceable++;
				}
				else if (sameSpot)
				{
					return PastDropResult.SAW_AT_COUNT;
				}
				else if (d.isEntered())
				{
					enteredElsewhere.add(new Object[]{k, d});
				}
			}
		}
		Object[] swap = null;
		Integer copies = progress.received.get(BossCatalog.normalize(itemName));
		if (progress.clogSynced && copies != null && copies >= 1 && listed - replaceable >= copies)
		{
			// History already lists every copy: when exactly one was added by hand, the new one corrects it
			if (enteredElsewhere.size() != 1)
			{
				return PastDropResult.ALL_COPIES_LISTED;
			}
			swap = enteredElsewhere.get(0);
		}
		if (swap != null)
		{
			KillEntry swapKill = (KillEntry) swap[0];
			removeFromHistory(boss.getName(), swapKill, (KillEntry.DropEntry) swap[1]);
			if (swapKill.drops == null && !swapKill.isDateKnown())
			{
				progress.kills.remove(swapKill);
			}
		}
		// adding it again at the same kill count replaces what the player entered before (never a drop On Rate saw)
		for (java.util.Iterator<KillEntry> kills = progress.kills.iterator(); kills.hasNext(); )
		{
			KillEntry k = kills.next();
			if (inCounting(k, partName) && countOf(k, partName) == kc && k.drops != null
				&& k.drops.removeIf(d -> d.isEntered() && d.getName().equals(shown)))
			{
				if (k.drops.isEmpty())
				{
					k.drops = null;
					if (!k.isDateKnown())
					{
						kills.remove();
					}
				}
				ctx.killsChanged.add(boss.getName());
			}
		}
		KillEntry at = null;
		int index = progress.kills.size();
		boolean placed = false;
		int firstDated = progress.kills.size();
		int previousKc = 0;
		for (int i = 0; i < progress.kills.size(); i++)
		{
			KillEntry k = progress.kills.get(i);
			if (k.isDateKnown() && firstDated == progress.kills.size())
			{
				firstDated = i;
			}
			if (!inCounting(k, partName))
			{
				continue;
			}
			int at0 = countOf(k, partName);
			if (at0 == kc && at == null)
			{
				at = k;
			}
			if (at0 > kc && !placed)
			{
				index = i;
				placed = true;
			}
			if (at0 < kc)
			{
				for (KillEntry.DropEntry d : k.getDrops())
				{
					if (d.getName().equals(shown))
					{
						previousKc = Math.max(previousKc, at0);
					}
				}
			}
		}
		if (!placed && part != null)
		{
			// no later kill of this part is saved: after the older entries without a date, before the dated ones
			index = firstDated;
		}
		if (at != null)
		{
			for (KillEntry.DropEntry d : at.getDrops())
			{
				if (d.getName().equals(shown))
				{
					return PastDropResult.SAW_AT_COUNT;
				}
			}
		}
		else if (index == 0 && progress.kills.size() >= MAX_KILLS)
		{
			return PastDropResult.HISTORY_FULL;
		}
		KillEntry.DropEntry entry = new KillEntry.DropEntry();
		entry.name = shown;
		entry.pet = drop.isPet() ? Boolean.TRUE : null;
		entry.entered = Boolean.TRUE;
		String key = BossCatalog.normalize(itemName);
		Integer have = progress.received.get(key);
		if (previousKc > 0)
		{
			entry.since = kc - previousKc;
		}
		else if (first || progress.clogSynced && !catalog.isClogShared(key) && have != null && have == 1)
		{
			// the player says it was the first, or the only copy the log shows is this one
			entry.since = kc;
			entry.first = Boolean.TRUE;
		}
		if (entry.since != null)
		{
			double expected;
			if (part != null && part.getRaid() != null && !drop.hasPartRates())
			{
				// a raid: each raid in the wait counts at its own purple chance (from its points when On Rate
				// counted them), and at the settings' chance for the ones from before
				expected = raidData.raidWaitExpected(boss, part, drop, progress, kc - entry.since, kc);
			}
			else
			{
				expected = entry.since * (part == null ? statistics.rateOf(boss, drop) : RateModel.partRate(boss, part, drop, ctx.settings()));
			}
			if (expected > 0)
			{
				entry.multiple = roundMultiple(expected);
			}
			// the same records a drop On Rate saw feeds
			if (progress.fastestGap == null)
			{
				progress.fastestGap = new HashMap<>();
				progress.longestGap = new HashMap<>();
			}
			progress.fastestGap.merge(key, entry.since, Math::min);
			progress.longestGap.merge(key, entry.since, Math::max);
		}
		if (at == null)
		{
			at = new KillEntry();
			at.kc = kc;
			at.time = 0;
			if (part != null)
			{
				at.part = partName;
				at.partKc = kc;
			}
			progress.kills.add(index, at);
			while (progress.kills.size() > MAX_KILLS)
			{
				progress.kills.remove(0);
			}
		}
		if (at.drops == null)
		{
			at.drops = new ArrayList<>();
		}
		at.drops.add(entry);
		ctx.killsChanged.add(boss.getName());
		// the dry streak counts from the latest drop of any copy (page kills: not known for a part)
		Integer last = progress.lastDropKc.get(key);
		if (part == null && (last == null || kc > last))
		{
			progress.lastDropKc.put(key, kc);
			progress.milestone.remove(key);
		}
		return PastDropResult.ADDED;
	}

	/**
	 * Saves today's account luck for the graph. One point per day: a later call on the same
	 * day replaces it. Returns true when something was stored.
	 */
	boolean recordHistory(long day, RaidSettings raids)
	{
		AccountStats account = statistics.accountStats(raids);
		RowStats total = account.getTotal();
		if (total.getExpected() <= 0 || Double.isNaN(total.getLuck()))
		{
			return false;
		}
		List<HistoryPoint> history = ctx.state().history();
		HistoryPoint point = new HistoryPoint(day, total.getExpected(), total.getReceived(), total.getLuck());
		if (!history.isEmpty() && history.get(history.size() - 1).day >= day)
		{
			history.set(history.size() - 1, point);
		}
		else
		{
			history.add(point);
		}
		while (history.size() > MAX_HISTORY)
		{
			history.remove(0);
		}
		return true;
	}

	/** The luck graph's points, oldest first. */
	List<HistoryPoint> history()
	{
		return new ArrayList<>(ctx.state().history());
	}

	/** The last unique On Rate saw in History (any boss), or null; with when History starts. */
	LastUnique lastUnique()
	{
		LastUnique result = null;
		long earliest = Long.MAX_VALUE;
		if (ctx.state().bosses == null)
		{
			return null;
		}
		for (String name : ctx.state().bosses.keySet())
		{
			BossDefinition boss = catalog.byName(name);
			BossProgress p = ctx.state().peek(name);
			if (boss == null || p == null)
			{
				continue;
			}
			for (KillEntry k : p.kills)
			{
				if (!k.isDateKnown())
				{
					continue;
				}
				earliest = Math.min(earliest, k.time);
				for (KillEntry.DropEntry d : k.getDrops())
				{
					DropDefinition drop = Statistics.rowOfSaved(boss, d.getName());
					if (drop != null && statistics.counts(boss, drop, p) && (result == null || k.time > result.time))
					{
						result = new LastUnique(d.getName(), boss.getName(), k.time, 0);
					}
				}
			}
		}
		if (earliest == Long.MAX_VALUE)
		{
			return null;
		}
		return result == null ? new LastUnique(null, null, 0, earliest) : new LastUnique(result.item, result.boss, result.time, earliest);
	}
}
