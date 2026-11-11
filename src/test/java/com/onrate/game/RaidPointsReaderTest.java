package com.onrate.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.tracking.RaidInsights;
import com.onrate.engine.tracking.RaidRecord;
import com.onrate.engine.tracking.Tracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.PluginMessage;
import org.junit.Before;
import org.junit.Test;

/**
 * What the reader makes of the game at the end of a raid: the Chambers points, the Tombs points (the
 * game's exact number or On Rate's estimate), the Theatre board, the Tombs plugin's message and the
 * Fractured Archive's damage share. Driven with a stand-in for the game client, so also with the
 * values missing, malformed or arriving in another order.
 */
public class RaidPointsReaderTest
{
	private static final String COX = "Chambers of Xeric";
	private static final String TOA = "Tombs of Amascut";
	private static final String TOB = "Theatre of Blood";
	private static final String TFA = "The Fractured Archive";

	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Integer, Widget> widgets = new HashMap<>();
	private final List<Player> players = new ArrayList<>();
	private Player me;
	private boolean noWorld;
	private boolean enabled = true;
	private final List<String> changed = new ArrayList<>();
	private Tracker tracker;
	private RaidPointsReader reader;

	@Before
	public void setUp()
	{
		tracker = new Tracker(Bundled.catalog(new Gson()));
		tracker.setSettings(raid -> 0.03);
		me = player("Me Name");
		players.add(me);
		IndexedObjectSet<?> set = Fakes.of(IndexedObjectSet.class, new Fakes.Answers().on("iterator", args -> players.iterator()));
		WorldView view = Fakes.of(WorldView.class, new Fakes.Answers().on("players", set));
		Client client = Fakes.of(Client.class, new Fakes.Answers()
			.on("getVarbitValue", args -> args.length == 1 ? varbits.getOrDefault((Integer) args[0], 0) : 0)
			.on("getVarpValue", args -> varps.getOrDefault((Integer) args[0], 0))
			.on("getWidget", args -> args.length == 1 && args[0] instanceof Integer ? widgets.get(args[0]) : null)
			.on("getLocalPlayer", args -> me)
			.on("getTopLevelWorldView", args -> noWorld ? null : view));
		ClientThread now = new ClientThread()
		{
			@Override
			public void invokeLater(Runnable r)
			{
				r.run();
			}
		};
		reader = new RaidPointsReader(client, now, tracker, changed::add, () -> enabled);
	}

	private static Player player(String name)
	{
		return Fakes.of(Player.class, new Fakes.Answers().on("getName", name));
	}

	private static Widget widget(String text)
	{
		return Fakes.of(Widget.class, new Fakes.Answers().on("getText", text));
	}

	private void chat(ChatMessageType type, String message)
	{
		reader.onChatMessage(new ChatMessage(null, type, "", message, "", 0));
	}

	/** The completion count message, as the plugin hands it to the tracker. */
	private String completion(String counter, int kc)
	{
		return tracker.onChatMessage("Your completed " + counter + " count is: <col=ff0000>" + kc + "</col>.", System.currentTimeMillis());
	}

	// ---- Chambers of Xeric ----

	private void inChambers(int personal, int total, int partySize, int scaledSize)
	{
		varbits.put(VarbitID.RAIDS_CLIENT_INDUNGEON, 1);
		varbits.put(VarbitID.RAIDS_CLIENT_PARTYSCORE, total);
		varps.put(VarPlayerID.RAIDS_PLAYERSCORE, personal);
		varbits.put(VarbitID.RAIDS_CLIENT_PARTYSIZE, partySize);
		varbits.put(VarbitID.RAIDS_CLIENT_PARTYSIZE_SCALED, scaledSize);
	}

	private void chambersDone()
	{
		chat(ChatMessageType.FRIENDSCHATNOTIFICATION, "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col>");
	}

