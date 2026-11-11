package com.onrate.engine.stats;

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

	/**
	 * The Fractured Archive: the team's chance of a unique in a solo or duo raid with no deaths, 1 in 16
	 * (since the game update of 11 November 2026; see {@link #TFA_CHANGE_MILLIS}).
	 */
	public static final double TFA_BASE_CHANCE = 1 / 16.0;
	/**
	 * Since that update the team's chance is fixed per raid size: one raid in this many gives the team a
	 * unique with 1, 2, 3... 8 players and no deaths (Jagex's loot scaling post).
	 */
	private static final double[] TFA_ONE_IN = {16, 16, 12.5, 10.5, 9.5, 8.5, 8, 7.5};
	/**
	 * When the game update with those chances went live: 11 November 2026, 11:30 UTC (the usual time
	 * of the Wednesday update; the worlds are down around it, so no raid spans it). Raids saved before it
	 * keep the chance they had.
	 */
	public static final long TFA_CHANGE_MILLIS = 1_794_396_600_000L;
	/** Before that update: 1 in 14 for a solo or duo with no deaths. */
	public static final double TFA_BASE_CHANCE_BEFORE = 1 / 14.0;
	/** Before that update: each player beyond two added 4% of the base chance (3: x1.04, 5: x1.12, 8: x1.24). */
	public static final double TFA_BONUS_PER_PLAYER_BEFORE = 0.04;
	/** Contribution a whole raid hands out: 100 per room, 300 for Ocellus, split by damage. */
	public static final double TFA_RAID_CONTRIBUTION = 700;
	/** A death takes 10% of your expected contribution (the raid's total over the team size). */
	public static final double TFA_DEATH_PENALTY = 0.10;
	/** The raid takes 1 to 8 players. */
	public static final int TFA_MAX_TEAM = 8;

	private RaidChance()
	{
	}

	/**
	 * The Fractured Archive's team-size multiplier on the base chance: 1 up to two players, then what
	 * the game's fixed chance for that size is over the solo and duo one (three players: 16 / 12.5 =
	 * x1.28, eight: 16 / 7.5 = x2.13). The base stays a setting, so every size follows a change to it.
	 */
	public static double tfaTeamMultiplier(int teamSize)
	{
		int team = Math.max(1, Math.min(TFA_MAX_TEAM, teamSize));
		return TFA_ONE_IN[0] / TFA_ONE_IN[team - 1];
	}

	/**
	 * The multiplier before the update of 11 November 2026: 1 up to two players, then +4% of the base
	 * per extra player. Jagex's examples (x1.12 for five, x1.24 for eight) added the 4%s rather than
	 * compounding them.
	 */
	public static double tfaTeamMultiplierBefore(int teamSize)
	{
		int team = Math.max(1, Math.min(TFA_MAX_TEAM, teamSize));
		return 1 + TFA_BONUS_PER_PLAYER_BEFORE * Math.max(0, team - 2);
	}

	/** True for a raid saved before the update of 11 November 2026 (or with no date: only older saves have none). */
	public static boolean tfaBeforeChange(long savedMillis)
	{
		return savedMillis < TFA_CHANGE_MILLIS;
	}

	/** Contribution an even share of the raid gives you: 700 solo, 350 in a duo, 175 in a four. */
	public static double tfaExpectedContribution(int teamSize)
	{
		return TFA_RAID_CONTRIBUTION / Math.max(1, Math.min(TFA_MAX_TEAM, teamSize));
	}

	/** Your contribution after deaths: each takes 10% of your expected contribution, never below 0. */
	public static double tfaContributionAfterDeaths(double contribution, int teamSize, int deaths)
	{
		double penalty = TFA_DEATH_PENALTY * tfaExpectedContribution(teamSize) * Math.max(0, deaths);
		return Math.max(0, contribution - penalty);
	}

	/**
	 * Your chance of the unique for one Fractured Archive raid. The team rolls the base chance times
	 * the team-size multiplier, scaled by how much of the raid's 700 contribution the team kept (deaths
	 * shrink it), and the unique goes to a player with their share of the team's contribution. Those
	 * two shares cancel, so your chance only depends on your own contribution:
	 * base x multiplier x your contribution / 700.
	 *
	 * @param contribution your contribution after deaths (0 to 700)
	 * @param baseChance   the team's chance for a solo or duo with no deaths (1/16 since 11 November 2026)
	 * @return NaN when the contribution is unknown (negative)
	 */
	public static double tfa(double contribution, int teamSize, double baseChance)
	{
		if (contribution < 0 || Double.isNaN(contribution))
		{
			return Double.NaN;
		}
		double share = Math.min(contribution, TFA_RAID_CONTRIBUTION) / TFA_RAID_CONTRIBUTION;
		return Math.max(0, baseChance) * tfaTeamMultiplier(teamSize) * share;
	}

	/**
	 * {@link #tfa} for a raid from before the update of 11 November 2026: 1 in 14 for the team, +4% of
	 * that per player beyond two, times your share.
	 */
	public static double tfaBefore(double contribution, int teamSize)
	{
		if (contribution < 0 || Double.isNaN(contribution))
		{
			return Double.NaN;
		}
		double share = Math.min(contribution, TFA_RAID_CONTRIBUTION) / TFA_RAID_CONTRIBUTION;
		return Math.max(0, TFA_BASE_CHANCE_BEFORE) * tfaTeamMultiplierBefore(teamSize) * share;
	}

	/**
	 * Your chance when only the team size (and maybe your deaths) is known: you are assumed to do
	 * an even share of the damage. A duo with no deaths gets 1/32 each.
	 *
	 * @param deaths your deaths, or a negative number when unknown (counted as none)
	 */
	public static double tfaEven(int teamSize, int deaths, double baseChance)
	{
		double contribution = tfaContributionAfterDeaths(tfaExpectedContribution(teamSize), teamSize, deaths);
		return tfa(contribution, teamSize, baseChance);
	}

	/** {@link #tfaEven} for a raid from before the update of 11 November 2026 (a duo with no deaths: 1/28 each). */
	public static double tfaEvenBefore(int teamSize, int deaths)
	{
		double contribution = tfaContributionAfterDeaths(tfaExpectedContribution(teamSize), teamSize, deaths);
		return tfaBefore(contribution, teamSize);
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

	/**
	 * Tombs of Amascut pet (Tumeken's guardian): 1% per (350,000 - 700 x level) points, where the
	 * level counts in full up to 400 and a third above that, up to 550. Not capped at 55%.
	 */
	public static double toaPetPointsPerPercent(int raidLevel)
	{
		int level = Math.min(550, Math.max(0, raidLevel));
		double scaled = level <= 400 ? level : 400 + (level - 400) / 3.0;
		return 350_000 - 700 * scaled;
	}

	/** Your chance of the pet in one raid, split over the team like a purple. NaN without points. */
	public static double toaPet(int personalPoints, int partyPoints, int teamPoints, int teamSize, int raidLevel)
	{
		if (personalPoints <= 0 || raidLevel < 0)
		{
			return Double.NaN;
		}
		double perPercent = toaPetPointsPerPercent(raidLevel);
		if (teamSize <= 1)
		{
			return Math.min(1, (personalPoints + Math.max(0, partyPoints)) / perPercent / 100);
		}
		// your share of the team's chance is your own points over the points per percent
		return Math.min(1, personalPoints / perPercent / 100);
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
