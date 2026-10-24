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
 * Line chart of the account's luck over time: dry at the bottom, spooned at the top, with the
 * "on rate" band around the middle shaded.
 */
class LuckGraph extends JComponent
{
	private static final int HEIGHT = 110;
	private static final int PAD_LEFT = 44;
	private static final int PAD_RIGHT = 6;
	private static final int PAD_TOP = 6;
	private static final int PAD_BOTTOM = 16;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	/** The history line: light grey, so it reads on all three bands. */
	private static final Color LINE = new Color(225, 225, 225);

	private List<HistoryPoint> points = new ArrayList<>();
	/** The point the mouse is nearest to, or -1. */
	private int hover = -1;

	LuckGraph()
	{
		setPreferredSize(new Dimension(100, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(LEFT_ALIGNMENT);
		ToolTipManager.sharedInstance().registerComponent(this);
		addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setHover(nearest(e.getX()));
			}
		});
		addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				setHover(-1);
			}
		});
	}

	/** A plain component has no accessible context; this one says what the picture is. */
	@Override
	public javax.accessibility.AccessibleContext getAccessibleContext()
	{
		if (accessibleContext == null)
		{
			accessibleContext = new AccessibleJComponent()
			{
			};
			accessibleContext.setAccessibleName("Your account's luck over time");
		}
		return accessibleContext;
	}

	private void setHover(int index)
	{
		if (index != hover)
		{
			hover = index;
			repaint();
		}
	}

	/** Index of the point whose x is closest to {@code x}, or -1 without points. */
	private int nearest(int x)
	{
		int best = -1;
		for (int i = 0; i < points.size(); i++)
		{
			if (best < 0 || Math.abs(xFor(points.get(i).getDay()) - x) < Math.abs(xFor(points.get(best).getDay()) - x))
			{
				best = i;
			}
		}
		return best;
	}

	void setPoints(List<HistoryPoint> points)
	{
		this.points = points == null ? new ArrayList<>() : points;
		hover = -1;
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

		// bands: dry below 35%, the middle (a bit dry, on rate, a bit spooned), spooned above;
		// the same lines as the verdict words (DropMath#verdict)
		int w = plotWidth();
		double dry = DropMath.DRY_BELOW;
		double spooned = DropMath.SPOONED_ABOVE;
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.9), 40));
		g.fillRect(PAD_LEFT, yFor(1), w, yFor(spooned) - yFor(1));
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.5), 40));
		g.fillRect(PAD_LEFT, yFor(spooned), w, yFor(dry) - yFor(spooned));
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.1), 40));
		g.fillRect(PAD_LEFT, yFor(dry), w, yFor(0) - yFor(dry));

		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		// words, not percentages: which end is the lucky one is the first thing to read
		g.drawString("Spooned", 2, yFor(1) + 10);
		g.drawString("On rate", 2, yFor(0.5) + 4);
		g.drawString("Dry", 2, yFor(0) - 1);
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
		g.drawLine(PAD_LEFT, yFor(0.5), PAD_LEFT + w, yFor(0.5));

		if (points.isEmpty())
		{
			g.dispose();
			return;
		}

		// dates under the plot: the first, the last and, when there is room, the middle one
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		long firstDay = points.get(0).getDay();
		long lastDay = points.get(points.size() - 1).getDay();
		String first = LocalDate.ofEpochDay(firstDay).format(DATE);
		String last = LocalDate.ofEpochDay(lastDay).format(DATE);
		int baseline = getHeight() - 3;
		g.drawString(first, PAD_LEFT, baseline);
		int lastWidth = g.getFontMetrics().stringWidth(last);
		g.drawString(last, PAD_LEFT + w - lastWidth, baseline);
		if (lastDay - firstDay >= 4)
		{
			String middle = LocalDate.ofEpochDay((firstDay + lastDay) / 2).format(DATE);
			int middleWidth = g.getFontMetrics().stringWidth(middle);
			int mx = PAD_LEFT + (w - middleWidth) / 2;
			if (mx > PAD_LEFT + g.getFontMetrics().stringWidth(first) + 6 && mx + middleWidth < PAD_LEFT + w - lastWidth - 6)
			{
				g.drawString(middle, mx, baseline);
			}
		}

		// the hovered day: a faint guide line, so the tooltip is clearly about this point
		if (hover >= 0 && hover < points.size())
		{
			int hx = xFor(points.get(hover).getDay());
			g.setColor(new Color(255, 255, 255, 70));
			g.setStroke(new BasicStroke(1f));
			g.drawLine(hx, PAD_TOP, hx, PAD_TOP + plotHeight());
		}

		HistoryPoint latest = points.get(points.size() - 1);
		// a neutral line: the coloured bands carry the verdict, so the history isn't painted in today's colour
		g.setColor(LINE);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 1; i < points.size(); i++)
		{
			HistoryPoint a = points.get(i - 1);
			HistoryPoint b = points.get(i);
			// luck only moves on days you play: across days without a point it stays flat, then
			// changes on the next day you played (a slope over the gap would suggest it drifted)
			long dayBefore = b.getDay() - 1;
			if (dayBefore > a.getDay())
			{
				g.drawLine(xFor(a.getDay()), yFor(a.getLuck()), xFor(dayBefore), yFor(a.getLuck()));
				g.drawLine(xFor(dayBefore), yFor(a.getLuck()), xFor(b.getDay()), yFor(b.getLuck()));
			}
			else
			{
				g.drawLine(xFor(a.getDay()), yFor(a.getLuck()), xFor(b.getDay()), yFor(b.getLuck()));
			}
		}
		if (hover >= 0 && hover < points.size())
		{
			HistoryPoint p = points.get(hover);
			int hx = xFor(p.getDay());
			int hy = yFor(p.getLuck());
			g.setColor(Color.WHITE);
			g.fillOval(hx - 4, hy - 4, 9, 9);
			g.setColor(ExpectedDropsPanel.luckColor(p.getLuck()).brighter());
			g.setStroke(new BasicStroke(2f));
			g.drawOval(hx - 4, hy - 4, 9, 9);
		}
		// the latest point, with its verdict beside it
		int x = xFor(latest.getDay());
		int y = yFor(latest.getLuck());
		Color latestColor = ExpectedDropsPanel.luckColor(latest.getLuck()).brighter();
		g.setColor(LINE);
		g.fillOval(x - 3, y - 3, 7, 7);
		String verdict = DropMath.verdict(latest.getLuck());
		if (!verdict.isEmpty())
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			int tw = g.getFontMetrics().stringWidth(verdict);
			int tx = Math.max(PAD_LEFT, Math.min(x - tw / 2, PAD_LEFT + w - tw));
			// above the dot, or below it when the dot is near the top
			int ty = y - 7 > PAD_TOP + 8 ? y - 7 : y + 15;
			g.setColor(latestColor);
			g.drawString(verdict, tx, ty);
		}
		g.dispose();
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		if (points.isEmpty())
		{
			return null;
		}
		HistoryPoint nearest = points.get(nearest(event.getX()));
		return String.format("<html><b>%s</b><br>%s: %s<br>%d received, %.1f expected</html>",
			LocalDate.ofEpochDay(nearest.getDay()).format(LONG_DATE), DropMath.verdict(nearest.getLuck()),
			Texts.html(Texts.luckPercent(nearest.getLuck())), nearest.getReceived(), nearest.getExpected());
	}

	private static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}
