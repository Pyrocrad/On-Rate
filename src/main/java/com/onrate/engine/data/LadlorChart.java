package com.onrate.engine.data;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The boss drops on the Ladlor ironman progression chart, in the order of the chart
 * (ladlorchart.json, bundled with the plugin). Each chart item lists the drops it needs.
 */
public final class LadlorChart
{
	private static LadlorChart bundled;

	private final List<Item> items;

	/** A drop a chart item needs, or a number of kills of a boss (a reward that is sure at that kill count). */
	public static final class Need
	{
		private String boss;
		private String drop;
		private Integer kills;
		private Integer count;

		public String getBoss()
		{
			return boss;
		}

		/** The drop's name as the boss page has it, or null for a kill-count need. */
		public String getDrop()
		{
			return drop;
		}

		/** How many of the drop are needed (crystal armour seeds: 3 for the body), at least 1. */
		public int getCount()
		{
			return count == null || count < 1 ? 1 : count;
		}

		/** Kills of the boss needed, or null for a drop. */
		public Integer getKills()
		{
			return kills;
		}
	}

	/** One item of the chart. */
	public static final class Item
	{
		private int step;
		private String name;
		private String icon;
		private List<Need> needs;
		private String note;
		private String route;

		/** Its place in the chart: items of one step share a number. */
		public int getStep()
		{
			return step;
		}

		public String getName()
		{
			return name;
		}

		/** An item name for the picture. */
		public String getIcon()
		{
			return icon;
		}

		public List<Need> getNeeds()
		{
			return needs == null ? Collections.emptyList() : needs;
		}

		/** What the chart item needs besides these drops and isn't counted, or null. */
		public String getNote()
		{
			return note;
		}

		/** A second way to get the item with its own explanation ("oathplate-shards"), or null. */
		public String getRoute()
		{
			return route;
		}
	}

	private static final class FileFormat
	{
		List<Item> items;
	}

	private LadlorChart(FileFormat file)
	{
		this.items = file.items == null ? new ArrayList<>() : file.items;
	}

	public static LadlorChart load(Gson gson, Reader reader)
	{
		FileFormat file = gson.fromJson(reader, FileFormat.class);
		return new LadlorChart(file == null ? new FileFormat() : file);
	}

	/**
	 * The file that ships with the plugin, loaded with the given Gson the first time and cached; empty
	 * if it can't be read.
	 */
	public static synchronized LadlorChart bundled(Gson gson)
	{
		if (bundled == null)
		{
			try (Reader reader = new InputStreamReader(LadlorChart.class.getResourceAsStream("/com/onrate/ladlorchart.json"),
				StandardCharsets.UTF_8))
			{
				bundled = load(gson, reader);
			}
			catch (Exception e)
			{
				bundled = new LadlorChart(new FileFormat());
			}
		}
		return bundled;
	}

	/** The file loaded by {@link #bundled(Gson)}; empty until the plugin has loaded it. */
	public static synchronized LadlorChart bundled()
	{
		return bundled == null ? new LadlorChart(new FileFormat()) : bundled;
	}

	/** The chart's items in chart order. */
	public List<Item> getItems()
	{
		return Collections.unmodifiableList(items);
	}
}
