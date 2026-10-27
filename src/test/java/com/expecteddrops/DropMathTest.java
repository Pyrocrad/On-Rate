package com.expecteddrops;

import com.expecteddrops.stats.DropMath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
	public void middleBandIsSplitInThree()
	{
		assertEquals("A bit dry", DropMath.verdict(0.40));
		assertEquals("On rate", DropMath.verdict(0.50));
		assertEquals("A bit spooned", DropMath.verdict(0.60));
		// right on a line: the side nearer the middle, as the dry and spooned lines always were
		assertEquals("Dry", DropMath.verdict(0.3499));
		assertEquals("A bit dry", DropMath.verdict(0.35));
		assertEquals("A bit dry", DropMath.verdict(0.4499));
		assertEquals("On rate", DropMath.verdict(0.45));
		assertEquals("On rate", DropMath.verdict(0.55));
		assertEquals("A bit spooned", DropMath.verdict(0.5501));
		assertEquals("A bit spooned", DropMath.verdict(0.65));
		assertEquals("Spooned", DropMath.verdict(0.6501));
		// the 10% and 90% lines are unchanged
		assertEquals("Very dry", DropMath.verdict(0.0999));
		assertEquals("Dry", DropMath.verdict(0.10));
		assertEquals("Spooned", DropMath.verdict(0.90));
		assertEquals("Very spooned", DropMath.verdict(0.9001));
		assertEquals("", DropMath.verdict(Double.NaN));
		// every word comes from the one list
		for (double luck = 0; luck <= 1; luck += 0.01)
		{
			assertTrue(DropMath.VERDICTS.contains(DropMath.verdict(luck)));
		}
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

	@Test
	public void sharesSumWithoutCapsIsPoisson()
	{
		for (int r = 0; r < 6; r++)
		{
			double[] plain = DropMath.shares(r, 2.4);
			double[] sum = DropMath.sharesSum(r, 0, 2.4, new double[0], new int[0]);
			assertEquals(plain[0], sum[0], 1e-15);
			assertEquals(plain[1], sum[1], 1e-15);
		}
	}

	@Test
	public void sharesSumMatchesBruteForce()
	{
		// Poisson(1.3) plus min(Poisson(2), 1) plus min(Poisson(0.7), 3), by hand
		double[] means = {2.0, 0.7};
		int[] caps = {1, 3};
		double[][] items = new double[2][];
		for (int i = 0; i < 2; i++)
		{
			items[i] = new double[caps[i] + 1];
			double rest = 1;
			for (int k = 0; k < caps[i]; k++)
			{
				items[i][k] = poisson(k, means[i]);
				rest -= items[i][k];
			}
			items[i][caps[i]] = rest;
		}
		double[] total = new double[12];
		for (int a = 0; a < 8; a++)
		{
			for (int b = 0; b <= 1; b++)
			{
				for (int c = 0; c <= 3; c++)
				{
					total[a + b + c] += poisson(a, 1.3) * items[0][b] * items[1][c];
				}
			}
		}
		double below = 0;
		for (int r = 0; r < 6; r++)
		{
			double[] s = DropMath.sharesSum(r, 0, 1.3, means, caps);
			assertEquals(below, s[0], 1e-6);
			assertEquals(total[r], s[1], 1e-6);
			below += total[r];
		}
	}

	@Test
	public void sureCopiesShiftTheCount()
	{
		// one sure copy and 0.98 expected besides: 1 is the fewest anyone can have
		double[] one = DropMath.sharesSum(1, 1, 0.98, null, null);
		assertEquals(0, one[0], 1e-15);
		assertEquals(Math.exp(-0.98), one[1], 1e-15);
		double[] none = DropMath.sharesSum(0, 1, 0.98, null, null);
		assertEquals(0, none[0] + none[1], 1e-15);
		assertTrue(DropMath.sharesSum(0, 0, 0, new double[]{0}, new int[]{1}) == null);
	}

	@Test
	public void valueLuckCountsCappedItemsOnce()
	{
		// an item you can only have once, 3 expected: having it is what 95% of players have
		double capped = DropMath.valueLuck(new double[]{3}, new long[]{100}, new int[]{1}, 100, 5000, 1L);
		assertEquals(0.5, capped, 1e-12);
		// as if copies piled up, having one would look dry
		double uncapped = DropMath.valueLuck(new double[]{3}, new long[]{100}, 100, 5000, 1L);
		assertTrue(uncapped < 0.3);
		assertEquals(DropMath.expectedCapped(3, 1), DropMath.expectedValue(3, 1), 1e-15);
		assertEquals(3, DropMath.expectedValue(3, 0), 1e-15);
	}

	private static double poisson(int k, double mean)
	{
		double p = Math.exp(-mean);
		for (int i = 1; i <= k; i++)
		{
			p *= mean / i;
		}
		return p;
	}

	// Golden values below were worked out by hand from the Poisson / geometric formulas.

	@Test
	public void goldenChanceAtLeastOne()
	{
		// 1 - (127/128)^100
		assertEquals(0.5435690025908804, DropMath.chanceAtLeastOne(1.0 / 128, 100), 1e-12);
		// Poisson: 1 - e^-1
		assertEquals(0.6321205588285577, DropMath.chanceAtLeastOne(1.0), 1e-12);
		// no kills, no rate, a certain rate
		assertEquals(0, DropMath.chanceAtLeastOne(1.0 / 128, 0), 0);
		assertEquals(0, DropMath.chanceAtLeastOne(1.0 / 128, -5), 0);
		assertEquals(0, DropMath.chanceAtLeastOne(0, 1000), 0);
		assertEquals(1, DropMath.chanceAtLeastOne(1, 1), 0);
		assertEquals(1, DropMath.chanceAtLeastOne(1.5, 3), 0);
		assertEquals(0, DropMath.chanceAtLeastOne(0), 0);
		assertEquals(0, DropMath.chanceAtLeastOne(-1), 0);
	}

	@Test
	public void chanceAtLeastOneStaysExactInTheTails()
	{
		// a tiny rate must not round to zero (1 - (1 - 1e-12) would)
		assertEquals(1e-12, DropMath.chanceAtLeastOne(1e-12, 1), 1e-20);
		assertEquals(1e-9, DropMath.chanceAtLeastOne(1e-12, 1000), 1e-17);
		// a huge kill count saturates at 1 without NaN
		assertEquals(1, DropMath.chanceAtLeastOne(1.0 / 5000, 10_000_000), 0);
		assertEquals(1, DropMath.chanceAtLeastOne(1.0 / 5000, Long.MAX_VALUE), 0);
	}

	@Test
	public void goldenKillsForChance()
	{
		// 50% needs ceil(ln 0.5 / ln(127/128)) = 89 kills at 1/128
		assertEquals(89, DropMath.killsForChance(1.0 / 128, 0.5));
		assertTrue(DropMath.chanceAtLeastOne(1.0 / 128, 89) >= 0.5);
		assertTrue(DropMath.chanceAtLeastOne(1.0 / 128, 88) < 0.5);
		assertEquals(-1, DropMath.killsForChance(0, 0.5));
		assertEquals(-1, DropMath.killsForChance(1.0 / 128, 0));
		assertEquals(1, DropMath.killsForChance(1, 0.5));
	}

	@Test
	public void goldenShares()
	{
		// no drops at mean 3: nobody has fewer, e^-3 have exactly none
		double[] none = DropMath.shares(0, 3.0);
		assertEquals(0, none[0], 0);
		assertEquals(Math.exp(-3), none[1], 1e-12);
		// 2 drops at mean 2: fewer = e^-2 (1 + 2), same = e^-2 * 2
		double[] two = DropMath.shares(2, 2.0);
		assertEquals(0.4060058497098381, two[0], 1e-12);
		assertEquals(0.2706705664732254, two[1], 1e-12);
		// negative received counts as none
		assertEquals(none[1], DropMath.shares(-4, 3.0)[1], 0);
		assertNull(DropMath.shares(1, 0));
		assertNull(DropMath.shares(1, Double.NaN));
		assertTrue(Double.isNaN(DropMath.luck(1, 0)));
		assertTrue(Double.isNaN(DropMath.luckPercentile(1, Double.NaN)));
	}

	@Test
	public void percentileIsBetweenZeroAndOneAndRisesWithDrops()
	{
		double last = -1;
		for (int received = 0; received <= 30; received++)
		{
			double luck = DropMath.luckPercentile(received, 5.0);
			assertTrue(luck >= 0 && luck <= 1);
			assertTrue("luck must not fall as drops rise", luck > last);
			last = luck;
		}
		// far in the tail of a huge mean there is no NaN or infinity
		assertTrue(DropMath.luckPercentile(2000, 1000) > 0.999999);
		assertEquals(0, DropMath.luckPercentile(0, 1000), 1e-300);
	}

	@Test
	public void cappedCountsStopAtTheCap()
	{
		// E[min(X,1)] = 1 - e^-lambda
		assertEquals(0.6321205588285577, DropMath.expectedCapped(1.0, 1), 1e-12);
		assertEquals(0, DropMath.expectedCapped(0, 3), 0);
		assertEquals(0, DropMath.expectedCapped(2.0, 0), 0);
		// a cap far above the mean changes nothing
		assertEquals(2.0, DropMath.expectedCapped(2.0, 60), 1e-9);
		assertEquals(2.0, DropMath.expectedValue(2.0, 0), 0);
		assertEquals(DropMath.expectedCapped(2.0, 1), DropMath.expectedValue(2.0, 1), 0);
		// one pet: you either have it or not; having it is as good as it gets
		double[] have = DropMath.sharesCapped(1, 1.0, 1);
		assertEquals(Math.exp(-1), have[0], 1e-12);
		assertEquals(1 - Math.exp(-1), have[1], 1e-12);
		// more than the cap is the same as the cap
		assertEquals(have[0], DropMath.sharesCapped(5, 1.0, 1)[0], 0);
		double[] dry = DropMath.sharesCapped(0, 1.0, 1);
		assertEquals(0, dry[0], 0);
		assertEquals(Math.exp(-1), dry[1], 1e-12);
	}

	@Test
	public void guaranteedCopiesShiftTheDistribution()
	{
		// 1 sure copy plus Poisson(1): with 1 copy in total nobody has fewer, e^-1 have as few
		double[] one = DropMath.sharesSum(1, 1, 1.0, null, null);
		assertEquals(0, one[0], 0);
		assertEquals(Math.exp(-1), one[1], 1e-12);
		// 2 in total is one more than the sure copy: fewer = e^-1, same = e^-1
		double[] two = DropMath.sharesSum(2, 1, 1.0, null, null);
		assertEquals(Math.exp(-1), two[0], 1e-12);
		assertEquals(Math.exp(-1), two[1], 1e-12);
		// fewer than the sure copies is impossible
		double[] impossible = DropMath.sharesSum(0, 1, 1.0, null, null);
		assertEquals(0, impossible[0], 0);
		assertEquals(0, impossible[1], 0);
		// nothing expected at all
		assertNull(DropMath.sharesSum(0, 0, 0, new double[]{0}, new int[]{1}));
	}

	@Test
	public void guaranteedKillOnlyHelpsAPlayerWithNone()
	{
		double q = 0.98;
		// before the guaranteed kill it is a plain binomial
		double[] early = DropMath.guaranteedPmf(49, 0.02, 50, 3);
		assertEquals(Math.pow(q, 49), early[0], 1e-12);
		// at the guaranteed kill nobody has none, and the mean is kills * rate + 0.98^50
		double[] pmf = DropMath.guaranteedPmf(50, 0.02, 50, 60);
		double sum = 0;
		double mean = 0;
		for (int k = 0; k < pmf.length; k++)
		{
			sum += pmf[k];
			mean += k * pmf[k];
		}
		assertEquals(0, pmf[0], 0);
		assertEquals(1, sum, 1e-12);
		assertEquals(1 + Math.pow(q, 50), mean, 1e-12);
		assertEquals(1 + Math.pow(q, 50), DropMath.guaranteedExpected(50, 0.02, 50), 1e-12);
		assertEquals(49 / 50.0, DropMath.guaranteedExpected(49, 0.02, 50), 1e-12);
		// exactly one head: none in 1-49 then a miss never happens; one in 1-49 and a miss at 50
		double one = Math.pow(q, 49) + 49 * 0.02 * Math.pow(q, 48) * q;
		assertEquals(one, pmf[1], 1e-12);
	}

	@Test
	public void cappedPartsInASumAddUpExactly()
	{
		// a plain item plus a pet that counts once: P(total = 0) = e^-a * e^-b
		double[] zero = DropMath.sharesSum(0, 0, 2.0, new double[]{1.0}, new int[]{1});
		assertEquals(0, zero[0], 0);
		assertEquals(Math.exp(-3), zero[1], 1e-12);
		// the pet can only add one, so a total of 1 is P(plain=1)*P(pet=0) + P(plain=0)*P(pet>=1)
		double[] one = DropMath.sharesSum(1, 0, 2.0, new double[]{1.0}, new int[]{1});
		double same = 2 * Math.exp(-2) * Math.exp(-1) + Math.exp(-2) * (1 - Math.exp(-1));
		assertEquals(Math.exp(-3), one[0], 1e-12);
		assertEquals(same, one[1], 1e-12);
	}

	@Test
	public void verdictBoundariesGoToTheSideNearerTheMiddle()
	{
		assertEquals("", DropMath.verdict(Double.NaN));
		assertEquals("Extremely dry", DropMath.verdict(0.0));
		assertEquals("Very dry", DropMath.verdict(0.01));
		assertEquals("Very dry", DropMath.verdict(0.0999));
		assertEquals("Dry", DropMath.verdict(0.10));
		assertEquals("Dry", DropMath.verdict(0.3499));
		assertEquals("A bit dry", DropMath.verdict(0.35));
		assertEquals("A bit dry", DropMath.verdict(0.4499));
		assertEquals("On rate", DropMath.verdict(0.45));
		assertEquals("On rate", DropMath.verdict(0.55));
		assertEquals("A bit spooned", DropMath.verdict(0.5501));
		assertEquals("A bit spooned", DropMath.verdict(0.65));
		assertEquals("Spooned", DropMath.verdict(0.6501));
		assertEquals("Spooned", DropMath.verdict(0.90));
		assertEquals("Very spooned", DropMath.verdict(0.9001));
		assertEquals("Very spooned", DropMath.verdict(0.99));
		assertEquals("Extremely spooned", DropMath.verdict(1.0));
	}

	@Test
	public void extremeTiersSitOutsideOnePercent()
	{
		assertEquals("Extremely dry", DropMath.verdict(0.0));
		assertEquals("Extremely dry", DropMath.verdict(0.0099));
		assertEquals("Very dry", DropMath.verdict(0.01));
		assertEquals("Very spooned", DropMath.verdict(0.99));
		assertEquals("Extremely spooned", DropMath.verdict(0.9901));
		assertEquals("Extremely spooned", DropMath.verdict(1.0));
		assertEquals("", DropMath.verdict(Double.NaN));
		for (double luck : new double[]{0.0, 0.0099, 0.01, 0.5, 0.99, 0.9901, 1.0})
		{
			assertTrue(DropMath.VERDICTS.contains(DropMath.verdict(luck)));
		}
		// real counts: none at 4.7 expected, and one at 0.005 expected
		assertEquals("Extremely dry", DropMath.verdict(DropMath.luck(0, 4.7)));
		assertEquals("Extremely spooned", DropMath.verdict(DropMath.luck(1, 0.005)));
	}

	@Test
	public void onRateIsFoundByNameNotPlace()
	{
		assertTrue(DropMath.VERDICTS.contains(DropMath.ON_RATE));
		assertEquals(DropMath.ON_RATE, DropMath.verdict(0.5));
		assertTrue(DropMath.isOnRate(0.5));
		assertFalse(DropMath.isOnRate(0.44));
		assertFalse(DropMath.isOnRate(Double.NaN));
		// the middle of the list, with as many words on each side
		int middle = DropMath.VERDICTS.indexOf(DropMath.ON_RATE);
		assertEquals(DropMath.VERDICTS.size() - 1 - middle, middle);
	}

	@Test
	public void parsesOddRates()
	{
		assertEquals(0, DropMath.parseRate("  "), 0);
		assertEquals(1.0 / 25_000, DropMath.parseRate(" 1 / 25,000 "), 1e-15);
		try
		{
			DropMath.parseRate("1/0");
			throw new AssertionError("a rate over zero should be refused");
		}
		catch (IllegalArgumentException expected)
		{
			// fine
		}
	}
}
