package com.expecteddrops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.expecteddrops.ui.ExpectedDropsPanel;
import com.expecteddrops.ui.ExpectedDropsPanel.ProfileState;
import com.expecteddrops.ui.ItemView;
import com.expecteddrops.ui.ViewOptions;
import com.google.gson.Gson;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.Tracker;
import com.onrate.game.Fakes;
import com.onrate.game.TeamDetector;
import com.onrate.store.ProfileStore;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.RuneScapeProfileChanged;
import org.junit.Before;
import org.junit.Test;

/**
 * The plugin's own profile loading, as the game drives it: logging in, the moment before RuneLite names
 * the account, logging out, another account, an account with nothing saved, damaged saved data and a
 * load that fails. The real plugin method runs, against a stand-in client and stand-in profile settings.
 *
 * <p>RuneLite's settings can't be built outside the client, so the one thing read from them here is the
 * profile key (set straight on an otherwise empty ConfigManager). The Slayer level, which the plugin saves
 * in those settings on login, is kept known so that this test never reaches them.
 */
public class ProfileLoadTest
{
	static
	{
		com.onrate.engine.data.KillTimes.bundled(new Gson());
		com.onrate.engine.data.LadlorChart.bundled(new Gson());
	}

	private static final RaidSettings RAIDS = raid -> 0.03;
	private static final int SLAYER = 50;

	/** Every profile's saved settings; can refuse to be read. */
	private static final class Profiles implements ProfileStore.Backend
	{
		final Map<String, Map<String, String>> data = new HashMap<>();
		final List<String> writes = new ArrayList<>();
		final Set<String> unreadable = new HashSet<>();

		Map<String, String> of(String profile)
		{
			return data.computeIfAbsent(profile, p -> new HashMap<>());
		}

		@Override
		public String get(String profile, String key)
		{
			if (unreadable.contains(profile))
			{
				throw new IllegalStateException("settings not readable");
			}
			return of(profile).get(key);
		}

		@Override
		public void set(String profile, String key, String value)
		{
			of(profile).put(key, value);
			writes.add(profile + "/" + key);
		}

		@Override
		public void unset(String profile, String key)
		{
			of(profile).remove(key);
			writes.add(profile + "/-" + key);
		}
	}

	private ExpectedDropsPlugin plugin;
	private Profiles profiles;
	private Tracker tracker;
	private ExpectedDropsPanel panel;
	private ConfigManager configManager;
	private GameState gameState = GameState.LOGIN_SCREEN;

	private static void set(Object target, Class<?> type, String name, Object value) throws Exception
	{
		Field f = type.getDeclaredField(name);
		f.setAccessible(true);
		f.set(target, value);
	}

	private void set(String name, Object value) throws Exception
	{
		set(plugin, ExpectedDropsPlugin.class, name, value);
	}

	private Object get(String name) throws Exception
	{
		Field f = ExpectedDropsPlugin.class.getDeclaredField(name);
		f.setAccessible(true);
		return f.get(plugin);
	}

	@Before
	public void setUp() throws Exception
	{
		Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		configManager = (ConfigManager) unsafe.allocateInstance(ConfigManager.class);

		plugin = new ExpectedDropsPlugin();
		profiles = new Profiles();
		tracker = new Tracker(Bundled.catalog(new Gson()));
		tracker.setSettings(RAIDS);
		Client client = Fakes.of(Client.class, new Fakes.Answers()
			.on("getGameState", args -> gameState)
			.on("getRealSkillLevel", SLAYER)
			.on("getAccountHash", 1234L));
		SwingUtilities.invokeAndWait(() ->
		{
			panel = new ExpectedDropsPanel(tracker, null, () -> RAIDS, s -> -1, () -> { }, () -> false, () -> { }, name -> 0L,
				() -> new ViewOptions(ItemView.TARGET_BARS, false, false, false, false, true));
			// as startUp leaves it: nobody's data is loaded yet
			panel.setProfileState(ProfileState.NOT_LOGGED_IN);
		});
		set("client", client);
		set("configManager", configManager);
		set("tracker", tracker);
		set("store", new ProfileStore(profiles, new Gson()));
		set("team", new TeamDetector(client));
		set("panel", panel);
		set("slayerLevel", SLAYER);

		profiles.of("A").put("progress", "{\"bosses\":{\"Vorkath\":{\"chatKc\":{\"vorkath\":50}}}}");
		profiles.of("A").put("kills.Vorkath", "[{\"k\":50,\"t\":1000}]");
		profiles.of("B").put("progress", "{\"bosses\":{\"Vorkath\":{\"chatKc\":{\"vorkath\":7}}}}");
	}

