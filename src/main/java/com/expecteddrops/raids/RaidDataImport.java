package com.expecteddrops.raids;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads your raid times from the Raid Data Tracker plugin's log files, so raids start on your own
 * times instead of waiting for five timed raids. Read only: it never writes to that plugin's folder.
 *
 * <p>The plugin keeps one file per raid, {@code raid_tracker_data.log}, with one JSON object per
 * line, in {@code raid-data-tracker/<raid>/} and, in newer versions, {@code raid-data-tracker/<name>/<raid>/}.
 * Only completed raids of this account (matched by its account hash) with a plausible time are
 * taken, so a second account's raids or a raid you left don't count. Times are in game ticks
 * (0.6 s).
 *
 * <p>Which time: Chambers of Xeric and Theatre of Blood the whole raid's time ({@code raidTime},
 * what the game's "total completion time" says; its {@code tobCompTime} is only the rooms added up).
 * Tombs of Amascut the challenge time ({@code toaCompTime}, the rooms), which is what On Rate times
 * itself, so a Tombs raid the plugin has only a total for is left out rather than mixed in.
 */
public final class RaidDataImport
{
	/** A raid time shorter than this isn't a finished raid (a record from a failed or abandoned run). */
	static final long MIN_MILLIS = 2 * 60_000L;
	/** A raid time longer than this is a stuck timer, not a raid. */
	static final long MAX_MILLIS = 3 * 60 * 60_000L;
	static final int MILLIS_PER_TICK = 600;
	/** The log file each raid folder holds. */
	static final String LOG_NAME = "raid_tracker_data.log";
	/** Raid folders of the other plugin; "unknown" holds raids it could not place and is skipped. */
	private static final String[] RAIDS = {"cox", "toa", "tob"};

	/** The boss name On Rate uses for each folder. */
	private static String boss(String raid)
	{
		switch (raid)
		{
			case "cox":
				return "Chambers of Xeric";
			case "toa":
				return "Tombs of Amascut";
			default:
				return "Theatre of Blood";
		}
	}

	private RaidDataImport()
	{
	}

	/** One raid time, by boss and raid mode. {@code when} is when the raid ended, in epoch millis. */
	public static final class Times
	{
		/** Boss, then mode, then {when, millis} pairs. */
		private final Map<String, Map<String, List<long[]>>> byBoss = new LinkedHashMap<>();

		void add(String boss, String part, long when, long millis)
		{
			byBoss.computeIfAbsent(boss, k -> new LinkedHashMap<>()).computeIfAbsent(part, k -> new ArrayList<>())
				.add(new long[]{when, millis});
		}

		public Map<String, Map<String, List<long[]>>> byBoss()
		{
			return byBoss;
		}

		public int count()
		{
			int n = 0;
			for (Map<String, List<long[]>> parts : byBoss.values())
			{
				for (List<long[]> list : parts.values())
				{
					n += list.size();
				}
			}
			return n;
		}
	}

	/**
	 * The times of {@code accountHash} found under the other plugin's folder, or none when the
	 * folder isn't there (the plugin isn't installed). Unreadable files and lines are skipped.
	 */
	public static Times read(File root, long accountHash)
	{
		Times times = new Times();
		if (root == null || !root.isDirectory() || accountHash == -1)
		{
			return times;
		}
		List<File> folders = new ArrayList<>();
		folders.add(root);
		File[] users = root.listFiles(File::isDirectory);
		if (users != null)
		{
			for (File u : users)
			{
				folders.add(u);
			}
		}
		for (File folder : folders)
		{
			for (String raid : RAIDS)
			{
				File log = new File(new File(folder, raid), LOG_NAME);
				if (log.isFile())
				{
					readLog(log, raid, accountHash, times);
				}
			}
		}
		return times;
	}

	private static void readLog(File log, String raid, long accountHash, Times times)
	{
		try (BufferedReader reader = Files.newBufferedReader(log.toPath(), StandardCharsets.UTF_8))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				line = line.trim();
				if (!line.isEmpty())
				{
					readLine(line, raid, accountHash, times);
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			// a file being written or in another encoding: take what was read, skip the rest
		}
	}

	private static void readLine(String line, String raid, long accountHash, Times times)
	{
		JsonObject o;
		try
		{
			JsonElement e = new JsonParser().parse(line);
			if (!e.isJsonObject())
			{
				return;
			}
			o = e.getAsJsonObject();
		}
		catch (RuntimeException e)
		{
			return;
		}
		try
		{
			addRecord(o, raid, accountHash, times);
		}
		catch (RuntimeException e)
		{
			// a field with the wrong type: skip this line only
		}
	}

	private static void addRecord(JsonObject o, String raid, long accountHash, Times times)
	{
		if (!has(o, "accountHash") || o.get("accountHash").getAsLong() != accountHash || !bool(o, "raidComplete"))
		{
			return;
		}
		long when = has(o, "date") ? o.get("date").getAsLong() : 0;
		int ticks = ticks(o, raid);
		if (when <= 0 || ticks <= 0)
		{
			return;
		}
		long millis = (long) ticks * MILLIS_PER_TICK;
		if (millis < MIN_MILLIS || millis > MAX_MILLIS)
		{
			return;
		}
		String part = part(o, raid);
		if (part != null)
		{
			times.add(boss(raid), part, when, millis);
		}
	}

	/** The raid's time in ticks: see the class comment for which one. -1 when there is none. */
	private static int ticks(JsonObject o, String raid)
	{
		switch (raid)
		{
			case "toa":
				return num(o, "toaCompTime");
			default:
				return num(o, "raidTime");
		}
	}

	/**
	 * The raid mode, as On Rate names its parts. Tombs of Amascut modes come from the raid level
	 * (Entry below 150, Normal 150 to 295, Expert from 300), because the other plugin's completion
	 * count mixes the modes.
	 */
	static String part(JsonObject o, String raid)
	{
		switch (raid)
		{
			case "cox":
				return bool(o, "challengeMode") ? "Challenge Mode" : "Normal";
			case "tob":
				return bool(o, "challengeMode") ? "Hard Mode" : "Normal";
			default:
				int level = num(o, "raidLevel");
				if (level < 0)
				{
					return null;
				}
				return level >= 300 ? "Expert Mode" : level >= 150 ? "Normal" : "Entry Mode";
		}
	}

	private static boolean has(JsonObject o, String key)
	{
		return o.has(key) && o.get(key).isJsonPrimitive();
	}

	private static boolean bool(JsonObject o, String key)
	{
		return has(o, key) && o.get(key).getAsBoolean();
	}

	private static int num(JsonObject o, String key)
	{
		return has(o, key) ? o.get(key).getAsInt() : -1;
	}
}
