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
import com.onrate.engine.stats.OathplateShards;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** Yama's oathplate shards: another way to the pieces, shown in the hovers. */
public class OathplateShardsTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;
	private BossCatalog catalog;
	private Tracker tracker;

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

	@Test
	public void onePieceWithShardsAgreesWithTheMathsDoneByHand()
	{
		// 450 shards are in hand after ceil(450 / 0.8075) = 558 kills: until then only the drop can give the piece,
		// so the expected kills are (1 - (1 - p)^558) / p
		double p = 1 / 600.0;
		double byHand = (1 - Math.pow(1 - p, 558)) / p;
		assertEquals(byHand, OathplateShards.expectedKills(new double[]{p}), 8);
		assertTrue(byHand > 350 && byHand < 380);
	}

	@Test
	public void shardsSaveKillsAndTheAnswerDoesNotChange()
	{
		double[] three = {1 / 600.0, 1 / 600.0, 1 / 600.0};
		double with = OathplateShards.expectedKills(three);
		double without = ChartTimes.expectedKillsForAll(three);
		assertTrue(with < without);
		assertTrue(with > 200);
		assertEquals(with, OathplateShards.expectedKills(three), 0);
		assertEquals(0, OathplateShards.expectedKills(new double[0]), 0);
		// a piece alone saves more than a piece in a set of three, in kills per piece
		assertTrue(OathplateShards.expectedKills(new double[]{1 / 600.0}) < 600);
	}

	@Test
	public void theHoverSaysWhatShardsAre()
	{
		String text = OathplateShards.describe(new double[]{1 / 600.0, 1 / 600.0}, 360);
		assertTrue(text, text.contains("Oathplate shards"));
		assertTrue(text, text.contains("450"));
		assertTrue(text, text.contains("2,520 crushed infernal shale"));
		assertTrue(text, text.contains("83 Smithing"));
		assertTrue(text, text.contains("all 2 missing pieces"));
		assertTrue(text, text.contains(" h)") || text.contains(" min)"));
		assertTrue(OathplateShards.describe(new double[]{1 / 600.0}, 360).contains("this piece"));
		// without a time per kill only kills are given
		assertFalse(OathplateShards.describe(new double[]{1 / 600.0}, Double.NaN).contains(" h)"));
	}

	@Test
	public void theChartItemShowsTheShardRoute()
	{
		ChartTime oathplate = null;
		for (ChartTime t : ChartTimes.compute(tracker, KillTimes.bundled(new com.google.gson.Gson()), RAIDS, LadlorChart.bundled(new com.google.gson.Gson())))
		{
			if (t.getName().equals("Oathplate armour"))
			{
				oathplate = t;
			}
			else
			{
				assertNull(t.getName() + " has no second route", t.getRoute());
			}
		}
		assertNotNull(oathplate);
		assertNotNull(oathplate.getRoute());
		assertTrue(oathplate.getRoute().contains("all 3 missing pieces"));
		// the headline time is still from drops alone
		assertEquals(600 * (1 + 1 / 2.0 + 1 / 3.0) * 360, oathplate.getSeconds(), 1.0);
	}

	@Test
	public void withTwoPiecesHadItTalksOfThePieceLeft()
	{
		BossDefinition yama = catalog.byName("Yama");
		List<String> counters = new ArrayList<>();
		for (String name : yama.getKcNames())
		{
			counters.add(name + " kills: 40");
		}
		java.util.Map<String, Integer> have = new java.util.HashMap<>();
		have.put("Oathplate helm", 1);
		have.put("Oathplate chest", 1);
		tracker.onClogPage(yama.getClogPages().get(0), counters, have);
		for (ChartTime t : ChartTimes.compute(tracker, KillTimes.bundled(new com.google.gson.Gson()), RAIDS, LadlorChart.bundled(new com.google.gson.Gson())))
		{
			if (t.getName().equals("Oathplate armour"))
			{
				// it recounts for the piece left and says how many you have
				assertTrue(t.getRoute(), t.getRoute().startsWith("You have 2 of 3 pieces."));
				assertTrue(t.getRoute(), t.getRoute().contains("the piece you are missing"));
				return;
			}
		}
		throw new AssertionError("Oathplate armour should still be on the chart");
	}

	@Test
	public void yamasPiecesCarryTheHintAndNothingElseDoes()
	{
		for (ItemTime t : ItemTimes.compute(tracker, KillTimes.bundled(new com.google.gson.Gson()), RAIDS))
		{
			boolean oathplate = t.getBoss().equals("Yama") && t.getIconItem().startsWith("Oathplate ");
			if (oathplate)
			{
				assertNotNull(t.getIconItem(), t.getHint());
				assertTrue(t.getHint().contains("Oathplate shards"));
			}
			else
			{
				assertNull(t.getBoss() + " / " + t.getIconItem(), t.getHint());
			}
		}
	}

	@Test
	public void theRouteIsRecountedAsYouGetPieces()
	{
		BossDefinition yama = catalog.byName("Yama");
		List<String> counters = new ArrayList<>();
		for (String name : yama.getKcNames())
		{
			counters.add(name + " kills: 40");
		}
		double[] kills = new double[3];
		String[][] had = {{}, {"Oathplate helm"}, {"Oathplate helm", "Oathplate chest"}};
		for (int i = 0; i < had.length; i++)
		{
			Tracker t = new Tracker(catalog);
			t.setSettings(RAIDS);
			java.util.Map<String, Integer> have = new java.util.HashMap<>();
			for (String piece : had[i])
			{
				have.put(piece, 1);
			}
			t.onClogPage(yama.getClogPages().get(0), counters, have);
			for (ChartTime c : ChartTimes.compute(t, KillTimes.bundled(new com.google.gson.Gson()), RAIDS, LadlorChart.bundled(new com.google.gson.Gson())))
			{
				if (c.getName().equals("Oathplate armour"))
				{
					String route = c.getRoute();
					assertTrue(route, i == 0 ? !route.startsWith("You have") : route.startsWith("You have " + i + " of 3 pieces."));
					java.util.regex.Matcher m = java.util.regex.Pattern.compile("takes about ([\\d,]+) kills").matcher(route);
					assertTrue(route, m.find());
					kills[i] = Double.parseDouble(m.group(1).replace(",", ""));
				}
			}
		}
		// fewer pieces missing: fewer kills
		assertTrue(kills[0] > kills[1] && kills[1] > kills[2]);
	}
}
