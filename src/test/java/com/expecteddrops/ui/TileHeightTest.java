package com.expecteddrops.ui;

import static org.junit.Assert.assertTrue;

import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.Test;

public class TileHeightTest
{
	@Test
	public void wrappedWordGetsItsHeight() throws Exception
	{
		// Swing lays components out on the UI thread, and a tile that wraps its text asks to be laid out
		// again once the current pass is over. So each pass is its own UI-thread task, which is also how the
		// panel gets its passes; run on any other thread the order would be a matter of timing.
		VerdictTile[] tiles = new VerdictTile[2];
		JPanel[] row = new JPanel[1];
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			tiles[0] = new VerdictTile("UNIQUES");
			tiles[1] = new VerdictTile("GP VALUE");
			tiles[0].set("On rate", java.awt.Color.ORANGE, "100 / 102", "about average", null);
			tiles[1].set("Extremely spooned", java.awt.Color.GREEN, "4.03b / 1.24b", "1 in 139 as lucky", null);
			row[0] = new JPanel(new GridLayout(1, 2, 4, 0));
			row[0].add(tiles[0]);
			row[0].add(tiles[1]);
		});
		for (int i = 0; i < 4; i++)
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
				row[0].setSize(225, row[0].getPreferredSize().height);
				row[0].doLayout();
				tiles[0].doLayout();
				tiles[1].doLayout();
				row[0].doLayout();
			});
		}
		int[] heights = new int[4];
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			JLabel w = (JLabel) tiles[1].getComponent(1);
			heights[0] = w.getHeight();
			heights[1] = w.getPreferredSize().height;
			heights[2] = row[0].getPreferredSize().height;
			heights[3] = tiles[1].getPreferredSize().height;
		});
		assertTrue("word label too short: " + heights[0] + " vs " + heights[1], heights[0] >= heights[1]);
		assertTrue(heights[2] >= heights[3]);
	}
}
