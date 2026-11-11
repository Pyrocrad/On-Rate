package com.expecteddrops.ui;

import com.onrate.engine.math.Luck;
import com.onrate.engine.math.Verdict;

import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.stats.RowStats;
import java.util.ArrayList;
import java.util.List;

/**
 * The panel's words and numbers: small functions that turn stats into short text. Kept apart
 * from the Swing code so they are easy to read and test.
 */
final class Texts
{
	private Texts()
	{
	}

	/** Boss page: a log page of this boss was opened but could not be read. */
	static final String LOG_PAGE_UNREADABLE = "Log page opened but not readable. The game may have changed; please report it.";

	static final String LOG_PAGE_UNREADABLE_TIP = "On Rate could not read this page, so your numbers are unchanged. "
		+ "A missing read does not mean zero drops. To clear this boss on purpose, use \"Reset this boss\u2026\". "
		+ "You can report it on On Rate's GitHub page.";

	static final String SETUP_UNREADABLE_TIP = "On Rate could not read it, so your numbers are unchanged. "
		+ "A missing read does not mean zero drops. You can report it on On Rate's GitHub page.";

	/**
	 * Boss page hover of a raid: how many raids of a mode used their own points instead of the setting.
	 * For Tombs of Amascut the game sends the exact points; only the raids where they didn't come
	 * ({@code estimated} of the {@code measured}) used On Rate's estimate.
	 */
	static String raidPointsLine(String family, String part, int measured, int estimated)
	{
		String raids = String.format(java.util.Locale.ROOT, "%,d %s raid%s", measured, part, measured == 1 ? "" : "s");
		if ("fractured".equals(family))
		{
			return raids + " used your share of the damage (On Rate's estimate) instead of the setting.";
		}
		if (!"toa".equals(family) || estimated <= 0)
		{
			return raids + " used your points instead of the setting.";
		}
		if (estimated >= measured)
		{
			return raids + " used your estimated points instead of the setting: the game's exact points didn't come, "
				+ "so On Rate worked them out from your hits.";
		}
		return raids + String.format(java.util.Locale.ROOT, " used your points instead of the setting: the exact points the game "
			+ "sent, or On Rate's estimate for the %,d where they didn't come.", estimated);
	}

	/**
	 * Boss page note of a raid: after a mode's chance for raids without points, where it came from when
	 * it is your own average instead of the setting. Older raids may have gone differently, so it says so.
	 */
	static String learnedNote(String raid, int raids)
	{
		if (raids <= 0)
		{
			return "";
		}
		boolean team = raid != null && (raid.startsWith("tob") || "fractured".equals(raid));
		return String.format(java.util.Locale.ROOT, " (%s over your last %d raids; you may have raided differently before)",
			team ? "your usual team" : "your average", raids);
	}

	/**
	 * A raid item hover row for one of your setups: "1 in 1,450 raids · 1 per ~790 h", without the hour
	 * part when {@code perHour} is NaN (not enough timed raids).
	 */
	static String itemSetupText(double perRaid, double perHour)
	{
		String raid = "1 in " + String.format(java.util.Locale.ROOT, "%,d", Math.round(1 / perRaid)) + " raids";
		if (Double.isNaN(perHour) || perHour <= 0)
		{
			return raid;
		}
		double hours = 1 / perHour;
		String h = hours < 10 ? String.format(java.util.Locale.ROOT, "%.1f", hours)
			: String.format(java.util.Locale.ROOT, "%,d", Math.round(hours));
		return raid + " · 1 per ~" + h + " h";
	}

	/** A forecast table cell: raids ("1,236"), or a dash when the items can't all drop at these chances. */
	static String raidsText(long raids)
	{
		return raids < 0 ? "—" : String.format(java.util.Locale.ROOT, "%,d", raids);
	}

	/** The same in hours at your average raid time ("~741 h"); a dash when unknown. */
	static String hoursText(long raids, long avgMillis)
	{
		if (raids < 0 || avgMillis <= 0)
		{
			return "—";
		}
		return String.format(java.util.Locale.ROOT, "~%,d h", Math.round(raids * avgMillis / 3_600_000.0));
	}

	/** A chance as "1 in 805"; a dash when it can't happen. */
	static String oneIn(double chance)
	{
		return !(chance > 0) ? "—" : String.format(java.util.Locale.ROOT, "1 in %,d", Math.round(1 / chance));
	}

	/** What's missing from a raid's log page: "4 uniques + pet", "1 unique", "Pet only". */
	static String missingText(int uniques, boolean pet)
	{
		if (uniques == 0)
		{
			return pet ? "Pet only" : "Nothing";
		}
		return uniques + (uniques == 1 ? " unique" : " uniques") + (pet ? " + pet" : "");
	}

	/**
	 * A Time page row's "where the time comes from" line, as the engine words it, with "wiki rate" said
	 * as the wiki's kill speed: next to drop chances, "rate" reads as the drop rate. The engine's own
	 * text stays as it is (the parity tests compare it with the original).
	 */
	static String timeBasis(String basis)
	{
		if (basis == null)
		{
			return null;
		}
		return basis.replace("Wiki rate:", "Wiki kill speed:").replace("else the wiki rate)", "else the wiki's kill speed)");
	}

	/** The welcome guide's "Hide guide" button: how to get the guide back. */
	static final String HIDE_GUIDE_TIP = "Hides this guide. To show it again, turn on 'Show welcome guide' in On Rate's "
		+ "settings (the cog next to On Rate in the Configuration panel).";

