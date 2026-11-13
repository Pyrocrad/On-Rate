package com.expecteddrops.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The "Loot Tracker is off" note in the panel (audit C3). */
public class LootTrackerNoteTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static ExpectedDropsPanel panel()
	{
		return new ExpectedDropsPanel(new Tracker(Bundled.catalog(new Gson())), null, () -> RAIDS, s -> -1, () -> { }, name -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
	}

	@Test
	public void hiddenUntilTheLootTrackerIsKnownToBeOff() throws Exception
	{
		boolean[] seen = new boolean[5];
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = panel();
			p.onActivate();
			// startup, or the state can't be told: no note
			seen[0] = p.isLootTrackerNoteShown();
			p.setLootTrackerOff(true);
			seen[1] = p.isLootTrackerNoteShown();
			// a redraw keeps it
			p.refresh();
			seen[2] = p.isLootTrackerNoteShown();
			// turned back on: gone, and a redraw doesn't bring it back
			p.setLootTrackerOff(false);
			seen[3] = p.isLootTrackerNoteShown();
			p.refresh();
			seen[4] = p.isLootTrackerNoteShown();
		});
		assertFalse("no note before anything is known", seen[0]);
		assertTrue(seen[1]);
		assertTrue(seen[2]);
		assertFalse(seen[3]);
		assertFalse(seen[4]);
	}

	@Test
	public void wordsOfTheNote()
	{
		assertTrue(Texts.LOOT_TRACKER_OFF.equals("Loot Tracker is off, so new drops at most bosses won't be recorded."));
		// the bosses whose new items come from the collection log message instead are named
		for (String boss : new String[]{"Gauntlet", "Obor", "Bryophyta", "Jad", "Zuk"})
		{
			assertTrue(boss, Texts.LOOT_TRACKER_OFF_TIP.contains(boss));
		}
	}
}
