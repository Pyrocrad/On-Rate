package com.expecteddrops.ui;

import static org.junit.Assert.assertTrue;

import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.Test;

public class TileHeightTest
{
	@Test
	public void wrappedWordGetsItsHeight()
	{
		VerdictTile a = new VerdictTile("UNIQUES");
		VerdictTile b = new VerdictTile("GP VALUE");
		a.set("On rate", java.awt.Color.ORANGE, "100 / 102", "about average", null);
		b.set("Extremely spooned", java.awt.Color.GREEN, "4.03b / 1.24b", "1 in 139 as lucky", null);
		JPanel row = new JPanel(new GridLayout(1, 2, 4, 0));
		row.add(a);
		row.add(b);
		for (int i = 0; i < 4; i++)
		{
			row.setSize(225, row.getPreferredSize().height);
			row.doLayout();
			a.doLayout();
			b.doLayout();
			row.doLayout();
		}
		JLabel w = (JLabel) b.getComponent(1);
		System.out.println("TEXT " + w.getText() + " h=" + w.getHeight() + " pref=" + w.getPreferredSize() + " tile=" + b.getSize() + " rowPref=" + row.getPreferredSize());
		assertTrue("word label too short: " + w.getHeight() + " vs " + w.getPreferredSize().height, w.getHeight() >= w.getPreferredSize().height);
		assertTrue(row.getPreferredSize().height >= b.getPreferredSize().height);
	}
}
