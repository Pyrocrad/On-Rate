package com.onrate.engine.tracking;

import com.onrate.engine.data.*;
import com.onrate.engine.stats.RaidSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.ToLongFunction;

/**
 * What a raid page shows about the raids On Rate saw: your purple chance (and GP value) an hour per
 * setup you chose, your personal bests, and your points raid by raid. Worked out from the saved raid
 * records and History only; nothing here is saved.
 */
public final class RaidInsights
{
	/** A setup row needs this many raids with a time before it shows an hour figure. */
	public static final int MIN_TIMED = 5;
	/** Raid levels are grouped in steps of this many. */
	public static final int LEVEL_STEP = 50;
	/** Team sizes from this one up share one row ("6+"). */
	public static final int TEAM_CAP = 6;

	/** One setup: a raid mode with a team size, and at Tombs of Amascut a raid level step. */
	public static final class Setup
	{
		private final String part;
		private final int team;
		private final int levelFrom;
		private int raids;
		private double chanceSum;
		private int timed;
		private double timedChance;
		private double timedGp;
		private long timedMillis;
		private double gpSum;
		private final int players;
		/** Per item: its expected count over these raids, and over the timed ones. */
		private final Map<String, double[]> items = new HashMap<>();

		Setup(String part, int team, int levelFrom)
		{
			this(part, team, levelFrom, team);
		}

		Setup(String part, int team, int levelFrom, int players)
		{
			this.part = part;
			this.team = team;
			this.levelFrom = levelFrom;
			this.players = players;
		}

		/** The raid mode, e.g. "Normal" or "Challenge Mode". */
		public String getPart()
		{
			return part;
		}

		/**
		 * Team size, at Chambers of Xeric the size the raid scaled to; 0 = unknown; {@link #TEAM_CAP}
		 * means that many or more.
		 */
		public int getTeam()
		{
			return team;
		}

		/**
		 * Players still in the raid at the end: the team size for a raid that wasn't scaled, fewer for a
		 * scaled one (1 for "1+2"), -1 when not known whether it was scaled (older Chambers raids).
		 */
		public int getPlayers()
		{
			return players;
		}

		/** Tombs of Amascut: the first raid level of the step, or -1 (another raid, or unknown). */
		public int getLevelFrom()
		{
			return levelFrom;
		}

		/** Raids of this setup with a known purple chance. */
		public int getRaids()
		{
			return raids;
		}

		/** Raids of this setup with both a purple chance and a time. */
		public int getTimed()
		{
			return timed;
		}

		/** True once there are enough timed raids for an hour figure. */
		public boolean isEnough()
		{
			return timed >= MIN_TIMED;
		}

		/** Average purple chance a raid (0 to 1). */
		public double getChancePerRaid()
		{
			return raids == 0 ? Double.NaN : chanceSum / raids;
		}

		/** Average GP value of the uniques a raid should give, at RuneLite's prices. */
		public double getGpPerRaid()
		{
			return raids == 0 ? Double.NaN : gpSum / raids;
		}

		/** Average time of the timed raids, or -1. */
		public long getAverageMillis()
		{
			return timed == 0 ? -1 : timedMillis / timed;
		}

		/** Purple chance an hour over the timed raids (0.088 = 8.8% an hour), NaN without times. */
		public double getChancePerHour()
		{
			return timedMillis <= 0 ? Double.NaN : timedChance / (timedMillis / 3_600_000.0);
		}

		/**
		 * One item's chance a raid at this setup (its expected count a raid: the purple chance times its
		 * share at these raid levels), NaN when the item isn't from the purple or there are no raids.
		 */
		public double getItemPerRaid(String item)
		{
			double[] s = items.get(item);
			return s == null || raids == 0 ? Double.NaN : s[0] / raids;
		}

		/** The same item an hour over the timed raids, NaN without times. */
		public double getItemPerHour(String item)
		{
			double[] s = items.get(item);
			return s == null || timedMillis <= 0 ? Double.NaN : s[1] / (timedMillis / 3_600_000.0);
		}

