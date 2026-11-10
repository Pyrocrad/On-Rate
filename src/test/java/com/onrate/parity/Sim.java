package com.onrate.parity;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.BossPart;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.math.Rates;
import com.onrate.parity.Side.Loot;
import com.onrate.parity.Side.Raid;
import com.onrate.parity.Side.Settings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Plays a plausible player: kills at bosses with drops rolled from the real drop rates, raids with
 * points, delves, colosseum runs, reward rolls, collection log visits, and the odd manual
 * correction. The output is a script of operations that is replayed on both implementations.
 */
final class Sim
{
	/** One step of the script. */
	static final class Op
	{
		final String method;
		final Object[] args;

		Op(String method, Object... args)
		{
			this.method = method;
			this.args = args;
		}

		@Override
		public String toString()
		{
			return method + Arrays.deepToString(args);
		}
	}

	static final long BASE = 1_700_000_000_000L;
	static final String PET_MESSAGE = "You have a funny feeling like you're being followed.";

	final Random r;
	final BossCatalog catalog;
	final List<Op> ops = new ArrayList<>();
	/** Names of bosses touched, for the observations. */
	final List<String> touched = new ArrayList<>();
	Settings settings = new Settings();
	long t = BASE;
	/** Items received per boss page, for the collection log pages. */
	private final Map<String, Map<String, Integer>> got = new LinkedHashMap<>();
	private final Map<String, Map<String, Integer>> counters = new LinkedHashMap<>();

	Sim(long seed, BossCatalog catalog)
	{
		this.r = new Random(seed);
		this.catalog = catalog;
	}

	private void op(String method, Object... args)
	{
		ops.add(new Op(method, args));
	}

	private void tick(int seconds)
	{
		t += seconds * 1000L + r.nextInt(900);
	}

	private static String commas(int n)
	{
		return String.format(Locale.ROOT, "%,d", n);
	}

	private void kcMessage(String name, int kc)
	{
		String[] styles = {"Your %s kill count is: <col=ff0000>%s</col>.", "Your completed %s count is: <col=ff0000>%s</col>.",
			"Your completion count for %s is: <col=ff0000>%s</col>.", "Your %s completion count is: <col=ff0000>%s</col>."};
		op("onChatMessage", String.format(Locale.ROOT, styles[r.nextInt(styles.length)], name, commas(kc)), t);
	}

	/** Items one kill gives: each drop rolled at (boosted) rate so a short run still sees some. */
	private List<Loot> rollLoot(BossDefinition boss, BossPart part, double boost)
	{
		List<Loot> items = new ArrayList<>();
		for (DropDefinition d : boss.getDrops())
		{
			double rate;
			if (d.hasPartRates() && part != null)
			{
				rate = Rates.parse(d.getPartRate(part.getName()));
			}
			else if (d.getRate() != null)
			{
				rate = Rates.parse(d.getRate());
			}
			else
			{
				rate = 1 / 400.0;
			}
			if (r.nextDouble() < Math.min(0.35, rate * boost))
			{
				List<String> names = d.getAllNames();
				items.add(new Loot(names.get(r.nextInt(names.size())), 1));
			}
		}
		// items several bosses share are kept apart from the boss's own drops by the catalogue: roll them too
		for (DropDefinition d : catalog.poolsOf(boss).values())
		{
			double rate = d.getRate() != null ? Rates.parse(d.getRate())
				: part != null && d.hasPartRates() ? Rates.parse(d.getPartRate(part.getName())) : 1 / 400.0;
			if (r.nextDouble() < Math.min(0.2, rate * boost))
			{
				items.add(new Loot(d.getName(), 1));
			}
		}
		if (r.nextInt(3) == 0)
		{
			items.add(new Loot("Bones", 1 + r.nextInt(3)));
		}
		return items;
	}

	private void count(String page, List<Loot> items)
	{
		Map<String, Integer> m = got.computeIfAbsent(page, k -> new LinkedHashMap<>());
		for (Loot l : items)
		{
			if (!"Bones".equals(l.name))
			{
				m.merge(l.name, 1, Integer::sum);
			}
		}
	}

