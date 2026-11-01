package com.expecteddrops.tracking;

import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.BossPart;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RaidChance;
import com.expecteddrops.stats.RaidSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The drop-rate maths of raids, raid modes and wave runs: pure functions of the catalog, the
 * raid settings and the saved raids, with no state of their own. Split out of {@link Tracker}.
 */
public final class RaidOdds
{
	private RaidOdds()
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
	 * What one saved raid adds to the expected count of a drop: its purple chance times the
	 * item's share at that raid level. The Tombs of Amascut pet has its own formula.
	 */
	static double recordDropExpected(BossDefinition boss, BossPart part, DropDefinition drop, RaidRecord r,
		double chance)
	{
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
			sum += DropMath.parseRate(drop.getLevelRate(w));
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
			return DropMath.parseRate(drop.getPartRate(part.getName()));
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
				return unique * DropMath.parseRate(drop.getPerUnique());
			}
		}
		return DropMath.parseRate(drop.getRate());
	}

	/**
	 * Expected count of a drop from one part. For raid modes, completions whose points
	 * were read use their own purple chance; older ones use the setting.
	 */
	static double partExpected(BossDefinition boss, BossPart part, DropDefinition drop, RaidSettings raids,
		int completions, PartRaids known)
	{
		double perRaid = partRate(boss, part, drop, raids);
		if (known == null || known.count == 0 || part.getRaid() == null || drop.hasPartRates())
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
		double rate = DropMath.parseRate(drop.getRate());
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
				rate = unique * DropMath.parseRate(drop.getPerUnique());
			}
		}
		if (drop.getMinionRate() != null && boss.getMinions() > 0)
		{
			// God Wars bodyguards roll some of the general's uniques too
			rate += boss.getMinions() * DropMath.parseRate(drop.getMinionRate());
		}
		return rate;
	}
}
