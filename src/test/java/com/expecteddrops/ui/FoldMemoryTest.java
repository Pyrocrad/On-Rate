package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
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
import java.util.List;
import org.junit.Test;

/** The folds on a raid page: History, Personal bests and Points over time are remembered; the others start folded. */
public class FoldMemoryTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final String COX = "Chambers of Xeric";

	private static ExpectedDropsPanel panel() throws Exception
	{
		RaidSettings raids = raid -> 0.035;
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			Tracker tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.035);
			ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { },
				s -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
			panel.setSize(225, 6000);
			return panel;
		}
	}

	private static boolean field(Object target, String name) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.getBoolean(target);
	}

	private static List<String> labels(Component c)
	{
		List<String> out = new ArrayList<>();
		collect(c, out);
		return out;
	}

	private static void collect(Component c, List<String> out)
	{
		if (c instanceof javax.swing.JLabel && ((javax.swing.JLabel) c).getText() != null)
		{
			out.add(((javax.swing.JLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				collect(child, out);
			}
		}
	}

	private static boolean any(List<String> labels, String part)
	{
		for (String l : labels)
		{
			if (l.contains(part))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void nothingSavedMeansEverythingFolded() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		panel.setFoldStateStore("", state -> { });
		assertFalse(field(panel, "historyOpen"));
		assertFalse(field(panel, "bestsOpen"));
		assertFalse(field(panel, "pointsOpen"));
	}

	@Test
	public void savedIdsOpenTheirSectionsOnly() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		panel.setFoldStateStore("history,points", state -> { });
		assertTrue(field(panel, "historyOpen"));
		assertFalse(field(panel, "bestsOpen"));
		assertTrue(field(panel, "pointsOpen"));
		// the setups and log folds are never remembered
		assertFalse(field(panel, "setupsOpen"));
		assertFalse(field(panel, "logOpen"));
	}

	@Test
	public void unknownAndBrokenValuesAreIgnored() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		panel.setFoldStateStore(" bests ,setups,,log,nonsense", state -> { });
		assertFalse(field(panel, "historyOpen"));
		assertTrue(field(panel, "bestsOpen"));
		assertFalse(field(panel, "pointsOpen"));
		assertFalse(field(panel, "setupsOpen"));
		assertFalse(field(panel, "logOpen"));
		panel.setFoldStateStore(null, null);
		assertFalse(field(panel, "bestsOpen"));
	}

	@Test
	public void restoringDoesNotWriteBack() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		List<String> saved = new ArrayList<>();
		panel.setFoldStateStore("history", saved::add);
		assertEquals(0, saved.size());
	}

	@Test
	public void aRememberedHistoryIsOpenWhenThePageIsShown() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		panel.setFoldStateStore("history", state -> { });
		panel.showBoss(COX);
		panel.refresh();
		assertTrue(any(labels(panel), "\u25be HISTORY"));
	}
}
