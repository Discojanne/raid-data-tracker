package com.raidtracker;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.google.inject.Inject;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidTrackerPanel;
import com.raidtracker.ui.chambers.ChambersFacts;
import com.raidtracker.ui.chambers.TargetTimeSettings;
import com.raidtracker.vanguard.VanguardLog;
import com.raidtracker.vanguard.VanguardNpcs;
import com.raidtracker.vanguard.VanguardSample;
import com.raidtracker.vanguard.VanguardTracker;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.game.ItemManager;
import net.runelite.api.widgets.WidgetUtil;
import com.raidtracker.toapointstracker.pointstracker.PointsTracker;
import com.raidtracker.toapointstracker.module.ComponentManager;
import com.raidtracker.toapointstracker.module.TombsOfAmascutModule;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Integer.parseInt;
import static java.lang.Float.parseFloat;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Raid Data Tracker"
)
public class RaidTrackerPlugin extends Plugin
{
	private static final String LEVEL_COMPLETE_MESSAGE = "complete! Duration:";
	private static final String RAID_COMPLETE_MESSAGE_COX = "Congratulations - your raid is complete!";
	private static final String RAID_COMPLETE_MESSAGE_TOA = "Challenge complete: The Wardens.";
	private static final String DUST_RECIPIENTS = "Dust recipients: ";
	private static final String TWISTED_KIT_RECIPIENTS = "Twisted Kit recipients: ";

    private static final Pattern TOB_TOTAL_COMPLETION_PATTERN = Pattern.compile("Theatre of Blood total completion time: (\\d+:(?:\\d+:)?(?:\\d+\\.)?\\d+)(?:\\. )?.*");
    private static final Pattern TOB_COMPLETION_PATTERN = Pattern.compile(".*Theatre of Blood completion time: (\\d+:(?:\\d+:)?(?:\\d+\\.)?\\d+)(?:\\. )?.*");

	private static final Pattern TOA_ROOM_COMPLETE_PATTERN = Pattern.compile("Challenge complete: ([A-Za-z- ]+).*Duration:.*?(\\d+:\\d+?:?\\d+?\\.?\\d+)(?:\\. )?.*");
	private static final Pattern TOA_COMPLETION_PATTERN = Pattern.compile(".*Tombs of Amascut: (.*) Mode (total|challenge) completion time:.*?(\\d+:\\d+?:?\\d+?\\.?\\d+)\\..*");
	private static final String TOA_EVENT_NAMESPACE = "tombs-of-amascut";
	private static final String TOA_EVENT_NAME_POINTS = "raidCompletedPoints";

    private static final int TOA_CANVAS_WIDGET_ID = 481;
    private static final int TOA_TIMER_WIDGET_ID = 46;

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
	private static final int DEATH_POINT_TICKS = 6;
	private static final int FULL_LAYOUT_PREP_ROOMS = 11;
	private static final String FLOOR_HIGHLIGHT = "@mes_hl_mag@";
	private static final String PLAYER_DEATH = "Oh dear, you are dead!";

	private int olmPhaseStartUnits = -1;
	private int olmPhase = 1;
	private boolean olmMageRecorded;
	private int olmHeadStartUnits = -1;
	private boolean olmFightStarted;
	private String lastPrepRoom = "";
	private boolean pendingDeath;
	private int pendingBaseline;
	private int pendingMin;
	private int pendingTicks;
	private int lastPersonalPoints;
	private boolean chambersLive;
	private int chambersAnchorUnits;
	private int lastLiveSignature = Integer.MIN_VALUE;
	private final VanguardTracker vanguards = new VanguardTracker();
	private final VanguardLog vanguardLog = new VanguardLog();
	private String vanguardKey;
	private String vanguardDirectory;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private RaidTrackerConfig config;

	@Inject
	private ItemManager itemManager;

	@Inject
	private RaidTracker raidTracker;

	public RaidTracker getRaidTracker() {
		return raidTracker;
	}

	@Inject
	private PointsTracker pointsTracker;

	private ComponentManager componentManager = null;

	@Setter
    @Inject
	private FileReadWriter fw;
	private boolean writerStarted = false;
	private boolean raidStarted = false;

	private static final WorldPoint TEMP_LOCATION = new WorldPoint(3360, 5152, 2);

	@Setter
	private RaidTrackerPanel panel;
	private NavigationButton navButton;

	@Inject
	private ConfigManager configManager;

	private TargetTimeSettings targetSettings;

	@Inject
	private PluginManager pluginManager;

    private boolean isFirstGameTick = true;

