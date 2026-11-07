package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.expecteddrops.ExpectedDropsPlugin;
import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.tracking.Tracker;
import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import org.junit.Test;

/** The Items tab's Time page: the average time to get each item you don't have. */
public class TimePageTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;

	private static Tracker tracker() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = new InputStreamReader(ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		// one synced page, so the panel shows the overview and not its welcome guide
		tracker.onClogPage("Zulrah", java.util.Arrays.asList("Zulrah kills: 300"), java.util.Collections.singletonMap("Tanzanite fang", 0));
		return tracker;
	}

	private static ExpectedDropsPanel panel(Tracker tracker)
	{
		return new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { }, () -> false, () -> { }, name -> 0L,
			() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
	}

	@SuppressWarnings("unchecked")
	private static String firstSort(ExpectedDropsPanel panel) throws Exception
	{
		Field f = ExpectedDropsPanel.class.getDeclaredField("sortSelect");
		f.setAccessible(true);
		return ((Enum<?>) ((JComboBox<Object>) f.get(panel)).getSelectedItem()).name();
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

	/** Opens the account overview's Items tab on the given page, as a saved page would. */
	private static List<String> open(ExpectedDropsPanel panel, int page) throws Exception
	{
		List<String> saved = new ArrayList<>();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.setAccountPageStore("1," + firstSort(panel) + "," + page, saved::add);
		List<String> out = new ArrayList<>();
		texts(panel, out);
		return out;
	}

	@Test
	public void theTimePageListsItemsWithAnEstimateBeforeYouHaveKills() throws Exception
	{
		List<String> texts = open(panel(tracker()), ExpectedDropsPanel.TOP_TIME);
		assertTrue(texts.toString(), texts.contains("FASTEST ITEMS"));
		// the fastest items come first, and each is an estimate (~) before you have timed kills
		boolean estimate = false;
		for (String t : texts)
		{
			estimate |= t.startsWith("~");
		}
		assertTrue(texts.toString(), estimate);
		// more than the five shown: the list says how many and offers all of them
		assertTrue(texts.toString(), texts.stream().anyMatch(t -> t.startsWith("top 5 of ") && t.endsWith("show all")));
	}

	@Test
	public void theTimePageIsOneOfTheFourSegments() throws Exception
	{
		List<String> texts = open(panel(tracker()), ExpectedDropsPanel.TOP_TIME);
		for (String segment : new String[]{"Items", "Pets", "Single drops", "Time"})
		{
			assertTrue(segment, texts.contains(segment));
		}
	}

	@Test
	public void theTimePageIsRememberedAndOlderSavedPagesStillOpen() throws Exception
	{
		ExpectedDropsPanel panel = panel(tracker());
		List<String> saved = new ArrayList<>();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.setAccountPageStore("1," + firstSort(panel) + "," + ExpectedDropsPanel.TOP_TIME, saved::add);
		// picking a page saves it; the Time page's number is accepted when it comes back
		List<String> out = new ArrayList<>();
		texts(panel, out);
		assertTrue(out.contains("FASTEST ITEMS"));
		ExpectedDropsPanel again = panel(tracker());
		List<String> old = open(again, ExpectedDropsPanel.TOP_DROPS);
		assertFalse(old.contains("FASTEST ITEMS"));
		assertEquals(4, ExpectedDropsPanel.TOP_TIME);
	}

	@Test
	public void itemsYouHaveAreLeftOut() throws Exception
	{
		Tracker tracker = tracker();
		// a Zulrah page with every unique in the log: nothing of Zulrah's is left to wait for
		tracker.onClogPage("Zulrah", java.util.Arrays.asList("Zulrah kills: 300"),
			java.util.Map.of("Tanzanite fang", 1, "Magic fang", 1, "Serpentine visage", 1, "Uncut onyx", 1,
				"Tanzanite mutagen", 1, "Magma mutagen", 1, "Jar of swamp", 1, "Pet snakeling", 1));
		List<com.expecteddrops.stats.ItemTime> all = com.expecteddrops.stats.ItemTimes.compute(tracker,
			com.expecteddrops.data.KillTimes.bundled(), RAIDS);
		boolean any = false;
		for (com.expecteddrops.stats.ItemTime t : all)
		{
			if (t.getBoss().equals("Zulrah") && t.getLabel().equals("Tanzanite fang"))
			{
				any = true;
				assertTrue("a fang you have is marked as had", t.isHave());
			}
		}
		assertTrue(any);
	}
}
