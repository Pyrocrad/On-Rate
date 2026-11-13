package com.expecteddrops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.onrate.engine.stats.RaidChance;
import com.onrate.game.ConfigRaidSettings;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

/**
 * The Fractured Archive's team chance setting after the game update of 11 November 2026: its new key and
 * default, and the old value (which RuneLite had saved for everyone, changed or not) cleared exactly once.
 */
public class TfaSettingTest
{
	private static ExpectedDropsConfig withTeamChance(double percent)
	{
		return new ExpectedDropsConfig()
		{
			@Override
			public double tfaTeamChance()
			{
				return percent;
			}
		};
	}

	@Test
	public void theSettingHasANewKeyAndTheNewDefault() throws Exception
	{
		Method m = ExpectedDropsConfig.class.getMethod("tfaTeamChance");
		ConfigItem item = m.getAnnotation(ConfigItem.class);
		assertEquals("tfaSoloDuoChance", item.keyName());
		assertEquals("Fractured Archive team purple chance (%)", item.name());
		assertEquals(6.25, new ExpectedDropsConfig()
		{
		}.tfaTeamChance(), 0);
		// what it says matches the rules it stands for
		assertTrue(item.description().contains("1 in 16 (6.25%)"));
		assertTrue(item.description().contains("1 in 12.5 for three"));
		assertTrue(item.description().contains("1 in 7.5 for eight"));
		assertTrue(item.description().contains("11 November 2026"));
		assertFalse(item.description().contains("7.14"));
		assertFalse(item.description().contains("adds 4%"));
	}

	@Test
	public void theOldKeyIsClearedAndNoSettingInUseEverIs() throws Exception
	{
		List<String> old = Arrays.asList(ExpectedDropsPlugin.OLD_SETTINGS);
		assertTrue(old.contains("tfaTeamChance"));
		// a key in use on that list would be wiped at every start
		for (Method m : ExpectedDropsConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item != null)
			{
				assertFalse(item.keyName() + " is still a setting", old.contains(item.keyName()));
			}
		}
	}

	@Test
	public void theOldValueIsClearedOnceAndTheNewSettingKeepsWhatThePlayerSet()
	{
		// as RuneLite holds them: the old chance (7.14, saved for everyone), another old setting, and
		// the new setting already changed by hand
		Map<String, String> saved = new HashMap<>();
		saved.put("tfaTeamChance", "7.142857142857143");
		saved.put("showTrip", "true");
		saved.put("tfaSoloDuoChance", "9.0");
		saved.put("tfaTeamSize", "4");
		List<String> unset = new ArrayList<>();
		ExpectedDropsPlugin.clearOldSettings(saved::get, key ->
		{
			unset.add(key);
			saved.remove(key);
		});
		assertEquals(2, unset.size());
		assertTrue(unset.contains("tfaTeamChance"));
		assertFalse(saved.containsKey("tfaTeamChance"));
		assertEquals("9.0", saved.get("tfaSoloDuoChance"));
		assertEquals("4", saved.get("tfaTeamSize"));

		// the next start, and the one after: nothing left to clear, nothing reset again
		unset.clear();
		ExpectedDropsPlugin.clearOldSettings(saved::get, unset::add);
		ExpectedDropsPlugin.clearOldSettings(saved::get, unset::add);
		assertTrue(unset.isEmpty());
		assertEquals("9.0", saved.get("tfaSoloDuoChance"));
	}

	@Test
	public void aPlayerWhoNeverHadTheOldValueLosesNothing()
	{
		Map<String, String> saved = new HashMap<>();
		saved.put("tfaSoloDuoChance", "6.25");
		List<String> unset = new ArrayList<>();
		ExpectedDropsPlugin.clearOldSettings(saved::get, unset::add);
		assertTrue(unset.isEmpty());
		assertEquals(1, saved.size());
	}

	@Test
	public void theSettingIsThePercentForASoloOrDuoAndIsKeptBetween0And100()
	{
		assertEquals(RaidChance.TFA_BASE_CHANCE, new ConfigRaidSettings(new ExpectedDropsConfig()
		{
		}).tfaBaseChance(), 1e-15);
		ConfigRaidSettings ten = new ConfigRaidSettings(withTeamChance(10));
		assertEquals(0.10, ten.tfaBaseChance(), 1e-12);
		// the default team size is a duo: half the team's chance each
		assertEquals(0.05, ten.uniqueChance("fractured"), 1e-12);
		assertEquals(1.0, new ConfigRaidSettings(withTeamChance(150)).tfaBaseChance(), 0);
		assertEquals(0.0, new ConfigRaidSettings(withTeamChance(-5)).tfaBaseChance(), 0);
		assertEquals(0.0, new ConfigRaidSettings(withTeamChance(-5)).uniqueChance("fractured"), 0);
	}

	@Test
	public void raidsOnRateDidntCountUseTheFixedChanceOfTheSettingsTeamSize()
	{
		double[] oneIn = {16, 16, 12.5, 10.5, 9.5, 8.5, 8, 7.5};
		for (int team = 1; team <= 8; team++)
		{
			int size = team;
			ConfigRaidSettings s = new ConfigRaidSettings(new ExpectedDropsConfig()
			{
				@Override
				public int tfaTeamSize()
				{
					return size;
				}
			});
			// (a team size other than the default is the player's own choice: nothing learned replaces it)
			assertEquals(team + " players", 1 / oneIn[team - 1] / team, s.uniqueChance("fractured"), 1e-12);
		}
		// outside 1 to 8 the setting is read as the nearest size
		assertEquals(1 / 7.5 / 8, new ConfigRaidSettings(new ExpectedDropsConfig()
		{
			@Override
			public int tfaTeamSize()
			{
				return 20;
			}
		}).uniqueChance("fractured"), 1e-12);
		assertEquals(1 / 16.0, new ConfigRaidSettings(new ExpectedDropsConfig()
		{
			@Override
			public int tfaTeamSize()
			{
				return 0;
			}
		}).uniqueChance("fractured"), 1e-12);
	}
}
