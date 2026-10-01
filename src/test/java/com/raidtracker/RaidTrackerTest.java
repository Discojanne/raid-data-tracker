package com.raidtracker;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.raidtracker.filereadwriter.FileReadWriter;
import com.raidtracker.ui.RaidTrackerPanel;
import junit.framework.TestCase;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.http.api.item.ItemPrice;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import com.google.inject.testing.fieldbinder.Bind;
import com.google.inject.testing.fieldbinder.BoundFieldModule;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RaidTrackerTest extends TestCase
{

    @Mock
    @Bind
    private Client client;

    @Mock
    @Bind
    private ConfigManager configManager;

    @Mock
    @Bind
    private ItemManager itemManager;

    @Mock
    @Bind
    private ClientToolbar clientToolbar;

    @Mock
    @Bind
    private RaidTrackerConfig raidTrackerConfig;

    @Mock
    @Bind
    private PluginManager pluginManager;

    @Inject
    private RaidTrackerPlugin raidTrackerPlugin;

    @Before
    public void setUp() {
        Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);
    }

	@Test
	public void TestLootSplits() {
		//TODO: double purples
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setRaidComplete(true);

		List<ItemPrice> kodaiTestList = new ArrayList<>();

		ItemPrice kodaiTest = new ItemPrice();

		kodaiTest.setId(0);
		kodaiTest.setName("Kodai Insignia");
		kodaiTest.setPrice(50505050);

		kodaiTestList.add(kodaiTest);

		Player player = mock(Player.class);

		RaidTrackerPanel panel = mock(RaidTrackerPanel.class);
		raidTrackerPlugin.setPanel(panel);

		FileReadWriter fw = mock(FileReadWriter.class);
		raidTrackerPlugin.setFw(fw);

		when(itemManager.search(anyString())).thenReturn(kodaiTestList);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getName()).thenReturn("Canvasba");
		when(raidTrackerConfig.FFACutoff()).thenReturn(1000000);

		raidTracker.setTeamSize(3);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "K1NG\u00a0DK - Kodai insignia",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

        assertEquals("K1NG DK", raidTracker.getSpecialLootReceiver());

		raidTrackerPlugin.setSplits(raidTracker);

		assertTrue(raidTracker.getLootSplitReceived() > -1);
		assertEquals(-1, raidTracker.lootSplitPaid);


		raidTracker.setSpecialLootReceiver("Canvasba");
		raidTracker.setSpecialLootInOwnName(true);
		raidTrackerPlugin.setSplits(raidTracker);

		assertTrue(raidTracker.getLootSplitReceived() > -1);
		assertTrue(raidTracker.getLootSplitPaid() > -1);

		assertFalse(raidTracker.isFreeForAll());

		//check ffa for below 1m split
		raidTracker.setSpecialLootValue(2000000);
		raidTracker.setLootSplitPaid(-1);
		raidTrackerPlugin.setSplits(raidTracker);

		assertTrue(raidTracker.isFreeForAll());
		assertEquals(2000000, raidTracker.getLootSplitReceived());
		assertEquals(-1, raidTracker.getLootSplitPaid());

		raidTracker.setSpecialLootReceiver("K1NG DK");
		raidTracker.setSpecialLootInOwnName(false);

		raidTracker.setLootSplitReceived(-1);
		raidTrackerPlugin.setSplits(raidTracker);

		assertEquals(-1, raidTracker.getLootSplitReceived());
	}

	//---------------------------------- onChatMessage tests ------------------------------------------------
	@Test
	public void TestRaidComplete()
	{
		RaidTracker raidTracker = new RaidTracker();

		when(client.getVarbitValue(anyInt())).thenReturn(5); //random integer, I chose 5
		raidTracker.setInRaidChambers(true);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Congratulations - your raid is complete! Team size: 15 Players Duration: 50:26 Personal best: 31:12",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertTrue(raidTracker.isRaidComplete());
	}

	@Test
	public void TestCoxDuration()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setRaidComplete(true);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Congratulations - your raid is complete! Team size: Solo Duration: 1:40:26 Personal best: 31:12",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(6026, raidTracker.getRaidTime());

		message.setMessage("Congratulations - your raid is complete! Team size: 11-15 Players Duration: 50:26.6 Personal best: 31:12");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(3027, raidTracker.getRaidTime());

		//dey0 case
		message.setMessage("Middle level complete! Duration: 7:53 Total: 20:50");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(1250, raidTracker.getMiddleTime());

		//regular case
		message.setMessage("Middle level complete! Duration: 20:50");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(1250, raidTracker.getMiddleTime());

		message.setMessage("Combat room 'Vanguards' complete! Duration: 3:19 Total: 16:16");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(199, raidTracker.getVanguardsTime());


	}

    @Test
    public void TestTobDuration()
    {
        RaidTracker raidTracker = new RaidTracker();
        raidTracker.setInTheatreOfBlood(true);
        raidTracker.setRaidComplete(true);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Theatre of Blood total completion time: 1:16:07. Personal best: 19:53",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

        assertEquals(4567, raidTracker.getRaidTime());

        message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Theatre of Blood total completion time: 49:00.60 (new personal best)",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

        assertEquals(2941, raidTracker.getRaidTime());

        message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>5:47.40</col><br>Theatre of Blood completion time: <col=ff0000>21:49.80</col> (new personal best)",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

        assertEquals(347, raidTracker.getVerzikTime());

        message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>5:47.40</col><br>Theatre of Blood completion time: <col=ff0000>21:49.80</col> (new personal best)",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

        assertEquals(1310, raidTracker.getTobCompTime());

    }

	@Test
	public void TestToaDuration()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInTombsOfAmascut(true);
		raidTracker.setRaidComplete(true);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Challenge complete: Path of Crondis. Duration: 0:34.20. Total: 0:34.20",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(34, raidTracker.getCrondisTime());

        message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Challenge complete: The Wardens. Duration: 10:15.60Tombs of Amascut: Expert Mode challenge completion time: 34:07.20. Personal best: 28:16.20",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(616, raidTracker.getWardensTime());
		assertEquals(2047, raidTracker.getToaCompTime());

        message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Tombs of Amascut: Expert Mode total completion time: 36:51.00. Personal best: 30:57.00",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(2211, raidTracker.getRaidTime());

	}

	@Test
	public void TestPurple()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setRaidComplete(true);

		List<ItemPrice> kodaiTestList = new ArrayList<>();

		ItemPrice kodaiTest = new ItemPrice();

		kodaiTest.setId(0);
		kodaiTest.setName("Kodai Insignia");
		kodaiTest.setPrice(505050);

		kodaiTestList.add(kodaiTest);

		when(itemManager.search(anyString())).thenReturn(kodaiTestList);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "K1NG\u00A0DK - Kodai insignia",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals("K1NG DK", raidTracker.getSpecialLootReceiver());
		assertEquals("Kodai insignia", raidTracker.getSpecialLoot());
		assertEquals(505050, raidTracker.getSpecialLootValue());
	}

	@Test
	public void TestTobPurple()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setRaidComplete(true);
		raidTracker.setInTheatreOfBlood(true);

		List<ItemPrice> avernicTestList = new ArrayList<>();

		ItemPrice avernicTest = new ItemPrice();

		avernicTest.setId(0);
		avernicTest.setName("Avernic defender hilt");
		avernicTest.setPrice(50505050);

		avernicTestList.add(avernicTest);

		when(itemManager.search(anyString())).thenReturn(avernicTestList);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Canvasba found something special: Avernic defender hilt",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

        message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Canvasba found something special: Lil\\u0027 Zik",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals("Canvasba", raidTracker.getSpecialLootReceiver());
		assertEquals("Avernic defender hilt", raidTracker.getSpecialLoot());
		assertFalse(raidTracker.petReceiver.isEmpty());
		assertEquals("Canvasba", raidTracker.getPetReceiver());
		assertEquals(50505050, raidTracker.getSpecialLootValue());
	}

	@Test
	public void TestToaPurple()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setRaidComplete(true);
		raidTracker.setInTombsOfAmascut(true);

		List<ItemPrice> lightbearerTestList = new ArrayList<>();

		ItemPrice lightbearerTest = new ItemPrice();

		lightbearerTest.setId(0);
		lightbearerTest.setName("Lightbearer");
		lightbearerTest.setPrice(50505050);

		lightbearerTestList.add(lightbearerTest);

		when(itemManager.search(anyString())).thenReturn(lightbearerTestList);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Canvasba found something special: Lightbearer",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

        message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Canvasba found something special: Tumeken\\u0027s guardian",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals("Canvasba", raidTracker.getSpecialLootReceiver());
		assertEquals("Lightbearer", raidTracker.getSpecialLoot());
		assertFalse(raidTracker.petReceiver.isEmpty());
		assertEquals(50505050, raidTracker.getSpecialLootValue());
	}

	@Test
	public void TestPetBeforeRaidCompleteIsIgnored()
	{
		RaidTracker tombs = new RaidTracker();
		tombs.setInTombsOfAmascut(true);

		ChatMessage message = new ChatMessage(
			null,
			ChatMessageType.GAMEMESSAGE,
			"",
			"Canvasba would have been followed by Tumeken's guardian",
			"",
			0);
		raidTrackerPlugin.checkChatMessage(message, tombs);

		assertTrue(tombs.petReceiver.isEmpty());
		assertTrue(tombs.getUniques().isEmpty());

		RaidTracker chambers = new RaidTracker();
		chambers.setInRaidChambers(true);
		raidTrackerPlugin.checkChatMessage(message, chambers);

		assertTrue(chambers.petReceiver.isEmpty());
		assertTrue(chambers.getUniques().isEmpty());
	}

	@Test
	public void TestDustAndKitRecipients()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setRaidComplete(true);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.FRIENDSCHATNOTIFICATION,
            "",
            "Dust recipients: Canvasba",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		message.setMessage("Twisted Kit recipients: BallerTom");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals("Canvasba", raidTracker.getDustReceiver());
		assertEquals("BallerTom", raidTracker.getKitReceiver());
	}

	@Test
	public void TestChallengeModeAndCompletionCount()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setRaidComplete(true);

        ChatMessage message = new ChatMessage(
            null,
            ChatMessageType.GAMEMESSAGE,
            "",
            "Your completed Chambers of Xeric Challenge Mode count is: 57.",
            "",
            0);
        raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertTrue(raidTracker.isChallengeMode());
		assertEquals(57, raidTracker.getCompletionCount());

		message.setMessage("Your completed Chambers of Xeric count is: 443");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(443, raidTracker.getCompletionCount());
	}


	//---------------------------------- onWidgetLoaded tests ------------------------------------------------


	@Test
	public void TestLootListFactory()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setRaidComplete(true);


		//------------------- general case - 2 stacks of regular items --------------------------
		Item[] items = new Item[2];

		items[0] = new Item(1, 500);
		items[1] = new Item(2, 600);

		ItemComposition comp1 = mock(ItemComposition.class);
		ItemComposition comp2 = mock(ItemComposition.class);

		when(itemManager.getItemComposition(1)).thenReturn(comp1);
		when(itemManager.getItemComposition(2)).thenReturn(comp2);

		when(comp1.getName()).thenReturn("Pure Essence");
		when(comp1.getId()).thenReturn(1);
		when(comp1.getPrice()).thenReturn(5);

		when(comp2.getName()).thenReturn("Teak Planks");
		when(comp2.getId()).thenReturn(2);
		when(comp2.getPrice()).thenReturn(255);

		ArrayList<RaidTrackerItem> lootList = raidTrackerPlugin.lootListFactory(items);

		assertEquals(2, lootList.size());
		assertEquals(2500, lootList.get(0).getPrice());
		assertEquals(153000, lootList.get(1).getPrice());

		//----------------------------------------- purple ----------------------------------------
		items = new Item[1];
		items[0] = new Item(3, 1);

		ItemComposition comp3 = mock(ItemComposition.class);
		when(itemManager.getItemComposition(3)).thenReturn(comp3);

		when(comp3.getName()).thenReturn("Twisted Bow");
		when(comp3.getId()).thenReturn(3);
		when(comp3.getPrice()).thenReturn(1198653000);

		lootList = raidTrackerPlugin.lootListFactory(items);

		assertEquals(1, lootList.size());
		assertEquals(1198653000, lootList.get(0).getPrice());


	}

	@Test
	public void TestGuardiansUsesTheRoomDuration()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);

		ChatMessage message = new ChatMessage(
			null,
			ChatMessageType.GAMEMESSAGE,
			"",
			"Combat room 'Guardians' complete! Duration: 1:47 Total: 16:16",
			"",
			0);
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(107, raidTracker.getGuardiansTime());
		assertEquals(-1, raidTracker.getRaidTime());
	}

	@Test
	public void TestFloorLineStripsHighlightAndKeepsTheLastTime()
	{
		RaidTracker raidTracker = new RaidTracker();
		raidTracker.setInRaidChambers(true);

		ChatMessage message = new ChatMessage(
			null,
			ChatMessageType.GAMEMESSAGE,
			"",
			"@mes_hl_mag@Middle level complete! Duration: 7:53 Total: 20:50",
			"",
			0);
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(1250, raidTracker.getMiddleTime());

		message.setMessage("@mes_hl_mag@Lower level complete! Duration: </col><col=ff0000>20:06</col>");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		assertEquals(1206, raidTracker.getLowerTime());
		assertEquals(-1, raidTracker.getUpperTime());
		assertEquals(-1, raidTracker.getRaidTime());
	}

	@Test
	public void TestOlmSplitsUseTheRaidClock()
	{
		RaidTracker raidTracker = raidTrackerPlugin.getRaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setTeamSize(1);

		raidTrackerPlugin.olmPhaseStarted(450);
		raidTrackerPlugin.mageHandDown(500);
		raidTrackerPlugin.meleeHandDown(560);
		raidTrackerPlugin.olmPhaseStarted(570);
		raidTrackerPlugin.mageHandDown(620);
		raidTrackerPlugin.meleeHandDown(700);
		raidTrackerPlugin.olmPhaseStarted(710);
		raidTrackerPlugin.mageHandDown(760);
		raidTrackerPlugin.meleeHandDown(800);
		raidTrackerPlugin.stampOlmHead(900);

		assertEquals(2, raidTracker.getOlmMageHandTimes().size());
		assertEquals(30, raidTracker.getOlmMageHandTimes().get(0).intValue());
		assertEquals(30, raidTracker.getOlmMageHandTimes().get(1).intValue());
		assertEquals(3, raidTracker.getOlmPhaseTimes().size());
		assertEquals(66, raidTracker.getOlmPhaseTimes().get(0).intValue());
		assertEquals(78, raidTracker.getOlmPhaseTimes().get(1).intValue());
		assertEquals(54, raidTracker.getOlmPhaseTimes().get(2).intValue());
		assertEquals(60, raidTracker.getOlmHeadTime());
		assertEquals(-1, raidTracker.getRaidTime());
		assertEquals(-1, raidTracker.getUpperTime());
		assertEquals(-1, raidTracker.getMiddleTime());
		assertEquals(-1, raidTracker.getLowerTime());
		assertEquals(0, raidTracker.getPersonalDeathCount());
	}

	@Test
	public void TestChambersDeathRowKeepsRoomAndPoints()
	{
		RaidTracker raidTracker = raidTrackerPlugin.getRaidTracker();
		raidTracker.setInRaidChambers(true);

		raidTrackerPlugin.notePersonalPoints(40000);
		ChatMessage message = new ChatMessage(
			null,
			ChatMessageType.GAMEMESSAGE,
			"",
			"Oh dear, you are dead!",
			"",
			0);
		raidTrackerPlugin.checkChatMessage(message, raidTracker);
		raidTrackerPlugin.notePersonalPoints(32000);

		assertEquals(1, raidTracker.getDeaths().size());
		assertEquals("Tekton", raidTracker.getDeaths().get(0).getRoom());
		assertEquals(8000, raidTracker.getDeaths().get(0).getPointsLost());
		assertEquals(0, raidTracker.getPersonalDeathCount());

		message.setMessage("Combat room 'Tekton' complete! Duration: 1:08");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);
		message.setMessage("Oh dear, you are dead!");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);
		raidTrackerPlugin.notePersonalPoints(25000);

		assertEquals("Crabs", raidTracker.getDeaths().get(1).getRoom());
		assertEquals(7000, raidTracker.getDeaths().get(1).getPointsLost());

		raidTracker.setChallengeMode(true);
		raidTrackerPlugin.settleChambersDeathRooms(raidTracker);
		assertEquals("Tekton", raidTracker.getDeaths().get(0).getRoom());
		assertEquals("Crabs", raidTracker.getDeaths().get(1).getRoom());

		raidTracker.setChallengeMode(false);
		raidTrackerPlugin.settleChambersDeathRooms(raidTracker);
		assertNull(raidTracker.getDeaths().get(0).getRoom());
		assertNull(raidTracker.getDeaths().get(1).getRoom());
		assertEquals(8000, raidTracker.getDeaths().get(0).getPointsLost());
		assertEquals(7000, raidTracker.getDeaths().get(1).getPointsLost());
		assertEquals(0, raidTracker.getPersonalDeathCount());

		Gson gson = new Gson();
		FileReadWriter writer = new FileReadWriter();
		writer.setGson(gson);
		String json = writer.getJSONString(raidTracker, gson, new JsonParser());
		assertFalse(json.contains("\"room\""));
		assertTrue(json.contains("\"pointsLost\":8000"));
		assertFalse(json.contains("\"personalDeathCount\""));
	}

	@Test
	public void TestSecondChambersDropLandsInCoxLogWithItsOwnPrice()
	{
		RaidTracker raidTracker = raidTrackerPlugin.getRaidTracker();
		raidTracker.setInRaidChambers(true);
		raidTracker.setRaidComplete(true);
		raidTracker.setTeamSize(3);

		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		when(player.getName()).thenReturn("Canvasba");
		when(itemManager.search(anyString())).thenAnswer(invocation -> {
			ItemPrice price = new ItemPrice();
			String name = invocation.getArgument(0);
			price.setName(name);
			price.setPrice(name.toLowerCase().contains("twisted") ? 999 : 111);
			List<ItemPrice> prices = new ArrayList<>();
			prices.add(price);
			return prices;
		});

		RaidTrackerPanel panel = mock(RaidTrackerPanel.class);
		raidTrackerPlugin.setPanel(panel);
		FileReadWriter fw = mock(FileReadWriter.class);
		raidTrackerPlugin.setFw(fw);

		ChatMessage message = new ChatMessage(
			null,
			ChatMessageType.FRIENDSCHATNOTIFICATION,
			"",
			"Canvasba - Kodai insignia",
			"",
			0);
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		message.setMessage("Someone Else - Twisted bow");
		raidTrackerPlugin.checkChatMessage(message, raidTracker);

		verify(fw, never()).writeToFile(any());

		assertEquals(2, raidTracker.getUniques().size());
		assertEquals("Kodai insignia", raidTracker.getUniques().get(0).getItem());
		assertEquals(111, raidTracker.getUniques().get(0).getValue().intValue());
		assertEquals("Canvasba", raidTracker.getUniques().get(0).getReceiver());
		assertTrue(raidTracker.getUniques().get(0).isOwn());
		assertEquals("Twisted bow", raidTracker.getUniques().get(1).getItem());
		assertEquals(999, raidTracker.getUniques().get(1).getValue().intValue());
		assertEquals("Someone Else", raidTracker.getUniques().get(1).getReceiver());
		assertFalse(raidTracker.getUniques().get(1).isOwn());
		assertTrue(raidTracker.getSpecialLoot().isEmpty());
		assertEquals(0, raidTracker.getPersonalDeathCount());
	}

	@Test
	public void TestNewFieldsRoundTripAndOldLinesStillLoad()
	{
		RaidTracker raid = new RaidTracker();
		raid.setInRaidChambers(true);
		raid.setTektonTime(68);
		raid.setGuardiansTime(107);
		raid.setOlmHeadTime(60);
		raid.getOlmMageHandTimes().add(30);
		raid.getOlmPhaseTimes().add(66);
		UniqueDrop drop = new UniqueDrop();
		drop.setItem("Twisted bow");
		drop.setReceiver("Someone Else");
		drop.setValue(999);
		raid.getUniques().add(drop);
		ChambersDeath death = new ChambersDeath();
		death.setRoom("Tekton");
		death.setPointsLost(8000);
		raid.getDeaths().add(death);

		Gson gson = new Gson();
		FileReadWriter writer = new FileReadWriter();
		writer.setGson(gson);
		String json = writer.getJSONString(raid, gson, new JsonParser());
		assertFalse(json.contains("\"accountHash\""));
		assertFalse(json.contains("\"profileType\""));
		assertFalse(json.contains("\"chestOpened\""));
		assertFalse(json.contains("\"raidComplete\""));
		assertFalse(json.contains("\"loggedIn\""));
		assertFalse(json.contains("\"inRaidChambers\""));
		assertFalse(json.contains("\"inTheatreOfBlood\""));
		assertFalse(json.contains("\"inTombsOfAmascut\""));
		assertFalse(json.contains("\"raidLevel\""));
		assertFalse(json.contains("\"percentage\""));
		assertFalse(json.contains("\"maidenTime\""));
		assertFalse(json.contains("\"tobCompTime\""));
		assertFalse(json.contains("\"apmekenTime\""));
		assertFalse(json.contains("\"toaCompTime\""));
		assertFalse(json.contains("\"totalTeamDeathCount\""));
		assertFalse(json.contains("\"personalDeathCount\""));
		assertFalse(json.contains("\"chambersDeaths\""));
		assertTrue(json.contains("\"deaths\""));
		assertTrue(json.contains("\"completionCount\""));
		assertTrue(json.contains("\"killCountID\""));
		assertTrue(json.contains("\"uniques\""));
		assertFalse(json.contains("\"specialLoot\""));
		assertFalse(json.contains("\"kitReceiver\""));
		assertFalse(json.contains("\"dustReceiver\""));
		assertFalse(json.contains("\"petReceiver\""));
		assertFalse(json.contains("\"extraDrops\""));

		RaidTracker deathless = new RaidTracker();
		deathless.setInRaidChambers(true);
		String deathlessJson = writer.getJSONString(deathless, gson, new JsonParser());
		assertFalse(deathlessJson.contains("\"deaths\""));

		RaidTracker loaded = gson.fromJson(new JsonParser().parse(json), RaidTracker.class);

		assertEquals(68, loaded.getTektonTime());
		assertEquals(107, loaded.getGuardiansTime());
		assertEquals(60, loaded.getOlmHeadTime());
		assertEquals(30, loaded.getOlmMageHandTimes().get(0).intValue());
		assertEquals(66, loaded.getOlmPhaseTimes().get(0).intValue());
		assertEquals("Tekton", loaded.getDeaths().get(0).getRoom());
		assertEquals(8000, loaded.getDeaths().get(0).getPointsLost());
		assertEquals("Twisted bow", loaded.getUniques().get(0).getItem());
		assertEquals("Someone Else", loaded.getUniques().get(0).getReceiver());
		assertEquals(999, loaded.getUniques().get(0).getValue().intValue());
		assertFalse(loaded.isInRaidChambers());
		writer.restoreRaidType(loaded, RaidType.COX);
		assertTrue(loaded.isInRaidChambers());
		assertEquals(0, loaded.getPersonalDeathCount());

		RaidTracker theatre = new RaidTracker();
		theatre.setInTheatreOfBlood(true);
		theatre.setMaidenTime(40);
		String theatreJson = writer.getJSONString(theatre, gson, new JsonParser());
		assertTrue(theatreJson.contains("\"maidenTime\":40"));
		assertTrue(theatreJson.contains("\"inTheatreOfBlood\":true"));

		RaidTracker legacy = new RaidTracker();
		legacy.setInRaidChambers(true);
		legacy.setSpecialLoot("Twisted bow");
		legacy.setSpecialLootReceiver("Canvasba");
		legacy.setSpecialLootInOwnName(true);
		legacy.setSpecialLootValue(999);
		String legacyJson = writer.getJSONString(legacy, gson, new JsonParser());
		assertTrue(legacyJson.contains("\"specialLoot\":\"Twisted bow\""));
		assertTrue(legacyJson.contains("\"specialLootReceiver\":\"Canvasba\""));

		String oldDeaths = "{\"chambersDeaths\":[{\"room\":\"Crabs\",\"pointsLost\":100}]}";
		RaidTracker oldDeathsRaid = gson.fromJson(oldDeaths, RaidTracker.class);
		assertEquals("Crabs", oldDeathsRaid.getDeaths().get(0).getRoom());
		assertEquals(100, oldDeathsRaid.getDeaths().get(0).getPointsLost());

		String oldLine = "{\"tektonTime\":68,\"personalDeathCount\":0,\"inTheatreOfBlood\":true,\"inTombsOfAmascut\":true,\"raidTime\":100,\"maidenTime\":40}";
		RaidTracker old = gson.fromJson(oldLine, RaidTracker.class);
		assertEquals(68, old.getTektonTime());
		assertEquals(0, old.getPersonalDeathCount());
		assertTrue(old.isInTheatreOfBlood());
		assertTrue(old.isInTombsOfAmascut());
		assertEquals(100, old.getRaidTime());
		assertEquals(40, old.getMaidenTime());
	}

	@Test
	public void TestLeagueLogUsesItsOwnFolder()
	{
		assertEquals("Canvasba", FileReadWriter.accountDirectoryName("Canvasba", false));
		assertEquals("Canvasba-league", FileReadWriter.accountDirectoryName("Canvasba", true));
		assertNull(FileReadWriter.accountDirectoryName(null, true));

		Client leagueClient = mock(Client.class);
		when(leagueClient.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL));
		assertEquals(Boolean.TRUE, WorldUtils.playerOnLeagueWorld(leagueClient));

		Client normalClient = mock(Client.class);
		when(normalClient.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
		assertEquals(Boolean.FALSE, WorldUtils.playerOnLeagueWorld(normalClient));

		Client deadmanClient = mock(Client.class);
		when(deadmanClient.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.DEADMAN));
		assertEquals(Boolean.FALSE, WorldUtils.playerOnLeagueWorld(deadmanClient));
		assertTrue(WorldUtils.playerOnUntrackedWorld(deadmanClient));

		Client betaClient = mock(Client.class);
		when(betaClient.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.BETA_WORLD));
		assertTrue(WorldUtils.playerOnUntrackedWorld(betaClient));

		Client tournamentClient = mock(Client.class);
		when(tournamentClient.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.TOURNAMENT_WORLD));
		assertTrue(WorldUtils.playerOnUntrackedWorld(tournamentClient));

		Client pvpClient = mock(Client.class);
		when(pvpClient.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.PVP));
		assertFalse(WorldUtils.playerOnUntrackedWorld(pvpClient));
		assertFalse(WorldUtils.playerOnUntrackedWorld(leagueClient));
		assertFalse(WorldUtils.playerOnUntrackedWorld(normalClient));

		Client unknownClient = mock(Client.class);
		when(unknownClient.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		assertNull(WorldUtils.playerOnLeagueWorld(unknownClient));
		assertFalse(WorldUtils.playerOnUntrackedWorld(unknownClient));
	}

}