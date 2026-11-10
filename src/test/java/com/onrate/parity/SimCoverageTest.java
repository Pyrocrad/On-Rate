package com.onrate.parity;

import static org.junit.Assert.assertTrue;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.Bundled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import org.junit.Test;

/**
 * The simulated players must really reach the paths the equivalence tests are meant to guard: a
 * Tombs points message that never matches a raid, or a History removal that never finds an entry,
 * would be compared on both sides and still prove nothing. This counts the steps that had an effect.
 */
public class SimCoverageTest
{
	@Test
	public void everyNewKindOfEventTakesEffectSomewhere() throws Exception
	{
		BossCatalog catalog = Bundled.catalog(new com.google.gson.Gson());
		List<BossDefinition> bosses = new ArrayList<>(catalog.getBosses());
		Map<String, int[]> tally = new TreeMap<>();
		for (int seed = 0; seed < 28; seed++)
		{
			Sim sim = new Sim(1000 + seed, catalog);
			for (int i = 0; i < 6; i++)
			{
				sim.playBoss(bosses.get((seed * 5 + i * 11) % bosses.size()));
			}
			sim.sprinkle();
			tally(sim, tally);
		}
		for (String raid : new String[]{"Tombs of Amascut", "Chambers of Xeric", "Theatre of Blood", "The Fractured Archive"})
		{
			for (int run = 0; run < 6; run++)
			{
				Sim sim = new Sim(88000 + run * 7 + raid.length(), catalog);
				sim.playBoss(catalog.byName(raid));
				sim.playBoss(catalog.byName(raid));
				sim.sprinkle();
				tally(sim, tally);
			}
		}
		StringBuilder report = new StringBuilder();
		tally.forEach((k, v) -> report.append(String.format("%n  %-32s %4d sent, %4d took effect", k, v[0], v[1])));
		System.out.println("simulator coverage:" + report);
		for (String kind : new String[]{"onToaPoints", "onPartyPoints", "onTimeMessage", "importRaidTimes", "REMOVE_HISTORY", "SWITCH",
			"onRaidRecord", "onClogPage", "addPastDrop", "splitPool"})
		{
			int[] v = tally.get(kind);
			assertTrue(kind + " never took effect: " + report, v != null && v[1] > 0);
		}
	}

	private static void tally(Sim sim, Map<String, int[]> tally) throws Exception
	{
		Side side = Side.REBUILT;
		side.newTracker();
		side.call("setSettings", sim.settings);
		AtomicInteger team = new AtomicInteger();
		side.call("setTeamSizeDetectorFor", (ToIntFunction<String>) b -> team.get());
		for (Sim.Op op : sim.ops)
		{
			if (op.method.equals("SET_TEAM"))
			{
				team.set((Integer) op.args[0]);
				continue;
			}
			if (op.method.equals("SET_SETTINGS"))
			{
				side.call("setSettings", op.args[0]);
				continue;
			}
			if (op.method.equals("OBSERVE"))
			{
				continue;
			}
			Object result;
			try
			{
				result = side.apply(op);
			}
			catch (RuntimeException e)
			{
				result = null;
			}
			int[] v = tally.computeIfAbsent(op.method, k -> new int[2]);
			v[0]++;
			if (tookEffect(op.method, result))
			{
				v[1]++;
			}
		}
	}

	private static boolean tookEffect(String method, Object result)
	{
		if (result == null || Boolean.FALSE.equals(result) || "nothing to remove".equals(result))
		{
			return false;
		}
		if (result instanceof Integer)
		{
			return (Integer) result > 0;
		}
		if (result instanceof java.util.Collection)
		{
			return !((java.util.Collection<?>) result).isEmpty();
		}
		return true;
	}
}
