package com.expecteddrops.tracking;

import java.util.Map;

/** Where a trip started: the numbers of its boss just before its first kill. Not saved. */
class Trip
{
	final String boss;
	final long startMillis;
	long lastMillis;
	final int startKc;
	final double startExpected;
	final int startReceived;
	final Map<String, Integer> receivedPerRow;
	/** Shared items (godsword shards...) this boss had dropped when the trip started, by pool key. */
	Map<String, Integer> poolSeenStart = new java.util.HashMap<>();

	Trip(String boss, long startMillis, int startKc, double startExpected, int startReceived,
		Map<String, Integer> receivedPerRow)
	{
		this.boss = boss;
		this.startMillis = startMillis;
		this.lastMillis = startMillis;
		this.startKc = startKc;
		this.startExpected = startExpected;
		this.startReceived = startReceived;
		this.receivedPerRow = receivedPerRow;
	}
}
