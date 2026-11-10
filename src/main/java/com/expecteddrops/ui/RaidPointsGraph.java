package com.expecteddrops.ui;

import com.onrate.engine.tracking.RaidInsights;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Your last raids of one mode, one dot each: your points (or that raid's purple chance), an
 * orange line for the average of the last {@link #AVERAGE} raids, and a purple diamond for a raid
 * that brought a unique.
 */
class RaidPointsGraph extends JComponent
{
	/** How many raids the graph shows, newest last. */
	static final int SHOWN = 40;
	/** The average line averages this many raids. */
	static final int AVERAGE = 10;
	private static final int HEIGHT = 110;
	private static final int PAD_LEFT = 30;
	private static final int PAD_RIGHT = 6;
	private static final int PAD_TOP = 6;
	private static final int PAD_BOTTOM = 14;
	private static final Color DOT = new Color(150, 150, 150);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

	private List<RaidInsights.Point> points = new ArrayList<>();
	private boolean chance;
	private int hover = -1;

	RaidPointsGraph()
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

	@Override
	public javax.accessibility.AccessibleContext getAccessibleContext()
	{
		if (accessibleContext == null)
		{
			accessibleContext = new AccessibleJComponent()
			{
			};
			accessibleContext.setAccessibleName("Your points per raid over your last raids, all modes together");
		}
		return accessibleContext;
	}

	/**
	 * The raids to draw (only those with a value are kept, the last {@link #SHOWN}), as points or as
	 * purple chance.
	 */
	void setPoints(List<RaidInsights.Point> all, boolean chance)
	{
		this.chance = chance;
		List<RaidInsights.Point> kept = new ArrayList<>();
		for (RaidInsights.Point p : all)
		{
			if (!Double.isNaN(value(p)) && value(p) > 0)
			{
				kept.add(p);
			}
		}
		points = kept.subList(Math.max(0, kept.size() - SHOWN), kept.size());
		hover = -1;
		repaint();
	}

	/** How many raids are drawn. */
	int shown()
	{
		return points.size();
	}

	private double value(RaidInsights.Point p)
	{
		return chance ? p.getChance() : p.getPoints();
	}

	/** Average of the raid at {@code i} and the ones before it, up to {@link #AVERAGE}. */
	double average(int i)
	{
		double sum = 0;
		int n = 0;
		for (int j = Math.max(0, i - AVERAGE + 1); j <= i; j++)
		{
			sum += value(points.get(j));
			n++;
		}
		return n == 0 ? Double.NaN : sum / n;
	}

	private void setHover(int index)
	{
		if (index != hover)
		{
			hover = index;
			repaint();
		}
	}

	private int nearest(int x)
	{
		int best = -1;
		for (int i = 0; i < points.size(); i++)
		{
			if (best < 0 || Math.abs(xFor(i) - x) < Math.abs(xFor(best) - x))
			{
				best = i;
			}
		}
		return best;
	}

	private int plotWidth()
	{
		return Math.max(1, getWidth() - PAD_LEFT - PAD_RIGHT);
	}

	private int plotHeight()
	{
		return Math.max(1, getHeight() - PAD_TOP - PAD_BOTTOM);
	}

	private int xFor(int i)
	{
		return points.size() <= 1 ? PAD_LEFT + plotWidth() / 2 : PAD_LEFT + (int) Math.round(i * (double) plotWidth() / (points.size() - 1));
	}

	/** Top of the scale: a round step above the highest raid. */
	private double top()
	{
		double max = 0;
		for (RaidInsights.Point p : points)
		{
			max = Math.max(max, value(p));
		}
		double step = step();
		return Math.max(step, Math.ceil(max / step) * step);
	}

	/** The step between the scale's lines: 10k points or 2% purple chance. */
	private double step()
	{
		return chance ? 0.02 : 10_000;
	}

	private int yFor(double v, double top)
	{
		return PAD_TOP + (int) Math.round((1 - v / top) * plotHeight());
	}

	private String label(double v)
	{
		return chance ? String.format(Locale.ROOT, "%.0f%%", v * 100) : String.format(Locale.ROOT, "%.0fk", v / 1000);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(ColorScheme.DARKER_GRAY_COLOR);
		g.fillRect(0, 0, getWidth(), getHeight());
		if (points.isEmpty())
		{
			g.dispose();
			return;
		}
		double top = top();
		double step = step();
		// the scale: a faint line and its label every step, at most four
		int lines = (int) Math.round(top / step);
		int every = Math.max(1, (int) Math.ceil(lines / 4.0));
		g.setFont(FontManager.getRunescapeSmallFont());
		for (int k = every; k <= lines; k += every)
		{
			int y = yFor(k * step, top);
			g.setColor(Theme.DIVIDER);
			g.drawLine(PAD_LEFT, y, PAD_LEFT + plotWidth(), y);
			g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
			g.drawString(label(k * step), 2, y + 4);
		}
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		int baseline = getHeight() - 2;
		g.drawString("older", PAD_LEFT, baseline);
		String newest = "newest";
		g.drawString(newest, PAD_LEFT + plotWidth() - g.getFontMetrics().stringWidth(newest), baseline);

		if (hover >= 0 && hover < points.size())
		{
			g.setColor(new Color(255, 255, 255, 70));
			g.drawLine(xFor(hover), PAD_TOP, xFor(hover), PAD_TOP + plotHeight());
		}
		for (int i = 0; i < points.size(); i++)
		{
			RaidInsights.Point p = points.get(i);
			int x = xFor(i);
			int y = yFor(value(p), top);
			if (p.isPurple())
			{
				g.setColor(Theme.RAID_PURPLE);
				g.fillPolygon(new Polygon(new int[]{x, x + 4, x, x - 4}, new int[]{y - 4, y, y + 4, y}, 4));
			}
			else
			{
				g.setColor(i == hover ? Color.WHITE : DOT);
				g.fillOval(x - 2, y - 2, 5, 5);
			}
		}
		g.setColor(Theme.ACCENT);
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		for (int i = 1; i < points.size(); i++)
		{
			g.drawLine(xFor(i - 1), yFor(average(i - 1), top), xFor(i), yFor(average(i), top));
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
		RaidInsights.Point p = points.get(nearest(event.getX()));
		long saved = p.getSavedMillis();
		String date = saved > 0 ? Instant.ofEpochMilli(saved).atZone(ZoneId.systemDefault()).format(DATE) : "";
		// a Tombs raid from Raid Data Tracker has no reliable raid number, only its date
		Tip tip = new Tip(p.getRaid().getKc() > 0
			? String.format(Locale.ROOT, "Raid %,d", p.getRaid().getKc()) + (date.isEmpty() ? "" : " · " + date)
			: date.isEmpty() ? "A raid" : date);
		if (p.getRaid().getPart() != null)
		{
			tip.row("Mode", p.getRaid().getPart());
		}
		if (p.getPoints() > 0)
		{
			tip.row("Your points", Texts.formatPoints(p.getPoints()));
		}
		if (!Double.isNaN(p.getChance()))
		{
			tip.row("Purple chance", String.format(Locale.ROOT, "%.1f%%", p.getChance() * 100));
		}
		if (p.getRaid().getTeamSize() > 0)
		{
			tip.row("Team", ExpectedDropsPanel.teamWord(p.getRaid().getTeamSize()));
		}
		if (p.getRaid().getRaidLevel() >= 0)
		{
			tip.row("Raid level", String.valueOf(p.getRaid().getRaidLevel()));
		}
		if (p.isPurple())
		{
			tip.row(p.getUniques().size() == 1 ? "Unique" : "Uniques", String.join(", ", p.getUniques()), Theme.RAID_PURPLE);
		}
		return tip.small();
	}
}
