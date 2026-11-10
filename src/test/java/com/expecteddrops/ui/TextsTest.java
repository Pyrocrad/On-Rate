package com.expecteddrops.ui;

import com.onrate.engine.math.Poisson;

import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The words under and behind each verdict. */
public class TextsTest
{
	@Test
	public void noneSaysHowManyHaveNone()
	{
		assertEquals("74% have none", Texts.luckShort(Poisson.shares(0, 0.30), 0));
		assertEquals("only 1 in 5 has none", Texts.luckShort(Poisson.shares(0, 1.65), 0));
		assertEquals(">99% have none", Texts.luckShort(Poisson.shares(0, 0.001), 0));
		assertEquals("74% of players have none by now, 26% have one or more", Texts.sharesLine(Poisson.shares(0, 0.30), 0));
	}

	@Test
	public void countsSayHowCommon()
	{
		// 4 of 5.67: 33% have this many or fewer
		assertEquals("1 in 3 as dry", Texts.luckShort(Poisson.shares(4, 5.67), 4));
		// 1 of 0.16: 15% have one or more
		assertEquals("1 in 7 as lucky", Texts.luckShort(Poisson.shares(1, 0.16), 1));
		// 2 of 1.65: the middle of the range
		assertEquals("about average", Texts.luckShort(Poisson.shares(2, 1.65), 2));
		assertEquals("18% of players have fewer, 15% as many, 67% more", Texts.sharesLine(Poisson.shares(4, 5.67), 4));
	}

	@Test
	public void valueLuckInWords()
	{
		assertEquals("1 in 6 as lucky", Texts.luckShort(0.83));
		assertEquals("1 in 3 as dry", Texts.luckShort(0.30));
		assertEquals("about average", Texts.luckShort(0.5));
		assertEquals("<1 in 10,000 as dry", Texts.luckShort(0.00001));
	}

	@Test
	public void shortLineFollowsTheVerdictBands()
	{
		// "On rate" (45% to 55%) is about average
		assertEquals("about average", Texts.luckShort(0.45));
		assertEquals("about average", Texts.luckShort(0.50));
		assertEquals("about average", Texts.luckShort(0.55));
		// a bit dry and a bit spooned say how common they are
		assertEquals("1 in 3 as dry", Texts.luckShort(0.35));
		assertEquals("1 in 3 as dry", Texts.luckShort(0.40));
		assertEquals("1 in 2 as dry", Texts.luckShort(0.44));
		assertEquals("1 in 2 as lucky", Texts.luckShort(0.56));
		assertEquals("1 in 3 as lucky", Texts.luckShort(0.60));
		assertEquals("1 in 3 as lucky", Texts.luckShort(0.65));
	}

	private static com.onrate.engine.stats.RowStats item(String name, double expected, int received)
	{
		return new com.onrate.engine.stats.RowStats(name, name, expected / 100, expected, received, false);
	}

	@Test
	public void onRateGetsOneNotableSentence()
	{
		// 1 of 4.0: 91% of players have more
		assertEquals("Driest: Magic fang, drier than 91% of players.",
			Texts.notableLine(java.util.Arrays.asList(item("Magic fang", 4.0, 1), item("Jar of swamp", 0.3, 0))));
		// none at 1.0 expected: 63% would have one
		assertEquals("6 in 10 players would have a Tumeken's shadow by now.",
			Texts.notableLine(java.util.Collections.singletonList(item("Tumeken's shadow", 1.0, 0))));
		assertEquals("7 in 10 players would have an Elder maul by now.",
			Texts.notableLine(java.util.Collections.singletonList(item("Elder maul", 1.3, 0))));
		assertEquals("8 in 10 players would have Bandos boots by now.",
			Texts.notableLine(java.util.Collections.singletonList(item("Bandos boots", 1.7, 0))));
		// 2 at 0.1: 99.5% have fewer, further out than the dry fang, so it wins
		assertEquals("Luckiest: Twisted bow, luckier than 99.5% of players.",
			Texts.notableLine(java.util.Arrays.asList(item("Magic fang", 4.0, 1), item("Twisted bow", 0.1, 2))));
		// nothing far from average: no sentence at all
		assertEquals(null, Texts.notableLine(java.util.Arrays.asList(item("Magic fang", 1.0, 1), item("Jar of swamp", 0.3, 0))));
		assertEquals(null, Texts.notableLine(java.util.Collections.emptyList()));
	}

