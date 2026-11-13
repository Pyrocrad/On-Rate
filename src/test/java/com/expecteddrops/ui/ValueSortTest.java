package com.expecteddrops.ui;

import com.onrate.engine.math.Luck;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.expecteddrops.ExpectedDropsPlugin;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import com.onrate.engine.tracking.Tracker;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import org.junit.Test;

/** "Luckiest by GP value": the boss list ordered and worded by its GP value verdict. */
public class ValueSortTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static Tracker seededTracker() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		Random random = new Random(5);
		for (BossDefinition boss : catalog.getBosses())
		{
			if (boss.getKcNames().isEmpty() || boss.getClogPages().isEmpty())
			{
				continue;
			}
			int kills = 80 + random.nextInt(500);
			List<String> counters = new ArrayList<>();
			for (String name : boss.getKcNames())
			{
				counters.add(name + " kills: " + kills);
			}
			Map<String, Integer> items = new LinkedHashMap<>();
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					items.put(name, random.nextInt(10) < 3 ? 1 : 0);
				}
			}
			tracker.onClogPage(boss.getClogPages().get(0), counters, items);
		}
		return tracker;
	}

	private static ExpectedDropsPanel panel(Tracker tracker, boolean geValue) throws Exception
	{
		// every item is worth 1m, except that a different price per item makes the order depend on the item
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { },
			name -> 1_000_000L + Math.abs(name.hashCode() % 50) * 1_000_000L,
			() -> new ViewOptions(ItemView.TARGET_BARS, geValue, false, false, false, true));
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.refresh();
		return panel;
	}

	@SuppressWarnings("unchecked")
	private static JComboBox<Object> sortBox(ExpectedDropsPanel panel) throws Exception
	{
		Field f = ExpectedDropsPanel.class.getDeclaredField("sortSelect");
		f.setAccessible(true);
		return (JComboBox<Object>) f.get(panel);
	}

	private static List<String> options(JComboBox<Object> box)
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < box.getItemCount(); i++)
		{
			names.add(box.getItemAt(i).toString());
		}
		return names;
	}

	private static void select(JComboBox<Object> box, String label)
	{
		for (int i = 0; i < box.getItemCount(); i++)
		{
			if (box.getItemAt(i).toString().equals(label))
			{
				box.setSelectedIndex(i);
				return;
			}
		}
		throw new AssertionError("no option " + label + " in " + options(box));
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

	@Test
	public void theOptionIsOfferedOnlyWhileGpValueIsOn() throws Exception
	{
		Tracker tracker = seededTracker();
		List<String> on = options(sortBox(panel(tracker, true)));
		assertEquals("Luckiest by GP value", on.get(on.indexOf("Luckiest first") + 1));
		assertFalse(options(sortBox(panel(tracker, false))).contains("Luckiest by GP value"));
	}

	@Test
	public void theChoiceFallsBackWhenGpValueIsTurnedOff() throws Exception
	{
		Tracker tracker = seededTracker();
		boolean[] geValue = {true};
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { },
			name -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, geValue[0], false, false, false, true));
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		JComboBox<Object> box = sortBox(panel);
		select(box, "Luckiest by GP value");
		panel.refresh();
		assertEquals("Luckiest by GP value", box.getSelectedItem().toString());
		// GP value is switched off in the settings: the option goes and the list is on Luckiest first
		geValue[0] = false;
		panel.refresh();
		assertFalse(options(box).contains("Luckiest by GP value"));
		assertEquals("Luckiest first", box.getSelectedItem().toString());
	}

	@Test
	public void bossesAreOrderedByTheirGpValueLuck() throws Exception
	{
		Tracker tracker = seededTracker();
		ExpectedDropsPanel panel = panel(tracker, true);
		select(sortBox(panel), "Luckiest by GP value");
		panel.refresh();

		// the order the panel should show: its own GP luck per boss, luckiest first, bosses without value last
		Method valueOf = ExpectedDropsPanel.class.getDeclaredMethod("bossValueOf", BossStats.class);
		valueOf.setAccessible(true);
		List<String> shown = new ArrayList<>();
		texts(panel, shown);
		Map<String, Double> luck = new LinkedHashMap<>();
		for (com.onrate.engine.stats.AccountStats a = tracker.accountStats(RAIDS, name -> 1L); a != null; a = null)
		{
			for (RowStats row : a.getRows())
			{
				double[] v = (double[]) valueOf.invoke(panel, tracker.stats(row.getLabel(), RAIDS));
				if (v != null && v[1] >= 1)
				{
					luck.put(row.getLabel(), v[2]);
				}
			}
		}
		assertTrue("some bosses have value", luck.size() > 5);
		List<String> expected = new ArrayList<>(luck.keySet());
		expected.sort((a, b) -> luck.get(a).equals(luck.get(b)) ? a.toLowerCase(java.util.Locale.ROOT).compareTo(b.toLowerCase(java.util.Locale.ROOT))
			: Double.compare(luck.get(b), luck.get(a)));
		List<String> order = new ArrayList<>();
		for (String text : shown)
		{
			// a long name wraps with a <br>: that is a space between the words
			String name = text.replaceAll("<br\\s*/?>", " ").replaceAll("<[^>]*>", "").replace(" *", "").trim();
			if (luck.containsKey(name) && !order.contains(name))
			{
				order.add(name);
			}
		}
		assertEquals(expected.subList(0, Math.min(expected.size(), order.size())), order.subList(0, Math.min(expected.size(), order.size())));
		assertTrue("the list shows bosses", order.size() > 5);
	}

	@Test
	public void aDisplayCopyChangesOnlyTheLuck()
	{
		RowStats row = new RowStats("Chambers of Xeric", "Twisted bow", 0.035, 6.3, 4, true).withStreak(120, true);
		RowStats shown = row.withDisplayLuck(0.995);
		assertEquals(0.995, shown.getLuck(), 0);
		assertTrue(Double.isNaN(row.withDisplayLuck(Double.NaN).getLuck()));
		// the row itself, its numbers and its hover shares are untouched
		assertEquals(Luck.of(4, 6.3), row.getLuck(), 1e-12);
		assertEquals(row.getExpected(), shown.getExpected(), 0);
		assertEquals(row.getReceived(), shown.getReceived());
		assertEquals(row.getStreak(), shown.getStreak());
		assertEquals(row.getShares()[0], shown.getShares()[0], 0);
	}
}
