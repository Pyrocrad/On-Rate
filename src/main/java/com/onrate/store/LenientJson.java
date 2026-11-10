package com.onrate.store;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads saved data that may be damaged: finds what can't be read and takes out only that (a null boss
 * or History entry, a count that isn't a number, a list where a map belongs), keeping everything else
 * exactly as it was. Data On Rate wrote itself comes out unchanged, so Gson reads it to the same
 * objects as before.
 *
 * <p>It never looks at the saved classes themselves. Each part of the saved text is tried by itself:
 * Gson reads a copy of the whole text that holds just that part (and what leads to it), so a part
 * that doesn't read is found, and what it holds is tried in turn. Parts of a saved object, entries of
 * a map and elements of a list are read independently of each other, so the rest is unaffected.
 *
 * <p>What was taken out is listed in {@link #dropped}, so the caller can keep the original text and
 * say what happened. A null in an object, a map or a list is taken out and counted (Gson never
 * writes one).
 */
final class LenientJson
{
	/** A way through the saved text: a member name, or null for "an element of a list". */
	private static final String ELEMENT = null;

	private final TypeAdapter<?> adapter;
	/** Where something was taken out, and why. */
	final List<String> dropped = new ArrayList<>();

	/** {@code type} is what the whole saved text is read as. */
	LenientJson(Gson gson, TypeToken<?> type)
	{
		this.adapter = gson.getAdapter(type);
	}

	/** A copy of {@code root} with what can't be read taken out, or null when none of it can. */
	JsonElement clean(JsonElement root, String path)
	{
		return prune(root, new ArrayList<>(), path);
	}

	private JsonElement prune(JsonElement e, List<String> steps, String path)
	{
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		String broken = rule(steps, e);
		if (broken != null)
		{
			return reject(path, broken);
		}
		// Gson reads a null in a list or map and a drop without a name, so look inside before trusting it
		if (!hasProblem(steps, e) && reads(steps, e))
		{
			return e;
		}
		if (e.isJsonObject())
		{
			JsonObject out = new JsonObject();
			for (Map.Entry<String, JsonElement> member : e.getAsJsonObject().entrySet())
			{
				if (member.getValue().isJsonNull())
				{
					reject(path + "." + member.getKey(), "empty");
					continue;
				}
				JsonElement kept = prune(member.getValue(), with(steps, member.getKey()), path + "." + member.getKey());
				if (kept != null)
				{
					out.add(member.getKey(), kept);
				}
			}
			return checked(steps, out, path);
		}
		if (e.isJsonArray())
		{
			JsonArray in = e.getAsJsonArray();
			if (onlyValues(in))
			{
				// a list of plain values that doesn't read stays whole or goes whole (a raid time is a pair)
				return reject(path, "unreadable");
			}
			JsonArray out = new JsonArray();
			int i = 0;
			for (JsonElement element : in)
			{
				String at = path + "[" + i++ + "]";
				if (element.isJsonNull())
				{
					reject(at, "empty");
					continue;
				}
				JsonElement kept = prune(element, with(steps, ELEMENT), at);
				if (kept != null)
				{
					out.add(kept);
				}
			}
			return checked(steps, out, path);
		}
		return reject(path, "unreadable");
	}

	/** Whether anything inside {@code e} breaks a rule or is null, whatever Gson makes of it. */
	private static boolean hasProblem(List<String> steps, JsonElement e)
	{
		if (e.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> member : e.getAsJsonObject().entrySet())
			{
				JsonElement value = member.getValue();
				if (value.isJsonNull() || rule(with(steps, member.getKey()), value) != null || hasProblem(with(steps, member.getKey()), value))
				{
					return true;
				}
			}
		}
		else if (e.isJsonArray())
		{
			for (JsonElement element : e.getAsJsonArray())
			{
				if (element.isJsonNull() || rule(with(steps, ELEMENT), element) != null || hasProblem(with(steps, ELEMENT), element))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** What is left of a part once what can't be read is out: kept when it reads and breaks no rule. */
	private JsonElement checked(List<String> steps, JsonElement out, String path)
	{
		String broken = rule(steps, out);
		if (broken != null)
		{
			return reject(path, broken);
		}
		return reads(steps, out) ? out : reject(path, "unreadable");
	}

	/**
	 * Two things Gson reads without complaint but On Rate can't use: a drop with no item name (saved
	 * under {@code i}, in a kill's {@code d} list), and a raid time that isn't a pair (its end and its length).
	 */
	private static String rule(List<String> steps, JsonElement e)
	{
		int n = steps.size();
		if (n >= 2 && steps.get(n - 1) == ELEMENT && "d".equals(steps.get(n - 2)) && e.isJsonObject())
		{
			JsonElement name = e.getAsJsonObject().get("i");
			if (name == null || !name.isJsonPrimitive())
			{
				return "no item name";
			}
		}
		if (n >= 3 && steps.get(n - 1) == ELEMENT && "importedTimes".equals(steps.get(n - 3)))
		{
			if (!e.isJsonArray() || e.getAsJsonArray().size() < 2)
			{
				return "not a raid time";
			}
		}
		return null;
	}

	private static boolean onlyValues(JsonArray array)
	{
		for (JsonElement element : array)
		{
			if (!element.isJsonPrimitive())
			{
				return false;
			}
		}
		return true;
	}

	private static List<String> with(List<String> steps, String next)
	{
		List<String> longer = new ArrayList<>(steps);
		longer.add(next);
		return longer;
	}

	/** Whether Gson reads the saved text when it holds only {@code part}, at the place {@code steps} lead to. */
	private boolean reads(List<String> steps, JsonElement part)
	{
		JsonElement tree = part;
		for (int i = steps.size() - 1; i >= 0; i--)
		{
			String step = steps.get(i);
			if (step == ELEMENT)
			{
				JsonArray list = new JsonArray();
				list.add(tree);
				tree = list;
			}
			else
			{
				JsonObject holder = new JsonObject();
				holder.add(step, tree);
				tree = holder;
			}
		}
		try
		{
			adapter.fromJsonTree(tree);
			return true;
		}
		catch (RuntimeException ex)
		{
			return false;
		}
	}

	private JsonElement reject(String path, String why)
	{
		dropped.add(path + " (" + why + ")");
		return null;
	}
}
