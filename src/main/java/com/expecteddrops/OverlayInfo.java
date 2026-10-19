package com.expecteddrops;

import java.awt.Color;

/** What the in-game overlay shows, worked out after each kill. */
class OverlayInfo
{
	final String boss;
	final String verdict;
	final Color verdictColor;
	final String uniques;
	/** The driest item's name, and how long it has been dry ("434 kills (1.4x)"). */
	final String driestItem;
	final String driest;
	final Color driestColor;
	final String trip;
	final long lastKillMillis;

	OverlayInfo(String boss, String verdict, Color verdictColor, String uniques, String driestItem, String driest,
		Color driestColor, String trip, long lastKillMillis)
	{
		this.boss = boss;
		this.verdict = verdict;
		this.verdictColor = verdictColor;
		this.uniques = uniques;
		this.driestItem = driestItem;
		this.driest = driest;
		this.driestColor = driestColor;
		this.trip = trip;
		this.lastKillMillis = lastKillMillis;
	}
}
