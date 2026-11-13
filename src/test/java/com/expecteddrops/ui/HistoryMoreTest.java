package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import java.awt.Component;
import java.awt.Container;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** History's 10 / 50 / all limit and its "show more" link. The pixel parity test turns it off. */
public class HistoryMoreTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final String COX = "Chambers of Xeric";
	private long now = System.currentTimeMillis() - 1000L * 3_600_000;
	private int kc;

	private Tracker raids(int count) throws Exception
	{
		Tracker tracker;
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.035);
		}
		for (int i = 0; i < count; i++)
		{
			RaidRecord r = RaidRecord.cox(43_380, 43_380, 1);
			now += 3_600_000;
			tracker.onRaidRecord(r, now);
			tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + (++kc) + "</col>.", now + 1000);
		}
		return tracker;
	}

	private static ExpectedDropsPanel panel(Tracker tracker) throws Exception
	{
		RaidSettings raids = raid -> 0.035;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { }, () -> false, () -> { },
			s -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
		panel.setSize(225, 20000);
		set(panel, "historyOpen", true);
		return panel;
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static List<String> labels(Component c)
	{
		List<String> out = new ArrayList<>();
		labels(c, out);
		return out;
	}

	private static void labels(Component c, List<String> out)
	{
		if (c instanceof javax.swing.JLabel && ((javax.swing.JLabel) c).getText() != null)
		{
			out.add(((javax.swing.JLabel) c).getText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				labels(child, out);
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

	/** The History rows: the kill count labels, "1", "2" ... as plain numbers, below the HISTORY heading. */
	private static int rows(List<String> labels)
	{
		int n = 0;
		boolean inHistory = false;
		for (String l : labels)
		{
			if (l.endsWith(" HISTORY"))
			{
				inHistory = true;
			}
			if (inHistory && l.matches("\\d{1,3}(,\\d{3})*"))
			{
				n++;
			}
		}
		return n;
	}

	@Test
	public void tenRowsAndNoLinkWhenThereAreNoMore() throws Exception
	{
		ExpectedDropsPanel panel = panel(raids(10));
		panel.showBoss(COX);
		panel.refresh();
		assertFalse(any(labels(panel), "Show all"));
		assertFalse(any(labels(panel), "Show 50"));
	}

	@Test
	public void theLinkGoesTenFiftyAllThenBackToTen() throws Exception
	{
		ExpectedDropsPanel panel = panel(raids(60));
		panel.showBoss(COX);
		panel.refresh();
		assertEquals(10, rows(labels(panel)));
		assertTrue(any(labels(panel), "Show 50"));

		set(panel, "historyLimit", 50);
		panel.refresh();
		assertEquals(50, rows(labels(panel)));
		assertTrue(any(labels(panel), "Show all 60"));

		set(panel, "historyLimit", 0);
		panel.refresh();
		assertEquals(60, rows(labels(panel)));
		assertTrue(any(labels(panel), "Show fewer"));
	}

	@Test
	public void theLimitGoesBackToTenOnAnotherBoss() throws Exception
	{
		ExpectedDropsPanel panel = panel(raids(60));
		panel.showBoss(COX);
		set(panel, "historyLimit", 0);
		panel.refresh();
		assertEquals(60, rows(labels(panel)));
		panel.showBoss("Zulrah");
		panel.refresh();
		panel.showBoss(COX);
		panel.refresh();
		assertEquals(10, rows(labels(panel)));
	}

	@Test
	public void theSwitchKeepsTenRowsAndNoLink() throws Exception
	{
		ExpectedDropsPanel panel = panel(raids(60));
		set(panel, "historyMore", false);
		panel.showBoss(COX);
		panel.refresh();
		assertEquals(10, rows(labels(panel)));
		assertFalse(any(labels(panel), "Show 50"));
		assertFalse(any(labels(panel), "Show all"));
	}
}
