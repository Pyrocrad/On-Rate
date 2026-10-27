/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2020, CasvM
 * Copyright (c) 2022, LlemonDuck
 * Copyright (c) 2022, TheStonedTurtle
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * Adapted for On Rate from RaidTrackerPlugin.java and PointsTracker.java in Raid Data
 * Tracker by Raitab (https://github.com/Raitab/raid-data-tracker, branch raid-data-tracker,
 * commit 963f3c4f). Only the parts that read raid points are used here: the Chambers of
 * Xeric points at the end of a raid, the Tombs of Amascut points estimate (from the Tombs
 * of Amascut plugin by LlemonDuck and TheStonedTurtle), the Tombs of Amascut plugin's party
 * points message and the Theatre of Blood end-of-raid board.
 */
package com.expecteddrops.raids;

import com.expecteddrops.raids.toa.RaidState;
import com.expecteddrops.raids.toa.RaidStateTracker;
import com.expecteddrops.raids.toa.ToaPointsEstimator;
import com.expecteddrops.tracking.RaidRecord;
import com.expecteddrops.tracking.Tracker;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.util.Text;

/**
 * Reads your points at the end of each raid and hands them to the {@link Tracker}, so that
 * raid's purple chance comes from the points instead of the settings. Registered on the
 * event bus by the plugin while "Use raid points" is on.
 */
public class RaidPointsReader
{
	private static final String RAID_COMPLETE_MESSAGE_COX = "Congratulations - your raid is complete!";
	private static final String RAID_COMPLETE_MESSAGE_TOA = "Challenge complete: The Wardens.";
	private static final String TOA_EVENT_NAMESPACE = "tombs-of-amascut";
	private static final String TOA_EVENT_NAME_POINTS = "raidCompletedPoints";

	/** Theatre of Blood board: the MVP's name, then each player's name and deaths. */
	private static final int[] TOB_NAMES = {
		InterfaceID.TobInfoboard.PLAYERNAME0, InterfaceID.TobInfoboard.PLAYERNAME1, InterfaceID.TobInfoboard.PLAYERNAME2,
		InterfaceID.TobInfoboard.PLAYERNAME3, InterfaceID.TobInfoboard.PLAYERNAME4,
	};
	private static final int[] TOB_DEATHS = {
		InterfaceID.TobInfoboard.DEATHS0, InterfaceID.TobInfoboard.DEATHS1, InterfaceID.TobInfoboard.DEATHS2,
		InterfaceID.TobInfoboard.DEATHS3, InterfaceID.TobInfoboard.DEATHS4,
	};

	private final Client client;
	private final ClientThread clientThread;
	private final Tracker tracker;
	private final Consumer<String> changed;
	private final BooleanSupplier enabled;

	private final RaidStateTracker raidStateTracker;
	private final ToaPointsEstimator toaPoints = new ToaPointsEstimator();
	/** True once the start of this Tombs raid was seen, so a half-tracked raid isn't saved. */
	private boolean toaStartSeen;
	private int toaRaidLevel = -1;
	/**
	 * Your exact Tombs of Amascut points, which the game sends at the end of each raid (since
	 * client revision 232), or 0 before they came. They replace On Rate's own estimate.
	 */
	private int toaExactPoints;

	public RaidPointsReader(Client client, ClientThread clientThread, Tracker tracker, Consumer<String> changed,
		BooleanSupplier enabled)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.tracker = tracker;
		this.changed = changed;
		this.enabled = enabled;
		this.raidStateTracker = new RaidStateTracker(client);
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		RaidState previous = raidStateTracker.onGameTick();
		RaidState current = raidStateTracker.getCurrentState();
		if (current.isInRaid())
		{
			int level = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
			if (level > 0)
			{
				toaRaidLevel = level;
			}
		}
		if (previous == null)
		{
			return;
		}
		toaPoints.setTeamSize(current.getPlayerCount());
		if (previous.getCurrentRoom() != null && previous.getCurrentRoom() != current.getCurrentRoom())
		{
			toaPoints.onRoomLeft(previous.getCurrentRoom());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		switch (e.getGameState())
		{
			case LOGGING_IN:
			case HOPPING:
				raidStateTracker.reset();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage()).replace(' ', ' ');

		if (message.startsWith(RAID_COMPLETE_MESSAGE_COX))
		{
			readCoxPoints();
			return;
		}

		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		if (message.startsWith(ToaPointsEstimator.START_MESSAGE))
		{
			toaPoints.reset();
			toaPoints.setTeamSize(raidStateTracker.getPlayerCount());
			toaStartSeen = true;
			toaRaidLevel = -1;
			toaExactPoints = 0;
			return;
		}
		if (!raidStateTracker.getCurrentState().isInRaid())
		{
			return;
		}
		if (message.startsWith(RAID_COMPLETE_MESSAGE_TOA))
		{
			saveToaRaid();
		}
		toaPoints.onGameMessage(message);
	}

