package com.expecteddrops;

import com.expecteddrops.stats.DropMath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DropMathTest
{
	@Test
	public void parsesRates()
	{
		assertEquals(0.02, DropMath.parseRate("1/50"), 1e-12);
		assertEquals(3.0 / 512, DropMath.parseRate("3/512"), 1e-12);
		assertEquals(1.0 / 1000, DropMath.parseRate("1/1,000"), 1e-12);
		assertEquals(0.25, DropMath.parseRate("0.25"), 1e-12);
		assertEquals(0, DropMath.parseRate(null), 0);
	}

	@Test
	public void chanceAtLeastOneMatchesClosedForm()
	{
		// 1 - (49/50)^50
		assertEquals(1 - Math.pow(49.0 / 50, 50), DropMath.chanceAtLeastOne(1.0 / 50, 50), 1e-12);
		assertEquals(0, DropMath.chanceAtLeastOne(1.0 / 50, 0), 0);
		// at the drop rate you are at about 63%
		assertEquals(0.632, DropMath.chanceAtLeastOne(1.0 / 5000, 5000), 0.001);
	}

	@Test
	public void luckIsHalfAtTheAverage()
	{
		// With mean 1, P(0)=0.3679 and P(1)=0.3679: one drop is exactly mid-pack.
		assertEquals(0.3679 + 0.5 * 0.3679, DropMath.luckPercentile(1, 1.0), 1e-3);
		// Dry: zero drops at mean 3 -> only half of the 5% who also have zero are drier.
		assertEquals(0.5 * Math.exp(-3), DropMath.luckPercentile(0, 3.0), 1e-9);
		// Lucky: 5 drops at mean 1 is luckier than almost everyone.
		assertTrue(DropMath.luckPercentile(5, 1.0) > 0.99);
		assertTrue(Double.isNaN(DropMath.luckPercentile(0, 0)));
	}

	@Test
	public void luckHandlesLargeMeans()
	{
		// Mean far beyond where e^-lambda underflows must still give a sensible answer.
		double luck = DropMath.luckPercentile(800, 800);
		assertTrue(luck > 0.45 && luck < 0.55);
	}

	@Test
	public void valueLuckMatchesCountLuckForOneItem()
	{
		// With a single item, value luck is just count luck in GP.
		double byCount = DropMath.luck(2, 1.3);
		double byValue = DropMath.valueLuck(new double[]{1.3}, new long[]{1_000_000}, 2_000_000, 20000, 42);
		assertEquals(byCount, byValue, 0.02);
	}

	@Test
	public void oneTwistedBowOutweighsManyDryDrops()
	{
		// Expected: 0.5 bows (1.2b each) and 20 cheap drops (1m each); received 1 bow and 5 cheap drops.
		double luck = DropMath.valueLuck(new double[]{0.5, 20}, new long[]{1_200_000_000L, 1_000_000},
			1_205_000_000L, 20000, 42);
		assertTrue(luck > 0.6);
		// ...while by count that player is very dry
		assertTrue(DropMath.luckPercentile(6, 20.5) < 0.01);
		assertTrue(Double.isNaN(DropMath.valueLuck(new double[]{1}, new long[]{0}, 0, 100, 1)));
	}

	@Test
	public void formatsGp()
	{
		assertEquals("1.20b", DropMath.formatGp(1_200_000_000L));
		assertEquals("345.6m", DropMath.formatGp(345_600_000));
		assertEquals("12.3k", DropMath.formatGp(12_300));
	}

	@Test
	public void killsForChance()
	{
		// 50% for a 1/50 drop takes 35 kills, 90% takes 114.
		assertEquals(35, DropMath.killsForChance(1.0 / 50, 0.5));
		assertEquals(114, DropMath.killsForChance(1.0 / 50, 0.9));
	}

	@Test
	public void formatsRates()
	{
		assertEquals("1/50", DropMath.formatRate(1.0 / 50));
		assertEquals("1/5,000", DropMath.formatRate(1.0 / 5000));
		assertEquals("1/170.7", DropMath.formatRate(3.0 / 512));
	}

	@Test
	public void mostCommonResultIsNotDry()
	{
		// 0 of 0.43 expected: 65% of players have none too, so that's on rate, not dry
		assertEquals("On rate", DropMath.verdict(DropMath.luck(0, 0.43)));
		assertEquals(0.5, DropMath.luck(0, 0.43), 1e-12);
		// 0 of 1.65: only 19% have none, so dry (it used to say very dry)
		assertEquals(Math.exp(-1.65), DropMath.luck(0, 1.65), 1e-12);
		assertEquals("Dry", DropMath.verdict(DropMath.luck(0, 1.65)));
		// 4 of 5.67: 33% have this many or fewer
		double[] s = DropMath.shares(4, 5.67);
		assertEquals(s[0] + s[1], DropMath.luck(4, 5.67), 1e-12);
		assertEquals("Dry", DropMath.verdict(DropMath.luck(4, 5.67)));
		// 1 of 0.16 on a trip: 15% get one or more, so spooned, not very spooned
		assertEquals(Math.exp(-0.16), DropMath.luck(1, 0.16), 1e-12);
		assertEquals("Spooned", DropMath.verdict(DropMath.luck(1, 0.16)));
		// big counts: almost no ties, so close to the usual percentile
		assertEquals(DropMath.luckPercentile(89, 92.4), DropMath.luck(89, 92.4), 0.03);
	}

	@Test
	public void sharesAddUp()
	{
		double[] s = DropMath.shares(2, 1.65);
		double more = 1 - s[0] - s[1];
		assertEquals(Math.exp(-1.65) * (1 + 1.65), s[0], 1e-12);
		assertEquals(Math.exp(-1.65) * 1.65 * 1.65 / 2, s[1], 1e-12);
		assertTrue(more > 0 && more < 1);
		assertEquals(null, DropMath.shares(1, 0));
	}
}
