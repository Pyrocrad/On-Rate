package com.expecteddrops.stats;

/**
 * One item of one boss for the account overview's top 5: the item's numbers plus where it drops.
 */
public class ItemHighlight
{
	private final String boss;
	private final RowStats row;
	private final boolean pet;

	public ItemHighlight(String boss, RowStats row)
	{
		this(boss, row, false);
	}

	public ItemHighlight(String boss, RowStats row, boolean pet)
	{
		this.boss = boss;
		this.row = row;
		this.pet = pet;
	}

	/** True for a boss pet (for the Items tab's Pets list). */
	public boolean isPet()
	{
		return pet;
	}

	/** The boss page to open, or null for a shared item (several bosses). */
	public String getBoss()
	{
		return boss;
	}

	public RowStats getRow()
	{
		return row;
	}
}
