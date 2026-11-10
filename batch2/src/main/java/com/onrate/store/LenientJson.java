package com.onrate.store;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.onrate.engine.tracking.KillEntry;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads saved data that may be damaged: walks the JSON along the saved classes and takes out only
 * what can't be read (a null boss or History entry, a count that isn't a number, a list where a map
 * belongs), keeping everything else exactly as it was. Data On Rate wrote itself comes out
 * unchanged, so Gson reads it to the same objects as before.
 *
 * <p>What was taken out is listed in {@link #dropped}, so the caller can keep the original text and
 * say what happened. A null member of a saved object is just absent (Gson doesn't write nulls), so
 * it isn't counted; a null in a map or a list is.
 */
final class LenientJson
{
	private static final String MODEL = "com.onrate.engine.tracking.";

	private final Gson gson;
	/** Where something was taken out, and why. */
	final List<String> dropped = new ArrayList<>();
	private final Map<Class<?>, Map<String, Field>> fieldsOf = new HashMap<>();

	LenientJson(Gson gson)
	{
		this.gson = gson;
	}

	/** A copy of {@code e} with what can't be read as {@code type} taken out, or null when none of it can. */
	JsonElement clean(JsonElement e, Type type, String path)
	{
		if (e == null || e.isJsonNull())
		{
			return null;
		}
		Class<?> raw = TypeToken.get(type).getRawType();
		if (Map.class.isAssignableFrom(raw))
		{
			return cleanMap(e, type, path);
		}
		if (Collection.class.isAssignableFrom(raw))
		{
			return cleanList(e, type, path);
		}
		if (raw == long[].class)
		{
			// the only long[] saved: an imported raid time, its end and its length
			if (!parses(e, type) || e.getAsJsonArray().size() < 2)
			{
				return reject(path, "not a raid time");
			}
			return e;
		}
		if (raw.getName().startsWith(MODEL))
		{
			return cleanObject(e, raw, path);
		}
		return parses(e, type) ? e : reject(path, "not a " + raw.getSimpleName());
	}

	private JsonElement cleanMap(JsonElement e, Type type, String path)
	{
		if (!e.isJsonObject())
		{
			return reject(path, "not a map");
		}
		Type valueType = typeArgument(type, 1);
		JsonObject out = new JsonObject();
		for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet())
		{
			String at = path + "." + entry.getKey();
			JsonElement value = entry.getValue().isJsonNull() ? null : clean(entry.getValue(), valueType, at);
			if (value == null)
			{
				if (entry.getValue().isJsonNull())
				{
					reject(at, "empty");
				}
				continue;
			}
			// the key must read too (a kill count keyed by team size needs a number)
			JsonObject one = new JsonObject();
			one.add(entry.getKey(), value);
			if (!parses(one, type))
			{
				reject(at, "unreadable key");
				continue;
			}
			out.add(entry.getKey(), value);
		}
		return out;
	}

	private JsonElement cleanList(JsonElement e, Type type, String path)
	{
		if (!e.isJsonArray())
		{
			return reject(path, "not a list");
		}
		Type elementType = typeArgument(type, 0);
		JsonArray out = new JsonArray();
		int i = 0;
		for (JsonElement element : e.getAsJsonArray())
		{
			String at = path + "[" + i++ + "]";
			JsonElement value = element.isJsonNull() ? null : clean(element, elementType, at);
			if (value == null)
			{
				if (element.isJsonNull())
				{
					reject(at, "empty");
				}
				continue;
			}
			out.add(value);
		}
		return out;
	}

	private JsonElement cleanObject(JsonElement e, Class<?> raw, String path)
	{
		if (!e.isJsonObject())
		{
			return reject(path, "not a " + raw.getSimpleName());
		}
		Map<String, Field> fields = fields(raw);
		JsonObject out = new JsonObject();
		for (Map.Entry<String, JsonElement> member : e.getAsJsonObject().entrySet())
		{
			Field f = fields.get(member.getKey());
			if (f == null)
			{
				// not one of ours (a newer version's): Gson skips it, and it stays as it was
				out.add(member.getKey(), member.getValue());
				continue;
			}
			if (member.getValue().isJsonNull())
			{
				continue;
			}
			JsonElement value = clean(member.getValue(), f.getGenericType(), path + "." + member.getKey());
			if (value != null)
			{
				out.add(member.getKey(), value);
			}
		}
		if (raw == KillEntry.DropEntry.class)
		{
			// a drop is nothing without its item (saved under its short JSON name)
			String nameKey = jsonName(fields, "name");
			if (nameKey == null || !out.has(nameKey) || !out.get(nameKey).isJsonPrimitive())
			{
				return reject(path, "no item name");
			}
		}
		return parses(out, raw) ? out : reject(path, "not a " + raw.getSimpleName());
	}

	/** The saved fields of a class by their JSON name (as Gson names them). */
	private Map<String, Field> fields(Class<?> raw)
	{
		return fieldsOf.computeIfAbsent(raw, c ->
		{
			Map<String, Field> byName = new HashMap<>();
			for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass())
			{
				for (Field f : k.getDeclaredFields())
				{
					int mods = f.getModifiers();
					if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || f.isSynthetic())
					{
						continue;
					}
					SerializedName name = f.getAnnotation(SerializedName.class);
					byName.put(name == null ? f.getName() : name.value(), f);
				}
			}
			return byName;
		});
	}

	/** The JSON name of the Java field with this name, or null. */
	private static String jsonName(Map<String, Field> fields, String javaName)
	{
		for (Map.Entry<String, Field> e : fields.entrySet())
		{
			if (e.getValue().getName().equals(javaName))
			{
				return e.getKey();
			}
		}
		return null;
	}

	private static Type typeArgument(Type type, int i)
	{
		return type instanceof ParameterizedType ? ((ParameterizedType) type).getActualTypeArguments()[i] : Object.class;
	}

	private boolean parses(JsonElement e, Type type)
	{
		try
		{
			gson.fromJson(e, type);
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
