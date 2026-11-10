package com.onrate.engine.tracking;

import com.onrate.engine.data.BossCatalog;
import com.onrate.engine.stats.RaidSettings;
import java.util.HashSet;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * What the parts of the tracker share: the catalogue, the account loaded now, the player's settings,
 * how to count the team at a kill, and which bosses' History and raids changed since the last save.
 *
 * <p>The account's state is swapped when another account logs in ({@link Tracker#setState}). Parts of
 * the tracker must therefore never keep the state object itself: they ask for {@link #state()} each
 * time, so they always see the account that is loaded now. Only used under the tracker's lock.
 */
final class TrackerContext
{
	final BossCatalog catalog;
	private TrackerState state = new TrackerState();
	private RaidSettings settings = raid -> 0;
	/** Your team at a boss kill (you included), by boss name; 0 when unknown or turned off. */
	private ToIntFunction<String> teamSize = boss -> 0;
	/** Bosses whose History changed since the last save (it is saved per boss). */
	final Set<String> killsChanged = new HashSet<>();
	/** Raids whose saved raids changed since the last save (saved per raid). */
	final Set<String> raidsChanged = new HashSet<>();

	TrackerContext(BossCatalog catalog)
	{
		this.catalog = catalog;
	}

	/** The account loaded now. Ask each time; never keep it. */
	TrackerState state()
	{
		return state;
	}

	void setState(TrackerState state)
	{
		this.state = state;
	}

	RaidSettings settings()
	{
		return settings;
	}

	void setSettings(RaidSettings settings)
	{
		this.settings = settings;
	}

	ToIntFunction<String> teamSize()
	{
		return teamSize;
	}

	void setTeamSize(ToIntFunction<String> teamSize)
	{
		this.teamSize = teamSize;
	}
}