	private void maybeTime(boolean raid)
	{
		if (!raid && r.nextInt(6) == 0)
		{
			op("onTimeMessage", String.format(Locale.ROOT, "Fight duration: <col=ff0000>%d:%02d.40</col>.", 1 + r.nextInt(3), r.nextInt(60)),
				r.nextBoolean() ? t - 200 : t + 500);
			return;
		}
		if (r.nextInt(3) == 0)
		{
			int m = 1 + r.nextInt(raid ? 40 : 4);
			op("onChatMessage", String.format(Locale.ROOT, raid ? "Challenge duration: <col=ff0000>%d:%02d.40</col>." : "Fight duration: <col=ff0000>%d:%02d.20</col>.",
				m, r.nextInt(60)), t + 100);
		}
	}

	/** Plays one boss for a number of kills. */
	void playBoss(BossDefinition boss)
	{
		touched.add(boss.getName());
		int n = 12 + r.nextInt(70);
		if (boss.isDelve())
		{
			playDelve(boss, n);
		}
		else if (boss.getWaves() > 0)
		{
			playWaves(boss, n);
		}
		else if (boss.getRollsPerKill() > 0)
		{
			playRolls(boss, n);
		}
		else if (boss.getRaid() != null)
		{
			playRaid(boss, Math.max(4, n / 4));
		}
		else
		{
			playKills(boss, n);
		}
		// a collection log visit: counters and what the log shows
		if (r.nextInt(4) != 0 && !boss.getClogPages().isEmpty())
		{
			visitLog(boss);
		}
		op("OBSERVE");
	}

	private void playKills(BossDefinition boss, int n)
	{
		int[] kc = new int[Math.max(1, boss.getParts().size())];
		for (int i = 0; i < n; i++)
		{
			tick(20 + r.nextInt(60));
			BossPart part = boss.hasParts() ? boss.getParts().get(r.nextInt(boss.getParts().size())) : null;
			int idx = part == null ? 0 : boss.getParts().indexOf(part);
			kc[idx]++;
			List<String> kcNames = part != null ? part.getKcNames() : boss.getKcNames();
			List<String> lootNames = part != null ? part.getLootNames() : boss.getLootNames();
			if (boss.isDetectTeam() && r.nextInt(4) == 0)
			{
				op("SET_TEAM", 1 + r.nextInt(6));
			}
			List<Loot> items = rollLoot(boss, part, 6);
			counters.computeIfAbsent(boss.getName(), k -> new LinkedHashMap<>()).merge(kcNames.isEmpty() ? "?" : kcNames.get(0), 1, Integer::sum);
			if (!kcNames.isEmpty())
			{
				kcMessage(kcNames.get(r.nextInt(kcNames.size())), kc[idx]);
			}
			maybeTime(false);
			if (!lootNames.isEmpty())
			{
				if (!boss.getMinionLoot().isEmpty() && r.nextInt(3) == 0)
				{
					op("onLoot", boss.getMinionLoot().get(0), 1, Collections.singletonList(new Loot("Bones", 1)), t + 250);
				}
				op("onLoot", lootNames.get(0), 1, items, t + 300);
			}
			count(boss.getName(), items);
			for (Loot l : items)
			{
				DropDefinition d = find(boss, l.name);
				if (d != null && d.isPet())
				{
					op("onChatMessage", PET_MESSAGE, t + 400);
				}
			}
			if (r.nextInt(25) == 0)
			{
				// a shared item from a monster that isn't a boss
				op("onOtherLoot", "Blue dragon", Collections.singletonList(new Loot(r.nextBoolean() ? "Draconic visage" : "Dragon chainbody", 1)));
			}
		}
	}

	private static DropDefinition find(BossDefinition boss, String item)
	{
		for (DropDefinition d : boss.getDrops())
		{
			if (d.getAllNames().contains(item))
			{
				return d;
			}
		}
		return null;
	}

