package com.expecteddrops.tracking;

/**
 * One finished raid with what On Rate could read about it. Saved with the completion, so
 * the purple chance can be worked out from the points instead of the settings.
 * Fields the game didn't give stay at 0 / -1.
 */
public class RaidRecord
{
	/** "cox", "toa", "tob" or "fractured": the start of the raid setting keys. */
	String family;
	/** Mode, matched from the completion count message, e.g. "Challenge Mode". */
	String part;
	/** Completion count of that mode at the end of this raid. */
	int kc;
	/** Your points (ToA: without the 5,000 everyone starts with; see {@link #estimated}). 0 = unknown. */
	int personalPoints;
	/** Team points. 0 = unknown (a ToA team without the Tombs of Amascut plugin's total). */
	int teamPoints;
	/** ToA: points the whole party earns once (puzzle rooms). */
	int partyPoints;
	int teamSize;
	/** ToA raid level, -1 when not a ToA raid. */
	int raidLevel = -1;
	/** True when the points are On Rate's own estimate (ToA). */
	boolean estimated;
	/** ToB board: your deaths, the team's deaths and whether you were MVP (-1 / null = unknown). */
	int deaths = -1;
	int teamDeaths = -1;
	Boolean mvp;
	/**
	 * The Fractured Archive: your contribution out of the raid's 700 before deaths, or -1 when
	 * unknown (then an even share of the team size is assumed).
	 */
	double contribution = -1;
	/** When it was saved (epoch millis), to match the board or a party message to it. */
	long savedMillis;

	public RaidRecord()
	{
	}

	public static RaidRecord cox(int personalPoints, int teamPoints, int teamSize)
	{
		RaidRecord r = new RaidRecord();
		r.family = "cox";
		r.personalPoints = personalPoints;
		r.teamPoints = teamPoints;
		r.teamSize = teamSize;
		return r;
	}

	/** {@code teamPoints} 0 when unknown. */
	public static RaidRecord toa(int personalPoints, int partyPoints, int teamPoints, int teamSize, int raidLevel)
	{
		RaidRecord r = new RaidRecord();
		r.family = "toa";
		r.personalPoints = personalPoints;
		r.partyPoints = partyPoints;
		r.teamPoints = teamPoints;
		r.teamSize = teamSize;
		r.raidLevel = raidLevel;
		r.estimated = true;
		return r;
	}

	/** A Tombs of Amascut raid with the exact points the game sent (not On Rate's estimate). */
	public static RaidRecord toaExact(int personalPoints, int teamSize, int raidLevel)
	{
		RaidRecord r = toa(personalPoints, 0, 0, teamSize, raidLevel);
		r.estimated = false;
		return r;
	}

	public static RaidRecord tob(int teamSize, int deaths, int teamDeaths, Boolean mvp)
	{
		RaidRecord r = new RaidRecord();
		r.family = "tob";
		r.teamSize = teamSize;
		r.deaths = deaths;
		r.teamDeaths = teamDeaths;
		r.mvp = mvp;
		return r;
	}

	/**
	 * A Fractured Archive raid. {@code deaths} -1 and {@code contribution} -1 when unknown: the
	 * chance then assumes an even share of the damage and no deaths.
	 */
	public static RaidRecord fractured(int teamSize, int deaths, double contribution)
	{
		RaidRecord r = new RaidRecord();
		r.family = "fractured";
		r.teamSize = teamSize;
		r.deaths = deaths;
		r.contribution = contribution;
		return r;
	}

	public double getContribution()
	{
		return contribution;
	}

	public String getFamily()
	{
		return family;
	}

	public String getPart()
	{
		return part;
	}

	public int getKc()
	{
		return kc;
	}

	public int getPersonalPoints()
	{
		return personalPoints;
	}

	public int getTeamPoints()
	{
		return teamPoints;
	}

	public int getPartyPoints()
	{
		return partyPoints;
	}

	public int getTeamSize()
	{
		return teamSize;
	}

	public int getRaidLevel()
	{
		return raidLevel;
	}

	public boolean isEstimated()
	{
		return estimated;
	}

	public int getDeaths()
	{
		return deaths;
	}

	public int getTeamDeaths()
	{
		return teamDeaths;
	}

	public Boolean getMvp()
	{
		return mvp;
	}
}
