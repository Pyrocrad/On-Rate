package com.expecteddrops.ui;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.border.Border;

/**
 * Makes a label that works with the mouse work with the keyboard too: it can be tabbed to, shows
 * a focus ring, is activated with Enter or Space and has a name for screen readers.
 */
final class Activatable
{
	private Activatable()
	{
	}

	/**
	 * Call after the component's own border is set: the focus ring is added around it without
	 * changing its size.
	 */
	static <T extends JComponent> T install(T c, String accessibleName, Runnable action)
	{
		c.setFocusable(true);
		if (c.getAccessibleContext() != null)
		{
			c.getAccessibleContext().setAccessibleName(accessibleName);
		}
		c.setBorder(BorderFactory.createCompoundBorder(new FocusRing(), c.getBorder()));
		c.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				c.repaint();
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				c.repaint();
			}
		});
		AbstractAction activate = new AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				action.run();
			}
		};
		c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
		c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "activate");
		c.getActionMap().put("activate", activate);
		return c;
	}

	/** An outline inside the component's bounds while it has the keyboard focus; takes no space. */
	private static final class FocusRing implements Border
	{
		@Override
		public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height)
		{
			if (!c.hasFocus())
			{
				return;
			}
			Graphics2D g = (Graphics2D) graphics.create();
			g.setColor(Theme.ACCENT);
			g.setStroke(new BasicStroke(1f));
			g.drawRect(x, y, width - 1, height - 1);
			g.dispose();
		}

		@Override
		public Insets getBorderInsets(Component c)
		{
			return new Insets(0, 0, 0, 0);
		}

		@Override
		public boolean isBorderOpaque()
		{
			return false;
		}
	}
}
