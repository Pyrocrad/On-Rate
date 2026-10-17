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

import java.util.Objects;

/**
 * Where you are in the Tombs of Amascut on one game tick.
 */
public final class RaidState
{

	private final boolean inLobby;
	private final boolean inRaid;
	private final RaidRoom currentRoom;
	private final int playerCount;

	public RaidState(boolean inLobby, boolean inRaid, RaidRoom currentRoom, int playerCount)
	{
		this.inLobby = inLobby;
		this.inRaid = inRaid;
		this.currentRoom = currentRoom;
		this.playerCount = playerCount;
	}

	public boolean isInLobby()
	{
		return inLobby;
	}

	public boolean isInRaid()
	{
		return inRaid;
	}

	public RaidRoom getCurrentRoom()
	{
		return currentRoom;
	}

	public int getPlayerCount()
	{
		return playerCount;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof RaidState))
		{
			return false;
		}
		RaidState other = (RaidState) o;
		return inLobby == other.inLobby && inRaid == other.inRaid && playerCount == other.playerCount
			&& currentRoom == other.currentRoom;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(inLobby, inRaid, currentRoom, playerCount);
	}
}
