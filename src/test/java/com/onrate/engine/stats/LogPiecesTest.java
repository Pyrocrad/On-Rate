package com.onrate.engine.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.data.KillTimes;
import com.onrate.engine.tracking.Tracker;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

/**
 * A row of pieces that come one per drop in a fixed order (the Hydra's eye, fang and heart, "pieces": 3): the
 * collection log needs every piece, so the green log, the Time page and the log forecast wait for all of them.
 * The panel's side of it (the Time list and the forecast table) is behind its logPieces switch.
 */
public class LogPiecesTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;
	private static final double RING = 1 / 181.0;
	private BossCatalog catalog;
	private Tracker tracker;
	private KillTimes web;

	@Before
	public void setUp() throws Exception
	{
		try (Reader reader = Bundled.reader("bosses.json"))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		web = KillTimes.bundled(new Gson());
		tracker = new Tracker(catalog);
	}

	@Test
	public void theHydraRingRowNeedsThreePiecesAndShowsTheRing()
	{
		DropDefinition ring = catalog.byName("Alchemical Hydra").getDrops().get(0);
		assertEquals("Brimstone ring pieces", ring.getDisplayName());
		assertEquals(3, ring.pieces());
		assertEquals("Brimstone ring", ring.iconItem());
		assertEquals(3, ring.piecesMissing(0));
		assertEquals(2, ring.piecesMissing(1));
		assertEquals(0, ring.piecesMissing(3));
		assertEquals(0, ring.piecesMissing(7));
		// any other row needs one
		assertEquals(1, catalog.byName("Alchemical Hydra").getDrops().get(1).pieces());
	}

	@Test
	public void atLeastIsTheBinomialTail()
	{
		// one: 1 - (1 - p)^n, as before
		assertEquals(-Math.expm1(500 * Math.log1p(-RING)), LogForecast.atLeast(1, 500, RING), 1e-12);
		// three, added up by hand
		double fewer = 0;
		for (int j = 0; j < 3; j++)
		{
			fewer += choose(500, j) * Math.pow(RING, j) * Math.pow(1 - RING, 500 - j);
		}
		assertEquals(1 - fewer, LogForecast.atLeast(3, 500, RING), 1e-12);
		assertEquals(0, LogForecast.atLeast(3, 2, RING), 0);
		assertEquals(1, LogForecast.atLeast(3, 3, 1), 0);
	}

	@Test
	public void threePiecesTakeLongerThanOneOrThreeSeparateItems()
	{
		long one = LogForecast.raidsFor(new double[]{RING}, 0.5);
		long separate = LogForecast.raidsFor(new double[]{RING, RING, RING}, 0.5);
		long pieces = LogForecast.raidsFor(new double[]{RING}, new int[]{3}, 0.5);
		assertTrue(one < separate);
		assertTrue(separate < pieces);
		// the median of three drops at 1/181: half of players have all three by then, not before
		assertTrue(LogForecast.atLeast(3, pieces, RING) >= 0.5);
		assertTrue(LogForecast.atLeast(3, pieces - 1, RING) < 0.5);
		// one each is the old answer
		assertEquals(separate, LogForecast.raidsFor(new double[]{RING, RING, RING}, new int[]{1, 1, 1}, 0.5));
		// the average wait for three drops is three average waits: 543 kills
		assertEquals(3 * 181.0, GreenLog.averageSeconds(new double[]{RING}, new int[]{3}, 1), 0.5);
	}

	@Test
	public void theGreenLogWaitsForEveryRingPiece()
	{
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Hydra's eye", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 693"), page);
		GreenLog.Page hydra = GreenLog.compute(tracker, web, RAIDS, boss -> true, false).get("Alchemical Hydra");
		assertNotNull(hydra);
		assertTrue(hydra.getMissing().contains("Brimstone ring pieces (2 to go)"));
		// with all three, the ring is off the list
		page.put("Hydra's fang", 1);
		page.put("Hydra's heart", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 693"), page);
		hydra = GreenLog.compute(tracker, web, RAIDS, boss -> true, false).get("Alchemical Hydra");
		for (String missing : hydra.getMissing())
		{
			assertFalse(missing, missing.startsWith("Brimstone ring pieces"));
		}
	}

	@Test
	public void theTimePageReadsTheRingAsNotDoneUntilThreePieces()
	{
		Map<String, Integer> page = new LinkedHashMap<>();
		page.put("Hydra's eye", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 693"), page);
		ItemTime ring = ringTime();
		// the engine's own reading stays "at least one", as the original has it
		assertTrue(ring.isHave());
		assertEquals(2, ItemTimes.piecesMissing(catalog, ring));
		ItemTime needs = ItemTimes.asLogNeeds(catalog, ring);
		assertFalse(needs.isHave());
		assertEquals(2 * ring.getSecondsPerDrop(), needs.getSecondsPerDrop(), 1e-9);
		assertNotNull(needs.getHint());
		// with all three it is had, and comes back as it is
		page.put("Hydra's fang", 1);
		page.put("Hydra's heart", 1);
		tracker.onClogPage("Alchemical Hydra", Collections.singletonList("Alchemical Hydra kills: 693"), page);
		ring = ringTime();
		assertEquals(0, ItemTimes.piecesMissing(catalog, ring));
		assertSame(ring, ItemTimes.asLogNeeds(catalog, ring));
	}

	private ItemTime ringTime()
	{
		for (ItemTime t : ItemTimes.compute(tracker, web, RAIDS))
		{
			if (t.getBoss().equals("Alchemical Hydra") && t.getLabel().equals("Brimstone ring pieces"))
			{
				return t;
			}
		}
		throw new AssertionError("no time for the ring pieces");
	}

	private static double choose(int n, int k)
	{
		double c = 1;
		for (int i = 0; i < k; i++)
		{
			c = c * (n - i) / (i + 1);
		}
		return c;
	}
}
