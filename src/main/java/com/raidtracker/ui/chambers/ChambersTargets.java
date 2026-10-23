package com.raidtracker.ui.chambers;

import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chambers times against the one target sheet. Every filter uses it.
 * Item and strategy boxes change the comparison. The typed boxes stay as saved.
 * The pace graph draws the personal best the same way Average does.
 * That line is the target raid time minus the fastest raid time in the comparison.
 */
public final class ChambersTargets {
	private static final int SOLO_PHASES = 3;
	private static final int MAX_PHASES = 15;

	private ChambersTargets() {
	}

	public static ChambersTimes.Table compare(
		List<RaidTracker> saved,
		RaidTracker live,
		int openSeconds,
		RaidTrackerConfig config,
		String mode,
		String size,
		boolean lastN,
		int count
	) {
		List<RaidTracker> raids = saved == null ? new ArrayList<RaidTracker>() : saved;
		if (lastN) {
			return lastN(raids, config, mode, size, count);
		}
		return thisRaid(raids, live, openSeconds, config, mode, size);
	}

	/**
	 * Every raid in the list, averaged, against the one target sheet.
	 * The time is that average. The diff is how far it sits from the target. Positive is slower.
	 */
	public static ChambersTimes.Table averageVersusTarget(List<RaidTracker> saved, RaidTrackerConfig config) {
		List<RaidTracker> raids = saved == null ? new ArrayList<RaidTracker>() : saved;
		if (raids.isEmpty()) {
			return emptyTarget("No completed raids in this filter yet.");
		}
		return againstSheet(raids, config);
	}

	/** The gray settings total. It always uses a 3-phase Olm. */
	public static String settingsTotal(RaidTrackerConfig config) {
		Integer raid = targets(config, null).get("raid");
		if (raid == null || raid <= 0) {
			return "";
		}
		return ChambersFacts.formatTime(raid);
	}

	/** Styled total, then the plain total and the added time when a toggle adds some. */
	public static String settingsTotalLine(RaidTrackerConfig config) {
		String styled = settingsTotal(config);
		int extra = addSeconds(config, "Target total");
		if (styled.isEmpty() || extra <= 0) {
			return styled;
		}
		Integer base = raidSeconds(config, false);
		if (base == null || base <= 0) {
			return styled;
		}
		return styled + " (" + ChambersFacts.formatTime(base) + " + " + ChambersFacts.formatTime(extra) + ")";
	}

	/** Saved points plus the money-run bonus. The parentheses are the saved total and that bonus. */
	public static String settingsPointsLine(RaidTrackerConfig config) {
		if (config == null) {
			return "";
		}
		Integer base = typed(config).get("points");
		if (base == null || base <= 0) {
			return "";
		}
		int extra = pointBonus(config);
		if (extra <= 0) {
			return Integer.toString(base);
		}
		return (base + extra) + " (" + base + " + " + extra + ")";
	}

	/** Points divided by the 3-phase target raid time. Blank when either piece is missing. */
	public static String settingsPph(RaidTrackerConfig config) {
		Integer pph = targets(config, null).get("pph");
		if (pph == null || pph <= 0) {
			return "";
		}
		return Integer.toString(pph);
	}

	/**
	 * Extra seconds a toggle adds on top of a saved room, or on top of the plain target total.
	 * Zero when that row has no target, or the toggles add nothing.
	 */
	public static int addSeconds(RaidTrackerConfig config, String label) {
		if (config == null || label == null) {
			return 0;
		}
		if ("Target total".equals(label)) {
			return totalAddSeconds(config);
		}
		String key = sheetKey(label);
		if (key == null) {
			return 0;
		}
		Map<String, Integer> styled = typed(config);
		Integer typed = styled.get(key);
		if (typed == null || typed <= 0) {
			return 0;
		}
		applyStyle(styled, config);
		Integer next = styled.get(key);
		if (next == null || next <= typed) {
			return 0;
		}
		return next - typed;
	}

	/**
	 * Extra time a toggle adds on top of the saved room box.
	 * Blank when the room has no target, or the toggles add nothing.
	 * The box itself stays the saved time.
	 */
	public static String addNote(RaidTrackerConfig config, String label) {
		int extra = addSeconds(config, label);
		if (extra <= 0) {
			return "";
		}
		return "+ " + ChambersFacts.formatTime(extra);
	}

