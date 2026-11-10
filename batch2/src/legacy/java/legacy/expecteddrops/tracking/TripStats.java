package legacy.expecteddrops.tracking;

import legacy.expecteddrops.stats.DropMath;
import java.util.Collections;
import java.util.List;

/** The current trip: kills, uniques and what was expected since it started. */
public class TripStats
{
	private final String boss;
	private final long startMillis;
	private final long lastMillis;
	private final int kills;
	private final int received;
	private final double expected;
	private final List<String> drops;
	private final double sharedExpected;
	private final int sharedReceived;
	private final List<String> sharedNames;

	TripStats(String boss, long startMillis, long lastMillis, int kills, int received, double expected, List<String> drops,
		double sharedExpected, int sharedReceived, List<String> sharedNames)
	{
		this.sharedExpected = sharedExpected;
		this.sharedReceived = sharedReceived;
		this.sharedNames = Collections.unmodifiableList(sharedNames);
		this.boss = boss;
		this.startMillis = startMillis;
		this.lastMillis = lastMillis;
		this.kills = kills;
		this.received = received;
		this.expected = expected;
		this.drops = Collections.unmodifiableList(drops);
	}

	public String getBoss()
	{
		return boss;
	}

	public long getStartMillis()
	{
		return startMillis;
	}

	public long getLastMillis()
	{
		return lastMillis;
	}

	public int getKills()
	{
		return kills;
	}

	public int getReceived()
	{
		return received;
	}

	public double getExpected()
	{
		return expected;
	}

	/** Items received this trip, one entry per drop. */
	public List<String> getDrops()
	{
		return drops;
	}

	/** Expected from shared items (godsword shards...) the boss drops, part of {@link #getExpected()}. */
	public double getSharedExpected()
	{
		return sharedExpected;
	}

	/** Shared items received this trip, part of {@link #getReceived()}. */
	public int getSharedReceived()
	{
		return sharedReceived;
	}

	/** Names of the shared items counted in the shared figures. */
	public List<String> getSharedNames()
	{
		return sharedNames;
	}

	/** Luck for this trip alone, from the first kill on (NaN only when nothing is expected). */
	public double getLuck()
	{
		return DropMath.luck(received, expected);
	}

	/** Of players doing the same kills: {share with fewer uniques, share with as many}; null when nothing was expected. */
	public double[] getShares()
	{
		return DropMath.shares(received, expected);
	}

	/** Kills per hour, or NaN for trips shorter than a minute. */
	public double getKillsPerHour()
	{
		long millis = lastMillis - startMillis;
		return millis < 60_000 || kills < 2 ? Double.NaN : (kills - 1) * 3_600_000.0 / millis;
	}
}
