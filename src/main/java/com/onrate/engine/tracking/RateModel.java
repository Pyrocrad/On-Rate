package com.onrate.engine.tracking;

import com.onrate.engine.math.Poisson;
import com.onrate.engine.math.Rates;
import com.onrate.engine.math.Untradeables;
import com.onrate.engine.math.ValueLuck;
import com.onrate.engine.math.Verdict;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.SharedPool;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.ItemHighlight;
import com.onrate.engine.stats.RaidChance;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongFunction;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a drop should give, from its rate, the raid points saved with each raid, the raid level,
 * the part of a combined page, the delve level or the wave. Pure functions: no state, no
 * RuneLite. Moved out of {@link Tracker} unchanged (the replay tests compare it with the original).
 */
public final class RateModel
{
	private RateModel()
	{
	}

	/** Your expected uniques for this raid from its points, or NaN when they are unknown. */
	public static double purpleChance(RaidRecord record)
	{
		if (record == null || record.family == null)
		{
			return Double.NaN;
		}
		switch (record.family)
		{
			case "cox":
				return RaidChance.cox(record.personalPoints, record.teamPoints);
			case "toa":
				return RaidChance.toa(record.personalPoints, record.partyPoints, record.teamPoints,
					record.teamSize, record.raidLevel);
			case "fractured":
				// only a measured contribution counts as "from your points"; a team size alone doesn't
				return record.contribution < 0 ? Double.NaN
					: fracturedChance(record, RaidChance.TFA_BASE_CHANCE, record.teamSize > 0 ? record.teamSize : 2);
			default:
				return Double.NaN;
		}
	}

	/**
	 * Share of a purple that is this item at a raid level (ToA's weights shift towards the
	 * rarer items at high raid levels). Falls back to {@code normal} without level weights.
	 */
	static double levelShare(BossDefinition boss, DropDefinition drop, int raidLevel, double normal)
	{
		boolean any = false;
		double total = 0;
		for (DropDefinition d : boss.getDrops())
		{
			any |= d.hasLevelWeights();
			Double w = d.getWeightAtLevel(raidLevel);
			if (w != null)
			{
				total += w;
			}
		}
		Double w = drop.getWeightAtLevel(raidLevel);
		if (!any || w == null || total <= 0)
		{
			return normal;
		}
		return w / total;
	}

	/**
	 * Your purple chance for one saved raid: from its points (CoX, ToA), or for ToB the
	 * team chance from the settings split over that raid's real team size. NaN when
	 * neither is known, so the setting is used.
	 */
	static double recordChance(RaidRecord record, BossPart part, RaidSettings raids)
	{
		if ("fractured".equals(record.family) && (record.teamSize > 0 || record.contribution >= 0))
		{
			// the base chance follows the setting for raids since the update of 11 November 2026 (older
			// ones keep the chance of their time); without a counted team, the team size setting
			int team = record.teamSize > 0 ? record.teamSize : raids.teamSize(part.getRaid());
			return fracturedChance(record, raids.tfaBaseChance(), team);
		}
		double chance = purpleChance(record);
		if (!Double.isNaN(chance))
		{
			return chance;
		}
		String key = part.getRaid();
		if ("tob".equals(record.family) && record.teamSize > 0 && key != null && !"none".equals(key))
		{
			return raids.uniqueChance(key) * Math.max(1, raids.teamSize(key)) / record.teamSize;
		}
		return Double.NaN;
	}

	/**
	 * The Fractured Archive: your chance from your contribution (or an even share of the team when
	 * it is unknown) after your deaths. A raid saved before the game update of 11 November 2026 keeps
	 * the chance it had then (1 in 14, +4% per player beyond two, whatever {@code baseChance} is now);
	 * raids since use the game's fixed chance for their team size, scaled from {@code baseChance}.
	 */
	static double fracturedChance(RaidRecord record, double baseChance, int teamSize)
	{
		int team = Math.max(1, teamSize);
		boolean before = RaidChance.tfaBeforeChange(record.savedMillis);
		if (record.contribution < 0)
		{
			return before ? RaidChance.tfaEvenBefore(team, record.deaths) : RaidChance.tfaEven(team, record.deaths, baseChance);
		}
		double kept = RaidChance.tfaContributionAfterDeaths(record.contribution, team, record.deaths);
		return before ? RaidChance.tfaBefore(kept, team) : RaidChance.tfa(kept, team, baseChance);
	}

