package com.onrate.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.stats.RaidChance;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.HistoryRow;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.SaveData;
import com.onrate.engine.tracking.Tracker;
import com.onrate.engine.tracking.TrackerState;
import java.util.List;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;

/**
 * The Fractured Archive around the game update of 11 November 2026, 11:30 UTC: which raids keep the
 * chance of before it, which get the fixed chance of their team size, at the very moment of the
 * change, for every team size, in any time zone and after saving and loading.
 */
public class FracturedArchiveUpdateTest
{
	private static final String TFA = "The Fractured Archive";
	private static final long CHANGE = RaidChance.TFA_CHANGE_MILLIS;
	private static final double[] ONE_IN = {16, 16, 12.5, 10.5, 9.5, 8.5, 8, 7.5};
	/** Raids On Rate didn't count: none in these tests, so the setting's own chance never matters. */
	private static final RaidSettings RAIDS = raid -> 0;

	private Tracker tracker;
	private int around;
	private int kc;

	@Before
	public void setUp()
	{
		tracker = new Tracker(Bundled.catalog(new Gson()));
		tracker.setSettings(RAIDS);
		tracker.setTeamSizeDetector(() -> around);
	}

	private void raid(int team, long when)
	{
		around = team;
		tracker.onChatMessage("Your The Fractured Archive completion count is: <col=ff0000>" + (++kc) + "</col>.", when);
	}

	private static double before(int team)
	{
		return (1 + 0.04 * Math.max(0, team - 2)) / 14 / team;
	}

	private static double since(int team)
	{
		return 1 / ONE_IN[team - 1] / team;
	}

	/** Each raid's chance as History shows it, oldest first. */
	private double[] chances()
	{
		List<HistoryRow> history = tracker.history(TFA);
		double[] out = new double[history.size()];
		for (int i = 0; i < out.length; i++)
		{
			out[i] = history.get(out.length - 1 - i).getPurpleChance();
		}
		return out;
	}

	private double rondache()
	{
		BossStats stats = tracker.stats(TFA, RAIDS);
		return stats.getRows().stream().filter(r -> r.getLabel().equals("Rondache")).findFirst().get().getExpected();
	}

	@Test
	public void theLastMillisecondBeforeTheUpdateIsOldAndTheUpdateItselfIsNew()
	{
		raid(4, CHANGE - 1);
		raid(4, CHANGE);
		raid(4, CHANGE + 1);
		assertEquals(before(4), chances()[0], 1e-15);
		assertEquals(since(4), chances()[1], 1e-15);
		assertEquals(since(4), chances()[2], 1e-15);
		assertEquals((before(4) + 2 * since(4)) * 5 / 15, rondache(), 1e-12);
	}

	@Test
	public void everyTeamSizeHasItsOwnChanceOnEachSideOfTheUpdate()
	{
		for (int team = 1; team <= 8; team++)
		{
			raid(team, CHANGE - 3_600_000L * (9 - team));
		}
		for (int team = 1; team <= 8; team++)
		{
			raid(team, CHANGE + 3_600_000L * team);
		}
		double[] chances = chances();
		assertEquals(16, chances.length);
		for (int team = 1; team <= 8; team++)
		{
			assertEquals(team + " players before", before(team), chances[team - 1], 1e-15);
			assertEquals(team + " players since", since(team), chances[8 + team - 1], 1e-15);
		}
		// solo and duo dropped from 1 in 14 to 1 in 16; from three players up the team's chance went up
		assertTrue(since(1) < before(1));
		assertTrue(since(2) < before(2));
		for (int team = 3; team <= 8; team++)
		{
			assertTrue(team + " players", since(team) > before(team));
		}
	}

	@Test
	public void moreThanEightPlayersAroundYouCountAsEight()
	{
		raid(12, CHANGE - 60_000);
		raid(12, CHANGE + 60_000);
		List<RaidRecord> raids = tracker.raidRecords(TFA);
		assertEquals(8, raids.get(0).getTeamSize());
		assertEquals(8, raids.get(1).getTeamSize());
		assertEquals(before(8), chances()[0], 1e-15);
		assertEquals(since(8), chances()[1], 1e-15);
	}