		void addItems(Setup other)
		{
			for (Map.Entry<String, double[]> e : other.items.entrySet())
			{
				double[] s = items.computeIfAbsent(e.getKey(), k -> new double[2]);
				s[0] += e.getValue()[0];
				s[1] += e.getValue()[1];
			}
		}

		/** GP value an hour over the timed raids, NaN without times. */
		public double getGpPerHour()
		{
			return timedMillis <= 0 ? Double.NaN : timedGp / (timedMillis / 3_600_000.0);
		}
	}

	/** A personal best: its value and the raid that set it. */
	public static final class Best
	{
		private final double value;
		private final RaidRecord raid;

		Best(double value, RaidRecord raid)
		{
			this.value = value;
			this.raid = raid;
		}

		public double getValue()
		{
			return value;
		}

		public RaidRecord getRaid()
		{
			return raid;
		}

		/** When the raid that set it was saved (epoch millis), 0 when unknown. */
		public long getSavedMillis()
		{
			return raid.savedMillis;
		}
	}

	/** Personal bests of one mode and team size. Any of them can be null. */
	public static final class Bests
	{
		private final String part;
		private final int team;
		private Best fastest;
		private Best mostPoints;
		private Best highestLevel;
		private Best biggestShare;
		private int raids;
		private int withoutDeath;
		private int withDeaths;
		private final int players;

		Bests(String part, int team, int players)
		{
			this.part = part;
			this.team = team;
			this.players = players;
		}

		public String getPart()
		{
			return part;
		}

		public int getTeam()
		{
			return team;
		}

		/** Players at the end, as {@link Setup#getPlayers()}. */
		public int getPlayers()
		{
			return players;
		}

		/** Fastest raid; value in milliseconds. */
		public Best getFastest()
		{
			return fastest;
		}

		/** Most of your own points (Chambers, Tombs). */
		public Best getMostPoints()
		{
			return mostPoints;
		}

		/** Highest raid level (Tombs). */
		public Best getHighestLevel()
		{
			return highestLevel;
		}

		/** Biggest contribution out of 700 (The Fractured Archive). */
		public Best getBiggestShare()
		{
			return biggestShare;
		}

		/** Raids of this mode and team size. */
		public int getRaids()
		{
			return raids;
		}

		/** Raids whose deaths are known and were 0 (Theatre of Blood). */
		public int getWithoutDeath()
		{
			return withoutDeath;
		}

		/** Raids whose deaths are known. */
		public int getDeathsKnown()
		{
			return withoutDeath + withDeaths;
		}
	}

	/** One raid on the points graph. */
	public static final class Point
	{
		private final RaidRecord raid;
		private final double chance;
		private final List<String> uniques;

		Point(RaidRecord raid, double chance, List<String> uniques)
		{
			this.raid = raid;
			this.chance = chance;
			this.uniques = uniques == null ? Collections.emptyList() : uniques;
		}

		/** The uniques this raid gave you, from History or Raid Data Tracker; empty for none. */
		public List<String> getUniques()
		{
			return uniques;
		}

		public RaidRecord getRaid()
		{
			return raid;
		}

		/** When the raid was saved (epoch millis), 0 when unknown. */
		public long getSavedMillis()
		{
			return raid.savedMillis;
		}

		/** Your points, or 0 when unknown (Theatre of Blood has none). */
		public int getPoints()
		{
			return raid.personalPoints;
		}

		/** That raid's purple chance (0 to 1), NaN when unknown. */
		public double getChance()
		{
			return chance;
		}

		/** True when this raid gave you a unique (History, or Raid Data Tracker for raids it read). */
		public boolean isPurple()
		{
			return !uniques.isEmpty();
		}
	}

	/** Group by the whole setup: mode and team size (Tombs of Amascut: team size and raid level). */
	public static final int BY_SETUP = 0;
	/** Group by team size only. */
	public static final int BY_TEAM = 1;
	/** Group by the other choice only: the mode, or at Tombs of Amascut the raid level. */
	public static final int BY_CHOICE = 2;

