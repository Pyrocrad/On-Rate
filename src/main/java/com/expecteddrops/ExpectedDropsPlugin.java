package com.expecteddrops;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.raids.RaidPointsReader;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.tracking.Tracker;
import com.expecteddrops.tracking.TrackerState;
import com.expecteddrops.ui.ExpectedDropsPanel;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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
	/** Script that draws a collection log page. */
	private static final int COLLECTION_DRAW_LIST = 2731;
	/** Doom of Mokhaiotl scoreboard: completions per delve level in children 46, 49, ... 70 (the last is "8+"). */
	private static final int DOOM_SCOREBOARD_GROUP = 920;
	private static final int DOOM_SCOREBOARD_FIRST_CHILD = 46;
	/** Put a file with this name here to use your own rates instead of the bundled ones. */
	static final File CUSTOM_DATA = new File(new File(RuneLite.RUNELITE_DIR, "on-rate"), "bosses.json");

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

	@Override
	protected void startUp() throws Exception
	{
		tracker = new Tracker(loadCatalog());
		tracker.setSettings(raidSettings());
		panel = new ExpectedDropsPanel(tracker, itemManager, this::raidSettings, this::itemIdFor, this::save,
			config::showWelcome, this::dismissWelcome, this::priceOf);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("On Rate")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

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

	private BossCatalog loadCatalog() throws Exception
	{
		if (CUSTOM_DATA.isFile())
		{
			try (Reader reader = new InputStreamReader(new FileInputStream(CUSTOM_DATA), StandardCharsets.UTF_8))
			{
				log.info("Using custom drop data from {}", CUSTOM_DATA);
				return BossCatalog.load(gson, reader);
			}
			catch (Exception e)
			{
				log.warn("Could not read {}, falling back to the bundled rates", CUSTOM_DATA, e);
			}
		}
		try (InputStream in = getClass().getResourceAsStream("bosses.json");
			Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
		{
			return BossCatalog.load(gson, reader);
		}
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
			public double contribution(String team, int kills)
			{
				if (!"royalTitans".equals(team) || kills <= 0)
				{
					return 1.0;
				}
				int duo = duoKills(kills);
				return (kills - duo + 0.5 * duo) / kills;
			}

			@Override
			public String contributionNote(String team, int kills)
			{
				if (!"royalTitans".equals(team))
				{
					return null;
				}
				int duo = duoKills(kills);
				if (duo == 0)
				{
					return "Counted as solo kills. If you duo, change 'Royal Titans' in the plugin settings.";
				}
				if (duo >= kills)
				{
					return "Counted as duo kills: about half the uniques of a solo kill (the pet chance stays the same).";
				}
				return String.format("Counted as %,d solo and %,d duo kills (a duo gives about half the uniques).",
					kills - duo, duo);
			}
		};
	}

	private int duoKills(int kills)
	{
		switch (config.royalTitansMode())
		{
			case DUO:
				return kills;
			case MIXED:
				return Math.max(0, Math.min(kills, config.royalTitansDuoKills()));
			default:
				return 0;
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
				percent = config.tobUniqueChance();
				break;
			case "tobHm":
				percent = config.tobHmUniqueChance();
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
		if (ExpectedDropsConfig.GROUP.equals(event.getGroup()) && !DATA_KEY.equals(event.getKey()) && panel != null)
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

	private void dismissWelcome()
	{
		configManager.setConfiguration(ExpectedDropsConfig.GROUP, "showWelcome", "false");
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
