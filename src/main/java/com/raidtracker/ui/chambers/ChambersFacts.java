package com.raidtracker.ui.chambers;

import com.raidtracker.ChambersDeath;
import com.raidtracker.RaidTracker;
import com.raidtracker.UniqueDrop;
import com.raidtracker.ui.RaidUniques;
import java.util.ArrayList;
import java.util.List;

/**
 * Chambers numbers derived from a logged raid. A time that is not above 0 is absent.
 * Nothing here is written back to the log.
 */
public final class ChambersFacts {
	public static final int FULL_LAYOUT_ROOMS = 11;
	public static final double PURPLE_POINTS = 867600.0;

	public static final String[] PREP_ROOMS = {
		"Tekton",
		"Crabs",
		"Ice demon",
		"Shamans",
		"Vanguards",
		"Thieving",
		"Vespula",
		"Tightrope",
		"Guardians",
		"Vasa",
		"Mystics",
		"Muttadiles"
	};

	private static final String[] PURPLE_NAMES = {
		"Dexterous Prayer Scroll",
		"Arcane Prayer Scroll",
		"Twisted Buckler",
		"Dragon Hunter Crossbow",
		"Dinh's Bulwark",
		"Ancestral Hat",
		"Ancestral Robe Top",
		"Ancestral Robe Bottom",
		"Dragon Claws",
		"Elder Maul",
		"Kodai Insignia",
		"Twisted Bow"
	};

	private static final int[] REGULAR_WEIGHTS = {14, 14, 4, 4, 3, 4, 4, 4, 3, 2, 2, 2};
	private static final int[] CM_WEIGHTS = {12, 12, 4, 4, 3, 4, 4, 4, 3, 2, 2, 2};

	private ChambersFacts() {
	}

	public static int roomSeconds(RaidTracker raid, String room) {
		if (raid == null || room == null) {
			return -1;
		}
		switch (room) {
			case "Tekton":
				return raid.getTektonTime();
			case "Crabs":
				return raid.getCrabsTime();
			case "Ice demon":
				return raid.getIceDemonTime();
			case "Shamans":
				return raid.getShamansTime();
			case "Vanguards":
				return raid.getVanguardsTime();
			case "Thieving":
				return raid.getThievingTime();
			case "Vespula":
				return raid.getVespulaTime();
			case "Tightrope":
				return raid.getTightropeTime();
			case "Guardians":
				return raid.getGuardiansTime();
			case "Vasa":
				return raid.getVasaTime();
			case "Mystics":
				return raid.getMysticsTime();
			case "Muttadiles":
				return raid.getMuttadilesTime();
			default:
				return -1;
		}
	}

	public static int floorOf(String room) {
		if ("Tekton".equals(room) || "Crabs".equals(room) || "Ice demon".equals(room) || "Shamans".equals(room)) {
			return 1;
		}
		if ("Vanguards".equals(room) || "Thieving".equals(room) || "Vespula".equals(room) || "Tightrope".equals(room)) {
			return 2;
		}
		if ("Guardians".equals(room) || "Vasa".equals(room) || "Mystics".equals(room) || "Muttadiles".equals(room)) {
			return 3;
		}
		return 0;
	}

	public static int prepCount(RaidTracker raid) {
		int count = 0;
		for (int i = 0; i < PREP_ROOMS.length; i++) {
			if (roomSeconds(raid, PREP_ROOMS[i]) > 0) {
				count++;
			}
		}
		return count;
	}

	public static boolean fullLayout(RaidTracker raid) {
		return prepCount(raid) >= FULL_LAYOUT_ROOMS;
	}

	/** Sum of the prep rooms that have a time. */
	public static int preOlmSeconds(RaidTracker raid) {
		int sum = 0;
		for (int i = 0; i < PREP_ROOMS.length; i++) {
			int seconds = roomSeconds(raid, PREP_ROOMS[i]);
			if (seconds > 0) {
				sum += seconds;
			}
		}
		return sum;
	}

