package com.onrate.engine.tracking;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** One unique for the account's Recent tab: the drop and where it came from. */
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class RecentDrop
{
	private final String boss;
	private final KillEntry kill;
	private final KillEntry.DropEntry drop;
}
