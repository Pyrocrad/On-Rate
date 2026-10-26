package com.expecteddrops.ui;

import com.expecteddrops.ExpectedDropsPlugin;
import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.stats.SharedPool;
import com.expecteddrops.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The "also drops from" note in the hover of an item and of a shared item. */
public class AlsoFromNoteTest
{
	private static Tracker tracker() throws Exception
	{
		try (Reader reader = new InputStreamReader(ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			Tracker tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.03);
			return tracker;
		}
	}

	private static String itemTip(RowStats row, BossStats stats) throws Exception
	{
		Method m = ExpectedDropsPanel.class.getDeclaredMethod("tooltip", RowStats.class, BossStats.class, String.class);
		m.setAccessible(true);
		return (String) m.invoke(null, row, stats, "kill");
	}

	private static String poolTip(SharedPool pool) throws Exception
	{
		Method m = ExpectedDropsPanel.class.getDeclaredMethod("poolTooltip", SharedPool.class, RowStats.class);
		m.setAccessible(true);
		return (String) m.invoke(null, pool, pool.toRow());
	}

	@Test
	public void anItemOtherMonstersDropOffersToLeaveItOut() throws Exception
	{
		Tracker tracker = tracker();
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Kraken tentacle", 3);
		tracker.onClogPage("Kraken", Collections.singletonList("Kraken kills: 800"), page);
		BossStats stats = tracker.stats("Kraken", raid -> 0.03);
		String tentacle = null;
		String jar = null;
		for (RowStats row : stats.getRows())
		{
			if (row.getLabel().equals("Kraken tentacle"))
			{
				tentacle = itemTip(row, stats);
			}
			else if (row.getLabel().equals("Jar of dirt"))
			{
				jar = itemTip(row, stats);
			}
		}
		assertTrue(tentacle, tentacle.contains("Also drops from cave krakens, and the collection log counts those too."));
		assertTrue(tentacle.contains("Right-click to leave it out."));
		assertFalse(jar, jar.contains("Also drops from"));
	}

	@Test
	public void aSharedItemOtherMonstersDropSaysItCanLookLuckier() throws Exception
	{
		Tracker tracker = tracker();
		boolean seen = false;
		for (SharedPool pool : tracker.sharedPools(raid -> 0.03))
		{
			String tip = poolTip(pool);
			if (pool.getItemName().equals("Dragon chainbody"))
			{
				assertTrue(tip, tip.contains("Also drops from dust devils and smoke devils, and the collection log counts "
					+ "those too, so this can look luckier than it is."));
				// shared items have no right-click to leave them out
				assertFalse(tip.contains("Right-click"));
				seen = true;
			}
			else if (pool.getItemName().equals("Dragon pickaxe"))
			{
				assertFalse(tip, tip.contains("Also drops from"));
			}
		}
		assertTrue(seen);
	}
}
