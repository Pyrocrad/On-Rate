package com.onrate.game;

import com.expecteddrops.ExpectedDropsConfig;
import com.expecteddrops.DuoMode;
import com.onrate.engine.stats.RaidChance;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.RaidHabits;
import java.util.Locale;
import java.util.function.Supplier;

/** The player's settings, as the numbers the drop maths needs. */
public final class ConfigRaidSettings implements RaidSettings
{
	private final ExpectedDropsConfig config;
	/** How you usually raid, from your recorded raids; used where a setting is still at its default. */
	private final Supplier<RaidHabits> habits;

	public ConfigRaidSettings(ExpectedDropsConfig config)
	{
		this(config, RaidHabits::none);
	}

	public ConfigRaidSettings(ExpectedDropsConfig config, Supplier<RaidHabits> habits)
	{
		this.config = config;
		this.habits = habits == null ? RaidHabits::none : habits;
	}

	/** The settings as they come: a setting still at one of these was never changed by the player. */
	private static final ExpectedDropsConfig DEFAULTS = new ExpectedDropsConfig()
	{
	};

	@Override
	public double uniqueChance(String raid)
	{
		double percent;
		switch (raid)
		{
			case "cox":
				percent = config.coxUniqueChance();
				break;
			case "coxCm":
				percent = config.coxCmUniqueChance();
				break;
			case "tob":
				percent = config.tobTeamChance() / teamSize(raid);
				break;
			case "tobHm":
				percent = config.tobHmTeamChance() / teamSize(raid);
				break;
			case "toaEntry":
				percent = config.toaEntryUniqueChance();
				break;
			case "toa":
				percent = config.toaUniqueChance();
				break;
			case "toaExpert":
				percent = config.toaExpertUniqueChance();
				break;
			case "fractured":
				// an even share of the damage, no deaths, at the usual team size
				return RaidChance.tfaEven(teamSize(raid), 0, tfaBaseChance());
			default:
				percent = 0;
		}
		if (learnedRaids(raid) > 0 && !RaidHabits.usesTeamSize(family(raid)))
		{
			return habits.get().chance(raid);
		}
		return Math.max(0, Math.min(100, percent)) / 100.0;
	}

	@Override
	public int teamSize(String raid)
	{
		if (raid.startsWith("tob") || "fractured".equals(raid))
		{
			if (learnedRaids(raid) > 0)
			{
				return habits.get().teamSize(raid);
			}
			return raid.startsWith("tob") ? tobTeamSize() : tfaTeamSize();
		}
		return 1;
	}

	/**
	 * How many of your recorded raids a raid mode's chance (or team size) comes from instead of the
	 * setting, 0 when it uses the setting: the setting was changed, or there aren't enough raids yet.
	 */
	@Override
	public int learnedRaids(String raid)
	{
		if (raid == null || !atDefault(raid))
		{
			return 0;
		}
		RaidHabits h = habits.get();
		boolean known = RaidHabits.usesTeamSize(family(raid)) ? h.teamSize(raid) > 0 : !Double.isNaN(h.chance(raid));
		return known ? h.raids(raid) : 0;
	}

	/** "tobHm" is a Theatre of Blood mode, "coxCm" a Chambers one... */
	private static String family(String raid)
	{
		return raid.startsWith("tob") ? "tob" : raid.startsWith("toa") ? "toa" : raid.startsWith("cox") ? "cox" : raid;
	}

	/** True when the setting a raid mode would use was left as it came. */
	private boolean atDefault(String raid)
	{
		switch (raid)
		{
			case "cox":
				return config.coxUniqueChance() == DEFAULTS.coxUniqueChance();
			case "coxCm":
				return config.coxCmUniqueChance() == DEFAULTS.coxCmUniqueChance();
			case "tob":
			case "tobHm":
				// the team chance is the game's; what you choose is the team size
				return config.tobTeamSize() == DEFAULTS.tobTeamSize();
			case "toaEntry":
				return config.toaEntryUniqueChance() == DEFAULTS.toaEntryUniqueChance();
			case "toa":
				return config.toaUniqueChance() == DEFAULTS.toaUniqueChance();
			case "toaExpert":
				return config.toaExpertUniqueChance() == DEFAULTS.toaExpertUniqueChance();
			case "fractured":
				return config.tfaTeamSize() == DEFAULTS.tfaTeamSize();
			default:
				return false;
		}
	}

	@Override
	public double tfaBaseChance()
	{
		return Math.max(0, Math.min(100, config.tfaTeamChance())) / 100.0;
	}

	@Override
	public double contribution(String team, int kills)
	{
		if (!isDuoTeam(team) || kills <= 0)
		{
			return 1.0;
		}
		int duo = duoKills(team, kills);
		return (kills - duo + 0.5 * duo) / kills;
	}

	@Override
	public String contributionNote(String team, int kills)
	{
		if (!isDuoTeam(team))
		{
			return null;
		}
		int duo = duoKills(team, kills);
		if (duo == 0)
		{
			return "Counted as solo kills. If you duo, change it under Team bosses in the settings.";
		}
		if (duo >= kills)
		{
			return "Counted as duo kills: about half the uniques of a solo kill (the pet chance stays the same). "
				+ "If you solo, change it under Team bosses in the settings.";
		}
		return String.format(Locale.ROOT, "Counted as %,d solo and %,d duo kills (a duo gives about half the uniques).",
			kills - duo, duo);
	}

	private int tobTeamSize()
	{
		return Math.max(1, Math.min(5, config.tobTeamSize()));
	}

	private int tfaTeamSize()
	{
		return Math.max(1, Math.min(RaidChance.TFA_MAX_TEAM, config.tfaTeamSize()));
	}

	private static boolean isDuoTeam(String team)
	{
		return "royalTitans".equals(team) || "yama".equals(team);
	}

	/** How many of these kills count as duo kills, from the boss's solo/duo setting. */
	private int duoKills(String team, int kills)
	{
		boolean yama = "yama".equals(team);
		DuoMode mode = yama ? config.yamaMode() : config.royalTitansMode();
		int solo = yama ? config.yamaSoloKills() : config.royalTitansSoloKills();
		switch (mode)
		{
			case DUO:
				return kills;
			case SOLO:
				return 0;
			case MIXED:
				return kills - Math.max(0, Math.min(kills, solo));
			default:
				return kills;
		}
	}
}
