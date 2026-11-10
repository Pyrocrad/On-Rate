package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import java.awt.Component;
import java.awt.Container;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** "Hide guide" hides the welcome guide and leaves the player in the panel (audit H8). */
public class HideGuideTest
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static JButton find(Component c, String text)
	{
		if (c instanceof JButton && text.equals(((JButton) c).getText()))
		{
			return (JButton) c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				JButton b = find(child, text);
				if (b != null)
				{
					return b;
				}
			}
		}
		return null;
	}

	@Test
	public void hidingTheGuideOnlyTurnsItOff() throws Exception
	{
		AtomicBoolean show = new AtomicBoolean(true);
		AtomicInteger dismissed = new AtomicInteger();
		String[] tip = new String[1];
		boolean[] shownAfter = new boolean[1];
		boolean[] found = new boolean[2];
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = new ExpectedDropsPanel(new Tracker(Bundled.catalog(new Gson())), null, () -> RAIDS, s -> -1,
				() -> { }, show::get, () ->
				{
					dismissed.incrementAndGet();
					show.set(false);
				}, name -> 0L, () -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
			p.onActivate();
			found[0] = find(p, "Hide guide and open settings") != null;
			JButton hide = find(p, "Hide guide");
			found[1] = hide != null;
			if (hide == null)
			{
				return;
			}
			tip[0] = hide.getToolTipText();
			hide.doClick();
			p.refresh();
			shownAfter[0] = p.isWelcomeShown();
		});
		assertFalse("the button no longer promises to open the settings", found[0]);
		assertTrue(found[1]);
		assertEquals(1, dismissed.get());
		assertFalse(shownAfter[0]);
		assertNotNull(tip[0]);
		// it says how to get the guide back
		assertTrue(tip[0], tip[0].contains("Show welcome guide"));
		assertFalse(tip[0], tip[0].contains("opens On Rate's settings"));
	}
}
