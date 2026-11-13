package com.expecteddrops.ui;


import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.Border;
import java.util.ArrayList;
import java.util.List;

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
		return install(c, accessibleName, action, null);
	}

	/**
	 * Like {@link #install(JComponent, String, Runnable)}; with a {@code group} the component is one of a
	 * list that has a single Tab stop: Up, Down, Home and End move between its members.
	 */
	static <T extends JComponent> T install(T c, String accessibleName, Runnable action, Group group)
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
				scrollIntoView(c);
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
			public void actionPerformed(ActionEvent e)
			{
				action.run();
			}
		};
		c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate");
		c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "activate");
		c.getActionMap().put("activate", activate);
		if (group != null)
		{
			group.add(c);
		}
		return c;
	}

	/** Scrolls the enclosing scroll pane, if any, so the component is fully visible (keyboard users tabbing down a long page). */
	static void scrollIntoView(JComponent c)
	{
		if (c.getWidth() > 0 && c.getHeight() > 0)
		{
			c.scrollRectToVisible(new Rectangle(0, 0, c.getWidth(), c.getHeight()));
		}
	}

	/** The component's border with the focus ring around it; the ring takes no space and only shows with the focus. */
	static Border withRing(Border inner)
	{
		return BorderFactory.createCompoundBorder(new FocusRing(), inner);
	}

	/**
	 * Opens a context menu from the keyboard: Shift+F10 or the context-menu key, on a component that can
	 * hold the focus.
	 */
	static void onContextMenu(JComponent c, Runnable show)
	{
		AbstractAction menu = new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				show.run();
			}
		};
		c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK), "contextMenu");
		c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0), "contextMenu");
		c.getActionMap().put("contextMenu", menu);
	}

	/** The text of an HTML tooltip without its tags, shortened: a name for a screen reader. */
	static String plain(String html)
	{
		if (html == null)
		{
			return "";
		}
		String text = html.replaceAll("<[^>]*>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", " ").trim();
		return text.length() > 80 ? text.substring(0, 80).trim() : text;
	}

	/**
	 * A list of rows with one Tab stop: only the row last focused (the first at the start) can be tabbed to,
	 * and Up, Down, Home and End move between the rows.
	 */
	static final class Group
	{
		private final List<JComponent> members = new ArrayList<>();
		private JComponent current;

		private void add(JComponent c)
		{
			members.add(c);
			if (current == null)
			{
				current = c;
			}
			else
			{
				c.setFocusable(false);
			}
			c.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusGained(FocusEvent e)
				{
					if (current != c)
					{
						current.setFocusable(false);
						current = c;
						c.setFocusable(true);
					}
				}
			});
			bind(c, KeyEvent.VK_UP, "rowUp", () -> move(c, -1));
			bind(c, KeyEvent.VK_DOWN, "rowDown", () -> move(c, 1));
			bind(c, KeyEvent.VK_HOME, "rowFirst", () -> moveTo(0));
			bind(c, KeyEvent.VK_END, "rowLast", () -> moveTo(members.size() - 1));
		}

		private static void bind(JComponent c, int key, String name, Runnable run)
		{
			c.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
			c.getActionMap().put(name, new AbstractAction()
			{
				@Override
				public void actionPerformed(ActionEvent e)
				{
					run.run();
				}
			});
		}

		private void move(JComponent from, int step)
		{
			moveTo(members.indexOf(from) + step);
		}

		private void moveTo(int index)
		{
			if (index < 0 || index >= members.size())
			{
				return;
			}
			JComponent target = members.get(index);
			target.setFocusable(true);
			target.requestFocusInWindow();
		}
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

