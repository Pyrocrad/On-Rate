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
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * The Time row's hover under History: fastest timed, median, average, latest and the timed count for a normal
 * boss, per-level times for Doom of Mokhaiotl, and a pointer to Personal bests on raids. The Doom row is left
 * out of the pixel parity test, so it is checked here.
 */
public class TimeHoverTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final String DOOM = "Doom of Mokhaiotl";
	private static final String COX = "Chambers of Xeric";

	private static Tracker tracker() throws Exception
	{
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			Tracker tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.035);
			return tracker;
		}
	}

	private static ExpectedDropsPanel panel(Tracker tracker) throws Exception
	{
		RaidSettings raids = raid -> 0.035;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { }, () -> false, () -> { },
			s -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
		panel.setSize(225, 6000);
		return panel;
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	/** The hovers of every component on the boss page, History open. */
	private static List<String> tips(ExpectedDropsPanel panel, String boss) throws Exception
	{
		set(panel, "historyOpen", true);
		panel.showBoss(boss);
		panel.refresh();
		List<String> out = new ArrayList<>();
		tips(panel, out);
		return out;
	}

	private static void tips(Component c, List<String> out)
	{
		if (c instanceof javax.swing.JComponent && ((javax.swing.JComponent) c).getToolTipText() != null)
		{
			out.add(((javax.swing.JComponent) c).getToolTipText());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				tips(child, out);
			}
		}
	}

	private static String find(List<String> tips, String title)
	{
		for (String t : tips)
		{
			if (t.contains(title))
			{
				return t;
			}
		}
		return null;
	}

	private static String clock(int seconds)
	{
		return String.format(java.util.Locale.ROOT, "%d:%02d.00", seconds / 60, seconds % 60);
	}

	private static void zulrah(Tracker tracker, int[] seconds, boolean untimedLast)
	{
		long now = 1_000_000;
		for (int i = 0; i < seconds.length; i++)
		{
			long at = now + i * 600_000L;
			tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>" + (10 + i) + "</col>.", at);
			tracker.onChatMessage("Fight duration: <col=ff0000>" + clock(seconds[i]) + "</col>.", at + 500);
		}
		if (untimedLast)
		{
			tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>" + (10 + seconds.length) + "</col>.", now + 100 * 600_000L);
		}
	}

	@Test
	public void aBossSaysFastestTimedMedianAverageLatestAndHowManyWereTimed() throws Exception
	{
		Tracker tracker = tracker();
		zulrah(tracker, new int[]{70, 50, 60, 100}, true);
		String tip = find(tips(panel(tracker), "Zulrah"), "Kill times in History");
		assertTrue(String.valueOf(tip), tip != null);
		assertTrue(tip, tip.contains("Fastest timed"));
		assertTrue(tip, tip.contains("0:50.0"));
		assertTrue(tip, tip.contains("1:05.0"));
		assertTrue(tip, tip.contains("1:10.0"));
		assertTrue(tip, tip.contains("1:40.0"));
		assertTrue(tip, tip.contains("4 of 5"));
		assertTrue(tip, tip.contains("not a personal best"));
		assertFalse(tip, tip.contains("PB"));
	}

	@Test
	public void oneTimedKillShowsOnlyItsTime() throws Exception
	{
		Tracker tracker = tracker();
		zulrah(tracker, new int[]{61}, false);
		List<String> tips = tips(panel(tracker), "Zulrah");
		String tip = find(tips, "Kill time in History");
		assertTrue(String.valueOf(tips), tip != null);
		assertTrue(tip, tip.contains("1:01.0"));
		assertTrue(tip, tip.contains("1 of 1"));
		assertFalse(tip, tip.contains("Fastest timed"));
		assertFalse(tip, tip.contains("Median"));
	}

	@Test
	public void aTeamBossKeepsTheAverageOnly() throws Exception
	{
		Tracker tracker = tracker();
		for (int i = 0; i < 3; i++)
		{
			long at = 1_000_000 + i * 3_600_000L;
			tracker.onChatMessage("Your Corporeal Beast kill count is: <col=ff0000>" + (i + 1) + "</col>.", at);
			tracker.onChatMessage("Fight duration: <col=ff0000>" + clock(100 + 10 * i) + "</col>.", at + 500);
		}
		List<String> tips = tips(panel(tracker), "Corporeal Beast");
		assertTrue(String.valueOf(tips), find(tips, "Average time") != null);
		assertTrue(String.valueOf(tips), find(tips, "Fastest timed") == null);
	}

	@Test
	public void doomShowsEachLevelsOwnTimes() throws Exception
	{
		Tracker tracker = tracker();
		long now = 1_000_000;
		int[] level3 = {50, 70, 60};
		for (int i = 0; i < level3.length; i++)
		{
			tracker.onChatMessage("Delve level: 3 duration: <col=ff0000>" + clock(level3[i]) + "</col>", now + i * 600_000L);
		}
		tracker.onChatMessage("Delve level: 8+ (12) duration: <col=ff0000>2:05.00</col>", now + 10 * 600_000L);
		tracker.onChatMessage("Delve level: 8+ (10) duration: <col=ff0000>1:55.00</col>", now + 11 * 600_000L);
		String tip = find(tips(panel(tracker), DOOM), "Delve times in History");
		assertTrue(String.valueOf(tip), tip != null);
		// level 3: fastest 0:50.0, median 1:00.0, latest 1:00.0, three delves
		assertTrue(tip, tip.contains("Level 3 · 3"));
		assertTrue(tip, tip.contains("0:50.0 · 1:00.0 · 1:00.0"));
		// the deep delves are one row: fastest 1:55.0, median 2:00.0, latest 1:55.0
		assertTrue(tip, tip.contains("Level 8+ · 2"));
		assertTrue(tip, tip.contains("1:55.0 · 2:00.0 · 1:55.0"));
		// levels without a timed delve have no row
		assertFalse(tip, tip.contains("Level 1 ·"));
		assertFalse(tip, tip.contains("Level 2 ·"));
	}

	@Test
	public void theDoomRowCanBeSwitchedOff() throws Exception
	{
		Tracker tracker = tracker();
		tracker.onChatMessage("Delve level: 3 duration: <col=ff0000>1:00.00</col>", 1_000_000);
		ExpectedDropsPanel panel = panel(tracker);
		set(panel, "delveTimes", false);
		assertTrue(find(tips(panel, DOOM), "Delve times in History") == null);
	}

	@Test
	public void aRaidPointsToPersonalBestsOnlyWithTheRaidSections() throws Exception
	{
		for (boolean extras : new boolean[]{true, false})
		{
			Tracker tracker = tracker();
			java.util.Map<String, Integer> page = new java.util.LinkedHashMap<>();
			page.put("Twisted bow", 0);
			tracker.onClogPage(COX, Collections.singletonList("Chambers of Xeric completions: 0"), page);
			long now = System.currentTimeMillis() - 100L * 3_600_000;
			for (int i = 0; i < 3; i++)
			{
				RaidRecord r = RaidRecord.cox(43_380, 43_380, 1);
				set(r, "durationMillis", (30 - i) * 60_000);
				now += 3_600_000;
				// the Time row reads the game's own time message, not the raid record
				tracker.onChatMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>"
					+ (30 - i) + ":00.00</col>", now - 500);
				tracker.onRaidRecord(r, now);
				tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + (i + 1) + "</col>.", now + 1000);
			}
			ExpectedDropsPanel panel = panel(tracker);
			set(panel, "raidExtras", extras);
			List<String> tips = tips(panel, COX);
			String tip = find(tips, "Average time");
			assertTrue(String.valueOf(tips), tip != null);
			assertEquals(tip, extras, tip.contains("see Personal bests"));
			assertFalse(tip, tip.contains("Fastest timed"));
		}
	}

	@Test
	public void theHoverWordsNeverCallItABest()
	{
		com.onrate.engine.tracking.TimeStats stats = timeStats();
		String tip = ExpectedDropsPanel.timeTip(stats, 12, false);
		assertTrue(tip, tip.contains("Fastest timed"));
		assertFalse(tip, tip.toLowerCase(java.util.Locale.ROOT).contains("record"));
		assertEquals(false, ExpectedDropsPanel.timeTip(stats, -1, false).contains("Fastest timed"));
		assertTrue(ExpectedDropsPanel.timeTip(stats, -1, true).contains("see Personal bests"));
	}

	private static com.onrate.engine.tracking.TimeStats timeStats()
	{
		try
		{
			Tracker tracker = tracker();
			zulrah(tracker, new int[]{50, 60}, false);
			return tracker.killTimes("Zulrah", null);
		}
		catch (Exception e)
		{
			throw new AssertionError(e);
		}
	}
}
