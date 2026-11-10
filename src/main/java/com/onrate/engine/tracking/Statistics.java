package com.onrate.engine.tracking;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.math.Poisson;
import com.onrate.engine.math.Rates;
import com.onrate.engine.math.Untradeables;
import com.onrate.engine.math.ValueLuck;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.ItemHighlight;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import com.onrate.engine.stats.SharedPool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToLongFunction;

/**
 * The numbers On Rate shows: a boss's rows (expected, received, luck), the account totals and the
 * shared-item pools, worked out from the saved state on every call, plus the small lookups they use
 * (which row a saved item belongs to, which raid mode a name means, the kills of each part), and the
 * one input they keep for themselves: the kill count at which On Rate started following a boss.
 * <p>
 * Reading can still change the saved state: {@code ctx.state().peek} fills in missing maps on a saved
 * boss, so these methods keep their exact order of reads. Package-private and unsynchronized:
 * reached only through {@link Tracker}, under its lock.
 */
final class Statistics
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;
	private final SharedItems sharedItems;

	Statistics(TrackerContext ctx, SharedItems sharedItems)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
		this.sharedItems = sharedItems;
	}

	/** Whether a drop counts towards this boss's totals: a unique the player didn't leave out. */
	boolean counts(BossDefinition boss, DropDefinition drop, BossProgress p)
	{
		return drop.countsTowardAny() && (p == null || p.leftOut == null || !p.leftOut.contains(BossCatalog.normalize(drop.getName())));
	}

	/** See {@link #counts(BossDefinition, DropDefinition, BossProgress)}, for the panel. */
	boolean counts(BossDefinition boss, DropDefinition drop)
	{
		return counts(boss, drop, ctx.state().peek(boss.getName()));
	}

	static DropDefinition dropNamed(BossDefinition boss, String name)
	{
		for (DropDefinition drop : boss.getDrops())
		{
			if (drop.getDisplayName().equals(name))
			{
				return drop;
			}
		}
		return null;
	}

	/**
	 * The row a saved unique belongs to: by the name the panel shows, or else by any of the row's
	 * item names, in any case ("Tome of fire (empty)" is saved under its own name).
	 */
	static DropDefinition rowOfSaved(BossDefinition boss, String name)
	{
		DropDefinition drop = dropNamed(boss, name);
		return drop != null ? drop : findDrop(boss, name);
	}

	/**
	 * The uniques one kill should give: the rates of its raid mode, boss or delve level, and
	 * your share of a team kill (pets don't scale with the team).
	 */
	double expectedFromKill(BossDefinition boss, KillEntry kill)
	{
		return perKill(boss, kill, drop -> 1);
	}

	/**
	 * What one kill should give, each unique weighted (1 to count uniques, its GE price for
	 * GP): the rates of its raid mode, boss or delve level, and your share of a team kill.
	 */
	double perKill(BossDefinition boss, KillEntry kill, java.util.function.ToDoubleFunction<DropDefinition> weight)
	{
		BossPart part = null;
		if (kill.part != null)
		{
			for (BossPart p : boss.getParts())
			{
				if (p.getName().equals(kill.part))
				{
					part = p;
				}
			}
		}
		double share = 1;
		if (boss.isDetectTeam() && kill.team != null && kill.team > 0)
		{
			share = 1.0 / kill.team;
		}
		else if (boss.getTeam() != null && kill.kc > 0)
		{
			// the share of this one kill under the solo/duo setting
			share = ctx.settings().contribution(boss.getTeam(), kill.kc) * kill.kc
				- (kill.kc > 1 ? ctx.settings().contribution(boss.getTeam(), kill.kc - 1) * (kill.kc - 1) : 0);
		}
		// a raid whose points On Rate read uses its own purple chance, like History shows
		RaidRecord record = null;
		double chance = Double.NaN;
		BossProgress progress = ctx.state().peek(boss.getName());
		if (part != null && part.getRaid() != null && kill.partKc != null && progress != null)
		{
			for (RaidRecord r : progress.raids)
			{
				if (r.kc == kill.partKc && part.getName().equals(r.part))
				{
					record = r;
				}
			}
			chance = record == null ? Double.NaN : RateModel.recordChance(record, part, ctx.settings());
		}
		double expected = 0;
		for (DropDefinition drop : boss.getDrops())
		{
			if (!counts(boss, drop, progress))
			{
				continue;
			}
			double rate;
			if (boss.isDelve() && kill.level != null)
			{
				rate = Rates.parse(drop.getLevelRate(kill.level));
			}
			else if (boss.getWaves() > 0)
			{
				rate = kill.level == null ? 0 : RateModel.waveRate(drop, kill.level);
			}
			else if (part != null && !Double.isNaN(chance) && !drop.hasPartRates())
			{
				rate = RateModel.recordDropExpected(boss, part, drop, record, chance);
			}
			else if (part != null)
			{
				rate = RateModel.partRate(boss, part, drop, ctx.settings());
			}
			else
			{
				rate = RateModel.effectiveRate(boss, drop, ctx.settings());
			}
			if (drop.getGuaranteedAt() != null && drop.getGuaranteedAt() == kill.kc)
			{
				// the kill where a player with none gets one: the typical player has none with
				// chance (1 - rate)^kc, so this kill gives rate + that, and no more pity after it
				rate += Math.pow(1 - rate, kill.kc);
			}
			// like the totals: a team shares a kill's drops, except the ones each player rolls
			expected += (drop.isNoShare() ? rate : rate * share) * weight.applyAsDouble(drop);
		}
		return expected;
	}

	/**
	 * Your share of the kills at a group boss: a kill with 4 players counts as a quarter.
	 * Kills On Rate didn't see (before it counted teams) count at the share it saw when it
	 * started counting, or as the boss's solo/duo setting says (Yama).
	 */
	static double sharedKills(BossDefinition boss, BossProgress p, int kc, RaidSettings raids)
	{
		int seen = 0;
		double share = 0;
		for (Map.Entry<Integer, Integer> e : p.teamKills.entrySet())
		{
			seen += e.getValue();
			share += e.getValue() / (double) Math.max(1, e.getKey());
		}
		int older = Math.max(0, kc - seen);
		// kills from before On Rate counted teams: a team setting when the boss has one, else
		// the share On Rate saw when it started counting (a Zalcano or Nex regular wasn't solo)
		double olderShare = boss.getTeam() != null ? raids.contribution(boss.getTeam(), older)
			: p.olderShare != null ? p.olderShare : 1.0;
		return older * olderShare + share;
	}

	/** Uniques expected so far at this boss, or NaN when it has no numbers. */
	double anyExpected(String bossName)
	{
		BossStats s = stats(bossName, ctx.settings());
		return s == null ? Double.NaN : s.getAnyRow().getExpected();
	}

	int currentKc(BossDefinition boss)
	{
		BossStats s = stats(boss.getName(), ctx.settings());
		return s == null ? 0 : s.getKillCount();
	}

	/** Remembers the kill count at which On Rate started following this boss. */
	void startTracking(BossDefinition boss)
	{
		BossProgress progress = ctx.state().get(boss.getName());
		if (progress.trackingStartKc == null)
		{
			progress.trackingStartKc = currentKc(boss);
		}
	}

	/** Average per-kill rate of a drop at this boss so far (works for raids, parts and delves). */
	double rateOf(BossDefinition boss, DropDefinition drop)
	{
		BossStats s = stats(boss.getName(), ctx.settings());
		if (s == null)
		{
			return 0;
		}
		for (RowStats row : s.getRows())
		{
			if (row.getIconItem() != null && row.getIconItem().equals(drop.getName()))
			{
				return row.getRate();
			}
		}
		return 0;
	}

	/**
	 * The part of a combined page that a chat counter or loot name belongs to, or null.
	 * When several parts match (e.g. "Corrupted Gauntlet" also contains "Gauntlet"),
	 * the longest match wins.
	 */
	static String partForName(BossDefinition boss, String text, boolean kcNames)
	{
		String t = BossCatalog.normalize(text);
		// Mode keywords ("expert", "(cm)", "hard mode"...) are the most reliable sign.
		for (BossPart part : boss.getParts())
		{
			for (String keyword : part.getKeywords())
			{
				if (t.contains(BossCatalog.normalize(keyword)))
				{
					return part.getName();
				}
			}
		}
		String best = null;
		int bestLength = -1;
		for (BossPart part : boss.getParts())
		{
			List<String> names = new ArrayList<>(kcNames ? part.getKcNames() : part.getLootNames());
			names.add(part.getName());
			for (String name : names)
			{
				String n = BossCatalog.normalize(name);
				if (t.contains(n) && n.length() > bestLength)
				{
					best = part.getName();
					bestLength = n.length();
				}
			}
		}
		return best;
	}

	/** Kill count per part of a combined page (chat count or collection log count, whichever is higher). */
	static Map<String, Integer> partKills(BossDefinition boss, BossProgress progress)
	{
		Map<String, Integer> result = new LinkedHashMap<>();
		for (BossPart part : boss.getParts())
		{
			int kc = 0;
			if (progress != null)
			{
				for (String name : part.getKcNames())
				{
					Integer v = progress.chatKc.get(BossCatalog.normalize(name));
					if (v != null)
					{
						kc = Math.max(kc, v);
					}
				}
				Integer clog = progress.clogKc.get(BossCatalog.normalize(part.getName()));
				if (clog != null)
				{
					kc = Math.max(kc, clog);
				}
			}
			result.put(part.getName(), kc);
		}
		return result;
	}

	/** True when this shared item is a pet (shared items live in the pools, not the bosses' drops). */
	boolean isPetItem(String itemName)
	{
		for (List<BossCatalog.PoolSource> sources : catalog.getPools().values())
		{
			for (BossCatalog.PoolSource src : sources)
			{
				if (src.getDrop().isPet() && src.getDrop().getName().equals(itemName))
				{
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * What the player should open to load this boss's history, or null when nothing is
	 * missing (or the boss has no collection log page we can read).
	 */
	String syncReminder(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return null;
		}
		BossProgress p = ctx.state().peek(boss.getName());
		if (boss.isDelve() && (p == null || !p.levelsSynced))
		{
			return "Open the Doom scoreboard once to load your completions for every delve level.";
		}
		if (!boss.getClogPages().isEmpty() && (p == null || !p.clogSynced))
		{
			return "Open the \"" + boss.getClogPages().get(0) + "\" page in your collection log once to load your past drops.";
		}
		return null;
	}

	static DropDefinition findDrop(BossDefinition boss, String itemName)
	{
		String key = BossCatalog.normalize(itemName);
		for (DropDefinition drop : boss.getDrops())
		{
			for (String name : drop.getAllNames())
			{
				if (BossCatalog.normalize(name).equals(key))
				{
					return drop;
				}
			}
		}
		return null;
	}

	BossStats stats(String bossName, RaidSettings raids)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return null;
		}
		BossProgress progress = ctx.state().peek(boss.getName());

		int kc = 0;
		boolean known = false;
		boolean synced = false;
		Map<Integer, Integer> levels = null;
		Map<String, Integer> parts = null;
		Map<Integer, Integer> waveRuns = null;
		if (boss.hasParts())
		{
			parts = partKills(boss, progress);
			kc = sum(parts.values());
			known = progress != null && (!progress.chatKc.isEmpty() || !progress.clogKc.isEmpty());
			synced = progress != null && progress.clogSynced;
		}
		else if (boss.isDelve())
		{
			levels = new TreeMap<>();
			for (int l = 1; l <= BossDefinition.DELVE_LEVELS; l++)
			{
				Integer n = progress == null ? null : progress.levelKc.get(l);
				levels.put(l, n == null ? 0 : n);
			}
			kc = sum(levels.values());
			known = progress != null && !progress.levelKc.isEmpty();
			synced = progress != null && progress.clogSynced;
		}
		else if (progress != null && boss.getWaves() > 0)
		{
			// claimed runs per wave; completions the log page showed that On Rate hadn't counted
			// as claims yet are from before it: they count as last-wave claims
			waveRuns = new TreeMap<>(progress.waveClaims == null ? Collections.emptyMap() : progress.waveClaims);
			int completions = combine(progress.clogKc.values(), boss.combineKcWithMax());
			int counted = progress.lastWaveClaimsAtSync == null ? 0 : progress.lastWaveClaimsAtSync;
			int older = Math.max(0, completions - counted);
			if (older > 0)
			{
				waveRuns.merge(boss.getWaves(), older, Integer::sum);
			}
			kc = sum(waveRuns.values());
			known = kc > 0 || !progress.chatKc.isEmpty() || !progress.clogKc.isEmpty();
			synced = progress.clogSynced;
		}
		else if (progress != null && boss.getRollsPerKill() > 0)
		{
			// reward rolls. Once the log page was read, its "Rewards claimed" number is the exact
			// count (big pulls at the cart or pool included), plus the rolls counted since.
			// Without it: the estimate for the ones from before (subdues times rolls per subdue,
			// minus unspent rewards), plus the ones counted since.
			Integer claimed = null;
			List<Integer> subdueCounts = new ArrayList<>();
			for (Map.Entry<String, Integer> e : progress.clogKc.entrySet())
			{
				if (e.getKey().contains("reward"))
				{
					claimed = e.getValue();
				}
				else
				{
					subdueCounts.add(e.getValue());
				}
			}
			int subdues = combine(subdueCounts, boss.combineKcWithMax());
			if (claimed != null)
			{
				int since = progress.lootKillsAtSync == null ? 0 : Math.max(0, progress.lootKills - progress.lootKillsAtSync);
				kc = claimed + since;
			}
			else
			{
				int before = progress.rollBase != null ? progress.rollBase : subdues * boss.getRollsPerKill();
				kc = before + progress.lootKills;
			}
			known = progress.rollBase != null || !progress.clogKc.isEmpty() || progress.lootKills > 0;
			synced = progress.clogSynced;
		}
		else if (progress != null)
		{
			int chat = combine(progress.chatKc.values(), boss.combineKcWithMax());
			int clog = combine(progress.clogKc.values(), boss.combineKcWithMax());
			known = !progress.chatKc.isEmpty() || !progress.clogKc.isEmpty();
			kc = known ? Math.max(chat, clog) : progress.lootKills;
			if (boss.getKcNames().isEmpty() && !progress.clogKc.isEmpty() && progress.lootKillsAtSync != null)
			{
				// no kill count message: the log count plus the kills seen since it was read
				kc = Math.max(kc, clog + Math.max(0, progress.lootKills - progress.lootKillsAtSync));
			}
			synced = progress.clogSynced;
		}

		List<RowStats> rows = new ArrayList<>();
		double anyExpected = 0;
		int anyReceived = 0;
		RowStats.Parts anyParts = new RowStats.Parts();
		// each raid mode's saved raids, added up once for all its items
		Map<String, RateModel.PartRaids> partRaids = new HashMap<>();
		if (parts != null)
		{
			for (BossPart part : boss.getParts())
			{
				partRaids.put(part.getName(), RateModel.partRaids(part, raids, parts.get(part.getName()), progress));
			}
		}
		// the boss's own uniques, then the shared items the player chose to judge per boss
		List<DropDefinition> drops = new ArrayList<>(boss.getDrops());
		Map<DropDefinition, Integer> splitReceived = new HashMap<>();
		for (Map.Entry<String, DropDefinition> e : catalog.poolsOf(boss).entrySet())
		{
			PoolSplit split = sharedItems.activeSplit(e.getKey());
			if (split != null)
			{
				drops.add(e.getValue());
				splitReceived.put(e.getValue(), sharedItems.poolSeenAt(boss.getName(), e.getKey()) + split.olderAt(boss.getName()));
			}
		}
		for (DropDefinition drop : drops)
		{
			double expected;
			double rate;
			boolean sureDropped = false;
			if (parts != null)
			{
				expected = 0;
				for (BossPart part : boss.getParts())
				{
					expected += RateModel.partExpected(boss, part, drop, raids, parts.get(part.getName()), partRaids.get(part.getName()));
				}
				rate = kc > 0 ? expected / kc : 0;
			}
			else if (waveRuns != null)
			{
				// each run counts every wave up to the one it was claimed at
				expected = 0;
				for (Map.Entry<Integer, Integer> e : waveRuns.entrySet())
				{
					expected += e.getValue() * RateModel.waveRate(drop, e.getKey());
				}
				rate = kc > 0 ? expected / kc : RateModel.effectiveRate(boss, drop, raids);
			}
			else if (levels != null)
			{
				expected = 0;
				for (Map.Entry<Integer, Integer> e : levels.entrySet())
				{
					expected += e.getValue() * Rates.parse(drop.getLevelRate(e.getKey()));
				}
				// average rate per completion so far, for the tooltip
				rate = kc > 0 ? expected / kc : 0;
			}
			else
			{
				rate = RateModel.effectiveRate(boss, drop, raids);
				if (boss.isDetectTeam() && !drop.isNoShare() && progress != null
					&& (!progress.teamKills.isEmpty() || progress.olderShare != null))
				{
					// group boss: each kill counts for your share of the team you had then
					expected = rate * sharedKills(boss, progress, kc, raids);
					rate = kc > 0 ? expected / kc : rate;
				}
				else
				{
					if (boss.getTeam() != null && !drop.isNoShare())
					{
						// team boss: your chance scales with your share of the damage
						rate *= raids.contribution(boss.getTeam(), kc);
					}
					expected = rate * kc;
				}
				Integer sure = drop.getGuaranteedAt();
				if (sure != null && kc >= sure)
				{
					// that kill was a sure drop for a player with none, but only until the first
					// head: no repeat at 100, 150... (the typical player, whatever you have)
					expected = Poisson.guaranteedExpected(kc, rate, sure);
					sureDropped = true;
				}
			}
			Integer split = splitReceived.get(drop);
			int received = split != null ? split : 0;
			// No kills here: anything in the log came from elsewhere (an occult necklace
			// from a regular smoke devil...), so it doesn't count for this boss.
			if (split == null && progress != null && kc > 0)
			{
				for (String name : drop.getAllNames())
				{
					Integer r = progress.received.get(BossCatalog.normalize(name));
					if (r != null)
					{
						received += r;
					}
				}
			}
			RowStats row = new RowStats(drop.getDisplayName(), drop.getName(), rate, expected, received, false);
			if (sureDropped)
			{
				// nobody past the guaranteed kill has none, and the count is no Poisson
				row.withParts(new RowStats.Parts().addGuaranteed(kc, rate, drop.getGuaranteedAt()));
			}
			// a split shared item has no dry streak: On Rate never saw when the older copies dropped
			if (split == null && !(drop.getMax() > 0 && received >= drop.getMax()))
			{
				Integer last = null;
				if (progress != null)
				{
					for (String name : drop.getAllNames())
					{
						Integer kcAt = progress.lastDropKc.get(BossCatalog.normalize(name));
						if (kcAt != null && (last == null || kcAt > last))
						{
							last = kcAt;
						}
					}
				}
				if (last != null)
				{
					row.withStreak(Math.max(0, kc - last), true);
				}
				else if (received == 0 && progress != null && progress.clogSynced
					&& !catalog.isClogShared(BossCatalog.normalize(drop.getName())))
				{
					row.withStreak(kc, true);
				}
				else if (progress != null && progress.trackingStartKc != null)
				{
					row.withStreak(Math.max(0, kc - progress.trackingStartKc), false);
				}
			}
			if (drop.getMax() > 0)
			{
				// The collection log counts these at most once (or twice for the two titan
				// scrolls), so for luck it's "have it or not".
				received = Math.min(received, drop.getMax());
				row = row.capped(drop.getMax(), received);
			}
			if (progress != null && progress.fastestGap != null)
			{
				Integer fastest = null;
				Integer longest = null;
				for (String name : drop.getAllNames())
				{
					Integer f = progress.fastestGap.get(BossCatalog.normalize(name));
					Integer l = progress.longestGap.get(BossCatalog.normalize(name));
					if (f != null && (fastest == null || f < fastest))
					{
						fastest = f;
					}
					if (l != null && (longest == null || l > longest))
					{
						longest = l;
					}
				}
				row.withRecords(fastest, longest);
			}
			rows.add(row);
			if (counts(boss, drop, progress))
			{
				// For once-only items the total counts the chance of having one by now.
				anyExpected += drop.getMax() > 0 ? Poisson.expectedCapped(expected, drop.getMax()) : expected;
				anyReceived += received;
				// and its luck adds up each item as it counts, not a Poisson of the total
				anyParts.add(row);
			}
		}
		RowStats any = new RowStats("Any", null, 0, anyExpected, anyReceived, true).withParts(anyParts);
		// "Any" streak: kills since the most recent unique On Rate knows of.
		double anyRate = 0;
		Integer lastAny = null;
		for (int i = 0; i < drops.size(); i++)
		{
			DropDefinition drop = drops.get(i);
			if (!counts(boss, drop, progress))
			{
				continue;
			}
			anyRate += rows.get(i).getRate();
			if (progress != null)
			{
				for (String name : drop.getAllNames())
				{
					Integer kcAt = progress.lastDropKc.get(BossCatalog.normalize(name));
					if (kcAt != null && (lastAny == null || kcAt > lastAny))
					{
						lastAny = kcAt;
					}
				}
			}
		}
		any.withRate(anyRate);
		if (lastAny != null)
		{
			any.withStreak(Math.max(0, kc - lastAny), true);
		}
		else if (anyReceived == 0 && synced)
		{
			any.withStreak(kc, true);
		}
		else if (progress != null && progress.trackingStartKc != null)
		{
			any.withStreak(Math.max(0, kc - progress.trackingStartKc), false);
		}
		double raidChance = boss.getRaid() != null ? raids.uniqueChance(boss.getRaid()) : Double.NaN;
		BossStats stats = new BossStats(boss, kc, known, synced, any, rows, raidChance, levels);
		stats.setPartCounts(parts);
		stats.setDrops(drops);
		return stats;
	}

	/**
	 * Every boss with kills or drops, added up: how dry or spooned the account is overall.
	 */
	AccountStats accountStats(RaidSettings raids)
	{
		return accountStats(raids, null);
	}

	/**
	 * Same as {@link #accountStats(RaidSettings)}, plus GE value when {@code prices} is given.
	 * Prices are per item name; untradeable items have price 0, so they count for the number
	 * of uniques but not for value.
	 */
	AccountStats accountStats(RaidSettings raids, ToLongFunction<String> prices)
	{
		List<Double> valueExpected = new ArrayList<>();
		List<Long> valuePrices = new ArrayList<>();
		double expectedValue = 0;
		long receivedValue = 0;

		List<RowStats> rows = new ArrayList<>();
		List<ItemHighlight> items = new ArrayList<>();
		Map<String, Integer> killCounts = new LinkedHashMap<>();
		double expected = 0;
		int received = 0;
		RowStats.Parts totalParts = new RowStats.Parts();
		List<Integer> valueCaps = new ArrayList<>();
		// every boss's numbers worked out once, then shared with the shared-items pass
		Map<String, BossStats> cache = new HashMap<>();
		List<RowStats> splitLeftovers = new ArrayList<>();
		List<String> unsynced = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossStats s = cachedStats(boss.getName(), raids, cache);
			RowStats any = s.getAnyRow();
			boolean skipped = s.getDrops().isEmpty() || s.getKillCount() <= 0 || syncReminder(boss.getName()) != null;
			if (skipped)
			{
				// a shared item split per boss still counts, as it did while shared
				for (int i = boss.getDrops().size(); i < s.getDrops().size(); i++)
				{
					splitLeftovers.add(s.getRows().get(i));
				}
			}
			if (s.getDrops().isEmpty() || s.getKillCount() <= 0)
			{
				continue;
			}
			if (syncReminder(boss.getName()) != null)
			{
				// kills known, drops from before On Rate not: it would only drag the account
				// towards dry, so it waits until its log page is opened
				unsynced.add(boss.getName());
				continue;
			}
			RowStats bossRow = new RowStats(boss.getName(), iconFor(boss), 0, any.getExpected(), any.getReceived(), false)
				.withRate(any.getRate()).withStreak(any.getStreak(), any.isStreakExact()).withParts(any.getParts());
			BossProgress progress = ctx.state().peek(boss.getName());
			double driest = Double.NaN;
			String driestItem = null;
			for (int i = 0; i < s.getDrops().size(); i++)
			{
				RowStats r = s.getRows().get(i);
				double m = r.getStreakMultiple();
				if (counts(boss, s.getDrops().get(i), progress) && !Double.isNaN(m) && (Double.isNaN(driest) || m > driest))
				{
					driest = m;
					driestItem = r.getLabel();
				}
			}
			rows.add(bossRow.withDriest(driest, driestItem));
			for (int i = 0; i < s.getDrops().size(); i++)
			{
				if (counts(boss, s.getDrops().get(i), progress))
				{
					items.add(new ItemHighlight(boss.getName(), s.getRows().get(i), s.getDrops().get(i).isPet()));
				}
			}
			if (prices != null)
			{
				int pieces = 0;
				long piecePrice = 0;
				for (int i = 0; i < s.getDrops().size(); i++)
				{
					DropDefinition drop = s.getDrops().get(i);
					RowStats row = s.getRows().get(i);
					if (!counts(boss, drop, progress))
					{
						continue;
					}
					long price = Math.max(0, prices.applyAsLong(drop.getName()));
					if (price <= 0)
					{
						continue;
					}
					valueExpected.add(row.getExpected());
					valuePrices.add(price);
					valueCaps.add(row.getCap());
					expectedValue += Poisson.expectedCounted(row.getExpected(), row.getCap()) * price;
					if (Untradeables.isHalberdPiece(drop.getName()))
					{
						pieces += row.getReceived();
						piecePrice = price;
					}
					else
					{
						receivedValue += row.getReceived() * price;
					}
				}
				receivedValue += Untradeables.setValue(pieces, piecePrice);
			}
			killCounts.put(boss.getName(), s.getKillCount());
			expected += any.getExpected();
			received += any.getReceived();
			totalParts.add(any);
		}
		// Items several bosses drop (dragon pickaxe, onyx...) have no row of their own here (they
		// show on the page of each boss that drops them), but they count in the account verdict
		// and GE value once On Rate knows the kills of every boss that drops them.
		for (RowStats row : splitLeftovers)
		{
			expected += row.getCap() > 0 ? Poisson.expectedCapped(row.getExpected(), row.getCap()) : row.getExpected();
			received += row.getReceived();
			totalParts.add(row);
			long price = prices == null ? 0 : Math.max(0, prices.applyAsLong(row.getIconItem()));
			if (price > 0)
			{
				valueExpected.add(row.getExpected());
				valuePrices.add(price);
				valueCaps.add(row.getCap());
				expectedValue += Poisson.expectedCounted(row.getExpected(), row.getCap()) * price;
				receivedValue += row.getReceived() * price;
			}
		}
		for (SharedPool pool : sharedPools(raids, cache))
		{
			if (isPetItem(pool.getItemName()) && pool.isLogKnown() && pool.getExpected() > 0)
			{
				// a pet several bosses drop (the Chaos elemental's): one entry in the Items tab's
				// pets, judged on all those bosses together, "have it or not" like every pet
				items.add(new ItemHighlight(null, pool.toRow(), true));
			}
			if (!pool.isComplete())
			{
				continue;
			}
			RowStats poolRow = pool.toRow();
			expected += pool.getCountedExpected();
			received += poolRow.getReceived();
			totalParts.add(poolRow);
			long price = prices == null ? 0 : Math.max(0, prices.applyAsLong(pool.getItemName()));
			if (price > 0)
			{
				valueExpected.add(pool.getExpected());
				valuePrices.add(price);
				valueCaps.add(poolRow.getCap());
				expectedValue += pool.getCountedExpected() * price;
				receivedValue += poolRow.getReceived() * price;
			}
		}
		rows.sort((a, b) -> Double.compare(b.getExpected(), a.getExpected()));
		RowStats total = new RowStats("All", null, 0, expected, received, true).withParts(totalParts);
		AccountStats account = new AccountStats(total, rows, killCounts);
		account.setUnsynced(unsynced);
		account.setItems(items);
		if (prices != null)
		{
			double[] lambdas = new double[valueExpected.size()];
			long[] itemPrices = new long[valuePrices.size()];
			int[] itemCaps = new int[valueCaps.size()];
			for (int i = 0; i < lambdas.length; i++)
			{
				lambdas[i] = valueExpected.get(i);
				itemPrices[i] = valuePrices.get(i);
				itemCaps[i] = valueCaps.get(i);
			}
			double luck = ValueLuck.of(lambdas, itemPrices, itemCaps, receivedValue, 5000, 1234567L);
			account.setValue(expectedValue, receivedValue, luck);
		}
		return account;
	}

	/**
	 * Every item several bosses drop. Complete when the log total is known and every boss
	 * that drops it has a known kill count; otherwise lists the pages still to open.
	 */
	List<SharedPool> sharedPools(RaidSettings raids)
	{
		return sharedPools(raids, new HashMap<>());
	}

	/** The shared items this boss drops (with every boss that drops them), for its page. */
	List<SharedPool> sharedPoolsFor(String bossName, RaidSettings raids)
	{
		List<SharedPool> result = new ArrayList<>();
		for (SharedPool pool : sharedPools(raids))
		{
			if (pool.getBosses().contains(bossName))
			{
				result.add(pool);
			}
		}
		return result;
	}

	BossStats cachedStats(String bossName, RaidSettings raids, Map<String, BossStats> cache)
	{
		BossStats s = cache.get(bossName);
		if (s == null)
		{
			s = stats(bossName, raids);
			cache.put(bossName, s);
		}
		return s;
	}

	List<SharedPool> sharedPools(RaidSettings raids, Map<String, BossStats> cache)
	{
		List<SharedPool> result = new ArrayList<>();
		for (Map.Entry<String, List<BossCatalog.PoolSource>> e : catalog.getPools().entrySet())
		{
			if (sharedItems.activeSplit(e.getKey()) != null)
			{
				// judged on each boss separately: those copies are rows on the bosses' own pages
				continue;
			}
			Integer logTotal = ctx.state().sharedClog().get(e.getKey());
			double expected = 0;
			boolean once = false;
			String label = null;
			String itemName = null;
			String alsoFrom = null;
			List<String> bosses = new ArrayList<>();
			List<String> missing = new ArrayList<>();
			for (BossCatalog.PoolSource src : e.getValue())
			{
				BossDefinition boss = src.getBoss();
				DropDefinition drop = src.getDrop();
				if (label == null)
				{
					label = drop.getDisplayName();
					itemName = drop.getName();
				}
				if (alsoFrom == null)
				{
					alsoFrom = drop.getAlsoFrom();
				}
				once |= drop.isOnce();
				bosses.add(boss.getName());
				BossStats s = cachedStats(boss.getName(), raids, cache);
				BossProgress p = ctx.state().peek(boss.getName());
				boolean kcKnown = s != null && (s.isKillCountKnown() || s.isClogSynced()
					|| (p != null && p.lootKills > 0 && boss.getKcNames().isEmpty()));
				if (!kcKnown)
				{
					String page = boss.getClogPages().isEmpty() ? boss.getName() : boss.getClogPages().get(0);
					if (!missing.contains(page))
					{
						missing.add(page);
					}
				}
				if (s != null)
				{
					expected += RateModel.poolExpected(boss, drop, s, raids);
				}
			}
			if (logTotal == null && missing.isEmpty())
			{
				// every kill count is known, but no page showing this item was opened yet
				BossDefinition first = e.getValue().get(0).getBoss();
				missing.add(first.getClogPages().isEmpty() ? first.getName() : first.getClogPages().get(0));
			}
			int got = logTotal == null ? 0 : logTotal;
			if (once)
			{
				got = Math.min(got, 1);
			}
			result.add(new SharedPool(label, itemName, expected, got, bosses, missing).withLogKnown(logTotal != null).withOnce(once)
				.withAlsoFrom(alsoFrom).withKey(e.getKey())
				.withSplit(sharedItems.canSplit(e.getKey()), sharedItems.olderCopies(e.getKey()), ctx.state().poolSplits().containsKey(e.getKey())));
		}
		return result;
	}

	/** The item that stands for a boss in the overview: its pet, or else its first unique. */
	static String iconFor(BossDefinition boss)
	{
		for (DropDefinition drop : boss.getDrops())
		{
			if (drop.isPet())
			{
				return drop.getName();
			}
		}
		return boss.getDrops().isEmpty() ? null : boss.getDrops().get(0).getName();
	}

	static int sum(Collection<Integer> values)
	{
		int total = 0;
		for (int v : values)
		{
			total += v;
		}
		return total;
	}

	static int combine(Collection<Integer> values, boolean max)
	{
		int result = 0;
		for (int v : values)
		{
			result = max ? Math.max(result, v) : result + v;
		}
		return result;
	}
}
