package com.onrate.engine;

import com.onrate.engine.data.Bundled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.KillTimes;
import com.onrate.engine.data.LadlorChart;
import com.onrate.engine.stats.ChartTime;
import com.onrate.engine.stats.ChartTimes;
import com.onrate.engine.stats.ItemTime;
import com.onrate.engine.stats.ItemTimes;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/** The Ladlor chart view: the chart's items you don't have, in chart order, with the time for all they need. */
public class ChartTimesTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;
	private BossCatalog catalog;
	private Tracker tracker;
	private final KillTimes web = KillTimes.bundled(new com.google.gson.Gson());

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
	}

	private List<ChartTime> chart()
	{
		return ChartTimes.compute(tracker, web, RAIDS, LadlorChart.bundled(new com.google.gson.Gson()));
	}

	private ChartTime find(String name)
	{
		for (ChartTime t : chart())
		{
			if (t.getName().equals(name))
			{
				return t;
			}
		}
		return null;
	}

	private ItemTime drop(String boss, String icon)
	{
		for (ItemTime t : ItemTimes.compute(tracker, web, RAIDS))
		{
			if (t.getBoss().equals(boss) && t.getIconItem().equals(icon))
			{
				return t;
			}
		}
		throw new AssertionError(boss + " / " + icon);
	}

	/** Puts a boss's collection log page in with these items had once. */
	private void sync(String bossName, int kills, String... have)
	{
		BossDefinition boss = catalog.byName(bossName);
		Map<String, Integer> items = new HashMap<>();
		for (String item : have)
		{
			items.put(item, 1);
		}
		List<String> counters = new ArrayList<>();
		for (String name : boss.getKcNames())
		{
			counters.add(name + " kills: " + kills);
		}
		tracker.onClogPage(boss.getClogPages().get(0), counters, items);
	}

	/** Puts a boss's collection log page in with this many copies of its first item (the crystal seeds) obtained. */
	private void sync(String bossName, int kills, String[] ignored, int seeds)
	{
		BossDefinition boss = catalog.byName(bossName);
		List<String> counters = new ArrayList<>();
		for (String name : boss.getKcNames())
		{
			counters.add(name + " kills: " + kills);
		}
		tracker.onClogPage(boss.getClogPages().get(0), counters, java.util.Collections.singletonMap("Crystal armour seed", seeds));
	}

	@Test
	public void expectedKillsForAllTheDropsOfOneBoss()
	{
		assertEquals(0, ChartTimes.expectedKillsForAll(new double[0]), 1e-12);
		assertEquals(500, ChartTimes.expectedKillsForAll(new double[]{1 / 500.0}), 1e-9);
		// two drops: the first one's wait, the second one's, less the time both were waited for together
		double p = 1 / 100.0;
		double q = 1 / 300.0;
		assertEquals(1 / p + 1 / q - 1 / (p + q), ChartTimes.expectedKillsForAll(new double[]{p, q}), 1e-9);
		// three alike: 1/p * (1 + 1/2 + 1/3)
		assertEquals(600 * (1 + 1 / 2.0 + 1 / 3.0), ChartTimes.expectedKillsForAll(new double[]{1 / 600.0, 1 / 600.0, 1 / 600.0}), 1e-6);
	}

	@Test
	public void theChartIsInChartOrder()
	{
		List<ChartTime> items = chart();
		assertFalse(items.isEmpty());
		int step = 0;
		for (ChartTime t : items)
		{
			assertTrue(t.getName() + " is out of order", t.getStep() >= step);
			step = t.getStep();
		}
		assertEquals("Hallowfell", items.get(0).getName());
		assertEquals("Obligator", items.get(items.size() - 1).getName());
		// the chart's own order inside a step: Ancestral top before hat, hat before bottom
		List<String> names = new ArrayList<>();
		for (ChartTime t : items)
		{
			names.add(t.getName());
		}
		assertTrue(names.indexOf("Ancestral robe top") < names.indexOf("Ancestral hat"));
		assertTrue(names.indexOf("Ancestral hat") < names.indexOf("Ancestral robe bottom"));
		assertTrue(names.indexOf("Voidwaker") < names.indexOf("Necklace of rupture"));
	}

	@Test
	public void everyNeedOfTheChartIsADropOfTheCatalog()
	{
		for (LadlorChart.Item item : LadlorChart.bundled(new com.google.gson.Gson()).getItems())
		{
			for (LadlorChart.Need need : item.getNeeds())
			{
				BossDefinition boss = catalog.byName(need.getBoss());
				assertNotNull(item.getName() + " needs " + need.getBoss(), boss);
				if (need.getDrop() != null)
				{
					assertTrue(item.getName() + " needs " + need.getDrop(),
						boss.getDrops().stream().anyMatch(d -> d.getName().equals(need.getDrop())));
				}
				else
				{
					assertNotNull(need.getKills());
				}
			}
		}
	}

	@Test
	public void dropsAtDifferentBossesAddUp()
	{
		ChartTime voidwaker = find("Voidwaker");
		assertNotNull(voidwaker);
		assertEquals(3, voidwaker.getParts().size());
		double sum = drop("Vet'ion and Calvar'ion", "Voidwaker blade").getSecondsPerDrop()
			+ drop("Venenatis and Spindel", "Voidwaker gem").getSecondsPerDrop()
			+ drop("Callisto and Artio", "Voidwaker hilt").getSecondsPerDrop();
		assertEquals(sum, voidwaker.getSeconds(), 1e-6);
		assertEquals("wiki rate", voidwaker.getSourceTag());
	}

	@Test
	public void dropsAtOneBossComeOnTheSameKills()
	{
		ChartTime oathplate = find("Oathplate armour");
		assertNotNull(oathplate);
		// Yama is 10 an hour: 360 s a kill; three pieces at 1 in 600 each
		double kills = 600 * (1 + 1 / 2.0 + 1 / 3.0);
		assertEquals(kills * 360, oathplate.getSeconds(), 1.0);
		// far less than the three pieces' times added up
		assertTrue(oathplate.getSeconds() < 3 * drop("Yama", "Oathplate chest").getSecondsPerDrop());
	}

	@Test
	public void aRewardThatIsSureAtAKillCountCountsTheKillsToGo()
	{
		ChartTime rite = find("Rite of vile transference");
		assertNotNull(rite);
		assertEquals(100 * 360.0, rite.getSeconds(), 1e-6);
		sync("Yama", 26);
		assertEquals(74 * 360.0, find("Rite of vile transference").getSeconds(), 1e-6);
		sync("Yama", 100);
		assertNull("with 100 kills there is nothing left of it", find("Rite of vile transference"));
	}

	@Test
	public void itemsYouHaveEverythingOfAreLeftOut()
	{
		assertNotNull(find("Hallowfell"));
		sync("The Mad Angel", 51, "Hallowfell");
		assertNull(find("Hallowfell"));
		// only part of a chart item had: it stays, with the rest to go
		sync("Yama", 40, "Oathplate helm", "Oathplate chest");
		ChartTime oathplate = find("Oathplate armour");
		assertNotNull(oathplate);
		long have = oathplate.getParts().stream().filter(ChartTime.Part::isHave).count();
		assertEquals(2, have);
		// one piece left: its own time
		assertEquals(drop("Yama", "Oathplate legs").getSecondsPerDrop(), oathplate.getSeconds(), 1.0);
	}

	@Test
	public void anItemWithNoTimeStaysInTheChartWithoutOne() throws Exception
	{
		String json = "{\"items\":[{\"step\":1,\"name\":\"Beef\",\"icon\":\"Beef\",\"needs\":[{\"boss\":\"Brutus\",\"drop\":\"Beef\"}]},"
			+ "{\"step\":2,\"name\":\"Hallowfell\",\"icon\":\"Hallowfell\",\"needs\":[{\"boss\":\"The Mad Angel\",\"drop\":\"Hallowfell\"}]}]}";
		LadlorChart custom = LadlorChart.load(new Gson(), new StringReader(json));
		List<ChartTime> items = ChartTimes.compute(tracker, web, RAIDS, custom);
		assertEquals(2, items.size());
		assertFalse(items.get(0).hasTime());
		assertTrue(items.get(1).hasTime());
		assertEquals("Beef", items.get(0).getName());
	}

	@Test
	public void theSourceOfATimeIsSaid()
	{
		assertEquals("wiki rate", find("Hallowfell").getSourceTag());
		assertTrue(find("Hallowfell").isEstimated());
		// 20 timed Mad Angel kills: your own times
		for (int i = 0; i < 20; i++)
		{
			long at = 1_000_000 + i * 3_600_000L;
			tracker.onChatMessage("Your Mad Angel kill count is: <col=ff0000>" + (i + 1) + "</col>.", at);
			tracker.onChatMessage("Fight duration: <col=ff0000>1:00.00</col>.", at + 500);
		}
		ChartTime own = find("Hallowfell");
		assertNotNull(own);
		assertEquals("your times", own.getSourceTag());
		assertFalse(own.isEstimated());
		assertEquals(60.0 * 127, own.getSeconds(), 1.0);
	}

	@Test
	public void whenOnlyPartIsYourOwnTheTagSaysBoth()
	{
		// Doom: level 2 from your timed delves, level 3 (done, but never timed) from the wiki rate
		for (int i = 0; i < 20; i++)
		{
			tracker.onChatMessage("Delve level: 2 duration: <col=ff0000>1:00.00</col>", 1_000_000 + i * 600_000L);
		}
		tracker.onDelveScoreboard(java.util.Collections.singletonMap(3, 5));
		ItemTime cloth = drop("Doom of Mokhaiotl", "Mokhaiotl cloth");
		assertTrue(cloth.isEstimated());
		assertTrue(cloth.usesOwnTimes());
		assertEquals("wiki + yours", cloth.getSourceTag());
	}

	@Test
	public void raidItemsAreMarkedAsRaids()
	{
		assertTrue(drop("Chambers of Xeric", "Twisted bow").isRaid());
		assertFalse(drop("Zulrah", "Tanzanite fang").isRaid());
		assertTrue(drop("Zulrah", "Pet snakeling").isPet());
		assertFalse(drop("Zulrah", "Tanzanite fang").isPet());
		assertEquals(Arrays.asList("wiki rate"), Arrays.asList(drop("Zulrah", "Tanzanite fang").getSourceTag()));
	}

	@Test
	public void theWholeChartSharesKillsBetweenItsItems() throws Exception
	{
		// the Chambers items come on the same raids: together far less than their times added up
		String json = "{\"items\":["
			+ "{\"step\":1,\"name\":\"Twisted bow\",\"icon\":\"Twisted bow\",\"needs\":[{\"boss\":\"Chambers of Xeric\",\"drop\":\"Twisted bow\"}]},"
			+ "{\"step\":2,\"name\":\"Dragon claws\",\"icon\":\"Dragon claws\",\"needs\":[{\"boss\":\"Chambers of Xeric\",\"drop\":\"Dragon claws\"}]},"
			+ "{\"step\":3,\"name\":\"Hallowfell\",\"icon\":\"Hallowfell\",\"needs\":[{\"boss\":\"The Mad Angel\",\"drop\":\"Hallowfell\"}]}]}";
		List<ChartTime> items = ChartTimes.compute(tracker, web, RAIDS, LadlorChart.load(new Gson(), new StringReader(json)));
		assertEquals(3, items.size());
		ChartTimes.Total total = ChartTimes.total(items);
		assertEquals(0, total.getWithoutTime());
		ItemTime bow = drop("Chambers of Xeric", "Twisted bow");
		ItemTime claws = drop("Chambers of Xeric", "Dragon claws");
		double raids = ChartTimes.expectedKillsForAll(new double[]{bow.getChancePerKill(), claws.getChancePerKill()});
		double expected = raids * bow.getSecondsPerKill() + drop("The Mad Angel", "Hallowfell").getSecondsPerDrop();
		assertEquals(expected, total.getSeconds(), 1.0);
		double added = items.get(0).getSeconds() + items.get(1).getSeconds() + items.get(2).getSeconds();
		assertTrue(total.getSeconds() < added);
		assertTrue(total.getSeconds() > items.get(1).getSeconds());
	}

	@Test
	public void theTotalLeavesOutItemsWithNoTimeAndCountsKillsToGo() throws Exception
	{
		String json = "{\"items\":["
			+ "{\"step\":1,\"name\":\"Beef\",\"icon\":\"Beef\",\"needs\":[{\"boss\":\"Brutus\",\"drop\":\"Beef\"}]},"
			+ "{\"step\":2,\"name\":\"Rite\",\"icon\":\"Dossier\",\"needs\":[{\"boss\":\"Yama\",\"kills\":100}]}]}";
		List<ChartTime> items = ChartTimes.compute(tracker, web, RAIDS, LadlorChart.load(new Gson(), new StringReader(json)));
		ChartTimes.Total total = ChartTimes.total(items);
		assertEquals(1, total.getWithoutTime());
		assertEquals(100 * 360.0, total.getSeconds(), 1e-6);
	}

	@Test
	public void slayerBossesNeedTheirSlayerLevel()
	{
		// the wiki's requirements
		assertEquals(91, catalog.byName("Cerberus").getSlayerLevel());
		assertEquals(95, catalog.byName("Alchemical Hydra").getSlayerLevel());
		assertEquals(93, catalog.byName("Thermonuclear Smoke Devil").getSlayerLevel());
		assertEquals(92, catalog.byName("Araxxor").getSlayerLevel());
		assertEquals(87, catalog.byName("Kraken").getSlayerLevel());
		assertEquals(85, catalog.byName("Abyssal Sire").getSlayerLevel());
		assertEquals(75, catalog.byName("Grotesque Guardians").getSlayerLevel());
		assertEquals(0, catalog.byName("Zulrah").getSlayerLevel());
		assertEquals(0, catalog.byName("Yama").getSlayerLevel());
	}

	private boolean canFight(String boss, int level)
	{
		return catalog.byName(boss).getSlayerLevel() <= level;
	}

	@Test
	public void itemsOfBossesAboveYourSlayerLevelAreLeftOut()
	{
		List<ItemTime> at80 = ItemTimes.compute(tracker, web, RAIDS, boss -> canFight(boss, 80));
		for (ItemTime t : at80)
		{
			assertTrue(t.getBoss() + " needs more than 80 Slayer", catalog.byName(t.getBoss()).getSlayerLevel() <= 80);
		}
		assertTrue(at80.stream().anyMatch(t -> t.getBoss().equals("Grotesque Guardians")));
		assertFalse(at80.stream().anyMatch(t -> t.getBoss().equals("Cerberus")));
		List<ItemTime> at99 = ItemTimes.compute(tracker, web, RAIDS, boss -> canFight(boss, 99));
		assertTrue(at99.stream().anyMatch(t -> t.getBoss().equals("Alchemical Hydra")));
		assertTrue(at99.size() > at80.size());
	}

	@Test
	public void aChartItemNeedingASlayerBossIsLeftOutBelowItsLevel()
	{
		List<String> names = new ArrayList<>();
		for (ChartTime t : ChartTimes.compute(tracker, web, RAIDS, LadlorChart.bundled(new com.google.gson.Gson()), boss -> canFight(boss, 80)))
		{
			names.add(t.getName());
		}
		assertFalse(names.contains("Eternal boots"));
		assertFalse(names.contains("Occult necklace"));
		assertTrue(names.contains("Hallowfell"));
		names.clear();
		for (ChartTime t : ChartTimes.compute(tracker, web, RAIDS, LadlorChart.bundled(new com.google.gson.Gson()), boss -> canFight(boss, 93)))
		{
			names.add(t.getName());
		}
		assertTrue(names.contains("Eternal boots"));
		assertTrue(names.contains("Occult necklace"));
	}

	@Test
	public void theFullChartHasItsSlayerAndCompletionItems()
	{
		List<String> names = new ArrayList<>();
		for (ChartTime t : chart())
		{
			names.add(t.getName());
		}
		for (String expected : new String[]{"Primordial boots", "Eternal boots", "Amulet of rancour", "Occult necklace", "Ferocious gloves",
			"Toxic blowpipe", "Necklace of rupture", "Masori body", "Masori mask", "Masori chaps", "Ancient icon",
			"Crystal body", "Crystal helm", "Crystal legs", "Bow of Faerdhinen"})
		{
			assertTrue(expected + " is on the chart", names.contains(expected));
		}
		// the chart's order: the boots before the rancour amulet, the amulet before the Doom items
		assertTrue(names.indexOf("Primordial boots") < names.indexOf("Amulet of rancour"));
		assertTrue(names.indexOf("Voidwaker") < names.indexOf("Necklace of rupture"));
		assertTrue(names.indexOf("Necklace of rupture") < names.indexOf("Toxic blowpipe"));
		assertTrue(names.indexOf("Ferocious gloves") < names.indexOf("Oathplate armour"));
		assertFalse(names.contains("Dizana's quiver"));
		assertFalse(names.contains("Infernal cape"));
		assertFalse(names.contains("Masori body (f)"));
		// crystal armour in the chart's order: body, helm, legs, then the bow
		assertTrue(names.indexOf("Crystal body") < names.indexOf("Crystal helm"));
		assertTrue(names.indexOf("Crystal helm") < names.indexOf("Crystal legs"));
		assertTrue(names.indexOf("Crystal legs") < names.indexOf("Bow of Faerdhinen"));
	}

	@Test
	public void theBlowpipeNeedsZulrahsFang()
	{
		ChartTime blowpipe = find("Toxic blowpipe");
		assertNotNull(blowpipe);
		assertEquals(drop("Zulrah", "Tanzanite fang").getSecondsPerDrop(), blowpipe.getSeconds(), 1e-6);
		sync("Zulrah", 300, "Tanzanite fang");
		assertNull(find("Toxic blowpipe"));
	}

	@Test
	public void theSlayerGatedChartItemsFollowTheirBossesLevels()
	{
		java.util.Map<String, Integer> level = new java.util.HashMap<>();
		level.put("Primordial boots", 91);
		level.put("Eternal boots", 91);
		level.put("Amulet of rancour", 92);
		level.put("Occult necklace", 93);
		level.put("Ferocious gloves", 95);
		for (java.util.Map.Entry<String, Integer> e : level.entrySet())
		{
			List<String> at = new ArrayList<>();
			for (ChartTime t : ChartTimes.compute(tracker, web, RAIDS, LadlorChart.bundled(new com.google.gson.Gson()), boss -> canFight(boss, e.getValue() - 1)))
			{
				at.add(t.getName());
			}
			assertFalse(e.getKey() + " is hidden one level short", at.contains(e.getKey()));
			at.clear();
			for (ChartTime t : ChartTimes.compute(tracker, web, RAIDS, LadlorChart.bundled(new com.google.gson.Gson()), boss -> canFight(boss, e.getValue())))
			{
				at.add(t.getName());
			}
			assertTrue(e.getKey() + " shows at its level", at.contains(e.getKey()));
		}
	}

	@Test
	public void crystalArmourIsCountedInSeeds()
	{
		// the body takes 3 crystal armour seeds, the legs 2, the helm 1
		ItemTime seed = drop("The Gauntlet", "Crystal armour seed");
		assertEquals(3 * seed.getSecondsPerDrop(), find("Crystal body").getSeconds(), 1e-6);
		assertEquals(2 * seed.getSecondsPerDrop(), find("Crystal legs").getSeconds(), 1e-6);
		assertEquals(seed.getSecondsPerDrop(), find("Crystal helm").getSeconds(), 1e-6);
		assertEquals("3 x Crystal armour seed", find("Crystal body").getParts().get(0).getLabel());
		assertEquals("wiki rate", find("Crystal body").getSourceTag());
	}

	@Test
	public void theSeedsYouHaveGoToTheChartItemsInOrder()
	{
		// 4 seeds obtained: the body (3) and the helm (1) are covered, the legs (2) are not
		sync("The Gauntlet", 214, new String[]{}, 4);
		assertNull(find("Crystal body"));
		assertNull(find("Crystal helm"));
		assertNotNull(find("Crystal legs"));
		assertEquals(2, find("Crystal legs").getParts().get(0).getMissing());
		// 5 seeds: one seed short on the legs
		sync("The Gauntlet", 214, new String[]{}, 5);
		assertEquals(1, find("Crystal legs").getParts().get(0).getMissing());
		ItemTime seed = drop("The Gauntlet", "Crystal armour seed");
		assertEquals(seed.getSecondsPerDrop(), find("Crystal legs").getSeconds(), 1e-6);
		// 2 seeds: not even the body
		sync("The Gauntlet", 214, new String[]{}, 2);
		assertEquals(1, find("Crystal body").getParts().get(0).getMissing());
		assertEquals(1, find("Crystal helm").getParts().get(0).getMissing());
		assertEquals(2, find("Crystal legs").getParts().get(0).getMissing());
		// 6: all of it
		sync("The Gauntlet", 214, new String[]{}, 6);
		assertNull(find("Crystal body"));
		assertNull(find("Crystal helm"));
		assertNull(find("Crystal legs"));
	}

	@Test
	public void theSameDropInSeveralChartItemsIsOneGrindInTheTotal()
	{
		List<ChartTime> items = new ArrayList<>();
		for (ChartTime t : chart())
		{
			if (t.getName().startsWith("Crystal"))
			{
				items.add(t);
			}
		}
		assertEquals(3, items.size());
		ItemTime seed = drop("The Gauntlet", "Crystal armour seed");
		ChartTimes.Total total = ChartTimes.total(items);
		// six copies of one drop: 6 / p kills, not three separate waits that happen to add to the same
		assertEquals(6 * seed.getSecondsPerDrop(), total.getSeconds(), 1.0);
	}

	@Test
	public void expectedKillsForSeveralCopies()
	{
		double p = 1 / 120.0;
		assertEquals(3 / p, ChartTimes.expectedKillsForAll(new double[]{p}, new int[]{3}), 1e-3);
		assertEquals(1 / p, ChartTimes.expectedKillsForAll(new double[]{p}, new int[]{1}), 1e-9);
		// two drops, one needed twice: checked against a simulation
		double p1 = 1 / 100.0;
		double p2 = 1 / 250.0;
		double exact = ChartTimes.expectedKillsForAll(new double[]{p1, p2}, new int[]{2, 1});
		java.util.Random random = new java.util.Random(99);
		double sum = 0;
		int runs = 200_000;
		for (int r = 0; r < runs; r++)
		{
			int a = 0;
			boolean b = false;
			int kills = 0;
			while (a < 2 || !b)
			{
				kills++;
				if (random.nextDouble() < p1)
				{
					a++;
				}
				if (random.nextDouble() < p2)
				{
					b = true;
				}
			}
			sum += kills;
		}
		assertEquals(sum / runs, exact, exact * 0.01);
		// more copies of anything takes longer
		assertTrue(exact > ChartTimes.expectedKillsForAll(new double[]{p1, p2}, new int[]{1, 1}));
	}
}