	/** Same values Raid Data Tracker and RuneLite's raids plugin read. */
	private void readCoxPoints()
	{
		if (!enabled.getAsBoolean() || client.getVarbitValue(VarbitID.RAIDS_CLIENT_INDUNGEON) != 1)
		{
			return;
		}
		int total = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE);
		int personal = client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE);
		int teamSize = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE);
		if (total <= 0 || personal <= 0)
		{
			return;
		}
		report(tracker.onRaidRecord(RaidRecord.cox(personal, total, teamSize), System.currentTimeMillis()));
	}

	private void saveToaRaid()
	{
		boolean started = toaStartSeen;
		toaStartSeen = false;
		int exact = toaExactPoints;
		toaExactPoints = 0;
		if (!enabled.getAsBoolean() || !started && exact <= 0)
		{
			// On Rate missed the start of this raid and the game hasn't sent the points yet,
			// so an estimate would be too low; if the points come in a moment, they're used then
			return;
		}
		int level = toaRaidLevel > 0 ? toaRaidLevel : client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
		// the game's own number when it already came, else the estimate (replaced if it comes later)
		RaidRecord record = exact > 0
			? RaidRecord.toaExact(exact, Math.max(1, raidStateTracker.getPlayerCount()), level)
			: RaidRecord.toa(toaPoints.getPersonalTotalPoints(), toaPoints.getNonPartyPoints(), 0,
				Math.max(1, raidStateTracker.getPlayerCount()), level);
		report(tracker.onRaidRecord(record, System.currentTimeMillis()));
	}

	/**
	 * The game sends your exact Tombs of Amascut points at the end of the raid (the same value the
	 * Tombs of Amascut plugin reads). Before the raid is saved they're kept for it; after, they
	 * replace the estimate in the saved raid.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		if (e.getVarpId() != VarPlayerID.TOA_PERSONAL_CONTRIBUTION || e.getValue() <= 0 || !enabled.getAsBoolean())
		{
			return;
		}
		if (toaStartSeen)
		{
			// the raid isn't saved yet: "Challenge complete" comes after this
			toaExactPoints = e.getValue();
			return;
		}
		report(tracker.onToaPoints(e.getValue(), System.currentTimeMillis()));
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		if (!raidStateTracker.getCurrentState().isInRaid() || !(e.getActor() instanceof NPC))
		{
			return;
		}
		NPC target = (NPC) e.getActor();
		toaPoints.onHitsplat(target.getId(), e.getHitsplat().getAmount(), e.getHitsplat().isMine(),
			raidStateTracker.getCurrentState().getCurrentRoom());
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned e)
	{
		if (raidStateTracker.getCurrentState().isInRaid())
		{
			toaPoints.onItemSpawned(e.getItem().getId());
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Actor actor = e.getActor();
		if (!raidStateTracker.getCurrentState().isInRaid() || !(actor instanceof NPC))
		{
			return;
		}
		if (actor.getAnimation() == AnimationID.NPC_WARDENS_KNEEL01)
		{
			toaPoints.onWardenDowned(((NPC) actor).getId());
		}
	}

	/**
	 * The Tombs of Amascut plugin shares the party's points at the end of a raid
	 * (thanks to Adam, LlemonDuck and jocopa3 for the PluginMessage event in RuneLite).
	 */
	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!enabled.getAsBoolean() || !TOA_EVENT_NAMESPACE.equals(message.getNamespace())
			|| !TOA_EVENT_NAME_POINTS.equals(message.getName()))
		{
			return;
		}
		Map<String, Object> data = message.getData();
		if (data == null || toInt(data.get("version")) != 1)
		{
			return;
		}
		int personal = toInt(data.get("personalPoints"));
		int total = toInt(data.get("totalPoints"));
		report(tracker.onPartyPoints(personal, total, System.currentTimeMillis()));
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.TOB_INFOBOARD || !enabled.getAsBoolean())
		{
			return;
		}
		clientThread.invokeLater(this::readTobBoard);
	}

	/** MVP and deaths from the board at the end of a Theatre of Blood raid. */
	private void readTobBoard()
	{
		Player me = client.getLocalPlayer();
		String myName = me == null || me.getName() == null ? null : clean(me.getName());
		int teamSize = 0;
		int teamDeaths = 0;
		int myDeaths = -1;
		for (int i = 0; i < TOB_NAMES.length; i++)
		{
			String name = widgetText(client.getWidget(TOB_NAMES[i]));
			if (name.isEmpty())
			{
				continue;
			}
			teamSize++;
			int deaths = widgetNumber(client.getWidget(TOB_DEATHS[i]));
			teamDeaths += deaths;
			if (myName != null && myName.equalsIgnoreCase(clean(name)))
			{
				myDeaths = deaths;
			}
		}
		if (teamSize == 0)
		{
			return;
		}
		String mvp = widgetText(client.getWidget(InterfaceID.TobInfoboard.MVP_PLAYER));
		Boolean isMvp = myName == null || mvp.isEmpty() ? null : myName.equalsIgnoreCase(clean(mvp));
		report(tracker.onRaidRecord(RaidRecord.tob(teamSize, myDeaths, teamDeaths, isMvp), System.currentTimeMillis()));
	}

	private void report(String boss)
	{
		if (boss != null)
		{
			changed.accept(boss);
		}
	}

	private static String clean(String name)
	{
		return Text.removeTags(name).replace(' ', ' ').trim();
	}

	private static String widgetText(Widget widget)
	{
		if (widget == null || widget.getText() == null || widget.getText().equals("-"))
		{
			return "";
		}
		return widget.getText();
	}

	private static int widgetNumber(Widget widget)
	{
		String text = widgetText(widget).trim();
		if (text.isEmpty())
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(text);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	private static int toInt(Object value)
	{
		return value instanceof Number ? ((Number) value).intValue() : -1;
	}
}
