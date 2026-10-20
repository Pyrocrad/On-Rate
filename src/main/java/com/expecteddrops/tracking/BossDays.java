package com.expecteddrops.tracking;

/** A boss's kills, uniques and expected over a stretch of days (Recent tab). */
public class BossDays
{
	private final String boss;
	private final DayCount count;
	private final java.util.List<String> uniqueNames;

	BossDays(String boss, DayCount count, java.util.List<String> uniqueNames)
	{
		this.boss = boss;
		this.count = count;
		this.uniqueNames = uniqueNames;
	}

	/** The uniques in those days that History knows the name of, oldest first ("Tyrannical ring"). */
	public java.util.List<String> getUniqueNames()
	{
		return uniqueNames;
	}

	/** The boss name, or null for the total of all bosses. */
	public String getBoss()
	{
		return boss;
	}

	public int getKills()
	{
		return count.kills;
	}

	public int getUniques()
	{
		return count.uniques;
	}

	public double getExpected()
	{
		return count.expected;
	}
}
