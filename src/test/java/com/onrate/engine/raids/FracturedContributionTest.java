package com.onrate.engine.raids;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class FracturedContributionTest
{
	private static final int GUARDIAN = 16659;
	private static final int BORK = 16640;
	private static final int SMORK = 16641;
	private static final int MOIA = 16667;
	private static final int UNDEAD_BINDER = 16670;
	private static final int XARTH = 16684;
	private static final int OCELLUS_P1 = 16673;
	private static final int OCELLUS_P3 = 16677;

	private static void hit(FracturedContribution c, int npc, int mine, int others)
	{
		c.onHitsplat(npc, mine, true, false);
		c.onHitsplat(npc, others, false, true);
	}

	@Test
	public void eachRoomSplitsItsContributionByDamage()
	{
		FracturedContribution c = new FracturedContribution();
		hit(c, GUARDIAN, 600, 400);          // 60 of 100
		hit(c, BORK, 300, 300);              // Bork and his Smorks are one room
		hit(c, SMORK, 100, 300);             // 400 of 1,000: 40
		hit(c, MOIA, 500, 500);
		hit(c, UNDEAD_BINDER, 0, 0);         // 50 (zero hits change nothing)
		hit(c, XARTH, 1_000, 0);             // 100
		hit(c, OCELLUS_P1, 1_000, 1_000);    // Ocellus is 300 over all his phases
		hit(c, OCELLUS_P3, 2_000, 6_000);    // 3,000 of 10,000: 90
		FracturedContribution.Result r = c.finish();
		assertEquals(60 + 40 + 50 + 100 + 90, r.contribution, 1e-9);
		assertEquals(0, r.deaths);
		assertFalse(c.isActive());
	}

	@Test
	public void aMissedFightLeavesTheContributionUnknown()
	{
		FracturedContribution c = new FracturedContribution();
		hit(c, BORK, 500, 500);
		hit(c, MOIA, 500, 500);
		hit(c, XARTH, 500, 500);
		hit(c, OCELLUS_P3, 500, 500);
		c.onLocalDeath();
		FracturedContribution.Result r = c.finish();
		assertEquals(-1, r.contribution, 0);
		assertEquals(1, r.deaths);
		assertNull(c.finish());
	}

	@Test
	public void deathsCountOnlyDuringARaidAndTheGuardianStartsANewOne()
	{
		FracturedContribution c = new FracturedContribution();
		c.onLocalDeath();
		assertFalse(c.isActive());
		// a wiped raid: never completed
		hit(c, GUARDIAN, 100, 100);
		hit(c, BORK, 100, 100);
		c.onLocalDeath();
		// the next raid starts at the Archive Guardian again
		hit(c, GUARDIAN, 1_000, 0);
		hit(c, BORK, 0, 1_000);
		hit(c, MOIA, 1_000, 0);
		hit(c, XARTH, 0, 1_000);
		hit(c, OCELLUS_P3, 1_000, 1_000);
		FracturedContribution.Result r = c.finish();
		assertEquals(100 + 0 + 100 + 0 + 150, r.contribution, 1e-9);
		assertEquals(0, r.deaths);
	}

	@Test
	public void ownerlessDamageAndOtherMonstersDontCount()
	{
		FracturedContribution c = new FracturedContribution();
		c.onHitsplat(GUARDIAN, 50, false, false); // poison, venom, burn
		c.onHitsplat(1, 50, true, false);        // not an Archive monster
		assertFalse(c.isActive());
	}
}
