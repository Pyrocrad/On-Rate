package com.expecteddrops.tracking;

import com.google.gson.annotations.SerializedName;

/**
 * One boss on one day: kills, uniques and the uniques you should have had from those kills.
 * Kept for the last two weeks, for "Today" and "Last 7 days" in the Recent tab.
 */
public class DayCount
{
	@SerializedName("k")
	int kills;
	@SerializedName("u")
	int uniques;
	@SerializedName("e")
	double expected;
	/**
	 * Time of the earliest kill in this count. Kills in History from before it (On Rate didn't
	 * count days yet) are added once, see Tracker#fillDaysFromHistory. Null for old counts.
	 */
	@SerializedName("f")
	Long first;

	public int getKills()
	{
		return kills;
	}

	public int getUniques()
	{
		return uniques;
	}

	public double getExpected()
	{
		return expected;
	}
}
