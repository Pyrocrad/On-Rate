package com.expecteddrops.stats;

/**
 * One item of one boss for the account overview's top 5: the item's numbers plus where it drops.
 */
public class ItemHighlight
{
	private final String boss;
	private final RowStats row;

	public ItemHighlight(String boss, RowStats row)
	{
		this.boss = boss;
		this.row = row;
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
