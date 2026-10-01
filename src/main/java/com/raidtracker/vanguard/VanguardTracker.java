package com.raidtracker.vanguard;

import java.util.UUID;

/**
 * Turns Vanguard spawn and dig events into one study sample per Challenge Mode raid.
 * Durations are game ticks between the event and the ticks the plugin has reported.
 */
public final class VanguardTracker {
	/** A dig shorter than this, with no kill and no leave, is the forced heal reset. */
	static final int MIN_NATURAL_TICKS = 20;

	private static final int IDLE = 0;
	private static final int UP = 1;
	private static final int DOWN = 2;

	private final VanguardHoles holes = new VanguardHoles();
	private String startedAt = "";
	private int teamSize;
	private boolean challengeMode;
	private VanguardSample sample;
	private VanguardUptime draft;
	private int tick;
	private int phase = IDLE;
	private int uptimeStart;
	private int deathBaseline = -1;
	private boolean closed;
	private boolean pendingKill;
	private int pendingKillDuration;

	public void startRaid(String startedAt, int teamSize) {
		reset();
		this.startedAt = startedAt == null ? "" : startedAt;
		this.teamSize = teamSize;
	}

	public void allowChallengeMode() {
		challengeMode = true;
	}

	public boolean isChallengeMode() {
		return challengeMode;
	}

	/** A Challenge Mode room is open and its tick counter still matters. */
	public boolean isTiming() {
		return sample != null && !closed;
	}

	/** Health is only read while a form is up. */
	public boolean needsHealth() {
		return phase == UP && draft != null && !closed;
	}

	public void contact(String emergedAt, int world, int size, int raidClock, int deathsSoFar) {
		if (!challengeMode || closed) {
			return;
		}
		ensureSample();
		if (sample.getEmergedAt().isEmpty()) {
			sample.setEmergedAt(emergedAt == null ? "" : emergedAt);
			sample.setWorld(world);
			sample.setRaidClockEmerge(raidClock);
			deathBaseline = Math.max(0, deathsSoFar);
			if (size > 0) {
				sample.setTeamSize(size);
			}
		}
	}

	public boolean needsContact() {
		return challengeMode && !closed && (sample == null || sample.getEmergedAt().isEmpty());
	}

	public void emerge(String style, int x, int y) {
		if (!challengeMode || closed || style == null) {
			return;
		}
		ensureSample();
		int hole = holes.add(x, y);
		if (phase != UP) {
			if (draft != null && draft.getDurationTicks() >= 0) {
				if (draft.getDigTick() >= 0 && !draft.isKilled() && !draft.isInterrupted()) {
					draft.setGapTicks(tick - draft.getDigTick());
				}
				commitDraft();
			}
			draft = new VanguardUptime();
			draft.setIndex(sample.getUptimes().size() + 1);
			phase = UP;
			uptimeStart = tick;
			pendingKill = false;
		}
		setHole(style, hole);
	}

	public void seen(String style, int ratio, int scale) {
		if (draft == null || style == null || scale <= 0) {
			return;
		}
		setHealth(style, ratio, scale);
	}

	public void dig(String style, int x, int y, int ratio, int scale, int animation) {
		if (!challengeMode || closed || phase != UP || draft == null || style == null) {
			return;
		}
		pendingKill = false;
		if (scale > 0) {
			setHealth(style, ratio, scale);
		}
		if (draft.getAnimation() < 0) {
			draft.setAnimation(animation);
		}
		if ("melee".equals(style) && draft.getMeleeHole() >= 0
			&& holes.distance(draft.getMeleeHole(), x, y) > VanguardHoles.SAME_HOLE) {
			draft.setMeleeOffHole(true);
		}
		if (draft.getDigTick() < 0) {
			draft.setDigTick(tick);
			draft.setDurationTicks(tick - uptimeStart);
			phase = DOWN;
		}
	}

	/** A combat form disappeared. The next tick treats it as a kill unless a dig arrives first. */
	public void combatGone() {
		if (!challengeMode || closed || phase != UP || draft == null || draft.getDigTick() >= 0) {
			return;
		}
		pendingKill = true;
		pendingKillDuration = tick - uptimeStart;
	}

