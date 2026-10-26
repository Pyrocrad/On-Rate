package com.expecteddrops.tracking;

/**
 * A chat message the plugin wants to show the player.
 */
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

	public Note(Kind kind, String text)
	{
		this.kind = kind;
		this.text = text;
	}

	public Kind getKind()
	{
		return kind;
	}

	public String getText()
	{
		return text;
	}

	@Override
	public String toString()
	{
		return kind + ": " + text;
	}
}
