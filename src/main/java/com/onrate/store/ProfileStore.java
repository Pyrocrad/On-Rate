package com.onrate.store;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.tracking.KillEntry;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.SaveData;
import com.onrate.engine.tracking.Tracker;
import com.onrate.engine.tracking.TrackerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and saves one account's data (the main data, plus the History and raids of each boss under
 * their own keys) in the settings of a RuneScape profile.
 *
 * <p>Every read and write names the profile that was loaded. RuneLite's "current" profile changes on
 * its own (it is none at the login screen, and switches as soon as another account logs in), so a save
 * that followed it could be dropped while logged out, or land in the next account. Saves always go to
 * the account whose data the tracker holds.
 */
public final class ProfileStore
{
	private static final Logger log = LoggerFactory.getLogger(ProfileStore.class);
	private static final TypeToken<List<RaidRecord>> RAIDS_TYPE = new TypeToken<List<RaidRecord>>()
	{
	};
	private static final TypeToken<List<KillEntry>> KILLS_TYPE = new TypeToken<List<KillEntry>>()
	{
	};

	/** RuneLite's per-profile settings, always addressed with the RuneScape profile key. */
	public interface Backend
	{
		String get(String profile, String key);

		void set(String profile, String key, String value);

		void unset(String profile, String key);
	}

	private final Backend backend;
	private final Gson gson;
	private String loadedProfile;
	/** True when the last load of a profile failed half way (see {@link #load}); false after one works. */
	private boolean loadFailed;
	/** Saved keys that couldn't be read or backed up when loaded: never written over until the next load. */
	private final java.util.Set<String> keep = new java.util.HashSet<>();

	public ProfileStore(Backend backend, Gson gson)
	{
		this.backend = backend;
		this.gson = gson;
	}

	/**
	 * Replaces the tracker's data with the one saved for this profile.
	 *
	 * <p>Damaged saved data never stops the load, and never loses what is still readable: each value
	 * is read along the saved classes and only the parts that can't be read are left out (see
	 * {@link LenientJson}). Whenever something is left out, the saved text is first kept under the
	 * same key plus {@code .corrupt}, and a warning says what was left out; a value whose backup
	 * can't be written is never written over this session. If loading still fails half way, no
	 * profile counts as loaded, so nothing is saved over the data that wasn't loaded.
	 *
	 * @return false when nothing was done: no profile yet, it is already the one loaded, or it failed
	 */
	public synchronized boolean load(String profile, Tracker tracker)
	{
		if (profile == null || profile.equals(loadedProfile))
		{
			return false;
		}
		loadedProfile = profile;
		loadFailed = false;
		keep.clear();
		try
		{
			String json = backend.get(profile, Keys.DATA);
			TrackerState state = json == null || json.isEmpty() ? null : read(profile, Keys.DATA, json, TypeToken.get(TrackerState.class));
			tracker.setState(state);
			for (BossDefinition boss : tracker.getCatalog().getBosses())
			{
				String key = Keys.killsKey(boss.getName());
				String kills = backend.get(profile, key);
				if (kills != null && !kills.isEmpty())
				{
					tracker.setKills(boss.getName(), read(profile, key, kills, KILLS_TYPE));
				}
			}
			for (BossDefinition boss : tracker.getCatalog().getBosses())
			{
				if (boss.getRaid() == null)
				{
					continue;
				}
				String key = Keys.raidsKey(boss.getName());
				String raids = backend.get(profile, key);
				if (raids != null && !raids.isEmpty())
				{
					tracker.setRaids(boss.getName(), read(profile, key, raids, RAIDS_TYPE));
				}
			}
			return true;
		}
		catch (RuntimeException e)
		{
			// the tracker may hold only part of this profile now: saving it would write over the rest
			log.error("Could not load the saved On Rate data of this profile; nothing will be saved until it loads", e);
			loadedProfile = null;
			loadFailed = true;
			return false;
		}
	}

