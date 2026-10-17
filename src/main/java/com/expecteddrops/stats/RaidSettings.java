package com.expecteddrops.stats;

/**
 * Chance that a raid gives you a unique (a "purple"), as a fraction between 0 and 1.
 */
public interface RaidSettings
{
	double uniqueChance(String raid);
}