	private void playRaid(BossDefinition boss, int raids)
	{
		int[] kc = new int[boss.getParts().size()];
		for (int i = 0; i < raids; i++)
		{
			tick(1500 + r.nextInt(1500));
			int p = r.nextInt(boss.getParts().size());
			BossPart part = boss.getParts().get(p);
			kc[p]++;
			boolean purple = r.nextInt(5) == 0;
			List<Loot> items = purple ? rollLoot(boss, part, 40) : new ArrayList<>();
			Raid record = null;
			switch (boss.getRaid())
			{
				case "cox":
					int team = 1 + r.nextInt(6);
					int mine = 12_000 + r.nextInt(40_000);
					record = new Raid("cox", mine, mine + (team - 1) * (10_000 + r.nextInt(30_000)), team);
					break;
				case "toa":
					int size = 1 + r.nextInt(4);
					int level = new int[]{0, 150, 250, 300, 350, 420, 500}[r.nextInt(7)];
					record = r.nextBoolean() ? new Raid("toaExact", 4_000 + r.nextInt(30_000), size, level)
						: new Raid("toa", 4_000 + r.nextInt(20_000), r.nextInt(8_000), r.nextBoolean() ? 0 : 30_000 + r.nextInt(30_000), size, level);
					break;
				case "tob":
					record = new Raid("tob", 1 + r.nextInt(5), r.nextInt(3), r.nextInt(4), r.nextBoolean() ? Boolean.TRUE : Boolean.FALSE);
					break;
				case "fractured":
					record = new Raid("fractured", 1 + r.nextInt(6), r.nextInt(3), r.nextBoolean() ? -1.0 : 80 + r.nextInt(500));
					break;
				default:
					break;
			}
			String name = part.getKcNames().get(0);
			boolean pointsFirst = r.nextBoolean();
			if ("toa".equals(boss.getRaid()) && r.nextInt(4) == 0)
			{
				// the Tombs of Amascut plugin's party total can come before the raid's own data
				op("onPartyPoints", 3_000 + r.nextInt(30_000), 40_000 + r.nextInt(40_000), t - 400);
			}
			if (record != null && pointsFirst)
			{
				op("onRaidRecord", record, t);
			}
			op("onChatMessage", String.format(Locale.ROOT, "Your completed %s count is: <col=ff0000>%s</col>.", name, commas(kc[p])), t + 800);
			if (record != null && !pointsFirst)
			{
				op("onRaidRecord", record, t + 1500);
			}
			maybeTime(true);
			raidExtras(boss, part, record, t);
			if (!boss.getLootNames().isEmpty())
			{
				op("onLoot", boss.getLootNames().get(0), 1, items, t + 2500);
			}
			count(boss.getName(), items);
			for (Loot l : items)
			{
				DropDefinition d = find(boss, l.name);
				if (d != null && d.isPet())
				{
					op("onChatMessage", PET_MESSAGE, t + 3000);
				}
			}
		}
	}

	/**
	 * What can follow a raid's completion: the exact Tombs points or the party total (after the raid data),
	 * a time-only message before or after the completion count, and times imported from another plugin.
	 */
	private void raidExtras(BossDefinition boss, BossPart part, Raid record, long at)
	{
		if ("toa".equals(boss.getRaid()))
		{
			switch (r.nextInt(4))
			{
				case 0:
					op("onToaPoints", 2_000 + r.nextInt(40_000), at + 2_000 + r.nextInt(3_000));
					break;
				case 1:
					op("onPartyPoints", 3_000 + r.nextInt(30_000), 40_000 + r.nextInt(40_000), at + 2_000 + r.nextInt(60_000));
					break;
				case 2:
					// too late to belong to this raid
					op("onToaPoints", 2_000 + r.nextInt(40_000), at + 3 * 60_000);
					break;
				default:
					break;
			}
		}
		if (r.nextInt(3) == 0)
		{
			int minutes = 10 + r.nextInt(40);
			String text = "cox".equals(boss.getRaid())
				? String.format(Locale.ROOT, "Congratulations - your raid is complete! Team size: 3 players Duration: %d:%02d.20", minutes, r.nextInt(60))
				: String.format(Locale.ROOT, "%s total completion time: %d:%02d.40", boss.getName(), minutes, r.nextInt(60));
			// time-only messages come either side of the completion count
			op("onTimeMessage", text, r.nextBoolean() ? at + 300 : at + 1_200);
		}
		if (r.nextInt(5) == 0)
		{
			List<long[]> samples = new ArrayList<>();
			// one that is this raid (ends within a few minutes of it), one older, one repeated
			samples.add(new long[]{at + 800 + r.nextInt(120_000), (15 + r.nextInt(30)) * 60_000L});
			long old = at - (2 + r.nextInt(30)) * 86_400_000L;
			samples.add(new long[]{old, (20 + r.nextInt(30)) * 60_000L});
			samples.add(new long[]{old, (20 + r.nextInt(30)) * 60_000L});
			op("importRaidTimes", boss.getName(), part.getName(), samples);
		}
	}

