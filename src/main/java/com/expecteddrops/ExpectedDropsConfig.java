package com.expecteddrops;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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

	@ConfigSection(
		name = "Raids",
		description = "Raid unique chances depend on your points and team, so set them here",
		position = 10
	)
	String raidsSection = "raids";

	@ConfigItem(
		keyName = "coxUniqueChance",
		name = "CoX purple chance (%)",
		description = "Your chance of a unique per Chambers raid, in percent. Every 8,676 of your personal points is 1%, so 30,000 points is about 3.5%.",
		position = 11,
		section = raidsSection
	)
	default double coxUniqueChance()
	{
		return 3.5;
	}

	@ConfigItem(
		keyName = "tobUniqueChance",
		name = "ToB purple chance (%)",
		description = "Your chance of a unique per Theatre raid, in percent. A team gets one about 1 in 9.1 raids; divide 11% by your team size (4-man is about 2.7%).",
		position = 12,
		section = raidsSection
	)
	default double tobUniqueChance()
	{
		return 2.7;
	}

	@ConfigItem(
		keyName = "toaUniqueChance",
		name = "ToA purple chance (%)",
		description = "Your chance of a unique per Tombs raid, in percent. It depends on raid level and your points; the OSRS Wiki's ToA page has a calculator.",
		position = 13,
		section = raidsSection
	)
	default double toaUniqueChance()
	{
		return 5.0;
	}
}
