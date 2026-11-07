package com.expecteddrops.stats;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.LadlorChart;
import com.expecteddrops.tracking.Tracker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How long each item of the Ladlor chart that you don't have takes, in the order of the chart.
 *
 * <p>A chart item needs one or more drops. Drops at different bosses are separate grinds, so
 * their times add up. Several drops at one boss (the three Oathplate pieces) come on the same
 * kills, so that boss's time is the expected number of kills until every one has dropped, times
 * the time of a kill. Each drop's time per kill and chance come from the Time page's calculation
 * ({@link ItemTimes}).
 */
public final class ChartTimes
{
	private ChartTimes()
	{
	}

	/** Items of the chart you are still missing something of, in chart order. */
	public static List<ChartTime> compute(Tracker tracker, com.expecteddrops.data.KillTimes web, RaidSettings raids, LadlorChart chart)
	{
		List<ItemTime> all = ItemTimes.compute(tracker, web, raids);
		Map<String, Map<String, ItemTime>> byBoss = new LinkedHashMap<>();
		for (ItemTime t : all)
		{
			byBoss.computeIfAbsent(t.getBoss(), k -> new LinkedHashMap<>())
				.put(BossCatalog.normalize(t.getIconItem()), t);
		}
		List<ChartTime> result = new ArrayList<>();
		for (LadlorChart.Item item : chart.getItems())
		{
			ChartTime t = timeOf(tracker, raids, byBoss, item);
			if (t != null)
			{
				result.add(t);
			}
		}
		return result;
	}

	/** Null when you have everything the item needs. */
	private static ChartTime timeOf(Tracker tracker, RaidSettings raids, Map<String, Map<String, ItemTime>> byBoss, LadlorChart.Item item)
	{
		List<ChartTime.Part> parts = new ArrayList<>();
		// the missing drops of each boss, which come on the same kills
		Map<String, List<ItemTime>> missingAtBoss = new LinkedHashMap<>();
		double seconds = 0;
		boolean unknown = false;
		boolean estimated = false;
		boolean usesOwn = false;
		boolean anythingMissing = false;
		for (LadlorChart.Need need : item.getNeeds())
		{
			Map<String, ItemTime> rows = byBoss.get(need.getBoss());
			if (need.getDrop() != null)
			{
				ItemTime drop = rows == null ? null : rows.get(BossCatalog.normalize(need.getDrop()));
				if (drop == null)
				{
					// no time for this boss (or a drop that isn't on its page): the item can't be timed
					parts.add(new ChartTime.Part(need.getDrop(), need.getBoss(), false, Double.NaN, Double.NaN, Double.NaN));
					unknown = true;
					anythingMissing = true;
					continue;
				}
				parts.add(new ChartTime.Part(drop.getLabel(), need.getBoss(), drop.isHave(), drop.isHave() ? 0 : drop.getSecondsPerDrop(),
					drop.getChancePerKill(), drop.getSecondsPerKill()));
				if (!drop.isHave())
				{
					anythingMissing = true;
					missingAtBoss.computeIfAbsent(need.getBoss(), k -> new ArrayList<>()).add(drop);
					estimated |= drop.isEstimated();
					usesOwn |= drop.usesOwnTimes();
				}
			}
			else if (need.getKills() != null)
			{
				// a reward that is sure at a kill count: the kills still to do
				BossStats stats = tracker.stats(need.getBoss(), raids);
				int done = stats == null ? 0 : stats.getKillCount();
				int left = Math.max(0, need.getKills() - done);
				String label = need.getKills() + " " + need.getBoss() + " kills";
				ItemTime any = rows == null || rows.isEmpty() ? null : rows.values().iterator().next();
				if (left == 0)
				{
					parts.add(new ChartTime.Part(label, need.getBoss(), true, 0, Double.NaN, Double.NaN));
				}
				else if (any == null)
				{
					parts.add(new ChartTime.Part(label, need.getBoss(), false, Double.NaN, Double.NaN, Double.NaN));
					unknown = true;
					anythingMissing = true;
				}
				else
				{
					double s = left * any.getSecondsPerKill();
					parts.add(new ChartTime.Part(label + " (" + left + " to go)", need.getBoss(), false, s, Double.NaN, any.getSecondsPerKill()));
					seconds += s;
					anythingMissing = true;
					estimated |= any.isEstimated();
					usesOwn |= any.usesOwnTimes();
				}
			}
		}
		if (!anythingMissing)
		{
			return null;
		}
		for (List<ItemTime> group : missingAtBoss.values())
		{
			double[] chances = new double[group.size()];
			for (int i = 0; i < chances.length; i++)
			{
				chances[i] = group.get(i).getChancePerKill();
			}
			seconds += expectedKillsForAll(chances) * group.get(0).getSecondsPerKill();
		}
		return new ChartTime(item.getStep(), item.getName(), item.getIcon(), item.getNote(), parts,
			unknown ? Double.NaN : seconds, estimated, usesOwn);
	}

	/** The time for everything the listed chart items still need, and how many items had no time and are left out of it. */
	public static final class Total
	{
		private final double seconds;
		private final int withoutTime;

		Total(double seconds, int withoutTime)
		{
			this.seconds = seconds;
			this.withoutTime = withoutTime;
		}

		public double getSeconds()
		{
			return seconds;
		}

		public int getWithoutTime()
		{
			return withoutTime;
		}
	}

	/**
	 * How long everything on the list takes together. Drops at one boss, across all the chart's items
	 * (the five Chambers items, say), come on the same raids, so this is less than the items added up.
	 */
	public static Total total(List<ChartTime> items)
	{
		Map<String, List<Double>> chancesAtBoss = new LinkedHashMap<>();
		Map<String, Double> secondsAtBoss = new LinkedHashMap<>();
		double seconds = 0;
		int without = 0;
		for (ChartTime item : items)
		{
			if (!item.hasTime())
			{
				without++;
				continue;
			}
			for (ChartTime.Part part : item.getParts())
			{
				if (part.isHave())
				{
					continue;
				}
				if (Double.isNaN(part.getChancePerKill()))
				{
					// the kills to go for a reward that is sure at a kill count
					seconds += part.getSeconds();
				}
				else
				{
					chancesAtBoss.computeIfAbsent(part.getBoss(), k -> new ArrayList<>()).add(part.getChancePerKill());
					secondsAtBoss.putIfAbsent(part.getBoss(), part.getSecondsPerKill());
				}
			}
		}
		for (Map.Entry<String, List<Double>> e : chancesAtBoss.entrySet())
		{
			double[] chances = e.getValue().stream().mapToDouble(Double::doubleValue).toArray();
			seconds += expectedKillsForAll(chances) * secondsAtBoss.get(e.getKey());
		}
		return new Total(seconds, without);
	}

	/**
	 * Expected number of kills until every drop has dropped at least once, when each kill rolls
	 * each drop independently at its own chance (inclusion and exclusion over the sets of drops).
	 */
	public static double expectedKillsForAll(double[] chances)
	{
		int n = chances.length;
		if (n == 0)
		{
			return 0;
		}
		if (n > 12)
		{
			throw new IllegalArgumentException("too many drops at one boss");
		}
		double expected = 0;
		for (int set = 1; set < (1 << n); set++)
		{
			double p = 0;
			int bits = 0;
			for (int i = 0; i < n; i++)
			{
				if ((set & (1 << i)) != 0)
				{
					p += chances[i];
					bits++;
				}
			}
			expected += (bits % 2 == 1 ? 1 : -1) / p;
		}
		return expected;
	}
}
