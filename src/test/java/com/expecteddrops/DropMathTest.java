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
}
