package com.onrate.parity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns any result object into canonical text by walking its public no-argument getters
 * ({@code getX}, {@code isX}, {@code hasX}). Class names are not part of the text, so the original
 * and the rebuilt classes give the same text when they hold the same values. Doubles print with
 * their exact shortest representation, so equal text means bit-equal numbers.
 */
final class Dumper
{
	private static final int MAX_DEPTH = 7;

	private Dumper()
	{
	}

	static String dump(Object o)
	{
		StringBuilder out = new StringBuilder();
		walk(o, out, 0, new IdentityHashMap<>());
		return out.toString();
	}

	private static boolean ours(Class<?> c)
	{
		String n = c.getName();
		return n.startsWith("com.expecteddrops.") || n.startsWith("legacy.expecteddrops.") || n.startsWith("com.onrate.");
	}

	private static void walk(Object o, StringBuilder out, int depth, IdentityHashMap<Object, Boolean> seen)
	{
		if (o == null)
		{
			out.append("null");
		}
		else if (o instanceof Double || o instanceof Float)
		{
			double d = ((Number) o).doubleValue();
			out.append(Double.isNaN(d) ? "NaN" : Double.toString(d));
		}
		else if (o instanceof Number || o instanceof Boolean || o instanceof CharSequence || o instanceof Character
			|| o instanceof Enum)
		{
			out.append(o instanceof Enum ? ((Enum<?>) o).name() : String.valueOf(o));
		}
		else if (o.getClass().isArray())
		{
			out.append('[');
			int n = java.lang.reflect.Array.getLength(o);
			for (int i = 0; i < n; i++)
			{
				if (i > 0)
				{
					out.append(',');
				}
				walk(java.lang.reflect.Array.get(o, i), out, depth + 1, seen);
			}
			out.append(']');
		}
		else if (o instanceof Map)
		{
			out.append('{');
			Map<String, Object> sorted = new TreeMap<>();
			for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet())
			{
				sorted.put(String.valueOf(e.getKey()), e.getValue());
			}
			boolean first = true;
			for (Map.Entry<String, Object> e : sorted.entrySet())
			{
				if (!first)
				{
					out.append(',');
				}
				first = false;
				out.append(e.getKey()).append('=');
				walk(e.getValue(), out, depth + 1, seen);
			}
			out.append('}');
		}
		else if (o instanceof Collection)
		{
			out.append('[');
			boolean first = true;
			List<Object> items = new ArrayList<>((Collection<?>) o);
			if (o instanceof java.util.Set)
			{
				items.sort((a, b) -> String.valueOf(a).compareTo(String.valueOf(b)));
			}
			for (Object item : items)
			{
				if (!first)
				{
					out.append(',');
				}
				first = false;
				walk(item, out, depth + 1, seen);
			}
			out.append(']');
		}
		else if (ours(o.getClass()) && depth < MAX_DEPTH && seen.put(o, true) == null)
		{
			out.append('(');
			List<Method> getters = new ArrayList<>();
			for (Method m : o.getClass().getMethods())
			{
				String n = m.getName();
				if (m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class
					&& m.getReturnType() != void.class && (n.startsWith("get") || n.startsWith("is") || n.startsWith("has"))
					&& !n.equals("getClass") && !n.equals("getCatalog") && !n.equals("getBoss") && !n.equals("getState"))
				{
					getters.add(m);
				}
			}
			getters.sort((a, b) -> a.getName().compareTo(b.getName()));
			boolean first = true;
			// public fields too (SaveData)
			List<java.lang.reflect.Field> fields = new ArrayList<>();
			for (java.lang.reflect.Field f : o.getClass().getFields())
			{
				if (!Modifier.isStatic(f.getModifiers()))
				{
					fields.add(f);
				}
			}
			fields.sort((a, b) -> a.getName().compareTo(b.getName()));
			for (java.lang.reflect.Field f : fields)
			{
				if (!first)
				{
					out.append(';');
				}
				first = false;
				out.append('.').append(f.getName()).append(':');
				try
				{
					walk(f.get(o), out, depth + 1, seen);
				}
				catch (IllegalAccessException e)
				{
					out.append("!access");
				}
			}
			for (Method m : getters)
			{
				if (!first)
				{
					out.append(';');
				}
				first = false;
				out.append(m.getName()).append(':');
				try
				{
					m.setAccessible(true);
					walk(m.invoke(o), out, depth + 1, seen);
				}
				catch (Exception e)
				{
					Throwable t = e.getCause() == null ? e : e.getCause();
					out.append("!").append(t.getClass().getSimpleName());
				}
			}
			out.append(')');
			seen.remove(o);
		}
		else
		{
			out.append(ours(o.getClass()) ? o.getClass().getSimpleName() : String.valueOf(o));
		}
	}

	/** The first line (one observation) that differs, in full, so a failure can be read. */
	static String firstDifference(String a, String b)
	{
		String[] x = a.split("\n", -1);
		String[] y = b.split("\n", -1);
		for (int i = 0; i < Math.min(x.length, y.length); i++)
		{
			if (!x[i].equals(y[i]))
			{
				int at = 0;
				while (at < Math.min(x[i].length(), y[i].length()) && x[i].charAt(at) == y[i].charAt(at))
				{
					at++;
				}
				int from = Math.max(0, at - 200);
				String key = x[i].contains(" = ") ? x[i].substring(0, x[i].indexOf(" = ")) : x[i];
				return "observation [" + key + "] differs at char " + at + "\n  original: ..." + x[i].substring(from, Math.min(x[i].length(), at + 300))
					+ "\n  rebuilt : ..." + y[i].substring(from, Math.min(y[i].length(), at + 300));
			}
		}
		return "different number of observations: " + x.length + " vs " + y.length;
	}

	static String dumpAll(Object... values)
	{
		return Arrays.stream(values).map(Dumper::dump).reduce("", (x, y) -> x + y + "\n");
	}
}
