package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The View button: its name, its size, and that every view draws. */
public class ViewChipTest
{
	private static void onSwing(Runnable r) throws Exception
	{
		SwingUtilities.invokeAndWait(r);
	}

	@Test
	public void showsTheCurrentViewInTheTooltipAndTheAccessibleName() throws Exception
	{
		onSwing(() ->
		{
			ViewChip chip = new ViewChip(v -> { });
			chip.setView(ItemView.RINGS);
			assertEquals("View: Chance rings", chip.getToolTipText());
			assertEquals("View: Chance rings", chip.getAccessibleContext().getAccessibleName());
			chip.setView(ItemView.DELVE_BARS);
			// the settings name has a parenthesis; the button doesn't
			assertEquals("View: Bars with tabs", chip.getToolTipText());
		});
	}

	@Test
	public void compactIsNarrowerAndKeepsTheHeight() throws Exception
	{
		onSwing(() ->
		{
			ViewChip chip = new ViewChip(v -> { });
			chip.setView(ItemView.RINGS);
			Dimension full = chip.getPreferredSize();
			chip.setCompact(true);
			Dimension compact = chip.getPreferredSize();
			assertTrue(compact.width < full.width);
			assertEquals(full.height, compact.height);
			// it has to share a 225 px row with "Sort:" and the sort list
			assertTrue("compact width " + compact.width, compact.width <= 45);
			// and the named button fits next to "ITEMS" on a boss page
			assertTrue("full width " + full.width, full.width <= 150);
		});
	}

	@Test
	public void everyViewDrawsSomething() throws Exception
	{
		onSwing(() ->
		{
			for (ItemView view : ItemView.values())
			{
				ViewChip chip = new ViewChip(v -> { });
				chip.setView(view);
				Dimension size = chip.getPreferredSize();
				chip.setSize(size);
				BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
				chip.paint(image.getGraphics());
				boolean drawn = false;
				for (int y = 0; y < size.height && !drawn; y++)
				{
					for (int x = 0; x < size.width && !drawn; x++)
					{
						drawn = (image.getRGB(x, y) >>> 24) != 0;
					}
				}
				assertTrue(view + " drew nothing", drawn);
			}
		});
	}

	@Test
	public void longestNameStillFits() throws Exception
	{
		onSwing(() ->
		{
			for (ItemView view : ItemView.values())
			{
				ViewChip chip = new ViewChip(v -> { });
				chip.setView(view);
				assertTrue(view + " is " + chip.getPreferredSize().width + " px wide", chip.getPreferredSize().width <= 150);
				assertNotEquals("", view.shortName());
			}
		});
	}
}
