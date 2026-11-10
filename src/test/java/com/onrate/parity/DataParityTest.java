package com.onrate.parity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

/**
 * The drop-rate data is the source of truth and must not drift. The three data files are compared
 * byte for byte with the originals, and the catalogue built from them is compared structurally:
 * every boss, part, drop, rate, weight, level rate, pool, shared and untracked item, value formula.
 */
public class DataParityTest
{
	private static String sha256(String resource) throws Exception
	{
		try (InputStream in = DataParityTest.class.getResourceAsStream(resource))
		{
			assertTrue("missing " + resource, in != null);
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buffer = new byte[8192];
			for (int n; (n = in.read(buffer)) > 0; )
			{
				md.update(buffer, 0, n);
			}
			StringBuilder hex = new StringBuilder();
			for (byte b : md.digest())
			{
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		}
	}

	@Test
	public void dataFilesAreByteIdenticalToTheOriginals() throws Exception
	{
		for (String file : new String[]{"bosses.json", "killtimes.json", "ladlorchart.json"})
		{
			assertEquals(file, sha256("/legacy/expecteddrops/" + file), sha256("/com/onrate/" + file));
		}
	}

	private String describe(Side s) throws Exception
	{
		s.newTracker();
		Object catalog = s.catalog;
		StringBuilder out = new StringBuilder();
		@SuppressWarnings("unchecked") List<Object> bosses = (List<Object>) s.callOn(catalog, "getBosses");
		Object defClass = null;
		for (Object boss : bosses)
		{
			out.append("BOSS ").append(Dumper.dump(boss)).append('\n');
			@SuppressWarnings("unchecked") List<Object> drops = (List<Object>) s.callOn(boss, "getDrops");
			@SuppressWarnings("unchecked") List<Object> parts = (List<Object>) s.callOn(boss, "getParts");
			for (Object drop : drops)
			{
				StringBuilder d = new StringBuilder();
				for (int level = 0; level <= 9; level++)
				{
					d.append(s.callOn(drop, "getLevelRate", level)).append('|');
				}
				for (Object part : parts)
				{
					String partName = (String) s.callOn(part, "getName");
					d.append(partName).append('=').append(s.callOn(drop, "getPartRate", partName)).append('/')
						.append(s.callOn(drop, "getWeight", partName)).append('/').append(s.callOn(boss, "getTotalWeight", partName)).append('|');
				}
				for (int raidLevel : new int[]{0, 50, 100, 150, 200, 300, 350, 400, 450, 500, 600})
				{
					d.append(s.callOn(drop, "getWeightAtLevel", raidLevel)).append(',');
				}
				for (int quantity : new int[]{0, 1, 2, 10, 750, 1500})
				{
					d.append(s.callOn(drop, "dropsFromQuantity", quantity)).append(';');
				}
				out.append("  DROP ").append(Dumper.dump(drop)).append(" extra ").append(d).append('\n');
			}
		}
		Map<String, Object> pools = new TreeMap<>();
		@SuppressWarnings("unchecked") Map<String, List<Object>> p = (Map<String, List<Object>>) s.callOn(catalog, "getPools");
		for (Map.Entry<String, List<Object>> e : p.entrySet())
		{
			List<String> sources = new ArrayList<>();
			for (Object src : e.getValue())
			{
				sources.add(Dumper.dump(s.callOn(src, "getBoss")).length() + ":" + s.callOn(s.callOn(src, "getBoss"), "getName") + "/"
					+ s.callOn(s.callOn(src, "getDrop"), "getName"));
			}
			pools.put(e.getKey(), sources);
		}
		out.append("POOLS ").append(pools).append('\n');
		out.append("EXCLUDED ITEMS ").append(Dumper.dump(s.callOn(catalog, "getExcludedItems"))).append('\n');
		out.append("EXCLUDED BOSSES ").append(Dumper.dump(s.callOn(catalog, "getExcludedBosses"))).append('\n');
		out.append("FORMULA ITEMS ").append(Dumper.dump(s.callOn(catalog, "getFormulaItemNames"))).append('\n');
		@SuppressWarnings("unchecked") java.util.Set<String> names = new java.util.TreeSet<>((java.util.Set<String>) s.callOn(catalog, "getAllItemNames"));
		for (String name : names)
		{
			Object formula = s.callOn(catalog, "getValueFormula", name);
			out.append("ITEM ").append(name).append(" shared=").append(s.callOn(catalog, "isClogShared", name))
				.append(" untracked=").append(s.callOn(catalog, "isUntracked", name))
				.append(" formula=").append(formula == null ? "none" : evaluate(s, formula)).append('\n');
		}
		out.append("HIDDEN ").append(Dumper.dump(s.callOn(catalog, "getHiddenBosses"))).append('\n');
		return out.toString();
	}

	/** A formula evaluated against a fixed price list, so equal results mean equal formulas. */
	private String evaluate(Side s, Object formula)
	{
		java.util.function.ToLongFunction<String> prices = name -> 100_000L + Math.abs(name.toLowerCase().hashCode()) % 9_000_000;
		return String.valueOf(s.callOn(formula, "evaluate", prices)) + "/" + Dumper.dump(s.callOn(formula, "getItemNames"));
	}

	@Test
	public void catalogueIsIdentical() throws Exception
	{
		String original = describe(Side.ORIGINAL);
		String rebuilt = describe(Side.REBUILT);
		assertTrue(original.length() > 50_000);
		assertEquals(Collections.emptyList(), diffLines(original, rebuilt));
		System.out.println("data parity: " + original.split("\n").length + " catalogue lines identical");
	}

	private static List<String> diffLines(String a, String b)
	{
		String[] x = a.split("\n");
		String[] y = b.split("\n");
		List<String> diffs = new ArrayList<>();
		for (int i = 0; i < Math.max(x.length, y.length) && diffs.size() < 5; i++)
		{
			String l = i < x.length ? x[i] : "<missing>";
			String r = i < y.length ? y[i] : "<missing>";
			if (!l.equals(r))
			{
				diffs.add("line " + i + "\n  original: " + l + "\n  rebuilt : " + r);
			}
		}
		return diffs;
	}
}
