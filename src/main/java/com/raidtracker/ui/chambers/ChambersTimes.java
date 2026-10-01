package com.raidtracker.ui.chambers;

import com.raidtracker.RaidTracker;
import java.util.ArrayList;
import java.util.List;

/**
 * Average and best Chambers rows, plus the pace line against your average.
 * Times under the room floors are left out of averages and bests.
 * Ice demon over 3:50 is left out unless ice milk is on, and then only through 4:30.
 */
public final class ChambersTimes {
	static final int MIN_SPLIT_SECONDS = 10;
	/** Ice demon at or under 3:50 is a normal kill. */
	static final int ICE_NORMAL_MAX = 230;
	/** Milking times longer than 4:30 stay out of the averages. */
	static final int ICE_MILK_MAX = 270;

	private ChambersTimes() {
	}

	public static final class Row {
		public final String label;
		public final String group;
		public final int floor;
		public final String value;
		public final String aside;
		public final Integer delta;
		public final boolean higher;
		public final boolean open;
		public final boolean gold;

		Row(String label, String group, int floor, String value, String aside, Integer delta, boolean higher, boolean open, boolean gold) {
			this.label = label;
			this.group = group;
			this.floor = floor;
			this.value = value;
			this.aside = aside == null ? "" : aside;
			this.delta = delta;
			this.higher = higher;
			this.open = open;
			this.gold = gold;
		}
	}

	public static final class Mark {
		public final String label;
		public final int aheadOfAverage;

		Mark(String label, int aheadOfAverage) {
			this.label = label;
			this.aheadOfAverage = aheadOfAverage;
		}
	}

	public static final class Table {
		public final String caption;
		public final List<Row> rows;
		public final List<Mark> pace;
		public final Integer personalBestGap;
		public final boolean graph;
		/** "average" or "target". The pace line names the zero line with this. */
		public final String paceAgainst;

		Table(String caption, List<Row> rows, List<Mark> pace, Integer personalBestGap, boolean graph) {
			this(caption, rows, pace, personalBestGap, graph, "average");
		}

		Table(String caption, List<Row> rows, List<Mark> pace, Integer personalBestGap, boolean graph, String paceAgainst) {
			this.caption = caption == null ? "" : caption;
			this.rows = rows == null ? new ArrayList<Row>() : rows;
			this.pace = pace == null ? new ArrayList<Mark>() : pace;
			this.personalBestGap = personalBestGap;
			this.graph = graph;
			this.paceAgainst = paceAgainst == null || paceAgainst.isEmpty() ? "average" : paceAgainst;
		}
	}

	public static Table average(List<RaidTracker> saved, RaidTracker live, int openSeconds) {
		return average(saved, live, openSeconds, false);
	}