	@Test
	public void chambersPointsAreSavedWithTheScaleAndThePlayersStillInTheRaid()
	{
		// scaled for three, two players still in it (and one the client lists without a name yet)
		inChambers(30_000, 60_000, 2, 3);
		players.add(player("Teammate"));
		players.add(player(null));
		chambersDone();
		assertTrue("the raid's mode isn't known until its completion message", tracker.raidRecords(COX).isEmpty());
		assertEquals(COX, completion("Chambers of Xeric", 5));

		RaidRecord r = tracker.raidRecords(COX).get(0);
		assertEquals(30_000, r.getPersonalPoints());
		assertEquals(60_000, r.getTeamPoints());
		assertEquals(3, r.getTeamSize());
		assertEquals("Normal", r.getPart());
		assertEquals(5, r.getKc());
		RaidInsights.Setup setup = tracker.raidInsights(COX, null).getSetups().get(0);
		assertEquals(3, setup.getTeam());
		assertEquals(2, setup.getPlayers());
	}

	@Test
	public void chambersPointsThatComeAfterTheCompletionMessageStillJoinIt()
	{
		inChambers(25_000, 25_000, 1, 1);
		completion("Chambers of Xeric Challenge Mode", 3);
		assertTrue(changed.isEmpty());
		chambersDone();
		assertEquals(Arrays.asList(COX), changed);
		assertEquals("Challenge Mode", tracker.raidRecords(COX).get(0).getPart());
		assertEquals(25_000, tracker.raidRecords(COX).get(0).getPersonalPoints());
	}

	@Test
	public void chambersIsIgnoredOutsideTheDungeonWithoutPointsOrWithTheSettingOff()
	{
		// the message somewhere else (a friends chat relaying it)
		inChambers(30_000, 60_000, 2, 2);
		varbits.put(VarbitID.RAIDS_CLIENT_INDUNGEON, 0);
		chambersDone();
		// in the dungeon, but the game gave no points
		inChambers(0, 60_000, 2, 2);
		chambersDone();
		inChambers(30_000, 0, 2, 2);
		chambersDone();
		// and with "Use raid points" off
		inChambers(30_000, 60_000, 2, 2);
		enabled = false;
		chambersDone();
		completion("Chambers of Xeric", 1);
		assertTrue(tracker.raidRecords(COX).isEmpty());
		assertTrue(changed.isEmpty());
	}

	@Test
	public void chambersWithoutAPlayerListLeavesThePlayersUnknown()
	{
		inChambers(30_000, 90_000, 3, 3);
		noWorld = true;
		chambersDone();
		completion("Chambers of Xeric", 1);
		RaidInsights.Setup setup = tracker.raidInsights(COX, null).getSetups().get(0);
		assertEquals(3, setup.getTeam());
		assertEquals("not known whether players had left", -1, setup.getPlayers());
	}

	@Test
	public void chambersNeverCountsMorePlayersThanTheRaidScaledFor()
	{
		// a solo raid with an onlooker's alt in view: still solo
		inChambers(30_000, 30_000, 1, 1);
		players.add(player("Someone else"));
		chambersDone();
		completion("Chambers of Xeric", 1);
		RaidInsights.Setup setup = tracker.raidInsights(COX, null).getSetups().get(0);
		assertEquals(1, setup.getTeam());
		assertEquals(1, setup.getPlayers());
	}

	// ---- Tombs of Amascut ----

	private void inTombs(int raidLevel)
	{
		widgets.put(InterfaceID.ToaHud.RAID_STATE, widget(""));
		varbits.put(VarbitID.TOA_CLIENT_RAID_LEVEL, raidLevel);
		reader.onGameTick(new GameTick());
	}

	private void tombsExactPoints(int points)
	{
		VarbitChanged e = new VarbitChanged();
		e.setVarpId(VarPlayerID.TOA_PERSONAL_CONTRIBUTION);
		e.setValue(points);
		reader.onVarbitChanged(e);
	}

	private void tombsStart()
	{
		chat(ChatMessageType.GAMEMESSAGE, "You enter the Tombs of Amascut...");
	}

	private void wardensDone()
	{
		chat(ChatMessageType.GAMEMESSAGE, "Challenge complete: The Wardens. Duration: <col=ef1020>4:01.20</col>");
	}

