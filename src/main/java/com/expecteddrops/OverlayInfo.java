package com.expecteddrops;

import java.awt.Color;

/** What the in-game overlay shows, worked out after each kill. */
class OverlayInfo
{
	final String boss;
	final String verdict;
	final Color verdictColor;
	final String uniques;
	final String driestLabel;
	final String driest;
	final Color driestColor;
	final String trip;
	final long lastKillMillis;

	OverlayInfo(String boss, String verdict, Color verdictColor, String uniques, String driestLabel, String driest,
		Color driestColor, String trip, long lastKillMillis)
	{
		this.boss = boss;
		this.verdict = verdict;
		this.verdictColor = verdictColor;
		this.uniques = uniques;
		this.driestLabel = driestLabel;
		this.driest = driest;
		this.driestColor = driestColor;
		this.trip = trip;
		this.lastKillMillis = lastKillMillis;
	}
}
