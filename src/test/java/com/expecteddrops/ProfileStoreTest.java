package com.expecteddrops;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.tracking.RaidRecord;
import com.expecteddrops.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** Saving and loading an account's data, and switching between accounts. */
public class ProfileStoreTest
{
	private static final RaidSettings RAIDS = raid -> 0.0;
	private static final long NOW = 1_000_000;

	/** The settings of one RuneScape profile, remembering what was written and in which order. */
	private static final class Settings
	{
		final Map<String, String> values = new HashMap<>();
		final List<String> writes = new ArrayList<>();
		final List<String> removals = new ArrayList<>();
	}

	private BossCatalog catalog;
	private Settings current;
	private ProfileStore store;
	private Tracker tracker;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = new InputStreamReader(
			ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		current = new Settings();
		// like RuneLite: the same backend always reads and writes the profile that is logged in now
		store = new ProfileStore(storeBackend(), new Gson());
		tracker = new Tracker(catalog);
	}

	private ProfileStore.Backend storeBackend()
	{
		return new ProfileStore.Backend()
		{
			@Override
			public String get(String key)
			{
				return current.values.get(key);
			}

			@Override
			public void set(String key, String value)
			{
				current.values.put(key, value);
				current.writes.add(key);
			}

			@Override
			public void unset(String key)
			{
				current.values.remove(key);
				current.removals.add(key);
			}
		};
	}

