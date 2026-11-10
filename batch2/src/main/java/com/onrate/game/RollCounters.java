package com.onrate.game;

import com.onrate.engine.tracking.Tracker;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.runelite.api.Client;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Content with loot per reward roll (Tempoross, Wintertodt): the game keeps a counter of rewards
 * not yet collected. When it drops, each reward collected is one roll; the counter and the total
 * subdues also give the number of rolls from before On Rate was running.
 */
public final class RollCounters
{
	/** Boss name, the game's counter of uncollected rewards, and the total subdues. */
	private static final Object[][] COUNTERS = {
		{"Tempoross", VarbitID.TEMPOROSS_REWARDPERMITS, VarPlayerID.TOTAL_TEMPOROSS_KILLS},
		{"Wintertodt", VarbitID.WINT_REWARD_POOL, VarPlayerID.TOTAL_WINTERTODT_KILLS},
	};

	private final Client client;
	private final Tracker tracker;
	/** Each counter's value last seen this login; a drop means rewards were collected. */
	private final Map<String, Integer> last = new HashMap<>();

	public RollCounters(Client client, Tracker tracker)
	{
		this.client = client;
		this.tracker = tracker;
	}

	/** @param changed told the boss whose numbers changed, and whether a roll was just counted */
	public void onVarbit(VarbitChanged event, BiConsumer<String, Boolean> changed)
	{
		for (Object[] c : COUNTERS)
		{
			String boss = (String) c[0];
			int rewardsVarbit = (Integer) c[1];
			int subduesVarp = (Integer) c[2];
			if (event.getVarbitId() != rewardsVarbit && event.getVarpId() != subduesVarp)
			{
				continue;
			}
			int held = client.getVarbitValue(rewardsVarbit);
			int subdues = client.getVarpValue(subduesVarp);
			String baseline = tracker.onRollBaseline(boss, subdues, held);
			if (baseline != null)
			{
				changed.accept(baseline, false);
			}
			Integer before = last.get(boss);
			if (before != null && held < before)
			{
				// each reward collected is one reward roll
				String rolled = tracker.onRewardRolls(boss, before - held, System.currentTimeMillis());
				if (rolled != null)
				{
					changed.accept(rolled, true);
				}
			}
			last.put(boss, held);
		}
	}

	/** A new login sends the counters again: that's not collecting rewards. */
	public void forget()
	{
		last.clear();
	}
}
