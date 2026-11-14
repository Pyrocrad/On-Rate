package com.expecteddrops;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import com.onrate.engine.data.KillTimes;
import com.onrate.engine.data.LadlorChart;
import java.time.LocalDate;

import com.expecteddrops.ui.*;
import com.google.gson.Gson;
import com.google.inject.Provides;
import com.onrate.engine.data.Bundled;
import com.onrate.engine.raids.RaidDataImport;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.tracking.*;
import com.onrate.game.*;
import com.onrate.service.AccountPrecompute;
import com.onrate.store.Keys;
import com.onrate.store.ProfileStore;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;

/**
 * On Rate. This class is wiring only: game events go to the tracker and the adapters in
 * {@code com.onrate.game}, the numbers come from {@code com.onrate.engine}, and the panel is the
 * original one. What a player sees and the settings they have are unchanged.
 */
@PluginDescriptor(
	name = "On Rate",
	description = "Shows whether you are on rate: expected vs received uniques for every boss, based on your kill count",
	tags = {"boss", "drops", "dry", "luck", "spoon", "collection", "log", "uniques", "expected", "rate", "doom", "delve"}
)
public class ExpectedDropsPlugin extends Plugin
{
	static final String DATA_KEY = Keys.DATA;
	/** Each boss's History is saved under its own key: "kills." plus the boss name. */
	static final String KILLS_KEY = Keys.KILLS;
	/** Each raid's saved raids (with points) are under their own key too: "raids." plus the name. */
	static final String RAIDS_KEY = Keys.RAIDS;
	/** The luck graph's point for today is worked out again at most this often (it's the whole account). */
	private static final long HISTORY_POINT_MILLIS = 10 * 60_000;
	private long historyPointAt;
	private long historyPointDay = -1;
	/** Doom of Mokhaiotl scoreboard: your completions per delve level 1 to 8, then "8+". */
	private static final int[] DOOM_LEVEL_TOTALS = {
		InterfaceID.DomScoreboard.P_TOTAL_LEVEL_1_VAL, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_2_VAL,
		InterfaceID.DomScoreboard.P_TOTAL_LEVEL_3_VAL, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_4_VAL,
		InterfaceID.DomScoreboard.P_TOTAL_LEVEL_5_VAL, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_6_VAL,
		InterfaceID.DomScoreboard.P_TOTAL_LEVEL_7_VAL, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_8_VAL,
		InterfaceID.DomScoreboard.P_TOTAL_LEVEL_8__VAL,
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ExpectedDropsConfig config;

	@Inject
	private Gson gson;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private PluginManager pluginManager;

	/** Reads raid points at the end of each raid (see RaidPointsReader for the credits). */
	private RaidPointsReader raidPoints;

	/** Bosses we already reminded about this session, so the reminder shows once. */

	private Tracker tracker;
	private ExpectedDropsPanel panel;
	private NavigationButton navButton;
	private ProfileStore store;
	/** Item ids and GE prices for the tracked items, from RuneLite's own price list. */
	private PriceBook prices;
	/** Reads a collection log page when the player opens it. */
	private ClogReader clog;
	/** Who is fighting a group boss with you, and the players around you. */
	private TeamDetector team;
	/** Tempoross and Wintertodt reward rolls, from the game's counters. */
	private RollCounters rolls;
	/** Works out the account after a kill on a worker thread, so the panel's next draw needn't. */
	private AccountPrecompute precompute;
	/** Raid Data Tracker's times, read on the executor and added only to the profile they were read for. */
	private RaidTimesImport raidTimesImport;

	@Provides
	ExpectedDropsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ExpectedDropsConfig.class);
	}

	/**
	 * Settings from earlier versions that were renamed (so a new default applies) or removed.
	 * Their old values are cleared so they don't linger in the RuneLite profile.
	 */
	static final String[] OLD_SETTINGS = {
		"toaUniqueChance", "tobUniqueChance", "tobHmUniqueChance", "coxCmUniqueChance",
		"royalTitansMode", "royalTitansDuoKills", "sharedUnknownAsZero", "showDropHistory", "showTrip",
		// The Fractured Archive's team chance from before the game update of 11 November 2026 (1 in 14):
		// its setting has a new key, so the new chance (1 in 16) applies to everyone
		"tfaTeamChance",
	};

	/**
	 * Clears every {@link #OLD_SETTINGS} value that is still saved. Only those keys are touched, and
	 * only when they hold something, so a second start changes nothing and the settings that replaced
	 * them keep whatever the player set.
	 */
	static void clearOldSettings(Function<String, String> saved, Consumer<String> unset)
	{
		for (String key : OLD_SETTINGS)
		{
			if (saved.apply(key) != null)
			{
				unset.accept(key);
			}
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		// The old "Colour-blind colours" tick is now a choice in "Luck colours": carry it over once.
		if ("true".equals(configManager.getConfiguration(ExpectedDropsConfig.GROUP, "colourBlind"))
			&& configManager.getConfiguration(ExpectedDropsConfig.GROUP, "luckColours") == null)
		{
			configManager.setConfiguration(ExpectedDropsConfig.GROUP, "luckColours", LuckColours.COLOUR_BLIND.name());
		}
		clearOldSettings(key -> configManager.getConfiguration(ExpectedDropsConfig.GROUP, key),
			key -> configManager.unsetConfiguration(ExpectedDropsConfig.GROUP, key));
		// Only the rates bundled with the plugin: On Rate reads and writes no files.
		// Kill times and the Ladlor chart are read with RuneLite's Gson (the Plugin Hub refuses fresh ones).
		KillTimes.bundled(gson);
		LadlorChart.bundled(gson);
		tracker = new Tracker(Bundled.catalog(gson));
		// the RS-profile calls RuneLite offers follow its current profile; these name the one loaded
		// (the same settings and keys: getRSProfileConfiguration is getConfiguration with the current one)
		store = new ProfileStore(new ProfileStore.Backend()
		{
			@Override
			public String get(String profile, String key)
			{
				return configManager.getConfiguration(ExpectedDropsConfig.GROUP, profile, key);
			}

			@Override
			public void set(String profile, String key, String value)
			{
				configManager.setConfiguration(ExpectedDropsConfig.GROUP, profile, key, value);
			}

			@Override
			public void unset(String profile, String key)
			{
				configManager.unsetConfiguration(ExpectedDropsConfig.GROUP, profile, key);
			}
		}, gson);
		ProfileStore loadedFrom = store;
		raidTimesImport = new RaidTimesImport(executor, clientThread::invokeLater, loadedFrom::loadedProfile);
		prices = new PriceBook(tracker.getCatalog(), itemManager);
		team = new TeamDetector(client);
		rolls = new RollCounters(client, tracker);
		tracker.setSettings(raidSettings());
		tracker.setTeamSizeDetectorFor(boss -> config.detectTeamSize() ? team.sizeFor(boss) : 0);
		precompute = new AccountPrecompute(tracker, executor, SwingUtilities::invokeLater, () ->
		{
			// worked out in the background only for a panel that is on screen
			ExpectedDropsPanel p = panel;
			return p != null && p.isOnScreen();
		});
		panel = new ExpectedDropsPanel(tracker, itemManager, this::raidSettings, this::itemIdFor, this::save,
			this::priceOf, this::viewOptions);
		panel.setPrecomputed(precompute);
		// nobody's data is loaded until a profile is: the panel says to log in (loadProfile updates it)
		panel.setProfileState(ExpectedDropsPanel.ProfileState.NOT_LOGGED_IN);
		panel.setSlayerLevel(() -> slayerLevel);
		panel.setGraphOpener(open -> configManager.setConfiguration(ExpectedDropsConfig.GROUP, "showLuckGraph", String.valueOf(open)));
		panel.setItemViewSaver(view -> configManager.setConfiguration(ExpectedDropsConfig.GROUP, "itemView", view));
		panel.setAccountPageStore(config.accountPage(),
			page -> configManager.setConfiguration(ExpectedDropsConfig.GROUP, "accountPage", page));
		panel.setFoldStateStore(config.foldState(),
			state -> configManager.setConfiguration(ExpectedDropsConfig.GROUP, "foldState", state));
		// the sort order and Items list come back as saved, but the panel always opens on the Bosses tab
		panel.startOnBossesTab();
		clog = new ClogReader(client, itemManager, tracker, prices, first ->
		{
			save();
			ExpectedDropsPanel p = panel;
			if (p != null)
			{
				SwingUtilities.invokeLater(() -> p.showBoss(first));
			}
		}, () ->
		{
			ExpectedDropsPanel p = panel;
			if (p != null)
			{
				SwingUtilities.invokeLater(p::refresh);
			}
		});

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("On Rate")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		updateLootTrackerStatus();

		raidPoints = new RaidPointsReader(client, clientThread, tracker, boss -> changed(boss, false), config::readRaidPoints);
		eventBus.register(raidPoints);

		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadProfile();
				resolveItemIds();
			}
		});
	}

	@Override
	protected void shutDown() throws Exception
	{
		recordHistoryPoint(true);
		save();
		if (precompute != null)
		{
			precompute.stop();
			precompute = null;
		}
		if (raidTimesImport != null)
		{
			// an import still reading must not add anything once the plugin has stopped
			raidTimesImport.stop();
			raidTimesImport = null;
		}
		if (raidPoints != null)
		{
			eventBus.unregister(raidPoints);
			raidPoints = null;
		}
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		ExpectedDropsPanel closing = panel;
		if (closing != null)
		{
			// the panel listens to the whole window's mouse for its tooltips: take that off again
			if (SwingUtilities.isEventDispatchThread())
			{
				closing.dispose();
			}
			else
			{
				SwingUtilities.invokeLater(closing::dispose);
			}
		}
		panel = null;
		tracker = null;
		if (store != null)
		{
			store.forget();
		}
		store = null;
		if (prices != null)
		{
			prices.clear();
		}
		if (team != null)
		{
			team.clear();
		}
		clog = null;
		rolls = null;
	}

	/**
	 * Reads your raid times from the Raid Data Tracker plugin's files, if it is installed and the
	 * setting is on. The files are read off the client thread; the times are added on it, and only if
	 * the profile they were read for is still loaded (see RaidTimesImport). Adding a time twice is
	 * harmless. Runs on the client thread, right after a profile was loaded.
	 */
	private void importRaidTimes()
	{
		Tracker t = tracker;
		ProfileStore s = store;
		RaidTimesImport importer = raidTimesImport;
		if (t == null || s == null || importer == null || !config.importRaidTimes())
		{
			return;
		}
		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			return;
		}
		String profile = s.loadedProfile();
		if (!RaidTimesImport.importsInto(profile, configManager.getRSProfiles()))
		{
			// a Leagues or other seasonal profile: the files can't tell its raids from the main game's
			return;
		}
		Filepath folder = raidDataFolder;
		if (folder == null)
		{
			// the player hasn't picked Raid Data Tracker's folder yet: nothing is read
			return;
		}
		importer.start(profile,
			() -> RaidDataImport.read(folder, accountHash),
			t, name -> changed(name, false));
	}

	/** Raid Data Tracker's folder, as the player picked it with RuneLite's file chooser (not kept between runs). */
	private volatile Filepath raidDataFolder;

	/** Asks for Raid Data Tracker's folder, then reads it for the account that is logged in. */
	private void chooseRaidDataFolder()
	{
		Filepath.Chooser chooser = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsDirectories()
			.setFileHidingEnabled(false)
			.setDialogTitle("Choose the Raid Data Tracker folder (raid-data tracker)");
		List<Filepath> picked = chooser.showDialog(client);
		if (picked == null || picked.isEmpty())
		{
			return;
		}
		raidDataFolder = picked.get(0);
		clientThread.invokeLater(this::importRaidTimes);
		ExpectedDropsPanel p = panel;
		if (p != null)
		{
			p.refresh();
		}
	}

	/** Notes who is hitting a group boss, each game tick. */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (tracker == null || !config.detectTeamSize())
		{
			return;
		}
		team.onTick(System.currentTimeMillis());
	}

	ViewOptions viewOptions()
	{
		Palette.setScheme(config.luckColours(), config.dryColour(), config.onRateColour());
		return new ViewOptions(config.itemView(), config.showGeValue(), config.showLuckGraph(),
			config.showTrip(), config.luckColours() == LuckColours.COLOUR_BLIND, config.showTopItems());
	}

	/** The player's raid, team and duo settings, as the numbers the drop maths needs. */
	RaidSettings raidSettings()
	{
		// raids On Rate didn't see use how you usually raid, where a setting is still as it came
		Tracker t = tracker;
		return new ConfigRaidSettings(config, t == null ? null : t::raidHabits);
	}

	/** Your real Slayer level (not boosted), -1 until the game has sent it. Set on the client thread, read by the panel. */
	private volatile int slayerLevel = -1;

	/** Per account: the last Slayer level seen, so the Time page can hide the Slayer bosses before the game sends the stats. */
	private static final String SLAYER_LEVEL_KEY = Keys.SLAYER_LEVEL;

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.SLAYER)
		{
			setSlayerLevel(event.getLevel());
		}
	}

	/** Takes a Slayer level (from the game, or the one remembered) and redraws the Time page when it changed. */
	private void setSlayerLevel(int level)
	{
		if (level < 1 || level == slayerLevel)
		{
			return;
		}
		slayerLevel = level;
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(ExpectedDropsConfig.GROUP, SLAYER_LEVEL_KEY, String.valueOf(level));
		}
		ExpectedDropsPanel p = panel;
		if (p != null)
		{
			// the Time page hides the Slayer bosses above it
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	/**
	 * Finds the Slayer level when logged in: from the game when it already has the stats (turning the
	 * plugin on while logged in sends no stat event), else the one remembered for this account.
	 * Runs on the client thread.
	 */
	private void readSlayerLevel()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int real = client.getRealSkillLevel(Skill.SLAYER);
		if (real > 1)
		{
			setSlayerLevel(real);
			return;
		}
		if (slayerLevel < 1 && configManager.getRSProfileKey() != null)
		{
			String saved = configManager.getRSProfileConfiguration(ExpectedDropsConfig.GROUP, SLAYER_LEVEL_KEY);
			try
			{
				setSlayerLevel(saved == null ? -1 : Integer.parseInt(saved));
			}
			catch (NumberFormatException e)
			{
				// not a level: wait for the game's
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// another account may log in: its level is not known yet
			slayerLevel = -1;
		}
		if ((event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING) && rolls != null)
		{
			// a new login sends the counters again: that's not collecting rewards
			rolls.forget();
		}
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadProfile();
			resolveItemIds();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// read once: shutDown clears these on another thread
		RollCounters r = rolls;
		if (tracker == null || r == null)
		{
			return;
		}
		r.onVarbit(event, this::changed);
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadProfile();
	}

	/** Settings the panel writes itself while the player clicks around: they change no numbers. */
	private static final Set<String> UI_STATE_KEYS = new HashSet<>(Arrays.asList(
		Keys.LAST_PAGE, "foldState", "showLuckGraph", "itemView"));

	/** The Loot Tracker was turned on or off: the panel's note follows. */
	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		if (LootTrackerStatus.isLootTracker(event.getPlugin()))
		{
			updateLootTrackerStatus();
		}
	}

	/**
	 * Tells the panel whether RuneLite's Loot Tracker is off. New drops only reach On Rate through it,
	 * so the panel says so; when it can't be told for sure, the panel says nothing.
	 */
	private void updateLootTrackerStatus()
	{
		ExpectedDropsPanel p = panel;
		if (p == null)
		{
			return;
		}
		boolean off = LootTrackerStatus.of(pluginManager) == LootTrackerStatus.State.DISABLED;
		SwingUtilities.invokeLater(() -> p.setLootTrackerOff(off));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ExpectedDropsConfig.GROUP.equals(event.getGroup()) && "chooseRaidDataFolder".equals(event.getKey()))
		{
			if ("true".equals(event.getNewValue()))
			{
				// a switch that acts as a button: back off first, then the chooser opens
				configManager.setConfiguration(ExpectedDropsConfig.GROUP, "chooseRaidDataFolder", false);
				SwingUtilities.invokeLater(this::chooseRaidDataFolder);
			}
			return;
		}
		if (ExpectedDropsConfig.GROUP.equals(event.getGroup()) && panel != null
			&& !DATA_KEY.equals(event.getKey()) && !event.getKey().startsWith(KILLS_KEY)
			&& !event.getKey().startsWith(RAIDS_KEY))
		{
			// a setting can change the numbers (raid chances, duo kills): don't draw ones worked out before it.
			// Not for what the panel itself remembers while you click around (its tab, the graph folded
			// open), nor for per-account values: those change no numbers.
			AccountPrecompute pc = precompute;
			if (pc != null && event.getProfile() == null && !UI_STATE_KEYS.contains(event.getKey()))
			{
				pc.supersedeKeepingMove();
			}
			ExpectedDropsPanel p = panel;
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		Tracker t = tracker;
		if (event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			// the Chambers raid time comes this way; nothing else from this type is counted
			if (t != null)
			{
				String timed = t.onTimeMessage(event.getMessage(), System.currentTimeMillis());
				if (timed != null)
				{
					changed(timed, true);
				}
			}
			return;
		}
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		if (t == null)
		{
			return;
		}
		String boss = t.onChatMessage(event.getMessage(), System.currentTimeMillis());
		if (boss != null)
		{
			// this kill is counted: the next one starts with nobody seen
			team.killCounted(boss);
			changed(boss, true);
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// read once: shutDown clears it on another thread
		Tracker t = tracker;
		if (t == null)
		{
			return;
		}
		Collection<ItemStack> stacks = event.getItems();
		List<Tracker.LootItem> items = new ArrayList<>();
		if (stacks != null)
		{
			for (ItemStack stack : stacks)
			{
				ItemComposition comp = itemManager.getItemComposition(stack.getId());
				items.add(new Tracker.LootItem(comp.getName(), stack.getQuantity()));
			}
		}
		String boss = t.onLoot(event.getName(), event.getAmount(), items, System.currentTimeMillis());
		if (boss != null)
		{
			changed(boss, true);
		}
		else if (t.onOtherLoot(event.getName(), items))
		{
			// a visage from a regular dragon, a chainbody from a smoke devil: noted as not from a boss
			changed(null, false);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.DOM_SCOREBOARD)
		{
			clientThread.invokeLater(this::readDoomScoreboard);
		}
	}

	private void readDoomScoreboard()
	{
		// read once: shutDown clears it on another thread
		Tracker tracker = this.tracker;
		if (tracker == null)
		{
			return;
		}
		Map<Integer, Integer> levels = new TreeMap<>();
		for (int level = 1; level <= DOOM_LEVEL_TOTALS.length; level++)
		{
			Widget w = client.getWidget(DOOM_LEVEL_TOTALS[level - 1]);
			if (w == null || w.getText() == null)
			{
				continue;
			}
			String digits = w.getText().replaceAll("[^0-9]", "");
			if (!digits.isEmpty())
			{
				levels.put(level, Integer.parseInt(digits));
			}
		}
		String boss = tracker.onDelveScoreboard(levels);
		if (boss != null)
		{
			save();
			ExpectedDropsPanel p = panel;
			if (p != null)
			{
				SwingUtilities.invokeLater(() -> p.showBoss(boss));
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.COLLECTION_DRAW_LIST)
		{
			return;
		}
		// Widgets are filled in by the end of the script; read them on the next tick of the client thread.
		clientThread.invokeLater(() ->
		{
			ClogReader reader = clog;
			if (tracker != null && reader != null)
			{
				reader.read();
			}
		});
	}

	private void changed(String boss, boolean fromKill)
	{
		// read once: shutDown clears these on another thread
		Tracker t = tracker;
		PriceBook book = prices;
		AccountPrecompute pc = precompute;
		if (t == null)
		{
			return;
		}
		recordHistoryPoint(false);
		save();
		if (book != null)
		{
			book.refresh(false);
		}
		if (fromKill)
		{
		}
		ExpectedDropsPanel p = panel;
		if (p == null || pc == null)
		{
			return;
		}
		boolean follow = fromKill && config.autoSwitch();
		// the account and the Time page are worked out on a worker thread; the panel draws once they're ready
		// a burst draws once; the newest boss to follow is kept even if a later event doesn't follow
		pc.request(raidSettings(), viewOptions().isGeValue() ? this::priceOf : null,
			follow ? () -> p.showBoss(boss) : null, p::refresh);
	}

	private void loadProfile()
	{
		readSlayerLevel();
		ProfileStore s = store;
		if (tracker == null || s == null)
		{
			return;
		}
		String key = configManager.getRSProfileKey();
		boolean loaded = s.load(key, tracker);
		ExpectedDropsPanel shown = panel;
		ExpectedDropsPanel.ProfileState state = profileState(loaded, s.loadFailed(), s.loadedProfile() != null,
			key != null, client.getGameState() == GameState.LOGGED_IN);
		if (!loaded)
		{
			if (shown != null && state != null)
			{
				SwingUtilities.invokeLater(() -> shown.setProfileState(state));
			}
			return;
		}
		// another account: numbers worked out for the last one must never be drawn
		AccountPrecompute pc = precompute;
		if (pc != null)
		{
			pc.supersede();
		}
		team.clear();
		importRaidTimes();
		ExpectedDropsPanel p = panel;
		if (p != null)
		{
			// Open on the account overview: how dry or spooned the account is overall.
			SwingUtilities.invokeLater(() ->
			{
				p.setProfileState(ExpectedDropsPanel.ProfileState.LOADED);
				p.showBoss(ExpectedDropsPanel.ACCOUNT);
			});
		}
	}

	/**
	 * What the panel should say after a load attempt, or null to leave it as it is.
	 *
	 * @param loadedNow  the attempt loaded a profile
	 * @param failed     the attempt failed half way (ProfileStore#loadFailed)
	 * @param anyLoaded  a profile is loaded (this one or an earlier one)
	 * @param hasProfile RuneLite named a profile to load
	 * @param loggedIn   the game is logged in
	 */
	static ExpectedDropsPanel.ProfileState profileState(boolean loadedNow, boolean failed, boolean anyLoaded,
		boolean hasProfile, boolean loggedIn)
	{
		if (loadedNow)
		{
			return ExpectedDropsPanel.ProfileState.LOADED;
		}
		if (failed)
		{
			return ExpectedDropsPanel.ProfileState.FAILED;
		}
		if (anyLoaded)
		{
			// already the one loaded, or an earlier account's data still shown: nothing changes
			return null;
		}
		// logged in but RuneLite hasn't named the profile yet: it follows in a moment
		return loggedIn && !hasProfile ? ExpectedDropsPanel.ProfileState.LOADING : ExpectedDropsPanel.ProfileState.NOT_LOGGED_IN;
	}

	/**
	 * Today's point for the luck graph. It adds up the whole account, so after a kill it's only
	 * worked out again every few minutes (and on a new day, and when On Rate stops).
	 */
	private void recordHistoryPoint(boolean now)
	{
		Tracker t = tracker;
		if (t == null)
		{
			return;
		}
		long millis = System.currentTimeMillis();
		long day = LocalDate.now().toEpochDay();
		if (now || day != historyPointDay || millis - historyPointAt >= HISTORY_POINT_MILLIS)
		{
			t.recordHistory(day, raidSettings());
			historyPointAt = millis;
			historyPointDay = day;
		}
	}

	void save()
	{
		ProfileStore s = store;
		if (s != null)
		{
			s.save(tracker);
		}
	}

	static String raidsKey(String bossName)
	{
		return Keys.raidsKey(bossName);
	}

	static String killsKey(String bossName)
	{
		return Keys.killsKey(bossName);
	}

	/**
	 * Looks up item ids for every name in the drop data once, so the panel can show icons.
	 * Runs on the client thread.
	 */
	private void resolveItemIds()
	{
		PriceBook book = prices;
		if (tracker == null || book == null || !book.resolve(client))
		{
			return;
		}
		ExpectedDropsPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::refresh);
		}
	}

	long priceOf(String itemName)
	{
		PriceBook book = prices;
		return book == null ? 0 : book.priceOf(itemName);
	}

	int itemIdFor(String itemName)
	{
		PriceBook book = prices;
		return book == null ? -1 : book.idOf(itemName);
	}
}

