package com.raidtracker;

import com.raidtracker.vanguard.VanguardSample;
import com.raidtracker.vanguard.VanguardTracker;
import com.raidtracker.vanguard.VanguardUptime;
import org.junit.Assert;
import org.junit.Test;

public class VanguardTrackerTest {
	@Test
	public void lettersFollowTheCompassAndStayPutAcrossRaids() {
		VanguardSample first = raid(100, 10, 120, 10, 110, 30);
		VanguardUptime uptime = first.getUptimes().get(0);
		Assert.assertEquals("A", uptime.getMelee());
		Assert.assertEquals("C", uptime.getRanged());
		Assert.assertEquals("B", uptime.getMagic());

		VanguardTracker reversed = armed();
		reversed.contact("t1", 302, 1, 100, 0);
		reversed.emerge("magic", 110, 30);
		reversed.emerge("ranged", 120, 10);
		reversed.emerge("melee", 100, 10);
		tick(reversed, 20);
		digAll(reversed, 100, 10, 120, 10, 110, 30);
		VanguardUptime again = reversed.finish(true, 200).getUptimes().get(0);
		Assert.assertEquals("A", again.getMelee());
		Assert.assertEquals("C", again.getRanged());
		Assert.assertEquals("B", again.getMagic());
	}

	@Test
	public void aTieOnEastGoesToTheSouthernHole() {
		VanguardTracker tracker = armed();
		tracker.contact("t1", 302, 1, 100, 0);
		tracker.emerge("melee", 100, 50);
		tracker.emerge("ranged", 100, 10);
		tracker.emerge("magic", 130, 30);
		tick(tracker, 20);
		digAll(tracker, 100, 50, 100, 10, 130, 30);
		VanguardUptime uptime = tracker.finish(true, 200).getUptimes().get(0);
		Assert.assertEquals("B", uptime.getMelee());
		Assert.assertEquals("A", uptime.getRanged());
		Assert.assertEquals("C", uptime.getMagic());
	}

	@Test
	public void recordsUptimeGapHealthAndADraggedMelee() {
		VanguardTracker tracker = armed();
		tracker.contact("2026-09-27T07:04:12Z", 302, 1, 400, 0);
		tracker.emerge("melee", 100, 10);
		tracker.emerge("ranged", 120, 10);
		tracker.emerge("magic", 110, 30);
		tick(tracker, 28);
		tracker.dig("melee", 100, 40, 20, 30, 8001);
		tracker.dig("ranged", 120, 10, 21, 30, 8002);
		tracker.dig("magic", 110, 30, 19, 30, 8003);
		tick(tracker, 6);
		tracker.emerge("melee", 110, 30);
		tracker.emerge("ranged", 100, 10);
		tracker.emerge("magic", 120, 10);
		tick(tracker, 22);
		tracker.heal();
		digAll(tracker, 110, 30, 100, 10, 120, 10);

		VanguardSample sample = tracker.finish(true, 900);
		Assert.assertEquals("2026-09-27T07:00:00Z", sample.getStartedAt());
		Assert.assertEquals(302, sample.getWorld());
		Assert.assertEquals(1, sample.getTeamSize());
		Assert.assertTrue(sample.isFinished());
		Assert.assertEquals(400, sample.getRaidClockEmerge());
		Assert.assertEquals(900, sample.getRaidClockComplete());
		Assert.assertEquals(2, sample.getUptimes().size());

		VanguardUptime first = sample.getUptimes().get(0);
		Assert.assertEquals(1, first.getIndex());
		Assert.assertEquals(28, first.getDurationTicks());
		Assert.assertEquals(Integer.valueOf(6), first.getGapTicks());
		Assert.assertEquals(20, first.getMeleeRatio());
		Assert.assertEquals(30, first.getMeleeScale());
		Assert.assertFalse(first.isHeal());
		Assert.assertFalse(first.isForced());
		Assert.assertTrue(first.isMeleeOffHole());
		Assert.assertEquals(8001, first.getAnimation());
		Assert.assertEquals("A", first.getMelee());
		Assert.assertEquals("C", first.getRanged());
		Assert.assertEquals("B", first.getMagic());

		VanguardUptime second = sample.getUptimes().get(1);
		Assert.assertEquals("B", second.getMelee());
		Assert.assertEquals("A", second.getRanged());
		Assert.assertEquals("C", second.getMagic());
		Assert.assertEquals(22, second.getDurationTicks());
		Assert.assertNull(second.getGapTicks());
		Assert.assertTrue(second.isHeal());
		Assert.assertTrue(second.isForced());
		Assert.assertFalse(second.isMeleeOffHole());
	}

