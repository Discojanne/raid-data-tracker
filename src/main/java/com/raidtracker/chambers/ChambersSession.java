package com.raidtracker.chambers;

import com.raidtracker.ChambersDeath;
import com.raidtracker.RaidTracker;
import com.raidtracker.RaidTrackerConfig;
import com.raidtracker.UniqueDrop;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidTrackerPanel;
import com.raidtracker.ui.chambers.ChambersFacts;
import com.raidtracker.vanguard.VanguardLog;
import com.raidtracker.vanguard.VanguardNpcs;
import com.raidtracker.vanguard.VanguardSample;
import com.raidtracker.vanguard.VanguardTracker;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;

/**
 * Live Chambers state. Theatre and Tombs stay on the plugin.
 * Shared chat lines stay there too and call in for the Chambers side.
 */
@Slf4j
public final class ChambersSession
{
	private static final int RAID_PARTY_SIZE = 5424;
	/** Chambers raid clock. 100 units is one minute. Chat has no Olm phase duration. */
	private static final int RAID_TIMER = 6386;
	/** Challenge Mode. The same varbit CoXGrind reads before it will record a Vanguard room. */
	private static final int CHALLENGE_MODE = 6385;
	private static final int OLM_ENCOUNTER = 29881;
	private static final int OLM_MAGE_HAND = 7550;
	private static final int OLM_MAGE_HAND_CM = 7553;
	private static final int OLM_MELEE_HAND = 7552;
	private static final int OLM_MELEE_HAND_CM = 7555;
	/** Your deaths in the current Chambers raid. It climbs when you die, including after the kick outside. */
	public static final int RAID_DEATH_COUNT = 10055;
	private static final int FULL_LAYOUT_PREP_ROOMS = 11;
	/**
	 * A death this soon after the room-complete chat still belongs to that room.
	 * The completion line can land first when you die as the room ends.
	 */
	private static final long DEATH_ROOM_GRACE_MILLIS = 5000L;
	/** Challenge Mode order. A death is named as the room you were in, which is the next one after the last split. */
	private static final String[] FIXED_ROOMS = {
		"Tekton",
		"Crabs",
		"Ice demon",
		"Shamans",
		"Vanguards",
		"Thieving",
		"Vespula",
		"Tightrope",
		"Guardians",
		"Vasa",
		"Mystics",
		"Muttadiles"
	};

	private final Client client;
	private final RaidTrackerConfig config;
	private final Supplier<RaidTracker> raids;
	private final Supplier<FileReadWriter> writer;
	private final Supplier<RaidTrackerPanel> panel;

	private int olmPhaseStartUnits = -1;
	private int olmPhase = 1;
	private boolean olmMageRecorded;
	private int olmHeadStartUnits = -1;
	private boolean olmFightStarted;
	private String lastPrepRoom = "";
	private long lastPrepFinishedAt;
	/** Tests pin the wall clock used for the death-room grace. Null follows the real clock. */
	private Long nowOverride;
	private boolean pendingDeath;
	private int pendingBaseline;
	private int pendingMin;
	private int lastPersonalPoints;
	/** A points sample after the death. The next death line is a new death, not the same one. */
	private boolean sawPointsSinceDeath;
	/** Stays set across a death kick-out, until the raid is reset. */
	private boolean tracking;
	private boolean chambersLive;
	private int chambersAnchorUnits;
	private int lastLiveSignature = Integer.MIN_VALUE;
	private final VanguardTracker vanguards = new VanguardTracker();
	private final VanguardLog vanguardLog = new VanguardLog();
	private String vanguardKey;
	private String vanguardDirectory;

	public ChambersSession(
		Client client,
		RaidTrackerConfig config,
		Supplier<RaidTracker> raids,
		Supplier<FileReadWriter> writer,
		Supplier<RaidTrackerPanel> panel)
	{
		this.client = client;
		this.config = config;
		this.raids = raids;
		this.writer = writer;
		this.panel = panel;
	}

