package com.onrate.game;

import com.onrate.engine.tracking.ClogPage;
import com.onrate.engine.tracking.Tracker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a collection log page from the game's widgets when the player opens it, and hands it to
 * the tracker. A page that can't be read changes nothing: a missing read is never "zero drops".
 */
public final class ClogReader
{
	private static final Logger log = LoggerFactory.getLogger(ClogReader.class);

	private final Client client;
	private final ItemManager itemManager;
	private final Tracker tracker;
	private final PriceBook prices;
	private final Consumer<String> synced;
	private final Runnable unreadable;
	/** Problems already warned about this session (a page title, or "header"), so each shows once. */
	private final Set<String> reported = new HashSet<>();
	/** Titles of pages we don't cover already noted at debug level. */
	private final Set<String> uncovered = new HashSet<>();

	/**
	 * @param synced     told the first boss a page changed
	 * @param unreadable told when a page we cover could not be read
	 */
	public ClogReader(Client client, ItemManager itemManager, Tracker tracker, PriceBook prices, Consumer<String> synced,
		Runnable unreadable)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.tracker = tracker;
		this.prices = prices;
		this.synced = synced;
		this.unreadable = unreadable;
	}

	/** Reads the page that is open now. Client thread only. */
	public void read()
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget itemsWidget = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (header == null || itemsWidget == null)
		{
			// most likely the log was closed before the next tick; not worth telling the player
			log.debug("Collection log page drawn but its widgets are gone (header {}, items {})", header != null, itemsWidget != null);
			return;
		}
		Widget[] headerLines = header.getDynamicChildren();
		String rawTitle = headerLines == null || headerLines.length == 0 ? null : headerLines[0].getText();
		List<String> rawCounters = new ArrayList<>();
		for (int i = 1; headerLines != null && i < headerLines.length; i++)
		{
			rawCounters.add(headerLines[i].getText());
		}

		List<ClogPage.Item> rawItems = new ArrayList<>();
		Widget[] itemWidgets = itemsWidget.getDynamicChildren();
		if (itemWidgets != null)
		{
			for (Widget w : itemWidgets)
			{
				int id = w.getItemId();
				if (id <= 0)
				{
					continue;
				}
				String name = itemManager.getItemComposition(id).getName();
				rawItems.add(new ClogPage.Item(name, w.getItemQuantity(), w.getOpacity()));
				prices.learn(name, id);
			}
		}

		ClogPage.Result page = ClogPage.parse(rawTitle, rawCounters, rawItems);
		String title = page.getTitle();
		ClogPage.Problem problem = page.getProblem();
		if (problem == ClogPage.Problem.NO_TITLE || problem == ClogPage.Problem.TITLE_IS_A_COUNTER)
		{
			// not even the page is known: the header may have changed
			fail(null, problem.toString());
			return;
		}
		if (!tracker.coversPage(title))
		{
			// a page On Rate doesn't read (Clues, Minigames, ...): normal, nothing to say
			if (uncovered.add(title))
			{
				log.debug("Collection log page \"{}\" is not one On Rate covers", title);
			}
			return;
		}
		if (problem != null)
		{
			fail(title, problem.toString());
			return;
		}
		if (page.isAllFaded() && tracker.wouldZeroSaved(title, page.getItems()))
		{
			// the log never loses items, so this page was misread: keep what we have
			fail(title, "every item read as not obtained, but drops are saved");
			return;
		}
		Set<String> changed = tracker.onClogPage(title, page.getCounters(), page.getItems());
		if (!changed.isEmpty())
		{
			synced.accept(changed.iterator().next());
		}
	}

	private void fail(String title, String reason)
	{
		if (tracker.onClogPageUnreadable(title) && reported.add(title == null ? "header" : title))
		{
			if (title == null)
			{
				log.warn("Collection log opened but its header could not be read: {}", reason);
			}
			else
			{
				log.warn("Collection log page \"{}\" opened but not read: {}", title, reason);
			}
		}
		unreadable.run();
	}
}