	@Test
	public void aShortDigIsForcedAndAKillIsNot() {
		VanguardTracker tracker = armed();
		tracker.contact("t1", 302, 1, 10, 0);
		tracker.emerge("melee", 100, 10);
		tracker.emerge("ranged", 120, 10);
		tracker.emerge("magic", 110, 30);
		tick(tracker, 10);
		digAll(tracker, 100, 10, 120, 10, 110, 30);
		VanguardUptime forced = tracker.finish(true, 80).getUptimes().get(0);
		Assert.assertTrue(forced.isForced());
		Assert.assertFalse(forced.isHeal());
		Assert.assertFalse(forced.isKilled());

		VanguardTracker killed = armed();
		killed.contact("t1", 302, 1, 10, 2);
		killed.emerge("melee", 100, 10);
		tick(killed, 4);
		killed.combatGone();
		killed.tick();
		killed.noteDeaths(3);
		VanguardSample sample = killed.finish(true, 40);
		VanguardUptime uptime = sample.getUptimes().get(0);
		Assert.assertTrue(uptime.isKilled());
		Assert.assertFalse(uptime.isForced());
		Assert.assertFalse(uptime.isInterrupted());
		Assert.assertEquals(4, uptime.getDurationTicks());
		Assert.assertEquals(1, sample.getDeaths());
		Assert.assertTrue(sample.isFinished());
	}

	@Test
	public void aResetKeepsTheSampleWithoutAKillCount() {
		VanguardTracker tracker = armed();
		tracker.contact("t1", 302, 3, 50, 0);
		tracker.emerge("melee", 100, 10);
		tracker.emerge("ranged", 120, 10);
		tracker.emerge("magic", 110, 30);
		tick(tracker, 5);
		VanguardSample left = tracker.finish(false, 70);
		Assert.assertFalse(left.isFinished());
		Assert.assertFalse(left.isConfirmed());
		Assert.assertNull(left.getKc());
		Assert.assertTrue(left.getUptimes().get(0).isInterrupted());
		Assert.assertEquals(5, left.getUptimes().get(0).getDurationTicks());

		left = tracker.confirm(216);
		Assert.assertEquals(Integer.valueOf(216), left.getKc());
		Assert.assertTrue(left.isConfirmed());
		Assert.assertEquals("A", left.getUptimes().get(0).getMelee());
	}

	@Test
	public void aDigOnTheSameTickCancelsAPendingKill() {
		VanguardTracker tracker = armed();
		tracker.contact("t1", 302, 1, 10, 0);
		tracker.emerge("melee", 100, 10);
		tick(tracker, 21);
		tracker.combatGone();
		tracker.dig("melee", 100, 10, 10, 30, 5);
		tracker.tick();
		VanguardUptime uptime = tracker.finish(true, 40).getUptimes().get(0);
		Assert.assertFalse(uptime.isKilled());
		Assert.assertEquals(21, uptime.getDurationTicks());
		Assert.assertFalse(uptime.isForced());
	}

	@Test
	public void aRegularRaidWritesNothing() {
		VanguardTracker tracker = new VanguardTracker();
		tracker.startRaid("2026-09-27T07:00:00Z", 1);
		tracker.contact("t1", 302, 1, 10, 0);
		tracker.emerge("melee", 100, 10);
		Assert.assertNull(tracker.finish(true, 40));
	}

	private static VanguardSample raid(int meleeX, int meleeY, int rangedX, int rangedY, int magicX, int magicY) {
		VanguardTracker tracker = armed();
		tracker.contact("t1", 302, 1, 100, 0);
		tracker.emerge("melee", meleeX, meleeY);
		tracker.emerge("ranged", rangedX, rangedY);
		tracker.emerge("magic", magicX, magicY);
		tick(tracker, 20);
		digAll(tracker, meleeX, meleeY, rangedX, rangedY, magicX, magicY);
		return tracker.finish(true, 200);
	}

	private static VanguardTracker armed() {
		VanguardTracker tracker = new VanguardTracker();
		tracker.startRaid("2026-09-27T07:00:00Z", 1);
		tracker.allowChallengeMode();
		return tracker;
	}

	private static void tick(VanguardTracker tracker, int times) {
		for (int i = 0; i < times; i++) {
			tracker.tick();
		}
	}

	private static void digAll(VanguardTracker tracker, int meleeX, int meleeY, int rangedX, int rangedY, int magicX, int magicY) {
		tracker.dig("melee", meleeX, meleeY, 15, 30, 1);
		tracker.dig("ranged", rangedX, rangedY, 15, 30, 1);
		tracker.dig("magic", magicX, magicY, 15, 30, 1);
	}
}
