package com.expecteddrops.ui;

import static com.expecteddrops.ui.ExpectedDropsPanel.WELCOME_VERDICT_LUCK;
import static com.expecteddrops.ui.ExpectedDropsPanel.luckColor;
import static com.expecteddrops.ui.ExpectedDropsPanel.summary;
import static com.expecteddrops.ui.Widgets.*;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.AccountStats;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.tracking.Tracker;
import static com.expecteddrops.ui.Texts.*;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Builds the tooltips and help texts of the panel's pages. Split out of {@link ExpectedDropsPanel}. */
final class PanelTexts
{
	private PanelTexts()
	{
	}

	/** A gap in drop rates as a colour: well under the rate is lucky, well over it is dry. */
	static Color multipleColor(com.expecteddrops.tracking.KillEntry.DropEntry d)
	{
		return multipleColor(d, false);
	}

	/** Grey for a first drop (nothing to compare with) unless {@code evenFirst}: the Drops view colours every drop by its luck. */
	static Color multipleColor(com.expecteddrops.tracking.KillEntry.DropEntry d, boolean evenFirst)
	{
		Double m = d.getMultiple();
		return (d.isFirst() && !evenFirst) || m == null ? ColorScheme.LIGHT_GRAY_COLOR : luckColor(Math.exp(-m)).brighter();
	}

	/** The gap before a unique, for a hover row: "7 kills (0.1\u00d7)", "your first one", or "". */
	static String gapText(com.expecteddrops.tracking.KillEntry.DropEntry d, String unit)
	{
		String after = afterText(d, unit);
		return after.startsWith("after ") ? after.substring("after ".length()) : after;
	}

	/**
	 * The hover of a History line for a raid, team boss, delve or Royal Titans kill: what the
	 * line abbreviates, one row each.
	 */
	static String historyKillTip(BossDefinition boss, com.expecteddrops.tracking.HistoryRow h, String unit, long now, String loot)
	{
		com.expecteddrops.tracking.KillEntry k = h.getKill();
		String title;
		if (boss.getWaves() > 0)
		{
			title = "Wave " + (k.getLevel() == null ? "?" : k.getLevel());
		}
		else if (boss.isDelve())
		{
			title = "Delve level " + (k.getLevel() == null ? "?" : BossDefinition.delveLevelLabel(k.getLevel()));
		}
		else
		{
			title = String.format("%s %,d", capital(unit), k.getPartKc() != null && boss.getRaid() != null ? k.getPartKc() : k.getKc());
		}
		Tip tip = new Tip(title);
		if (boss.getRaid() != null)
		{
			com.expecteddrops.tracking.RaidRecord r = h.getRaid();
			if (k.getPart() != null && !"Normal".equals(k.getPart()))
			{
				tip.row("Mode", k.getPart());
			}
			if (r == null)
			{
				tip.row("Points", "not recorded");
			}
			else
			{
				if (r.getPersonalPoints() > 0)
				{
					tip.row("Your points", String.format("%,d", r.getPersonalPoints()));
				}
				if (!Double.isNaN(h.getPurpleChance()))
				{
					tip.row("Purple chance", String.format("%.1f%%", h.getPurpleChance() * 100));
				}
				tip.row("Team", String.valueOf(r.getTeamSize()));
				if (r.getDeaths() > 0)
				{
					tip.row("Deaths", String.valueOf(r.getDeaths()));
				}
			}
		}
		else if (boss.isDetectTeam())
		{
			tip.row("Team", k.getTeam() == null ? "unknown" : k.getTeam() == 1 ? "solo"
				: k.getTeam() + ", your share 1/" + k.getTeam());
		}
		else if ("Royal Titans".equals(boss.getName()))
		{
			tip.row("Titan looted", k.getTitan() == null ? "unknown" : k.getTitan());
		}
		tip.row("When", daysAgo(k.getTime(), now));
		tip.row("Uniques", loot.isEmpty() ? "none" : loot);
		return tip.wide();
	}