	private void playDelve(BossDefinition boss, int n)
	{
		Map<Integer, Integer> totals = new LinkedHashMap<>();
		for (int i = 0; i < n; i++)
		{
			tick(120 + r.nextInt(200));
			int level = 1 + r.nextInt(9);
			totals.merge(level, 1, Integer::sum);
			String shown = level >= 9 ? "8+ (" + (9 + r.nextInt(6)) + ")" : String.valueOf(level);
			op("onChatMessage", "Delve level: " + shown + " duration: <col=ff0000>" + (1 + r.nextInt(3)) + ":" + String.format("%02d", r.nextInt(60)) + ".20</col>", t);
			List<Loot> items = rollLoot(boss, null, 8);
			op("onLoot", boss.getLootNames().get(0), 1, items, t + 400);
			count(boss.getName(), items);
		}
		if (r.nextBoolean())
		{
			Map<Integer, Integer> scoreboard = new LinkedHashMap<>();
			for (Map.Entry<Integer, Integer> e : totals.entrySet())
			{
				scoreboard.put(e.getKey(), e.getValue() + r.nextInt(3));
			}
			op("onDelveScoreboard", scoreboard);
		}
	}

	private void playWaves(BossDefinition boss, int n)
	{
		for (int i = 0; i < Math.max(4, n / 4); i++)
		{
			tick(600 + r.nextInt(600));
			int wave = 4 + r.nextInt(boss.getWaves() - 3);
			for (int w = 1; w <= wave; w++)
			{
				op("onWaveCompleted", w);
			}
			List<Loot> items = rollLoot(boss, null, 60);
			if (wave == boss.getWaves())
			{
				items.add(new Loot("Dizana's quiver (uncharged)", 1));
			}
			op("onLoot", boss.getLootNames().get(0), 1, items, t);
			count(boss.getName(), items);
		}
	}

	private void playRolls(BossDefinition boss, int n)
	{
		int subdues = 50 + r.nextInt(200);
		op("onRollBaseline", boss.getName(), subdues, r.nextInt(30));
		for (int i = 0; i < n; i++)
		{
			tick(300 + r.nextInt(300));
			op("onRewardRolls", boss.getName(), 1 + r.nextInt(4), t);
			List<Loot> items = rollLoot(boss, null, 25);
			op("onLoot", boss.getLootNames().get(0), 1, items, t + 100);
			count(boss.getName(), items);
		}
	}

	private void visitLog(BossDefinition boss)
	{
		Map<String, Integer> page = new LinkedHashMap<>();
		Map<String, Integer> seen = got.getOrDefault(boss.getName(), Collections.emptyMap());
		for (DropDefinition d : boss.getDrops())
		{
			int extra = r.nextInt(6) == 0 ? 1 + r.nextInt(2) : 0;
			page.put(d.getName(), (seen.getOrDefault(d.getName(), 0) + extra) * (d.isOnce() ? 1 : 1));
		}
		for (DropDefinition d : catalog.poolsOf(boss).values())
		{
			int fromAll = 0;
			for (Map<String, Integer> other : got.values())
			{
				fromAll += other.getOrDefault(d.getName(), 0);
			}
			// sometimes copies On Rate never saw (from before it, or other monsters)
			page.put(d.getName(), fromAll + (r.nextInt(3) == 0 ? 1 + r.nextInt(2) : 0));
		}
		List<String> lines = new ArrayList<>();
		Map<String, Integer> kcs = counters.getOrDefault(boss.getName(), Collections.emptyMap());
		int extraKc = 20 + r.nextInt(300);
		if (boss.hasParts())
		{
			for (BossPart part : boss.getParts())
			{
				lines.add(part.getKcNames().get(0) + " kills: " + (extraKc + r.nextInt(50)));
			}
		}
		else
		{
			lines.add(boss.getName() + " kills: " + (extraKc + kcs.values().stream().mapToInt(Integer::intValue).sum()));
		}
		op("onClogPage", boss.getClogPages().get(0), lines, page);
	}

