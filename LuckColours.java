package com.expecteddrops.ui;

/** The colours for dry to spooned: a ready-made set, or your own dry and on rate colours. */
public enum LuckColours
{
	CLASSIC("Classic (red, yellow, green)"),
	COLOUR_BLIND("Colour-blind (orange, grey, blue)"),
	PURPLE_AMBER("Purple to amber (to green)"),
	PINK_GREY("Pink to grey (to green)"),
	MONO("Mono (grey to green)"),
	CUSTOM("Custom (pick below)");

	private final String label;

	LuckColours(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
