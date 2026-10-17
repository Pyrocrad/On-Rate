package com.expecteddrops.stats;

/**
 * Purple chances from raid points, using the formulas on the OSRS Wiki. Each method returns
 * your own expected number of uniques for one raid (for a single roll, that is the chance).
 */
public final class RaidChance
{
	/** Chambers of Xeric: every 8,676 team points is 1% for a unique. */
	public static final double COX_POINTS_PER_PERCENT = 8_676;
	/** One unique roll is capped at 570,000 points (65.7%); points above that roll again. */
	public static final int COX_POINTS_PER_ROLL = 570_000;
	/** At most six uniques per raid. */
	public static final int COX_MAX_ROLLS = 6;

	/** Tombs of Amascut: the chance to see a unique is capped at 55%. */
	public static final double TOA_MAX_CHANCE = 0.55;
	/** Uniques rolled below their minimum raid level are kept 1 time in 50. */
	public static final double TOA_LOW_LEVEL_KEEP = 1 / 50.0;

	private RaidChance()
	{
	}

	/**
	 * Expected uniques for the whole team in one Chambers raid: 1% per 8,676 points, each
	 * roll capped at 570,000 points (65.7%), with further points rolling for another unique.
	 */
	public static double coxTeamUniques(int teamPoints)
	{
		double uniques = 0;
		for (int roll = 0; roll < COX_MAX_ROLLS; roll++)
		{
			int points = Math.min(Math.max(teamPoints - roll * COX_POINTS_PER_ROLL, 0), COX_POINTS_PER_ROLL);
			uniques += points / COX_POINTS_PER_PERCENT / 100;
		}
		return uniques;
	}

	/**
	 * Your expected uniques for one Chambers raid. Each unique goes to a player with a
	 * chance equal to their share of the points. NaN when the points are unknown.
	 */
	public static double cox(int personalPoints, int teamPoints)
	{
		if (personalPoints <= 0)
		{
			return Double.NaN;
		}
		int team = Math.max(teamPoints, personalPoints);
		return coxTeamUniques(team) * personalPoints / team;
	}

	/**
	 * The raid level used for unique rolls: full up to 310, a third from 310 to 430, and a
	 * sixth above that.
	 */
	public static double toaScaledLevel(int raidLevel)
	{
		if (raidLevel <= 310)
		{
			return Math.max(0, raidLevel);
		}
		if (raidLevel <= 430)
		{
			return 310 + (raidLevel - 310) / 3.0;
		}
		return 350 + (raidLevel - 430) / 6.0;
	}

	/** Team points for 1% unique chance at this raid level: 10,500 - 20 x scaled level. */
	public static double toaPointsPerPercent(int raidLevel)
	{
		return 10_500 - 20 * toaScaledLevel(raidLevel);
	}

	/** The team's chance of a unique, capped at 55%. Points without the 5,000 starting points. */
	public static double toaTeamChance(double teamPoints, int raidLevel)
	{
		return Math.min(TOA_MAX_CHANCE, Math.max(0, teamPoints) / toaPointsPerPercent(raidLevel) / 100);
	}

	/**
	 * Your chance of a unique for one Tombs raid. Only one unique per raid; it goes to a
	 * player with a chance equal to their share of the points.
	 *
	 * @param personalPoints your points without the 5,000 starting points
	 * @param partyPoints    points the whole party earns once (puzzles); yours when solo
	 * @param teamPoints     the team total, 0 when unknown
	 * @param teamSize       players in the raid
	 * @return NaN when the points or raid level are unknown
	 */
	public static double toa(int personalPoints, int partyPoints, int teamPoints, int teamSize, int raidLevel)
	{
		if (personalPoints <= 0 || raidLevel < 0)
		{
			return Double.NaN;
		}
		if (teamSize <= 1)
		{
			return toaTeamChance(personalPoints + Math.max(0, partyPoints), raidLevel);
		}
		if (teamPoints > 0)
		{
			int team = Math.max(teamPoints, personalPoints);
			return toaTeamChance(team, raidLevel) * personalPoints / team;
		}
		// Team total unknown: below the 55% cap your share works out to your own points
		// over the points per percent, whatever the others did.
		return Math.min(TOA_MAX_CHANCE, personalPoints / toaPointsPerPercent(raidLevel) / 100);
	}

	/** How much of an item's normal share survives at this raid level (1, or 1/50 below its minimum). */
	public static double toaLevelFactor(int raidLevel, Integer minRaidLevel)
	{
		if (minRaidLevel == null || raidLevel < 0 || raidLevel >= minRaidLevel)
		{
			return 1;
		}
		return TOA_LOW_LEVEL_KEEP;
	}
}
