package com.expecteddrops.ui;

/** The display settings the panel follows. */
public class ViewOptions
{
	/** Plain defaults: target bars, GE value on, extras off. */
	public static final ViewOptions DEFAULT = new ViewOptions(ItemView.TARGET_BARS, true, false, false, false, false);

	private final ItemView itemView;
	private final boolean geValue;
	private final boolean luckGraph;
	private final boolean dropHistory;
	private final boolean tripCard;
	private final boolean colourBlind;

	public ViewOptions(ItemView itemView, boolean geValue, boolean luckGraph, boolean dropHistory, boolean tripCard,
		boolean colourBlind)
	{
		this.itemView = itemView == null ? ItemView.TARGET_BARS : itemView;
		this.geValue = geValue;
		this.luckGraph = luckGraph;
		this.dropHistory = dropHistory;
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

	public boolean isDropHistory()
	{
		return dropHistory;
	}

	public boolean isTripCard()
	{
		return tripCard;
	}

	public boolean isColourBlind()
	{
		return colourBlind;
	}
}