	/** One of the shared items this boss drops, or null. */
	private String pickPoolItem(BossDefinition boss)
	{
		List<DropDefinition> pooled = new ArrayList<>(catalog.poolsOf(boss).values());
		return pooled.isEmpty() ? null : pooled.get(r.nextInt(pooled.size())).getName();
	}

	/**
	 * Logs into another account, plays a little there, and comes back: what the plugin does on a profile
	 * change (save this account, load the other). Nothing of one account may show in the other.
	 */
	void switchAway()
	{
		op("SWITCH", "alt");
		op("OBSERVE");
		if (!touched.isEmpty())
		{
			BossDefinition boss = catalog.byName(touched.get(r.nextInt(touched.size())));
			if (boss.getRaid() == null && !boss.isDelve() && boss.getWaves() == 0 && boss.getRollsPerKill() == 0)
			{
				playKills(boss, 3 + r.nextInt(8));
			}
		}
		op("OBSERVE");
		op("SWITCH", "main");
		op("OBSERVE");
	}

	/** A few manual corrections and setting changes at random. */
	void sprinkle()
	{
		for (int i = 0; i < 6; i++)
		{
			if (touched.isEmpty())
			{
				return;
			}
			BossDefinition boss = catalog.byName(touched.get(r.nextInt(touched.size())));
			if (boss.getDrops().isEmpty())
			{
				continue;
			}
			DropDefinition drop = boss.getDrops().get(r.nextInt(boss.getDrops().size()));
			switch (r.nextInt(7))
			{
				case 0:
					op("setLeftOut", boss.getName(), drop.getName(), r.nextBoolean());
					break;
				case 1:
					op("addPastDrop", boss.getName(), drop.getName(), 1 + r.nextInt(30), r.nextBoolean());
					break;
				case 2:
					settings = new Settings();
					settings.duoShare = r.nextDouble();
					settings.tobTeam = 1 + r.nextInt(5);
					settings.tfaTeam = 1 + r.nextInt(8);
					settings.cox = 0.02 + r.nextDouble() * 0.06;
					op("SET_SETTINGS", settings);
					break;
				case 3:
					String key = catalog.poolOf(boss, pickPoolItem(boss));
					if (key != null)
					{
						List<BossCatalog.PoolSource> sources = catalog.getPools().get(key);
						String from = r.nextInt(4) == 0 ? "*not from a boss" : sources.get(r.nextInt(sources.size())).getBoss().getName();
						if (r.nextInt(5) == 0)
						{
							op("unsplitPool", key);
						}
						else
						{
							op("splitPool", key, from);
						}
					}
					break;
				case 4:
					op("resetTrip");
					break;
				case 5:
					op("setLeftOut", boss.getName(), drop.getName(), false);
					break;
				default:
					op("onOtherLoot", "Smoke devil", Collections.singletonList(new Loot("Occult necklace", 1)));
					break;
			}
			if (r.nextInt(3) == 0)
			{
				op("REMOVE_HISTORY", boss.getName(), r.nextInt(5));
			}
		}
		// judge one shared item per boss, where a played boss has any
		for (String name : touched)
		{
			BossDefinition boss = catalog.byName(name);
			String key = catalog.poolOf(boss, pickPoolItem(boss));
			if (key != null)
			{
				op("splitPool", key, r.nextInt(3) == 0 ? "*not from a boss" : name);
				op("OBSERVE");
				break;
			}
		}
		if (r.nextInt(2) == 0)
		{
			switchAway();
		}
		op("OBSERVE");
	}
}
