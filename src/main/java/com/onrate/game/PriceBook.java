package com.onrate.game;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.ValueFormula;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Item ids and prices for the items On Rate tracks, from RuneLite's own price list (no web
 * requests). Untradeable items are worth what they make, where {@code bosses.json} says so.
 */
public final class PriceBook
{
	private static final Logger log = LoggerFactory.getLogger(PriceBook.class);
	private static final long REFRESH_MILLIS = 10 * 60_000;

	private final BossCatalog catalog;
	private final ItemManager itemManager;
	private final Map<String, Integer> itemIds = new ConcurrentHashMap<>();
	/** GE price per normalized item name. Untradeables are 0. */
	private final Map<String, Long> prices = new ConcurrentHashMap<>();
	private volatile boolean resolved;
	private long refreshedAt;

	public PriceBook(BossCatalog catalog, ItemManager itemManager)
	{
		this.catalog = catalog;
		this.itemManager = itemManager;
	}

	/** Remembers an id seen on the collection log page. */
	public void learn(String itemName, int id)
	{
		itemIds.putIfAbsent(BossCatalog.normalize(itemName), id);
	}

	/** Looks up an id for every item in the drop data, once. Client thread only. */
	public boolean resolve(Client client)
	{
		if (resolved)
		{
			return false;
		}
		Set<String> wanted = new HashSet<>(catalog.getAllItemNames());
		wanted.addAll(catalog.getFormulaItemNames());
		int count = client.getItemCount();
		for (int id = 0; id < count; id++)
		{
			ItemComposition comp = client.getItemDefinition(id);
			if (comp == null || comp.getNote() != -1 || comp.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			String key = BossCatalog.normalize(comp.getName());
			if (wanted.contains(key))
			{
				itemIds.putIfAbsent(key, id);
			}
		}
		resolved = true;
		refresh(true);
		return true;
	}

	/** Copies the prices of all tracked items from RuneLite's list. Client thread only. */
	public void refresh(boolean force)
	{
		long now = System.currentTimeMillis();
		if (!force && now - refreshedAt < REFRESH_MILLIS)
		{
			return;
		}
		refreshedAt = now;
		for (Map.Entry<String, Integer> e : itemIds.entrySet())
		{
			try
			{
				prices.put(e.getKey(), (long) Math.max(0, itemManager.getItemPrice(e.getValue())));
			}
			catch (RuntimeException ex)
			{
				log.debug("No price for {}", e.getKey(), ex);
			}
		}
	}

	/** GP value of an item: its price, or what it makes when it can't be traded. */
	public long priceOf(String itemName)
	{
		Long price = prices.get(BossCatalog.normalize(itemName));
		if (price != null && price > 0)
		{
			return price;
		}
		ValueFormula formula = catalog.getValueFormula(itemName);
		return formula == null ? 0 : formula.evaluate(this::tradedPrice);
	}

	private long tradedPrice(String itemName)
	{
		Long price = prices.get(BossCatalog.normalize(itemName));
		return price == null ? 0 : price;
	}

	/** The id for an item picture, or -1 while it is not known. */
	public int idOf(String itemName)
	{
		Integer id = itemIds.get(BossCatalog.normalize(itemName));
		return id == null ? -1 : id;
	}

	public boolean isResolved()
	{
		return resolved;
	}

	public void clear()
	{
		itemIds.clear();
		prices.clear();
		resolved = false;
		refreshedAt = 0;
	}
}