	/** Account page before any RuneLite profile is loaded. */
	static final String LOG_IN_FIRST = "Log in to see your account.";

	static final String LOADING_ACCOUNT = "Loading your account…";

	/**
	 * ProfileStore#load failed: nothing is saved until a load works, so the saved data stays as it was.
	 * The next login (or profile change) tries again.
	 */
	static final String LOAD_FAILED = "On Rate couldn't load this account's saved data. It saves nothing until it loads, "
		+ "so your saved data stays as it was. Log out and in again to try again. If it keeps happening, please tell us on Discord.";

	/** The account page's note while no account is loaded. */
	static String profileNote(ExpectedDropsPanel.ProfileState state)
	{
		switch (state)
		{
			case LOADING:
				return LOADING_ACCOUNT;
			case FAILED:
				return LOAD_FAILED;
			default:
				return LOG_IN_FIRST;
		}
	}

	/** The big word on the account page while no account is loaded. */
	static String profileVerdict(ExpectedDropsPanel.ProfileState state)
	{
		switch (state)
		{
			case LOADING:
				return "Loading";
			case FAILED:
				return "Couldn't load";
			default:
				return "Not logged in";
		}
	}

	/**
	 * Setup area: RuneLite's Loot Tracker plugin is off. At every boss with Loot Tracker names (Tracker#onLoot)
	 * all new uniques come only from it, first copies included; only the bosses without (The Gauntlet, Obor,
	 * Bryophyta, Jad, Zuk) get first copies from the collection log message. Kill counts and pets come from chat.
	 */
	static final String LOOT_TRACKER_OFF = "Loot Tracker is off, so new drops at most bosses won't be recorded.";

	static final String LOOT_TRACKER_OFF_TIP = "On Rate reads drops at most bosses from RuneLite's Loot Tracker plugin, "
		+ "first copies included. Kill counts and pets still count from chat. The Gauntlet, Obor, Bryophyta, Jad and Zuk "
		+ "aren't affected: their new items come from the collection log message. Turn the Loot Tracker on in the "
		+ "Configuration panel and this note goes away.";

	/**
	 * The "Reset this boss" question: what is gone for good, and what opening the collection log page
	 * brings back. HTML, for a dialog.
	 */
	static String resetConfirm(String bossName)
	{
		return "<html><body style='width: 260px'>Forget everything On Rate tracked for " + bossName + "?"
			+ "<br><br><b>Gone for good.</b> Syncing the collection log can't bring these back: History, "
			+ "raid points, counted team sizes, dry streaks, fastest and longest gaps, drops you added by hand, "
			+ "Doom levels, Colosseum claims, items you left out and which milestone messages were sent."
			+ "<br><br><b>Can be synced again.</b> The kill count and item counts come back when you open "
			+ "its collection log page.</body></html>";
	}

	/** The buttons of the reset question. Cancel, the last one, is the default. */
	static final String[] RESET_OPTIONS = {"Reset", "Cancel"};

	/**
	 * The one line in the setup area when log pages were opened but could not be read, or null when
	 * there is nothing to say (the normal case).
	 */
	static String setupProblem(List<String> pages, boolean headerUnreadable)
	{
		if (!headerUnreadable && pages.isEmpty())
		{
			return null;
		}
		String which = !headerUnreadable && pages.size() == 1 ? "The \"" + pages.get(0) + "\" log page"
			: "A collection log page";
		return which + " was opened but could not be read. The game may have changed; please report it.";
	}

	/** "today", "yesterday", "3 days ago" for a time in milliseconds. */
	static String daysAgo(long time, long now)
	{
		if (time <= 0)
		{
			// added by hand from memory: the kill count is known, the date isn't
			return "date unknown";
		}
		long days = java.time.temporal.ChronoUnit.DAYS.between(
			java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
			java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate());
		return days <= 0 ? "today" : days == 1 ? "yesterday" : String.format(java.util.Locale.ROOT, "%,d days ago", days);
	}

	/**
	 * How lucky one drop was: "quicker than 99.9% of players (1 in 1,000)" when it came before the
	 * drop rate says, "slower than 94% of players" when after, "" when the wait isn't known exactly.
	 * The "1 in N" is how many players are as quick (or as slow) as you; it is left out when it is
	 * under 1 in 10.
	 */
	static String dropLuck(com.onrate.engine.tracking.KillEntry.DropEntry d)
	{
		if (d.getMultiple() == null || d.isAtLeast())
		{
			return "";
		}
		// older versions saved a multiple rounded to two decimals: 0.00 is only known to be under 0.005
		return waitLuck(d.getMultiple());
	}

	/** {@link #dropLuck} for a wait of {@code multiple} times the drop rate (also a wait still going on). */
	static String waitLuck(double multiple)
	{
		boolean floor = multiple == 0;
		double m = floor ? 0.005 : multiple;
		if (m == 1)
		{
			return "right on the drop rate";
		}
		boolean quick = m < 1;
		// players slower than you (quick) or as slow as you (slow), and as quick as you or quicker
		double behind = Math.exp(-m);
		double oneIn = quick ? 1 / (1 - behind) : 1 / behind;
		String text = (quick ? "quicker than " : "slower than ") + shareText(quick ? behind : 1 - behind) + " of players";
		if (oneIn >= 10)
		{
			text += " (1 in " + (floor ? "200+" : oneInText(oneIn)) + ")";
		}
		return text;
	}

