package com.expecteddrops.tracking;

/**
 * The player's choice to judge a shared item (dragon axe, Virtus...) on each boss separately:
 * the copies On Rate saw drop go to the boss they dropped from, and the older ones it didn't
 * see all go to {@link #olderBoss}. Saved with how many older copies there were, so a copy the
 * log gains later without On Rate seeing it (played elsewhere) makes the item shared again
 * until the player answers once more.
 */
class PoolSplit
{
	/** The boss the older copies came from; null when there were none. */
	String olderBoss;
	/** How many older copies the choice was made for. */
	int olderCopies;

	PoolSplit(String olderBoss, int olderCopies)
	{
		this.olderBoss = olderBoss;
		this.olderCopies = olderCopies;
	}
}
