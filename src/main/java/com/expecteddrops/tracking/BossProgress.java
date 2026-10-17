package com.expecteddrops.tracking;

import java.util.HashMap;
import java.util.Map;

/**
 * What we know about one boss for one account. Saved as JSON in the RuneLite profile.
 */
public class BossProgress
{
	/** Latest kill count per chat counter name (e.g. normal and challenge mode). */
	Map<String, Integer> chatKc = new HashMap<>();
	/** Latest kill count per collection log counter label. */
	Map<String, Integer> clogKc = new HashMap<>();
	/** Kills seen through the loot tracker since the plugin was installed. */
	int lootKills;
	/** Received count per normalized item name. */
	Map<String, Integer> received = new HashMap<>();
	/** True once the boss's collection log page has been opened. */
	boolean clogSynced;
	/** Delve bosses only: completions per delve level (9 = "8+"). */
	Map<Integer, Integer> levelKc = new HashMap<>();
	/** Delve bosses only: true once the scoreboard has been read. */
	boolean levelsSynced;

	void ensureMaps()
	{
		if (chatKc == null)
		{
			chatKc = new HashMap<>();
		}
		if (clogKc == null)
		{
			clogKc = new HashMap<>();
		}
		if (levelKc == null)
		{
			levelKc = new HashMap<>();
		}
		if (received == null)
		{
			received = new HashMap<>();
		}
	}
}
