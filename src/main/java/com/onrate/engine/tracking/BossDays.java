package com.onrate.engine.tracking;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

/** A boss's kills, uniques and expected over a stretch of days (Recent tab). */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class BossDays
{
	@Getter private final String boss;
	private final DayCount count;
	@Getter private final List<String> uniqueNames;

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