	/** {@link #dropLuck} for a list row: "1 in 34 players this quick", "1 in 30 players wait this long". */
	static String dropLuckShort(com.onrate.engine.tracking.KillEntry.DropEntry d)
	{
		return d.getMultiple() == null || d.isAtLeast() ? "" : waitLuckShort(d.getMultiple(), false);
	}

	/**
	 * Short luck wording for a list row. {@code ongoing}: the wait isn't over yet ("1 in 16 still
	 * waiting"). Under 1 in 10 it falls back to "quicker/slower than 74%".
	 */
	static String waitLuckShort(double multiple, boolean ongoing)
	{
		boolean floor = multiple == 0;
		double m = floor ? 0.005 : multiple;
		if (m == 1)
		{
			return "right on the drop rate";
		}
		boolean quick = m < 1;
		double behind = Math.exp(-m);
		double oneIn = quick ? 1 / (1 - behind) : 1 / behind;
		if (oneIn >= 10)
		{
			String n = floor ? "200+" : oneInText(oneIn);
			return quick ? "1 in " + n + " players this quick"
				: ongoing ? "1 in " + n + " still waiting" : "1 in " + n + " players wait this long";
		}
		return (quick ? "quicker than " : "slower than ") + shareText(quick ? behind : 1 - behind);
	}

	private static String shareText(double share)
	{
		return share >= 0.9995 ? ">99.9%" : share >= 0.995 ? String.format(java.util.Locale.ROOT, "%.1f%%", share * 100)
			: share < 0.005 ? "<1%" : String.format(java.util.Locale.ROOT, "%.0f%%", share * 100);
	}

	/** 3 significant digits: 25, 1,030, 41,200. */
	private static String oneInText(double n)
	{
		double scale = Math.pow(10, Math.max(0, Math.floor(Math.log10(n)) - 2));
		return String.format(java.util.Locale.ROOT, "%,.0f", Math.round(n / scale) * scale);
	}

	/** "after 7 kills (0.1x)", "your first one", or "" when the gap is unknown. */
	static String afterText(com.onrate.engine.tracking.KillEntry.DropEntry d, String unit)
	{
		if (d.isFirst())
		{
			return "your first one";
		}
		if (d.getSince() == null)
		{
			return d.isEntered() ? "added by you" : "";
		}
		if (d.isAtLeast())
		{
			return String.format(java.util.Locale.ROOT, "after %,d+ %ss", d.getSince(), unit);
		}
		return String.format(java.util.Locale.ROOT, "after %,d %s%s", d.getSince(), unit, d.getSince() == 1 ? "" : "s")
			+ (d.getMultiple() == null ? "" : " (" + formatMultiple(d.getMultiple()) + ")");
	}

	/**
	 * "Vorkath 437", or for a page with several bosses the one that dropped it with its own
	 * kill count ("Callisto 96"); raid modes other than Normal get a short tag ("CoX CM 12").
	 */
	static String whereText(BossDefinition boss, String bossName, com.onrate.engine.tracking.KillEntry kill)
	{
		if (boss != null && boss.hasParts() && kill.getPart() != null && kill.getPartKc() != null)
		{
			if (boss.getRaid() == null)
			{
				return String.format(java.util.Locale.ROOT, "%s %,d", kill.getPart(), kill.getPartKc());
			}
			if (!"Normal".equals(kill.getPart()))
			{
				return String.format(java.util.Locale.ROOT, "%s %s %,d", bossName, shortMode(kill.getPart()), kill.getPartKc());
			}
			return String.format(java.util.Locale.ROOT, "%s %,d", bossName, kill.getPartKc());
		}
		return String.format(java.util.Locale.ROOT, "%s %,d", bossName, kill.getKc());
	}

	/**
	 * Where a saved kill happened, for hover rows: the page's boss, or for a page with several
	 * bosses the one it was ("Corrupted Gauntlet", "Artio"), or a raid with its mode ("Tombs of Amascut, Expert Mode").
	 */
	static String whereName(BossDefinition boss, String bossName, com.onrate.engine.tracking.KillEntry kill)
	{
		if (boss != null && boss.hasParts() && kill.getPart() != null && kill.getPartKc() != null)
		{
			if (boss.getRaid() == null)
			{
				return kill.getPart();
			}
			// a raid with one mode needs no "Normal"
			return boss.getParts().size() == 1 ? bossName : bossName + ", " + kill.getPart();
		}
		return bossName;
	}

	/** The kill count of a saved kill as a number: the mode's or boss's own on a page with several, else the page's. */
	static String killCount(BossDefinition boss, com.onrate.engine.tracking.KillEntry kill)
	{
		boolean own = boss != null && boss.hasParts() && kill.getPart() != null && kill.getPartKc() != null;
		return String.format(java.util.Locale.ROOT, "%,d", own ? kill.getPartKc() : kill.getKc());
	}

	/**
	 * The kill count of a saved kill without the boss, for hover rows: "437", or with the mode or
	 * boss of a combined page and its own count ("Challenge Mode 12", "Artio 40").
	 */
	static String killValue(BossDefinition boss, com.onrate.engine.tracking.KillEntry kill)
	{
		if (boss != null && boss.hasParts() && kill.getPart() != null && kill.getPartKc() != null)
		{
			return String.format(java.util.Locale.ROOT, "%s %,d", kill.getPart(), kill.getPartKc());
		}
		return String.format(java.util.Locale.ROOT, "%,d", kill.getKc());
	}

