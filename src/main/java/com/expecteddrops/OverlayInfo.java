package com.expecteddrops;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import java.awt.Color;

/** What the in-game overlay shows, worked out after each kill. */
@AllArgsConstructor(access = AccessLevel.PACKAGE)
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
}
