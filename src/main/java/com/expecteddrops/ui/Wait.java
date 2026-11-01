package com.expecteddrops.ui;



/** A long wait on the account page: a drop that took long, or an item still being waited for. */
/** A wait for the Longest waits list: a drop that took long, or an item you're still waiting for. */
final class Wait
{
	final double multiple;
	final com.expecteddrops.tracking.RecentDrop drop;
	final com.expecteddrops.stats.ItemHighlight ongoing;

	Wait(double multiple, com.expecteddrops.tracking.RecentDrop drop, com.expecteddrops.stats.ItemHighlight ongoing)
	{
		this.multiple = multiple;
		this.drop = drop;
		this.ongoing = ongoing;
	}
}