	/** Extra points a money-run box adds on top of the saved total. Zero when that box is blank or off. */
	public static int addPoints(RaidTrackerConfig config, String label) {
		if (config == null || !"Total Points".equals(label)) {
			return 0;
		}
		Integer typed = typed(config).get("points");
		if (typed == null || typed <= 0) {
			return 0;
		}
		return pointBonus(config);
	}

	private static int pointBonus(RaidTrackerConfig config) {
		int extra = 0;
		if (config.milkVespula()) {
			extra += 1000;
		}
		if (config.killRope()) {
			extra += 3248;
		}
		if (config.overThieve()) {
			extra += 718;
		}
		return extra;
	}

	private static int totalAddSeconds(RaidTrackerConfig config) {
		Integer base = raidSeconds(config, false);
		Integer styled = raidSeconds(config, true);
		if (base == null || styled == null || styled <= base) {
			return 0;
		}
		return styled - base;
	}

	private static Integer raidSeconds(RaidTrackerConfig config, boolean styled) {
		Map<String, Integer> sheet = typed(config);
		if (styled) {
			applyStyle(sheet, config);
		}
		derivePreOlm(sheet);
		return expandOlm(sheet, null).get("raid");
	}

	/** The text a target box should show. Target total is calculated. */
	public static String shown(RaidTrackerConfig config, String label) {
		if (config == null || label == null) {
			return "";
		}
		if ("Target total".equals(label)) {
			return settingsTotal(config);
		}
		if ("Tekton".equals(label)) {
			return text(config.cmTekton());
		}
		if ("Crabs".equals(label)) {
			return text(config.cmCrabs());
		}
		if ("Ice demon".equals(label)) {
			return text(config.cmIceDemon());
		}
		if ("Shamans".equals(label)) {
			return text(config.cmShamans());
		}
		if ("Vanguards".equals(label)) {
			return text(config.cmVanguards());
		}
		if ("Thieving".equals(label)) {
			return text(config.cmThieving());
		}
		if ("Vespula".equals(label)) {
			return text(config.cmVespula());
		}
		if ("Tightrope".equals(label)) {
			return text(config.cmTightrope());
		}
		if ("Guardians".equals(label)) {
			return text(config.cmGuardians());
		}
		if ("Vasa".equals(label)) {
			return text(config.cmVasa());
		}
		if ("Mystics".equals(label)) {
			return text(config.cmMystics());
		}
		if ("Muttadiles".equals(label)) {
			return text(config.cmMuttadiles());
		}
		if ("Olm mage hand".equals(label)) {
			return text(config.cmOlmMage());
		}
		if ("Olm phase".equals(label)) {
			return text(config.cmOlmPhase());
		}
		if ("Olm head".equals(label)) {
			return text(config.cmOlmHead());
		}
		if ("Between room time".equals(label)) {
			return text(config.cmBetweenRooms());
		}
		if ("Total Points".equals(label)) {
			return text(config.cmTotalPoints());
		}
		if ("PPH".equals(label)) {
			return settingsPph(config);
		}
		return "";
	}

	private static String text(String value) {
		return value == null ? "" : value;
	}

	private static String sheetKey(String label) {
		if ("Olm mage hand".equals(label)) {
			return "mage";
		}
		if ("Olm phase".equals(label)) {
			return "phase";
		}
		if ("Olm head".equals(label)) {
			return "head";
		}
		if ("Between room time".equals(label)) {
			return "between";
		}
		if ("Tekton".equals(label) || "Crabs".equals(label) || "Ice demon".equals(label)
			|| "Shamans".equals(label) || "Vanguards".equals(label) || "Thieving".equals(label)
			|| "Vespula".equals(label) || "Tightrope".equals(label) || "Guardians".equals(label)
			|| "Vasa".equals(label) || "Mystics".equals(label) || "Muttadiles".equals(label)) {
			return label;
		}
		return null;
	}

