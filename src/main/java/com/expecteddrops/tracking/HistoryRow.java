package com.expecteddrops.tracking;

/**
 * A kill for the History section, with what On Rate saved about its raid (points, purple
 * chance, team) when there was one.
 */
public class HistoryRow
{
	private final KillEntry kill;
	private final RaidRecord raid;
	private final double purpleChance;

	HistoryRow(KillEntry kill, RaidRecord raid, double purpleChance)
	{
		this.kill = kill;
		this.raid = raid;
		this.purpleChance = purpleChance;
	}

	public KillEntry getKill()
	{
		return kill;
	}

	/** The raid's points and team, or null for other bosses (or a raid without a record). */
	public RaidRecord getRaid()
	{
		return raid;
	}

	/** Your purple chance in that raid, NaN when unknown. */
	public double getPurpleChance()
	{
		return purpleChance;
	}
}
