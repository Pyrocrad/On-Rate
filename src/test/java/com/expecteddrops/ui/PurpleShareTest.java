package com.expecteddrops.ui;

import com.expecteddrops.ExpectedDropsPlugin;
import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/** The "Share of a purple" row in a raid item's hover. */
public class PurpleShareTest
{
	private static String share(String bossName, String item) throws Exception
	{
		try (Reader reader = new InputStreamReader(
			ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			BossCatalog catalog = BossCatalog.load(new Gson(), reader);
			BossDefinition boss = catalog.byName(bossName);
			for (DropDefinition drop : boss.getDrops())
			{
				if (drop.getDisplayName().equals(item))
				{
					return ExpectedDropsPanel.purpleShare(boss, drop);
				}
			}
			throw new AssertionError("no " + item);
		}
	}

	@Test
	public void raidUniquesShowTheirShare() throws Exception
	{
		assertEquals("5 of 15", share("The Fractured Archive", "Rondache"));
		assertEquals("1 of 15", share("The Fractured Archive", "Obligator (uncharged)"));
		// Entry Mode has no purples, so ToB's share is the same in every mode that has them
		assertEquals("8 of 19", share("Theatre of Blood", "Avernic defender hilt"));
	}

	@Test
	public void noShareWhenItChangesOrIsntAPurple() throws Exception
	{
		// out of 60 in normal mode, 56 in Challenge Mode
		assertNull(share("Chambers of Xeric", "Twisted bow"));
		// ToA's weights move with the raid level
		assertNull(share("Tombs of Amascut", "Osmumten's fang"));
		// rolled on its own
		assertNull(share("The Fractured Archive", "Orion"));
		assertNull(share("The Fractured Archive", "Elemental fragments"));
	}
}
