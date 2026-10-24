package com.expecteddrops;

import com.expecteddrops.tracking.KillEntry;
import com.expecteddrops.tracking.RaidRecord;
import com.expecteddrops.tracking.SaveData;
import com.expecteddrops.tracking.Tracker;
import com.expecteddrops.tracking.TrackerState;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
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
	boolean load(String profile, Tracker tracker)
	{
		if (profile == null || profile.equals(loadedProfile))
		{
			return false;
		}
		loadedProfile = profile;
		TrackerState state = null;
		String json = backend.get(ExpectedDropsPlugin.DATA_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				state = gson.fromJson(json, TrackerState.class);
			}
			catch (Exception e)
			{
				log.warn("Could not read saved On Rate data, starting fresh", e);
			}
		}
		tracker.setState(state);
		for (com.expecteddrops.data.BossDefinition boss : tracker.getCatalog().getBosses())
		{
			String kills = backend.get(ExpectedDropsPlugin.killsKey(boss.getName()));
			if (kills == null || kills.isEmpty())
			{
				continue;
			}
			try
			{
				tracker.setKills(boss.getName(), gson.fromJson(kills, KILLS_TYPE));
			}
			catch (Exception e)
			{
				log.warn("Could not read the saved On Rate history of {}", boss.getName(), e);
			}
		}
		for (com.expecteddrops.data.BossDefinition boss : tracker.getCatalog().getBosses())
		{
			if (boss.getRaid() == null)
			{
				continue;
			}
			String raids = backend.get(ExpectedDropsPlugin.raidsKey(boss.getName()));
			if (raids == null || raids.isEmpty())
			{
				continue;
			}
			try
			{
				tracker.setRaids(boss.getName(), gson.fromJson(raids, RAIDS_TYPE));
			}
			catch (Exception e)
			{
				log.warn("Could not read the saved On Rate raids of {}", boss.getName(), e);
			}
		}
		return true;
	}

	/** Writes what changed since the last save. Does nothing before a profile is loaded. */
	void save(Tracker tracker)
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
