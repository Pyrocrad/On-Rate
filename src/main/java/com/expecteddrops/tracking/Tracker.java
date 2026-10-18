package com.expecteddrops.tracking;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.BossPart;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.AccountStats;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RaidChance;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

	private static final int[] MILESTONES = {1, 2, 3, 5, 10};
	/** A kill count jump bigger than this (a sync, a first message) doesn't announce milestones. */
	private static final int MAX_QUIET_JUMP = 5;

	private final BossCatalog catalog;
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
		lastBoss = null;
		trip = null;
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
			state.get(boss.getName()).chatKc.put(BossCatalog.normalize(counter), kc);
			markKill(boss, nowMillis);
			afterKill(boss, nowMillis);
			lastPart = partForName(boss, counter, true);
			startTracking(boss);
			onRaidCompletion(boss, lastPart, kc, nowMillis);
			checkMilestones(boss);
			return boss.getName();
		}

		String plain = message.replaceAll("<[^>]*>", "");
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
			latestToa.personalPoints = personalPoints;
			latestToa.teamPoints = teamPoints;
			latestToa = null;
			for (BossDefinition boss : catalog.getBosses())
			{
				if ("toa".equals(boss.getRaid()))
				{
					return boss.getName();
				}
			}
			return null;
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
		record.part = part == null ? bossName : part;
		record.kc = kc;
		List<RaidRecord> raids = state.get(bossName).raids;
		raids.add(record);
		while (raids.size() > MAX_RAID_RECORDS)
		{
			raids.remove(0);
		}
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
		state.get(boss.getName()).levelKc.merge(level, 1, Integer::sum);
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
	static String unit(BossDefinition boss)
	{
		if (boss.getRaid() != null)
		{
			return "raids";
		}
		if (boss.isDelve())
		{
			return "levels";
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
			text = String.format("%s after %,d %s (%s the rate).", name, kills, unit, multiple(kills * rate));
		}
		else if (historyKnown && (before == null || before == 0) && kc > 0)
		{
			text = String.format("Your first %s, after %,d %s (%s the rate).", name, kc, unit, multiple(kc * rate));
		}
		else if (progress.trackingStartKc != null && kc > progress.trackingStartKc)
		{
			int kills = kc - progress.trackingStartKc;
			text = String.format("%s after at least %,d %s.", name, kills, unit);
		}
		if (text != null && rate > 0)
		{
			outbox.add(new Note(Note.Kind.DROP, text));
		}
		progress.lastDropKc.put(key, kc);
		addDropKc(progress, key, kc);
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
	 * After a kill: announce items that just passed 1x, 2x, 3x, 5x or 10x the drop rate
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
			DropDefinition drop = boss.getDrops().get(i);
			if (!drop.countsTowardAny() || row.getStreak() == null || row.getRate() <= 0)
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
				? String.format("%,d %s without one", row.getStreak(), unit)
				: String.format("at least %,d %s since your last one", row.getStreak(), unit);
			outbox.add(new Note(Note.Kind.MILESTONE, String.format("%s: %dx the drop rate dry (%s).",
				names.get(0), levels.get(0), streak)));
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
		progress.lootKills += Math.max(1, amount);
		if (boss.getKcNames().isEmpty())
		{
			// no kill count message for this boss: the loot is the kill
			afterKill(boss, nowMillis);
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
		}
		markKill(boss, nowMillis);
		startTracking(boss);
		return boss.getName();
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
						Integer before = progress.received.get(key);
						if (before != null && qty > before && progress.trackingStartKc != null)
						{
							// a new copy since the last look; the best guess for when is "now"
							progress.lastDropKc.put(key, currentKc(boss));
							addDropKc(progress, key, currentKc(boss));
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
		List<SyncStatus> result = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = state.peek(boss.getName());
			for (String page : boss.getClogPages())
			{
				bossesByPage.computeIfAbsent(page, k -> new ArrayList<>()).add(boss.getName());
				boolean synced = p != null && p.clogSynced;
				syncedByPage.merge(page.toLowerCase(Locale.ROOT), synced, Boolean::logicalAnd);
			}
			if (boss.isDelve())
			{
				result.add(new SyncStatus(DELVE_SCOREBOARD, Collections.singletonList(boss.getName()),
					p != null && p.levelsSynced));
			}
		}
		List<SyncStatus> pages = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : bossesByPage.entrySet())
		{
			pages.add(new SyncStatus(e.getKey(), e.getValue(),
				syncedByPage.getOrDefault(e.getKey().toLowerCase(Locale.ROOT), false)));
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
		if (state.bosses != null)
		{
			state.bosses.remove(bossName);
		}
	}

	/** Most drops remembered per item for the drop history. */
	private static final int MAX_DROP_HISTORY = 500;
	/** A trip ends after this long without a kill, or when you kill another boss. */
	static final long TRIP_IDLE_MILLIS = 30 * 60_000;

	private Trip trip;

	private static void addDropKc(BossProgress progress, String key, int kc)
	{
		List<Integer> list = progress.dropKcs.computeIfAbsent(key, k -> new ArrayList<>());
		list.add(kc);
		while (list.size() > MAX_DROP_HISTORY)
		{
			list.remove(0);
		}
	}

	/**
	 * Every drop On Rate saw at this boss, per item label (as in the rows), with the kill
	 * count it came at. Drops from before On Rate was installed aren't known.
	 */
	public synchronized DropHistory dropHistory(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		BossProgress progress = state.peek(bossName);
		BossStats s = stats(bossName, settings);
		Map<String, List<Integer>> drops = new LinkedHashMap<>();
		if (boss != null)
		{
			for (DropDefinition drop : boss.getDrops())
			{
				List<Integer> kcs = new ArrayList<>();
				if (progress != null)
				{
					for (String name : drop.getAllNames())
					{
						List<Integer> list = progress.dropKcs.get(BossCatalog.normalize(name));
						if (list != null)
						{
							kcs.addAll(list);
						}
					}
				}
				Collections.sort(kcs);
				drops.put(drop.getDisplayName(), kcs);
			}
		}
		return new DropHistory(s == null ? 0 : s.getKillCount(), progress == null ? null : progress.trackingStartKc, drops);
	}

	/**
	 * Called after each kill: continues the current trip, or starts a new one when this is
	 * another boss or the last kill was a while ago. The new trip includes this kill.
	 */
	private void afterKill(BossDefinition boss, long nowMillis)
	{
		if (trip != null && trip.boss.equals(boss.getName()) && nowMillis - trip.lastMillis <= TRIP_IDLE_MILLIS)
		{
			trip.lastMillis = nowMillis;
			return;
		}
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
		trip = new Trip(boss.getName(), nowMillis, s.getKillCount() - 1,
			any.getExpected() - any.getRate(), any.getReceived(), received);
	}

	/** Forget the current trip; the next kill starts a new one. */
	public synchronized void resetTrip()
	{
		trip = null;
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
		int completions, BossProgress progress)
	{
		double perRaid = partRate(boss, part, drop, raids);
		if (progress == null || part.getRaid() == null || drop.hasPartRates() || progress.raids.isEmpty())
		{
			return completions * perRaid;
		}
		List<RaidRecord> known = new ArrayList<>();
		for (RaidRecord r : progress.raids)
		{
			if (part.getName().equals(r.part) && !Double.isNaN(recordChance(r, part, raids)))
			{
				known.add(r);
			}
		}
		int counted = Math.min(known.size(), completions);
		if (counted == 0)
		{
			return completions * perRaid;
		}
		// share of a purple that is this item (the same for every raid of this mode)
		double share = partRate(boss, part, drop, key -> 1.0);
		double expected = (completions - counted) * perRaid;
		for (RaidRecord r : known.subList(known.size() - counted, known.size()))
		{
			expected += recordChance(r, part, raids) * share * RaidChance.toaLevelFactor(r.raidLevel, drop.getMinRaidLevel());
		}
		return expected;
	}

	/** Per-kill rate for a drop, taking raid settings into account. */
	public static double effectiveRate(BossDefinition boss, DropDefinition drop, RaidSettings raids)
	{
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
		else if (progress != null)
		{
			int chat = combine(progress.chatKc.values(), boss.combineKcWithMax());
			int clog = combine(progress.clogKc.values(), boss.combineKcWithMax());
			known = !progress.chatKc.isEmpty() || !progress.clogKc.isEmpty();
			kc = known ? Math.max(chat, clog) : progress.lootKills;
			synced = progress.clogSynced;
		}

		List<RowStats> rows = new ArrayList<>();
		double anyExpected = 0;
		int anyReceived = 0;
		for (DropDefinition drop : boss.getDrops())
		{
			double expected;
			double rate;
			if (parts != null)
			{
				expected = 0;
				for (BossPart part : boss.getParts())
				{
					expected += partExpected(boss, part, drop, raids, parts.get(part.getName()), progress);
				}
				rate = kc > 0 ? expected / kc : 0;
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
				if (boss.getTeam() != null && !drop.isNoShare())
				{
					// team boss: your chance scales with your share of the damage
					rate *= raids.contribution(boss.getTeam(), kc);
				}
				expected = rate * kc;
			}
			int received = 0;
			if (progress != null)
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
			if (!(drop.isOnce() && received > 0))
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
			if (drop.isOnce())
			{
				// The collection log counts these at most once, so for luck it's "have it or not".
				received = Math.min(received, 1);
				row = row.once(received);
			}
			rows.add(row);
			if (drop.countsTowardAny())
			{
				// For once-only items the total counts the chance of having one by now.
				anyExpected += drop.isOnce() ? DropMath.chanceAtLeastOne(expected) : expected;
				anyReceived += received;
			}
		}
		RowStats any = new RowStats("Any", null, 0, anyExpected, anyReceived, true);
		// "Any" streak: kills since the most recent unique On Rate knows of.
		double anyRate = 0;
		Integer lastAny = null;
		for (int i = 0; i < boss.getDrops().size(); i++)
		{
			DropDefinition drop = boss.getDrops().get(i);
			if (!drop.countsTowardAny())
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
		// Shared items: what the per-boss rows counted, and their price, to top up with the log total.
		Map<String, Integer> sharedCounted = new HashMap<>();
		Map<String, Long> sharedPrice = new HashMap<>();

		List<RowStats> rows = new ArrayList<>();
		Map<String, Integer> killCounts = new LinkedHashMap<>();
		double expected = 0;
		int received = 0;
		for (BossDefinition boss : catalog.getBosses())
		{
			BossStats s = stats(boss.getName(), raids);
			RowStats any = s.getAnyRow();
			if (s.getKillCount() <= 0 && any.getReceived() <= 0)
			{
				continue;
			}
			RowStats bossRow = new RowStats(boss.getName(), iconFor(boss), 0, any.getExpected(), any.getReceived(), false);
			double driest = Double.NaN;
			String driestItem = null;
			for (int i = 0; i < boss.getDrops().size(); i++)
			{
				RowStats r = s.getRows().get(i);
				double m = r.getStreakMultiple();
				if (boss.getDrops().get(i).countsTowardAny() && !Double.isNaN(m) && (Double.isNaN(driest) || m > driest))
				{
					driest = m;
					driestItem = r.getLabel();
				}
			}
			rows.add(bossRow.withDriest(driest, driestItem));
			for (int i = 0; i < boss.getDrops().size(); i++)
			{
				DropDefinition drop = boss.getDrops().get(i);
				String key = BossCatalog.normalize(drop.getName());
				if (drop.countsTowardAny() && catalog.isClogShared(key))
				{
					sharedCounted.merge(key, s.getRows().get(i).getReceived(), Integer::sum);
					if (prices != null)
					{
						sharedPrice.put(key, Math.max(0, prices.applyAsLong(drop.getName())));
					}
				}
			}
			if (prices != null)
			{
				for (int i = 0; i < boss.getDrops().size(); i++)
				{
					DropDefinition drop = boss.getDrops().get(i);
					RowStats row = s.getRows().get(i);
					if (!drop.countsTowardAny())
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
					expectedValue += row.getExpected() * price;
					receivedValue += row.getReceived() * price;
				}
			}
			killCounts.put(boss.getName(), s.getKillCount());
			expected += any.getExpected();
			received += any.getReceived();
		}
		// The collection log total of a shared item is exact for the whole account: use it
		// where it's higher than what the per-boss rows could count.
		for (Map.Entry<String, Integer> e : sharedCounted.entrySet())
		{
			Integer logTotal = state.sharedClog().get(e.getKey());
			if (logTotal != null && logTotal > e.getValue())
			{
				int extra = logTotal - e.getValue();
				received += extra;
				receivedValue += extra * sharedPrice.getOrDefault(e.getKey(), 0L);
			}
		}
		rows.sort((a, b) -> Double.compare(b.getExpected(), a.getExpected()));
		RowStats total = new RowStats("All", null, 0, expected, received, true);
		AccountStats account = new AccountStats(total, rows, killCounts);
		if (prices != null)
		{
			double[] lambdas = new double[valueExpected.size()];
			long[] itemPrices = new long[valuePrices.size()];
			for (int i = 0; i < lambdas.length; i++)
			{
				lambdas[i] = valueExpected.get(i);
				itemPrices[i] = valuePrices.get(i);
			}
			double luck = DropMath.valueLuck(lambdas, itemPrices, receivedValue, 5000, 1234567L);
			account.setValue(expectedValue, receivedValue, luck);
		}
		return account;
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
