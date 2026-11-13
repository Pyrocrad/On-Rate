package com.expecteddrops.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.tracking.HistoryPoint;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class LuckGraphLayoutTest
{
	private static FontMetrics metrics()
	{
		return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
	}

	private static HistoryPoint point(long day, double luck) throws Exception
	{
		Constructor<HistoryPoint> c = HistoryPoint.class.getDeclaredConstructor(long.class, double.class, int.class, double.class);
		c.setAccessible(true);
		return c.newInstance(day, 1.0, 1, luck);
	}

	@Test
	public void leftMarginFitsTheWidestAxisWord()
	{
		FontMetrics fm = metrics();
		int widest = Math.max(fm.stringWidth("Spooned"), Math.max(fm.stringWidth("On rate"), fm.stringWidth("Dry")));
		int pad = LuckGraph.leftPad(fm);
		assertTrue(pad >= widest + 6);
		assertTrue(pad >= 44);
	}

	@Test
	public void clearSpotAboveIsKept()
	{
		int[] spot = LuckGraph.placeEndLabel(Collections.<Line2D>emptyList(), 200, 60, 50, 10, 3, 50, 200, 0, 100);
		assertEquals(53, spot[1]);
		assertEquals(0, spot[2]);
		assertTrue(spot[0] >= 50 && spot[0] + 50 <= 200);
	}

	@Test
	public void labelMovesBelowWhenTheLineCrossesAbove()
	{
		// a falling line passes through the box above the dot
		List<Line2D> line = new ArrayList<>();
		line.add(new Line2D.Float(100, 40, 200, 55));
		int[] spot = LuckGraph.placeEndLabel(line, 200, 60, 50, 10, 3, 50, 200, 0, 100);
		assertEquals(75, spot[1]);
		assertEquals(0, spot[2]);
	}

	@Test
	public void labelStaysAboveWhenTheLineCrossesBelow()
	{
		List<Line2D> line = new ArrayList<>();
		line.add(new Line2D.Float(100, 90, 200, 70));
		int[] spot = LuckGraph.placeEndLabel(line, 200, 60, 50, 10, 3, 50, 200, 0, 100);
		assertEquals(53, spot[1]);
		assertEquals(0, spot[2]);
	}

	@Test
	public void backingIsAskedForWhenBothSpotsAreCrossed()
	{
		List<Line2D> line = new ArrayList<>();
		line.add(new Line2D.Float(150, 0, 150, 100));
		int[] spot = LuckGraph.placeEndLabel(line, 200, 60, 50, 10, 3, 50, 200, 0, 100);
		assertEquals(1, spot[2]);
	}

	@Test
	public void spotOutsideThePlotIsNotUsed()
	{
		// near the top: above would leave the plot, so below is used
		int[] spot = LuckGraph.placeEndLabel(Collections.<Line2D>emptyList(), 200, 8, 50, 10, 3, 50, 200, 0, 100);
		assertEquals(23, spot[1]);
		assertEquals(0, spot[2]);
	}

	@Test
	public void segmentsHoldFlatRunAcrossGapDays() throws Exception
	{
		LuckGraph graph = new LuckGraph();
		graph.setSize(200, 110);
		List<HistoryPoint> pts = new ArrayList<>();
		pts.add(point(100, 0.5));
		pts.add(point(110, 0.9));
		graph.setPoints(pts);
		List<Line2D> segs = graph.lineSegments();
		assertEquals(2, segs.size());
		assertEquals(segs.get(0).getY1(), segs.get(0).getY2(), 0.001);
		assertFalse(segs.get(1).getY1() == segs.get(1).getY2());
	}
}
