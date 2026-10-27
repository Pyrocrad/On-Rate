package com.expecteddrops.tracking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns what a collection log page shows (plain values copied from the client's widgets) into
 * the page title, its counters and the quantity obtained of each item. No client needed, so it
 * can be tested; the plugin only copies the widgets into these values.
 */
public final class ClogPage
{
	/**
	 * Opacity of an item the player has: the log draws items they don't have faded (more than 0).
	 * RuneLite has no constant for it; it is how the log draws. To re-check after a game update:
	 * run the plugin with `./gradlew run`, open a log page, and compare the opacity of an owned
	 * and a missing item in RuneLite's widget inspector (children of InterfaceID.Collection.ITEMS_CONTENTS).
	 */
	public static final int OBTAINED_OPACITY = 0;

	/** A counter line such as "Vorkath kills: 1,234". */
	private static final Pattern COUNTER_LINE = Pattern.compile(".*:\\s*[\\d,]+\\s*");

	/** One item of a page, as the widget shows it. */
	public static final class Item
	{
		final String name;
		final int quantity;
		final int opacity;

		public Item(String name, int quantity, int opacity)
		{
			this.name = name;
			this.quantity = quantity;
			this.opacity = opacity;
		}
	}

	/** Why a page could not be read. */
	public enum Problem
	{
		/** No title: the header widget has no lines or its first line is empty. */
		NO_TITLE,
		/** The first header line is a counter, so the header layout is not what we expect. */
		TITLE_IS_A_COUNTER,
		/** The title is there but no item could be read. */
		NO_ITEMS,
	}

	/** What was read. Use title, counters and items only when {@link #getProblem()} is null. */
	public static final class Result
	{
		private final Problem problem;
		private final String title;
		private final List<String> counters;
		private final Map<String, Integer> items;
		private final boolean allFaded;

		Result(Problem problem, String title, List<String> counters, Map<String, Integer> items, boolean allFaded)
		{
			this.problem = problem;
			this.title = title;
			this.counters = Collections.unmodifiableList(counters);
			this.items = Collections.unmodifiableMap(items);
			this.allFaded = allFaded;
		}

		public Problem getProblem()
		{
			return problem;
		}

		/** Page title with tags removed; empty when there is none. */
		public String getTitle()
		{
			return title;
		}

		/** Header lines after the title ("Vorkath kills: 123"), tags removed. */
		public List<String> getCounters()
		{
			return counters;
		}

		/** Quantity obtained per item name; 0 for items the player does not have. */
		public Map<String, Integer> getItems()
		{
			return items;
		}

		/** True when the page has items and every one reads as not obtained. */
		public boolean isAllFaded()
		{
			return allFaded;
		}
	}

	private ClogPage()
	{
	}

	/**
	 * @param rawTitle    first header line as drawn (may have tags), or null when the header has no lines
	 * @param rawCounters the other header lines, as drawn
	 * @param items       every item widget of the page that holds an item
	 */
	public static Result parse(String rawTitle, List<String> rawCounters, List<Item> items)
	{
		String title = rawTitle == null ? "" : stripTags(rawTitle).trim();

		List<String> counters = new ArrayList<>();
		if (rawCounters != null)
		{
			for (String line : rawCounters)
			{
				String text = line == null ? "" : stripTags(line).trim();
				if (!text.isEmpty())
				{
					counters.add(text);
				}
			}
		}

		Map<String, Integer> obtained = new LinkedHashMap<>();
		boolean anyObtained = false;
		if (items != null)
		{
			for (Item item : items)
			{
				if (item == null || item.name == null || item.name.isEmpty())
				{
					continue;
				}
				boolean has = item.opacity == OBTAINED_OPACITY;
				obtained.put(item.name, has ? Math.max(1, item.quantity) : 0);
				anyObtained |= has;
			}
		}
		boolean allFaded = !obtained.isEmpty() && !anyObtained;

		Problem problem = title.isEmpty() ? Problem.NO_TITLE
			: COUNTER_LINE.matcher(title).matches() ? Problem.TITLE_IS_A_COUNTER
			: obtained.isEmpty() ? Problem.NO_ITEMS
			: null;
		return new Result(problem, title, counters, obtained, allFaded);
	}

	private static String stripTags(String text)
	{
		return text.replaceAll("<[^>]*>", "");
	}
}
