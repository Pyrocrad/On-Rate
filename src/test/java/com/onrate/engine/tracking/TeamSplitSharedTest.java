package com.onrate.engine.tracking;

import static org.junit.Assert.assertEquals;

import com.expecteddrops.ui.ExpectedDropsPanel;
import com.google.gson.Gson;
import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import org.junit.Test;

/** The Teams row's "counts as" total is the same kill share Statistics gives the expected number. */
public class TeamSplitSharedTest
{
	@Test
	public void statisticsSharedKillsUsesTheSumTheTeamsRowShows() throws Exception
	{
		BossCatalog catalog = Bundled.catalog(new Gson());
		RaidSettings raids = raid -> 0.035;
		BossProgress p = new BossProgress();
		p.teamKills.put(1, 12);
		p.teamKills.put(2, 40);
		p.teamKills.put(3, 95);
		p.teamKills.put(7, 3);
		p.olderShare = 0.25;
		int kc = 160;
		double expected = Statistics.sharedKills(catalog.byName("Nex"), p, kc, raids);
		// 12 + 40/2 + 95/3 + 3/7 from the real sizes, plus 10 earlier kills at 1/4 each
		assertEquals(12 + 20 + 95 / 3.0 + 3 / 7.0 + 10 * 0.25, expected, 1e-9);
		assertEquals(expected, ExpectedDropsPanel.teamSplitShared(p.teamKills, kc, 0.25), 1e-9);
	}
}
