package com.raidtracker.ui.chambers;

import com.raidtracker.ChambersDeath;
import com.raidtracker.RaidTracker;
import java.util.ArrayList;
import java.util.List;

/**
 * Points and purple summary for one filtered set of Chambers raids.
 */
public final class ChambersPurples {
	public enum Mark {
		DRY,
		PURPLE,
		EXPECTED,
		BOTH,
		NEXT
	}

	public static final class Item {
		public final String name;
		public final int got;
		public final double onRate;

		Item(String name, int got, double onRate) {
			this.name = name;
			this.got = got;
			this.onRate = onRate;
		}

		public double diff() {
			return got - onRate;
		}
	}

	public static final class Drop {
		public final String kc;
		public final String item;
		public final boolean challengeMode;
		public final String kind;

		Drop(String kc, String item, boolean challengeMode, String kind) {
			this.kc = kc;
			this.item = item;
			this.challengeMode = challengeMode;
			this.kind = kind == null ? "" : kind;
		}
	}

	/** How many of your deaths were in one room. */
	public static final class Room {
		public final String name;
		public final int deaths;

		Room(String name, int deaths) {
			this.name = name;
			this.deaths = deaths;
		}
	}

	public final int raids;
	public final int regular;
	public final int challengeMode;
	public final int actual;
	public final int scrolls;
	public final long points;
	public final int pointsLost;
	public final double averagePoints;
	public final double expected;
	public final int dryNow;
	public final int longestDry;
	public final int averageDry;
	public final int expectedEvery;
	public final List<Item> items;
	public final List<Item> sides;
	public final List<Mark> marks;
	public final List<String> sideKinds;
	public final List<Drop> tracked;
	public final List<Room> rooms;

	private ChambersPurples(
		int raids,
		int regular,
		int challengeMode,
		int actual,
		int scrolls,
		long points,
		int pointsLost,
		double averagePoints,
		double expected,
		int dryNow,
		int longestDry,
		int averageDry,
		int expectedEvery,
		List<Item> items,
		List<Item> sides,
		List<Mark> marks,
		List<String> sideKinds,
		List<Drop> tracked,
		List<Room> rooms
	) {
		this.raids = raids;
		this.regular = regular;
		this.challengeMode = challengeMode;
		this.actual = actual;
		this.scrolls = scrolls;
		this.points = points;
		this.pointsLost = pointsLost;
		this.averagePoints = averagePoints;
		this.expected = expected;
		this.dryNow = dryNow;
		this.longestDry = longestDry;
		this.averageDry = averageDry;
		this.expectedEvery = expectedEvery;
		this.items = items;
		this.sides = sides;
		this.marks = marks;
		this.sideKinds = sideKinds;
		this.tracked = tracked;
		this.rooms = rooms == null ? new ArrayList<Room>() : rooms;
	}

	public static ChambersPurples empty() {
		return new ChambersPurples(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			new ArrayList<Item>(), new ArrayList<Item>(), new ArrayList<Mark>(), new ArrayList<String>(), new ArrayList<Drop>(),
			new ArrayList<Room>());
	}

	public boolean isEmpty() {
		return raids == 0;
	}

	public double rate() {
		return expected > 0 ? raids / expected : 0;
	}

	public double diff() {
		return actual - expected;
	}

	public double lostPurples() {
		return pointsLost / ChambersFacts.PURPLE_POINTS;
	}

	public double lostRaids() {
		return averagePoints > 0 ? pointsLost / averagePoints : 0;
	}

