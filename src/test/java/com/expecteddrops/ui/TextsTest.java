package com.expecteddrops.ui;

import com.expecteddrops.stats.DropMath;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/** The words under and behind each verdict. */
public class TextsTest
{
	@Test
	public void noneSaysHowManyHaveNone()
	{
		assertEquals("74% have none", Texts.luckShort(DropMath.shares(0, 0.30), 0));
		assertEquals("only 1 in 5 has none", Texts.luckShort(DropMath.shares(0, 1.65), 0));
		assertEquals(">99% have none", Texts.luckShort(DropMath.shares(0, 0.001), 0));
		assertEquals("74% of players have none by now, 26% have one or more", Texts.sharesLine(DropMath.shares(0, 0.30), 0));
	}

	@Test
	public void countsSayHowCommon()
	{
		// 4 of 5.67: 33% have this many or fewer
		assertEquals("1 in 3 as dry", Texts.luckShort(DropMath.shares(4, 5.67), 4));
		// 1 of 0.16: 15% have one or more
		assertEquals("1 in 7 as lucky", Texts.luckShort(DropMath.shares(1, 0.16), 1));
		// 2 of 1.65: the middle of the range
		assertEquals("about average", Texts.luckShort(DropMath.shares(2, 1.65), 2));
		assertEquals("18% of players have fewer, 15% as many, 67% more", Texts.sharesLine(DropMath.shares(4, 5.67), 4));
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

	private static com.expecteddrops.stats.RowStats item(String name, double expected, int received)
	{
		return new com.expecteddrops.stats.RowStats(name, name, expected / 100, expected, received, false);
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
		assertEquals("drier than 81%", Texts.luckPercent(DropMath.shares(0, 1.65), 0));
		assertEquals("74% have none", Texts.luckPercent(DropMath.shares(0, 0.30), 0));
		// 0 of 10: about 1 in 22,000 has none, past what the drop table can tell apart
		assertEquals("drier than 99.99% (<1 in 10,000 players)", Texts.luckPercent(DropMath.shares(0, 10), 0));
		// 4 of 5.67: 67% have more
		assertEquals("drier than 67%", Texts.luckPercent(DropMath.shares(4, 5.67), 4));
		// 1 of 0.16: 85% have none
		assertEquals("luckier than 85%", Texts.luckPercent(DropMath.shares(1, 0.16), 1));
		// 3 of 0.2: about 1 in 871 has this many or more
		assertEquals("luckier than 99.8% (1 in 871 players)", Texts.luckPercent(DropMath.shares(3, 0.2), 3));
		// 2 of 1.65: just over half have fewer
		assertEquals("luckier than 51%", Texts.luckPercent(DropMath.shares(2, 1.65), 2));
		// 1 of 1.0: 37% fewer, 37% as many, 26% more: no side is a majority
		assertEquals("about average", Texts.luckPercent(DropMath.shares(1, 1.0), 1));
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
		com.expecteddrops.stats.RowStats none = new com.expecteddrops.stats.RowStats("Hydra's claw", null, 1 / 1001.0, 2.1, 0, false);
		assertEquals("2.1\u00d7 the drop rate without one", Texts.rateMultipleLine(none));
		com.expecteddrops.stats.RowStats any = new com.expecteddrops.stats.RowStats("Any", null, 0, 1.25, 0, true);
		assertEquals("1.3\u00d7 the unique rate without a unique", Texts.rateMultipleLine(any));
		// once you have one, the dry streak note says it instead
		com.expecteddrops.stats.RowStats one = new com.expecteddrops.stats.RowStats("Hydra's claw", null, 1 / 1001.0, 2.1, 1, false);
		assertEquals(null, Texts.rateMultipleLine(one));
	}

	@Test
	public void rateMultipleCountsAPetInFull()
	{
		// one pet expected: 1x the rate, though only 0.63 of it can count
		com.expecteddrops.stats.RowStats any = new com.expecteddrops.stats.RowStats("Any", null, 0,
			DropMath.expectedCapped(1, 1), 0, true).withParts(new com.expecteddrops.stats.RowStats.Parts().addCapped(1, 1));
		assertEquals("1.0\u00d7 the unique rate without a unique", Texts.rateMultipleLine(any));
	}

	@Test
	public void dryStreakSaysWhatItCountsAndAgreesInNumber()
	{
		com.expecteddrops.stats.RowStats one = new com.expecteddrops.stats.RowStats("Magic fang", null, 0.5, 5, 1, false)
			.withStreak(1, true);
		assertEquals("Dry streak: 1 kill since your last one (0.5\u00d7 the rate).", Texts.streakNote(one, "kill"));
		com.expecteddrops.stats.RowStats many = new com.expecteddrops.stats.RowStats("Magic fang", null, 0.5, 5, 1, false)
			.withStreak(40, false);
		assertEquals("Dry streak: at least 40 kills (20\u00d7 the rate or more). Your last one came before On Rate started tracking.",
			Texts.streakNote(many, "kill"));
	}
}
