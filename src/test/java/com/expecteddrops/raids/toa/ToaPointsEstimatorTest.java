package com.expecteddrops.raids.toa;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

/**
 * Checks the Tombs of Amascut points estimate (ported from Raid Data Tracker / the Tombs of
 * Amascut plugin) follows its rules.
 */
public class ToaPointsEstimatorTest
{
	/** Any monster without its own factor counts 1 point per damage. */
	private static final int PLAIN_NPC = 1;

	private ToaPointsEstimator points;

	@Before
	public void setUp()
	{
		points = new ToaPointsEstimator();
		points.onGameMessage(ToaPointsEstimator.START_MESSAGE + ".");
		points.setTeamSize(1);
	}

	@Test
	public void startsAtZeroWithoutTheBasePoints()
	{
		assertEquals(0, points.getPersonalTotalPoints());
	}

	@Test
	public void onlyYourOwnHitsCount()
	{
		points.onHitsplat(PLAIN_NPC, 40, true, RaidRoom.AKKHA);
		points.onHitsplat(PLAIN_NPC, 40, false, RaidRoom.AKKHA);
		points.onHitsplat(PLAIN_NPC, 0, true, RaidRoom.AKKHA);
		assertEquals(40, points.getPersonalTotalPoints());
	}

	@Test
	public void monstersHaveTheirOwnFactor()
	{
		points.onHitsplat(NpcID.TOA_BABA, 50, true, RaidRoom.BABA);             // x2.0
		points.onHitsplat(NpcID.TOA_BABA_BOULDER, 50, true, RaidRoom.BABA);     // x0
		points.onHitsplat(NpcID.TOA_ZEBAK, 10, true, RaidRoom.ZEBAK);           // x1.5
		points.onHitsplat(NpcID.TOA_KEPHRI_GUARDIAN_MELEE, 11, true, RaidRoom.KEPHRI); // x0.5, rounded down
		points.onHitsplat(NpcID.TOA_HET_GOAL_VULNERABLE, 4, true, RaidRoom.HET); // x2.5
		points.onHitsplat(NpcID.TOA_PATH_APMEKEN_BABOON_MELEE_1, 10, true, RaidRoom.BABA); // x1.2
		assertEquals(100 + 0 + 15 + 5 + 10 + 12, points.getPersonalTotalPoints());
	}

	@Test
	public void roomsAreCappedAt20kAndCrondisAt10k()
	{
		for (int i = 0; i < 300; i++)
		{
			points.onHitsplat(PLAIN_NPC, 100, true, RaidRoom.AKKHA);
		}
		assertEquals(20_000, points.getPersonalTotalPoints());

		// a finished room starts a new room cap
		points.onGameMessage(ToaPointsEstimator.ROOM_FINISH_MESSAGE + ": Akkha.");
		for (int i = 0; i < 300; i++)
		{
			points.onHitsplat(PLAIN_NPC, 100, true, RaidRoom.CRONDIS);
		}
		assertEquals(30_000, points.getPersonalTotalPoints());
	}

	@Test
	public void totalIsCappedAt64kIncludingTheBase()
	{
		for (int room = 0; room < 5; room++)
		{
			for (int i = 0; i < 200; i++)
			{
				points.onHitsplat(PLAIN_NPC, 100, true, RaidRoom.WARDENS);
			}
			points.onGameMessage(ToaPointsEstimator.ROOM_FINISH_MESSAGE);
		}
		assertEquals(64_000 - 5_000, points.getPersonalTotalPoints());
	}

	@Test
	public void deathsCost20PercentButAtLeast1000()
	{
		points.onHitsplat(PLAIN_NPC, 10_000, true, RaidRoom.AKKHA);
		// total 15,000 with the base: 20% is 3,000
		points.onGameMessage(ToaPointsEstimator.DEATH_MESSAGE + ".");
		assertEquals(7_000, points.getPersonalTotalPoints());

		ToaPointsEstimator early = new ToaPointsEstimator();
		// 20% of the 5,000 base is less than 1,000, so 1,000 goes
		early.onGameMessage(ToaPointsEstimator.DEATH_MESSAGE);
		assertEquals(-1_000, early.getPersonalTotalPoints());
	}

