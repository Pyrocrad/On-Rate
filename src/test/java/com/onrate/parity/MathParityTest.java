package com.onrate.parity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import legacy.expecteddrops.stats.DropMath;
import com.onrate.engine.math.Luck;
import com.onrate.engine.math.Poisson;
import com.onrate.engine.math.Rates;
import com.onrate.engine.math.Untradeables;
import com.onrate.engine.math.ValueLuck;
import com.onrate.engine.math.Verdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.Test;

/**
 * The statistics, old against new. {@code DropMath} (the original) is the reference; every
 * function of the rebuilt math layer must return the very same double, bit for bit, for the same
 * input. Grids are dense and cover the extremes (a drop rate of 1 in 20 million up to 400
 * expected, 0 to 120 received).
 */
public class MathParityTest
{
	private static long checks;

	private static void same(String what, double original, double rebuilt)
	{
		checks++;
		if (Double.compare(original, rebuilt) != 0)
		{
			fail(what + ": original " + original + " but rebuilt " + rebuilt);
		}
	}

	private static void sameArray(String what, double[] original, double[] rebuilt)
	{
		checks++;
		if (original == null || rebuilt == null)
		{
			assertEquals(what, original == null, rebuilt == null);
			return;
		}
		assertEquals(what + " length", original.length, rebuilt.length);
		for (int i = 0; i < original.length; i++)
		{
			same(what + "[" + i + "]", original[i], rebuilt[i]);
		}
	}

	/** Expected counts from 1e-6 to 400, log-spaced, plus awkward values. */
	private static List<Double> expectedValues()
	{
		List<Double> list = new ArrayList<>();
		for (double e = 1e-6; e < 450; e *= 1.37)
		{
			list.add(e);
		}
		list.addAll(Arrays.asList(0.0, -1.0, Double.NaN, 0.5, 1.0, 2.0, 30.0, 30.0000001, 100.0));
		return list;
	}

	@Test
	public void rateParsingAndFormatting()
	{
		String[] rates = {null, "", "  ", "1/50", "3/512", "7/2448", " 1 / 1,024 ", "0.02", "0", "1/3", "2.5/100", "1/1"};
		for (String r : rates)
		{
			same("parse " + r, DropMath.parseRate(r), Rates.parse(r));
		}
		for (String bad : new String[]{"1/0", "1/-5", "abc", "1/x"})
		{
			String a = null;
			String b = null;
			try
			{
				DropMath.parseRate(bad);
			}
			catch (RuntimeException e)
			{
				a = e.getClass().getName();
			}
			try
			{
				Rates.parse(bad);
			}
			catch (RuntimeException e)
			{
				b = e.getClass().getName();
			}
			assertEquals("exception for " + bad, a, b);
		}
		for (double d = 1e-7; d < 1e4; d *= 1.21)
		{
			assertEquals(DropMath.formatRate(d), Rates.format(d));
		}
		assertEquals(DropMath.formatRate(0), Rates.format(0));
		assertEquals(DropMath.formatRate(-3), Rates.format(-3));
		for (double gp = -5e9; gp < 5e9; gp += 7_654_321.5)
		{
			assertEquals(DropMath.formatGp(gp), Rates.gp(gp));
		}
		for (double gp : new double[]{0, 1, 999, 999.5, 1000, 1_000_000, 999_999_999, 1_000_000_000, 2.5e12})
		{
			assertEquals(DropMath.formatGp(gp), Rates.gp(gp));
		}
	}

