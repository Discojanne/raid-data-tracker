package com.raidtracker.vanguard;

/**
 * One time the Vanguards were above ground. The file stores hole letters, not tiles.
 */
public class VanguardUptime {
	private int index;
	private int meleeHole = -1;
	private int rangedHole = -1;
	private int magicHole = -1;
	private String melee = "";
	private String ranged = "";
	private String magic = "";
	private int durationTicks = -1;
	private Integer gapTicks;
	private int meleeRatio;
	private int meleeScale;
	private int rangedRatio;
	private int rangedScale;
	private int magicRatio;
	private int magicScale;
	private boolean heal;
	private boolean forced;
	private boolean meleeOffHole;
	private int animation = -1;
	private boolean killed;
	private boolean interrupted;
	private int digTick = -1;

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public int getMeleeHole() {
		return meleeHole;
	}

	public void setMeleeHole(int meleeHole) {
		this.meleeHole = meleeHole;
	}

	public int getRangedHole() {
		return rangedHole;
	}

	public void setRangedHole(int rangedHole) {
		this.rangedHole = rangedHole;
	}

	public int getMagicHole() {
		return magicHole;
	}

	public void setMagicHole(int magicHole) {
		this.magicHole = magicHole;
	}

	public String getMelee() {
		return melee;
	}

	public void setMelee(String melee) {
		this.melee = melee;
	}

	public String getRanged() {
		return ranged;
	}

	public void setRanged(String ranged) {
		this.ranged = ranged;
	}

	public String getMagic() {
		return magic;
	}

	public void setMagic(String magic) {
		this.magic = magic;
	}

	public int getDurationTicks() {
		return durationTicks;
	}

	public void setDurationTicks(int durationTicks) {
		this.durationTicks = durationTicks;
	}

	public Integer getGapTicks() {
		return gapTicks;
	}

	public void setGapTicks(Integer gapTicks) {
		this.gapTicks = gapTicks;
	}

	public int getMeleeRatio() {
		return meleeRatio;
	}

	public void setMeleeRatio(int meleeRatio) {
		this.meleeRatio = meleeRatio;
	}

	public int getMeleeScale() {
		return meleeScale;
	}

	public void setMeleeScale(int meleeScale) {
		this.meleeScale = meleeScale;
	}

	public int getRangedRatio() {
		return rangedRatio;
	}

	public void setRangedRatio(int rangedRatio) {
		this.rangedRatio = rangedRatio;
	}

	public int getRangedScale() {
		return rangedScale;
	}

	public void setRangedScale(int rangedScale) {
		this.rangedScale = rangedScale;
	}

	public int getMagicRatio() {
		return magicRatio;
	}

	public void setMagicRatio(int magicRatio) {
		this.magicRatio = magicRatio;
	}

	public int getMagicScale() {
		return magicScale;
	}

	public void setMagicScale(int magicScale) {
		this.magicScale = magicScale;
	}

	public boolean isHeal() {
		return heal;
	}

	public void setHeal(boolean heal) {
		this.heal = heal;
	}

	public boolean isForced() {
		return forced;
	}

	public void setForced(boolean forced) {
		this.forced = forced;
	}

	public boolean isMeleeOffHole() {
		return meleeOffHole;
	}

	public void setMeleeOffHole(boolean meleeOffHole) {
		this.meleeOffHole = meleeOffHole;
	}

	public int getAnimation() {
		return animation;
	}

	public void setAnimation(int animation) {
		this.animation = animation;
	}

	public boolean isKilled() {
		return killed;
	}

	public void setKilled(boolean killed) {
		this.killed = killed;
	}

	public boolean isInterrupted() {
		return interrupted;
	}

	public void setInterrupted(boolean interrupted) {
		this.interrupted = interrupted;
	}

	public int getDigTick() {
		return digTick;
	}

	public void setDigTick(int digTick) {
		this.digTick = digTick;
	}
}
