package com.expecteddrops.tracking;

import java.util.HashMap;
import java.util.Map;

/**
 * All saved progress for one account, keyed by boss name.
 */
public class TrackerState
{
	Map<String, BossProgress> bosses = new HashMap<>();

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
