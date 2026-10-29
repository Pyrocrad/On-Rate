package com.expecteddrops.tracking;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.BossPart;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.AccountStats;
import com.expecteddrops.stats.SharedPool;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.ItemHighlight;
import com.expecteddrops.stats.RaidChance;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns game events into kill counts and received counts, and works out the expected
 * numbers. Has no RuneLite dependencies so it can be unit tested; all methods are
 * synchronized because events arrive on the client thread and the panel reads on Swing.
 */
public class Tracker
{
	/** Same pattern RuneLite's chat commands plugin uses for kill count messages. */
	static final Pattern KILLCOUNT_PATTERN = Pattern.compile("Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?(?:<col=[0-9a-f]{6}>|@.+?@)(?<kc>[0-9,]+)</col>");
	private static final Pattern NEW_CLOG_ITEM = Pattern.compile("New item added to your collection log: (.+)");
	/** Completion message for a delve level, e.g. "Delve level: 3 duration: 1:52.20" ("8+" for deep delves). */
	private static final Pattern DELVE_LEVEL = Pattern.compile("Delve level: ?(\\d+\\+?)");
	/** Fortis Colosseum: "Wave 7 completed!" (the wording still needs checking in game). */
	private static final Pattern WAVE_DONE = Pattern.compile("^Wave:? (\\d+) completed", Pattern.CASE_INSENSITIVE);
	/** Guaranteed for beating the last wave, so its loot tells which wave a claim was at. */
	private static final String LAST_WAVE_REWARD = "dizana's quiver";
	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
	};
	private static final long PET_WINDOW_MILLIS = 60_000;

	/** An item from a loot event, already resolved to its name. */
	public static class LootItem
	{
		final String name;
		final int quantity;

		public LootItem(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	private static final int[] MILESTONES = {2, 3, 5, 10};
	/** A kill count jump bigger than this (a sync, a first message) doesn't announce milestones. */
	private static final int MAX_QUIET_JUMP = 5;

	private final BossCatalog catalog;
	/**
	 * Collection log pages we cover that were opened but could not be read this session (normalized
	 * title to the title shown). Never saved: it says nothing about the data, only about the last read.
	 */
	private final Map<String, String> unreadablePages = new LinkedHashMap<>();
	/** A log page was opened whose header could not be read, so we don't know which page it was. */
	private boolean headerUnreadable;
	private final List<Note> outbox = new ArrayList<>();
	private RaidSettings settings = raid -> 0;
	private TrackerState state = new TrackerState();
	private String lastBoss;
	/** For combined pages: which part (e.g. Dagannoth Rex) was killed last. */
	private String lastPart;
	private long lastKillMillis;
	/** A finished raid's data that arrived before its completion count message. */
	private RaidRecord pendingRecord;
	private long pendingRecordMillis;
	/** The raid mode whose completion message came last, for data that arrives after it. */
	private String lastRaidBoss;
	private String lastRaidPart;
	private int lastRaidKc;
	private long lastRaidMillis;
	/** The latest Tombs of Amascut record, for the team total the ToA plugin sends after it. */
	private RaidRecord latestToa;
	private long latestToaMillis;
	private int[] pendingParty;
	private long pendingPartyMillis;
	/** Points and the completion count message arrive within seconds of each other. */
	private static final long RAID_MATCH_MILLIS = 60_000;
	/** The Theatre of Blood board can be read a while after the completion message. */
	private static final long TOB_MATCH_MILLIS = 5 * 60_000;
	private static final long PARTY_MATCH_MILLIS = 2 * 60_000;
	/** Most raid records kept per raid. */
	private static final int MAX_RAID_RECORDS = 5000;

	public Tracker(BossCatalog catalog)
	{
		this.catalog = catalog;
	}

	public BossCatalog getCatalog()
	{
		return catalog;
	}

	/** Players around you right now (you included), or 0 when unknown or turned off. */
	private java.util.function.IntSupplier teamSize = () -> 0;

	/** How to count the players around you at a group boss kill (see BossDefinition#isDetectTeam). */
	public synchronized void setTeamSizeDetector(java.util.function.IntSupplier teamSize)
	{
		this.teamSize = teamSize == null ? () -> 0 : teamSize;
	}

	/** Bosses whose History changed since the last save (it is saved per boss, see {@link #changedKills()}). */
	private final Set<String> killsChanged = new HashSet<>();
	/** Raids whose saved raids changed since the last save (saved per raid, see {@link #saveData}). */
	private final Set<String> raidsChanged = new HashSet<>();

	/**
	 * Everything to save, serialized while holding the lock: the main data, plus the History and
	 * raids of the bosses that changed since the last save.
	 */
	public synchronized SaveData saveData(java.util.function.Function<Object, String> toJson)
	{
		SaveData data = new SaveData(toJson.apply(state));
		for (String name : killsChanged)
		{
			BossProgress p = state.peek(name);
			data.kills.put(name, p == null || p.kills.isEmpty() ? null : toJson.apply(p.kills));
		}
		killsChanged.clear();
		for (String name : raidsChanged)
		{
			BossProgress p = state.peek(name);
			data.raids.put(name, p == null || p.raids.isEmpty() ? null : toJson.apply(p.raids));
		}
		raidsChanged.clear();
		return data;
	}

	/** Puts back a raid's saved raids when an account is loaded. */
	public synchronized void setRaids(String bossName, List<RaidRecord> raids)
	{
		if (raids != null && !raids.isEmpty() && catalog.byName(bossName) != null)
		{
			List<RaidRecord> list = state.get(bossName).raids;
			list.clear();
			list.addAll(raids.subList(Math.max(0, raids.size() - MAX_RAID_RECORDS), raids.size()));
			raidsChanged.remove(bossName);
		}
	}

	/**
	 * The History of every boss whose kills changed since the last call, to save. History is
	 * saved apart from the rest, one boss at a time, so a kill only rewrites that boss's
	 * history instead of all of them. An empty list means the boss was reset.
	 */
	public synchronized Map<String, List<KillEntry>> changedKills()
	{
		Map<String, List<KillEntry>> changed = new HashMap<>();
		for (String name : killsChanged)
		{
			BossProgress p = state.peek(name);
			changed.put(name, p == null ? new ArrayList<>() : new ArrayList<>(p.kills));
		}
		killsChanged.clear();
		return changed;
	}

	/** Puts back a boss's saved History when an account is loaded. */
	public synchronized void setKills(String bossName, List<KillEntry> kills)
	{
		// Recent fills in from History; do that again with the History just loaded
		daysFilled.remove(bossName);
		if (kills != null && !kills.isEmpty() && catalog.byName(bossName) != null)
		{
			List<KillEntry> list = state.get(bossName).kills;
			list.clear();
			list.addAll(kills.subList(Math.max(0, kills.size() - MAX_KILLS), kills.size()));
		}
	}

	/** Kills kept per boss for the History section. */
	public static final int MAX_KILLS = 500;
	/** Days kept for the Recent tab's day counts. */
	static final int DAYS_KEPT = 14;

	/** The day counter of a boss on the day of this time (local time zone), created when missing. */
	private DayCount dayCount(String bossName, long millis)
	{
		long day = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay();
		Map<String, Map<String, DayCount>> days = state.days();
		if (!days.containsKey(String.valueOf(day)))
		{
			// a new day: forget the ones that are too old
			days.keySet().removeIf(d ->
			{
				try
				{
					return Long.parseLong(d) <= day - DAYS_KEPT;
				}
				catch (NumberFormatException e)
				{
					return true;
				}
			});
		}
		return days.computeIfAbsent(String.valueOf(day), d -> new HashMap<>()).computeIfAbsent(bossName, b ->
		{
			DayCount count = new DayCount();
			count.first = millis;
			return count;
		});
	}

	/** Bosses whose History was already added to the day counts this session. */
	private final java.util.Set<String> daysFilled = new java.util.HashSet<>();

	/**
	 * Adds kills from History to the day counts when they're older than what the counts hold, so
	 * Today and Last 7 days include the kills from before On Rate counted days. Safe to repeat:
	 * a kill is only added when it's older than the earliest one already counted that day.
	 */
	private void fillDaysFromHistory(BossDefinition boss, long nowMillis)
	{
		if (!daysFilled.add(boss.getName()))
		{
			return;
		}
		BossProgress p = state.peek(boss.getName());
		if (p == null || p.kills.isEmpty())
		{
			return;
		}
		java.time.ZoneId zone = java.time.ZoneId.systemDefault();
		long today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay();
		Map<Long, List<KillEntry>> toAdd = new HashMap<>();
		for (KillEntry k : p.kills)
		{
			if (k.time <= 0)
			{
				continue;
			}
			long day = java.time.Instant.ofEpochMilli(k.time).atZone(zone).toLocalDate().toEpochDay();
			if (day > today || day <= today - DAYS_KEPT)
			{
				continue;
			}
			Map<String, DayCount> forDay = state.days().get(String.valueOf(day));
			DayCount count = forDay == null ? null : forDay.get(boss.getName());
			// no count that day yet, or this kill came before the first one it holds
			if (count == null || count.first != null && k.time < count.first)
			{
				toAdd.computeIfAbsent(day, d -> new ArrayList<>()).add(k);
			}
		}
		for (List<KillEntry> kills : toAdd.values())
		{
			long earliest = Long.MAX_VALUE;
			DayCount count = null;
			for (KillEntry k : kills)
			{
				count = dayCount(boss.getName(), k.time);
				count.kills++;
				count.expected += expectedFromKill(boss, k);
				for (KillEntry.DropEntry d : k.getDrops())
				{
					DropDefinition drop = dropNamed(boss, d.getName());
					if (drop != null && drop.countsTowardAny())
					{
						count.uniques++;
					}
				}
				earliest = Math.min(earliest, k.time);
			}
			count.first = count.first == null ? earliest : Math.min(count.first, earliest);
		}
	}

	/** Names of the uniques History has for this boss between these days (local epoch days). */
	private List<String> uniqueNames(String bossName, long fromDay, long toDay)
	{
		List<String> names = new ArrayList<>();
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = state.peek(bossName);
		if (boss == null || p == null)
		{
			return names;
		}
		java.time.ZoneId zone = java.time.ZoneId.systemDefault();
		for (KillEntry k : p.kills)
		{
			if (!k.isDateKnown())
			{
				continue;
			}
			long day = java.time.Instant.ofEpochMilli(k.time).atZone(zone).toLocalDate().toEpochDay();
			if (day < fromDay || day > toDay)
			{
				continue;
			}
			for (KillEntry.DropEntry d : k.getDrops())
			{
				DropDefinition drop = rowOfSaved(boss, d.getName());
				if (drop != null && drop.countsTowardAny())
				{
					names.add(d.getName());
				}
			}
		}
		return names;
	}

	/** Tests only: drops the day counts, as a version from before they existed would have. */
	public synchronized void forgetDaysForTest()
	{
		state.days().clear();
		daysFilled.clear();
	}

	/** True when the player left this item out of the calculations for this boss. */
	public synchronized boolean isLeftOut(String bossName, String itemName)
	{
		BossProgress p = state.peek(bossName);
		return p != null && p.leftOut != null && p.leftOut.contains(BossCatalog.normalize(itemName));
	}

	/**
	 * Leaves an item out of this boss's calculations (its verdicts, the account overview, the
	 * Items tab and dry milestones), or counts it again. The row itself stays visible.
	 */
	public synchronized void setLeftOut(String bossName, String itemName, boolean leftOut)
	{
		if (catalog.byName(bossName) == null)
		{
			return;
		}
		BossProgress p = state.get(bossName);
		if (p.leftOut == null)
		{
			p.leftOut = new java.util.TreeSet<>();
		}
		if (leftOut)
		{
			p.leftOut.add(BossCatalog.normalize(itemName));
		}
		else
		{
			p.leftOut.remove(BossCatalog.normalize(itemName));
		}
	}

	/** Copies of a shared item On Rate saw this boss drop. */
	private int poolSeenAt(String bossName, String key)
	{
		BossProgress p = state.peek(bossName);
		Integer n = p == null || p.poolSeen == null ? null : p.poolSeen.get(key);
		return n == null ? 0 : n;
	}

	private boolean poolOnce(String key)
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

	/** Where older copies of a shared item go when they came from other monsters, not a boss. */
	public static final String NOT_FROM_A_BOSS = "*not from a boss";

	/** True when the log also counts this shared item from monsters other than its bosses. */
	private boolean poolAlsoFrom(String key)
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
	private int elsewhereSeen(String key)
	{
		Integer n = state.poolElsewhereSeen().get(key);
		return n == null ? 0 : n;
	}

	/**
	 * Loot from a monster that isn't one of the item's bosses: for a shared item the log also
	 * counts from other monsters (draconic visage, dragon chainbody, uncut onyx), it is noted as
	 * not from a boss and kept in the log total. True when something was noted.
	 */
	private boolean recordElsewhere(BossDefinition boss, String itemName)
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
				state.poolElsewhereSeen().merge(e.getKey(), 1, Integer::sum);
				if (state.sharedClog().containsKey(e.getKey()))
				{
					state.sharedClog().merge(e.getKey(), 1, Integer::sum);
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
	public synchronized boolean onOtherLoot(String source, Collection<LootItem> items)
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
	private int olderCopies(String key)
	{
		Integer total = state.sharedClog().get(key);
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
	private boolean canSplit(String key)
	{
		return catalog.getPools().containsKey(key) && state.sharedClog().containsKey(key);
	}

	/** Where older copies of this item can go: its bosses, and "not from a boss" when other monsters drop it too. */
	private boolean validWhere(String key, String where)
	{
		return poolBosses(key).contains(where) || (NOT_FROM_A_BOSS.equals(where) && poolAlsoFrom(key));
	}

	/**
	 * The player's choice for a shared item while it still covers every copy, else null: a copy
	 * the log gained without On Rate seeing it makes the item shared again until they answer.
	 */
	private PoolSplit activeSplit(String key)
	{
		PoolSplit split = state.poolSplits().get(key);
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

	private List<String> poolBosses(String key)
	{
		List<String> names = new ArrayList<>();
		for (BossCatalog.PoolSource src : catalog.getPools().getOrDefault(key, Collections.emptyList()))
		{
			names.add(src.getBoss().getName());
		}
		return names;
	}

	/** See {@link #splitPool(String, Map)}, with every older copy from one place. */
	public synchronized boolean splitPool(String key, String olderFrom)
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
	public synchronized boolean splitPool(String key, Map<String, Integer> older)
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
		state.poolSplits().put(key, new PoolSplit(kept));
		return true;
	}

	/** Judges a shared item on all its bosses together again. */
	public synchronized void unsplitPool(String key)
	{
		state.poolSplits().remove(key);
	}

	/** The pool key of a shared item this boss's page judges on its own (the player split it), or null. */
	public synchronized String splitPoolOf(String bossName, String itemName)
	{
		BossDefinition boss = catalog.byName(bossName);
		String key = boss == null || itemName == null ? null : catalog.poolOf(boss, itemName);
		return key != null && activeSplit(key) != null ? key : null;
	}

	/** Whether a drop counts towards this boss's totals: a unique the player didn't leave out. */
	private boolean counts(BossDefinition boss, DropDefinition drop, BossProgress p)
	{
		return drop.countsTowardAny() && (p == null || p.leftOut == null || !p.leftOut.contains(BossCatalog.normalize(drop.getName())));
	}

	/** See {@link #counts(BossDefinition, DropDefinition, BossProgress)}, for the panel. */
	public synchronized boolean counts(BossDefinition boss, DropDefinition drop)
	{
		return counts(boss, drop, state.peek(boss.getName()));
	}

	private static DropDefinition dropNamed(BossDefinition boss, String name)
	{
		for (DropDefinition drop : boss.getDrops())
		{
			if (drop.getDisplayName().equals(name))
			{
				return drop;
			}
		}
		return null;
	}

	/**
	 * The row a saved unique belongs to: by the name the panel shows, or else by any of the row's
	 * item names, in any case ("Tome of fire (empty)" is saved under its own name).
	 */
	private static DropDefinition rowOfSaved(BossDefinition boss, String name)
	{
		DropDefinition drop = dropNamed(boss, name);
		return drop != null ? drop : findDrop(boss, name);
	}

	/** Adds a kill to today's count, with the uniques that one kill should give. */
	private void countKillToday(BossDefinition boss, KillEntry kill)
	{
		DayCount day = dayCount(boss.getName(), kill.time);
		day.kills++;
		Trip before = beforeThisKill;
		BossStats s = before != null && before.boss.equals(boss.getName()) ? stats(boss.getName(), settings) : null;
		// exactly what this kill added to the boss's expected; when the kill count jumped by
		// more than one (first kill after a break) only this one kill's own rates count
		day.expected += s != null && s.getKillCount() == before.startKc + 1
			? Math.max(0, s.getAnyRow().getExpected() - before.startExpected)
			: expectedFromKill(boss, kill);
	}

	/** Adds a unique to the count of the day it dropped. */
	private void countUniqueToday(BossDefinition boss, DropDefinition drop)
	{
		if (drop.countsTowardAny())
		{
			dayCount(boss.getName(), lastKillMillis > 0 ? lastKillMillis : System.currentTimeMillis()).uniques++;
		}
	}

	/**
	 * The uniques one kill should give: the rates of its raid mode, boss or delve level, and
	 * your share of a team kill (pets don't scale with the team).
	 */
	double expectedFromKill(BossDefinition boss, KillEntry kill)
	{
		return perKill(boss, kill, drop -> 1);
	}

	/**
	 * What one kill should give, each unique weighted (1 to count uniques, its GE price for
	 * GP): the rates of its raid mode, boss or delve level, and your share of a team kill.
	 */
	private double perKill(BossDefinition boss, KillEntry kill, java.util.function.ToDoubleFunction<DropDefinition> weight)
	{
		BossPart part = null;
		if (kill.part != null)
		{
			for (BossPart p : boss.getParts())
			{
				if (p.getName().equals(kill.part))
				{
					part = p;
				}
			}
		}
		double share = 1;
		if (boss.isDetectTeam() && kill.team != null && kill.team > 0)
		{
			share = 1.0 / kill.team;
		}
		else if (boss.getTeam() != null && kill.kc > 0)
		{
			// the share of this one kill under the solo/duo setting
			share = settings.contribution(boss.getTeam(), kill.kc) * kill.kc
				- (kill.kc > 1 ? settings.contribution(boss.getTeam(), kill.kc - 1) * (kill.kc - 1) : 0);
		}
		// a raid whose points On Rate read uses its own purple chance, like History shows
		RaidRecord record = null;
		double chance = Double.NaN;
		BossProgress progress = state.peek(boss.getName());
		if (part != null && part.getRaid() != null && kill.partKc != null && progress != null)
		{
			for (RaidRecord r : progress.raids)
			{
				if (r.kc == kill.partKc && part.getName().equals(r.part))
				{
					record = r;
				}
			}
			chance = record == null ? Double.NaN : recordChance(record, part, settings);
		}
		double expected = 0;
		for (DropDefinition drop : boss.getDrops())
		{
			if (!counts(boss, drop, progress))
			{
				continue;
			}
			double rate;
			if (boss.isDelve() && kill.level != null)
			{
				rate = DropMath.parseRate(drop.getLevelRate(kill.level));
			}
			else if (boss.getWaves() > 0)
			{
				rate = kill.level == null ? 0 : waveRate(drop, kill.level);
			}
			else if (part != null && !Double.isNaN(chance) && !drop.hasPartRates())
			{
				rate = recordDropExpected(boss, part, drop, record, chance);
			}
			else if (part != null)
			{
				rate = partRate(boss, part, drop, settings);
			}
			else
			{
				rate = effectiveRate(boss, drop, settings);
			}
			if (drop.getGuaranteedAt() != null && drop.getGuaranteedAt() == kill.kc)
			{
				// the kill where a player with none gets one: the typical player has none with
				// chance (1 - rate)^kc, so this kill gives rate + that, and no more pity after it
				rate += Math.pow(1 - rate, kill.kc);
			}
			// like the totals: a team shares a kill's drops, except the ones each player rolls
			expected += (drop.isNoShare() ? rate : rate * share) * weight.applyAsDouble(drop);
		}
		return expected;
	}

	/**
	 * GE value the current trip's own kills should give (their delve level, raid points, team),
	 * or NaN without a trip or when History lacks one of its kills.
	 */
	public synchronized double tripValue(java.util.function.ToLongFunction<String> prices)
	{
		if (trip == null)
		{
			return Double.NaN;
		}
		BossDefinition boss = catalog.byName(trip.boss);
		BossProgress p = boss == null ? null : state.peek(boss.getName());
		BossStats s = boss == null ? null : stats(boss.getName(), settings);
		if (p == null || s == null)
		{
			return Double.NaN;
		}
		int kills = Math.max(0, s.getKillCount() - trip.startKc);
		double sum = 0;
		int found = 0;
		for (int i = p.kills.size() - 1; i >= 0 && found < kills; i--)
		{
			KillEntry k = p.kills.get(i);
			if (k.time < trip.startMillis)
			{
				break;
			}
			sum += perKill(boss, k, drop -> Math.max(0, prices.applyAsLong(drop.getName())));
			found++;
		}
		return found == kills && kills > 0 ? sum : Double.NaN;
	}

	/**
	 * Kills, uniques and expected per boss over the last {@code days} days (1 = today), most
	 * kills first. The first entry is the total of all bosses (its boss is null).
	 */
	public synchronized List<BossDays> recentDays(int days, long nowMillis)
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			fillDaysFromHistory(boss, nowMillis);
		}
		long today = java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay();
		Map<String, DayCount> perBoss = new HashMap<>();
		DayCount total = new DayCount();
		for (Map.Entry<String, Map<String, DayCount>> d : state.days().entrySet())
		{
			long day;
			try
			{
				day = Long.parseLong(d.getKey());
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			if (day > today || day <= today - days)
			{
				continue;
			}
			for (Map.Entry<String, DayCount> b : d.getValue().entrySet())
			{
				DayCount sum = perBoss.computeIfAbsent(b.getKey(), k -> new DayCount());
				sum.kills += b.getValue().kills;
				sum.uniques += b.getValue().uniques;
				sum.expected += b.getValue().expected;
				total.kills += b.getValue().kills;
				total.uniques += b.getValue().uniques;
				total.expected += b.getValue().expected;
			}
		}
		List<BossDays> result = new ArrayList<>();
		result.add(new BossDays(null, total, new ArrayList<>()));
		perBoss.entrySet().stream()
			.filter(e -> e.getValue().kills > 0 || e.getValue().uniques > 0)
			.sorted((a, b) -> a.getValue().kills != b.getValue().kills ? b.getValue().kills - a.getValue().kills
				: a.getKey().compareTo(b.getKey()))
			.forEach(e -> result.add(new BossDays(e.getKey(), e.getValue(),
				uniqueNames(e.getKey(), today - days + 1, today))));
		return result;
	}

	/** Saves a kill for the History section (the oldest ones go once there are too many). */
	private KillEntry addKill(BossDefinition boss, BossProgress progress, long nowMillis)
	{
		KillEntry entry = new KillEntry();
		entry.kc = currentKc(boss);
		entry.time = nowMillis;
		progress.kills.add(entry);
		killsChanged.add(boss.getName());
		while (progress.kills.size() > MAX_KILLS)
		{
			progress.kills.remove(0);
		}
		return entry;
	}

	/** Adds a unique to the kill it came from (the latest kill at that kill count). */
	private void addDropToHistory(BossDefinition boss, BossProgress progress, int kc, KillEntry.DropEntry drop)
	{
		KillEntry latest = progress.kills.isEmpty() ? null : progress.kills.get(progress.kills.size() - 1);
		if (latest == null || latest.kc != kc || !latest.isDateKnown())
		{
			latest = addKill(boss, progress, lastKillMillis > 0 ? lastKillMillis : System.currentTimeMillis());
			latest.kc = kc;
		}
		if (latest.drops == null)
		{
			latest.drops = new ArrayList<>();
		}
		latest.drops.add(drop);
		killsChanged.add(boss.getName());
	}

	/** This boss's saved kills, newest first, with each raid's points and purple chance. */
	public synchronized List<HistoryRow> history(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = state.peek(bossName);
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
							chance = recordChance(match, part, settings);
						}
					}
				}
			}
			rows.add(new HistoryRow(k, match, chance));
		}
		return rows;
	}

	/** Kills saved for this boss (at most {@link #MAX_KILLS}). */
	public synchronized int killsSeen(String bossName)
	{
		BossProgress p = state.peek(bossName);
		return p == null ? 0 : p.kills.size();
	}

	/** The latest uniques across every boss, newest first. */
	public synchronized List<RecentDrop> recentDrops(int limit)
	{
		List<RecentDrop> all = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = state.peek(boss.getName());
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
	public synchronized boolean removeFromHistory(String bossName, KillEntry kill, KillEntry.DropEntry drop)
	{
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = state.peek(bossName);
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
		killsChanged.add(bossName);
		return true;
	}

	/**
	 * Why a drop added by hand can't be ranked in Luckiest drops / Longest waits, or null when it can
	 * (or isn't in History): its wait isn't known, or the boss's drop rate isn't.
	 */
	public synchronized String pastDropNotRankedReason(String bossName, String itemName, int kc)
	{
		return pastDropNotRankedReason(bossName, itemName, kc, null);
	}

	/** As above for a drop entered at a part of a combined page. */
	public synchronized String pastDropNotRankedReason(String bossName, String itemName, int kc, String partName)
	{
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = state.peek(bossName);
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
				DropDefinition drop = rowOfSaved(boss, d.getName());
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
	 * Drops across every boss whose wait is known exactly and was lucky (shorter than the drop rate
	 * gives on average) or dry (longer), best first: luckiest are ranked by the smallest multiple of
	 * the drop rate, dry ones by the largest. Drops added by hand count; undated ones are fine.
	 */
	public synchronized List<RecentDrop> dropsByLuck(boolean lucky, int limit)
	{
		List<RecentDrop> all = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = state.peek(boss.getName());
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
					DropDefinition drop = rowOfSaved(boss, d.getName());
					if (drop != null && counts(boss, drop, p))
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
	public synchronized boolean addPastDrop(String bossName, String itemName, int kc)
	{
		return addPastDrop(bossName, itemName, kc, false);
	}

	/**
	 * As {@link #addPastDrop(String, String, int)}, with {@code first} true when the player says
	 * this was their first copy: its wait is then its kill count, even when the collection log
	 * shows more copies. Ignored when an earlier copy is already listed (the gap since it is used).
	 */
	public synchronized boolean addPastDrop(String bossName, String itemName, int kc, boolean first)
	{
		return addPastDrop(bossName, itemName, kc, first, null);
	}

	/** Kills in the counting a past drop is entered in: the part's own count, or the page's when it has no parts. */
	private static boolean inCounting(KillEntry k, String part)
	{
		return part == null || part.equals(k.part) && k.partKc != null;
	}

	private static int countOf(KillEntry k, String part)
	{
		return part == null ? k.kc : k.partKc;
	}

	/** Most kills a past drop can be entered at: the page's kill count, or the part's own. 0 when unknown. */
	public synchronized int pastDropLimit(String bossName, String partName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return 0;
		}
		if (partName == null)
		{
			return boss.hasParts() ? 0 : currentKc(boss);
		}
		BossStats s = stats(bossName, settings);
		Integer n = s == null || s.getPartCounts() == null ? null : s.getPartCounts().get(partName);
		return n == null ? 0 : n;
	}

	/**
	 * The parts of a combined page a drop can be entered at: the ones the item drops from, that
	 * you have kills at. Empty for a page without parts.
	 */
	public synchronized List<String> pastDropParts(String bossName, String itemName)
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
			if (pastDropLimit(bossName, part.getName()) < 1)
			{
				continue;
			}
			fallback.add(part.getName());
			if (drop != null && partRate(boss, part, drop, settings) > 0)
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
	public synchronized boolean addPastDrop(String bossName, String itemName, int kc, boolean first, String partName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null || kc < 1)
		{
			return false;
		}
		BossPart part = null;
		for (BossPart candidate : boss.getParts())
		{
			if (candidate.getName().equals(partName))
			{
				part = candidate;
			}
		}
		if (boss.hasParts() != (part != null) || (partName != null && part == null)
			|| kc > pastDropLimit(bossName, partName))
		{
			return false;
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
			return false;
		}
		BossProgress progress = state.get(boss.getName());
		String shown = drop.getDisplayName();
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
				killsChanged.add(boss.getName());
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
					return false;
				}
			}
		}
		else if (index == 0 && progress.kills.size() >= MAX_KILLS)
		{
			return false;
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
			double rate = part == null ? rateOf(boss, drop) : partRate(boss, part, drop, settings);
			if (rate > 0)
			{
				entry.multiple = Math.round(entry.since * rate * 100) / 100.0;
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
		killsChanged.add(boss.getName());
		// the dry streak counts from the latest drop of any copy (page kills: not known for a part)
		Integer last = progress.lastDropKc.get(key);
		if (part == null && (last == null || kc > last))
		{
			progress.lastDropKc.put(key, kc);
			progress.milestone.remove(key);
		}
		return true;
	}

	/** Group bosses: how many kills On Rate saw per team size. Empty for other bosses. */
	public synchronized Map<Integer, Integer> teamKills(String bossName)
	{
		BossProgress p = state.peek(bossName);
		return p == null ? Collections.emptyMap() : new TreeMap<>(p.teamKills);
	}

	/** The share the kills from before team counting use at a group boss, or null while they count as solo. */
	public synchronized Double olderShare(String bossName)
	{
		BossProgress p = state.peek(bossName);
		return p == null ? null : p.olderShare;
	}

	/**
	 * Your share of the kills at a group boss: a kill with 4 players counts as a quarter.
	 * Kills On Rate didn't see (before it counted teams) count at the share it saw when it
	 * started counting, or as the boss's solo/duo setting says (Yama).
	 */
	private static double sharedKills(BossDefinition boss, BossProgress p, int kc, RaidSettings raids)
	{
		int seen = 0;
		double share = 0;
		for (Map.Entry<Integer, Integer> e : p.teamKills.entrySet())
		{
			seen += e.getValue();
			share += e.getValue() / (double) Math.max(1, e.getKey());
		}
		int older = Math.max(0, kc - seen);
		// kills from before On Rate counted teams: a team setting when the boss has one, else
		// the share On Rate saw when it started counting (a Zalcano or Nex regular wasn't solo)
		double olderShare = boss.getTeam() != null ? raids.contribution(boss.getTeam(), older)
			: p.olderShare != null ? p.olderShare : 1.0;
		return older * olderShare + share;
	}

	/**
	 * Group bosses without a team setting: the share to use for the kills from before On Rate
	 * counted teams, fixed once (the average of the kills it counted, or this kill's team).
	 * Set before the kill is counted, so the trip card and Recent don't see the older kills'
	 * share change as something this kill did.
	 */
	private void rememberOlderShare(BossDefinition boss, BossProgress p)
	{
		if (!boss.isDetectTeam() || boss.getTeam() != null || p.olderShare != null)
		{
			return;
		}
		if (!p.teamKills.isEmpty())
		{
			int seen = 0;
			double share = 0;
			for (Map.Entry<Integer, Integer> e : p.teamKills.entrySet())
			{
				seen += e.getValue();
				share += e.getValue() / (double) Math.max(1, e.getKey());
			}
			p.olderShare = share / seen;
			return;
		}
		int size = teamSize.getAsInt();
		if (size > 0)
		{
			p.olderShare = 1.0 / size;
		}
	}

	/**
	 * Average purple chance of the raids in this mode where On Rate had your points, or NaN.
	 * A good value for the setting that raids without points use.
	 */
	public synchronized double averagePurpleChance(String bossName, String part)
	{
		double sum = 0;
		int n = 0;
		for (RaidRecord r : raidRecords(bossName))
		{
			double chance = purpleChance(r);
			if ((part == null ? bossName : part).equals(r.part) && !Double.isNaN(chance))
			{
				sum += chance;
				n++;
			}
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	/** Settings used for streaks and milestone messages (raid chances, team shares). */
	public synchronized void setSettings(RaidSettings settings)
	{
		this.settings = settings;
	}

	/** Chat messages produced since the last call (drops and milestones). */
	public synchronized List<Note> drainNotes()
	{
		List<Note> notes = new ArrayList<>(outbox);
		outbox.clear();
		return notes;
	}

	public synchronized void setState(TrackerState state)
	{
		this.state = state == null ? new TrackerState() : state;
		unreadablePages.clear();
		headerUnreadable = false;
		killsChanged.clear();
		raidsChanged.clear();
		// another account: nothing of the last one's session carries over
		daysFilled.clear();
		outbox.clear();
		pendingWave = null;
		lastPart = null;
		lastKillMillis = 0;
		lastRaidMillis = 0;
		latestToaMillis = 0;
		pendingPartyMillis = 0;
		lastBoss = null;
		trip = null;
		beforeThisKill = null;
		pendingRecord = null;
		lastRaidBoss = null;
		latestToa = null;
		pendingParty = null;
		// raids saved inside the main data by earlier versions move to their own key
		if (this.state.bosses != null)
		{
			for (Map.Entry<String, BossProgress> e : this.state.bosses.entrySet())
			{
				BossProgress p = e.getValue();
				p.ensureMaps();
				if (p.savedRaids != null && !p.savedRaids.isEmpty())
				{
					p.raids.addAll(p.savedRaids);
					raidsChanged.add(e.getKey());
				}
				p.savedRaids = null;
			}
		}
	}

	public synchronized TrackerState getState()
	{
		return state;
	}

	/** Name of the boss most recently killed this session, or null. */
	public synchronized String getLastBoss()
	{
		return lastBoss;
	}

	/**
	 * Handles a game chat message. Returns the boss whose numbers changed, or null.
	 */
	public synchronized String onChatMessage(String message, long nowMillis)
	{
		if (message == null)
		{
			return null;
		}

		Matcher m = KILLCOUNT_PATTERN.matcher(message);
		if (m.find())
		{
			String pre = m.group("pre");
			String post = m.group("post");
			if ((pre == null || pre.isEmpty()) && (post == null || post.isEmpty()))
			{
				return null;
			}
			String counter = m.group("boss").replaceAll("<[^>]*>", "").trim();
			BossDefinition boss = catalog.byKcName(counter);
			if (boss == null)
			{
				return null;
			}
			int kc = Integer.parseInt(m.group("kc").replace(",", ""));
			BossProgress progress = state.get(boss.getName());
			if (boss.getWaves() > 0)
			{
				// the kill count is the last wave beaten; the run counts when its loot is claimed
				progress.chatKc.put(BossCatalog.normalize(counter), kc);
				pendingWave = boss.getWaves();
				return boss.getName();
			}
			Integer known = progress.chatKc.get(BossCatalog.normalize(counter));
			if (known == null || kc > known)
			{
				rememberOlderShare(boss, progress);
				beforeKill(boss, nowMillis);
			}
			Integer previous = progress.chatKc.put(BossCatalog.normalize(counter), kc);
			Integer team = null;
			if (boss.isDetectTeam() && (previous == null || kc > previous))
			{
				int size = teamSize.getAsInt();
				if (size > 0)
				{
					progress.teamKills.merge(size, 1, Integer::sum);
					team = size;
				}
			}
			if (previous == null || kc > previous)
			{
				KillEntry entry = addKill(boss, progress, nowMillis);
				if (boss.hasParts())
				{
					// on a combined page each mode or boss has its own count
					entry.partKc = kc;
					entry.part = partForName(boss, counter, true);
				}
				entry.team = team;
				countKillToday(boss, entry);
			}
			markKill(boss, nowMillis);
			afterKill(boss, nowMillis);
			lastPart = partForName(boss, counter, true);
			startTracking(boss);
			onRaidCompletion(boss, lastPart, kc, nowMillis);
			checkMilestones(boss);
			return boss.getName();
		}

		String plain = message.replaceAll("<[^>]*>", "");
		Matcher wave = WAVE_DONE.matcher(plain);
		if (wave.find())
		{
			// the wave a claim now would be for
			pendingWave = Integer.parseInt(wave.group(1));
			return null;
		}
		Matcher delve = DELVE_LEVEL.matcher(plain);
		if (plain.contains("duration:") && delve.find())
		{
			String level = delve.group(1);
			return onDelveLevel(level.endsWith("+") ? BossDefinition.DELVE_LEVELS : Integer.parseInt(level), nowMillis);
		}

		Matcher clog = NEW_CLOG_ITEM.matcher(plain);
		if (clog.find())
		{
			return onNewClogItem(clog.group(1).trim(), nowMillis);
		}

		for (String petMessage : PET_MESSAGES)
		{
			if (message.contains(petMessage))
			{
				return onPet(nowMillis);
			}
		}
		return null;
	}

	/**
	 * Data read at the end of a raid: points (CoX, estimated for ToA) or the ToB board.
	 * The completion count message tells which mode it was; it may come just before or
	 * just after. Returns the boss whose numbers changed, or null.
	 */
	public synchronized String onRaidRecord(RaidRecord record, long nowMillis)
	{
		if (record == null || record.family == null)
		{
			return null;
		}
		record.savedMillis = nowMillis;
		if ("toa".equals(record.family))
		{
			latestToa = record;
			latestToaMillis = nowMillis;
			applyPendingParty(record, nowMillis);
		}
		if (lastRaidBoss != null && nowMillis - lastRaidMillis <= matchWindow(record.family)
			&& raidFamily(lastRaidBoss, lastRaidPart).startsWith(record.family))
		{
			String boss = lastRaidBoss;
			saveRecord(boss, lastRaidPart, lastRaidKc, record);
			lastRaidBoss = null;
			return boss;
		}
		pendingRecord = record;
		pendingRecordMillis = nowMillis;
		return null;
	}

	/**
	 * Your exact points for the latest Tombs of Amascut raid, sent by the game a moment after
	 * the raid was saved: they replace On Rate's estimate in it. Returns the boss, or null.
	 */
	public synchronized String onToaPoints(int personalPoints, long nowMillis)
	{
		if (personalPoints <= 0 || latestToa == null || nowMillis - latestToaMillis > PARTY_MATCH_MILLIS)
		{
			return null;
		}
		String toaBoss = toaBossName();
		double before = toaBoss == null ? Double.NaN : anyExpected(toaBoss);
		latestToa.personalPoints = personalPoints;
		// the estimate's extra points (counted outside a RuneLite party) are part of the exact number
		latestToa.partyPoints = 0;
		latestToa.estimated = false;
		if (toaBoss != null)
		{
			raidsChanged.add(toaBoss);
			shiftToday(toaBoss, anyExpected(toaBoss) - before);
		}
		return toaBoss;
	}

	private String toaBossName()
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			if ("toa".equals(boss.getRaid()))
			{
				return boss.getName();
			}
		}
		return null;
	}

	/**
	 * Team and personal points the Tombs of Amascut plugin sends at the end of a raid.
	 * They replace On Rate's own estimate for the latest ToA raid.
	 */
	public synchronized String onPartyPoints(int personalPoints, int teamPoints, long nowMillis)
	{
		if (personalPoints <= 0 || teamPoints <= 0)
		{
			return null;
		}
		if (latestToa != null && nowMillis - latestToaMillis <= PARTY_MATCH_MILLIS)
		{
			String toaBoss = null;
			for (BossDefinition boss : catalog.getBosses())
			{
				if ("toa".equals(boss.getRaid()))
				{
					toaBoss = boss.getName();
				}
			}
			double before = toaBoss == null ? Double.NaN : anyExpected(toaBoss);
			latestToa.personalPoints = personalPoints;
			latestToa.teamPoints = teamPoints;
			latestToa = null;
			if (toaBoss != null)
			{
				raidsChanged.add(toaBoss);
				shiftToday(toaBoss, anyExpected(toaBoss) - before);
			}
			return toaBoss;
		}
		pendingParty = new int[]{personalPoints, teamPoints};
		pendingPartyMillis = nowMillis;
		return null;
	}

	private void applyPendingParty(RaidRecord record, long nowMillis)
	{
		if (pendingParty != null && nowMillis - pendingPartyMillis <= PARTY_MATCH_MILLIS)
		{
			record.personalPoints = pendingParty[0];
			record.teamPoints = pendingParty[1];
			latestToa = null;
		}
		pendingParty = null;
	}

	private static long matchWindow(String family)
	{
		return "tob".equals(family) ? TOB_MATCH_MILLIS : RAID_MATCH_MILLIS;
	}

	private void onRaidCompletion(BossDefinition boss, String part, int kc, long nowMillis)
	{
		String family = raidFamily(boss.getName(), part);
		if (family.isEmpty())
		{
			return;
		}
		if (pendingRecord != null && nowMillis - pendingRecordMillis <= matchWindow(pendingRecord.family)
			&& family.startsWith(pendingRecord.family))
		{
			saveRecord(boss.getName(), part, kc, pendingRecord);
			pendingRecord = null;
			lastRaidBoss = null;
			return;
		}
		pendingRecord = null;
		lastRaidBoss = boss.getName();
		lastRaidPart = part;
		lastRaidKc = kc;
		lastRaidMillis = nowMillis;
	}

	/** The raid setting key of a boss part ("coxCm"), or "" when it isn't a raid. */
	private String raidFamily(String bossName, String partName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null || boss.getRaid() == null)
		{
			return "";
		}
		if (partName != null && boss.hasParts())
		{
			for (BossPart part : boss.getParts())
			{
				if (part.getName().equals(partName))
				{
					return part.getRaid() == null || "none".equals(part.getRaid()) ? "" : part.getRaid();
				}
			}
		}
		return boss.hasParts() ? "" : boss.getRaid();
	}

	private void saveRecord(String bossName, String part, int kc, RaidRecord record)
	{
		double before = anyExpected(bossName);
		record.part = part == null ? bossName : part;
		record.kc = kc;
		List<RaidRecord> raids = state.get(bossName).raids;
		raids.add(record);
		raidsChanged.add(bossName);
		while (raids.size() > MAX_RAID_RECORDS)
		{
			raids.remove(0);
		}
		shiftToday(bossName, anyExpected(bossName) - before);
	}

	/** Uniques expected so far at this boss, or NaN when it has no numbers. */
	private double anyExpected(String bossName)
	{
		BossStats s = stats(bossName, settings);
		return s == null ? Double.NaN : s.getAnyRow().getExpected();
	}

	/**
	 * A raid is counted at the purple chance from the settings first; when its points come in a
	 * moment later, this moves the day's expected along, so Recent uses your points too.
	 */
	private void shiftToday(String bossName, double change)
	{
		if (Double.isNaN(change) || Math.abs(change) < 1e-12)
		{
			return;
		}
		BossProgress progress = state.peek(bossName);
		KillEntry latest = progress == null || progress.kills.isEmpty() ? null : progress.kills.get(progress.kills.size() - 1);
		DayCount day = dayCount(bossName, latest == null ? System.currentTimeMillis() : latest.time);
		day.expected = Math.max(0, day.expected + change);
	}

	/** Most points the luck graph keeps (about three years of daily play). */
	private static final int MAX_HISTORY = 1000;

	/**
	 * Saves today's account luck for the graph. One point per day: a later call on the same
	 * day replaces it. Returns true when something was stored.
	 */
	public synchronized boolean recordHistory(long day, RaidSettings raids)
	{
		AccountStats account = accountStats(raids);
		RowStats total = account.getTotal();
		if (total.getExpected() <= 0 || Double.isNaN(total.getLuck()))
		{
			return false;
		}
		List<HistoryPoint> history = state.history();
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
	public synchronized List<HistoryPoint> history()
	{
		return new ArrayList<>(state.history());
	}

	/** Your expected uniques for this raid from its points, or NaN when they are unknown. */
	public static double purpleChance(RaidRecord record)
	{
		if (record == null || record.family == null)
		{
			return Double.NaN;
		}
		switch (record.family)
		{
			case "cox":
				return RaidChance.cox(record.personalPoints, record.teamPoints);
			case "toa":
				return RaidChance.toa(record.personalPoints, record.partyPoints, record.teamPoints,
					record.teamSize, record.raidLevel);
			default:
				return Double.NaN;
		}
	}

	/**
	 * Share of a purple that is this item at a raid level (ToA's weights shift towards the
	 * rarer items at high raid levels). Falls back to {@code normal} without level weights.
	 */
	private static double levelShare(BossDefinition boss, DropDefinition drop, int raidLevel, double normal)
	{
		boolean any = false;
		double total = 0;
		for (DropDefinition d : boss.getDrops())
		{
			any |= d.hasLevelWeights();
			Double w = d.getWeightAtLevel(raidLevel);
			if (w != null)
			{
				total += w;
			}
		}
		Double w = drop.getWeightAtLevel(raidLevel);
		if (!any || w == null || total <= 0)
		{
			return normal;
		}
		return w / total;
	}

	/**
	 * Your purple chance for one saved raid: from its points (CoX, ToA), or for ToB the
	 * team chance from the settings split over that raid's real team size. NaN when
	 * neither is known, so the setting is used.
	 */
	static double recordChance(RaidRecord record, BossPart part, RaidSettings raids)
	{
		double chance = purpleChance(record);
		if (!Double.isNaN(chance))
		{
			return chance;
		}
		String key = part.getRaid();
		if ("tob".equals(record.family) && record.teamSize > 0 && key != null && !"none".equals(key))
		{
			return raids.uniqueChance(key) * Math.max(1, raids.teamSize(key)) / record.teamSize;
		}
		return Double.NaN;
	}

	/**
	 * What one saved raid adds to the expected count of a drop: its purple chance times the
	 * item's share at that raid level. The Tombs of Amascut pet has its own formula.
	 */
	private static double recordDropExpected(BossDefinition boss, BossPart part, DropDefinition drop, RaidRecord r,
		double chance)
	{
		if (drop.isPet() && "toa".equals(r.family))
		{
			double pet = RaidChance.toaPet(r.personalPoints, r.partyPoints, r.teamPoints, r.teamSize, r.raidLevel);
			if (!Double.isNaN(pet))
			{
				return pet;
			}
		}
		double share = partRate(boss, part, drop, key -> 1.0);
		double itemShare = r.raidLevel >= 0 ? levelShare(boss, drop, r.raidLevel, share) : share;
		return chance * itemShare * RaidChance.toaLevelFactor(r.raidLevel, drop.getMinRaidLevel());
	}

	/** Completions of this raid mode whose purple chance came from your points. */
	public synchronized int measuredRaids(String bossName, String part)
	{
		int n = 0;
		for (RaidRecord r : raidRecords(bossName))
		{
			if ((part == null ? bossName : part).equals(r.part) && !Double.isNaN(purpleChance(r)))
			{
				n++;
			}
		}
		return n;
	}

	/** Every raid saved for this boss, oldest first. */
	public synchronized List<RaidRecord> raidRecords(String bossName)
	{
		BossProgress progress = state.peek(bossName);
		return progress == null ? new ArrayList<>() : new ArrayList<>(progress.raids);
	}

	private BossDefinition delveBoss()
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			if (boss.isDelve())
			{
				return boss;
			}
		}
		return null;
	}

	private String onDelveLevel(int level, long nowMillis)
	{
		BossDefinition boss = delveBoss();
		if (boss == null || level < 1 || level > BossDefinition.DELVE_LEVELS)
		{
			return null;
		}
		BossProgress progress = state.get(boss.getName());
		beforeKill(boss, nowMillis);
		progress.levelKc.merge(level, 1, Integer::sum);
		KillEntry entry = addKill(boss, progress, nowMillis);
		entry.level = level;
		countKillToday(boss, entry);
		markKill(boss, nowMillis);
		afterKill(boss, nowMillis);
		startTracking(boss);
		checkMilestones(boss);
		return boss.getName();
	}

	/**
	 * Handles the Doom scoreboard, which lists completions per delve level
	 * (key 9 = "8+"). These are full totals, so they replace what we counted.
	 */
	public synchronized String onDelveScoreboard(Map<Integer, Integer> levels)
	{
		BossDefinition boss = delveBoss();
		if (boss == null || levels.isEmpty())
		{
			return null;
		}
		BossProgress progress = state.get(boss.getName());
		progress.levelsSynced = true;
		for (Map.Entry<Integer, Integer> e : levels.entrySet())
		{
			if (e.getKey() >= 1 && e.getKey() <= BossDefinition.DELVE_LEVELS)
			{
				progress.levelKc.put(e.getKey(), e.getValue());
			}
		}
		startTracking(boss);
		return boss.getName();
	}

	/**
	 * For bosses whose loot the loot tracker can't see (Gauntlet, Jad, Zuk), a first-time
	 * collection log message is the only sign of a drop. Pets are skipped because the pet
	 * message already counts them.
	 */
	private String onNewClogItem(String itemName, long nowMillis)
	{
		if (lastBoss == null || nowMillis - lastKillMillis > PET_WINDOW_MILLIS)
		{
			return null;
		}
		BossDefinition boss = catalog.byName(lastBoss);
		if (boss == null || !boss.getLootNames().isEmpty())
		{
			return null;
		}
		DropDefinition drop = findDrop(boss, itemName);
		if (drop == null || drop.isPet())
		{
			return null;
		}
		recordDrop(boss, drop, itemName);
		state.get(boss.getName()).received.merge(BossCatalog.normalize(itemName), 1, Integer::sum);
		return boss.getName();
	}

	private String onPet(long nowMillis)
	{
		if (lastBoss == null || nowMillis - lastKillMillis > PET_WINDOW_MILLIS)
		{
			return null;
		}
		BossDefinition boss = catalog.byName(lastBoss);
		if (boss == null)
		{
			return null;
		}
		DropDefinition pet = null;
		for (DropDefinition drop : boss.getDrops())
		{
			if (!drop.isPet())
			{
				continue;
			}
			// On a combined page, each boss has its own pet: take the one of the part killed last.
			if (pet == null || (lastPart != null && drop.getPartRate(lastPart) != null))
			{
				pet = drop;
			}
		}
		if (pet == null)
		{
			return null;
		}
		recordDrop(boss, pet, pet.getName());
		state.get(boss.getName()).received.merge(BossCatalog.normalize(pet.getName()), 1, Integer::sum);
		return boss.getName();
	}

	private int currentKc(BossDefinition boss)
	{
		BossStats s = stats(boss.getName(), settings);
		return s == null ? 0 : s.getKillCount();
	}

	/** Remembers the kill count at which On Rate started following this boss. */
	private void startTracking(BossDefinition boss)
	{
		BossProgress progress = state.get(boss.getName());
		if (progress.trackingStartKc == null)
		{
			progress.trackingStartKc = currentKc(boss);
		}
	}

	/** Word for what is counted at this boss: kills, raids, chests or levels. */
	/** "kills", "raids", "delves" (Doom) or "chests" (Barrows, Moons): what one completion is called. */
	public static String unit(BossDefinition boss)
	{
		if (boss.getRollsPerKill() > 0)
		{
			return "rolls";
		}
		if (boss.getWaves() > 0)
		{
			return "runs";
		}
		if (boss.getRaid() != null)
		{
			return "raids";
		}
		if (boss.isDelve())
		{
			return "delves";
		}
		for (String kc : boss.getKcNames())
		{
			if (kc.toLowerCase(Locale.ROOT).contains("chest"))
			{
				return "chests";
			}
		}
		return "kills";
	}

	/** {@link #unit} for one: "kill", "raid", "delve" or "chest". */
	/** "1 kill", "5 kills": the unit of a boss for a count. */
	private static String units(BossDefinition boss, int count)
	{
		return count == 1 ? unitOne(boss) : unit(boss);
	}

	public static String unitOne(BossDefinition boss)
	{
		String units = unit(boss);
		return units.substring(0, units.length() - 1);
	}

	/**
	 * A unique dropped: remember the kill count, and say how many kills it took.
	 * Call before the received count goes up.
	 */
	private void recordDrop(BossDefinition boss, DropDefinition drop, String itemName)
	{
		BossProgress progress = state.get(boss.getName());
		String key = BossCatalog.normalize(itemName);
		int kc = currentKc(boss);
		Integer previous = progress.lastDropKc.get(key);
		Integer before = progress.received.get(key);
		double rate = rateOf(boss, drop);

		String unit = unit(boss);
		String name = drop.getDisplayName().equals(drop.getName()) ? itemName : drop.getDisplayName();
		// "first" is only certain when the collection log told us the history of this item
		boolean historyKnown = progress.clogSynced && !catalog.isClogShared(key);
		String text = null;
		if (previous != null && kc > previous)
		{
			int kills = kc - previous;
			text = String.format("%s after %,d %s (%s the drop rate).", name, kills, units(boss, kills), multiple(kills * rate));
		}
		else if (historyKnown && (before == null || before == 0) && kc > 0)
		{
			text = String.format("Your first %s, after %,d %s (%s the drop rate).", name, kc, units(boss, kc), multiple(kc * rate));
		}
		else if (progress.trackingStartKc != null && kc > progress.trackingStartKc)
		{
			int kills = kc - progress.trackingStartKc;
			text = String.format("%s after at least %,d %s.", name, kills, units(boss, kills));
		}
		if (text != null && rate > 0)
		{
			outbox.add(new Note(Note.Kind.DROP, text));
		}
		// records: the kills this drop took, when known exactly
		Integer took = previous != null && kc > previous ? Integer.valueOf(kc - previous)
			: historyKnown && (before == null || before == 0) && kc > 0 ? Integer.valueOf(kc) : null;
		if (took != null)
		{
			if (progress.fastestGap == null)
			{
				progress.fastestGap = new HashMap<>();
				progress.longestGap = new HashMap<>();
			}
			progress.fastestGap.merge(key, took, Math::min);
			progress.longestGap.merge(key, took, Math::max);
		}
		KillEntry.DropEntry entry = new KillEntry.DropEntry();
		entry.name = drop.getDisplayName().equals(drop.getName()) ? itemName : drop.getDisplayName();
		entry.pet = drop.isPet() ? Boolean.TRUE : null;
		if (previous != null && kc > previous)
		{
			entry.since = kc - previous;
		}
		else if (historyKnown && (before == null || before == 0) && kc > 0)
		{
			entry.since = kc;
			entry.first = Boolean.TRUE;
		}
		else if (progress.trackingStartKc != null && kc > progress.trackingStartKc)
		{
			// an earlier one came before On Rate: only a minimum is known
			entry.since = kc - progress.trackingStartKc;
			entry.atLeast = Boolean.TRUE;
		}
		if (entry.since != null && !entry.isAtLeast() && rate > 0)
		{
			// two decimals is all the panel shows, and it keeps the saved history short
			entry.multiple = Math.round(entry.since * rate * 100) / 100.0;
		}
		addDropToHistory(boss, progress, kc, entry);
		countUniqueToday(boss, drop);
		progress.lastDropKc.put(key, kc);
		progress.milestone.remove(key);
	}

	/** Average per-kill rate of a drop at this boss so far (works for raids, parts and delves). */
	private double rateOf(BossDefinition boss, DropDefinition drop)
	{
		BossStats s = stats(boss.getName(), settings);
		if (s == null)
		{
			return 0;
		}
		for (RowStats row : s.getRows())
		{
			if (row.getIconItem() != null && row.getIconItem().equals(drop.getName()))
			{
				return row.getRate();
			}
		}
		return 0;
	}

	private static String multiple(double m)
	{
		if (m < 0.1)
		{
			return String.format("%.2fx", m);
		}
		return m < 10 ? String.format("%.1fx", m) : String.format("%.0fx", m);
	}

	/**
	 * After a kill: announce items that just passed 2x, 3x, 5x or 10x the drop rate
	 * without dropping. Several at once become one message. A big jump in kill count
	 * (first sync, first message after installing) only catches up quietly.
	 */
	private void checkMilestones(BossDefinition boss)
	{
		BossProgress progress = state.get(boss.getName());
		BossStats s = stats(boss.getName(), settings);
		if (s == null)
		{
			return;
		}
		int kc = s.getKillCount();
		Integer lastChecked = progress.lastCheckedKc;
		boolean quiet = lastChecked == null || kc <= lastChecked || kc - lastChecked > MAX_QUIET_JUMP;
		progress.lastCheckedKc = kc;

		List<String> names = new ArrayList<>();
		List<Integer> levels = new ArrayList<>();
		for (int i = 0; i < s.getRows().size(); i++)
		{
			RowStats row = s.getRows().get(i);
			DropDefinition drop = s.getDrops().get(i);
			if (!counts(boss, drop, progress) || row.getStreak() == null || row.getRate() <= 0)
			{
				continue;
			}
			double m = row.getStreakMultiple();
			int level = -1;
			for (int t = 0; t < MILESTONES.length; t++)
			{
				if (m >= MILESTONES[t])
				{
					level = t;
				}
			}
			String key = BossCatalog.normalize(drop.getName());
			int announced = progress.milestone.getOrDefault(key, -1);
			if (level > announced)
			{
				progress.milestone.put(key, level);
				if (!quiet)
				{
					names.add(row.getLabel());
					levels.add(MILESTONES[level]);
				}
			}
		}
		if (names.isEmpty())
		{
			return;
		}
		String unit = unit(boss);
		if (names.size() == 1)
		{
			RowStats row = rowFor(s, names.get(0));
			String streak = row.isStreakExact()
				? String.format("%,d %s without one", row.getStreak(), units(boss, row.getStreak()))
				: String.format("at least %,d %s since your last one", row.getStreak(), units(boss, row.getStreak()));
			outbox.add(new Note(Note.Kind.MILESTONE, String.format("%s: %s, %dx the drop rate.",
				names.get(0), streak, levels.get(0))));
			return;
		}
		int top = levels.get(0);
		boolean same = levels.stream().allMatch(l -> l == top);
		StringBuilder list = new StringBuilder();
		int shown = Math.min(3, names.size());
		for (int i = 0; i < shown; i++)
		{
			if (i > 0)
			{
				list.append(i == shown - 1 && names.size() == shown ? " and " : ", ");
			}
			list.append(names.get(i));
			if (!same)
			{
				list.append(" (").append(levels.get(i)).append("x)");
			}
		}
		if (names.size() > shown)
		{
			list.append(" and ").append(names.size() - shown).append(" more");
		}
		outbox.add(new Note(Note.Kind.MILESTONE, same
			? String.format("%s: %d items passed %dx the drop rate dry: %s.", boss.getName(), names.size(), top, list)
			: String.format("%s: %d items passed a dry milestone: %s.", boss.getName(), names.size(), list)));
	}

	private static RowStats rowFor(BossStats s, String label)
	{
		for (RowStats row : s.getRows())
		{
			if (row.getLabel().equals(label))
			{
				return row;
			}
		}
		return null;
	}

	/**
	 * The part of a combined page that a chat counter or loot name belongs to, or null.
	 * When several parts match (e.g. "Corrupted Gauntlet" also contains "Gauntlet"),
	 * the longest match wins.
	 */
	private static String partForName(BossDefinition boss, String text, boolean kcNames)
	{
		String t = BossCatalog.normalize(text);
		// Mode keywords ("expert", "(cm)", "hard mode"...) are the most reliable sign.
		for (BossPart part : boss.getParts())
		{
			for (String keyword : part.getKeywords())
			{
				if (t.contains(BossCatalog.normalize(keyword)))
				{
					return part.getName();
				}
			}
		}
		String best = null;
		int bestLength = -1;
		for (BossPart part : boss.getParts())
		{
			List<String> names = new ArrayList<>(kcNames ? part.getKcNames() : part.getLootNames());
			names.add(part.getName());
			for (String name : names)
			{
				String n = BossCatalog.normalize(name);
				if (t.contains(n) && n.length() > bestLength)
				{
					best = part.getName();
					bestLength = n.length();
				}
			}
		}
		return best;
	}

	/** Kill count per part of a combined page (chat count or collection log count, whichever is higher). */
	private static Map<String, Integer> partKills(BossDefinition boss, BossProgress progress)
	{
		Map<String, Integer> result = new LinkedHashMap<>();
		for (BossPart part : boss.getParts())
		{
			int kc = 0;
			if (progress != null)
			{
				for (String name : part.getKcNames())
				{
					Integer v = progress.chatKc.get(BossCatalog.normalize(name));
					if (v != null)
					{
						kc = Math.max(kc, v);
					}
				}
				Integer clog = progress.clogKc.get(BossCatalog.normalize(part.getName()));
				if (clog != null)
				{
					kc = Math.max(kc, clog);
				}
			}
			result.put(part.getName(), kc);
		}
		return result;
	}

	/**
	 * Handles a loot tracker event. Returns the boss whose numbers changed, or null.
	 */
	public synchronized String onLoot(String source, int amount, Collection<LootItem> items, long nowMillis)
	{
		BossDefinition boss = catalog.byLootName(source);
		if (boss == null)
		{
			return null;
		}
		BossProgress progress = state.get(boss.getName());
		boolean minion = catalog.isMinionLoot(source);
		String titan = BossCatalog.normalize(source).contains("branda") ? "Branda"
			: BossCatalog.normalize(source).contains("eldric") ? "Eldric" : null;
		KillEntry latest = progress.kills.isEmpty() ? null : progress.kills.get(progress.kills.size() - 1);
		if (titan != null && latest != null && latest.kc == currentKc(boss))
		{
			latest.titan = titan;
			killsChanged.add(boss.getName());
		}
		if (boss.getWaves() > 0)
		{
			countWaveClaim(boss, progress, items, nowMillis);
		}
		else if (!minion && boss.getRollsPerKill() <= 0)
		{
			// (reward rolls are counted from the permits, see onRewardRolls)
			if (boss.getKcNames().isEmpty())
			{
				beforeKill(boss, nowMillis);
			}
			// a bodyguard's loot counts for its general, but it isn't a kill of the general
			progress.lootKills += Math.max(1, amount);
			if (boss.getKcNames().isEmpty())
			{
				// no kill count message for this boss: the loot is the kill
				afterKill(boss, nowMillis);
			}
		}
		if (boss.hasParts())
		{
			lastPart = partForName(boss, source, false);
		}

		for (LootItem item : items)
		{
			DropDefinition drop = findDrop(boss, item.name);
			if (drop != null && item.quantity > 0)
			{
				// One stack is one drop; the collection log counts quantity, but for
				// the uniques we track those are the same thing.
				recordDrop(boss, drop, item.name);
				progress.received.merge(BossCatalog.normalize(item.name), 1, Integer::sum);
			}
			else if (item.quantity > 0)
			{
				// An item several bosses drop: once the log total is known, keep it up to date.
				String pool = catalog.poolOf(boss, item.name);
				if (pool != null && state.sharedClog().containsKey(pool))
				{
					state.sharedClog().merge(pool, 1, Integer::sum);
				}
				if (pool == null)
				{
					// a shared item this boss isn't one of the bosses of (onyx from Zalcano)
					recordElsewhere(boss, item.name);
				}
				else
				{
					// which boss it came from, for judging it per boss (see PoolSplit)
					if (progress.poolSeen == null)
					{
						progress.poolSeen = new HashMap<>();
					}
					progress.poolSeen.merge(pool, 1, Integer::sum);
				}
			}
		}
		markKill(boss, nowMillis);
		startTracking(boss);
		return boss.getName();
	}

	/** Fortis Colosseum: the wave a claim right now would be for, from the last wave message. */
	private Integer pendingWave;

	/**
	 * A drop's chance over a whole run claimed at {@code wave}: its chance on each wave up to
	 * that one, added up (at most one unique per wave, so this is its expected count).
	 */
	public static double waveRate(DropDefinition drop, int wave)
	{
		double sum = 0;
		for (int w = 1; w <= wave; w++)
		{
			sum += DropMath.parseRate(drop.getLevelRate(w));
		}
		return sum;
	}

	/** Tests and the plugin: the wave the next claim is for (a wave message was seen). */
	public synchronized void onWaveCompleted(int wave)
	{
		pendingWave = wave;
	}

	/**
	 * A claimed run: counted at the wave it reached (the last wave when the guaranteed reward is
	 * in it, else the last "Wave N completed" message). Without a known wave the run isn't
	 * counted, only its drops.
	 */
	private void countWaveClaim(BossDefinition boss, BossProgress progress, Collection<LootItem> items, long nowMillis)
	{
		Integer wave = pendingWave;
		for (LootItem item : items)
		{
			if (BossCatalog.normalize(item.name).startsWith(LAST_WAVE_REWARD))
			{
				wave = boss.getWaves();
			}
		}
		pendingWave = null;
		if (wave == null || wave < 1)
		{
			return;
		}
		wave = Math.min(wave, boss.getWaves());
		beforeKill(boss, nowMillis);
		if (progress.waveClaims == null)
		{
			progress.waveClaims = new TreeMap<>();
		}
		progress.waveClaims.merge(wave, 1, Integer::sum);
		KillEntry entry = addKill(boss, progress, nowMillis);
		entry.level = wave;
		countKillToday(boss, entry);
		markKill(boss, nowMillis);
		afterKill(boss, nowMillis);
	}

	/** True when this shared item is a pet (shared items live in the pools, not the bosses' drops). */
	private boolean isPetItem(String itemName)
	{
		for (List<BossCatalog.PoolSource> sources : catalog.getPools().values())
		{
			for (BossCatalog.PoolSource src : sources)
			{
				if (src.getDrop().isPet() && src.getDrop().getName().equals(itemName))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** This boss when its loot comes per reward roll (Tempoross, Wintertodt), else null. */
	private BossDefinition rollsBoss(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		return boss != null && boss.getRollsPerKill() > 0 ? boss : null;
	}

	/**
	 * Reward permits spent at the reward pool: each is one roll, counted like a kill (trip,
	 * Recent, History). Returns the boss, or null.
	 */
	public synchronized String onRewardRolls(String bossName, int rolls, long nowMillis)
	{
		BossDefinition boss = rollsBoss(bossName);
		if (boss == null || rolls <= 0)
		{
			return null;
		}
		BossProgress progress = state.get(boss.getName());
		for (int i = 0; i < rolls; i++)
		{
			beforeKill(boss, nowMillis);
			progress.lootKills++;
			KillEntry entry = addKill(boss, progress, nowMillis);
			countKillToday(boss, entry);
			markKill(boss, nowMillis);
			afterKill(boss, nowMillis);
		}
		startTracking(boss);
		return boss.getName();
	}

	/**
	 * The rolls from before On Rate counted them, worked out once from the game's own numbers:
	 * your subdues times the rolls per subdue, minus the permits you hadn't spent yet (they
	 * haven't rolled). Later calls change nothing. Returns the boss when it was set, else null.
	 */
	public synchronized String onRollBaseline(String bossName, int subdues, int permitsHeld)
	{
		BossDefinition boss = rollsBoss(bossName);
		if (boss == null || subdues <= 0 || permitsHeld < 0)
		{
			return null;
		}
		BossProgress progress = state.get(boss.getName());
		if (progress.rollBase != null)
		{
			return null;
		}
		// rolls counted before the baseline (none, normally) came from those same subdues
		progress.rollBase = Math.max(0, subdues * boss.getRollsPerKill() - permitsHeld - progress.lootKills);
		return boss.getName();
	}

	/** True when the collection log page with this title is one On Rate reads. */
	public synchronized boolean coversPage(String pageTitle)
	{
		return !catalog.byClogPage(pageTitle).isEmpty();
	}

	/**
	 * A collection log page was opened but could not be read. Changes no saved data. Pass a null or
	 * empty title when not even the title could be read.
	 *
	 * @return true when this is worth telling the player: the page is one we cover, or the title is unknown
	 */
	public synchronized boolean onClogPageUnreadable(String pageTitle)
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
	public synchronized boolean logPageUnreadable(String bossName)
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
	public synchronized List<String> unreadablePages()
	{
		return new ArrayList<>(unreadablePages.values());
	}

	/** True when a log page was opened whose title could not be read. */
	public synchronized boolean isHeaderUnreadable()
	{
		return headerUnreadable;
	}

	/**
	 * True when applying this page would set a count we already hold above zero back to zero: every
	 * item reads as not obtained, but a drop on the page is saved as received. The log never loses
	 * items, so this is a page that was misread, not a player who lost drops. A fresh account whose
	 * page is truly all faded has nothing saved and is not flagged.
	 */
	public synchronized boolean wouldZeroSaved(String pageTitle, Map<String, Integer> items)
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
			BossProgress progress = state.peek(boss.getName());
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					String key = BossCatalog.normalize(name);
					if (findQuantity(items, key) == null)
					{
						continue;
					}
					Integer saved = catalog.isClogShared(key) ? state.sharedClog().get(key)
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
	public synchronized Set<String> onClogPage(String pageTitle, List<String> counters, Map<String, Integer> items)
	{
		Set<String> changed = new LinkedHashSet<>();
		List<BossDefinition> bosses = catalog.byClogPage(pageTitle);
		for (BossDefinition boss : bosses)
		{
			BossProgress progress = state.get(boss.getName());

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
					String part = partForName(boss, p[0], true);
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
					state.sharedClog().put(e.getKey(), e.getValue().dropsFromQuantity(total));
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
							state.sharedClog().put(key, total);
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
							progress.lastDropKc.put(key, currentKc(boss));
							progress.milestone.remove(key);
						}
						progress.received.put(key, qty);
					}
				}
			}
			progress.clogSynced = true;
			startTracking(boss);
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

	private static Integer findQuantity(Map<String, Integer> items, String normalizedName)
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

	private static boolean labelNamesBoss(String label, BossDefinition boss)
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
	public synchronized String bossWithMostKills()
	{
		String best = null;
		int bestKc = 0;
		if (state.bosses == null)
		{
			return null;
		}
		for (String name : state.bosses.keySet())
		{
			BossDefinition boss = catalog.byName(name);
			if (boss == null)
			{
				continue;
			}
			BossProgress p = state.peek(name);
			int kc = boss.isDelve() ? sum(p.levelKc.values()) : boss.hasParts() ? sum(partKills(boss, p).values()) : Math.max(p.lootKills, Math.max(combine(p.chatKc.values(), boss.combineKcWithMax()),
				combine(p.clogKc.values(), boss.combineKcWithMax())));
			if (kc > bestKc)
			{
				bestKc = kc;
				best = boss.getName();
			}
		}
		return best;
	}

	/** Label used in the checklist and reminders for the Doom scoreboard. */
	public static final String DELVE_SCOREBOARD = "Doom scoreboard";

	/**
	 * The setup checklist: every collection log page the plugin can read, in alphabetical
	 * order, plus the Doom scoreboard. A page counts as synced once every boss on it is.
	 */
	public synchronized List<SyncStatus> syncChecklist()
	{
		Map<String, List<String>> bossesByPage = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, Boolean> syncedByPage = new LinkedHashMap<>();
		Set<String> killedPages = new HashSet<>();
		List<SyncStatus> result = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = state.peek(boss.getName());
			for (String page : boss.getClogPages())
			{
				bossesByPage.computeIfAbsent(page, k -> new ArrayList<>()).add(boss.getName());
				boolean synced = p != null && p.clogSynced;
				syncedByPage.merge(page.toLowerCase(Locale.ROOT), synced, Boolean::logicalAnd);
				if (p != null && currentKc(boss) > 0)
				{
					killedPages.add(page.toLowerCase(Locale.ROOT));
				}
			}
			if (boss.isDelve())
			{
				result.add(new SyncStatus(DELVE_SCOREBOARD, Collections.singletonList(boss.getName()),
					p != null && p.levelsSynced, p != null && currentKc(boss) > 0));
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

	/**
	 * What the player should open to load this boss's history, or null when nothing is
	 * missing (or the boss has no collection log page we can read).
	 */
	public synchronized String syncReminder(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return null;
		}
		BossProgress p = state.peek(boss.getName());
		if (boss.isDelve() && (p == null || !p.levelsSynced))
		{
			return "Open the Doom scoreboard once to load your completions for every delve level.";
		}
		if (!boss.getClogPages().isEmpty() && (p == null || !p.clogSynced))
		{
			return "Open the \"" + boss.getClogPages().get(0) + "\" page in your collection log once to load your past drops.";
		}
		return null;
	}

	public synchronized void reset(String bossName)
	{
		BossDefinition resetBoss = catalog.byName(bossName);
		if (resetBoss != null)
		{
			for (String page : resetBoss.getClogPages())
			{
				unreadablePages.remove(BossCatalog.normalize(page));
			}
		}
		if (state.bosses != null && state.bosses.remove(bossName) != null)
		{
			killsChanged.add(bossName);
			raidsChanged.add(bossName);
		}
		// Recent's day counts, the trip and the last-kill pointers must not outlive the reset
		for (Map<String, DayCount> forDay : state.days().values())
		{
			forDay.remove(bossName);
		}
		daysFilled.remove(bossName);
		if (trip != null && trip.boss.equals(bossName))
		{
			trip = null;
			beforeThisKill = null;
		}
		if (bossName.equals(lastBoss))
		{
			lastBoss = null;
		}
	}

	/** A trip ends after this long without a kill, or when you kill another boss. */
	static final long TRIP_IDLE_MILLIS = 30 * 60_000;
	/**
	 * The same for content where one completion takes long: a raid, the Inferno or the Fight
	 * Caves often takes more than 30 minutes, which would start a new trip every time. Two hours
	 * leaves room for a slow raid plus a break; another boss or the button still end it sooner.
	 */
	static final long LONG_TRIP_IDLE_MILLIS = 120 * 60_000;

	static long tripIdleMillis(BossDefinition boss)
	{
		String name = boss.getName();
		return boss.getRaid() != null || "TzKal-Zuk".equals(name) || "TzTok-Jad".equals(name)
			? LONG_TRIP_IDLE_MILLIS : TRIP_IDLE_MILLIS;
	}

	private Trip trip;

	/** This boss's numbers just before the kill being counted (for the trip card and Recent). */
	private Trip beforeThisKill;

	private boolean continuesTrip(BossDefinition boss, long nowMillis)
	{
		return trip != null && trip.boss.equals(boss.getName()) && nowMillis - trip.lastMillis <= tripIdleMillis(boss);
	}

	/**
	 * Called just before a kill is counted: remembers the numbers as they are now, so the trip
	 * card and Recent get exactly what this kill added (also for raids, whose points come in a
	 * moment after the kill).
	 */
	private void beforeKill(BossDefinition boss, long nowMillis)
	{
		if (trip != null && !continuesTrip(boss, nowMillis))
		{
			// this kill starts a new trip: the last one is over
			summarizeTrip();
		}
		beforeThisKill = null;
		BossStats s = stats(boss.getName(), settings);
		if (s == null)
		{
			return;
		}
		Map<String, Integer> received = new HashMap<>();
		for (RowStats row : s.getRows())
		{
			received.put(row.getLabel(), row.getReceived());
		}
		RowStats any = s.getAnyRow();
		beforeThisKill = new Trip(boss.getName(), nowMillis, s.getKillCount(), any.getExpected(), any.getReceived(), received);
	}

	/**
	 * Called after each kill: continues the current trip, or starts a new one when this is
	 * another boss or the last kill was a while ago. The new trip includes this kill.
	 */
	private void afterKill(BossDefinition boss, long nowMillis)
	{
		Trip before = beforeThisKill;
		beforeThisKill = null;
		if (continuesTrip(boss, nowMillis))
		{
			trip.lastMillis = nowMillis;
			return;
		}
		BossStats s = stats(boss.getName(), settings);
		if (s == null)
		{
			return;
		}
		if (before != null && before.boss.equals(boss.getName()) && s.getKillCount() == before.startKc + 1)
		{
			trip = before;
			return;
		}
		// no snapshot of exactly one kill ago (e.g. the first kill count after a long break
		// jumped by more than one): start from this kill at the average rate
		Map<String, Integer> received = new HashMap<>();
		for (RowStats row : s.getRows())
		{
			received.put(row.getLabel(), row.getReceived());
		}
		RowStats any = s.getAnyRow();
		trip = new Trip(boss.getName(), nowMillis, s.getKillCount() - 1,
			any.getExpected() - any.getRate(), any.getReceived(), received);
	}

	/** Forget the current trip; the next kill starts a new one. */
	public synchronized void resetTrip()
	{
		summarizeTrip();
		trip = null;
	}

	/** Trips shorter than this get no summary line. */
	static final int TRIP_SUMMARY_KILLS = 3;

	/** One chat line for the trip that just ended: kills, uniques, expected and the verdict. */
	private void summarizeTrip()
	{
		TripStats t = tripStats();
		if (t == null || t.getKills() < TRIP_SUMMARY_KILLS)
		{
			return;
		}
		BossDefinition boss = catalog.byName(t.getBoss());
		String units = boss == null ? "kills" : unit(boss);
		String uniques = t.getReceived() == 0 ? "no uniques"
			: t.getReceived() == 1 ? "1 unique (" + String.join(", ", t.getDrops()) + ")"
			: t.getReceived() + " uniques (" + String.join(", ", t.getDrops()) + ")";
		double luck = t.getLuck();
		outbox.add(new Note(Note.Kind.TRIP, String.format("Trip at %s: %,d %s, %s, %s expected%s.", t.getBoss(),
			t.getKills(), units, uniques, t.getExpected() >= 10 ? String.format("%.1f", t.getExpected()) : String.format("%.2f", t.getExpected()),
			Double.isNaN(luck) ? "" : " \u00b7 " + DropMath.verdict(luck))));
	}

	/** The last unique On Rate saw in History (any boss), or null; with when History starts. */
	public synchronized LastUnique lastUnique()
	{
		LastUnique result = null;
		long earliest = Long.MAX_VALUE;
		if (state.bosses == null)
		{
			return null;
		}
		for (String name : state.bosses.keySet())
		{
			BossDefinition boss = catalog.byName(name);
			BossProgress p = state.peek(name);
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
					DropDefinition drop = rowOfSaved(boss, d.getName());
					if (drop != null && counts(boss, drop, p) && (result == null || k.time > result.time))
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

	/** See {@link #lastUnique()}: the item (null when none in History), its boss and time, and History's start. */
	public static final class LastUnique
	{
		public final String item;
		public final String boss;
		public final long time;
		public final long historyStart;

		LastUnique(String item, String boss, long time, long historyStart)
		{
			this.item = item;
			this.boss = boss;
			this.time = time;
			this.historyStart = historyStart;
		}
	}

	/** The current trip's numbers, or null when there is no trip. */
	public synchronized TripStats tripStats()
	{
		if (trip == null)
		{
			return null;
		}
		BossStats s = stats(trip.boss, settings);
		if (s == null)
		{
			return null;
		}
		List<String> drops = new ArrayList<>();
		for (RowStats row : s.getRows())
		{
			Integer before = trip.receivedPerRow.get(row.getLabel());
			int got = row.getReceived() - (before == null ? 0 : before);
			for (int i = 0; i < got; i++)
			{
				drops.add(row.getLabel());
			}
		}
		RowStats any = s.getAnyRow();
		return new TripStats(trip.boss, trip.startMillis, trip.lastMillis, Math.max(0, s.getKillCount() - trip.startKc),
			Math.max(0, any.getReceived() - trip.startReceived), Math.max(0, any.getExpected() - trip.startExpected), drops);
	}

	private void markKill(BossDefinition boss, long nowMillis)
	{
		lastBoss = boss.getName();
		lastKillMillis = nowMillis;
	}

	private static DropDefinition findDrop(BossDefinition boss, String itemName)
	{
		String key = BossCatalog.normalize(itemName);
		for (DropDefinition drop : boss.getDrops())
		{
			for (String name : drop.getAllNames())
			{
				if (BossCatalog.normalize(name).equals(key))
				{
					return drop;
				}
			}
		}
		return null;
	}

	/**
	 * Rate of a drop for one part of a combined page (one boss, or one raid mode).
	 * Explicit part rates win; raid modes use their own purple chance and weights.
	 */
	public static double partRate(BossDefinition boss, BossPart part, DropDefinition drop, RaidSettings raids)
	{
		if (drop.hasPartRates())
		{
			return DropMath.parseRate(drop.getPartRate(part.getName()));
		}
		if (part.getRaid() != null)
		{
			double unique = raids.uniqueChance(part.getRaid());
			Double weight = drop.getWeight(part.getName());
			if (weight != null)
			{
				double total = boss.getTotalWeight(part.getName());
				return total > 0 ? unique * weight / total : 0;
			}
			if (drop.getPerUnique() != null)
			{
				return unique * DropMath.parseRate(drop.getPerUnique());
			}
		}
		return DropMath.parseRate(drop.getRate());
	}

	/**
	 * Expected count of a drop from one part. For raid modes, completions whose points
	 * were read use their own purple chance; older ones use the setting.
	 */
	private static double partExpected(BossDefinition boss, BossPart part, DropDefinition drop, RaidSettings raids,
		int completions, PartRaids known)
	{
		double perRaid = partRate(boss, part, drop, raids);
		if (known == null || known.count == 0 || part.getRaid() == null || drop.hasPartRates())
		{
			return completions * perRaid;
		}
		double expected = (completions - known.count) * perRaid;
		if (drop.isPet() && known.toa)
		{
			// the Tombs of Amascut pet has its own formula from the points
			return expected + known.petSum;
		}
		double share = partRate(boss, part, drop, key -> 1.0);
		for (Map.Entry<Integer, Double> e : known.chanceByLevel.entrySet())
		{
			int level = e.getKey();
			double itemShare = level >= 0 ? levelShare(boss, drop, level, share) : share;
			expected += e.getValue() * itemShare * RaidChance.toaLevelFactor(level, drop.getMinRaidLevel());
		}
		return expected;
	}

	/**
	 * A raid mode's saved raids whose chance is known, added up once per calculation: the purple
	 * chances per raid level (the item shares depend on the level) and the pet chances. Only the
	 * latest {@code completions} raids count.
	 */
	static final class PartRaids
	{
		int count;
		boolean toa;
		double petSum;
		final Map<Integer, Double> chanceByLevel = new HashMap<>();
	}

	private static PartRaids partRaids(BossPart part, RaidSettings raids, int completions, BossProgress progress)
	{
		if (progress == null || part.getRaid() == null || progress.raids.isEmpty() || completions <= 0)
		{
			return null;
		}
		List<RaidRecord> known = new ArrayList<>();
		List<Double> chances = new ArrayList<>();
		for (RaidRecord r : progress.raids)
		{
			if (!part.getName().equals(r.part))
			{
				continue;
			}
			double chance = recordChance(r, part, raids);
			if (!Double.isNaN(chance))
			{
				known.add(r);
				chances.add(chance);
			}
		}
		PartRaids result = new PartRaids();
		int from = Math.max(0, known.size() - completions);
		for (int i = from; i < known.size(); i++)
		{
			RaidRecord r = known.get(i);
			result.count++;
			result.chanceByLevel.merge(r.raidLevel, chances.get(i), Double::sum);
			if ("toa".equals(r.family))
			{
				result.toa = true;
				double pet = RaidChance.toaPet(r.personalPoints, r.partyPoints, r.teamPoints, r.teamSize, r.raidLevel);
				result.petSum += Double.isNaN(pet) ? 0 : pet;
			}
		}
		return result;
	}

	/** Per-kill rate for a drop, taking raid settings into account. */
	public static double effectiveRate(BossDefinition boss, DropDefinition drop, RaidSettings raids)
	{
		if (boss.getWaves() > 0)
		{
			// a whole run, claimed at the last wave
			return waveRate(drop, boss.getWaves());
		}
		double rate = DropMath.parseRate(drop.getRate());
		if (boss.getRaid() != null)
		{
			double unique = raids.uniqueChance(boss.getRaid());
			if (drop.getWeight() != null)
			{
				double total = boss.getTotalWeight();
				rate = total > 0 ? unique * drop.getWeight() / total : 0;
			}
			else if (drop.getPerUnique() != null)
			{
				rate = unique * DropMath.parseRate(drop.getPerUnique());
			}
		}
		if (drop.getMinionRate() != null && boss.getMinions() > 0)
		{
			// God Wars bodyguards roll some of the general's uniques too
			rate += boss.getMinions() * DropMath.parseRate(drop.getMinionRate());
		}
		return rate;
	}

	public synchronized BossStats stats(String bossName, RaidSettings raids)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return null;
		}
		BossProgress progress = state.peek(boss.getName());

		int kc = 0;
		boolean known = false;
		boolean synced = false;
		Map<Integer, Integer> levels = null;
		Map<String, Integer> parts = null;
		Map<Integer, Integer> waveRuns = null;
		if (boss.hasParts())
		{
			parts = partKills(boss, progress);
			kc = sum(parts.values());
			known = progress != null && (!progress.chatKc.isEmpty() || !progress.clogKc.isEmpty());
			synced = progress != null && progress.clogSynced;
		}
		else if (boss.isDelve())
		{
			levels = new TreeMap<>();
			for (int l = 1; l <= BossDefinition.DELVE_LEVELS; l++)
			{
				Integer n = progress == null ? null : progress.levelKc.get(l);
				levels.put(l, n == null ? 0 : n);
			}
			kc = sum(levels.values());
			known = progress != null && !progress.levelKc.isEmpty();
			synced = progress != null && progress.clogSynced;
		}
		else if (progress != null && boss.getWaves() > 0)
		{
			// claimed runs per wave; completions the log page showed that On Rate hadn't counted
			// as claims yet are from before it: they count as last-wave claims
			waveRuns = new TreeMap<>(progress.waveClaims == null ? Collections.emptyMap() : progress.waveClaims);
			int completions = combine(progress.clogKc.values(), boss.combineKcWithMax());
			int counted = progress.lastWaveClaimsAtSync == null ? 0 : progress.lastWaveClaimsAtSync;
			int older = Math.max(0, completions - counted);
			if (older > 0)
			{
				waveRuns.merge(boss.getWaves(), older, Integer::sum);
			}
			kc = sum(waveRuns.values());
			known = kc > 0 || !progress.chatKc.isEmpty() || !progress.clogKc.isEmpty();
			synced = progress.clogSynced;
		}
		else if (progress != null && boss.getRollsPerKill() > 0)
		{
			// reward rolls: the estimate for the ones from before, plus the ones counted since
			int subdues = combine(progress.clogKc.values(), boss.combineKcWithMax());
			int before = progress.rollBase != null ? progress.rollBase : subdues * boss.getRollsPerKill();
			kc = before + progress.lootKills;
			known = progress.rollBase != null || !progress.clogKc.isEmpty() || progress.lootKills > 0;
			synced = progress.clogSynced;
		}
		else if (progress != null)
		{
			int chat = combine(progress.chatKc.values(), boss.combineKcWithMax());
			int clog = combine(progress.clogKc.values(), boss.combineKcWithMax());
			known = !progress.chatKc.isEmpty() || !progress.clogKc.isEmpty();
			kc = known ? Math.max(chat, clog) : progress.lootKills;
			if (boss.getKcNames().isEmpty() && !progress.clogKc.isEmpty() && progress.lootKillsAtSync != null)
			{
				// no kill count message: the log count plus the kills seen since it was read
				kc = Math.max(kc, clog + Math.max(0, progress.lootKills - progress.lootKillsAtSync));
			}
			synced = progress.clogSynced;
		}

		List<RowStats> rows = new ArrayList<>();
		double anyExpected = 0;
		int anyReceived = 0;
		RowStats.Parts anyParts = new RowStats.Parts();
		// each raid mode's saved raids, added up once for all its items
		Map<String, PartRaids> partRaids = new HashMap<>();
		if (parts != null)
		{
			for (BossPart part : boss.getParts())
			{
				partRaids.put(part.getName(), partRaids(part, raids, parts.get(part.getName()), progress));
			}
		}
		// the boss's own uniques, then the shared items the player chose to judge per boss
		List<DropDefinition> drops = new ArrayList<>(boss.getDrops());
		Map<DropDefinition, Integer> splitReceived = new HashMap<>();
		for (Map.Entry<String, DropDefinition> e : catalog.poolsOf(boss).entrySet())
		{
			PoolSplit split = activeSplit(e.getKey());
			if (split != null)
			{
				drops.add(e.getValue());
				splitReceived.put(e.getValue(), poolSeenAt(boss.getName(), e.getKey()) + split.olderAt(boss.getName()));
			}
		}
		for (DropDefinition drop : drops)
		{
			double expected;
			double rate;
			boolean sureDropped = false;
			if (parts != null)
			{
				expected = 0;
				for (BossPart part : boss.getParts())
				{
					expected += partExpected(boss, part, drop, raids, parts.get(part.getName()), partRaids.get(part.getName()));
				}
				rate = kc > 0 ? expected / kc : 0;
			}
			else if (waveRuns != null)
			{
				// each run counts every wave up to the one it was claimed at
				expected = 0;
				for (Map.Entry<Integer, Integer> e : waveRuns.entrySet())
				{
					expected += e.getValue() * waveRate(drop, e.getKey());
				}
				rate = kc > 0 ? expected / kc : effectiveRate(boss, drop, raids);
			}
			else if (levels != null)
			{
				expected = 0;
				for (Map.Entry<Integer, Integer> e : levels.entrySet())
				{
					expected += e.getValue() * DropMath.parseRate(drop.getLevelRate(e.getKey()));
				}
				// average rate per completion so far, for the tooltip
				rate = kc > 0 ? expected / kc : 0;
			}
			else
			{
				rate = effectiveRate(boss, drop, raids);
				if (boss.isDetectTeam() && !drop.isNoShare() && progress != null
					&& (!progress.teamKills.isEmpty() || progress.olderShare != null))
				{
					// group boss: each kill counts for your share of the team you had then
					expected = rate * sharedKills(boss, progress, kc, raids);
					rate = kc > 0 ? expected / kc : rate;
				}
				else
				{
					if (boss.getTeam() != null && !drop.isNoShare())
					{
						// team boss: your chance scales with your share of the damage
						rate *= raids.contribution(boss.getTeam(), kc);
					}
					expected = rate * kc;
				}
				Integer sure = drop.getGuaranteedAt();
				if (sure != null && kc >= sure)
				{
					// that kill was a sure drop for a player with none, but only until the first
					// head: no repeat at 100, 150... (the typical player, whatever you have)
					expected = DropMath.guaranteedExpected(kc, rate, sure);
					sureDropped = true;
				}
			}
			Integer split = splitReceived.get(drop);
			int received = split != null ? split : 0;
			// No kills here: anything in the log came from elsewhere (an occult necklace
			// from a regular smoke devil...), so it doesn't count for this boss.
			if (split == null && progress != null && kc > 0)
			{
				for (String name : drop.getAllNames())
				{
					Integer r = progress.received.get(BossCatalog.normalize(name));
					if (r != null)
					{
						received += r;
					}
				}
			}
			RowStats row = new RowStats(drop.getDisplayName(), drop.getName(), rate, expected, received, false);
			if (sureDropped)
			{
				// nobody past the guaranteed kill has none, and the count is no Poisson
				row.withParts(new RowStats.Parts().addGuaranteed(kc, rate, drop.getGuaranteedAt()));
			}
			// a split shared item has no dry streak: On Rate never saw when the older copies dropped
			if (split == null && !(drop.getMax() > 0 && received >= drop.getMax()))
			{
				Integer last = null;
				if (progress != null)
				{
					for (String name : drop.getAllNames())
					{
						Integer kcAt = progress.lastDropKc.get(BossCatalog.normalize(name));
						if (kcAt != null && (last == null || kcAt > last))
						{
							last = kcAt;
						}
					}
				}
				if (last != null)
				{
					row.withStreak(Math.max(0, kc - last), true);
				}
				else if (received == 0 && progress != null && progress.clogSynced
					&& !catalog.isClogShared(BossCatalog.normalize(drop.getName())))
				{
					row.withStreak(kc, true);
				}
				else if (progress != null && progress.trackingStartKc != null)
				{
					row.withStreak(Math.max(0, kc - progress.trackingStartKc), false);
				}
			}
			if (drop.getMax() > 0)
			{
				// The collection log counts these at most once (or twice for the two titan
				// scrolls), so for luck it's "have it or not".
				received = Math.min(received, drop.getMax());
				row = row.capped(drop.getMax(), received);
			}
			if (progress != null && progress.fastestGap != null)
			{
				Integer fastest = null;
				Integer longest = null;
				for (String name : drop.getAllNames())
				{
					Integer f = progress.fastestGap.get(BossCatalog.normalize(name));
					Integer l = progress.longestGap.get(BossCatalog.normalize(name));
					if (f != null && (fastest == null || f < fastest))
					{
						fastest = f;
					}
					if (l != null && (longest == null || l > longest))
					{
						longest = l;
					}
				}
				row.withRecords(fastest, longest);
			}
			rows.add(row);
			if (counts(boss, drop, progress))
			{
				// For once-only items the total counts the chance of having one by now.
				anyExpected += drop.getMax() > 0 ? DropMath.expectedCapped(expected, drop.getMax()) : expected;
				anyReceived += received;
				// and its luck adds up each item as it counts, not a Poisson of the total
				anyParts.add(row);
			}
		}
		RowStats any = new RowStats("Any", null, 0, anyExpected, anyReceived, true).withParts(anyParts);
		// "Any" streak: kills since the most recent unique On Rate knows of.
		double anyRate = 0;
		Integer lastAny = null;
		for (int i = 0; i < drops.size(); i++)
		{
			DropDefinition drop = drops.get(i);
			if (!counts(boss, drop, progress))
			{
				continue;
			}
			anyRate += rows.get(i).getRate();
			if (progress != null)
			{
				for (String name : drop.getAllNames())
				{
					Integer kcAt = progress.lastDropKc.get(BossCatalog.normalize(name));
					if (kcAt != null && (lastAny == null || kcAt > lastAny))
					{
						lastAny = kcAt;
					}
				}
			}
		}
		any.withRate(anyRate);
		if (lastAny != null)
		{
			any.withStreak(Math.max(0, kc - lastAny), true);
		}
		else if (anyReceived == 0 && synced)
		{
			any.withStreak(kc, true);
		}
		else if (progress != null && progress.trackingStartKc != null)
		{
			any.withStreak(Math.max(0, kc - progress.trackingStartKc), false);
		}
		double raidChance = boss.getRaid() != null ? raids.uniqueChance(boss.getRaid()) : Double.NaN;
		BossStats stats = new BossStats(boss, kc, known, synced, any, rows, raidChance, levels);
		stats.setPartCounts(parts);
		stats.setDrops(drops);
		return stats;
	}

	/**
	 * Every boss with kills or drops, added up: how dry or spooned the account is overall.
	 */
	public synchronized AccountStats accountStats(RaidSettings raids)
	{
		return accountStats(raids, null);
	}

	/**
	 * Same as {@link #accountStats(RaidSettings)}, plus GE value when {@code prices} is given.
	 * Prices are per item name; untradeable items have price 0, so they count for the number
	 * of uniques but not for value.
	 */
	public synchronized AccountStats accountStats(RaidSettings raids, ToLongFunction<String> prices)
	{
		List<Double> valueExpected = new ArrayList<>();
		List<Long> valuePrices = new ArrayList<>();
		double expectedValue = 0;
		long receivedValue = 0;

		List<RowStats> rows = new ArrayList<>();
		List<ItemHighlight> items = new ArrayList<>();
		Map<String, Integer> killCounts = new LinkedHashMap<>();
		double expected = 0;
		int received = 0;
		RowStats.Parts totalParts = new RowStats.Parts();
		List<Integer> valueCaps = new ArrayList<>();
		// every boss's numbers worked out once, then shared with the shared-items pass
		Map<String, BossStats> cache = new HashMap<>();
		List<RowStats> splitLeftovers = new ArrayList<>();
		List<String> unsynced = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossStats s = cachedStats(boss.getName(), raids, cache);
			RowStats any = s.getAnyRow();
			boolean skipped = s.getDrops().isEmpty() || s.getKillCount() <= 0 || syncReminder(boss.getName()) != null;
			if (skipped)
			{
				// a shared item split per boss still counts, as it did while shared
				for (int i = boss.getDrops().size(); i < s.getDrops().size(); i++)
				{
					splitLeftovers.add(s.getRows().get(i));
				}
			}
			if (s.getDrops().isEmpty() || s.getKillCount() <= 0)
			{
				continue;
			}
			if (syncReminder(boss.getName()) != null)
			{
				// kills known, drops from before On Rate not: it would only drag the account
				// towards dry, so it waits until its log page is opened
				unsynced.add(boss.getName());
				continue;
			}
			RowStats bossRow = new RowStats(boss.getName(), iconFor(boss), 0, any.getExpected(), any.getReceived(), false)
				.withRate(any.getRate()).withStreak(any.getStreak(), any.isStreakExact()).withParts(any.getParts());
			BossProgress progress = state.peek(boss.getName());
			double driest = Double.NaN;
			String driestItem = null;
			for (int i = 0; i < s.getDrops().size(); i++)
			{
				RowStats r = s.getRows().get(i);
				double m = r.getStreakMultiple();
				if (counts(boss, s.getDrops().get(i), progress) && !Double.isNaN(m) && (Double.isNaN(driest) || m > driest))
				{
					driest = m;
					driestItem = r.getLabel();
				}
			}
			rows.add(bossRow.withDriest(driest, driestItem));
			for (int i = 0; i < s.getDrops().size(); i++)
			{
				if (counts(boss, s.getDrops().get(i), progress))
				{
					items.add(new ItemHighlight(boss.getName(), s.getRows().get(i), s.getDrops().get(i).isPet()));
				}
			}
			if (prices != null)
			{
				int pieces = 0;
				long piecePrice = 0;
				for (int i = 0; i < s.getDrops().size(); i++)
				{
					DropDefinition drop = s.getDrops().get(i);
					RowStats row = s.getRows().get(i);
					if (!counts(boss, drop, progress))
					{
						continue;
					}
					long price = Math.max(0, prices.applyAsLong(drop.getName()));
					if (price <= 0)
					{
						continue;
					}
					valueExpected.add(row.getExpected());
					valuePrices.add(price);
					valueCaps.add(row.getCap());
					expectedValue += DropMath.expectedValue(row.getExpected(), row.getCap()) * price;
					if (DropMath.isHalberdPiece(drop.getName()))
					{
						pieces += row.getReceived();
						piecePrice = price;
					}
					else
					{
						receivedValue += row.getReceived() * price;
					}
				}
				receivedValue += DropMath.pieceSetValue(pieces, piecePrice);
			}
			killCounts.put(boss.getName(), s.getKillCount());
			expected += any.getExpected();
			received += any.getReceived();
			totalParts.add(any);
		}
		// Items several bosses drop (dragon pickaxe, onyx...) have no row of their own here (they
		// show on the page of each boss that drops them), but they count in the account verdict
		// and GE value once On Rate knows the kills of every boss that drops them.
		for (RowStats row : splitLeftovers)
		{
			expected += row.getCap() > 0 ? DropMath.expectedCapped(row.getExpected(), row.getCap()) : row.getExpected();
			received += row.getReceived();
			totalParts.add(row);
			long price = prices == null ? 0 : Math.max(0, prices.applyAsLong(row.getIconItem()));
			if (price > 0)
			{
				valueExpected.add(row.getExpected());
				valuePrices.add(price);
				valueCaps.add(row.getCap());
				expectedValue += DropMath.expectedValue(row.getExpected(), row.getCap()) * price;
				receivedValue += row.getReceived() * price;
			}
		}
		for (SharedPool pool : sharedPools(raids, cache))
		{
			if (isPetItem(pool.getItemName()) && pool.isLogKnown() && pool.getExpected() > 0)
			{
				// a pet several bosses drop (the Chaos elemental's): one entry in the Items tab's
				// pets, judged on all those bosses together, "have it or not" like every pet
				items.add(new ItemHighlight(null, pool.toRow(), true));
			}
			if (!pool.isComplete())
			{
				continue;
			}
			RowStats poolRow = pool.toRow();
			expected += pool.getCountedExpected();
			received += poolRow.getReceived();
			totalParts.add(poolRow);
			long price = prices == null ? 0 : Math.max(0, prices.applyAsLong(pool.getItemName()));
			if (price > 0)
			{
				valueExpected.add(pool.getExpected());
				valuePrices.add(price);
				valueCaps.add(poolRow.getCap());
				expectedValue += pool.getCountedExpected() * price;
				receivedValue += poolRow.getReceived() * price;
			}
		}
		rows.sort((a, b) -> Double.compare(b.getExpected(), a.getExpected()));
		RowStats total = new RowStats("All", null, 0, expected, received, true).withParts(totalParts);
		AccountStats account = new AccountStats(total, rows, killCounts);
		account.setUnsynced(unsynced);
		account.setItems(items);
		if (prices != null)
		{
			double[] lambdas = new double[valueExpected.size()];
			long[] itemPrices = new long[valuePrices.size()];
			int[] itemCaps = new int[valueCaps.size()];
			for (int i = 0; i < lambdas.length; i++)
			{
				lambdas[i] = valueExpected.get(i);
				itemPrices[i] = valuePrices.get(i);
				itemCaps[i] = valueCaps.get(i);
			}
			double luck = DropMath.valueLuck(lambdas, itemPrices, itemCaps, receivedValue, 5000, 1234567L);
			account.setValue(expectedValue, receivedValue, luck);
		}
		return account;
	}

	/**
	 * Every item several bosses drop. Complete when the log total is known and every boss
	 * that drops it has a known kill count; otherwise lists the pages still to open.
	 */
	public synchronized List<SharedPool> sharedPools(RaidSettings raids)
	{
		return sharedPools(raids, new HashMap<>());
	}

	/** The shared items this boss drops (with every boss that drops them), for its page. */
	public synchronized List<SharedPool> sharedPoolsFor(String bossName, RaidSettings raids)
	{
		List<SharedPool> result = new ArrayList<>();
		for (SharedPool pool : sharedPools(raids))
		{
			if (pool.getBosses().contains(bossName))
			{
				result.add(pool);
			}
		}
		return result;
	}

	private BossStats cachedStats(String bossName, RaidSettings raids, Map<String, BossStats> cache)
	{
		BossStats s = cache.get(bossName);
		if (s == null)
		{
			s = stats(bossName, raids);
			cache.put(bossName, s);
		}
		return s;
	}

	private List<SharedPool> sharedPools(RaidSettings raids, Map<String, BossStats> cache)
	{
		List<SharedPool> result = new ArrayList<>();
		for (Map.Entry<String, List<BossCatalog.PoolSource>> e : catalog.getPools().entrySet())
		{
			if (activeSplit(e.getKey()) != null)
			{
				// judged on each boss separately: those copies are rows on the bosses' own pages
				continue;
			}
			Integer logTotal = state.sharedClog().get(e.getKey());
			double expected = 0;
			boolean once = false;
			String label = null;
			String itemName = null;
			String alsoFrom = null;
			List<String> bosses = new ArrayList<>();
			List<String> missing = new ArrayList<>();
			for (BossCatalog.PoolSource src : e.getValue())
			{
				BossDefinition boss = src.getBoss();
				DropDefinition drop = src.getDrop();
				if (label == null)
				{
					label = drop.getDisplayName();
					itemName = drop.getName();
				}
				if (alsoFrom == null)
				{
					alsoFrom = drop.getAlsoFrom();
				}
				once |= drop.isOnce();
				bosses.add(boss.getName());
				BossStats s = cachedStats(boss.getName(), raids, cache);
				BossProgress p = state.peek(boss.getName());
				boolean kcKnown = s != null && (s.isKillCountKnown() || s.isClogSynced()
					|| (p != null && p.lootKills > 0 && boss.getKcNames().isEmpty()));
				if (!kcKnown)
				{
					String page = boss.getClogPages().isEmpty() ? boss.getName() : boss.getClogPages().get(0);
					if (!missing.contains(page))
					{
						missing.add(page);
					}
				}
				if (s != null)
				{
					expected += poolExpected(boss, drop, s, raids);
				}
			}
			if (logTotal == null && missing.isEmpty())
			{
				// every kill count is known, but no page showing this item was opened yet
				BossDefinition first = e.getValue().get(0).getBoss();
				missing.add(first.getClogPages().isEmpty() ? first.getName() : first.getClogPages().get(0));
			}
			int got = logTotal == null ? 0 : logTotal;
			if (once)
			{
				got = Math.min(got, 1);
			}
			result.add(new SharedPool(label, itemName, expected, got, bosses, missing).withLogKnown(logTotal != null).withOnce(once)
				.withAlsoFrom(alsoFrom).withKey(e.getKey())
				.withSplit(canSplit(e.getKey()), olderCopies(e.getKey()), state.poolSplits().containsKey(e.getKey())));
		}
		return result;
	}

	/** Expected drops of a pooled item from one boss, from its kill counts. */
	private static double poolExpected(BossDefinition boss, DropDefinition drop, BossStats s, RaidSettings raids)
	{
		if (s.getPartCounts() != null && boss.hasParts())
		{
			double expected = 0;
			for (BossPart part : boss.getParts())
			{
				Integer kc = s.getPartCounts().get(part.getName());
				if (kc != null)
				{
					expected += kc * partRate(boss, part, drop, raids);
				}
			}
			return expected;
		}
		double rate = effectiveRate(boss, drop, raids);
		if (boss.getTeam() != null && !drop.isNoShare())
		{
			rate *= raids.contribution(boss.getTeam(), s.getKillCount());
		}
		return rate * s.getKillCount();
	}

	/** The item that stands for a boss in the overview: its pet, or else its first unique. */
	private static String iconFor(BossDefinition boss)
	{
		for (DropDefinition drop : boss.getDrops())
		{
			if (drop.isPet())
			{
				return drop.getName();
			}
		}
		return boss.getDrops().isEmpty() ? null : boss.getDrops().get(0).getName();
	}

	private static int sum(Collection<Integer> values)
	{
		int total = 0;
		for (int v : values)
		{
			total += v;
		}
		return total;
	}

	private static int combine(Collection<Integer> values, boolean max)
	{
		int result = 0;
		for (int v : values)
		{
			result = max ? Math.max(result, v) : result + v;
		}
		return result;
	}
}
