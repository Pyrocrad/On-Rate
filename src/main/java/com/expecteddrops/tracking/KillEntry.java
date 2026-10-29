package com.expecteddrops.tracking;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;

/**
 * One kill On Rate saw, for the History section: when, at what kill count, the uniques it
 * gave and what else is known about it (raid mode, team size, delve level, titan looted).
 * Saved in the RuneLite profile; kept short so the history stays small.
 */
public class KillEntry
{
	/** Kill count of the whole page after this kill (raids: all modes together). */
	@SerializedName("k")
	int kc;
	/** Kill count in the chat message (one mode or one boss of a combined page). */
	@SerializedName("p")
	Integer partKc;
	/** When, in milliseconds. */
	@SerializedName("t")
	long time;
	/** Raid mode or boss of a combined page ("Challenge Mode", "Dagannoth Rex"), or null. */
	@SerializedName("m")
	String part;
	/** Players around you at a group boss, or null. */
	@SerializedName("n")
	Integer team;
	/** Delve level (Doom), or null. */
	@SerializedName("l")
	Integer level;
	/** Royal Titans: the titan you looted ("Branda", "Eldric"), or null. */
	@SerializedName("ti")
	String titan;
	/** Uniques from this kill. */
	@SerializedName("d")
	List<DropEntry> drops;

	public int getKc()
	{
		return kc;
	}

	/** False for a kill the player added by hand from memory: its kill count is known, its date isn't. */
	public boolean isDateKnown()
	{
		return time > 0;
	}

	public Integer getPartKc()
	{
		return partKc;
	}

	public long getTime()
	{
		return time;
	}

	public String getPart()
	{
		return part;
	}

	public Integer getTeam()
	{
		return team;
	}

	public Integer getLevel()
	{
		return level;
	}

	public String getTitan()
	{
		return titan;
	}

	public List<DropEntry> getDrops()
	{
		return drops == null ? Collections.emptyList() : drops;
	}

	/** One unique from a kill. */
	public static class DropEntry
	{
		/** Name as shown in the panel. */
		@SerializedName("i")
		String name;
		/** Kills since the previous one of this item, or null when unknown. */
		@SerializedName("s")
		Integer since;
		/** That gap in multiples of the drop rate, or null. */
		@SerializedName("x")
		Double multiple;
		/** True when it's the first one (the collection log said so). */
		@SerializedName("f")
		Boolean first;
		/** True when {@link #since} is only a minimum (an earlier one came before On Rate). */
		@SerializedName("a")
		Boolean atLeast;
		@SerializedName("pt")
		Boolean pet;
		/** True when the player added this drop by hand ("I got this at kill 115"). */
		@SerializedName("u")
		Boolean entered;

		public String getName()
		{
			return name;
		}

		public Integer getSince()
		{
			return since;
		}

		public Double getMultiple()
		{
			return multiple;
		}

		public boolean isFirst()
		{
			return first != null && first;
		}

		public boolean isAtLeast()
		{
			return atLeast != null && atLeast;
		}

		public boolean isPet()
		{
			return pet != null && pet;
		}

		/**
		 * How lucky this wait was: the share of players who would have had one by then at the
		 * drop rate (1 - e^-multiple). Small is lucky, large is dry. NaN when the wait isn't known exactly.
		 */
		public double getLuck()
		{
			return multiple == null || isAtLeast() ? Double.NaN : 1 - Math.exp(-multiple);
		}

		public boolean isEntered()
		{
			return entered != null && entered;
		}
	}
}