	/** RuneLite names this profile (null: none, as at the login screen) and says so. */
	private void runeLiteProfile(String key) throws Exception
	{
		set(configManager, ConfigManager.class, "rsProfileKey", key);
		plugin.onRuneScapeProfileChanged(new RuneScapeProfileChanged(null, key));
		SwingUtilities.invokeAndWait(() -> { });
	}

	private void gameState(GameState state) throws Exception
	{
		gameState = state;
		GameStateChanged e = new GameStateChanged();
		e.setGameState(state);
		plugin.onGameStateChanged(e);
		// the login screen forgets the Slayer level; keep it known (see the class comment)
		set("slayerLevel", SLAYER);
		SwingUtilities.invokeAndWait(() -> { });
	}

	private int vorkathKills()
	{
		return tracker.stats("Vorkath", RAIDS).getKillCount();
	}

	private ProfileState shown() throws Exception
	{
		ProfileState[] state = new ProfileState[1];
		SwingUtilities.invokeAndWait(() -> state[0] = panel.getProfileState());
		return state[0];
	}

	@Test
	public void loggingInLoadsThatAccount() throws Exception
	{
		set(configManager, ConfigManager.class, "rsProfileKey", "A");
		gameState(GameState.LOGGED_IN);
		assertEquals(50, vorkathKills());
		assertEquals(1, tracker.killsSeen("Vorkath"));
		assertEquals(ProfileState.LOADED, shown());
		assertTrue("loading writes nothing", profiles.writes.isEmpty());
	}

	@Test
	public void atTheLoginScreenNothingIsLoadedAndThePanelSaysToLogIn() throws Exception
	{
		runeLiteProfile(null);
		assertEquals(0, vorkathKills());
		assertEquals(ProfileState.NOT_LOGGED_IN, shown());
		// and nothing can be saved over anyone's data
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>3</col>.", 1_000);
		plugin.save();
		assertTrue(profiles.writes.isEmpty());
	}

	@Test
	public void loggedInBeforeRuneLiteNamesTheAccountSaysLoadingThenLoads() throws Exception
	{
		gameState(GameState.LOGGED_IN);
		assertEquals(ProfileState.LOADING, shown());
		assertEquals(0, vorkathKills());
		runeLiteProfile("A");
		assertEquals(ProfileState.LOADED, shown());
		assertEquals(50, vorkathKills());
	}

	@Test
	public void loggingOutKeepsTheAccountAndItsSavesStillGoToIt() throws Exception
	{
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("A");
		gameState(GameState.LOGIN_SCREEN);
		runeLiteProfile(null);
		assertEquals("the account's numbers stay on screen", 50, vorkathKills());
		assertEquals(ProfileState.LOADED, shown());
		// something still to save (a click in the panel while logged out) lands in A, not nowhere
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>51</col>.", 2_000);
		plugin.save();
		assertFalse(profiles.writes.isEmpty());
		for (String write : profiles.writes)
		{
			assertTrue(write, write.startsWith("A/"));
		}
		assertTrue(profiles.of("A").get("progress").contains("\"vorkath\":51"));
	}

	@Test
	public void anotherAccountShowsOnlyItsOwnDataAndStartsItsSessionFresh() throws Exception
	{
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("A");
		@SuppressWarnings("unchecked") Set<String> reminded = (Set<String>) get("remindedThisSession");
		reminded.add("Vorkath");
		String savedA = profiles.of("A").get("progress");

		gameState(GameState.LOGIN_SCREEN);
		runeLiteProfile(null);
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("B");
		assertEquals(7, vorkathKills());
		assertEquals("A's History is not B's", 0, tracker.killsSeen("Vorkath"));
		assertEquals(ProfileState.LOADED, shown());
		assertTrue("B gets its own reminder to open the log page", reminded.isEmpty());
		assertEquals(savedA, profiles.of("A").get("progress"));

		// B's kill is saved to B
		profiles.writes.clear();
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>8</col>.", 3_000);
		plugin.save();
		for (String write : profiles.writes)
		{
			assertTrue(write, write.startsWith("B/"));
		}
		assertEquals(savedA, profiles.of("A").get("progress"));
	}

