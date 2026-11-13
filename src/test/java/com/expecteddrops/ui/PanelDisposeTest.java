package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import org.junit.Test;

/**
 * What the panel adds outside itself (a mouse listener for the whole window, a longer tooltip delay
 * for all of RuneLite) is taken off again when the plugin stops.
 */
public class PanelDisposeTest
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

	private static int mouseListeners()
	{
		return Toolkit.getDefaultToolkit().getAWTEventListeners(AWTEvent.MOUSE_EVENT_MASK).length;
	}

	@Test
	public void theWindowWideMouseListenerIsRemoved() throws Exception
	{
		int[] seen = new int[4];
		SwingUtilities.invokeAndWait(() ->
		{
			seen[0] = mouseListeners();
			ExpectedDropsPanel p = panel();
			seen[1] = mouseListeners();
			p.dispose();
			seen[2] = mouseListeners();
			// stopping twice (or a panel disposed again) changes nothing
			p.dispose();
			seen[3] = mouseListeners();
		});
		assertEquals("the panel adds one listener", seen[0] + 1, seen[1]);
		assertEquals("and takes it off when the plugin stops", seen[0], seen[2]);
		assertEquals(seen[0], seen[3]);
	}

	@Test
	public void theTooltipDelayIsPutBackWhenStoppedWithTheMouseOverThePanel() throws Exception
	{
		int[] seen = new int[3];
		SwingUtilities.invokeAndWait(() ->
		{
			ToolTipManager tips = ToolTipManager.sharedInstance();
			seen[0] = tips.getDismissDelay();
			ExpectedDropsPanel p = panel();
			// the mouse moves onto the panel: tooltips stay up longer
			p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, 5, 5, 0, false));
			seen[1] = tips.getDismissDelay();
			// the plugin is turned off while the mouse is still there
			p.dispose();
			seen[2] = tips.getDismissDelay();
		});
		assertEquals(ExpectedDropsPanel.TOOLTIP_DISMISS_MS, seen[1]);
		assertEquals("RuneLite's own delay is back", seen[0], seen[2]);
	}
}
