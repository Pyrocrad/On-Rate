package com.expecteddrops.ui;

import lombok.Getter;

/** The display settings the panel follows. */
@Getter
public class ViewOptions
{
	/** Plain defaults: target bars, GE value on, extras off. */
	public static final ViewOptions DEFAULT = new ViewOptions(ItemView.TARGET_BARS, true, false, false, false);

	private final ItemView itemView;
	private final boolean geValue;
	private final boolean luckGraph;
	private final boolean tripCard;
	private final boolean colourBlind;
	private final boolean topItems;

	public ViewOptions(ItemView itemView, boolean geValue, boolean luckGraph, boolean tripCard, boolean colourBlind)
	{
		this(itemView, geValue, luckGraph, tripCard, colourBlind, false);
	}

	public ViewOptions(ItemView itemView, boolean geValue, boolean luckGraph, boolean tripCard, boolean colourBlind,
		boolean topItems)
	{
		this.topItems = topItems;
		this.itemView = itemView == null ? ItemView.TARGET_BARS : itemView;
		this.geValue = geValue;
		this.luckGraph = luckGraph;
		this.tripCard = tripCard;
		this.colourBlind = colourBlind;
	}
}
