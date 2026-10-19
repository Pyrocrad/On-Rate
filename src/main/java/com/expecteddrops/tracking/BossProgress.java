package com.expecteddrops.tracking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
	/** Loot-only bosses: lootKills when the log page was last read, to add kills since then. */
	Integer lootKillsAtSync;
	/** Group bosses: kills per team size (players around you at the kill) that On Rate saw. */
	Map<Integer, Integer> teamKills = new HashMap<>();
	/** Received count per normalized item name. */
	Map<String, Integer> received = new HashMap<>();
	/** True once the boss's collection log page has been opened. */
	boolean clogSynced;
	/** Delve bosses only: completions per delve level (9 = "8+"). */
	Map<Integer, Integer> levelKc = new HashMap<>();
	/** Delve bosses only: true once the scoreboard has been read. */
	boolean levelsSynced;
	/** Kill count at the last drop of each item that On Rate saw (normalized item name). */
	Map<String, Integer> lastDropKc = new HashMap<>();
	/** Kill count when On Rate first knew it; how long ago older drops came is unknown. */
	Integer trackingStartKc;
	/** Highest milestone already announced per item in its current dry streak. */
	Map<String, Integer> milestone = new HashMap<>();
	/** Kill count at the last milestone check, so a big jump (first sync) stays quiet. */
	Integer lastCheckedKc;
	/** Raids only: each raid finished with On Rate, with its points (see RaidRecord). */
	List<RaidRecord> raids = new ArrayList<>();
	/**
	 * The last kills On Rate saw, newest last, for the History section. Saved under its own
	 * key per boss (see Tracker#changedKills), not with the rest of this.
	 */
	transient List<KillEntry> kills = new ArrayList<>();

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
		if (lastDropKc == null)
		{
			lastDropKc = new HashMap<>();
		}
		if (milestone == null)
		{
			milestone = new HashMap<>();
		}
		if (raids == null)
		{
			raids = new ArrayList<>();
		}
		if (kills == null)
		{
			kills = new ArrayList<>();
		}
		if (received == null)
		{
			received = new HashMap<>();
		}
		if (teamKills == null)
		{
			teamKills = new HashMap<>();
		}
	}
}
