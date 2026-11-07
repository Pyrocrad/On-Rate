package com.expecteddrops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.tracking.HistoryRow;
import com.expecteddrops.tracking.KillEntry;
import com.expecteddrops.tracking.RaidRecord;
import com.expecteddrops.tracking.TimeStats;
import com.expecteddrops.tracking.Tracker;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/** How long kills and raids took, read from the game's time messages. */
public class KillTimesTest
{
	private Tracker tracker;
	private long now = 1_000_000;

	@Before
	public void setUp() throws Exception
	{
		BossCatalog catalog;
		try (Reader reader = new InputStreamReader(
			ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		tracker = new Tracker(catalog);
	}

	private static String zulrahTime(String time)
	{
		return "Fight duration: <col=ff0000>" + time + "</col>. Personal best: 0:55.80";
	}

	private static String zulrahKc(int kc)
	{
		return "Your Zulrah kill count is: <col=ff0000>" + kc + "</col>.";
	}

	private KillEntry kill(String boss, int kc)
	{
		for (HistoryRow row : tracker.history(boss))
		{
			KillEntry k = row.getKill();
			if ((k.getPartKc() != null ? k.getPartKc() : k.getKc()) == kc)
			{
				return k;
			}
		}
		throw new AssertionError("no kill " + kc + " at " + boss);
	}

	@Test
	public void timeAfterTheKillCount()
	{
		tracker.onChatMessage(zulrahKc(10), now);
		assertEquals("Zulrah", tracker.onChatMessage(zulrahTime("1:01.20"), now + 600));
		assertEquals(61_200, (int) kill("Zulrah", 10).getDurationMillis());
	}

	@Test
	public void timeBeforeTheKillCount()
	{
		assertNull(tracker.onChatMessage(zulrahTime("1:01.20"), now));
		tracker.onChatMessage(zulrahKc(10), now + 600);
		assertEquals(61_200, (int) kill("Zulrah", 10).getDurationMillis());
	}

	@Test
	public void fastKillsKeepTheirOwnTimeWhicheverOrderTheMessagesCome()
	{
		// time first: each time waits for the kill after it, and must not land on the kill before
		tracker.onChatMessage(zulrahTime("0:40.20"), now);
		tracker.onChatMessage(zulrahKc(10), now + 600);
		tracker.onChatMessage(zulrahTime("0:45.60"), now + 10_000);
		tracker.onChatMessage(zulrahKc(11), now + 10_600);
		assertEquals(40_200, (int) kill("Zulrah", 10).getDurationMillis());
		assertEquals(45_600, (int) kill("Zulrah", 11).getDurationMillis());
		// time second
		tracker.onChatMessage(zulrahKc(12), now + 20_000);
		tracker.onChatMessage(zulrahTime("0:50.00"), now + 20_600);
		tracker.onChatMessage(zulrahKc(13), now + 30_000);
		tracker.onChatMessage(zulrahTime("0:52.80"), now + 30_600);
		assertEquals(50_000, (int) kill("Zulrah", 12).getDurationMillis());
		assertEquals(52_800, (int) kill("Zulrah", 13).getDurationMillis());
	}

	@Test
	public void aTimeWithNoKillNearItIsNotKept()
	{
		tracker.onChatMessage(zulrahTime("1:01.20"), now);
		// the next kill is minutes later: that time belonged to something else
		tracker.onChatMessage(zulrahKc(10), now + 5 * 60_000);
		assertNull(kill("Zulrah", 10).getDurationMillis());
	}

	@Test
	public void aBossWithoutTimeMessagesHasNoTime()
	{
		tracker.onChatMessage(zulrahKc(10), now);
		assertNull(kill("Zulrah", 10).getDurationMillis());
		assertNull(tracker.killTimes("Zulrah", null));
	}

	@Test
	public void timesOfRoomsAreNotTheKill()
	{
		tracker.onChatMessage("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>1:41.40</col>", now);
		tracker.onChatMessage(zulrahKc(10), now + 600);
		assertNull(kill("Zulrah", 10).getDurationMillis());
	}

	@Test
	public void chambersRaidTimeBeforeTheCompletionCountAndThePoints()
	{
		tracker.onChatMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>26:10.20</col> Personal best: </col><col=ff0000>25:00.00</col>", now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now + 600);
		tracker.onRaidRecord(RaidRecord.cox(24_000, 70_000, 3), now + 1_200);
		List<RaidRecord> raids = tracker.raidRecords("Chambers of Xeric");
		assertEquals(1, raids.size());
		assertEquals(26 * 60_000 + 10_200, raids.get(0).getDurationMillis());
		assertEquals(26 * 60_000 + 10_200, (int) kill("Chambers of Xeric", 100).getDurationMillis());
	}

	@Test
	public void timeMessagesFromFriendsChatNeverCountKills()
	{
		// the Chambers time arrives as a friends chat notification: only the time is read from it
		assertNull(tracker.onTimeMessage("Your Zulrah kill count is: <col=ff0000>10</col>.", now));
		assertTrue(tracker.history("Zulrah").isEmpty());
		tracker.onTimeMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>26:10.20</col>", now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now + 600);
		assertEquals(26 * 60_000 + 10_200, (int) kill("Chambers of Xeric", 100).getDurationMillis());
		// and after the count, as the message may come either side
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>101</col>.", now + 3_600_000);
		assertEquals("Chambers of Xeric", tracker.onTimeMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>2 players</col> Duration:</col> <col=ff0000>25:00.00</col>", now + 3_600_500));
		assertEquals(25 * 60_000, (int) kill("Chambers of Xeric", 101).getDurationMillis());
	}

	@Test
	public void chambersRaidTimeAfterThePoints()
	{
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now);
		tracker.onRaidRecord(RaidRecord.cox(24_000, 70_000, 3), now + 600);
		assertEquals(-1, tracker.raidRecords("Chambers of Xeric").get(0).getDurationMillis());
		tracker.onChatMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>26:10.20</col> (new personal best)</col>", now + 1_200);
		assertEquals(26 * 60_000 + 10_200, tracker.raidRecords("Chambers of Xeric").get(0).getDurationMillis());
		assertEquals(26 * 60_000 + 10_200, (int) kill("Chambers of Xeric", 100).getDurationMillis());
	}

	@Test
	public void raidModesKeepTheirOwnTimes()
	{
		tracker.onChatMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>40:00.00</col>", now);
		tracker.onChatMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>20</col>.", now + 600);
		tracker.onChatMessage("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>26:00.00</col>", now + 3_600_000);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>100</col>.", now + 3_600_600);
		assertEquals(40 * 60_000, (int) kill("Chambers of Xeric", 20).getDurationMillis());
		assertEquals(26 * 60_000, (int) kill("Chambers of Xeric", 100).getDurationMillis());
		assertEquals(26 * 60_000, tracker.killTimes("Chambers of Xeric", "Normal").getBestMillis());
		assertEquals(40 * 60_000, tracker.killTimes("Chambers of Xeric", "Challenge Mode").getBestMillis());
	}

	@Test
	public void tombsUsesTheChallengeTimeWhicheverComesFirst()
	{
		tracker.onChatMessage("Tombs of Amascut: Expert Mode challenge completion time: <col=ff0000>31:35.40</col>.", now);
		tracker.onChatMessage("Tombs of Amascut: Expert Mode total completion time: <col=ff0000>36:00.00</col>.", now + 100);
		tracker.onChatMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>50</col>.", now + 600);
		assertEquals(31 * 60_000 + 35_400, (int) kill("Tombs of Amascut", 50).getDurationMillis());
		// the other way round
		tracker.onChatMessage("Tombs of Amascut: Expert Mode total completion time: <col=ff0000>36:00.00</col>.", now + 3_600_000);
		tracker.onChatMessage("Tombs of Amascut: Expert Mode challenge completion time: <col=ff0000>30:00.00</col>.", now + 3_600_100);
		tracker.onChatMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>51</col>.", now + 3_600_600);
		assertEquals(30 * 60_000, (int) kill("Tombs of Amascut", 51).getDurationMillis());
	}

	@Test
	public void doomDelvesCarryTheirOwnTime()
	{
		tracker.onChatMessage("Delve level: 3 duration: <col=ff0000>1:52.20</col>", now);
		tracker.onChatMessage("Delve level: 8+ (12) duration: <col=ff0000>2:05.00</col>", now + 600_000);
		List<HistoryRow> history = tracker.history("Doom of Mokhaiotl");
		assertEquals(2, history.size());
		assertEquals(125_000, (int) history.get(0).getKill().getDurationMillis());
		assertEquals(112_200, (int) history.get(1).getKill().getDurationMillis());
	}

	@Test
	public void timeStatsFromTheKillsThatHaveATime()
	{
		int[] seconds = {70, 50, 60, 100};
		for (int i = 0; i < seconds.length; i++)
		{
			long at = now + i * 600_000L;
			tracker.onChatMessage(zulrahKc(10 + i), at);
			tracker.onChatMessage(zulrahTime(String.format("%d:%02d.00", seconds[i] / 60, seconds[i] % 60)), at + 500);
		}
		// one kill with no time must not count
		tracker.onChatMessage(zulrahKc(14), now + 10 * 600_000L);
		TimeStats stats = tracker.killTimes("Zulrah", null);
		assertNotNull(stats);
		assertEquals(4, stats.getCount());
		assertEquals(50_000, stats.getBestMillis());
		assertEquals(70_000, stats.getAverageMillis());
		assertEquals(65_000, stats.getMedianMillis());
		assertEquals(100_000, stats.getLastMillis());
	}

	@Test
	public void olderSavesWithoutATimeStillLoad()
	{
		List<KillEntry> kills = new Gson().fromJson("[{\"k\":5,\"t\":1000},{\"k\":6,\"t\":2000,\"du\":61200}]",
			new com.google.gson.reflect.TypeToken<List<KillEntry>>()
			{
			}.getType());
		assertNull(kills.get(0).getDurationMillis());
		assertEquals(61_200, (int) kills.get(1).getDurationMillis());
		RaidRecord record = new Gson().fromJson("{\"family\":\"cox\",\"personalPoints\":1000,\"teamSize\":2}", RaidRecord.class);
		assertEquals(-1, record.getDurationMillis());
		// and the time survives a round trip
		RaidRecord timed = new Gson().fromJson(new Gson().toJson(RaidRecord.cox(1, 2, 3)), RaidRecord.class);
		assertEquals(-1, timed.getDurationMillis());
		assertTrue(new Gson().toJson(kills.get(1)).contains("\"du\":61200"));
	}
}