	public void heal() {
		if (draft != null && (phase == UP || phase == DOWN)) {
			draft.setHeal(true);
		}
	}

	public void noteDeaths(int raidDeaths) {
		if (sample == null || deathBaseline < 0) {
			return;
		}
		sample.setDeaths(Math.max(0, raidDeaths - deathBaseline));
	}

	public void tick() {
		tick++;
		if (pendingKill && phase == UP && draft != null && draft.getDigTick() < 0) {
			draft.setKilled(true);
			draft.setDurationTicks(pendingKillDuration);
			phase = DOWN;
		}
		pendingKill = false;
	}

	public VanguardSample finish(boolean finished, int raidClock) {
		if (sample == null) {
			return null;
		}
		if (!closed) {
			if (phase == UP && draft != null) {
				if (draft.getDurationTicks() < 0) {
					draft.setDurationTicks(tick - uptimeStart);
				}
				if (finished) {
					draft.setKilled(true);
				} else {
					draft.setInterrupted(true);
				}
			}
			if (draft != null && draft.getDurationTicks() >= 0) {
				commitDraft();
			}
			closed = true;
			phase = IDLE;
		}
		if (finished) {
			sample.setFinished(true);
			sample.setRaidClockComplete(raidClock);
		} else if (sample.getRaidClockComplete() < 0) {
			sample.setFinished(false);
			sample.setRaidClockComplete(raidClock);
		}
		applyLetters();
		return sample;
	}

	public VanguardSample confirm(int kc) {
		if (sample == null) {
			return null;
		}
		sample.setKc(kc);
		sample.setConfirmed(true);
		applyLetters();
		return sample;
	}

	/** Drops the open sample. The id is returned so a row already written can be removed. */
	public String discard() {
		String id = sample == null ? null : sample.getId();
		reset();
		return id;
	}

	private void ensureSample() {
		if (sample != null) {
			return;
		}
		sample = new VanguardSample();
		sample.setId(UUID.randomUUID().toString());
		sample.setStartedAt(startedAt);
		sample.setTeamSize(teamSize);
	}

	private void setHole(String style, int hole) {
		if ("melee".equals(style)) {
			draft.setMeleeHole(hole);
		} else if ("ranged".equals(style)) {
			draft.setRangedHole(hole);
		} else if ("magic".equals(style)) {
			draft.setMagicHole(hole);
		}
	}

	private void setHealth(String style, int ratio, int scale) {
		if ("melee".equals(style)) {
			draft.setMeleeRatio(ratio);
			draft.setMeleeScale(scale);
		} else if ("ranged".equals(style)) {
			draft.setRangedRatio(ratio);
			draft.setRangedScale(scale);
		} else if ("magic".equals(style)) {
			draft.setMagicRatio(ratio);
			draft.setMagicScale(scale);
		}
	}

	private void commitDraft() {
		boolean natural = !draft.isKilled() && !draft.isInterrupted();
		boolean shortDig = natural && draft.getDurationTicks() >= 0 && draft.getDurationTicks() < MIN_NATURAL_TICKS;
		draft.setForced(draft.isHeal() || shortDig);
		sample.getUptimes().add(draft);
		draft = null;
	}

	private void applyLetters() {
		for (int i = 0; i < sample.getUptimes().size(); i++) {
			VanguardUptime uptime = sample.getUptimes().get(i);
			uptime.setMelee(holes.letter(uptime.getMeleeHole()));
			uptime.setRanged(holes.letter(uptime.getRangedHole()));
			uptime.setMagic(holes.letter(uptime.getMagicHole()));
		}
	}

	private void reset() {
		startedAt = "";
		teamSize = 0;
		challengeMode = false;
		sample = null;
		draft = null;
		tick = 0;
		phase = IDLE;
		uptimeStart = 0;
		deathBaseline = -1;
		closed = false;
		pendingKill = false;
		pendingKillDuration = 0;
		holes.clear();
	}
}
