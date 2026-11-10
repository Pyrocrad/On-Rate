package com.expecteddrops;

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
	public void raidPurpleChancesAreFoldedAway() throws Exception
	{
		Field f = ExpectedDropsConfig.class.getField("raidChancesSection");
		assertTrue("HOW-IT-WORKS says this section is folded away", f.getAnnotation(ConfigSection.class).closedByDefault());
	}
}
