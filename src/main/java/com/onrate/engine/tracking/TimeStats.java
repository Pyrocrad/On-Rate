package com.onrate.engine.tracking;

import lombok.Getter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How long a boss's kills (or one raid mode's raids) took, from the kills in History that have
 * a time: the fastest, the average, the middle one and the latest, in milliseconds.
 */
public class TimeStats
{
	@Getter private final int count;
	private final int best;
	private final int average;
	private final int median;
	private final int last;

	TimeStats(List<Integer> times, int last)
	{
		List<Integer> sorted = new ArrayList<>(times);
		Collections.sort(sorted);
		this.count = sorted.size();
		this.best = sorted.get(0);
		long sum = 0;
		for (int t : sorted)
		{
			sum += t;
		}
		this.average = (int) Math.round((double) sum / count);
		this.median = count % 2 == 1 ? sorted.get(count / 2) : (int) Math.round((sorted.get(count / 2 - 1) + sorted.get(count / 2)) / 2.0);
		this.last = last;
	}

	public int getBestMillis()
	{
		return best;
	}

	public int getAverageMillis()
	{
		return average;
	}

	public int getMedianMillis()
	{
		return median;
	}

	/** The time of the most recent kill that has one. */
	public int getLastMillis()
	{
		return last;
	}
}
