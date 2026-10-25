package com.expecteddrops.ui;

import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.stats.RowStats;
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

	/** "today", "yesterday", "3 days ago" for a time in milliseconds. */
	static String daysAgo(long time, long now)
	{
		long days = java.time.temporal.ChronoUnit.DAYS.between(
			java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
			java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate());
		return days <= 0 ? "today" : days == 1 ? "yesterday" : String.format("%,d days ago", days);
	}

	/** "after 7 kills (0.1x)", "your first one", or "" when the gap is unknown. */
	static String afterText(com.expecteddrops.tracking.KillEntry.DropEntry d, String unit)
	{
		if (d.isFirst())
		{
			return "your first one";
		}
		if (d.getSince() == null)
		{
			return "";
		}
		if (d.isAtLeast())
		{
			return String.format("after %,d+ %ss", d.getSince(), unit);
		}
		return String.format("after %,d %s%s", d.getSince(), unit, d.getSince() == 1 ? "" : "s")
			+ (d.getMultiple() == null ? "" : " (" + formatMultiple(d.getMultiple()) + ")");
	}

	/**
	 * "Vorkath 437", or for a page with several bosses the one that dropped it with its own
	 * kill count ("Callisto 96"); raid modes other than Normal get a short tag ("CoX CM 12").
	 */
	static String whereText(BossDefinition boss, String bossName, com.expecteddrops.tracking.KillEntry kill)
	{
		if (boss != null && boss.hasParts() && kill.getPart() != null && kill.getPartKc() != null)
		{
			if (boss.getRaid() == null)
			{
				return String.format("%s %,d", kill.getPart(), kill.getPartKc());
			}
			if (!"Normal".equals(kill.getPart()))
			{
				return String.format("%s %s %,d", bossName, shortMode(kill.getPart()), kill.getPartKc());
			}
			return String.format("%s %,d", bossName, kill.getPartKc());
		}
		return String.format("%s %,d", bossName, kill.getKc());
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
		return points >= 10_000 ? String.format("%.1fk", points / 1000.0) : String.format("%,d", points);
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
			return String.format("About 1 in %,d players is this dry (or drier)", n);
		}
		long n = Math.max(2, Math.round(1 / Math.max(1 - luck, 0.0001)));
		return String.format("About 1 in %,d players is this lucky (or luckier)", n);
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
		return String.format("%s the %s rate", formatMultiple(multiple), row.isAny() ? "unique" : "drop");
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
		String kills = String.format("%,d %s%s", row.getStreak(), unit, row.getStreak() == 1 ? "" : "s");
		double m = row.getStreakMultiple();
		boolean multiple = !Double.isNaN(m) && row.getRate() > 0;
		if (!row.isStreakExact())
		{
			// only a minimum is known: the last one came before On Rate
			return "Dry streak: at least " + kills + (multiple ? String.format(" (%s the rate or more)", formatMultiple(m)) : "")
				+ ". Your last one came before On Rate started tracking.";
		}
		return String.format("Dry streak: %s %s", kills, row.isAny() ? "since your last unique" : "since your last one")
			+ (multiple ? String.format(" (%s the rate)", formatMultiple(m)) : "") + ".";
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
				shown.add(raid ? String.format("%,d %s", e.getValue(), name) : String.format("%s %,d", name, e.getValue()));
			}
		}
		if (raid)
		{
			shown.add(0, String.format("%,d raids", total));
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
				shown.add(raid ? String.format("%,d %s", e.getValue(), e.getKey()) : String.format("%s %,d", e.getKey(), e.getValue()));
			}
		}
		return "<html><body style='width: 165px'>" + String.join(" \u00b7 ", shown) + "</body></html>";
	}

	/** Kill count per boss on a combined page, e.g. "Dagannoth Rex: 436". */
	static String levelCell(int level, Integer count)
	{
		return String.format("<td><font color='#ffffff'>Level %s:</font></td><td align=right>&nbsp;%,d</td>",
			BossDefinition.delveLevelLabel(level), count == null ? 0 : count);
	}

	static String formatExpected(double expected)
	{
		if (expected >= 100)
		{
			return String.format("%.0f", expected);
		}
		if (expected >= 10)
		{
			return String.format("%.1f", expected);
		}
		return String.format("%.2f", expected);
	}

	static String formatMultiple(double m)
	{
		if (m < 0.1)
		{
			return String.format("%.2f\u00d7", m);
		}
		return m < 10 ? String.format("%.1f\u00d7", m) : String.format("%.0f\u00d7", m);
	}

	static String formatPercent(double p)
	{
		if (p > 0.999)
		{
			return ">99.9%";
		}
		return String.format("%.1f%%", p * 100);
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
		return String.format("%.0f%%", p);
	}

	/**
	 * Short line under a verdict: how common your result is among players at your kill count.
	 * "74% have none" when you have none, else "1 in 3 as dry" or "1 in 6 as lucky" (also for a
	 * bit dry and a bit spooned), or "about average" when the verdict is "On rate". Matches the tie-aware verdict (see DropMath#tieAware).
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
				return String.format("%.0f%% have none", same * 100);
			}
			return String.format("only 1 in %,d has none", Math.max(2, Math.round(1 / Math.max(same, 0.0001))));
		}
		return luckShort(com.expecteddrops.stats.DropMath.tieAware(fewer, same));
	}

	/** The same from the luck value alone (GP value, the luck graph). */
	static String luckShort(double luck)
	{
		if (Double.isNaN(luck))
		{
			return "";
		}
		if (com.expecteddrops.stats.DropMath.VERDICTS.get(3).equals(com.expecteddrops.stats.DropMath.verdict(luck)))
		{
			// "On rate": the same band as the verdict (a bit dry and a bit spooned get their 1 in N)
			return "about average";
		}
		if (luck < 0.5)
		{
			return luck < 0.0001 ? "<1 in 10,000 as dry"
				: String.format("1 in %,d as dry", Math.max(2, Math.round(1 / luck)));
		}
		return 1 - luck < 0.0001 ? "<1 in 10,000 as lucky"
			: String.format("1 in %,d as lucky", Math.max(2, Math.round(1 / (1 - luck))));
	}

	/**
	 * Below this many expected uniques a boss page says "Too early" instead of a verdict: a
	 * handful of kills can't tell dry from lucky.
	 */
	static final double TOO_EARLY_BELOW = 0.5;

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
	static String notableLine(java.util.List<com.expecteddrops.stats.RowStats> items)
	{
		com.expecteddrops.stats.RowStats best = null;
		double bestShare = 0;
		boolean bestLucky = false;
		for (com.expecteddrops.stats.RowStats r : items)
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
			return String.format("Luckiest: %s, luckier than %s of players.", name, sharePct(bestShare));
		}
		if (best.getReceived() <= 0)
		{
			// with none, the players who have more are the ones who'd have one by now
			String players = bestShare < 0.95
				? String.format("%d in 10 players", (int) Math.floor(bestShare * 10))
				: sharePct(bestShare) + " of players";
			return String.format("%s would have %s by now.", players, withArticle(name));
		}
		return String.format("Driest: %s, drier than %s of players.", name, sharePct(bestShare));
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
		return String.format("Most of it is the %s (%.0f%%).", names.get(best), Math.floor(values.get(best) / total * 100));
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
			return same >= 0.995 ? ">99% have none" : String.format("%.0f%% have none", same * 100);
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
			: String.format(" (1 in %,d players)", Math.max(2, Math.round(1 / rest))));
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
			return String.format("%d%%", Math.round(p));
		}
		if (share < 0.999)
		{
			return String.format("%.1f%%", Math.floor(p * 10) / 10);
		}
		return String.format("%.2f%%", Math.min(99.99, Math.floor(p * 100) / 100));
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
			return String.format("%s of players have none by now, %s have one or more", pct(same), pct(more));
		}
		return String.format("%s of players have fewer, %s as many%s", pct(fewer), pct(same),
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
