package com.onrate.engine.raids;

import com.google.gson.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

/**
 * Reads your raid times from the Raid Data Tracker plugin's log files, so raids start on your own
 * times instead of waiting for five timed raids. Read only: it never writes to that plugin's folder, and
 * it reads only the folder the player picked with RuneLite's file chooser.
 *
 * <p>The plugin keeps one file per raid, {@code raid_tracker_data.log}, with one JSON object per
 * line, in {@code .runelite/raid-data tracker/<name>/<raid>/} (the folder name has a space in it; older
 * versions kept the raid folders directly in {@code raid-data tracker/}). Only completed raids of this
 * account (matched by its account hash) with a plausible time are taken, so a second account's raids or
 * a raid you left don't count. Its times are in whole seconds.
 *
 * <p>Which time: the whole raid's time ({@code raidTime}), which the plugin reads from the game's
 * "Duration" (Chambers) or "total completion time" (Tombs) message. For Tombs that is the total, not the
 * challenge time ({@code toaCompTime}): On Rate counts only the total there. Chambers and Tombs only: the
 * plugin does not record whether a Theatre of Blood raid was Normal or Hard Mode, so those times can't be
 * told apart and are left out.
 */
public final class RaidDataImport
{
	/** A raid time shorter than this isn't a finished raid (a record from a failed or abandoned run). */
	static final long MIN_MILLIS = 2 * 60_000L;
	/** A raid time longer than this is a stuck timer, not a raid. */
	static final long MAX_MILLIS = 3 * 60 * 60_000L;
	static final int MILLIS_PER_SECOND = 1000;
	/** The other plugin's folder inside {@code .runelite}: the name has a space, not a hyphen, before "tracker". */
	public static final String FOLDER = "raid-data tracker";
	/** The log file each raid folder holds. */
	static final String LOG_NAME = "raid_tracker_data.log";
	/** Raid folders of the other plugin; "unknown" holds raids it could not place and is skipped. */
	private static final String[] RAIDS = {"cox", "toa"};

	/** The boss name On Rate uses for each folder. */
	private static String boss(String raid)
	{
		switch (raid)
		{
			case "cox":
				return "Chambers of Xeric";
			default:
				return "Tombs of Amascut";
		}
	}

	private RaidDataImport()
	{
	}

	/**
	 * One finished raid as the other plugin saved it: its mode and that mode's completion count, when
	 * it ended, how long it took (-1 when unknown), the team, the points, the raid level (Tombs only,
	 * else -1) and the unique it gave you (null for none, or a unique that went to a teammate).
	 */
	public static final class Raid
	{
		public final String part;
		public final int kc;
		public final long when;
		public final long millis;
		public final int teamSize;
		public final int personalPoints;
		public final int totalPoints;
		public final int raidLevel;
		public final String unique;

		Raid(String part, int kc, long when, long millis, int teamSize, int personalPoints, int totalPoints, int raidLevel, String unique)
		{
			this.part = part;
			this.kc = kc;
			this.when = when;
			this.millis = millis;
			this.teamSize = teamSize;
			this.personalPoints = personalPoints;
			this.totalPoints = totalPoints;
			this.raidLevel = raidLevel;
			this.unique = unique;
		}
	}

	/** One raid time, by boss and raid mode. {@code when} is when the raid ended, in epoch millis. */
	public static final class Times
	{
		/** Boss, then mode, then {when, millis} pairs. */
		private final Map<String, Map<String, List<long[]>>> byBoss = new LinkedHashMap<>();
		/** Boss, then its whole raids in the order read. */
		private final Map<String, List<Raid>> raids = new LinkedHashMap<>();

		void addRaid(String boss, Raid raid)
		{
			raids.computeIfAbsent(boss, k -> new ArrayList<>()).add(raid);
		}

		/** The whole raids read, per boss. */
		public Map<String, List<Raid>> raids()
		{
			return raids;
		}

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
	 * The times of {@code accountHash} found under the folder the player chose (Raid Data Tracker's
	 * folder, or the {@code .runelite} folder that holds it), or none when it has no raid logs.
	 * Unreadable files and lines are skipped.
	 */
	public static Times read(Filepath chosen, long accountHash)
	{
		Times times = new Times();
		if (chosen == null || !chosen.isDirectory() || accountHash == -1)
		{
			return times;
		}
		Filepath root = chosen;
		try
		{
			Filepath inside = chosen.joinSegment(FOLDER);
			if (inside.isDirectory())
			{
				root = inside;
			}
		}
		catch (RuntimeException e)
		{
			// read the folder as it was chosen
		}
		Filepath top = root;
		List<Filepath> folders = new ArrayList<>();
		folders.add(top);
		try (Stream<Filepath> children = top.walk(1))
		{
			folders.addAll(children.filter(f -> !f.equals(top) && f.isDirectory()).collect(Collectors.toList()));
		}
		catch (IOException | RuntimeException e)
		{
			// unreadable folder: the top level only
		}
		for (Filepath folder : folders)
		{
			for (String raid : RAIDS)
			{
				try
				{
					Filepath log = folder.joinSegment(raid).joinSegment(LOG_NAME);
					if (log.isFile())
					{
						readLog(log, raid, accountHash, times);
					}
				}
				catch (RuntimeException e)
				{
					// skip this folder
				}
			}
		}
		return times;
	}

	private static void readLog(Filepath log, String raid, long accountHash, Times times)
	{
		try (BufferedReader reader = log.openBufferedReader())
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
		String part = part(o, raid);
		if (when <= 0 || part == null)
		{
			return;
		}
		int seconds = seconds(o);
		long millis = seconds <= 0 ? -1 : (long) seconds * MILLIS_PER_SECOND;
		boolean plausible = millis >= MIN_MILLIS && millis <= MAX_MILLIS;
		if (plausible)
		{
			times.add(boss(raid), part, when, millis);
		}
		int kc = num(o, "completionCount");
		int personal = num(o, "personalPoints");
		if (kc > 0 && personal > 0)
		{
			// the unique only counts when it was yours, not a teammate's
			String unique = bool(o, "specialLootInOwnName") && has(o, "specialLoot") ? o.get("specialLoot").getAsString().trim() : "";
			times.addRaid(boss(raid), new Raid(part, kc, when, plausible ? millis : -1, Math.max(0, num(o, "teamSize")), personal,
				Math.max(0, num(o, "totalPoints")), "toa".equals(raid) ? num(o, "raidLevel") : -1, unique.isEmpty() ? null : unique));
		}
	}

	/** The raid's whole time in seconds (see the class comment): -1 when there is none. */
	private static int seconds(JsonObject o)
	{
		return num(o, "raidTime");
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
