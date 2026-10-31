package com.expecteddrops;

import java.awt.Color;

/** What the in-game overlay shows, worked out after each kill. */
class OverlayInfo
{
	final String boss;
	/** This trip: "20 kills \u00b7 1 unique". */
	final String trip;
	/** What this trip should have by now ("0.41"). */
	final String tripExpected;
	/** The verdict for this trip alone, or "Too early". */
	final String tripLuck;
	final Color tripLuckColor;
	/** The whole account at this boss: "Dry \u00b7 9 of 12.5". */
	final String account;
	final Color accountColor;
	/** The driest item and its streak against the rate ("Bandos hilt \u00b7 1.2x"), or null. */
	final String driest;
	final Color driestColor;
	final long lastKillMillis;

	OverlayInfo(String boss, String trip, String tripExpected, String tripLuck, Color tripLuckColor, String account,
		Color accountColor, String driest, Color driestColor, long lastKillMillis)
	{
		this.boss = boss;
		this.trip = trip;
		this.tripExpected = tripExpected;
		this.tripLuck = tripLuck;
		this.tripLuckColor = tripLuckColor;
		this.account = account;
		this.accountColor = accountColor;
		this.driest = driest;
		this.driestColor = driestColor;
		this.lastKillMillis = lastKillMillis;
	}
}
