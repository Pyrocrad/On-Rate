package com.expecteddrops;

import com.expecteddrops.stats.RaidChance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Raid formulas from the OSRS Wiki (Ancient chest; Chest (Tombs of Amascut)). */
public class RaidChanceTest
{
	@Test
	public void coxIsOnePercentPer8676Points()
	{
		assertEquals(0.01, RaidChance.cox(8_676, 8_676), 1e-12);
		assertEquals(30_000 / 867_600.0, RaidChance.cox(30_000, 30_000), 1e-12);
	}

	@Test
	public void coxRollIsCappedAt570kAndRollsAgain()
	{
		assertEquals(0.657, RaidChance.coxTeamUniques(570_000), 0.0005);
		// 130,000 more points roll for a second unique
		assertEquals((570_000 + 130_000) / 867_600.0, RaidChance.coxTeamUniques(700_000), 1e-12);
		// at most six rolls
		assertEquals(RaidChance.coxTeamUniques(6 * 570_000), RaidChance.coxTeamUniques(9_000_000), 1e-12);
	}

	@Test
	public void coxShareIsYourShareOfThePoints()
	{
		assertEquals(RaidChance.coxTeamUniques(100_000) / 4, RaidChance.cox(25_000, 100_000), 1e-12);
		assertTrue(Double.isNaN(RaidChance.cox(0, 100_000)));
	}

	@Test
	public void toaScaledRaidLevel()
	{
		assertEquals(300, RaidChance.toaScaledLevel(300), 1e-12);
		assertEquals(340, RaidChance.toaScaledLevel(400), 1e-12);
		assertEquals(350, RaidChance.toaScaledLevel(430), 1e-12);
		assertEquals(360, RaidChance.toaScaledLevel(490), 1e-12);
	}

	@Test
	public void toaWikiExampleLevel400()
	{
		// "at raid level 400, the chance is 1% per 3,700 points"
		assertEquals(3_700, RaidChance.toaPointsPerPercent(400), 1e-9);
		assertEquals(0.05, RaidChance.toa(18_500, 0, 0, 1, 400), 1e-12);
	}

	@Test
	public void toaIsCappedAt55Percent()
	{
		// a solo raid can't reach the cap (64,000 points at most), a big team can
		assertTrue(RaidChance.toa(59_000, 1_500, 0, 1, 600) < 0.55);
		assertEquals(0.55, RaidChance.toaTeamChance(200_000, 600), 1e-12);
		assertEquals(0.55 / 5, RaidChance.toa(40_000, 0, 200_000, 5, 600), 1e-12);
		assertEquals(0.55, RaidChance.toa(170_000, 0, 0, 8, 600), 1e-12);
	}

	@Test
	public void toaSoloIncludesPuzzlePoints()
	{
		assertEquals(10_500 / (10_500 - 20 * 150.0) / 100, RaidChance.toa(9_000, 1_500, 0, 1, 150), 1e-12);
	}

	@Test
	public void toaTeamWithoutTotalUsesYourOwnPoints()
	{
		// below the cap, your share of the team chance is your points over the points per percent
		assertEquals(20_000 / 3_700.0 / 100, RaidChance.toa(20_000, 1_500, 0, 4, 400), 1e-12);
		assertEquals(RaidChance.toa(20_000, 0, 60_000, 4, 400), RaidChance.toa(20_000, 1_500, 0, 4, 400), 1e-12);
	}

	@Test
	public void toaUnknownPointsFallBack()
	{
		assertTrue(Double.isNaN(RaidChance.toa(0, 0, 0, 1, 300)));
		assertTrue(Double.isNaN(RaidChance.toa(10_000, 0, 0, 1, -1)));
	}

	@Test
	public void toaLowLevelItems()
	{
		assertEquals(1.0, RaidChance.toaLevelFactor(149, 50), 0);
		assertEquals(1 / 50.0, RaidChance.toaLevelFactor(149, 150), 1e-12);
		assertEquals(1.0, RaidChance.toaLevelFactor(150, 150), 0);
		assertEquals(1.0, RaidChance.toaLevelFactor(0, null), 0);
	}
}
