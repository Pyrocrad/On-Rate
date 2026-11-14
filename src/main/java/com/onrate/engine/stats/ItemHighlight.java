package com.onrate.engine.stats;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One item of one boss for the account overview's top 5: the item's numbers plus where it drops.
 */
@Getter
@AllArgsConstructor
public class ItemHighlight
{
	private final String boss;
	private final RowStats row;
	private final boolean pet;

	public ItemHighlight(String boss, RowStats row)
	{
		this(boss, row, false);
	}
}
