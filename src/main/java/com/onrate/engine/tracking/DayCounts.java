package com.onrate.engine.tracking;

import java.util.HashSet;
import java.time.Instant;
import java.util.Set;
import java.time.ZoneId;

import com.onrate.engine.data.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The day counts behind Today and Last 7 days: kills, uniques and expected per boss per local day,
 * the oldest days dropped, History's older kills added once per boss per session, and a raid's
 * expected moved along when its points come in after the kill was counted.
 * <p>
 * Package-private and unsynchronized: reached only through {@link Tracker}, under its lock.
 */
final class DayCounts
{
	private final TrackerContext ctx;
	private final BossCatalog catalog;
	private final Statistics statistics;

	/** Days kept for the Recent tab's day counts. */
	static final int DAYS_KEPT = 14;
	/** Bosses whose History was already added to the day counts this session. */
	private final Set<String> daysFilled = new HashSet<>();

	DayCounts(TrackerContext ctx, Statistics statistics)
	{
		this.ctx = ctx;
		this.catalog = ctx.catalog;
		this.statistics = statistics;
	}

	/** Another account was loaded: its History is added to the day counts again when asked. */
	void clear()
	{
		daysFilled.clear();
	}

	/** This boss's History was loaded or reset: it is added to the day counts again when asked. */
	void forget(String bossName)
	{
		daysFilled.remove(bossName);
	}

	/** The day counter of a boss on the day of this time (local time zone), created when missing. */
	DayCount dayCount(String bossName, long millis)
	{
		long day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
		Map<String, Map<String, DayCount>> days = ctx.state().days();
		if (!days.containsKey(String.valueOf(day)))
		{
			// a new day: forget the ones that are too old
			days.keySet().removeIf(d ->
			{
				try
				{
					return Long.parseLong(d) <= day - DAYS_KEPT;
				}
				catch (NumberFormatException e)
				{
					return true;
				}
			});
		}
		return days.computeIfAbsent(String.valueOf(day), d -> new HashMap<>()).computeIfAbsent(bossName, b ->
		{
			DayCount count = new DayCount();
			count.first = millis;
			return count;
		});
	}

	/**
	 * Adds kills from History to the day counts when they're older than what the counts hold, so
	 * Today and Last 7 days include the kills from before On Rate counted days. Safe to repeat:
	 * a kill is only added when it's older than the earliest one already counted that day.
	 */
	void fillDaysFromHistory(BossDefinition boss, long nowMillis)
	{
		if (!daysFilled.add(boss.getName()))
		{
			return;
		}
		BossProgress p = ctx.state().peek(boss.getName());
		if (p == null || p.kills.isEmpty())
		{
			return;
		}
		ZoneId zone = ZoneId.systemDefault();
		long today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay();
		Map<Long, List<KillEntry>> toAdd = new HashMap<>();
		for (KillEntry k : p.kills)
		{
			if (k.time <= 0)
			{
				continue;
			}
			long day = Instant.ofEpochMilli(k.time).atZone(zone).toLocalDate().toEpochDay();
			if (day > today || day <= today - DAYS_KEPT)
			{
				continue;
			}
			Map<String, DayCount> forDay = ctx.state().days().get(String.valueOf(day));
			DayCount count = forDay == null ? null : forDay.get(boss.getName());
			// no count that day yet, or this kill came before the first one it holds
			if (count == null || count.first != null && k.time < count.first)
			{
				toAdd.computeIfAbsent(day, d -> new ArrayList<>()).add(k);
			}
		}
		for (List<KillEntry> kills : toAdd.values())
		{
			long earliest = Long.MAX_VALUE;
			DayCount count = null;
			for (KillEntry k : kills)
			{
				count = dayCount(boss.getName(), k.time);
				count.kills++;
				count.expected += statistics.expectedFromKill(boss, k);
				for (KillEntry.DropEntry d : k.getDrops())
				{
					DropDefinition drop = Statistics.dropNamed(boss, d.getName());
					if (drop != null && drop.countsTowardAny())
					{
						count.uniques++;
					}
				}
				earliest = Math.min(earliest, k.time);
			}
			count.first = count.first == null ? earliest : Math.min(count.first, earliest);
		}
	}

