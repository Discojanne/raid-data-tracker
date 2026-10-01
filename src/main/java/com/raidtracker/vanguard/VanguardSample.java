package com.raidtracker.vanguard;

import java.util.ArrayList;
import java.util.List;

/**
 * One Challenge Mode visit to the Vanguard room. This is study data, not a raid row.
 */
public class VanguardSample {
	private String id;
	private String startedAt = "";
	private String emergedAt = "";
	private int world;
	private int teamSize;
	private Integer kc;
	private boolean confirmed;
	private int raidClockEmerge = -1;
	private int raidClockComplete = -1;
	private boolean finished;
	private int deaths;
	private List<VanguardUptime> uptimes = new ArrayList<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(String startedAt) {
		this.startedAt = startedAt == null ? "" : startedAt;
	}

	public String getEmergedAt() {
		return emergedAt;
	}

	public void setEmergedAt(String emergedAt) {
		this.emergedAt = emergedAt == null ? "" : emergedAt;
	}

	public int getWorld() {
		return world;
	}

	public void setWorld(int world) {
		this.world = world;
	}

	public int getTeamSize() {
		return teamSize;
	}

	public void setTeamSize(int teamSize) {
		this.teamSize = teamSize;
	}

	public Integer getKc() {
		return kc;
	}

	public void setKc(Integer kc) {
		this.kc = kc;
	}

	public boolean isConfirmed() {
		return confirmed;
	}

	public void setConfirmed(boolean confirmed) {
		this.confirmed = confirmed;
	}

	public int getRaidClockEmerge() {
		return raidClockEmerge;
	}

	public void setRaidClockEmerge(int raidClockEmerge) {
		this.raidClockEmerge = raidClockEmerge;
	}

	public int getRaidClockComplete() {
		return raidClockComplete;
	}

	public void setRaidClockComplete(int raidClockComplete) {
		this.raidClockComplete = raidClockComplete;
	}

	public boolean isFinished() {
		return finished;
	}

	public void setFinished(boolean finished) {
		this.finished = finished;
	}

	public int getDeaths() {
		return deaths;
	}

	public void setDeaths(int deaths) {
		this.deaths = deaths;
	}

	public List<VanguardUptime> getUptimes() {
		if (uptimes == null) {
			uptimes = new ArrayList<>();
		}
		return uptimes;
	}
}
