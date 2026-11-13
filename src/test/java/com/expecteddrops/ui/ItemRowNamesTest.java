package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import javax.swing.JPanel;
import org.junit.Test;

public class ItemRowNamesTest
{
	@Test
	public void nameRowMakesPanelFocusableWithAccessibleNameAndPlainDescription() throws Exception
	{
		Method m = ExpectedDropsPanel.class.getDeclaredMethod("nameRow", JPanel.class, String.class, String.class);
		m.setAccessible(true);
		JPanel panel = new JPanel();
		m.invoke(null, panel, "Twisted bow", "<html>You got <b>1</b> where 0.19<br>was expected.</html>");
		assertTrue(panel.isFocusable());
		assertEquals("Twisted bow", panel.getAccessibleContext().getAccessibleName());
		String description = panel.getAccessibleContext().getAccessibleDescription();
		assertNotNull(description);
		assertEquals("You got 1 where 0.19 was expected.", description.replace(" .", "."));
	}
}
