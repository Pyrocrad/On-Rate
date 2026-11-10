package com.onrate.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/**
 * Every read and write goes to the profile that was loaded, whichever profile RuneLite has as its
 * current one at that moment (audit F3).
 */
public class ProfileBindingTest
{
	private static final RaidSettings RAIDS = raid -> 0.0;
	private static final long NOW = 1_000_000;

	/**
	 * RuneLite's settings for several RuneScape profiles. {@code active} is RuneLite's current profile
	 * (null at the login screen); the store must never depend on it.
	 */
	private static final class Profiles implements ProfileStore.Backend
	{
		final Map<String, Map<String, String>> data = new HashMap<>();
		final List<String> writes = new ArrayList<>();
		String active;
		/** Keys whose writes fail (the settings refuse them), as profile + "/" + key. */
		final Set<String> refuse = new HashSet<>();

		Map<String, String> of(String profile)
		{
			return data.computeIfAbsent(profile, p -> new HashMap<>());
		}

		@Override
		public String get(String profile, String key)
		{
			return of(profile).get(key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			if (refuse.contains(profile + "/" + key))
			{
				throw new IllegalStateException("refused");
			}
			of(profile).put(key, value);
			writes.add(profile + "/" + key);
		}

		@Override
		public void unset(String profile, String key)
		{
			if (refuse.contains(profile + "/" + key))
			{
				throw new IllegalStateException("refused");
			}
			of(profile).remove(key);
			writes.add(profile + "/-" + key);
		}
	}

	private BossCatalog catalog;
	private Profiles profiles;
	private ProfileStore store;
	private Tracker tracker;

	@Before
	public void setUp()
	{
		catalog = Bundled.catalog(new Gson());
		profiles = new Profiles();
		store = new ProfileStore(profiles, new Gson());
		tracker = new Tracker(catalog);
	}

