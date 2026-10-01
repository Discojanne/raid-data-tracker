package com.raidtracker.vanguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The three Vanguard holes. A is the western hole, C the eastern hole.
 * When two holes share an east-west line, the southern one comes first.
 */
public final class VanguardHoles {
	/** Tiles. A center inside this distance is the same hole. Farther than this, melee has left its hole. */
	public static final int SAME_HOLE = 5;

	private final List<int[]> points = new ArrayList<>();

	public int size() {
		return points.size();
	}

	public void clear() {
		points.clear();
	}

	/**
	 * Matches an existing hole, or records a new one.
	 * Once three holes exist, a later point snaps to the nearest of those three.
	 */
	public int add(int x, int y) {
		int nearest = -1;
		int best = Integer.MAX_VALUE;
		for (int i = 0; i < points.size(); i++) {
			int distance = chebyshev(points.get(i)[0], points.get(i)[1], x, y);
			if (distance < best) {
				best = distance;
				nearest = i;
			}
		}
		if (nearest >= 0 && (best <= SAME_HOLE || points.size() >= 3)) {
			return nearest;
		}
		points.add(new int[] {x, y});
		return points.size() - 1;
	}

	public int distance(int index, int x, int y) {
		if (index < 0 || index >= points.size()) {
			return Integer.MAX_VALUE;
		}
		return chebyshev(points.get(index)[0], points.get(index)[1], x, y);
	}

	/** {@code A}, {@code B}, or {@code C}. Empty when the index is unknown. */
	public String letter(int index) {
		if (index < 0 || index >= points.size()) {
			return "";
		}
		Integer[] order = new Integer[points.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (left, right) -> {
			int byEast = Integer.compare(points.get(left)[0], points.get(right)[0]);
			if (byEast != 0) {
				return byEast;
			}
			return Integer.compare(points.get(left)[1], points.get(right)[1]);
		});
		for (int place = 0; place < order.length; place++) {
			if (order[place] == index) {
				return place == 0 ? "A" : place == 1 ? "B" : "C";
			}
		}
		return "";
	}

	public static int chebyshev(int x1, int y1, int x2, int y2) {
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}
}
