package com.onrate.game;

import static org.junit.Assert.assertEquals;

import com.onrate.game.LootTrackerStatus.State;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import org.junit.Test;

/** Whether RuneLite's Loot Tracker is on, as the panel's note needs it (audit C3). */
public class LootTrackerStatusTest
{
	private final Plugin lootTracker = new LootTrackerPlugin();
	private final Plugin other = new Plugin()
	{
	};

	@Test
	public void enabledWhenThePluginManagerSaysSo()
	{
		assertEquals(State.ENABLED, LootTrackerStatus.of(Arrays.asList(other, lootTracker), p -> true));
	}

	@Test
	public void disabledWhenThePluginManagerSaysSo()
	{
		// only the Loot Tracker's own answer counts, not another plugin's
		assertEquals(State.DISABLED, LootTrackerStatus.of(Arrays.asList(other, lootTracker), p -> p != lootTracker));
	}

	@Test
	public void unknownWhenItCantBeTold()
	{
		// no plugin manager (yet)
		assertEquals(State.UNKNOWN, LootTrackerStatus.of(null));
		// the plugins aren't loaded, or the Loot Tracker isn't among them (startup, or removed)
		assertEquals(State.UNKNOWN, LootTrackerStatus.of(null, p -> false));
		assertEquals(State.UNKNOWN, LootTrackerStatus.of(Collections.emptyList(), p -> false));
		List<Plugin> withoutIt = Collections.singletonList(other);
		assertEquals(State.UNKNOWN, LootTrackerStatus.of(withoutIt, p -> false));
		// the check itself fails
		assertEquals(State.UNKNOWN, LootTrackerStatus.of(Collections.singletonList(lootTracker), p ->
		{
			throw new IllegalStateException("no config yet");
		}));
	}

	@Test
	public void recognisesOnlyTheLootTracker()
	{
		assertEquals(true, LootTrackerStatus.isLootTracker(lootTracker));
		assertEquals(false, LootTrackerStatus.isLootTracker(other));
		assertEquals(false, LootTrackerStatus.isLootTracker(null));
	}
}
