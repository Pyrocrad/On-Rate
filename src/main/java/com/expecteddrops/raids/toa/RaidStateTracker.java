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
 * Adapted for On Rate from Raid Data Tracker by Raitab
 * (https://github.com/Raitab/raid-data-tracker, branch raid-data-tracker, commit 963f3c4f),
 * which took it from the Tombs of Amascut plugin by LlemonDuck and TheStonedTurtle.
 * Changes: no Lombok, no event bus (On Rate calls it directly), kept under the licence above.
 */
package com.expecteddrops.raids.toa;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Works out every game tick whether you're in the Tombs of Amascut, which room you're
 * in and how many players are in the party.
 */
public class RaidStateTracker
{

	private static final int REGION_LOBBY = 13454;

	static final RaidState DEFAULT_STATE = new RaidState(false, false, null, 0);

	private final Client client;

	private RaidState currentState = DEFAULT_STATE;

	// delay inRaid = false by 3 ticks to alleviate any unexpected delays between rooms
	private int raidLeaveTicks = 0;

	public RaidStateTracker(Client client)
	{
		this.client = client;
	}

	public RaidState getCurrentState()
	{
		return currentState;
	}

	/**
	 * Call once per game tick. Returns the state before this tick when it changed,
	 * or null when nothing changed.
	 */
	public RaidState onGameTick()
	{
		Player player = client.getLocalPlayer();
		LocalPoint lp = player == null ? null : player.getLocalLocation();
		int region = lp == null ? -1 : WorldPoint.fromLocalInstance(client, lp).getRegionID();

		Widget w = client.getWidget(InterfaceID.ToaHud.RAID_STATE);

		boolean inLobby = region == REGION_LOBBY;
		RaidRoom currentRoom = RaidRoom.forRegionId(region);
		boolean inRaidRaw = currentRoom != null || (w != null && !w.isHidden());

		raidLeaveTicks = inRaidRaw ? 3 : raidLeaveTicks - 1;
		boolean inRaid = raidLeaveTicks > 0;

		return setRaidState(new RaidState(inLobby, inRaid, currentRoom, countPlayers()));
	}

	public int getPlayerCount()
	{
		return this.currentState.getPlayerCount();
	}

	/** Call when logging in or hopping worlds. */
	public void reset()
	{
		raidLeaveTicks = 0;
		currentState = DEFAULT_STATE;
	}

	private RaidState setRaidState(RaidState newValue)
	{
		RaidState previous = currentState;
		currentState = newValue;
		return currentState.equals(previous) ? null : previous;
	}

	/**
	 * Counts the number of players based on varbits for each player slot.
	 */
	private int countPlayers()
	{
		return 1 +
			(client.getVarbitValue(VarbitID.TOA_CLIENT_P1) != 0 ? 1 : 0) +
			(client.getVarbitValue(VarbitID.TOA_CLIENT_P2) != 0 ? 1 : 0) +
			(client.getVarbitValue(VarbitID.TOA_CLIENT_P3) != 0 ? 1 : 0) +
			(client.getVarbitValue(VarbitID.TOA_CLIENT_P4) != 0 ? 1 : 0) +
			(client.getVarbitValue(VarbitID.TOA_CLIENT_P5) != 0 ? 1 : 0) +
			(client.getVarbitValue(VarbitID.TOA_CLIENT_P6) != 0 ? 1 : 0) +
			(client.getVarbitValue(VarbitID.TOA_CLIENT_P7) != 0 ? 1 : 0);
	}
}
