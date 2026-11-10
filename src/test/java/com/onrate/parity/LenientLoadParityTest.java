package com.onrate.parity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.tracking.KillEntry;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.SaveData;
import com.onrate.engine.tracking.Tracker;
import com.onrate.engine.tracking.TrackerState;
import com.onrate.store.Keys;
import com.onrate.store.ProfileStore;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import org.junit.Test;

/**
 * Valid saved data loads exactly as before the damage-tolerant load (audit F5): simulated players'
 * saved data, read the old way (Gson straight from the text) and through ProfileStore, gives the
 * same tracker, and nothing is reported as damaged.
 */
public class LenientLoadParityTest
{
	private static final Type KILLS = new TypeToken<List<KillEntry>>() { }.getType();
	private static final Type RAIDS = new TypeToken<List<RaidRecord>>() { }.getType();

	private static final class Profile implements ProfileStore.Backend
	{
		final Map<String, String> values = new HashMap<>();

		@Override
		public String get(String profile, String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			values.put(key, value);
		}

		@Override
		public void unset(String profile, String key)
		{
			values.remove(key);
		}
	}

	/** Everything a loaded tracker would save: the main data and every boss's History and raids. */
	private static String everything(Tracker t, BossCatalog catalog, Gson gson)
	{
		List<String> all = new ArrayList<>();
		for (BossDefinition b : catalog.getBosses())
		{
			all.add(b.getName());
		}
		t.markUnsaved(all, all);
		SaveData d = t.saveData(gson::toJson);
		return d.progress + "\n" + new java.util.TreeMap<>(d.kills) + "\n" + new java.util.TreeMap<>(d.raids);
	}

	@Test
	public void simulatedPlayersLoadTheSameThroughTheTolerantLoad() throws Exception
	{
		Gson gson = new Gson();
		int players = 0;
		int keys = 0;
		for (int seed = 0; seed < 10; seed++)
		{
			Side side = Side.REBUILT;
			side.newTracker();
			BossCatalog catalog = (BossCatalog) side.catalog;
			Tracker played = (Tracker) side.tracker;
			AtomicInteger team = new AtomicInteger();
			played.setTeamSizeDetectorFor((ToIntFunction<String>) boss -> team.get());
			Profile profile = new Profile();
			ProfileStore store = new ProfileStore(profile, gson);
			store.load("p", played);

			Sim sim = new Sim(31000 + seed, catalog);
			List<BossDefinition> bosses = new ArrayList<>(catalog.getBosses());
			for (int i = 0; i < 6; i++)
			{
				sim.playBoss(bosses.get((seed * 7 + i * 13) % bosses.size()));
			}
			sim.sprinkle();
			side.call("setSettings", sim.settings);
			for (Sim.Op op : sim.ops)
			{
				switch (op.method)
				{
					case "SET_TEAM":
						team.set((Integer) op.args[0]);
						break;
					case "SET_SETTINGS":
						side.call("setSettings", op.args[0]);
						break;
					case "OBSERVE":
					case "SWITCH":
						// account switching has its own store mimic in the parity harness
						break;
					default:
						try
						{
							side.apply(op);
						}
						catch (RuntimeException e)
						{
							// a step both implementations refuse; nothing to save
						}
						store.save(played);
				}
			}
			store.save(played);
			keys += profile.values.size();

			// the old way: Gson straight from the saved text
			Tracker before = new Tracker(catalog);
			before.setState(gson.fromJson(profile.values.get(Keys.DATA), TrackerState.class));
			for (BossDefinition b : catalog.getBosses())
			{
				String k = profile.values.get(Keys.killsKey(b.getName()));
				if (k != null)
				{
					before.setKills(b.getName(), gson.fromJson(k, KILLS));
				}
				String r = b.getRaid() == null ? null : profile.values.get(Keys.raidsKey(b.getName()));
				if (r != null)
				{
					before.setRaids(b.getName(), gson.fromJson(r, RAIDS));
				}
			}
			// now: through the store
			Tracker after = new Tracker(catalog);
			Map<String, String> savedBefore = new HashMap<>(profile.values);
			assertTrue(new ProfileStore(profile, gson).load("p", after));

			assertEquals("seed " + seed, everything(before, catalog, gson), everything(after, catalog, gson));
			assertEquals("loading valid data writes nothing", savedBefore, profile.values);
			for (String key : profile.values.keySet())
			{
				assertFalse("valid data was reported as damaged: " + key, key.endsWith(Keys.CORRUPT_SUFFIX));
			}
			players++;
		}
		System.out.println("lenient load parity: " + players + " simulated players, " + keys + " saved keys identical");
	}
}
