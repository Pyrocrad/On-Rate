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

	@Test
	public void aGroupOfRowsHasOneTabStopAndArrowKeysMoveBetweenThem()
	{
		Activatable.Group group = new Activatable.Group();
		JLabel a = Activatable.install(new JLabel("A"), "A", () -> { }, group);
		JLabel b = Activatable.install(new JLabel("B"), "B", () -> { }, group);
		JLabel c = Activatable.install(new JLabel("C"), "C", () -> { }, group);
		assertTrue(a.isFocusable());
		assertEquals(false, b.isFocusable());
		assertEquals(false, c.isFocusable());
		for (JLabel row : new JLabel[]{a, b, c})
		{
			for (int key : new int[]{KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_HOME, KeyEvent.VK_END})
			{
				assertNotNull(row.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0)));
			}
		}
		// moving down makes the next row focusable (it is the one the Tab key lands on from then on)
		Object down = a.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
		a.getActionMap().get(down).actionPerformed(new ActionEvent(a, 0, "down"));
		assertTrue(b.isFocusable());
	}

	@Test
	public void theContextMenuKeysAreBound()
	{
		AtomicInteger shown = new AtomicInteger();
		JLabel label = new JLabel("Row");
		Activatable.onContextMenu(label, shown::incrementAndGet);
		for (KeyStroke key : new KeyStroke[]{
			KeyStroke.getKeyStroke(KeyEvent.VK_F10, java.awt.event.InputEvent.SHIFT_DOWN_MASK),
			KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0)})
		{
			Object name = label.getInputMap(JComponent.WHEN_FOCUSED).get(key);
			assertNotNull(name);
			label.getActionMap().get(name).actionPerformed(new ActionEvent(label, 0, "menu"));
		}
		assertEquals(2, shown.get());
	}

	@Test
	public void plainTurnsATooltipIntoAName()
	{
		assertEquals("Vorkath Uniques: dry", Activatable.plain("<html><b>Vorkath</b><br>Uniques: dry</html>"));
		assertEquals("", Activatable.plain(null));
	}

	@Test
	public void theGraphCanBeFocusedAndHasArrowKeys()
	{
		LuckGraph graph = new LuckGraph();
		assertTrue(graph.isFocusable());
		for (int key : new int[]{KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END})
		{
			assertNotNull(graph.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke(key, 0)));
		}
	}
}
