package com.expecteddrops.ui;


import com.onrate.engine.math.Verdict;

import com.onrate.engine.tracking.HistoryPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.*;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Line chart of the account's luck over time: dry at the bottom, spooned at the top, with the
 * "on rate" band around the middle shaded.
 */
class LuckGraph extends JComponent
{
	private static final int HEIGHT = 110;
	/** Smallest left margin; the real one is widened to fit the axis words (see {@link #leftPad}). */
	private static final int PAD_LEFT_MIN = 44;
	private static final String[] AXIS_WORDS = {"Spooned", "On rate", "Dry"};
	private static final int PAD_RIGHT = 6;
	private static final int PAD_TOP = 6;
	private static final int PAD_BOTTOM = 16;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	/** The history line: light grey, so it reads on all three bands. */
	private static final Color LINE = new Color(225, 225, 225);

	private List<HistoryPoint> points = new ArrayList<>();
	/** The days the plot spans: first to last point, or the window set with {@link #setWindow}. */
	private long fromDay;
	private long toDay;
	/** A window is on: the first point may be a carry-in from before it, and the hover says "in total". */
	private boolean windowed;
	/** The point the mouse is nearest to, or -1. */
	private int hover = -1;
	/** Left margin, measured from the axis-word font the first time it is needed. */
	private int padLeft = -1;
	/** True while the marker was put there with the keyboard (it goes when the focus does). */
	private boolean keyboardHover;