	private void vorkathKill(Tracker t, int kc, boolean head)
	{
		t.onChatMessage("Your Vorkath kill count is: <col=ff0000>" + kc + "</col>.", NOW + kc * 1000L);
		if (head)
		{
			t.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), NOW + kc * 1000L);
		}
	}

	private void coxRaid(Tracker t, int kc)
	{
		t.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + kc + "</col>.", NOW + kc * 1000L);
		t.onRaidRecord(RaidRecord.cox(25_000, 50_000, 2), NOW + kc * 1000L + 500);
	}

	private Tracker reload(String profile)
	{
		Tracker t = new Tracker(catalog);
		assertTrue(new ProfileStore(profiles, new Gson()).load(profile, t));
		return t;
	}

	private void assertOnlyWritesTo(String profile)
	{
		assertFalse(profiles.writes.isEmpty());
		for (String w : profiles.writes)
		{
			assertTrue(w + " went to another profile than " + profile, w.startsWith(profile + "/"));
		}
	}

	@Test
	public void dataLoadedFromAIsAlwaysWrittenToA()
	{
		profiles.active = "A";
		store.load("A", tracker);
		vorkathKill(tracker, 10, true);
		coxRaid(tracker, 3);
		store.save(tracker);
		assertOnlyWritesTo("A");
		assertTrue(profiles.of("A").containsKey("progress"));
		assertTrue(profiles.of("A").containsKey("kills.Vorkath"));
		assertTrue(profiles.of("A").containsKey("raids.ChambersofXeric"));
	}

	@Test
	public void runeLiteSwitchingToBCannotRedirectAsPendingWrites()
	{
		profiles.active = "A";
		store.load("A", tracker);
		vorkathKill(tracker, 10, true);
		store.save(tracker);
		profiles.writes.clear();
		profiles.of("B").put("progress", "{\"bosses\":{}}");
		Map<String, String> bBefore = new HashMap<>(profiles.of("B"));

		// B logs in: RuneLite's current profile is B before On Rate has loaded it, and a save comes then
		vorkathKill(tracker, 11, true);
		profiles.active = "B";
		store.save(tracker);

		assertOnlyWritesTo("A");
		assertEquals("B was not touched", bBefore, profiles.of("B"));
		assertEquals(11, reload("A").stats("Vorkath", RAIDS).getKillCount());
	}

	@Test
	public void savingWhileLoggedOutKeepsThePendingChanges()
	{
		profiles.active = "A";
		store.load("A", tracker);
		vorkathKill(tracker, 41, true);
		vorkathKill(tracker, 42, true);
		store.save(tracker);

		// at the login screen RuneLite has no current profile; a History entry is removed in the panel
		profiles.active = null;
		com.onrate.engine.tracking.KillEntry kill41 = null;
		for (com.onrate.engine.tracking.HistoryRow h : tracker.history("Vorkath"))
		{
			if (h.getKill().getKc() == 41)
			{
				kill41 = h.getKill();
			}
		}
		assertNotNull(kill41);
		assertTrue(tracker.removeFromHistory("Vorkath", kill41, kill41.getDrops().get(0)));
		store.save(tracker);

		// restarting RuneLite later: the removal was kept
		for (com.onrate.engine.tracking.HistoryRow h : reload("A").history("Vorkath"))
		{
			assertTrue("the removed entry came back", h.getKill().getKc() != 41 || h.getKill().getDrops().isEmpty());
		}
	}

	@Test
	public void aFailedHistoryWriteIsTriedAgainOnTheNextSave()
	{
		store.load("A", tracker);
		vorkathKill(tracker, 10, true);
		profiles.refuse.add("A/kills.Vorkath");
		store.save(tracker);
		assertNull(profiles.of("A").get("kills.Vorkath"));
		// the main data is still written: History has nothing to do with it
		assertNotNull(profiles.of("A").get("progress"));

		// nothing new happens, but the next save writes the History that failed
		profiles.refuse.clear();
		store.save(tracker);
		assertNotNull(profiles.of("A").get("kills.Vorkath"));
		assertEquals(1, reload("A").history("Vorkath").size());
	}

	@Test
	public void aFailedRaidsWriteKeepsItsMarkAndHoldsBackTheMainData()
	{
		store.load("A", tracker);
		coxRaid(tracker, 3);
		profiles.refuse.add("A/raids.ChambersofXeric");
		store.save(tracker);
		assertNull(profiles.of("A").get("raids.ChambersofXeric"));
		// raids moved out of older main data must stay in the saved main data until stored on their own
		assertNull(profiles.of("A").get("progress"));

		profiles.refuse.clear();
		store.save(tracker);
		assertNotNull(profiles.of("A").get("raids.ChambersofXeric"));
		assertNotNull(profiles.of("A").get("progress"));
		assertEquals(1, reload("A").raidRecords("Chambers of Xeric").size());
	}

	@Test
	public void aFailedMainDataWriteDoesNotStopTheNextSave()
	{
		store.load("A", tracker);
		vorkathKill(tracker, 10, false);
		profiles.refuse.add("A/progress");
		store.save(tracker);
		assertNull(profiles.of("A").get("progress"));
		profiles.refuse.clear();
		store.save(tracker);
		assertEquals(10, reload("A").stats("Vorkath", RAIDS).getKillCount());
	}

	@Test
	public void theSavedKeysAndJsonAreExactlyWhatTheTrackerHandsOut()
	{
		// the same play on two trackers: one saved through the store, one serialised directly as
		// before this change (Keys + saveData). Every key and value must be identical.
		Tracker viaStore = new Tracker(catalog);
		Tracker direct = new Tracker(catalog);
		store.load("A", viaStore);
		direct.setState(null);
		for (Tracker t : java.util.Arrays.asList(viaStore, direct))
		{
			vorkathKill(t, 7, true);
			coxRaid(t, 2);
		}
		store.save(viaStore);
		com.onrate.engine.tracking.SaveData d = direct.saveData(new Gson()::toJson);
		Map<String, String> expected = new HashMap<>();
		expected.put(Keys.DATA, d.progress);
		d.kills.forEach((boss, json) -> expected.put(Keys.killsKey(boss), json));
		d.raids.forEach((boss, json) -> expected.put(Keys.raidsKey(boss), json));
		assertEquals(expected, profiles.of("A"));

		// and it reads back
		Tracker loaded = reload("A");
		assertEquals(7, loaded.stats("Vorkath", RAIDS).getKillCount());
		assertEquals(1, loaded.history("Vorkath").size());
		assertEquals(25_000, loaded.raidRecords("Chambers of Xeric").get(0).getPersonalPoints());
	}

	@Test
	public void historyAndRaidsStaySeparateAndUntouchedByOtherChanges()
	{
		store.load("A", tracker);
		vorkathKill(tracker, 10, true);
		coxRaid(tracker, 3);
		store.save(tracker);
		String kills = profiles.of("A").get("kills.Vorkath");
		String raids = profiles.of("A").get("raids.ChambersofXeric");

		profiles.writes.clear();
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>5</col>.", NOW);
		store.save(tracker);
		assertFalse(profiles.writes.contains("A/kills.Vorkath"));
		assertFalse(profiles.writes.contains("A/raids.ChambersofXeric"));
		assertEquals(kills, profiles.of("A").get("kills.Vorkath"));
		assertEquals(raids, profiles.of("A").get("raids.ChambersofXeric"));
	}

	@Test
	public void aSeasonalProfileOfTheSameAccountNeverGetsTheMainProfilesData()
	{
		// RuneLite gives a Leagues world its own profile key for the same account
		store.load("main", tracker);
		vorkathKill(tracker, 100, true);
		store.save(tracker);
		Map<String, String> mainSaved = new HashMap<>(profiles.of("main"));
		profiles.writes.clear();

		profiles.active = "leagues";
		assertTrue(store.load("leagues", tracker));
		BossStats vorkath = tracker.stats("Vorkath", RAIDS);
		assertTrue(vorkath == null || vorkath.getKillCount() == 0);
		tracker.onChatMessage("Your Zulrah kill count is: <col=ff0000>3</col>.", NOW);
		store.save(tracker);

		assertOnlyWritesTo("leagues");
		assertEquals(mainSaved, profiles.of("main"));
		assertFalse(profiles.of("leagues").containsKey("kills.Vorkath"));
	}

	@Test
	public void unreadableDataIsBackedUpInTheProfileItCameFrom()
	{
		profiles.of("A").put("progress", "{not json");
		profiles.active = "B";
		store.load("A", tracker);
		assertEquals("{not json", profiles.of("A").get("progress.corrupt"));
		assertFalse(profiles.of("B").containsKey("progress.corrupt"));
	}
}
