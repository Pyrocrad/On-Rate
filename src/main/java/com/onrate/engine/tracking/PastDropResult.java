package com.onrate.engine.tracking;

import lombok.Getter;

/** The outcome of adding a past drop by hand: added, or why it was refused. */
public enum PastDropResult
{
	ADDED(false),
	/** The boss or item isn't known, or the item can't be entered at that page or part. */
	UNKNOWN(false),
	/** The kill count is below 1. */
	COUNT_TOO_LOW(true),
	/** The kill count is above the page's (or part's) own kill count. */
	COUNT_TOO_HIGH(true),
	/** On Rate saw this item at that kill count itself, so it can't be replaced. */
	SAW_AT_COUNT(true),
	/** History already lists every copy the collection log shows, and none can be replaced. */
	ALL_COPIES_LISTED(false),
	/** History is full of newer kills. */
	HISTORY_FULL(false);

	@Getter private final boolean retryable;

	PastDropResult(boolean retryable)
	{
		this.retryable = retryable;
	}

	public boolean isAdded()
	{
		return this == ADDED;
	}
}
