package com.expecteddrops.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All saved progress for one account, keyed by boss name.
 */
public class TrackerState
{
	/**
	 * What this plugin writes. Saves from before the number existed read as 0; a save with a
	 * higher number was written by a newer On Rate.
	 */
	static final int CURRENT_VERSION = 1;

	/** Version of the plugin's save format that wrote this (see {@link #CURRENT_VERSION}). */
	int version;

	Map<String, BossProgress> bosses = new HashMap<>();
	/**
	 * Collection log totals of items that several bosses drop (godsword shards, Virtus...).
	 * The log only shows one total, so it can't be split per boss, but it is exact for the
	 * account as a whole.
	 */
	Map<String, Integer> sharedClog = new HashMap<>();

	/** Kills, uniques and expected per day (epoch day as text) per boss, for the last two weeks. */
	Map<String, Map<String, DayCount>> days = new HashMap<>();

	Map<String, Map<String, DayCount>> days()
	{
		if (days == null)
		{
			days = new HashMap<>();
		}
		return days;
	}

	/** Luck graph: the account's luck at the end of each day On Rate was used. */
	List<HistoryPoint> history = new ArrayList<>();

	List<HistoryPoint> history()
	{
		if (history == null)
		{
			history = new ArrayList<>();
		}
		return history;
	}

	/** Shared items (pool key) the player chose to judge on each boss separately. Null in older saves. */
	Map<String, PoolSplit> poolSplits = new HashMap<>();

	/** Copies of shared items (pool key) On Rate saw drop from monsters other than their bosses. Null in older saves. */
	Map<String, Integer> poolElsewhereSeen = new HashMap<>();

	Map<String, Integer> poolElsewhereSeen()
	{
		if (poolElsewhereSeen == null)
		{
			poolElsewhereSeen = new HashMap<>();
		}
		return poolElsewhereSeen;
	}

	Map<String, PoolSplit> poolSplits()
	{
		if (poolSplits == null)
		{
			poolSplits = new HashMap<>();
		}
		return poolSplits;
	}

	Map<String, Integer> sharedClog()
	{
		if (sharedClog == null)
		{
			sharedClog = new HashMap<>();
		}
		return sharedClog;
	}

	BossProgress get(String boss)
	{
		if (bosses == null)
		{
			bosses = new HashMap<>();
		}
		BossProgress progress = bosses.computeIfAbsent(boss, k -> new BossProgress());
		progress.ensureMaps();
		return progress;
	}

	BossProgress peek(String boss)
	{
		if (bosses == null)
		{
			return null;
		}
		BossProgress progress = bosses.get(boss);
		if (progress != null)
		{
			progress.ensureMaps();
		}
		return progress;
	}

	/** Whether a newer On Rate wrote this: it can hold things this version doesn't know. */
	public boolean isFromNewerVersion()
	{
		return version > CURRENT_VERSION;
	}

	/**
	 * Fixes what a damaged or hand-edited save can leave behind (null entries in maps and lists),
	 * so later code never meets them on the Swing thread.
	 */
	void repair()
	{
		days();
		sharedClog();
		poolSplits();
		poolElsewhereSeen();
		history();
		if (bosses == null)
		{
			bosses = new HashMap<>();
		}
		bosses.values().removeIf(b -> b == null);
		history.removeIf(h -> h == null);
		days.values().removeIf(d -> d == null);
		sharedClog.values().removeIf(v -> v == null);
		poolSplits.values().removeIf(v -> v == null);
		poolElsewhereSeen.values().removeIf(v -> v == null);
	}
}
