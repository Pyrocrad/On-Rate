package com.expecteddrops.ui;

/** How the panel shows your luck per item (and per boss in the account overview). */
public enum ItemView
{
	TARGET_BARS("Target bars"),
	DELVE_BARS("Bars with tabs (Delve Calculator)"),
	COMPACT("Compact table"),
	RINGS("Chance rings"),
	GAUGE("Luck gauge");

	private final String label;

	ItemView(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
