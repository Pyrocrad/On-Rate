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

	@Test
	public void theQuestionMarkOpensAndClosesTheHelp() throws Exception
	{
		boolean[] r = new boolean[5];
		String[] tip = new String[1];
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = panel();
			JLabel help = find(p, "?");
			r[0] = help != null && help.isFocusable();
			if (help == null)
			{
				return;
			}
			tip[0] = help.getToolTipText();
			r[1] = p.isHelpOpen();
			press(help, KeyEvent.VK_ENTER);
			r[2] = p.isHelpOpen();
			press(help, KeyEvent.VK_SPACE);
			r[3] = p.isHelpOpen();
			// Escape closes it
			press(help, KeyEvent.VK_ENTER);
			Object esc = p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
			p.getActionMap().get(esc).actionPerformed(new ActionEvent(p, 0, "esc"));
			r[4] = p.isHelpOpen();
		});
		assertTrue("the ? can be tabbed to", r[0]);
		assertFalse("closed to begin with", r[1]);
		assertTrue("Enter opens it", r[2]);
		assertFalse("Space closes it", r[3]);
		assertFalse("Escape closes it", r[4]);
		assertNotNull("hovering still shows it", tip[0]);
	}

	@Test
	public void goingToAnotherPageClosesTheHelp() throws Exception
	{
		boolean[] r = new boolean[2];
		SwingUtilities.invokeAndWait(() ->
		{
			ExpectedDropsPanel p = panel();
			JLabel help = find(p, "?");
			press(help, KeyEvent.VK_ENTER);
			r[0] = p.isHelpOpen();
			p.showBoss("Zulrah");
			r[1] = p.isHelpOpen();
		});
		assertTrue(r[0]);
		assertFalse(r[1]);
	}

}
