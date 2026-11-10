package com.onrate.parity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.Bundled;
import com.onrate.parity.Side.Settings;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.junit.Test;

/**
 * The same player, played into the original tracker and the rebuilt tracker. After every few
 * steps everything either of them can tell you (every row of every boss, the account totals with
 * prices, shared pools, trips, day counts, history, kill times, the Time page, the saved JSON) is
 * turned into text and compared. One differing digit fails the test.
 */
public class EngineParityTest
{
	private static final Gson GSON = new Gson();
	private static long observations;
	private static long ops;

	private static final ToLongFunction<String> PRICES = name ->
	{
		int h = Math.abs(name.toLowerCase().hashCode());
		return h % 7 == 0 ? 0 : (h % 5000) * 1000L + 100;
	};

	private static BossCatalog catalog()
	{
		return Bundled.catalog(GSON);
	}

	/** Plays a script on both sides, comparing results step by step and everything at each OBSERVE. */
	private void replay(String label, List<Sim.Op> script, Settings initial, List<String> touched) throws Exception
	{
		Side[] sides = {Side.ORIGINAL, Side.REBUILT};
		AtomicInteger team = new AtomicInteger(0);
		Settings[] settings = {initial};
		for (Side s : sides)
		{
			s.newTracker();
			s.call("setTeamSizeDetectorFor", (ToIntFunction<String>) boss -> team.get());
			s.call("setSettings", initial);
		}
		long now = Sim.BASE;
		for (int i = 0; i < script.size(); i++)
		{
			Sim.Op op = script.get(i);
			ops++;
			if (op.method.equals("SET_TEAM"))
			{
				team.set((Integer) op.args[0]);
				continue;
			}
			if (op.method.equals("SET_SETTINGS"))
			{
				settings[0] = (Settings) op.args[0];
				for (Side s : sides)
				{
					s.call("setSettings", settings[0]);
				}
				continue;
			}
			if (op.method.equals("OBSERVE"))
			{
				String a = observe(sides[0], settings[0], touched, now);
				String b = observe(sides[1], settings[0], touched, now);
				observations++;
				if (!a.equals(b))
				{
					fail(label + " after step " + i + " (" + (i > 0 ? script.get(i - 1) : "start") + "): " + Dumper.firstDifference(a, b));
				}
				continue;
			}
			now = Math.max(now, lastTime(op));
			String[] results = new String[2];
			for (int k = 0; k < 2; k++)
			{
				try
				{
					results[k] = Dumper.dump(sides[k].apply(op));
				}
				catch (RuntimeException e)
				{
					results[k] = "threw " + e.getClass().getSimpleName();
				}
			}
			assertEquals(label + " step " + i + " " + op + " returned differently", results[0], results[1]);
		}
		// the final state, saved: bytes must match
		String a = observe(sides[0], settings[0], touched, now);
		String b = observe(sides[1], settings[0], touched, now);
		if (!a.equals(b))
		{
			fail(label + " at the end: " + Dumper.firstDifference(a, b));
		}
	}

	private static Object[] withSettings(Object[] args, Settings s)
	{
		return args;
	}

	private static long lastTime(Sim.Op op)
	{
		for (int i = op.args.length - 1; i >= 0; i--)
		{
			if (op.args[i] instanceof Long && (Long) op.args[i] > Sim.BASE / 2)
			{
				return (Long) op.args[i];
			}
		}
		return 0;
	}

