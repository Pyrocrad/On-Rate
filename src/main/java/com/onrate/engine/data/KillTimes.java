package com.onrate.engine.data;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Average time for a kill or raid, bundled with the plugin (killtimes.json): the stand-in for the
 * Time page until you have enough timed kills of your own. Read once; nothing is fetched while
 * the plugin runs.
 */
public final class KillTimes
{
	private static KillTimes bundled;

	/** The only place a guide link may point at: the OSRS Wiki's article address. */
	private static final String WIKI_BASE = "https://oldschool.runescape.wiki/w/";

	private final Map<String, Raw> bosses;
	private final Doom doom;
	private final String guideBase;

	/** A figure from the file: kills (raids, completions) per hour, and where it comes from. */
	public static final class Estimate
	{
		private final double killsPerHour;
		private final List<String> guides;
		private final String note;
		private final boolean lowConfidence;

		Estimate(Raw raw)
		{
			this.killsPerHour = raw.kph;
			this.guides = raw.guides == null ? Collections.emptyList() : raw.guides;
			this.note = raw.note;
			this.lowConfidence = "low".equals(raw.confidence);
		}

		public double getKillsPerHour()
		{
			return killsPerHour;
		}

		public double getSecondsPerKill()
		{
			return 3600.0 / killsPerHour;
		}

		/** Titles of the OSRS Wiki pages the figure comes from. */
		public List<String> getGuides()
		{
			return guides;
		}

		/** What the figure assumes, or null. */
		public String getNote()
		{
			return note;
		}

		/** True when only a thin source was found. */
		public boolean isLowConfidence()
		{
			return lowConfidence;
		}
	}

	/** Doom of Mokhaiotl: time per delve level, banking per run and the depth assumed with no history. */
	public static final class Doom
	{
		private double[] secondsPerLevel;
		private double bankingSecondsPerRun;
		private int assumedDepthWithoutHistory = 8;

		/** Seconds for one delve of this level (1 to 8, 9 for the deep delves). */
		public double secondsFor(int level)
		{
			if (secondsPerLevel == null || secondsPerLevel.length == 0)
			{
				return Double.NaN;
			}
			return secondsPerLevel[Math.max(0, Math.min(secondsPerLevel.length, level) - 1)];
		}

		/** Banking and walking back after each run (claiming), which the fight times don't include. */
		public double getBankingSecondsPerRun()
		{
			return bankingSecondsPerRun;
		}

		public int getAssumedDepthWithoutHistory()
		{
			return assumedDepthWithoutHistory;
		}

		public boolean isAvailable()
		{
			return secondsPerLevel != null && secondsPerLevel.length > 0;
		}
	}

	private static final class Raw
	{
		Double kph;
		List<String> guides;
		String note;
		String confidence;
		Map<String, Raw> parts;
	}

	private static final class FileFormat
	{
		String guideUrl;
		Map<String, Raw> bosses;
		Doom doom;
	}

	private KillTimes(FileFormat file)
	{
		this.bosses = file.bosses == null ? Collections.emptyMap() : file.bosses;
		this.doom = file.doom == null ? new Doom() : file.doom;
		this.guideBase = file.guideUrl;
	}

	public static KillTimes load(Gson gson, Reader reader)
	{
		FileFormat file = gson.fromJson(reader, FileFormat.class);
		return new KillTimes(file == null ? new FileFormat() : file);
	}

	/**
	 * The file that ships with the plugin, loaded with the given Gson the first time and cached; empty (no estimates)
	 * if it can't be read.
	 */
	public static synchronized KillTimes bundled(Gson gson)
	{
		if (bundled == null)
		{
			try (Reader reader = new InputStreamReader(KillTimes.class.getResourceAsStream("/com/onrate/killtimes.json"),
				StandardCharsets.UTF_8))
			{
				bundled = load(gson, reader);
			}
			catch (Exception e)
			{
				bundled = new KillTimes(new FileFormat());
			}
		}
		return bundled;
	}

	/** The file loaded by {@link #bundled(Gson)}; empty (no estimates) until the plugin has loaded it. */
	public static synchronized KillTimes bundled()
	{
		return bundled == null ? new KillTimes(new FileFormat()) : bundled;
	}

	/**
	 * The figure for a boss, or for one part of a combined page (a raid mode, one boss of a pair);
	 * null when there is none. {@code part} is ignored for a boss without parts.
	 */
	public Estimate forBoss(String boss, String part)
	{
		Raw raw = bosses.get(boss);
		if (raw == null)
		{
			return null;
		}
		if (raw.parts != null)
		{
			Raw p = part == null ? null : raw.parts.get(part);
			return p == null || p.kph == null || p.kph <= 0 ? null : new Estimate(p);
		}
		return raw.kph == null || raw.kph <= 0 ? null : new Estimate(raw);
	}

	/** The parts of a combined page that have a figure, in file order. */
	public List<String> partsWithEstimate(String boss)
	{
		Raw raw = bosses.get(boss);
		List<String> names = new ArrayList<>();
		if (raw != null && raw.parts != null)
		{
			for (Map.Entry<String, Raw> e : raw.parts.entrySet())
			{
				if (e.getValue().kph != null && e.getValue().kph > 0)
				{
					names.add(e.getKey());
				}
			}
		}
		return names;
	}

	public Doom doom()
	{
		return doom;
	}

	/**
	 * The address of the OSRS Wiki page with this title (a guide from {@link Estimate#getGuides()}): spaces become
	 * underscores and anything else a link can't hold is percent-encoded, while {@code ' ( ) /} and letters and digits
	 * stay as they are. Null for an empty title, or when the file's base address is not the OSRS Wiki's.
	 */
	public String guideUrl(String title)
	{
		if (title == null || title.trim().isEmpty() || !WIKI_BASE.equals(guideBase))
		{
			return null;
		}
		StringBuilder sb = new StringBuilder(WIKI_BASE);
		for (byte b : title.trim().replace(' ', '_').getBytes(StandardCharsets.UTF_8))
		{
			char c = (char) (b & 0xff);
			if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || "_-.~'()/,:!".indexOf(c) >= 0)
			{
				sb.append(c);
			}
			else
			{
				sb.append('%').append(Character.toUpperCase(Character.forDigit((b >> 4) & 0xf, 16)))
					.append(Character.toUpperCase(Character.forDigit(b & 0xf, 16)));
			}
		}
		return sb.toString();
	}
}
