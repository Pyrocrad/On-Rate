package com.onrate.game;

import lombok.AllArgsConstructor;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/**
 * Counts your team at a group-boss kill: you plus the players seen hitting the boss with you
 * (Nex, Yama, the Hueycoatl, Corp). Zalcano and the Fractured Archive can't be told that way
 * (mining and smithing count there, and the raid is an instance), so they use the players around you.
 */
@AllArgsConstructor
public final class TeamDetector
{
	/** Players within this many tiles of you count as your team where hits can't tell. */
	private static final int RADIUS_TILES = 15;
	private static final int LOCAL_UNITS_PER_TILE = 128;

	private final Client client;
	private final TeamSpotter spotter = new TeamSpotter();

	/** Notes who is hitting a group boss. Call every game tick. */
	public void onTick(long now)
	{
		Player me = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (me == null || view == null)
		{
			return;
		}
		for (Player p : view.players())
		{
			if (p == null || p == me || p.getName() == null)
			{
				continue;
			}
			Actor target = p.getInteracting();
			String fight = target instanceof NPC ? TeamSpotter.fightOf(target.getName()) : null;
			if (fight != null)
			{
				spotter.onHitting(fight, p.getName(), now);
			}
		}
	}

	/** Your team at a kill (you included); called on the client thread, from the kill message. */
	public int sizeFor(String bossName)
	{
		String fight = TeamSpotter.fightOf(bossName);
		return fight == null ? playersAround() : 1 + spotter.others(fight, System.currentTimeMillis());
	}

	/** This kill is counted: the next one starts with nobody seen. */
	public void killCounted(String bossName)
	{
		String fight = TeamSpotter.fightOf(bossName);
		if (fight != null)
		{
			spotter.clear(fight);
		}
	}

	public void clear()
	{
		spotter.clearAll();
	}

	private int playersAround()
	{
		Player me = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		LocalPoint here = me == null ? null : me.getLocalLocation();
		if (here == null || view == null)
		{
			return 0;
		}
		int count = 0;
		for (Player p : view.players())
		{
			LocalPoint there = p == null ? null : p.getLocalLocation();
			if (there == null)
			{
				continue;
			}
			int dx = Math.abs(there.getX() - here.getX()) / LOCAL_UNITS_PER_TILE;
			int dy = Math.abs(there.getY() - here.getY()) / LOCAL_UNITS_PER_TILE;
			if (Math.max(dx, dy) <= RADIUS_TILES)
			{
				count++;
			}
		}
		return Math.max(1, count);
	}
}
