package com.onrate.engine.tracking;

import lombok.Getter;
import java.util.Collections;
import java.util.List;

/**
 * One line of the setup checklist: a collection log page (or the Doom scoreboard)
 * and whether it has been opened yet.
 */
@Getter
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
}