	@Test
	public void anAccountWithNothingSavedLoadsEmpty() throws Exception
	{
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("A");
		gameState(GameState.LOGIN_SCREEN);
		runeLiteProfile(null);
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("new account");
		assertEquals("nothing of A is left", 0, vorkathKills());
		assertEquals(0, tracker.killsSeen("Vorkath"));
		assertEquals(ProfileState.LOADED, shown());
		assertTrue(profiles.writes.isEmpty());
	}

	@Test
	public void theSameProfileNamedAgainDoesNotLoadOverWhatIsInMemory() throws Exception
	{
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("A");
		// a kill the tracker holds (saved or not): RuneLite repeating the profile, or the game state
		// changing while logged in (a world hop), must not put the saved numbers back over it
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>51</col>.", 2_000);
		runeLiteProfile("A");
		gameState(GameState.HOPPING);
		gameState(GameState.LOGGED_IN);
		assertEquals(51, vorkathKills());
		assertEquals(2, tracker.killsSeen("Vorkath"));
		assertEquals(ProfileState.LOADED, shown());
	}

	@Test
	public void damagedSavedDataStillLoadsAndIsKeptAsACopy() throws Exception
	{
		profiles.of("A").put("progress", "{\"bosses\":{\"Vorkath\":{\"chatKc\":{\"vorkath\":50}},\"Zulrah\":null}");
		profiles.of("A").put("kills.Vorkath", "[{\"k\":50,\"t\":1000},");
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("A");
		assertEquals("the account opens, with what could not be read left out", ProfileState.LOADED, shown());
		assertEquals(0, tracker.killsSeen("Vorkath"));
		assertEquals("[{\"k\":50,\"t\":1000},", profiles.of("A").get("kills.Vorkath.corrupt"));
		assertNotNull(profiles.of("A").get("progress.corrupt"));
	}

	@Test
	public void aLoadThatFailsSaysSoSavesNothingAndWorksOnTheNextTry() throws Exception
	{
		gameState(GameState.LOGGED_IN);
		profiles.unreadable.add("A");
		runeLiteProfile("A");
		assertEquals(ProfileState.FAILED, shown());
		// whatever happens now is never written over the data that wasn't loaded
		tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>1</col>.", 1_000);
		plugin.save();
		assertTrue(profiles.writes.isEmpty());

		// the settings can be read again: RuneLite names the profile once more and it loads
		profiles.unreadable.clear();
		runeLiteProfile("A");
		assertEquals(ProfileState.LOADED, shown());
		assertEquals(50, vorkathKills());
	}

	@Test
	public void aFailedLoadOfTheNextAccountNeverShowsOrSavesTheLastOne() throws Exception
	{
		gameState(GameState.LOGGED_IN);
		runeLiteProfile("A");
		String savedA = profiles.of("A").get("progress");
		gameState(GameState.LOGIN_SCREEN);
		runeLiteProfile(null);
		gameState(GameState.LOGGED_IN);
		profiles.unreadable.add("B");
		runeLiteProfile("B");
		assertEquals(ProfileState.FAILED, shown());
		plugin.save();
		assertTrue("nothing is written to either account", profiles.writes.isEmpty());
		assertEquals(savedA, profiles.of("A").get("progress"));
		assertNull(profiles.of("B").get("progress.corrupt"));
	}

	@Test
	public void eventsAfterThePluginStoppedDoNothing() throws Exception
	{
		set("tracker", null);
		set("store", null);
		runeLiteProfile("A");
		gameState(GameState.LOGGED_IN);
		assertEquals(ProfileState.NOT_LOGGED_IN, shown());
		assertTrue(profiles.writes.isEmpty());
	}
}
