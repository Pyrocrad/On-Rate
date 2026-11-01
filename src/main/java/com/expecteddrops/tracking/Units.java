package com.expecteddrops.tracking;

import com.expecteddrops.data.BossDefinition;
import java.util.Locale;

/** What one completion at a boss is called: kills, raids, delves, chests, runs or rolls. Split out of {@link Tracker}. */
public final class Units
{
	private Units()
	{
	}

	/** Word for what is counted at this boss: kills, raids, chests or levels. */
	/** "kills", "raids", "delves" (Doom) or "chests" (Barrows, Moons): what one completion is called. */
	public static String unit(BossDefinition boss)
	{
		if (boss.getRollsPerKill() > 0)
		{
			return "rolls";
		}
		if (boss.getWaves() > 0)
		{
			return "runs";
		}
		if (boss.getRaid() != null)
		{
			return "raids";
		}
		if (boss.isDelve())
		{
			return "delves";
		}
		for (String kc : boss.getKcNames())
		{
			if (kc.toLowerCase(Locale.ROOT).contains("chest"))
			{
				return "chests";
			}
		}
		return "kills";
	}

	/** {@link #unit} for one: "kill", "raid", "delve" or "chest". */
	/** "1 kill", "5 kills": the unit of a boss for a count. */
	static String units(BossDefinition boss, int count)
	{
		return count == 1 ? unitOne(boss) : unit(boss);
	}

	public static String unitOne(BossDefinition boss)
	{
		String units = unit(boss);
		return units.substring(0, units.length() - 1);
	}
}