	LuckGraph()
	{
		setPreferredSize(new Dimension(100, HEIGHT));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, HEIGHT));
		setAlignmentX(LEFT_ALIGNMENT);
		ToolTipManager.sharedInstance().registerComponent(this);
		installKeyboard();
		addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				setHover(nearest(e.getX()));
			}
		});
		addMouseListener(new MouseAdapter()
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

	/**
	 * Keyboard way to read the graph: Tab to it, then Left and Right (Home, End) move the marker from point to
	 * point; the point's text is the same as the tooltip and is set as the description for screen readers.
	 */
	private void installKeyboard()
	{
		setFocusable(true);
		addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				repaint();
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				if (keyboardHover)
				{
					keyboardHover = false;
					setHover(-1);
				}
				repaint();
			}
		});
		bindKey(KeyEvent.VK_LEFT, "markerLeft", () -> moveMarker(hover < 0 ? points.size() - 1 : hover - 1));
		bindKey(KeyEvent.VK_RIGHT, "markerRight", () -> moveMarker(hover < 0 ? points.size() - 1 : hover + 1));
		bindKey(KeyEvent.VK_HOME, "markerFirst", () -> moveMarker(0));
		bindKey(KeyEvent.VK_END, "markerLast", () -> moveMarker(points.size() - 1));
	}

	private void bindKey(int key, String name, Runnable run)
	{
		getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
		getActionMap().put(name, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				run.run();
			}
		});
	}

	private void moveMarker(int index)
	{
		if (points.isEmpty())
		{
			return;
		}
		int clamped = Math.max(0, Math.min(points.size() - 1, index));
		keyboardHover = true;
		setHover(clamped);
		getAccessibleContext().setAccessibleDescription(textFor(clamped).replace("<br>", ", ").replaceAll("<[^>]*>", ""));
	}

	private void setHover(int index)
	{
		if (index != hover)
		{
			hover = index;
			repaint();
		}
	}

	/** Index of the point whose x is closest to {@code x}, or -1 without points. A carry-in from before the window is skipped. */
	private int nearest(int x)
	{
		int best = -1;
		for (int i = 0; i < points.size(); i++)
		{
			if (points.get(i).getDay() < fromDay)
			{
				continue;
			}
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
		windowed = false;
		if (!this.points.isEmpty())
		{
			fromDay = this.points.get(0).getDay();
			toDay = this.points.get(this.points.size() - 1).getDay();
		}
		hover = -1;
		repaint();
	}

	/**
	 * Draws {@code points} (see {@link LuckWindow#select}) over the days {@code from} to {@code to}. A first point before
	 * {@code from} is drawn at the left edge, and the line runs flat to {@code to}.
	 */
	void setWindow(List<HistoryPoint> points, long from, long to)
	{
		this.points = points == null ? new ArrayList<>() : points;
		fromDay = from;
		toDay = Math.max(from, to);
		windowed = true;
		hover = -1;
		repaint();
	}

	/** The left margin: the widest axis word plus 6 px (2 px in front, 4 px before the plot), never under 44. */
	static int leftPad(FontMetrics fm)
	{
		int widest = 0;
		for (String word : AXIS_WORDS)
		{
			widest = Math.max(widest, fm.stringWidth(word));
		}
		return Math.max(PAD_LEFT_MIN, widest + 6);
	}

	private int padLeft()
	{
		if (padLeft < 0)
		{
			padLeft = leftPad(getFontMetrics(FontManager.getRunescapeSmallFont()));
		}
		return padLeft;
	}

	private int plotWidth()
	{
		return Math.max(1, getWidth() - padLeft() - PAD_RIGHT);
	}

	private int plotHeight()
	{
		return Math.max(1, getHeight() - PAD_TOP - PAD_BOTTOM);
	}

	private int xFor(long day)
	{
		if (toDay == fromDay)
		{
			return padLeft() + plotWidth() / 2;
		}
		long clamped = Math.max(fromDay, Math.min(toDay, day));
		return padLeft() + (int) Math.round((clamped - fromDay) * (double) plotWidth() / (toDay - fromDay));
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
		// the same lines as the verdict words (Verdict#labelOf)
		int w = plotWidth();
		double dry = Verdict.DRY_BELOW;
		double spooned = Verdict.SPOONED_ABOVE;
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.9), 40));
		g.fillRect(padLeft(), yFor(1), w, yFor(spooned) - yFor(1));
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.5), 40));
		g.fillRect(padLeft(), yFor(spooned), w, yFor(dry) - yFor(spooned));
		g.setColor(withAlpha(ExpectedDropsPanel.luckColor(0.1), 40));
		g.fillRect(padLeft(), yFor(dry), w, yFor(0) - yFor(dry));

		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		// words, not percentages: which end is the lucky one is the first thing to read
		g.drawString("Spooned", 2, yFor(1) + 10);
		g.drawString("On rate", 2, yFor(0.5) + 4);
		g.drawString("Dry", 2, yFor(0) - 1);
		g.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
		g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{3, 3}, 0));
		g.drawLine(padLeft(), yFor(0.5), padLeft() + w, yFor(0.5));

		if (points.isEmpty())
		{
			g.dispose();
			return;
		}

		// dates under the plot: the first, the last and, when there is room, the middle one
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		long firstDay = fromDay;
		long lastDay = toDay;
		String first = LocalDate.ofEpochDay(firstDay).format(DATE);
		String last = LocalDate.ofEpochDay(lastDay).format(DATE);
		int baseline = getHeight() - 3;
		g.drawString(first, padLeft(), baseline);
		int lastWidth = g.getFontMetrics().stringWidth(last);
		g.drawString(last, padLeft() + w - lastWidth, baseline);
		if (lastDay - firstDay >= 4)
		{
			String middle = LocalDate.ofEpochDay((firstDay + lastDay) / 2).format(DATE);
			int middleWidth = g.getFontMetrics().stringWidth(middle);
			int mx = padLeft() + (w - middleWidth) / 2;
			if (mx > padLeft() + g.getFontMetrics().stringWidth(first) + 6 && mx + middleWidth < padLeft() + w - lastWidth - 6)
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
		// in a window the line stays flat from the last point to the last day of the window
		if (latest.getDay() < toDay)
		{
			g.setColor(LINE);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(xFor(latest.getDay()), yFor(latest.getLuck()), xFor(toDay), yFor(latest.getLuck()));
		}
		// the latest point, with its verdict beside it
		int x = xFor(Math.max(latest.getDay(), toDay));
		int y = yFor(latest.getLuck());
		Color latestColor = ExpectedDropsPanel.luckColor(latest.getLuck()).brighter();
		g.setColor(LINE);
		g.fillOval(x - 3, y - 3, 7, 7);
		String verdict = Verdict.labelOf(latest.getLuck());
		if (!verdict.isEmpty())
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			int tw = g.getFontMetrics().stringWidth(verdict);
			FontMetrics fm = g.getFontMetrics();
			int[] spot = placeEndLabel(lineSegments(), x, y, tw, fm.getAscent(), fm.getDescent(),
				padLeft(), padLeft() + w, 0, PAD_TOP + plotHeight());
			if (spot[2] == 1)
			{
				// no clear spot: a dark backing keeps the word readable where the line passes behind it
				g.setColor(withAlpha(ColorScheme.DARKER_GRAY_COLOR, 220));
				g.fillRect(spot[0] - 2, spot[1] - fm.getAscent() - 1, tw + 4, fm.getAscent() + fm.getDescent() + 2);
			}
			g.setColor(latestColor);
			g.drawString(verdict, spot[0], spot[1]);
		}
		if (hasFocus())
		{
			g.setColor(Theme.ACCENT);
			g.setStroke(new BasicStroke(1f));
			g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
		}
		g.dispose();
	}

	/** The drawn polyline as straight segments (flat across days without a point, as in the paint). */
	List<Line2D> lineSegments()
	{
		List<Line2D> out = new ArrayList<>();
		for (int i = 1; i < points.size(); i++)
		{
			HistoryPoint a = points.get(i - 1);
			HistoryPoint b = points.get(i);
			int ax = xFor(a.getDay());
			int ay = yFor(a.getLuck());
			long dayBefore = b.getDay() - 1;
			if (dayBefore > a.getDay())
			{
				out.add(new Line2D.Float(ax, ay, xFor(dayBefore), ay));
				out.add(new Line2D.Float(xFor(dayBefore), ay, xFor(b.getDay()), yFor(b.getLuck())));
			}
			else
			{
				out.add(new Line2D.Float(ax, ay, xFor(b.getDay()), yFor(b.getLuck())));
			}
		}
		return out;
	}

	/**
	 * Where the end verdict word goes: {x, baseline y, backing}. It tries above the dot, then below,
	 * and takes the first spot inside the plot that the line does not cross. When every spot is
	 * crossed, it takes the first one inside the plot and asks for a dark backing (backing = 1).
	 */
	static int[] placeEndLabel(List<Line2D> line, int x, int y, int textWidth, int ascent, int descent,
		int left, int right, int top, int bottom)
	{
		int tx = Math.max(left, Math.min(x - textWidth / 2, right - textWidth));
		int[] baselines = {y - 7, y + 15};
		int firstInside = -1;
		for (int i = 0; i < baselines.length; i++)
		{
			Rectangle box = new Rectangle(tx - 2, baselines[i] - ascent - 1, textWidth + 4, ascent + descent + 2);
			if (box.y < top || box.y + box.height > bottom)
			{
				continue;
			}
			if (firstInside < 0)
			{
				firstInside = i;
			}
			boolean crossed = false;
			for (Line2D segment : line)
			{
				if (segment.intersects(box))
				{
					crossed = true;
					break;
				}
			}
			if (!crossed)
			{
				return new int[]{tx, baselines[i], 0};
			}
		}
		if (firstInside >= 0)
		{
			return new int[]{tx, baselines[firstInside], 1};
		}
		// no room either way (a very short plot): keep it above the dot, backed
		return new int[]{tx, Math.max(top + ascent + 1, baselines[0]), 1};
	}

	@Override
	public String getToolTipText(MouseEvent event)
	{
		if (points.isEmpty())
		{
			return null;
		}
		int index = nearest(event.getX());
		if (index < 0)
		{
			return null;
		}
		return textFor(index);
	}

	/** The tooltip of one point: its date, verdict and got / expected. */
	private String textFor(int index)
	{
		HistoryPoint nearest = points.get(index);
		return String.format(Locale.ROOT, "<html><b>%s</b><br>%s: %s<br>%d got, %.1f expected" + (windowed ? " in total" : "") + "</html>",
			LocalDate.ofEpochDay(nearest.getDay()).format(LONG_DATE), Verdict.labelOf(nearest.getLuck()),
			Texts.html(Texts.luckPercent(nearest.getLuck())), nearest.getReceived(), nearest.getExpected());
	}

	private static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}

