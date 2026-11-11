package com.onrate.engine.stats;

/**
 * Player settings the drop maths needs: raid purple chances, and how much of the damage
 * the player does at team bosses.
 */
public interface RaidSettings
{
	/** Chance that a raid mode gives you a unique (a "purple"), as a fraction between 0 and 1. */
	double uniqueChance(String raid);

	/**
	 * The team size {@link #uniqueChance} assumes (ToB splits the team's purples between
	 * the players). Raids with a known team size scale from this.
	 */
	default int teamSize(String raid)
	{
		return 1;
	}

	/**
	 * The Fractured Archive: the team's unique chance for a solo or duo with no deaths (bigger teams
	 * have their own, higher chance), which raids with a known team size scale from. Used for raids
	 * since the game update of 11 November 2026; older ones keep the chance of their time.
	 */
	default double tfaBaseChance()
	{
		return RaidChance.TFA_BASE_CHANCE;
	}

	/**
	 * Your average share of the drops at a team boss over {@code kills} kills: 1 when you
	 * did everything solo, 0.5 for all duos. Pets that don't scale are not affected.
	 */
	default double contribution(String team, int kills)
	{
		return 1.0;
	}

	/**
	 * How many of your recorded raids a raid mode's {@link #uniqueChance} (or, without points, its
	 * {@link #teamSize}) was learned from instead of the setting; 0 when it is the setting.
	 */
	default int learnedRaids(String raid)
	{
		return 0;
	}

	/** Short text for the panel explaining the split used, e.g. "300 solo and 120 duo kills". */
	default String contributionNote(String team, int kills)
	{
		return null;
	}
}
