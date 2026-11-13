package com.expecteddrops.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.io.Reader;
import java.lang.reflect.Field;
import javax.swing.JComponent;
import org.junit.Test;

/** The View buttons show only on pages that draw rows the item view changes. */
public class ViewButtonVisibilityTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;

	private static Tracker tracker() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		tracker.onClogPage("Zulrah", java.util.Arrays.asList("Zulrah kills: 300"), java.util.Collections.singletonMap("Tanzanite fang", 0));
		return tracker;
	}

	private static ExpectedDropsPanel panel() throws Exception
	{
		return new ExpectedDropsPanel(tracker(), null, () -> RAIDS, s -> -1, () -> { }, () -> false, () -> { }, name -> 0L,
			() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
	}

	private static boolean visible(ExpectedDropsPanel panel, String field) throws Exception
	{
		Field f = ExpectedDropsPanel.class.getDeclaredField(field);
		f.setAccessible(true);
		return ((JComponent) f.get(panel)).isVisible();
	}

	@Test
	public void theTimeAndSingleDropsPagesHaveNoViewButton() throws Exception
	{
		for (int page : new int[]{ExpectedDropsPanel.TOP_TIME, ExpectedDropsPanel.TOP_DROPS})
		{
			ExpectedDropsPanel panel = panel();
			panel.showBoss(ExpectedDropsPanel.ACCOUNT);
			panel.setAccountPageStore("1,0," + page, s -> { });
			assertFalse("page " + page, visible(panel, "sortRow"));
		}
	}

	@Test
	public void theBossesTabKeepsItsSortRow() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		panel.showBoss(ExpectedDropsPanel.ACCOUNT);
		panel.startOnBossesTab();
		assertTrue(visible(panel, "sortRow"));
	}

	@Test
	public void aBossPageWithItemRowsKeepsItsItemsLine() throws Exception
	{
		ExpectedDropsPanel panel = panel();
		panel.showBoss("Zulrah");
		assertTrue(visible(panel, "itemsHeading"));
	}
}
