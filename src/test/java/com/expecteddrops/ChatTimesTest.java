package com.expecteddrops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.expecteddrops.tracking.ChatTimes;
import org.junit.Test;

public class ChatTimesTest
{
	private static int millis(String message)
	{
		ChatTimes.Reading r = ChatTimes.read(message);
		assertNotNull(message, r);
		return r.getMillis();
	}

	@Test
	public void parsesTimes()
	{
		assertEquals(26 * 60_000 + 10_200, ChatTimes.parseMillis("26:10.20"));
		assertEquals(61_200, ChatTimes.parseMillis("1:01.2"));
		assertEquals(52_000, ChatTimes.parseMillis("0:52"));
		assertEquals(3_723_000, ChatTimes.parseMillis("1:02:03"));
		assertEquals(0, ChatTimes.parseMillis("abc"));
		assertEquals(0, ChatTimes.parseMillis("0:00"));
		assertEquals(0, ChatTimes.parseMillis(null));
	}

	@Test
	public void formatsTimes()
	{
		assertEquals("1:01.2", ChatTimes.format(61_200));
		assertEquals("26:10", ChatTimes.format(26 * 60_000 + 10_200));
		assertEquals("1:02:03", ChatTimes.format(3_723_000));
		assertEquals("0:52.0", ChatTimes.format(52_000));
		assertEquals("", ChatTimes.format(0));
	}

	@Test
	public void bossFightDuration()
	{
		assertEquals(61_200, millis("Fight duration: <col=ff0000>1:01.20</col>. Personal best: 0:55.80"));
		assertEquals(61_200, millis("Fight duration: <col=ff0000>1:01.20</col> (new personal best)"));
		assertEquals(ChatTimes.RANK_KILL, ChatTimes.read("Fight duration: 1:01.20").getRank());
	}

	@Test
	public void gauntletAndTempoross()
	{
		assertEquals(5 * 60_000 + 3_400, millis("Challenge duration: <col=ff0000>5:03.40</col>. Personal best: 4:50.00"));
		assertEquals(7 * 60_000 + 2_000, millis("Corrupted challenge duration: <col=ff0000>7:02.00</col> (new personal best)"));
		assertEquals(5 * 60_000 + 12_600, millis("Subdued in <col=ff0000>5:12.60</col>. Personal best: 4:40.20"));
	}

	@Test
	public void chambersRaid()
	{
		String message = "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col> Duration:</col> <col=ff0000>26:10.20</col> Personal best: </col><col=ff0000>25:00.00</col>";
		assertEquals(26 * 60_000 + 10_200, millis(message));
		assertEquals(ChatTimes.RANK_TOTAL, ChatTimes.read(message).getRank());
		assertEquals(18 * 60_000 + 59_000, millis("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col> Duration:</col> <col=ff0000>18:59.00</col> (new personal best)</col>"));
	}

	@Test
	public void tombsCountsOnlyTheTotalTime()
	{
		// the challenge time leaves out the rooms around the fight: not a time we keep
		assertNull(ChatTimes.read("Tombs of Amascut: Expert Mode challenge completion time: <col=ff0000>31:35.40</col>. Personal best: 24:19.80"));
		ChatTimes.Reading total = ChatTimes.read("Tombs of Amascut: Expert Mode total completion time: <col=ff0000>36:00.00</col>");
		assertEquals(36 * 60_000, total.getMillis());
		assertEquals(ChatTimes.RANK_TOTAL, total.getRank());
	}

	@Test
	public void theatreAndArchiveTotals()
	{
		ChatTimes.Reading tob = ChatTimes.read("Theatre of Blood total completion time: <col=ff0000>23:50.40</col>. Personal best: 22:12.00");
		assertEquals(23 * 60_000 + 50_400, tob.getMillis());
		assertEquals(ChatTimes.RANK_TOTAL, tob.getRank());
		ChatTimes.Reading archive = ChatTimes.read("The Fractured Archive: Total completion time: <col=ff0000>12:00.00</col>. Personal best: 11:00.00");
		assertEquals(12 * 60_000, archive.getMillis());
		assertEquals(ChatTimes.RANK_TOTAL, archive.getRank());
	}

	@Test
	public void timesOfRoomsAndWavesAreNotTheKill()
	{
		assertNull(ChatTimes.read("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>1:41.40</col>"));
		assertNull(ChatTimes.read("Challenge complete: Apmeken. Duration: <col=ff0000>3:04.20</col>"));
		assertNull(ChatTimes.read("Your Zulrah kill count is: <col=ff0000>309</col>."));
		assertNull(ChatTimes.read("Nothing to see here"));
		assertNull(ChatTimes.read(null));
	}
}
