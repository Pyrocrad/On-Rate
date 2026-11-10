package com.onrate.parity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.Bundled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.junit.Test;

/**
 * Every sentence and number the panel prints comes from {@code Texts} and {@code ShareText}. The
 * integrated plugin keeps the original UI classes, now running on the new engine: for the same
 * input they must print exactly the same strings as the original.
 */
public class TextsParityTest
{
	private static final Class<?> OLD = load("legacy.expecteddrops.ui.Texts");
	private static final Class<?> NEW = load("com.expecteddrops.ui.Texts");
	private static long compared;

	private static Class<?> load(String name)
	{
		try
		{
			return Class.forName(name);
		}
		catch (ClassNotFoundException e)
		{
			throw new IllegalStateException(e);
		}
	}

	private static Object call(Class<?> type, String method, Class<?>[] sig, Object... args) throws Exception
	{
		Method m = type.getDeclaredMethod(method, sig);
		m.setAccessible(true);
		return m.invoke(null, args);
	}

	/** Calls the same function on both, with arguments built for each side, and compares the result. */
	private static void same(String method, Class<?>[] oldSig, Object[] oldArgs, Class<?>[] newSig, Object[] newArgs) throws Exception
	{
		Object a = call(OLD, method, oldSig, oldArgs);
		Object b = call(NEW, method, newSig, newArgs);
		compared++;
		assertEquals(method + Arrays.deepToString(newArgs), a, b);
	}

	private static void same(String method, Class<?>[] sig, Object... args) throws Exception
	{
		same(method, sig, args, sig, args);
	}

	private static legacy.expecteddrops.stats.RowStats oldRow(double rate, double e, int got, Integer streak, boolean exact, int cap)
	{
		legacy.expecteddrops.stats.RowStats r = new legacy.expecteddrops.stats.RowStats("Item", "Item", rate, e, got, false).withStreak(streak, exact);
		return cap > 0 ? r.capped(cap, Math.min(got, cap)) : r;
	}

	private static com.onrate.engine.stats.RowStats newRow(double rate, double e, int got, Integer streak, boolean exact, int cap)
	{
		com.onrate.engine.stats.RowStats r = new com.onrate.engine.stats.RowStats("Item", "Item", rate, e, got, false).withStreak(streak, exact);
		return cap > 0 ? r.capped(cap, Math.min(got, cap)) : r;
	}

	private static double[] values()
	{
		List<Double> list = new ArrayList<>();
		for (double e = 0.0004; e < 400; e *= 1.45)
		{
			list.add(e);
		}
		list.addAll(Arrays.asList(0.0, 0.5, 1.0, 0.099, 0.1, 0.35, 0.45, 0.55, 0.65, 0.9, 0.99, 0.995, 0.9999, Double.NaN));
		return list.stream().mapToDouble(Double::doubleValue).toArray();
	}

	@Test
	public void numberFormatsAndLuckWordsAreIdentical() throws Exception
	{
		Class<?>[] d = {double.class};
		for (double v : values())
		{
			same("formatExpected", d, v);
			same("formatMultiple", d, v);
			same("formatPercent", d, v);
			same("pct", d, v);
			same("sharePct", d, v);
			same("luckShort", d, v);
			same("luckPercent", d, v);
			same("plainLuck", d, v);
			same("waitLuck", d, v);
			same("waitLuckShort", new Class<?>[]{double.class, boolean.class}, v, true);
			same("waitLuckShort", new Class<?>[]{double.class, boolean.class}, v, false);
			for (int kills : new int[]{0, 1, 50, 1000})
			{
				same("nextChance", new Class<?>[]{double.class, int.class}, v / 1000, kills);
			}
		}
	}