	private final List<Setup> setups;
	private final List<Bests> bests;
	private final Map<String, List<Point>> points;
	private final List<String> order;
	/** Your latest raids with a known chance, all modes together, or null when there are none. */
	private Setup recent;

	private RaidInsights(List<Setup> setups, List<Bests> bests, Map<String, List<Point>> points, List<String> order)
	{
		this.setups = setups;
		this.bests = bests;
		this.points = points;
		this.order = order;
	}

	/** Setups, those with enough timed raids first, sorted by purple chance an hour, highest first. */
	public List<Setup> getSetups()
	{
		return setups;
	}

	/**
	 * The setups grouped one of three ways ({@link #BY_SETUP}, {@link #BY_TEAM}, {@link #BY_CHOICE}),
	 * sorted the same way. A merged row has no mode (null), no team (-1) or no raid level (-1).
	 */
	public List<Setup> getSetups(int by)
	{
		if (by == BY_SETUP)
		{
			return setups;
		}
		Map<String, Setup> merged = new LinkedHashMap<>();
		for (Setup s : setups)
		{
			boolean level = s.levelFrom >= 0 || isLevelled();
			String part = by == BY_CHOICE && !level ? s.part : null;
			int team = by == BY_TEAM ? s.team : -1;
			int from = by == BY_CHOICE && level ? s.levelFrom : -1;
			int players = by == BY_TEAM ? s.players : -1;
			Setup m = merged.computeIfAbsent(part + "#" + team + "#" + players + "#" + from, k -> new Setup(part, team, from, players));
			m.raids += s.raids;
			m.chanceSum += s.chanceSum;
			m.gpSum += s.gpSum;
			m.timed += s.timed;
			m.timedChance += s.timedChance;
			m.timedGp += s.timedGp;
			m.timedMillis += s.timedMillis;
			m.addItems(s);
		}
		List<Setup> list = new ArrayList<>(merged.values());
		sort(list, order);
		return list;
	}

	/** True when the setups are told apart by raid level (Tombs of Amascut). */
	private boolean isLevelled()
	{
		for (Setup s : setups)
		{
			if (s.levelFrom >= 0)
			{
				return true;
			}
		}
		return false;
	}

	private static void sort(List<Setup> setups, List<String> order)
	{
		setups.sort(Comparator.comparing((Setup s) -> !s.isEnough())
			.thenComparing(s -> s.isEnough() ? -s.getChancePerHour() : 0.0)
			.thenComparing(s -> s.part == null ? -1 : order.indexOf(s.part))
			.thenComparingInt(s -> s.team)
			.thenComparingInt(s -> -s.players)
			.thenComparingInt(s -> s.levelFrom));
	}

	/**
	 * Your latest {@link RaidHabits#RECENT} raids with a known chance, all modes together, as one row:
	 * how you raid now, for the collection log forecast. Null when On Rate saw none.
	 */
	public Setup getRecent()
	{
		return recent;
	}

	private static Setup recentOf(BossDefinition boss, Map<String, BossPart> parts, List<RaidRecord> all, RaidSettings settings)
	{
		Setup recent = new Setup(null, -1, -1);
		for (int i = all.size() - 1; i >= 0 && recent.raids < RaidHabits.RECENT; i--)
		{
			RaidRecord r = all.get(i);
			BossPart part = r.part == null ? null : parts.get(r.part);
			double chance = part == null ? Double.NaN : RateModel.recordChance(r, part, settings);
			if (Double.isNaN(chance))
			{
				continue;
			}
			recent.raids++;
			recent.chanceSum += chance;
			if (r.durationMillis > 0)
			{
				recent.timed++;
				recent.timedChance += chance;
				recent.timedMillis += r.durationMillis;
			}
			addItems(boss, part, r, chance, recent);
		}
		return recent.raids == 0 ? null : recent;
	}

	/** Personal bests per mode and team size, in the boss's mode order then team size. */
	public List<Bests> getBests()
	{
		return bests;
	}

	/** Raids per mode, oldest first, for the graph. */
	public List<Point> getPoints(String part)
	{
		List<Point> p = points.get(part);
		return p == null ? Collections.emptyList() : p;
	}