	@Test
	public void valueHoverNamesTheItemMostOfItComesFrom()
	{
		java.util.List<String> names = java.util.Arrays.asList("Crystal tool seed", "Zalcano shard", "Smolcano");
		assertEquals("Most of it is the Crystal tool seed (95%).",
			Texts.mainValueSource(names, java.util.Arrays.asList(950.0, 50.0, 0.0)));
		// spread out: nothing to say
		assertEquals(null, Texts.mainValueSource(names, java.util.Arrays.asList(40.0, 35.0, 25.0)));
		assertEquals(null, Texts.mainValueSource(names, java.util.Arrays.asList(0.0, 0.0, 0.0)));
	}

	@Test
	public void hoversSayItAsAPercentage()
	{
		// told from the side that holds most players, so the number is always over half
		assertEquals("drier than 81%", Texts.luckPercent(Poisson.shares(0, 1.65), 0));
		assertEquals("74% have none", Texts.luckPercent(Poisson.shares(0, 0.30), 0));
		// 0 of 10: about 1 in 22,000 has none, past what the drop table can tell apart
		assertEquals("drier than 99.99% (<1 in 10,000 players)", Texts.luckPercent(Poisson.shares(0, 10), 0));
		// 4 of 5.67: 67% have more
		assertEquals("drier than 67%", Texts.luckPercent(Poisson.shares(4, 5.67), 4));
		// 1 of 0.16: 85% have none
		assertEquals("luckier than 85%", Texts.luckPercent(Poisson.shares(1, 0.16), 1));
		// 3 of 0.2: about 1 in 871 has this many or more
		assertEquals("luckier than 99.8% (1 in 871 players)", Texts.luckPercent(Poisson.shares(3, 0.2), 3));
		// 2 of 1.65: just over half have fewer
		assertEquals("luckier than 51%", Texts.luckPercent(Poisson.shares(2, 1.65), 2));
		// 1 of 1.0: 37% fewer, 37% as many, 26% more: no side is a majority
		assertEquals("about average", Texts.luckPercent(Poisson.shares(1, 1.0), 1));
		// by value
		assertEquals("luckier than 83%", Texts.luckPercent(0.83));
		assertEquals("drier than 70%", Texts.luckPercent(0.30));
		assertEquals("drier than 99.99% (<1 in 10,000 players)", Texts.luckPercent(0.00001));
		assertEquals("drier than 97% (1 in 33 players)", Texts.luckPercent(0.03));
	}

	@Test
	public void howFarPastTheRate()
	{
		// none at 2.1 expected: 2.1 times the drop rate, the way players say it
		com.onrate.engine.stats.RowStats none = new com.onrate.engine.stats.RowStats("Hydra's claw", null, 1 / 1001.0, 2.1, 0, false);
		assertEquals("2.1\u00d7 the drop rate without one", Texts.rateMultipleLine(none));
		com.onrate.engine.stats.RowStats any = new com.onrate.engine.stats.RowStats("Any", null, 0, 1.25, 0, true);
		assertEquals("1.3\u00d7 the unique rate without a unique", Texts.rateMultipleLine(any));
		// once you have one, the dry streak note says it instead
		com.onrate.engine.stats.RowStats one = new com.onrate.engine.stats.RowStats("Hydra's claw", null, 1 / 1001.0, 2.1, 1, false);
		assertEquals(null, Texts.rateMultipleLine(one));
	}

	@Test
	public void rateMultipleCountsAPetInFull()
	{
		// one pet expected: 1x the rate, though only 0.63 of it can count
		com.onrate.engine.stats.RowStats any = new com.onrate.engine.stats.RowStats("Any", null, 0,
			Poisson.expectedCapped(1, 1), 0, true).withParts(new com.onrate.engine.stats.RowStats.Parts().addCapped(1, 1));
		assertEquals("1.0\u00d7 the unique rate without a unique", Texts.rateMultipleLine(any));
	}