	/** Names of the uniques History has for this boss between these days (local epoch days). */
	List<String> uniqueNames(String bossName, long fromDay, long toDay)
	{
		List<String> names = new ArrayList<>();
		BossDefinition boss = catalog.byName(bossName);
		BossProgress p = ctx.state().peek(bossName);
		if (boss == null || p == null)
		{
			return names;
		}
		ZoneId zone = ZoneId.systemDefault();
		for (KillEntry k : p.kills)
		{
			if (!k.isDateKnown())
			{
				continue;
			}
			long day = Instant.ofEpochMilli(k.time).atZone(zone).toLocalDate().toEpochDay();
			if (day < fromDay || day > toDay)
			{
				continue;
			}
			for (KillEntry.DropEntry d : k.getDrops())
			{
				DropDefinition drop = Statistics.rowOfSaved(boss, d.getName());
				if (drop != null && drop.countsTowardAny())
				{
					names.add(d.getName());
				}
			}
		}
		return names;
	}

	/** Tests only: drops the day counts, as a version from before they existed would have. */
	void forgetDaysForTest()
	{
		ctx.state().days().clear();
		daysFilled.clear();
	}

	/**
	 * Kills, uniques and expected per boss over the last {@code days} days (1 = today), most
	 * kills first. The first entry is the total of all bosses (its boss is null).
	 */
	List<BossDays> recentDays(int days, long nowMillis)
	{
		for (BossDefinition boss : catalog.getBosses())
		{
			fillDaysFromHistory(boss, nowMillis);
		}
		long today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
		Map<String, DayCount> perBoss = new HashMap<>();
		DayCount total = new DayCount();
		for (Map.Entry<String, Map<String, DayCount>> d : ctx.state().days().entrySet())
		{
			long day;
			try
			{
				day = Long.parseLong(d.getKey());
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			if (day > today || day <= today - days)
			{
				continue;
			}
			for (Map.Entry<String, DayCount> b : d.getValue().entrySet())
			{
				DayCount sum = perBoss.computeIfAbsent(b.getKey(), k -> new DayCount());
				sum.kills += b.getValue().kills;
				sum.uniques += b.getValue().uniques;
				sum.expected += b.getValue().expected;
				total.kills += b.getValue().kills;
				total.uniques += b.getValue().uniques;
				total.expected += b.getValue().expected;
			}
		}
		List<BossDays> result = new ArrayList<>();
		result.add(new BossDays(null, total, new ArrayList<>()));
		perBoss.entrySet().stream()
			.filter(e -> e.getValue().kills > 0 || e.getValue().uniques > 0)
			.sorted((a, b) -> a.getValue().kills != b.getValue().kills ? b.getValue().kills - a.getValue().kills
				: a.getKey().compareTo(b.getKey()))
			.forEach(e -> result.add(new BossDays(e.getKey(), e.getValue(),
				uniqueNames(e.getKey(), today - days + 1, today))));
		return result;
	}

	/**
	 * A raid is counted at the purple chance from the settings first; when its points come in a
	 * moment later, this moves the day's expected along, so Recent uses your points too.
	 */
	void shiftToday(String bossName, double change)
	{
		if (Double.isNaN(change) || Math.abs(change) < 1e-12)
		{
			return;
		}
		BossProgress progress = ctx.state().peek(bossName);
		// the latest kill with a date: drops added by hand have none
		KillEntry latest = null;
		for (int i = progress == null ? -1 : progress.kills.size() - 1; i >= 0 && latest == null; i--)
		{
			latest = progress.kills.get(i).isDateKnown() ? progress.kills.get(i) : null;
		}
		DayCount day = dayCount(bossName, latest == null ? System.currentTimeMillis() : latest.time);
		day.expected = Math.max(0, day.expected + change);
	}
}