	@Provides
	RaidTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidTrackerConfig.class);
	}

	@Override
	public void configure(Binder binder)
	{
		binder.install(new TombsOfAmascutModule());
	}

	@Override
	protected void startUp() {
		panel = new RaidTrackerPanel(itemManager, fw, config, clientThread, client, configManager, pluginManager);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel-icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Raid Data Tracker")
				.priority(6)
				.icon(icon)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
		targetSettings = new TargetTimeSettings(config, configManager);
		targetSettings.start();
		targetSettings.forgetTotal();

		if (componentManager == null) {
			componentManager = injector.getInstance(ComponentManager.class);
		}
		componentManager.onPluginStart();

		if (client.getGameState().equals(GameState.LOGGED_IN) || client.getGameState().equals(GameState.LOADING))
		{
			useCurrentLogFolder();
			SwingUtilities.invokeLater(() -> panel.loadRTList());
		}
	}

	@Override
	protected void shutDown() {
		leaveVanguards();
		raidTracker.setInRaidChambers(false);
		if (targetSettings != null) {
			targetSettings.stop();
			targetSettings = null;
		}
		clientToolbar.removeNavigation(navButton);
		reset();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e) {
		if (!e.getGroup().equals(RaidTrackerConfig.CONFIG_GROUP)) {
			return;
		}
		if (targetSettings != null && !targetSettings.isBusy() && !"cmSoloTotal".equals(e.getKey())) {
			targetSettings.forgetTotal();
			targetSettings.refreshTotal();
		}
		panel.loadRTList();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}

		boolean tempInRaid = client.getVarbitValue(Varbits.IN_RAID) == 1;
		boolean tempInTob = client.getVarbitValue(Varbits.THEATRE_OF_BLOOD) > 1;
		boolean tempInToa = client.getVarbitValue(Varbits.TOA_RAID_LEVEL) > 0;

		// if the player's raid state has changed
		if (tempInRaid ^ raidTracker.isInRaidChambers()) {
			// if the player is inside of a raid then check the raid
			if (tempInRaid && raidTracker.isLoggedIn()) {
				checkRaidPresence();
			} else if (!tempInRaid && !raidTracker.isRaidComplete()) {
				leaveVanguards();
				if (chambersLive) {
					chambersLive = false;
					lastLiveSignature = Integer.MIN_VALUE;
					SwingUtilities.invokeLater(() -> panel.clearLiveChambers());
				}
			} else if (raidTracker.isRaidComplete() && !raidTracker.isChestOpened()) {
				//player just exited a raid, if the chest is not looted write the raid tracker anyway.
				//might deprecate the writing after widgetloaded in the future, not decided yet.
				if (writerStarted) {
					return;
				}

				settleChambersDeathRooms(raidTracker);
				fw.writeToFile(raidTracker);

				writerStarted = true;

				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});
			}
		}

		if (tempInTob ^ raidTracker.isInTheatreOfBlood()) {
			if (tempInTob && raidTracker.isLoggedIn()) {
				checkTobPresence();
			} else if (raidTracker.isRaidComplete()) {
				//not tested

				if (writerStarted) {
					return;
				}

				fw.writeToFile(raidTracker);

				writerStarted = true;

				SwingUtilities.invokeLater(() -> {
					panel.addDrop(raidTracker);
					reset();
				});
			}
			else {
				reset();
			}
		}

		if (tempInToa ^ raidTracker.isInTombsOfAmascut()) {

			// Removed else if for ToA
			// No varbits are changed when a player leaves ToA, so the else-if would never fire
			if (tempInToa && raidTracker.isLoggedIn()) {
				checkToaPresence();
			}
			else {
				reset();
			}
		}

		if (raidTracker.isInTombsOfAmascut()) {
			// Tombs of Amascut orb healths 0=hide 1-27=% of health - 27 is 100% health and 1 is 0% health, 30=dead
			if (event.getVarbitId() >= Varbits.TOA_MEMBER_0_HEALTH && event.getVarbitId() <= Varbits.TOA_MEMBER_7_HEALTH && event.getValue() == 30) {
				int toa_member = event.getVarbitId() - Varbits.TOA_MEMBER_0_HEALTH;
				if (toa_member == 0) {
					raidTracker.personalDeathCount++;
				}

				raidTracker.totalTeamDeathCount++;
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			if (event.getGameState() == GameState.LOGGING_IN || client.getGameState() == GameState.LOGGED_IN) {
				SwingUtilities.invokeLater(() -> panel.loadRTList());
			}
			else if (client.getGameState() == GameState.LOGIN_SCREEN
					|| client.getGameState() == GameState.CONNECTION_LOST) {
				leaveVanguards();
				raidTracker.setLoggedIn(false);
				isFirstGameTick = true;
			}
			else if (client.getGameState() == GameState.HOPPING) {
				leaveVanguards();
				isFirstGameTick = true;
				reset();
			}
			return;
		}

		if (event.getGameState() == GameState.LOGGING_IN)
		{
			useCurrentLogFolder();
			SwingUtilities.invokeLater(() -> panel.loadRTList());

		}

		if (client.getGameState() == GameState.LOGGED_IN) {
			// skip event while the game decides if the player belongs in a raid or not
			if (client.getLocalPlayer() == null
					|| client.getLocalPlayer().getWorldLocation().equals(TEMP_LOCATION))
			{
				//noinspection UnnecessaryReturnStatement
				return;
			}
		}
		else if (client.getGameState() == GameState.LOGIN_SCREEN
				|| client.getGameState() == GameState.CONNECTION_LOST)
		{
			leaveVanguards();
			raidTracker.setLoggedIn(false);
            isFirstGameTick = true;
		}
		else if (client.getGameState() == GameState.HOPPING)
		{
			leaveVanguards();
            isFirstGameTick = true;
			reset();
		}
	}

	private void useCurrentLogFolder() {
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		Boolean league = WorldUtils.playerOnLeagueWorld(client);
		if (league == null) {
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) {
				return;
			}
			league = Boolean.FALSE;
		}
		String username = accountName();
		if (username.isEmpty()) {
			return;
		}
		if (username.equals(fw.getUsername()) && league == fw.isLeague()) {
			return;
		}
		fw.updateUsername(username, league);
	}

	/**
	 * The login name is the account folder. A Jagex session can be in game before that name is set,
	 * so the character name is used until the login name appears.
	 */
	private String accountName() {
		String username = client.getUsername();
		if (username != null && !username.isEmpty()) {
			return username;
		}
		if (client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null) {
			return "";
		}
		String name = Text.sanitize(client.getLocalPlayer().getName());
		return name == null ? "" : name;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}

		checkChatMessage(event, raidTracker);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {

        if (client.getLocalPlayer() == null) {
            return;
        }

        if (fw.getUsername() == null || fw.getUsername().isEmpty()) {
            useCurrentLogFolder();
            if (fw.getUsername() != null && !fw.getUsername().isEmpty()) {
                SwingUtilities.invokeLater(() -> panel.loadRTList());
            }
        }

        if (isFirstGameTick) {
            isFirstGameTick = false;
            raidTracker.setProfileType(String.valueOf(RuneScapeProfileType.getCurrent(client)));
            raidTracker.setAccountHash(client.getAccountHash());
            useCurrentLogFolder();
            SwingUtilities.invokeLater(() -> panel.loadRTList());
        }

        if (WorldUtils.playerOnUntrackedWorld(client)) {
            return;
        }

		int WIDGET_TIMER = WidgetUtil.packComponentId(TOA_CANVAS_WIDGET_ID, TOA_TIMER_WIDGET_ID);
		if (raidTracker.isInRaidChambers() && !raidTracker.isRaidComplete()) {
			notePersonalPoints(client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS));
			watchVanguards();
		}
		if (chambersLive && raidTracker.isInRaidChambers() && panel.wantsLiveChambers()) {
			pushLiveChambers();
		}

		if (raidTracker.isInTombsOfAmascut() && client.getWidget(WIDGET_TIMER) != null) {
			if (!Objects.equals(Objects.requireNonNull(client.getWidget(WIDGET_TIMER)).getText(), "00:00")
                    && !Objects.equals(Objects.requireNonNull(client.getWidget(WIDGET_TIMER)).getText(), "0:00.00")
                    && !raidStarted
            ) {
				raidStarted = true;
				raidTracker.setTeamSize(pointsTracker.getTeamSize());
				raidTracker.setRaidLevel(client.getVarbitValue(Varbits.TOA_RAID_LEVEL));
			}
		}
	}

	// Thank Adam, LlemonDuck, and jocopa3 for creating PluginMessage event in core for us
	@Subscribe
	public void onPluginMessage(PluginMessage message) {
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}

		if (message.getNamespace().equals(TOA_EVENT_NAMESPACE)
                && message.getName().equals(TOA_EVENT_NAME_POINTS)
                && (Integer) message.getData().get("version") == 1
        ) {
			log.debug("received PluginMessage from Tombs of Amascut plugin");
			int personalPoints = (Integer) message.getData().get("personalPoints");
			int totalPoints = (Integer) message.getData().get("totalPoints");

			raidTracker.setPersonalPoints(personalPoints);
			raidTracker.setTotalPoints(totalPoints);
			raidTracker.setPercentage(raidTracker.getPersonalPoints() / (raidTracker.getTotalPoints() / 100.0));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}

		ItemContainer rewardItemContainer;
		switch (event.getGroupId()) {
			case (InterfaceID.CHAMBERS_OF_XERIC_REWARD):
				if (raidTracker.isChestOpened() || !raidTracker.isRaidComplete()) {
					return;
				}

				raidTracker.setChestOpened(true);

				rewardItemContainer = client.getItemContainer(InventoryID.CHAMBERS_OF_XERIC_CHEST);

				if (rewardItemContainer == null) {
					return;
				}

				if (writerStarted) {
					//unnecessary return check?
					return;
				}

                raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));

                writerStarted = true;

                settleChambersDeathRooms(raidTracker);
                fw.writeToFile(raidTracker);

                SwingUtilities.invokeLater(() -> {
                    panel.addDrop(raidTracker);
                    reset();
                });

                break;

            case (InterfaceID.TOB_REWARD):
                RaidTracker unclaimedRewardsRT = null;

                if (!raidTracker.isRaidComplete()) {
                    unclaimedRewardsRT = fw.getUnclaimedRewardsRT(raidTracker.getAccountHash(), RaidType.TOB);
                    if (unclaimedRewardsRT != null) {
                        log.info("Found unclaimed rewards RT");
                        raidTracker = unclaimedRewardsRT;
                    }
                }

                if (raidTracker.isChestOpened() || !raidTracker.isRaidComplete()) {
                    return;
                }

                raidTracker.setChestOpened(true);

                rewardItemContainer = client.getItemContainer(InventoryID.THEATRE_OF_BLOOD_CHEST);

                if (rewardItemContainer == null) {
                    return;
                }

                raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));

                writerStarted = true;

                if (unclaimedRewardsRT != null) {
                    raidTracker.setChestOpened(true);
                    fw.updateRTLog(raidTracker, RaidType.TOB);
                } else {
                    fw.writeToFile(raidTracker);
                }

                SwingUtilities.invokeLater(() -> {
                    panel.addDropToPanel(raidTracker);
                    reset();
                });
                break;

            //459 is the mvp screen of TOB
            case (459):
                AtomicReference<String> mvp = new AtomicReference<>("");
                AtomicReference<String> player1 = new AtomicReference<>("");
                AtomicReference<String> player2 = new AtomicReference<>("");
                AtomicReference<String> player3 = new AtomicReference<>("");
                AtomicReference<String> player4 = new AtomicReference<>("");
                AtomicReference<String> player5 = new AtomicReference<>("");
                AtomicInteger deathsPlayer1 = new AtomicInteger();
                AtomicInteger deathsPlayer2 = new AtomicInteger();
                AtomicInteger deathsPlayer3 = new AtomicInteger();
                AtomicInteger deathsPlayer4 = new AtomicInteger();
                AtomicInteger deathsPlayer5 = new AtomicInteger();

                clientThread.invokeLater(() -> {
                    mvp.set(getWidgetText(client.getWidget(459, 14)));
                    player1.set(getWidgetText(client.getWidget(459, 22)));
                    player2.set(getWidgetText(client.getWidget(459, 24)));
                    player3.set(getWidgetText(client.getWidget(459, 26)));
                    player4.set(getWidgetText(client.getWidget(459, 28)));
                    player5.set(getWidgetText(client.getWidget(459, 30)));
                    deathsPlayer1.set(getWidgetNumber(client.getWidget(459, 23)));
                    deathsPlayer2.set(getWidgetNumber(client.getWidget(459, 25)));
                    deathsPlayer3.set(getWidgetNumber(client.getWidget(459, 27)));
                    deathsPlayer4.set(getWidgetNumber(client.getWidget(459, 29)));
                    deathsPlayer5.set(getWidgetNumber(client.getWidget(459, 31)));

                    raidTracker.setMvp(mvp.get());
                    raidTracker.setTobPlayer1(player1.get());
                    raidTracker.setTobPlayer2(player2.get());
                    raidTracker.setTobPlayer3(player3.get());
                    raidTracker.setTobPlayer4(player4.get());
                    raidTracker.setTobPlayer5(player5.get());

                    raidTracker.setTobPlayer1DeathCount(deathsPlayer1.get());
                    raidTracker.setTobPlayer2DeathCount(deathsPlayer2.get());
                    raidTracker.setTobPlayer3DeathCount(deathsPlayer3.get());
                    raidTracker.setTobPlayer4DeathCount(deathsPlayer4.get());
                    raidTracker.setTobPlayer5DeathCount(deathsPlayer5.get());

                    if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
                        raidTracker.setMvpInOwnName(mvp.get().equalsIgnoreCase(client.getLocalPlayer().getName()));
                    }
                });
                break;

            case (InterfaceID.TOA_REWARD):
                if (raidTracker.isChestOpened() || !raidTracker.isRaidComplete()) {
                    return;
                }

                raidTracker.setChestOpened(true);

                rewardItemContainer = client.getItemContainer(InventoryID.TOA_REWARD_CHEST);

                if (rewardItemContainer == null) {
                    return;
                }

                raidTracker.setLootList(lootListFactory(rewardItemContainer.getItems()));

                fw.writeToFile(raidTracker);

                writerStarted = true;

                SwingUtilities.invokeLater(() -> {
                    // Doesn't use reset() due to ToA using a log update function on tomb exit
                    panel.addDrop(raidTracker);
                    writerStarted = false;
                });
                break;
            case (InterfaceID.TOA_PARTY):
                SwingUtilities.invokeLater(() -> panel.showWarningView());
                break;
        }
	}

	private String getWidgetText(Widget widget) {
		if (widget == null) {
			return "";
		}
		else if (widget.getText().equals("-")) {
			return "";
		}
		return widget.getText();
	}

	private int getWidgetNumber(Widget widget) {
		if (widget == null) {
			return 0;
		}
		else if (widget.getText().equals("-")) {
			return 0;
		}
		return Integer.parseInt(widget.getText());
	}

	public void checkChatMessage(ChatMessage event, RaidTracker raidTracker)
	{
		raidTracker.setLoggedIn(true);

		String playerName = "";

		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null) {
			playerName = Text.toJagexName(client.getLocalPlayer().getName());
		}

		if ((raidTracker.isInRaidChambers() || raidTracker.isInTheatreOfBlood() || raidTracker.isInTombsOfAmascut()) &&
			(event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION || event.getType() == ChatMessageType.GAMEMESSAGE)) {
			//unescape java to avoid unicode
			String message = unescapeJavaString(Text.removeTags(event.getMessage())).replaceAll("\u00A0", " ").replace(FLOOR_HIGHLIGHT, "");
            Matcher m;

			// Fixes issue with inconsistent resets due to
			// Varbits.TOA_RAID_LEVEL not resetting when you leave
			if (message.contains("You enter the Tombs of Amascut")) {
				reset();
			}

            // Filter out message with "-" so we don't accidentally consider it specialLoot
            if (message.contains("- patient record")) return;

			if (raidTracker.isInRaidChambers() && !raidTracker.isRaidComplete() && message.contains(PLAYER_DEATH)) {
				recordChambersDeath();
			}

			if (message.contains(LEVEL_COMPLETE_MESSAGE)) {
				String timeString = message.split("complete! Duration: ")[1];

				if (message.startsWith("Upper")) {
					raidTracker.setUpperTime(stringTimeToSeconds(timeString.split(" ")[timeString.split(" ").length - 1]));
				}
				if (message.startsWith("Middle")) {
					raidTracker.setMiddleTime(stringTimeToSeconds(timeString.split(" ")[timeString.split(" ").length - 1]));
				}
				if (message.startsWith("Lower")) {
					raidTracker.setLowerTime(stringTimeToSeconds(timeString.split(" ")[timeString.split(" ").length - 1]));
				}

				if (message.toLowerCase().contains("shamans")) {
					raidTracker.setShamansTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Shamans";
				}

				if (message.toLowerCase().contains("vasa")) {
					raidTracker.setVasaTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Vasa";
				}

				if (message.toLowerCase().contains("vanguards")) {
					raidTracker.setVanguardsTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Vanguards";
					finishVanguards(true);
				}

				if (message.toLowerCase().contains("mystics")) {
					raidTracker.setMysticsTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Mystics";
				}

				if (message.toLowerCase().contains("tekton")) {
					raidTracker.setTektonTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Tekton";
				}

				if (message.toLowerCase().contains("muttadiles")) {
					raidTracker.setMuttadilesTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Muttadiles";
				}

				if (message.toLowerCase().contains("guardians")) {
					raidTracker.setGuardiansTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Guardians";
				}

				if (message.toLowerCase().contains("vespula")) {
					raidTracker.setVespulaTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Vespula";
				}

				if (message.toLowerCase().contains("ice demon")) {
					raidTracker.setIceDemonTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Ice demon";
				}

				if (message.toLowerCase().contains("thieving")) {
					raidTracker.setThievingTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Thieving";
				}

				if (message.toLowerCase().contains("tightrope")) {
					raidTracker.setTightropeTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Tightrope";
				}

				if (message.toLowerCase().contains("crabs")) {
					raidTracker.setCrabsTime(stringTimeToSeconds(timeString.split(" ")[0]));
					lastPrepRoom = "Crabs";
				}
				chambersAnchorUnits = client.getVarbitValue(RAID_TIMER);

			}

			if (message.startsWith(RAID_COMPLETE_MESSAGE_COX) || message.startsWith(RAID_COMPLETE_MESSAGE_TOA)) {
				if (raidTracker.isInRaidChambers()) {
					raidTracker.setTotalPoints(client.getVarbitValue(Varbits.TOTAL_POINTS));
					raidTracker.setPersonalPoints(client.getVarpValue(VarPlayer.RAIDS_PERSONAL_POINTS));
					raidTracker.setTeamSize(client.getVarbitValue(RAID_PARTY_SIZE));
					if (pendingDeath && raidTracker.getPersonalPoints() > 0) {
						absorbDeathPoints(raidTracker.getPersonalPoints());
					}
					pendingDeath = false;
					stampOlmHead(client.getVarbitValue(RAID_TIMER));
				} else if (raidTracker.isInTombsOfAmascut()) {
					raidTracker.setPersonalPoints(pointsTracker.getPersonalTotalPoints());
				}


				raidTracker.setPercentage(raidTracker.getPersonalPoints() / (raidTracker.getTotalPoints() / 100.0));

				// Points being 0 (for any reason) causes the % to be NaN and cause a log writing error
				if (Double.isNaN(raidTracker.getPercentage())) {
					raidTracker.setTotalPoints(-1);
					raidTracker.setPersonalPoints(-1);
					raidTracker.setPercentage(-1);
				}

				// Without TOA party point tracking, the points percentage can't be calculated properly
				if (raidTracker.getPercentage() < 0 && raidTracker.getPercentage() != -1) {
					raidTracker.setPercentage(-1);
				}

				raidTracker.setRaidComplete(true);

				raidTracker.setDate(System.currentTimeMillis());
			}

			//the message after every completed tob wave.
			if (message.toLowerCase().contains("wave '")) {
				String wave = message.toLowerCase().split("'")[1];

				switch (wave) {
					case("the maiden of sugadinti"):
						raidTracker.setMaidenTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("the pestilent bloat"):
						raidTracker.setBloatTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("the nylocas"):
						raidTracker.setNyloTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("sotetseg"):
						raidTracker.setSotetsegTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("xarpus"):
						raidTracker.setXarpusTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split(" total")[0]));
						break;
					case("the final challenge"):
						raidTracker.setVerzikTime(stringTimeToSeconds(message.toLowerCase().split("duration: ")[1].split("theatre")[0]));
						break;
				}
			}

			if (message.contains("Challenge complete") || message.contains("total completion")) {
				if ((m = TOA_ROOM_COMPLETE_PATTERN.matcher(message)).matches()) {
					String room = m.group(1).toLowerCase();
					int duration = stringTimeToSeconds(m.group(2));
					if (room == null || room.isEmpty()) {
						log.warn("Failed to find room {} for completion string {}", m.group(1), event.getMessage());
						return;
					}

					switch (room) {
						case "path of crondis":
							raidTracker.setCrondisTime(duration);
							break;
						case "zebak":
							raidTracker.setZebakTime(duration);
							break;
						case "path of apmeken":
							raidTracker.setApmekenTime(duration);
							break;
						case "ba-ba":
							raidTracker.setBabaTime(duration);
							break;
						case "path of scabaras":
							raidTracker.setScabarasTime(duration);
							break;
						case "kephri":
							raidTracker.setKephriTime(duration);
							break;
						case "path of het":
							raidTracker.setHetTime(duration);
							break;
						case "akkha":
							raidTracker.setAkkhaTime(duration);
							break;
						case "the wardens":
							raidTracker.setWardensTime(duration);
							break;
					}
				}

				if ((m = TOA_COMPLETION_PATTERN.matcher(message)).matches()) {
					int duration = stringTimeToSeconds(m.group(3));
					if (Objects.equals(m.group(2), "challenge"))
					{
						raidTracker.setToaCompTime(duration);
					}
					if (Objects.equals(m.group(2), "total"))
					{
						raidTracker.setRaidTime(duration);
					}
				}
			}

            if ((m = TOB_COMPLETION_PATTERN.matcher(message)).matches()) {
                raidTracker.setTobCompTime(stringTimeToSeconds(m.group(1)));
            }

            if ((m = TOB_TOTAL_COMPLETION_PATTERN.matcher(message)).matches()) {
                raidTracker.setRaidTime(stringTimeToSeconds(m.group(1)));
            }

            if (raidTracker.isRaidComplete() && message.contains("Team size:")) {
				raidTracker.setRaidTime(stringTimeToSeconds(message.split("Duration: ")[1].split(" ")[0]));
			}

			//works for tob
			if (message.contains("count is:")) {
				boolean chambersCount = message.contains("Chambers of Xeric");
				int count = parseInt(message.split("count is:")[1].trim().replace(".", ""));
				raidTracker.setChallengeMode(message.contains("Chambers of Xeric Challenge Mode"));
				raidTracker.setCompletionCount(count);
				if (chambersCount) {
					if (raidTracker.isChallengeMode()) {
						saveVanguard(vanguards.confirm(count));
					} else {
						dropVanguard(vanguards.discard());
					}
				}
				if (raidTracker.isInTheatreOfBlood()) {
					int teamSize = 0;

					for (int i = 6442; i  < 6447; i++) {
						if (client.getVarbitValue(i) != 0) {
							teamSize++;
						}
					}
					raidTracker.setTeamSize(teamSize);
					raidTracker.setRaidComplete(true);
                    raidTracker.setDate(System.currentTimeMillis());
				}
			}

			// Only special loot should contain the " - " (except for the raid complete message in Cox)
			if (raidTracker.isRaidComplete() && message.contains(" - ") && !message.startsWith(RAID_COMPLETE_MESSAGE_COX)) {
				String receiver = message.split(" - ")[0];
				String item = message.split(" - ")[1];
				boolean own = receiver.toLowerCase().trim().equals(playerName.toLowerCase().trim());
				if (raidTracker.isInRaidChambers()) {
					addChambersUnique(item, receiver, own, getItemPrice(itemManager.search(item).get(0)));
				} else if (!raidTracker.getSpecialLootReceiver().isEmpty()) {
					//in case of multiple purples, a new purple is stored on a new line in the file, so a new raidtracker object will be used and written to the file
					RaidTracker altRT = copyData();

					altRT.setSpecialLootReceiver(receiver);
					altRT.setSpecialLoot(item);
					altRT.setSpecialLootInOwnName(own);
					altRT.setSpecialLootValue(getItemPrice(itemManager.search(item).get(0)));

					setSplits(altRT);

					fw.writeToFile(altRT);

					SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
				} else {
					raidTracker.setSpecialLootReceiver(receiver);
					raidTracker.setSpecialLoot(item);
					raidTracker.setSpecialLootValue(getItemPrice(itemManager.search(item).get(0)));
					raidTracker.setSpecialLootInOwnName(own);

					setSplits(raidTracker);
				}
			}

			//for tob & toa it works a bit different, not possible to get duplicates.
			//toa tested, tob untested in game
			if (raidTracker.isRaidComplete() && !raidTracker.isInRaidChambers() && message.toLowerCase().contains("found something special") && !message.toLowerCase().contains("lil' zik") && !message.toLowerCase().contains("tumeken's guardian")) {
				raidTracker.setSpecialLootReceiver(message.split(" found something special: ")[0]);
				raidTracker.setSpecialLoot(message.split(" found something special: ")[1]);

				raidTracker.setSpecialLootValue(getItemPrice(itemManager.search(raidTracker.getSpecialLoot()).get(0)));

				raidTracker.setSpecialLootInOwnName(raidTracker.getSpecialLootReceiver().toLowerCase().trim().equals(playerName.toLowerCase().trim()));

				setSplits(raidTracker);
			}

			if (raidTracker.isRaidComplete() && message.startsWith(TWISTED_KIT_RECIPIENTS)) {
				String[] recipients = message.split(TWISTED_KIT_RECIPIENTS)[1].split(",");

				for (String recip : recipients) {
					String name = recip.trim();
					if (raidTracker.isInRaidChambers()) {
						addChambersUnique("Twisted Kit", name, receivedByYou(name, playerName), null);
					} else if (raidTracker.getKitReceiver().isEmpty()) {
						raidTracker.setKitReceiver(name);
					}
					else {
						RaidTracker altRT = copyData();
						altRT.setKitReceiver(name);

						fw.writeToFile(altRT);

						SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
					}
				}
			}

			if (raidTracker.isRaidComplete() && message.startsWith(DUST_RECIPIENTS)) {
				String[] recipients = message.split(DUST_RECIPIENTS)[1].split(",");

				for (String recip : recipients) {
					String name = recip.trim();
					if (raidTracker.isInRaidChambers()) {
						addChambersUnique("Metamorphic Dust", name, receivedByYou(name, playerName), null);
					} else if (raidTracker.getDustReceiver().isEmpty()) {
						raidTracker.setDustReceiver(name);
					}
					else {
						RaidTracker altRT = copyData();
						altRT.setDustReceiver(name);

						fw.writeToFile(altRT);

						SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
					}
				}
			}

			boolean petMessage = message.toLowerCase().contains("olmlet") || message.toLowerCase().contains("lil' zik")
				|| message.toLowerCase().contains("tumeken's guardian") || message.toLowerCase().contains("would have been followed");
			if (raidTracker.isInRaidChambers() && raidTracker.isRaidComplete() && petMessage) {
				boolean duplicate = message.toLowerCase().contains("would have been followed");
				String receiver = duplicate ? playerName : message.split(" ")[0];
				boolean inOwnName = playerName.equals(message.split(" ")[0]) || duplicate;
				addChambersUnique(petItemName(message), receiver, inOwnName, null);
			} else if (raidTracker.isRaidComplete() && petMessage) {
				boolean inOwnName = false;
				boolean duplicate = message.toLowerCase().contains("would have been followed");

				if (playerName.equals(message.split(" ")[0]) || duplicate)	{
					inOwnName = true;
				}

				if (!raidTracker.getPetReceiver().isEmpty()) {
					RaidTracker altRT = copyData();

					if (duplicate) {
						altRT.setPetReceiver(playerName);
					}
					else {
						altRT.setPetReceiver(message.split(" ")[0]);
					}

					altRT.setPetInMyName(inOwnName);

					fw.writeToFile(altRT);

					SwingUtilities.invokeLater(() -> panel.addDrop(altRT, false));
				}
				else {
					if (duplicate) {
						raidTracker.setPetReceiver(playerName);
					}
					else {
						raidTracker.setPetReceiver(message.split(" ")[0]);
					}
					raidTracker.setPetInMyName(inOwnName);
				}
			}
		}
	}

	public void setSplits(RaidTracker raidTracker)
	{

		int lootSplit = raidTracker.getSpecialLootValue() / raidTracker.getTeamSize();

		int cutoff = config.FFACutoff();

		//
		if (raidTracker.getSpecialLoot().length() > 0) {
			if (config.defaultFFA() || lootSplit < cutoff) {
				raidTracker.setFreeForAll(true);
				if (raidTracker.isSpecialLootInOwnName()) {
					raidTracker.setLootSplitReceived(raidTracker.getSpecialLootValue());
				}
			} else if (raidTracker.isSpecialLootInOwnName()) {
				raidTracker.setLootSplitPaid(raidTracker.getSpecialLootValue() - lootSplit);
				raidTracker.setLootSplitReceived(lootSplit);
			} else {
				raidTracker.setLootSplitReceived(lootSplit);
			}
		}
	}

	public ArrayList<RaidTrackerItem> lootListFactory(Item[] items)
	{
		ArrayList<RaidTrackerItem> lootList = new ArrayList<>();
		Arrays.stream(items)
				.filter(item -> item.getId() > -1)
				.forEach(item -> {
					ItemComposition comp = itemManager.getItemComposition(item.getId());
					lootList.add(new RaidTrackerItem() {
						{
							name = comp.getName();
							id = comp.getId();
							quantity = item.getQuantity();
							price = getItemPrice(id) * quantity;
						}
					});
				});
		return lootList;
	}

	private void checkRaidPresence()
	{
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		raidTracker.setInRaidChambers(client.getVarbitValue(Varbits.IN_RAID) == 1);
		if (raidTracker.isInRaidChambers()) {
			chambersLive = true;
			chambersAnchorUnits = 0;
			lastLiveSignature = Integer.MIN_VALUE;
			beginVanguards();
		}
	}

	private void checkTobPresence() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
		//1 = in party outside, 2 = spectating, 3 = dead spectating
		raidTracker.setInTheatreOfBlood(client.getVarbitValue(Varbits.THEATRE_OF_BLOOD) > 1);
	}

	private void checkToaPresence() {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}

		raidTracker.setInTombsOfAmascut(client.getVarbitValue(Varbits.TOA_RAID_LEVEL) > 0);
	}

	private int stringTimeToSeconds(String s)
	{
		String[] split = s.split(":");
		return split.length == 3 ? parseInt(split[0]) * 3600 + parseInt(split[1]) * 60 + Math.round(parseFloat(split[2])) : parseInt(split[0]) * 60 + Math.round(parseFloat(split[1]));
	}

	public RaidTracker copyData() {
		RaidTracker RT = new RaidTracker();
        RT.setAccountHash(raidTracker.getAccountHash());
        RT.setProfileType(raidTracker.getProfileType());
		RT.setDate(raidTracker.getDate());
		RT.setTeamSize(raidTracker.getTeamSize());
		RT.setChallengeMode(raidTracker.isChallengeMode());
		RT.setInTheatreOfBlood(raidTracker.isInTheatreOfBlood());
		RT.setInRaidChambers(raidTracker.isInRaidChambers());
		RT.setCompletionCount(raidTracker.getCompletionCount());
		RT.setKillCountID(raidTracker.getKillCountID());

		return RT;
	}

	private void reset()
	{
		raidTracker = new RaidTracker();
        raidTracker.setAccountHash(client.getAccountHash());
        raidTracker.setProfileType(String.valueOf(RuneScapeProfileType.getCurrent(client)));
		writerStarted = false;
		raidStarted = false;
		resetChambersState();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event) {
		if (WorldUtils.playerOnUntrackedWorld(client)) {
			return;
		}
		NPC npc = event.getNpc();
		if (npc == null) {
			return;
		}
		if (trackingChambersOlm() && isMageHand(npc.getId())) {
			olmPhaseStarted(client.getVarbitValue(RAID_TIMER));
		}
		vanguardSpawned(npc);
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event) {
		if (WorldUtils.playerOnUntrackedWorld(client) || event.getNpc() == null) {
			return;
		}
		NPC npc = event.getNpc();
		String style = VanguardNpcs.style(npc.getId());
		if (style != null) {
			vanguardSpawned(npc);
			return;
		}
		if (npc.getId() == VanguardNpcs.WALKING && event.getOld() != null) {
			String from = VanguardNpcs.style(event.getOld().getId());
			if (from != null) {
				vanguardDig(from, npc);
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event) {
		if (!watchingVanguards() || event.getNpc() == null) {
			return;
		}
		if (VanguardNpcs.style(event.getNpc().getId()) != null) {
			vanguards.combatGone();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event) {
		if (!watchingVanguards() || !(event.getActor() instanceof NPC)) {
			return;
		}
		NPC npc = (NPC) event.getActor();
		if (VanguardNpcs.isVanguard(npc.getId()) && event.getHitsplat() != null
			&& event.getHitsplat().getHitsplatType() == HitsplatID.HEAL) {
			vanguards.heal();
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event) {
		if (WorldUtils.playerOnUntrackedWorld(client) || !trackingChambersOlm()) {
			return;
		}
		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) {
			return;
		}
		int id = ((NPC) actor).getId();
		int timer = client.getVarbitValue(RAID_TIMER);
		if (isMageHand(id)) {
			mageHandDown(timer);
		} else if (isMeleeHand(id)) {
			meleeHandDown(timer);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event) {
		if (WorldUtils.playerOnUntrackedWorld(client) || !trackingChambersOlm()) {
			return;
		}
		if (event.getGameObject() != null && event.getGameObject().getId() == OLM_ENCOUNTER) {
			olmPhaseStarted(client.getVarbitValue(RAID_TIMER));
		}
	}

	private boolean trackingChambersOlm() {
		return raidTracker.isInRaidChambers() && !raidTracker.isRaidComplete();
	}

	private boolean isMageHand(int id) {
		return id == OLM_MAGE_HAND || id == OLM_MAGE_HAND_CM;
	}

	private boolean isMeleeHand(int id) {
		return id == OLM_MELEE_HAND || id == OLM_MELEE_HAND_CM;
	}

	/** Raid-clock split from a mage-hand spawn, or the Olm encounter object. */
	public void olmPhaseStarted(int timerUnits) {
		if (!trackingChambersOlm() || olmPhaseStartUnits >= 0 || olmHeadStartUnits >= 0) {
			return;
		}
		olmPhaseStartUnits = timerUnits;
		olmMageRecorded = false;
		olmFightStarted = true;
	}

	/** Mage hand down. The last phase has no mage-hand split. */
	public void mageHandDown(int timerUnits) {
		if (!trackingChambersOlm() || olmPhaseStartUnits < 0 || olmMageRecorded) {
			return;
		}
		if (olmPhase < expectedOlmPhases()) {
			addOlmSeconds(raidTracker.getOlmMageHandTimes(), timerUnits - olmPhaseStartUnits, true);
		}
		olmMageRecorded = true;
	}

	/** Melee hand down. Starts the head once the last phase ends. */
	public void meleeHandDown(int timerUnits) {
		if (!trackingChambersOlm() || olmPhaseStartUnits < 0) {
			return;
		}
		addOlmSeconds(raidTracker.getOlmPhaseTimes(), timerUnits - olmPhaseStartUnits, false);
		int finished = olmPhase;
		olmPhase++;
		olmPhaseStartUnits = -1;
		olmMageRecorded = false;
		if (finished >= expectedOlmPhases() && olmHeadStartUnits < 0) {
			olmHeadStartUnits = timerUnits;
		}
	}

	public void stampOlmHead(int timerUnits) {
		if (!raidTracker.isInRaidChambers() || olmHeadStartUnits < 0 || raidTracker.getOlmHeadTime() > 0) {
			return;
		}
		int seconds = raidClockSeconds(timerUnits - olmHeadStartUnits);
		if (seconds > 0) {
			raidTracker.setOlmHeadTime(seconds);
		}
	}

	private void addOlmSeconds(ArrayList<Integer> times, int unitDelta, boolean mageHand) {
		int seconds = raidClockSeconds(unitDelta);
		if (seconds <= 0) {
			return;
		}
		ArrayList<Integer> list = times;
		if (list == null) {
			list = new ArrayList<>();
			if (mageHand) {
				raidTracker.setOlmMageHandTimes(list);
			} else {
				raidTracker.setOlmPhaseTimes(list);
			}
		}
		list.add(seconds);
	}

	private int expectedOlmPhases() {
		int size = client.getVarbitValue(RAID_PARTY_SIZE);
		if (size <= 0 || size > 100) {
			size = raidTracker.getTeamSize();
		}
		if (size <= 0 || size > 100) {
			size = 1;
		}
		return 3 + (size / 8);
	}

	/** 100 raid-clock units is one minute. Leftover units are 0.6 seconds. */
	private int raidClockSeconds(int units) {
		if (units <= 0) {
			return 0;
		}
		int minutes = units / 100;
		int seconds = (units % 100) * 6 / 10;
		return minutes * 60 + seconds;
	}

	public void notePersonalPoints(int pointsNow) {
		if (!raidTracker.isInRaidChambers() || raidTracker.isRaidComplete() || pointsNow <= 0) {
			return;
		}
		if (pendingDeath) {
			absorbDeathPoints(pointsNow);
			pendingTicks--;
			if (pendingTicks <= 0) {
				pendingDeath = false;
			}
		}
		lastPersonalPoints = pointsNow;
	}

	private void recordChambersDeath() {
		if (!raidTracker.isInRaidChambers() || raidTracker.isRaidComplete()) {
			return;
		}
		int pointsNow = lastPersonalPoints;
		if (pendingDeath) {
			absorbDeathPoints(pointsNow);
			pendingDeath = false;
		}
		int baseline = lastPersonalPoints > 0 ? lastPersonalPoints : Math.max(0, pointsNow);
		ChambersDeath death = new ChambersDeath();
		death.setRoom(chambersDeathRoom());
		death.setPointsLost(0);
		if (raidTracker.getDeaths() == null) {
			raidTracker.setDeaths(new ArrayList<>());
		}
		raidTracker.getDeaths().add(death);
		pendingDeath = true;
		pendingBaseline = baseline;
		pendingMin = Math.max(0, pointsNow);
		pendingTicks = DEATH_POINT_TICKS;
		absorbDeathPoints(pointsNow);
		vanguards.noteDeaths(raidTracker.getDeaths().size());
	}

	private void absorbDeathPoints(int pointsNow) {
		if (pointsNow > 0 && pointsNow < pendingMin) {
			pendingMin = pointsNow;
		}
		if (pendingBaseline > pendingMin && raidTracker.getDeaths() != null && !raidTracker.getDeaths().isEmpty()) {
			ChambersDeath death = raidTracker.getDeaths().get(raidTracker.getDeaths().size() - 1);
			if (death != null) {
				death.setPointsLost(pendingBaseline - pendingMin);
			}
		}
	}

	/**
	 * Olm once a phase or the head has started.
	 * On the fixed layout, the next room only when nothing sits between it and the room that just finished.
	 */
	private String chambersDeathRoom() {
		if (olmFightStarted || olmHeadStartUnits >= 0) {
			return "Olm";
		}
		if (lastPrepRoom.isEmpty()) {
			return "Tekton";
		}
		if ("Tekton".equals(lastPrepRoom)) {
			return "Crabs";
		}
		if ("Ice demon".equals(lastPrepRoom)) {
			return "Shamans";
		}
		if ("Vanguards".equals(lastPrepRoom)) {
			return "Thieving";
		}
		if ("Guardians".equals(lastPrepRoom)) {
			return "Vasa";
		}
		if ("Mystics".equals(lastPrepRoom)) {
			return "Muttadiles";
		}
		return null;
	}

	public void settleChambersDeathRooms(RaidTracker raidTracker) {
		if (raidTracker == null || raidTracker.isChallengeMode() || prepRoomsLogged(raidTracker) >= FULL_LAYOUT_PREP_ROOMS) {
			return;
		}
		if (raidTracker.getDeaths() == null) {
			return;
		}
		for (int i = 0; i < raidTracker.getDeaths().size(); i++) {
			ChambersDeath death = raidTracker.getDeaths().get(i);
			if (death != null) {
				death.setRoom(null);
			}
		}
	}

	private int prepRoomsLogged(RaidTracker raidTracker) {
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

	private void addChambersUnique(String item, String receiver, boolean own, Integer value) {
		if (item == null || item.isEmpty()) {
			return;
		}
		if (raidTracker.getUniques() == null) {
			raidTracker.setUniques(new ArrayList<>());
		}
		UniqueDrop drop = new UniqueDrop();
		drop.setItem(item);
		drop.setReceiver(receiver == null ? "" : receiver.trim());
		drop.setOwn(own);
		if (value != null) {
			drop.setValue(value);
			applyChambersSplit(drop);
		}
		raidTracker.getUniques().add(drop);
	}

	private void applyChambersSplit(UniqueDrop drop) {
		int price = drop.getValue();
		int team = raidTracker.getTeamSize();
		if (team <= 0) {
			team = 1;
		}
		int lootSplit = price / team;
		int cutoff = config.FFACutoff();
		if (config.defaultFFA() || lootSplit < cutoff) {
			drop.setFreeForAll(true);
			if (drop.isOwn()) {
				drop.setSplitReceived(price);
			}
		} else if (drop.isOwn()) {
			drop.setSplitPaid(price - lootSplit);
			drop.setSplitReceived(lootSplit);
		} else {
			drop.setSplitReceived(lootSplit);
		}
	}

	private boolean receivedByYou(String receiver, String playerName) {
		return receiver != null && playerName != null && receiver.toLowerCase().equals(playerName.toLowerCase().trim());
	}

	private String petItemName(String message) {
		String lower = message.toLowerCase();
		if (lower.contains("lil' zik")) {
			return "Lil' Zik";
		}
		if (lower.contains("tumeken")) {
			return "Tumeken's guardian";
		}
		return "Olmlet";
	}

	private void resetChambersState() {
		olmPhaseStartUnits = -1;
		olmPhase = 1;
		olmMageRecorded = false;
		olmHeadStartUnits = -1;
		olmFightStarted = false;
		lastPrepRoom = "";
		pendingDeath = false;
		pendingBaseline = 0;
		pendingMin = 0;
		pendingTicks = 0;
		lastPersonalPoints = 0;
		chambersLive = false;
		chambersAnchorUnits = 0;
		lastLiveSignature = Integer.MIN_VALUE;
	}

	/** Refresh the Times page from the raid in memory. A closed sidebar and a steady clock do nothing. */
	private void pushLiveChambers() {
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
		if (signature == lastLiveSignature) {
			return;
		}
		lastLiveSignature = signature;
		RaidTracker snap = ChambersFacts.copyTimes(raidTracker);
		int shown = open;
		SwingUtilities.invokeLater(() -> panel.showLiveChambers(snap, shown));
	}

	private int openChambersSeconds() {
		if (!chambersLive || !raidTracker.isInRaidChambers() || raidTracker.isRaidComplete()) {
			return -1;
		}
		int now = client.getVarbitValue(RAID_TIMER);
		int from = chambersAnchorUnits;
		if (olmPhaseStartUnits >= 0) {
			from = olmPhaseStartUnits;
		} else if (olmHeadStartUnits >= 0) {
			from = olmHeadStartUnits;
		}
		int units = now - from;
		if (units < 0) {
			units = 0;
		}
		return raidClockSeconds(units);
	}

	private static int listSignature(java.util.List<Integer> values) {
		int signature = 1;
		if (values == null) {
			return signature;
		}
		for (int i = 0; i < values.size() && i < 8; i++) {
			Integer value = values.get(i);
			signature = 31 * signature + (value == null ? 0 : value);
		}
		return signature;
	}

	//from stackoverflow
	public String unescapeJavaString(String st) {

		if (st == null) {
			return null;
		}

		StringBuilder sb = new StringBuilder(st.length());

		for (int i = 0; i < st.length(); i++) {
			char ch = st.charAt(i);
			if (ch == '\\') {
				char nextChar = (i == st.length() - 1) ? '\\' : st
						.charAt(i + 1);
				// Octal escape?
				if (nextChar >= '0' && nextChar <= '7') {
					String code = "" + nextChar;
					i++;
					if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
							&& st.charAt(i + 1) <= '7') {
						code += st.charAt(i + 1);
						i++;
						if ((i < st.length() - 1) && st.charAt(i + 1) >= '0'
								&& st.charAt(i + 1) <= '7') {
							code += st.charAt(i + 1);
							i++;
						}
					}
					sb.append((char) Integer.parseInt(code, 8));
					continue;
				}
				switch (nextChar) {
					case '\\':
						break;
					case 'b':
						ch = '\b';
						break;
					case 'f':
						ch = '\f';
						break;
					case 'n':
						ch = '\n';
						break;
					case 'r':
						ch = '\r';
						break;
					case 't':
						ch = '\t';
						break;
					case '\"':
						ch = '\"';
						break;
					case '\'':
						ch = '\'';
						break;
					// Hex Unicode: u????
					case 'u':
						if (i >= st.length() - 5) {
							ch = 'u';
							break;
						}
						int code = Integer.parseInt(
								"" + st.charAt(i + 2) + st.charAt(i + 3)
										+ st.charAt(i + 4) + st.charAt(i + 5), 16);
						sb.append(Character.toChars(code));
						i += 5;
						continue;
				}
				i++;
			}
			sb.append(ch);
		}
		return sb.toString();
	}

	public int getItemPrice(ItemPrice itemPrice) {
		long wiki = itemPrice.getWikiPrice();
		long ge = itemPrice.getPrice();
		return toIntPrice(wiki > 0 ? wiki : ge);
	}
	public int getItemPrice(int itemID) {
		long live = itemManager.getItemPrice(itemID);
		long ge = itemManager.getItemComposition(itemID).getPrice();
		return toIntPrice(live > 0 ? live : ge);
	}

	private static int toIntPrice(long price) {
		if (price > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (price < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) price;
	}

	private void beginVanguards() {
		saveVanguard(vanguards.finish(false, raidClock()));
		if (!config.trackVanguards()) {
			vanguards.discard();
			clearVanguardFile();
			return;
		}
		bindVanguardFile();
		if (vanguardKey == null) {
			vanguards.discard();
			return;
		}
		vanguards.startRaid(Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), client.getVarbitValue(RAID_PARTY_SIZE));
		if (client.getVarbitValue(CHALLENGE_MODE) != 0) {
			vanguards.allowChallengeMode();
		}
	}

	private void leaveVanguards() {
		saveVanguard(vanguards.finish(false, raidClock()));
		vanguards.discard();
		clearVanguardFile();
	}

	private void finishVanguards(boolean finished) {
		if (raidTracker.getDeaths() != null) {
			vanguards.noteDeaths(raidTracker.getDeaths().size());
		}
		saveVanguard(vanguards.finish(finished, raidClock()));
	}

	private void watchVanguards() {
		if (!watchingVanguards() || !vanguards.isTiming()) {
			return;
		}
		vanguards.tick();
		if (!vanguards.needsHealth()) {
			return;
		}
		for (NPC npc : client.getNpcs()) {
			String style = VanguardNpcs.style(npc.getId());
			if (style != null && npc.getHealthScale() > 0) {
				vanguards.seen(style, npc.getHealthRatio(), npc.getHealthScale());
			}
		}
	}

	private boolean watchingVanguards() {
		if (!config.trackVanguards() || !raidTracker.isInRaidChambers() || raidTracker.isRaidComplete()) {
			return false;
		}
		if (client.getVarbitValue(CHALLENGE_MODE) != 0) {
			vanguards.allowChallengeMode();
		}
		return vanguards.isChallengeMode();
	}

	private void vanguardSpawned(NPC npc) {
		String style = VanguardNpcs.style(npc.getId());
		if (style == null || !watchingVanguards()) {
			return;
		}
		int[] at = vanguardCenter(npc);
		if (at == null) {
			return;
		}
		if (vanguards.needsContact()) {
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

	private void vanguardDig(String style, NPC npc) {
		if (!watchingVanguards()) {
			return;
		}
		int[] at = vanguardCenter(npc);
		if (at == null) {
			return;
		}
		vanguards.dig(style, at[0], at[1], npc.getHealthRatio(), npc.getHealthScale(), npc.getAnimation());
	}

	private static int[] vanguardCenter(NPC npc) {
		WorldPoint southwest = npc.getWorldLocation();
		if (southwest == null) {
			return null;
		}
		int size = 3;
		if (npc.getComposition() != null && npc.getComposition().getSize() > 0) {
			size = npc.getComposition().getSize();
		}
		int nudge = Math.max(0, (size - 1) / 2);
		return new int[] {southwest.getX() + nudge, southwest.getY() + nudge};
	}

	private void bindVanguardFile() {
		if (vanguardKey != null && vanguardDirectory != null) {
			return;
		}
		long hash = client.getAccountHash();
		String directory = fw.getCoxDirectory();
		if (hash == 0L || hash == -1L || directory == null) {
			return;
		}
		String key = Long.toUnsignedString(hash);
		if (fw.isLeague()) {
			key = key + FileReadWriter.LEAGUE_SUFFIX;
		}
		vanguardKey = key;
		vanguardDirectory = directory;
	}

	private void clearVanguardFile() {
		vanguardKey = null;
		vanguardDirectory = null;
	}

	private void saveVanguard(VanguardSample sample) {
		if (sample == null) {
			return;
		}
		bindVanguardFile();
		if (vanguardKey == null || vanguardDirectory == null) {
			return;
		}
		try {
			vanguardLog.saveLater(vanguardDirectory, vanguardKey, sample);
			log.info("Logged Chambers vanguards world {} kc {} uptimes {}", sample.getWorld(), sample.getKc(), sample.getUptimes().size());
		} catch (IOException ex) {
			log.warn("Could not write the vanguard log", ex);
		}
	}

	private void dropVanguard(String id) {
		if (id == null) {
			return;
		}
		bindVanguardFile();
		if (vanguardKey == null || vanguardDirectory == null) {
			return;
		}
		try {
			vanguardLog.removeLater(vanguardDirectory, vanguardKey, id);
		} catch (IOException ex) {
			log.warn("Could not update the vanguard log", ex);
		}
	}

	private int raidClock() {
		return client.getVarbitValue(RAID_TIMER);
	}

    public void updateCurrentRT(RaidType raidType) {
		if (writerStarted) {
			return;
		}

		writerStarted = true;

		fw.updateRTLog(raidTracker, raidType);

		SwingUtilities.invokeLater(() -> {
			panel.updateView(true);
            reset();
		});
	}

}