	@Test
	public void dryStreakSaysWhatItCountsAndAgreesInNumber()
	{
		com.onrate.engine.stats.RowStats one = new com.onrate.engine.stats.RowStats("Magic fang", null, 0.5, 5, 1, false)
			.withStreak(1, true);
		assertEquals("Dry streak: 1 kill since your last one (0.5\u00d7 the drop rate).", Texts.streakNote(one, "kill"));
		com.onrate.engine.stats.RowStats many = new com.onrate.engine.stats.RowStats("Magic fang", null, 0.5, 5, 1, false)
			.withStreak(40, false);
		assertEquals("Dry streak: at least 40 kills (20\u00d7 the drop rate or more). Your last one came before On Rate started tracking.",
			Texts.streakNote(many, "kill"));
	}

	@Test
	public void setupSaysNothingUnlessALogPageCouldNotBeRead()
	{
		// the normal case: no line at all
		assertNull(Texts.setupProblem(Collections.emptyList(), false));
		assertEquals("The \"Vorkath\" log page was opened but could not be read. The game may have changed; please report it.",
			Texts.setupProblem(Collections.singletonList("Vorkath"), false));
		// several pages, or a header that could not be read: still one line, naming none
		String generic = "A collection log page was opened but could not be read. The game may have changed; please report it.";
		assertEquals(generic, Texts.setupProblem(Arrays.asList("Vorkath", "Zulrah"), false));
		assertEquals(generic, Texts.setupProblem(Collections.emptyList(), true));
		assertEquals(generic, Texts.setupProblem(Collections.singletonList("Vorkath"), true));
	}

	@Test
	public void unreadableWordingIsCalmAndPointsToReset()
	{
		assertEquals("Log page opened but not readable. The game may have changed; please report it.", Texts.LOG_PAGE_UNREADABLE);
		assertTrue(Texts.LOG_PAGE_UNREADABLE_TIP.contains("does not mean zero drops"));
		assertTrue(Texts.LOG_PAGE_UNREADABLE_TIP.contains("Reset this boss"));
	}

	@Test
	public void aDropAddedByHandHasNoDate()
	{
		assertEquals("date unknown", Texts.daysAgo(0, 1_000_000));
		assertEquals("today", Texts.daysAgo(1_000_000, 1_000_000));
	}

	private static com.onrate.engine.tracking.KillEntry.DropEntry dropWith(double multiple)
	{
		return new com.google.gson.Gson().fromJson("{\"i\":\"x\",\"s\":10,\"x\":" + multiple + "}",
			com.onrate.engine.tracking.KillEntry.DropEntry.class);
	}

	@Test
	public void luckyDropsSayOneInN()
	{
		// 0.001 of the drop rate: 1 in 1,000 players is this quick
		assertEquals("quicker than 99.9% of players (1 in 1,000)", Texts.dropLuck(dropWith(0.001)));
		assertEquals("quicker than >99.9% of players (1 in 2,500)", Texts.dropLuck(dropWith(0.0004)));
		assertEquals("quicker than 96% of players (1 in 26)", Texts.dropLuck(dropWith(0.04)));
		// not that rare: no "1 in N"
		assertEquals("quicker than 74% of players", Texts.dropLuck(dropWith(0.3)));
		assertEquals("right on the drop rate", Texts.dropLuck(dropWith(1.0)));
		// dry: slower than most, 1 in N players waits this long
		assertEquals("slower than 95% of players (1 in 20)", Texts.dropLuck(dropWith(3.0)));
		// saved rounded to 0.00 (older versions): only known to be under 0.005
		assertEquals("quicker than 99.5% of players (1 in 200+)", Texts.dropLuck(dropWith(0.0)));
	}

	@Test
	public void aWaitStillGoingOnHasTheSameLuckWords()
	{
		// 5 times the drop rate without one: 1 in 148 players waits this long
		assertEquals("slower than 99% of players (1 in 148)", Texts.waitLuck(5.0));
		assertEquals(Texts.dropLuck(dropWith(3.0)), Texts.waitLuck(3.0));
	}

	@Test
	public void listRowsUseShortLuckWords()
	{
		assertEquals("1 in 1,000 players this quick", Texts.waitLuckShort(0.001, false));
		assertEquals("1 in 20 players wait this long", Texts.waitLuckShort(3.0, false));
		assertEquals("1 in 148 still waiting", Texts.waitLuckShort(5.0, true));
		// under 1 in 10 there is no "1 in N"
		assertEquals("quicker than 74%", Texts.waitLuckShort(0.3, false));
		assertEquals("slower than 63%", Texts.waitLuckShort(1.0001, true).replace("right on the drop rate", "slower than 63%"));
		assertEquals("1 in 200+ players this quick", Texts.waitLuckShort(0.0, false));
	}

