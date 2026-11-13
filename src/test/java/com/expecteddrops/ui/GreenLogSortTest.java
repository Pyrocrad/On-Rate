package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.KillTimes;
import com.onrate.engine.stats.GreenLog;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import com.onrate.engine.tracking.Tracker;
import java.awt.Component;
import java.awt.Container;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import org.junit.Test;

/** "Closest to green log": a sort of the Bosses list, with a Pets slider and the time on each row. */
public class GreenLogSortTest
{
	static
	{
		KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static Tracker tracker() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		// a collection log page opened at a few bosses (kills, no uniques), so the account overview has rows
		int kills = 200;
		for (com.onrate.engine.data.BossDefinition boss : catalog.getBosses())
		{
			if (boss.getKcNames().isEmpty() || boss.getClogPages().isEmpty())
			{
				continue;
			}
			List<String> counters = new ArrayList<>();
			for (String name : boss.getKcNames())
			{
				counters.add(name + " kills: " + kills);
			}
			java.util.Map<String, Integer> items = new java.util.LinkedHashMap<>();
			for (com.onrate.engine.data.DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					items.put(name, 0);
				}
			}
			tracker.onClogPage(boss.getClogPages().get(0), counters, items);
		}
		return tracker;
	}

	private static ExpectedDropsPanel panel(Tracker tracker) throws Exception
	{
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { }, () -> false, () -> { },
			name -> 1L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
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

	private static PetsSwitch petsSwitch(ExpectedDropsPanel panel) throws Exception
	{
		Field f = ExpectedDropsPanel.class.getDeclaredField("petsSwitch");
		f.setAccessible(true);
		return (PetsSwitch) f.get(panel);
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
	public void theSortIsTheLastOne() throws Exception
	{
		JComboBox<Object> box = sortBox(panel(tracker()));
		assertEquals("Closest to green log", box.getItemAt(box.getItemCount() - 1).toString());
	}

	@Test
	public void theSliderShowsOnlyForThatSort() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		JComboBox<Object> box = sortBox(panel);
		assertFalse(petsSwitch(panel).isVisible());
		box.setSelectedItem(box.getItemAt(box.getItemCount() - 1));
		panel.refresh();
		assertTrue(petsSwitch(panel).isVisible());
		box.setSelectedIndex(0);
		panel.refresh();
		assertFalse(petsSwitch(panel).isVisible());
	}

	@Test
	public void theRowsShowATimeInsteadOfAVerdict() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		JComboBox<Object> box = sortBox(panel);
		box.setSelectedItem(box.getItemAt(box.getItemCount() - 1));
		panel.refresh();
		List<String> shown = new ArrayList<>();
		texts(panel, shown);
		boolean timed = false;
		for (String text : shown)
		{
			timed |= text.matches("~?[0-9,.]+ (h|min)");
		}
		assertTrue("a row shows a time: " + shown, timed);
	}

	@Test
	public void turningPetsOnMakesEveryTimeLonger() throws Exception
	{
		Tracker tracker = tracker();
		Map<String, GreenLog.Page> without = GreenLog.compute(tracker, KillTimes.bundled(), RAIDS, b -> true, false);
		Map<String, GreenLog.Page> with = GreenLog.compute(tracker, KillTimes.bundled(), RAIDS, b -> true, true);
		for (String boss : new String[]{"Zulrah", "Vorkath"})
		{
			assertNotNull(boss, without.get(boss));
			assertTrue(boss, with.get(boss).getAverageSeconds() >= without.get(boss).getAverageSeconds());
		}
	}

	@Test
	public void theSliderChangesTheList() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		JComboBox<Object> box = sortBox(panel);
		box.setSelectedItem(box.getItemAt(box.getItemCount() - 1));
		panel.refresh();
		List<String> before = new ArrayList<>();
		texts(panel, before);
		Method toggle = PetsSwitch.class.getDeclaredMethod("toggle");
		toggle.setAccessible(true);
		toggle.invoke(petsSwitch(panel));
		assertTrue(petsSwitch(panel).isOn());
		List<String> after = new ArrayList<>();
		texts(panel, after);
		assertFalse("the times change with pets on", before.equals(after));
	}

	@Test
	public void quickestFirstThenNoAnswerThenDone() throws Exception
	{
		Tracker tracker = tracker();
		Map<String, GreenLog.Page> pages = GreenLog.compute(tracker, KillTimes.bundled(), RAIDS, b -> true, false);
		List<RowStats> rows = new ArrayList<>();
		for (String name : new String[]{"Vorkath", "Zulrah", "Corporeal Beast", "No such boss"})
		{
			rows.add(new RowStats(name, null, 0.01, 1, 0, false));
		}
		rows.sort(ExpectedDropsPanel.greenLogOrder(pages));
		// the one with no page is after every timed one
		assertEquals("No such boss", rows.get(rows.size() - 1).getLabel());
		double last = 0;
		for (RowStats row : rows.subList(0, rows.size() - 1))
		{
			GreenLog.Page page = pages.get(row.getLabel());
			if (page != null && !page.isDone() && !Double.isNaN(page.getAverageSeconds()))
			{
				assertTrue(row.getLabel(), page.getAverageSeconds() >= last);
				last = page.getAverageSeconds();
			}
		}
	}

	@Test
	public void theTimeIsMarkedWhenItRestsOnTheWiki()
	{
		assertEquals("-", ExpectedDropsPanel.greenLogText(null));
		GreenLog.Page zulrah = null;
		try
		{
			zulrah = GreenLog.compute(new Tracker(BossCatalog.load(new Gson(), Bundled.reader("bosses.json"))), KillTimes.bundled(),
				RAIDS, b -> true, false).get("Zulrah");
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
		assertTrue(zulrah.isEstimated());
		assertTrue(ExpectedDropsPanel.greenLogText(zulrah).startsWith("~"));
	}
}
