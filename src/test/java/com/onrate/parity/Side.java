package com.onrate.parity;

import com.google.gson.Gson;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * One of the two implementations under test, driven by reflection so that exactly the same
 * scenario script runs against the original ({@code com.expecteddrops}) and the rebuilt
 * ({@code com.onrate.engine}) classes.
 */
final class Side
{
	// preload bundled data with a Gson, as the plugin does at startup (bundled() alone is empty until then)
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new com.google.gson.Gson());
		com.onrate.engine.data.LadlorChart.bundled(new com.google.gson.Gson());
	}

	static final Side ORIGINAL = new Side("original", "legacy.expecteddrops");
	static final Side REBUILT = new Side("rebuilt", "com.onrate.engine");

	/** A loot item argument (the nested class differs per side). */
	static final class Loot
	{
		final String name;
		final int quantity;

		Loot(String name, int quantity)
		{
			this.name = name;
			this.quantity = quantity;
		}
	}

	/** A raid record built through its static factory ({@code cox}, {@code toa}, ...). */
	static final class Raid
	{
		final String factory;
		final Object[] args;

		Raid(String factory, Object... args)
		{
			this.factory = factory;
			this.args = args;
		}
	}

	/** Player settings as plain numbers, adapted to each side's {@code RaidSettings}. */
	static final class Settings
	{
		double cox = 0.035, coxCm = 0.05, tob = 0.027, tobHm = 0.032, toaEntry = 0.0075, toa = 0.025, toaExpert = 0.05,
			fractured = 1 / 28.0;
		int tobTeam = 4;
		int tfaTeam = 2;
		/** Share of kills counted as duo at the duo-team bosses, 0..1. */
		double duoShare = 1.0;
	}

	final String name;
	final String prefix;
	final Gson gson = new Gson();
	Object tracker;
	Object catalog;
	/** Saved settings of every account, as RuneLite's profile would hold them (key to JSON). */
	final java.util.Map<String, java.util.Map<String, String>> accounts = new java.util.HashMap<>();
	String account = "main";

	private Side(String name, String prefix)
	{
		this.name = name;
		this.prefix = prefix;
	}

	Class<?> cls(String sub, String simple)
	{
		try
		{
			return Class.forName(prefix + "." + sub + "." + simple);
		}
		catch (ClassNotFoundException e)
		{
			throw new IllegalStateException(e);
		}
	}

	/** Creates a fresh tracker over a fresh catalog from this side's own copy of bosses.json. */
	Object newTracker() throws Exception
	{
		Class<?> catalogClass = cls("data", "BossCatalog");
		String path = this == ORIGINAL ? "/legacy/expecteddrops/bosses.json" : "/com/onrate/bosses.json";
		try (java.io.Reader reader = new java.io.InputStreamReader(Side.class.getResourceAsStream(path),
			java.nio.charset.StandardCharsets.UTF_8))
		{
			catalog = catalogClass.getMethod("load", Gson.class, java.io.Reader.class).invoke(null, new Gson(), reader);
		}
		tracker = cls("tracking", "Tracker").getConstructor(catalogClass).newInstance(catalog);
		accounts.clear();
		account = "main";
		return tracker;
	}

	/**
	 * Runs one step of a simulated player: a public tracker method, or one of the steps that need this
	 * side's own objects (removing an entry from History, switching account).
	 */
	Object apply(Sim.Op op)
	{
		switch (op.method)
		{
			case "SWITCH":
				switchAccount((String) op.args[0]);
				return "switched to " + op.args[0];
			case "REMOVE_HISTORY":
				return removeFromHistory((String) op.args[0], (Integer) op.args[1]);
			default:
				return call(op.method, op.args);
		}
	}

	/** Removes the first drop of the n-th History line that has one, as the panel's menu would. */
	private Object removeFromHistory(String boss, int n)
	{
		List<?> rows = (List<?>) call("history", boss);
		List<Object> withDrops = new ArrayList<>();
		for (Object row : rows)
		{
			Object kill = callOn(row, "getKill");
			if (!((List<?>) callOn(kill, "getDrops")).isEmpty())
			{
				withDrops.add(kill);
			}
		}
		if (withDrops.isEmpty())
		{
			return "nothing to remove";
		}
		Object kill = withDrops.get(n % withDrops.size());
		Object drop = ((List<?>) callOn(kill, "getDrops")).get(0);
		return call("removeFromHistory", boss, kill, drop);
	}

	/**
	 * What the plugin does when another account logs in: save this one as the profile store does (the
	 * main data, then each changed History and raid list, a null meaning "remove"), then load the other
	 * one the same way (state, then every boss's History, then every raid's records).
	 */
	private void switchAccount(String to)
	{
		saveAccount();
		account = to;
		loadAccount(accounts.computeIfAbsent(to, k -> new java.util.HashMap<>()));
	}

	/** Saves the current account into its store, as the profile store does, and returns that store. */
	java.util.Map<String, String> saveAccount()
	{
		Object save = call("saveData", (java.util.function.Function<Object, String>) gson::toJson);
		java.util.Map<String, String> store = accounts.computeIfAbsent(account, k -> new java.util.HashMap<>());
		try
		{
			@SuppressWarnings("unchecked") java.util.Map<String, String> kills = (java.util.Map<String, String>) save.getClass().getField("kills").get(save);
			@SuppressWarnings("unchecked") java.util.Map<String, String> raids = (java.util.Map<String, String>) save.getClass().getField("raids").get(save);
			for (java.util.Map.Entry<String, String> e : kills.entrySet())
			{
				if (e.getValue() == null)
				{
					store.remove("kills." + e.getKey());
				}
				else
				{
					store.put("kills." + e.getKey(), e.getValue());
				}
			}
			for (java.util.Map.Entry<String, String> e : raids.entrySet())
			{
				if (e.getValue() == null)
				{
					store.remove("raids." + e.getKey());
				}
				else
				{
					store.put("raids." + e.getKey(), e.getValue());
				}
			}
			store.put("progress", (String) save.getClass().getField("progress").get(save));
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException(e);
		}
		return store;
	}

	/** Loads an account from a store, as the profile store does: state, then every History, then every raid list. */
	void loadAccount(java.util.Map<String, String> next)
	{
		String json = next.get("progress");
		call("setState", json == null || json.isEmpty() ? null : gson.fromJson(json, cls("tracking", "TrackerState")));
		java.lang.reflect.Type killList = com.google.gson.reflect.TypeToken.getParameterized(List.class, cls("tracking", "KillEntry")).getType();
		java.lang.reflect.Type raidList = com.google.gson.reflect.TypeToken.getParameterized(List.class, cls("tracking", "RaidRecord")).getType();
		List<?> bosses = (List<?>) callOn(catalog, "getBosses");
		for (Object boss : bosses)
		{
			String name = (String) callOn(boss, "getName");
			String kills = next.get("kills." + name);
			if (kills != null && !kills.isEmpty())
			{
				call("setKills", name, gson.fromJson(kills, killList));
			}
		}
		for (Object boss : bosses)
		{
			if (callOn(boss, "getRaid") == null)
			{
				continue;
			}
			String name = (String) callOn(boss, "getName");
			String raids = next.get("raids." + name);
			if (raids != null && !raids.isEmpty())
			{
				call("setRaids", name, gson.fromJson(raids, raidList));
			}
		}
	}

	/** Calls a public method of the tracker, converting the argument wrappers to this side's types. */
	Object call(String method, Object... args)
	{
		return callOn(tracker, method, args);
	}

	Object callStatic(String sub, String type, String method, Object... args)
	{
		return callOn(cls(sub, type), method, args);
	}

	Object callOn(Object target, String method, Object... args)
	{
		boolean isClass = target instanceof Class;
		Class<?> type = isClass ? (Class<?>) target : target.getClass();
		Object[] converted = new Object[args.length];
		for (int i = 0; i < args.length; i++)
		{
			converted[i] = convert(args[i]);
		}
		for (Method m : type.getMethods())
		{
			if (!m.getName().equals(method) || m.getParameterCount() != args.length || !fits(m, converted))
			{
				continue;
			}
			if (isClass && !Modifier.isStatic(m.getModifiers()))
			{
				continue;
			}
			try
			{
				return m.invoke(isClass ? null : target, converted);
			}
			catch (InvocationTargetException e)
			{
				Throwable cause = e.getCause();
				if (cause instanceof RuntimeException)
				{
					throw (RuntimeException) cause;
				}
				throw new IllegalStateException(cause);
			}
			catch (IllegalAccessException e)
			{
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalArgumentException("No " + method + "/" + args.length + " on " + type.getName());
	}

	private Object convert(Object arg)
	{
		try
		{
			if (arg instanceof Raid)
			{
				Raid r = (Raid) arg;
				return callOn(cls("tracking", "RaidRecord"), r.factory, r.args);
			}
			if (arg instanceof Settings)
			{
				return settingsProxy((Settings) arg);
			}
			if (arg instanceof List && (((List<?>) arg).isEmpty() || ((List<?>) arg).get(0) instanceof Loot))
			{
				List<Object> items = new ArrayList<>();
				Class<?> lootClass = cls("tracking", "Tracker$LootItem");
				for (Object l : (List<?>) arg)
				{
					Loot loot = (Loot) l;
					items.add(lootClass.getConstructor(String.class, int.class).newInstance(loot.name, loot.quantity));
				}
				return items;
			}
		}
		catch (ReflectiveOperationException e)
		{
			throw new IllegalStateException(e);
		}
		return arg;
	}

	private boolean fits(Method m, Object[] args)
	{
		Class<?>[] types = m.getParameterTypes();
		for (int i = 0; i < types.length; i++)
		{
			Object a = args[i];
			Class<?> t = types[i];
			if (a == null)
			{
				if (t.isPrimitive())
				{
					return false;
				}
				continue;
			}
			if (t.isPrimitive())
			{
				if (!primitiveFits(t, a))
				{
					return false;
				}
			}
			else if (!t.isInstance(a))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean primitiveFits(Class<?> t, Object a)
	{
		return t == int.class && a instanceof Integer || t == long.class && a instanceof Long
			|| t == double.class && a instanceof Double || t == boolean.class && a instanceof Boolean;
	}

	/** This side's RaidSettings for these settings (for building its panel). */
	Object raidSettings(Settings s)
	{
		return settingsProxy(s);
	}

	private Object settingsProxy(Settings s)
	{
		Class<?> iface = cls("stats", "RaidSettings");
		InvocationHandler handler = (proxy, method, args) ->
		{
			switch (method.getName())
			{
				case "uniqueChance":
					switch ((String) args[0])
					{
						case "cox": return s.cox;
						case "coxCm": return s.coxCm;
						case "tob": return s.tob;
						case "tobHm": return s.tobHm;
						case "toaEntry": return s.toaEntry;
						case "toa": return s.toa;
						case "toaExpert": return s.toaExpert;
						case "fractured": return s.fractured;
						default: return 0.0;
					}
				case "teamSize":
					String raid = (String) args[0];
					return raid.startsWith("tob") ? s.tobTeam : "fractured".equals(raid) ? s.tfaTeam : 1;
				case "tfaBaseChance":
					return 1 / 14.0;
				case "contribution":
					return "royalTitans".equals(args[0]) || "yama".equals(args[0]) ? 1.0 - 0.5 * s.duoShare : 1.0;
				case "contributionNote":
					return null;
				case "learnedRaids":
					// the parity settings are fixed: nothing learned from recorded raids
					return 0;
				case "toString":
					return "settings";
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				default:
					throw new UnsupportedOperationException(method.getName());
			}
		};
		return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, handler);
	}
}
