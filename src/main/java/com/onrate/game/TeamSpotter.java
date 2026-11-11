package com.onrate.game;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

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
		// asked for every player in view on every game tick: no pattern is compiled and no text is
		// built here (tags are rare in NPC names, and the names are compared in place)
		String n = (name.indexOf('<') >= 0 ? TAGS.matcher(name).replaceAll("") : name).trim();
		int from = n.regionMatches(true, 0, "the ", 0, 4) ? 4 : 0;
		for (String fight : FIGHTS)
		{
			int end = from + fight.length();
			if (n.regionMatches(true, from, fight, 0, fight.length()) && (n.length() == end || n.charAt(end) == ' '))
			{
				return fight;
			}
		}
		return null;
	}

	private static final Pattern TAGS = Pattern.compile("<[^>]*>");

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