	@Test
	public void sharesLuckPercentileAndCaps()
	{
		for (double expected : expectedValues())
		{
			for (int received = -2; received <= 120; received++)
			{
				sameArray("shares " + received + "/" + expected, DropMath.shares(received, expected), Poisson.shares(received, expected));
				same("luck " + received + "/" + expected, DropMath.luck(received, expected), Luck.of(received, expected));
				same("midP " + received + "/" + expected, DropMath.luckPercentile(received, expected), Luck.midP(received, expected));
				for (int cap = 1; cap <= 3; cap++)
				{
					sameArray("sharesCapped " + received + "/" + expected + "/" + cap,
						DropMath.sharesCapped(received, expected, cap), Poisson.sharesCapped(received, expected, cap));
					same("luckCapped " + received + "/" + expected + "/" + cap,
						DropMath.luckCapped(received, expected, cap), Luck.ofCapped(received, expected, cap));
				}
			}
			for (int cap = 0; cap <= 4; cap++)
			{
				same("expectedCapped " + expected + "/" + cap, DropMath.expectedCapped(expected, cap), Poisson.expectedCapped(expected, cap));
				same("expectedValue " + expected + "/" + cap, DropMath.expectedValue(expected, cap), Poisson.expectedCounted(expected, cap));
			}
			same("chance " + expected, DropMath.chanceAtLeastOne(expected), Poisson.chanceAtLeastOne(expected));
		}
		for (double fewer = 0; fewer <= 1; fewer += 0.013)
		{
			for (double same = 0; same <= 1; same += 0.017)
			{
				same("tieAware", DropMath.tieAware(fewer, same), Luck.tieAware(fewer, same));
			}
		}
	}

	@Test
	public void chancesAndKillsNeeded()
	{
		for (double rate : new double[]{0, -1, 1e-7, 1 / 5000.0, 1 / 181.0, 1 / 50.0, 0.3, 0.999, 1, 2})
		{
			for (long kc : new long[]{-5, 0, 1, 2, 50, 1000, 100_000, 5_000_000})
			{
				same("chance " + rate + "/" + kc, DropMath.chanceAtLeastOne(rate, kc), Poisson.chanceAtLeastOne(rate, kc));
			}
			for (double p : new double[]{0, -0.1, 0.5, 0.9, 0.99, 0.999999})
			{
				assertEquals(DropMath.killsForChance(rate, p), Poisson.killsForChance(rate, p));
			}
		}
	}

	@Test
	public void verdictBandsAndEveryBoundary()
	{
		List<Double> lucks = new ArrayList<>();
		for (double l = -0.01; l <= 1.01; l += 0.0005)
		{
			lucks.add(l);
		}
		for (double edge : new double[]{0.01, 0.1, 0.35, 0.45, 0.55, 0.65, 0.9, 0.99, 0, 1, 0.5})
		{
			lucks.add(edge);
			lucks.add(Math.nextUp(edge));
			lucks.add(Math.nextDown(edge));
		}
		lucks.add(Double.NaN);
		for (double luck : lucks)
		{
			assertEquals("verdict " + luck, DropMath.verdict(luck), Verdict.labelOf(luck));
			assertEquals("onRate " + luck, DropMath.isOnRate(luck), Verdict.isOnRate(luck));
		}
		assertEquals(DropMath.TOO_EARLY_BELOW, Verdict.TOO_EARLY_BELOW, 0);
		assertEquals(DropMath.DRY_BELOW, Verdict.DRY_BELOW, 0);
		assertEquals(DropMath.SPOONED_ABOVE, Verdict.SPOONED_ABOVE, 0);
		assertEquals(DropMath.VERDICTS, Verdict.labels());
		assertEquals(DropMath.ON_RATE, Verdict.ON_RATE.label());
	}

