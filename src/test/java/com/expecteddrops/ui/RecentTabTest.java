package com.expecteddrops.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.awt.Component;
import java.awt.Container;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import org.junit.Test;

/** The Recent tab: two tiles, a Uniques / By boss / Trips switch, and one page at a time. */
public class RecentTabTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final long NOW = System.currentTimeMillis();

	private static ExpectedDropsPanel panel() throws Exception
	{
		Tracker tracker;
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			tracker = new Tracker(BossCatalog.load(new Gson(), reader));
		}
		tracker.setSettings(raid -> 0.03);
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Vorkath's head", 0);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 200"), page);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>201</col>.", NOW);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), NOW);
		RaidSettings raids = raid -> 0.03;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { }, () -> false, () -> { },
			s -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, true, false, false, false, true));
		panel.setSize(225, 4000);
		set(panel, "accountTab", 2);
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		return panel;
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static void texts(Component c, List<String> out)
	{
		if (c instanceof JLabel && ((JLabel) c).getText() != null)
		{
			out.add(((JLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				texts(child, out);
			}
		}
	}

	private static String all(ExpectedDropsPanel panel)
	{
		List<String> out = new ArrayList<>();
		texts(panel, out);
		return String.join("|", out);
	}

	@Test
	public void tilesAndSwitchAreAlwaysThereAndTheUniquesPageIsFirst() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		panel.refresh();
		String text = all(panel);
		assertTrue(text, text.contains("TODAY") && text.contains("LAST 7 DAYS"));
		assertTrue(text, text.contains("Uniques") && text.contains("By boss") && text.contains("Trips"));
		assertTrue(text, text.contains("LATEST UNIQUES"));
	}

	@Test
	public void theSwitchShowsOnePageAtATime() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		set(panel, "recentView", 1);
		panel.refresh();
		String bosses = all(panel);
		assertTrue(bosses, bosses.contains("TODAY") && bosses.contains("Vorkath"));
		assertFalse(bosses, bosses.contains("LATEST UNIQUES"));
		set(panel, "recentView", 2);
		panel.refresh();
		assertTrue(all(panel), all(panel).contains("No finished trips yet."));
	}

	@Test
	public void theOldPlainTabIsStillThereForTheParityComparison() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		set(panel, "recentTabs", false);
		panel.refresh();
		String text = all(panel);
		assertFalse(text, text.contains("By boss"));
		assertTrue(text, text.contains("LATEST UNIQUES"));
	}
}
