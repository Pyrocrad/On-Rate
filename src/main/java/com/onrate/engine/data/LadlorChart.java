package com.onrate.engine.data;

import lombok.Getter;
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
		@Getter private String boss;
		@Getter private String drop;
		@Getter private Integer kills;
		private Integer count;

		/** How many of the drop are needed (crystal armour seeds: 3 for the body), at least 1. */
		public int getCount()
		{
			return count == null || count < 1 ? 1 : count;
		}
	}

	/** One item of the chart. */
	public static final class Item
	{
		@Getter private int step;
		@Getter private String name;
		@Getter private String icon;
		private List<Need> needs;
		@Getter private String note;
		@Getter private String route;

		public List<Need> getNeeds()
		{
			return needs == null ? Collections.emptyList() : needs;
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