	@Test
	public void aRaidCountsFromItsCompletionMessageWhicheverSideItsDamageShareComesOn()
	{
		around = 2;
		// completed just before the update; its damage share is read just after: still a raid from before
		tracker.onChatMessage("Your The Fractured Archive completion count is: <col=ff0000>1</col>.", CHANGE - 500);
		tracker.onRaidRecord(RaidRecord.fractured(0, 0, 420), CHANGE + 500);
		// the damage share read just before the update, the completion message just after: a raid since
		tracker.onRaidRecord(RaidRecord.fractured(0, 0, 420), CHANGE + 600_000 - 500);
		tracker.onChatMessage("Your The Fractured Archive completion count is: <col=ff0000>2</col>.", CHANGE + 600_000 + 500);
		List<RaidRecord> raids = tracker.raidRecords(TFA);
		assertEquals(2, raids.size());
		assertEquals(420, raids.get(0).getContribution(), 0);
		assertEquals(420, raids.get(1).getContribution(), 0);
		assertEquals(420 / 700.0 / 14, chances()[0], 1e-15);
		assertEquals(420 / 700.0 / 16, chances()[1], 1e-15);
	}

	@Test
	public void theTimeZoneOfTheComputerChangesNothing()
	{
		TimeZone usual = TimeZone.getDefault();
		try
		{
			// where 11:30 UTC is already the 12th, and where it is still the night of the 10th to the 11th
			for (String zone : new String[]{"Pacific/Kiritimati", "Pacific/Auckland", "Pacific/Honolulu", "UTC"})
			{
				TimeZone.setDefault(TimeZone.getTimeZone(zone));
				setUp();
				kc = 0;
				raid(3, CHANGE - 1);
				raid(3, CHANGE);
				assertEquals(zone, before(3), chances()[0], 1e-15);
				assertEquals(zone, since(3), chances()[1], 1e-15);
			}
		}
		finally
		{
			TimeZone.setDefault(usual);
		}
	}

	@Test
	public void savingAndLoadingKeepsEachRaidOnItsSideOfTheUpdate()
	{
		raid(5, CHANGE - 1);
		raid(5, CHANGE);
		Gson gson = new Gson();
		SaveData data = tracker.saveData(gson::toJson);
		Tracker loaded = new Tracker(Bundled.catalog(new Gson()));
		loaded.setSettings(RAIDS);
		loaded.setState(gson.fromJson(data.progress, TrackerState.class));
		loaded.setKills(TFA, gson.fromJson(data.kills.get(TFA), new com.google.gson.reflect.TypeToken<List<com.onrate.engine.tracking.KillEntry>>()
		{
		}.getType()));
		loaded.setRaids(TFA, gson.fromJson(data.raids.get(TFA), new com.google.gson.reflect.TypeToken<List<RaidRecord>>()
		{
		}.getType()));
		tracker = loaded;
		assertEquals(before(5), chances()[0], 1e-15);
		assertEquals(since(5), chances()[1], 1e-15);
	}

	@Test
	public void aRaidSavedWithoutADateIsFromBeforeTheUpdate()
	{
		// only a version from before raids carried their date could have saved one: long before this update
		Gson gson = new Gson();
		tracker.setState(gson.fromJson("{\"bosses\":{\"The Fractured Archive\":{\"chatKc\":{\"the fractured archive\":1}}}}", TrackerState.class));
		tracker.setKills(TFA, gson.fromJson("[{\"k\":1,\"p\":1,\"t\":1000,\"m\":\"Normal\"}]",
			new com.google.gson.reflect.TypeToken<List<com.onrate.engine.tracking.KillEntry>>()
			{
			}.getType()));
		tracker.setRaids(TFA, gson.fromJson("[{\"family\":\"fractured\",\"part\":\"Normal\",\"kc\":1,\"teamSize\":4}]",
			new com.google.gson.reflect.TypeToken<List<RaidRecord>>()
			{
			}.getType()));
		assertEquals(before(4), chances()[0], 1e-15);
	}

	@Test
	public void theSettingsChanceMovesOnlyTheRaidsSinceTheUpdateWhateverTheirTeam()
	{
		raid(1, CHANGE - 1);
		raid(6, CHANGE - 1);
		raid(1, CHANGE);
		raid(6, CHANGE);
		RaidSettings tripled = new RaidSettings()
		{
			@Override
			public double uniqueChance(String raid)
			{
				return 0;
			}

			@Override
			public double tfaBaseChance()
			{
				return 3 * RaidChance.TFA_BASE_CHANCE;
			}
		};
		BossStats stats = tracker.stats(TFA, tripled);
		double rondache = stats.getRows().stream().filter(r -> r.getLabel().equals("Rondache")).findFirst().get().getExpected();
		assertEquals((before(1) + before(6) + 3 * since(1) + 3 * since(6)) * 5 / 15, rondache, 1e-12);
	}
}
