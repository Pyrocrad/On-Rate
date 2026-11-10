package com.onrate.engine.tracking;

/**
 * A finished raid read from another plugin's files (Raid Data Tracker), to add to the raids On
 * Rate saved itself: its mode and that mode's completion count, when it ended, its time, team,
 * points, raid level and the unique it gave you.
 */
public final class ImportedRaid
{
	final String part;
	final int kc;
	final long when;
	final long millis;
	final int teamSize;
	final int personalPoints;
	final int totalPoints;
	final int raidLevel;
	final String unique;

	/**
	 * @param millis the raid's time, or -1 when unknown
	 * @param raidLevel Tombs of Amascut's raid level, -1 for another raid
	 * @param unique the unique this raid gave you, or null
	 */
	public ImportedRaid(String part, int kc, long when, long millis, int teamSize, int personalPoints, int totalPoints,
		int raidLevel, String unique)
	{
		this.part = part;
		this.kc = kc;
		this.when = when;
		this.millis = millis;
		this.teamSize = teamSize;
		this.personalPoints = personalPoints;
		this.totalPoints = totalPoints;
		this.raidLevel = raidLevel;
		this.unique = unique;
	}
}