	/** Everything either implementation can say about its current state, as one string. */
	private String observe(Side s, Settings st, List<String> touched, long now)
	{
		StringBuilder out = new StringBuilder();
		TreeMap<String, Object> map = new TreeMap<>();
		for (String boss : touched)
		{
			map.put("stats " + boss, s.call("stats", boss, st));
			map.put("history " + boss, s.call("history", boss));
			map.put("killTimes " + boss, s.call("killTimes", boss, null));
			map.put("killsSeen " + boss, s.call("killsSeen", boss));
			map.put("teamKills " + boss, s.call("teamKills", boss));
			map.put("olderShare " + boss, s.call("olderShare", boss));
			map.put("raidRecords " + boss, s.call("raidRecords", boss));
			map.put("syncReminder " + boss, s.call("syncReminder", boss));
			map.put("sharedPoolsFor " + boss, s.call("sharedPoolsFor", boss, st));
			map.put("measured " + boss, s.call("measuredRaids", boss, null));
			map.put("levelTimes " + boss, s.call("levelTimes", boss, 3));
		}
		map.put("account", s.call("accountStats", st));
		map.put("accountWithPrices", s.call("accountStats", st, PRICES));
		map.put("sharedPools", s.call("sharedPools", st));
		map.put("today", s.call("recentDays", 1, now));
		map.put("week", s.call("recentDays", 7, now));
		map.put("trip", s.call("tripStats"));
		map.put("tripValue", s.call("tripValue", PRICES));
		map.put("lastUnique", s.call("lastUnique"));
		map.put("checklist", s.call("syncChecklist"));
		map.put("mostKills", s.call("bossWithMostKills"));
		map.put("luckyDrops", s.call("dropsByLuck", true, 25));
		map.put("dryDrops", s.call("dropsByLuck", false, 25));
		map.put("recentDrops", s.call("recentDrops", 25));
		map.put("lastBoss", s.call("getLastBoss"));
		map.put("tripIdle", s.call("tripIdleLimitMillis"));
		Object killTimes = s.callStatic("data", "KillTimes", "bundled");
		Object chart = s.callStatic("data", "LadlorChart", "bundled");
		map.put("itemTimes", s.callStatic("stats", "ItemTimes", "compute", s.tracker, killTimes, st));
		map.put("chartTimes", s.callStatic("stats", "ChartTimes", "compute", s.tracker, killTimes, st, chart));
		map.put("notes", withoutTripNotes(s.call("drainNotes")));
		map.put("save", s.call("saveData", (Function<Object, String>) GSON::toJson));
		for (Map.Entry<String, Object> e : map.entrySet())
		{
			out.append(e.getKey()).append(" = ").append(Dumper.dump(e.getValue())).append('\n');
		}
		return out.toString();
	}

	/**
	 * The trip summary chat line was removed on purpose (the original still sends it), so TRIP
	 * notes are left out on both sides; every other note must still match.
	 */
	private static Object withoutTripNotes(Object notes)
	{
		List<Object> kept = new ArrayList<>();
		for (Object n : (Iterable<?>) notes)
		{
			Object kind;
			try
			{
				kind = n.getClass().getMethod("getKind").invoke(n);
			}
			catch (ReflectiveOperationException e)
			{
				throw new IllegalStateException(e);
			}
			if (!"TRIP".equals(String.valueOf(kind)))
			{
				kept.add(n);
			}
		}
		return kept;
	}

	private List<BossDefinition> allBosses(BossCatalog catalog)
	{
		return new ArrayList<>(catalog.getBosses());
	}

	@Test
	public void everyBossPlayedOnBothImplementations() throws Exception
	{
		BossCatalog catalog = catalog();
		List<BossDefinition> bosses = allBosses(catalog);
		int seeds = 0;
		// each scenario plays a handful of bosses; the offsets walk the whole catalogue
		for (int seed = 0; seed < 28; seed++)
		{
			Sim sim = new Sim(1000 + seed, catalog);
			for (int i = 0; i < 6; i++)
			{
				sim.playBoss(bosses.get((seed * 5 + i * 11) % bosses.size()));
			}
			sim.sprinkle();
			replay("seed " + seed, sim.ops, sim.settings, sim.touched);
			seeds++;
		}
		System.out.println("engine parity: " + seeds + " scenarios, " + ops + " steps, " + observations + " full-state comparisons");
	}

	@Test
	public void specialBossesPlayedOnBothImplementations() throws Exception
	{
		BossCatalog catalog = catalog();
		String[] special = {"Chambers of Xeric", "Theatre of Blood", "Tombs of Amascut", "The Fractured Archive", "Doom of Mokhaiotl",
			"Fortis Colosseum", "Tempoross", "Wintertodt", "Nex", "Yama", "Royal Titans", "Corporeal Beast", "Zalcano", "Hueycoatl",
			"Vorkath", "Dagannoth Kings", "Callisto and Artio", "Barrows", "General Graardor", "Chaos Fanatic", "Chaos Elemental",
			"Abyssal Sire", "Alchemical Hydra", "Araxxor", "Zulrah"};
		int seed = 0;
		for (String name : special)
		{
			BossDefinition boss = catalog.byName(name);
			if (boss == null)
			{
				fail("no such boss in the catalogue: " + name);
			}
			for (int run = 0; run < 2; run++)
			{
				Sim sim = new Sim(5000 + seed++, catalog);
				sim.playBoss(boss);
				sim.playBoss(boss);
				sim.sprinkle();
				replay(name + " run " + run, sim.ops, sim.settings, sim.touched);
			}
		}
	}