	private RaidTracker raid()
	{
		return raids.get();
	}

	public boolean isLive()
	{
		return chambersLive;
	}

	/** True from raid entry until reset, including the time spent outside after a death. */
	public boolean isTracking()
	{
		return tracking;
	}

	/** Chambers raid entry. The anchor starts at 0 until a room or floor line lands. */
	public void enteredRaid()
	{
		tracking = true;
		chambersLive = true;
		chambersAnchorUnits = 0;
		lastLiveSignature = Integer.MIN_VALUE;
		beginVanguards();
	}

	/**
	 * Left before the raid was complete. Nothing is written.
	 * Olm and death state stay until {@link #resetChambersState()}, which is what the plugin already did.
	 */
	public void leftBeforeComplete()
	{
		leaveVanguards();
		if (!chambersLive)
		{
			return;
		}
		chambersLive = false;
		lastLiveSignature = Integer.MIN_VALUE;
		SwingUtilities.invokeLater(() -> panel.get().clearLiveChambers());
	}

	public int partySize()
	{
		return client.getVarbitValue(RAID_PARTY_SIZE);
	}

	public int raidClock()
	{
		return client.getVarbitValue(RAID_TIMER);
	}

	/** Chambers-only. A floor or room duration moves the live anchor. */
	public void noteRoomClock()
	{
		chambersAnchorUnits = raidClock();
	}

	/** Chambers-only. Death rooms use the prep room that just finished. */
	public void prepRoomFinished(String room)
	{
		lastPrepRoom = room == null ? "" : room;
		lastPrepFinishedAt = nowMillis();
	}

	/** Tests pin the wall clock so a death can land inside or outside the room grace. */
	public void setNowMillis(Long millis)
	{
		nowOverride = millis;
	}

	private long nowMillis()
	{
		return nowOverride != null ? nowOverride.longValue() : System.currentTimeMillis();
	}

	/** Chambers side of the shared kill-count line. A regular count drops the study room. */
	public void onChambersKillCount(boolean challengeMode, int count)
	{
		if (challengeMode)
		{
			saveVanguard(vanguards.confirm(count));
		}
		else
		{
			dropVanguard(vanguards.discard());
		}
	}

	/** Chambers side of the shared raid-complete line. Points lost are closed, then the head is stamped. */
	public void finishDeathWatch(int personalPoints)
	{
		if (pendingDeath && pointsLostNow() == 0 && personalPoints > 0)
		{
			absorbDeathPoints(personalPoints);
		}
		pendingDeath = false;
	}

	/** Mage hand spawn, from {@code onNpcSpawned}. */
	public void npcSpawned(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		if (trackingChambersOlm() && isMageHand(npc.getId()))
		{
			olmPhaseStarted(raidClock());
		}
		vanguardSpawned(npc);
	}

	/** Vanguard form change or dig, from {@code onNpcChanged}. */
	public void npcChanged(NPC npc, NPCComposition old)
	{
		if (npc == null)
		{
			return;
		}
		String style = VanguardNpcs.style(npc.getId());
		if (style != null)
		{
			vanguardSpawned(npc);
			return;
		}
		if (npc.getId() == VanguardNpcs.WALKING && old != null)
		{
			String from = VanguardNpcs.style(old.getId());
			if (from != null)
			{
				vanguardDig(from, npc);
			}
		}
	}

	/** A combat form left, from {@code onNpcDespawned}. */
	public void npcDespawned(NPC npc)
	{
		if (!watchingVanguards() || npc == null)
		{
			return;
		}
		if (VanguardNpcs.style(npc.getId()) != null)
		{
			vanguards.combatGone();
		}
	}

