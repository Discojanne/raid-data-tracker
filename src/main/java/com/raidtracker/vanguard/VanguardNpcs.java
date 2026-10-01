package com.raidtracker.vanguard;

/**
 * Vanguard ids. Normal and Challenge Mode share them.
 * 7526 is the form they take while shuffling.
 */
public final class VanguardNpcs {
	public static final int WALKING = 7526;
	public static final int MELEE = 7527;
	public static final int RANGED = 7528;
	public static final int MAGIC = 7529;

	private VanguardNpcs() {
	}

	/** {@code melee}, {@code ranged}, or {@code magic}. Null for the walking form and anything else. */
	public static String style(int id) {
		if (id == MELEE) {
			return "melee";
		}
		if (id == RANGED) {
			return "ranged";
		}
		if (id == MAGIC) {
			return "magic";
		}
		return null;
	}

	public static boolean isVanguard(int id) {
		return id >= WALKING && id <= MAGIC;
	}
}
