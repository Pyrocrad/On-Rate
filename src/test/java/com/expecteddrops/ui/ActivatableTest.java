package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import org.junit.Test;

/** Labels that work with the mouse also work with the keyboard. */
public class ActivatableTest
{
	@Test
	public void enterAndSpaceActivateAndTheLabelHasAName()
	{
		AtomicInteger clicks = new AtomicInteger();
		JLabel label = Activatable.install(new JLabel("Items"), "Items tab", clicks::incrementAndGet);
		assertTrue(label.isFocusable());
		assertEquals("Items tab", label.getAccessibleContext().getAccessibleName());
		for (int key : new int[]{KeyEvent.VK_ENTER, KeyEvent.VK_SPACE})
		{
			Object name = label.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0));
			assertNotNull(name);
			label.getActionMap().get(name).actionPerformed(new ActionEvent(label, 0, "activate"));
		}
		assertEquals(2, clicks.get());
	}

	@Test
	public void theFocusRingTakesNoSpace()
	{
		JLabel plain = new JLabel("Items");
		java.awt.Insets before = plain.getInsets();
		Activatable.install(plain, "Items tab", () -> { });
		assertEquals(before, plain.getInsets());
	}

	@Test
	public void theGraphHasAnAccessibleName()
	{
		assertEquals("Your account's luck over time", new LuckGraph().getAccessibleContext().getAccessibleName());
	}
}
