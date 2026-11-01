package com.expecteddrops.ui;

import java.awt.Graphics;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;

/** A text field that shows a grey hint while it is empty. */
/** Text field that shows a grey hint while it's empty. */
final class HintField extends JTextField
{
	private final String hint;

	HintField(String hint)
	{
		this.hint = hint;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		if (getText().isEmpty())
		{
			g.setColor(ColorScheme.MEDIUM_GRAY_COLOR.brighter());
			g.setFont(getFont());
			java.awt.Insets in = getInsets();
			g.drawString(hint, in.left + 2, (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2);
		}
	}
}