	/**
	 * True when a raid drop comes out of the purple (a weight or a share per unique). Other raid
	 * drops, like The Fractured Archive's pet, folio and fragments, roll on their own at a flat rate
	 * per completion, whatever the points or contribution.
	 */
	static boolean fromPurple(BossPart part, DropDefinition drop)
	{
		return drop.getWeight(part.getName()) != null || drop.getPerUnique() != null;
	}

	/** {@link #fromPurple}, for the panel. */
	public static boolean fromPurplePublic(BossPart part, DropDefinition drop)
	{
		return fromPurple(part, drop);
	}

	/**
	 * What one saved raid adds to the expected count of a drop: its purple chance times the
	 * item's share at that raid level. The Tombs of Amascut pet has its own formula, and drops
	 * that don't come from the purple keep their flat rate.
	 */
	static double recordDropExpected(BossDefinition boss, BossPart part, DropDefinition drop, RaidRecord r,
		double chance)
	{
		if (!fromPurple(part, drop))
		{
			return partRate(boss, part, drop, key -> 1.0);
		}
		if (drop.isPet() && "toa".equals(r.family))
		{
			double pet = RaidChance.toaPet(r.personalPoints, r.partyPoints, r.teamPoints, r.teamSize, r.raidLevel);
			if (!Double.isNaN(pet))
			{
				return pet;
			}
		}
		double share = partRate(boss, part, drop, key -> 1.0);
		double itemShare = r.raidLevel >= 0 ? levelShare(boss, drop, r.raidLevel, share) : share;
		return chance * itemShare * RaidChance.toaLevelFactor(r.raidLevel, drop.getMinRaidLevel());
	}

	/**
	 * A drop's chance over a whole run claimed at {@code wave}: its chance on each wave up to
	 * that one, added up (at most one unique per wave, so this is its expected count).
	 */
	public static double waveRate(DropDefinition drop, int wave)
	{
		double sum = 0;
		for (int w = 1; w <= wave; w++)
		{
			sum += Rates.parse(drop.getLevelRate(w));
		}
		return sum;
	}

	/**
	 * Rate of a drop for one part of a combined page (one boss, or one raid mode).
	 * Explicit part rates win; raid modes use their own purple chance and weights.
	 */
	public static double partRate(BossDefinition boss, BossPart part, DropDefinition drop, RaidSettings raids)
	{
		if (drop.hasPartRates())
		{
			return Rates.parse(drop.getPartRate(part.getName()));
		}
		if (part.getRaid() != null)
		{
			double unique = raids.uniqueChance(part.getRaid());
			Double weight = drop.getWeight(part.getName());
			if (weight != null)
			{
				double total = boss.getTotalWeight(part.getName());
				return total > 0 ? unique * weight / total : 0;
			}
			if (drop.getPerUnique() != null)
			{
				return unique * Rates.parse(drop.getPerUnique());
			}
		}
		return Rates.parse(drop.getRate());
	}

	/**
	 * Expected count of a drop from one part. For raid modes, completions whose points
	 * were read use their own purple chance; older ones use the setting.
	 */
	static double partExpected(BossDefinition boss, BossPart part, DropDefinition drop, RaidSettings raids,
		int completions, PartRaids known)
	{
		double perRaid = partRate(boss, part, drop, raids);
		if (known == null || known.count == 0 || part.getRaid() == null || drop.hasPartRates() || !fromPurple(part, drop))
		{
			return completions * perRaid;
		}
		double expected = (completions - known.count) * perRaid;
		if (drop.isPet() && known.toa)
		{
			// the Tombs of Amascut pet has its own formula from the points
			return expected + known.petSum;
		}
		double share = partRate(boss, part, drop, key -> 1.0);
		for (Map.Entry<Integer, Double> e : known.chanceByLevel.entrySet())
		{
			int level = e.getKey();
			double itemShare = level >= 0 ? levelShare(boss, drop, level, share) : share;
			expected += e.getValue() * itemShare * RaidChance.toaLevelFactor(level, drop.getMinRaidLevel());
		}
		return expected;
	}

