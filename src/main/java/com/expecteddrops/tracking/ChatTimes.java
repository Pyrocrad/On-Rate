package com.expecteddrops.tracking;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads how long a kill or raid took from the game's chat messages ("Fight duration: 1:01.20",
 * "Team size: 3 players Duration: 26:10.20", "Subdued in 5:12.60"...). The wordings are the ones
 * RuneLite's chat commands plugin reads for personal bests. Times of single rooms and waves
 * ("Wave 'The Maiden of Sugadinti' complete! Duration: 1:41.40") are not the time of the whole
 * kill, so they are ignored.
 */
public final class ChatTimes
{
	/** A reading from a message that names the whole kill or raid time. */
	public static final int RANK_TOTAL = 3;
	/** A reading from a plain "duration:" style message. */
	public static final int RANK_KILL = 2;

	private static final String TIME = "(\\d+(?::\\d{2}){1,2}(?:\\.\\d+)?)";
	private static final long MAX_MILLIS = 24L * 60 * 60 * 1000;

	/** Chambers of Xeric: "Congratulations - your raid is complete! Team size: 3 players Duration: 26:10.20". */
	private static final Pattern RAID_COMPLETE = Pattern.compile("your raid is complete!?.*?duration:\\s*" + TIME, Pattern.CASE_INSENSITIVE);
	/** Tombs of Amascut: "Tombs of Amascut: Expert Mode challenge completion time: 31:35.40". Not used: only the total counts. */
	private static final Pattern CHALLENGE_TIME = Pattern.compile("challenge completion time:\\s*" + TIME, Pattern.CASE_INSENSITIVE);
	/**
	 * The whole run: "Theatre of Blood total completion time: 23:50.40", "Tombs of Amascut: Expert Mode total
	 * completion time: 36:00.00", "The Fractured Archive: Total completion time: 12:00.00".
	 */
	private static final Pattern TOTAL_TIME = Pattern.compile("total completion time:\\s*" + TIME, Pattern.CASE_INSENSITIVE);
	/** Bosses, the Gauntlet ("Challenge duration"), Tempoross ("Subdued in"), other timed content. */
	private static final Pattern KILL_TIME = Pattern.compile(
		"(?:(?:fight |lap |challenge |corrupted challenge )?duration:|subdued in|(?<!total )completion time:)\\s*" + TIME,
		Pattern.CASE_INSENSITIVE);
	/** Messages about a part of a run: their time is not the run's. */
	private static final Pattern PART_OF_A_RUN = Pattern.compile("\\bwave\\b|\\broom\\b|challenge complete|\\bphase\\b", Pattern.CASE_INSENSITIVE);

	private ChatTimes()
	{
	}

	/** A time read from a message and how sure we are it is the whole kill's. */
	public static final class Reading
	{
		private final int millis;
		private final int rank;

		Reading(int millis, int rank)
		{
			this.millis = millis;
			this.rank = rank;
		}

		public int getMillis()
		{
			return millis;
		}

		/** Higher wins when several messages of one kill give a time. */
		public int getRank()
		{
			return rank;
		}
	}

	/** The time of the whole kill or raid in this message, or null when it has none. */
	public static Reading read(String message)
	{
		if (message == null)
		{
			return null;
		}
		String plain = message.replace("<br>", " ").replaceAll("<[^>]*>", "").replaceAll("@[^@\\s]{1,12}@", "");
		if (PART_OF_A_RUN.matcher(plain).find())
		{
			return null;
		}
		if (CHALLENGE_TIME.matcher(plain).find())
		{
			// Tombs: the challenge time leaves out the rooms around the fight; the total that comes with it is used instead
			return null;
		}
		Reading r = first(RAID_COMPLETE, plain, RANK_TOTAL);
		if (r == null)
		{
			r = first(TOTAL_TIME, plain, RANK_TOTAL);
		}
		if (r == null)
		{
			r = first(KILL_TIME, plain, RANK_KILL);
		}
		return r;
	}

	private static Reading first(Pattern pattern, String plain, int rank)
	{
		Matcher m = pattern.matcher(plain);
		if (!m.find())
		{
			return null;
		}
		int millis = parseMillis(m.group(1));
		return millis > 0 ? new Reading(millis, rank) : null;
	}

	/** "26:10.20", "1:01.2", "1:02:03" to milliseconds; 0 when it isn't a sensible time. */
	public static int parseMillis(String time)
	{
		if (time == null)
		{
			return 0;
		}
		String[] parts = time.trim().split(":");
		if (parts.length < 2 || parts.length > 3)
		{
			return 0;
		}
		try
		{
			double seconds = Double.parseDouble(parts[parts.length - 1]);
			long minutes = Long.parseLong(parts[parts.length - 2]);
			long hours = parts.length == 3 ? Long.parseLong(parts[0]) : 0;
			long total = Math.round((hours * 3600 + minutes * 60 + seconds) * 1000);
			return total > 0 && total < MAX_MILLIS ? (int) total : 0;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** "26:10", "1:01.2" (tenths under ten minutes), "1:02:03" for an hour or more. */
	public static String format(int millis)
	{
		if (millis <= 0)
		{
			return "";
		}
		long tenths = Math.round(millis / 100.0);
		long totalSeconds = tenths / 10;
		long hours = totalSeconds / 3600;
		long minutes = totalSeconds % 3600 / 60;
		long seconds = totalSeconds % 60;
		if (hours > 0)
		{
			return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
		}
		if (millis < 10 * 60_000)
		{
			return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths % 10);
		}
		return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
	}
}