	@Test
	public void deathKeepsRoomPointsTowardsTheCap()
	{
		points.onHitsplat(PLAIN_NPC, 15_000, true, RaidRoom.AKKHA);
		points.onGameMessage(ToaPointsEstimator.DEATH_MESSAGE);
		// 20,000 - 4,000 = 16,000 total; room points stay at 15,000 so only 5,000 more fit
		points.onHitsplat(PLAIN_NPC, 10_000, true, RaidRoom.AKKHA);
		assertEquals(16_000 + 5_000 - 5_000, points.getPersonalTotalPoints());
	}

	@Test
	public void wardensPhaseTwoStopsCountingAfterThreeDowns()
	{
		points.onHitsplat(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, 10, true, RaidRoom.WARDENS); // x2
		for (int i = 0; i < 3; i++)
		{
			points.onWardenDowned(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE);
		}
		points.onHitsplat(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, 10, true, RaidRoom.WARDENS);
		assertEquals(40, points.getPersonalTotalPoints());
		points.onWardenDowned(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE);
		points.onHitsplat(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, 10, true, RaidRoom.WARDENS);
		assertEquals(40, points.getPersonalTotalPoints());
		// phase 3 still counts (x2.5)
		points.onHitsplat(NpcID.TOA_WARDEN_TUMEKEN_PHASE3, 10, true, RaidRoom.WARDENS);
		assertEquals(65, points.getPersonalTotalPoints());
	}

	@Test
	public void failingTheRoomResetsTheDowns()
	{
		for (int i = 0; i < 4; i++)
		{
			points.onWardenDowned(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE);
		}
		points.onGameMessage(ToaPointsEstimator.ROOM_FAIL_MESSAGE + " The Wardens.");
		points.onHitsplat(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE, 10, true, RaidRoom.WARDENS);
		assertEquals(20, points.getPersonalTotalPoints());
	}

	@Test
	public void mvpItemsGive300PerPlayerOnce()
	{
		points.setTeamSize(3);
		points.onItemSpawned(ItemID.TOA_ZEBAK_FANG);
		points.onItemSpawned(ItemID.TOA_ZEBAK_FANG);
		points.onItemSpawned(ItemID.TOA_AKKHA_ASHES);
		assertEquals(2 * 900, points.getPersonalTotalPoints());
	}

	@Test
	public void puzzleRoomsGiveFixedPoints()
	{
		points.onRoomLeft(RaidRoom.SCABARAS);
		points.onRoomLeft(RaidRoom.APMEKEN);
		points.onRoomLeft(RaidRoom.CRONDIS);
		points.onRoomLeft(RaidRoom.HET);
		points.onRoomLeft(RaidRoom.WARDENS);
		points.onRoomLeft(RaidRoom.AKKHA);
		points.onRoomLeft(null);
		assertEquals(300 + 450 + 400, points.getPersonalTotalPoints());
		assertEquals(5 * 300, points.getNonPartyPoints());
		assertEquals(1150 + 1500, points.getTotalPoints());
	}

	@Test
	public void startMessageResets()
	{
		points.onHitsplat(PLAIN_NPC, 500, true, RaidRoom.AKKHA);
		points.onRoomLeft(RaidRoom.HET);
		points.onGameMessage(ToaPointsEstimator.START_MESSAGE);
		assertEquals(0, points.getPersonalTotalPoints());
		assertEquals(0, points.getNonPartyPoints());
	}

	@Test
	public void roomsFromRegions()
	{
		assertEquals(RaidRoom.WARDENS, RaidRoom.forRegionId(15184));
		assertEquals(RaidRoom.WARDENS, RaidRoom.forRegionId(15696));
		assertEquals(RaidRoom.CRONDIS, RaidRoom.forRegionId(15698));
		assertEquals(null, RaidRoom.forRegionId(12345));
		assertEquals(RaidRoom.BABA, RaidRoom.forString("Ba-Ba"));
	}
}
