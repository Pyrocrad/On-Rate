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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The "?" help and About note open on click or Enter/Space, and close again (audit 6.1: hover-only help). */
public class HelpFoldTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;

	private static ExpectedDropsPanel panel()
	{
		ExpectedDropsPanel p = new ExpectedDropsPanel(new Tracker(Bundled.catalog(new Gson())), null, () -> RAIDS, s -> -1,
			() -> { }, name -> 0L,
			() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
		p.onActivate();
		return p;
	}

	private static JLabel find(Component c, String text)
	{
		if (c instanceof JLabel && text.equals(((JLabel) c).getText()))
		{
			return (JLabel) c;
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				JLabel l = find(child, text);
				if (l != null)
				{
					return l;
				}
			}
		}
		return null;
	}

	private static void press(JComponent c, int key)
	{
		Object name = c.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
		assertNotNull(name);
		c.getActionMap().get(name).actionPerformed(new ActionEvent(c, 0, "activate"));
	}

}
