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
import com.expecteddrops.tracking.Note;
import com.expecteddrops.tracking.RaidRecord;
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
		assertEquals(1234 / 50.0, head.getExpected(), 1e-9);
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
		assertEquals(455 / 128.0, row(stats, "Dragon axe").getExpected(), 1e-12);
		// Only the kings drop dragon axes, so the collection log total now counts.
		assertEquals(3, row(stats, "Dragon axe").getReceived());

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

		AccountStats account = tracker.accountStats(RAIDS);
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
	public void sharedItemsUseTheLogTotalForTheAccount()
	{
		Map<String, Integer> graardor = new LinkedHashMap<>();
		graardor.put("Bandos chestplate", 1);
		graardor.put("Godsword shard 1", 2);
		tracker.onClogPage("General Graardor", Collections.singletonList("General Graardor kills: 762"), graardor);
		BossStats boss = tracker.stats("General Graardor", RAIDS);
		// on the boss page the shards can't be credited (they also come from the other GWD bosses)...
		assertEquals(0, row(boss, "Godsword shard 1").getReceived());
		// ...but the account total uses the log total
		AccountStats account = tracker.accountStats(RAIDS, name -> name.startsWith("Godsword shard") ? 10_000_000L : 0L);
		assertEquals(3, account.getTotal().getReceived());
		assertEquals(20_000_000L, account.getReceivedValue());
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
		assertEquals(9000 * (1 / 50.0 + 1 / 1000.0 + 1 / 3000.0 + 2 / 5000.0), withoutPet, 1e-9);

		tracker.onChatMessage("Your Royal Titan kill count is: <col=ff0000>600</col>.", now);
		BossStats titans = tracker.stats("Royal Titans", RAIDS);
		assertEquals(1 - Math.exp(-4.0), DropMath.chanceAtLeastOne(row(titans, "Deadeye prayer scroll").getExpected()), 1e-9);
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
			"150 Vorkath's head: 1x the drop rate dry (50 kills without one).",
			"200 Vorkath's head: 2x the drop rate dry (100 kills without one)."), milestones);
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
	public void anyRowUsesPoisson()
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>50</col>.", now);
		RowStats any = tracker.stats("Vorkath", RAIDS).getAnyRow();
		// head, necklace, jar and the two visages, plus the pet as "have it or not"
		double lambda = 50 * (1 / 50.0 + 1 / 1000.0 + 1 / 3000.0 + 1 / 5000.0 + 1 / 5000.0) + (1 - Math.exp(-50 / 3000.0));
		assertEquals(lambda, any.getExpected(), 1e-9);
		assertEquals(DropMath.luckPercentile(0, lambda), any.getLuck(), 1e-12);
	}

	private static RowStats row(BossStats stats, String label)
	{
		return stats.getRows().stream().filter(r -> r.getLabel().equals(label)).findFirst()
			.orElseThrow(() -> new AssertionError("no row " + label));
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
		// 49 raids at the 5% Expert setting plus this one at 5%, fang is 7/24
		assertEquals(50 * 0.05 * 7 / 24.0, fang.getExpected(), 1e-9);
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
			if (row.getLabel().startsWith("Tumeken"))
			{
				// below 150 a rolled shadow is only kept 1 time in 50
				assertEquals(chance / 24.0 / 50, row.getExpected(), 1e-12);
			}
		}
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
	public void tobBoardIsSavedButUsesTheSetting()
	{
		tracker.onChatMessage("Your completed Theatre of Blood count is: <col=ff0000>20</col>.", now);
		tracker.onRaidRecord(RaidRecord.tob(4, 1, 3, true), now + 120_000);
		List<RaidRecord> saved = tracker.raidRecords("Theatre of Blood");
		assertEquals(1, saved.size());
		assertEquals(4, saved.get(0).getTeamSize());
		assertEquals(Boolean.TRUE, saved.get(0).getMvp());
		assertEquals(0, tracker.measuredRaids("Theatre of Blood", "Normal"));
		assertEquals(20 * 0.027, tracker.stats("Theatre of Blood", RAIDS).getRows().get(0).getExpected() / (8 / 19.0), 1e-9);
	}

	@Test
	public void raidRecordsAreSaved()
	{
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>7</col>.", now);
		tracker.onRaidRecord(RaidRecord.cox(25_000, 50_000, 2), now + 1000);
		Gson gson = new Gson();
		Tracker loaded = new Tracker(catalog);
		loaded.setState(gson.fromJson(gson.toJson(tracker.getState()), TrackerState.class));
		assertEquals(1, loaded.measuredRaids("Chambers of Xeric", "Normal"));
		assertEquals(25_000, loaded.raidRecords("Chambers of Xeric").get(0).getPersonalPoints());
	}

	@Test
	public void historyKeepsOnePointPerDay()
	{
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
}
