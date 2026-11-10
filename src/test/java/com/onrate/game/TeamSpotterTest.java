package com.onrate.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TeamSpotterTest
{
	@Test
	public void knowsWhichNpcsAreGroupBosses()
	{
		assertEquals("nex", TeamSpotter.fightOf("Nex"));
		assertEquals("yama", TeamSpotter.fightOf("Yama"));
		assertEquals("hueycoatl", TeamSpotter.fightOf("The Hueycoatl"));
		assertEquals("hueycoatl", TeamSpotter.fightOf("Hueycoatl"));
		assertEquals("hueycoatl", TeamSpotter.fightOf("Hueycoatl tail"));
		assertEquals("corporeal beast", TeamSpotter.fightOf("Corporeal Beast"));
		// names that only contain one
		assertNull(TeamSpotter.fightOf("Nexling"));
		assertNull(TeamSpotter.fightOf("Zalcano"));
		assertNull(TeamSpotter.fightOf("Vorkath"));
		assertNull(TeamSpotter.fightOf(null));
	}

	@Test
	public void onlyPlayersWhoHitTheBossCount()
	{
		TeamSpotter spotter = new TeamSpotter();
		assertEquals(0, spotter.others("nex", 1_000));
		spotter.onHitting("nex", "Alice", 1_000);
		spotter.onHitting("nex", "Bob", 2_000);
		// the same player hitting again is still one player
		spotter.onHitting("nex", "Alice", 3_000);
		assertEquals(2, spotter.others("nex", 4_000));
		// another boss's fight is separate
		assertEquals(0, spotter.others("yama", 4_000));
	}

	@Test
	public void aPlayerSeenLongAgoNoLongerCounts()
	{
		TeamSpotter spotter = new TeamSpotter();
		spotter.onHitting("corporeal beast", "Alice", 0);
		spotter.onHitting("corporeal beast", "Bob", TeamSpotter.WINDOW_MILLIS);
		assertEquals(2, spotter.others("corporeal beast", TeamSpotter.WINDOW_MILLIS));
		assertEquals(1, spotter.others("corporeal beast", TeamSpotter.WINDOW_MILLIS + 1));
	}

	@Test
	public void theNextKillStartsFresh()
	{
		TeamSpotter spotter = new TeamSpotter();
		spotter.onHitting("nex", "Alice", 1_000);
		spotter.clear("nex");
		assertEquals(0, spotter.others("nex", 2_000));
	}
}