	/** A heal splat on a Vanguard, from {@code onHitsplatApplied}. */
	public void hitsplatApplied(Actor actor, Hitsplat hitsplat)
	{
		if (!watchingVanguards() || !(actor instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) actor;
		if (VanguardNpcs.isVanguard(npc.getId()) && hitsplat != null
			&& hitsplat.getHitsplatType() == HitsplatID.HEAL)
		{
			vanguards.heal();
		}
	}

	/** Olm hands, from {@code onActorDeath}. */
	public void actorDied(Actor actor)
	{
		if (!trackingChambersOlm() || !(actor instanceof NPC))
		{
			return;
		}
		int id = ((NPC) actor).getId();
		int timer = raidClock();
		if (isMageHand(id))
		{
			mageHandDown(timer);
		}
		else if (isMeleeHand(id))
		{
			meleeHandDown(timer);
		}
	}

	/** Olm encounter object, from {@code onGameObjectSpawned}. */
	public void olmObjectSpawned(int objectId)
	{
		if (!trackingChambersOlm())
		{
			return;
		}
		if (objectId == OLM_ENCOUNTER)
		{
			olmPhaseStarted(raidClock());
		}
	}

	public boolean trackingChambersOlm()
	{
		return raid().isInRaidChambers() && !raid().isRaidComplete();
	}

	public boolean isMageHand(int id)
	{
		return id == OLM_MAGE_HAND || id == OLM_MAGE_HAND_CM;
	}

	public boolean isMeleeHand(int id)
	{
		return id == OLM_MELEE_HAND || id == OLM_MELEE_HAND_CM;
	}

	/** Raid-clock split from a mage-hand spawn, or the Olm encounter object. */
	public void olmPhaseStarted(int timerUnits)
	{
		if (!trackingChambersOlm() || olmPhaseStartUnits >= 0 || olmHeadStartUnits >= 0)
		{
			return;
		}
		olmPhaseStartUnits = timerUnits;
		olmMageRecorded = false;
		olmFightStarted = true;
	}

	/** Mage hand down. The last phase has no mage-hand split. */
	public void mageHandDown(int timerUnits)
	{
		if (!trackingChambersOlm() || olmPhaseStartUnits < 0 || olmMageRecorded)
		{
			return;
		}
		if (olmPhase < expectedOlmPhases())
		{
			addOlmSeconds(raid().getOlmMageHandTimes(), timerUnits - olmPhaseStartUnits, true);
		}
		olmMageRecorded = true;
	}

	/** Melee hand down. Starts the head once the last phase ends. */
	public void meleeHandDown(int timerUnits)
	{
		if (!trackingChambersOlm() || olmPhaseStartUnits < 0)
		{
			return;
		}
		addOlmSeconds(raid().getOlmPhaseTimes(), timerUnits - olmPhaseStartUnits, false);
		int finished = olmPhase;
		olmPhase++;
		olmPhaseStartUnits = -1;
		olmMageRecorded = false;
		if (finished >= expectedOlmPhases() && olmHeadStartUnits < 0)
		{
			olmHeadStartUnits = timerUnits;
		}
	}

	public void stampOlmHead(int timerUnits)
	{
		RaidTracker raidTracker = raid();
		if (!raidTracker.isInRaidChambers() || olmHeadStartUnits < 0 || raidTracker.getOlmHeadTime() > 0)
		{
			return;
		}
		int seconds = raidClockSeconds(timerUnits - olmHeadStartUnits);
		if (seconds > 0)
		{
			raidTracker.setOlmHeadTime(seconds);
		}
	}

	private void addOlmSeconds(ArrayList<Integer> times, int unitDelta, boolean mageHand)
	{
		int seconds = raidClockSeconds(unitDelta);
		if (seconds <= 0)
		{
			return;
		}
		ArrayList<Integer> list = times;
		if (list == null)
		{
			list = new ArrayList<>();
			if (mageHand)
			{
				raid().setOlmMageHandTimes(list);
			}
			else
			{
				raid().setOlmPhaseTimes(list);
			}
		}
		list.add(seconds);
	}

	private int expectedOlmPhases()
	{
		int size = client.getVarbitValue(RAID_PARTY_SIZE);
		if (size <= 0 || size > 100)
		{
			size = raid().getTeamSize();
		}
		if (size <= 0 || size > 100)
		{
			size = 1;
		}
		return 3 + (size / 8);
	}

	/** 100 raid-clock units is one minute. Leftover units are 0.6 seconds. */
	private int raidClockSeconds(int units)
	{
		if (units <= 0)
		{
			return 0;
		}
		int minutes = units / 100;
		int seconds = (units % 100) * 6 / 10;
		return minutes * 60 + seconds;
	}

	public void notePersonalPoints(int pointsNow)
	{
		RaidTracker raidTracker = raid();
		if (raidTracker.isRaidComplete() || pointsNow <= 0)
		{
			return;
		}
		if (!raidTracker.isInRaidChambers() && !tracking)
		{
			return;
		}
		if (pendingDeath)
		{
			// An unchanged sample is still this death. The next chat line is another death once the points change.
			if (pointsNow != lastPersonalPoints)
			{
				sawPointsSinceDeath = true;
			}
			if (pointsLostNow() > 0 && pointsNow > pendingMin)
			{
				pendingDeath = false;
			}
		}
		if (!pendingDeath && lastPersonalPoints > 0 && pointsNow < lastPersonalPoints)
		{
			openDeath(lastPersonalPoints, lastPersonalPoints - pointsNow);
		}
		lastPersonalPoints = pointsNow;
	}

	/**
	 * Chambers death count from varbit {@code 10055}. One climb is one of your deaths.
	 * The chat line and this count are the same death until the personal points change.
	 */
	public void noteRaidDeaths(int count)
	{
		RaidTracker raidTracker = raid();
		if (raidTracker.isRaidComplete() || count < 1 || count > 30)
		{
			return;
		}
		if (!raidTracker.isInRaidChambers() && !tracking)
		{
			return;
		}
		int have = raidTracker.getDeaths() == null ? 0 : raidTracker.getDeaths().size();
		while (have < count)
		{
			if (pendingDeath && !sawPointsSinceDeath)
			{
				break;
			}
			if (pendingDeath)
			{
				pendingDeath = false;
			}
			openDeath(lastPersonalPoints, fortyPercent(lastPersonalPoints));
			have++;
		}
	}

	/**
	 * Chambers-only. The death line is the player's own.
	 * A death keeps 60% of the personal points, so the loss is 40% of the score before it falls.
	 * If the live score has already fallen, the loss is the drop. That equals {@code score / 0.6 - score}.
	 */
	public void recordChambersDeath(int pointsNow)
	{
		RaidTracker raidTracker = raid();
		if (raidTracker.isRaidComplete())
		{
			return;
		}
		if (!raidTracker.isInRaidChambers() && !tracking)
		{
			return;
		}
		int current = pointsNow > 0 ? pointsNow : lastPersonalPoints;
		if (pendingDeath && !sawPointsSinceDeath)
		{
			if (pointsLostNow() == 0)
			{
				setLatestPointsLost(pointsLostFrom(current));
			}
			return;
		}
		if (pendingDeath)
		{
			pendingDeath = false;
		}
		int baseline = lastPersonalPoints > current && current > 0 ? lastPersonalPoints : Math.max(current, 0);
		openDeath(baseline, pointsLostFrom(current));
	}

	public void recordChambersDeath()
	{
		recordChambersDeath(0);
	}

	private int pointsLostNow()
	{
		RaidTracker raidTracker = raid();
		if (raidTracker.getDeaths() == null || raidTracker.getDeaths().isEmpty())
		{
			return 0;
		}
		ChambersDeath death = raidTracker.getDeaths().get(raidTracker.getDeaths().size() - 1);
		return death == null ? 0 : death.getPointsLost();
	}

	private void openDeath(int baseline, int pointsLost)
	{
		RaidTracker raidTracker = raid();
		ChambersDeath death = new ChambersDeath();
		death.setRoom(chambersDeathRoom());
		death.setPointsLost(Math.max(0, pointsLost));
		if (raidTracker.getDeaths() == null)
		{
			raidTracker.setDeaths(new ArrayList<>());
		}
		raidTracker.getDeaths().add(death);
		pendingDeath = true;
		sawPointsSinceDeath = false;
		pendingBaseline = Math.max(0, baseline);
		pendingMin = pendingBaseline;
		vanguards.noteDeaths(raidTracker.getDeaths().size());
	}

	/** 40% of the score, or the drop when the live score has already fallen. */
	private int pointsLostFrom(int current)
	{
		if (lastPersonalPoints > current && current > 0)
		{
			return lastPersonalPoints - current;
		}
		return fortyPercent(current > 0 ? current : lastPersonalPoints);
	}

	private int fortyPercent(int points)
	{
		if (points <= 0)
		{
			return 0;
		}
		return (int) Math.round(points * 0.4);
	}

	private void setLatestPointsLost(int pointsLost)
	{
		RaidTracker raidTracker = raid();
		if (raidTracker.getDeaths() == null || raidTracker.getDeaths().isEmpty())
		{
			return;
		}
		ChambersDeath death = raidTracker.getDeaths().get(raidTracker.getDeaths().size() - 1);
		if (death != null)
		{
			death.setPointsLost(Math.max(0, pointsLost));
		}
	}

	private void absorbDeathPoints(int pointsNow)
	{
		RaidTracker raidTracker = raid();
		if (pointsNow > 0 && pointsNow < pendingMin)
		{
			pendingMin = pointsNow;
		}
		if (pendingBaseline > pendingMin && raidTracker.getDeaths() != null && !raidTracker.getDeaths().isEmpty())
		{
			ChambersDeath death = raidTracker.getDeaths().get(raidTracker.getDeaths().size() - 1);
			if (death != null)
			{
				death.setPointsLost(pendingBaseline - pendingMin);
			}
		}
	}

	/**
	 * Olm once a phase or the head has started.
	 * A death under 5 seconds after a room finishes still belongs to that room.
	 * Otherwise the next room in the fixed order. After Muttadiles, Olm has not started yet, so the room stays unset.
	 */
	private String chambersDeathRoom()
	{
		if (olmFightStarted || olmHeadStartUnits >= 0)
		{
			return "Olm";
		}
		if (deathStillInFinishedRoom())
		{
			return lastPrepRoom;
		}
		if (lastPrepRoom.isEmpty())
		{
			return FIXED_ROOMS[0];
		}
		for (int i = 0; i < FIXED_ROOMS.length - 1; i++)
		{
			if (FIXED_ROOMS[i].equals(lastPrepRoom))
			{
				return FIXED_ROOMS[i + 1];
			}
		}
		return null;
	}

	private boolean deathStillInFinishedRoom()
	{
		if (lastPrepRoom.isEmpty() || lastPrepFinishedAt <= 0)
		{
			return false;
		}
		long elapsed = nowMillis() - lastPrepFinishedAt;
		return elapsed >= 0 && elapsed < DEATH_ROOM_GRACE_MILLIS;
	}

	public void settleChambersDeathRooms(RaidTracker raidTracker)
	{
		if (raidTracker == null || raidTracker.isChallengeMode() || prepRoomsLogged(raidTracker) >= FULL_LAYOUT_PREP_ROOMS)
		{
			return;
		}
		if (raidTracker.getDeaths() == null)
		{
			return;
		}
		for (int i = 0; i < raidTracker.getDeaths().size(); i++)
		{
			ChambersDeath death = raidTracker.getDeaths().get(i);
			if (death != null)
			{
				death.setRoom(null);
			}
		}
	}

	private int prepRoomsLogged(RaidTracker raidTracker)
	{
		int count = 0;
		if (raidTracker.getTektonTime() > 0) count++;
		if (raidTracker.getCrabsTime() > 0) count++;
		if (raidTracker.getIceDemonTime() > 0) count++;
		if (raidTracker.getShamansTime() > 0) count++;
		if (raidTracker.getVanguardsTime() > 0) count++;
		if (raidTracker.getThievingTime() > 0) count++;
		if (raidTracker.getVespulaTime() > 0) count++;
		if (raidTracker.getTightropeTime() > 0) count++;
		if (raidTracker.getGuardiansTime() > 0) count++;
		if (raidTracker.getVasaTime() > 0) count++;
		if (raidTracker.getMysticsTime() > 0) count++;
		if (raidTracker.getMuttadilesTime() > 0) count++;
		return count;
	}

	/** Chambers side of the shared loot, kit, dust, and pet lines. One raid keeps one line. */
	public void addChambersUnique(String item, String receiver, boolean own, Integer value)
	{
		RaidTracker raidTracker = raid();
		if (item == null || item.isEmpty())
		{
			return;
		}
		if (raidTracker.getUniques() == null)
		{
			raidTracker.setUniques(new ArrayList<>());
		}
		UniqueDrop drop = new UniqueDrop();
		drop.setItem(item);
		drop.setReceiver(receiver == null ? "" : receiver.trim());
		drop.setOwn(own);
		if (value != null)
		{
			drop.setValue(value);
			applyChambersSplit(drop);
		}
		raidTracker.getUniques().add(drop);
	}

	private void applyChambersSplit(UniqueDrop drop)
	{
		int price = drop.getValue();
		int team = raid().getTeamSize();
		if (team <= 0)
		{
			team = 1;
		}
		int lootSplit = price / team;
		int cutoff = config.FFACutoff();
		if (config.defaultFFA() || lootSplit < cutoff)
		{
			drop.setFreeForAll(true);
			if (drop.isOwn())
			{
				drop.setSplitReceived(price);
			}
		}
		else if (drop.isOwn())
		{
			drop.setSplitPaid(price - lootSplit);
			drop.setSplitReceived(lootSplit);
		}
		else
		{
			drop.setSplitReceived(lootSplit);
		}
	}

	public boolean receivedByYou(String receiver, String playerName)
	{
		return receiver != null && playerName != null && receiver.toLowerCase().equals(playerName.toLowerCase().trim());
	}

	public String petItemName(String message)
	{
		String lower = message.toLowerCase();
		if (lower.contains("lil' zik"))
		{
			return "Lil' Zik";
		}
		if (lower.contains("tumeken"))
		{
			return "Tumeken's guardian";
		}
		return "Olmlet";
	}

	public void resetChambersState()
	{
		olmPhaseStartUnits = -1;
		olmPhase = 1;
		olmMageRecorded = false;
		olmHeadStartUnits = -1;
		olmFightStarted = false;
		lastPrepRoom = "";
		lastPrepFinishedAt = 0;
		pendingDeath = false;
		pendingBaseline = 0;
		pendingMin = 0;
		lastPersonalPoints = 0;
		sawPointsSinceDeath = false;
		tracking = false;
		chambersLive = false;
		chambersAnchorUnits = 0;
		lastLiveSignature = Integer.MIN_VALUE;
	}

	/** Refresh the Times page from the raid in memory. A closed sidebar and a steady clock do nothing. */
	public void pushLiveChambers()
	{
		if (!chambersLive)
		{
			return;
		}
		int generation = panel.get().liveGeneration();
		RaidTracker raidTracker = raid();
		if (!chambersLive || raidTracker == null || !raidTracker.isInRaidChambers() || generation != panel.get().liveGeneration())
		{
			return;
		}
		int open = openChambersSeconds();
		int signature = open;
		signature = 31 * signature + raidTracker.getRaidTime();
		signature = 31 * signature + raidTracker.getLowerTime();
		signature = 31 * signature + raidTracker.getOlmHeadTime();
		signature = 31 * signature + raidTracker.getPersonalPoints();
		signature = 31 * signature + raidTracker.getTektonTime();
		signature = 31 * signature + raidTracker.getCrabsTime();
		signature = 31 * signature + raidTracker.getIceDemonTime();
		signature = 31 * signature + raidTracker.getShamansTime();
		signature = 31 * signature + raidTracker.getVanguardsTime();
		signature = 31 * signature + raidTracker.getThievingTime();
		signature = 31 * signature + raidTracker.getVespulaTime();
		signature = 31 * signature + raidTracker.getTightropeTime();
		signature = 31 * signature + raidTracker.getGuardiansTime();
		signature = 31 * signature + raidTracker.getVasaTime();
		signature = 31 * signature + raidTracker.getMysticsTime();
		signature = 31 * signature + raidTracker.getMuttadilesTime();
		signature = 31 * signature + listSignature(raidTracker.getOlmMageHandTimes());
		signature = 31 * signature + listSignature(raidTracker.getOlmPhaseTimes());
		signature = 31 * signature + ChambersFacts.deathSignature(raidTracker);
		if (signature == lastLiveSignature)
		{
			return;
		}
		if (!chambersLive || generation != panel.get().liveGeneration())
		{
			return;
		}
		lastLiveSignature = signature;
		RaidTracker snap = ChambersFacts.copyTimes(raidTracker);
		int shown = open;
		SwingUtilities.invokeLater(() -> panel.get().showLiveChambers(snap, shown, generation));
	}

	private int openChambersSeconds()
	{
		RaidTracker raidTracker = raid();
		if (!chambersLive || !raidTracker.isInRaidChambers() || raidTracker.isRaidComplete())
		{
			return -1;
		}
		int now = raidClock();
		int from = chambersAnchorUnits;
		if (olmPhaseStartUnits >= 0)
		{
			from = olmPhaseStartUnits;
		}
		else if (olmHeadStartUnits >= 0)
		{
			from = olmHeadStartUnits;
		}
		int units = now - from;
		if (units < 0)
		{
			units = 0;
		}
		return raidClockSeconds(units);
	}

	private static int listSignature(List<Integer> values)
	{
		int signature = 1;
		if (values == null)
		{
			return signature;
		}
		for (int i = 0; i < values.size() && i < 8; i++)
		{
			Integer value = values.get(i);
			signature = 31 * signature + (value == null ? 0 : value);
		}
		return signature;
	}

	private void beginVanguards()
	{
		saveVanguard(vanguards.finish(false, raidClock()));
		if (!config.trackVanguards())
		{
			vanguards.discard();
			clearVanguardFile();
			return;
		}
		bindVanguardFile();
		if (vanguardKey == null)
		{
			vanguards.discard();
			return;
		}
		vanguards.startRaid(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), client.getVarbitValue(RAID_PARTY_SIZE));
		if (client.getVarbitValue(CHALLENGE_MODE) != 0)
		{
			vanguards.allowChallengeMode();
		}
	}