	/** Time from the lower floor to the raid finish. Absent when either clock is missing. */
	public static int olmSeconds(RaidTracker raid) {
		if (raid == null || raid.getRaidTime() <= 0 || raid.getLowerTime() <= 0) {
			return -1;
		}
		int olm = raid.getRaidTime() - raid.getLowerTime();
		return olm > 0 ? olm : -1;
	}

	/** Raid time that is not prep and not Olm. */
	public static int betweenSeconds(RaidTracker raid) {
		int prep = preOlmSeconds(raid);
		int olm = olmSeconds(raid);
		if (raid == null || raid.getRaidTime() <= 0 || prep <= 0 || olm <= 0) {
			return -1;
		}
		int between = raid.getRaidTime() - prep - olm;
		return between > 0 ? between : -1;
	}

	public static int mageSeconds(RaidTracker raid, int phase) {
		return listSeconds(raid == null ? null : raid.getOlmMageHandTimes(), phase);
	}

	public static int phaseSeconds(RaidTracker raid, int phase) {
		return listSeconds(raid == null ? null : raid.getOlmPhaseTimes(), phase);
	}

	public static int pointsPerHour(RaidTracker raid) {
		if (raid == null || raid.getPersonalPoints() <= 0 || raid.getRaidTime() <= 0) {
			return -1;
		}
		return (int) (raid.getPersonalPoints() / (raid.getRaidTime() / 3600.0));
	}

	public static int pointsLost(RaidTracker raid) {
		if (raid == null || raid.getDeaths() == null) {
			return 0;
		}
		int sum = 0;
		for (int i = 0; i < raid.getDeaths().size(); i++) {
			ChambersDeath death = raid.getDeaths().get(i);
			if (death != null && death.getPointsLost() > 0) {
				sum += death.getPointsLost();
			}
		}
		return sum;
	}

	public static boolean died(RaidTracker raid) {
		return raid != null && raid.getDeaths() != null && !raid.getDeaths().isEmpty();
	}

	public static String[] purpleNames() {
		return PURPLE_NAMES.clone();
	}

	public static int regularWeight(int index) {
		return REGULAR_WEIGHTS[index];
	}

	public static int cmWeight(int index) {
		return CM_WEIGHTS[index];
	}

	public static int regularWeightTotal() {
		return 60;
	}

	public static int cmWeightTotal() {
		return 56;
	}

	/** Purples received in your name, in log order. A missing price still counts. Kit, dust, and pet are not included. */
	public static List<String> ownPurples(RaidTracker raid) {
		List<String> names = new ArrayList<>();
		if (raid == null) {
			return names;
		}
		if (raid.getUniques() != null && !raid.getUniques().isEmpty()) {
			for (int i = 0; i < raid.getUniques().size(); i++) {
				UniqueDrop drop = raid.getUniques().get(i);
				if (drop != null && drop.isOwn()) {
					String name = canonicalPurple(drop.getItem());
					if (!name.isEmpty()) {
						names.add(name);
					}
				}
			}
			return names;
		}
		String legacy = canonicalPurple(raid.getSpecialLoot());
		if (!legacy.isEmpty() && raid.isSpecialLootInOwnName()) {
			names.add(legacy);
		}
		return names;
	}

	/** Your own pet, kit, or dust. A purple is not included. */
	public static List<String> ownSides(RaidTracker raid) {
		List<String> names = new ArrayList<>();
		if (raid == null) {
			return names;
		}
		if (raid.getUniques() != null && !raid.getUniques().isEmpty()) {
			for (int i = 0; i < raid.getUniques().size(); i++) {
				UniqueDrop drop = raid.getUniques().get(i);
				if (drop == null || !drop.isOwn()) {
					continue;
				}
				String side = sideName(drop.getItem());
				if (!side.isEmpty()) {
					names.add(side);
				}
			}
			return names;
		}
		if (raid.getKitReceiver() != null && !raid.getKitReceiver().isEmpty() && chestHas(raid, RaidUniques.TWISTED_KIT.getItemID())) {
			names.add("Twisted Kit");
		}
		if (raid.getDustReceiver() != null && !raid.getDustReceiver().isEmpty() && chestHas(raid, RaidUniques.DUST.getItemID())) {
			names.add("Metamorphic Dust");
		}
		if (raid.isPetInMyName()) {
			names.add("Olmlet");
		}
		return names;
	}

