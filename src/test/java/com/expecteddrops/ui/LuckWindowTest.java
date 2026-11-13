package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.tracking.HistoryPoint;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.Test;

/** The Luck over time window: which points it draws, and that the graph draws every case without errors. */
public class LuckWindowTest
{
	private static HistoryPoint point(long day, double luck) throws Exception
	{
		Constructor<HistoryPoint> c = HistoryPoint.class.getDeclaredConstructor(long.class, double.class, int.class, double.class);
		c.setAccessible(true);
		return c.newInstance(day, 10.0, 3, luck);
	}

	@Test
	public void windowEndsToday()
	{
		assertEquals(71, LuckWindow.start(100, 30));
		assertEquals(100, LuckWindow.start(100, 1));
	}

	@Test
	public void spanCountsCalendarDays() throws Exception
	{
		assertEquals(0, LuckWindow.span(Collections.emptyList()));
		assertEquals(1, LuckWindow.span(Arrays.asList(point(5, 0.5))));
		assertEquals(31, LuckWindow.span(Arrays.asList(point(5, 0.5), point(35, 0.4))));
	}

	@Test
	public void keepsPointsInsideAndTheLastOneBefore() throws Exception
	{
		HistoryPoint a = point(10, 0.2);
		HistoryPoint b = point(60, 0.3);
		HistoryPoint c = point(75, 0.4);
		HistoryPoint d = point(100, 0.5);
		List<HistoryPoint> out = LuckWindow.select(Arrays.asList(a, b, c, d), 71, 100);
		assertEquals(Arrays.asList(b, c, d), out);
		assertEquals(2, LuckWindow.inside(out, 71));
	}

	@Test
	public void boundaryDaysAreInside() throws Exception
	{
		HistoryPoint first = point(71, 0.2);
		HistoryPoint last = point(100, 0.3);
		List<HistoryPoint> out = LuckWindow.select(Arrays.asList(first, last), 71, 100);
		assertEquals(Arrays.asList(first, last), out);
		assertEquals(2, LuckWindow.inside(out, 71));
	}

	@Test
	public void quietWindowKeepsOnlyTheCarryIn() throws Exception
	{
		HistoryPoint old = point(10, 0.2);
		List<HistoryPoint> out = LuckWindow.select(Arrays.asList(point(5, 0.1), old), 71, 100);
		assertEquals(1, out.size());
		assertSame(old, out.get(0));
		assertEquals(0, LuckWindow.inside(out, 71));
	}

	@Test
	public void oneAndTwoPointsInsideAndNoHistory() throws Exception
	{
		assertEquals(1, LuckWindow.select(Arrays.asList(point(90, 0.5)), 71, 100).size());
		assertEquals(2, LuckWindow.select(Arrays.asList(point(80, 0.5), point(90, 0.6)), 71, 100).size());
		assertTrue(LuckWindow.select(Collections.emptyList(), 71, 100).isEmpty());
		assertTrue(LuckWindow.select(null, 71, 100).isEmpty());
	}

	@Test
	public void pointsAfterTodayAreLeftOut() throws Exception
	{
		List<HistoryPoint> out = LuckWindow.select(Arrays.asList(point(90, 0.5), point(120, 0.6)), 71, 100);
		assertEquals(1, out.size());
	}

	@Test
	public void graphDrawsZeroOneAndTwoPointsInAWindow() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				LuckGraph graph = new LuckGraph();
				graph.setSize(200, 110);
				List<List<HistoryPoint>> cases = new ArrayList<>();
				cases.add(Collections.emptyList());
				cases.add(Arrays.asList(point(10, 0.2)));
				cases.add(Arrays.asList(point(90, 0.4)));
				cases.add(Arrays.asList(point(10, 0.2), point(80, 0.7)));
				cases.add(Arrays.asList(point(75, 0.2), point(90, 0.7)));
				for (List<HistoryPoint> pts : cases)
				{
					graph.setWindow(pts, 71, 100);
					graph.paint(new BufferedImage(200, 110, BufferedImage.TYPE_INT_ARGB).createGraphics());
				}
				graph.setPoints(Arrays.asList(point(5, 0.3)));
				graph.paint(new BufferedImage(200, 110, BufferedImage.TYPE_INT_ARGB).createGraphics());
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
	}
}