	/** True when no raid of this boss was saved. */
	public boolean isEmpty()
	{
		return setups.isEmpty() && bests.isEmpty();
	}

	/** The row a team size goes in: 0 unknown, then 1 to {@link #TEAM_CAP}, the cap meaning that many or more. */
	static int teamRow(int teamSize)
	{
		return teamSize <= 0 ? 0 : Math.min(teamSize, TEAM_CAP);
	}

	/** The first raid level of the step a raid level goes in, or -1 when unknown. */
	static int levelStep(int raidLevel)
	{
		return raidLevel < 0 ? -1 : raidLevel / LEVEL_STEP * LEVEL_STEP;
	}

	/**
	 * Works it all out for one raid. {@code prices} may be null (no GP value); {@code counts} says
	 * whether a drop counts (not left out), like the GP value tile.
	 */
	static RaidInsights of(BossDefinition boss, BossProgress progress, RaidSettings settings,
		ToLongFunction<String> prices, BiPredicate<BossDefinition, DropDefinition> counts)
	{
		return of(boss, progress, settings, prices, counts, Collections.emptyMap(), Collections.emptyList(), Collections.emptyMap());
	}

	/** The same, with your uniques from raids read from Raid Data Tracker, by "mode#completion count". */
	static RaidInsights of(BossDefinition boss, BossProgress progress, RaidSettings settings,
		ToLongFunction<String> prices, BiPredicate<BossDefinition, DropDefinition> counts, Map<String, String> imported,
		List<RaidRecord> extra, Map<RaidRecord, String> extraUniques)
	{
		if (boss == null || boss.getRaid() == null || progress == null)
		{
			return new RaidInsights(new ArrayList<>(), new ArrayList<>(), new LinkedHashMap<>(), new ArrayList<>());
		}
		Map<String, BossPart> parts = new LinkedHashMap<>();
		for (BossPart p : boss.getParts())
		{
			parts.put(p.getName(), p);
		}
		Map<String, List<String>> purples = purpleRaids(progress, imported);
		Map<String, Setup> setupMap = new LinkedHashMap<>();
		Map<String, Bests> bestMap = new LinkedHashMap<>();
		Map<String, List<Point>> points = new LinkedHashMap<>();
		boolean toa = "toa".equals(boss.getRaid());
		// the saved raids, and the raids that only show here (Tombs of Amascut from Raid Data Tracker), by date
		List<RaidRecord> all = new ArrayList<>(progress.raids);
		if (extra != null && !extra.isEmpty())
		{
			all.addAll(extra);
			all.sort(Comparator.comparingLong(x -> x.savedMillis));
		}
		for (RaidRecord r : all)
		{
			BossPart part = r.part == null ? null : parts.get(r.part);
			if (part == null)
			{
				continue;
			}
			double chance = RateModel.recordChance(r, part, settings);
			int team = teamRow(r.teamSize);
			// Chambers of Xeric can be scaled: fewer players than the scale; -1 when that isn't known
			int players = !"cox".equals(boss.getRaid()) || team <= 1 ? team
				: r.players == null ? -1 : Math.min(teamRow(r.players), team);
			points.computeIfAbsent(part.getName(), k -> new ArrayList<>())
				.add(new Point(r, chance, extraUniques != null && extraUniques.containsKey(r)
					? Collections.singletonList(extraUniques.get(r)) : purples.get(part.getName() + "#" + r.kc)));
			addBest(bestMap.computeIfAbsent(part.getName() + "#" + team + "#" + players, k -> new Bests(part.getName(), team, players)), r);
			if (Double.isNaN(chance))
			{
				continue;
			}
			int level = toa ? levelStep(r.raidLevel) : -1;
			Setup s = setupMap.computeIfAbsent(part.getName() + "#" + team + "#" + players + "#" + level,
				k -> new Setup(part.getName(), team, level, players));
			double gp = prices == null ? 0 : gpOf(boss, part, r, chance, prices, counts);
			s.raids++;
			s.chanceSum += chance;
			s.gpSum += gp;
			addItems(boss, part, r, chance, s);
			if (r.durationMillis > 0)
			{
				s.timed++;
				s.timedChance += chance;
				s.timedGp += gp;
				s.timedMillis += r.durationMillis;
			}
		}
		Setup recent = recentOf(boss, parts, all, settings);
		List<Setup> setups = new ArrayList<>(setupMap.values());
		List<String> order = new ArrayList<>(parts.keySet());
		sort(setups, order);
		List<Bests> bests = new ArrayList<>(bestMap.values());
		bests.sort(Comparator.comparingInt((Bests b) -> order.indexOf(b.part)).thenComparingInt(b -> b.team).thenComparingInt(b -> -b.players));
		RaidInsights insights = new RaidInsights(setups, bests, points, order);
		insights.recent = recent;
		return insights;
	}

