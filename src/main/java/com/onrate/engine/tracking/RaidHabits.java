package com.onrate.engine.tracking;

import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * How you usually raid, from the raids On Rate saw: your average purple chance per raid mode
 * (Chambers of Xeric and Tombs of Amascut, from your points) and your usual team size (Theatre of
 * Blood and The Fractured Archive, which have no points). Raids On Rate didn't see (older
 * completions) can use these instead of the settings' typical values. Worked out from the saved
 * raid records only; nothing here is saved.
 */
public final class RaidHabits
{
	/** A raid mode needs this many recorded raids before its average is used. */
	public static final int MIN_RAIDS = 10;
	/** Only the latest this many raids of a mode count: how you raid now. */
	public static final int RECENT = 50;

	private static final RaidHabits NONE = new RaidHabits(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

	private final Map<String, Double> chances;
	private final Map<String, Integer> teams;
	private final Map<String, Integer> raids;

	private RaidHabits(Map<String, Double> chances, Map<String, Integer> teams, Map<String, Integer> raids)
	{
		this.chances = chances;
		this.teams = teams;
		this.raids = raids;
	}

	/** No recorded raids: every mode keeps its setting. */
	public static RaidHabits none()
	{
		return NONE;
	}

	/**
	 * Your average purple chance per raid (0 to 1) for a raid mode key ("cox", "toaExpert"...), or NaN
	 * when there are fewer than {@link #MIN_RAIDS} raids with points.
	 */
	public double chance(String raid)
	{
		Double c = chances.get(raid);
		return c == null ? Double.NaN : c;
	}

	/** Your usual team size for a raid mode key ("tob", "fractured"...), or 0 when not enough raids. */
	public int teamSize(String raid)
	{
		Integer t = teams.get(raid);
		return t == null ? 0 : t;
	}

	/** How many recorded raids {@link #chance} or {@link #teamSize} came from, 0 when it isn't used. */
	public int raids(String raid)
	{
		Integer n = raids.get(raid);
		return n == null ? 0 : n;
	}

	/**
	 * Works it out for every raid in the catalogue. {@code progress} gives a boss's saved progress
	 * (null when there is none).
	 */
	public static RaidHabits of(Iterable<BossDefinition> bosses, Function<String, BossProgress> progress)
	{
		Map<String, Double> chances = new HashMap<>();
		Map<String, Integer> teams = new HashMap<>();
		Map<String, Integer> raids = new HashMap<>();
		for (BossDefinition boss : bosses)
		{
			if (boss.getRaid() == null)
			{
				continue;
			}
			BossProgress p = progress.apply(boss.getName());
			if (p == null || p.raids.isEmpty())
			{
				continue;
			}
			for (BossPart part : boss.getParts())
			{
				String key = part.getRaid();
				if (key == null || "none".equals(key))
				{
					continue;
				}
				List<RaidRecord> mine = new ArrayList<>();
				for (RaidRecord r : p.raids)
				{
					if (part.getName().equals(r.part))
					{
						mine.add(r);
					}
				}
				if (usesTeamSize(boss.getRaid()))
				{
					addTeam(key, mine, teams, raids);
				}
				else
				{
					addChance(key, mine, chances, raids);
				}
			}
		}
		return chances.isEmpty() && teams.isEmpty() ? NONE : new RaidHabits(chances, teams, raids);
	}

	/** Theatre of Blood and The Fractured Archive have no points: what they learn is your team size. */
	public static boolean usesTeamSize(String family)
	{
		return "tob".equals(family) || "fractured".equals(family);
	}

	/** The average purple chance of the latest raids with points (an average, as expected counts add up). */
	private static void addChance(String key, List<RaidRecord> mine, Map<String, Double> chances, Map<String, Integer> raids)
	{
		double sum = 0;
		int n = 0;
		for (int i = mine.size() - 1; i >= 0 && n < RECENT; i--)
		{
			double c = RateModel.purpleChance(mine.get(i));
			if (!Double.isNaN(c))
			{
				sum += c;
				n++;
			}
		}
		if (n >= MIN_RAIDS)
		{
			chances.put(key, sum / n);
			raids.put(key, n);
		}
	}

	/** The middle team size of the latest raids whose team was counted. */
	private static void addTeam(String key, List<RaidRecord> mine, Map<String, Integer> teams, Map<String, Integer> raids)
	{
		List<Integer> sizes = new ArrayList<>();
		for (int i = mine.size() - 1; i >= 0 && sizes.size() < RECENT; i--)
		{
			int t = mine.get(i).teamSize;
			if (t > 0)
			{
				sizes.add(t);
			}
		}
		if (sizes.size() >= MIN_RAIDS)
		{
			Collections.sort(sizes);
			teams.put(key, sizes.get(sizes.size() / 2));
			raids.put(key, sizes.size());
		}
	}
}
