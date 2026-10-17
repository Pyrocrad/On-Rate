package com.expecteddrops;

/** How the player usually kills the Royal Titans. */
public enum RoyalTitansMode
{
	SOLO("Solo"),
	DUO("Duo"),
	MIXED("Some of each");

	private final String name;

	RoyalTitansMode(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
