package com.expecteddrops.tracking;

import java.util.Map;
import java.util.TreeMap;

/**
 * The player's choice to judge a shared item (dragon axe, draconic visage...) on each boss
 * separately: the copies On Rate saw drop go to the boss they dropped from, and the older ones
 * it didn't see go where the player said ({@link #older}). Saved with how many older copies
 * there were, so a copy the log gains later without On Rate seeing it (played elsewhere) makes
 * the item shared again until the player answers once more.
 */
class PoolSplit
{
	/**
	 * Older copies per boss name, or under {@link Tracker#NOT_FROM_A_BOSS} for the ones from
	 * other monsters (a visage from a regular dragon).
	 */
	Map<String, Integer> older = new TreeMap<>();

	PoolSplit(Map<String, Integer> older)
	{
		this.older.putAll(older);
	}

	int olderCopies()
	{
		int n = 0;
		for (Integer v : older == null ? java.util.Collections.<Integer>emptyList() : older.values())
		{
			n += v == null ? 0 : v;
		}
		return n;
	}

	int olderAt(String where)
	{
		Integer n = older == null ? null : older.get(where);
		return n == null ? 0 : n;
	}
}
