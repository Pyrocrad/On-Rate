package com.onrate.engine;

import com.onrate.engine.stats.RaidChance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Raid formulas from the OSRS Wiki (Ancient chest; Chest (Tombs of Amascut)). */
public class RaidChanceTest
{
	@Test
	public void toaPetHasItsOwnLevelScaling()
	{
		// wiki: 1% per (350,000 - 700 x level); full up to 400, a third above, at most 550
		assertEquals(140_000, RaidChance.toaPetPointsPerPercent(300), 1e-9);
		assertEquals(70_000, RaidChance.toaPetPointsPerPercent(400), 1e-9);
		assertEquals(350_000 - 700 * (400 + 100 / 3.0), RaidChance.toaPetPointsPerPercent(500), 1e-6);
		assertEquals(RaidChance.toaPetPointsPerPercent(550), RaidChance.toaPetPointsPerPercent(600), 1e-9);
		// at level 400 the pet is about 1/19 of the purple chance, not 1/30
		double ratio = RaidChance.toaPet(30_000, 0, 0, 1, 400) / RaidChance.toa(30_000, 0, 0, 1, 400);
		assertEquals(3_700 / 70_000.0, ratio, 1e-9);
	}

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

	@Test
	public void fracturedArchiveTeamBonusAddsFourPercentPerPlayerBeyondTwo()
	{
		// Jagex's loot post: 3-man x1.04, 5-man x1.12, 8-man x1.24 (added, not compounded)
		assertEquals(1.0, RaidChance.tfaTeamMultiplier(1), 1e-12);
		assertEquals(1.0, RaidChance.tfaTeamMultiplier(2), 1e-12);
		assertEquals(1.04, RaidChance.tfaTeamMultiplier(3), 1e-12);
		assertEquals(1.12, RaidChance.tfaTeamMultiplier(5), 1e-12);
		assertEquals(1.24, RaidChance.tfaTeamMultiplier(8), 1e-12);
		assertEquals(1.24, RaidChance.tfaTeamMultiplier(12), 1e-12);
		// the wiki's 8-man team chance: 8.86%
		assertEquals(0.0886, RaidChance.tfa(700, 8, RaidChance.TFA_BASE_CHANCE), 0.00005);
	}

	@Test
	public void fracturedArchiveSplitsTheUniqueByContribution()
	{
		double base = RaidChance.TFA_BASE_CHANCE;
		// a duo with even damage: 1 in 14 for the team, half each
		assertEquals(1 / 28.0, RaidChance.tfaEven(2, 0, base), 1e-12);
		assertEquals(1 / 14.0, RaidChance.tfaEven(1, 0, base), 1e-12);
		// 30% of the contribution is 30% of the team's purples
		assertEquals(0.3 / 14, RaidChance.tfa(0.3 * 700, 2, base), 1e-12);
		// a four with even damage: 1.08 / 14 for the team, a quarter each
		assertEquals(1.08 / 14 / 4, RaidChance.tfaEven(4, 0, base), 1e-12);
		assertTrue(Double.isNaN(RaidChance.tfa(-1, 2, base)));
		assertEquals(base, RaidChance.tfa(900, 2, base), 1e-12);
	}

	@Test
	public void fracturedArchiveDeathTakesTenPercentOfExpectedContribution()
	{
		// "In a duo, your expected contribution is 350 out of 700, so a death would subtract 35"
		assertEquals(350, RaidChance.tfaExpectedContribution(2), 1e-12);
		assertEquals(315, RaidChance.tfaContributionAfterDeaths(350, 2, 1), 1e-12);
		// the same 35 whoever carried more damage
		assertEquals(565, RaidChance.tfaContributionAfterDeaths(600, 2, 1), 1e-12);
		assertEquals(0, RaidChance.tfaContributionAfterDeaths(20, 2, 1), 1e-12);
		// unknown deaths count as none
		assertEquals(350, RaidChance.tfaContributionAfterDeaths(350, 2, -1), 1e-12);
		assertEquals(315 / 700.0 / 14, RaidChance.tfaEven(2, 1, RaidChance.TFA_BASE_CHANCE), 1e-12);
	}
}
