package com.onrate.engine.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class LogForecastTest
{
	@Test
	public void oneItemIsItsOwnDropRate()
	{
		// 1 in 100: half of players have it by 69 raids, 9 in 10 by 230
		double[] p = {0.01};
		assertEquals(69, LogForecast.raidsFor(p, 0.5));
		assertEquals(230, LogForecast.raidsFor(p, 0.9));
	}

	@Test
	public void nothingMissingIsDone()
	{
		assertEquals(0, LogForecast.raidsFor(new double[0], 0.9));
	}

	@Test
	public void anItemThatCantDropNeverFinishes()
	{
		assertEquals(LogForecast.NEVER, LogForecast.raidsFor(new double[]{0.01, 0}, 0.5));
	}

	@Test
	public void moreItemsTakeLonger()
	{
		long one = LogForecast.raidsFor(new double[]{0.01}, 0.5);
		long two = LogForecast.raidsFor(new double[]{0.01, 0.01}, 0.5);
		assertTrue(two > one);
		assertTrue(LogForecast.chanceDone(new double[]{0.01, 0.01}, two) >= 0.5);
		assertTrue(LogForecast.chanceDone(new double[]{0.01, 0.01}, two - 1) < 0.5);
	}

	@Test
	public void matchesSimulatedRaids()
	{
		// a Chambers log at 5% a raid: weights out of 60 for a few uniques still missing
		double[] p = {0.05 * 2 / 60, 0.05 * 2 / 60, 0.05 * 4 / 60, 0.05 * 14 / 60};
		long half = LogForecast.raidsFor(p, 0.5);
		Random random = new Random(42);
		int players = 4000;
		int doneByHalf = 0;
		for (int i = 0; i < players; i++)
		{
			boolean[] got = new boolean[p.length];
			int left = p.length;
			for (long raid = 1; raid <= half && left > 0; raid++)
			{
				// at most one purple a raid, and it is one of the items by its weight
				double roll = random.nextDouble();
				double edge = 0;
				for (int k = 0; k < p.length; k++)
				{
					edge += p[k];
					if (roll < edge)
					{
						if (!got[k])
						{
							got[k] = true;
							left--;
						}
						break;
					}
				}
			}
			if (left == 0)
			{
				doneByHalf++;
			}
		}
		// within a few percent of half
		assertEquals(0.5, doneByHalf / (double) players, 0.04);
	}

	@Test
	public void severalItemsAtDifferentChances()
	{
		// Grotesque Guardians: gloves 1/250, hammer 1/375, core 1/500 (each item is its own roll, chances multiply)
		double[] p = {1 / 250.0, 1 / 375.0, 1 / 500.0};
		assertEquals(592, LogForecast.raidsFor(p, 0.5));
		assertEquals(1334, LogForecast.raidsFor(p, 0.9));
		// with the 1/3000 pet
		double[] withPet = {1 / 250.0, 1 / 375.0, 1 / 500.0, 1 / 3000.0};
		assertEquals(2133, LogForecast.raidsFor(withPet, 0.5));
		assertEquals(6907, LogForecast.raidsFor(withPet, 0.9));
	}
}