	@Test
	public void resetSaysWhatIsGoneForGood()
	{
		String text = Texts.resetConfirm("Vorkath");
		assertTrue(text.contains("Vorkath"));
		int gone = text.indexOf("Gone for good");
		int back = text.indexOf("Can be synced again");
		assertTrue(gone >= 0 && back > gone);
		String lost = text.substring(gone, back);
		for (String part : new String[]{"History", "raid points", "team sizes", "dry streaks", "fastest and longest gaps",
			"added by hand", "Doom levels", "Colosseum claims", "milestone"})
		{
			assertTrue(part, lost.contains(part));
		}
		String synced = text.substring(back);
		assertTrue(synced.contains("kill count") && synced.contains("item counts"));
		// Cancel is the default (initial) button
		assertEquals("Cancel", Texts.RESET_OPTIONS[1]);
		assertEquals("Reset", Texts.RESET_OPTIONS[0]);
	}

	@Test
	public void toaPointsAreExactUnlessTheEstimateWasUsed()
	{
		// the game sent the exact points for every raid: no "estimated"
		String exact = Texts.raidPointsLine("toa", "Expert Mode", 12, 0);
		assertEquals("12 Expert Mode raids used your points instead of the setting.", exact);
		// the estimate only where the exact points didn't come
		String mixed = Texts.raidPointsLine("toa", "Expert Mode", 12, 2);
		assertTrue(mixed, mixed.startsWith("12 Expert Mode raids used your points") && mixed.contains("estimate for the 2 where"));
		String allEstimated = Texts.raidPointsLine("toa", "Normal", 1, 1);
		assertTrue(allEstimated, allEstimated.startsWith("1 Normal raid used your estimated points"));
		// other raids are unchanged
		assertEquals("3 Normal raids used your points instead of the setting.", Texts.raidPointsLine("cox", "Normal", 3, 0));
		assertEquals("1 Normal raid used your share of the damage (On Rate's estimate) instead of the setting.",
			Texts.raidPointsLine("fractured", "Normal", 1, 0));
	}

	@Test
	public void timeBasisSaysKillSpeed()
	{
		assertEquals("Wiki kill speed: 39 kills an hour, from the OSRS Wiki's money-making guide.",
			Texts.timeBasis("Wiki rate: 39 kills an hour, from the OSRS Wiki's money-making guide."));
		assertEquals("(your times once you have 20 timed delves of that level, else the wiki's kill speed).",
			Texts.timeBasis("(your times once you have 20 timed delves of that level, else the wiki rate)."));
		assertEquals("Your own times: 25 timed kills.", Texts.timeBasis("Your own times: 25 timed kills."));
		assertNull(Texts.timeBasis(null));
	}

	@Test
	public void itemSetupTextSaysRaidsAndHours()
	{
		assertEquals("1 in 1,450 raids · 1 per ~790 h", Texts.itemSetupText(1 / 1450.0, 1 / 790.0));
		assertEquals("1 in 30 raids · 1 per ~2.5 h", Texts.itemSetupText(1 / 30.0, 1 / 2.5));
		assertEquals("1 in 30 raids", Texts.itemSetupText(1 / 30.0, Double.NaN));
	}

	@Test
	public void learnedNoteSaysWhereTheChanceCameFrom()
	{
		assertEquals("", Texts.learnedNote("cox", 0));
		assertEquals(" (your average over your last 50 raids; you may have raided differently before)", Texts.learnedNote("cox", 50));
		assertEquals(" (your usual team over your last 12 raids; you may have raided differently before)", Texts.learnedNote("tob", 12));
	}

	@Test
	public void forecastSaysRaidsThenHours()
	{
		assertEquals("1,020", Texts.raidsText(1020));
		assertEquals("—", Texts.raidsText(-1));
		// 33 minutes a raid
		assertEquals("~561 h", Texts.hoursText(1020, 33 * 60_000));
		assertEquals("—", Texts.hoursText(1020, -1));
		assertEquals("1 in 805", Texts.oneIn(1 / 805.0));
		assertEquals("4 uniques + pet", Texts.missingText(4, true));
		assertEquals("1 unique", Texts.missingText(1, false));
		assertEquals("Pet only", Texts.missingText(0, true));
	}
}
