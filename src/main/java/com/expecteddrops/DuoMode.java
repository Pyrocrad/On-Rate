package com.expecteddrops;

/** How the player usually kills a boss you can do solo or duo (Royal Titans, Yama). */
public enum DuoMode
{
	DUO("Duo"),
	SOLO("Solo"),
	MIXED("Some of each");

	private final String name;

	DuoMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
