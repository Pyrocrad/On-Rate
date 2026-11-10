package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.RaidInsights;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import java.awt.Component;
import java.awt.Container;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The raid sections under History (purple chance by setup, personal bests, points over time) and
 * the PB tags in History. The pixel parity test leaves them out, so they are checked here.
 */
public class RaidSectionsTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final String COX = "Chambers of Xeric";
	private long now = System.currentTimeMillis() - 100L * 3_600_000;
	private int kc;

	private static Tracker tracker() throws Exception
	{
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			Tracker tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.035);
			return tracker;
		}
	}

	private static ExpectedDropsPanel panel(Tracker tracker, boolean gp) throws Exception
	{
		RaidSettings raids = raid -> 0.035;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { }, () -> false, () -> { },
			s -> "Twisted bow".equals(s) ? 1_000_000_000L : 0L, () -> new ViewOptions(ItemView.TARGET_BARS, gp, false, false, false, true));
		panel.setSize(225, 6000);
		return panel;
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	/** One Chambers raid with its points, team and time, then its completion message. */
	private void raid(Tracker tracker, int points, int teamPoints, int team, int minutes) throws Exception
	{
		RaidRecord r = RaidRecord.cox(points, teamPoints, team);
		set(r, "durationMillis", minutes * 60_000);
		now += 3_600_000;
		tracker.onRaidRecord(r, now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + (++kc) + "</col>.", now + 1000);
	}

	private Tracker raided() throws Exception
	{
		Tracker tracker = tracker();
		java.util.Map<String, Integer> page = new java.util.LinkedHashMap<>();
		page.put("Twisted bow", 0);
		tracker.onClogPage(COX, Collections.singletonList("Chambers of Xeric completions: 0"), page);
		for (int i = 0; i < 5; i++)
		{
			raid(tracker, 43_380, 43_380, 1, 30);
			raid(tracker, 21_690, 43_380, 2, 12);
		}
		raid(tracker, 52_100, 52_100, 1, 29);
		return tracker;
	}

	private static List<String> labels(Component c)
	{
		List<String> out = new ArrayList<>();
		labels(c, out);
		return out;
	}

	private static void labels(Component c, List<String> out)
	{
		if (c instanceof javax.swing.JLabel && ((javax.swing.JLabel) c).getText() != null)
		{
			out.add(((javax.swing.JLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				labels(child, out);
			}
		}
	}

	private static boolean any(List<String> labels, String part)
	{
		for (String l : labels)
		{
			if (l.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void theThreeSectionsAreFoldedUnderHistoryOnARaidPage() throws Exception
	{
		ExpectedDropsPanel panel = panel(raided(), true);
		panel.showBoss(COX);
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "▸ PURPLE CHANCE BY SETUP"));
		assertTrue(any(labels, "▸ PERSONAL BESTS"));
		assertTrue(any(labels, "▸ POINTS OVER TIME"));
		// folded: nothing of their content yet
		assertFalse(any(labels, "FASTEST PURPLES"));
	}

	@Test
	public void setupsAreSortedByChanceAnHourWithTheTopOneTagged() throws Exception
	{
		ExpectedDropsPanel panel = panel(raided(), true);
		set(panel, "setupsOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		List<String> labels = labels(panel);
		// the duo gets 12.5% an hour (2.5% a raid in 12 minutes), solo less
		assertTrue(any(labels, "Duo") && any(labels, "FASTEST PURPLES"));
		assertTrue(labels.contains("12.5%/h"));
		assertTrue(any(labels, "GP value"));

		set(panel, "setupsGp", true);
		panel.refresh();
		assertTrue(any(labels(panel), "MOST GP"));
	}

	@Test
	public void withoutGpValueThereIsNoGpTab() throws Exception
	{
		ExpectedDropsPanel panel = panel(raided(), false);
		set(panel, "setupsOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		assertFalse(labels(panel).contains("GP value"));
	}

	@Test
	public void personalBestsAndThePbTagInHistory() throws Exception
	{
		ExpectedDropsPanel panel = panel(raided(), true);
		set(panel, "bestsOpen", true);
		set(panel, "historyOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(labels.contains("Fastest") && labels.contains("Most points"));
		assertTrue(labels.contains("52.1k"));
		assertTrue(labels.contains("12:00"));
		// the last raid holds the solo points record, and History says so
		assertTrue(any(labels, ">PB</font>"));
	}

	@Test
	public void pointsOverTimeDrawsTheGraph() throws Exception
	{
		ExpectedDropsPanel panel = panel(raided(), true);
		set(panel, "pointsOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		assertEquals(1, count(panel, RaidPointsGraph.class));
		assertTrue(labels(panel).contains("Points"));
	}

	@Test
	public void raidsLeftForTheLogUseYourRecentRaids() throws Exception
	{
		ExpectedDropsPanel panel = panel(raided(), true);
		set(panel, "logOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "RAIDS LEFT FOR THE LOG"));
		assertTrue(labels.contains("Missing") && labels.contains("12 uniques + pet"));
		// the table: 50% and 90% columns, raids then hours
		assertTrue(labels.contains("50% chance") && labels.contains("90% chance"));
		assertTrue(labels.contains("All uniques") && labels.contains("With the pet"));
		assertTrue(labels.contains("  in hours"));
	}

	@Test
	public void raidsLeftForTheLogNeedTheLogPage() throws Exception
	{
		Tracker tracker = tracker();
		for (int i = 0; i < 5; i++)
		{
			raid(tracker, 43_380, 43_380, 1, 30);
		}
		ExpectedDropsPanel panel = panel(tracker, true);
		set(panel, "logOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		assertTrue(any(labels(panel), "Open this raid's collection log page"));
	}

	@Test
	public void theSwitchTurnsEverythingOff() throws Exception
	{
		ExpectedDropsPanel panel = panel(raided(), true);
		set(panel, "raidExtras", false);
		set(panel, "historyOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		List<String> labels = labels(panel);
		assertFalse(any(labels, "PURPLE CHANCE BY SETUP"));
		assertFalse(any(labels, "PERSONAL BESTS"));
		assertFalse(any(labels, ">PB</font>"));
	}

	@Test
	public void setupNamesSayTheTeamAndTheModeOrRaidLevel() throws Exception
	{
		assertEquals("Solo", ExpectedDropsPanel.teamWord(1));
		assertEquals("Trio", ExpectedDropsPanel.teamWord(3));
		assertEquals("Team of 5", ExpectedDropsPanel.teamWord(5));
		assertEquals("Team of 6+", ExpectedDropsPanel.teamWord(9));
		assertEquals("Team unknown", ExpectedDropsPanel.teamWord(0));

		Tracker tracker = tracker();
		RaidRecord r = RaidRecord.toaExact(10_000, 2, 310);
		tracker.onRaidRecord(r, now);
		tracker.onChatMessage("Your completed Tombs of Amascut count is: <col=ff0000>1</col>.", now + 1000);
		RaidInsights.Setup s = tracker.raidInsights("Tombs of Amascut", null).getSetups().get(0);
		assertEquals("Duo · level 300–349", ExpectedDropsPanel.setupName(
			tracker.stats("Tombs of Amascut", raid -> 0.03).getBoss(), s));
	}

	@Test
	public void onlyTheTopFiveSetupsAreListed() throws Exception
	{
		Tracker tracker = raided();
		// four more team sizes: seven setups in all, two of them still waiting for timed raids
		for (int team = 3; team <= 5; team++)
		{
			for (int i = 0; i < 5; i++)
			{
				raid(tracker, 8_000, 8_000 * team, team, 20);
			}
		}
		raid(tracker, 5_000, 30_000, 6, 20);
		raid(tracker, 5_000, 30_000, 0, 20);
		ExpectedDropsPanel panel = panel(tracker, true);
		set(panel, "setupsOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		List<String> labels = labels(panel);
		int rows = 0;
		for (String l : labels)
		{
			if (l.endsWith("%/h"))
			{
				rows++;
			}
		}
		assertEquals(ExpectedDropsPanel.SETUPS_SHOWN, rows);
		assertTrue(any(labels, "Your top 5 of 7 setups."));
		// the ones still waiting don't fit in the top 5 here
		assertFalse(any(labels, "timed, needs 5"));
	}

	@Test
	public void pointsOverTimeShowsEveryModeOnOneGraph() throws Exception
	{
		Tracker tracker = raided();
		RaidRecord cm = RaidRecord.cox(41_300, 41_300, 1);
		set(cm, "durationMillis", 48 * 60_000);
		now += 3_600_000;
		tracker.onRaidRecord(cm, now);
		tracker.onChatMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>1</col>.", now + 1000);
		ExpectedDropsPanel panel = panel(tracker, true);
		set(panel, "pointsOpen", true);
		panel.showBoss(COX);
		panel.refresh();
		// no switch between modes: the 11 normal raids and the Challenge Mode raid together
		assertFalse(labels(panel).contains("CM"));
		RaidPointsGraph graph = find(panel, RaidPointsGraph.class);
		assertEquals(12, graph.shown());
	}

	private static <T> T find(Component c, Class<T> type)
	{
		if (type.isInstance(c))
		{
			return type.cast(c);
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				T found = find(child, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void scaledRaidsSayHowManyPlayersAndHowMuchScale()
	{
		assertEquals("Trio", ExpectedDropsPanel.teamLabel(3, 3));
		assertEquals("1+2", ExpectedDropsPanel.teamLabel(3, 1));
		assertEquals("2+2", ExpectedDropsPanel.teamLabel(4, 2));
		assertEquals("Trio/scaled", ExpectedDropsPanel.teamLabel(3, -1));
		assertEquals("Solo", ExpectedDropsPanel.teamLabel(1, -1));
		assertEquals("1+5+", ExpectedDropsPanel.teamLabel(RaidInsights.TEAM_CAP, 1));
	}

	private static int count(Component c, Class<?> type)
	{
		int n = type.isInstance(c) ? 1 : 0;
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				n += count(child, type);
			}
		}
		return n;
	}
}
