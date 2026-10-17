package com.expecteddrops;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(ExpectedDropsConfig.GROUP)
public interface ExpectedDropsConfig extends Config
{
	String GROUP = "expecteddrops";

	@ConfigItem(
		keyName = "autoSwitch",
		name = "Follow last kill",
		description = "Switch the panel to the boss you just killed",
		position = 1
	)
	default boolean autoSwitch()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWelcome",
		name = "Show welcome guide",
		description = "Show the getting-started steps at the top of the panel. 'Got it' turns this off.",
		position = 2
	)
	default boolean showWelcome()
	{
		return true;
	}

	@ConfigItem(
		keyName = "syncReminders",
		name = "Sync reminders",
		description = "After killing a boss whose history isn't loaded yet, say in chat which collection log page to open (once per session)",
		position = 3
	)
	default boolean syncReminders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dropMessages",
		name = "Drop messages",
		description = "When a unique drops, say in chat how many kills it took and how that compares with the drop rate",
		position = 4
	)
	default boolean dropMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "milestoneMessages",
		name = "Dry milestone messages",
		description = "Say in chat when an item passes 1x, 2x, 3x, 5x or 10x its drop rate without dropping",
		position = 5
	)
	default boolean milestoneMessages()
	{
		return true;
	}

	@ConfigSection(
		name = "Team bosses",
		description = "At team bosses your chance of a unique scales with your share of the damage",
		position = 20
	)
	String teamSection = "team";

	@ConfigItem(
		keyName = "royalTitansMode",
		name = "Royal Titans",
		description = "How you kill the Royal Titans. In a duo you get about half the uniques of a solo kill; the pet chance stays the same.",
		position = 21,
		section = teamSection
	)
	default RoyalTitansMode royalTitansMode()
	{
		return RoyalTitansMode.SOLO;
	}

	@ConfigItem(
		keyName = "royalTitansDuoKills",
		name = "Royal Titans duo kills",
		description = "Only used with 'Some of each': how many of your Royal Titans kills were in a duo. The rest count as solo.",
		position = 22,
		section = teamSection
	)
	@Range(min = 0)
	default int royalTitansDuoKills()
	{
		return 0;
	}

	@ConfigSection(
		name = "Raids",
		description = "Raid unique chances depend on your points and team, so set them here",
		position = 10
	)
	String raidsSection = "raids";

	@ConfigItem(
		keyName = "readRaidPoints",
		name = "Use raid points",
		description = "At the end of each raid, use your points for that raid's purple chance: read from the game in Chambers of Xeric, estimated from your damage in Tombs of Amascut (like Raid Data Tracker does). Raids without points (older ones, synced from the collection log, Theatre of Blood) use the percentages below.",
		position = 11,
		section = raidsSection
	)
	default boolean readRaidPoints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "coxUniqueChance",
		name = "CoX purple chance (%)",
		description = "Your chance of a unique per normal Chambers raid, in percent, for raids without points. Every 8,676 of your personal points is 1%, so 30,000 points is about 3.5%.",
		position = 12,
		section = raidsSection
	)
	default double coxUniqueChance()
	{
		return 3.5;
	}

	@ConfigItem(
		keyName = "coxCmUniqueChance",
		name = "CoX Challenge Mode (%)",
		description = "Your chance of a unique per Challenge Mode raid, in percent, for raids without points. Same formula as normal mode: every 8,676 of your personal points is 1%.",
		position = 13,
		section = raidsSection
	)
	default double coxCmUniqueChance()
	{
		return 3.5;
	}

	@ConfigItem(
		keyName = "tobUniqueChance",
		name = "ToB purple chance (%)",
		description = "Your chance of a unique per normal Theatre raid, in percent. A team gets one about 1 in 9.1 raids (11%); divide by your team size (4-man is about 2.7%).",
		position = 14,
		section = raidsSection
	)
	default double tobUniqueChance()
	{
		return 2.7;
	}

	@ConfigItem(
		keyName = "tobHmUniqueChance",
		name = "ToB Hard Mode (%)",
		description = "Your chance of a unique per Hard Mode raid, in percent. A team gets one about 1 in 7.7 raids (13%); divide by your team size (4-man is about 3.2%).",
		position = 15,
		section = raidsSection
	)
	default double tobHmUniqueChance()
	{
		return 3.2;
	}

	@ConfigItem(
		keyName = "toaEntryUniqueChance",
		name = "ToA Entry Mode (%)",
		description = "Your chance of a unique per Entry Mode raid without points, in percent. Very low: at low raid levels most rolled uniques are turned into other loot.",
		position = 16,
		section = raidsSection
	)
	default double toaEntryUniqueChance()
	{
		return 0.75;
	}

	@ConfigItem(
		keyName = "toaUniqueChance",
		name = "ToA Normal (%)",
		description = "Your chance of a unique per normal Tombs raid without points, in percent. It depends on raid level and your points; the OSRS Wiki's ToA page has a calculator.",
		position = 17,
		section = raidsSection
	)
	default double toaUniqueChance()
	{
		return 2.5;
	}

	@ConfigItem(
		keyName = "toaExpertUniqueChance",
		name = "ToA Expert Mode (%)",
		description = "Your chance of a unique per Expert Mode raid without points, in percent. It depends on raid level and your points; the OSRS Wiki's ToA page has a calculator.",
		position = 18,
		section = raidsSection
	)
	default double toaExpertUniqueChance()
	{
		return 5.0;
	}
}
