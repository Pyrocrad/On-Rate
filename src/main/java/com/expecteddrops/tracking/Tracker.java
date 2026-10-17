package com.expecteddrops.tracking;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns game events into kill counts and received counts, and works out the expected
 * numbers. Has no RuneLite dependencies so it can be unit tested; all methods are
 * synchronized because events arrive on the client thread and the panel reads on Swing.
 */
public class Tracker
{
	/** Same pattern RuneLite's chat commands plugin uses for kill count messages. */
	static final Pattern KILLCOUNT_PATTERN = Pattern.compile("Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?(?:<col=[0-9a-f]{6}>|@.+?@)(?<kc>[0-9,]+)</col>");
	private static final Pattern NEW_CLOG_ITEM = Pattern.compile("New item added to your collection log: (.+)");
	/** Completion message for a delve level, e.g. "Delve level: 3 duration: 1:52.20" ("8+" for deep delves). */
	private static final Pattern DELVE_LEVEL = Pattern.compile("Delve level: ?(\\d+\\+?)");
	private static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
	};
	private static final long PET_WINDOW_MILLIS = 60_000;

	/** An item from a loot event, already resolved to its name. */
	public static class LootItem
	{
		final String name;
		final int quantity;

		public LootItem(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	private final BossCatalog catalog;
	private TrackerState state = new TrackerState();
	private String lastBoss;
	private long lastKillMillis;

	public Tracker(BossCatalog catalog)
	{
		this.catalog = catalog;
	}

	public BossCatalog getCatalog()
	{
		return catalog;
	}

	public synchronized void setState(TrackerState state)
	{
		this.state = state == null ? new TrackerState() : state;
		lastBoss = null;
	}

	public synchronized TrackerState getState()
	{
		return state;
	}

	/** Name of the boss most recently killed this session, or null. */
	public synchronized String getLastBoss()
	{
		return lastBoss;
	}

	/**
	 * Handles a game chat message. Returns the boss whose numbers changed, or null.
	 */
	public synchronized String onChatMessage(String message, long nowMillis)
	{
		if (message == null)
		{
			return null;
		}

		Matcher m = KILLCOUNT_PATTERN.matcher(message);
		if (m.find())
		{
			String pre = m.group("pre");
			String post = m.group("post");
			if ((pre == null || pre.isEmpty()) && (post == null || post.isEmpty()))
			{
				return null;
			}
			String counter = m.group("boss").replaceAll("<[^>]*>", "").trim();
			BossDefinition boss = catalog.byKcName(counter);
			if (boss == null)
			{
				return null;
			}
			int kc = Integer.parseInt(m.group("kc").replace(",", ""));
			state.get(boss.getName()).chatKc.put(BossCatalog.normalize(counter), kc);
			markKill(boss, nowMillis);
			return boss.getName();
		}

		String plain = message.replaceAll("<[^>]*>", "");
		Matcher delve = DELVE_LEVEL.matcher(plain);
		if (plain.contains("duration:") && delve.find())
		{
			String level = delve.group(1);
			return onDelveLevel(level.endsWith("+") ? BossDefinition.DELVE_LEVELS : Integer.parseInt(level), nowMillis);
		}

		Matcher clog = NEW_CLOG_ITEM.matcher(plain);
		if (clog.find())
		{
			return onNewClogItem(clog.group(1).trim(), nowMillis);
		}

		for (String petMessage : PET_MESSAGES)
		{
			if (message.contains(petMessage))
			{
				return onPet(nowMillis);
			}
		}
		return null;
	}

	private BossDefinition delveBoss()
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			if (boss.isDelve())
			{
				return boss;
			}
		}
		return null;
	}

	private String onDelveLevel(int level, long nowMillis)
	{
		BossDefinition boss = delveBoss();
		if (boss == null || level < 1 || level > BossDefinition.DELVE_LEVELS)
		{
			return null;
		}
		state.get(boss.getName()).levelKc.merge(level, 1, Integer::sum);
		markKill(boss, nowMillis);
		return boss.getName();
	}

	/**
	 * Handles the Doom scoreboard, which lists completions per delve level
	 * (key 9 = "8+"). These are full totals, so they replace what we counted.
	 */
	public synchronized String onDelveScoreboard(Map<Integer, Integer> levels)
	{
		BossDefinition boss = delveBoss();
		if (boss == null || levels.isEmpty())
		{
			return null;
		}
		BossProgress progress = state.get(boss.getName());
		progress.levelsSynced = true;
		for (Map.Entry<Integer, Integer> e : levels.entrySet())
		{
			if (e.getKey() >= 1 && e.getKey() <= BossDefinition.DELVE_LEVELS)
			{
				progress.levelKc.put(e.getKey(), e.getValue());
			}
		}
		return boss.getName();
	}

	/**
	 * For bosses whose loot the loot tracker can't see (Gauntlet, Jad, Zuk), a first-time
	 * collection log message is the only sign of a drop. Pets are skipped because the pet
	 * message already counts them.
	 */
	private String onNewClogItem(String itemName, long nowMillis)
	{
		if (lastBoss == null || nowMillis - lastKillMillis > PET_WINDOW_MILLIS)
		{
			return null;
		}
		BossDefinition boss = catalog.byName(lastBoss);
		if (boss == null || !boss.getLootNames().isEmpty())
		{
			return null;
		}
		DropDefinition drop = findDrop(boss, itemName);
		if (drop == null || drop.isPet())
		{
			return null;
		}
		state.get(boss.getName()).received.merge(BossCatalog.normalize(itemName), 1, Integer::sum);
		return boss.getName();
	}

	private String onPet(long nowMillis)
	{
		if (lastBoss == null || nowMillis - lastKillMillis > PET_WINDOW_MILLIS)
		{
			return null;
		}
		BossDefinition boss = catalog.byName(lastBoss);
		if (boss == null)
		{
			return null;
		}
		for (DropDefinition drop : boss.getDrops())
		{
			if (drop.isPet())
			{
				state.get(boss.getName()).received.merge(BossCatalog.normalize(drop.getName()), 1, Integer::sum);
				return boss.getName();
			}
		}
		return null;
	}

	/**
	 * Handles a loot tracker event. Returns the boss whose numbers changed, or null.
	 */
	public synchronized String onLoot(String source, int amount, Collection<LootItem> items, long nowMillis)
	{
		BossDefinition boss = catalog.byLootName(source);
		if (boss == null)
		{
			return null;
		}
		BossProgress progress = state.get(boss.getName());
		progress.lootKills += Math.max(1, amount);

		for (LootItem item : items)
		{
			DropDefinition drop = findDrop(boss, item.name);
			if (drop != null && item.quantity > 0)
			{
				// One stack is one drop; the collection log counts quantity, but for
				// the uniques we track those are the same thing.
				progress.received.merge(BossCatalog.normalize(item.name), 1, Integer::sum);
			}
		}
		markKill(boss, nowMillis);
		return boss.getName();
	}

	/**
	 * Handles an opened collection log page.
	 *
	 * @param pageTitle page title, e.g. "Vorkath"
	 * @param counters  header lines like "Vorkath kills: 123", tags already removed
	 * @param items     every item on the page with its obtained quantity (0 if not obtained)
	 * @return bosses whose numbers changed
	 */
	public synchronized Set<String> onClogPage(String pageTitle, List<String> counters, Map<String, Integer> items)
	{
		Set<String> changed = new LinkedHashSet<>();
		List<BossDefinition> bosses = catalog.byClogPage(pageTitle);
		for (BossDefinition boss : bosses)
		{
			BossProgress progress = state.get(boss.getName());

			// Kill counts: use the counters that name this boss; if none do and the page
			// belongs to this boss alone, use all of them.
			List<String[]> parsed = new ArrayList<>();
			for (String line : counters)
			{
				int colon = line.lastIndexOf(':');
				if (colon < 0)
				{
					continue;
				}
				String label = line.substring(0, colon).trim();
				String value = line.substring(colon + 1).trim().replace(",", "");
				if (!value.matches("\\d+"))
				{
					continue;
				}
				parsed.add(new String[]{label, value});
			}
			List<String[]> matching = new ArrayList<>();
			for (String[] p : parsed)
			{
				if (labelNamesBoss(p[0], boss))
				{
					matching.add(p);
				}
			}
			if (matching.isEmpty() && bosses.size() == 1)
			{
				matching = parsed;
			}
			if (!matching.isEmpty())
			{
				progress.clogKc.clear();
				for (String[] p : matching)
				{
					progress.clogKc.put(BossCatalog.normalize(p[0]), Integer.parseInt(p[1]));
				}
			}

			// Received counts: the collection log is the full history, so it replaces
			// what we have, except for items several bosses share.
			for (DropDefinition drop : boss.getDrops())
			{
				for (String name : drop.getAllNames())
				{
					String key = BossCatalog.normalize(name);
					if (catalog.isClogShared(key))
					{
						continue;
					}
					Integer qty = findQuantity(items, key);
					if (qty != null)
					{
						progress.received.put(key, qty);
					}
				}
			}
			progress.clogSynced = true;
			changed.add(boss.getName());
		}
		return changed;
	}

	private static Integer findQuantity(Map<String, Integer> items, String normalizedName)
	{
		for (Map.Entry<String, Integer> e : items.entrySet())
		{
			if (BossCatalog.normalize(e.getKey()).equals(normalizedName))
			{
				return e.getValue();
			}
		}
		return null;
	}

	private static boolean labelNamesBoss(String label, BossDefinition boss)
	{
		String l = BossCatalog.normalize(label);
		if (l.contains(BossCatalog.normalize(boss.getName())))
		{
			return true;
		}
		for (String kc : boss.getKcNames())
		{
			if (l.contains(BossCatalog.normalize(kc)))
			{
				return true;
			}
		}
		return false;
	}

	/** The boss with the highest known kill count, used to pick what to show on login. */
	public synchronized String bossWithMostKills()
	{
		String best = null;
		int bestKc = 0;
		if (state.bosses == null)
		{
			return null;
		}
		for (String name : state.bosses.keySet())
		{
			BossDefinition boss = catalog.byName(name);
			if (boss == null)
			{
				continue;
			}
			BossProgress p = state.peek(name);
			int kc = boss.isDelve() ? sum(p.levelKc.values()) : Math.max(p.lootKills, Math.max(combine(p.chatKc.values(), boss.combineKcWithMax()),
				combine(p.clogKc.values(), boss.combineKcWithMax())));
			if (kc > bestKc)
			{
				bestKc = kc;
				best = boss.getName();
			}
		}
		return best;
	}

	/** Label used in the checklist and reminders for the Doom scoreboard. */
	public static final String DELVE_SCOREBOARD = "Doom scoreboard";

	/**
	 * The setup checklist: every collection log page the plugin can read, in alphabetical
	 * order, plus the Doom scoreboard. A page counts as synced once every boss on it is.
	 */
	public synchronized List<SyncStatus> syncChecklist()
	{
		Map<String, List<String>> bossesByPage = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		Map<String, Boolean> syncedByPage = new LinkedHashMap<>();
		List<SyncStatus> result = new ArrayList<>();
		for (BossDefinition boss : catalog.getBosses())
		{
			BossProgress p = state.peek(boss.getName());
			for (String page : boss.getClogPages())
			{
				bossesByPage.computeIfAbsent(page, k -> new ArrayList<>()).add(boss.getName());
				boolean synced = p != null && p.clogSynced;
				syncedByPage.merge(page.toLowerCase(Locale.ROOT), synced, Boolean::logicalAnd);
			}
			if (boss.isDelve())
			{
				result.add(new SyncStatus(DELVE_SCOREBOARD, Collections.singletonList(boss.getName()),
					p != null && p.levelsSynced));
			}
		}
		List<SyncStatus> pages = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : bossesByPage.entrySet())
		{
			pages.add(new SyncStatus(e.getKey(), e.getValue(),
				syncedByPage.getOrDefault(e.getKey().toLowerCase(Locale.ROOT), false)));
		}
		pages.addAll(result);
		return pages;
	}

	/**
	 * What the player should open to load this boss's history, or null when nothing is
	 * missing (or the boss has no collection log page we can read).
	 */
	public synchronized String syncReminder(String bossName)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return null;
		}
		BossProgress p = state.peek(boss.getName());
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

	public synchronized void reset(String bossName)
	{
		if (state.bosses != null)
		{
			state.bosses.remove(bossName);
		}
	}

	private void markKill(BossDefinition boss, long nowMillis)
	{
		lastBoss = boss.getName();
		lastKillMillis = nowMillis;
	}

	private static DropDefinition findDrop(BossDefinition boss, String itemName)
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

	/** Per-kill rate for a drop, taking raid settings into account. */
	public static double effectiveRate(BossDefinition boss, DropDefinition drop, RaidSettings raids)
	{
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
		return rate;
	}

	public synchronized BossStats stats(String bossName, RaidSettings raids)
	{
		BossDefinition boss = catalog.byName(bossName);
		if (boss == null)
		{
			return null;
		}
		BossProgress progress = state.peek(boss.getName());

		int kc = 0;
		boolean known = false;
		boolean synced = false;
		Map<Integer, Integer> levels = null;
		if (boss.isDelve())
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
		else if (progress != null)
		{
			int chat = combine(progress.chatKc.values(), boss.combineKcWithMax());
			int clog = combine(progress.clogKc.values(), boss.combineKcWithMax());
			known = !progress.chatKc.isEmpty() || !progress.clogKc.isEmpty();
			kc = known ? Math.max(chat, clog) : progress.lootKills;
			synced = progress.clogSynced;
		}

		List<RowStats> rows = new ArrayList<>();
		double anyExpected = 0;
		int anyReceived = 0;
		for (DropDefinition drop : boss.getDrops())
		{
			double expected;
			double rate;
			if (levels != null)
			{
				expected = 0;
				for (Map.Entry<Integer, Integer> e : levels.entrySet())
				{
					expected += e.getValue() * DropMath.parseRate(drop.getLevelRate(e.getKey()));
				}
				// average rate per completion so far, for the tooltip
				rate = kc > 0 ? expected / kc : 0;
			}
			else
			{
				rate = effectiveRate(boss, drop, raids);
				expected = rate * kc;
			}
			int received = 0;
			if (progress != null)
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
			rows.add(new RowStats(drop.getDisplayName(), drop.getName(), rate, expected, received, false));
			if (drop.countsTowardAny())
			{
				anyExpected += expected;
				anyReceived += received;
			}
		}
		RowStats any = new RowStats("Any", null, 0, anyExpected, anyReceived, true);
		double raidChance = boss.getRaid() != null ? raids.uniqueChance(boss.getRaid()) : Double.NaN;
		return new BossStats(boss, kc, known, synced, any, rows, raidChance, levels);
	}

	private static int sum(Collection<Integer> values)
	{
		int total = 0;
		for (int v : values)
		{
			total += v;
		}
		return total;
	}

	private static int combine(Collection<Integer> values, boolean max)
	{
		int result = 0;
		for (int v : values)
		{
			result = max ? Math.max(result, v) : result + v;
		}
		return result;
	}
}
