package com.expecteddrops;

import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.tracking.Tracker;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Test;

/** Events that arrive while On Rate is being stopped on another thread must not fail. */
public class ShutdownGuardTest
{
	private static void set(Object target, String name, Object value) throws Exception
	{
		Field f = ExpectedDropsPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	@Test
	public void aVarbitDuringShutdownIsIgnored() throws Exception
	{
		ExpectedDropsPlugin plugin = new ExpectedDropsPlugin();
		// shutDown has cleared the roll counters but not yet the tracker check
		set(plugin, "tracker", new Tracker(Bundled.catalog(new Gson())));
		set(plugin, "rolls", null);
		plugin.onVarbitChanged(new VarbitChanged());
	}

	@Test
	public void lootDuringShutdownUsesTheTrackerItStartedWith() throws Exception
	{
		ExpectedDropsPlugin plugin = new ExpectedDropsPlugin();
		boolean[] askedOther = new boolean[1];
		Tracker stopping = new Tracker(Bundled.catalog(new Gson()))
		{
			@Override
			public synchronized String onLoot(String source, int amount, Collection<LootItem> items, long nowMillis)
			{
				try
				{
					// shutDown runs on another thread while this loot is handled
					set(plugin, "tracker", null);
				}
				catch (Exception e)
				{
					throw new AssertionError(e);
				}
				return null;
			}

			@Override
			public synchronized boolean onOtherLoot(String source, Collection<LootItem> items)
			{
				askedOther[0] = true;
				return false;
			}
		};
		set(plugin, "tracker", stopping);
		plugin.onLootReceived(new LootReceived("Goblin", 2, LootRecordType.NPC, Collections.emptyList(), 1, null));
		assertTrue("the rest of the event uses the same tracker", askedOther[0]);
	}
}
