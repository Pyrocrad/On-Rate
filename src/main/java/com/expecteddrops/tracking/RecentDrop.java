package com.expecteddrops.tracking;

/** One unique for the account's Recent tab: the drop and where it came from. */
public class RecentDrop
{
	private final String boss;
	private final KillEntry kill;
	private final KillEntry.DropEntry drop;

	RecentDrop(String boss, KillEntry kill, KillEntry.DropEntry drop)
	{
		this.boss = boss;
		this.kill = kill;
		this.drop = drop;
	}

	public String getBoss()
	{
		return boss;
	}

	public KillEntry getKill()
	{
		return kill;
	}

	public KillEntry.DropEntry getDrop()
	{
		return drop;
	}
}
