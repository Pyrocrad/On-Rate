package com.expecteddrops.ui;

import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.RaidInsights;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import java.util.Collections;
import java.util.function.ToLongFunction;
import org.junit.Test;

/**
 * One draw of a raid page goes through the saved raids once (twice with the GP value on), however many
 * parts of the page ask: the sections, the line of facts, History's PB tags and every item's hover.
 */
public class RaidInsightsPerDrawTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final String COX = "Chambers of Xeric";
	private long now = System.currentTimeMillis() - 100L * 3_600_000;
	private int kc;
	private final int[] calls = new int[1];

	private Tracker tracker()
	{
		Tracker tracker = new Tracker(Bundled.catalog(new Gson()))
		{
			@Override
			public synchronized RaidInsights raidInsights(String bossName, ToLongFunction<String> prices)
			{
				calls[0]++;
				return super.raidInsights(bossName, prices);
			}
		};
		tracker.setSettings(raid -> 0.035);
		tracker.onClogPage(COX, Collections.singletonList("Chambers of Xeric completions: 0"), Collections.singletonMap("Twisted bow", 0));
		for (int i = 0; i < 6; i++)
		{
			raid(tracker);
		}
		return tracker;
	}

	private void raid(Tracker tracker)
	{
		now += 3_600_000;
		tracker.onRaidRecord(RaidRecord.cox(30_000, 60_000, 2, 2), now);
		tracker.onChatMessage("Your completed Chambers of Xeric count is: <col=ff0000>" + (++kc) + "</col>.", now + 1000);
	}

	private static ExpectedDropsPanel panel(Tracker tracker, boolean gp)
	{
		RaidSettings raids = raid -> 0.035;
		ExpectedDropsPanel panel = new ExpectedDropsPanel(tracker, null, () -> raids, s -> -1, () -> { },
			s -> 1_000_000L, () -> new ViewOptions(ItemView.TARGET_BARS, gp, false, false, false, true));
		panel.setSize(225, 6000);
		return panel;
	}

	@Test
	public void aRaidPageWorksItsInsightsOutOncePerDraw()
	{
		ExpectedDropsPanel panel = panel(tracker(), false);
		panel.showBoss(COX);
		calls[0] = 0;
		panel.refresh();
		assertTrue("the page asked for the raid's insights", calls[0] >= 1);
		assertTrue("worked out " + calls[0] + " times in one draw", calls[0] <= 1);
	}

	@Test
	public void withTheGpValueOnItIsOnceWithPricesAndOnceWithout()
	{
		ExpectedDropsPanel panel = panel(tracker(), true);
		panel.showBoss(COX);
		calls[0] = 0;
		panel.refresh();
		assertTrue("the page asked for the raid's insights", calls[0] >= 1);
		assertTrue("worked out " + calls[0] + " times in one draw", calls[0] <= 2);
	}

	@Test
	public void theNextDrawSeesANewRaid()
	{
		Tracker tracker = tracker();
		ExpectedDropsPanel panel = panel(tracker, false);
		panel.showBoss(COX);
		panel.refresh();
		raid(tracker);
		calls[0] = 0;
		panel.refresh();
		assertTrue("nothing is kept from the last draw", calls[0] >= 1);
	}
}
