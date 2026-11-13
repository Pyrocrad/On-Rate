package com.expecteddrops.ui;

import com.onrate.engine.tracking.HistoryPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Which part of the luck history the graph draws. Every point is the whole account's luck on one day, so a window
 * only zooms in on the same line; it is never the luck of those days alone. Pure: today is passed in.
 */
final class LuckWindow
{
	private LuckWindow()
	{
	}

	/** First day of a window of {@code days} calendar days that ends on {@code today}. */
	static long start(long today, int days)
	{
		return today - days + 1;
	}

	/** How many calendar days the history spans, first to last point; 0 without points. */
	static long span(List<HistoryPoint> history)
	{
		if (history == null || history.isEmpty())
		{
			return 0;
		}
		return history.get(history.size() - 1).getDay() - history.get(0).getDay() + 1;
	}

	/**
	 * The points to draw for the days {@code from} to {@code to}: the last point before {@code from} first (so the line
	 * starts where it stood and stays flat), then the points inside the window. Points after {@code to} are left out.
	 * {@code history} is oldest first, as saved.
	 */
	static List<HistoryPoint> select(List<HistoryPoint> history, long from, long to)
	{
		List<HistoryPoint> out = new ArrayList<>();
		if (history == null)
		{
			return out;
		}
		HistoryPoint before = null;
		for (HistoryPoint p : history)
		{
			if (p.getDay() < from)
			{
				before = p;
			}
			else if (p.getDay() <= to)
			{
				out.add(p);
			}
		}
		if (before != null)
		{
			out.add(0, before);
		}
		return out;
	}

	/** How many of the selected points lie inside the window (the first one is a carry-in when it is before {@code from}). */
	static int inside(List<HistoryPoint> selected, long from)
	{
		int n = 0;
		for (HistoryPoint p : selected)
		{
			if (p.getDay() >= from)
			{
				n++;
			}
		}
		return n;
	}
}
