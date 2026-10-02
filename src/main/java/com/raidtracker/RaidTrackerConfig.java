package com.raidtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(RaidTrackerConfig.CONFIG_GROUP)
public interface RaidTrackerConfig extends Config
{
	String CONFIG_GROUP = "raidtracker";

	@ConfigSection(
		name = "General",
		description = "Loot splits and which panels are shown.",
		position = 0,
		closedByDefault = false
	)
	String generalSection = "generalSection";

	@ConfigItem(
		keyName = "defaultFFA",
		name = "default FFA",
		description = "Sets the default split to free for all, rather than split",
		section = "generalSection"
	)
	default boolean defaultFFA()
	{
		return false;
	}

	@ConfigItem(
		keyName = "FFACutoff",
		name = "FFA cut off",
		description = "The value of which, when the split reaches under that value, is considered free for all",
		section = "generalSection"
	)
	default int FFACutoff()
	{
		return 1000000;
	}

	@ConfigItem(
		keyName = "lastXKills",
		name = "Last X Kills",
		description = "When the 'Last X Kills' option is selected, this value is used as X",
		section = "generalSection"
	)
	default int lastXKills()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "showTitle",
		name = "Show Title",
		description = "Disable this checkmark to hide the title in the ui",
		section = "generalSection"
	)
	default boolean showTitle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showKillsLogged",
		name = "Show Kills Logged",
		description = "Disable this checkmark to hide the Kills Logged panel in the ui",
		section = "generalSection"
	)
	default boolean showKillsLogged()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFilters",
		name = "Show Filters",
		description = "Disable this checkmark to hide the Filter Panel in the ui",
		section = "generalSection"
	)
	default boolean showFilters()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showUniquesTable",
		name = "Show Uniques Table",
		description = "Disable this checkmark to hide the Uniques Table in the ui",
		section = "generalSection"
	)
	default boolean showUniquesTable()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPoints",
		name = "Show Points",
		description = "Disable this checkmark to hide the Points Panel in the ui",
		section = "generalSection"
	)
	default boolean showPoints()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTimeSplits",
		name = "Show Time Splits",
		description = "Disable this checkmark to hide the Time Splits Panel in the ui",
		section = "generalSection"
	)
	default boolean showTimeSplits()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSplitGPEarned",
		name = "Show Split GP Earned",
		description = "Disable this checkmark to hide the Split GP Earned Panel in the ui",
		section = "generalSection"
	)
	default boolean showSplitGPEarned()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMVPs",
		name = "Show MVPs",
		description = "Disable this checkmark to hide the MVPs Panel in the ui",
		section = "generalSection"
	)
	default boolean showMVPs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showRegularDrops",
		name = "Show Regular Drops",
		description = "Disable this checkmark to hide the Regular Drops Panel in the ui",
		section = "generalSection"
	)
	default boolean showRegularDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSplitChanger",
		name = "Show Split Changer",
		description = "Disable this checkmark to hide the Split Changer in the ui",
		section = "generalSection"
	)
	default boolean showSplitChanger()
	{
		return true;
	}

	@ConfigItem(
		keyName = "toaFilterCustomLow",
		name = "toaFilterCustomLow",
		description = "toaFilterCustomLow",
		hidden = true
	)
	default int toaFilterCustomLow()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "toaFilterCustomHigh",
		name = "toaFilterCustomHigh",
		description = "toaFilterCustomHigh",
		hidden = true
	)
	default int toaFilterCustomHigh()
	{
		return 600;
	}

	@ConfigSection(
		name = "Other",
		description = "Settings that are not part of a raid target.",
		position = 60,
		closedByDefault = true
	)
	String otherSection = "otherSection";

	@ConfigItem(
		keyName = "trackVanguards",
		name = "Track Vanguards",
		description = "Logs each Challenge Mode Vanguard room to a vanguards file for later study. The raid log is unchanged. Nothing is drawn in game.",
		section = "otherSection",
		position = 0
	)
	default boolean trackVanguards()
	{
		return false;
	}

	@ConfigSection(
		name = "Items",
		description = "Untick an item you are not bringing. That adds time to the room.",
		position = 20,
		closedByDefault = false
	)
	String itemSection = "itemSection";

	@ConfigSection(
		name = "Strategies",
		description = "Untick a method you are not using. That adds time to the room.",
		position = 21,
		closedByDefault = false
	)
	String strategySection = "strategySection";

	@ConfigSection(
		name = "Money run",
		description = "Tick a slower method. That adds time to the room.",
		position = 22,
		closedByDefault = false
	)
	String moneyRunSection = "moneyRunSection";

	@ConfigItem(
		keyName = "useTbow",
		name = "Using Twisted bow",
		description = "Off adds 6 seconds to Shamans, 2 seconds to Vanguards, 8 seconds to Tightrope when killing rope is on, 30 seconds to Vasa, 10 seconds to Muttadiles, and 20 seconds to Olm head.",
		section = "itemSection",
		position = 0
	)
	default boolean useTbow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "slayerHelm",
		name = "Slayer helm",
		description = "Off adds 9 seconds to Shamans.",
		section = "itemSection",
		position = 1
	)
	default boolean slayerHelm()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lockpick",
		name = "Lockpick",
		description = "Off adds 4 seconds to Thieving.",
		section = "itemSection",
		position = 2
	)
	default boolean lockpick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "axe",
		name = "Axe",
		description = "Off adds 5 seconds to Ice demon.",
		section = "itemSection",
		position = 3
	)
	default boolean axe()
	{
		return true;
	}

	@ConfigItem(
		keyName = "salve",
		name = "Salve",
		description = "Off adds 5 seconds to Mystics.",
		section = "itemSection",
		position = 4
	)
	default boolean salve()
	{
		return true;
	}

	@ConfigItem(
		keyName = "preVeng",
		name = "Pre-veng",
		description = "Off adds 4 seconds to Tekton.",
		section = "strategySection",
		position = 0
	)
	default boolean preVeng()
	{
		return true;
	}

	@ConfigItem(
		keyName = "vespPotSkip",
		name = "Vesp pot skip",
		description = "Off adds 6 seconds to Tightrope.",
		section = "strategySection",
		position = 1
	)
	default boolean vespPotSkip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "crabTank",
		name = "Crab tank",
		description = "Off adds 4 seconds to Crabs.",
		section = "strategySection",
		position = 2
	)
	default boolean crabTank()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sippingPools",
		name = "Sipping pools",
		description = "On adds 12 seconds to Between rooms.",
		section = "strategySection",
		position = 3
	)
	default boolean sippingPools()
	{
		return false;
	}

	@ConfigItem(
		keyName = "bankAfterRope",
		name = "Banking after rope",
		description = "On adds 5 seconds to Between rooms.",
		section = "strategySection",
		position = 4
	)
	default boolean bankAfterRope()
	{
		return false;
	}

	@ConfigItem(
		keyName = "killRope",
		name = "Killing rope",
		description = "On adds 59 seconds to Tightrope.",
		section = "moneyRunSection",
		position = 0
	)
	default boolean killRope()
	{
		return false;
	}

	@ConfigItem(
		keyName = "milkVespula",
		name = "Vesp milk",
		description = "On adds 18 seconds to Vespula.",
		section = "moneyRunSection",
		position = 1
	)
	default boolean milkVespula()
	{
		return false;
	}

	@ConfigItem(
		keyName = "iceMilking",
		name = "Ice milk",
		description = "On adds the ice milk seconds to Ice demon, and keeps Ice demon splits over 3:50, up to 4:30, in the averages.",
		section = "moneyRunSection",
		position = 2
	)
	default boolean iceMilking()
	{
		return false;
	}

	@Range(min = 0, max = 600)
	@ConfigItem(
		keyName = "iceMilkSeconds",
		name = "Ice milk seconds",
		description = "Seconds added to Ice demon when ice milk is on.",
		section = "moneyRunSection",
		position = 3
	)
	default int iceMilkSeconds()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "overThieve",
		name = "Over-thieve",
		description = "On adds 4 seconds to Thieving.",
		section = "moneyRunSection",
		position = 4
	)
	default boolean overThieve()
	{
		return false;
	}


	@ConfigSection(
		name = "Target times",
		description = "Times used for every Chambers filter. Times are MM:SS. Points and PPH are whole numbers. Blank means no target.",
		position = 40,
		closedByDefault = true
	)
	String cmSolo = "cmSolo";

	@ConfigItem(keyName = "cmSoloTotal", name = "Target total", description = "Pre-Olm plus Olm plus between rooms, including strategy adds. Calculated from the rooms below. This box is filled in and cannot be edited.", section = "cmSolo", position = 1)
	default String cmSoloTotal() { return "25:30"; }

	@ConfigItem(keyName = "cmTekton", name = "Tekton", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 2)
	default String cmTekton() { return "1:10"; }

	@ConfigItem(keyName = "cmCrabs", name = "Crabs", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 3)
	default String cmCrabs() { return "0:56"; }

	@ConfigItem(keyName = "cmIceDemon", name = "Ice demon", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 4)
	default String cmIceDemon() { return "2:24"; }

	@ConfigItem(keyName = "cmShamans", name = "Shamans", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 5)
	default String cmShamans() { return "1:03"; }

	@ConfigItem(keyName = "cmVanguards", name = "Vanguards", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 6)
	default String cmVanguards() { return "2:12"; }

	@ConfigItem(keyName = "cmThieving", name = "Thieving", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 7)
	default String cmThieving() { return "1:15"; }

	@ConfigItem(keyName = "cmVespula", name = "Vespula", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 8)
	default String cmVespula() { return "0:57"; }

	@ConfigItem(keyName = "cmTightrope", name = "Tightrope", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 9)
	default String cmTightrope() { return "0:47"; }

	@ConfigItem(keyName = "cmGuardians", name = "Guardians", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 10)
	default String cmGuardians() { return "1:47"; }

	@ConfigItem(keyName = "cmVasa", name = "Vasa", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 11)
	default String cmVasa() { return "1:10"; }

	@ConfigItem(keyName = "cmMystics", name = "Mystics", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 12)
	default String cmMystics() { return "1:40"; }

	@ConfigItem(keyName = "cmMuttadiles", name = "Muttadiles", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 13)
	default String cmMuttadiles() { return "1:25"; }

	@ConfigItem(keyName = "cmOlmMage", name = "Olm mage hand", description = "MM:SS. Used for every mage hand. The last phase has none. Blank means no target.", section = "cmSolo", position = 14)
	default String cmOlmMage() { return "0:56"; }

	@ConfigItem(keyName = "cmOlmPhase", name = "Olm phase", description = "MM:SS. Used for every Olm phase. Blank means no target.", section = "cmSolo", position = 16)
	default String cmOlmPhase() { return "1:54"; }

	@ConfigItem(keyName = "cmOlmHead", name = "Olm head", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 20)
	default String cmOlmHead() { return "1:06"; }

	@ConfigItem(keyName = "cmBetweenRooms", name = "Between room time", description = "MM:SS. Blank means no target.", section = "cmSolo", position = 21)
	default String cmBetweenRooms() { return "0:56"; }

	@ConfigItem(keyName = "cmTotalPoints", name = "Total Points", description = "Whole number. Blank means no target.", section = "cmSolo", position = 24)
	default String cmTotalPoints() { return "63750"; }

	@ConfigItem(keyName = "cmPph", name = "PPH", description = "Whole number. Blank means no target.", section = "cmSolo", position = 25)
	default String cmPph() { return "130000"; }
}
