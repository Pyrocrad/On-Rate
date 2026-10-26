package com.expecteddrops.ui;

import com.expecteddrops.ExpectedDropsPlugin;
import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.tracking.KillEntry;
import com.expecteddrops.tracking.Tracker;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The right-click "Remove from History" menu on Recent and History lines. */
public class RemoveFromHistoryMenuTest
{
	private static final long NOW = System.currentTimeMillis();

	private static Tracker tracker() throws Exception
	{
		try (Reader reader = new InputStreamReader(ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			Tracker tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.03);
			return tracker;
		}
	}

	private static ExpectedDropsPanel panel(Tracker tracker) throws Exception
	{
		RaidSettings raids = raid -> 0.03;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { }, () -> false, () -> { },
			s -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, true, false, false, false, true));
		panel.setSize(225, 4000);
		return panel;
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static Object selectedBoss(ExpectedDropsPanel panel) throws Exception
	{
		java.lang.reflect.Field f = ExpectedDropsPanel.class.getDeclaredField("bossSelect");
		f.setAccessible(true);
		return ((JComboBox<?>) f.get(panel)).getSelectedItem();
	}

	/** The right-click menu a line got from the panel, or null. */
	private static JPopupMenu menuOf(Component c) throws Exception
	{
		for (MouseListener l : c.getMouseListeners())
		{
			for (java.lang.reflect.Field f : l.getClass().getDeclaredFields())
			{
				if (JPopupMenu.class.isAssignableFrom(f.getType()))
				{
					f.setAccessible(true);
					return (JPopupMenu) f.get(l);
				}
			}
		}
		return null;
	}

	/** Every line (a JPanel) whose menu is the Remove from History one. */
	private static void removeLines(Component c, List<JPanel> out) throws Exception
	{
		JPopupMenu menu = c instanceof JPanel ? menuOf(c) : null;
		if (menu != null && menu.getComponentCount() > 0
			&& "Remove from History".equals(((JMenuItem) menu.getComponent(0)).getText()))
		{
			out.add((JPanel) c);
			return;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				removeLines(child, out);
			}
		}
	}

	private static List<JPanel> removeLines(ExpectedDropsPanel panel) throws Exception
	{
		List<JPanel> out = new ArrayList<>();
		removeLines(panel, out);
		return out;
	}

	private static void click(Component c, int button)
	{
		c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, NOW, 0, 5, 5, 1, false, button));
	}

	@Test
	public void recentLinesHaveTheMenuAndOnlyALeftClickOpensTheBoss() throws Exception
	{
		Tracker tracker = tracker();
		// the account overview has its tabs once a boss is synced
		java.util.Map<String, Integer> page = new java.util.LinkedHashMap<>();
		page.put("Vorkath's head", 0);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 200"), page);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>201</col>.", NOW);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), NOW);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>202</col>.", NOW + 1);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), NOW + 1);
		ExpectedDropsPanel panel = panel(tracker);
		set(panel, "accountTab", 2);
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.refresh();

		// one menu per latest unique, and none on the day lines
		List<JPanel> lines = removeLines(panel);
		assertEquals(2, lines.size());
		JPopupMenu menu = menuOf(lines.get(0));
		assertEquals(1, menu.getComponentCount());
		assertTrue(!(menu.getComponent(0) instanceof JMenu));

		// a right click is for the menu: it doesn't open the boss; a left click still does
		click(lines.get(0), MouseEvent.BUTTON3);
		assertEquals(ExpectedDropsPanel.ACCOUNT, selectedBoss(panel));
		click(lines.get(0), MouseEvent.BUTTON1);
		assertEquals("Vorkath", selectedBoss(panel));
	}

	@Test
	public void historyLinesHaveTheMenuOneItemPerUniqueOfAKill() throws Exception
	{
		Tracker tracker = tracker();
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>10</col>.", NOW);
		tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), NOW);
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>11</col>.", NOW + 1);
		// a kill line that lists two uniques (Nex lists every kill with its team)
		List<KillEntry> nex = new Gson().fromJson("[{\"k\":5,\"n\":2,\"t\":" + NOW
			+ ",\"d\":[{\"i\":\"Zaryte vambraces\"},{\"i\":\"Nihil horn\"}]}]", new com.google.gson.reflect.TypeToken<List<KillEntry>>()
		{
		}.getType());
		tracker.setKills("Nex", nex);
		ExpectedDropsPanel panel = panel(tracker);
		set(panel, "historyOpen", true);

		panel.showBoss("Vorkath");
		panel.refresh();
		// the line with the head; the kill without a unique isn't listed
		assertEquals(1, removeLines(panel).size());

		panel.showBoss("Nex");
		panel.refresh();
		List<JPanel> lines = removeLines(panel);
		assertEquals(1, lines.size());
		JMenu each = (JMenu) menuOf(lines.get(0)).getComponent(0);
		assertEquals(2, each.getItemCount());
		assertEquals("Zaryte vambraces", each.getItem(0).getText());
		assertEquals("Nihil horn", each.getItem(1).getText());
	}
}