	@Test
	public void sentencesAboutRowsAreIdentical() throws Exception
	{
		Class<?>[] oldSig = {legacy.expecteddrops.stats.RowStats.class};
		Class<?>[] newSig = {com.onrate.engine.stats.RowStats.class};
		for (double e : values())
		{
			if (Double.isNaN(e))
			{
				continue;
			}
			for (int got = 0; got <= 40; got += got < 6 ? 1 : 9)
			{
				for (int cap : new int[]{0, 1, 2})
				{
					for (Integer streak : new Integer[]{null, 0, 37, 2400})
					{
						for (boolean exact : new boolean[]{true, false})
						{
							legacy.expecteddrops.stats.RowStats o = oldRow(1 / 512.0, e, got, streak, exact, cap);
							com.onrate.engine.stats.RowStats n = newRow(1 / 512.0, e, got, streak, exact, cap);
							Object[] oa = {o};
							Object[] na = {n};
							same("streakText", oldSig, oa, newSig, na);
							same("rateMultiple", oldSig, oa, newSig, na);
							same("rateMultipleLine", oldSig, oa, newSig, na);
							same("gotOfExpected", oldSig, oa, newSig, na);
							same("haveText", oldSig, oa, newSig, na);
							same("isQuiet", oldSig, oa, newSig, na);
							same("streakNote", new Class<?>[]{oldSig[0], String.class}, new Object[]{o, "kill"},
								new Class<?>[]{newSig[0], String.class}, new Object[]{n, "kill"});
							double[] shares = n.getShares();
							assertEquals(Arrays.toString(o.getShares()), Arrays.toString(shares));
							Class<?>[] s = {double[].class, int.class};
							same("luckShort", s, shares, got);
							same("luckPercent", s, shares, got);
							same("sharesLine", s, shares, got);
						}
					}
				}
			}
		}
	}

	@Test
	public void notableLinesAndSmallHelpersAreIdentical() throws Exception
	{
		List<legacy.expecteddrops.stats.RowStats> olds = new ArrayList<>();
		List<com.onrate.engine.stats.RowStats> news = new ArrayList<>();
		for (double e : new double[]{0.05, 0.4, 1.0, 2.6, 5.0, 12.0})
		{
			for (int got : new int[]{0, 1, 4, 9})
			{
				olds.add(oldRow(0.01, e, got, null, true, 0));
				news.add(newRow(0.01, e, got, null, true, 0));
			}
		}
		for (int i = 0; i < olds.size(); i++)
		{
			int to = Math.min(olds.size(), i + 5);
			same("notableLine", new Class<?>[]{List.class}, new Object[]{olds.subList(i, to)}, new Class<?>[]{List.class},
				new Object[]{news.subList(i, to)});
		}
		for (String name : new String[]{"Tumeken's shadow", "Elder maul", "Bandos boots", "", "Onyx"})
		{
			same("withArticle", new Class<?>[]{String.class}, name);
		}
		same("joinAnd", new Class<?>[]{List.class}, Arrays.asList("Vorkath", "Zulrah", "Cerberus"));
		same("mainValueSource", new Class<?>[]{List.class, List.class}, Arrays.asList("A", "B"), Arrays.asList(95.0, 5.0));
		System.out.println("texts parity: " + compared + " calls identical");
	}

	@Test
	public void copyMyLuckIsIdentical() throws Exception
	{
		BossCatalog catalog = Bundled.catalog(new com.google.gson.Gson());
		List<BossDefinition> bosses = new ArrayList<>(catalog.getBosses());
		int texts = 0;
		for (int seed = 0; seed < 10; seed++)
		{
			Sim sim = new Sim(41000 + seed, catalog);
			for (int i = 0; i < 6; i++)
			{
				sim.playBoss(bosses.get((seed * 7 + i * 5) % bosses.size()));
			}
			String[] out = new String[2];
			for (int k = 0; k < 2; k++)
			{
				Side side = k == 0 ? Side.ORIGINAL : Side.REBUILT;
				side.newTracker();
				side.call("setSettings", sim.settings);
				AtomicInteger team = new AtomicInteger();
				side.call("setTeamSizeDetectorFor", (ToIntFunction<String>) b -> team.get());
				for (Sim.Op op : sim.ops)
				{
					if (op.method.equals("SET_TEAM"))
					{
						team.set((Integer) op.args[0]);
					}
					else if (!op.method.equals("SET_SETTINGS") && !op.method.equals("OBSERVE"))
					{
						try
						{
							side.apply(op);
						}
						catch (RuntimeException e)
						{
							// identical on both, checked by the replay test
						}
					}
				}
				Object account = side.call("accountStats", sim.settings, (ToLongFunction<String>) n -> 1_000L * Math.abs(n.hashCode() % 977));
				Class<?> share = load(k == 0 ? "legacy.expecteddrops.ui.ShareText" : "com.expecteddrops.ui.ShareText");
				Method m = share.getDeclaredMethod("account", account.getClass(), boolean.class);
				m.setAccessible(true);
				out[k] = (String) m.invoke(null, account, seed % 2 == 0);
			}
			assertEquals("seed " + seed, out[0], out[1]);
			texts += out[0] == null ? 0 : 1;
		}
		assertTrue(texts > 0);
	}
}
