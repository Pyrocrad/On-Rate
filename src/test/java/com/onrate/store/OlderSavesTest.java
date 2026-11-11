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
import com.onrate.engine.tracking.RaidInsights;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/**
 * Saved data as earlier versions of On Rate wrote it (fewer fields, raids inside the main data, a boss
 * that has since left the catalogue) and as a later one might (fields this version doesn't know): it
 * loads, nothing counts as damage, and nothing is lost when it is saved again.
 */
public class OlderSavesTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;
	private static final long NOW = 1_700_000_000_000L;
	private static final String COX = "Chambers of Xeric";
	private static final String COX_RAIDS = "raids.ChambersofXeric";

	/** The settings of one profile; remembers every write, and can refuse some. */
	private static final class Profile implements ProfileStore.Backend
	{
		final Map<String, String> values = new HashMap<>();
		final List<String> writes = new ArrayList<>();
		final Set<String> refuse = new HashSet<>();

		@Override
		public String get(String profile, String key)
		{
			return values.get(key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			if (refuse.contains(key))
			{
				throw new IllegalStateException("refused");
			}
			values.put(key, value);
			writes.add(key);
		}

		@Override
		public void unset(String profile, String key)
		{
			if (refuse.contains(key))
			{
				throw new IllegalStateException("refused");
			}
			values.remove(key);
			writes.add("-" + key);
		}
	}

	private BossCatalog catalog;
	private Profile saved;

	@Before
	public void setUp()
	{
		catalog = Bundled.catalog(new Gson());
		saved = new Profile();
	}

	private Tracker load(ProfileStore store)
	{
		Tracker t = new Tracker(catalog);
		t.setSettings(RAIDS);
		assertTrue(store.load("main", t));
		return t;
	}

	private Tracker load()
	{
		return load(new ProfileStore(saved, new Gson()));
	}

	private void assertNothingCountedAsDamage()
	{
		for (String key : saved.values.keySet())
		{
			assertFalse(key + " was kept as a damaged copy", key.endsWith(".corrupt"));
		}
	}

	@Test
	public void aRaidFromBeforeTimesDeathsAndPlayersLoadsWithThoseUnknown()
	{
		saved.values.put("progress", "{\"bosses\":{\"Chambers of Xeric\":{\"chatKc\":{\"chambers of xeric\":7}}}}");
		saved.values.put(COX_RAIDS, "[{\"family\":\"cox\",\"part\":\"Normal\",\"kc\":7,\"personalPoints\":25000,"
			+ "\"teamPoints\":50000,\"teamSize\":2}]");
		Tracker t = load();
		assertNothingCountedAsDamage();

		RaidRecord r = t.raidRecords(COX).get(0);
		assertEquals("no time", -1, r.getDurationMillis());
		assertEquals("not a Tombs raid", -1, r.getRaidLevel());
		assertEquals("deaths unknown", -1, r.getDeaths());
		assertEquals("contribution unknown", -1, r.getContribution(), 0);

		// the raid sections count it for the chance, and never as a timed raid, a deathless raid or a best time
		RaidInsights insights = t.raidInsights(COX, null);
		assertEquals(1, insights.getSetups().size());
		RaidInsights.Setup setup = insights.getSetups().get(0);
		assertEquals(1, setup.getRaids());
		assertEquals(0, setup.getTimed());
		assertTrue(Double.isNaN(setup.getChancePerHour()));
		assertEquals("a duo whose players at the end were never read", -1, setup.getPlayers());
		RaidInsights.Bests bests = insights.getBests().get(0);
		assertNull(bests.getFastest());
		assertNull(bests.getHighestLevel());
		assertNull(bests.getBiggestShare());
		assertEquals(0, bests.getDeathsKnown());
		assertEquals(25_000, (int) bests.getMostPoints().getValue());
		assertEquals("when it was saved is unknown", 0, bests.getMostPoints().getSavedMillis());
	}

	@Test
	public void aSaveWithOnlyCountsLoadsAndComesBackTheSame()
	{
		saved.values.put("progress", "{\"bosses\":{\"Vorkath\":{\"chatKc\":{\"vorkath\":50},\"received\":{\"vorkath's head\":1}}}}");
		ProfileStore store = new ProfileStore(saved, new Gson());
		Tracker t = load(store);
		assertNothingCountedAsDamage();
		BossStats stats = t.stats("Vorkath", RAIDS);
		assertEquals(50, stats.getKillCount());
		assertEquals(1, stats.getAnyRow().getReceived());
		// everything added since (the days, the luck graph, shared items) starts empty and works
		assertTrue(t.history().isEmpty());
		// (no point yet: the log page was never opened, so the boss waits out of the account total)
		assertFalse(t.recordHistory(20_000, RAIDS));
		assertNotNull(t.recentDays(7, NOW));
		assertNotNull(t.sharedPools(RAIDS));

		t.onChatMessage("Your Vorkath kill count is: <col=ff0000>51</col>.", NOW);
		store.save(t);
		Tracker again = load();
		assertEquals(51, again.stats("Vorkath", RAIDS).getKillCount());
		assertEquals(1, again.stats("Vorkath", RAIDS).getAnyRow().getReceived());
		assertEquals(1, again.killsSeen("Vorkath"));
	}

	@Test
	public void historyFromBeforeTimesLoads()
	{
		saved.values.put("progress", "{\"bosses\":{\"Vorkath\":{\"chatKc\":{\"vorkath\":6}}}}");
		saved.values.put("kills.Vorkath", "[{\"k\":5,\"t\":1000},{\"k\":6,\"t\":2000,\"d\":[{\"i\":\"Vorkath's head\",\"s\":6}]}]");
		Tracker t = load();
		assertNothingCountedAsDamage();
		assertEquals(2, t.killsSeen("Vorkath"));
		assertNull("no kill has a time", t.killTimes("Vorkath", null));
		assertEquals(1, t.recentDrops(10).size());
		assertEquals("Vorkath's head", t.recentDrops(10).get(0).getDrop().getName());
		assertNull(t.recentDrops(10).get(0).getKill().getDurationMillis());
	}

	@Test
	public void fieldsALaterVersionAddedAreNotDamage()
	{
		saved.values.put("progress", "{\"somethingNew\":{\"a\":1},\"bosses\":{\"Vorkath\":{\"chatKc\":{\"vorkath\":50},"
			+ "\"alsoNew\":[1,2,3]}}}");
		saved.values.put("kills.Vorkath", "[{\"k\":50,\"t\":1000,\"zz\":true}]");
		saved.values.put(COX_RAIDS, "[{\"family\":\"cox\",\"part\":\"Normal\",\"kc\":7,\"personalPoints\":25000,"
			+ "\"teamPoints\":50000,\"teamSize\":2,\"newField\":\"x\"}]");
		Tracker t = load();
		assertNothingCountedAsDamage();
		assertEquals(50, t.stats("Vorkath", RAIDS).getKillCount());
		assertEquals(1, t.killsSeen("Vorkath"));
		assertEquals(1, t.raidRecords(COX).size());
	}

	@Test
	public void aBossThatLeftTheCatalogueKeepsItsSavedData()
	{
		String gone = "\"Some Old Boss\":{\"chatKc\":{\"some old boss\":12},\"received\":{\"old item\":2}}";
		saved.values.put("progress", "{\"bosses\":{" + gone + ",\"Vorkath\":{\"chatKc\":{\"vorkath\":50}}}}");
		saved.values.put("kills.SomeOldBoss", "[{\"k\":12,\"t\":1000}]");
		saved.values.put("raids.SomeOldBoss", "[{\"family\":\"cox\",\"kc\":1}]");
		ProfileStore store = new ProfileStore(saved, new Gson());
		Tracker t = load(store);
		assertNothingCountedAsDamage();
		assertNull("not a boss On Rate shows", t.stats("Some Old Boss", RAIDS));

		t.onChatMessage("Your Vorkath kill count is: <col=ff0000>51</col>.", NOW);
		t.recordHistory(20_000, RAIDS);
		store.save(t);
		// its numbers are still in the main data, and its History and raids were never touched
		assertTrue(saved.values.get("progress"), saved.values.get("progress").contains("\"Some Old Boss\""));
		assertTrue(saved.values.get("progress").contains("\"some old boss\":12"));
		assertTrue(saved.values.get("progress").contains("\"old item\":2"));
		assertEquals("[{\"k\":12,\"t\":1000}]", saved.values.get("kills.SomeOldBoss"));
		assertEquals("[{\"family\":\"cox\",\"kc\":1}]", saved.values.get("raids.SomeOldBoss"));
		assertFalse(saved.writes.contains("kills.SomeOldBoss"));
		assertFalse(saved.writes.contains("-kills.SomeOldBoss"));
		assertFalse(saved.writes.contains("raids.SomeOldBoss"));
		assertFalse(saved.writes.contains("-raids.SomeOldBoss"));
	}

	@Test
	public void moreHistoryThanIsKeptShowsTheNewestAndIsNotCutOnDiskUntilThatBossChanges()
	{
		StringBuilder kills = new StringBuilder("[");
		int count = Tracker.MAX_KILLS + 100;
		for (int kc = 1; kc <= count; kc++)
		{
			kills.append(kc > 1 ? "," : "").append("{\"k\":").append(kc).append(",\"t\":").append(1000L * kc).append("}");
		}
		kills.append("]");
		saved.values.put("progress", "{\"bosses\":{\"Vorkath\":{\"chatKc\":{\"vorkath\":" + count + "}},"
			+ "\"Zulrah\":{\"chatKc\":{\"zulrah\":3}}}}");
		saved.values.put("kills.Vorkath", kills.toString());
		ProfileStore store = new ProfileStore(saved, new Gson());
		Tracker t = load(store);
		assertNothingCountedAsDamage();
		assertEquals(Tracker.MAX_KILLS, t.killsSeen("Vorkath"));
		assertEquals("the newest kill is first", count, t.history("Vorkath").get(0).getKill().getKc());
		assertEquals(101, t.history("Vorkath").get(Tracker.MAX_KILLS - 1).getKill().getKc());

		// a kill somewhere else saves, but Vorkath's longer History stays as it was saved
		t.onChatMessage("Your Zulrah kill count is: <col=ff0000>4</col>.", NOW);
		store.save(t);
		assertEquals(kills.toString(), saved.values.get("kills.Vorkath"));
		assertFalse(saved.writes.contains("kills.Vorkath"));
	}

	@Test
	public void raidsInsideTheMainDataMoveToTheirOwnKeyAndComeBack()
	{
		// how the first versions saved them
		saved.values.put("progress", "{\"bosses\":{\"Chambers of Xeric\":{\"chatKc\":{\"chambers of xeric\":7},"
			+ "\"raids\":[{\"family\":\"cox\",\"part\":\"Normal\",\"kc\":7,\"personalPoints\":25000,"
			+ "\"teamPoints\":50000,\"teamSize\":2}]}}}");
		ProfileStore store = new ProfileStore(saved, new Gson());
		Tracker t = load(store);
		assertNothingCountedAsDamage();
		assertEquals(1, t.raidRecords(COX).size());
		assertEquals(1, t.measuredRaids(COX, "Normal"));

		store.save(t);
		assertTrue(saved.values.get(COX_RAIDS).contains("25000"));
		assertFalse("the main data no longer holds the raid", saved.values.get("progress").contains("25000"));
		assertTrue("the raids are written before the main data drops them",
			saved.writes.indexOf(COX_RAIDS) < saved.writes.indexOf("progress"));

		Tracker again = load();
		assertEquals(1, again.raidRecords(COX).size());
		assertEquals(25_000, again.raidRecords(COX).get(0).getPersonalPoints());
		assertEquals(7, again.raidRecords(COX).get(0).getKc());
	}

	@Test
	public void raidsInsideTheMainDataAreNeverDroppedWhileTheirOwnKeyCannotBeWritten()
	{
		String old = "{\"bosses\":{\"Chambers of Xeric\":{\"chatKc\":{\"chambers of xeric\":7},"
			+ "\"raids\":[{\"family\":\"cox\",\"part\":\"Normal\",\"kc\":7,\"personalPoints\":25000,"
			+ "\"teamPoints\":50000,\"teamSize\":2}]}}}";
		saved.values.put("progress", old);
		ProfileStore store = new ProfileStore(saved, new Gson());
		Tracker t = load(store);
		saved.refuse.add(COX_RAIDS);
		store.save(t);
		assertEquals("the old main data, with the raid in it, is still what is saved", old, saved.values.get("progress"));
		assertNull(saved.values.get(COX_RAIDS));

		// the settings take the write again: now the raid moves, and only then leaves the main data
		saved.refuse.clear();
		store.save(t);
		assertTrue(saved.values.get(COX_RAIDS).contains("25000"));
		assertFalse(saved.values.get("progress").contains("25000"));
	}

	@Test
	public void moreRaidsThanAreKeptLoadTheNewest()
	{
		StringBuilder raids = new StringBuilder("[");
		int count = 5_050;
		for (int kc = 1; kc <= count; kc++)
		{
			raids.append(kc > 1 ? "," : "").append("{\"family\":\"cox\",\"part\":\"Normal\",\"kc\":").append(kc)
				.append(",\"personalPoints\":20000,\"teamPoints\":20000,\"teamSize\":1}");
		}
		raids.append("]");
		saved.values.put("progress", "{\"bosses\":{\"Chambers of Xeric\":{\"chatKc\":{\"chambers of xeric\":" + count + "}}}}");
		saved.values.put(COX_RAIDS, raids.toString());
		Tracker t = load();
		assertNothingCountedAsDamage();
		List<RaidRecord> loaded = t.raidRecords(COX);
		assertEquals(5_000, loaded.size());
		assertEquals(51, loaded.get(0).getKc());
		assertEquals(count, loaded.get(loaded.size() - 1).getKc());
	}
}
