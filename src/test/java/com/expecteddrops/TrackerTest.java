package com.expecteddrops;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.tracking.SyncStatus;
import com.expecteddrops.tracking.Tracker;
import com.expecteddrops.tracking.TrackerState;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class TrackerTest
{
	private static final RaidSettings RAIDS = raid -> "cox".equals(raid) ? 0.035 : 0.05;

	private BossCatalog catalog;
	private Tracker tracker;
	private long now = 1_000_000;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = new InputStreamReader(
			ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		tracker = new Tracker(catalog);
	}

	@Test
	public void bundledDataIsValid()
	{
		assertTrue(catalog.getBosses().size() >= 50);
		Set<String> names = new HashSet<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			assertTrue("duplicate boss " + boss.getName(), names.add(boss.getName()));
			assertFalse("no drops for " + boss.getName(), boss.getDrops().isEmpty());
			assertTrue("no way to count kills for " + boss.getName(),
				!boss.getKcNames().isEmpty() || !boss.getLootNames().isEmpty());
			int pets = 0;
			for (DropDefinition drop : boss.getDrops())
			{
				if (boss.isDelve())
				{
					assertTrue(drop.hasLevelRates());
					for (int level = 1; level <= BossDefinition.DELVE_LEVELS; level++)
					{
						double rate = DropMath.parseRate(drop.getLevelRate(level));
						assertTrue(rate >= 0 && rate < 1);
					}
					continue;
				}
				double rate = Tracker.effectiveRate(boss, drop, RAIDS);
				assertTrue(boss.getName() + " / " + drop.getName() + " has rate " + rate, rate > 0 && rate < 1);
				if (drop.isPet())
				{
					pets++;
				}
			}
			assertTrue("more than one pet for " + boss.getName(), pets <= 1);
		}
	}

	@Test
	public void killCountFromChat()
	{
		assertEquals("Vorkath", tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>1,234</col>.", now));
		BossStats stats = tracker.stats("Vorkath", RAIDS);
		assertEquals(1234, stats.getKillCount());
		assertTrue(stats.isKillCountKnown());
		RowStats head = stats.getRows().get(0);
		assertEquals("Vorkath's head", head.getLabel());
		assertEquals(1234 / 50.0, head.getExpected(), 1e-9);
	}

	@Test
	public void raidModesAreAddedTogether()
	{
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now);
		tracker.onChatMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>20</col>.", now);
		BossStats stats = tracker.stats("Chambers of Xeric", RAIDS);
		assertEquals(120, stats.getKillCount());
		RowStats tbow = stats.getRows().stream().filter(r -> r.getLabel().equals("Twisted bow")).findFirst().get();
		assertEquals(120 * 0.035 * 2 / 69, tbow.getExpected(), 1e-9);
	}

	@Test
	public void otherMessagesAreIgnored()
	{
		assertNull(tracker.onChatMessage("Welcome to Old School RuneScape.", now));
		assertNull(tracker.onChatMessage("Your Some Random Thing kill count is: <col=ff0000>5</col>.", now));
	}

	@Test
	public void lootCountsUniquesAndKills()
	{
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>500</col>.", now);
		tracker.onLoot("Zulrah", 1, Arrays.asList(
			new Tracker.LootItem("Zulrah's scales", 250),
			new Tracker.LootItem("Tanzanite fang", 1)), now);
		BossStats stats = tracker.stats("Zulrah", RAIDS);
		assertEquals(500, stats.getKillCount());
		assertEquals(1, stats.getAnyRow().getReceived());
		assertEquals(1, stats.getRows().get(0).getReceived());
	}

	@Test
	public void lootOnlyBossCountsKillsFromLoot()
	{
		for (int i = 0; i < 3; i++)
		{
			tracker.onLoot("Tormented Demon", 1, Collections.singletonList(new Tracker.LootItem("Ashes", 1)), now);
		}
		BossStats stats = tracker.stats("Tormented Demons", RAIDS);
		assertEquals(3, stats.getKillCount());
		assertFalse(stats.isKillCountKnown());
	}

	@Test
	public void collectionLogSetsHistory()
	{
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Vorkath's head", 7);
		items.put("Draconic visage", 1);
		items.put("Skeletal visage", 0);
		items.put("Jar of decay", 0);
		items.put("Vorki", 1);
		Set<String> changed = tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 350"), items);
		assertEquals(Collections.singleton("Vorkath"), changed);

		BossStats stats = tracker.stats("Vorkath", RAIDS);
		assertEquals(350, stats.getKillCount());
		assertTrue(stats.isClogSynced());
		assertEquals(7, row(stats, "Vorkath's head").getReceived());
		assertEquals(1, row(stats, "Vorki").getReceived());
		// Draconic visage also drops from KBD, so the log total isn't credited to Vorkath.
		assertEquals(0, row(stats, "Draconic visage").getReceived());

		// New drops after the sync are added on top.
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		assertEquals(8, row(tracker.stats("Vorkath", RAIDS), "Vorkath's head").getReceived());
	}

	@Test
	public void collectionLogPicksTheRightCounter()
	{
		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Parasitic egg", 2);
		tracker.onClogPage("The Nightmare", Arrays.asList("The Nightmare kills: 40", "Phosani's Nightmare kills: 310"), items);
		BossStats stats = tracker.stats("Phosani's Nightmare", RAIDS);
		assertEquals(310, stats.getKillCount());
		assertEquals(2, row(stats, "Parasitic egg").getReceived());
	}

	@Test
	public void petGoesToLastBoss()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>10</col>.", now);
		assertEquals("Vorkath", tracker.onChatMessage("You have a funny feeling like you're being followed.", now + 1000));
		assertEquals(1, row(tracker.stats("Vorkath", RAIDS), "Vorki").getReceived());
		// Too long after a kill: not credited.
		assertNull(tracker.onChatMessage("You have a funny feeling like you're being followed.", now + 10 * 60_000));
	}

	@Test
	public void gauntletUsesCollectionLogMessage()
	{
		tracker.onChatMessage("Your Corrupted Gauntlet completion count is: <col=ff0000>80</col>.", now);
		assertEquals("Corrupted Gauntlet",
			tracker.onChatMessage("New item added to your collection log: <col=ef1020>Enhanced crystal weapon seed</col>", now + 2000));
		BossStats stats = tracker.stats("Corrupted Gauntlet", RAIDS);
		assertEquals(80, stats.getKillCount());
		assertEquals(1, row(stats, "Enhanced crystal weapon seed").getReceived());
		// Bosses with loot tracking don't take credit from the message (the loot event already did).
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>3</col>.", now);
		assertNull(tracker.onChatMessage("New item added to your collection log: Vorkath's head", now + 1000));
	}

	@Test
	public void doomMatchesTheDelveCalculatorScreenshot()
	{
		// Level counts from the Delve Calculator screenshot: expected Any 4.418, cloth 1.553,
		// eye 1.348, treads 1.122, Dom 0.396 (shown there as 4 41.8%, 1 55.3%, 1 34.8%, 1 12.2%, 0 39.6%).
		Map<Integer, Integer> levels = new LinkedHashMap<>();
		int[] counts = {515, 511, 453, 378, 302, 225, 119, 6, 0};
		for (int i = 0; i < counts.length; i++)
		{
			levels.put(i + 1, counts[i]);
		}
		assertEquals("Doom of Mokhaiotl", tracker.onDelveScoreboard(levels));
		BossStats stats = tracker.stats("Doom of Mokhaiotl", RAIDS);
		assertEquals(2509, stats.getKillCount());
		assertEquals(4.418, stats.getAnyRow().getExpected(), 0.0005);
		assertEquals(1.553, row(stats, "Mokhaiotl cloth").getExpected(), 0.0005);
		assertEquals(1.348, row(stats, "Eye of ayak (uncharged)").getExpected(), 0.0005);
		assertEquals(1.122, row(stats, "Avernic treads").getExpected(), 0.0005);
		assertEquals(0.396, row(stats, "Dom").getExpected(), 0.0005);
	}

	@Test
	public void doomCountsLevelsFromChat()
	{
		assertEquals("Doom of Mokhaiotl", tracker.onChatMessage("Delve level: 3 duration: <col=ff0000>1:52.20</col>", now));
		tracker.onChatMessage("Delve level: 8+ (12) duration: <col=ff0000>2:05.00</col>", now);
		tracker.onChatMessage("Delve level: 8+ (13) duration: <col=ff0000>2:01.00</col>", now);
		BossStats stats = tracker.stats("Doom of Mokhaiotl", RAIDS);
		assertEquals(3, stats.getKillCount());
		assertEquals(1, (int) stats.getLevelCounts().get(3));
		assertEquals(2, (int) stats.getLevelCounts().get(BossDefinition.DELVE_LEVELS));
		assertEquals(1 / 2000.0 + 2 / 540.0, row(stats, "Mokhaiotl cloth").getExpected(), 1e-12);
		// Dom comes from the pet message right after a level.
		assertEquals("Doom of Mokhaiotl", tracker.onChatMessage("You have a funny feeling like you're being followed.", now + 500));
		assertEquals(1, row(tracker.stats("Doom of Mokhaiotl", RAIDS), "Dom").getReceived());
		// Uniques come from the loot tracker's Doom event.
		tracker.onLoot("Doom of Mokhaiotl", 1, Collections.singletonList(new Tracker.LootItem("Avernic treads", 1)), now);
		assertEquals(1, row(tracker.stats("Doom of Mokhaiotl", RAIDS), "Avernic treads").getReceived());
	}

	@Test
	public void checklistTracksWhatIsSynced()
	{
		List<SyncStatus> before = tracker.syncChecklist();
		assertTrue(before.stream().noneMatch(SyncStatus::isSynced));
		// every page is listed once
		Set<String> pages = new HashSet<>();
		for (SyncStatus s : before)
		{
			assertTrue("duplicate page " + s.getPage(), pages.add(s.getPage().toLowerCase()));
		}
		assertTrue(pages.contains("vorkath"));
		assertTrue(pages.contains("doom scoreboard"));

		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Vorkath's head", 1);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 10"), items);
		Map<Integer, Integer> levels = new LinkedHashMap<>();
		levels.put(1, 5);
		tracker.onDelveScoreboard(levels);

		List<SyncStatus> after = tracker.syncChecklist();
		assertEquals(2, after.stream().filter(SyncStatus::isSynced).count());
		assertEquals(before.size(), after.size());
	}

	@Test
	public void remindersSayWhatToOpen()
	{
		assertEquals("Open the \"Vorkath\" page in your collection log once to load your past drops.", tracker.syncReminder("Vorkath"));
		assertEquals("Open the \"The Nightmare\" page in your collection log once to load your past drops.",
			tracker.syncReminder("Phosani's Nightmare"));
		assertTrue(tracker.syncReminder("Doom of Mokhaiotl").contains("Doom scoreboard"));
		// Bosses without a readable page get no reminder.
		assertNull(tracker.syncReminder("Callisto"));

		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 10"), new LinkedHashMap<>());
		assertNull(tracker.syncReminder("Vorkath"));
	}

	@Test
	public void barrowsGroupsPiecesPerBrother()
	{
		tracker.onChatMessage("Your Barrows chest count is: <col=ff0000>100</col>.", now);
		tracker.onLoot("Barrows", 1, Arrays.asList(new Tracker.LootItem("Dharok's greataxe", 1), new Tracker.LootItem("Dharok's helm", 1)), now);
		BossStats stats = tracker.stats("Barrows", RAIDS);
		assertEquals(100, stats.getKillCount());
		assertEquals(2, row(stats, "Dharok's pieces").getReceived());
		assertEquals(100 * 28.0 / 2448, row(stats, "Dharok's pieces").getExpected(), 1e-9);
	}

	@Test
	public void stateSurvivesJsonRoundTrip()
	{
		Gson gson = new Gson();
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>42</col>.", now);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		String json = gson.toJson(tracker.getState());

		Tracker restored = new Tracker(catalog);
		restored.setState(gson.fromJson(json, TrackerState.class));
		BossStats stats = restored.stats("Vorkath", RAIDS);
		assertNotNull(stats);
		assertEquals(42, stats.getKillCount());
		assertEquals(1, row(stats, "Vorkath's head").getReceived());
		assertEquals("Vorkath", restored.bossWithMostKills());
	}

	@Test
	public void anyRowUsesPoisson()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>50</col>.", now);
		RowStats any = tracker.stats("Vorkath", RAIDS).getAnyRow();
		double lambda = 50 * (1 / 50.0 + 1 / 1000.0 + 1 / 3000.0 + 1 / 3000.0 + 1 / 5000.0 + 1 / 5000.0);
		assertEquals(lambda, any.getExpected(), 1e-9);
		assertEquals(DropMath.luckPercentile(0, lambda), any.getLuck(), 1e-12);
	}

	private static RowStats row(BossStats stats, String label)
	{
		return stats.getRows().stream().filter(r -> r.getLabel().equals(label)).findFirst()
			.orElseThrow(() -> new AssertionError("no row " + label));
	}
}
