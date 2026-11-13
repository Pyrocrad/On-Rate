package com.onrate.engine.stats;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.Locale;
import java.util.Random;

/**
 * Yama's other way to Oathplate: oathplate shards. Every kill drops some (about 0.81 on average),
 * 450 of them with 2,520 crushed infernal shale make any piece you are missing (83 Smithing), so
 * the pieces come from the drop chance and from the shards together. Figures from the OSRS Wiki's
 * oathplate shards page. The shale grind is not counted in any time here.
 */
public final class OathplateShards
{
	public static final double SHARDS_PER_KILL = 0.8075;
	public static final int SHARDS_PER_PIECE = 450;
	public static final int SHALE_PER_PIECE = 2520;
	public static final int SMITHING_LEVEL = 83;

	private static final int TRIALS = 4000;
	private static final long SEED = 20260607L;

	private OathplateShards()
	{
	}

	/**
	 * Expected kills until every missing piece is had, from drops and shards together, starting
	 * with no shards. Each kill drops piece {@code i} at {@code chances[i]}, and 450 shards turn
	 * into a missing piece whenever you have that many. Shards come at their average rate, so the
	 * count is a fair figure and not a forecast. The same answer every time it is asked.
	 */
	public static double expectedKills(double[] chances)
	{
		if (chances.length == 0)
		{
			return 0;
		}
		// the same chances are asked for on every draw and after every kill, and the answer never
		// changes (the simulation starts from the same seed): it is worked out once per set of chances
		String key = Arrays.toString(chances);
		synchronized (KNOWN)
		{
			Double known = KNOWN.get(key);
			if (known != null)
			{
				return known;
			}
		}
		double kills = simulate(chances);
		synchronized (KNOWN)
		{
			KNOWN.put(key, kills);
		}
		return kills;
	}

	/** The last answers of {@link #expectedKills}, by the chances they are for. */
	private static final Map<String, Double> KNOWN = new LinkedHashMap<String, Double>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Double> eldest)
		{
			return size() > 64;
		}
	};

	private static double simulate(double[] chances)
	{
		int n = chances.length;
		Random random = new Random(SEED);
		boolean[] have = new boolean[n];
		double total = 0;
		for (int trial = 0; trial < TRIALS; trial++)
		{
			Arrays.fill(have, false);
			int missing = n;
			double shards = 0;
			int kills = 0;
			while (missing > 0)
			{
				kills++;
				for (int i = 0; i < n; i++)
				{
					if (!have[i] && random.nextDouble() < chances[i])
					{
						have[i] = true;
						missing--;
					}
				}
				shards += SHARDS_PER_KILL;
				while (shards >= SHARDS_PER_PIECE && missing > 0)
				{
					shards -= SHARDS_PER_PIECE;
					for (int i = 0; i < n; i++)
					{
						if (!have[i])
						{
							have[i] = true;
							missing--;
							break;
						}
					}
				}
			}
			total += kills;
		}
		return total / TRIALS;
	}

	/**
	 * The hover text for {@code chances.length} missing pieces: what shards are and what they save.
	 *
	 * @param secondsPerKill the time of one Yama kill, NaN when unknown (then only kills are given)
	 */
	public static String describe(double[] chances, double secondsPerKill)
	{
		return describe(chances, secondsPerKill, 0);
	}

	/**
	 * {@link #describe(double[], double)} for someone who already has {@code have} of the pieces: it
	 * says so, and the numbers are for the ones still missing.
	 */
	public static String describe(double[] chances, double secondsPerKill, int have)
	{
		int n = chances.length;
		double withShards = expectedKills(chances);
		double dropsOnly = ChartTimes.expectedKillsForAll(chances);
		boolean timed = !Double.isNaN(secondsPerKill);
		String which = n == 1 ? (have > 0 ? "the piece you are missing" : "this piece") : "all " + n + " missing pieces";
		String had = have > 0 ? String.format(Locale.ROOT, "You have %d of %d pieces. ", have, have + n) : "";
		return had + String.format(Locale.ROOT, "Oathplate shards: Yama also drops about %.2f a kill, and %d of them plus %,d crushed infernal shale "
				+ "(%d Smithing) make a piece you are missing. With drops and shards together %s takes about %,.0f kills%s, "
				+ "against %,.0f kills%s from drops alone. This assumes you start with no shards; the shale is not counted.",
			SHARDS_PER_KILL, SHARDS_PER_PIECE, SHALE_PER_PIECE, SMITHING_LEVEL, which,
			withShards, timed ? " (" + ItemTime.formatLong(withShards * secondsPerKill) + ")" : "",
			dropsOnly, timed ? " (" + ItemTime.formatLong(dropsOnly * secondsPerKill) + ")" : "");
	}
}

