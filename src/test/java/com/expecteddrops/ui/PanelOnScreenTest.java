package com.expecteddrops.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.awt.event.HierarchyEvent;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The panel's on-screen flag, which the plugin reads to decide on background work (audit F1). */
public class PanelOnScreenTest
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
		return new ExpectedDropsPanel(new Tracker(Bundled.catalog(new Gson())), null, () -> RAIDS, s -> -1, () -> { },
			() -> false, () -> { }, name -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
	}

	private static void onSwing(Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	@Test
	public void followsOpeningAndClosingInTheSidebar() throws Exception
	{
		boolean[] seen = new boolean[3];
		onSwing(() ->
		{
			ExpectedDropsPanel p = panel();
			seen[0] = p.isOnScreen();
			p.onActivate();
			seen[1] = p.isOnScreen();
			p.onDeactivate();
			seen[2] = p.isOnScreen();
		});
		assertFalse("not on screen before it is ever opened", seen[0]);
		assertTrue(seen[1]);
		assertFalse(seen[2]);
	}

	@Test
	public void leavingTheScreenWithoutBeingDeactivatedClearsTheFlag() throws Exception
	{
		boolean[] seen = new boolean[2];
		onSwing(() ->
		{
			ExpectedDropsPanel p = panel();
			p.onActivate();
			seen[0] = p.isOnScreen();
			// taken off screen some other way (the sidebar closed, the window hidden): Swing says so
			p.dispatchEvent(new HierarchyEvent(p, HierarchyEvent.HIERARCHY_CHANGED, p, null, HierarchyEvent.SHOWING_CHANGED));
			seen[1] = p.isOnScreen();
		});
		assertTrue(seen[0]);
		assertFalse("the flag follows what Swing reports", seen[1]);
	}

	@Test
	public void readableFromAnotherThread() throws Exception
	{
		ExpectedDropsPanel[] p = new ExpectedDropsPanel[1];
		onSwing(() ->
		{
			p[0] = panel();
			p[0].onActivate();
		});
		// the client thread reads it while the Swing thread owns the panel
		boolean[] seen = new boolean[1];
		Thread t = new Thread(() -> seen[0] = p[0].isOnScreen());
		t.start();
		t.join();
		assertTrue(seen[0]);
	}
}