	/**
	 * A raid mode's saved raids whose chance is known, added up once per calculation: the purple
	 * chances per raid level (the item shares depend on the level) and the pet chances. Only the
	 * latest {@code completions} raids count.
	 */
	static final class PartRaids
	{
		int count;
		boolean toa;
		double petSum;
		final Map<Integer, Double> chanceByLevel = new HashMap<>();
	}

	static PartRaids partRaids(BossPart part, RaidSettings raids, int completions, BossProgress progress)
	{
		if (progress == null || part.getRaid() == null || progress.raids.isEmpty() || completions <= 0)
		{
			return null;
		}
		List<RaidRecord> known = new ArrayList<>();
		List<Double> chances = new ArrayList<>();
		for (RaidRecord r : progress.raids)
		{
			if (!part.getName().equals(r.part))
			{
				continue;
			}
			double chance = recordChance(r, part, raids);
			if (!Double.isNaN(chance))
			{
				known.add(r);
				chances.add(chance);
			}
		}
		PartRaids result = new PartRaids();
		int from = Math.max(0, known.size() - completions);
		for (int i = from; i < known.size(); i++)
		{
			RaidRecord r = known.get(i);
			result.count++;
			result.chanceByLevel.merge(r.raidLevel, chances.get(i), Double::sum);
			if ("toa".equals(r.family))
			{
				result.toa = true;
				double pet = RaidChance.toaPet(r.personalPoints, r.partyPoints, r.teamPoints, r.teamSize, r.raidLevel);
				result.petSum += Double.isNaN(pet) ? 0 : pet;
			}
		}
		return result;
	}

	/** Per-kill rate for a drop, taking raid settings into account. */
	public static double effectiveRate(BossDefinition boss, DropDefinition drop, RaidSettings raids)
	{
		if (boss.getWaves() > 0)
		{
			// a whole run, claimed at the last wave
			return waveRate(drop, boss.getWaves());
		}
		double rate = Rates.parse(drop.getRate());
		if (boss.getRaid() != null)
		{
			double unique = raids.uniqueChance(boss.getRaid());
			if (drop.getWeight() != null)
			{
				double total = boss.getTotalWeight();
				rate = total > 0 ? unique * drop.getWeight() / total : 0;
			}
			else if (drop.getPerUnique() != null)
			{
				rate = unique * Rates.parse(drop.getPerUnique());
			}
		}
		if (drop.getMinionRate() != null && boss.getMinions() > 0)
		{
			// God Wars bodyguards roll some of the general's uniques too
			rate += boss.getMinions() * Rates.parse(drop.getMinionRate());
		}
		return rate;
	}

	/** Expected drops of a pooled item from one boss, from its kill counts. */
	static double poolExpected(BossDefinition boss, DropDefinition drop, BossStats s, RaidSettings raids)
	{
		if (s.getPartCounts() != null && boss.hasParts())
		{
			double expected = 0;
			for (BossPart part : boss.getParts())
			{
				Integer kc = s.getPartCounts().get(part.getName());
				if (kc != null)
				{
					expected += kc * partRate(boss, part, drop, raids);
				}
			}
			return expected;
		}
		double rate = effectiveRate(boss, drop, raids);
		if (boss.getTeam() != null && !drop.isNoShare())
		{
			rate *= raids.contribution(boss.getTeam(), s.getKillCount());
		}
		return rate * s.getKillCount();
	}
}
