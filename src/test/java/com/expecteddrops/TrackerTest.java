package com.expecteddrops;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.BossPart;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.AccountStats;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RaidChance;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.stats.SharedPool;
import com.expecteddrops.tracking.Note;
import com.expecteddrops.tracking.BossDays;
import com.expecteddrops.tracking.RaidRecord;
import com.expecteddrops.tracking.SaveData;
import com.expecteddrops.ui.ExpectedDropsPanel;
import com.expecteddrops.tracking.HistoryRow;
import com.expecteddrops.tracking.KillEntry;
import com.expecteddrops.tracking.RecentDrop;
import com.expecteddrops.tracking.TripStats;
import com.expecteddrops.tracking.SyncStatus;
import com.expecteddrops.tracking.Tracker;
import com.expecteddrops.tracking.TrackerState;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
	private static final Map<String, Double> RAID_CHANCES = new java.util.HashMap<>();
	static
	{
		RAID_CHANCES.put("cox", 0.035);
		RAID_CHANCES.put("coxCm", 0.05);
		RAID_CHANCES.put("tob", 0.027);
		RAID_CHANCES.put("tobHm", 0.032);
		RAID_CHANCES.put("toaEntry", 0.0075);
		RAID_CHANCES.put("toa", 0.025);
		RAID_CHANCES.put("toaExpert", 0.05);
	}
	private static final RaidSettings RAIDS = raid -> RAID_CHANCES.getOrDefault(raid, 0.0);

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
		// Chaos Elemental only has the dragon pickaxe and a pet it shares with Chaos Fanatic:
		// no items of its own, so its page shows only those shared items
		assertTrue(catalog.getExcludedBosses().contains("Chaos Elemental"));
		assertNotNull(catalog.byName("Chaos Elemental"));
		// shared items get their pictures looked up like any other item
		assertTrue(catalog.getAllItemNames().contains("dragon axe"));
		assertTrue(catalog.getAllItemNames().contains("pet chaos elemental"));
		assertTrue(catalog.getAllItemNames().contains("dragon pickaxe"));
		Set<String> names = new HashSet<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			assertTrue("duplicate boss " + boss.getName(), names.add(boss.getName()));
			assertTrue("no drops for " + boss.getName(),
				!boss.getDrops().isEmpty() || catalog.getExcludedBosses().contains(boss.getName()));
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
				if (boss.hasParts())
				{
					assertTrue(boss.getName() + " / " + drop.getName() + " has no part rates",
						drop.hasPartRates() || boss.getRaid() != null);
					double best = 0;
					for (BossPart part : boss.getParts())
					{
						double rate = Tracker.partRate(boss, part, drop, RAIDS);
						assertTrue(rate >= 0 && rate < 1);
						best = Math.max(best, rate);
					}
					assertTrue(boss.getName() + " / " + drop.getName() + " never drops", best > 0);
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
		// 1/50 a kill, and the 50th kill always drops one
		assertEquals(1233 / 50.0 + 1, head.getExpected(), 1e-9);
	}

	@Test
	public void raidModesHaveTheirOwnChance()
	{
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now);
		tracker.onChatMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>20</col>.", now);
		BossStats cox = tracker.stats("Chambers of Xeric", RAIDS);
		assertEquals(120, cox.getKillCount());
		assertEquals(100, (int) cox.getPartCounts().get("Normal"));
		assertEquals(20, (int) cox.getPartCounts().get("Challenge Mode"));
		// weights out of 60 in normal mode, out of 56 in Challenge Mode (scrolls 14 -> 12)
		assertEquals(100 * 0.035 * 2 / 60 + 20 * 0.05 * 2 / 56, row(cox, "Twisted bow").getExpected(), 1e-12);
		assertEquals(100 * 0.035 * 14 / 60 + 20 * 0.05 * 12 / 56, row(cox, "Dexterous prayer scroll").getExpected(), 1e-12);

		tracker.onChatMessage("Your completed Tombs of Amascut: Entry Mode count is: <col=ff0000>50</col>.", now);
		tracker.onChatMessage("Your completed Tombs of Amascut count is: <col=ff0000>10</col>.", now);
		tracker.onChatMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>5</col>.", now);
		BossStats toa = tracker.stats("Tombs of Amascut", RAIDS);
		assertEquals(65, toa.getKillCount());
		assertEquals((50 * 0.0075 + 10 * 0.025 + 5 * 0.05) * 7 / 24, row(toa, "Osmumten's fang").getExpected(), 1e-12);

		tracker.onChatMessage("Your completed Theatre of Blood: Entry Mode count is: <col=ff0000>30</col>.", now);
		tracker.onChatMessage("Your completed Theatre of Blood count is: <col=ff0000>10</col>.", now);
		BossStats tob = tracker.stats("Theatre of Blood", RAIDS);
		assertEquals(40, tob.getKillCount());
		// Entry Mode completions add nothing
		assertEquals(10 * 0.027 * 8 / 19, row(tob, "Avernic defender hilt").getExpected(), 1e-12);
		assertEquals(10 / 650.0, row(tob, "Lil' zik").getExpected(), 1e-12);
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

	/** A made-up boss with no kill count message, counted from the Loot Tracker only. */
	private static Tracker lootOnlyTracker()
	{
		BossDefinition boss = new BossDefinition("Test Beast", Collections.emptyList(),
			Collections.singletonList("Test beast"), Collections.singletonList("Test Beast"),
			Collections.singletonList(new DropDefinition("Test fang", "1/300")));
		return new Tracker(new BossCatalog(Collections.singletonList(boss)));
	}

	@Test
	public void lootOnlyBossCountsKillsFromLoot()
	{
		Tracker t = lootOnlyTracker();
		for (int i = 0; i < 3; i++)
		{
			t.onLoot("Test beast", 1, Collections.singletonList(new Tracker.LootItem("Ashes", 1)), now);
		}
		BossStats stats = t.stats("Test Beast", RAIDS);
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
		// Draconic visage also drops from KBD, so it isn't tracked at all.
		assertFalse(stats.getRows().stream().anyMatch(r -> r.getLabel().equals("Draconic visage")));

		// New drops after the sync are added on top.
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		assertEquals(8, row(tracker.stats("Vorkath", RAIDS), "Vorkath's head").getReceived());
		// the two heads the loot tracker saw are in Recent; the log's older ones aren't
		assertEquals(2, tracker.recentDrops(10).size());
	}

	@Test
	public void logGrowthAtABossWithoutKillsAddsNothingToRecent()
	{
		Map<String, Integer> hydra = new LinkedHashMap<>();
		hydra.put("Hydra's claw", 0);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 0"), hydra);
		hydra.put("Hydra's claw", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 0"), hydra);
		assertTrue(tracker.recentDrops(10).isEmpty());
		assertTrue(tracker.history("Alchemical Hydra").isEmpty());
		assertNull(tracker.lastUnique());
		for (BossDays d : tracker.recentDays(7, System.currentTimeMillis()))
		{
			assertFalse("Alchemical Hydra".equals(d.getBoss()));
			assertEquals(0, d.getUniques());
		}
		// the log count is kept for when there are kills
		tracker.onChatMessage("Your Alchemical Hydra kill count is: <col=ff0000>5</col>.", now);
		assertEquals(1, row(tracker.stats("Alchemical Hydra", RAIDS), "Hydra's claw").getReceived());
	}

	@Test
	public void itemsFirstSeenInTheLogAreNotDatedDrops()
	{
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Steel ring", 3);
		tracker.onClogPage("Deranged Archaeologist", Collections.singletonList("Deranged Archaeologist kills: 40"), page);
		assertTrue(tracker.recentDrops(10).isEmpty());
		// a kill elsewhere, then the log shows one more ring: when and where it came from is unknown
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>10</col>.", now);
		page.put("Steel ring", 4);
		tracker.onClogPage("Deranged Archaeologist", Collections.singletonList("Deranged Archaeologist kills: 40"), page);
		assertTrue(tracker.recentDrops(10).isEmpty());
		assertTrue(tracker.history("Deranged Archaeologist").isEmpty());
		assertNull(tracker.lastUnique().item);
		for (BossDays d : tracker.recentDays(1, now))
		{
			assertEquals(0, d.getUniques());
		}
		// the count, expected and dry streak are as before: 4 rings, streak restarted
		RowStats ring = row(tracker.stats("Deranged Archaeologist", RAIDS), "Steel ring");
		assertEquals(4, ring.getReceived());
		assertEquals(40 * 3 / 131.0, ring.getExpected(), 1e-9);
		assertEquals(0, (int) ring.getStreak());
	}

	@Test
	public void firstLogSyncAfterALootDropKeepsOnlyThatDrop()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>351</col>.", now);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>10</col>.", now + 60_000);
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Vorkath's head", 7);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 351"), page);
		List<RecentDrop> recent = tracker.recentDrops(10);
		assertEquals(1, recent.size());
		assertEquals("Vorkath's head", recent.get(0).getDrop().getName());
		assertEquals(351, recent.get(0).getKill().getKc());
		assertEquals(now, recent.get(0).getKill().getTime());
		assertEquals(7, row(tracker.stats("Vorkath", RAIDS), "Vorkath's head").getReceived());
		int uniquesToday = 0;
		for (BossDays d : tracker.recentDays(1, now))
		{
			if ("Vorkath".equals(d.getBoss()))
			{
				uniquesToday = d.getUniques();
			}
		}
		assertEquals(1, uniquesToday);
	}

	@Test
	public void repeatedAndInconsistentLogReadsAddNoDrops()
	{
		Map<String, Integer> page = new LinkedHashMap<>();
		for (int qty : new int[]{2, 2, 1, 2, 2})
		{
			page.put("Steel ring", qty);
			tracker.onClogPage("Deranged Archaeologist", Collections.singletonList("Deranged Archaeologist kills: 40"), page);
		}
		assertTrue(tracker.recentDrops(10).isEmpty());
		assertTrue(tracker.history("Deranged Archaeologist").isEmpty());
		// a lower read is still stored as it was (unchanged behaviour); the last read wins
		assertEquals(2, row(tracker.stats("Deranged Archaeologist", RAIDS), "Steel ring").getReceived());
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
		assertEquals("The Gauntlet",
			tracker.onChatMessage("New item added to your collection log: <col=ef1020>Enhanced crystal weapon seed</col>", now + 2000));
		BossStats stats = tracker.stats("The Gauntlet", RAIDS);
		assertEquals(80, stats.getKillCount());
		assertEquals(80 / 400.0, row(stats, "Enhanced crystal weapon seed").getExpected(), 1e-12);
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
		// Same per-item numbers as the Delve Calculator. The "Any" total differs slightly on
		// purpose: the pet counts as "have it or not" (0.327 instead of 0.396).
		double dom = 225 / 1000.0 + 119 / 750.0 + 6 / 500.0;
		double items = row(stats, "Mokhaiotl cloth").getExpected() + row(stats, "Eye of ayak (uncharged)").getExpected()
			+ row(stats, "Avernic treads").getExpected();
		assertEquals(items + (1 - Math.exp(-dom)), stats.getAnyRow().getExpected(), 1e-9);
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
		assertEquals("Open the \"Dagannoth Kings\" page in your collection log once to load your past drops.",
			tracker.syncReminder("Dagannoth Kings"));

		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 10"), new LinkedHashMap<>());
		assertNull(tracker.syncReminder("Vorkath"));
	}

	@Test
	public void barrowsListsEveryPiece()
	{
		tracker.onChatMessage("Your Barrows chest count is: <col=ff0000>100</col>.", now);
		tracker.onLoot("Barrows", 1, Arrays.asList(new Tracker.LootItem("Dharok's greataxe", 1), new Tracker.LootItem("Dharok's helm", 1)), now);
		BossStats stats = tracker.stats("Barrows", RAIDS);
		assertEquals(100, stats.getKillCount());
		assertEquals(24, stats.getRows().size());
		assertEquals(1, row(stats, "Dharok's greataxe").getReceived());
		assertEquals(100 * 7.0 / 2448, row(stats, "Dharok's greataxe").getExpected(), 1e-9);
		assertEquals(100 * 7.0 * 24 / 2448, stats.getAnyRow().getExpected(), 1e-9);
	}

	@Test
	public void dagannothKingsShareOnePage()
	{
		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Berserker ring", 2);
		items.put("Dragon axe", 3);
		items.put("Seers ring", 0);
		tracker.onClogPage("Dagannoth Kings", Arrays.asList(
			"Dagannoth Rex kills: 436", "Dagannoth Prime kills: 10", "Dagannoth Supreme kills: 9"), items);
		BossStats stats = tracker.stats("Dagannoth Kings", RAIDS);
		assertEquals(455, stats.getKillCount());
		assertEquals(436, (int) stats.getPartCounts().get("Dagannoth Rex"));
		assertEquals(436 / 128.0, row(stats, "Berserker ring").getExpected(), 1e-12);
		assertEquals(10 / 128.0, row(stats, "Seers ring").getExpected(), 1e-12);
		// Wintertodt drops dragon axes too: the log has one total, so it's judged on both together
		com.expecteddrops.stats.SharedPool axe = tracker.sharedPoolsFor("Dagannoth Kings", RAIDS).stream()
			.filter(pool -> pool.getItemName().equals("Dragon axe")).findFirst().orElse(null);
		assertNotNull(axe);
		assertEquals(455 / 128.0, axe.getExpected(), 1e-12);
		assertEquals(3, axe.getReceived());
		assertTrue(axe.getBosses().contains("Wintertodt"));

		// A later Rex kill updates only Rex, and the pet message credits Rex's pet.
		assertEquals("Dagannoth Kings", tracker.onChatMessage("Your Dagannoth Rex kill count is: <col=ff0000>437</col>.", now));
		tracker.onChatMessage("You have a funny feeling like you're being followed.", now + 500);
		stats = tracker.stats("Dagannoth Kings", RAIDS);
		assertEquals(437, (int) stats.getPartCounts().get("Dagannoth Rex"));
		assertEquals(1, row(stats, "Pet dagannoth rex").getReceived());
		assertEquals(0, row(stats, "Pet dagannoth prime").getReceived());
	}

	@Test
	public void gauntletCountersGoToTheRightPart()
	{
		tracker.onClogPage("The Gauntlet", Arrays.asList("Gauntlet completions: 12", "Corrupted Gauntlet completions: 300"),
			new LinkedHashMap<>());
		BossStats stats = tracker.stats("The Gauntlet", RAIDS);
		assertEquals(12, (int) stats.getPartCounts().get("Gauntlet"));
		assertEquals(300, (int) stats.getPartCounts().get("Corrupted Gauntlet"));
		assertEquals(12 / 120.0 + 300 / 50.0, row(stats, "Crystal armour seed").getExpected(), 1e-12);
	}

	@Test
	public void accountAddsUpEveryBoss()
	{
		AccountStats empty = tracker.accountStats(RAIDS);
		assertTrue(empty.getRows().isEmpty());
		assertTrue(Double.isNaN(empty.getTotal().getLuck()));

		Map<String, Integer> vorkath = new LinkedHashMap<>();
		vorkath.put("Vorkath's head", 9);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 412"), vorkath);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>120</col>.", now);

		// Zulrah's log page isn't loaded: its drops from before are unknown, so it waits
		AccountStats waiting = tracker.accountStats(RAIDS);
		assertEquals(1, waiting.getRows().size());
		assertEquals(Collections.singletonList("Zulrah"), waiting.getUnsynced());

		tracker.onClogPage("Zulrah", Collections.singletonList("Zulrah kills: 120"), Collections.singletonMap("Tanzanite fang", 0));
		AccountStats account = tracker.accountStats(RAIDS);
		assertTrue(account.getUnsynced().isEmpty());
		assertEquals(2, account.getRows().size());
		double expected = tracker.stats("Vorkath", RAIDS).getAnyRow().getExpected()
			+ tracker.stats("Zulrah", RAIDS).getAnyRow().getExpected();
		assertEquals(expected, account.getTotal().getExpected(), 1e-9);
		assertEquals(9, account.getTotal().getReceived());
		// most expected uniques first
		assertEquals("Vorkath", account.getRows().get(0).getLabel());
		assertEquals(412, account.getKillCount("Vorkath"));
		assertEquals("Vorki", account.getRows().get(0).getIconItem());
	}

	@Test
	public void accountValueUsesPricesAndSkipsUntradeables()
	{
		Map<String, Integer> vorkath = new LinkedHashMap<>();
		vorkath.put("Vorkath's head", 9);
		vorkath.put("Skeletal visage", 1);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 500"), vorkath);
		java.util.function.ToLongFunction<String> prices = name ->
			name.equalsIgnoreCase("Skeletal visage") ? 20_000_000L : name.equalsIgnoreCase("Dragonbone necklace") ? 5_000_000L : 0L;

		AccountStats account = tracker.accountStats(RAIDS, prices);
		// count luck still includes the untradeable heads and the pet
		assertEquals(10, account.getTotal().getReceived());
		assertEquals(20_000_000L, account.getReceivedValue());
		assertEquals(500 / 5000.0 * 20_000_000 + 500 / 1000.0 * 5_000_000, account.getExpectedValue(), 1);
		assertFalse(Double.isNaN(account.getValueLuck()));
		// without prices there is no value
		assertTrue(Double.isNaN(tracker.accountStats(RAIDS).getValueLuck()));
	}

	@Test
	public void itemsSeveralBossesDropAreLeftOut()
	{
		// the dragon pickaxe, godsword shards, Virtus, onyx... have one log total for all
		// their bosses, so they would count as expected but never as received
		for (String item : new String[]{"Dragon pickaxe", "Godsword shard 1", "Godsword shard 2", "Godsword shard 3",
			"Chromium ingot", "Virtus mask", "Virtus robe top", "Virtus robe bottom", "Dragon chainbody",
			"Draconic visage", "Uncut onyx", "Pet chaos elemental", "Dragon 2h sword", "Dragon thrownaxe", "Dragon knife"})
		{
			assertTrue(item, catalog.getExcludedItems().contains(item));
			for (BossDefinition boss : catalog.getBosses())
			{
				for (DropDefinition drop : boss.getDrops())
				{
					assertFalse(boss.getName() + " still has " + item, drop.getName().equalsIgnoreCase(item));
				}
			}
		}
		Map<String, Integer> graardor = new LinkedHashMap<>();
		graardor.put("Bandos chestplate", 1);
		graardor.put("Godsword shard 1", 2);
		tracker.onClogPage("General Graardor", Collections.singletonList("General Graardor kills: 762"), graardor);
		BossStats boss = tracker.stats("General Graardor", RAIDS);
		assertEquals(1, boss.getAnyRow().getReceived());
		// the bodyguards roll the armour too (1/16,256 each, three of them per kill)
		assertEquals(762 * (3 * (1 / 381.0 + 3 / 16256.0) + 1 / 508.0) + DropMath.chanceAtLeastOne(762 / 5000.0),
			boss.getAnyRow().getExpected(), 1e-9);
		AccountStats account = tracker.accountStats(RAIDS);
		// shard 1 is shared by all four generals: not counted until their pages are open too
		assertEquals(1, account.getTotal().getReceived());
	}

	@Test
	public void sharedItemsArePooledButStayOutOfTheAccount()
	{
		Map<String, Integer> skotizo = new LinkedHashMap<>();
		skotizo.put("Uncut onyx", 2);
		tracker.onClogPage("Skotizo", Collections.singletonList("Skotizo kills: 100"), skotizo);
		AccountStats before = tracker.accountStats(RAIDS);
		SharedPool onyx = pool("Uncut onyx");
		assertFalse(onyx.isComplete());
		assertEquals(Collections.singletonList("Zulrah"), onyx.getMissingPages());
		int receivedBefore = before.getTotal().getReceived();

		Map<String, Integer> zulrah = new LinkedHashMap<>();
		zulrah.put("Uncut onyx", 2);
		zulrah.put("Tanzanite fang", 1);
		tracker.onClogPage("Zulrah", Collections.singletonList("Zulrah kills: 1000"), zulrah);
		AccountStats after = tracker.accountStats(RAIDS);
		onyx = pool("Uncut onyx");
		assertTrue(onyx.isComplete());
		assertEquals(2, onyx.getReceived());
		double expected = 0;
		for (BossCatalog.PoolSource src : catalog.getPools().get("uncut onyx"))
		{
			int kc = src.getBoss().getName().equals("Skotizo") ? 100 : 1000;
			expected += kc * Tracker.effectiveRate(src.getBoss(), src.getDrop(), RAIDS);
		}
		assertEquals(expected, onyx.getExpected(), 1e-9);
		// no row of their own, but they count in the account verdict: the fang plus the 2 onyx
		assertEquals(receivedBefore + 1 + 2, after.getTotal().getReceived());
		// ...but their GE value counts once every boss that drops them is known
		AccountStats withPrices = tracker.accountStats(RAIDS, item -> item.equals("Uncut onyx") ? 2_500_000L : 0L);
		assertEquals(2 * 2_500_000L, withPrices.getReceivedValue());
		assertEquals(expected * 2_500_000L, withPrices.getExpectedValue(), 1);

		// a new onyx from the loot tracker keeps the log total up to date
		tracker.onLoot("Zulrah", 1, Collections.singletonList(new Tracker.LootItem("Uncut onyx", 1)), now);
		assertEquals(3, pool("Uncut onyx").getReceived());
	}

	@Test
	public void chaosElementalKillsCountForTheDragonPickaxe()
	{
		assertTrue(catalog.getPools().containsKey("dragon pickaxe"));
		boolean chaos = false;
		for (BossCatalog.PoolSource src : catalog.getPools().get("dragon pickaxe"))
		{
			chaos |= src.getBoss().getName().equals("Chaos Elemental");
		}
		assertTrue(chaos);
		// flagged items that also come from outside the bosses are never pooled
		assertFalse(catalog.getPools().containsKey("dragon 2h sword"));
	}

	@Test
	public void bodyguardLootCountsButIsNotAKill()
	{
		tracker.onChatMessage("Your General Graardor kill count is: <col=ff0000>10</col>.", now);
		tracker.onLoot("General Graardor", 1, Collections.emptyList(), now);
		tracker.onLoot("Sergeant Steelwill", 1, Collections.singletonList(new Tracker.LootItem("Bandos boots", 1)), now);
		BossStats s = tracker.stats("General Graardor", RAIDS);
		assertEquals(10, s.getKillCount());
		assertEquals(1, row(s, "Bandos boots").getReceived());
		assertEquals(1 / 381.0 + 3 / 16256.0, row(s, "Bandos boots").getRate(), 1e-12);
		assertEquals(1 / 127.0 + 3 * 3 / 16129.0, Tracker.effectiveRate(catalog.byName("Commander Zilyana"),
			findDrop(catalog.byName("Commander Zilyana"), "Saradomin sword"), RAIDS), 1e-12);
		assertTrue(catalog.byName("General Graardor").isEstimate());
		assertFalse(catalog.byName("Vorkath").isEstimate());
		assertTrue(catalog.byName("Chambers of Xeric").isEstimate());
	}

	@Test
	public void lootOnlyBossesCountKillsFromLootAfterTheLog()
	{
		Tracker t = lootOnlyTracker();
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Test fang", 1);
		t.onLoot("Test beast", 1, Collections.emptyList(), now);
		t.onClogPage("Test Beast", Collections.singletonList("Test Beast kills: 400"), page);
		assertEquals(400, t.stats("Test Beast", RAIDS).getKillCount());
		t.onLoot("Test beast", 1, Collections.emptyList(), now);
		t.onLoot("Test beast", 1, Collections.singletonList(new Tracker.LootItem("Test fang", 1)), now);
		BossStats s = t.stats("Test Beast", RAIDS);
		assertEquals(402, s.getKillCount());
		assertEquals(2, row(s, "Test fang").getReceived());
		assertEquals(402 / 300.0, row(s, "Test fang").getExpected(), 1e-9);

		BossDefinition maggot = catalog.byKcName("Maggot King");
		assertNotNull(maggot);
		assertTrue(maggot.isEstimate());
		// only the Bosses and Raids tabs of the collection log
		assertNull(catalog.byName("Tormented Demons"));
		assertNull(catalog.byName("Glough's Experiments"));
	}

	@Test
	public void groupBossesCountYourShareOfEachKill()
	{
		int[] around = {1};
		tracker.setTeamSizeDetector(() -> around[0]);
		// 100 kills from before On Rate could see them: solo
		tracker.onChatMessage("Your Nex kill count is: <col=ff0000>100</col>.", now);
		around[0] = 4;
		for (int kc = 101; kc <= 120; kc++)
		{
			tracker.onChatMessage("Your Nex kill count is: <col=ff0000>" + kc + "</col>.", now);
		}
		// the same message again doesn't count twice
		tracker.onChatMessage("Your Nex kill count is: <col=ff0000>120</col>.", now);
		Map<Integer, Integer> seen = tracker.teamKills("Nex");
		assertEquals(Integer.valueOf(1), seen.get(1));
		assertEquals(Integer.valueOf(20), seen.get(4));
		BossStats s = tracker.stats("Nex", RAIDS);
		assertEquals(120, s.getKillCount());
		// the 99 unseen kills count at the share of the first kill On Rate counted (solo here),
		// then 1 solo + 20 kills at a quarter each
		assertEquals((99 + 1 + 20 / 4.0) / 172.0, row(s, "Zaryte vambraces").getExpected(), 1e-9);
		// the pet doesn't scale with the team
		assertEquals(120 / 500.0, row(s, "Nexling").getExpected(), 1e-9);

		// other bosses ignore the team size
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>10</col>.", now);
		assertTrue(tracker.teamKills("Vorkath").isEmpty());
	}

	@Test
	public void zalcanoSeedIsSharedButShardsAndPetAreNot()
	{
		int[] around = {10};
		tracker.setTeamSizeDetector(() -> around[0]);
		// 200 kills from before On Rate (the log page), then 40 it sees at a team of 10
		tracker.onClogPage("Zalcano", Collections.singletonList("Zalcano kills: 200"), new LinkedHashMap<>());
		for (int kc = 201; kc <= 240; kc++)
		{
			tracker.onChatMessage("Your Zalcano kill count is: <col=ff0000>" + kc + "</col>.", now + kc * 60_000L);
		}
		BossStats s = tracker.stats("Zalcano", RAIDS);
		assertEquals(240, s.getKillCount());
		// one seed roll per kill for a team of 10, the older kills at the team first seen
		assertEquals(240 * 39 / 8000.0 / 10, row(s, "Crystal tool seed").getExpected(), 1e-9);
		assertEquals(0.1, tracker.olderShare("Zalcano"), 1e-9);
		// the trip is what its own kills add, not the older kills changing share
		TripStats trip = tracker.tripStats();
		assertEquals(40, trip.getKills());
		// (the pet counts as "have it or not")
		assertEquals(40 * (39 / 8000.0 / 10 + 1 / 750.0) + Math.exp(-200 / 2250.0) - Math.exp(-240 / 2250.0),
			trip.getExpected(), 1e-6);
		// every player rolls their own shard and pet
		assertEquals(240 / 750.0, row(s, "Zalcano shard").getExpected(), 1e-9);
		assertEquals(240 / 2250.0, row(s, "Smolcano").getExpected(), 1e-9);
	}

	@Test
	public void yamaKillsBeforeCountingUseTheSoloDuoSetting()
	{
		// all earlier kills as duo: half a kill each
		RaidSettings duo = new RaidSettings()
		{
			public double uniqueChance(String raid)
			{
				return 0;
			}

			public double contribution(String team, int kills)
			{
				return "yama".equals(team) ? 0.5 : 1.0;
			}
		};
		int[] around = {0};
		tracker.setTeamSizeDetector(() -> around[0]);
		tracker.onChatMessage("Your Yama kill count is: <col=ff0000>100</col>.", now);
		BossStats before = tracker.stats("Yama", duo);
		assertEquals(100 * 0.5 / 600.0, row(before, "Oathplate helm").getExpected(), 1e-9);
		// from now on the team is counted: 10 solo kills
		around[0] = 1;
		for (int kc = 101; kc <= 110; kc++)
		{
			tracker.onChatMessage("Your Yama kill count is: <col=ff0000>" + kc + "</col>.", now);
		}
		BossStats after = tracker.stats("Yama", duo);
		assertEquals((100 * 0.5 + 10) / 600.0, row(after, "Oathplate helm").getExpected(), 1e-9);
		// the pet doesn't scale
		assertEquals(110 / 2500.0, row(after, "Yami").getExpected(), 1e-9);
	}

	@Test
	public void averagePurpleChanceOfMeasuredRaids()
	{
		assertTrue(Double.isNaN(tracker.averagePurpleChance("Chambers of Xeric", "Normal")));
	}

	@Test
	public void itemsInTheLogWithoutKillsDontCount()
	{
		// with 0 Hydra kills, a Hydra item in the log came from elsewhere: it says nothing about Hydra
		Map<String, Integer> hydra = new LinkedHashMap<>();
		hydra.put("Hydra's claw", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 0"), hydra);
		BossStats s = tracker.stats("Alchemical Hydra", RAIDS);
		assertEquals(0, s.getKillCount());
		assertEquals(0, s.getAnyRow().getReceived());
		AccountStats account = tracker.accountStats(RAIDS);
		for (RowStats row : account.getRows())
		{
			assertFalse(row.getLabel().equals("Alchemical Hydra"));
		}
		// once there are kills, the log counts again
		tracker.onChatMessage("Your Alchemical Hydra kill count is: <col=ff0000>5</col>.", now);
		assertEquals(1, row(tracker.stats("Alchemical Hydra", RAIDS), "Hydra's claw").getReceived());
	}

	@Test
	public void dragonThrownaxesAndKnivesAreNotHydraUniques()
	{
		// wyrms, drakes and hydras drop them too, so the log total can't be credited to the Hydra
		for (String item : new String[]{"Dragon thrownaxe", "Dragon knife"})
		{
			assertTrue(catalog.getExcludedItems().contains(item));
			assertFalse(catalog.getPools().containsKey(BossCatalog.normalize(item)));
			assertTrue(catalog.isUntracked(item));
		}
		Map<String, Integer> hydra = new LinkedHashMap<>();
		hydra.put("Dragon thrownaxe", 1500);
		hydra.put("Dragon knife", 1500);
		hydra.put("Hydra's claw", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 2000"), hydra);
		BossStats s = tracker.stats("Alchemical Hydra", RAIDS);
		assertFalse(hasRow(s, "Dragon thrownaxe"));
		assertFalse(hasRow(s, "Dragon knife"));
		assertEquals(1, s.getAnyRow().getReceived());
		double expected = s.getAnyRow().getExpected();
		// what's left: the brimstone pieces, tail, leather, claw, heads, jar and the pet
		double rates = 3 / 181.0 + 1 / 513.0 + 1 / 514.0 + 1 / 1001.0 + 1 / 256.0 + 1 / 2000.0;
		assertEquals(2000 * rates + DropMath.chanceAtLeastOne(2000 / 3000.0), expected, 1e-9);
		AccountStats gp = tracker.accountStats(RAIDS, item -> 1_000_000L);
		// more thrownaxes and knives in the log change nothing: not the uniques, not the GP value
		hydra.put("Dragon thrownaxe", 3000);
		hydra.put("Dragon knife", 3000);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 2000"), hydra);
		s = tracker.stats("Alchemical Hydra", RAIDS);
		assertEquals(1, s.getAnyRow().getReceived());
		assertEquals(expected, s.getAnyRow().getExpected(), 1e-12);
		AccountStats gpAfter = tracker.accountStats(RAIDS, item -> 1_000_000L);
		assertEquals(gp.getExpectedValue(), gpAfter.getExpectedValue(), 1e-6);
		assertEquals(gp.getReceivedValue(), gpAfter.getReceivedValue());
		assertTrue(tracker.recentDrops(10).isEmpty());
	}

	@Test
	public void hydraLootThrownaxesAndKnivesAreNotCounted()
	{
		tracker.onChatMessage("Your Alchemical Hydra kill count is: <col=ff0000>10</col>.", now);
		tracker.onLoot("Alchemical Hydra", 1, Collections.singletonList(new Tracker.LootItem("Dragon thrownaxe", 750)), now);
		tracker.onChatMessage("Your Alchemical Hydra kill count is: <col=ff0000>11</col>.", now + 1);
		tracker.onLoot("Alchemical Hydra", 1, Collections.singletonList(new Tracker.LootItem("Dragon knife", 600)), now + 1);
		assertEquals(0, tracker.stats("Alchemical Hydra", RAIDS).getAnyRow().getReceived());
		assertTrue(tracker.recentDrops(10).isEmpty());
		for (BossDays d : tracker.recentDays(1, now))
		{
			assertEquals(0, d.getUniques());
		}
		assertNull(tracker.lastUnique().item);
		// a real Hydra unique still counts
		tracker.onLoot("Alchemical Hydra", 1, Collections.singletonList(new Tracker.LootItem("Hydra's claw", 1)), now);
		assertEquals(1, tracker.stats("Alchemical Hydra", RAIDS).getAnyRow().getReceived());
		assertEquals("Hydra's claw", tracker.recentDrops(10).get(0).getDrop().getName());
	}

	@Test
	public void savedThrownaxesAndKnivesStayInHistoryButNotInRecent()
	{
		// History saved before they were left out: kept as it is, but not shown as uniques
		tracker.setKills("Alchemical Hydra", savedKills("[{\"k\":5,\"t\":" + now + ",\"d\":[{\"i\":\"Dragon thrownaxe\"}]},"
			+ "{\"k\":9,\"t\":" + (now + 1) + ",\"d\":[{\"i\":\"Dragon knife\"},{\"i\":\"Hydra's claw\"}]}]"));
		List<RecentDrop> recent = tracker.recentDrops(10);
		assertEquals(1, recent.size());
		assertEquals("Hydra's claw", recent.get(0).getDrop().getName());
		assertEquals("Hydra's claw", tracker.lastUnique().item);
		List<HistoryRow> history = tracker.history("Alchemical Hydra");
		assertEquals(2, history.size());
		assertEquals(2, history.get(0).getKill().getDrops().size());
		assertEquals("Dragon knife", history.get(0).getKill().getDrops().get(0).getName());
		assertEquals("Dragon thrownaxe", history.get(1).getKill().getDrops().get(0).getName());
	}

	@Test
	public void savedPooledDropsStayInRecent()
	{
		// a dragon axe the Kings dropped when it was still one of their rows: a real drop with
		// a real date, so it stays in Recent now that the axe is pooled with Wintertodt
		assertFalse(catalog.isUntracked("Dragon axe"));
		assertTrue(catalog.getPools().containsKey("dragon axe"));
		tracker.setKills("Dagannoth Kings", savedKills(
			"[{\"k\":300,\"p\":120,\"m\":\"Dagannoth Rex\",\"t\":" + now + ",\"d\":[{\"i\":\"Dragon axe\",\"s\":40}]}]"));
		List<RecentDrop> recent = tracker.recentDrops(10);
		assertEquals(1, recent.size());
		assertEquals("Dagannoth Kings", recent.get(0).getBoss());
		assertEquals("Dragon axe", recent.get(0).getDrop().getName());
		assertEquals(now, recent.get(0).getKill().getTime());
	}

	@Test
	public void onlyItemsFlaggedClogSharedAreUntracked()
	{
		assertTrue(catalog.isUntracked("Dragon thrownaxe"));
		assertTrue(catalog.isUntracked("dragon knife"));
		assertTrue(catalog.isUntracked("Dragon 2h sword"));
		// pooled items and normal rows are still tracked
		assertFalse(catalog.isUntracked("Dragon axe"));
		assertFalse(catalog.isUntracked("Dragon pickaxe"));
		assertFalse(catalog.isUntracked("Hydra's claw"));
		assertFalse(catalog.isUntracked("Tome of fire (empty)"));
	}

	private static List<KillEntry> savedKills(String json)
	{
		return new Gson().fromJson(json, new com.google.gson.reflect.TypeToken<List<KillEntry>>()
		{
		}.getType());
	}

	@Test
	public void recentKeepsItemsSavedUnderAnotherNameOfTheirRow()
	{
		// a Tome of fire (empty) is saved under that name; it's still the Tome of fire row
		tracker.onRollBaseline("Wintertodt", 10, 0);
		tracker.onLoot("Reward cart (Wintertodt)", 1, Collections.singletonList(new Tracker.LootItem("Tome of fire (empty)", 1)), now);
		List<RecentDrop> recent = tracker.recentDrops(10);
		assertEquals(1, recent.size());
		assertEquals("Tome of fire (empty)", recent.get(0).getDrop().getName());
	}

	@Test
	public void sharedItemsShowOnEachBossThatDropsThem()
	{
		Map<String, Integer> skotizo = new LinkedHashMap<>();
		skotizo.put("Uncut onyx", 1);
		tracker.onClogPage("Skotizo", Collections.singletonList("Skotizo kills: 100"), skotizo);
		List<SharedPool> forZulrah = tracker.sharedPoolsFor("Zulrah", RAIDS);
		assertEquals(1, forZulrah.size());
		SharedPool onyx = forZulrah.get(0);
		assertTrue(onyx.isLogKnown());
		assertFalse(onyx.isComplete());
		// Zulrah's kills aren't known yet, so only Skotizo's count for now
		assertEquals(100 / 1000.0, onyx.getExpected(), 1e-9);
		assertEquals(Collections.singletonList("Zulrah"), onyx.getMissingPages());
		// a dragon pickaxe page was never opened: how many you have is unknown
		for (SharedPool p : tracker.sharedPoolsFor("Kalphite Queen", RAIDS))
		{
			assertFalse(p.getLabel(), p.isLogKnown());
		}
	}

	@Test
	public void grotesqueGuardiansRollTwice()
	{
		BossDefinition gg = catalog.byName("Grotesque Guardians");
		assertEquals(1 / 500.0, Tracker.effectiveRate(gg, findDrop(gg, "Black tourmaline core"), RAIDS), 1e-12);
		assertEquals(1 / 250.0, Tracker.effectiveRate(gg, findDrop(gg, "Granite gloves"), RAIDS), 1e-12);
	}

	private SharedPool pool(String label)
	{
		for (SharedPool p : tracker.sharedPools(RAIDS))
		{
			if (p.getLabel().equals(label))
			{
				return p;
			}
		}
		throw new AssertionError("no pool " + label);
	}

	private static DropDefinition findDrop(BossDefinition boss, String name)
	{
		for (DropDefinition d : boss.getDrops())
		{
			if (d.getName().equals(name))
			{
				return d;
			}
		}
		throw new AssertionError(name);
	}

	@Test
	public void petsAndReadScrollsCountOnce()
	{
		Map<String, Integer> vorkath = new LinkedHashMap<>();
		vorkath.put("Vorki", 1);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 9000"), vorkath);
		BossStats stats = tracker.stats("Vorkath", RAIDS);
		RowStats vorki = row(stats, "Vorki");
		assertEquals(3.0, vorki.getExpected(), 1e-9);           // shown as 3 expected...
		// ...but having the pet at 9000 kc is roughly average, not dry
		assertTrue(vorki.getLuck() > 0.45 && vorki.getLuck() < 0.55);
		double withoutPet = stats.getAnyRow().getExpected() - (1 - Math.exp(-3.0));
		assertEquals(9000 * (1 / 50.0 + 1 / 1000.0 + 1 / 3000.0 + 1 / 5000.0) + (1 - 1 / 50.0), withoutPet, 1e-9);

		tracker.onChatMessage("Your Royal Titan kill count is: <col=ff0000>600</col>.", now);
		BossStats titans = tracker.stats("Royal Titans", RAIDS);
		assertEquals(1 - Math.exp(-4.0), DropMath.chanceAtLeastOne(row(titans, "Deadeye prayer scroll").getExpected()), 1e-9);
	}

	@Test
	public void titanScrollsAreSeparateAtHalfTheTitanRate()
	{
		tracker.onChatMessage("Your Royal Titan kill count is: <col=ff0000>150</col>.", now);
		BossStats s = tracker.stats("Royal Titans", RAIDS);
		// 1/75 from its own titan, looting both titans equally: 1/150 per kill each
		assertEquals(150 / 150.0, row(s, "Deadeye prayer scroll").getExpected(), 1e-9);
		assertEquals(150 / 150.0, row(s, "Mystic vigour prayer scroll").getExpected(), 1e-9);
		assertTrue(row(s, "Deadeye prayer scroll").isOnce());
	}

	@Test
	public void cappedLuckMatchesOnceItems()
	{
		// a cap of 1 is the same as "have it or not"
		assertEquals(DropMath.chanceAtLeastOne(2.0), DropMath.expectedCapped(2.0, 1), 1e-12);
		double chance = DropMath.chanceAtLeastOne(2.0);
		// having it when 86% of players do is average, not lucky (tie-aware)
		assertEquals(0.5, DropMath.luckCapped(1, 2.0, 1), 1e-12);
		// not having it: only the 14% without one are this dry or drier
		assertEquals(1 - chance, DropMath.luckCapped(0, 2.0, 1), 1e-12);
		// E[min(X, 2)] = P(X >= 1) + P(X >= 2)
		double p0 = Math.exp(-2.0);
		double p1 = 2.0 * p0;
		assertEquals((1 - p0) + (1 - p0 - p1), DropMath.expectedCapped(2.0, 2), 1e-12);
	}

	@Test
	public void verdicts()
	{
		assertEquals("Very dry", DropMath.verdict(0.05));
		assertEquals("Dry", DropMath.verdict(0.2));
		assertEquals("On rate", DropMath.verdict(0.5));
		assertEquals("Spooned", DropMath.verdict(0.8));
		assertEquals("Very spooned", DropMath.verdict(0.95));
		assertEquals("", DropMath.verdict(Double.NaN));
	}

	@Test
	public void chestBossesUseTheCollectionLogMessage()
	{
		tracker.onChatMessage("Your Obor kill count is: <col=ff0000>50</col>.", now);
		assertEquals("Obor", tracker.onChatMessage("New item added to your collection log: Hill giant club", now + 3000));
		BossStats stats = tracker.stats("Obor", RAIDS);
		assertEquals(50 / 118.0, row(stats, "Hill giant club").getExpected(), 1e-12);
		assertEquals(1, row(stats, "Hill giant club").getReceived());
	}

	@Test
	public void raidModesFromCollectionLogLabels()
	{
		tracker.onClogPage("Chambers of Xeric", Arrays.asList("Chambers of Xeric completions: 100", "Chambers of Xeric (CM) completions: 20"),
			new LinkedHashMap<>());
		BossStats cox = tracker.stats("Chambers of Xeric", RAIDS);
		assertEquals(100, (int) cox.getPartCounts().get("Normal"));
		assertEquals(20, (int) cox.getPartCounts().get("Challenge Mode"));

		tracker.onClogPage("Tombs of Amascut", Arrays.asList("Tombs of Amascut (Entry) completions: 7",
			"Tombs of Amascut completions: 30", "Tombs of Amascut (Expert) completions: 12"), new LinkedHashMap<>());
		BossStats toa = tracker.stats("Tombs of Amascut", RAIDS);
		assertEquals(7, (int) toa.getPartCounts().get("Entry Mode"));
		assertEquals(30, (int) toa.getPartCounts().get("Normal"));
		assertEquals(12, (int) toa.getPartCounts().get("Expert Mode"));
	}

	@Test
	public void royalTitansScaleWithDuoKills()
	{
		tracker.onChatMessage("Your Royal Titan kill count is: <col=ff0000>400</col>.", now);
		RaidSettings mixed = new RaidSettings()
		{
			@Override
			public double uniqueChance(String raid)
			{
				return 0;
			}

			@Override
			public double contribution(String team, int kills)
			{
				// 100 duo kills out of 400
				return (kills - 100 + 0.5 * 100) / kills;
			}
		};
		BossStats solo = tracker.stats("Royal Titans", RAIDS);
		BossStats split = tracker.stats("Royal Titans", mixed);
		assertEquals(400 / 75.0, row(solo, "Element staff crowns").getExpected(), 1e-12);
		assertEquals(350 / 75.0, row(split, "Element staff crowns").getExpected(), 1e-12);
		// the pet doesn't scale with damage
		assertEquals(400 / 3000.0, row(split, "Bran").getExpected(), 1e-12);
	}

	@Test
	public void historyKeepsKillsAndDrops()
	{
		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Vorkath's head", 2);
		items.put("Dragonbone necklace", 0);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 100"), items);
		vorkathKc(101);
		// kills before On Rate aren't in the history
		assertEquals(1, tracker.killsSeen("Vorkath"));
		for (int kc = 102; kc <= 140; kc++)
		{
			tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>" + kc + "</col>.", now + kc);
		}
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Dragonbone necklace", 1)), now + 140);
		assertEquals(40, tracker.killsSeen("Vorkath"));
		// heads came before On Rate too: only a minimum gap is known
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now + 140);
		KillEntry.DropEntry head = tracker.history("Vorkath").get(0).getKill().getDrops().get(1);
		assertTrue(head.isAtLeast());
		assertFalse(head.isFirst());
		assertNull(head.getMultiple());

		List<HistoryRow> history = tracker.history("Vorkath");
		assertEquals(140, history.get(0).getKill().getKc());
		assertEquals(101, history.get(history.size() - 1).getKill().getKc());
		KillEntry.DropEntry necklace = history.get(0).getKill().getDrops().get(0);
		assertEquals("Dragonbone necklace", necklace.getName());
		// never had one before: the whole kill count, and it's the first
		assertTrue(necklace.isFirst());
		assertEquals(140, (int) necklace.getSince());
		assertEquals(140 / 1000.0, necklace.getMultiple(), 1e-9);
		assertTrue(history.get(1).getKill().getDrops().isEmpty());
	}

	@Test
	public void recentDropsAreNewestFirstAcrossBosses()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>50</col>.", now);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>500</col>.", now + 60_000);
		tracker.onLoot("Zulrah", 1, Collections.singletonList(new Tracker.LootItem("Tanzanite fang", 1)), now + 60_000);

		List<RecentDrop> recent = tracker.recentDrops(10);
		assertEquals(2, recent.size());
		assertEquals("Zulrah", recent.get(0).getBoss());
		assertEquals("Tanzanite fang", recent.get(0).getDrop().getName());
		assertEquals(500, recent.get(0).getKill().getKc());
		assertEquals("Vorkath", recent.get(1).getBoss());
		assertEquals(1, tracker.recentDrops(1).size());
	}

	@Test
	public void historyKeepsTheLastKills()
	{
		for (int kc = 1; kc <= Tracker.MAX_KILLS + 50; kc++)
		{
			vorkathKc(kc);
		}
		assertEquals(Tracker.MAX_KILLS, tracker.killsSeen("Vorkath"));
		List<HistoryRow> history = tracker.history("Vorkath");
		assertEquals(Tracker.MAX_KILLS + 50, history.get(0).getKill().getKc());
		assertEquals(51, history.get(history.size() - 1).getKill().getKc());
	}

	@Test
	public void raidHistoryHasPointsAndChance()
	{
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now - 1_800_000);
		tracker.onRaidRecord(RaidRecord.cox(43_380, 43_380, 1), now + 1000);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>101</col>.", now + 2000);
		tracker.onChatMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>5</col>.", now + 900_000);

		List<HistoryRow> history = tracker.history("Chambers of Xeric");
		HistoryRow cm = history.get(0);
		assertEquals("Challenge Mode", cm.getKill().getPart());
		assertEquals(5, (int) cm.getKill().getPartKc());
		assertNull(cm.getRaid());
		HistoryRow normal = history.get(1);
		assertEquals(101, (int) normal.getKill().getPartKc());
		assertEquals(43_380, normal.getRaid().getPersonalPoints());
		assertEquals(0.05, normal.getPurpleChance(), 1e-9);
	}

	private void vorkathKc(int kc)
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>" + kc + "</col>.", now);
	}

	@Test
	public void dryStreaksAndDropMessages()
	{
		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Vorkath's head", 7);
		items.put("Dragonbone necklace", 0);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 350"), items);
		vorkathKc(351);
		tracker.drainNotes();

		BossStats stats = tracker.stats("Vorkath", RAIDS);
		// never had a necklace: the streak is the whole kill count
		assertEquals(351, (int) row(stats, "Dragonbone necklace").getStreak());
		assertTrue(row(stats, "Dragonbone necklace").isStreakExact());
		// had heads before tracking: only a minimum is known
		assertEquals(1, (int) row(stats, "Vorkath's head").getStreak());
		assertFalse(row(stats, "Vorkath's head").isStreakExact());

		for (int kc = 352; kc <= 380; kc++)
		{
			vorkathKc(kc);
		}
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		List<Note> notes = tracker.drainNotes();
		assertEquals(1, notes.size());
		assertEquals(Note.Kind.DROP, notes.get(0).getKind());
		assertEquals("Vorkath's head after at least 30 kills.", notes.get(0).getText());

		for (int kc = 381; kc <= 420; kc++)
		{
			vorkathKc(kc);
		}
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		notes = tracker.drainNotes();
		assertEquals("Vorkath's head after 40 kills (0.8x the rate).", notes.get(notes.size() - 1).getText());
		stats = tracker.stats("Vorkath", RAIDS);
		assertEquals(0, (int) row(stats, "Vorkath's head").getStreak());
		assertTrue(row(stats, "Vorkath's head").isStreakExact());

		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Dragonbone necklace", 1)), now);
		notes = tracker.drainNotes();
		assertEquals("Your first Dragonbone necklace, after 420 kills (0.4x the rate).", notes.get(0).getText());
	}

	@Test
	public void milestonesAreAnnouncedOncePerLevel()
	{
		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Vorkath's head", 7);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 100"), items);
		vorkathKc(100);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		tracker.drainNotes();

		List<String> milestones = new ArrayList<>();
		for (int kc = 101; kc <= 210; kc++)
		{
			vorkathKc(kc);
			for (Note n : tracker.drainNotes())
			{
				if (n.getKind() == Note.Kind.MILESTONE && n.getText().startsWith("Vorkath's head"))
				{
					milestones.add(kc + " " + n.getText());
				}
			}
		}
		assertEquals(Arrays.asList(
			"150 Vorkath's head: 50 kills without one, 1x the drop rate.",
			"200 Vorkath's head: 100 kills without one, 2x the drop rate."), milestones);
	}

	@Test
	public void bigJumpsStayQuiet()
	{
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 5000"), new LinkedHashMap<>());
		vorkathKc(10);
		vorkathKc(5000);
		vorkathKc(5001);
		for (Note n : tracker.drainNotes())
		{
			assertFalse(n.toString(), n.getKind() == Note.Kind.MILESTONE);
		}
	}

	@Test
	public void manyItemsAtOnceBecomeOneMessage()
	{
		tracker.onClogPage("Barrows Chests", Collections.singletonList("Barrows Chests opened: 340"), new LinkedHashMap<>());
		List<Note> notes = new ArrayList<>();
		for (int kc = 340; kc <= 351; kc++)
		{
			tracker.onChatMessage("Your Barrows chest count is: <col=ff0000>" + kc + "</col>.", now);
			notes.addAll(tracker.drainNotes());
		}
		assertEquals(1, notes.size());
		assertEquals("Barrows: 24 items passed 1x the drop rate dry: Ahrim's hood, Ahrim's robetop, Ahrim's robeskirt and 21 more.",
			notes.get(0).getText());
	}

	@Test
	public void moonsOfPerilCountsLunarChests()
	{
		tracker.onChatMessage("Your Lunar Chest count is: <col=ff0000>236</col>.", now);
		BossStats stats = tracker.stats("Moons of Peril", RAIDS);
		assertEquals(236, stats.getKillCount());
		assertEquals(236 / 224.0, row(stats, "Eclipse atlatl").getExpected(), 1e-12);
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
	public void leftOutItemsDontCountButStayListed()
	{
		Gson gson = new Gson();
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>50</col>.", now);
		double before = tracker.stats("Vorkath", RAIDS).getAnyRow().getExpected();
		tracker.setLeftOut("Vorkath", "Jar of decay", true);
		assertTrue(tracker.isLeftOut("Vorkath", "jar of decay"));
		BossStats s = tracker.stats("Vorkath", RAIDS);
		// gone from the total, still a row of its own
		assertEquals(before - 50 / 3000.0, s.getAnyRow().getExpected(), 1e-9);
		assertEquals(50 / 3000.0, row(s, "Jar of decay").getExpected(), 1e-9);
		// and from the account's Items tab
		for (com.expecteddrops.stats.ItemHighlight h : tracker.accountStats(RAIDS).getItems())
		{
			assertFalse("Jar of decay".equals(h.getRow().getLabel()));
		}
		// it's saved with the rest
		Tracker restored = new Tracker(catalog);
		restored.setState(gson.fromJson(gson.toJson(tracker.getState()), TrackerState.class));
		assertTrue(restored.isLeftOut("Vorkath", "Jar of decay"));
		// and can count again
		tracker.setLeftOut("Vorkath", "Jar of decay", false);
		assertEquals(before, tracker.stats("Vorkath", RAIDS).getAnyRow().getExpected(), 1e-9);
	}

	@Test
	public void anyRowAddsUpEachItemAsItCounts()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>49</col>.", now);
		RowStats any = tracker.stats("Vorkath", RAIDS).getAnyRow();
		// head, necklace, jar and the skeletal visage (the draconic one is shared with KBD),
		// plus the pet as "have it or not"
		double plain = 49 * (1 / 50.0 + 1 / 1000.0 + 1 / 3000.0 + 1 / 5000.0);
		double pet = 49 / 3000.0;
		assertEquals(plain + (1 - Math.exp(-pet)), any.getExpected(), 1e-9);
		// with none, the luck is the share of players who have none too: none of anything
		assertEquals(Math.exp(-plain - pet), any.getLuck(), 1e-12);
		assertEquals(1 - Math.exp(-plain - pet), any.getChanceAtLeastOne(), 1e-12);

		// the 50th kill's head roll is a sure one: + 1 - 1/50, and nobody has none any more
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>50</col>.", now);
		any = tracker.stats("Vorkath", RAIDS).getAnyRow();
		double lambda = 50 * (1 / 50.0 + 1 / 1000.0 + 1 / 3000.0 + 1 / 5000.0) + (1 - 1 / 50.0) + (1 - Math.exp(-50 / 3000.0));
		assertEquals(lambda, any.getExpected(), 1e-9);
		assertEquals(0, any.getLuck(), 1e-12);
		assertEquals(1, any.getChanceAtLeastOne(), 1e-12);
	}

	@Test
	public void anyRowWithoutCapsIsStillPoisson()
	{
		// Hespori has one unique and no pet: the total is that item's Poisson
		tracker.onChatMessage("Your Hespori kill count is: <col=ff0000>70</col>.", now);
		RowStats any = tracker.stats("Hespori", RAIDS).getAnyRow();
		assertEquals(DropMath.luck(0, 2.0), any.getLuck(), 1e-12);
		assertEquals(DropMath.chanceAtLeastOne(2.0), any.getChanceAtLeastOne(), 1e-12);
	}

	@Test
	public void petOnlyPageAgreesWithItsPet()
	{
		// TzKal-Zuk's only unique is the pet (1/100): at 300 kills without it, the page's
		// verdict is the pet's, not a Poisson of the 0.95 pets that can count
		tracker.onChatMessage("Your TzKal-Zuk kill count is: <col=ff0000>300</col>.", now);
		BossStats s = tracker.stats("TzKal-Zuk", RAIDS);
		RowStats pet = row(s, "Jal-nib-rek");
		RowStats any = s.getAnyRow();
		assertEquals(1 - Math.exp(-3), any.getExpected(), 1e-12);
		assertEquals(Math.exp(-3), pet.getLuck(), 1e-12);
		assertEquals(pet.getLuck(), any.getLuck(), 1e-12);
		assertEquals("Very dry", DropMath.verdict(any.getLuck()));
		assertEquals(pet.getChanceAtLeastOne(), any.getChanceAtLeastOne(), 1e-12);
		assertEquals(3.0, any.getUncappedExpected(), 1e-12);
	}

	@Test
	public void accountTotalAddsUpPetsAsHaveItOrNot()
	{
		tracker.onClogPage("The Inferno", Collections.singletonList("TzKal-Zuk kills: 300"), new LinkedHashMap<>());
		tracker.onClogPage("The Fight Caves", Collections.singletonList("TzTok-Jad kills: 200"), new LinkedHashMap<>());
		AccountStats account = tracker.accountStats(RAIDS);
		RowStats total = account.getTotal();
		assertEquals((1 - Math.exp(-3)) + (1 - Math.exp(-1)), total.getExpected(), 1e-9);
		// no pet from either: as rare as missing both
		assertEquals(Math.exp(-3) * Math.exp(-1), total.getLuck(), 1e-12);
		// the boss rows in the overview judge each boss like its page does
		for (RowStats bossRow : account.getRows())
		{
			assertEquals(tracker.stats(bossRow.getLabel(), RAIDS).getAnyRow().getLuck(), bossRow.getLuck(), 1e-12);
		}
	}

	@Test
	public void vorkathsHeadIsCertainFromTheFiftiethKill()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>49</col>.", now);
		RowStats head = row(tracker.stats("Vorkath", RAIDS), "Vorkath's head");
		assertEquals(1 - Math.exp(-49 / 50.0), head.getChanceAtLeastOne(), 1e-12);

		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>50</col>.", now);
		head = row(tracker.stats("Vorkath", RAIDS), "Vorkath's head");
		assertEquals(50 / 50.0 + 1 - 1 / 50.0, head.getExpected(), 1e-12);
		assertEquals(1, head.getChanceAtLeastOne(), 1e-12);
		// one sure head plus 49 rolls at 1/50: with none you'd be the only one
		assertEquals(0, head.getLuck(), 1e-12);
	}

	@Test
	public void sharedPetIsHaveItOrNot()
	{
		// Chaos Elemental 1/300: 300 kills is one pet expected, and the pool keeps it as that
		tracker.onChatMessage("Your Chaos Elemental kill count is: <col=ff0000>300</col>.", now);
		SharedPool pet = pool("Pet chaos elemental");
		assertTrue(pet.isOnce());
		assertEquals(1.0, pet.getExpected(), 1e-12);
		assertEquals(1 - Math.exp(-1), pet.getCountedExpected(), 1e-12);
		RowStats row = pet.toRow();
		assertEquals(1, row.getCap());
		// without it: the 37% who don't have it either, like any pet row, not "On rate"
		assertEquals(Math.exp(-1), row.getLuck(), 1e-12);
		assertEquals(1 - Math.exp(-1), row.getChanceAtLeastOne(), 1e-12);
	}

	private static RowStats row(BossStats stats, String label)
	{
		return stats.getRows().stream().filter(r -> r.getLabel().equals(label)).findFirst()
			.orElseThrow(() -> new AssertionError("no row " + label));
	}

	private static boolean hasRow(BossStats stats, String label)
	{
		return stats.getRows().stream().anyMatch(r -> r.getLabel().equals(label));
	}

	@Test
	public void madAngelIsTracked()
	{
		tracker.onChatMessage("Your Mad Angel kill count is: <col=ff0000>254</col>.", now);
		BossStats s = tracker.stats("The Mad Angel", RAIDS);
		assertNotNull(s);
		assertEquals(254, s.getKillCount());
		// Hallowfell 1/127 + Jar of Light 1/1000 count towards Any; the teleports don't
		double any = 254 / 127.0 + 254 / 1000.0 + DropMath.chanceAtLeastOne(254 / 2000.0);
		assertEquals(any, s.getAnyRow().getExpected(), 1e-9);
	}

	@Test
	public void coxPointsReplaceTheSettingForThatRaid()
	{
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now - 1_800_000);

		// points first, then the completion message (raid 101: 43,380 points solo = 5%)
		tracker.onRaidRecord(RaidRecord.cox(43_380, 43_380, 1), now + 1000);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>101</col>.", now + 2000);
		// completion message first, then the points (raid 102: 8,676 of 17,352 team points = 1%)
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>102</col>.", now + 600_000);
		tracker.onRaidRecord(RaidRecord.cox(8_676, 17_352, 2), now + 601_000);

		assertEquals(2, tracker.measuredRaids("Chambers of Xeric", "Normal"));
		List<RaidRecord> saved = tracker.raidRecords("Chambers of Xeric");
		assertEquals(101, saved.get(0).getKc());
		assertEquals(2, saved.get(1).getTeamSize());
		assertEquals(0.05, Tracker.purpleChance(saved.get(0)), 1e-9);
		assertEquals(0.01, Tracker.purpleChance(saved.get(1)), 1e-9);

		BossStats after = tracker.stats("Chambers of Xeric", RAIDS);
		double purples = 0;
		for (RowStats row : after.getRows())
		{
			if (!row.isOnce())
			{
				purples += row.getExpected();
			}
		}
		// 100 raids at the 3.5% setting, plus the two measured raids
		assertEquals(100 * 0.035 + 0.06, purples, 1e-6);
	}

	@Test
	public void raidDataWithoutACompletionIsIgnored()
	{
		tracker.onRaidRecord(RaidRecord.cox(30_000, 30_000, 1), now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>10</col>.", now + 120_000);
		assertEquals(0, tracker.measuredRaids("Chambers of Xeric", "Normal"));
	}

	@Test
	public void challengeModePointsGoToChallengeMode()
	{
		tracker.onChatMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>5</col>.", now);
		tracker.onRaidRecord(RaidRecord.cox(60_000, 60_000, 1), now + 1000);
		assertEquals(1, tracker.measuredRaids("Chambers of Xeric", "Challenge Mode"));
		assertEquals(0, tracker.measuredRaids("Chambers of Xeric", "Normal"));
	}

	@Test
	public void coxPointsDontGoToTombs()
	{
		tracker.onChatMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>5</col>.", now);
		tracker.onRaidRecord(RaidRecord.cox(30_000, 30_000, 1), now + 1000);
		assertTrue(tracker.raidRecords("Tombs of Amascut").isEmpty());
	}

	@Test
	public void toaUsesEstimatedPointsAndRaidLevel()
	{
		tracker.onChatMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>50</col>.", now);
		// solo 400 raid level: 1% per 3,700 points, so 18,500 points is 5%
		tracker.onRaidRecord(RaidRecord.toa(18_000, 500, 0, 1, 400), now + 1000);
		assertEquals(1, tracker.measuredRaids("Tombs of Amascut", "Expert Mode"));
		assertEquals(0.05, Tracker.purpleChance(tracker.raidRecords("Tombs of Amascut").get(0)), 1e-9);

		BossStats toa = tracker.stats("Tombs of Amascut", RAIDS);
		RowStats fang = null;
		for (RowStats row : toa.getRows())
		{
			if (row.getLabel().startsWith("Osmumten"))
			{
				fang = row;
			}
		}
		assertNotNull(fang);
		// 49 raids at the 5% Expert setting (fang 7/24) plus this one at 5%, at raid level 400
		// where the fang is 4/19
		assertEquals(49 * 0.05 * 7 / 24.0 + 0.05 * 4 / 19.0, fang.getExpected(), 1e-9);
	}

	@Test
	public void tripExpectedIsWhatTheRaidAdded()
	{
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your completed Tombs of Amascut count is: <col=ff0000>39</col>.", now);
		tracker.onChatMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>134</col>.", now);
		long later = now + 2 * 86_400_000L;
		double before = tracker.stats("Tombs of Amascut", RAIDS).getAnyRow().getExpected();
		// a new trip: the raid counts at the setting first, then the points come in and change it
		tracker.onChatMessage("Your completed Tombs of Amascut count is: <col=ff0000>40</col>.", later);
		tracker.onRaidRecord(RaidRecord.toa(18_000, 500, 0, 1, 400), later + 1000);
		double after = tracker.stats("Tombs of Amascut", RAIDS).getAnyRow().getExpected();
		TripStats trip = tracker.tripStats();
		assertEquals(1, trip.getKills());
		assertEquals(after - before, trip.getExpected(), 1e-9);
		// Recent's "Today" uses the points too, not the setting the raid was first counted at
		assertEquals(after - before, tracker.recentDays(1, later + 2000).get(0).getExpected(), 1e-5);
	}

	@Test
	public void recentFillsInKillsFromBeforeDaysWereCounted()
	{
		tracker.setSettings(RAIDS);
		long today = System.currentTimeMillis();
		long earlier = today - 86_400_000L;
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", earlier);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>101</col>.", earlier + 60_000);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), earlier + 61_000);
		double perKill = tracker.stats("Vorkath", RAIDS).getAnyRow().getRate();
		// as if those kills were saved by a version that didn't count days: only History has them
		tracker.forgetDaysForTest();
		List<BossDays> week = tracker.recentDays(7, today);
		assertEquals(2, week.get(0).getKills());
		assertEquals(1, week.get(0).getUniques());
		assertEquals(2 * perKill, week.get(0).getExpected(), 1e-6);
		// asking again doesn't count them twice, and a new kill adds just itself
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>102</col>.", today);
		week = tracker.recentDays(7, today);
		assertEquals(3, week.get(0).getKills());
		assertEquals(3 * perKill, week.get(0).getExpected(), 1e-4);
	}

	@Test
	public void recentFillsInAgainAfterSwitchingAccounts()
	{
		tracker.setSettings(RAIDS);
		long today = System.currentTimeMillis();
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", today - 60_000);
		Gson gson = new Gson();
		SaveData data = tracker.saveData(gson::toJson);
		assertEquals(1, tracker.recentDays(1, today).get(0).getKills());
		// another account loads (without day counts, as from an earlier version) in the same session
		com.google.gson.JsonObject json = gson.fromJson(data.progress, com.google.gson.JsonObject.class);
		json.remove("days");
		tracker.setState(gson.fromJson(json, TrackerState.class));
		tracker.setKills("Vorkath", gson.fromJson(data.kills.get("Vorkath"), new com.google.gson.reflect.TypeToken<List<KillEntry>>()
		{
		}.getType()));
		assertEquals(1, tracker.recentDays(1, today).get(0).getKills());
	}

	@Test
	public void lowRaidLevelsKeepFewUniques()
	{
		tracker.onChatMessage("Your completed Tombs of Amascut: Entry Mode count is: <col=ff0000>1</col>.", now);
		tracker.onRaidRecord(RaidRecord.toa(10_000, 0, 0, 1, 100), now + 1000);
		BossStats toa = tracker.stats("Tombs of Amascut", RAIDS);
		double chance = RaidChance.toa(10_000, 0, 0, 1, 100);
		for (RowStats row : toa.getRows())
		{
			if (row.getLabel().startsWith("Osmumten"))
			{
				// level 100 is above the fang's minimum of 50
				assertEquals(chance * 7 / 24.0, row.getExpected(), 1e-12);
			}
			if (row.getLabel().startsWith("Tumeken's shadow"))
			{
				// below 150 a rolled shadow is only kept 1 time in 50
				assertEquals(chance / 24.0 / 50, row.getExpected(), 1e-12);
			}
			if (row.getLabel().equals("Tumeken's guardian"))
			{
				// the pet has its own formula: 1% per (350,000 - 700 x level) points
				assertEquals(10_000 / (350_000 - 700 * 100.0) / 100, row.getExpected(), 1e-12);
			}
		}
	}

	@Test
	public void exactToaPointsReplaceTheEstimate()
	{
		tracker.onChatMessage("Your completed Tombs of Amascut count is: <col=ff0000>3</col>.", now);
		// On Rate's estimate first, then the game's own number a moment later
		tracker.onRaidRecord(RaidRecord.toa(10_000, 900, 0, 1, 300), now + 1000);
		assertEquals("Tombs of Amascut", tracker.onToaPoints(15_000, now + 2000));
		RaidRecord r = tracker.raidRecords("Tombs of Amascut").get(0);
		assertEquals(15_000, r.getPersonalPoints());
		assertEquals(0, r.getPartyPoints());
		assertFalse(r.isEstimated());
		assertEquals(RaidChance.toa(15_000, 0, 0, 1, 300), Tracker.purpleChance(r), 1e-12);
		// too late (another raid's points) changes nothing
		assertNull(tracker.onToaPoints(20_000, now + 10 * 60_000));
	}

	@Test
	public void vorkathsHeadIsGuaranteedOnThe50thKill()
	{
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 49"), Collections.singletonMap("Vorkath's head", 0));
		assertEquals(49 / 50.0, headExpected(), 1e-9);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 350"), Collections.singletonMap("Vorkath's head", 6));
		// 349 kills at 1/50 and the 50th kill a sure one
		assertEquals(349 / 50.0 + 1, headExpected(), 1e-9);
	}

	private double headExpected()
	{
		for (RowStats row : tracker.stats("Vorkath", RAIDS).getRows())
		{
			if (row.getLabel().equals("Vorkath's head"))
			{
				return row.getExpected();
			}
		}
		throw new AssertionError("no head row");
	}

	@Test
	public void toaPluginTotalReplacesTheEstimate()
	{
		tracker.onChatMessage("Your completed Tombs of Amascut count is: <col=ff0000>3</col>.", now);
		tracker.onRaidRecord(RaidRecord.toa(15_000, 900, 0, 3, 300), now + 1000);
		tracker.onPartyPoints(16_000, 48_000, now + 5000);
		RaidRecord r = tracker.raidRecords("Tombs of Amascut").get(0);
		assertEquals(16_000, r.getPersonalPoints());
		assertEquals(48_000, r.getTeamPoints());
		assertEquals(RaidChance.toaTeamChance(48_000, 300) / 3, Tracker.purpleChance(r), 1e-9);
	}

	@Test
	public void tobSplitsTheTeamChanceOverTheTeamSize()
	{
		// settings: 11% for the team, usually a 4-man
		RaidSettings tob = new RaidSettings()
		{
			@Override
			public double uniqueChance(String raid)
			{
				return "tob".equals(raid) ? 0.11 / 4 : 0;
			}

			@Override
			public int teamSize(String raid)
			{
				return raid.startsWith("tob") ? 4 : 1;
			}
		};
		tracker.onChatMessage("Your completed Theatre of Blood count is: <col=ff0000>20</col>.", now);
		// the board of the 20th raid says it was a duo, MVP, one death
		tracker.onRaidRecord(RaidRecord.tob(2, 1, 3, true), now + 120_000);
		List<RaidRecord> saved = tracker.raidRecords("Theatre of Blood");
		assertEquals(1, saved.size());
		assertEquals(2, saved.get(0).getTeamSize());
		assertEquals(Boolean.TRUE, saved.get(0).getMvp());
		assertEquals(0, tracker.measuredRaids("Theatre of Blood", "Normal"));
		double purples = 0;
		for (RowStats row : tracker.stats("Theatre of Blood", tob).getRows())
		{
			if (!row.isOnce())
			{
				purples += row.getExpected();
			}
		}
		// 19 raids as a 4-man and the duo raid
		assertEquals(19 * 0.11 / 4 + 0.11 / 2, purples, 1e-9);
	}

	@Test
	public void raidRecordsAreSavedApart()
	{
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>7</col>.", now);
		tracker.onRaidRecord(RaidRecord.cox(25_000, 50_000, 2), now + 1000);
		Gson gson = new Gson();
		SaveData data = tracker.saveData(gson::toJson);
		// the raids have their own key; the main data doesn't carry them
		assertTrue(data.raids.containsKey("Chambers of Xeric"));
		assertFalse(data.progress, data.progress.contains("50000"));
		// a kill elsewhere doesn't write the raids again
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>10</col>.", now + 60_000);
		assertTrue(tracker.saveData(gson::toJson).raids.isEmpty());

		Tracker loaded = new Tracker(catalog);
		loaded.setState(gson.fromJson(data.progress, TrackerState.class));
		loaded.setRaids("Chambers of Xeric", gson.fromJson(data.raids.get("Chambers of Xeric"),
			new com.google.gson.reflect.TypeToken<List<RaidRecord>>()
			{
			}.getType()));
		assertEquals(1, loaded.measuredRaids("Chambers of Xeric", "Normal"));
		assertEquals(25_000, loaded.raidRecords("Chambers of Xeric").get(0).getPersonalPoints());
	}

	@Test
	public void raidsSavedInTheMainDataMoveOut()
	{
		// how earlier versions saved them: inside the main data
		String old = "{\"bosses\":{\"Chambers of Xeric\":{\"chatKc\":{\"chambers of xeric\":7},"
			+ "\"raids\":[{\"family\":\"cox\",\"part\":\"Normal\",\"kc\":7,\"personalPoints\":25000,"
			+ "\"teamPoints\":50000,\"teamSize\":2}]}}}";
		Gson gson = new Gson();
		Tracker loaded = new Tracker(catalog);
		loaded.setState(gson.fromJson(old, TrackerState.class));
		assertEquals(1, loaded.raidRecords("Chambers of Xeric").size());
		SaveData data = loaded.saveData(gson::toJson);
		assertTrue(data.raids.get("Chambers of Xeric").contains("25000"));
		assertFalse(data.progress, data.progress.contains("25000"));
	}

	@Test
	public void historyKeepsOnePointPerDay()
	{
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 99"), Collections.singletonMap("Vorkath's head", 2));
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", now);
		assertTrue(tracker.recordHistory(20000, RAIDS));
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>101</col>.", now);
		assertTrue(tracker.recordHistory(20000, RAIDS));
		assertEquals(1, tracker.history().size());
		assertEquals(tracker.accountStats(RAIDS).getTotal().getExpected(), tracker.history().get(0).getExpected(), 1e-9);
		assertTrue(tracker.recordHistory(20001, RAIDS));
		assertEquals(2, tracker.history().size());
		// survives saving and loading
		Gson gson = new Gson();
		Tracker loaded = new Tracker(catalog);
		loaded.setState(gson.fromJson(gson.toJson(tracker.getState()), TrackerState.class));
		assertEquals(2, loaded.history().size());
	}

	@Test
	public void noHistoryWithoutKills()
	{
		assertFalse(tracker.recordHistory(20000, RAIDS));
		assertTrue(tracker.history().isEmpty());
	}

	@Test
	public void raidsTakingLongerThanHalfAnHourStayOneTrip()
	{
		tracker.setSettings(RAIDS);
		long raid = 40 * 60_000L;
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>10</col>.", now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>11</col>.", now + raid);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>12</col>.", now + 2 * raid);
		assertEquals(3, tracker.tripStats().getKills());
		// a normal boss still ends a trip after half an hour
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", now + 3 * raid);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>101</col>.", now + 4 * raid);
		assertEquals(1, tracker.tripStats().getKills());
	}

	@Test
	public void normalBossTripEndsAfterHalfAnHourIdle()
	{
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", now);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>101</col>.", now + 30 * 60_000L);
		// 30 minutes is still the same trip
		assertEquals(2, tracker.tripStats().getKills());
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>102</col>.", now + 61 * 60_000L);
		// 31 idle minutes: a new trip that starts with this kill
		assertEquals(1, tracker.tripStats().getKills());
	}

	@Test
	public void raidTripSurvivesAnHourAndAHalfGap()
	{
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>10</col>.", now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>11</col>.", now + 90 * 60_000L);
		assertEquals(2, tracker.tripStats().getKills());
		// past two hours it's a new trip after all
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>12</col>.", now + 211 * 60_000L);
		assertEquals(1, tracker.tripStats().getKills());
	}

	@Test
	public void tripValueUsesTheTripsOwnDelveLevels()
	{
		tracker.setSettings(RAIDS);
		java.util.function.ToLongFunction<String> prices = name -> name.startsWith("Dom") ? 0 : 10_000_000L;
		// deep delves first: they're in the boss's average, but they're another trip
		tracker.onChatMessage("Delve level: 8+ (12) duration: <col=ff0000>2:05.00</col>", now);
		tracker.onChatMessage("Delve level: 8+ (13) duration: <col=ff0000>2:01.00</col>", now + 60_000);
		tracker.resetTrip();
		// then only level 1, where nothing drops: worth nothing
		for (int i = 0; i < 5; i++)
		{
			tracker.onChatMessage("Delve level: 1 duration: <col=ff0000>0:50.00</col>", now + (2 + i) * 60_000L);
		}
		assertEquals(5, tracker.tripStats().getKills());
		assertEquals(0, tracker.tripValue(prices), 1e-9);
		// a level 4 delve adds what level 4 gives: cloth, eye and treads at 1/1,350 each
		tracker.onChatMessage("Delve level: 4 duration: <col=ff0000>1:30.00</col>", now + 8 * 60_000L);
		assertEquals(3 * 10_000_000.0 / 1350, tracker.tripValue(prices), 1e-6);
	}

	@Test
	public void temporossCountsRewardRolls()
	{
		tracker.setSettings(RAIDS);
		BossDefinition tempoross = catalog.byName("Tempoross");
		assertEquals("rolls", Tracker.unit(tempoross));
		// the log page knows subdues: 4 rolls each until the permits say more
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Fish barrel", 0);
		page.put("Tackle box", 1);
		tracker.onClogPage("Tempoross", Collections.singletonList("Tempoross kills: 100"), page);
		assertEquals(400, tracker.stats("Tempoross", RAIDS).getKillCount());
		// the game's numbers: 100 subdues, 20 permits not spent yet
		assertEquals("Tempoross", tracker.onRollBaseline("Tempoross", 100, 20));
		assertEquals(380, tracker.stats("Tempoross", RAIDS).getKillCount());
		// set once only
		assertNull(tracker.onRollBaseline("Tempoross", 120, 0));
		// spending 5 permits is 5 rolls, a trip of 5
		assertEquals("Tempoross", tracker.onRewardRolls("Tempoross", 5, now));
		assertEquals(385, tracker.stats("Tempoross", RAIDS).getKillCount());
		assertEquals(5, tracker.tripStats().getKills());
		// the loot itself isn't a kill
		tracker.onLoot("Reward pool (Tempoross)", 1, Collections.singletonList(new Tracker.LootItem("Fish barrel", 1)), now);
		BossStats s = tracker.stats("Tempoross", RAIDS);
		assertEquals(385, s.getKillCount());
		assertEquals(1, row(s, "Fish barrel").getReceived());
		assertEquals(385 / 1600.0, row(s, "Big harpoonfish").getExpected(), 1e-9);
	}

	@Test
	public void wintertodtCountsCartRolls()
	{
		tracker.setSettings(RAIDS);
		assertEquals("Wintertodt", tracker.onRollBaseline("Wintertodt", 200, 30));
		// 200 subdues at 3 rolls, 30 rewards still in the cart
		assertEquals(570, tracker.stats("Wintertodt", RAIDS).getKillCount());
		tracker.onRewardRolls("Wintertodt", 30, now);
		BossStats s = tracker.stats("Wintertodt", RAIDS);
		assertEquals(600, s.getKillCount());
		// the outfit is one row: one roll at 1/150 for whichever piece you have fewest of
		assertEquals(600 / 150.0, row(s, "Pyromancer outfit pieces").getExpected(), 1e-9);
		// any piece counts for it
		tracker.onLoot("Reward cart (Wintertodt)", 1, Collections.singletonList(new Tracker.LootItem("Pyromancer boots", 1)), now);
		assertEquals(1, row(tracker.stats("Wintertodt", RAIDS), "Pyromancer outfit pieces").getReceived());
		// Tempoross's numbers are its own
		assertNull(tracker.onRewardRolls("Vorkath", 5, now));
	}

	@Test
	public void colosseumCountsRunsByTheirClaimedWave()
	{
		tracker.setSettings(RAIDS);
		BossDefinition colosseum = catalog.byName("Fortis Colosseum");
		DropDefinition echo = colosseum.getDrops().get(0);
		assertEquals("runs", Tracker.unit(colosseum));
		// the wiki's chances per wave add up: 1/124 on wave 4 ... 1/12 on wave 12 for any unique
		double wave12 = 0;
		for (DropDefinition d : colosseum.getDrops())
		{
			if (!d.isPet())
			{
				wave12 += Tracker.waveRate(d, 12) - Tracker.waveRate(d, 11);
			}
		}
		assertEquals(1 / 12.0, wave12, 1e-9);
		assertEquals(1 / 310.0, Tracker.waveRate(echo, 4), 1e-12);
		assertEquals(0, Tracker.waveRate(echo, 3), 1e-12);
		// 10 completions from before On Rate: wave 12 claims
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Echo crystal", 1);
		tracker.onClogPage("Fortis Colosseum", Collections.singletonList("Sol Heredit kills: 10"), page);
		BossStats s = tracker.stats("Fortis Colosseum", RAIDS);
		assertEquals(10, s.getKillCount());
		assertEquals(10 * Tracker.waveRate(echo, 12), row(s, "Echo crystal").getExpected(), 1e-9);
		// a run claimed after wave 7 counts the waves up to 7
		tracker.onWaveCompleted(7);
		tracker.onLoot("Fortis Colosseum", 1, Collections.singletonList(new Tracker.LootItem("Sunfire splinters", 500)), now);
		s = tracker.stats("Fortis Colosseum", RAIDS);
		assertEquals(11, s.getKillCount());
		assertEquals(10 * Tracker.waveRate(echo, 12) + Tracker.waveRate(echo, 7), row(s, "Echo crystal").getExpected(), 1e-9);
		// a full run: the kill count message (not a run by itself), then the claim with the quiver
		tracker.onChatMessage("Your Sol Heredit kill count is: <col=ff0000>11</col>.", now + 60_000);
		assertEquals(11, tracker.stats("Fortis Colosseum", RAIDS).getKillCount());
		tracker.onLoot("Fortis Colosseum", 1, Arrays.asList(new Tracker.LootItem("Dizana's quiver (uncharged)", 1),
			new Tracker.LootItem("Echo crystal", 1)), now + 61_000);
		s = tracker.stats("Fortis Colosseum", RAIDS);
		assertEquals(12, s.getKillCount());
		// one trip of two runs
		assertEquals(2, tracker.tripStats().getKills());
		// reading the log page again (11 completions now) doesn't count the new one twice
		tracker.onClogPage("Fortis Colosseum", Collections.singletonList("Sol Heredit kills: 11"), page);
		assertEquals(12, tracker.stats("Fortis Colosseum", RAIDS).getKillCount());
		assertEquals(2, row(s, "Echo crystal").getReceived());
		// a claim without a known wave isn't counted as a run
		tracker.onLoot("Fortis Colosseum", 1, Collections.singletonList(new Tracker.LootItem("Sunfire splinters", 100)), now + 120_000);
		assertEquals(12, tracker.stats("Fortis Colosseum", RAIDS).getKillCount());
	}

	@Test
	public void aPetSeveralBossesDropCountsOnceAmongThePets()
	{
		tracker.setSettings(RAIDS);
		Map<String, Integer> ele = new LinkedHashMap<>();
		ele.put("Dragon pickaxe", 0);
		ele.put("Pet chaos elemental", 0);
		tracker.onClogPage("Chaos Elemental", Collections.singletonList("Chaos Elemental kills: 300"), ele);
		Map<String, Integer> fanatic = new LinkedHashMap<>();
		fanatic.put("Odium shard 1", 0);
		fanatic.put("Pet chaos elemental", 0);
		tracker.onClogPage("Chaos Fanatic", Collections.singletonList("Chaos Fanatic kills: 1000"), fanatic);
		List<com.expecteddrops.stats.ItemHighlight> pets = new ArrayList<>();
		for (com.expecteddrops.stats.ItemHighlight h : tracker.accountStats(RAIDS).getItems())
		{
			if (h.isPet() && h.getRow().getLabel().equals("Pet chaos elemental"))
			{
				pets.add(h);
			}
		}
		// once, not zero times (it used to be left out) and not twice
		assertEquals(1, pets.size());
		RowStats pet = pets.get(0).getRow();
		// judged on both bosses' kills: 300 at 1/300 and 1000 at 1/1,000
		assertEquals(2.0, pet.getExpected(), 1e-9);
		assertEquals(1 - Math.exp(-2.0), pet.getChanceAtLeastOne(), 1e-9);
		assertFalse(pet.isComplete());
		// once it's in the log, you have it
		ele.put("Pet chaos elemental", 1);
		tracker.onClogPage("Chaos Elemental", Collections.singletonList("Chaos Elemental kills: 300"), ele);
		for (com.expecteddrops.stats.ItemHighlight h : tracker.accountStats(RAIDS).getItems())
		{
			if (h.getRow().getLabel().equals("Pet chaos elemental"))
			{
				assertTrue(h.getRow().isComplete());
			}
		}
	}

	@Test
	public void aTripEndsWithOneSummaryLine()
	{
		tracker.setSettings(RAIDS);
		for (int kc = 100; kc < 110; kc++)
		{
			tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>" + kc + "</col>.", now + (kc - 100) * 60_000L);
		}
		tracker.drainNotes();
		// a kill at another boss ends the Vorkath trip
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>50</col>.", now + 20 * 60_000L);
		List<String> trips = new ArrayList<>();
		for (Note n : tracker.drainNotes())
		{
			if (n.getKind() == Note.Kind.TRIP)
			{
				trips.add(n.getText());
			}
		}
		assertEquals(1, trips.size());
		assertTrue(trips.get(0), trips.get(0).startsWith("Trip at Vorkath: 10 kills, no uniques, "));
		// too short a trip says nothing
		tracker.resetTrip();
		for (Note n : tracker.drainNotes())
		{
			assertFalse(n.getKind() == Note.Kind.TRIP);
		}
	}

	@Test
	public void recordsAndTheLastUnique()
	{
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", now);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		assertEquals(null, row(tracker.stats("Vorkath", RAIDS), "Vorkath's head").getFastest());
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>130</col>.", now + 60_000);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now + 60_000);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>140</col>.", now + 120_000);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now + 120_000);
		RowStats head = row(tracker.stats("Vorkath", RAIDS), "Vorkath's head");
		assertEquals(Integer.valueOf(10), head.getFastest());
		assertEquals(Integer.valueOf(30), head.getLongest());
		Tracker.LastUnique last = tracker.lastUnique();
		assertEquals("Vorkath's head", last.item);
		assertEquals(now + 120_000, last.time);
	}

	@Test
	public void tripCountsKillsAndDrops()
	{
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", now);
		TripStats trip = tracker.tripStats();
		assertEquals("Vorkath", trip.getBoss());
		assertEquals(1, trip.getKills());
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>101</col>.", now + 60_000);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>102</col>.", now + 120_000);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now + 121_000);
		trip = tracker.tripStats();
		assertEquals(3, trip.getKills());
		assertEquals(1, trip.getReceived());
		assertEquals(Collections.singletonList("Vorkath's head"), trip.getDrops());
		double perKill = tracker.stats("Vorkath", RAIDS).getAnyRow().getRate();
		assertEquals(3 * perKill, trip.getExpected(), 0.01);
		assertEquals(60.0, trip.getKillsPerHour(), 1e-9);

		// another boss starts a new trip
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>50</col>.", now + 180_000);
		assertEquals("Zulrah", tracker.tripStats().getBoss());
		assertEquals(1, tracker.tripStats().getKills());
		// and so does a long break
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>51</col>.", now + 180_000 + 31 * 60_000);
		assertEquals(1, tracker.tripStats().getKills());
		tracker.resetTrip();
		assertNull(tracker.tripStats());
	}

	@Test
	public void checklistKnowsWhichBossesYouKilled()
	{
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>120</col>.", now);
		boolean zulrah = false;
		for (com.expecteddrops.tracking.SyncStatus status : tracker.syncChecklist())
		{
			if (status.getBosses().contains("Zulrah"))
			{
				assertTrue(status.isKilled());
				assertFalse(status.isSynced());
				zulrah = true;
			}
			else if (status.getBosses().contains("Vorkath"))
			{
				assertFalse(status.isKilled());
			}
		}
		assertTrue(zulrah);
	}

	@Test
	public void petsShowTheLuckiestAndDriestFive()
	{
		// eight bosses with their pet, eight without
		String[][] bosses = {{"Vorkath", "Vorki"}, {"Zulrah", "Pet snakeling"}, {"Cerberus", "Hellpuppy"}, {"Kraken", "Pet kraken"},
			{"Scorpia", "Scorpia's offspring"}, {"Sarachnis", "Sraracha"}, {"Kalphite Queen", "Kalphite princess"}, {"Skotizo", "Skotos"},
			{"Giant Mole", "Baby mole"}, {"King Black Dragon", "Prince black dragon"}, {"Chaos Fanatic", "Pet chaos elemental"},
			{"Abyssal Sire", "Abyssal orphan"}, {"Alchemical Hydra", "Ikkle hydra"}, {"Phantom Muspah", "Muphin"},
			{"Scurrius", "Scurry"}, {"Amoxliatl", "Moxi"}};
		for (int i = 0; i < bosses.length; i++)
		{
			tracker.onClogPage(bosses[i][0], Collections.singletonList(bosses[i][0] + " kills: " + (100 + i * 50)),
				Collections.singletonMap(bosses[i][1], i < 8 ? 1 : 0));
		}
		List<com.expecteddrops.stats.ItemHighlight> pets = ExpectedDropsPanel.topItems(tracker.accountStats(RAIDS), ExpectedDropsPanel.TOP_PETS);
		assertEquals(10, pets.size());
		for (int i = 0; i < 10; i++)
		{
			// first the pets you have, then the ones you don't
			assertEquals(i < 5, pets.get(i).getRow().isComplete());
		}
		// luckiest first: got it with the fewest expected
		assertTrue(pets.get(0).getRow().getExpected() <= pets.get(4).getRow().getExpected());
		// driest first: the one you'd most likely have by now
		assertTrue(pets.get(5).getRow().getChanceAtLeastOne() >= pets.get(9).getRow().getChanceAtLeastOne());
	}

	@Test
	public void eachBossCountsInItsOwnUnit()
	{
		assertEquals("delves", Tracker.unit(catalog.byName("Doom of Mokhaiotl")));
		assertEquals("delve", Tracker.unitOne(catalog.byName("Doom of Mokhaiotl")));
		assertEquals("raids", Tracker.unit(catalog.byName("Chambers of Xeric")));
		assertEquals("kills", Tracker.unit(catalog.byName("Vorkath")));
	}

	@Test
	public void daysCountKillsUniquesAndExpected()
	{
		long today = System.currentTimeMillis();
		long lastWeek = today - 3 * 86_400_000L;
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>10</col>.", lastWeek);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>11</col>.", today);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), today);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>5</col>.", today);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>6</col>.", today);

		List<com.expecteddrops.tracking.BossDays> day = tracker.recentDays(1, today);
		assertNull(day.get(0).getBoss());
		assertEquals(3, day.get(0).getKills());
		assertEquals(1, day.get(0).getUniques());
		// most kills first
		assertEquals("Zulrah", day.get(1).getBoss());
		assertEquals(2, day.get(1).getKills());
		BossStats vorkath = tracker.stats("Vorkath", RAIDS);
		assertEquals(vorkath.getAnyRow().getExpected() / vorkath.getKillCount(), day.get(2).getExpected(), 1e-5);
		// the hover says which unique it was
		assertEquals(Collections.singletonList("Vorkath's head"), day.get(2).getUniqueNames());
		assertTrue(day.get(1).getUniqueNames().isEmpty());

		List<com.expecteddrops.tracking.BossDays> week = tracker.recentDays(7, today);
		assertEquals(4, week.get(0).getKills());
		assertEquals(2, week.get(1).getKills());
		assertEquals("Vorkath", week.get(1).getBoss());

		// saved and loaded with the rest
		Gson gson = new Gson();
		Tracker loaded = new Tracker(catalog);
		loaded.setState(gson.fromJson(gson.toJson(tracker.getState()), TrackerState.class));
		assertEquals(4, loaded.recentDays(7, today).get(0).getKills());
	}

	@Test
	public void teamKillsCountTheirShareToday()
	{
		int[] team = {4};
		tracker.setTeamSizeDetector(() -> team[0]);
		long today = System.currentTimeMillis();
		tracker.onChatMessage("Your Nex kill count is: <col=ff0000>10</col>.", today);
		BossDefinition nex = catalog.byName("Nex");
		double solo = 0;
		double pet = 0;
		for (DropDefinition d : nex.getDrops())
		{
			if (d.countsTowardAny())
			{
				double rate = Tracker.effectiveRate(nex, d, RAIDS);
				if (d.isPet())
				{
					pet += rate;
				}
				else
				{
					solo += rate;
				}
			}
		}
		tracker.setSettings(RAIDS);
		assertEquals(solo / 4 + pet, tracker.recentDays(1, today).get(1).getExpected(), 1e-9);
	}

	@Test
	public void accountRowsKnowTheirDryStreak()
	{
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 100"), Collections.singletonMap("Vorkath's head", 0));
		RowStats row = tracker.accountStats(RAIDS).getRows().get(0);
		assertEquals(100, (int) row.getStreak());
		assertTrue(row.getStreakMultiple() > 1);
	}

	@Test
	public void historyIsSavedPerBoss()
	{
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>10</col>.", now);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>11</col>.", now + 60_000);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>5</col>.", now + 120_000);

		Gson gson = new Gson();
		Map<String, List<KillEntry>> changed = tracker.changedKills();
		assertEquals(new HashSet<>(Arrays.asList("Vorkath", "Zulrah")), changed.keySet());
		// nothing new since: nothing to save
		assertTrue(tracker.changedKills().isEmpty());
		// the main data doesn't carry the history
		String main = gson.toJson(tracker.getState());
		assertFalse(main.contains("Vorkath's head\""));
		String vorkath = gson.toJson(changed.get("Vorkath"));
		// a kill without uniques is just its kill count and time
		assertTrue(vorkath, vorkath.contains("{\"k\":11,\"t\":"));

		Tracker loaded = new Tracker(catalog);
		loaded.setState(gson.fromJson(main, TrackerState.class));
		loaded.setKills("Vorkath", gson.fromJson(vorkath, new com.google.gson.reflect.TypeToken<List<KillEntry>>()
		{
		}.getType()));
		List<HistoryRow> history = loaded.history("Vorkath");
		assertEquals(2, history.size());
		assertEquals(11, history.get(0).getKill().getKc());
		assertEquals("Vorkath's head", history.get(1).getKill().getDrops().get(0).getName());
		assertTrue(loaded.changedKills().isEmpty());

		// a reset clears the saved history too
		loaded.reset("Vorkath");
		assertTrue(loaded.changedKills().get("Vorkath").isEmpty());
	}

	@Test
	public void stackedItemsCountAsDrops()
	{
		// Ardeaglais teleports drop 2 at a time: the log's 6 are 3 drops
		Map<String, Integer> angel = new LinkedHashMap<>();
		angel.put("Ardeaglais teleport", 6);
		tracker.onClogPage("The Mad Angel", Collections.singletonList("Mad Angel kills: 100"), angel);
		assertEquals(3, row(tracker.stats("The Mad Angel", RAIDS), "Ardeaglais teleport").getReceived());

		Map<String, Integer> hydra = new LinkedHashMap<>();
		hydra.put("Dragon thrownaxe", 1480);
		hydra.put("Dragon knife", 1480);
		hydra.put("Hydra's claw", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 2000"), hydra);
		BossStats s = tracker.stats("Alchemical Hydra", RAIDS);
		// thrownaxes and knives are left out (other monsters drop them too)
		assertFalse(hasRow(s, "Dragon thrownaxe"));
		assertFalse(hasRow(s, "Dragon knife"));
		assertEquals(1, row(s, "Hydra's claw").getReceived());
		assertEquals(2000 / 256.0, row(s, "Alchemical hydra heads").getExpected(), 1e-9);
	}

	@Test
	public void araxxorHalberdPiecesAreOneIn600Each()
	{
		tracker.onChatMessage("Your Araxxor kill count is: <col=ff0000>600</col>.", now);
		assertEquals(1.0, row(tracker.stats("Araxxor", RAIDS), "Noxious blade").getExpected(), 1e-9);
	}

	@Test
	public void toaHighRaidLevelsShiftTheWeights()
	{
		tracker.onChatMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>1</col>.", now);
		// raid level 400: fang 4, Lightbearer 5, ward 3, Masori 2+2+2, shadow 1 = 19
		tracker.onRaidRecord(RaidRecord.toa(18_500, 0, 0, 1, 400), now + 1000);
		BossStats toa = tracker.stats("Tombs of Amascut", RAIDS);
		double chance = RaidChance.toa(18_500, 0, 0, 1, 400);
		assertEquals(chance * 4 / 19, row(toa, "Osmumten's fang").getExpected(), 1e-12);
		assertEquals(chance * 5 / 19, row(toa, "Lightbearer").getExpected(), 1e-12);
		assertEquals(chance * 1 / 19, row(toa, "Tumeken's shadow (uncharged)").getExpected(), 1e-12);
	}

	/** Per-kill rate of a pooled item for one boss (pooled drops are not on the boss's own page). */
	private double poolRate(String bossName, String pool)
	{
		for (BossCatalog.PoolSource src : catalog.getPools().get(pool))
		{
			if (src.getBoss().getName().equals(bossName))
			{
				return Tracker.effectiveRate(src.getBoss(), src.getDrop(), RAIDS);
			}
		}
		throw new AssertionError(bossName + " does not drop " + pool);
	}

	@Test
	public void sharedPoolOnlyAddsUpTheBossesYouKnow()
	{
		// Uncut onyx: Skotizo 1/1000 and Zulrah 1/512 (different rates), only Zulrah's kills are known
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>1000</col>.", now);
		SharedPool onyx = pool("Uncut onyx");
		double zulrahRate = poolRate("Zulrah", "uncut onyx");
		assertEquals(1000 * zulrahRate, onyx.getExpected(), 1e-9);
		assertEquals(Collections.singletonList("Skotizo"), onyx.getMissingPages());
		assertFalse(onyx.isComplete());
		// no log page showing the item yet: the total is unknown, not zero
		assertFalse(onyx.isLogKnown());
		assertEquals(0, onyx.getReceived());

		// Skotizo's page, with none of the item: the total is known and both bosses count
		tracker.onClogPage("Skotizo", Collections.singletonList("Skotizo kills: 100"), Collections.singletonMap("Uncut onyx", 0));
		onyx = pool("Uncut onyx");
		double skotizoRate = poolRate("Skotizo", "uncut onyx");
		assertTrue(onyx.isComplete());
		assertTrue(onyx.isLogKnown());
		assertEquals(0, onyx.getReceived());
		assertEquals(1000 * zulrahRate + 100 * skotizoRate, onyx.getExpected(), 1e-9);
		assertTrue(skotizoRate != zulrahRate);
	}

	@Test
	public void sharedItemWithNoKillsAnywhereIsNotAnExpectedDrop()
	{
		SharedPool onyx = pool("Uncut onyx");
		assertEquals(0, onyx.getExpected(), 0);
		assertEquals(2, onyx.getMissingPages().size());
		assertFalse(onyx.isComplete());
	}

	@Test
	public void unknownTeamSizeCountsEveryGroupKillAsSolo()
	{
		// a detector that can't tell (0) is the same as one that always sees you alone
		Tracker unknown = new Tracker(catalog);
		unknown.setTeamSizeDetector(() -> 0);
		Tracker solo = new Tracker(catalog);
		solo.setTeamSizeDetector(() -> 1);
		Tracker none = new Tracker(catalog);
		none.setTeamSizeDetector(null);
		for (Tracker t : new Tracker[]{unknown, solo, none})
		{
			t.onChatMessage("Your Nex kill count is: <col=ff0000>100</col>.", now);
			for (int kc = 101; kc <= 110; kc++)
			{
				t.onChatMessage("Your Nex kill count is: <col=ff0000>" + kc + "</col>.", now);
			}
		}
		assertTrue(unknown.teamKills("Nex").isEmpty());
		assertTrue(none.teamKills("Nex").isEmpty());
		double expected = row(solo.stats("Nex", RAIDS), "Zaryte vambraces").getExpected();
		assertTrue(expected > 0);
		assertEquals(expected, row(unknown.stats("Nex", RAIDS), "Zaryte vambraces").getExpected(), 1e-12);
		assertEquals(expected, row(none.stats("Nex", RAIDS), "Zaryte vambraces").getExpected(), 1e-12);
	}

	@Test
	public void tripBoundaryIsInclusive()
	{
		// half an hour for a normal boss, two hours for a raid (Tracker keeps these package-private)
		final long NORMAL_TRIP = 30 * 60_000L;
		final long LONG_TRIP = 120 * 60_000L;
		tracker.setSettings(RAIDS);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>100</col>.", now);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>101</col>.", now + NORMAL_TRIP);
		assertEquals(2, tracker.tripStats().getKills());
		// one millisecond later than half an hour after the last kill starts a new trip
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>102</col>.", now + 2 * NORMAL_TRIP + 1);
		assertEquals(1, tracker.tripStats().getKills());

		Tracker raid = new Tracker(catalog);
		raid.setSettings(RAIDS);
		raid.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>10</col>.", now);
		raid.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>11</col>.", now + LONG_TRIP);
		assertEquals(2, raid.tripStats().getKills());
		raid.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>12</col>.", now + 2 * LONG_TRIP + 1);
		assertEquals(1, raid.tripStats().getKills());
	}

	@Test
	public void switchingAccountsDropsPendingNotes()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>1</col>.", 1000);
		tracker.onLoot("Vorkath", 1, java.util.Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), 1000);
		tracker.setState(null);
		assertTrue(tracker.drainNotes().isEmpty());
	}

	@Test
	public void resetRemovesTheBossFromRecentAndTheTrip()
	{
		long now = System.currentTimeMillis();
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>1</col>.", now);
		tracker.reset("Vorkath");
		assertNull(tracker.tripStats());
		assertNull(tracker.getLastBoss());
		for (com.expecteddrops.tracking.BossDays d : tracker.recentDays(7, now))
		{
			assertTrue(!"Vorkath".equals(d.getBoss()) || d.getKills() == 0);
		}
	}

	// ---------------------------------------------------------------- Remove from History

	private static List<KillEntry> historyJson(String json)
	{
		return new Gson().fromJson(json, new com.google.gson.reflect.TypeToken<List<KillEntry>>()
		{
		}.getType());
	}

	private static int uniquesOn(List<BossDays> days, String boss)
	{
		for (BossDays d : days)
		{
			if (boss == null ? d.getBoss() == null : boss.equals(d.getBoss()))
			{
				return d.getUniques();
			}
		}
		return 0;
	}

	/** The saved uniques of a boss, newest first, as History holds them: {kill, drop}. */
	private List<Object[]> savedUniques(String boss)
	{
		List<Object[]> list = new ArrayList<>();
		for (HistoryRow h : tracker.history(boss))
		{
			for (KillEntry.DropEntry d : h.getKill().getDrops())
			{
				list.add(new Object[]{h.getKill(), d});
			}
		}
		return list;
	}

	@Test
	public void removeFromHistoryRemovesOnlyThatEntry()
	{
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Steel ring", 1);
		tracker.onClogPage("Deranged Archaeologist", Collections.singletonList("Deranged Archaeologist kills: 40"), page);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>10</col>.", now);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), now);
		tracker.onChatMessage("Your Deranged Archaeologist kill count is: <col=ff0000>41</col>.", now + 60_000);
		tracker.onLoot("Deranged archaeologist", 1, Collections.singletonList(new Tracker.LootItem("Steel ring", 1)), now + 60_000);
		for (int kc = 42; kc <= 44; kc++)
		{
			tracker.onChatMessage("Your Deranged Archaeologist kill count is: <col=ff0000>" + kc + "</col>.", now + 60_000 + kc);
		}
		tracker.onChatMessage("Your Deranged Archaeologist kill count is: <col=ff0000>45</col>.", now + 120_000);
		tracker.onLoot("Deranged archaeologist", 1, Collections.singletonList(new Tracker.LootItem("Steel ring", 1)), now + 120_000);

		RowStats ringBefore = row(tracker.stats("Deranged Archaeologist", RAIDS), "Steel ring");
		RowStats anyBefore = tracker.stats("Deranged Archaeologist", RAIDS).getAnyRow();
		int seenBefore = tracker.killsSeen("Deranged Archaeologist");
		List<BossDays> daysBefore = tracker.recentDays(7, now + 120_000);
		assertEquals(2, uniquesOn(daysBefore, "Deranged Archaeologist"));
		assertEquals(3, uniquesOn(daysBefore, null));
		assertEquals(now + 120_000, tracker.lastUnique().time);

		// two Steel rings at the same boss: remove the newest one only
		List<Object[]> rings = savedUniques("Deranged Archaeologist");
		assertEquals(2, rings.size());
		KillEntry kill45 = (KillEntry) rings.get(0)[0];
		assertEquals(45, kill45.getKc());
		tracker.changedKills();
		assertTrue(tracker.removeFromHistory("Deranged Archaeologist", kill45, (KillEntry.DropEntry) rings.get(0)[1]));

		// gone from History and Recent; the other ring and the other boss stay
		List<Object[]> left = savedUniques("Deranged Archaeologist");
		assertEquals(1, left.size());
		assertEquals(41, ((KillEntry) left.get(0)[0]).getKc());
		List<RecentDrop> recent = tracker.recentDrops(10);
		assertEquals(2, recent.size());
		assertEquals(41, recent.get(0).getKill().getKc());
		assertEquals("Vorkath", recent.get(1).getBoss());
		assertEquals(1, savedUniques("Vorkath").size());
		// what's read from History follows: Last unique, and the names in the day's hover
		assertEquals("Steel ring", tracker.lastUnique().item);
		assertEquals(now + 60_000, tracker.lastUnique().time);
		List<BossDays> daysAfter = tracker.recentDays(7, now + 120_000);
		for (BossDays d : daysAfter)
		{
			if ("Deranged Archaeologist".equals(d.getBoss()))
			{
				assertEquals(Collections.singletonList("Steel ring"), d.getUniqueNames());
			}
		}
		// the kill itself stays
		assertEquals(seenBefore, tracker.killsSeen("Deranged Archaeologist"));
		assertTrue(tracker.history("Deranged Archaeologist").get(0).getKill().getDrops().isEmpty());
		assertEquals(45, tracker.history("Deranged Archaeologist").get(0).getKill().getKc());
		// Today and Last 7 days keep their counts: kills, uniques and expected (the day a unique
		// was counted on isn't saved, so a removal can't know which day to take it off)
		assertEquals(2, uniquesOn(daysAfter, "Deranged Archaeologist"));
		assertEquals(3, uniquesOn(daysAfter, null));
		assertEquals(1, uniquesOn(daysAfter, "Vorkath"));
		for (int i = 0; i < daysBefore.size(); i++)
		{
			assertEquals(daysBefore.get(i).getKills(), daysAfter.get(i).getKills());
			assertEquals(daysBefore.get(i).getUniques(), daysAfter.get(i).getUniques());
			assertEquals(daysBefore.get(i).getExpected(), daysAfter.get(i).getExpected(), 1e-12);
		}
		// what isn't built from History doesn't change: received, kill count, expected, dry streak
		BossStats s = tracker.stats("Deranged Archaeologist", RAIDS);
		RowStats ring = row(s, "Steel ring");
		assertEquals(45, s.getKillCount());
		assertEquals(ringBefore.getReceived(), ring.getReceived());
		assertEquals(3, ring.getReceived());
		assertEquals(ringBefore.getExpected(), ring.getExpected(), 1e-12);
		assertEquals(ringBefore.getStreak(), ring.getStreak());
		assertEquals(0, (int) ring.getStreak());
		assertEquals(anyBefore.getReceived(), s.getAnyRow().getReceived());
		assertEquals(anyBefore.getExpected(), s.getAnyRow().getExpected(), 1e-12);
		// saved again with the next save
		assertTrue(tracker.changedKills().containsKey("Deranged Archaeologist"));
		// and it can't be removed twice
		assertFalse(tracker.removeFromHistory("Deranged Archaeologist", kill45, (KillEntry.DropEntry) rings.get(0)[1]));
	}

	@Test
	public void removeFromHistoryTellsIdenticalEntriesApart()
	{
		// a pre-fix collection log sync could put a second, identical ring in the same kill
		tracker.setKills("Deranged Archaeologist", historyJson("[{\"k\":40,\"t\":" + now + ",\"d\":[{\"i\":\"Steel ring\"},{\"i\":\"Steel ring\"}]},"
			+ "{\"k\":41,\"t\":" + (now + 1) + ",\"d\":[{\"i\":\"Steel ring\"}]}]"));
		KillEntry kill40 = tracker.history("Deranged Archaeologist").get(1).getKill();
		KillEntry kill41 = tracker.history("Deranged Archaeologist").get(0).getKill();
		KillEntry.DropEntry first = kill40.getDrops().get(0);
		KillEntry.DropEntry second = kill40.getDrops().get(1);
		assertTrue(tracker.removeFromHistory("Deranged Archaeologist", kill40, second));
		assertEquals(1, kill40.getDrops().size());
		assertTrue(kill40.getDrops().get(0) == first);
		assertEquals(1, kill41.getDrops().size());
		assertEquals(2, tracker.recentDrops(10).size());
		// the last unique of a kill: the kill stays, without uniques
		assertTrue(tracker.removeFromHistory("Deranged Archaeologist", kill41, kill41.getDrops().get(0)));
		assertEquals(2, tracker.killsSeen("Deranged Archaeologist"));
		assertTrue(kill41.getDrops().isEmpty());
		assertEquals(1, tracker.recentDrops(10).size());
		assertTrue(tracker.recentDrops(10).get(0).getDrop() == first);
	}

	@Test
	public void removeFromHistoryOnlyTakesTheShownEntry()
	{
		tracker.setKills("Deranged Archaeologist", historyJson("[{\"k\":40,\"t\":" + now + ",\"d\":[{\"i\":\"Steel ring\"}]}]"));
		KillEntry kill = tracker.history("Deranged Archaeologist").get(0).getKill();
		// an entry that looks the same but isn't the saved one is not removed
		KillEntry lookalike = historyJson("[{\"k\":40,\"t\":" + now + ",\"d\":[{\"i\":\"Steel ring\"}]}]").get(0);
		tracker.changedKills();
		assertFalse(tracker.removeFromHistory("Deranged Archaeologist", lookalike, lookalike.getDrops().get(0)));
		assertFalse(tracker.removeFromHistory("Deranged Archaeologist", kill, lookalike.getDrops().get(0)));
		assertFalse(tracker.removeFromHistory("Vorkath", kill, kill.getDrops().get(0)));
		assertFalse(tracker.removeFromHistory("Deranged Archaeologist", null, null));
		assertEquals(1, tracker.recentDrops(10).size());
		assertTrue(tracker.changedKills().isEmpty());
		// another account loaded since it was shown: nothing happens
		KillEntry.DropEntry ring = kill.getDrops().get(0);
		tracker.setState(null);
		assertFalse(tracker.removeFromHistory("Deranged Archaeologist", kill, ring));
		assertEquals(1, kill.getDrops().size());
	}

	private static long atLocal(java.time.LocalDate day, int hour, int minute)
	{
		return day.atTime(hour, minute).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	/** One boss's uniques on one day, as Today / Last 7 days add them up. */
	private int uniquesOnDay(String boss, java.time.LocalDate day, java.time.LocalDate today)
	{
		long noon = atLocal(today, 12, 0);
		int back = (int) (today.toEpochDay() - day.toEpochDay());
		return uniquesOn(tracker.recentDays(back + 1, noon), boss) - (back == 0 ? 0 : uniquesOn(tracker.recentDays(back, noon), boss));
	}

	@Test
	public void removingALegacyLogArtefactLeavesBothDaysCounts()
	{
		// an older version's log sync put a second ring in the kill from 3 days ago (its kill count
		// hadn't changed) and counted it on the sync day
		java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 20);
		java.time.LocalDate killDay = today.minusDays(3);
		long kill = atLocal(killDay, 18, 0);
		long sync = atLocal(today, 9, 0);
		tracker.setState(new Gson().fromJson("{\"bosses\":{\"Deranged Archaeologist\":{\"received\":{\"steel ring\":2},"
			+ "\"clogKc\":{\"deranged archaeologist kills\":40},\"clogSynced\":true,\"trackingStartKc\":39,\"lastDropKc\":{\"steel ring\":40}}},"
			+ "\"days\":{\"" + killDay.toEpochDay() + "\":{\"Deranged Archaeologist\":{\"k\":1,\"u\":1,\"e\":0.02,\"f\":" + kill + "}},"
			+ "\"" + today.toEpochDay() + "\":{\"Deranged Archaeologist\":{\"k\":0,\"u\":1,\"e\":0,\"f\":" + sync + "}}}}", TrackerState.class));
		tracker.setKills("Deranged Archaeologist", historyJson("[{\"k\":40,\"t\":" + kill
			+ ",\"d\":[{\"i\":\"Steel ring\",\"s\":39,\"f\":true},{\"i\":\"Steel ring\"}]}]"));
		assertEquals(1, uniquesOnDay("Deranged Archaeologist", killDay, today));
		assertEquals(1, uniquesOnDay("Deranged Archaeologist", today, today));
		RowStats before = row(tracker.stats("Deranged Archaeologist", RAIDS), "Steel ring");

		KillEntry k = tracker.history("Deranged Archaeologist").get(0).getKill();
		KillEntry.DropEntry genuine = k.getDrops().get(0);
		assertTrue(tracker.removeFromHistory("Deranged Archaeologist", k, k.getDrops().get(1)));

		// the genuine ring's day isn't touched, and neither is the sync day (it keeps the artefact
		// until it expires)
		assertEquals(1, uniquesOnDay("Deranged Archaeologist", killDay, today));
		assertEquals(1, uniquesOnDay("Deranged Archaeologist", today, today));
		// the genuine ring and the counts stay
		assertEquals(1, k.getDrops().size());
		assertTrue(k.getDrops().get(0) == genuine);
		assertTrue(genuine.isFirst());
		RowStats after = row(tracker.stats("Deranged Archaeologist", RAIDS), "Steel ring");
		assertEquals(2, after.getReceived());
		assertEquals(before.getReceived(), after.getReceived());
		assertEquals(before.getExpected(), after.getExpected(), 1e-12);
		assertEquals(before.getStreak(), after.getStreak());
		assertEquals(40, tracker.stats("Deranged Archaeologist", RAIDS).getKillCount());
	}

	@Test
	public void removingBodyguardLootAfterMidnightLeavesTheDaysCounts()
	{
		// the general's kill before midnight; a bodyguard drops a chestplate after it, which goes
		// with that kill in History but is counted on the new day
		java.time.LocalDate today = java.time.LocalDate.of(2026, 10, 20);
		java.time.LocalDate yesterday = today.minusDays(1);
		tracker.onChatMessage("Your General Graardor kill count is: <col=ff0000>100</col>.", atLocal(yesterday, 23, 50));
		tracker.onLoot("Sergeant Strongstack", 1, Collections.singletonList(new Tracker.LootItem("Bones", 1)), atLocal(today, 0, 5));
		tracker.onLoot("Sergeant Steelwill", 1, Collections.singletonList(new Tracker.LootItem("Bandos chestplate", 1)), atLocal(today, 0, 6));
		KillEntry k = tracker.history("General Graardor").get(0).getKill();
		assertEquals(100, k.getKc());
		assertTrue(k.getTime() < atLocal(today, 0, 0));
		assertEquals(0, uniquesOnDay("General Graardor", yesterday, today));
		assertEquals(1, uniquesOnDay("General Graardor", today, today));
		int received = tracker.stats("General Graardor", RAIDS).getAnyRow().getReceived();

		assertTrue(tracker.removeFromHistory("General Graardor", k, k.getDrops().get(0)));

		assertEquals(0, uniquesOnDay("General Graardor", yesterday, today));
		assertEquals(1, uniquesOnDay("General Graardor", today, today));
		assertTrue(tracker.recentDrops(10).isEmpty());
		assertEquals(received, tracker.stats("General Graardor", RAIDS).getAnyRow().getReceived());
		assertEquals(100, tracker.stats("General Graardor", RAIDS).getKillCount());
	}

	// ---------------------------------------------------------------- uniques saved under another name

	@Test
	public void lastUniqueAndDayNamesKnowItemsSavedUnderAnotherName()
	{
		// a Tome of fire (empty) is saved under that name; it's still the Tome of fire row
		tracker.onRollBaseline("Wintertodt", 10, 0);
		// searching the cart: the roll first, then its loot
		tracker.onRewardRolls("Wintertodt", 1, now);
		tracker.onLoot("Reward cart (Wintertodt)", 1, Collections.singletonList(new Tracker.LootItem("Tome of fire (empty)", 1)), now);
		Tracker.LastUnique last = tracker.lastUnique();
		assertEquals("Tome of fire (empty)", last.item);
		assertEquals("Wintertodt", last.boss);
		assertEquals(now, last.time);
		boolean named = false;
		for (BossDays d : tracker.recentDays(1, now))
		{
			if ("Wintertodt".equals(d.getBoss()))
			{
				assertEquals(Collections.singletonList("Tome of fire (empty)"), d.getUniqueNames());
				assertEquals(1, d.getUniques());
				named = true;
			}
		}
		assertTrue(named);
		// left out of the calculations: not the last unique any more
		tracker.setLeftOut("Wintertodt", "Tome of fire", true);
		assertNull(tracker.lastUnique().item);
	}

	@Test
	public void lastUniqueKnowsItemsSavedInAnotherCase()
	{
		// the game's name can differ in case from the drop data ("Jar of Chemicals")
		tracker.setKills("Alchemical Hydra", new Gson().fromJson("[{\"k\":300,\"t\":" + now + ",\"d\":[{\"i\":\"Jar of Chemicals\"}]}]",
			new com.google.gson.reflect.TypeToken<List<KillEntry>>()
			{
			}.getType()));
		assertEquals("Jar of Chemicals", tracker.lastUnique().item);
		assertEquals("Alchemical Hydra", tracker.lastUnique().boss);
		// an item that isn't a row of the boss still doesn't count
		tracker.setKills("Vorkath", new Gson().fromJson("[{\"k\":5,\"t\":" + (now + 1) + ",\"d\":[{\"i\":\"Dragon thrownaxe\"}]}]",
			new com.google.gson.reflect.TypeToken<List<KillEntry>>()
			{
			}.getType()));
		assertEquals("Jar of Chemicals", tracker.lastUnique().item);
	}
}
