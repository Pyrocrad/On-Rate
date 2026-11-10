package com.expecteddrops.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.expecteddrops.ExpectedDropsPlugin;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import org.junit.Test;

/** "Copy my luck" in the footer: only on an account overview with a loaded boss. */
public class ShareLinkTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static BossCatalog catalog() throws Exception
	{
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			return BossCatalog.load(new Gson(), reader);
		}
	}

	/** A tracker with one boss loaded from its collection log page, or none loaded. */
	private static Tracker tracker(BossCatalog catalog, BossDefinition loaded)
	{
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		if (loaded != null)
		{
			List<String> counters = new ArrayList<>();
			for (String name : loaded.getKcNames())
			{
				counters.add(name + " kills: 200");
			}
			Map<String, Integer> items = new LinkedHashMap<>();
			for (DropDefinition drop : loaded.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					items.put(name, 0);
				}
			}
			tracker.onClogPage(loaded.getClogPages().get(0), counters, items);
		}
		return tracker;
	}

	private static BossDefinition aBoss(BossCatalog catalog)
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			if (!boss.getKcNames().isEmpty() && !boss.getClogPages().isEmpty() && !boss.getDrops().isEmpty())
			{
				return boss;
			}
		}
		throw new AssertionError("no boss to load");
	}

	private static ExpectedDropsPanel panel(Tracker tracker, String page) throws Exception
	{
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { }, () -> false, () -> { },
			name -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, true, false, false, false, true));
		panel.showBoss(page);
		panel.refresh();
		return panel;
	}

	private static Object field(ExpectedDropsPanel panel, String name) throws Exception
	{
		Field f = ExpectedDropsPanel.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(panel);
	}

	@Test
	public void shownOnTheAccountOverviewOnceABossIsLoaded() throws Exception
	{
		BossCatalog catalog = catalog();
		ExpectedDropsPanel panel = panel(tracker(catalog, aBoss(catalog)), ExpectedDropsPanel.ACCOUNT);
		assertTrue(((JLabel) field(panel, "shareLink")).isVisible());
		assertTrue(((JLabel) field(panel, "shareSep")).isVisible());
		String text = (String) field(panel, "shareText");
		assertNotNull(text);
		assertTrue(text, text.startsWith("On Rate: "));
	}

	@Test
	public void hiddenWhenNothingIsLoadedYet() throws Exception
	{
		BossCatalog catalog = catalog();
		ExpectedDropsPanel panel = panel(tracker(catalog, null), ExpectedDropsPanel.ACCOUNT);
		assertFalse(((JLabel) field(panel, "shareLink")).isVisible());
		assertFalse(((JLabel) field(panel, "shareSep")).isVisible());
		assertNull(field(panel, "shareText"));
	}

	@Test
	public void hiddenOnABossPage() throws Exception
	{
		BossCatalog catalog = catalog();
		BossDefinition boss = aBoss(catalog);
		ExpectedDropsPanel panel = panel(tracker(catalog, boss), ExpectedDropsPanel.ACCOUNT);
		panel.showBoss(boss.getName());
		panel.refresh();
		assertFalse(((JLabel) field(panel, "shareLink")).isVisible());
		assertNull(field(panel, "shareText"));
	}
}
