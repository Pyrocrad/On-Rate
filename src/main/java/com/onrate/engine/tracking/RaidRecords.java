package com.onrate.engine.tracking;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import com.onrate.engine.data.*;
import com.onrate.engine.stats.RaidChance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raid data that arrives in pieces: the points (from the raid's own interface or another plugin) and
 * the completion count message come seconds apart in either order, the Tombs of Amascut team total
 * and party points can follow later, and the Fractured Archive damage share may come just after. They
 * are matched within each raid's window and saved as one record per completion. Also the raid
 * records themselves: how many were measured, the average purple chance, and what a raid wait expects.
 * <p>
 * Package-private and unsynchronized: reached only through {@link Tracker}, under its lock.
 */
final class RaidRecords
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;
	private final Statistics statistics;
	private final DayCounts dayCounts;

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
	/** The latest Fractured Archive raid, for the damage share that may come just after it. */
	private RaidRecord latestFractured;
	private String latestFracturedBoss;
	private long latestFracturedMillis;
	private int[] pendingParty;
	private long pendingPartyMillis;
	/** Points and the completion count message arrive within seconds of each other. */
	private static final long RAID_MATCH_MILLIS = 60_000;
	/** The Theatre of Blood board can be read a while after the completion message. */
	private static final long TOB_MATCH_MILLIS = 5 * 60_000;
	private static final long PARTY_MATCH_MILLIS = 2 * 60_000;
	/** Most raid records kept per raid. */
	static final int MAX_RAID_RECORDS = 5000;
	/**
	 * Your uniques from raids read from Raid Data Tracker, per boss and "mode#completion count". Only
	 * kept while the account is loaded (they are read again at every login), never saved: History
	 * stays what On Rate saw itself.
	 */
	private final Map<String, Map<String, String>> importedUniques = new HashMap<>();
	/** Tombs of Amascut raids from Raid Data Tracker, for the raid sections only; never saved. */
	private final Map<String, List<RaidRecord>> sectionOnly = new HashMap<>();
	private final Map<RaidRecord, String> sectionUniques = new IdentityHashMap<>();

	RaidRecords(TrackerContext ctx, Statistics statistics, DayCounts dayCounts)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
		this.statistics = statistics;
		this.dayCounts = dayCounts;
	}

	/**
	 * Another account was loaded: raid data waiting for its match is dropped. Exactly the fields
	 * Tracker reset before it was split up; the pending record's time, the last raid's mode and
	 * count and the latest Fractured Archive raid were never reset there and are still left as is.
	 */
	void clear()
	{
		importedUniques.clear();
		sectionOnly.clear();
		sectionUniques.clear();
		lastRaidMillis = 0;
		latestToaMillis = 0;
		pendingPartyMillis = 0;
		pendingRecord = null;
		lastRaidBoss = null;
		latestToa = null;
		pendingParty = null;
	}

	/**
	 * Average purple chance of the raids in this mode where On Rate had your points, or NaN.
	 * A good value for the setting that raids without points use.
	 */
	double averagePurpleChance(String bossName, String part)
	{
		double sum = 0;
		int n = 0;
		BossDefinition boss = catalog.byName(bossName);
		for (RaidRecord r : raidRecords(bossName))
		{
			double chance = RateModel.purpleChance(r);
			if (!Double.isNaN(chance) && "fractured".equals(r.family) && boss != null && boss.hasParts())
			{
				// the Fractured Archive's base chance follows the setting
				chance = RateModel.recordChance(r, boss.getParts().get(0), ctx.settings());
			}
			if ((part == null ? bossName : part).equals(r.part) && !Double.isNaN(chance))
			{
				sum += chance;
				n++;
			}
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	/**
	 * Data read at the end of a raid: points (CoX, estimated for ToA) or the ToB board.
	 * The completion count message tells which mode it was; it may come just before or
	 * just after. Returns the boss whose numbers changed, or null.
	 */
	String onRaidRecord(RaidRecord record, long nowMillis)
	{
		if (record == null || record.family == null)
		{
			return null;
		}
		record.savedMillis = nowMillis;
		if ("fractured".equals(record.family) && latestFractured != null
			&& nowMillis - latestFracturedMillis <= RAID_MATCH_MILLIS)
		{
			// the completion message came first and saved the raid: add the damage share to it
			String boss = latestFracturedBoss;
			double before = statistics.anyExpected(boss);
			latestFractured.contribution = record.contribution;
			latestFractured.deaths = record.deaths;
			latestFractured = null;
			ctx.raidsChanged.add(boss);
			dayCounts.shiftToday(boss, statistics.anyExpected(boss) - before);
			return boss;
		}
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
	String onToaPoints(int personalPoints, long nowMillis)
	{
		if (personalPoints <= 0 || latestToa == null || nowMillis - latestToaMillis > PARTY_MATCH_MILLIS)
		{
			return null;
		}
		String toaBoss = toaBossName();
		double before = toaBoss == null ? Double.NaN : statistics.anyExpected(toaBoss);
		latestToa.personalPoints = personalPoints;
		// the estimate's extra points (counted outside a RuneLite party) are part of the exact number
		latestToa.partyPoints = 0;
		latestToa.estimated = false;
		if (toaBoss != null)
		{
			ctx.raidsChanged.add(toaBoss);
			dayCounts.shiftToday(toaBoss, statistics.anyExpected(toaBoss) - before);
		}
		return toaBoss;
	}

	String toaBossName()
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
	String onPartyPoints(int personalPoints, int teamPoints, long nowMillis)
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
			double before = toaBoss == null ? Double.NaN : statistics.anyExpected(toaBoss);
			latestToa.personalPoints = personalPoints;
			latestToa.teamPoints = teamPoints;
			latestToa = null;
			if (toaBoss != null)
			{
				ctx.raidsChanged.add(toaBoss);
				dayCounts.shiftToday(toaBoss, statistics.anyExpected(toaBoss) - before);
			}
			return toaBoss;
		}
		pendingParty = new int[]{personalPoints, teamPoints};
		pendingPartyMillis = nowMillis;
		return null;
	}

	void applyPendingParty(RaidRecord record, long nowMillis)
	{
		if (pendingParty != null && nowMillis - pendingPartyMillis <= PARTY_MATCH_MILLIS)
		{
			record.personalPoints = pendingParty[0];
			record.teamPoints = pendingParty[1];
			latestToa = null;
		}
		pendingParty = null;
	}

	static long matchWindow(String family)
	{
		return "tob".equals(family) ? TOB_MATCH_MILLIS : RAID_MATCH_MILLIS;
	}

	void onRaidCompletion(BossDefinition boss, String part, int kc, long nowMillis)
	{
		String family = raidFamily(boss.getName(), part);
		if (family.isEmpty())
		{
			return;
		}
		if ("fractured".equals(family))
		{
			// the damage share may have come just before (or comes just after: see onRaidRecord);
			// the team is counted around you now, for its bonus and your expected contribution
			boolean pending = pendingRecord != null && "fractured".equals(pendingRecord.family)
				&& nowMillis - pendingRecordMillis <= RAID_MATCH_MILLIS;
			RaidRecord record = pending ? pendingRecord : RaidRecord.fractured(0, -1, -1);
			pendingRecord = null;
			lastRaidBoss = null;
			int size = ctx.teamSize().applyAsInt(boss.getName());
			if (size > 0)
			{
				record.teamSize = Math.min(size, RaidChance.TFA_MAX_TEAM);
			}
			if (record.teamSize > 0 || record.contribution >= 0)
			{
				record.savedMillis = nowMillis;
				saveRecord(boss.getName(), part, kc, record);
				latestFractured = pending ? null : record;
				latestFracturedBoss = boss.getName();
				latestFracturedMillis = nowMillis;
			}
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
	String raidFamily(String bossName, String partName)
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

	void saveRecord(String bossName, String part, int kc, RaidRecord record)
	{
		double before = statistics.anyExpected(bossName);
		record.part = part == null ? bossName : part;
		record.kc = kc;
		if (record.durationMillis < 0)
		{
			// the raid's time message may have come before its points
			record.durationMillis = FightTimes.knownDuration(ctx.state().get(bossName), record.part, kc);
		}
		List<RaidRecord> raids = ctx.state().get(bossName).raids;
		raids.add(record);
		ctx.raidsChanged.add(bossName);
		while (raids.size() > MAX_RAID_RECORDS)
		{
			raids.remove(0);
		}
		dayCounts.shiftToday(bossName, statistics.anyExpected(bossName) - before);
	}

	/**
	 * Adds raids read from Raid Data Tracker as raid records, so they count with their own points like
	 * the raids On Rate saw. A raid On Rate already has (same mode and completion count) is left as it
	 * is: On Rate's own record wins. Per mode, a raid whose completion count doesn't go up with its date
	 * is skipped, as a count that runs backwards can't be placed. Raids are kept oldest first. Safe to
	 * call again with the same list.
	 *
	 * @return how many raids were added
	 */
	int importRaids(String bossName, List<ImportedRaid> imported)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null || boss.getRaid() == null || imported == null || imported.isEmpty())
		{
			return 0;
		}
		Map<String, BossPart> parts = new HashMap<>();
		for (BossPart p : boss.getParts())
		{
			parts.put(p.getName(), p);
		}
		BossProgress progress = ctx.state().get(bossName);
		Set<String> have = new HashSet<>();
		for (RaidRecord r : progress.raids)
		{
			have.add(r.part + "#" + r.kc);
		}
		List<ImportedRaid> sorted = new ArrayList<>(imported);
		sorted.sort(Comparator.comparingLong(r -> r.when));
		Map<String, Integer> lastKc = new HashMap<>();
		Map<String, String> uniques = importedUniques.computeIfAbsent(bossName, k -> new HashMap<>());
		boolean toa = "toa".equals(boss.getRaid());
		List<RaidRecord> extra = new ArrayList<>();
		int added = 0;
		for (ImportedRaid i : sorted)
		{
			if (i.part == null || !parts.containsKey(i.part) || i.personalPoints <= 0)
			{
				continue;
			}
			if (toa)
			{
				// Raid Data Tracker's Tombs counts don't reliably follow the mode (a raid's level and
				// count can disagree), so these raids can't be placed among your raids for the luck
				// numbers. They only show in the raid sections, matched to your own raids by date.
				if (!nearOwnRaid(progress, i.when))
				{
					RaidRecord r = RaidRecord.toa(i.personalPoints, 0, i.totalPoints, Math.max(1, i.teamSize), i.raidLevel);
					r.part = i.part;
					r.savedMillis = i.when;
					r.durationMillis = i.millis > 0 && i.millis <= Integer.MAX_VALUE ? (int) i.millis : -1;
					extra.add(r);
					if (i.unique != null)
					{
						sectionUniques.put(r, i.unique);
					}
				}
				continue;
			}
			if (i.kc <= 0)
			{
				continue;
			}
			Integer last = lastKc.get(i.part);
			if (last != null && i.kc <= last)
			{
				continue;
			}
			lastKc.put(i.part, i.kc);
			String key = i.part + "#" + i.kc;
			if (i.unique != null)
			{
				uniques.put(key, i.unique);
			}
			if (!have.add(key))
			{
				continue;
			}
			RaidRecord r = "toa".equals(boss.getRaid())
				? RaidRecord.toa(i.personalPoints, 0, i.totalPoints, Math.max(1, i.teamSize), i.raidLevel)
				: RaidRecord.cox(i.personalPoints, Math.max(i.totalPoints, i.personalPoints), Math.max(1, i.teamSize));
			r.part = i.part;
			r.kc = i.kc;
			r.savedMillis = i.when;
			r.durationMillis = i.millis > 0 && i.millis <= Integer.MAX_VALUE ? (int) i.millis : -1;
			// before the newer raids at the end, without moving any raid already saved
			int at = progress.raids.size();
			while (at > 0 && progress.raids.get(at - 1).savedMillis > r.savedMillis)
			{
				at--;
			}
			progress.raids.add(at, r);
			added++;
		}
		if (toa)
		{
			// read again at every login: the new list replaces the last one
			List<RaidRecord> old = sectionOnly.put(bossName, extra);
			if (old != null)
			{
				old.forEach(sectionUniques::remove);
			}
		}
		if (added > 0)
		{
			while (progress.raids.size() > MAX_RAID_RECORDS)
			{
				progress.raids.remove(0);
			}
			ctx.raidsChanged.add(bossName);
		}
		return added;
	}

	/** A raid read from Raid Data Tracker within this long of one On Rate saved is that same raid. */
	static final long SAME_RAID_MILLIS = 10 * 60_000;

	private static boolean nearOwnRaid(BossProgress progress, long when)
	{
		for (RaidRecord r : progress.raids)
		{
			if (r.savedMillis > 0 && Math.abs(r.savedMillis - when) <= SAME_RAID_MILLIS)
			{
				return true;
			}
		}
		return false;
	}

	/** Raids read from Raid Data Tracker that only show in the raid sections (Tombs of Amascut), oldest first. */
	List<RaidRecord> sectionOnly(String bossName)
	{
		List<RaidRecord> r = sectionOnly.get(bossName);
		return r == null ? Collections.emptyList() : r;
	}

	/** Your unique from each of those raids. */
	Map<RaidRecord, String> sectionUniques()
	{
		return sectionUniques;
	}

	/** Your uniques from raids read from Raid Data Tracker, by "mode#completion count". */
	Map<String, String> importedUniques(String bossName)
	{
		Map<String, String> u = importedUniques.get(bossName);
		return u == null ? Collections.emptyMap() : u;
	}

	/** Completions of this raid mode whose purple chance came from your points. */
	int measuredRaids(String bossName, String part)
	{
		int n = 0;
		for (RaidRecord r : raidRecords(bossName))
		{
			if ((part == null ? bossName : part).equals(r.part) && !Double.isNaN(RateModel.purpleChance(r)))
			{
				n++;
			}
		}
		return n;
	}

	/** Every raid saved for this boss, oldest first. */
	List<RaidRecord> raidRecords(String bossName)
	{
		BossProgress progress = ctx.state().peek(bossName);
		return progress == null ? new ArrayList<>() : new ArrayList<>(progress.raids);
	}

	/**
	 * Uniques of this item you would expect from the raids of a mode after {@code fromKc} up to
	 * and including {@code toKc} (that mode's own counts): raids On Rate counted use their own
	 * purple chance (from the points), the others the chance from the settings.
	 */
	double raidWaitExpected(BossDefinition boss, BossPart part, DropDefinition drop, BossProgress progress,
		int fromKc, int toKc)
	{
		double perRaid = RateModel.partRate(boss, part, drop, ctx.settings());
		Map<Integer, RaidRecord> counted = new HashMap<>();
		for (RaidRecord r : progress.raids)
		{
			if (part.getName().equals(r.part) && r.kc > fromKc && r.kc <= toKc)
			{
				counted.put(r.kc, r);
			}
		}
		double sum = 0;
		for (int raid = fromKc + 1; raid <= toKc; raid++)
		{
			RaidRecord r = counted.get(raid);
			double chance = r == null ? Double.NaN : RateModel.recordChance(r, part, ctx.settings());
			sum += Double.isNaN(chance) ? perRaid : RateModel.recordDropExpected(boss, part, drop, r, chance);
		}
		return sum;
	}
}

