package com.onrate.engine;

import com.onrate.engine.data.Bundled;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.KillTimes;
import com.onrate.engine.stats.ItemTime;
import com.onrate.engine.stats.ItemTimes;
import com.onrate.engine.stats.RaidChance;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Average time to get each item: your own timed kills once there are enough, the estimate until then. */
public class ItemTimesTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;
	private BossCatalog catalog;
	private Tracker tracker;
	private KillTimes web;
	private long now = 1_000_000;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		web = KillTimes.bundled(new com.google.gson.Gson());
		tracker = new Tracker(catalog);
	}

	private ItemTime find(String boss, String label)
	{
		for (ItemTime t : ItemTimes.compute(tracker, web, RAIDS))
		{
			if (t.getBoss().equals(boss) && t.getLabel().equals(label))
			{
				return t;
			}
		}
		return null;
	}

	/** {@code n} Zulrah kills, each 100 seconds, an hour apart. */
	private void zulrahKills(int n, int from)
	{
		for (int i = 0; i < n; i++)
		{
			long at = now + (from + i) * 3_600_000L;
			tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>" + (from + i + 1) + "</col>.", at);
			tracker.onChatMessage("Fight duration: <col=ff0000>1:40.00</col>. Personal best: 0:55.80", at + 500);
		}
	}

	@Test
	public void theBundledFileHasTheBossesItPromises()
	{
		assertNotNull(web.forBoss("Zulrah", null));
		assertEquals(180.0, web.forBoss("Zulrah", null).getSecondsPerKill(), 1e-9);
		assertNotNull(web.forBoss("Chambers of Xeric", "Normal"));
		assertNull(web.forBoss("Chambers of Xeric", null));
		assertNull(web.forBoss("Brutus", null));
		assertTrue(web.doom().isAvailable());
		// every boss and part in the file is one the catalog has
		for (com.onrate.engine.data.BossDefinition boss : catalog.getBosses())
		{
			if (boss.hasParts())
			{
				for (String part : web.partsWithEstimate(boss.getName()))
				{
					assertTrue(boss.getName() + " / " + part, boss.getParts().stream().anyMatch(p -> p.getName().equals(part)));
				}
			}
		}
	}

	@Test
	public void noKillsYetUsesTheEstimateAndTheDropRate()
	{
		ItemTime fang = find("Zulrah", "Tanzanite fang");
		assertNotNull(fang);
		assertTrue(fang.isEstimated());
		// 20 an hour is 180 s a kill; 1 in 512 a kill
		assertEquals(180.0, fang.getSecondsPerKill(), 1e-9);
		assertEquals(512 * 180.0, fang.getSecondsPerDrop(), 1.0);
		assertEquals("kill", fang.getUnit());
		assertFalse(fang.isHave());
	}

	@Test
	public void ownAverageTakesOverAtTwentyTimedKills()
	{
		zulrahKills(19, 0);
		assertTrue(find("Zulrah", "Tanzanite fang").isEstimated());
		zulrahKills(1, 19);
		ItemTime fang = find("Zulrah", "Tanzanite fang");
		assertFalse(fang.isEstimated());
		assertEquals(100.0, fang.getSecondsPerKill(), 1e-6);
		assertEquals(512 * 100.0, fang.getSecondsPerDrop(), 1.0);
	}

	@Test
	public void raidsNeedOnlyFiveTimedRaids()
	{
		for (int i = 0; i < 4; i++)
		{
			coxRaid(i + 1);
		}
		assertTrue(find("Chambers of Xeric", "Twisted bow").isEstimated());
		coxRaid(5);
		ItemTime tbow = find("Chambers of Xeric", "Twisted bow");
		assertFalse(tbow.isEstimated());
		assertEquals("raid", tbow.getUnit());
		assertEquals(1500.0, tbow.getSecondsPerKill(), 1e-6);
		// the chance comes from your points: 25,000 of 25,000, 2 of 60 of the unique chance
		double unique = RaidChance.cox(25_000, 25_000);
		assertEquals(unique * 2 / 60, tbow.getChancePerKill(), 1e-9);
		assertEquals(1500.0 / (unique * 2 / 60), tbow.getSecondsPerDrop(), 1.0);
	}

	private void coxRaid(int kc)
	{
		long at = now + kc * 3_600_000L;
		tracker.onChatMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>25:00.00</col>", at);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + kc + "</col>.", at + 500);
		tracker.onRaidRecord(RaidRecord.cox(25_000, 25_000, 1), at + 1_000);
	}

	@Test
	public void aCombinedPageWeighsItsPartsByYourKills()
	{
		// Callisto is 25 an hour (144 s), Artio 52 an hour: only Callisto kills here
		for (int i = 1; i <= 3; i++)
		{
			tracker.onChatMessage("Your Callisto kill count is: <col=ff0000>" + i + "</col>.", now + i * 600_000L);
		}
		ItemTime hilt = find("Callisto and Artio", "Voidwaker hilt");
		assertNotNull(hilt);
		assertEquals(144.0, hilt.getSecondsPerKill(), 1e-6);
		assertTrue(hilt.isEstimated());
	}

	@Test
	public void aCombinedPageWithNoKillsAssumesItsFirstModeWithATime()
	{
		// the Tombs have a time only for expert mode
		ItemTime t = find("Tombs of Amascut", "Tumeken's shadow (uncharged)");
		assertNotNull(t);
		assertEquals(3600 / 1.75, t.getSecondsPerKill(), 1e-6);
		assertTrue(t.getBasis().contains("Expert Mode"));
	}

	@Test
	public void aBossWithNoFigureHasNoTimeUntilYouHaveYourOwn()
	{
		assertNull(find("Brutus", "Mooleta"));
		assertNull(find("Wintertodt", "Phoenix"));
	}

	@Test
	public void doomWithNoDelvesAssumesARunToDelveEight()
	{
		ItemTime treads = find("Doom of Mokhaiotl", "Avernic treads");
		assertNotNull(treads);
		assertEquals("run", treads.getUnit());
		assertTrue(treads.isEstimated());
		// 8 levels at 67.5 s plus a minute of banking; treads from level 4 at 1/1350, 1/810, 1/765, 1/720, 1/630
		double run = 8 * 67.5 + 60;
		double chance = 1 / 1350.0 + 1 / 810.0 + 1 / 765.0 + 1 / 720.0 + 1 / 630.0;
		assertEquals(run, treads.getSecondsPerKill(), 1e-6);
		assertEquals(run / chance, treads.getSecondsPerDrop(), 1.0);
		// cloth can drop from level 2, so it comes sooner than the treads
		assertTrue(find("Doom of Mokhaiotl", "Mokhaiotl cloth").getSecondsPerDrop() > 0);
	}

	@Test
	public void doomUsesTheDepthsYouReallyWent()
	{
		// 10 runs: all got to level 4, none deeper
		java.util.Map<Integer, Integer> levels = new java.util.LinkedHashMap<>();
		for (int l = 1; l <= 4; l++)
		{
			levels.put(l, 10);
		}
		tracker.onDelveScoreboard(levels);
		ItemTime treads = find("Doom of Mokhaiotl", "Avernic treads");
		assertNotNull(treads);
		assertEquals("delve", treads.getUnit());
		assertTrue(treads.isEstimated());
		// 40 delves at 67.5 s, plus a minute of banking for each of the 10 runs, over the 40 delves
		assertEquals(67.5 + 60.0 * 10 / 40, treads.getSecondsPerKill(), 1e-6);
		// only level 4 can drop treads: 10 rolls at 1/1350 over 40 delves
		assertEquals((10 / 1350.0) / 40, treads.getChancePerKill(), 1e-9);
		// a player who always goes deeper waits less per drop than one who stops at level 4
		java.util.Map<Integer, Integer> deeper = new java.util.LinkedHashMap<>();
		for (int l = 1; l <= 8; l++)
		{
			deeper.put(l, 10);
		}
		Tracker other = new Tracker(catalog);
		other.onDelveScoreboard(deeper);
		double shallow = treads.getSecondsPerDrop();
		for (ItemTime t : ItemTimes.compute(other, web, RAIDS))
		{
			if (t.getBoss().equals("Doom of Mokhaiotl") && t.getLabel().equals("Avernic treads"))
			{
				assertTrue(t.getSecondsPerDrop() < shallow);
			}
		}
	}

	@Test
	public void doomLevelTimesAreYoursOnceYouHaveTwentyOfThatLevel()
	{
		for (int i = 0; i < 20; i++)
		{
			tracker.onChatMessage("Delve level: 2 duration: <col=ff0000>1:00.00</col>", now + i * 600_000L);
		}
		ItemTime cloth = find("Doom of Mokhaiotl", "Mokhaiotl cloth");
		assertNotNull(cloth);
		assertFalse(cloth.isEstimated());
		assertEquals(60.0, cloth.getSecondsPerKill(), 1e-6);
		// 20 delves at level 2, 1 in 2500 each
		assertEquals(60.0 * 2500, cloth.getSecondsPerDrop(), 1.0);
	}

	@Test
	public void longTimesRead()
	{
		assertEquals("<1 min", ItemTime.formatLong(20));
		assertEquals("45 min", ItemTime.formatLong(45 * 60));
		assertEquals("3.3 h", ItemTime.formatLong(3.3 * 3600));
		assertEquals("145 h", ItemTime.formatLong(145 * 3600));
		assertEquals("-", ItemTime.formatLong(Double.NaN));
	}
}
