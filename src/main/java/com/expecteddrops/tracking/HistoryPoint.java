package com.expecteddrops.tracking;

/**
 * One point of the luck graph: the whole account on one day. Saved with the rest of the
 * progress, at most one point per day (later updates that day replace it).
 */
public class HistoryPoint
{
	/** Days since 1970-01-01 (LocalDate.toEpochDay). */
	long day;
	double expected;
	int received;
	/** Luck percentile 0..1 at the end of that day. */
	double luck;

	HistoryPoint()
	{
	}

	HistoryPoint(long day, double expected, int received, double luck)
	{
		this.day = day;
		this.expected = expected;
		this.received = received;
		this.luck = luck;
	}

	public long getDay()
	{
		return day;
	}

	public double getExpected()
	{
		return expected;
	}

	public int getReceived()
	{
		return received;
	}

	public double getLuck()
	{
		return luck;
	}
}
