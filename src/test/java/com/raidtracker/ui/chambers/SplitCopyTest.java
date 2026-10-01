package com.raidtracker.ui.chambers;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class SplitCopyTest {
	@Test
	public void discordPasteDropsDiffsAndGroupsTheList() {
		String text = ChambersTimesView.discordSplits(Arrays.asList(
			row("Tekton", "Rooms", "01:08", "-0:02", -2, false, false),
			row("Pre-Olm", "Rooms", "16:40", "+0:10", 10, false, false),
			row("Mage P1", "Olm", "00:56", "0:00", 0, false, false),
			row("Olm P1", "Olm", "01:53", "-0:04", -4, false, false),
			row("Olm", "Olm", "08:13", "-0:20", -20, false, false),
			row("Raid time", "Finish", "28:34", "-1:00", -60, false, false),
			row("Between rooms", "Finish", "00:56", "--", null, false, false),
			row("Total points", "Finish", "64226", "+120", 120, true, false),
			row("PPH", "Finish", "135000", "+500", 500, true, false)
		), "CM 221");

		Assert.assertEquals(
			"```\n"
				+ "CM 221\n"
				+ "Tekton           1:08\n"
				+ "Pre-Olm         16:40\n"
				+ "\n"
				+ "Mage P1          0:56\n"
				+ "Olm P1           1:53\n"
				+ "Olm              8:13\n"
				+ "\n"
				+ "Raid time       28:34\n"
				+ "Between rooms    0:56\n"
				+ "Total points    64226\n"
				+ "PPH            135000\n"
				+ "```",
			text
		);
	}

	@Test
	public void bestSplitsKeepEachKillCount() {
		String text = ChambersTimesView.discordSplits(Arrays.asList(
			row("Tekton", "Rooms", "01:08", "CM 214", null, false, false),
			row("Crabs", "Rooms", "00:52", "KC 90", null, false, false)
		), "Splits");

		Assert.assertEquals(
			"```\n"
				+ "Splits\n"
				+ "Tekton  1:08  CM 214\n"
				+ "Crabs   0:52  KC 90\n"
				+ "```",
			text
		);
	}

	@Test
	public void openSplitIsCopiedWithoutAComparison() {
		String text = ChambersTimesView.discordSplits(Arrays.asList(
			row("Tekton", "Rooms", "01:08", "-0:02", -2, false, false),
			row("Current", "Current", "04:12", "", null, false, true)
		), "In progress");

		Assert.assertEquals(
			"```\n"
				+ "In progress\n"
				+ "Tekton   1:08\n"
				+ "Current  4:12\n"
				+ "```",
			text
		);
	}

	@Test
	public void emptyListCopiesNothing() {
		Assert.assertEquals("", ChambersTimesView.discordSplits(Collections.<ChambersTimes.Row>emptyList(), ""));
		Assert.assertEquals("", ChambersTimesView.discordSplits(null, "CM 1"));
	}

	@Test
	public void copyTitleUsesTheBadgeThenTheBestChoice() {
		Assert.assertEquals("Last 10", ChambersTimesView.copyTitle("Target", "Splits", "Last", 10, "CM 4"));
		Assert.assertEquals("CM 221", ChambersTimesView.copyTitle("Average", null, "This", 10, "CM 221"));
		Assert.assertEquals("In progress", ChambersTimesView.copyTitle("Target", null, "This", 10, "In progress"));
		Assert.assertEquals("Splits", ChambersTimesView.copyTitle("Best", "Splits", "This", 10, "Fastest in this filter"));
		Assert.assertEquals("Raid time", ChambersTimesView.copyTitle("Best", "Raid time", "This", 10, ""));
		Assert.assertEquals("PPH", ChambersTimesView.copyTitle("Best", "PPH", "This", 10, ""));
		Assert.assertEquals("KC 12", ChambersTimesView.copyTitle("Best", "Raid time", "This", 10, "KC 12"));
		Assert.assertEquals("", ChambersTimesView.copyTitle("Average", null, "This", 10, ""));
	}

	private static ChambersTimes.Row row(String label, String group, String value, String aside, Integer delta, boolean higher, boolean open) {
		return new ChambersTimes.Row(label, group, 0, value, aside, delta, higher, open, false);
	}
}
