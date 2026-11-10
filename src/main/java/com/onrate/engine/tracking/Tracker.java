package com.onrate.engine.tracking;


import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.SharedPool;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
 * <p>
 * The public front door and the event coordinator: it handles each event (chat messages,
 * loot, pets, delves, waves, reward rolls) and calls its parts in a fixed order, records
 * drops and milestone messages, and loads and saves the account. The work itself is done by
 * package-private parts, each reached only from here under this object's lock:
 * {@link Statistics} (the numbers), {@link SharedItems}, {@link DayCounts}, {@link Trips},
 * {@link FightTimes}, {@link RaidRecords}, {@link ClogSync} and {@link HistoryBook}. They share
 * one {@link TrackerContext}, so after an account switch they all read the new account.
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
	private final List<Note> outbox = new ArrayList<>();
	private String lastBoss;
	/** For combined pages: which part (e.g. Dagannoth Rex) was killed last. */
	private String lastPart;
	private long lastKillMillis;

	public Tracker(BossCatalog catalog)
	{
		this.catalog = catalog;
		this.ctx = new TrackerContext(catalog);
		this.fightTimes = new FightTimes(ctx);
		this.sharedItems = new SharedItems(ctx);
		this.statistics = new Statistics(ctx, sharedItems);
		this.dayCounts = new DayCounts(ctx, statistics);
		this.trips = new Trips(ctx, statistics, sharedItems, outbox);
		this.raidData = new RaidRecords(ctx, statistics, dayCounts);
		this.clogSync = new ClogSync(ctx, statistics);
		this.historyBook = new HistoryBook(ctx, statistics, raidData);
	}

	/** The catalogue, the account loaded now, the settings, the team counter and the save marks, shared with the parts of the tracker. */
	private final TrackerContext ctx;
	/** History: the kills kept per boss, past drops added by hand, and the luck graph's daily points. */
	private final HistoryBook historyBook;
	/** Reading collection log pages: kill counts and drops loaded from the log, unreadable pages, the sync checklist. */
	private final ClogSync clogSync;
	/** Raid points and completions matched up (in either order), and the raid records and averages. */
	private final RaidRecords raidData;
	/** The current trip: kills at one boss without a long break, and its summary when it ends. */
	private final Trips trips;
	/** Today and Last 7 days: kills, uniques and expected per boss per day. */
	private final DayCounts dayCounts;
	/** The numbers: per boss, for the account and for shared items, worked out from the saved state. */
	private final Statistics statistics;
	/** Shared items (dragon pickaxe, visage...): copies seen per boss, older copies, the player's per-boss split. */
	private final SharedItems sharedItems;
	/** Kill and raid times: matching time messages to kills, and times imported from Raid Data Tracker. */
	private final FightTimes fightTimes;

	public BossCatalog getCatalog()
	{
		return catalog;
	}

	/** How to count your team at a group boss kill, whichever the boss (see BossDefinition#isDetectTeam). */
	public synchronized void setTeamSizeDetector(java.util.function.IntSupplier teamSize)
	{
		ctx.setTeamSize(teamSize == null ? boss -> 0 : boss -> teamSize.getAsInt());
	}

	/** The same, told which boss it is for: each boss can count its team its own way. */
	public synchronized void setTeamSizeDetectorFor(java.util.function.ToIntFunction<String> teamSize)
	{
		ctx.setTeamSize(teamSize == null ? boss -> 0 : teamSize);
	}


	/**
	 * Everything to save, serialized while holding the lock: the main data, plus the History and
	 * raids of the bosses that changed since the last save.
	 */
	public synchronized SaveData saveData(java.util.function.Function<Object, String> toJson)
	{
		SaveData data = new SaveData(toJson.apply(ctx.state()));
		for (String name : ctx.killsChanged)
		{
			BossProgress p = ctx.state().peek(name);
			data.kills.put(name, p == null || p.kills.isEmpty() ? null : toJson.apply(p.kills));
		}
		ctx.killsChanged.clear();
		for (String name : ctx.raidsChanged)
		{
			BossProgress p = ctx.state().peek(name);
			data.raids.put(name, p == null || p.raids.isEmpty() ? null : toJson.apply(p.raids));
		}
		ctx.raidsChanged.clear();
		return data;
	}

	/**
	 * Marks bosses whose History or raids could not be written as still changed, so the next
	 * {@link #saveData} hands them out again.
	 */
	public synchronized void markUnsaved(Collection<String> kills, Collection<String> raids)
	{
		ctx.killsChanged.addAll(kills);
		ctx.raidsChanged.addAll(raids);
	}

	/** Puts back a raid's saved raids when an account is loaded. */
	public synchronized void setRaids(String bossName, List<RaidRecord> raids)
	{
		if (raids != null && !raids.isEmpty() && catalog.byName(bossName) != null)
		{
			List<RaidRecord> list = ctx.state().get(bossName).raids;
			list.clear();
			list.addAll(raids.subList(Math.max(0, raids.size() - RaidRecords.MAX_RAID_RECORDS), raids.size()));
			ctx.raidsChanged.remove(bossName);
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
		for (String name : ctx.killsChanged)
		{
			BossProgress p = ctx.state().peek(name);
			changed.put(name, p == null ? new ArrayList<>() : new ArrayList<>(p.kills));
		}
		ctx.killsChanged.clear();
		return changed;
	}

	/** Puts back a boss's saved History when an account is loaded. */
	public synchronized void setKills(String bossName, List<KillEntry> kills)
	{
		// Recent fills in from History; do that again with the History just loaded
		dayCounts.forget(bossName);
		if (kills != null && !kills.isEmpty() && catalog.byName(bossName) != null)
		{
			List<KillEntry> list = ctx.state().get(bossName).kills;
			list.clear();
			list.addAll(kills.subList(Math.max(0, kills.size() - MAX_KILLS), kills.size()));
		}
	}

	/** Kills kept per boss for the History section. */
	public static final int MAX_KILLS = 500;

	/** Tests only: drops the day counts, as a version from before they existed would have. */
	public synchronized void forgetDaysForTest()
	{
		dayCounts.forgetDaysForTest();
	}

	/** True when the player left this item out of the calculations for this boss. */
	public synchronized boolean isLeftOut(String bossName, String itemName)
	{
		BossProgress p = ctx.state().peek(bossName);
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
		BossProgress p = ctx.state().get(bossName);
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

	/** Where older copies of a shared item go when they came from other monsters, not a boss. */
	public static final String NOT_FROM_A_BOSS = "*not from a boss";

	/**
	 * Loot from a monster On Rate doesn't track as a boss (a blue dragon, a smoke devil): only
	 * looked at for the shared items the log also counts from them. True when the saved data changed.
	 */
	public synchronized boolean onOtherLoot(String source, Collection<LootItem> items)
	{
		return sharedItems.onOtherLoot(source, items);
	}

	/** See {@link #splitPool(String, Map)}, with every older copy from one place. */
	public synchronized boolean splitPool(String key, String olderFrom)
	{
		return sharedItems.splitPool(key, olderFrom);
	}

	/**
	 * Judges a shared item on each boss separately: the copies On Rate saw go to the boss that
	 * dropped them, and the older ones to the places given (boss names, or {@link #NOT_FROM_A_BOSS}
	 * for an item other monsters drop too). They must add up to the older copies there are now.
	 * False, and nothing changes, when they don't or the item can't be split yet.
	 */
	public synchronized boolean splitPool(String key, Map<String, Integer> older)
	{
		return sharedItems.splitPool(key, older);
	}

	/** Judges a shared item on all its bosses together again. */
	public synchronized void unsplitPool(String key)
	{
		sharedItems.unsplitPool(key);
	}

	/** The pool key of a shared item this boss's page judges on its own (the player split it), or null. */
	public synchronized String splitPoolOf(String bossName, String itemName)
	{
		return sharedItems.splitPoolOf(bossName, itemName);
	}

	/** See {@link #counts(BossDefinition, DropDefinition, BossProgress)}, for the panel. */
	public synchronized boolean counts(BossDefinition boss, DropDefinition drop)
	{
		return statistics.counts(boss, drop);
	}

	/** Adds a kill to today's count, with the uniques that one kill should give. */
	private void countKillToday(BossDefinition boss, KillEntry kill)
	{
		DayCount day = dayCounts.dayCount(boss.getName(), kill.time);
		day.kills++;
		Trip before = trips.beforeThisKill();
		BossStats s = before != null && before.boss.equals(boss.getName()) ? statistics.stats(boss.getName(), ctx.settings()) : null;
		// exactly what this kill added to the boss's expected; when the kill count jumped by
		// more than one (first kill after a break) only this one kill's own rates count
		day.expected += s != null && s.getKillCount() == before.startKc + 1
			? Math.max(0, s.getAnyRow().getExpected() - before.startExpected)
			: statistics.expectedFromKill(boss, kill);
	}

	/** Adds a unique to the count of the day it dropped. */
	private void countUniqueToday(BossDefinition boss, DropDefinition drop)
	{
		if (drop.countsTowardAny())
		{
			dayCounts.dayCount(boss.getName(), lastKillMillis > 0 ? lastKillMillis : System.currentTimeMillis()).uniques++;
		}
	}

	/**
	 * GE value the current trip's own kills should give (their delve level, raid points, team),
	 * or NaN without a trip or when History lacks one of its kills.
	 */
	public synchronized double tripValue(java.util.function.ToLongFunction<String> prices)
	{
		return trips.tripValue(prices);
	}

	/**
	 * Kills, uniques and expected per boss over the last {@code days} days (1 = today), most
	 * kills first. The first entry is the total of all bosses (its boss is null).
	 */
	public synchronized List<BossDays> recentDays(int days, long nowMillis)
	{
		return dayCounts.recentDays(days, nowMillis);
	}

	/**
	 * Only the time of a kill or raid, from a message type that never carries kill counts (the
	 * Chambers raid time comes as a friends chat notification). Returns the boss whose History
	 * changed, or null.
	 */
	public synchronized String onTimeMessage(String message, long nowMillis)
	{
		ChatTimes.Reading time = ChatTimes.read(message);
		return time == null ? null : fightTimes.onDuration(time, nowMillis);
	}

	/**
	 * Adds raid times read from another plugin's log (Raid Data Tracker): for one raid mode, when
	 * each raid ended (epoch millis) and how long it took (millis). A raid already imported (same end
	 * time) is skipped, and one On Rate timed itself is not counted twice: its time is used, or
	 * filled in when On Rate saw the raid but not its time. Safe to call again with the same list.
	 *
	 * @return how many times were new
	 */
	public synchronized int importRaidTimes(String bossName, String part, List<long[]> samples)
	{
		return fightTimes.importRaidTimes(bossName, part, samples);
	}

	/**
	 * Adds whole raids read from Raid Data Tracker as saved raids, so their points count for the
	 * purple chance like the raids On Rate saw. Raids On Rate already has are skipped. Safe to call again.
	 *
	 * @return how many raids were added
	 */
	public synchronized int importRaids(String bossName, List<ImportedRaid> raids)
	{
		return raidData.importRaids(bossName, raids);
	}

	/**
	 * How long this boss's kills took (a combined page: one part's), from the kills in History and,
	 * for raids, the times imported from Raid Data Tracker. Null when nothing has a time.
	 */
	public synchronized TimeStats killTimes(String bossName, String partName)
	{
		return fightTimes.killTimes(bossName, partName);
	}

	/** How many raid times of this boss were imported from another plugin (all modes, or one). */
	public synchronized int importedRaidTimes(String bossName, String part)
	{
		return fightTimes.importedRaidTimes(bossName, part);
	}

	/** How long delves of one level took (Doom: 1 to 8, 9 for the deep delves), or null when none has a time. */
	public synchronized TimeStats levelTimes(String bossName, int level)
	{
		return fightTimes.levelTimes(bossName, level);
	}

	/** Adds a unique to the kill it came from (the latest kill at that kill count). */
	private void addDropToHistory(BossDefinition boss, BossProgress progress, int kc, KillEntry.DropEntry drop)
	{
		KillEntry latest = progress.kills.isEmpty() ? null : progress.kills.get(progress.kills.size() - 1);
		if (latest == null || latest.kc != kc || !latest.isDateKnown())
		{
			latest = historyBook.addKill(boss, progress, lastKillMillis > 0 ? lastKillMillis : System.currentTimeMillis());
			latest.kc = kc;
		}
		if (latest.drops == null)
		{
			latest.drops = new ArrayList<>();
		}
		latest.drops.add(drop);
		ctx.killsChanged.add(boss.getName());
	}

	/** This boss's saved kills, newest first, with each raid's points and purple chance. */
	public synchronized List<HistoryRow> history(String bossName)
	{
		return historyBook.history(bossName);
	}

	/** Kills saved for this boss (at most {@link #MAX_KILLS}). */
	public synchronized int killsSeen(String bossName)
	{
		return historyBook.killsSeen(bossName);
	}

	/** The latest uniques across every boss, newest first. */
	public synchronized List<RecentDrop> recentDrops(int limit)
	{
		return historyBook.recentDrops(limit);
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
		return historyBook.removeFromHistory(bossName, kill, drop);
	}

	/**
	 * Why a drop added by hand can't be ranked in Luckiest drops / Longest waits, or null when it can
	 * (or isn't in History): its wait isn't known, or the boss's drop rate isn't.
	 */
	public synchronized String pastDropNotRankedReason(String bossName, String itemName, int kc)
	{
		return historyBook.pastDropNotRankedReason(bossName, itemName, kc);
	}

	/** As above for a drop entered at a part of a combined page. */
	public synchronized String pastDropNotRankedReason(String bossName, String itemName, int kc, String partName)
	{
		return historyBook.pastDropNotRankedReason(bossName, itemName, kc, partName);
	}

	/**
	 * The drops of one item at one boss in History (seen or added by hand) whose wait is known
	 * exactly, luckiest first, for the item's hover.
	 */
	public synchronized List<RecentDrop> dropsOf(String bossName, String itemName)
	{
		return historyBook.dropsOf(bossName, itemName);
	}

	/**
	 * Drops across every boss whose wait is known exactly and was lucky (shorter than the drop rate
	 * gives on average) or dry (longer), best first: luckiest are ranked by the smallest multiple of
	 * the drop rate, dry ones by the largest. Drops added by hand count; undated ones are fine.
	 */
	public synchronized List<RecentDrop> dropsByLuck(boolean lucky, int limit)
	{
		return historyBook.dropsByLuck(lucky, limit);
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
		return historyBook.addPastDrop(bossName, itemName, kc);
	}

	/**
	 * As {@link #addPastDrop(String, String, int)}, with {@code first} true when the player says
	 * this was their first copy: its wait is then its kill count, even when the collection log
	 * shows more copies. Ignored when an earlier copy is already listed (the gap since it is used).
	 */
	public synchronized boolean addPastDrop(String bossName, String itemName, int kc, boolean first)
	{
		return historyBook.addPastDrop(bossName, itemName, kc, first);
	}

	/** Most kills a past drop can be entered at: the page's kill count, or the part's own. 0 for a page whose kills are unknown. */
	public synchronized int pastDropLimit(String bossName, String partName)
	{
		return historyBook.pastDropLimit(bossName, partName);
	}

	/** Most a past drop can be entered at for a part whose kill count On Rate doesn't know. */
	public static final int UNKNOWN_PART_LIMIT = 100_000;

	/**
	 * The parts of a combined page a drop can be entered at: the ones the item drops from (all of
	 * them when none has a known rate). Empty for a page without parts.
	 */
	public synchronized List<String> pastDropParts(String bossName, String itemName)
	{
		return historyBook.pastDropParts(bossName, itemName);
	}

	/**
	 * As above for a page with parts: {@code partName} is the mode or boss ("Challenge Mode",
	 * "Artio") and {@code kc} that part's own kill count, as the game's chat shows it. The wait and
	 * its luck use that part's kills and drop rate. The item's dry streak counts the page's kills,
	 * which aren't known for that moment, so it stays as it is. Null for a page without parts.
	 */
	public synchronized boolean addPastDrop(String bossName, String itemName, int kc, boolean first, String partName)
	{
		return historyBook.addPastDrop(bossName, itemName, kc, first, partName);
	}

	/** Group bosses: how many kills On Rate saw per team size. Empty for other bosses. */
	public synchronized Map<Integer, Integer> teamKills(String bossName)
	{
		BossProgress p = ctx.state().peek(bossName);
		return p == null ? Collections.emptyMap() : new TreeMap<>(p.teamKills);
	}

	/** The share the kills from before team counting use at a group boss, or null while they count as solo. */
	public synchronized Double olderShare(String bossName)
	{
		BossProgress p = ctx.state().peek(bossName);
		return p == null ? null : p.olderShare;
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
		int size = ctx.teamSize().applyAsInt(boss.getName());
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
		return raidData.averagePurpleChance(bossName, part);
	}

	/** Settings used for streaks and milestone messages (raid chances, team shares). */
	public synchronized void setSettings(RaidSettings settings)
	{
		ctx.setSettings(settings);
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
		ctx.setState(state == null ? new TrackerState() : state);
		clogSync.clear();
		ctx.killsChanged.clear();
		ctx.raidsChanged.clear();
		// another account: nothing of the last one's session carries over
		dayCounts.clear();
		outbox.clear();
		pendingWave = null;
		lastPart = null;
		lastKillMillis = 0;
		raidData.clear();
		lastBoss = null;
		trips.clear();
		// raids saved inside the main data by earlier versions move to their own key
		if (ctx.state().bosses != null)
		{
			for (Map.Entry<String, BossProgress> e : ctx.state().bosses.entrySet())
			{
				BossProgress p = e.getValue();
				p.ensureMaps();
				if (p.savedRaids != null && !p.savedRaids.isEmpty())
				{
					p.raids.addAll(p.savedRaids);
					ctx.raidsChanged.add(e.getKey());
				}
				p.savedRaids = null;
			}
		}
	}

	public synchronized TrackerState getState()
	{
		return ctx.state();
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
			BossProgress progress = ctx.state().get(boss.getName());
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
				trips.beforeKill(boss, nowMillis);
			}
			Integer previous = progress.chatKc.put(BossCatalog.normalize(counter), kc);
			Integer team = null;
			if (boss.isDetectTeam() && (previous == null || kc > previous))
			{
				int size = ctx.teamSize().applyAsInt(boss.getName());
				if (size > 0)
				{
					progress.teamKills.merge(size, 1, Integer::sum);
					team = size;
				}
			}
			if (previous == null || kc > previous)
			{
				KillEntry entry = historyBook.addKill(boss, progress, nowMillis);
				if (boss.hasParts())
				{
					// on a combined page each mode or boss has its own count
					entry.partKc = kc;
					entry.part = Statistics.partForName(boss, counter, true);
				}
				entry.team = team;
				countKillToday(boss, entry);
				fightTimes.offerDuration(boss.getName(), entry, nowMillis);
			}
			markKill(boss, nowMillis);
			trips.afterKill(boss, nowMillis);
			lastPart = Statistics.partForName(boss, counter, true);
			statistics.startTracking(boss);
			raidData.onRaidCompletion(boss, lastPart, kc, nowMillis);
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
			ChatTimes.Reading delveTime = ChatTimes.read(plain);
			return onDelveLevel(level.endsWith("+") ? BossDefinition.DELVE_LEVELS : Integer.parseInt(level), nowMillis,
				delveTime == null ? -1 : delveTime.getMillis());
		}

		ChatTimes.Reading time = ChatTimes.read(message);
		if (time != null)
		{
			return fightTimes.onDuration(time, nowMillis);
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
		return raidData.onRaidRecord(record, nowMillis);
	}

	/**
	 * Your exact points for the latest Tombs of Amascut raid, sent by the game a moment after
	 * the raid was saved: they replace On Rate's estimate in it. Returns the boss, or null.
	 */
	public synchronized String onToaPoints(int personalPoints, long nowMillis)
	{
		return raidData.onToaPoints(personalPoints, nowMillis);
	}

	/**
	 * Team and personal points the Tombs of Amascut plugin sends at the end of a raid.
	 * They replace On Rate's own estimate for the latest ToA raid.
	 */
	public synchronized String onPartyPoints(int personalPoints, int teamPoints, long nowMillis)
	{
		return raidData.onPartyPoints(personalPoints, teamPoints, nowMillis);
	}

	/**
	 * Saves today's account luck for the graph. One point per day: a later call on the same
	 * day replaces it. Returns true when something was stored.
	 */
	public synchronized boolean recordHistory(long day, RaidSettings raids)
	{
		return historyBook.recordHistory(day, raids);
	}

	/** The luck graph's points, oldest first. */
	public synchronized List<HistoryPoint> history()
	{
		return historyBook.history();
	}

	/** Completions of this raid mode whose purple chance came from your points. */
	public synchronized int measuredRaids(String bossName, String part)
	{
		return raidData.measuredRaids(bossName, part);
	}

	/** Every raid saved for this boss, oldest first. */
	public synchronized List<RaidRecord> raidRecords(String bossName)
	{
		return raidData.raidRecords(bossName);
	}

	/**
	 * A raid's setups (purple chance and GP value an hour), personal bests and points per raid,
	 * from the raids On Rate saw. {@code prices} may be null (no GP value).
	 */
	public synchronized RaidInsights raidInsights(String bossName, ToLongFunction<String> prices)
	{
		return RaidInsights.of(catalog.byName(bossName), ctx.state().peek(bossName), ctx.settings(), prices,
			statistics::counts, raidData.importedUniques(bossName),
			raidData.sectionOnly(bossName), raidData.sectionUniques());
	}

	/** {@link #raidHabits()} worked out last time, and the raids it came from. */
	private RaidHabits habits;
	private long habitsKey = Long.MIN_VALUE;

	/**
	 * How you usually raid (average purple chance, usual team size), from the raids On Rate saw,
	 * for the raids it didn't see. Worked out again only when a raid was added or changed.
	 */
	public synchronized RaidHabits raidHabits()
	{
		long key = 17;
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = boss.getRaid() == null ? null : ctx.state().peek(boss.getName());
			if (p != null && !p.raids.isEmpty())
			{
				RaidRecord last = p.raids.get(p.raids.size() - 1);
				key = key * 31 + p.raids.size();
				key = key * 31 + last.savedMillis + last.personalPoints + last.teamSize;
			}
		}
		if (habits == null || key != habitsKey || ctx.state() != habitsState)
		{
			habits = RaidHabits.of(catalog.getBosses(), name -> ctx.state().peek(name));
			habitsKey = key;
			habitsState = ctx.state();
		}
		return habits;
	}

	/** The account {@link #habits} belong to: another account loaded starts over. */
	private Object habitsState;

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

	private String onDelveLevel(int level, long nowMillis, int durationMillis)
	{
		BossDefinition boss = delveBoss();
		if (boss == null || level < 1 || level > BossDefinition.DELVE_LEVELS)
		{
			return null;
		}
		BossProgress progress = ctx.state().get(boss.getName());
		trips.beforeKill(boss, nowMillis);
		progress.levelKc.merge(level, 1, Integer::sum);
		KillEntry entry = historyBook.addKill(boss, progress, nowMillis);
		entry.level = level;
		// the delve message carries its own time
		entry.durationMillis = durationMillis > 0 ? durationMillis : null;
		countKillToday(boss, entry);
		markKill(boss, nowMillis);
		trips.afterKill(boss, nowMillis);
		statistics.startTracking(boss);
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
		BossProgress progress = ctx.state().get(boss.getName());
		progress.levelsSynced = true;
		for (Map.Entry<Integer, Integer> e : levels.entrySet())
		{
			if (e.getKey() >= 1 && e.getKey() <= BossDefinition.DELVE_LEVELS)
			{
				progress.levelKc.put(e.getKey(), e.getValue());
			}
		}
		statistics.startTracking(boss);
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
		DropDefinition drop = Statistics.findDrop(boss, itemName);
		if (drop == null || drop.isPet())
		{
			return null;
		}
		recordDrop(boss, drop, itemName);
		ctx.state().get(boss.getName()).received.merge(BossCatalog.normalize(itemName), 1, Integer::sum);
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
		ctx.state().get(boss.getName()).received.merge(BossCatalog.normalize(pet.getName()), 1, Integer::sum);
		return boss.getName();
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
		BossProgress progress = ctx.state().get(boss.getName());
		String key = BossCatalog.normalize(itemName);
		int kc = statistics.currentKc(boss);
		Integer previous = progress.lastDropKc.get(key);
		Integer before = progress.received.get(key);
		double rate = statistics.rateOf(boss, drop);

		String unit = unit(boss);
		String name = drop.getDisplayName().equals(drop.getName()) ? itemName : drop.getDisplayName();
		// "first" is only certain when the collection log told us the history of this item
		boolean historyKnown = progress.clogSynced && !catalog.isClogShared(key);
		String text = null;
		if (previous != null && kc > previous)
		{
			int kills = kc - previous;
			text = String.format(java.util.Locale.ROOT, "%s after %,d %s (%s the drop rate).", name, kills, units(boss, kills), multiple(kills * rate));
		}
		else if (historyKnown && (before == null || before == 0) && kc > 0)
		{
			text = String.format(java.util.Locale.ROOT, "Your first %s, after %,d %s (%s the drop rate).", name, kc, units(boss, kc), multiple(kc * rate));
		}
		else if (progress.trackingStartKc != null && kc > progress.trackingStartKc)
		{
			int kills = kc - progress.trackingStartKc;
			text = String.format(java.util.Locale.ROOT, "%s after at least %,d %s.", name, kills, units(boss, kills));
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
			// three significant digits: a very lucky drop (0.0004x) must not round to 0, and it keeps the history short
			entry.multiple = roundMultiple(entry.since * rate);
		}
		addDropToHistory(boss, progress, kc, entry);
		countUniqueToday(boss, drop);
		progress.lastDropKc.put(key, kc);
		progress.milestone.remove(key);
	}

	/** A multiple of the drop rate to three significant digits (0.000412, 0.0732, 1.23, 45.6). */
	static double roundMultiple(double m)
	{
		if (m <= 0 || Double.isNaN(m) || Double.isInfinite(m))
		{
			return 0;
		}
		double scale = Math.pow(10, 2 - Math.floor(Math.log10(m)));
		return Math.round(m * scale) / scale;
	}

	private static String multiple(double m)
	{
		if (m < 0.1)
		{
			return String.format(java.util.Locale.ROOT, "%.2fx", m);
		}
		return m < 10 ? String.format(java.util.Locale.ROOT, "%.1fx", m) : String.format(java.util.Locale.ROOT, "%.0fx", m);
	}

	/**
	 * After a kill: announce items that just passed 2x, 3x, 5x or 10x the drop rate
	 * without dropping. Several at once become one message. A big jump in kill count
	 * (first sync, first message after installing) only catches up quietly.
	 */
	private void checkMilestones(BossDefinition boss)
	{
		BossProgress progress = ctx.state().get(boss.getName());
		BossStats s = statistics.stats(boss.getName(), ctx.settings());
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
			if (!statistics.counts(boss, drop, progress) || row.getStreak() == null || row.getRate() <= 0)
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
				? String.format(java.util.Locale.ROOT, "%,d %s without one", row.getStreak(), units(boss, row.getStreak()))
				: String.format(java.util.Locale.ROOT, "at least %,d %s since your last one", row.getStreak(), units(boss, row.getStreak()));
			outbox.add(new Note(Note.Kind.MILESTONE, String.format(java.util.Locale.ROOT, "%s: %s, %dx the drop rate.",
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
			? String.format(java.util.Locale.ROOT, "%s: %d items passed %dx the drop rate dry: %s.", boss.getName(), names.size(), top, list)
			: String.format(java.util.Locale.ROOT, "%s: %d items passed a dry milestone: %s.", boss.getName(), names.size(), list)));
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
	 * Handles a loot tracker event. Returns the boss whose numbers changed, or null.
	 */
	public synchronized String onLoot(String source, int amount, Collection<LootItem> items, long nowMillis)
	{
		BossDefinition boss = catalog.byLootName(source);
		if (boss == null)
		{
			return null;
		}
		BossProgress progress = ctx.state().get(boss.getName());
		boolean minion = catalog.isMinionLoot(source);
		String titan = BossCatalog.normalize(source).contains("branda") ? "Branda"
			: BossCatalog.normalize(source).contains("eldric") ? "Eldric" : null;
		KillEntry latest = progress.kills.isEmpty() ? null : progress.kills.get(progress.kills.size() - 1);
		if (titan != null && latest != null && latest.kc == statistics.currentKc(boss))
		{
			latest.titan = titan;
			ctx.killsChanged.add(boss.getName());
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
				trips.beforeKill(boss, nowMillis);
			}
			// a bodyguard's loot counts for its general, but it isn't a kill of the general
			progress.lootKills += Math.max(1, amount);
			if (boss.getKcNames().isEmpty())
			{
				// no kill count message for this boss: the loot is the kill
				trips.afterKill(boss, nowMillis);
			}
		}
		if (boss.hasParts())
		{
			lastPart = Statistics.partForName(boss, source, false);
		}

		for (LootItem item : items)
		{
			DropDefinition drop = Statistics.findDrop(boss, item.name);
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
				if (pool != null && ctx.state().sharedClog().containsKey(pool))
				{
					ctx.state().sharedClog().merge(pool, 1, Integer::sum);
				}
				if (pool == null)
				{
					// a shared item this boss isn't one of the bosses of (onyx from Zalcano)
					sharedItems.recordElsewhere(boss, item.name);
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
		statistics.startTracking(boss);
		return boss.getName();
	}

	/** Fortis Colosseum: the wave a claim right now would be for, from the last wave message. */
	private Integer pendingWave;

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
		trips.beforeKill(boss, nowMillis);
		if (progress.waveClaims == null)
		{
			progress.waveClaims = new TreeMap<>();
		}
		progress.waveClaims.merge(wave, 1, Integer::sum);
		KillEntry entry = historyBook.addKill(boss, progress, nowMillis);
		entry.level = wave;
		countKillToday(boss, entry);
		markKill(boss, nowMillis);
		trips.afterKill(boss, nowMillis);
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
		BossProgress progress = ctx.state().get(boss.getName());
		for (int i = 0; i < rolls; i++)
		{
			trips.beforeKill(boss, nowMillis);
			progress.lootKills++;
			KillEntry entry = historyBook.addKill(boss, progress, nowMillis);
			countKillToday(boss, entry);
			markKill(boss, nowMillis);
			trips.afterKill(boss, nowMillis);
		}
		statistics.startTracking(boss);
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
		BossProgress progress = ctx.state().get(boss.getName());
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
		return clogSync.coversPage(pageTitle);
	}

	/**
	 * A collection log page was opened but could not be read. Changes no saved data. Pass a null or
	 * empty title when not even the title could be read.
	 *
	 * @return true when this is worth telling the player: the page is one we cover, or the title is unknown
	 */
	public synchronized boolean onClogPageUnreadable(String pageTitle)
	{
		return clogSync.onClogPageUnreadable(pageTitle);
	}

	/** True when a log page of this boss was opened this session but could not be read. */
	public synchronized boolean logPageUnreadable(String bossName)
	{
		return clogSync.logPageUnreadable(bossName);
	}

	/** Titles of the pages we cover that were opened but could not be read this session. */
	public synchronized List<String> unreadablePages()
	{
		return clogSync.unreadablePages();
	}

	/** True when a log page was opened whose title could not be read. */
	public synchronized boolean isHeaderUnreadable()
	{
		return clogSync.isHeaderUnreadable();
	}

	/**
	 * True when applying this page would set a count we already hold above zero back to zero: every
	 * item reads as not obtained, but a drop on the page is saved as received. The log never loses
	 * items, so this is a page that was misread, not a player who lost drops. A fresh account whose
	 * page is truly all faded has nothing saved and is not flagged.
	 */
	public synchronized boolean wouldZeroSaved(String pageTitle, Map<String, Integer> items)
	{
		return clogSync.wouldZeroSaved(pageTitle, items);
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
		return clogSync.onClogPage(pageTitle, counters, items);
	}

	/** The boss with the highest known kill count, used to pick what to show on login. */
	public synchronized String bossWithMostKills()
	{
		return clogSync.bossWithMostKills();
	}

	/** Label used in the checklist and reminders for the Doom scoreboard. */
	public static final String DELVE_SCOREBOARD = "Doom scoreboard";

	/**
	 * The setup checklist: every collection log page the plugin can read, in alphabetical
	 * order, plus the Doom scoreboard. A page counts as synced once every boss on it is.
	 */
	public synchronized List<SyncStatus> syncChecklist()
	{
		return clogSync.syncChecklist();
	}

	/**
	 * What the player should open to load this boss's history, or null when nothing is
	 * missing (or the boss has no collection log page we can read).
	 */
	public synchronized String syncReminder(String bossName)
	{
		return statistics.syncReminder(bossName);
	}

	public synchronized void reset(String bossName)
	{
		clogSync.forget(bossName);
		if (ctx.state().bosses != null && ctx.state().bosses.remove(bossName) != null)
		{
			ctx.killsChanged.add(bossName);
			ctx.raidsChanged.add(bossName);
		}
		// Recent's day counts, the trip and the last-kill pointers must not outlive the reset
		for (Map<String, DayCount> forDay : ctx.state().days().values())
		{
			forDay.remove(bossName);
		}
		dayCounts.forget(bossName);
		trips.forget(bossName);
		if (bossName.equals(lastBoss))
		{
			lastBoss = null;
		}
	}

	/** True for content where one completion takes long: a raid, the Inferno or the Fight Caves. */
	public static boolean isLongRun(BossDefinition boss)
	{
		return Trips.isLongRun(boss);
	}

	/** How long without a kill ends the current trip (0 when there is none). */
	public synchronized long tripIdleLimitMillis()
	{
		return trips.tripIdleLimitMillis();
	}

	/** Forget the current trip; the next kill starts a new one. */
	public synchronized void resetTrip()
	{
		trips.resetTrip();
	}

	/** The last unique On Rate saw in History (any boss), or null; with when History starts. */
	public synchronized LastUnique lastUnique()
	{
		return historyBook.lastUnique();
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
		return trips.tripStats();
	}

	private void markKill(BossDefinition boss, long nowMillis)
	{
		lastBoss = boss.getName();
		lastKillMillis = nowMillis;
	}

	public synchronized BossStats stats(String bossName, RaidSettings raids)
	{
		return statistics.stats(bossName, raids);
	}

	/**
	 * Every boss with kills or drops, added up: how dry or spooned the account is overall.
	 */
	public synchronized AccountStats accountStats(RaidSettings raids)
	{
		return statistics.accountStats(raids);
	}

	/**
	 * Same as {@link #accountStats(RaidSettings)}, plus GE value when {@code prices} is given.
	 * Prices are per item name; untradeable items have price 0, so they count for the number
	 * of uniques but not for value.
	 */
	public synchronized AccountStats accountStats(RaidSettings raids, ToLongFunction<String> prices)
	{
		return statistics.accountStats(raids, prices);
	}

	/**
	 * Every item several bosses drop. Complete when the log total is known and every boss
	 * that drops it has a known kill count; otherwise lists the pages still to open.
	 */
	public synchronized List<SharedPool> sharedPools(RaidSettings raids)
	{
		return statistics.sharedPools(raids);
	}

	/** The shared items this boss drops (with every boss that drops them), for its page. */
	public synchronized List<SharedPool> sharedPoolsFor(String bossName, RaidSettings raids)
	{
		return statistics.sharedPoolsFor(bossName, raids);
	}

}
