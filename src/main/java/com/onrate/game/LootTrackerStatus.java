package com.onrate.game;

import java.util.Collection;
import java.util.function.Predicate;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;

/**
 * Whether RuneLite's Loot Tracker plugin is turned on. On Rate sees new drops through the
 * {@code LootReceived} event, and only the Loot Tracker posts it, so with it off no new drop arrives.
 * <p>
 * Read through {@link PluginManager#isPluginEnabled}, the same check RuneLite's own plugin list uses
 * (the "runelite" setting named after the plugin, or its default when never set), so no setting key is
 * guessed here. When it can't be told for sure (no plugin manager, the Loot Tracker isn't loaded, or
 * the check fails) the answer is {@link State#UNKNOWN} and nothing should be shown.
 */
public final class LootTrackerStatus
{
	public enum State
	{
		ENABLED,
		DISABLED,
		UNKNOWN,
	}

	private LootTrackerStatus()
	{
	}

	/** The Loot Tracker's state from RuneLite's plugin manager; UNKNOWN when it can't be told. */
	public static State of(PluginManager plugins)
	{
		if (plugins == null)
		{
			return State.UNKNOWN;
		}
		try
		{
			return of(plugins.getPlugins(), plugins::isPluginEnabled);
		}
		catch (RuntimeException e)
		{
			return State.UNKNOWN;
		}
	}

	/** As {@link #of(PluginManager)}, for the loaded plugins and a way to ask whether one is turned on. */
	static State of(Collection<Plugin> loaded, Predicate<Plugin> enabled)
	{
		if (loaded == null)
		{
			return State.UNKNOWN;
		}
		for (Plugin p : loaded)
		{
			if (isLootTracker(p))
			{
				try
				{
					return enabled.test(p) ? State.ENABLED : State.DISABLED;
				}
				catch (RuntimeException e)
				{
					return State.UNKNOWN;
				}
			}
		}
		// not loaded (yet): don't claim it is off
		return State.UNKNOWN;
	}

	/** True for RuneLite's Loot Tracker plugin. */
	public static boolean isLootTracker(Plugin plugin)
	{
		return plugin instanceof LootTrackerPlugin;
	}
}