	public static String sideKind(String name) {
		if ("Olmlet".equals(name)) {
			return "pet";
		}
		if ("Twisted Kit".equals(name)) {
			return "kit";
		}
		if ("Metamorphic Dust".equals(name)) {
			return "dust";
		}
		return "";
	}

	public static boolean isScroll(String name) {
		return "Dexterous Prayer Scroll".equals(name) || "Arcane Prayer Scroll".equals(name);
	}

	/** Time fields only, so a live raid can be read off the client thread. */
	public static RaidTracker copyTimes(RaidTracker raid) {
		RaidTracker copy = new RaidTracker();
		if (raid == null) {
			return copy;
		}
		copy.setChallengeMode(raid.isChallengeMode());
		copy.setTeamSize(raid.getTeamSize());
		copy.setCompletionCount(raid.getCompletionCount());
		copy.setPersonalPoints(raid.getPersonalPoints());
		copy.setTotalPoints(raid.getTotalPoints());
		copy.setRaidTime(raid.getRaidTime());
		copy.setUpperTime(raid.getUpperTime());
		copy.setMiddleTime(raid.getMiddleTime());
		copy.setLowerTime(raid.getLowerTime());
		copy.setTektonTime(raid.getTektonTime());
		copy.setCrabsTime(raid.getCrabsTime());
		copy.setIceDemonTime(raid.getIceDemonTime());
		copy.setShamansTime(raid.getShamansTime());
		copy.setVanguardsTime(raid.getVanguardsTime());
		copy.setThievingTime(raid.getThievingTime());
		copy.setVespulaTime(raid.getVespulaTime());
		copy.setTightropeTime(raid.getTightropeTime());
		copy.setGuardiansTime(raid.getGuardiansTime());
		copy.setVasaTime(raid.getVasaTime());
		copy.setMysticsTime(raid.getMysticsTime());
		copy.setMuttadilesTime(raid.getMuttadilesTime());
		copy.setOlmHeadTime(raid.getOlmHeadTime());
		copy.setInRaidChambers(true);
		copy.setRaidComplete(raid.isRaidComplete());
		if (raid.getOlmMageHandTimes() != null) {
			copy.setOlmMageHandTimes(new ArrayList<>(raid.getOlmMageHandTimes()));
		}
		if (raid.getOlmPhaseTimes() != null) {
			copy.setOlmPhaseTimes(new ArrayList<>(raid.getOlmPhaseTimes()));
		}
		return copy;
	}

	public static String formatTime(int seconds) {
		if (seconds < 0) {
			return "";
		}
		return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
	}

	private static int listSeconds(List<Integer> times, int phase) {
		if (times == null || phase < 1 || phase > 8 || times.size() < phase) {
			return -1;
		}
		Integer value = times.get(phase - 1);
		return value == null ? -1 : value;
	}

	private static String canonicalPurple(String name) {
		if (name == null) {
			return "";
		}
		String trimmed = name.trim();
		for (int i = 0; i < PURPLE_NAMES.length; i++) {
			if (PURPLE_NAMES[i].equalsIgnoreCase(trimmed)) {
				return PURPLE_NAMES[i];
			}
		}
		return "";
	}

	private static String sideName(String name) {
		if (name == null) {
			return "";
		}
		String trimmed = name.trim();
		if ("Twisted Kit".equalsIgnoreCase(trimmed)) {
			return "Twisted Kit";
		}
		if ("Metamorphic Dust".equalsIgnoreCase(trimmed)) {
			return "Metamorphic Dust";
		}
		if ("Olmlet".equalsIgnoreCase(trimmed)) {
			return "Olmlet";
		}
		return "";
	}

	private static boolean chestHas(RaidTracker raid, int itemId) {
		if (raid.getLootList() == null) {
			return false;
		}
		for (int i = 0; i < raid.getLootList().size(); i++) {
			if (raid.getLootList().get(i) != null && raid.getLootList().get(i).getId() == itemId) {
				return true;
			}
		}
		return false;
	}
}
