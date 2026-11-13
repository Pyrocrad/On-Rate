package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** A search that matches nothing keeps the page on screen instead of wiping it. */
public class SearchNoMatchTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static ExpectedDropsPanel panel()
	{
		return new ExpectedDropsPanel(new Tracker(Bundled.catalog(new Gson())), null, () -> RAIDS, s -> -1, () -> { }, name -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
	}

	@Test
	public void noMatchKeepsThePageAndClearingBringsTheListBack() throws Exception
	{
		Object[] seen = new Object[6];
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = panel();
			p.onActivate();
			p.showBoss("Vorkath");
			p.search("zzzzqq");
			seen[0] = p.isNoMatch();
			seen[1] = p.bossCount();
			// a redraw while nothing matches (a kill, a setting) must not lose the page
			p.refresh();
			seen[2] = p.currentPage();
			p.search("");
			seen[3] = p.isNoMatch();
			seen[4] = p.currentPage();
			seen[5] = p.bossCount() > 1;
		});
		assertEquals(Boolean.TRUE, seen[0]);
		assertEquals(0, seen[1]);
		assertEquals("Vorkath", seen[2]);
		assertEquals(Boolean.FALSE, seen[3]);
		assertEquals("Vorkath", seen[4]);
		assertEquals(Boolean.TRUE, seen[5]);
	}

	@Test
	public void aMatchIsNotANoMatch() throws Exception
	{
		boolean[] noMatch = new boolean[1];
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = panel();
			p.onActivate();
			p.search("vork");
			noMatch[0] = p.isNoMatch();
		});
		assertFalse(noMatch[0]);
	}

	@Test
	public void searchFieldHasAccessibleNameAndEscapeClears() throws Exception
	{
		boolean[] ok = new boolean[1];
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = panel();
			p.search("zzzzqq");
			ok[0] = p.isNoMatch();
			p.pressEscapeInSearch();
			ok[0] = ok[0] && !p.isNoMatch() && "Search bosses".equals(p.searchAccessibleName());
		});
		assertTrue(ok[0]);
	}
}