	@Test
	public void tombsUsesTheGamesExactPointsWhenTheyComeBeforeTheRaidEnds()
	{
		tombsStart();
		inTombs(300);
		tombsExactPoints(21_000);
		wardensDone();
		completion("Tombs of Amascut: Expert Mode", 1);
		RaidRecord r = tracker.raidRecords(TOA).get(0);
		assertEquals(21_000, r.getPersonalPoints());
		assertFalse(r.isEstimated());
		assertEquals(300, r.getRaidLevel());
		assertEquals(1, r.getTeamSize());
		assertEquals("Expert Mode", r.getPart());
	}

	@Test
	public void tombsExactPointsThatComeAfterTheRaidWasSavedReplaceTheEstimate()
	{
		tombsStart();
		inTombs(150);
		wardensDone();
		completion("Tombs of Amascut", 4);
		RaidRecord r = tracker.raidRecords(TOA).get(0);
		assertTrue("On Rate's own estimate so far", r.isEstimated());
		changed.clear();
		tombsExactPoints(18_500);
		assertEquals(Arrays.asList(TOA), changed);
		assertEquals(18_500, r.getPersonalPoints());
		assertFalse(r.isEstimated());
		assertEquals(150, r.getRaidLevel());
	}

	@Test
	public void tombsCountsThePartyFromTheGamesPlayerSlots()
	{
		varbits.put(VarbitID.TOA_CLIENT_P1, 1);
		varbits.put(VarbitID.TOA_CLIENT_P2, 1);
		tombsStart();
		inTombs(200);
		tombsExactPoints(12_000);
		wardensDone();
		completion("Tombs of Amascut", 1);
		assertEquals(3, tracker.raidRecords(TOA).get(0).getTeamSize());
	}

	@Test
	public void tombsIsIgnoredOutsideTheRaidOrWithTheSettingOff()
	{
		// not in the raid: no HUD, no raid room
		tombsStart();
		reader.onGameTick(new GameTick());
		wardensDone();
		completion("Tombs of Amascut", 1);
		assertTrue(tracker.raidRecords(TOA).isEmpty());

		// in the raid with the setting off
		enabled = false;
		tombsStart();
		inTombs(300);
		tombsExactPoints(21_000);
		wardensDone();
		completion("Tombs of Amascut", 2);
		assertTrue(tracker.raidRecords(TOA).isEmpty());
	}

	@Test
	public void tombsExactPointsOfZeroOrLessAreNotPoints()
	{
		tombsStart();
		inTombs(300);
		tombsExactPoints(0);
		tombsExactPoints(-5);
		wardensDone();
		completion("Tombs of Amascut: Expert Mode", 1);
		// saved from the estimate instead (nothing was seen of this raid, so the estimate has no points either)
		assertTrue(tracker.raidRecords(TOA).get(0).isEstimated());
	}

	@Test
	public void loggingInAgainStartsTheRaidStateOver()
	{
		tombsStart();
		inTombs(300);
		tombsExactPoints(21_000);
		GameStateChanged login = new GameStateChanged();
		login.setGameState(GameState.LOGGING_IN);
		reader.onGameStateChanged(login);
		// until the next game tick says so, On Rate no longer takes you to be in the raid
		wardensDone();
		completion("Tombs of Amascut: Expert Mode", 1);
		assertTrue(tracker.raidRecords(TOA).isEmpty());
	}

	/**
	 * Known gap (finding 1.2 in the November 2026 review): when the start of the raid was missed (the
	 * plugin turned on or the client restarted mid-raid) no raid is saved, although the game sent the exact
	 * points. This test says what happens today. A fix is meant to change its last line.
	 */
	@Test
	public void aTombsRaidWhoseStartWasMissedIsNotSavedToday()
	{
		inTombs(300);
		tombsExactPoints(21_000);
		wardensDone();
		completion("Tombs of Amascut: Expert Mode", 1);
		assertTrue(tracker.raidRecords(TOA).isEmpty());
	}

	// ---- the Tombs of Amascut plugin's party points ----

