package com.raidtracker.ui.chambers;

import com.raidtracker.RaidTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Raid type and party size for the Times and Points &amp; Purples pages.
 * A raid with no party size is included only in All.
 */
public final class ChambersFilter {
	public static final String ALL = "All";
	public static final String REGULAR = "Regular";
	public static final String REGULAR_FULL = "Regular full";
	public static final String CM = "CM";
	public static final String SOLO = "Solo";
	public static final String TEAM = "Team";

	private ChambersFilter() {
	}

	public static List<RaidTracker> select(List<RaidTracker> raids, String mode, String size) {
		List<RaidTracker> out = new ArrayList<>();
		if (raids == null) {
			return out;
		}
		for (int i = 0; i < raids.size(); i++) {
			RaidTracker raid = raids.get(i);
			if (raid != null && matches(raid, mode, size)) {
				out.add(raid);
			}
		}
		return out;
	}

	public static boolean matches(RaidTracker raid, String mode, String size) {
		if (CM.equals(mode) && !raid.isChallengeMode()) {
			return false;
		}
		if (REGULAR.equals(mode) && (raid.isChallengeMode() || ChambersFacts.fullLayout(raid))) {
			return false;
		}
		if (REGULAR_FULL.equals(mode) && (raid.isChallengeMode() || !ChambersFacts.fullLayout(raid))) {
			return false;
		}
		int team = raid.getTeamSize();
		if (size == null || ALL.equals(size)) {
			return true;
		}
		if (SOLO.equals(size)) {
			return team == 1;
		}
		if (TEAM.equals(size)) {
			return team >= 2;
		}
		try {
			return team == Integer.parseInt(size);
		} catch (NumberFormatException ex) {
			return true;
		}
	}

	/** All, Solo, Team, then each party size of 2 or more that appears in the log. */
	public static List<String> sizeChoices(List<RaidTracker> raids) {
		List<String> choices = new ArrayList<>();
		choices.add(ALL);
		choices.add(SOLO);
		choices.add(TEAM);
		TreeSet<Integer> parties = new TreeSet<>();
		if (raids != null) {
			for (int i = 0; i < raids.size(); i++) {
				RaidTracker raid = raids.get(i);
				if (raid != null && raid.getTeamSize() >= 2) {
					parties.add(raid.getTeamSize());
				}
			}
		}
		for (Integer party : parties) {
			choices.add(Integer.toString(party));
		}
		return choices;
	}
}
