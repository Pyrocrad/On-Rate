package com.expecteddrops.ui;

import com.expecteddrops.ExpectedDropsPlugin;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseListener;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** The right-click menu that judges a shared item on each boss separately, and back. */
public class SplitSharedMenuTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static Tracker tracker() throws Exception
	{
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			Tracker tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.03);
			return tracker;
		}
	}

	private static ExpectedDropsPanel panel(Tracker tracker)
	{
		RaidSettings raids = raid -> 0.03;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { },
			s -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, true, false, false, false, true));
		panel.setSize(225, 4000);
		return panel;
	}

	private static JPopupMenu menuOf(Component c) throws Exception
	{
		for (MouseListener l : c.getMouseListeners())
		{
			if (!l.getClass().getName().startsWith("com.expecteddrops"))
			{
				continue;
			}
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

	/** The first menu entry (an item or a submenu) anywhere on the page with this text. */
	private static JMenuItem entry(Component c, String text) throws Exception
	{
		JPopupMenu menu = menuOf(c);
		if (menu != null)
		{
			for (Component e : menu.getComponents())
			{
				if (e instanceof JMenuItem && text.equals(((JMenuItem) e).getText()))
				{
					return (JMenuItem) e;
				}
			}
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				JMenuItem found = entry(child, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	@Test
	public void olderCopiesGoToTheBossPickedAndCanComeBack() throws Exception
	{
		Tracker tracker = tracker();
		Map<String, Integer> items = new LinkedHashMap<>();
		items.put("Berserker ring", 2);
		items.put("Dragon axe", 3);
		tracker.onClogPage("Dagannoth Kings", Arrays.asList(
			"Dagannoth Rex kills: 436", "Dagannoth Prime kills: 10", "Dagannoth Supreme kills: 9"), items);
		tracker.onRollBaseline("Wintertodt", 200, 0);
		ExpectedDropsPanel panel = panel(tracker);
		panel.showBoss("Dagannoth Kings");
		panel.refresh();

		JMenu from = (JMenu) entry(panel, "Move all 3 older ones to");
		assertNotNull(from);
		List<String> bosses = new ArrayList<>();
		for (int i = 0; i < from.getItemCount(); i++)
		{
			bosses.add(from.getItem(i).getText());
		}
		assertEquals(Arrays.asList("Dagannoth Kings", "Wintertodt"), bosses);
		assertNotNull(entry(panel, "Split my 3 older ones between bosses..."));
		from.getItem(0).doClick();
		assertEquals("dragon axe", tracker.splitPoolOf("Dagannoth Kings", "Dragon axe"));

		// now a row of the Kings' own, with the way back on it
		panel.refresh();
		assertNull(entry(panel, "Move all 3 older ones to"));
		JMenuItem back = entry(panel, "Move it back to Shared with other bosses");
		assertNotNull(back);
		back.doClick();
		assertNull(tracker.splitPoolOf("Dagannoth Kings", "Dragon axe"));
	}

	@Test
	public void aVisageCanBeNotFromABoss() throws Exception
	{
		Tracker tracker = tracker();
		tracker.onClogPage("Vorkath", java.util.Collections.singletonList("Vorkath kills: 500"),
			java.util.Collections.singletonMap("Draconic visage", 1));
		ExpectedDropsPanel panel = panel(tracker);
		panel.showBoss("Vorkath");
		panel.refresh();
		JMenu from = (JMenu) entry(panel, "Move my older one to");
		assertNotNull(from);
		// King Black Dragon has no kills here, so only Vorkath and "not from a boss"
		assertEquals(2, from.getItemCount());
		assertEquals("Vorkath", from.getItem(0).getText());
		assertEquals("Not from a boss (other dragons and skeletal wyverns)", from.getItem(1).getText());
		from.getItem(1).doClick();
		assertEquals("draconic visage", tracker.splitPoolOf("Vorkath", "Draconic visage"));
		// split, but the one copy is no boss's: Vorkath has the row with none
		panel.refresh();
		assertNotNull(entry(panel, "Move it back to Shared with other bosses"));
	}
}
