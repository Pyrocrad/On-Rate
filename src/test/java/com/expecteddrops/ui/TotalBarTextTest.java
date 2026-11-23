package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.tracking.Tracker;
import java.awt.Component;
import java.awt.Container;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.Test;

/**
 * Bars with tabs, Expected tab: the first row (Any unique on a boss page, the total in the account overview) says
 * how many you should have by now in words, with no bar and no rounded-down count. Item rows keep the bar.
 * The pixel parity test turns it off (totalBarText), because the original draws the bar.
 */
public class TotalBarTextTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static Tracker hydra() throws Exception
	{
		Tracker tracker;
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			tracker = new Tracker(BossCatalog.load(new Gson(), reader));
			tracker.setSettings(raid -> 0.035);
		}
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Hydra's eye", 1);
		page.put("Hydra's fang", 1);
		page.put("Hydra's heart", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 693"), page);
		return tracker;
	}

	private static ExpectedDropsPanel panel(Tracker tracker)
	{
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raid -> 0.035, s -> -1, () -> { },
			s -> 0L, () -> new ViewOptions(ItemView.DELVE_BARS, false, false, false, false, true));
		panel.setSize(225, 20000);
		return panel;
	}

	private static void set(Object target, String field, Object value) throws Exception
	{
		java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	/** Every bar's centred text, top to bottom. */
	private static List<String> barTexts(Component c) throws Exception
	{
		List<String> out = new ArrayList<>();
		barTexts(c, out);
		return out;
	}

	private static void barTexts(Component c, List<String> out) throws Exception
	{
		if (c instanceof DropBar)
		{
			java.lang.reflect.Field f = DropBar.class.getDeclaredField("text");
			f.setAccessible(true);
			out.add((String) f.get(c));
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				barTexts(child, out);
			}
		}
	}

	private static void onSwing(ThrowingRunnable r) throws Exception
	{
		Exception[] failed = new Exception[1];
		Error[] error = new Error[1];
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				r.run();
			}
			catch (Exception e)
			{
				failed[0] = e;
			}
			catch (Error e)
			{
				error[0] = e;
			}
		});
		if (error[0] != null)
		{
			throw error[0];
		}
		if (failed[0] != null)
		{
			throw failed[0];
		}
	}

	private interface ThrowingRunnable
	{
		void run() throws Exception;
	}

	@Test
	public void theBossTotalSaysWhatItIs() throws Exception
	{
		Tracker tracker = hydra();
		double expected = tracker.stats("Alchemical Hydra", raid -> 0.035).getAnyRow().getExpected();
		onSwing(() ->
		{
			ExpectedDropsPanel panel = panel(tracker);
			panel.showBoss("Alchemical Hydra");
			panel.refresh();
			List<String> texts = barTexts(panel);
			assertEquals(Texts.formatExpected(expected) + " expected by now", texts.get(0));
			// the item rows keep their Delve-style percentage
			assertTrue(texts.get(1), texts.get(1).endsWith("%"));
		});
	}

	@Test
	public void theAccountTotalSaysWhatItIs() throws Exception
	{
		Tracker tracker = hydra();
		onSwing(() ->
		{
			ExpectedDropsPanel panel = panel(tracker);
			set(panel, "accountTab", 0);
			panel.showBoss("Account overview");
			panel.refresh();
			List<String> texts = barTexts(panel);
			assertFalse(texts.isEmpty());
			assertTrue(texts.get(0), texts.get(0).endsWith(" expected by now"));
		});
	}

	@Test
	public void theSwitchDrawsTheOldBar() throws Exception
	{
		Tracker tracker = hydra();
		onSwing(() ->
		{
			ExpectedDropsPanel panel = panel(tracker);
			set(panel, "totalBarText", false);
			panel.showBoss("Alchemical Hydra");
			panel.refresh();
			for (String text : barTexts(panel))
			{
				assertFalse(text, text.contains("expected by now"));
			}
		});
	}
}