	static String poolTooltip(com.expecteddrops.stats.SharedPool pool, RowStats row)
	{
		Tip tip = new Tip(pool.getLabel()).line("Shared by several bosses", Theme.TEXT_MUTED);
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			tip.row("Your result", "<b>" + DropMath.verdict(luck) + "</b>", luckColor(luck).brighter())
				.line(capital(sharesLine(row.getShares(), row.getReceived())), Theme.TEXT_MUTED);
		}
		tip.row("Dropped by", joinAnd(pool.getBosses()))
			.row("Expected from all", formatExpected(pool.getExpected()))
			.row("In your collection log", String.valueOf(pool.getReceived()));
		if (!pool.isComplete())
		{
			tip.note("Kills not counted yet: open the " + joinAnd(pool.getMissingPages()) + " log page"
				+ (pool.getMissingPages().size() == 1 ? "" : "s") + ".");
		}
		if (pool.getAlsoFrom() != null)
		{
			// shared items have no "leave out": the account totals count the log total as it is
			tip.note("Also drops from " + pool.getAlsoFrom() + ", and the collection log counts those too, "
				+ "so this can look luckier than it is.");
		}
		if (pool.isSplittable())
		{
			tip.note(pool.getAlsoFrom() != null
				? "Know where yours came from? Right-click to judge it on each boss separately, with the ones from "
					+ pool.getAlsoFrom() + " left out."
				: "Know which boss yours came from? Right-click to judge it on each boss separately.");
		}
		return tip.wide();
	}

	/** One line for the overview: GE value received / expected and its verdict (details on hover). */
	static String valueText(AccountStats account)
	{
		double luck = account.getValueLuck();
		if (account.getExpectedValue() <= 0 || Double.isNaN(luck))
		{
			return "GE value: prices not loaded yet";
		}
		String color = String.format("#%06x", luckColor(luck).brighter().getRGB() & 0xFFFFFF);
		return String.format("<html>GE value: %s / %s \u00b7 <font color='%s'>%s</font></html>",
			DropMath.formatGp(account.getReceivedValue()), DropMath.formatGp(account.getExpectedValue()),
			color, DropMath.verdict(luck));
	}

	static String valueTooltip(AccountStats account)
	{
		double luck = account.getValueLuck();
		if (account.getExpectedValue() <= 0 || Double.isNaN(luck))
		{
			return null;
		}
		return new Tip("GP value \u00b7 " + DropMath.verdict(luck))
			.row("Got", DropMath.formatGp(account.getReceivedValue()))
			.row("Expected", DropMath.formatGp(account.getExpectedValue()))
			.row("By value", capital(luckPercent(luck)) + " for your kill count")
			.note("RuneLite's own price list. Pets, jars and other untradeables count as 0 GP.")
			.wide();
	}

	/** "?" help for a boss page: how to read the chosen view. */
	static String bossHelp(ItemView view)
	{
		String how;
		switch (view)
		{
			case DELVE_BARS:
				how = "<b>Expected</b>: the number is how many you should have by now; the bar is how close that is to the next whole one."
					+ "<br><b>Received</b>: the number is how many you got; the bar is how lucky that is.";
				break;
			case COMPACT:
				how = "Each item shows how many you got / should have by now, and how lucky that is "
					+ "(a luck %: 50% is average, low is dry, high is spooned; hover for how many players have fewer or more).";
				break;
			case RINGS:
				how = "Each ring fills as the chance of having the item by now grows. Once you have it, the ring shows "
					+ "the chance of having had another since your last.";
				break;
			case GAUGE:
				how = "The needle shows your luck from dry to spooned; each item is a dot on the same scale.";
				break;
			default:
				how = "The fill is how many you got; the white line is how many you should have by now.";
		}
		return "<html><body style='width: 240px'>" + how + "<br><br>Hover an item for its drop rate, your chances "
			+ "and your dry streak. Right-click one to leave it out of the calculations (a drop you got elsewhere, "
			+ "or jars you don't care about). Right-click a shared item to judge it on each boss separately. "
			+ "Click \u21c4 next to ITEMS for another view."
			+ "</body></html>";
	}

	/** Short names of the item views, for the switch next to ITEMS. */
	static String viewName(ItemView view)
	{
		switch (view)
		{
			case DELVE_BARS:
				return "tabs";
			case COMPACT:
				return "table";
			case RINGS:
				return "rings";
			case GAUGE:
				return "gauge";
			default:
				return "bars";
		}
	}

	/** The colour of the welcome key's verdict word {@code i}, as the boss page shows it. */
	static Color welcomeVerdictColor(int i)
	{
		double luck = WELCOME_VERDICT_LUCK[i];
		return DropMath.isOnRate(luck) ? Theme.TEXT : Palette.verdict(luck).brighter();
	}

	/** A wrapped grey line in the welcome guide with a thin line above, starting with an optional white lead. */
	static JLabel welcomeLine(String lead, String text)
	{
		String html = lead == null ? text : "<font color='" + Theme.hex(Theme.TEXT) + "'>" + lead + "</font> " + text;
		JLabel line = smallLabel(Theme.wrap(html, 160), Theme.TEXT_MUTED);
		line.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		line.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.S, 0, Theme.S, 0)));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/** A section title in the welcome guide, with an optional grey line under it. */
	static JPanel welcomeHeading(String title, String sub)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		p.setBorder(new EmptyBorder(Theme.M + Theme.S, 0, Theme.S, 0));
		JLabel t = new JLabel(title);
		t.setFont(FontManager.getRunescapeBoldFont());
		t.setForeground(Theme.TEXT);
		t.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		p.add(t);
		if (sub != null)
		{
			JLabel s = smallLabel(Theme.wrap(sub, 160), Theme.TEXT_MUTED);
			s.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
			p.add(s);
		}
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	static String tooltip(RowStats row, BossStats stats, String unit)
	{
		return tooltip(row, stats, unit, java.util.Collections.emptyList());
	}

	static String tooltip(RowStats row, BossStats stats, String unit, List<String[]> dropLuck)
	{
		Tip tip = new Tip(row.isAny() ? "Any unique" : row.getLabel());
		tip.line(stats.getBoss().getName(), Theme.TEXT_MUTED);
		DropDefinition drop = row.isAny() ? null : findDrop(stats.getDrops(), row.getLabel());
		int kills = stats.getKillCount();
		String units = kills == 1 ? unit : unit + "s";

		// one plain sentence first, then the verdict and the split of players it comes from
		double luck = row.getLuck();
		tip.line(summary(row));
		if (!Double.isNaN(luck))
		{
			tip.row("Your result", "<b>" + DropMath.verdict(luck) + "</b>", luckColor(luck).brighter());
			// the players at your kill count: fewer, as many as you (in your colour), more
			tip.split(row.getShares(), row.getReceived(), luckColor(luck));
			tip.line(capital(sharesLine(row.getShares(), row.getReceived())), Theme.TEXT_MUTED);
		}

		if (!dropLuck.isEmpty())
		{
			// each drop on its own: how long it took against the drop rate, luckiest first
			tip.line("How lucky you were when you got it", Theme.TEXT_MUTED);
			for (String[] d : dropLuck)
			{
				if (d[1].isEmpty())
				{
					tip.line(d[0], Theme.TEXT_MUTED);
				}
				else
				{
					tip.row(d[0], d[1]);
				}
			}
		}
		boolean oneRate = !row.isAny() && (drop == null || (!drop.hasLevelRates() && !drop.hasPartRates()));
		if (oneRate)
		{
			tip.row("Drop rate", DropMath.formatRate(row.getRate()) + " per " + unit);
		}
		tip.row("Chance of one by now", formatPercent(row.getChanceAtLeastOne()));
		String multiple = rateMultiple(row);
		if (multiple != null)
		{
			tip.row(row.isAny() ? "Without a unique" : "Without one", String.format("%,d %s, %s", kills, units, multiple));
		}
		if (oneRate && row.getRate() > 0)
		{
			long half = DropMath.killsForChance(row.getRate(), 0.5);
			long most = DropMath.killsForChance(row.getRate(), 0.9);
			Integer sure = drop == null ? null : drop.getGuaranteedAt();
			if (sure != null)
			{
				// the guaranteed kill makes it certain by then (Vorkath's 50th)
				half = Math.min(half, sure);
				most = Math.min(most, sure);
			}
			tip.row("From none, 50% / 90% takes", String.format("%,d / %,d %ss", half, most, unit));
		}

		// rates that depend on the run: one row each, under a small heading
		if (drop != null && drop.hasLevelRates() && stats.getBoss().getWaves() > 0)
		{
			// a run counts every wave up to the claim: the chance by the wave you claim at
			tip.line("Chance over a run, by the wave you claim at", Theme.TEXT_MUTED);
			for (int wave = 1; wave <= stats.getBoss().getWaves(); wave++)
			{
				double cumulative = com.expecteddrops.tracking.Tracker.waveRate(drop, wave);
				if (cumulative > 0)
				{
					tip.row("Wave " + wave, DropMath.formatRate(cumulative));
				}
			}
		}
		else if (drop != null && drop.hasLevelRates())
		{
			tip.line("Rate per delve level", Theme.TEXT_MUTED);
			for (int level = 1; level <= BossDefinition.DELVE_LEVELS; level++)
			{
				String rate = drop.getLevelRate(level);
				if (rate != null)
				{
					tip.row(BossDefinition.delveLevelLabel(level), DropMath.formatRate(DropMath.parseRate(rate)));
				}
			}
		}
		else if (drop != null && drop.hasPartRates())
		{
			tip.line("Rate per " + unit, Theme.TEXT_MUTED);
			for (java.util.Map.Entry<String, Integer> e : stats.getPartCounts().entrySet())
			{
				String rate = drop.getPartRate(e.getKey());
				if (rate != null)
				{
					tip.row(e.getKey(), DropMath.formatRate(DropMath.parseRate(rate)));
				}
			}
		}

		tip.note(streakNote(row, unit));
		if (row.getFastest() != null)
		{
			tip.note(row.getFastest().equals(row.getLongest())
				? String.format("Since On Rate: one drop took %,d %s.", row.getFastest(), unit + (row.getFastest() == 1 ? "" : "s"))
				: String.format("Since On Rate: fastest drop after %,d %s, longest after %,d.", row.getFastest(),
					unit + (row.getFastest() == 1 ? "" : "s"), row.getLongest()));
		}
		boolean ownDrop = drop != null && stats.getBoss().getDrops().contains(drop);
		if (ownDrop && drop.getAlsoFrom() != null)
		{
			tip.note("Also drops from " + drop.getAlsoFrom() + ", and the collection log counts those too. "
				+ "Got yours there? Right-click to leave it out.");
		}
		if (drop != null && !ownDrop)
		{
			// a shared item the player split: their own choice of where the older copies came from
			tip.note("Other bosses drop this too. You chose to judge it on each boss separately: the copies On Rate "
				+ "saw drop count for the boss that dropped them, and the older ones where you said. "
				+ "Right-click to judge it with the other bosses again.");
		}
		if (multiple != null)
		{
			// a dry spell says nothing about the next one
			tip.note(String.format("Each %s is its own roll. Going without one does not make the next more likely.", unit));
		}
		return tip.wide();
	}

	static DropDefinition findDrop(List<DropDefinition> drops, String label)
	{
		for (DropDefinition drop : drops)
		{
			if (drop.getDisplayName().equals(label))
			{
				return drop;
			}
		}
		return null;
	}
}
