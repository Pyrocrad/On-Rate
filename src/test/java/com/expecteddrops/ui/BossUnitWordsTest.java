package com.expecteddrops.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** A boss page says the boss's own unit (chests, delves...), not always "kills" (audit M2). */
public class BossUnitWordsTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

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

	private static List<String> page(Tracker tracker, String boss) throws Exception
	{
		List<String> out = new ArrayList<>();
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { }, () -> false, () -> { },
				name -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
			p.onActivate();
			p.showBoss(boss);
			texts(p, out);
		});
		return out;
	}

	private static Tracker tracker()
	{
		Tracker t = new Tracker(Bundled.catalog(new Gson()));
		t.setSettings(RAIDS);
		return t;
	}

	@Test
	public void noKillsYetSaysTheBossesOwnUnit() throws Exception
	{
		assertTrue(page(tracker(), "Barrows").toString(), page(tracker(), "Barrows").contains("No chests yet"));
		assertTrue(page(tracker(), "Doom of Mokhaiotl").contains("No delves yet"));
		assertTrue(page(tracker(), "Vorkath").contains("No kills yet"));
	}

	@Test
	public void tooEarlySaysTheBossesOwnUnit() throws Exception
	{
		Tracker t = tracker();
		t.onClogPage("Barrows Chests", Collections.singletonList("Barrows Chests opened: 2"), new java.util.LinkedHashMap<>());
		List<String> texts = page(t, "Barrows");
		String joined = String.join("\n", texts);
		assertTrue(joined, joined.contains("Too few chests"));
		assertFalse(joined, joined.contains("Too few kills"));
	}
}
