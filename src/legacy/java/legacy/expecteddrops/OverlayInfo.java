package legacy.expecteddrops;

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
	final long lastKillMillis;

	OverlayInfo(String boss, String trip, String tripExpected, String tripLuck, Color tripLuckColor, long lastKillMillis)
	{
		this.boss = boss;
		this.trip = trip;
		this.tripExpected = tripExpected;
		this.tripLuck = tripLuck;
		this.tripLuckColor = tripLuckColor;
		this.lastKillMillis = lastKillMillis;
	}
}