	private static PluginMessage points(String namespace, String name, Object version, Object personal, Object total)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("version", version);
		data.put("personalPoints", personal);
		data.put("totalPoints", total);
		return new PluginMessage(namespace, name, data);
	}

	@Test
	public void theTombsPluginsTotalGoesIntoTheRaidAndAnythingElseIsIgnored()
	{
		varbits.put(VarbitID.TOA_CLIENT_P1, 1);
		tombsStart();
		inTombs(300);
		wardensDone();
		completion("Tombs of Amascut: Expert Mode", 1);
		RaidRecord r = tracker.raidRecords(TOA).get(0);
		changed.clear();

		// another plugin, another message, no data, another version, text where numbers belong, no points
		reader.onPluginMessage(points("some-other-plugin", "raidCompletedPoints", 1, 20_000, 60_000));
		reader.onPluginMessage(points("tombs-of-amascut", "somethingElse", 1, 20_000, 60_000));
		reader.onPluginMessage(new PluginMessage("tombs-of-amascut", "raidCompletedPoints"));
		reader.onPluginMessage(new PluginMessage("tombs-of-amascut", "raidCompletedPoints", new HashMap<>()));
		reader.onPluginMessage(points("tombs-of-amascut", "raidCompletedPoints", 2, 20_000, 60_000));
		reader.onPluginMessage(points("tombs-of-amascut", "raidCompletedPoints", "1", 20_000, 60_000));
		reader.onPluginMessage(points("tombs-of-amascut", "raidCompletedPoints", 1, "20000", "60000"));
		reader.onPluginMessage(points("tombs-of-amascut", "raidCompletedPoints", 1, null, 60_000));
		reader.onPluginMessage(points("tombs-of-amascut", "raidCompletedPoints", 1, 0, 0));
		assertTrue(changed.isEmpty());
		assertEquals(0, r.getTeamPoints());

		reader.onPluginMessage(points("tombs-of-amascut", "raidCompletedPoints", 1, 20_000, 60_000L));
		assertEquals(Arrays.asList(TOA), changed);
		assertEquals(20_000, r.getPersonalPoints());
		assertEquals(60_000, r.getTeamPoints());
	}

	// ---- Theatre of Blood ----

	private static final int[] NAMES = {
		InterfaceID.TobInfoboard.PLAYERNAME0, InterfaceID.TobInfoboard.PLAYERNAME1, InterfaceID.TobInfoboard.PLAYERNAME2,
		InterfaceID.TobInfoboard.PLAYERNAME3, InterfaceID.TobInfoboard.PLAYERNAME4,
	};
	private static final int[] DEATHS = {
		InterfaceID.TobInfoboard.DEATHS0, InterfaceID.TobInfoboard.DEATHS1, InterfaceID.TobInfoboard.DEATHS2,
		InterfaceID.TobInfoboard.DEATHS3, InterfaceID.TobInfoboard.DEATHS4,
	};

	private void boardRow(int row, String name, String deaths)
	{
		widgets.put(NAMES[row], widget(name));
		widgets.put(DEATHS[row], widget(deaths));
	}

	private void openBoard()
	{
		WidgetLoaded loaded = new WidgetLoaded();
		loaded.setGroupId(InterfaceID.TOB_INFOBOARD);
		reader.onWidgetLoaded(loaded);
	}

	@Test
	public void theTheatreBoardGivesTheTeamYourDeathsAndTheMvp()
	{
		boardRow(0, "Alice", "2");
		boardRow(1, "<col=ffffff>Me Name</col>", "1");
		boardRow(2, "-", "-");
		widgets.put(InterfaceID.TobInfoboard.MVP_PLAYER, widget("me name"));
		openBoard();
		assertEquals(TOB, completion("Theatre of Blood", 10));
		RaidRecord r = tracker.raidRecords(TOB).get(0);
		assertEquals(2, r.getTeamSize());
		assertEquals(1, r.getDeaths());
		assertEquals(3, r.getTeamDeaths());
		assertEquals(Boolean.TRUE, r.getMvp());
	}

	@Test
	public void aBoardThatCantBeFullyReadKeepsWhatItCan()
	{
		// deaths that aren't a number count as none; you are not on the board; no MVP shown
		boardRow(0, "Alice", "abc");
		boardRow(1, "Bob", "");
		boardRow(3, "Carol", "4");
		openBoard();
		completion("Theatre of Blood", 1);
		RaidRecord r = tracker.raidRecords(TOB).get(0);
		assertEquals(3, r.getTeamSize());
		assertEquals("your own deaths are unknown", -1, r.getDeaths());
		assertEquals(4, r.getTeamDeaths());
		assertNull(r.getMvp());
	}

	@Test
	public void anEmptyBoardAnotherInterfaceOrTheSettingOffSaveNothing()
	{
		// the board with nobody on it
		boardRow(0, "-", "-");
		openBoard();
		// another interface
		boardRow(0, "Alice", "0");
		WidgetLoaded other = new WidgetLoaded();
		other.setGroupId(InterfaceID.TOB_INFOBOARD + 1);
		reader.onWidgetLoaded(other);
		// the setting off
		enabled = false;
		openBoard();
		completion("Theatre of Blood", 1);
		assertTrue(tracker.raidRecords(TOB).isEmpty());
	}

	@Test
	public void theBoardWithoutALocalPlayerStillCountsTheTeam()
	{
		me = null;
		boardRow(0, "Alice", "1");
		boardRow(1, "Bob", "0");
		widgets.put(InterfaceID.TobInfoboard.MVP_PLAYER, widget("Alice"));
		openBoard();
		completion("Theatre of Blood", 1);
		RaidRecord r = tracker.raidRecords(TOB).get(0);
		assertEquals(2, r.getTeamSize());
		assertEquals(-1, r.getDeaths());
		assertNull(r.getMvp());
	}

	// ---- The Fractured Archive ----

	private void hit(int npcId, int amount, boolean mine)
	{
		HitsplatApplied e = new HitsplatApplied();
		e.setActor(Fakes.of(NPC.class, new Fakes.Answers().on("getId", npcId)));
		e.setHitsplat(Fakes.of(Hitsplat.class, new Fakes.Answers().on("getAmount", amount).on("isMine", mine).on("isOthers", !mine)));
		reader.onHitsplatApplied(e);
	}

	private void archiveDone(int kc)
	{
		chat(ChatMessageType.GAMEMESSAGE, "Your The Fractured Archive completion count is: <col=ff0000>" + kc + "</col>.");
		tracker.onChatMessage("Your The Fractured Archive completion count is: <col=ff0000>" + kc + "</col>.", System.currentTimeMillis());
	}

	@Test
	public void theArchivesDamageShareAndDeathsAreSavedWithTheRaid()
	{
		// the Archive Guardian, Ezekiel, Moia, the Dagon'hai and Ocellus: 30 of every 100 damage is yours
		for (int npc : new int[]{16659, 16652, 16667, 16643, 16672})
		{
			hit(npc, 30, true);
			hit(npc, 70, false);
		}
		// a hit on something that isn't part of the raid changes nothing
		hit(1, 500, true);
		reader.onActorDeath(new ActorDeath(me));
		reader.onActorDeath(new ActorDeath(player("Teammate")));
		archiveDone(1);
		RaidRecord r = tracker.raidRecords(TFA).get(0);
		assertEquals(210, r.getContribution(), 1e-9);
		assertEquals(1, r.getDeaths());
	}

	@Test
	public void anArchiveRaidWithAFightMissedHasNoShare()
	{
		// turned on after the first fight: four of the five fights seen
		for (int npc : new int[]{16652, 16667, 16643, 16672})
		{
			hit(npc, 50, true);
			hit(npc, 50, false);
		}
		tracker.setTeamSizeDetector(() -> 2);
		archiveDone(1);
		RaidRecord r = tracker.raidRecords(TFA).get(0);
		assertEquals("an even share is assumed instead", -1, r.getContribution(), 0);
		assertEquals(2, r.getTeamSize());
	}

	@Test
	public void anArchiveRaidOnRateSawNothingOfSavesNothingByItself()
	{
		archiveDone(1);
		assertTrue(tracker.raidRecords(TFA).isEmpty());
		assertTrue(changed.isEmpty());
	}
}
