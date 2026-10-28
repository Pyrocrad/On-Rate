package com.expecteddrops.ui;

/** The display settings the panel follows. */
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

	public ItemView getItemView()
	{
		return itemView;
	}

	public boolean isGeValue()
	{
		return geValue;
	}

	public boolean isLuckGraph()
	{
		return luckGraph;
	}

	public boolean isTripCard()
	{
		return tripCard;
	}

	/** The account overview's Items tab. */
	public boolean isTopItems()
	{
		return topItems;
	}

	public boolean isColourBlind()
	{
		return colourBlind;
	}
}
