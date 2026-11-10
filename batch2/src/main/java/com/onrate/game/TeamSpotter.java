package com.onrate.game;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Who fights a group boss with you: the other players seen hitting it. Only they share the kill,
 * not the players who happen to stand nearby. Seen players expire after a few minutes, and a
 * boss's list is cleared once its kill is counted.
 */
final class TeamSpotter
{
	/** A player seen hitting the boss this long ago (or less) still counts as part of the fight. */
	static final long WINDOW_MILLIS = 5 * 60_000;

	/** The bosses counted this way, by the name their NPC and their kill count both use. */
	private static final String[] FIGHTS = {"nex", "yama", "hueycoatl", "corporeal beast"};

	/** Fight key to the players seen hitting it and when. */
	private final Map<String, Map<String, Long>> seen = new HashMap<>();

	/**
	 * The fight an NPC or boss name belongs to ("Nex", "The Hueycoatl", "Hueycoatl tail"), or null
	 * when it isn't one counted by who hits it.
	 */
	static String fightOf(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = name.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
		if (n.startsWith("the "))
		{
			n = n.substring(4);
		}
		for (String fight : FIGHTS)
		{
			if (n.equals(fight) || n.startsWith(fight + " "))
			{
				return fight;
			}
		}
		return null;
	}

	synchronized void onHitting(String fight, String player, long nowMillis)
	{
		seen.computeIfAbsent(fight, f -> new HashMap<>()).put(player, nowMillis);
	}

	/** The other players seen hitting this boss lately (you are not counted). */
	synchronized int others(String fight, long nowMillis)
	{
		Map<String, Long> players = seen.get(fight);
		if (players == null)
		{
			return 0;
		}
		players.values().removeIf(at -> nowMillis - at > WINDOW_MILLIS);
		return players.size();
	}

	/** The kill is counted: the next one starts with nobody seen. */
	synchronized void clear(String fight)
	{
		seen.remove(fight);
	}

	synchronized void clearAll()
	{
		seen.clear();
	}
}
