package com.onrate.engine.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidChance;
import java.io.Reader;
import org.junit.Before;
import org.junit.Test;

public class RaidHabitsTest
{
	private Tracker tracker;
	private long now = 10_000_000;
	private int coxKc;
	private int tobKc;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			tracker = new Tracker(BossCatalog.load(new Gson(), reader));
		}
		tracker.setSettings(raid -> 0.03);
	}

	private void cox(int points, int teamPoints)
	{
		now += 3_600_000;
		tracker.onRaidRecord(RaidRecord.cox(points, teamPoints, 3), now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + (++coxKc) + "</col>.", now + 1000);
	}

	private void tob(int team)
	{
		now += 3_600_000;
		tracker.onRaidRecord(RaidRecord.tob(team, 0, 0, false), now);
		tracker.onChatMessage("Your completed Theatre of Blood count is: <col=ff0000>" + (++tobKc) + "</col>.", now + 1000);
	}

	@Test
	public void noRaidsKeepsTheSettings()
	{
		RaidHabits h = tracker.raidHabits();
		assertSame(RaidHabits.none(), h);
		assertTrue(Double.isNaN(h.chance("cox")));
		assertEquals(0, h.teamSize("tob"));
		assertEquals(0, h.raids("cox"));
	}

	@Test
	public void needsTenRaidsBeforeAnAverage()
	{
		for (int i = 0; i < RaidHabits.MIN_RAIDS - 1; i++)
		{
			cox(30_000, 90_000);
		}
		assertTrue(Double.isNaN(tracker.raidHabits().chance("cox")));
		cox(30_000, 90_000);
		assertEquals(RaidChance.cox(30_000, 90_000), tracker.raidHabits().chance("cox"), 1e-12);
		assertEquals(RaidHabits.MIN_RAIDS, tracker.raidHabits().raids("cox"));
		// a Challenge Mode average needs its own raids
		assertTrue(Double.isNaN(tracker.raidHabits().chance("coxCm")));
	}

	@Test
	public void averagesOnlyTheLatestRaids()
	{
		for (int i = 0; i < RaidHabits.RECENT; i++)
		{
			cox(10_000, 30_000);
		}
		for (int i = 0; i < RaidHabits.RECENT; i++)
		{
			cox(40_000, 120_000);
		}
		assertEquals(RaidChance.cox(40_000, 120_000), tracker.raidHabits().chance("cox"), 1e-12);
		assertEquals(RaidHabits.RECENT, tracker.raidHabits().raids("cox"));
	}

	@Test
	public void theatreLearnsTheUsualTeam()
	{
		for (int i = 0; i < 6; i++)
		{
			tob(3);
		}
		for (int i = 0; i < 4; i++)
		{
			tob(5);
		}
		assertEquals(3, tracker.raidHabits().teamSize("tob"));
		assertTrue(Double.isNaN(tracker.raidHabits().chance("tob")));
	}
}
