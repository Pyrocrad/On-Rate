package com.onrate.game;

import com.onrate.engine.raids.RaidDataImport;
import com.onrate.engine.tracking.Tracker;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raid times from the Raid Data Tracker plugin's files, read on a worker thread and added on the
 * client thread, only to the RuneLite profile they were read for.
 *
 * <p>Reading takes a moment. If another profile is loaded meanwhile (another account, or a Leagues
 * world of the same account), what was read is dropped: it is never added to whichever account
 * happens to be loaded when the read finishes. Nothing is added after {@link #stop()}.
 */
public final class RaidTimesImport
{
	private static final Logger log = LoggerFactory.getLogger(RaidTimesImport.class);

	private final Executor worker;
	private final Consumer<Runnable> clientThread;
	private final Supplier<String> loadedProfile;
	private volatile boolean stopped;

	/**
	 * @param worker reads the files (the plugin's executor)
	 * @param clientThread runs a task on the client thread, where the profile is loaded and events arrive
	 * @param loadedProfile the RuneLite profile key whose data the tracker holds now (ProfileStore)
	 */
	public RaidTimesImport(Executor worker, Consumer<Runnable> clientThread, Supplier<String> loadedProfile)
	{
		this.worker = worker;
		this.clientThread = clientThread;
		this.loadedProfile = loadedProfile;
	}

	/**
	 * True when raid times may be imported into this profile: a standard RuneScape profile. Raid Data
	 * Tracker's records carry the account but not the world type, so in a Leagues, Deadman or other
	 * seasonal profile of the same account they would bring in the main game's raids. False too when
	 * the profile isn't known.
	 */
	public static boolean importsInto(String profileKey, List<RuneScapeProfile> profiles)
	{
		if (profileKey == null || profiles == null)
		{
			return false;
		}
		for (RuneScapeProfile p : profiles)
		{
			if (profileKey.equals(p.getKey()))
			{
				return p.getType() == RuneScapeProfileType.STANDARD;
			}
		}
		return false;
	}

	/**
	 * Reads on the worker, then adds the times on the client thread if {@code profile} is still the
	 * loaded one; {@code changed} is told each boss that gained times. Adding a time twice is harmless.
	 */
	public void start(String profile, Supplier<RaidDataImport.Times> read, Tracker tracker, Consumer<String> changed)
	{
		if (stopped || profile == null || tracker == null)
		{
			return;
		}
		worker.execute(() ->
		{
			RaidDataImport.Times times;
			try
			{
				times = read.get();
			}
			catch (RuntimeException e)
			{
				log.debug("Could not import raid times from Raid Data Tracker", e);
				return;
			}
			if (times == null || stopped)
			{
				return;
			}
			clientThread.accept(() -> apply(profile, times, tracker, changed));
		});
	}

	private void apply(String profile, RaidDataImport.Times times, Tracker tracker, Consumer<String> changed)
	{
		if (stopped)
		{
			return;
		}
		if (!profile.equals(loadedProfile.get()))
		{
			log.debug("Raid Data Tracker times read for another profile were not imported");
			return;
		}
		java.util.Set<String> bosses = new java.util.LinkedHashSet<>();
		for (Map.Entry<String, Map<String, List<long[]>>> boss : times.byBoss().entrySet())
		{
			int added = 0;
			for (Map.Entry<String, List<long[]>> part : boss.getValue().entrySet())
			{
				added += tracker.importRaidTimes(boss.getKey(), part.getKey(), part.getValue());
			}
			if (added > 0)
			{
				bosses.add(boss.getKey());
			}
		}
		// the whole raids: their points, team and raid level, saved like the raids On Rate saw
		for (Map.Entry<String, List<RaidDataImport.Raid>> boss : times.raids().entrySet())
		{
			List<com.onrate.engine.tracking.ImportedRaid> raids = new java.util.ArrayList<>();
			for (RaidDataImport.Raid r : boss.getValue())
			{
				raids.add(new com.onrate.engine.tracking.ImportedRaid(r.part, r.kc, r.when, r.millis, r.teamSize,
					r.personalPoints, r.totalPoints, r.raidLevel, r.unique));
			}
			// the uniques are kept in memory even when no raid is new, so the graph can name them
			if (tracker.importRaids(boss.getKey(), raids) > 0 || !raids.isEmpty())
			{
				bosses.add(boss.getKey());
			}
		}
		bosses.forEach(changed);
	}

	/** The plugin stopped: nothing read from now on is added. */
	public void stop()
	{
		stopped = true;
	}
}
