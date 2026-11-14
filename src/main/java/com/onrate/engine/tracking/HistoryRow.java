package com.onrate.engine.tracking;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A kill for the History section, with what On Rate saved about its raid (points, purple
 * chance, team) when there was one.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class HistoryRow
{
	private final KillEntry kill;
	private final RaidRecord raid;
	private final double purpleChance;
}
