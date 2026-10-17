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

	public SyncStatus(String page, List<String> bosses, boolean synced)
	{
		this.page = page;
		this.bosses = Collections.unmodifiableList(bosses);
		this.synced = synced;
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
