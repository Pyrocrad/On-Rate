package com.onrate.engine.tracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * A chat message the plugin wants to show the player.
 */
@Getter
@AllArgsConstructor
public class Note
{
	public enum Kind
	{
		/** A unique dropped: how many kills it took. */
		DROP,
		/** An item passed 2x, 3x, 5x... the drop rate without dropping. */
		MILESTONE,
		/** A trip ended: its kills, uniques and verdict in one line (with the drop messages). */
		TRIP
	}

	private final Kind kind;
	private final String text;

	@Override
	public String toString()
	{
		return kind + ": " + text;
	}
}
