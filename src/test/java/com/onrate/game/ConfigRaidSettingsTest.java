package com.onrate.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.expecteddrops.ExpectedDropsConfig;
import com.expecteddrops.DuoMode;
import org.junit.Test;

public class ConfigRaidSettingsTest
{
	private static ExpectedDropsConfig config(DuoMode titans, int soloKills, int tobTeam)
	{
		return new ExpectedDropsConfig()
		{
			@Override
			public DuoMode royalTitansMode()
			{
				return titans;
			}

			@Override
			public int royalTitansSoloKills()
			{
				return soloKills;
			}

			@Override
			public int tobTeamSize()
			{
				return tobTeam;
			}
		};
	}

	@Test
	public void defaultsAreTheOnesPlayersAlwaysHad()
	{
		ConfigRaidSettings s = new ConfigRaidSettings(new ExpectedDropsConfig()
		{
		});
		assertEquals(0.035, s.uniqueChance("cox"), 1e-12);
		assertEquals(0.066, s.uniqueChance("coxCm"), 1e-12);
		assertEquals(0.11 / 4, s.uniqueChance("tob"), 1e-12);
		assertEquals(0.13 / 4, s.uniqueChance("tobHm"), 1e-12);
		assertEquals(0.0075, s.uniqueChance("toaEntry"), 1e-12);
		assertEquals(0.025, s.uniqueChance("toa"), 1e-12);
		assertEquals(0.05, s.uniqueChance("toaExpert"), 1e-12);
		assertEquals(0, s.uniqueChance("nothing"), 0);
		// a duo with no deaths: 1 in 16 for the team (since the update of 11 November 2026), an even half each
		assertEquals(1 / 32.0, s.uniqueChance("fractured"), 1e-12);
		assertEquals(1 / 16.0, s.tfaBaseChance(), 1e-12);
	}

	@Test
	public void teamSizesAreClamped()
	{
		assertEquals(5, new ConfigRaidSettings(config(DuoMode.DUO, 0, 99)).teamSize("tob"));
		assertEquals(1, new ConfigRaidSettings(config(DuoMode.DUO, 0, -3)).teamSize("tobHm"));
		assertEquals(1, new ConfigRaidSettings(config(DuoMode.DUO, 0, 4)).teamSize("cox"));
	}

	@Test
	public void duoKillsHalveTheShareAndSoloKillsKeepIt()
	{
		assertEquals(0.5, new ConfigRaidSettings(config(DuoMode.DUO, 0, 4)).contribution("royalTitans", 100), 1e-12);
		assertEquals(1.0, new ConfigRaidSettings(config(DuoMode.SOLO, 0, 4)).contribution("royalTitans", 100), 1e-12);
		// 30 solo, 70 duo: (30 + 35) / 100
		assertEquals(0.65, new ConfigRaidSettings(config(DuoMode.MIXED, 30, 4)).contribution("royalTitans", 100), 1e-12);
		assertEquals(1.0, new ConfigRaidSettings(config(DuoMode.DUO, 0, 4)).contribution("nex", 100), 1e-12);
		assertEquals(1.0, new ConfigRaidSettings(config(DuoMode.DUO, 0, 4)).contribution("royalTitans", 0), 1e-12);
	}

	@Test
	public void notesSayWhatIsAssumedAndWhereToChangeIt()
	{
		assertNull(new ConfigRaidSettings(config(DuoMode.DUO, 0, 4)).contributionNote("nex", 10));
		assertNotNull(new ConfigRaidSettings(config(DuoMode.DUO, 0, 4)).contributionNote("royalTitans", 10));
	}

	/** A tracker that saw {@code n} Chambers raids at these points and {@code n} Theatre raids of three. */
	private static com.onrate.engine.tracking.Tracker raided(int n) throws Exception
	{
		com.onrate.engine.tracking.Tracker t;
		try (java.io.Reader reader = com.onrate.engine.data.Bundled.reader("bosses.json"))
		{
			t = new com.onrate.engine.tracking.Tracker(com.onrate.engine.data.BossCatalog.load(new com.google.gson.Gson(), reader));
		}
		long now = 10_000_000;
		for (int i = 1; i <= n; i++)
		{
			now += 3_600_000;
			t.onRaidRecord(com.onrate.engine.tracking.RaidRecord.cox(43_380, 100_000, 3), now);
			t.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + i + "</col>.", now + 1000);
			now += 3_600_000;
			t.onRaidRecord(com.onrate.engine.tracking.RaidRecord.tob(3, 0, 0, false), now);
			t.onChatMessage("Your completed Theatre of Blood count is: <col=ff0000>" + i + "</col>.", now + 1000);
		}
		return t;
	}

	@Test
	public void settingsLeftAsTheyCameUseYourRecordedRaids() throws Exception
	{
		com.onrate.engine.tracking.Tracker t = raided(10);
		ConfigRaidSettings s = new ConfigRaidSettings(new ExpectedDropsConfig()
		{
		}, t::raidHabits);
		// 43,380 points is 5%
		assertEquals(0.05, s.uniqueChance("cox"), 1e-9);
		assertEquals(10, s.learnedRaids("cox"));
		// Theatre of Blood: the team chance stays, split over your usual team of three
		assertEquals(3, s.teamSize("tob"));
		assertEquals(0.11 / 3, s.uniqueChance("tob"), 1e-12);
		// modes without enough raids keep the setting
		assertEquals(0.066, s.uniqueChance("coxCm"), 1e-12);
		assertEquals(0, s.learnedRaids("coxCm"));
	}

	@Test
	public void aChangedSettingWins() throws Exception
	{
		com.onrate.engine.tracking.Tracker t = raided(10);
		ConfigRaidSettings s = new ConfigRaidSettings(new ExpectedDropsConfig()
		{
			@Override
			public double coxUniqueChance()
			{
				return 2.0;
			}

			@Override
			public int tobTeamSize()
			{
				return 5;
			}
		}, t::raidHabits);
		assertEquals(0.02, s.uniqueChance("cox"), 1e-12);
		assertEquals(0, s.learnedRaids("cox"));
		assertEquals(5, s.teamSize("tob"));
	}

	@Test
	public void fewerThanTenRaidsUseTheDefaults() throws Exception
	{
		com.onrate.engine.tracking.Tracker t = raided(9);
		ConfigRaidSettings s = new ConfigRaidSettings(new ExpectedDropsConfig()
		{
		}, t::raidHabits);
		assertEquals(0.035, s.uniqueChance("cox"), 1e-12);
		assertEquals(4, s.teamSize("tob"));
	}
}
