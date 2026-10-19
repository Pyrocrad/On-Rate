package com.expecteddrops.tracking;

import java.util.Collections;
import java.util.List;

/**
 * One line of the setup checklist: a collection log page (or the Doom scoreboard)
 * and whether it has been opened yet.
 */
public class SyncStatus
{
	private final String page;
	private final List<String> bosses;
	private final boolean synced;
	private final boolean killed;

	public SyncStatus(String page, List<String> bosses, boolean synced)
	{
		this(page, bosses, synced, false);
	}

	public SyncStatus(String page, List<String> bosses, boolean synced, boolean killed)
	{
		this.page = page;
		this.bosses = Collections.unmodifiableList(bosses);
		this.synced = synced;
		this.killed = killed;
	}

	/** True when On Rate saw a kill at one of its bosses: the pages that matter most. */
	public boolean isKilled()
	{
		return killed;
	}

	/** What the player should open, e.g. "Vorkath" or "Doom scoreboard". */
	public String getPage()
	{
		return page;
	}

	/** Bosses whose history this page loads. */
	public List<String> getBosses()
	{
		return bosses;
	}

	public boolean isSynced()
	{
		return synced;
	}
}
