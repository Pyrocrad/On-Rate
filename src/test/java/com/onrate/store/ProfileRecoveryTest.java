package com.onrate.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.KillEntry;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/**
 * Damaged saved data (audit F5): the readable parts load, the damaged parts are left out, the saved
 * text is kept as a backup first, and nothing that wasn't loaded is written over.
 */
public class ProfileRecoveryTest
{
	private static final RaidSettings RAIDS = raid -> 0.0;
	private static final long NOW = 1_000_000;
	private static final String DATA = Keys.DATA;
	private static final String VORKATH = Keys.killsKey("Vorkath");
	private static final String ZULRAH = Keys.killsKey("Zulrah");
	private static final String COX_KILLS = Keys.killsKey("Chambers of Xeric");
	private static final String COX = Keys.raidsKey("Chambers of Xeric");

	/** One profile's saved settings; keys listed in {@link #refuse} can't be written. */
	private static final class Profile implements ProfileStore.Backend
	{
		final Map<String, String> values = new HashMap<>();
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
		}

		@Override
		public void unset(String profile, String key)
		{
			if (refuse.contains(key))
			{
				throw new IllegalStateException("refused");
			}
			values.remove(key);
		}
	}

	private final Gson gson = new Gson();
	private BossCatalog catalog;
	private Profile profile;

	@Before
	public void setUp()
	{
		catalog = Bundled.catalog(gson);
		profile = new Profile();
		// a valid account: Vorkath with History and a head, Zulrah with History, a Chambers raid with points
		Tracker t = new Tracker(catalog);
		ProfileStore store = new ProfileStore(profile, gson);
		store.load("p", t);
		vorkath(t, 41, true);
		vorkath(t, 42, false);
		t.onChatMessage("Your Zulrah kill count is: <col=ff0000>50</col>.", NOW);
		t.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>7</col>.", NOW + 5000);
		t.onRaidRecord(RaidRecord.cox(25_000, 50_000, 2), NOW + 5500);
		store.save(t);
		assertNotNull(profile.values.get(VORKATH));
		assertNotNull(profile.values.get(ZULRAH));
		assertNotNull(profile.values.get(COX));
	}

	private static void vorkath(Tracker t, int kc, boolean head)
	{
		t.onChatMessage("Your Vorkath kill count is: <col=ff0000>" + kc + "</col>.", NOW + kc * 1000L);
		if (head)
		{
			t.onLoot("Vorkath", 1, Collections.singletonList(new Tracker.LootItem("Vorkath's head", 1)), NOW + kc * 1000L);
		}
	}

	private JsonObject data()
	{
		return new JsonParser().parse(profile.values.get(DATA)).getAsJsonObject();
	}

	private JsonObject boss(JsonObject data, String name)
	{
		return data.getAsJsonObject("bosses").getAsJsonObject(name);
	}

	/** Saves hand-made JSON as is: unlike Gson, JsonElement.toString keeps the nulls. */
	private void put(String key, JsonElement json)
	{
		profile.values.put(key, json.toString());
	}

	private ProfileStore store;

	private Tracker load()
	{
		Tracker t = new Tracker(catalog);
		store = new ProfileStore(profile, gson);
		assertTrue(store.load("p", t));
		return t;
	}

	private static int kc(Tracker t, String boss)
	{
		BossStats s = t.stats(boss, RAIDS);
		return s == null ? 0 : s.getKillCount();
	}

	private static List<Integer> historyKcs(Tracker t, String boss)
	{
		List<Integer> kcs = new java.util.ArrayList<>();
		t.history(boss).forEach(h -> kcs.add(h.getKill().getKc()));
		return kcs;
	}

	@Test
	public void aNullBossEntryIsLeftOutAndTheRestLoads()
	{
		JsonObject d = data();
		d.getAsJsonObject("bosses").add("Vorkath", JsonNull.INSTANCE);
		put(DATA, d);
		String saved = profile.values.get(DATA);

		Tracker t = load();
		assertEquals(50, kc(t, "Zulrah"));
		assertEquals(7, kc(t, "Chambers of Xeric"));
		// Vorkath's History is under its own key and still loads
		assertEquals(2, historyKcs(t, "Vorkath").size());
		assertEquals("the saved text is kept first", saved, profile.values.get(DATA + Keys.CORRUPT_SUFFIX));

		// a normal save afterwards writes valid data that loads again, with the backup untouched
		t.onChatMessage("Your Zulrah kill count is: <col=ff0000>51</col>.", NOW);
		store.save(t);
		assertEquals(saved, profile.values.get(DATA + Keys.CORRUPT_SUFFIX));
		Tracker again = load();
		assertEquals(51, kc(again, "Zulrah"));
		assertEquals(2, historyKcs(again, "Vorkath").size());
	}

	@Test
	public void aNullHistoryEntryIsLeftOutAndTheOtherKillsStay()
	{
		JsonArray kills = new JsonParser().parse(profile.values.get(VORKATH)).getAsJsonArray();
		int valid = kills.size();
		kills.add(JsonNull.INSTANCE);
		put(VORKATH, kills);
		String saved = profile.values.get(VORKATH);

		Tracker t = load();
		assertEquals(valid, historyKcs(t, "Vorkath").size());
		assertEquals(saved, profile.values.get(VORKATH + Keys.CORRUPT_SUFFIX));

		// the next kill saves every kill that was readable, plus the new one
		vorkath(t, 43, false);
		store.save(t);
		List<KillEntry> persisted = gson.fromJson(profile.values.get(VORKATH),
			new com.google.gson.reflect.TypeToken<List<KillEntry>>() { }.getType());
		assertEquals(valid + 1, persisted.size());
		assertFalse(persisted.contains(null));
	}

	@Test
	public void aNullRaidEntryIsLeftOutAndTheOtherRaidsStay()
	{
		JsonArray raids = new JsonParser().parse(profile.values.get(COX)).getAsJsonArray();
		raids.add(JsonNull.INSTANCE);
		put(COX, raids);

		Tracker t = load();
		assertEquals(1, t.raidRecords("Chambers of Xeric").size());
		assertEquals(25_000, t.raidRecords("Chambers of Xeric").get(0).getPersonalPoints());
		assertNotNull(profile.values.get(COX + Keys.CORRUPT_SUFFIX));
	}

	@Test
	public void aMissingMapIsNotDamage()
	{
		// an older version saved no received counts and no kill counts for this boss: that's allowed
		JsonObject d = data();
		boss(d, "Zulrah").remove("received");
		boss(d, "Zulrah").remove("clogKc");
		put(DATA, d);

		Tracker t = load();
		assertEquals(50, kc(t, "Zulrah"));
		for (String key : profile.values.keySet())
		{
			assertFalse("absent is not damaged: " + key, key.endsWith(Keys.CORRUPT_SUFFIX));
		}
	}

	@Test
	public void anEmptyListIsNotDamageEither()
	{
		profile.values.put(ZULRAH, "[]");
		Tracker t = load();
		assertTrue(historyKcs(t, "Zulrah").isEmpty());
		assertNull(profile.values.get(ZULRAH + Keys.CORRUPT_SUFFIX));
	}

	@Test
	public void aValueOfTheWrongTypeLosesOnlyThatValue()
	{
		JsonObject d = data();
		JsonObject vork = boss(d, "Vorkath");
		// a kill count that isn't a number, and a map where a list of names belongs
		vork.getAsJsonObject("received").addProperty("Jar of decay", "lots");
		vork.add("leftOut", new JsonObject());
		put(DATA, d);

		Tracker t = load();
		BossStats s = t.stats("Vorkath", RAIDS);
		assertEquals("the kill count still loads", 42, s.getKillCount());
		assertEquals("the head still counts", 1, s.getRows().stream()
			.filter(r -> "Vorkath's head".equals(r.getIconItem())).findFirst().get().getReceived());
		assertNotNull(profile.values.get(DATA + Keys.CORRUPT_SUFFIX));
	}

	@Test
	public void oneUnreadableBossAmongValidOnes()
	{
		JsonObject d = data();
		d.getAsJsonObject("bosses").addProperty("Vorkath", "not a boss");
		put(DATA, d);

		Tracker t = load();
		assertEquals(50, kc(t, "Zulrah"));
		assertEquals(7, kc(t, "Chambers of Xeric"));
		assertEquals(1, t.raidRecords("Chambers of Xeric").size());
		assertEquals(2, historyKcs(t, "Vorkath").size());
	}

	@Test
	public void unreadableHistoryNextToValidMainData()
	{
		profile.values.put(VORKATH, "[[[");
		Tracker t = load();
		assertEquals("the main data is all there", 42, kc(t, "Vorkath"));
		assertEquals(50, kc(t, "Zulrah"));
		assertEquals(1, historyKcs(t, "Zulrah").size());
		assertEquals("[[[", profile.values.get(VORKATH + Keys.CORRUPT_SUFFIX));

		// a later save keeps the copy of what couldn't be read
		vorkath(t, 43, false);
		store.save(t);
		assertEquals("[[[", profile.values.get(VORKATH + Keys.CORRUPT_SUFFIX));
	}

	@Test
	public void unreadableRaidsNextToValidHistory()
	{
		String kills = profile.values.get(COX_KILLS);
		profile.values.put(COX, "oops");
		Tracker t = load();
		assertTrue(t.raidRecords("Chambers of Xeric").isEmpty());
		assertEquals("oops", profile.values.get(COX + Keys.CORRUPT_SUFFIX));
		assertEquals(1, historyKcs(t, "Chambers of Xeric").size());
		assertEquals(2, historyKcs(t, "Vorkath").size());
		// History is not written again just because the raids were damaged
		t.onChatMessage("Your Zulrah kill count is: <col=ff0000>51</col>.", NOW);
		store.save(t);
		assertEquals(kills, profile.values.get(COX_KILLS));
	}

	@Test
	public void damagedHistoryIsNeverWrittenOverWhenItsCopyCannotBeKept()
	{
		profile.values.put(VORKATH, "[[[");
		profile.refuse.add(VORKATH + Keys.CORRUPT_SUFFIX);
		Tracker t = load();
		vorkath(t, 43, false);
		store.save(t);
		assertEquals("the only copy of the damaged History stays where it was", "[[[", profile.values.get(VORKATH));
		// and the rest of the account still saves
		assertTrue(profile.values.get(DATA).contains("43"));
	}

	@Test
	public void aLoadThatFailsHalfWayNeverSavesOverTheProfile()
	{
		Map<String, String> before = new HashMap<>(profile.values);
		Tracker failing = new Tracker(catalog)
		{
			@Override
			public synchronized void setKills(String bossName, List<KillEntry> kills)
			{
				throw new IllegalStateException("something unexpected half way");
			}
		};
		ProfileStore s = new ProfileStore(profile, gson);
		assertFalse(s.load("p", failing));
		failing.onChatMessage("Your Zulrah kill count is: <col=ff0000>99</col>.", NOW);
		s.save(failing);
		assertEquals("nothing was written over", before, profile.values);
		// the next load of the profile is tried again
		assertTrue(s.load("p", new Tracker(catalog)));
	}

	@Test
	public void aFailedLoadIsToldApartFromNoProfileAndAlreadyLoaded()
	{
		ProfileStore s = new ProfileStore(profile, gson);
		// no profile yet, nothing tried: not a failure
		assertFalse(s.load(null, new Tracker(catalog)));
		assertFalse(s.loadFailed());
		Tracker failing = new Tracker(catalog)
		{
			@Override
			public synchronized void setKills(String bossName, List<KillEntry> kills)
			{
				throw new IllegalStateException("something unexpected half way");
			}
		};
		assertFalse(s.load("p", failing));
		assertTrue("a load that failed half way says so", s.loadFailed());
		// the next try that works clears it; loading the same profile again does nothing and isn't a failure
		assertTrue(s.load("p", new Tracker(catalog)));
		assertFalse(s.loadFailed());
		assertFalse(s.load("p", new Tracker(catalog)));
		assertFalse(s.loadFailed());
	}

	@Test
	public void validHistoryAndRaidsOfOtherBossesAreNeverRewrittenAfterADamagedLoad()
	{
		JsonObject d = data();
		d.getAsJsonObject("bosses").add("Vorkath", JsonNull.INSTANCE);
		put(DATA, d);
		String zulrah = profile.values.get(ZULRAH);
		String cox = profile.values.get(COX);
		String vorkathKills = profile.values.get(VORKATH);

		Tracker t = load();
		vorkath(t, 43, false);
		store.save(t);
		assertEquals(zulrah, profile.values.get(ZULRAH));
		assertEquals(cox, profile.values.get(COX));
		// Vorkath's History is written with every old kill plus the new one, never fewer
		List<KillEntry> persisted = gson.fromJson(profile.values.get(VORKATH),
			new com.google.gson.reflect.TypeToken<List<KillEntry>>() { }.getType());
		List<KillEntry> old = gson.fromJson(vorkathKills, new com.google.gson.reflect.TypeToken<List<KillEntry>>() { }.getType());
		assertEquals(old.size() + 1, persisted.size());
	}

	@Test
	public void aDropWithoutItsItemIsLeftOutAndTheKillStays()
	{
		JsonArray kills = new JsonParser().parse(profile.values.get(VORKATH)).getAsJsonArray();
		JsonObject withHead = null;
		for (JsonElement k : kills)
		{
			// drops are saved under "d"
			if (k.getAsJsonObject().has("d"))
			{
				withHead = k.getAsJsonObject();
			}
		}
		assertNotNull(withHead);
		withHead.getAsJsonArray("d").add(new JsonObject());
		put(VORKATH, kills);

		Tracker t = load();
		assertEquals(2, historyKcs(t, "Vorkath").size());
		long drops = t.history("Vorkath").stream().mapToLong(h -> h.getKill().getDrops().size()).sum();
		assertEquals("the real head stays, the empty drop goes", 1, drops);
	}
}