	private void vorkathKill(int kc, boolean head)
	{
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>" + kc + "</col>.", NOW + kc * 1000L);
		if (head)
		{
			tracker.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), NOW + kc * 1000L);
		}
	}

	/** A fresh tracker loaded from whatever the current profile has saved. */
	private Tracker reload(String profile)
	{
		Tracker t = new Tracker(catalog);
		assertTrue(store.load(profile, t));
		return t;
	}

	@Test
	public void savedDataComesBackWithHistoryAndRaids()
	{
		store.load("main", tracker);
		vorkathKill(42, true);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>7</col>.", NOW);
		tracker.onRaidRecord(RaidRecord.cox(25_000, 50_000, 2), NOW + 1000);
		store.save(tracker);

		assertTrue(current.values.containsKey("progress"));
		assertTrue(current.values.containsKey("kills.Vorkath"));
		assertTrue(current.values.containsKey("raids.ChambersofXeric"));

		// the next login of the same account, with a new session
		Tracker loaded = reload("main again");
		BossStats stats = loaded.stats("Vorkath", RAIDS);
		assertNotNull(stats);
		assertEquals(42, stats.getKillCount());
		assertEquals(1, loaded.history("Vorkath").stream().filter(r -> r.getKill().getKc() == 42).count());
		assertEquals(25_000, loaded.raidRecords("Chambers of Xeric").get(0).getPersonalPoints());
	}

	@Test
	public void nothingSavedStartsEmpty()
	{
		assertTrue(store.load("main", tracker));
		BossStats stats = tracker.stats("Vorkath", RAIDS);
		assertTrue(stats == null || stats.getKillCount() == 0);
		assertTrue(tracker.history("Vorkath").isEmpty());
		assertTrue(current.writes.isEmpty());
	}

	@Test
	public void unreadableDataStartsFreshInsteadOfFailing()
	{
		vorkathKill(10, false);
		current.values.put("progress", "{this is not json");
		current.values.put("kills.Vorkath", "[[[");
		current.values.put("raids.ChambersofXeric", "oops");
		// no exception, and the old in-memory data of the other account is gone
		assertTrue(store.load("main", tracker));
		BossStats stats = tracker.stats("Vorkath", RAIDS);
		assertTrue(stats == null || stats.getKillCount() == 0);
		assertTrue(tracker.history("Vorkath").isEmpty());
		assertTrue(tracker.raidRecords("Chambers of Xeric").isEmpty());
		// and saving afterwards replaces the unreadable data with valid data
		vorkathKill(11, false);
		store.save(tracker);
		assertEquals(11, reload("again").stats("Vorkath", RAIDS).getKillCount());
	}

	@Test
	public void noProfileOrTheSameProfileLoadsNothing()
	{
		assertFalse(store.load(null, tracker));
		assertTrue(store.load("main", tracker));
		vorkathKill(5, false);
		// RuneLite sends the same profile again on every login: don't throw the session away
		assertFalse(store.load("main", tracker));
		assertEquals(5, tracker.stats("Vorkath", RAIDS).getKillCount());
	}

	@Test
	public void nothingIsSavedBeforeAProfileIsLoaded()
	{
		vorkathKill(5, true);
		store.save(tracker);
		store.save(null);
		assertTrue(current.writes.isEmpty());
		assertTrue(current.values.isEmpty());
	}

	@Test
	public void forgettingTheProfileStopsSaving()
	{
		store.load("main", tracker);
		store.forget();
		vorkathKill(5, true);
		store.save(tracker);
		assertTrue(current.writes.isEmpty());
		// the same profile counts as new again afterwards
		assertTrue(store.load("main", new Tracker(catalog)));
	}

	@Test
	public void switchingAccountsShowsOnlyTheNewAccount()
	{
		Settings a = new Settings();
		Settings b = new Settings();
		current = a;
		store.load("A", tracker);
		vorkathKill(100, true);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>50</col>.", NOW);
		store.save(tracker);

		// B has only a Zulrah kill count
		current = b;
		Tracker other = new Tracker(catalog);
		ProfileStore otherStore = new ProfileStore(storeBackend(), new Gson());
		otherStore.load("B", other);
		other.onChatMessage("Your Zulrah kill count is: <col=ff0000>7</col>.", NOW);
		otherStore.save(other);

		// the same tracker moves to B
		current = b;
		assertTrue(store.load("B", tracker));
		BossStats vorkath = tracker.stats("Vorkath", RAIDS);
		assertTrue("A's Vorkath leaked into B", vorkath == null || vorkath.getKillCount() == 0);
		assertTrue(tracker.history("Vorkath").isEmpty());
		assertEquals(7, tracker.stats("Zulrah", RAIDS).getKillCount());
	}

	@Test
	public void savingAfterASwitchNeverWritesTheOldAccountIntoTheNewOne()
	{
		Settings a = new Settings();
		Settings b = new Settings();
		current = a;
		store.load("A", tracker);
		vorkathKill(100, true);
		store.save(tracker);
		Map<String, String> savedA = new HashMap<>(a.values);

		// A's last kill was never saved: it must not end up in B after the switch
		vorkathKill(101, true);
		current = b;
		store.load("B", tracker);
		store.save(tracker);

		assertEquals("A's settings changed after leaving it", savedA, a.values);
		for (String key : b.values.keySet())
		{
			assertFalse("A's " + key + " was written into B", key.startsWith("kills.") || key.startsWith("raids."));
		}
		assertFalse(b.values.get("progress").contains("Vorkath's head"));
	}

	@Test
	public void onlyBossesThatChangedAreWrittenAgain()
	{
		store.load("main", tracker);
		vorkathKill(10, false);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>5</col>.", NOW);
		store.save(tracker);
		assertTrue(current.writes.contains("kills.Vorkath"));
		assertTrue(current.writes.contains("kills.Zulrah"));

		current.writes.clear();
		vorkathKill(11, false);
		store.save(tracker);
		assertTrue(current.writes.contains("kills.Vorkath"));
		assertFalse(current.writes.contains("kills.Zulrah"));

		// nothing changed: only the small main data is written
		current.writes.clear();
		store.save(tracker);
		assertEquals(Collections.singletonList("progress"), current.writes);
	}

	@Test
	public void historyAndRaidsAreWrittenBeforeTheMainData()
	{
		store.load("main", tracker);
		vorkathKill(10, true);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>7</col>.", NOW);
		tracker.onRaidRecord(RaidRecord.cox(25_000, 50_000, 2), NOW + 1000);
		store.save(tracker);
		// if saving stops half way, raids that moved out of the main data are never lost
		assertEquals("progress", current.writes.get(current.writes.size() - 1));
		assertTrue(current.writes.indexOf("raids.ChambersofXeric") < current.writes.indexOf("progress"));
		assertTrue(current.writes.indexOf("kills.Vorkath") < current.writes.indexOf("progress"));
	}

	@Test
	public void resettingABossRemovesItsSavedHistory()
	{
		store.load("main", tracker);
		vorkathKill(10, true);
		store.save(tracker);
		assertTrue(current.values.containsKey("kills.Vorkath"));

		tracker.reset("Vorkath");
		store.save(tracker);
		assertFalse(current.values.containsKey("kills.Vorkath"));
		assertTrue(current.removals.contains("kills.Vorkath"));
		BossStats stats = reload("fresh").stats("Vorkath", RAIDS);
		assertTrue(stats == null || stats.getKillCount() == 0);
	}
}
