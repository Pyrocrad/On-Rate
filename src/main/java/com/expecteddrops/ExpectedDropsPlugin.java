package com.expecteddrops;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.raids.RaidPointsReader;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.tracking.Tracker;
import com.expecteddrops.tracking.TrackerState;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.tracking.TripStats;
import com.expecteddrops.ui.ExpectedDropsPanel;
import com.expecteddrops.ui.Palette;
import com.expecteddrops.ui.ViewOptions;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "On Rate",
	description = "Shows whether you are on rate: expected vs received uniques for every boss, based on your kill count",
	tags = {"boss", "drops", "dry", "luck", "spoon", "collection", "log", "uniques", "expected", "rate", "doom", "delve"}
)
public class ExpectedDropsPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(ExpectedDropsPlugin.class);
	static final String DATA_KEY = "progress";
	/** Each boss's History is saved under its own key: "kills." plus the boss name. */
	static final String KILLS_KEY = "kills.";
	private static final java.lang.reflect.Type KILLS_TYPE =
		new com.google.gson.reflect.TypeToken<List<com.expecteddrops.tracking.KillEntry>>()
		{
		}.getType();
	/** Script that draws a collection log page. */
	private static final int COLLECTION_DRAW_LIST = 2731;
	/** Doom of Mokhaiotl scoreboard: completions per delve level in children 46, 49, ... 70 (the last is "8+"). */
	private static final int DOOM_SCOREBOARD_GROUP = 920;
	private static final int DOOM_SCOREBOARD_FIRST_CHILD = 46;

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
	private OverlayManager overlayManager;

	private OnRateOverlay overlay;
	/** What the overlay shows; worked out after each kill on the client thread. */
	private volatile OverlayInfo overlayInfo;

	/** Reads raid points at the end of each raid (see RaidPointsReader for the credits). */
	private RaidPointsReader raidPoints;

	/** Bosses we already reminded about this session, so the reminder shows once. */
	private final Set<String> remindedThisSession = new HashSet<>();

	private Tracker tracker;
	private ExpectedDropsPanel panel;
	private NavigationButton navButton;
	private String loadedProfile;
	private final Map<String, Integer> itemIds = new ConcurrentHashMap<>();
	private boolean itemIdsResolved;
	/** GE price per normalized item name, from RuneLite's own price list. Untradeables are 0. */
	private final Map<String, Long> prices = new ConcurrentHashMap<>();
	private long pricesRefreshedAt;
	private static final long PRICE_REFRESH_MILLIS = 10 * 60_000;

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
	};

	@Override
	protected void startUp() throws Exception
	{
		for (String key : OLD_SETTINGS)
		{
			if (configManager.getConfiguration(ExpectedDropsConfig.GROUP, key) != null)
			{
				configManager.unsetConfiguration(ExpectedDropsConfig.GROUP, key);
			}
		}
		tracker = new Tracker(loadCatalog());
		tracker.setSettings(raidSettings());
		tracker.setTeamSizeDetector(() -> config.detectTeamSize() ? playersAround() : 0);
		panel = new ExpectedDropsPanel(tracker, itemManager, this::raidSettings, this::itemIdFor, this::save,
			config::showWelcome, this::dismissWelcome, this::priceOf, this::viewOptions);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("On Rate")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		overlay = new OnRateOverlay(this, config, () -> overlayInfo);
		overlayManager.add(overlay);

		openPanelOnce();

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
		save();
		if (raidPoints != null)
		{
			eventBus.unregister(raidPoints);
			raidPoints = null;
		}
		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
		}
		overlayInfo = null;
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		tracker = null;
		loadedProfile = null;
		itemIds.clear();
		itemIdsResolved = false;
		prices.clear();
		pricesRefreshedAt = 0;
	}

	/** Saved once On Rate has opened its panel for a new player. */
	private static final String FIRST_RUN_KEY = "firstRunDone";

	/**
	 * The very first time On Rate starts for a player, open its panel so they see the
	 * welcome guide. Never again after that.
	 */
	private void openPanelOnce()
	{
		if (configManager.getConfiguration(ExpectedDropsConfig.GROUP, FIRST_RUN_KEY) != null || !config.showWelcome())
		{
			return;
		}
		configManager.setConfiguration(ExpectedDropsConfig.GROUP, FIRST_RUN_KEY, "true");
		NavigationButton button = navButton;
		SwingUtilities.invokeLater(() -> clientToolbar.openPanel(button));
	}

	/**
	 * Opens On Rate's settings, so a new player sees the choices that change their numbers
	 * (luck view, duo bosses, raid chances) right after the welcome guide. RuneLite's own
	 * config plugin opens the panel for this event, just like "Configure" on an overlay.
	 */
	private void openSettings()
	{
		OnRateOverlay forPlugin = overlay;
		if (forPlugin == null)
		{
			return;
		}
		SwingUtilities.invokeLater(() -> eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "On Rate"), forPlugin)));
	}

	/** Players within this many tiles of you at a group boss kill count as your team. */
	private static final int TEAM_RADIUS_TILES = 15;
	private static final int LOCAL_UNITS_PER_TILE = 128;

	/** You plus the players around you (called on the client thread, from the kill message). */
	private int playersAround()
	{
		Player me = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		LocalPoint here = me == null ? null : me.getLocalLocation();
		if (here == null || view == null)
		{
			return 0;
		}
		int count = 0;
		for (Player p : view.players())
		{
			LocalPoint there = p == null ? null : p.getLocalLocation();
			if (there == null)
			{
				continue;
			}
			int dx = Math.abs(there.getX() - here.getX()) / LOCAL_UNITS_PER_TILE;
			int dy = Math.abs(there.getY() - here.getY()) / LOCAL_UNITS_PER_TILE;
			if (Math.max(dx, dy) <= TEAM_RADIUS_TILES)
			{
				count++;
			}
		}
		return Math.max(1, count);
	}

	private BossCatalog loadCatalog() throws Exception
	{
		// Only the rates bundled with the plugin: On Rate reads and writes no files.
		try (InputStream in = getClass().getResourceAsStream("bosses.json");
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return BossCatalog.load(gson, reader);
		}
	}

	ViewOptions viewOptions()
	{
		return new ViewOptions(config.itemView(), config.showGeValue(), config.showLuckGraph(),
			config.showTrip(), config.colourBlind(), config.showTopItems());
	}

	/** Verdict, driest item and trip of the boss you are killing, for the overlay. */
	private void updateOverlayInfo()
	{
		TripStats trip = tracker.tripStats();
		if (trip == null)
		{
			overlayInfo = null;
			return;
		}
		BossStats stats = tracker.stats(trip.getBoss(), raidSettings());
		if (stats == null)
		{
			overlayInfo = null;
			return;
		}
		Palette.setColourBlind(config.colourBlind());
		RowStats any = stats.getAnyRow();
		double luck = any.getLuck();
		String verdict = Double.isNaN(luck) ? "-" : DropMath.verdict(luck);
		RowStats driest = null;
		for (RowStats row : stats.getRows())
		{
			if (row.getStreak() == null || row.getRate() <= 0 || row.isComplete())
			{
				continue;
			}
			if (driest == null || row.getStreakMultiple() > driest.getStreakMultiple())
			{
				driest = row;
			}
		}
		String driestText = null;
		String driestItem = null;
		java.awt.Color driestColor = java.awt.Color.WHITE;
		if (driest != null)
		{
			double m = driest.getStreakMultiple();
			driestItem = shortName(driest.getLabel());
			driestText = String.format("%s%,d kill%s (%.1fx)", driest.isStreakExact() ? "" : "at least ", driest.getStreak(),
				driest.getStreak() == 1 ? "" : "s", m);
			driestColor = Palette.luck(Math.exp(-m)).brighter();
		}
		String tripText = String.format("%,d kill%s \u00b7 %d drop%s", trip.getKills(), trip.getKills() == 1 ? "" : "s",
			trip.getReceived(), trip.getReceived() == 1 ? "" : "s");
		overlayInfo = new OverlayInfo(trip.getBoss(), verdict, Palette.luck(luck).brighter(),
			String.format("%,d / %.1f", any.getReceived(), any.getExpected()), driestItem, driestText, driestColor,
			tripText, trip.getLastMillis());
	}

	/** Item names that fit the overlay: cut at a word, "Deadeye prayer scroll" becomes "Deadeye prayer...". */
	static String shortName(String label)
	{
		final int max = 18;
		if (label.length() <= max)
		{
			return label;
		}
		int cut = label.lastIndexOf(' ', max - 3);
		return label.substring(0, cut > 0 ? cut : max - 3) + "...";
	}

	RaidSettings raidSettings()
	{
		return new RaidSettings()
		{
			@Override
			public double uniqueChance(String raid)
			{
				return raidChance(raid);
			}

			@Override
			public int teamSize(String raid)
			{
				return raid.startsWith("tob") ? tobTeamSize() : 1;
			}

			@Override
			public double contribution(String team, int kills)
			{
				if (!isDuoTeam(team) || kills <= 0)
				{
					return 1.0;
				}
				int duo = duoKills(team, kills);
				return (kills - duo + 0.5 * duo) / kills;
			}

			@Override
			public String contributionNote(String team, int kills)
			{
				if (!isDuoTeam(team))
				{
					return null;
				}
				String setting = "yama".equals(team) ? "Yama" : "Royal Titans";
				int duo = duoKills(team, kills);
				if (duo == 0)
				{
					return "Counted as solo kills. If you duo, change '" + setting + "' in the plugin settings.";
				}
				if (duo >= kills)
				{
					return "Counted as duo kills: about half the uniques of a solo kill (the pet chance stays the same). "
						+ "If you solo, change '" + setting + "' in the plugin settings.";
				}
				return String.format("Counted as %,d solo and %,d duo kills (a duo gives about half the uniques).",
					kills - duo, duo);
			}
		};
	}

	private int tobTeamSize()
	{
		return Math.max(1, Math.min(5, config.tobTeamSize()));
	}

	private static boolean isDuoTeam(String team)
	{
		return "royalTitans".equals(team) || "yama".equals(team);
	}

	/** How many of these kills count as duo kills, from the boss's solo/duo setting. */
	private int duoKills(String team, int kills)
	{
		boolean yama = "yama".equals(team);
		DuoMode mode = yama ? config.yamaMode() : config.royalTitansMode();
		int solo = yama ? config.yamaSoloKills() : config.royalTitansSoloKills();
		switch (mode)
		{
			case DUO:
				return kills;
			case SOLO:
				return 0;
			case MIXED:
				return kills - Math.max(0, Math.min(kills, solo));
			default:
				return kills;
		}
	}

	private double raidChance(String raid)
	{
		double percent;
		switch (raid)
		{
			case "cox":
				percent = config.coxUniqueChance();
				break;
			case "coxCm":
				percent = config.coxCmUniqueChance();
				break;
			case "tob":
				percent = config.tobTeamChance() / tobTeamSize();
				break;
			case "tobHm":
				percent = config.tobHmTeamChance() / tobTeamSize();
				break;
			case "toaEntry":
				percent = config.toaEntryUniqueChance();
				break;
			case "toa":
				percent = config.toaUniqueChance();
				break;
			case "toaExpert":
				percent = config.toaExpertUniqueChance();
				break;
			default:
				percent = 0;
		}
		return Math.max(0, Math.min(100, percent)) / 100.0;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadProfile();
			resolveItemIds();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadProfile();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ExpectedDropsConfig.GROUP.equals(event.getGroup()) && panel != null
			&& !DATA_KEY.equals(event.getKey()) && !event.getKey().startsWith(KILLS_KEY))
		{
			SwingUtilities.invokeLater(panel::refresh);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String boss = tracker.onChatMessage(event.getMessage(), System.currentTimeMillis());
		if (boss != null)
		{
			changed(boss, true);
		}
		postNotes();
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
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
		String boss = tracker.onLoot(event.getName(), event.getAmount(), items, System.currentTimeMillis());
		if (boss != null)
		{
			changed(boss, true);
		}
		postNotes();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == DOOM_SCOREBOARD_GROUP)
		{
			clientThread.invokeLater(this::readDoomScoreboard);
		}
	}

	private void readDoomScoreboard()
	{
		Map<Integer, Integer> levels = new TreeMap<>();
		for (int level = 1; level <= 9; level++)
		{
			Widget w = client.getWidget(DOOM_SCOREBOARD_GROUP, DOOM_SCOREBOARD_FIRST_CHILD + (level - 1) * 3);
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
			if (panel != null)
			{
				SwingUtilities.invokeLater(() -> panel.showBoss(boss));
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != COLLECTION_DRAW_LIST)
		{
			return;
		}
		// Widgets are filled in by the end of the script; read them on the next tick of the client thread.
		clientThread.invokeLater(this::readCollectionLogPage);
	}

	private void readCollectionLogPage()
	{
		Widget header = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
		Widget itemsWidget = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);
		if (header == null || itemsWidget == null)
		{
			return;
		}
		Widget[] headerLines = header.getDynamicChildren();
		if (headerLines == null || headerLines.length == 0)
		{
			return;
		}
		String rawTitle = headerLines[0].getText();
		if (rawTitle == null || rawTitle.isEmpty())
		{
			return;
		}
		String title = Text.removeTags(rawTitle);

		List<String> counters = new ArrayList<>();
		for (int i = 1; i < headerLines.length; i++)
		{
			String text = headerLines[i].getText();
			if (text != null && !text.isEmpty())
			{
				counters.add(Text.removeTags(text));
			}
		}

		Map<String, Integer> items = new LinkedHashMap<>();
		Widget[] itemWidgets = itemsWidget.getDynamicChildren();
		if (itemWidgets != null)
		{
			for (Widget w : itemWidgets)
			{
				int id = w.getItemId();
				if (id <= 0)
				{
					continue;
				}
				String name = itemManager.getItemComposition(id).getName();
				// Obtained items are drawn fully opaque; missing ones are faded out.
				boolean obtained = w.getOpacity() == 0;
				items.put(name, obtained ? Math.max(1, w.getItemQuantity()) : 0);
				itemIds.putIfAbsent(BossCatalog.normalize(name), id);
			}
		}

		Set<String> changed = tracker.onClogPage(title, counters, items);
		if (!changed.isEmpty())
		{
			save();
			String first = changed.iterator().next();
			if (panel != null)
			{
				SwingUtilities.invokeLater(() -> panel.showBoss(first));
			}
		}
	}

	private void changed(String boss, boolean fromKill)
	{
		// one point per day for the luck graph
		tracker.recordHistory(java.time.LocalDate.now().toEpochDay(), raidSettings());
		if (fromKill)
		{
			updateOverlayInfo();
		}
		save();
		refreshPrices(false);
		if (fromKill)
		{
			remindToSync(boss);
		}
		if (panel == null)
		{
			return;
		}
		boolean follow = fromKill && config.autoSwitch();
		SwingUtilities.invokeLater(() ->
		{
			if (follow)
			{
				panel.showBoss(boss);
			}
			else
			{
				panel.refresh();
			}
		});
	}

	/** Posts drop and milestone messages from the tracker, if the player wants them. */
	private void postNotes()
	{
		for (com.expecteddrops.tracking.Note note : tracker.drainNotes())
		{
			boolean wanted = note.getKind() == com.expecteddrops.tracking.Note.Kind.DROP
				? config.dropMessages() : config.milestoneMessages();
			if (wanted)
			{
				postChat(note.getText());
			}
		}
	}

	private void postChat(String text)
	{
		String message = new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("On Rate: ")
			.append(ChatColorType.NORMAL)
			.append(text)
			.build();
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	/** Once per session, tells the player in chat which page loads this boss's history. */
	private void remindToSync(String boss)
	{
		if (!config.syncReminders() || !remindedThisSession.add(boss))
		{
			return;
		}
		String reminder = tracker.syncReminder(boss);
		if (reminder == null)
		{
			return;
		}
		postChat(reminder);
	}

	/** "Got it" on the welcome guide: hide it, then show the settings it talks about. */
	private void dismissWelcome()
	{
		configManager.setConfiguration(ExpectedDropsConfig.GROUP, "showWelcome", "false");
		openSettings();
	}

	private void loadProfile()
	{
		String profile = configManager.getRSProfileKey();
		if (profile == null || profile.equals(loadedProfile) || tracker == null)
		{
			return;
		}
		loadedProfile = profile;
		remindedThisSession.clear();
		TrackerState state = null;
		String json = configManager.getRSProfileConfiguration(ExpectedDropsConfig.GROUP, DATA_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				state = gson.fromJson(json, TrackerState.class);
			}
			catch (Exception e)
			{
				log.warn("Could not read saved On Rate data, starting fresh", e);
			}
		}
		tracker.setState(state);
		for (com.expecteddrops.data.BossDefinition boss : tracker.getCatalog().getBosses())
		{
			String kills = configManager.getRSProfileConfiguration(ExpectedDropsConfig.GROUP, killsKey(boss.getName()));
			if (kills == null || kills.isEmpty())
			{
				continue;
			}
			try
			{
				tracker.setKills(boss.getName(), gson.fromJson(kills, KILLS_TYPE));
			}
			catch (Exception e)
			{
				log.warn("Could not read the saved On Rate history of {}", boss.getName(), e);
			}
		}
		if (panel != null)
		{
			// Open on the account overview: how dry or spooned the account is overall.
			SwingUtilities.invokeLater(() -> panel.showBoss(ExpectedDropsPanel.ACCOUNT));
		}
	}

	void save()
	{
		if (tracker == null || loadedProfile == null)
		{
			return;
		}
		configManager.setRSProfileConfiguration(ExpectedDropsConfig.GROUP, DATA_KEY, gson.toJson(tracker.getState()));
		// only the History of bosses that changed is written again
		for (Map.Entry<String, List<com.expecteddrops.tracking.KillEntry>> e : tracker.changedKills().entrySet())
		{
			if (e.getValue().isEmpty())
			{
				configManager.unsetRSProfileConfiguration(ExpectedDropsConfig.GROUP, killsKey(e.getKey()));
			}
			else
			{
				configManager.setRSProfileConfiguration(ExpectedDropsConfig.GROUP, killsKey(e.getKey()), gson.toJson(e.getValue()));
			}
		}
	}

	static String killsKey(String bossName)
	{
		return KILLS_KEY + bossName.replaceAll("[^A-Za-z0-9]", "");
	}

	/**
	 * Looks up item ids for every name in the drop data once, so the panel can show icons.
	 * Runs on the client thread.
	 */
	private void resolveItemIds()
	{
		if (itemIdsResolved || tracker == null)
		{
			return;
		}
		Set<String> wanted = tracker.getCatalog().getAllItemNames();
		int count = client.getItemCount();
		for (int id = 0; id < count; id++)
		{
			ItemComposition comp = client.getItemDefinition(id);
			if (comp == null || comp.getNote() != -1 || comp.getPlaceholderTemplateId() != -1)
			{
				continue;
			}
			String key = BossCatalog.normalize(comp.getName());
			if (wanted.contains(key))
			{
				itemIds.putIfAbsent(key, id);
			}
		}
		itemIdsResolved = true;
		refreshPrices(true);
		if (panel != null)
		{
			SwingUtilities.invokeLater(panel::refresh);
		}
	}

	/**
	 * Copies the GE prices of all tracked items from RuneLite's price list. RuneLite keeps
	 * that list up to date itself; this plugin makes no web requests. Client thread only.
	 */
	private void refreshPrices(boolean force)
	{
		long now = System.currentTimeMillis();
		if (!force && now - pricesRefreshedAt < PRICE_REFRESH_MILLIS)
		{
			return;
		}
		pricesRefreshedAt = now;
		for (Map.Entry<String, Integer> e : itemIds.entrySet())
		{
			try
			{
				prices.put(e.getKey(), Math.max(0, itemManager.getItemPrice(e.getValue())));
			}
			catch (RuntimeException ex)
			{
				log.debug("No price for {}", e.getKey(), ex);
			}
		}
	}

	long priceOf(String itemName)
	{
		Long price = prices.get(BossCatalog.normalize(itemName));
		return price == null ? 0 : price;
	}

	int itemIdFor(String itemName)
	{
		Integer id = itemIds.get(BossCatalog.normalize(itemName));
		return id == null ? -1 : id;
	}
}
