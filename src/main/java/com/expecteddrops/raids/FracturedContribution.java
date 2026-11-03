package com.expecteddrops.raids;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Estimates your contribution in The Fractured Archive from the damage you and your team deal.
 * Jagex's loot post: each room hands out 100 contribution (Ocellus 300) split by damage, minion
 * damage included. The game doesn't show the number, so this adds up the hitsplats On Rate sees:
 * yours, and the other players' (the game shows them on monsters too).
 *
 * <p>NPC IDs are from the OSRS Wiki's boss pages and its Strategies page (November 2026). Not
 * modelled, because Jagex hasn't said how they work: the soft cap on minion damage, and how
 * Ocellus's 300 is split between his phases (here it is one share of all his damage).
 */
public class FracturedContribution
{
	/** The five fights of a run: the Archive Guardian, one of each pair, then Ocellus. */
	enum Room
	{
		ARCHIVE_GUARDIAN(0, 100, 16659, 16660, 16664),
		EZEKIEL(1, 100, 16652, 16653),
		BORK(1, 100, 16640, 16641),
		MOIA(2, 100, 16667, 16670),
		DREADNOTH(2, 100, 16665, 16666),
		DAGONHAI_ELDER(3, 100, 16643, 16644, 16646, 16647, 16648, 16650),
		XARTH(3, 100, 16684, 16687, 16688),
		OCELLUS(4, 300, 16672, 16673, 16674, 16675, 16676, 16677, 16678, 16679, 16680, 16681);

		/** Which of the five fights this is (rooms of a pair share it). */
		final int slot;
		final double contribution;
		final int[] npcIds;

		Room(int slot, double contribution, int... npcIds)
		{
			this.slot = slot;
			this.contribution = contribution;
			this.npcIds = npcIds;
		}
	}

	static final int SLOTS = 5;

	private static final Map<Integer, Room> BY_NPC = new HashMap<>();

	static
	{
		for (Room room : Room.values())
		{
			for (int id : room.npcIds)
			{
				BY_NPC.put(id, room);
			}
		}
	}

	/** What On Rate saw of one raid. */
	public static final class Result
	{
		/** Your contribution out of 700 before deaths, or -1 when a fight was missed. */
		public final double contribution;
		/** Your deaths in the raid. */
		public final int deaths;

		Result(double contribution, int deaths)
		{
			this.contribution = contribution;
			this.deaths = deaths;
		}
	}

	private final Map<Room, long[]> damage = new EnumMap<>(Room.class);
	private int deaths;

	/** True when the NPC belongs to The Fractured Archive. */
	public static boolean isArchiveNpc(int npcId)
	{
		return BY_NPC.containsKey(npcId);
	}

	/**
	 * A hitsplat on a monster. Only player damage counts (yours or another player's), not poison,
	 * venom, burns or healing, which have no owner.
	 */
	public void onHitsplat(int npcId, int amount, boolean mine, boolean others)
	{
		Room room = BY_NPC.get(npcId);
		if (room == null || amount <= 0 || !mine && !others)
		{
			return;
		}
		if (room == Room.ARCHIVE_GUARDIAN && seenPastGuardian())
		{
			// the Archive Guardian is the first fight: this is a new raid (the last one was wiped or missed)
			reset();
		}
		long[] d = damage.computeIfAbsent(room, r -> new long[2]);
		d[0] += mine ? amount : 0;
		d[1] += amount;
	}

	/** You died: counted only during a raid On Rate is following. */
	public void onLocalDeath()
	{
		if (isActive())
		{
			deaths++;
		}
	}

	/** True once a fight of this raid was seen. */
	public boolean isActive()
	{
		return !damage.isEmpty();
	}

	/**
	 * The raid is complete: what it saw, then starts over. Null when it saw nothing of the raid.
	 * The contribution is -1 unless all five fights were seen (On Rate turned on mid-raid, say).
	 */
	public Result finish()
	{
		if (!isActive())
		{
			return null;
		}
		boolean[] seen = new boolean[SLOTS];
		double contribution = 0;
		for (Map.Entry<Room, long[]> e : damage.entrySet())
		{
			long[] d = e.getValue();
			if (d[1] > 0)
			{
				seen[e.getKey().slot] = true;
				contribution += e.getKey().contribution * d[0] / d[1];
			}
		}
		boolean all = true;
		for (boolean s : seen)
		{
			all &= s;
		}
		Result result = new Result(all ? contribution : -1, deaths);
		reset();
		return result;
	}

	public void reset()
	{
		damage.clear();
		deaths = 0;
	}

	private boolean seenPastGuardian()
	{
		for (Room room : damage.keySet())
		{
			if (room != Room.ARCHIVE_GUARDIAN)
			{
				return true;
			}
		}
		return false;
	}
}
