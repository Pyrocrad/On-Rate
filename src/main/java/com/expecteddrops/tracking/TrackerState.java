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
	Map<String, BossProgress> bosses = new HashMap<>();
	/**
	 * Collection log totals of items that several bosses drop (godsword shards, Virtus...).
	 * The log only shows one total, so it can't be split per boss, but it is exact for the
	 * account as a whole.
	 */
	Map<String, Integer> sharedClog = new HashMap<>();

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
}