	/** {@link #killValue} with what is counted in front when there is no mode: "Raid 115", "Kill 437". */
	static String killText(BossDefinition boss, com.onrate.engine.tracking.KillEntry kill)
	{
		boolean mode = boss != null && boss.hasParts() && kill.getPart() != null && kill.getPartKc() != null;
		return mode ? killValue(boss, kill)
			: capital(boss == null ? "kill" : com.onrate.engine.tracking.Tracker.unitOne(boss)) + " " + killValue(boss, kill);
	}

	static String shortMode(String part)
	{
		switch (part)
		{
			case "Challenge Mode":
				return "CM";
			case "Hard Mode":
				return "HM";
			case "Entry Mode":
				return "Entry";
			case "Expert Mode":
				return "Expert";
			default:
				return part;
		}
	}

	static String formatPoints(int points)
	{
		return points >= 10_000 ? String.format(java.util.Locale.ROOT, "%.1fk", points / 1000.0) : String.format(java.util.Locale.ROOT, "%,d", points);
	}

	/** "Vorkath", "Vorkath and Zulrah", "Vorkath, Zulrah and Cerberus". */
	public static String joinAnd(List<String> names)
	{
		if (names.size() <= 1)
		{
			return String.join("", names);
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}

	static String lowerFirst(String text)
	{
		return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	/** "2.6×" since the last unique, "1.2×+" when the last one came before On Rate, "-" when unknown. */
	static String streakText(RowStats row)
	{
		double m = row.getStreakMultiple();
		if (Double.isNaN(m) || !row.isStreakExact() && row.getStreak() != null && row.getStreak() == 0)
		{
			// unknown, or the last one came before On Rate and nothing was killed since
			return "-";
		}
		return formatMultiple(m) + (row.isStreakExact() ? "" : "+");
	}

	/**
	 * Luck in plain words: "About 1 in 6 players is this dry (or drier)". Around the middle it
	 * just says you're about average.
	 */
	public static String plainLuck(double luck)
	{
		if (Double.isNaN(luck))
		{
			return null;
		}
		if (luck >= 0.4 && luck <= 0.6)
		{
			return "About average for your kill count";
		}
		if (luck < 0.5)
		{
			long n = Math.max(2, Math.round(1 / Math.max(luck, 0.0001)));
			return String.format(java.util.Locale.ROOT, "About 1 in %,d players is this dry (or drier)", n);
		}
		long n = Math.max(2, Math.round(1 / Math.max(1 - luck, 0.0001)));
		return String.format(java.util.Locale.ROOT, "About 1 in %,d players is this lucky (or luckier)", n);
	}

	/** Chance of at least one more in the next {@code kills}, at {@code rate} per kill. */
	public static double nextChance(double rate, int kills)
	{
		return rate <= 0 ? 0 : -Math.expm1(-rate * kills);
	}

	/**
	 * For something you don't have yet, how far past the drop rate you are, the way players
	 * count it: "2.1\u00d7 the drop rate without one" (1\u00d7 is on rate, 2\u00d7 dry, 3\u00d7 very dry).
	 * Null when you have one, or nothing is expected.
	 */
	static String rateMultipleLine(RowStats row)
	{
		String multiple = rateMultiple(row);
		return multiple == null ? null : multiple + (row.isAny() ? " without a unique" : " without one");
	}

	/** {@link #rateMultipleLine} without its ending, for a hover row: "2.1× the drop rate". */
	static String rateMultiple(RowStats row)
	{
		// the drop rates times the kills: a capped pet still counts in full here
		double multiple = row.getUncappedExpected();
		if (row.getReceived() > 0 || row.isComplete() || !(multiple > 0))
		{
			return null;
		}
		return String.format(java.util.Locale.ROOT, "%s the %s rate", formatMultiple(multiple), row.isAny() ? "unique" : "drop");
	}

	/** The dry streak as a footnote line, or null when unknown. */
	static String streakNote(RowStats row, String unit)
	{
		// with none yet, rateMultipleLine already says how far past the rate you are
		if (row.getStreak() == null || row.getReceived() == 0 && row.isStreakExact())
		{
			return null;
		}
		if (!row.isStreakExact() && row.getStreak() == 0)
		{
			return "Dry streak: your last one came before On Rate, so it counts from your next " + unit + ".";
		}
		String kills = String.format(java.util.Locale.ROOT, "%,d %s%s", row.getStreak(), unit, row.getStreak() == 1 ? "" : "s");
		double m = row.getStreakMultiple();
		boolean multiple = !Double.isNaN(m) && row.getRate() > 0;
		if (!row.isStreakExact())
		{
			// only a minimum is known: the last one came before On Rate
			return "Dry streak: at least " + kills + (multiple ? String.format(java.util.Locale.ROOT, " (%s the drop rate or more)", formatMultiple(m)) : "")
				+ ". Your last one came before On Rate started tracking.";
		}
		return String.format(java.util.Locale.ROOT, "Dry streak: %s %s", kills, row.isAny() ? "since your last unique" : "since your last one")
			+ (multiple ? String.format(java.util.Locale.ROOT, " (%s the drop rate)", formatMultiple(m)) : "") + ".";
	}

	/** Two-column table of completions per delve level, like the Doom plugin's header. */
	static String levelTable(java.util.Map<Integer, Integer> levels)
	{
		StringBuilder sb = new StringBuilder("<html><table cellpadding=0 cellspacing=0>");
		int half = (BossDefinition.DELVE_LEVELS + 1) / 2;
		for (int i = 1; i <= half; i++)
		{
			sb.append("<tr>").append(levelCell(i, levels.get(i)));
			int j = i + half;
			if (j <= BossDefinition.DELVE_LEVELS)
			{
				sb.append("<td width=12></td>").append(levelCell(j, levels.get(j)));
			}
			sb.append("</tr>");
		}
		return sb.append("</table></html>").toString();
	}

	/** "184 raids · 182 Normal · 2 CM" or "Callisto 96 · Artio 624", short enough for the header. */
	static String partLine(java.util.Map<String, Integer> parts, boolean raid, int total)
	{
		List<String> shown = new ArrayList<>();
		for (java.util.Map.Entry<String, Integer> e : parts.entrySet())
		{
			if (e.getValue() > 0)
			{
				String name = raid ? shortMode(e.getKey()) : e.getKey().replace("Dagannoth ", "");
				shown.add(raid ? String.format(java.util.Locale.ROOT, "%,d %s", e.getValue(), name) : String.format(java.util.Locale.ROOT, "%s %,d", name, e.getValue()));
			}
		}
		if (raid)
		{
			shown.add(0, String.format(java.util.Locale.ROOT, "%,d raids", total));
		}
		return String.join(" \u00b7 ", shown);
	}

	/** "Callisto 96 · Artio 624" or "182 Normal · 2 Challenge Mode"; parts with 0 are left out. */
	static String partTable(java.util.Map<String, Integer> parts, boolean raid)
	{
		List<String> shown = new ArrayList<>();
		for (java.util.Map.Entry<String, Integer> e : parts.entrySet())
		{
			if (e.getValue() > 0)
			{
				shown.add(raid ? String.format(java.util.Locale.ROOT, "%,d %s", e.getValue(), e.getKey()) : String.format(java.util.Locale.ROOT, "%s %,d", e.getKey(), e.getValue()));
			}
		}
		return "<html><body style='width: 165px'>" + String.join(" \u00b7 ", shown) + "</body></html>";
	}

	/** Kill count per boss on a combined page, e.g. "Dagannoth Rex: 436". */
	static String levelCell(int level, Integer count)
	{
		return String.format(java.util.Locale.ROOT, "<td><font color='#ffffff'>Level %s:</font></td><td align=right>&nbsp;%,d</td>",
			BossDefinition.delveLevelLabel(level), count == null ? 0 : count);
	}

	static String formatExpected(double expected)
	{
		if (expected >= 100)
		{
			return String.format(java.util.Locale.ROOT, "%.0f", expected);
		}
		if (expected >= 10)
		{
			return String.format(java.util.Locale.ROOT, "%.1f", expected);
		}
		return String.format(java.util.Locale.ROOT, "%.2f", expected);
	}

	static String formatMultiple(double m)
	{
		if (m < 0.1)
		{
			return String.format(java.util.Locale.ROOT, "%.2f\u00d7", m);
		}
		return m < 10 ? String.format(java.util.Locale.ROOT, "%.1f\u00d7", m) : String.format(java.util.Locale.ROOT, "%.0f\u00d7", m);
	}

	static String formatPercent(double p)
	{
		if (p > 0.999)
		{
			return ">99.9%";
		}
		return String.format(java.util.Locale.ROOT, "%.1f%%", p * 100);
	}

	static String initials(String name)
	{
		StringBuilder sb = new StringBuilder();
		for (String part : name.split("[\\s(]+"))
		{
			if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0)) && sb.length() < 3)
			{
				sb.append(Character.toUpperCase(part.charAt(0)));
			}
		}
		return sb.toString();
	}

	/**
	 * A luck percentile as a whole percentage for "luckier than ...": "<1%" and ">99%" at the
	 * ends, so a very dry 0.3% doesn't read as "luckier than 0%" (nobody is drier).
	 */
	static String pct(double luck)
	{
		double p = luck * 100;
		if (p < 0.5)
		{
			return "<1%";
		}
		if (p >= 99.5)
		{
			return ">99%";
		}
		return String.format(java.util.Locale.ROOT, "%.0f%%", p);
	}

	/**
	 * Short line under a verdict: how common your result is among players at your kill count.
	 * "74% have none" when you have none, else "1 in 3 as dry" or "1 in 6 as lucky" (also for a
	 * bit dry and a bit spooned), or "about average" when the verdict is "On rate". Matches the tie-aware verdict (see Luck#tieAware).
	 */
	static String luckShort(double[] shares, int received)
	{
		if (shares == null)
		{
			return "";
		}
		double fewer = shares[0];
		double same = shares[1];
		if (received <= 0 || fewer <= 1e-12)
		{
			if (same >= 0.995)
			{
				return ">99% have none";
			}
			if (same >= 0.5)
			{
				return String.format(java.util.Locale.ROOT, "%.0f%% have none", same * 100);
			}
			return String.format(java.util.Locale.ROOT, "only 1 in %,d has none", Math.max(2, Math.round(1 / Math.max(same, 0.0001))));
		}
		return luckShort(Luck.tieAware(fewer, same));
	}

	/** The same from the luck value alone (GP value, the luck graph). */
	static String luckShort(double luck)
	{
		if (Double.isNaN(luck))
		{
			return "";
		}
		if (Verdict.isOnRate(luck))
		{
			// "On rate": the same band as the verdict (a bit dry and a bit spooned get their 1 in N)
			return "about average";
		}
		if (luck < 0.5)
		{
			return luck < 0.0001 ? "<1 in 10,000 as dry"
				: String.format(java.util.Locale.ROOT, "1 in %,d as dry", Math.max(2, Math.round(1 / luck)));
		}
		return 1 - luck < 0.0001 ? "<1 in 10,000 as lucky"
			: String.format(java.util.Locale.ROOT, "1 in %,d as lucky", Math.max(2, Math.round(1 / (1 - luck))));
	}

	/**
	 * Below this many expected uniques a boss page says "Too early" instead of a verdict: a
	 * handful of kills can't tell dry from lucky.
	 */
	static final double TOO_EARLY_BELOW = Verdict.TOO_EARLY_BELOW;

	/**
	 * A row with nothing to read yet: none got and under {@link #TOO_EARLY_BELOW} expected. Its verdict is
	 * always "On rate" (see {@code Luck.of}), so lists draw it quietly without hiding anything
	 * dry. Never "Any unique".
	 */
	static boolean isQuiet(com.onrate.engine.stats.RowStats r)
	{
		return r != null && !r.isAny() && r.getReceived() == 0 && r.getExpected() < TOO_EARLY_BELOW;
	}

	/**
	 * "got / expected" for a row, as boss pages show it: "0 / 2.60", "3 / 1.20". Expected is the average a
	 * player would have by now at the drop rates, not a number owed. A cap (a pet is 1, Warm gloves 3) only
	 * limits what counts as got; the expected number is the row's own, as in every other view.
	 */
	static String gotOfExpected(com.onrate.engine.stats.RowStats r)
	{
		return String.format(java.util.Locale.ROOT, "%,d / %s", r.getReceived(), formatExpected(r.getExpected()));
	}

	/** What a row says once you have every copy that counts: "have it", "have both", "have all 3". */
	static String haveText(com.onrate.engine.stats.RowStats r)
	{
		switch (r.getCap())
		{
			case 0:
			case 1:
				return "have it";
			case 2:
				return "have both";
			default:
				return "have all " + r.getCap();
		}
	}

	/** How far out a dry or lucky item must be to be worth a sentence: 8 in 10 players. */
	static final double NOTABLE_SHARE = 0.80;
	/** An item you don't have yet is worth a sentence once this many players would have it. */
	static final double NOTABLE_CHANCE = 0.60;

	/**
	 * One true sentence for under an "On rate" verdict: the item furthest from average, or null
	 * when nothing stands out. The driest item ("drier than 88% of players"), an item most
	 * players would have by now while you don't ("6 in 10 players would have a Tumeken's shadow
	 * by now"), or the luckiest item ("luckier than 97% of players"), whichever is furthest out.
	 */
	static String notableLine(java.util.List<com.onrate.engine.stats.RowStats> items)
	{
		com.onrate.engine.stats.RowStats best = null;
		double bestShare = 0;
		boolean bestLucky = false;
		for (com.onrate.engine.stats.RowStats r : items)
		{
			double[] shares = r == null || r.isAny() ? null : r.getShares();
			if (shares == null)
			{
				continue;
			}
			double fewer = r.getReceived() <= 0 ? 0 : shares[0];
			double more = Math.max(0, 1 - fewer - shares[1]);
			// none yet: a sentence once most players would have one; else 8 in 10 out
			double dryNeeded = r.getReceived() <= 0 ? NOTABLE_CHANCE : NOTABLE_SHARE;
			if (more >= dryNeeded && more > bestShare)
			{
				best = r;
				bestShare = more;
				bestLucky = false;
			}
			if (fewer >= NOTABLE_SHARE && fewer > bestShare)
			{
				best = r;
				bestShare = fewer;
				bestLucky = true;
			}
		}
		if (best == null)
		{
			return null;
		}
		String name = best.getLabel();
		if (bestLucky)
		{
			return String.format(java.util.Locale.ROOT, "Luckiest: %s, luckier than %s of players.", name, sharePct(bestShare));
		}
		if (best.getReceived() <= 0)
		{
			// with none, the players who have more are the ones who'd have one by now
			String players = bestShare < 0.95
				? String.format(java.util.Locale.ROOT, "%d in 10 players", (int) Math.floor(bestShare * 10))
				: sharePct(bestShare) + " of players";
			return String.format(java.util.Locale.ROOT, "%s would have %s by now.", players, withArticle(name));
		}
		return String.format(java.util.Locale.ROOT, "Driest: %s, drier than %s of players.", name, sharePct(bestShare));
	}

	/** An item this close to its expected count (a quarter either way) is "about on rate" for its own line. */
	static final double CLOSE_TO_EXPECTED = 0.25;

	/**
	 * The true sentences the line under the verdict takes turns with, in a fixed order: the
	 * {@link #notableLine} first (it may be about a dry item; nothing after it is), then neutral facts. Every
	 * fact needs enough data behind it (half a unique expected, as for "Too early"), and none says a drop is
	 * due or coming.
	 *
	 * @param items  the items of the page (a boss's drops, or the account's highlighted items)
	 * @param bosses the account's boss rows on the account overview, or null on a boss page
	 * @param countLuck the luck of the uniques verdict, NaN when unknown
	 * @param valueLuck the luck of the GP value verdict, NaN when it isn't shown
	 * @param what "uniques", or "purples" at a raid
	 */
	static List<String> notableLines(List<RowStats> items, List<RowStats> bosses, double countLuck, double valueLuck,
		String what)
	{
		return notableLines(items, bosses, countLuck, valueLuck, what, java.util.Collections.emptyList());
	}

	/** The same, with the page's own facts (the log forecast, the average pace) after the first ones. */
	static List<String> notableLines(List<RowStats> items, List<RowStats> bosses, double countLuck, double valueLuck,
		String what, List<String> pageFacts)
	{
		List<String> facts = new ArrayList<>();
		String first = notableLine(items);
		if (first != null)
		{
			facts.add(first);
		}
		facts.addAll(pageFacts);
		// the two verdicts on different sides: what that means, in words
		Verdict count = Verdict.of(countLuck);
		Verdict value = Verdict.of(valueLuck);
		if (count != null && value != null && count.isDry() && value.isSpooned())
		{
			facts.add("Fewer " + what + " than expected, but more valuable ones.");
		}
		else if (count != null && value != null && count.isSpooned() && value.isDry())
		{
			facts.add("More " + what + " than expected, but less valuable ones.");
		}
		if (bosses == null)
		{
			// the item closest to its expected count, when one is close
			RowStats close = null;
			double closest = CLOSE_TO_EXPECTED;
			int have = 0;
			int total = 0;
			for (RowStats r : items)
			{
				if (r == null || r.isAny())
				{
					continue;
				}
				total++;
				if (r.getReceived() > 0 || r.isComplete())
				{
					have++;
				}
				if (r.getReceived() >= 1 && r.getExpected() >= TOO_EARLY_BELOW)
				{
					double off = Math.abs(r.getReceived() - r.getExpected()) / r.getExpected();
					if (off <= closest)
					{
						closest = off;
						close = r;
					}
				}
			}
			if (close != null)
			{
				facts.add(String.format(java.util.Locale.ROOT, "%s: %,d got, %s expected.", close.getLabel(),
					close.getReceived(), formatExpected(close.getExpected())));
			}
			if (total >= 2 && have > 0)
			{
				facts.add(have == total && total == 2 ? "You have both " + what + " here."
					: have == total ? String.format(java.util.Locale.ROOT, "You have all %d %s here.", total, what)
					: String.format(java.util.Locale.ROOT, "You have %d of the %d %s here.", have, total, what));
			}
		}
		else
		{
			// how the bosses split, and which one gave the most
			int dry = 0;
			int on = 0;
			int lucky = 0;
			RowStats most = null;
			for (RowStats b : bosses)
			{
				Verdict v = b == null || b.getExpected() < TOO_EARLY_BELOW ? null : Verdict.of(b.getLuck());
				if (v == null)
				{
					continue;
				}
				if (v.isDry())
				{
					dry++;
				}
				else if (v.isSpooned())
				{
					lucky++;
				}
				else
				{
					on++;
				}
				if (b.getReceived() > 0 && (most == null || b.getReceived() > most.getReceived()))
				{
					most = b;
				}
			}
			if (dry + on + lucky >= 2)
			{
				facts.add(String.format(java.util.Locale.ROOT, "Of %d bosses: %d drier than average, %d on rate, %d luckier.",
					dry + on + lucky, dry, on, lucky));
			}
			if (most != null)
			{
				facts.add(String.format(java.util.Locale.ROOT, "Most %s: %s, %,d.", what, most.getLabel(), most.getReceived()));
			}
		}
		return facts;
	}

	/**
	 * The log forecast as a sentence. With the pet still missing it gives both: "Rest of the log, 50% chance:
	 * 1,520 more kills (~790 h) with the pet, 1,010 (~520 h) without." Else one figure: "Rest of the log: 50%
	 * chance within 1,010 more kills (~520 h)." A chance, not a promise, and the same whatever came before. Null
	 * when nothing is missing or it can't be done.
	 *
	 * @param withPet    kills (or raids) after which half of players with these chances would have everything,
	 *                   the pet included
	 * @param withoutPet the same without the pet (equal to {@code withPet} when the pet isn't missing)
	 * @param petMissing whether the pet is still missing
	 * @param seconds    seconds per kill, NaN when unknown (then no hours)
	 * @param units      "kills", "raids"
	 * @param onlyItem   the one item still missing (the pet when it's the last), or null when there are more
	 */
	static String logLeftLine(long withPet, long withoutPet, boolean petMissing, double seconds, String units, String onlyItem)
	{
		if (withPet <= 0)
		{
			return null;
		}
		if (petMissing && withoutPet > 0 && withoutPet < withPet)
		{
			return String.format(java.util.Locale.ROOT, "Rest of the log, 50%% chance: %,d more %s%s with the pet, %,d%s without.",
				withPet, units, aboutTime(withPet * seconds), withoutPet, aboutTime(withoutPet * seconds));
		}
		String what = onlyItem == null ? "Rest of the log" : "Last item for the log, " + onlyItem;
		return String.format(java.util.Locale.ROOT, "%s: 50%% chance within %,d more %s%s.", what, withPet, units,
			aboutTime(withPet * seconds));
	}

	/**
	 * The average pace: "On average one unique every 82 kills (~9.5 h)." Each kill is its own roll, so this
	 * is the same after a long wait as after a drop. Null when nothing is expected.
	 *
	 * @param perKill uniques expected per kill
	 * @param seconds seconds per kill, NaN when unknown (then no hours)
	 * @param units   "kills", "raids"
	 * @param one     "unique", "purple"
	 */
	static String averagePaceLine(double perKill, double seconds, String units, String one)
	{
		if (!(perKill > 0))
		{
			return null;
		}
		double every = 1 / perKill;
		String count = every >= 10 ? String.format(java.util.Locale.ROOT, "%,d", Math.round(every))
			: String.format(java.util.Locale.ROOT, "%.1f", every);
		return String.format(java.util.Locale.ROOT, "On average one %s every %s %s%s.", one, count, units,
			aboutTime(every * seconds));
	}

	/** " (~790 h)", " (~9.5 h)", " (~40 min)", or "" when the time isn't known. */
	static String aboutTime(double seconds)
	{
		if (!(seconds > 0) || Double.isInfinite(seconds))
		{
			return "";
		}
		double hours = seconds / 3600;
		if (hours < 1)
		{
			return String.format(java.util.Locale.ROOT, " (~%d min)", Math.max(1, Math.round(hours * 60)));
		}
		if (hours < 10)
		{
			return String.format(java.util.Locale.ROOT, " (~%.1f h)", hours);
		}
		return String.format(java.util.Locale.ROOT, " (~%,d h)", Math.round(hours));
	}

	/**
	 * For the "Unique value" hover: the item most of the value comes from, when one item is at
	 * least half of it ("Most of it is the Crystal tool seed (95%)."), else null.
	 */
	static String mainValueSource(java.util.List<String> names, java.util.List<Double> values)
	{
		double total = 0;
		int best = -1;
		for (int i = 0; i < values.size(); i++)
		{
			double v = Math.max(0, values.get(i));
			total += v;
			if (best < 0 || v > values.get(best))
			{
				best = i;
			}
		}
		if (best < 0 || total <= 0 || values.get(best) / total < 0.5)
		{
			return null;
		}
		return String.format(java.util.Locale.ROOT, "Most of it is the %s (%.0f%%).", names.get(best), Math.floor(values.get(best) / total * 100));
	}

	/** "a Tumeken's shadow", "an Elder maul", "Bandos boots" (a pair or a set takes none). */
	static String withArticle(String name)
	{
		if (name.isEmpty())
		{
			return name;
		}
		if (name.endsWith("s") && !name.endsWith("'s"))
		{
			return name;
		}
		return ("AEIOUaeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}

	/**
	 * For hovers: how your result compares with players at your kill count, as a percentage.
	 * Always told from the side that holds most players, so the number is over 50%: "drier than
	 * 67%" (67% have more), "luckier than 83%" (83% have fewer), and "74% have none" when you
	 * and most players have none. Players with exactly your count are on neither side. "About
	 * average" when neither side is a majority. Past 95% the "1 in N" is added: "drier than
	 * 99.91% (1 in 1,205)".
	 */
	static String luckPercent(double[] shares, int received)
	{
		if (shares == null)
		{
			return "";
		}
		double fewer = received <= 0 ? 0 : shares[0];
		double same = shares[1];
		double more = Math.max(0, 1 - fewer - same);
		if (more > 0.5)
		{
			return side("drier", more);
		}
		if (fewer > 0.5)
		{
			return side("luckier", fewer);
		}
		if (received <= 0)
		{
			return same >= 0.995 ? ">99% have none" : String.format(java.util.Locale.ROOT, "%.0f%% have none", same * 100);
		}
		return "about average";
	}

	/** The same from the luck value alone (GP value, the luck graph). */
	static String luckPercent(double luck)
	{
		if (Double.isNaN(luck))
		{
			return "";
		}
		if (luck < 0.495)
		{
			return side("drier", 1 - luck);
		}
		if (luck > 0.505)
		{
			return side("luckier", luck);
		}
		return "about average";
	}

	/** "drier than 67%", with "(1 in 33)" added past 95%: how rare a result this far out is. */
	private static String side(String word, double share)
	{
		String text = word + " than " + sharePct(share);
		if (share < 0.95)
		{
			return text;
		}
		double rest = 1 - share;
		return text + (rest < 0.0001 ? " (<1 in 10,000 players)"
			: String.format(java.util.Locale.ROOT, " (1 in %,d players)", Math.max(2, Math.round(1 / rest))));
	}

	/**
	 * A majority share as a percentage: whole numbers like the hover's split ("67%", "97%"), then
	 * decimals rounded down so it never reaches 100% ("99.4%", "99.91%").
	 */
	static String sharePct(double share)
	{
		double p = share * 100;
		if (share < 0.99)
		{
			return String.format(java.util.Locale.ROOT, "%d%%", Math.round(p));
		}
		if (share < 0.999)
		{
			return String.format(java.util.Locale.ROOT, "%.1f%%", Math.floor(p * 10) / 10);
		}
		return String.format(java.util.Locale.ROOT, "%.2f%%", Math.min(99.99, Math.floor(p * 100) / 100));
	}

	/**
	 * The full split for a hover: "74% of players have none by now, 26% have one or more", or
	 * "18% of players have fewer, 15% as many, 67% more". Null when nothing is expected.
	 */
	static String sharesLine(double[] shares, int received)
	{
		if (shares == null)
		{
			return null;
		}
		double fewer = shares[0];
		double same = shares[1];
		double more = Math.max(0, 1 - fewer - same);
		if (received <= 0 || fewer <= 1e-12)
		{
			return String.format(java.util.Locale.ROOT, "%s of players have none by now, %s have one or more", pct(same), pct(more));
		}
		return String.format(java.util.Locale.ROOT, "%s of players have fewer, %s as many%s", pct(fewer), pct(same),
			more < 0.0005 ? "" : ", " + pct(more) + " more");
	}

	/** First letter upper case, for a short line used on its own. */
	static String capital(String text)
	{
		return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}

	/**
	 * Makes "<1%" or "<1 in 10,000" safe inside Swing HTML, which drops a "<" before a digit
	 * (it reads it as the start of a tag). Real tags like {@code <br>} are left alone.
	 */
	static String html(String text)
	{
		return text == null ? null : text.replaceAll("<(?=[0-9])", "&lt;");
	}
}
