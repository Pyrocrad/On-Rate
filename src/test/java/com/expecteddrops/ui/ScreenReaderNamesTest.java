package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The names a screen reader gets: the points graph follows its mode, and the tabs say which is selected. */
public class ScreenReaderNamesTest
{
	@Test
	public void pointsGraphNameFollowsTheMode() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			RaidPointsGraph graph = new RaidPointsGraph();
			graph.setPoints(new ArrayList<>(), false);
			String points = graph.getAccessibleContext().getAccessibleName();
			assertEquals("Your points per raid over your last raids, all modes together", points);
			graph.setPoints(new ArrayList<>(), true);
			String chance = graph.getAccessibleContext().getAccessibleName();
			assertEquals("Your purple chance per raid over your last raids, all modes together", chance);
			assertNotEquals(points, chance);
		});
	}

	@Test
	public void tabNameSaysWhenSelected()
	{
		assertEquals("Expected, selected", ExpectedDropsPanel.tabAccessibleName("Expected", true));
		assertEquals("Received", ExpectedDropsPanel.tabAccessibleName("Received", false));
	}
}
