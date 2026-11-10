package com.onrate.engine.tracking;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How long kills and raids took: a time message is matched to the kill it belongs to (it can come a
 * moment before or after the kill count), raid times imported from Raid Data Tracker are kept and
 * matched to the raids On Rate saw, and the times are summed up for the Time pages.
 * <p>
 * Package-private and unsynchronized: reached only through {@link Tracker}, under its lock.
 */
final class FightTimes
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;

	/**
	 * A kill's time message and its kill count message arrive within a moment of each other, in
	 * either order: the one that comes first waits here for the other.
	 */
	private static final long DURATION_MATCH_MILLIS = 20_000;
	private ChatTimes.Reading pendingDuration;
	private long pendingDurationAt;
	/** The kill whose time message may still come, and the rank of the time it has. */
	private KillEntry durationEntry;
	private String durationBoss;
	private long durationAt;
	private int durationRank;
	/** A raid time from another plugin counts as the same raid as one On Rate timed when they ended this close together. */
	private static final long IMPORT_MATCH_MILLIS = 5 * 60_000;

	FightTimes(TrackerContext ctx)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
	}

	/**
	 * A kill was just counted: it takes the time message that came a moment before it, and
	 * waits for one that comes a moment after.
	 */
	void offerDuration(String bossName, KillEntry entry, long nowMillis)
	{
		durationEntry = entry;
		durationBoss = bossName;
		durationAt = nowMillis;
		durationRank = 0;
		ChatTimes.Reading waiting = pendingDuration;
		pendingDuration = null;
		if (waiting != null && nowMillis - pendingDurationAt <= DURATION_MATCH_MILLIS)
		{
			setDuration(bossName, entry, waiting);
		}
	}

	/**
	 * A message with the time of a whole kill or raid. Goes to the kill counted a moment ago when
	 * that has no time yet (or a less certain one), else waits for the kill it belongs to.
	 * Returns the boss whose History changed, or null.
	 */
	String onDuration(ChatTimes.Reading time, long nowMillis)
	{
		if (durationEntry != null && nowMillis - durationAt <= DURATION_MATCH_MILLIS
			&& (durationEntry.durationMillis == null || time.getRank() > durationRank))
		{
			setDuration(durationBoss, durationEntry, time);
			return durationBoss;
		}
		// two time messages can wait for one kill (Tombs: the challenge time, then a total): keep the better one
		boolean waiting = pendingDuration != null && nowMillis - pendingDurationAt <= DURATION_MATCH_MILLIS;
		if (!waiting || time.getRank() >= pendingDuration.getRank())
		{
			pendingDuration = time;
			pendingDurationAt = nowMillis;
		}
		return null;
	}

	void setDuration(String bossName, KillEntry entry, ChatTimes.Reading time)
	{
		entry.durationMillis = time.getMillis();
		if (entry == durationEntry)
		{
			durationRank = time.getRank();
		}
		ctx.killsChanged.add(bossName);
		BossProgress progress = ctx.state().peek(bossName);
		if (progress == null || entry.partKc == null)
		{
			return;
		}
		// the raid's own record, when its points were already saved
		for (int i = progress.raids.size() - 1, looked = 0; i >= 0 && looked < 10; i--, looked++)
		{
			RaidRecord record = progress.raids.get(i);
			if (record.kc == entry.partKc && java.util.Objects.equals(record.part, entry.part))
			{
				if (record.durationMillis != time.getMillis())
				{
					record.durationMillis = time.getMillis();
					ctx.raidsChanged.add(bossName);
				}
				return;
			}
		}
	}

	/** The time of the latest kill of this raid mode, when its message came before the raid's points. */
	static int knownDuration(BossProgress progress, String part, int kc)
	{
		for (int i = progress.kills.size() - 1, looked = 0; i >= 0 && looked < 10; i--, looked++)
		{
			KillEntry kill = progress.kills.get(i);
			if (kill.partKc != null && kill.partKc == kc && java.util.Objects.equals(kill.part, part)
				&& kill.durationMillis != null)
			{
				return kill.durationMillis;
			}
		}
		return -1;
	}

	/**
	 * Adds raid times read from another plugin's log (Raid Data Tracker): for one raid mode, when
	 * each raid ended (epoch millis) and how long it took (millis). A raid already imported (same end
	 * time) is skipped, and one On Rate timed itself is not counted twice: its time is used, or
	 * filled in when On Rate saw the raid but not its time. Safe to call again with the same list.
	 *
	 * @return how many times were new
	 */
	int importRaidTimes(String bossName, String part, List<long[]> samples)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null || boss.getRaid() == null || part == null || samples == null || samples.isEmpty())
		{
			return 0;
		}
		BossProgress progress = ctx.state().get(bossName);
		if (progress.importedTimes == null)
		{
			progress.importedTimes = new HashMap<>();
		}
		List<long[]> known = progress.importedTimes.computeIfAbsent(part, k -> new ArrayList<>());
		java.util.Set<Long> have = new java.util.HashSet<>();
		for (long[] k : known)
		{
			have.add(k[0]);
		}
		int added = 0;
		for (long[] sample : samples)
		{
			if (sample == null || sample.length < 2 || sample[0] <= 0 || sample[1] <= 0 || sample[1] > Integer.MAX_VALUE
				|| !have.add(sample[0]))
			{
				continue;
			}
			known.add(new long[]{sample[0], sample[1]});
			added++;
			fillLiveRaid(bossName, progress, part, sample[0], (int) sample[1]);
		}
		if (known.isEmpty())
		{
			progress.importedTimes.remove(part);
		}
		return added;
	}

	/** Gives the raid On Rate saw at this time (when it has no time yet) the imported time. */
	void fillLiveRaid(String bossName, BossProgress progress, String part, long when, int millis)
	{
		KillEntry nearest = null;
		for (KillEntry kill : progress.kills)
		{
			if (kill.isDateKnown() && part.equals(kill.part) && Math.abs(kill.time - when) <= IMPORT_MATCH_MILLIS
				&& (nearest == null || Math.abs(kill.time - when) < Math.abs(nearest.time - when)))
			{
				nearest = kill;
			}
		}
		if (nearest == null || nearest.durationMillis != null)
		{
			return;
		}
		nearest.durationMillis = millis;
		ctx.killsChanged.add(bossName);
		for (RaidRecord record : progress.raids)
		{
			if (nearest.partKc != null && record.kc == nearest.partKc && part.equals(record.part) && record.durationMillis < 0)
			{
				record.durationMillis = millis;
				ctx.raidsChanged.add(bossName);
			}
		}
	}

	/**
	 * How long this boss's kills took (a combined page: one part's), from the kills in History and,
	 * for raids, the times imported from Raid Data Tracker. Null when nothing has a time.
	 */
	TimeStats killTimes(String bossName, String partName)
	{
		BossProgress progress = ctx.state().peek(bossName);
		if (progress == null)
		{
			return null;
		}
		List<Integer> times = new ArrayList<>();
		int last = -1;
		long lastAt = Long.MIN_VALUE;
		List<long[]> live = new ArrayList<>();
		for (KillEntry kill : progress.kills)
		{
			if (kill.durationMillis == null || (partName != null && !partName.equals(kill.part)))
			{
				continue;
			}
			times.add(kill.durationMillis);
			last = kill.durationMillis;
			lastAt = kill.time;
			if (kill.isDateKnown())
			{
				live.add(new long[]{kill.time, kill.durationMillis});
			}
		}
		if (progress.importedTimes != null)
		{
			for (Map.Entry<String, List<long[]>> e : progress.importedTimes.entrySet())
			{
				if (partName != null && !partName.equals(e.getKey()))
				{
					continue;
				}
				for (long[] sample : e.getValue())
				{
					if (countedLive(live, sample[0]))
					{
						continue;
					}
					times.add((int) sample[1]);
					if (sample[0] >= lastAt)
					{
						last = (int) sample[1];
						lastAt = sample[0];
					}
				}
			}
		}
		return times.isEmpty() ? null : new TimeStats(times, last);
	}

	/** True when a raid On Rate timed itself ended within a few minutes of this imported one: the same raid. */
	static boolean countedLive(List<long[]> live, long when)
	{
		for (long[] l : live)
		{
			if (Math.abs(l[0] - when) <= IMPORT_MATCH_MILLIS)
			{
				return true;
			}
		}
		return false;
	}

	/** How many raid times of this boss were imported from another plugin (all modes, or one). */
	int importedRaidTimes(String bossName, String part)
	{
		BossProgress progress = ctx.state().peek(bossName);
		if (progress == null || progress.importedTimes == null)
		{
			return 0;
		}
		int n = 0;
		for (Map.Entry<String, List<long[]>> e : progress.importedTimes.entrySet())
		{
			if (part == null || part.equals(e.getKey()))
			{
				n += e.getValue().size();
			}
		}
		return n;
	}

	/** How long delves of one level took (Doom: 1 to 8, 9 for the deep delves), or null when none has a time. */
	TimeStats levelTimes(String bossName, int level)
	{
		BossProgress progress = ctx.state().peek(bossName);
		if (progress == null)
		{
			return null;
		}
		List<Integer> times = new ArrayList<>();
		int last = -1;
		for (KillEntry kill : progress.kills)
		{
			if (kill.durationMillis != null && kill.level != null && kill.level == level)
			{
				times.add(kill.durationMillis);
				last = kill.durationMillis;
			}
		}
		return times.isEmpty() ? null : new TimeStats(times, last);
	}
}
