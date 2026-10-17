/*
BSD 2-Clause License

Copyright (c) 2022, LlemonDuck
Copyright (c) 2022, TheStonedTurtle
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
/*
 * Adapted for On Rate from PointsTracker.java in Raid Data Tracker by Raitab
 * (https://github.com/Raitab/raid-data-tracker, branch raid-data-tracker, commit 963f3c4f),
 * which took it from the Tombs of Amascut plugin by LlemonDuck and TheStonedTurtle.
 * Changes: split from the RuneLite events so it can be unit tested (ToaPointsReader feeds
 * it), no Guava or Lombok. The numbers and rules are unchanged.
 */
package com.expecteddrops.raids.toa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;

/**
 * Estimates your Tombs of Amascut points. The game never shows them, so this counts your
 * own hitsplats times a points factor per monster, plus fixed amounts for puzzle rooms and
 * MVP items, minus 20% (at least 1,000) per death.
 */
public class ToaPointsEstimator
{

	/*
	 * I have some insider knowledge here that the blog was describing points earning slightly wrong wrt deaths.
	 * Points are earned to both total and room points at the same time,
	 * rather than being queued up in room points and added onto total after the room.
	 * When dying, you preserve the room points amount toward cap, but subtract 20% from total.
	 * There is no special behaviour when wiping a room; the 20% points lost is intended to account for that.
	 */

	public static final String START_MESSAGE = "You enter the Tombs of Amascut";
	public static final String DEATH_MESSAGE = "You have died";
	public static final String ROOM_FAIL_MESSAGE = "Your party failed to complete";
	public static final String ROOM_FINISH_MESSAGE = "Challenge complete";

	static final int BASE_POINTS = 5000;
	static final int MAX_ROOM_POINTS = 20_000;
	static final int CRONDIS_MAX_ROOM_POINTS = 10_000;

	static final int MAX_TOTAL_POINTS = 64_000;

	private static final Map<Integer, Double> DAMAGE_POINTS_FACTORS;

	static
	{
		Map<Integer, Double> m = new HashMap<>();
		m.put(NpcID.TOA_WARDEN_TUMEKEN_CORE, 0.0);
		m.put(NpcID.TOA_WARDEN_ELIDINIS_CORE, 0.0);
		m.put(NpcID.WARDENS_P3_ORB_BLUE, 0.0); // no red orb?

		m.put(NpcID.TOA_BABA_BOULDER, 0.0);
		m.put(NpcID.TOA_BABA_BOULDER_WEAK, 0.0);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_MELEE_1, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_MELEE_2, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_RANGED_1, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_RANGED_2, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_MAGIC_1, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_MAGIC_2, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_SHAMAN, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_ZOMBIE, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_CURSED, 1.2);
		m.put(NpcID.TOA_PATH_APMEKEN_BABOON_THRALL, 1.2);
		m.put(NpcID.TOA_BABA, 2.0);
		m.put(NpcID.TOA_BABA_COFFIN, 2.0);
		m.put(NpcID.TOA_BABA_DIGGING, 2.0);

		m.put(NpcID.TOA_ZEBAK_TRANSMOG, 1.5);
		m.put(NpcID.TOA_ZEBAK, 1.5);
		m.put(NpcID.TOA_ZEBAK_ENRAGED, 1.5);
		m.put(NpcID.TOA_ZEBAK_DEAD, 1.5);

		m.put(NpcID.TOA_KEPHRI_GUARDIAN_RANGED, 0.5);
		m.put(NpcID.TOA_KEPHRI_GUARDIAN_MELEE, 0.5);
		m.put(NpcID.TOA_KEPHRI_GUARDIAN_MAGE, 0.5);

		m.put(NpcID.TOA_HET_GOAL_VULNERABLE, 2.5);

