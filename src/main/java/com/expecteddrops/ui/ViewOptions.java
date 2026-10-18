package com.expecteddrops.ui;

/** The display settings the panel follows. */
public class ViewOptions
{
	/** Plain defaults: target bars, GE value on, extras off. */
	public static final ViewOptions DEFAULT = new ViewOptions(ItemView.COMPACT, true, false, false, false, false);

	private final ItemView itemView;
	private final boolean geValue;
	private final boolean luckGraph;
	private final boolean dropHistory;
	private final boolean tripCard;
	private final boolean colourBlind;
	private final boolean topItems;

	public ViewOptions(ItemView itemView, boolean geValue, boolean luckGraph, boolean dropHistory, boolean tripCard,
		boolean colourBlind)
	{
		this(itemView, geValue, luckGraph, dropHistory, tripCard, colourBlind, false);
	}

	public ViewOptions(ItemView itemView, boolean geValue, boolean luckGraph, boolean dropHistory, boolean tripCard,
		boolean colourBlind, boolean topItems)
	{
		this.topItems = topItems;
		this.itemView = itemView == null ? ItemView.COMPACT : itemView;
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

	/** The account overview's top 5 driest / luckiest items. */
	public boolean isTopItems()
	{
		return topItems;
	}

	public boolean isColourBlind()
	{
		return colourBlind;
	}
}
