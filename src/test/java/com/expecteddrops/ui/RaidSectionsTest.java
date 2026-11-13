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
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { },
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
		// what a player can see: the hidden one-time notice card also mentions Raid Data Tracker
		if (!c.isVisible())
		{
			return;
		}
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

	private Tracker mole(boolean synced) throws Exception
	{
		Tracker tracker = tracker();
		java.util.Map<String, Integer> page = new java.util.LinkedHashMap<>();
		page.put("Immaculate mole skin", 0);
		page.put("Baby mole", 0);
		if (synced)
		{
			tracker.onClogPage("Giant Mole", Collections.singletonList("Giant Mole kills: 300"), page);
		}
		return tracker;
	}

	@Test
	public void aPlainBossPageGetsTheForecastTable() throws Exception
	{
		ExpectedDropsPanel panel = panel(mole(true), false);
		set(panel, "logOpen", true);
		panel.showBoss("Giant Mole");
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "UNIQUES FORECAST"));
		assertFalse(any(labels, "RAIDS LEFT FOR THE LOG"));
		assertTrue(labels.contains("Missing") && labels.contains("1 unique + pet"));
		assertTrue(labels.contains("50% chance") && labels.contains("90% chance"));
		assertTrue(labels.contains("Immaculate mole skin") && labels.contains("With the pet"));
	}

	@Test
	public void aPlainBossForecastNeedsTheLogPage() throws Exception
	{
		ExpectedDropsPanel panel = panel(mole(false), false);
		set(panel, "logOpen", true);
		panel.showBoss("Giant Mole");
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "UNIQUES FORECAST"));
		assertTrue(any(labels, "Open this boss's collection log page"));
		assertFalse(labels.contains("50% chance"));
	}

	@Test
	public void theSwitchTurnsTheBossForecastOff() throws Exception
	{
		ExpectedDropsPanel panel = panel(mole(true), false);
		set(panel, "raidExtras", false);
		panel.showBoss("Giant Mole");
		panel.refresh();
		assertFalse(any(labels(panel), "UNIQUES FORECAST"));
	}

	/** A boss page with its collection log read: the kill lines it showed, and no items yet. */
	private static Tracker synced(String boss, String... lines) throws Exception
	{
		Tracker tracker = tracker();
		tracker.onClogPage(boss, java.util.Arrays.asList(lines), new java.util.LinkedHashMap<String, Integer>());
		return tracker;
	}

	private static List<String> open(Tracker tracker, String boss) throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker, false);
		set(panel, "logOpen", true);
		panel.showBoss(boss);
		panel.refresh();
		return labels(panel);
	}

	@Test
	public void doomForecastsAtTheDelveLevelsYouDid() throws Exception
	{
		// no delves yet: a note, no table
		List<String> none = open(synced("Doom of Mokhaiotl", "Doom of Mokhaiotl kills: 0"), "Doom of Mokhaiotl");
		assertTrue(any(none, "UNIQUES FORECAST"));
		assertTrue(any(none, "No delves seen yet"));
		assertFalse(none.contains("50% chance"));
		// deeper delves: a higher chance a delve, so fewer delves to the same odds
		Tracker shallow = synced("Doom of Mokhaiotl", "Doom of Mokhaiotl kills: 40");
		shallow.onDelveScoreboard(java.util.Collections.singletonMap(3, 40));
		Tracker deep = synced("Doom of Mokhaiotl", "Doom of Mokhaiotl kills: 40");
		deep.onDelveScoreboard(java.util.Collections.singletonMap(8, 40));
		List<String> a = open(shallow, "Doom of Mokhaiotl");
		List<String> b = open(deep, "Doom of Mokhaiotl");
		assertTrue(any(a, "50% chance") && any(b, "50% chance"));
		assertTrue(any(a, "delve levels you have done so far"));
		assertFalse(a.equals(b));
	}

	@Test
	public void theForecastStaysWhenHistoryIsOpen() throws Exception
	{
		Tracker tracker = synced("Zulrah", "Zulrah kills: 300");
		for (int i = 0; i < 3; i++)
		{
			tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>" + (301 + i) + "</col>.", 1_000_000 + i * 3_600_000L);
		}
		ExpectedDropsPanel panel = panel(tracker, false);
		set(panel, "logOpen", true);
		set(panel, "historyOpen", true);
		panel.showBoss("Zulrah");
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "HISTORY"));
		assertTrue(any(labels, "UNIQUES FORECAST"));
		assertTrue(labels.contains("50% chance"));
	}

	@Test
	public void theDoomForecastStaysWhenHistoryIsOpen() throws Exception
	{
		Tracker tracker = synced("Doom of Mokhaiotl", "Doom of Mokhaiotl kills: 40");
		for (int i = 0; i < 40; i++)
		{
			tracker.onChatMessage("Delve level: " + (i % 9 + 1) + " duration: <col=ff0000>1:00.00</col>", 1_000_000 + i * 600_000L);
		}
		ExpectedDropsPanel panel = panel(tracker, false);
		set(panel, "logOpen", true);
		set(panel, "historyOpen", true);
		panel.showBoss("Doom of Mokhaiotl");
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "HISTORY"));
		assertTrue(any(labels, "UNIQUES FORECAST"));
	}

	@Test
	public void aCleanBossHasNoFootnote() throws Exception
	{
		List<String> labels = open(synced("Zulrah", "Zulrah kills: 300"), "Zulrah");
		assertTrue(any(labels, "UNIQUES FORECAST"));
		assertTrue(labels.contains("50% chance") && labels.contains("90% chance"));
		assertFalse(any(labels, "average share or estimate"));
	}

	@Test
	public void aTeamBossGetsTheShareFootnote() throws Exception
	{
		List<String> labels = open(synced("Nex", "Nex kills: 50"), "Nex");
		assertTrue(any(labels, "UNIQUES FORECAST"));
		assertTrue(any(labels, "average share or estimate"));
	}

	@Test
	public void estimateBossesGetTheFootnote() throws Exception
	{
		assertTrue(any(open(synced("Tempoross", "Tempoross kills: 50"), "Tempoross"), "average share or estimate"));
		assertTrue(any(open(synced("Barrows Chests", "Barrows Chests: 50"), "Barrows"), "average share or estimate"));
	}

	@Test
	public void vorkathForecastsTheRealRollsAndLeavesTheHeadOut() throws Exception
	{
		List<String> labels = open(synced("Vorkath", "Vorkath kills: 120"), "Vorkath");
		assertTrue(any(labels, "UNIQUES FORECAST"));
		assertTrue(any(labels, "Vorkath's head is guaranteed at kill 50 and is not part of this table"));
		assertTrue(labels.contains("50% chance"));
	}

	@Test
	public void aCombinedPageGetsOneTablePerPartWithAllThreePets() throws Exception
	{
		Tracker tracker = synced("Dagannoth Kings", "Dagannoth Rex kills: 436", "Dagannoth Prime kills: 10",
			"Dagannoth Supreme kills: 0");
		ExpectedDropsPanel panel = panel(tracker, false);
		set(panel, "logOpen", true);
		panel.showBoss("Dagannoth Kings");
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "DAGANNOTH REX (436 kills)"));
		assertTrue(any(labels, "DAGANNOTH PRIME (10 kills)"));
		// a part with no kills yet shows a note, not a table
		assertTrue(any(labels, "No Dagannoth Supreme kills seen yet"));
		assertFalse(any(labels, "DAGANNOTH SUPREME ("));
		// each part with kills has its own table and its own pet (the old single-pet bug kept one)
		int tables = 0;
		for (String l : labels)
		{
			tables += l.equals("50% chance") ? 1 : 0;
		}
		assertEquals(2, tables);
		// all three kings with kills: three tables, each ending in "With the pet", and all three pets in the hovers
		ExpectedDropsPanel all = panel(synced("Dagannoth Kings", "Dagannoth Rex kills: 5", "Dagannoth Prime kills: 5",
			"Dagannoth Supreme kills: 5"), false);
		set(all, "logOpen", true);
		all.showBoss("Dagannoth Kings");
		all.refresh();
		int withPet = 0;
		for (String l : labels(all))
		{
			withPet += l.equals("With the pet") ? 1 : 0;
		}
		assertEquals(3, withPet);
		StringBuilder tips = new StringBuilder();
		tips(all, tips);
		String text = tips.toString().toLowerCase(java.util.Locale.ROOT);
		assertTrue(text.contains("pet dagannoth rex") && text.contains("pet dagannoth prime")
			&& text.contains("pet dagannoth supreme"));
	}

	private static void tips(Component c, StringBuilder out)
	{
		if (c instanceof javax.swing.JComponent && ((javax.swing.JComponent) c).getToolTipText() != null)
		{
			out.append(((javax.swing.JComponent) c).getToolTipText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				tips(child, out);
			}
		}
	}

	@Test
	public void theForecastNeedsTheLogPageOnAnyBoss() throws Exception
	{
		for (String name : new String[]{"Zulrah", "Dagannoth Kings", "Vorkath"})
		{
			List<String> labels = open(tracker(), name);
			assertTrue(name, any(labels, "Open this boss's collection log page"));
			assertFalse(name, labels.contains("50% chance"));
		}
	}

	@Test
	public void theSwitchTurnsTheForecastOffOnEveryBoss() throws Exception
	{
		for (String name : new String[]{"Zulrah", "Nex", "Vorkath", "Dagannoth Kings"})
		{
			ExpectedDropsPanel panel = panel(synced(name, name + " kills: 50"), false);
			set(panel, "raidExtras", false);
			panel.showBoss(name);
			panel.refresh();
			assertFalse(name, any(labels(panel), "UNIQUES FORECAST"));
		}
	}

	@Test
	public void shareAndEstimateBossesGetTheTable() throws Exception
	{
		for (String name : new String[]{"Nex", "Royal Titans", "Tempoross", "Barrows", "Zalcano"})
		{
			ExpectedDropsPanel panel = panel(tracker(), false);
			set(panel, "logOpen", true);
			panel.showBoss(name);
			panel.refresh();
			assertTrue(name, any(labels(panel), "UNIQUES FORECAST"));
		}
	}

	@Test
	public void bossesLeftOutGetNoForecastTable() throws Exception
	{
		for (String name : new String[]{"Fortis Colosseum", "Wintertodt", "Chaos Elemental",
			"Moons of Peril"})
		{
			ExpectedDropsPanel panel = panel(tracker(), false);
			panel.showBoss(name);
			panel.refresh();
			List<String> labels = labels(panel);
			assertFalse(name, any(labels, "UNIQUES FORECAST"));
			assertFalse(name, any(labels, "RAIDS LEFT FOR THE LOG"));
		}
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

	private static java.util.Map<Integer, Integer> sizes(int... pairs)
	{
		java.util.Map<Integer, Integer> m = new java.util.TreeMap<>();
		for (int i = 0; i < pairs.length; i += 2)
		{
			m.put(pairs[i], pairs[i + 1]);
		}
		return m;
	}

	@Test
	public void teamSplitTextListsSizesAndEarlierKills()
	{
		assertEquals("Solo: 12 · Duo: 40 · Trio: 95 · Earlier: 3",
			ExpectedDropsPanel.teamSplitText(sizes(1, 12, 2, 40, 3, 95), 150));
		// all kills seen: no Earlier
		assertEquals("Trio: 5", ExpectedDropsPanel.teamSplitText(sizes(3, 5), 5));
		// nothing seen: no split at all
		assertEquals(null, ExpectedDropsPanel.teamSplitText(sizes(), 50));
		// a kill count lower than the kills seen never gives a negative Earlier
		assertEquals("Solo: 4", ExpectedDropsPanel.teamSplitText(sizes(1, 4), 2));
	}

	@Test
	public void teamSplitMergesLargeTeamsButTheSharedTotalStaysExact()
	{
		java.util.Map<Integer, Integer> seen = sizes(4, 8, 6, 6, 9, 3);
		assertEquals("Team of 4: 8 · Team of 6+: 9 · Earlier: 1", ExpectedDropsPanel.teamSplitText(seen, 18));
		// 8/4 + 6/6 + 3/9 from the real sizes, plus 1 earlier kill at 0.5
		assertEquals(8 / 4.0 + 6 / 6.0 + 3 / 9.0 + 0.5, ExpectedDropsPanel.teamSplitShared(seen, 18, 0.5), 1e-9);
	}

	@Test
	public void teamSplitSharedFollowsTheKillsTheTrackerSaw() throws Exception
	{
		// (that it equals Statistics.sharedKills is checked in TeamSplitSharedTest)
		Tracker tracker = tracker();
		java.util.concurrent.atomic.AtomicInteger team = new java.util.concurrent.atomic.AtomicInteger(1);
		tracker.setTeamSizeDetectorFor(b -> team.get());
		int[] sizesPerKill = {1, 1, 2, 3, 3, 3};
		for (int i = 0; i < sizesPerKill.length; i++)
		{
			team.set(sizesPerKill[i]);
			tracker.onChatMessage("Your Nex kill count is: <col=ff0000>" + (i + 1) + "</col>.", now + i * 1000L);
		}
		java.util.Map<Integer, Integer> seen = tracker.teamKills("Nex");
		assertEquals(sizes(1, 2, 2, 1, 3, 3), seen);
		double shared = ExpectedDropsPanel.teamSplitShared(seen, 6, 1.0);
		assertEquals(2 + 0.5 + 1.0, shared, 1e-9);
	}

	@Test
	public void teamSplitRowShowsAtGroupBossesOnly() throws Exception
	{
		Tracker tracker = tracker();
		java.util.concurrent.atomic.AtomicInteger team = new java.util.concurrent.atomic.AtomicInteger(1);
		tracker.setTeamSizeDetectorFor(b -> team.get());
		for (int i = 0; i < 4; i++)
		{
			team.set(i < 1 ? 1 : 3);
			tracker.onChatMessage("Your Nex kill count is: <col=ff0000>" + (i + 1) + "</col>.", now + i * 1000L);
		}
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>1</col>.", now + 10_000L);
		ExpectedDropsPanel panel = panel(tracker, true);
		set(panel, "historyOpen", true);
		panel.showBoss("Nex");
		panel.refresh();
		List<String> labels = labels(panel);
		assertTrue(any(labels, "Solo: 1 · Trio: 3"));
		assertTrue(any(labels, ">Teams<") || any(labels, "Teams"));
		// switched off
		set(panel, "teamSplit", false);
		panel.refresh();
		assertFalse(any(labels(panel), "Solo: 1 · Trio: 3"));
		// a boss without team counting has no such row
		set(panel, "teamSplit", true);
		panel.showBoss("Vorkath");
		panel.refresh();
		assertFalse(any(labels(panel), "Solo: "));
	}

	@Test
	public void teamSplitTipSaysWhatEachSizeCountsAs()
	{
		String tip = ExpectedDropsPanel.teamSplitTip(sizes(1, 2, 3, 3), 6, 1.0, false);
		assertTrue(tip.contains("each counts as 1/3"));
		assertTrue(tip.contains("4 of 6 kills"));
		assertTrue(tip.contains("1 kill from before On Rate counted teams"));
		assertTrue(ExpectedDropsPanel.teamSplitTip(sizes(1, 2), 2, 1.0, true).contains("players around you"));
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