	public static Table average(List<RaidTracker> saved, RaidTracker live, int openSeconds, boolean includeIceMilking) {
		List<RaidTracker> raids = saved == null ? new ArrayList<RaidTracker>() : saved;
		boolean showingLive = live != null && (openSeconds > 0 || hasSplit(live));
		if (!showingLive && raids.isEmpty()) {
			return new Table("No completed raids in this filter yet.", new ArrayList<Row>(), new ArrayList<Mark>(), null, true);
		}
		RaidTracker subject = showingLive ? live : raids.get(raids.size() - 1);
		List<String> keys = keysFor(raids, subject);
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			Integer shown = raw(subject, key);
			if (shown == null) {
				continue;
			}
			Double benchmark = averageOf(raids, key, includeIceMilking);
			rows.add(row(key, shown, benchmark, null, false));
		}
		if (showingLive && openSeconds > 0) {
			rows.add(new Row("Current", "Current", 0, ChambersFacts.formatTime(openSeconds), "", null, false, true, false));
		}
		if (rows.isEmpty()) {
			return new Table(showingLive ? "Waiting for the first room." : "No room times in this filter yet.", rows, new ArrayList<Mark>(), null, true);
		}
		List<RaidTracker> comparison = showingLive ? raids : withoutLast(raids);
		List<Mark> pace = pace(comparison, subject, !showingLive || subject.getRaidTime() > 0, includeIceMilking);
		Integer gap = personalBestGap(comparison);
		return new Table(caption(subject, showingLive), rows, pace, gap, true);
	}

	public static Table bestSplits(List<RaidTracker> saved) {
		return bestSplits(saved, false);
	}

	public static Table bestSplits(List<RaidTracker> saved, boolean includeIceMilking) {
		List<RaidTracker> raids = saved == null ? new ArrayList<RaidTracker>() : saved;
		if (raids.isEmpty()) {
			return new Table("No completed raids in this filter yet.", new ArrayList<Row>(), new ArrayList<Mark>(), null, false);
		}
		List<String> keys = keysFor(raids, null);
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			boolean higher = higher(key);
			RaidTracker bestRaid = null;
			int best = higher ? Integer.MIN_VALUE : Integer.MAX_VALUE;
			for (int r = 0; r < raids.size(); r++) {
				Integer value = valid(raids.get(r), key, includeIceMilking);
				if (value == null || (higher ? value <= best : value >= best)) {
					continue;
				}
				best = value;
				bestRaid = raids.get(r);
			}
			if (bestRaid == null) {
				continue;
			}
			rows.add(row(key, best, null, killCount(bestRaid), true));
		}
		if (rows.isEmpty()) {
			return new Table("No room times in this filter yet.", rows, new ArrayList<Mark>(), null, false);
		}
		return new Table("Fastest in this filter", rows, new ArrayList<Mark>(), null, false);
	}

	public static Table bestRaid(List<RaidTracker> saved) {
		return bestRaid(saved, false);
	}

	public static Table bestRaid(List<RaidTracker> saved, boolean includeIceMilking) {
		return showcase(pickFastest(saved), "No raid time in this filter yet.", includeIceMilking);
	}

	public static Table bestPointsPerHour(List<RaidTracker> saved) {
		return bestPointsPerHour(saved, false);
	}

	public static Table bestPointsPerHour(List<RaidTracker> saved, boolean includeIceMilking) {
		return showcase(pickHighestRate(saved), "No points per hour in this filter yet.", includeIceMilking);
	}

	private static Table showcase(RaidTracker raid, String empty, boolean includeIceMilking) {
		if (raid == null) {
			return new Table(empty, new ArrayList<Row>(), new ArrayList<Mark>(), null, false);
		}
		List<RaidTracker> one = new ArrayList<>();
		one.add(raid);
		List<String> keys = keysFor(one, null);
		List<Row> rows = new ArrayList<>();
		for (int i = 0; i < keys.size(); i++) {
			Integer value = valid(raid, keys.get(i), includeIceMilking);
			if (value == null) {
				continue;
			}
			rows.add(row(keys.get(i), value, null, null, true));
		}
		return new Table(killCount(raid), rows, new ArrayList<Mark>(), null, false);
	}

	static List<Mark> pace(List<RaidTracker> comparison, RaidTracker subject, boolean finished, boolean includeIceMilking) {
		List<Mark> marks = new ArrayList<>();
		if (comparison == null || comparison.isEmpty() || subject == null) {
			return marks;
		}
		Double averageTotal = averageOf(comparison, "raid", includeIceMilking);
		if (averageTotal == null) {
			return marks;
		}
		int roomBank = 0;
		int phaseBank = 0;
		boolean olmApplied = false;
		boolean plotted = false;
		List<String> keys = keysFor(comparison, subject);
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			if (key.startsWith("mage:") || "pre".equals(key) || "between".equals(key) || "points".equals(key) || "pph".equals(key)) {
				continue;
			}
			Integer actual = raw(subject, key);
			Double average = averageOf(comparison, key, includeIceMilking);
			if (actual == null || average == null) {
				continue;
			}
			int gain = (int) Math.round(average) - actual;
			if (isPrep(key)) {
				roomBank += gain;
				marks.add(new Mark(label(key), roomBank));
				plotted = true;
			} else if (isPhase(key)) {
				phaseBank += gain;
				if (!olmApplied) {
					marks.add(new Mark(label(key), roomBank + phaseBank));
					plotted = true;
				}
			} else if ("olm".equals(key)) {
				olmApplied = true;
				marks.add(new Mark("Olm", roomBank + gain));
				plotted = true;
			}
		}
		if (finished) {
			Integer total = raw(subject, "raid");
			if (total != null) {
				marks.add(new Mark("Finish", (int) Math.round(averageTotal) - total));
				plotted = true;
			}
		}
		if (!plotted) {
			return new ArrayList<>();
		}
		List<Mark> withStart = new ArrayList<>();
		withStart.add(new Mark("Start", 0));
		withStart.addAll(marks);
		return withStart;
	}

	static Integer personalBestGap(List<RaidTracker> comparison) {
		if (comparison == null || comparison.isEmpty()) {
			return null;
		}
		Double averageTotal = averageOf(comparison, "raid", false);
		Integer best = null;
		for (int i = 0; i < comparison.size(); i++) {
			Integer time = valid(comparison.get(i), "raid");
			if (time == null) {
				continue;
			}
			best = best == null ? time : Math.min(best, time);
		}
		if (averageTotal == null || best == null) {
			return null;
		}
		return (int) Math.round(averageTotal) - best;
	}

	private static List<RaidTracker> withoutLast(List<RaidTracker> raids) {
		if (raids.size() <= 1) {
			return new ArrayList<>();
		}
		return new ArrayList<>(raids.subList(0, raids.size() - 1));
	}

	private static boolean hasSplit(RaidTracker raid) {
		for (int i = 0; i < ChambersFacts.PREP_ROOMS.length; i++) {
			if (ChambersFacts.roomSeconds(raid, ChambersFacts.PREP_ROOMS[i]) > 0) {
				return true;
			}
		}
		return ChambersFacts.mageSeconds(raid, 1) > 0 || ChambersFacts.phaseSeconds(raid, 1) > 0 || raid.getOlmHeadTime() > 0;
	}

	private static Row row(String key, int value, Double benchmark, String aside, boolean gold) {
		boolean points = higher(key);
		Integer delta = null;
		String diff = "";
		if (benchmark != null) {
			delta = value - (int) Math.round(benchmark);
			diff = points ? pointDiff(delta) : timeDiff(delta);
		}
		String shown = points ? Integer.toString(value) : ChambersFacts.formatTime(value);
		return new Row(label(key), group(key), ChambersFacts.floorOf(key), shown, aside == null || aside.isEmpty() ? diff : aside, delta, points, false, gold);
	}

	private static String caption(RaidTracker raid, boolean live) {
		if (live && raid.getRaidTime() <= 0) {
			return "In progress";
		}
		String kc = killCount(raid);
		return kc.isEmpty() ? "" : kc;
	}

	private static String killCount(RaidTracker raid) {
		if (raid.getCompletionCount() <= 0) {
			return "";
		}
		return (raid.isChallengeMode() ? "CM " : "KC ") + raid.getCompletionCount();
	}

	private static String timeDiff(int delta) {
		if (delta == 0) {
			return "0:00";
		}
		return (delta > 0 ? "+" : "-") + ChambersFacts.formatTime(Math.abs(delta));
	}

	private static String pointDiff(int delta) {
		if (delta == 0) {
			return "0";
		}
		return (delta > 0 ? "+" : "") + delta;
	}

	private static Double averageOf(List<RaidTracker> raids, String key, boolean includeIceMilking) {
		long sum = 0;
		int count = 0;
		for (int i = 0; i < raids.size(); i++) {
			Integer value = valid(raids.get(i), key, includeIceMilking);
			if (value == null) {
				continue;
			}
			sum += value;
			count++;
		}
		if (count == 0) {
			return null;
		}
		return (double) sum / count;
	}

	private static RaidTracker pickFastest(List<RaidTracker> raids) {
		if (raids == null) {
			return null;
		}
		RaidTracker bestRaid = null;
		int best = Integer.MAX_VALUE;
		for (int i = 0; i < raids.size(); i++) {
			Integer time = valid(raids.get(i), "raid");
			if (time == null || time >= best) {
				continue;
			}
			best = time;
			bestRaid = raids.get(i);
		}
		return bestRaid;
	}

	private static RaidTracker pickHighestRate(List<RaidTracker> raids) {
		if (raids == null) {
			return null;
		}
		RaidTracker bestRaid = null;
		int best = Integer.MIN_VALUE;
		for (int i = 0; i < raids.size(); i++) {
			Integer rate = valid(raids.get(i), "pph");
			if (rate == null || rate <= best) {
				continue;
			}
			best = rate;
			bestRaid = raids.get(i);
		}
		return bestRaid;
	}

	static List<String> keysFor(List<RaidTracker> raids, RaidTracker extra) {
		List<RaidTracker> source = new ArrayList<>();
		if (raids != null) {
			source.addAll(raids);
		}
		if (extra != null) {
			source.add(extra);
		}
		List<String> keys = new ArrayList<>();
		for (int i = 0; i < ChambersFacts.PREP_ROOMS.length; i++) {
			if (present(source, ChambersFacts.PREP_ROOMS[i])) {
				keys.add(ChambersFacts.PREP_ROOMS[i]);
			}
		}
		if (present(source, "pre")) {
			keys.add("pre");
		}
		for (int phase = 1; phase <= 8; phase++) {
			String mage = "mage:" + phase;
			String full = "phase:" + phase;
			if (present(source, mage)) {
				keys.add(mage);
			}
			if (present(source, full)) {
				keys.add(full);
			}
		}
		if (present(source, "head")) {
			keys.add("head");
		}
		if (present(source, "olm")) {
			keys.add("olm");
		}
		if (present(source, "raid")) {
			keys.add("raid");
		}
		if (present(source, "between")) {
			keys.add("between");
		}
		if (present(source, "points")) {
			keys.add("points");
		}
		if (present(source, "pph")) {
			keys.add("pph");
		}
		return keys;
	}

	private static boolean present(List<RaidTracker> raids, String key) {
		for (int i = 0; i < raids.size(); i++) {
			if (raw(raids.get(i), key) != null) {
				return true;
			}
		}
		return false;
	}

	static Integer raw(RaidTracker raid, String key) {
		if (raid == null || key == null) {
			return null;
		}
		int seconds;
		if ("pre".equals(key)) {
			seconds = ChambersFacts.preOlmSeconds(raid);
		} else if ("olm".equals(key)) {
			seconds = ChambersFacts.olmSeconds(raid);
		} else if ("between".equals(key)) {
			seconds = ChambersFacts.betweenSeconds(raid);
		} else if ("raid".equals(key)) {
			seconds = raid.getRaidTime();
		} else if ("head".equals(key)) {
			seconds = raid.getOlmHeadTime();
		} else if ("points".equals(key)) {
			seconds = raid.getPersonalPoints();
		} else if ("pph".equals(key)) {
			seconds = ChambersFacts.pointsPerHour(raid);
		} else if (key.startsWith("mage:")) {
			seconds = ChambersFacts.mageSeconds(raid, phaseNumber(key));
		} else if (key.startsWith("phase:")) {
			seconds = ChambersFacts.phaseSeconds(raid, phaseNumber(key));
		} else {
			seconds = ChambersFacts.roomSeconds(raid, key);
		}
		return seconds > 0 ? seconds : null;
	}

	static Integer valid(RaidTracker raid, String key) {
		return valid(raid, key, false);
	}

	static Integer valid(RaidTracker raid, String key, boolean includeIceMilking) {
		Integer value = raw(raid, key);
		if (value == null || !acceptable(key, value, includeIceMilking)) {
			return null;
		}
		return value;
	}

	static boolean acceptable(String key, int seconds) {
		return acceptable(key, seconds, false);
	}

	static boolean acceptable(String key, int seconds, boolean includeIceMilking) {
		if ("points".equals(key) || "pph".equals(key)) {
			return seconds > 0;
		}
		if (seconds < MIN_SPLIT_SECONDS) {
			return false;
		}
		if ("Ice demon".equals(key)) {
			if (seconds < 80) {
				return false;
			}
			if (seconds <= ICE_NORMAL_MAX) {
				return true;
			}
			return includeIceMilking && seconds <= ICE_MILK_MAX;
		}
		Integer min = minimum(key);
		Integer max = maximum(key);
		if (min != null && seconds < min) {
			return false;
		}
		if (max != null && seconds > max) {
			return false;
		}
		return true;
	}

	private static Integer minimum(String key) {
		if ("Tekton".equals(key)) {
			return 20;
		}
		if ("Crabs".equals(key) || "Thieving".equals(key) || "Muttadiles".equals(key)) {
			return 35;
		}
		if ("Shamans".equals(key)) {
			return 17;
		}
		if ("Vanguards".equals(key)) {
			return 50;
		}
		if ("Vespula".equals(key)) {
			return 5;
		}
		if ("Tightrope".equals(key)) {
			return 15;
		}
		if ("Guardians".equals(key)) {
			return 25;
		}
		if ("Vasa".equals(key) || "Mystics".equals(key)) {
			return 20;
		}
		if ("between".equals(key)) {
			return 10;
		}
		return null;
	}

	private static Integer maximum(String key) {
		if (isPrep(key) || "between".equals(key)) {
			return 240;
		}
		return null;
	}

	private static boolean higher(String key) {
		return "points".equals(key) || "pph".equals(key);
	}

	private static boolean isPrep(String key) {
		return ChambersFacts.floorOf(key) > 0;
	}

	private static boolean isPhase(String key) {
		return "head".equals(key) || (key != null && key.startsWith("phase:"));
	}

	private static int phaseNumber(String key) {
		int colon = key.indexOf(':');
		if (colon < 0) {
			return -1;
		}
		try {
			return Integer.parseInt(key.substring(colon + 1));
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

	static String label(String key) {
		if (key.startsWith("mage:")) {
			return "Mage P" + key.substring("mage:".length());
		}
		if (key.startsWith("phase:")) {
			return "Olm P" + key.substring("phase:".length());
		}
		if ("head".equals(key)) {
			return "Olm head";
		}
		if ("pre".equals(key)) {
			return "Pre-Olm";
		}
		if ("olm".equals(key)) {
			return "Olm";
		}
		if ("between".equals(key)) {
			return "Between rooms";
		}
		if ("raid".equals(key)) {
			return "Raid time";
		}
		if ("points".equals(key)) {
			return "Total points";
		}
		if ("pph".equals(key)) {
			return "PPH";
		}
		return key;
	}

	static String group(String key) {
		if ("raid".equals(key) || "between".equals(key) || "points".equals(key) || "pph".equals(key)) {
			return "Finish";
		}
		if ("olm".equals(key) || "head".equals(key) || key.startsWith("mage:") || key.startsWith("phase:")) {
			return "Olm";
		}
		return "Rooms";
	}
}