	public void leaveVanguards()
	{
		saveVanguard(vanguards.finish(false, raidClock()));
		vanguards.discard();
		clearVanguardFile();
	}

	/** Chambers-only. The Vanguards room line closes the study sample. */
	public void finishVanguards(boolean finished)
	{
		RaidTracker raidTracker = raid();
		if (raidTracker.getDeaths() != null)
		{
			vanguards.noteDeaths(raidTracker.getDeaths().size());
		}
		saveVanguard(vanguards.finish(finished, raidClock()));
	}

	public void watchVanguards()
	{
		if (!watchingVanguards() || !vanguards.isTiming())
		{
			return;
		}
		vanguards.tick();
		if (!vanguards.needsHealth())
		{
			return;
		}
		for (NPC npc : client.getNpcs())
		{
			String style = VanguardNpcs.style(npc.getId());
			if (style != null && npc.getHealthScale() > 0)
			{
				vanguards.seen(style, npc.getHealthRatio(), npc.getHealthScale());
			}
		}
	}

	private boolean watchingVanguards()
	{
		RaidTracker raidTracker = raid();
		if (!config.trackVanguards() || !raidTracker.isInRaidChambers() || raidTracker.isRaidComplete())
		{
			return false;
		}
		if (client.getVarbitValue(CHALLENGE_MODE) != 0)
		{
			vanguards.allowChallengeMode();
		}
		return vanguards.isChallengeMode();
	}