	public static ChambersPurples of(List<RaidTracker> raids) {
		List<RaidTracker> list = raids == null ? new ArrayList<RaidTracker>() : raids;
		if (list.isEmpty()) {
			return empty();
		}
		String[] names = ChambersFacts.purpleNames();
		int[] got = new int[names.length];
		double[] expectedItems = new double[names.length];
		int regular = 0;
		int challenge = 0;
		int actual = 0;
		int scrolls = 0;
		long points = 0;
		int pointed = 0;
		int lost = 0;
		double expected = 0;
		for (int i = 0; i < list.size(); i++) {
			RaidTracker raid = list.get(i);
			if (raid.isChallengeMode()) {
				challenge++;
			} else {
				regular++;
			}
			lost += ChambersFacts.pointsLost(raid);
			List<String> purples = ChambersFacts.ownPurples(raid);
			if (!purples.isEmpty()) {
				actual++;
			}
			for (int p = 0; p < purples.size(); p++) {
				int index = indexOf(names, purples.get(p));
				if (index >= 0) {
					got[index]++;
				}
				if (ChambersFacts.isScroll(purples.get(p))) {
					scrolls++;
				}
			}
			if (raid.getPersonalPoints() <= 0) {
				continue;
			}
			points += raid.getPersonalPoints();
			pointed++;
			double chance = raid.getPersonalPoints() / ChambersFacts.PURPLE_POINTS;
			expected += chance;
			int totalWeight = raid.isChallengeMode() ? ChambersFacts.cmWeightTotal() : ChambersFacts.regularWeightTotal();
			for (int w = 0; w < names.length; w++) {
				int weight = raid.isChallengeMode() ? ChambersFacts.cmWeight(w) : ChambersFacts.regularWeight(w);
				expectedItems[w] += chance * weight / totalWeight;
			}
		}
		int streak = 0;
		int longest = 0;
		int finishedSum = 0;
		int finishedCount = 0;
		for (int i = 0; i < list.size(); i++) {
			if (!ChambersFacts.ownPurples(list.get(i)).isEmpty()) {
				if (streak > 0) {
					finishedSum += streak;
					finishedCount++;
					longest = Math.max(longest, streak);
				}
				streak = 0;
			} else {
				streak++;
				longest = Math.max(longest, streak);
			}
		}
		int dryNow = streak;
		if (streak > 0) {
			finishedSum += streak;
			finishedCount++;
		}
		int averageDry = finishedCount == 0 ? 0 : (int) Math.round((double) finishedSum / finishedCount);
		double rate = expected > 0 ? list.size() / expected : 0;
		int expectedEvery = rate > 0 ? Math.max(1, (int) Math.round(rate)) : 0;
		List<Item> items = new ArrayList<>();
		for (int i = 0; i < names.length; i++) {
			items.add(new Item(names[i], got[i], expectedItems[i]));
		}
		int pets = 0;
		int kits = 0;
		int dusts = 0;
		List<Mark> marks = new ArrayList<>();
		List<String> sideKinds = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			boolean purple = !ChambersFacts.ownPurples(list.get(i)).isEmpty();
			boolean onRate = expectedEvery > 0 && (i + 1) % expectedEvery == 0;
			if (purple && onRate) {
				marks.add(Mark.BOTH);
			} else if (purple) {
				marks.add(Mark.PURPLE);
			} else if (onRate) {
				marks.add(Mark.EXPECTED);
			} else {
				marks.add(Mark.DRY);
			}
			List<String> sides = ChambersFacts.ownSides(list.get(i));
			String kind = "";
			for (int s = 0; s < sides.size(); s++) {
				String sideKind = ChambersFacts.sideKind(sides.get(s));
				if ("pet".equals(sideKind)) {
					pets++;
				} else if ("kit".equals(sideKind)) {
					kits++;
				} else if ("dust".equals(sideKind)) {
					dusts++;
				}
				if (kind.isEmpty()) {
					kind = sideKind;
				}
			}
			sideKinds.add(kind);
		}
		marks.add(Mark.NEXT);
		sideKinds.add("");
		List<Item> sides = new ArrayList<>();
		sides.add(new Item("Olmlet", pets, 0));
		sides.add(new Item("Twisted Kit", kits, 0));
		sides.add(new Item("Metamorphic Dust", dusts, 0));
		List<Drop> tracked = new ArrayList<>();
		for (int i = list.size() - 1; i >= 0; i--) {
			RaidTracker raid = list.get(i);
			String kc = raid.getCompletionCount() > 0 ? Integer.toString(raid.getCompletionCount()) : "--";
			List<String> purples = ChambersFacts.ownPurples(raid);
			for (int p = 0; p < purples.size(); p++) {
				tracked.add(new Drop(kc, purples.get(p), raid.isChallengeMode(), ""));
			}
			List<String> ownSides = ChambersFacts.ownSides(raid);
			for (int s = 0; s < ownSides.size(); s++) {
				tracked.add(new Drop(kc, ownSides.get(s), raid.isChallengeMode(), ChambersFacts.sideKind(ownSides.get(s))));
			}
		}
		List<Room> rooms = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			RaidTracker raid = list.get(i);
			if (raid.getDeaths() == null) {
				continue;
			}
			for (int d = 0; d < raid.getDeaths().size(); d++) {
				ChambersDeath death = raid.getDeaths().get(d);
				if (death == null || death.getRoom() == null || death.getRoom().isEmpty()) {
					continue;
				}
				addRoom(rooms, death.getRoom());
			}
		}
		sortRooms(rooms);
		double averagePoints = pointed == 0 ? 0 : (double) points / pointed;
		return new ChambersPurples(list.size(), regular, challenge, actual, scrolls, points, lost,
			averagePoints, expected, dryNow, longest, averageDry, expectedEvery, items, sides, marks, sideKinds, tracked,
			rooms);
	}

	private static void addRoom(List<Room> rooms, String name) {
		for (int i = 0; i < rooms.size(); i++) {
			Room room = rooms.get(i);
			if (room.name.equals(name)) {
				rooms.set(i, new Room(name, room.deaths + 1));
				return;
			}
		}
		rooms.add(new Room(name, 1));
	}

	private static void sortRooms(List<Room> rooms) {
		for (int i = 1; i < rooms.size(); i++) {
			Room room = rooms.get(i);
			int j = i;
			while (j > 0 && before(room, rooms.get(j - 1))) {
				rooms.set(j, rooms.get(j - 1));
				j--;
			}
			rooms.set(j, room);
		}
	}

	private static boolean before(Room room, Room other) {
		if (room.deaths != other.deaths) {
			return room.deaths > other.deaths;
		}
		return room.name.compareTo(other.name) < 0;
	}

	private static int indexOf(String[] names, String name) {
		for (int i = 0; i < names.length; i++) {
			if (names[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}
}
