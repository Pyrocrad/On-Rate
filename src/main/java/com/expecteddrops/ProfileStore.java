package com.expecteddrops;

import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.tracking.KillEntry;
import com.expecteddrops.tracking.RaidRecord;
import com.expecteddrops.tracking.SaveData;
import com.expecteddrops.tracking.Tracker;
import com.expecteddrops.tracking.TrackerState;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and saves one account's data (the main data, plus the History and raids of each
 * boss under their own keys) in the settings of the current RuneScape profile.
 */
final class ProfileStore
{
	private static final Logger log = LoggerFactory.getLogger(ProfileStore.class);
	private static final Type RAIDS_TYPE = new TypeToken<List<RaidRecord>>()
	{
	}.getType();
	private static final Type KILLS_TYPE = new TypeToken<List<KillEntry>>()
	{
	}.getType();

	/** The settings of the current profile. */
	interface Backend
	{
		String get(String key);

		void set(String key, String value);

		void unset(String key);
	}

	static final String BACKUP_SUFFIX = ".corrupt";

	private final Backend backend;
	private final Gson gson;
	private String loadedProfile;

	ProfileStore(Backend backend, Gson gson)
	{
		this.backend = backend;
		this.gson = gson;
	}

	/**
	 * Replaces the tracker's data with the one saved for this profile.
	 *
	 * @return false when nothing was done: no profile yet, or it is already the one loaded
	 */
	synchronized boolean load(String profile, Tracker tracker)
	{
		if (profile == null || profile.equals(loadedProfile))
		{
			return false;
		}
		TrackerState state = read(ExpectedDropsPlugin.DATA_KEY, TrackerState.class);
		if (state != null && state.isFromNewerVersion())
		{
			log.warn("This On Rate data was saved by a newer version; some of it may be ignored");
		}
		tracker.setState(state);
		for (BossDefinition boss : tracker.getCatalog().getBosses())
		{
			String name = boss.getName();
			List<KillEntry> kills = withoutNulls(read(ExpectedDropsPlugin.killsKey(name), KILLS_TYPE));
			if (kills != null)
			{
				tracker.setKills(name, kills);
			}
			if (boss.getRaid() != null)
			{
				List<RaidRecord> raids = withoutNulls(read(ExpectedDropsPlugin.raidsKey(name), RAIDS_TYPE));
				if (raids != null)
				{
					tracker.setRaids(name, raids);
				}
			}
		}
		// only now: if anything above threw, the next call tries this profile again
		loadedProfile = profile;
		return true;
	}

	/** Whether this is the profile whose data the tracker holds (so a save can't land in another one). */
	synchronized boolean isLoaded(String profile)
	{
		return profile != null && profile.equals(loadedProfile);
	}

	/** Reads one saved value. Unreadable data is backed up and read as absent (null). */
	private <T> T read(String key, Type type)
	{
		String json = backend.get(key);
		if (json == null || json.isEmpty())
		{
			return null;
		}
		try
		{
			return gson.fromJson(json, type);
		}
		catch (RuntimeException e)
		{
			log.warn("Could not read the saved On Rate data under {}", key, e);
			backup(key, json);
			return null;
		}
	}

	private <T> T read(String key, Class<T> type)
	{
		return read(key, (Type) type);
	}

	/** The list without the null entries a damaged save can hold (they would fail later, on the Swing thread). */
	private static <T> List<T> withoutNulls(List<T> list)
	{
		if (list == null)
		{
			return null;
		}
		List<T> clean = new ArrayList<>(list.size());
		for (T t : list)
		{
			if (t != null)
			{
				clean.add(t);
			}
		}
		return clean;
	}

	/** Writes what changed since the last save. Does nothing before a profile is loaded. */
	synchronized void save(Tracker tracker)
	{
		if (tracker == null || loadedProfile == null)
		{
			return;
		}
		// put together under the tracker's lock: safe from any thread (shutDown runs on Swing's)
		SaveData data = tracker.saveData(gson::toJson);
		// only the History and raids of bosses that changed are written again; they go first,
		// so raids moved out of the main data by an update are stored before it drops them
		for (Map.Entry<String, String> e : data.kills.entrySet())
		{
			write(ExpectedDropsPlugin.killsKey(e.getKey()), e.getValue());
		}
		for (Map.Entry<String, String> e : data.raids.entrySet())
		{
			write(ExpectedDropsPlugin.raidsKey(e.getKey()), e.getValue());
		}
		backend.set(ExpectedDropsPlugin.DATA_KEY, data.progress);
	}

	/** Forgets which profile is loaded (the plugin stopped). */
	void forget()
	{
		loadedProfile = null;
	}

	/** Keeps unreadable data under a ".corrupt" key, so the next save doesn't destroy it (the first one only). */
	private void backup(String key, String raw)
	{
		// the first unreadable value is the one worth keeping: a later failure must not replace it
		if (backend.get(key + BACKUP_SUFFIX) == null)
		{
			backend.set(key + BACKUP_SUFFIX, raw);
		}
	}

	private void write(String key, String json)
	{
		if (json == null)
		{
			backend.unset(key);
		}
		else
		{
			backend.set(key, json);
		}
	}
}
