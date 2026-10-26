package com.expecteddrops;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import com.expecteddrops.ui.ItemView;
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

	@ConfigSection(
		name = "Chat messages",
		description = "What On Rate says in your chat box",
		position = 3
	)
	String chatSection = "chat";

	@ConfigItem(
		keyName = "syncReminders",
		name = "Sync reminders",
		description = "After killing a boss whose history isn't loaded yet, say in chat which collection log page to open (once per session)",
		position = 4,
		section = chatSection
	)
	default boolean syncReminders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "dropMessages",
		name = "Drop messages",
		description = "When a unique drops, say in chat how many kills it took and how that compares with the drop rate",
		position = 5,
		section = chatSection
	)
	default boolean dropMessages()
	{
		return true;
	}

	@ConfigItem(
		keyName = "milestoneMessages",
		name = "Dry milestone messages",
		description = "Say in chat when an item passes 2x, 3x, 5x or 10x its drop rate without dropping",
		position = 6,
		section = chatSection
	)
	default boolean milestoneMessages()
	{
		return true;
	}

	@ConfigSection(
		name = "Display",
		description = "How the panel looks, and extras you can turn on",
		position = 6
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "itemView",
		name = "Luck view",
		description = "How your luck per item (and per boss in the account overview) is shown. Changed with the "
			+ "switch next to ITEMS in the panel, so it's not listed here",
		position = 7,
		section = displaySection,
		hidden = true
	)
	default ItemView itemView()
	{
		return ItemView.TARGET_BARS;
	}

	@ConfigItem(
		keyName = "showGeValue",
		name = "GE value",
		description = "Show the GP value tile: the GE value of your uniques, got vs expected, on boss pages and the account overview",
		position = 8,
		section = displaySection
	)
	default boolean showGeValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTopItems",
		name = "Items tab",
		description = "Show an Items tab on the account overview: up to 15 of your driest and 15 of your luckiest items across every boss, and your pets",
		position = 9,
		section = displaySection
	)
	default boolean showTopItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLuckGraph",
		name = "Luck graph",
		description = "Whether the Luck over time section in the account overview is open. Opened and closed "
			+ "by clicking its title in the panel, so it's not listed here",
		position = 10,
		section = displaySection,
		hidden = true
	)
	default boolean showLuckGraph()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showTripCard",
		name = "Trip card",
		description = "Show kills, uniques and expected for your current trip on the boss page",
		position = 11,
		section = displaySection
	)
	default boolean showTrip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "In-game overlay",
		description = "Show a small box on the game screen with your verdict, driest item and trip while you kill a boss",
		position = 13,
		section = displaySection
	)
	default boolean showOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "colourBlind",
		name = "Colour-blind colours",
		description = "Use orange to blue instead of red to green for dry to spooned",
		position = 14,
		section = displaySection
	)
	default boolean colourBlind()
	{
		return false;
	}

	@ConfigSection(
		name = "Team bosses",
		description = "At team bosses your chance of a unique scales with your share of the damage",
		position = 20
	)
	String teamSection = "team";

	@ConfigItem(
		keyName = "detectTeamSize",
		name = "Count your team",
		description = "At Nex, Yama, the Hueycoatl, the Corporeal Beast and Zalcano, count the players around you at each kill "
			+ "and give you that share of the kill (a quarter with four players). Earlier kills use the team of "
			+ "the first kill On Rate counted (Yama: as set below).",
		position = 50,
		section = teamSection
	)
	default boolean detectTeamSize()
	{
		return true;
	}

	@ConfigItem(
		keyName = "royalTitansTeam",
		name = "Royal Titans: how you kill them",
		description = "How you kill the Royal Titans. Counted as duo kills unless you change it. In a duo you get about half the uniques of a solo kill; the pet chance stays the same.",
		position = 51,
		section = teamSection
	)
	default DuoMode royalTitansMode()
	{
		return DuoMode.DUO;
	}

	@ConfigItem(
		keyName = "royalTitansSoloKills",
		name = "Royal Titans: solo kills",
		description = "Only used with 'Some of each': how many of your Royal Titans kills were solo. The rest count as duo.",
		position = 52,
		section = teamSection
	)
	@Range(min = 0)
	default int royalTitansSoloKills()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "yamaTeam",
		name = "Yama: earlier kills",
		description = "How you killed Yama before On Rate could count your team (it counts it from now on). "
			+ "Counted as duo kills unless you change it. In a duo you get about half the uniques of a solo kill; the pet chance stays the same.",
		position = 53,
		section = teamSection
	)
	default DuoMode yamaMode()
	{
		return DuoMode.DUO;
	}

	@ConfigItem(
		keyName = "yamaSoloKills",
		name = "Yama: solo kills",
		description = "Only used with 'Some of each': how many of those earlier Yama kills were solo. The rest count as duo.",
		position = 54,
		section = teamSection
	)
	@Range(min = 0)
	default int yamaSoloKills()
	{
		return 0;
	}

	@ConfigSection(
		name = "Raids",
		description = "How On Rate works out each raid's purple chance",
		position = 30
	)
	String raidsSection = "raids";

	@ConfigSection(
		name = "Raid purple chances",
		description = "Only used for raids where On Rate has no points: everything from before you installed it, "
			+ "and Theatre of Blood. Most players can leave these alone.",
		position = 40,
		closedByDefault = true
	)
	String raidChancesSection = "raidChances";

	@ConfigItem(
		keyName = "readRaidPoints",
		name = "Use raid points",
		description = "Work out each raid's purple chance as it ends: from your points in Chambers of Xeric, from the game's points in Tombs of Amascut (or an estimate), and from the end-of-raid team size in Theatre of Blood. Older raids use the percentages below.",
		position = 31,
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
		position = 32,
		section = raidChancesSection
	)
	default double coxUniqueChance()
	{
		return 3.5;
	}

	@ConfigItem(
		keyName = "coxCmPurpleChance",
		name = "CoX Challenge Mode (%)",
		description = "Your chance of a unique per Challenge Mode raid, in percent, for raids without points. Same formula as normal mode (every 8,676 of your personal points is 1%); a Challenge Mode raid is usually 6.3 to 6.9%.",
		position = 33,
		section = raidChancesSection
	)
	default double coxCmUniqueChance()
	{
		return 6.6;
	}

	@ConfigItem(
		keyName = "tobTeamChance",
		name = "ToB team purple chance (%)",
		description = "The team's chance of a unique per normal Theatre raid, in percent: about 1 in 9.1 raids (11%) with no deaths. On Rate divides it by the team size. Entry Mode has no uniques.",
		position = 34,
		section = raidChancesSection
	)
	default double tobTeamChance()
	{
		return 11.0;
	}

	@ConfigItem(
		keyName = "tobHmTeamChance",
		name = "ToB Hard Mode team (%)",
		description = "The team's chance of a unique per Hard Mode raid, in percent: about 1 in 7.7 raids (13%). On Rate divides it by the team size.",
		position = 35,
		section = raidChancesSection
	)
	default double tobHmTeamChance()
	{
		return 13.0;
	}

	@ConfigItem(
		keyName = "tobTeamSize",
		name = "ToB team size",
		description = "How many players you usually raid Theatre of Blood with. Your share of the team's purples is 1 / team size. Raids where On Rate read the end-of-raid board use that raid's real team size.",
		position = 36,
		section = raidsSection
	)
	@Range(min = 1, max = 5)
	default int tobTeamSize()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "toaEntryUniqueChance",
		name = "ToA Entry Mode (%)",
		description = "Your chance of a unique per Entry Mode raid without points, in percent. Very low: at low raid levels most rolled uniques are turned into other loot.",
		position = 37,
		section = raidChancesSection
	)
	default double toaEntryUniqueChance()
	{
		return 0.75;
	}

	@ConfigItem(
		keyName = "toaNormalUniqueChance",
		name = "ToA Normal (%)",
		description = "Your chance of a unique per normal Tombs raid without points, in percent. It depends on raid level and your points; the OSRS Wiki's ToA page has a calculator.",
		position = 38,
		section = raidChancesSection
	)
	default double toaUniqueChance()
	{
		return 2.5;
	}

	@ConfigItem(
		keyName = "toaExpertUniqueChance",
		name = "ToA Expert Mode (%)",
		description = "Your chance of a unique per Expert Mode raid without points, in percent. It depends on raid level and your points; the OSRS Wiki's ToA page has a calculator.",
		position = 39,
		section = raidChancesSection
	)
	default double toaExpertUniqueChance()
	{
		return 5.0;
	}
}