		m.put(NpcID.TOA_WARDENS_P1_OBELISK_NPC_INACTIVE, 1.5);
		m.put(NpcID.TOA_WARDENS_P1_OBELISK_NPC, 1.5);
		m.put(NpcID.TOA_WARDENS_P2_OBELISK_NPC, 1.5);
		// non-combat wardens (prevents extra points during p1->p2 transition)
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE1_INACTIVE, 0.0);
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE1, 0.0);
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE1_INACTIVE, 0.0);
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE1, 0.0);

		// Phase 2
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, 2.0);
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE, 2.0);
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED, 0.0); // downed
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE, 2.0);
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE, 2.0);
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED, 0.0); // downed

		// Phase 3
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE3_INACTIVE, 0.0);
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE3_INACTIVE, 0.0);
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE3, 2.5);
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE3, 2.5);
		m.put(NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, 2.5);
		m.put(NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING, 2.5);
		DAMAGE_POINTS_FACTORS = Collections.unmodifiableMap(m);
	}

	// these have a cap at 3 "downs"
	static final Set<Integer> P2_WARDENS = Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(
		NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED,
		NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE,
		NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE,
		NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED
	)));

	static final Set<Integer> MVP_ITEMS = Collections.unmodifiableSet(new HashSet<>(java.util.Arrays.asList(
		ItemID.TOA_ZEBAK_FANG,
		ItemID.TOA_KEPHRI_POO,
		ItemID.TOA_BABA_BANANA,
		ItemID.TOA_AKKHA_ASHES
	)));

	private int personalRoomPoints;
	private int personalTotalPoints;
	private int nonPartyPoints; // points that are earned once by the entire party
	private final List<Integer> seenMvpItems = new ArrayList<>(4);

	private int teamSize;
	private int wardenDowns;

	public ToaPointsEstimator()
	{
		reset();
	}

	/** The party size changed (from the raid state). */
	public void setTeamSize(int teamSize)
	{
		this.teamSize = teamSize;
	}

	public int getTeamSize()
	{
		return teamSize;
	}

	/** You left a room: puzzle rooms give fixed points. */
	public void onRoomLeft(RaidRoom previousRoom)
	{
		if (previousRoom == null)
		{
			return;
		}

		switch (previousRoom)
		{
			// puzzle estimates
			case SCABARAS:
				personalTotalPoints += 300;
				nonPartyPoints += 300;
				break;

			case APMEKEN:
				personalTotalPoints += 450;
				nonPartyPoints += 300;
				break;

			case CRONDIS:
				personalTotalPoints += 400;
				nonPartyPoints += 300;
				break;

			case HET:
			case WARDENS:
				nonPartyPoints += 300;
				break;

			default:
				break;
		}
	}

	/** A game message while in the raid (tags already removed). */
	public void onGameMessage(String message)
	{
		if (message.startsWith(START_MESSAGE))
		{
			reset();
		}
		else if (message.startsWith(DEATH_MESSAGE))
		{
			personalTotalPoints -= (int) Math.max(0.2 * personalTotalPoints, 1000);
			if (personalTotalPoints < 0)
			{
				personalTotalPoints = 0;
			}

		}
		else if (message.startsWith(ROOM_FAIL_MESSAGE))
		{
			wardenDowns = 0;
		}
		else if (message.startsWith(ROOM_FINISH_MESSAGE))
		{
			personalRoomPoints = 0;
		}
	}

	/** A hitsplat on an NPC. Only your own hits ({@code mine}) earn you points. */
	public void onHitsplat(int npcId, int amount, boolean mine, RaidRoom currentRoom)
	{
		if (amount < 1)
		{
			return;
		}

		if (P2_WARDENS.contains(npcId) && wardenDowns > 3)
		{
			return;
		}

		Double known = DAMAGE_POINTS_FACTORS.get(npcId);
		double factor = known == null ? 1.0 : known;
		if (mine)
		{
			int pointsEarned = (int) (amount * factor);
			int roomMax = currentRoom == RaidRoom.CRONDIS ? CRONDIS_MAX_ROOM_POINTS : MAX_ROOM_POINTS;
			if (personalRoomPoints + pointsEarned > roomMax)
			{
				pointsEarned = roomMax - personalRoomPoints;
			}

			this.personalRoomPoints = Math.min(roomMax, personalRoomPoints + pointsEarned);
			this.personalTotalPoints = Math.min(MAX_TOTAL_POINTS, personalTotalPoints + pointsEarned);
		}
	}

	/** An item appeared on the floor; the MVP items give 300 points per player. */
	public void onItemSpawned(int itemId)
	{
		if (MVP_ITEMS.contains(itemId) && !seenMvpItems.contains(itemId))
		{
			personalTotalPoints += 300 * teamSize;
			seenMvpItems.add(itemId);
		}
	}

	/** A P2 Warden knelt down (was downed). */
	public void onWardenDowned(int npcId)
	{
		if (P2_WARDENS.contains(npcId))
		{
			wardenDowns++;
		}
	}

	/** Your points so far, without the 5,000 everyone starts with. */
	public int getPersonalTotalPoints()
	{
		return this.personalTotalPoints - BASE_POINTS;
	}

	/** Points the whole party earns once (puzzle completions). */
	public int getNonPartyPoints()
	{
		return nonPartyPoints;
	}

	public double getPersonalPercent()
	{
		if (teamSize <= 1)
		{
			return 1.0;
		}

		return (double) getPersonalTotalPoints() / getTotalPoints();
	}

	public int getTotalPoints()
	{
		return getPersonalTotalPoints() + nonPartyPoints;
	}

	public void reset()
	{
		this.personalTotalPoints = BASE_POINTS;
		this.personalRoomPoints = 0;
		this.nonPartyPoints = 0;
		this.teamSize = 0;
		this.wardenDowns = 0;
		this.seenMvpItems.clear();
	}

}