	private void vanguardSpawned(NPC npc)
	{
		String style = VanguardNpcs.style(npc.getId());
		if (style == null || !watchingVanguards())
		{
			return;
		}
		int[] at = vanguardCenter(npc);
		if (at == null)
		{
			return;
		}
		if (vanguards.needsContact())
		{
			RaidTracker raidTracker = raid();
			int deaths = raidTracker.getDeaths() == null ? 0 : raidTracker.getDeaths().size();
			vanguards.contact(
				Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
				client.getWorld(),
				client.getVarbitValue(RAID_PARTY_SIZE),
				raidClock(),
				deaths);
		}
		vanguards.emerge(style, at[0], at[1]);
	}

	private void vanguardDig(String style, NPC npc)
	{
		if (!watchingVanguards())
		{
			return;
		}
		int[] at = vanguardCenter(npc);
		if (at == null)
		{
			return;
		}
		vanguards.dig(style, at[0], at[1], npc.getHealthRatio(), npc.getHealthScale(), npc.getAnimation());
	}

	private static int[] vanguardCenter(NPC npc)
	{
		WorldPoint southwest = npc.getWorldLocation();
		if (southwest == null)
		{
			return null;
		}
		int size = 3;
		if (npc.getComposition() != null && npc.getComposition().getSize() > 0)
		{
			size = npc.getComposition().getSize();
		}
		int nudge = Math.max(0, (size - 1) / 2);
		return new int[] {southwest.getX() + nudge, southwest.getY() + nudge};
	}

