package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import com.onrate.service.AccountPrecompute;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.Test;

/** A click that changes the data drops numbers worked out before it; a plain redraw doesn't (audit F2). */
public class PanelSupersedeTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static final class Tasks
	{
		final List<Runnable> list = new ArrayList<>();

		synchronized void add(Runnable r)
		{
			list.add(r);
		}

		void runAll()
		{
			List<Runnable> now;
			synchronized (this)
			{
				now = new ArrayList<>(list);
				list.clear();
			}
			now.forEach(Runnable::run);
		}
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

	private Tracker tracker;
	private Tasks worker;
	private Tasks swing;
	private AccountPrecompute pre;
	private ExpectedDropsPanel panel;

	private void setUpPanel()
	{
		tracker = new Tracker(Bundled.catalog(new Gson()));
		tracker.setSettings(RAIDS);
		tracker.onClogPage("Vorkath", Collections.singletonList("Vorkath kills: 500"), Collections.singletonMap("Vorkath's head", 9));
		worker = new Tasks();
		swing = new Tasks();
		pre = new AccountPrecompute(tracker, worker::add, swing::add, () -> true);
		panel = new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { },
			s -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, true, false, false, false, true));
		panel.setSize(225, 4000);
		panel.setPrecomputed(pre);
		panel.showBoss("Vorkath");
		panel.refresh();
	}

	@Test
	public void leavingAnItemOutDropsNumbersWorkedOutBefore() throws Exception
	{
		setUpPanel();
		List<String> drawn = new ArrayList<>();
		// a kill asked for the account; it is worked out and on its way to the Swing thread
		pre.request(RAIDS, null, () -> drawn.add("background"));
		worker.runAll();
		JMenuItem leaveOut = entry(panel, "Leave out of the calculations");
		assertNotNull(leaveOut);
		leaveOut.doClick();
		boolean anyLeftOut = false;
		for (com.onrate.engine.data.DropDefinition d : tracker.getCatalog().byName("Vorkath").getDrops())
		{
			anyLeftOut |= tracker.isLeftOut("Vorkath", d.getName());
		}
		assertTrue("the click changed the data", anyLeftOut);
		swing.runAll();
		assertTrue("numbers from before the click are never drawn", drawn.isEmpty());
	}

	@Test
	public void aPlainRedrawKeepsThePendingResult() throws Exception
	{
		setUpPanel();
		List<String> drawn = new ArrayList<>();
		pre.request(RAIDS, null, () -> drawn.add("background"));
		worker.runAll();
		// a redraw that changes no data (a tab, a setting the panel keeps): the kill's draw still comes
		panel.refresh();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		swing.runAll();
		assertEquals(Collections.singletonList("background"), drawn);
	}
}
