package com.expecteddrops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

/** What the settings say about themselves (audit H2). */
public class SettingsTextTest
{
	@Test
	public void raidDataTrackerImportDoesNotPromiseTheatreOfBlood() throws Exception
	{
		Method m = ExpectedDropsConfig.class.getMethod("importRaidTimes");
		String description = m.getAnnotation(ConfigItem.class).description();
		// RaidDataImport reads Chambers and Tombs only: the files can't tell ToB Normal from Hard Mode
		assertTrue(description.contains("Chambers of Xeric") && description.contains("Tombs of Amascut"));
		assertFalse(description.contains("Theatre of Blood"));
	}

	@Test
	public void raidDataTrackerImportSaysTheFolderMustBePickedAgainEachStart() throws Exception
	{
		String description = ExpectedDropsConfig.class.getMethod("importRaidTimes").getAnnotation(ConfigItem.class).description();
		assertTrue(description.contains("Nothing is read until you pick its folder"));
		assertTrue(description.contains("each time RuneLite starts"));
		assertFalse(description.contains("when you log in"));
	}

	@Test
	public void raidPurpleChancesAreFoldedAway() throws Exception
	{
		Field f = ExpectedDropsConfig.class.getField("raidChancesSection");
		assertTrue("HOW-IT-WORKS says this section is folded away", f.getAnnotation(ConfigSection.class).closedByDefault());
	}

	@Test
	public void raidSettingsAreNamedAfterTheirRaid() throws Exception
	{
		String[][] expected = {
			{"coxUniqueChance", "Chambers of Xeric purple chance, Normal (%)"},
			{"coxCmPurpleChance", "Chambers of Xeric purple chance, Challenge Mode (%)"},
			{"tobTeamChance", "Theatre of Blood team purple chance, Normal (%)"},
			{"tobHmTeamChance", "Theatre of Blood team purple chance, Hard Mode (%)"},
			{"tobTeamSize", "Theatre of Blood team size"},
			{"toaEntryUniqueChance", "Tombs of Amascut purple chance, Entry Mode (%)"},
			{"toaNormalUniqueChance", "Tombs of Amascut purple chance, Normal (%)"},
			{"toaExpertUniqueChance", "Tombs of Amascut purple chance, Expert Mode (%)"},
			{"tfaTeamSize", "Fractured Archive team size"},
			{"tfaSoloDuoChance", "Fractured Archive team purple chance (%)"},
		};
		int found = 0;
		for (Method m : ExpectedDropsConfig.class.getMethods())
		{
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item == null)
			{
				continue;
			}
			for (String[] e : expected)
			{
				if (e[0].equals(item.keyName()))
				{
					assertEquals(e[1], item.name());
					found++;
				}
			}
		}
		assertEquals("every raid setting keeps its key", expected.length, found);
	}
}
