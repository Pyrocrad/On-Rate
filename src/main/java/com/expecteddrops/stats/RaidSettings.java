package com.expecteddrops.stats;

/**
 * Player settings the drop maths needs: raid purple chances, and how much of the damage
 * the player does at team bosses.
 */
public interface RaidSettings
{
	/** Chance that a raid mode gives you a unique (a "purple"), as a fraction between 0 and 1. */
	double uniqueChance(String raid);

	/**
	 * Your average share of the drops at a team boss over {@code kills} kills: 1 when you
	 * did everything solo, 0.5 for all duos. Pets that don't scale are not affected.
	 */
	default double contribution(String team, int kills)
	{
		return 1.0;
	}

	/** Short text for the panel explaining the split used, e.g. "300 solo and 120 duo kills". */
	default String contributionNote(String team, int kills)
	{
		return null;
	}
}