	private static ChambersTimes.Table thisRaid(
		List<RaidTracker> raids,
		RaidTracker live,
		int openSeconds,
		RaidTrackerConfig config,
		String mode,
		String size
	) {
		boolean showingLive = live != null && (openSeconds > 0 || hasSplit(live));
		if (!showingLive && raids.isEmpty()) {
			return new ChambersTimes.Table("No completed raids in this filter yet.", new ArrayList<ChambersTimes.Row>(), new ArrayList<ChambersTimes.Mark>(), null, true, "target");
		}
		RaidTracker subject = showingLive ? live : raids.get(raids.size() - 1);
		List<RaidTracker> scope = new ArrayList<>();
		scope.add(subject);
		Map<String, Integer> targets = targets(config, scope);
		List<String> keys = ChambersTimes.keysFor(raids, subject);
		List<ChambersTimes.Row> rows = new ArrayList<>();
		Map<String, Integer> actual = new LinkedHashMap<>();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			Integer shown = ChambersTimes.raw(subject, key);
			if (shown == null) {
				continue;
			}
			actual.put(key, shown);
			rows.add(row(key, shown, targets.get(key), false, ChambersFacts.diedInRoom(subject, ChambersTimes.label(key))));
		}
		if (showingLive && openSeconds > 0) {
			rows.add(new ChambersTimes.Row("Current", "Current", 0, ChambersFacts.formatTime(openSeconds), "", null, false, true, false));
		}
		if (rows.isEmpty()) {
			return new ChambersTimes.Table(showingLive ? "Waiting for the first room." : "No room times in this filter yet.", rows, new ArrayList<ChambersTimes.Mark>(), null, true, "target");
		}
		boolean finished = !showingLive || subject.getRaidTime() > 0;
		List<ChambersTimes.Mark> pace = pace(keys, actual, targets, finished);
		List<RaidTracker> comparison = showingLive ? raids : beforeLast(raids);
		return new ChambersTimes.Table(caption(subject, showingLive), rows, pace, personalBestGap(comparison, targets.get("raid")), true, "target");
	}

	private static ChambersTimes.Table lastN(List<RaidTracker> raids, RaidTrackerConfig config, String mode, String size, int count) {
		if (raids.isEmpty()) {
			return emptyTarget("No completed raids in this filter yet.");
		}
		int window = Math.max(1, Math.min(100, count));
		List<RaidTracker> slice = raids.size() <= window ? raids : raids.subList(raids.size() - window, raids.size());
		return againstSheet(slice, config);
	}

	/** Average of these raids on the target sheet. A room with no valid time is left out. */
	private static ChambersTimes.Table againstSheet(List<RaidTracker> slice, RaidTrackerConfig config) {
		Map<String, Integer> targets = targets(config, slice);
		List<String> keys = ChambersTimes.keysFor(slice, null);
		List<ChambersTimes.Row> rows = new ArrayList<>();
		Map<String, Integer> actual = new LinkedHashMap<>();
		boolean includeIceMilking = config != null && config.iceMilking();
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			Integer shown = averageOf(slice, key, includeIceMilking);
			if (shown == null) {
				continue;
			}
			actual.put(key, shown);
			rows.add(row(key, shown, targets.get(key), false));
		}
		if (rows.isEmpty()) {
			return emptyTarget("No room times in this filter yet.");
		}
		List<ChambersTimes.Mark> pace = pace(keys, actual, targets, actual.containsKey("raid"));
		return new ChambersTimes.Table("", rows, pace, personalBestGap(slice, targets.get("raid")), true, "target");
	}

	private static ChambersTimes.Table emptyTarget(String caption) {
		return new ChambersTimes.Table(caption, new ArrayList<ChambersTimes.Row>(), new ArrayList<ChambersTimes.Mark>(), null, true, "target");
	}

	/**
	 * Seconds the fastest raid in this comparison sits ahead of the target line.
	 * This uses the raids before the one on screen, or every saved raid while one is live.
	 * Last N and Avg use the raids in that average. A raid under 10 seconds is left out.
	 */
	private static Integer personalBestGap(List<RaidTracker> comparison, Integer targetRaid) {
		Integer best = ChambersTimes.fastestRaidSeconds(comparison);
		if (best == null || targetRaid == null || targetRaid <= 0) {
			return null;
		}
		return targetRaid - best;
	}

	private static List<RaidTracker> beforeLast(List<RaidTracker> raids) {
		if (raids.size() <= 1) {
			return new ArrayList<>();
		}
		return raids.subList(0, raids.size() - 1);
	}

	static Map<String, Integer> targets(RaidTrackerConfig config, List<RaidTracker> raids) {
		Map<String, Integer> sheet = typed(config);
		applyStyle(sheet, config);
		derivePreOlm(sheet);
		return expandOlm(sheet, raids);
	}

	private static Map<String, Integer> typed(RaidTrackerConfig config) {
		Map<String, Integer> sheet = new LinkedHashMap<>();
		if (config == null) {
			return sheet;
		}
		putTime(sheet, "Tekton", config.cmTekton());
		putTime(sheet, "Crabs", config.cmCrabs());
		putTime(sheet, "Ice demon", config.cmIceDemon());
		putTime(sheet, "Shamans", config.cmShamans());
		putTime(sheet, "Vanguards", config.cmVanguards());
		putTime(sheet, "Thieving", config.cmThieving());
		putTime(sheet, "Vespula", config.cmVespula());
		putTime(sheet, "Tightrope", config.cmTightrope());
		putTime(sheet, "Guardians", config.cmGuardians());
		putTime(sheet, "Vasa", config.cmVasa());
		putTime(sheet, "Mystics", config.cmMystics());
		putTime(sheet, "Muttadiles", config.cmMuttadiles());
		putTime(sheet, "mage", config.cmOlmMage());
		putTime(sheet, "phase", config.cmOlmPhase());
		putTime(sheet, "head", config.cmOlmHead());
		putTime(sheet, "between", config.cmBetweenRooms());
		putCount(sheet, "points", config.cmTotalPoints());
		return sheet;
	}

	private static void applyStyle(Map<String, Integer> sheet, RaidTrackerConfig config) {
		if (config == null) {
			return;
		}
		boolean killRope = config.killRope();
		if (!config.useTbow()) {
			shift(sheet, "Shamans", 6);
			shift(sheet, "Vanguards", 2);
			shift(sheet, "Vasa", 22);
			shift(sheet, "Muttadiles", 13);
			shift(sheet, "head", 20);
			if (killRope) {
				shift(sheet, "Tightrope", 8);
			}
		}
		if (!config.slayerHelm()) {
			shift(sheet, "Shamans", 9);
		}
		if (!config.lockpick()) {
			shift(sheet, "Thieving", 4);
		}
		if (!config.axe()) {
			shift(sheet, "Ice demon", 5);
		}
		if (!config.salve()) {
			shift(sheet, "Mystics", 5);
		}
		if (!config.preVeng()) {
			shift(sheet, "Tekton", 4);
		}
		if (!config.vespPotSkip()) {
			shift(sheet, "Tightrope", 6);
		}
		if (!config.crabTank()) {
			shift(sheet, "Crabs", 4);
		}
		if (config.iceMilking()) {
			shift(sheet, "Ice demon", clamp(config.iceMilkSeconds()));
		}
		if (killRope) {
			shift(sheet, "Tightrope", 59);
		}
		if (config.milkVespula()) {
			shift(sheet, "Vespula", 15);
		}
		if (config.overThieve()) {
			shift(sheet, "Thieving", 4);
		}
		shift(sheet, "points", pointBonus(config));
		if (config.sippingPools()) {
			shift(sheet, "between", 12);
		}
		if (config.bankAfterRope()) {
			shift(sheet, "between", 5);
		}
	}

	private static void derivePreOlm(Map<String, Integer> sheet) {
		int sum = 0;
		boolean any = false;
		for (int i = 0; i < ChambersFacts.PREP_ROOMS.length; i++) {
			Integer value = sheet.get(ChambersFacts.PREP_ROOMS[i]);
			if (value != null && value > 0) {
				sum += value;
				any = true;
			}
		}
		if (any) {
			sheet.put("pre", sum);
		}
	}

	/**
	 * One raid uses its own phase count. A mix keeps the 3-phase Olm total
	 * and still copies the phase time onto a later phase.
	 */
	private static Map<String, Integer> expandOlm(Map<String, Integer> sheet, List<RaidTracker> raids) {
		int shared = -1;
		int stamp = SOLO_PHASES;
		boolean mixed = false;
		if (raids != null) {
			for (int i = 0; i < raids.size(); i++) {
				RaidTracker raid = raids.get(i);
				if (raid == null) {
					continue;
				}
				int count = phaseCount(raid);
				if (count > stamp) {
					stamp = count;
				}
				if (shared < 0) {
					shared = count;
				} else if (shared != count) {
					mixed = true;
				}
			}
		}
		int total = mixed || shared < 0 ? SOLO_PHASES : shared;
		Integer phase = sheet.get("phase");
		Integer head = sheet.get("head");
		if (head != null && head > 0 && (phase == null || phase <= 0)) {
			sheet.put("olm", head + gapSeconds(SOLO_PHASES));
		}
		if (phase == null || phase <= 0) {
			deriveRaid(sheet);
			return sheet;
		}
		int drawn = Math.min(stamp, 8);
		int phases = Math.min(Math.max(total, 1), MAX_PHASES);
		for (int i = 1; i <= drawn; i++) {
			sheet.put("phase:" + i, phase);
		}
		Integer mage = sheet.get("mage");
		if (mage != null && mage > 0) {
			for (int i = 1; i < stamp && i <= 8; i++) {
				sheet.put("mage:" + i, mage);
			}
		}
		int sum = phase * phases;
		if (head != null && head > 0) {
			sum += head;
		}
		sum += gapSeconds(phases);
		sheet.put("olm", sum);
		deriveRaid(sheet);
		return sheet;
	}

	private static void deriveRaid(Map<String, Integer> sheet) {
		Integer pre = sheet.get("pre");
		Integer olm = sheet.get("olm");
		Integer between = sheet.get("between");
		if (pre != null && pre > 0 && olm != null && olm > 0 && between != null && between > 0) {
			sheet.put("raid", pre + olm + between);
		}
		derivePph(sheet);
	}

	/** Same truncation as a logged raid. A missing raid time or points target clears it. */
	private static void derivePph(Map<String, Integer> sheet) {
		Integer points = sheet.get("points");
		Integer raid = sheet.get("raid");
		if (points == null || points <= 0 || raid == null || raid <= 0) {
			sheet.remove("pph");
			return;
		}
		sheet.put("pph", (int) (points / (raid / 3600.0)));
	}

	static int phaseCount(RaidTracker raid) {
		if (raid == null) {
			return SOLO_PHASES;
		}
		int recorded = 0;
		if (raid.getOlmPhaseTimes() != null) {
			int limit = Math.min(raid.getOlmPhaseTimes().size(), MAX_PHASES);
			for (int i = 0; i < limit; i++) {
				Integer value = raid.getOlmPhaseTimes().get(i);
				if (value != null && value > 0) {
					recorded = i + 1;
				}
			}
		}
		int scale = raid.getTeamSize() <= 0 ? 1 : raid.getTeamSize();
		int expected = SOLO_PHASES + (scale / 8);
		return Math.min(MAX_PHASES, Math.max(expected, recorded));
	}

	private static int gapSeconds(int phases) {
		int count = phases < SOLO_PHASES ? SOLO_PHASES : phases;
		return 60 * (count - 1) / 2;
	}

	private static List<ChambersTimes.Mark> pace(List<String> keys, Map<String, Integer> actual, Map<String, Integer> targets, boolean finished) {
		List<ChambersTimes.Mark> marks = new ArrayList<>();
		int roomBank = 0;
		int phaseBank = 0;
		boolean olmApplied = false;
		boolean plotted = false;
		for (int i = 0; i < keys.size(); i++) {
			String key = keys.get(i);
			if (key.startsWith("mage:") || "pre".equals(key) || "between".equals(key) || "points".equals(key) || "pph".equals(key)) {
				continue;
			}
			Integer value = actual.get(key);
			Integer target = targets.get(key);
			if (value == null || target == null) {
				continue;
			}
			int gain = target - value;
			if (ChambersFacts.floorOf(key) > 0) {
				roomBank += gain;
				marks.add(new ChambersTimes.Mark(ChambersTimes.label(key), roomBank));
				plotted = true;
			} else if ("head".equals(key) || key.startsWith("phase:")) {
				phaseBank += gain;
				if (!olmApplied) {
					marks.add(new ChambersTimes.Mark(ChambersTimes.label(key), roomBank + phaseBank));
					plotted = true;
				}
			} else if ("olm".equals(key)) {
				olmApplied = true;
				marks.add(new ChambersTimes.Mark("Olm", roomBank + gain));
				plotted = true;
			}
		}
		if (finished) {
			Integer total = actual.get("raid");
			Integer target = targets.get("raid");
			if (total != null && target != null) {
				marks.add(new ChambersTimes.Mark("Finish", target - total));
				plotted = true;
			}
		}
		if (!plotted) {
			return new ArrayList<>();
		}
		List<ChambersTimes.Mark> withStart = new ArrayList<>();
		withStart.add(new ChambersTimes.Mark("Start", 0));
		withStart.addAll(marks);
		return withStart;
	}

	private static ChambersTimes.Row row(String key, int value, Integer target, boolean open) {
		return row(key, value, target, open, false);
	}

	private static ChambersTimes.Row row(String key, int value, Integer target, boolean open, boolean died) {
		boolean points = "points".equals(key) || "pph".equals(key);
		Integer delta = null;
		String diff = "";
		if (target != null) {
			delta = value - target;
			diff = points ? pointDiff(delta) : timeDiff(delta);
		}
		String shown = points ? Integer.toString(value) : ChambersFacts.formatTime(value);
		return new ChambersTimes.Row(ChambersTimes.label(key), ChambersTimes.group(key), ChambersFacts.floorOf(key), shown, diff, delta, points, open, false, died);
	}

	private static Integer averageOf(List<RaidTracker> raids, String key, boolean includeIceMilking) {
		long sum = 0;
		int count = 0;
		for (int i = 0; i < raids.size(); i++) {
			Integer value = ChambersTimes.valid(raids.get(i), key, includeIceMilking);
			if (value == null) {
				continue;
			}
			sum += value;
			count++;
		}
		if (count == 0) {
			return null;
		}
		return (int) Math.round((double) sum / count);
	}

	private static boolean hasSplit(RaidTracker raid) {
		for (int i = 0; i < ChambersFacts.PREP_ROOMS.length; i++) {
			if (ChambersTimes.raw(raid, ChambersFacts.PREP_ROOMS[i]) != null) {
				return true;
			}
		}
		return ChambersTimes.raw(raid, "mage:1") != null
			|| ChambersTimes.raw(raid, "phase:1") != null
			|| ChambersTimes.raw(raid, "head") != null;
	}

	private static String caption(RaidTracker raid, boolean live) {
		if (live && raid.getRaidTime() <= 0) {
			return "In progress";
		}
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

	private static void shift(Map<String, Integer> sheet, String key, int delta) {
		if (delta == 0) {
			return;
		}
		Integer value = sheet.get(key);
		if (value == null || value <= 0) {
			return;
		}
		int next = value + delta;
		if (next < 1) {
			next = 1;
		}
		sheet.put(key, next);
	}

	private static void putTime(Map<String, Integer> sheet, String key, String text) {
		Integer seconds = parseTime(text);
		if (seconds != null) {
			sheet.put(key, seconds);
		}
	}

	private static void putCount(Map<String, Integer> sheet, String key, String text) {
		Integer count = parseCount(text);
		if (count != null) {
			sheet.put(key, count);
		}
	}

	static Integer parseTime(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		int colon = trimmed.indexOf(':');
		if (colon <= 0 || colon >= trimmed.length() - 1) {
			return null;
		}
		try {
			int minutes = Integer.parseInt(trimmed.substring(0, colon).trim());
			int seconds = Integer.parseInt(trimmed.substring(colon + 1).trim());
			if (minutes < 0 || seconds < 0 || seconds >= 60) {
				return null;
			}
			int total = minutes * 60 + seconds;
			return total > 0 ? total : null;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static Integer parseCount(String text) {
		if (text == null) {
			return null;
		}
		String trimmed = text.trim();
		if (trimmed.isEmpty()) {
			return null;
		}
		try {
			int value = Integer.parseInt(trimmed);
			return value > 0 ? value : null;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	private static int clamp(int seconds) {
		if (seconds < 0) {
			return 0;
		}
		return Math.min(seconds, 600);
	}

}