	@Test
	public void accountSwitchingKeepsAccountsApart() throws Exception
	{
		BossCatalog catalog = catalog();
		List<BossDefinition> bosses = allBosses(catalog);
		for (int seed = 0; seed < 6; seed++)
		{
			Sim sim = new Sim(12000 + seed, catalog);
			for (int i = 0; i < 4; i++)
			{
				sim.playBoss(bosses.get((seed * 11 + i * 13) % bosses.size()));
			}
			sim.switchAway();
			sim.playBoss(bosses.get((seed * 5 + 3) % bosses.size()));
			sim.switchAway();
			// on both implementations, step by step
			replay("switching seed " + seed, sim.ops, sim.settings, sim.touched);

			// and on the new one: a fresh account shows nothing of the old one, and coming back restores it exactly
			Side side = Side.REBUILT;
			replayInto(side, sim);
			String before = persistedOnly(observe(side, sim.settings, sim.touched, Sim.BASE + 9_000_000_000L));
			side.apply(new Sim.Op("SWITCH", "fresh"));
			Object account = side.call("accountStats", sim.settings);
			assertEquals("a new account starts empty", 0, ((List<?>) account.getClass().getMethod("getRows").invoke(account)).size());
			assertEquals(null, side.call("tripStats"));
			assertEquals(0, ((List<?>) side.call("recentDrops", 50)).size());
			side.apply(new Sim.Op("SWITCH", "main"));
			String after = persistedOnly(observe(side, sim.settings, sim.touched, Sim.BASE + 9_000_000_000L));
			if (!before.equals(after))
			{
				fail("switching back changed the account: " + Dumper.firstDifference(before, after));
			}
		}
	}

	@Test
	public void savedDataLoadsAcrossVersionsBothWays() throws Exception
	{
		BossCatalog catalog = catalog();
		List<BossDefinition> bosses = allBosses(catalog);
		for (int seed = 0; seed < 6; seed++)
		{
			Sim sim = new Sim(9000 + seed, catalog);
			for (int i = 0; i < 7; i++)
			{
				sim.playBoss(bosses.get((seed * 9 + i * 7) % bosses.size()));
			}
			sim.sprinkle();
			for (Side from : new Side[]{Side.ORIGINAL, Side.REBUILT})
			{
				Side to = from == Side.ORIGINAL ? Side.REBUILT : Side.ORIGINAL;
				replayInto(from, sim);
				// everything `from` has saved for this account (switches mid-session saved part of it already)
				java.util.Map<String, String> store = new java.util.HashMap<>(from.saveAccount());
				String before = observe(from, sim.settings, sim.touched, Sim.BASE + 10_000_000_000L);
				String progress = store.get("progress");

				// the other version loads it, as ProfileStore does
				to.newTracker();
				to.call("setSettings", sim.settings);
				to.loadAccount(store);
				String after = observe(to, sim.settings, sim.touched, Sim.BASE + 10_000_000_000L);
				// the first save re-sends everything that changed; compare what the loaded tracker reports
				before = persistedOnly(before);
				after = persistedOnly(after);
				if (!before.equals(after))
				{
					fail("seed " + seed + " " + from.name + " -> " + to.name + ": " + Dumper.firstDifference(before, after));
				}
				assertTrue(progress.length() > 100);
			}
		}
	}

	/** Observations that depend only on what is saved: the trip, the last boss and chat notes live in memory only. */
	private static String persistedOnly(String observations)
	{
		return observations.replaceAll("(?m)^(save|lastBoss|trip|tripValue|tripIdle|lastUnique|notes) = .*$", "");
	}

	private void replayInto(Side side, Sim sim) throws Exception
	{
		side.newTracker();
		side.call("setSettings", sim.settings);
		AtomicInteger team = new AtomicInteger(0);
		side.call("setTeamSizeDetectorFor", (ToIntFunction<String>) boss -> team.get());
		Settings current = sim.settings;
		for (Sim.Op op : sim.ops)
		{
			if (op.method.equals("SET_TEAM"))
			{
				team.set((Integer) op.args[0]);
			}
			else if (op.method.equals("SET_SETTINGS"))
			{
				current = (Settings) op.args[0];
				side.call("setSettings", current);
			}
			else if (!op.method.equals("OBSERVE"))
			{
				try
				{
					side.apply(op);
				}
				catch (RuntimeException e)
				{
					// same on both sides, checked in the replay test
				}
			}
		}
	}
}