	/**
	 * The uniques of each raid that brought one, by "mode#kc": from History, and for raids History
	 * doesn't have, from Raid Data Tracker.
	 */
	private static Map<String, List<String>> purpleRaids(BossProgress progress, Map<String, String> imported)
	{
		Map<String, List<String>> purples = new HashMap<>();
		for (KillEntry k : progress.kills)
		{
			if (k.partKc != null && k.drops != null && !k.drops.isEmpty())
			{
				List<String> names = purples.computeIfAbsent((k.part == null ? "" : k.part) + "#" + k.partKc, x -> new ArrayList<>());
				for (KillEntry.DropEntry d : k.drops)
				{
					names.add(d.getName());
				}
			}
		}
		if (imported != null)
		{
			for (Map.Entry<String, String> e : imported.entrySet())
			{
				purples.computeIfAbsent(e.getKey(), x -> new ArrayList<>(Collections.singletonList(e.getValue())));
			}
		}
		return purples;
	}

	private static void addBest(Bests b, RaidRecord r)
	{
		b.raids++;
		if (r.durationMillis > 0 && (b.fastest == null || r.durationMillis < b.fastest.value))
		{
			b.fastest = new Best(r.durationMillis, r);
		}
		if (r.personalPoints > 0 && (b.mostPoints == null || r.personalPoints > b.mostPoints.value))
		{
			b.mostPoints = new Best(r.personalPoints, r);
		}
		if (r.raidLevel >= 0 && (b.highestLevel == null || r.raidLevel > b.highestLevel.value))
		{
			b.highestLevel = new Best(r.raidLevel, r);
		}
		if (r.contribution >= 0 && (b.biggestShare == null || r.contribution > b.biggestShare.value))
		{
			b.biggestShare = new Best(r.contribution, r);
		}
		if (r.deaths == 0)
		{
			b.withoutDeath++;
		}
		else if (r.deaths > 0)
		{
			b.withDeaths++;
		}
	}

	/** Adds what one raid should give of each item from the purple (the pet too) to its setup. */
	private static void addItems(BossDefinition boss, BossPart part, RaidRecord r, double chance, Setup s)
	{
		for (DropDefinition drop : boss.getDrops())
		{
			if (!RateModel.fromPurple(part, drop))
			{
				continue;
			}
			double expected = RateModel.recordDropExpected(boss, part, drop, r, chance);
			double[] sums = s.items.computeIfAbsent(drop.getName(), k -> new double[2]);
			sums[0] += expected;
			if (r.durationMillis > 0)
			{
				sums[1] += expected;
			}
		}
	}

	/** GP value the uniques of one raid should give: each drop's expected count times its price. */
	private static double gpOf(BossDefinition boss, BossPart part, RaidRecord r, double chance,
		ToLongFunction<String> prices, BiPredicate<BossDefinition, DropDefinition> counts)
	{
		double sum = 0;
		for (DropDefinition drop : boss.getDrops())
		{
			if (!RateModel.fromPurple(part, drop) || (counts != null && !counts.test(boss, drop)))
			{
				continue;
			}
			long price = Math.max(0, prices.applyAsLong(drop.getName()));
			if (price > 0)
			{
				sum += RateModel.recordDropExpected(boss, part, drop, r, chance) * price;
			}
		}
		return sum;
	}
}
