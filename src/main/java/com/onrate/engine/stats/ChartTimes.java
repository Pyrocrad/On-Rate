package com.onrate.engine.stats;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.LadlorChart;
import com.onrate.engine.tracking.Tracker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How long each item of the Ladlor chart that you don't have takes, in the order of the chart.
 *
 * <p>A chart item is counted as the drops that give it: one or more, some of them several times
 * (crystal armour seeds: 3 for the body). Drops at different bosses are separate grinds, so their
 * times add up. Several drops at one boss (the three Oathplate pieces) come on the same kills, so
 * that boss's time is the expected number of kills until every one has dropped, times the time of
 * a kill. Each drop's time per kill and chance come from the Time page's calculation
 * ({@link ItemTimes}).
 *
 * <p>The collection log counts a drop once, however many chart items it went into, so the copies
 * you have are shared out along the chart: the first chart item that needs the drop takes its
 * copies first (the body's three seeds, then the helm's one, then the legs' two).
 */
public final class ChartTimes
{
	private ChartTimes()
	{
	}

	/** Items of the chart you are still missing something of, in chart order. */
	public static List<ChartTime> compute(Tracker tracker, com.onrate.engine.data.KillTimes web, RaidSettings raids, LadlorChart chart)
	{
		return compute(tracker, web, raids, chart, boss -> true);
	}

	/**
	 * The chart items you are missing something of, leaving out any that needs a drop of a boss
	 * {@code allowed} doesn't let through (a Slayer boss above your level).
	 */
	public static List<ChartTime> compute(Tracker tracker, com.onrate.engine.data.KillTimes web, RaidSettings raids, LadlorChart chart,
		java.util.function.Predicate<String> allowed)
	{
		List<ItemTime> all = ItemTimes.compute(tracker, web, raids, allowed);
		Map<String, Map<String, ItemTime>> byBoss = new LinkedHashMap<>();
		for (ItemTime t : all)
		{
			byBoss.computeIfAbsent(t.getBoss(), k -> new LinkedHashMap<>())
				.put(BossCatalog.normalize(t.getIconItem()), t);
		}
		// copies of each drop already given to an earlier chart item
		Map<String, Integer> taken = new LinkedHashMap<>();
		List<ChartTime> result = new ArrayList<>();
		for (LadlorChart.Item item : chart.getItems())
		{
			if (item.getNeeds().stream().anyMatch(need -> !allowed.test(need.getBoss())))
			{
				continue;
			}
			ChartTime t = timeOf(tracker, raids, byBoss, taken, item);
			if (t != null)
			{
				result.add(t);
			}
		}
		return result;
	}

	/** Null when you have everything the item needs. */
	private static ChartTime timeOf(Tracker tracker, RaidSettings raids, Map<String, Map<String, ItemTime>> byBoss,
		Map<String, Integer> taken, LadlorChart.Item item)
	{
		List<ChartTime.Part> parts = new ArrayList<>();
		// the missing drops of each boss, which come on the same kills (with how many copies of each)
		Map<String, List<ItemTime>> missingAtBoss = new LinkedHashMap<>();
		Map<String, List<Integer>> missingCounts = new LinkedHashMap<>();
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
				String key = need.getBoss() + "|" + BossCatalog.normalize(need.getDrop());
				int copies = need.getCount();
				int before = taken.getOrDefault(key, 0);
				taken.put(key, before + copies);
				// the copies you have go to the chart items in order
				int missing = copies - Math.max(0, Math.min(copies, drop.getReceived() - before));
				String label = copies > 1 ? copies + " x " + drop.getLabel() : drop.getLabel();
				parts.add(new ChartTime.Part(label, need.getBoss(), missing == 0, missing * drop.getSecondsPerDrop(),
					drop.getChancePerKill(), drop.getSecondsPerKill(), key, missing));
				if (missing > 0)
				{
					anythingMissing = true;
					missingAtBoss.computeIfAbsent(need.getBoss(), k -> new ArrayList<>()).add(drop);
					missingCounts.computeIfAbsent(need.getBoss(), k -> new ArrayList<>()).add(missing);
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
		for (Map.Entry<String, List<ItemTime>> e : missingAtBoss.entrySet())
		{
			List<ItemTime> group = e.getValue();
			double[] chances = new double[group.size()];
			int[] counts = new int[group.size()];
			for (int i = 0; i < chances.length; i++)
			{
				chances[i] = group.get(i).getChancePerKill();
				counts[i] = missingCounts.get(e.getKey()).get(i);
			}
			seconds += expectedKillsForAll(chances, counts) * group.get(0).getSecondsPerKill();
		}
		ChartTime result = new ChartTime(item.getStep(), item.getName(), item.getIcon(), item.getNote(), parts,
			unknown ? Double.NaN : seconds, estimated, usesOwn);
		if ("oathplate-shards".equals(item.getRoute()))
		{
			List<ItemTime> yama = missingAtBoss.get("Yama");
			if (yama != null && !yama.isEmpty())
			{
				long yamaNeeds = item.getNeeds().stream().filter(n -> "Yama".equals(n.getBoss()) && n.getDrop() != null).count();
				result.withRoute(OathplateShards.describe(yama.stream().mapToDouble(ItemTime::getChancePerKill).toArray(),
					yama.get(0).getSecondsPerKill(), (int) (yamaNeeds - yama.size())));
			}
		}
		return result;
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
	 * (the five Chambers items, say), come on the same kills, and the same drop in two items (the
	 * crystal seeds of the body, helm and legs) is one grind of all its copies, so this is less than
	 * the items added up.
	 */
	public static Total total(List<ChartTime> items)
	{
		// per boss: each drop with its chance and how many copies are missing in all
		Map<String, Map<String, double[]>> dropsAtBoss = new LinkedHashMap<>();
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
				if (part.getDropKey() == null)
				{
					// the kills to go for a reward that is sure at a kill count
					seconds += part.getSeconds();
				}
				else
				{
					double[] slot = dropsAtBoss.computeIfAbsent(part.getBoss(), k -> new LinkedHashMap<>())
						.computeIfAbsent(part.getDropKey(), k -> new double[]{part.getChancePerKill(), 0});
					slot[1] += part.getMissing();
					secondsAtBoss.putIfAbsent(part.getBoss(), part.getSecondsPerKill());
				}
			}
		}
		for (Map.Entry<String, Map<String, double[]>> e : dropsAtBoss.entrySet())
		{
			double[] chances = new double[e.getValue().size()];
			int[] counts = new int[chances.length];
			int i = 0;
			for (double[] slot : e.getValue().values())
			{
				chances[i] = slot[0];
				counts[i++] = (int) slot[1];
			}
			seconds += expectedKillsForAll(chances, counts) * secondsAtBoss.get(e.getKey());
		}
		return new Total(seconds, without);
	}

	/**
	 * Expected number of kills until every drop has dropped at least once, when each kill rolls
	 * each drop independently at its own chance (inclusion and exclusion over the sets of drops).
	 */
	public static double expectedKillsForAll(double[] chances)
	{
		int[] ones = new int[chances.length];
		java.util.Arrays.fill(ones, 1);
		return expectedKillsForAll(chances, ones);
	}

	/**
	 * Expected number of kills until drop {@code i} has dropped {@code counts[i]} times, for every
	 * drop, each rolled independently at its own chance on every kill. One copy of each is worked
	 * out exactly by inclusion and exclusion over the sets of drops; more than one by adding up,
	 * kill by kill, the chance that something is still missing (the expected value of a wait is the
	 * sum of the chances that it is not over yet).
	 */
	public static double expectedKillsForAll(double[] chances, int[] counts)
	{
		int n = chances.length;
		if (n == 0)
		{
			return 0;
		}
		boolean single = true;
		for (int c : counts)
		{
			single &= c == 1;
		}
		if (single)
		{
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
		// dist[i][j]: the chance of exactly j copies of drop i so far, the last slot being "all of them"
		double[][] dist = new double[n][];
		for (int i = 0; i < n; i++)
		{
			dist[i] = new double[counts[i] + 1];
			dist[i][0] = 1;
		}
		double expected = 0;
		for (long kill = 0; kill < 50_000_000L; kill++)
		{
			double allDone = 1;
			for (int i = 0; i < n; i++)
			{
				allDone *= dist[i][counts[i]];
			}
			double stillMissing = 1 - allDone;
			expected += stillMissing;
			if (stillMissing < 1e-12)
			{
				break;
			}
			for (int i = 0; i < n; i++)
			{
				double p = chances[i];
				double[] d = dist[i];
				int need = counts[i];
				d[need] += d[need - 1] * p;
				for (int j = need - 1; j >= 1; j--)
				{
					d[j] = d[j] * (1 - p) + d[j - 1] * p;
				}
				d[0] *= 1 - p;
			}
		}
		return expected;
	}
}
