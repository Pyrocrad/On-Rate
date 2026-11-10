package com.onrate.engine.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class RaidInsightsTest
{
	private static final Map<String, Double> CHANCES = new HashMap<>();
	static
	{
		CHANCES.put("cox", 0.035);
		CHANCES.put("coxCm", 0.05);
		CHANCES.put("tob", 0.11);
		CHANCES.put("toa", 0.025);
	}
	private static final RaidSettings RAIDS = raid -> CHANCES.getOrDefault(raid, 0.0);
	private static final String COX = "Chambers of Xeric";
	private static final String TOA = "Tombs of Amascut";

	private Tracker tracker;
	private long now = 10_000_000;
	private int coxKc;
	private int cmKc;
	private int toaKc;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			tracker = new Tracker(BossCatalog.load(new Gson(), reader));
		}
		tracker.setSettings(RAIDS);
	}

	/** One Chambers raid: its points, then the completion message, like the game sends them. */
	private RaidRecord cox(int points, int teamPoints, int team, int minutes)
	{
		RaidRecord r = RaidRecord.cox(points, teamPoints, team);
		r.durationMillis = minutes < 0 ? -1 : minutes * 60_000;
		now += 3_600_000;
		tracker.onRaidRecord(r, now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + (++coxKc) + "</col>.", now + 1000);
		return r;
	}

	private RaidRecord cm(int points, int minutes)
	{
		RaidRecord r = RaidRecord.cox(points, points, 1);
		r.durationMillis = minutes * 60_000;
		now += 3_600_000;
		tracker.onRaidRecord(r, now);
		tracker.onChatMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>" + (++cmKc) + "</col>.", now + 1000);
		return r;
	}

	private RaidRecord toa(int points, int team, int level, int minutes)
	{
		RaidRecord r = RaidRecord.toaExact(points, team, level);
		r.durationMillis = minutes * 60_000;
		now += 3_600_000;
		tracker.onRaidRecord(r, now);
		tracker.onChatMessage("Your completed Tombs of Amascut count is: <col=ff0000>" + (++toaKc) + "</col>.", now + 1000);
		return r;
	}

	@Test
	public void setupsAreGroupedByModeAndTeamAndSortedByChanceAnHour()
	{
		for (int i = 0; i < 5; i++)
		{
			// 43,380 points solo is 5% a raid; at 30 minutes, 10% an hour
			cox(43_380, 43_380, 1, 30);
			// half of 43,380 team points in a duo is 2.5% a raid; at 12 minutes, 12.5% an hour
			cox(21_690, 43_380, 2, 12);
		}
		cm(43_380, 50);
		cm(43_380, 50);

		List<RaidInsights.Setup> setups = tracker.raidInsights(COX, null).getSetups();
		assertEquals(3, setups.size());
		RaidInsights.Setup duo = setups.get(0);
		assertEquals("Normal", duo.getPart());
		assertEquals(2, duo.getTeam());
		assertEquals(-1, duo.getLevelFrom());
		assertEquals(5, duo.getRaids());
		assertTrue(duo.isEnough());
		assertEquals(0.025, duo.getChancePerRaid(), 1e-9);
		assertEquals(12 * 60_000, duo.getAverageMillis());
		assertEquals(0.125, duo.getChancePerHour(), 1e-9);
		RaidInsights.Setup solo = setups.get(1);
		assertEquals(1, solo.getTeam());
		assertEquals(0.10, solo.getChancePerHour(), 1e-9);
		// two timed Challenge Mode raids aren't enough yet, so they come last
		RaidInsights.Setup challenge = setups.get(2);
		assertEquals("Challenge Mode", challenge.getPart());
		assertEquals(2, challenge.getTimed());
		assertFalse(challenge.isEnough());
	}

	@Test
	public void eachItemHasItsChanceARaidAndAnHourPerSetup()
	{
		for (int i = 0; i < 5; i++)
		{
			cox(43_380, 43_380, 1, 30);
		}
		RaidInsights.Setup solo = tracker.raidInsights(COX, null).getSetups().get(0);
		// 5% a raid, and a twisted bow is 2 of the 60 weight
		assertEquals(0.05 * 2 / 60, solo.getItemPerRaid("Twisted bow"), 1e-12);
		// two raids an hour
		assertEquals(0.05 * 2 / 60 * 2, solo.getItemPerHour("Twisted bow"), 1e-12);
		// the pet comes with 1 in 53 purples
		assertEquals(0.05 / 53, solo.getItemPerRaid("Olmlet"), 1e-12);
		assertTrue(Double.isNaN(solo.getItemPerRaid("Not an item")));
		// merged rows add up their items too
		assertEquals(0.05 * 2 / 60, tracker.raidInsights(COX, null).getSetups(RaidInsights.BY_TEAM).get(0).getItemPerRaid("Twisted bow"), 1e-12);
	}

	@Test
	public void recentRaidsAreOneRowOverAllModes()
	{
		assertNull(tracker.raidInsights(COX, null).getRecent());
		for (int i = 0; i < 3; i++)
		{
			cox(43_380, 43_380, 1, 30);
			cm(43_380, 50);
		}
		RaidInsights.Setup recent = tracker.raidInsights(COX, null).getRecent();
		assertEquals(6, recent.getRaids());
		assertEquals(0.05, recent.getChancePerRaid(), 1e-9);
		// a twisted bow is 2 of 60 in normal mode and 2 of 56 in Challenge Mode
		assertEquals(0.05 * (2 / 60.0 + 2 / 56.0) / 2, recent.getItemPerRaid("Twisted bow"), 1e-12);
		assertEquals(40 * 60_000, recent.getAverageMillis());
	}

	@Test
	public void setupsCanBeGroupedByTeamOrByModeAlone()
	{
		for (int i = 0; i < 5; i++)
		{
			cox(43_380, 43_380, 1, 30);
			cm(43_380, 30);
		}
		cox(21_690, 43_380, 2, 15);

		RaidInsights insights = tracker.raidInsights(COX, null);
		assertEquals(3, insights.getSetups(RaidInsights.BY_SETUP).size());
		List<RaidInsights.Setup> byTeam = insights.getSetups(RaidInsights.BY_TEAM);
		assertEquals(2, byTeam.size());
		// Normal and Challenge Mode solo raids together: 10 raids, 5 hours, 0.5 purples
		RaidInsights.Setup solo = byTeam.get(0);
		assertNull(solo.getPart());
		assertEquals(1, solo.getTeam());
		assertEquals(10, solo.getRaids());
		assertEquals(0.10, solo.getChancePerHour(), 1e-9);
		List<RaidInsights.Setup> byMode = insights.getSetups(RaidInsights.BY_CHOICE);
		assertEquals(2, byMode.size());
		assertEquals(-1, byMode.get(0).getTeam());
		assertEquals(6, byMode.stream().filter(s -> "Normal".equals(s.getPart())).findFirst().get().getRaids());
	}

	@Test
	public void tombsGroupedByChoiceIsByRaidLevel()
	{
		toa(10_000, 1, 300, 40);
		toa(10_000, 2, 310, 40);
		toa(10_000, 2, 400, 40);

		List<RaidInsights.Setup> byLevel = tracker.raidInsights(TOA, null).getSetups(RaidInsights.BY_CHOICE);
		assertEquals(2, byLevel.size());
		assertNull(byLevel.get(0).getPart());
		assertEquals(2, byLevel.stream().filter(s -> s.getLevelFrom() == 300).findFirst().get().getRaids());
	}

	@Test
	public void untimedRaidsCountForTheChanceButNotTheHour()
	{
		for (int i = 0; i < 5; i++)
		{
			cox(43_380, 43_380, 1, 30);
		}
		cox(86_760, 86_760, 1, -1);

		RaidInsights.Setup solo = tracker.raidInsights(COX, null).getSetups().get(0);
		assertEquals(6, solo.getRaids());
		assertEquals(5, solo.getTimed());
		assertEquals((0.05 * 5 + 0.10) / 6, solo.getChancePerRaid(), 1e-9);
		assertEquals(0.10, solo.getChancePerHour(), 1e-9);
	}

	@Test
	public void tombsSetupsUseRaidLevelStepsOfFifty()
	{
		toa(10_000, 2, 300, 40);
		toa(10_000, 2, 349, 40);
		toa(10_000, 2, 350, 40);

		List<RaidInsights.Setup> setups = tracker.raidInsights(TOA, null).getSetups();
		assertEquals(2, setups.size());
		assertEquals(300, setups.get(0).getLevelFrom());
		assertEquals(2, setups.get(0).getRaids());
		assertEquals(350, setups.get(1).getLevelFrom());
		assertEquals(RaidInsights.levelStep(-1), -1);
	}

	@Test
	public void bigTeamsShareOneRow()
	{
		assertEquals(0, RaidInsights.teamRow(0));
		assertEquals(5, RaidInsights.teamRow(5));
		assertEquals(RaidInsights.TEAM_CAP, RaidInsights.teamRow(RaidInsights.TEAM_CAP));
		assertEquals(RaidInsights.TEAM_CAP, RaidInsights.teamRow(40));
	}

	@Test
	public void gpValueIsEachDropsExpectedTimesItsPrice()
	{
		for (int i = 0; i < 5; i++)
		{
			cox(43_380, 43_380, 1, 30);
		}
		// only the twisted bow has a price: 5% a raid times its weight of 2 out of the 60 of the purple table
		RaidInsights.Setup solo = tracker.raidInsights(COX, name -> "Twisted bow".equals(name) ? 1_000_000_000L : 0).getSetups().get(0);
		assertEquals(0.05 * 2 / 60 * 1_000_000_000, solo.getGpPerRaid(), 1);
		assertEquals(solo.getGpPerRaid() * 2, solo.getGpPerHour(), 1);
		// without prices there is no GP value
		assertEquals(0, tracker.raidInsights(COX, null).getSetups().get(0).getGpPerRaid(), 0);
	}

	@Test
	public void personalBestsPerModeAndTeamPointToTheRaidThatSetThem()
	{
		RaidRecord slow = cox(52_100, 52_100, 1, 40);
		RaidRecord fast = cox(30_000, 30_000, 1, 31);
		cox(25_000, 50_000, 2, 22);
		cm(41_300, 48);

		List<RaidInsights.Bests> bests = tracker.raidInsights(COX, null).getBests();
		assertEquals(3, bests.size());
		RaidInsights.Bests solo = bests.get(0);
		assertEquals("Normal", solo.getPart());
		assertEquals(1, solo.getTeam());
		assertEquals(2, solo.getRaids());
		assertEquals(31 * 60_000, solo.getFastest().getValue(), 0);
		assertSame(fast, solo.getFastest().getRaid());
		assertEquals(52_100, solo.getMostPoints().getValue(), 0);
		assertSame(slow, solo.getMostPoints().getRaid());
		assertNull(solo.getHighestLevel());
		assertEquals(2, bests.get(1).getTeam());
		assertEquals("Challenge Mode", bests.get(2).getPart());
	}

	@Test
	public void tombsBestsIncludeTheHighestRaidLevel()
	{
		toa(12_000, 1, 300, 40);
		RaidRecord high = toa(9_000, 1, 425, 45);

		RaidInsights.Bests solo = tracker.raidInsights(TOA, null).getBests().get(0);
		assertEquals(425, solo.getHighestLevel().getValue(), 0);
		assertSame(high, solo.getHighestLevel().getRaid());
		assertEquals(12_000, solo.getMostPoints().getValue(), 0);
	}

	@Test
	public void pointsAreListedPerModeOldestFirst()
	{
		cox(30_000, 30_000, 1, 30);
		cox(40_000, 40_000, 1, 30);
		cm(41_300, 48);

		RaidInsights insights = tracker.raidInsights(COX, null);
		List<RaidInsights.Point> normal = insights.getPoints("Normal");
		assertEquals(2, normal.size());
		assertEquals(30_000, normal.get(0).getPoints());
		assertEquals(40_000, normal.get(1).getPoints());
		assertFalse(normal.get(1).isPurple());
		assertEquals(1, insights.getPoints("Challenge Mode").size());
		assertTrue(insights.getPoints("Nothing").isEmpty());
	}

	@Test
	public void aRaidWithAUniqueInHistoryIsMarkedPurple()
	{
		cox(30_000, 30_000, 1, 30);
		tracker.onLoot(COX, 1, Collections.singletonList(new Tracker.LootItem("Dexterous prayer scroll", 1)), now + 2000);
		cox(40_000, 40_000, 1, 30);

		List<RaidInsights.Point> normal = tracker.raidInsights(COX, null).getPoints("Normal");
		assertTrue(normal.get(0).isPurple());
		assertFalse(normal.get(1).isPurple());
	}

	@Test
	public void nothingForABossThatIsntARaid()
	{
		assertTrue(tracker.raidInsights("Vorkath", null).isEmpty());
		assertTrue(tracker.raidInsights(COX, null).isEmpty());
	}

	private static ImportedRaid imported(String part, int kc, long when, int minutes, int team, int points, int total, String unique)
	{
		return new ImportedRaid(part, kc, when, minutes < 0 ? -1 : minutes * 60_000L, team, points, total, -1, unique);
	}

	@Test
	public void importedRaidsAreSavedWithTheirPointsAndShowInTheSections()
	{
		List<ImportedRaid> raids = new java.util.ArrayList<>();
		for (int kc = 1; kc <= 5; kc++)
		{
			raids.add(imported("Normal", kc, kc * 3_600_000L, 30, 1, 43_380, 43_380, kc == 3 ? "Twisted bow" : null));
		}
		assertEquals(5, tracker.importRaids(COX, raids));

		List<RaidRecord> saved = tracker.raidRecords(COX);
		assertEquals(5, saved.size());
		assertEquals(3, saved.get(2).getKc());
		assertEquals("Normal", saved.get(2).getPart());
		assertEquals(30 * 60_000, saved.get(2).getDurationMillis());
		// their purple chance comes from their points, like a raid On Rate saw
		assertEquals(5, tracker.measuredRaids(COX, "Normal"));
		RaidInsights insights = tracker.raidInsights(COX, null);
		assertEquals(0.10, insights.getSetups().get(0).getChancePerHour(), 1e-9);
		List<RaidInsights.Point> points = insights.getPoints("Normal");
		assertTrue(points.get(2).isPurple());
		assertEquals(Collections.singletonList("Twisted bow"), points.get(2).getUniques());
		assertFalse(points.get(1).isPurple());
	}

	@Test
	public void aRaidOnRateSawItselfIsNotImportedAgain()
	{
		RaidRecord own = cox(52_100, 52_100, 1, 31);
		List<ImportedRaid> raids = new java.util.ArrayList<>();
		raids.add(imported("Normal", 1, now - 1000, 40, 2, 10_000, 20_000, "Dragon claws"));
		raids.add(imported("Normal", 2, now + 3_600_000, 30, 1, 30_000, 30_000, null));
		assertEquals(1, tracker.importRaids(COX, raids));
		// importing the same list again adds nothing
		assertEquals(0, tracker.importRaids(COX, raids));

		List<RaidRecord> saved = tracker.raidRecords(COX);
		assertEquals(2, saved.size());
		assertSame(own, saved.get(0));
		assertEquals(52_100, saved.get(0).getPersonalPoints());
		// the unique Raid Data Tracker saw still names the purple of On Rate's own record
		assertEquals(Collections.singletonList("Dragon claws"), tracker.raidInsights(COX, null).getPoints("Normal").get(0).getUniques());
	}

	@Test
	public void aCompletionCountThatRunsBackwardsIsSkipped()
	{
		List<ImportedRaid> raids = new java.util.ArrayList<>();
		raids.add(imported("Normal", 10, 1_000, 30, 1, 30_000, 30_000, null));
		raids.add(imported("Normal", 9, 2_000, 30, 1, 30_000, 30_000, null));
		raids.add(imported("Normal", 11, 3_000, 30, 1, 30_000, 30_000, null));
		// another mode has its own count
		raids.add(imported("Challenge Mode", 2, 2_500, 50, 1, 40_000, 40_000, null));
		// a mode this raid doesn't have, and a raid without points
		raids.add(imported("Hard Mode", 3, 2_600, 50, 1, 40_000, 40_000, null));
		raids.add(imported("Normal", 12, 4_000, 30, 1, 0, 0, null));
		assertEquals(3, tracker.importRaids(COX, raids));
		List<RaidRecord> saved = tracker.raidRecords(COX);
		assertEquals(10, saved.get(0).getKc());
		assertEquals("Challenge Mode", saved.get(1).getPart());
		assertEquals(11, saved.get(2).getKc());
	}

	@Test
	public void importedRaidsGoInTheirPlaceByDate()
	{
		RaidRecord own = cox(30_000, 30_000, 1, 30);
		List<ImportedRaid> raids = new java.util.ArrayList<>();
		raids.add(imported("Normal", 5, now + 7_200_000, 30, 1, 30_000, 30_000, null));
		raids.add(imported("Normal", 0 + 2, 1_000, 30, 1, 30_000, 30_000, null));
		tracker.importRaids(COX, raids);
		List<RaidRecord> saved = tracker.raidRecords(COX);
		assertEquals(3, saved.size());
		assertEquals(2, saved.get(0).getKc());
		assertSame(own, saved.get(1));
		assertEquals(5, saved.get(2).getKc());
	}

	@Test
	public void tombsRaidsOnlyShowInTheSectionsAndDontChangeTheSavedRaids()
	{
		// one raid On Rate saw itself; Raid Data Tracker has the same raid a few seconds later
		RaidRecord own = toa(26_595, 3, 225, 40);
		List<ImportedRaid> raids = new java.util.ArrayList<>();
		raids.add(new ImportedRaid("Normal", 40, now + 5_000, 2_410_000L, 3, 26_595, 26_596, 225, null));
		raids.add(new ImportedRaid("Expert Mode", 11, now - 86_400_000L, 2_200_000L, 1, 30_000, 30_000, 300, "Osmumten's fang"));
		raids.add(new ImportedRaid("Normal", 12, now - 3 * 86_400_000L, -1, 2, 20_000, 40_000, 240, null));
		// nothing is saved: Tombs counts in those files can't be placed among your raids
		assertEquals(0, tracker.importRaids(TOA, raids));
		assertEquals(1, tracker.raidRecords(TOA).size());
		assertSame(own, tracker.raidRecords(TOA).get(0));

		RaidInsights insights = tracker.raidInsights(TOA, null);
		int raidsInSetups = 0;
		for (RaidInsights.Setup s : insights.getSetups())
		{
			raidsInSetups += s.getRaids();
		}
		// your own raid once (not again from the other plugin), plus the two other raids
		assertEquals(3, raidsInSetups);
		RaidInsights.Point expert = insights.getPoints("Expert Mode").get(0);
		assertEquals(0, expert.getRaid().getKc());
		assertEquals(Collections.singletonList("Osmumten's fang"), expert.getUniques());
		// the older raid comes first on the graph
		assertEquals(240, insights.getPoints("Normal").get(0).getRaid().getRaidLevel());

		// importing again replaces the list instead of adding it twice
		tracker.importRaids(TOA, raids);
		int again = 0;
		for (RaidInsights.Setup s : tracker.raidInsights(TOA, null).getSetups())
		{
			again += s.getRaids();
		}
		assertEquals(3, again);
	}

	private RaidRecord coxScaled(int points, int scale, int players, int minutes)
	{
		RaidRecord r = RaidRecord.cox(points, points * scale, scale, players);
		r.durationMillis = minutes * 60_000;
		now += 3_600_000;
		tracker.onRaidRecord(r, now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + (++coxKc) + "</col>.", now + 1000);
		return r;
	}

	@Test
	public void aScaledRaidARealTeamAndAnUnknownOneAreSeparateSetups()
	{
		coxScaled(30_000, 3, 1, 40);
		coxScaled(20_000, 3, 3, 25);
		// saved before the players were counted: was it a trio or a scaled raid?
		cox(20_000, 60_000, 3, 25);

		List<RaidInsights.Setup> setups = tracker.raidInsights(COX, null).getSetups();
		assertEquals(3, setups.size());
		java.util.Set<Integer> players = new java.util.HashSet<>();
		for (RaidInsights.Setup s : setups)
		{
			assertEquals(3, s.getTeam());
			players.add(s.getPlayers());
		}
		assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(1, 3, -1)), players);
		// and three lines of personal bests
		assertEquals(3, tracker.raidInsights(COX, null).getBests().size());
		// grouped by team, they stay apart; grouped by mode alone, together
		assertEquals(3, tracker.raidInsights(COX, null).getSetups(RaidInsights.BY_TEAM).size());
		assertEquals(1, tracker.raidInsights(COX, null).getSetups(RaidInsights.BY_CHOICE).size());
	}

	@Test
	public void soloRaidsAndOtherRaidsAreNeverScaled()
	{
		cox(30_000, 30_000, 1, 30);
		toa(10_000, 3, 300, 40);
		assertEquals(1, tracker.raidInsights(COX, null).getSetups().get(0).getPlayers());
		assertEquals(3, tracker.raidInsights(TOA, null).getSetups().get(0).getPlayers());
	}

	@Test
	public void playersAreOnlySavedWhenKnownSoOldSavesStayTheSame()
	{
		Gson gson = new Gson();
		// a raid without the count is written exactly as before
		assertFalse(gson.toJson(RaidRecord.cox(30_000, 90_000, 3)).contains("players"));
		RaidRecord scaled = RaidRecord.cox(30_000, 90_000, 3, 1);
		String json = gson.toJson(scaled);
		assertTrue(json.contains("\"players\":1"));
		assertEquals(Integer.valueOf(1), gson.fromJson(json, RaidRecord.class).players);
		// an old save has no players field: it loads as unknown
		RaidRecord old = gson.fromJson("{\"family\":\"cox\",\"part\":\"Normal\",\"kc\":241,\"personalPoints\":10102,"
			+ "\"teamPoints\":37189,\"teamSize\":2,\"raidLevel\":-1,\"durationMillis\":1301400}", RaidRecord.class);
		assertNull(old.players);
		assertEquals(2, old.teamSize);
		// 0 players counted is no count
		assertNull(RaidRecord.cox(30_000, 90_000, 3, 0).players);
	}
}