	@Test
	public void sumsOfCappedAndGuaranteedItems()
	{
		Random random = new Random(20261107);
		for (int trial = 0; trial < 4000; trial++)
		{
			int sure = random.nextInt(3) == 0 ? random.nextInt(3) : 0;
			double plain = random.nextInt(4) == 0 ? 0 : random.nextDouble() * (random.nextBoolean() ? 3 : 40);
			int n = random.nextInt(4);
			double[] means = new double[n];
			int[] caps = new int[n];
			for (int i = 0; i < n; i++)
			{
				means[i] = random.nextInt(5) == 0 ? 0 : Math.exp(random.nextDouble() * 8 - 6);
				caps[i] = random.nextInt(3) + 1;
			}
			int g = random.nextInt(3) == 0 ? 1 + random.nextInt(2) : 0;
			List<double[]> guaranteed = new ArrayList<>();
			for (int i = 0; i < g; i++)
			{
				guaranteed.add(new double[]{random.nextInt(300), 1 / (20.0 + random.nextInt(200)), 50});
			}
			int received = random.nextInt(25) - 1;
			String tag = "trial " + trial;
			sameArray(tag + " sharesSum", DropMath.sharesSum(received, sure, plain, means, caps, guaranteed),
				Poisson.sharesSum(received, sure, plain, means, caps, guaranteed));
			sameArray(tag + " sharesSum5", DropMath.sharesSum(received, sure, plain, means, caps),
				Poisson.sharesSum(received, sure, plain, means, caps));
		}
		assertNull(Poisson.sharesSum(0, 0, 0, new double[]{0}, new int[]{1}));
		sameArray("nulls", DropMath.sharesSum(1, 1, 0.98, null, null), Poisson.sharesSum(1, 1, 0.98, null, null));
	}

	@Test
	public void guaranteedKill()
	{
		for (double kills : new double[]{0, 1, 49, 49.4, 50, 50.6, 51, 100, 150, 400})
		{
			for (double rate : new double[]{0, 1 / 100.0, 1 / 50.0, 1 / 20.0, 0.5, 1})
			{
				same("guaranteedExpected " + kills + "/" + rate, DropMath.guaranteedExpected(kills, rate, 50),
					Poisson.guaranteedExpected(kills, rate, 50));
				for (int max = 0; max <= 9; max++)
				{
					sameArray("guaranteedPmf " + kills + "/" + rate + "/" + max, DropMath.guaranteedPmf(kills, rate, 50, max),
						Poisson.guaranteedPmf(kills, rate, 50, max));
				}
			}
		}
	}

	@Test
	public void goldValueLuckSimulation()
	{
		Random random = new Random(7777);
		for (int trial = 0; trial < 150; trial++)
		{
			int n = 1 + random.nextInt(9);
			double[] expected = new double[n];
			long[] prices = new long[n];
			int[] caps = new int[n];
			for (int i = 0; i < n; i++)
			{
				expected[i] = random.nextInt(6) == 0 ? 0 : Math.exp(random.nextDouble() * 7 - 4.5);
				prices[i] = random.nextInt(5) == 0 ? 0 : (long) (Math.exp(random.nextDouble() * 12) * 100);
				caps[i] = random.nextInt(3) == 0 ? 1 + random.nextInt(2) : 0;
			}
			long received = random.nextBoolean() ? 0 : (long) (random.nextDouble() * 5e7);
			long seed = random.nextBoolean() ? 1234567L : random.nextLong();
			int samples = trial % 10 == 0 ? 5000 : 800;
			same("valueLuck " + trial, DropMath.valueLuck(expected, prices, caps, received, samples, seed),
				ValueLuck.of(expected, prices, caps, received, samples, seed));
			same("valueLuck(no caps) " + trial, DropMath.valueLuck(expected, prices, received, samples, seed),
				ValueLuck.of(expected, prices, received, samples, seed));
		}
		assertEquals(5000, ValueLuck.SAMPLES);
		assertEquals(1234567L, ValueLuck.SEED);
	}

	@Test
	public void halberdPieces()
	{
		for (String name : new String[]{"Noxious point", "noxious BLADE", "Noxious pommel", "Noxious halberd", null, ""})
		{
			assertEquals(DropMath.isHalberdPiece(name), Untradeables.isHalberdPiece(name));
		}
		assertEquals(DropMath.halberdPieceCount(), Untradeables.halberdPieceCount());
		for (int pieces = 0; pieces < 12; pieces++)
		{
			assertEquals(DropMath.pieceSetValue(pieces, 4_500_000), Untradeables.setValue(pieces, 4_500_000));
		}
	}

	@Test
	public void reportsHowManyComparisons()
	{
		// keeps the count honest in the build log
		System.out.println("math parity: " + checks + " comparisons so far in this class");
	}
}