	private void bindVanguardFile()
	{
		if (vanguardKey != null && vanguardDirectory != null)
		{
			return;
		}
		long hash = client.getAccountHash();
		FileReadWriter fw = writer.get();
		String directory = fw.getCoxDirectory();
		if (hash == 0L || hash == -1L || directory == null)
		{
			return;
		}
		String key = Long.toUnsignedString(hash);
		if (fw.isLeague())
		{
			key = key + FileReadWriter.LEAGUE_SUFFIX;
		}
		vanguardKey = key;
		vanguardDirectory = directory;
	}

	private void clearVanguardFile()
	{
		vanguardKey = null;
		vanguardDirectory = null;
	}

	private void saveVanguard(VanguardSample sample)
	{
		if (sample == null)
		{
			return;
		}
		bindVanguardFile();
		if (vanguardKey == null || vanguardDirectory == null)
		{
			return;
		}
		try
		{
			vanguardLog.saveLater(vanguardDirectory, vanguardKey, sample);
			log.info("Logged Chambers vanguards world {} kc {} uptimes {}", sample.getWorld(), sample.getKc(), sample.getUptimes().size());
		}
		catch (IOException ex)
		{
			log.warn("Could not write the vanguard log", ex);
		}
	}

	private void dropVanguard(String id)
	{
		if (id == null)
		{
			return;
		}
		bindVanguardFile();
		if (vanguardKey == null || vanguardDirectory == null)
		{
			return;
		}
		try
		{
			vanguardLog.removeLater(vanguardDirectory, vanguardKey, id);
		}
		catch (IOException ex)
		{
			log.warn("Could not update the vanguard log", ex);
		}
	}
}
