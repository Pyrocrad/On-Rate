package com.expecteddrops.ui;

import com.expecteddrops.stats.DropMath;
import com.expecteddrops.tracking.HistoryPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Line chart of the account's luck percentile over time: 0% (driest) at the bottom,
 * 100% (most spooned) at the top, with the "on rate" band around 50% shaded.
 */
class LuckGraph extends JComponent
{
	private static final int HEIGHT = 110;
	private static final int PAD_LEFT = 26;
	private static final int PAD_RIGHT = 6;
	private static final int PAD_TOP = 6;
	private static final int PAD_BOTTOM = 16;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private List<HistoryPoint> points = new ArrayList<>();

	LuckGraph()
	{
		setPreferredSize(new Dimension(100, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(LEFT_ALIGNMENT);
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	void setPoints(List<HistoryPoint> points)
	{
		this.points = points == null ? new ArrayList<>() : points;
		repaint();
	}

	private int plotWidth()
	{
		return Math.max(1, getWidth() - PAD_LEFT - PAD_RIGHT);
	}

	private int plotHeight()
	{
		return Math.max(1, getHeight() - PAD_TOP - PAD_BOTTOM);
	}

	private int xFor(long day)
	{
		long first = points.get(0).getDay();
		long last = points.get(points.size() - 1).getDay();
		if (last == first)
		{
			return PAD_LEFT + plotWidth() / 2;
		}
		return PAD_LEFT + (int) Math.round((day - first) * (double) plotWidth() / (last - first));
	}

	private int yFor(double luck)
	{
		return PAD_TOP + (int) Math.round((1 - luck) * plotHeight());
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(0, 0, getWidth(), getHeight());

		// bands: dry below 35%, on rate 35-65%, spooned above
		int w = plotWidth();
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.9), 40));
		g.fillRect(PAD_LEFT, yFor(1), w, yFor(0.65) - yFor(1));
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.5), 40));
		g.fillRect(PAD_LEFT, yFor(0.65), w, yFor(0.35) - yFor(0.65));
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.1), 40));
		g.fillRect(PAD_LEFT, yFor(0.35), w, yFor(0) - yFor(0.35));

		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		for (double level : new double[]{1, 0.5, 0})
		{
			int y = yFor(level);
			g.drawString(String.format("%.0f%%", level * 100), 2, y + 4);
		}
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
		g.drawLine(PAD_LEFT, yFor(0.5), PAD_LEFT + w, yFor(0.5));

		if (points.isEmpty())
		{
			g.dispose();
			return;
		}

		// dates under the plot
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		String first = LocalDate.ofEpochDay(points.get(0).getDay()).format(DATE);
		String last = LocalDate.ofEpochDay(points.get(points.size() - 1).getDay()).format(DATE);
		int baseline = getHeight() - 3;
		g.drawString(first, PAD_LEFT, baseline);
		if (points.size() > 1)
		{
			g.drawString(last, PAD_LEFT + w - g.getFontMetrics().stringWidth(last), baseline);
		}

		HistoryPoint latest = points.get(points.size() - 1);
		Color line = ExpectedDropsPanel.luckColor(latest.getLuck()).brighter();
		g.setColor(line);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 1; i < points.size(); i++)
		{
			HistoryPoint a = points.get(i - 1);
			HistoryPoint b = points.get(i);
			g.drawLine(xFor(a.getDay()), yFor(a.getLuck()), xFor(b.getDay()), yFor(b.getLuck()));
		}
		int x = xFor(latest.getDay());
		int y = yFor(latest.getLuck());
		g.fillOval(x - 3, y - 3, 7, 7);
		g.dispose();
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		if (points.isEmpty())
		{
			return null;
		}
		HistoryPoint nearest = points.get(0);
		for (HistoryPoint p : points)
		{
			if (Math.abs(xFor(p.getDay()) - event.getX()) < Math.abs(xFor(nearest.getDay()) - event.getX()))
			{
				nearest = p;
			}
		}
		return String.format("<html><b>%s</b><br>%s: luckier than %.0f%%<br>%d received, %.1f expected</html>",
			LocalDate.ofEpochDay(nearest.getDay()).format(LONG_DATE), DropMath.verdict(nearest.getLuck()),
			nearest.getLuck() * 100, nearest.getReceived(), nearest.getExpected());
	}

	private static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}
