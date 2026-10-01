package com.raidtracker;

import com.raidtracker.vanguard.VanguardLog;
import com.raidtracker.vanguard.VanguardSample;
import com.raidtracker.vanguard.VanguardUptime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class VanguardLogTest {
	@Test
	public void writesASeparateFileAndReplacesTheSameRoom() throws Exception {
		Path root = Files.createTempDirectory("raidtracker-vanguards");
		VanguardLog store = new VanguardLog();
		VanguardSample sample = sample("room-1");
		store.save(root.toString(), "acct-1", sample);

		sample.setKc(216);
		sample.setConfirmed(true);
		store.save(root.toString(), "acct-1", sample);
		store.save(root.toString(), "acct-1", sample("room-2"));

		List<VanguardSample> loaded = store.load(root.toString(), "acct-1");
		Assert.assertEquals(2, loaded.size());
		Assert.assertEquals(Integer.valueOf(216), loaded.get(0).getKc());
		Assert.assertTrue(loaded.get(0).isConfirmed());
		Assert.assertEquals("A", loaded.get(0).getUptimes().get(0).getMelee());
		Assert.assertEquals(Integer.valueOf(6), loaded.get(0).getUptimes().get(0).getGapTicks());
		Assert.assertNull(loaded.get(0).getUptimes().get(1).getGapTicks());
		Assert.assertEquals("room-2", loaded.get(1).getId());

		Path file = root.resolve("vanguards-acct-1.json");
		Assert.assertTrue(Files.exists(file));
		Assert.assertFalse(Files.exists(root.resolve("raid_tracker_data.log")));
		String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		Assert.assertTrue(json.contains("\"kc\": 216"));
		Assert.assertTrue(json.contains("\"melee\": \"A\""));

		store.remove(root.toString(), "acct-1", "room-2");
		Assert.assertEquals(1, store.load(root.toString(), "acct-1").size());
	}

	@Test
	public void aLeagueKeyUsesItsOwnFile() throws Exception {
		Path root = Files.createTempDirectory("raidtracker-vanguards-league");
		VanguardLog store = new VanguardLog();
		store.save(root.toString(), "99", sample("main"));
		store.save(root.toString(), "99-league", sample("league"));
		Assert.assertTrue(Files.exists(root.resolve("vanguards-99.json")));
		Assert.assertTrue(Files.exists(root.resolve("vanguards-99-league.json")));
		Assert.assertEquals("main", store.load(root.toString(), "99").get(0).getId());
		Assert.assertEquals("league", store.load(root.toString(), "99-league").get(0).getId());
	}

	@Test
	public void saveLaterFlushesOntoTheSameFile() throws Exception {
		Path root = Files.createTempDirectory("raidtracker-vanguards-later");
		VanguardLog store = new VanguardLog();
		store.saveLater(root.toString(), "acct-1", sample("room-1"));
		store.removeLater(root.toString(), "acct-1", "missing");
		store.flush();

		Assert.assertEquals(1, store.load(root.toString(), "acct-1").size());
		Assert.assertEquals("room-1", store.load(root.toString(), "acct-1").get(0).getId());
		Assert.assertTrue(Files.exists(root.resolve("vanguards-acct-1.json")));
	}

	private static VanguardSample sample(String id) {
		VanguardSample sample = new VanguardSample();
		sample.setId(id);
		sample.setStartedAt("2026-09-27T07:00:00Z");
		sample.setEmergedAt("2026-09-27T07:04:12Z");
		sample.setWorld(302);
		sample.setTeamSize(1);
		sample.setRaidClockEmerge(400);
		sample.setRaidClockComplete(900);
		sample.setFinished(true);
		VanguardUptime first = new VanguardUptime();
		first.setIndex(1);
		first.setMelee("A");
		first.setRanged("C");
		first.setMagic("B");
		first.setDurationTicks(28);
		first.setGapTicks(6);
		first.setMeleeRatio(20);
		first.setMeleeScale(30);
		first.setAnimation(8001);
		sample.getUptimes().add(first);
		VanguardUptime second = new VanguardUptime();
		second.setIndex(2);
		second.setMelee("B");
		second.setRanged("A");
		second.setMagic("C");
		second.setDurationTicks(22);
		second.setHeal(true);
		second.setForced(true);
		sample.getUptimes().add(second);
		return sample;
	}
}