	/**
	 * One saved value, with what can't be read left out (null when none of it can). When anything was
	 * left out the saved text is kept as a backup first.
	 */
	private <T> T read(String profile, String key, String json, TypeToken<T> type)
	{
		LenientJson lenient = new LenientJson(gson, type);
		JsonElement tree;
		try
		{
			tree = new JsonParser().parse(json);
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read the saved On Rate {}; it is kept as {}", key, key + Keys.CORRUPT_SUFFIX, e);
			backup(profile, key, json);
			return null;
		}
		JsonElement clean = lenient.clean(tree, key);
		T value = null;
		if (clean != null)
		{
			try
			{
				value = gson.getAdapter(type).fromJsonTree(clean);
			}
			catch (RuntimeException e)
			{
				lenient.dropped.add(key + " (" + e.getMessage() + ")");
			}
		}
		if (!lenient.dropped.isEmpty() || (value == null && !tree.isJsonNull()))
		{
			List<String> what = lenient.dropped;
			log.warn("Parts of the saved On Rate {} could not be read and were left out ({}): {}; the saved text is kept as {}",
				key, what.size(), what.subList(0, Math.min(10, what.size())), key + Keys.CORRUPT_SUFFIX);
			backup(profile, key, json);
		}
		return value;
	}

	/** Writes what changed since the last save. Does nothing before a profile is loaded. */
	public synchronized void save(Tracker tracker)
	{
		if (tracker == null || loadedProfile == null)
		{
			return;
		}
		String profile = loadedProfile;
		// put together under the tracker's lock: safe from any thread (shutDown runs on Swing's)
		SaveData data = tracker.saveData(gson::toJson);
		// only the History and raids of bosses that changed are written again; they go first,
		// so raids moved out of the main data by an update are stored before it drops them
		List<String> killsLeft = new ArrayList<>();
		List<String> raidsLeft = new ArrayList<>();
		for (Map.Entry<String, String> e : data.kills.entrySet())
		{
			if (!write(profile, Keys.killsKey(e.getKey()), e.getValue()))
			{
				killsLeft.add(e.getKey());
			}
		}
		for (Map.Entry<String, String> e : data.raids.entrySet())
		{
			if (!write(profile, Keys.raidsKey(e.getKey()), e.getValue()))
			{
				raidsLeft.add(e.getKey());
			}
		}
		if (!killsLeft.isEmpty() || !raidsLeft.isEmpty())
		{
			// not written: the next save tries them again
			tracker.markUnsaved(killsLeft, raidsLeft);
		}
		if (!raidsLeft.isEmpty())
		{
			// the main data may no longer hold raids that just moved out of it: keep the saved one
			// until they are stored under their own key
			return;
		}
		if (keep.contains(Keys.DATA))
		{
			return;
		}
		try
		{
			backend.set(profile, Keys.DATA, data.progress);
		}
		catch (RuntimeException e)
		{
			// the main data is written whole on every save: the next one tries again
			log.warn("Could not save On Rate data", e);
		}
	}

	/** True when the last load failed half way, so no profile counts as loaded. False after a load works. */
	public synchronized boolean loadFailed()
	{
		return loadFailed;
	}

	/** The RuneLite profile key whose data the tracker holds now, or null before one is loaded. */
	public synchronized String loadedProfile()
	{
		return loadedProfile;
	}

	/** Forgets which profile is loaded (the plugin stopped). */
	public synchronized void forget()
	{
		loadedProfile = null;
	}

	/**
	 * Keeps a saved text that couldn't be fully read under its key plus ".corrupt". If even that
	 * fails, the key itself is not written this session, so the original stays where it was.
	 */
	private void backup(String profile, String key, String raw)
	{
		try
		{
			backend.set(profile, key + Keys.CORRUPT_SUFFIX, raw);
		}
		catch (RuntimeException e)
		{
			log.error("Could not keep a copy of the unreadable On Rate {}; it won't be written over", key, e);
			keep.add(key);
		}
	}

	/** Writes one key; false when the settings refused it. */
	private boolean write(String profile, String key, String json)
	{
		if (keep.contains(key))
		{
			// unreadable, and no copy of it could be made: leave the original alone
			return true;
		}
		try
		{
			if (json == null)
			{
				backend.unset(profile, key);
			}
			else
			{
				backend.set(profile, key, json);
			}
			return true;
		}
		catch (RuntimeException e)
		{
			log.warn("Could not save On Rate's {}", key, e);
			return false;
		}
	}
}
