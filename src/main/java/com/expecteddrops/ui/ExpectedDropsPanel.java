package com.expecteddrops.ui;

import com.onrate.engine.math.Luck;
import com.onrate.engine.math.Poisson;
import com.onrate.engine.math.Rates;
import com.onrate.engine.math.Untradeables;
import com.onrate.engine.math.ValueLuck;
import com.onrate.engine.math.Verdict;

import com.onrate.engine.data.BossDefinition;
import com.onrate.engine.data.DropDefinition;
import com.onrate.engine.stats.AccountStats;
import com.onrate.engine.stats.BossStats;
import com.onrate.engine.tracking.PastDropResult;
import com.onrate.engine.tracking.RaidInsights;
import com.onrate.engine.stats.RaidSettings;
import com.onrate.engine.stats.RowStats;
import com.onrate.engine.tracking.SyncStatus;
import com.onrate.engine.tracking.Tracker;
import static com.expecteddrops.ui.Texts.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;

/**
 * Side panel: pick a boss, see expected vs received for each unique.
 */
public class ExpectedDropsPanel extends PluginPanel
{
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExpectedDropsPanel.class);

	/** The "Any unique" row is set apart by weight, not colour: yellow means luck here. */

	/** First entry in the boss list: the whole account added up. */
	public static final String ACCOUNT = "Account overview";

	private enum Tab
	{
		EXPECTED, RECEIVED
	}

	/** Orders for the boss rows in the account overview. */
	private enum Sort
	{
		EXPECTED("Most expected"),
		DRIEST("Driest first"),
		LUCKIEST("Luckiest first"),
		VALUE("Luckiest by GP value"),
		STREAK("Longest dry streak"),
		GREEN_LOG("Closest to green log");

		private final String label;

		Sort(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final Tracker tracker;
	private final ItemManager itemManager;
	private final Supplier<RaidSettings> raidSettings;
	private final ToIntFunction<String> itemIdFor;
	private final Runnable save;
	private final BooleanSupplier showWelcome;
	private final Runnable dismissWelcome;
	private final ToLongFunction<String> priceOf;
	private final Supplier<ViewOptions> options;
	private final JLabel helpLabel = new JLabel("?");
	/** The "?" help and the About note as text you can open (click, Enter, Space), as well as hover; closed by default. */
	private final Card helpCard = new Card();
	private final JLabel helpText = new JLabel();
	private final Card aboutCard = new Card();
	private final JLabel aboutText = new JLabel();
	private String helpHtml;
	private String aboutHtml;
	private boolean helpOpen;
	private boolean aboutOpen;
	/** The page the help and About blocks belong to; they close when it changes. */
	private String helpPage;
	private JPanel tabsRow;

	private final Card welcomePanel = new Card();
	/** The welcome guide's and the notice's old edge (a bar down the left), for when the card look is off. */
	private javax.swing.border.Border welcomeBorder;
	private javax.swing.border.Border timesNoticeBorder;
	/** Inside a guide or notice card: the old padding, plus the outline. */
	private static final javax.swing.border.Border CARD_PADDING = new EmptyBorder(Theme.M + 1, Theme.M + 1, Theme.M + 1, Theme.M + 1);
	/** The verdict words in the welcome guide's key, with the luck each stands for: recoloured with the palette. */
	private final List<JLabel> welcomeVerdicts = new ArrayList<>();
	private static final double[] WELCOME_VERDICT_LUCK = {0.005, 0.05, 0.22, 0.40, 0.50, 0.60, 0.78, 0.95, 0.995};
	/** The guide's reference part (verdict key, limits, odd moments): folded until "How to read it" is clicked. */
	private final JPanel welcomeMore = new JPanel();
	private boolean welcomeMoreOpen;
	/** "3 of N pages opened · next: Vorkath" and its bar, inside the guide (N is the number of log pages On Rate reads). */
	private final JLabel welcomeProgress = new JLabel();
	private final TargetBar welcomeProgressBar = new TargetBar();
	private final JLabel setupHeader = new JLabel();
	/** One line, only when a log page was opened but could not be read. */
	private final JLabel setupProblem = new JLabel();
	/** "Loot Tracker is off": shown only when the plugin knows for sure that it is off. */
	private final JLabel lootTrackerOffLabel = new JLabel();
	private boolean lootTrackerOff;
	/** See {@link #setProfileState}. */
	private ProfileState profileState = ProfileState.LOADED;

	private boolean profileLoaded()
	{
		return profileState == ProfileState.LOADED;
	}
	private final JPanel setupList = new JPanel(new GridLayout(0, 1, 0, 2));
	/** The space under the collection log line, hidden with it. */
	private final java.awt.Component setupGap = spacer(Theme.M);
	private boolean setupExpanded;

	private final HintField searchField = new HintField("Search bosses...");
	/** Shown under the search box while nothing matches it; the page underneath stays as it was. */
	private final JLabel noMatchLabel = new JLabel();
	/** True while the search text matches no boss (the boss list is empty but the page is kept). */
	private boolean noMatch;
	/** The page last selected in the boss list, kept while a search with no match empties the list. */
	private String keptPage;
	private final JLabel accountLink = new JLabel("\u2039 Account");
	/** Takes the account link's place on the account overview: back to the boss you were on. */
	private final JLabel backLink = new JLabel("\u2039 Back");
	/** The last boss page shown, for the back link. */
	private String lastBoss;
	private final JPanel headerRow = new JPanel(new BorderLayout(6, 0));
	private final JComboBox<String> bossSelect = new JComboBox<>();
	/** Finds bosses by name and by item; built on first use. */
	private BossSearch bossSearch;
	/** The item a boss was found by while searching ("Magic fang +1"), by boss name; empty when not searching. */
	private java.util.Map<String, String> bossReasons = new java.util.HashMap<>();
	private final JLabel headerIcon = new JLabel();
	private final JLabel headerName = new JLabel();
	private final JLabel verdictLabel = new JLabel();
	private final JPanel graphPanel = new JPanel();
	/** Account overview: the 5 driest or luckiest items across every boss. */
	private final JPanel topItemsPanel = new JPanel();
	/** Items tab list: driest, luckiest or pets. */
	private int topMode = TOP_DRIEST;
	public static final int TOP_DRIEST = 0;
	public static final int TOP_LUCKIEST = 1;
	public static final int TOP_PETS = 2;
	/** Items tab view of single drops: luckiest drops and longest waits. */
	public static final int TOP_DROPS = 3;
	/** Items tab view of the average time to get each item you don't have. */
	public static final int TOP_TIME = 4;
	/** Pets shown per group in the Items tab: the luckiest ones you have, the driest ones you don't. */
	static final int TOP_PETS_SHOWN = 5;
	/** Latest uniques shown on the Recent tab before "show all". */
	static final int RECENT_SHOWN = 10;
	/** Account overview: showing the Items tab (the top items) instead of the bosses. */
	private int accountTab = TAB_BOSSES;
	/** False while the panel is closed in the sidebar (see {@link #onActivate()}). */
	private boolean shown = true;
	private boolean outOfDate;
	/**
	 * Whether the panel is on screen, for other threads: the plugin works out numbers in the background
	 * only for a panel someone can see. Set on the Swing thread only.
	 */
	private volatile boolean onScreen;
	private static final int TAB_BOSSES = 0;
	private static final int TAB_ITEMS = 1;
	private static final int TAB_RECENT = 2;
	/** Whether the History section on boss pages is open (it starts folded). */
	private boolean historyOpen;
	/**
	 * History's row limit: 10, then 50, then all (0). Goes back to 10 when another boss is opened. False keeps
	 * the old fixed 10 and no link; the pixel parity test turns it off (HistoryMoreTest covers it).
	 */
	private boolean historyMore = true;
	private int historyLimit = 10;
	private String historyLimitBoss;
	/**
	 * The raid sections (purple chance by setup, personal bests, points over time) and the PB tags
	 * in History. Always on for players; the pixel parity test turns it off, because the original
	 * panel it compares with has none of them.
	 */
	private boolean raidExtras = true;
	/**
	 * The verdict tiles' look for the rest of the panel: lists in rounded, outlined cards, the trip card with its
	 * verdict edge, one pill and one tab style, and the Luck over time window row (30 days | 90 days | All). Always on for players; the pixel parity test turns it off, because
	 * the original panel it compares with is drawn the old way.
	 */
	private boolean boxes = true;
	/**
	 * The line under the verdict tiles takes turns with other true facts ({@link Texts#notableLines}), under any
	 * verdict: the next one each time the page is opened or a kill comes in, or on a click. Always on for
	 * players; the pixel parity test turns it off (the original shows one line, under "On rate" only).
	 */
	private boolean moreFacts = true;
	/**
	 * The trip card says "Too early" instead of a verdict under {@link Verdict#TRIP_TOO_EARLY_BELOW} uniques expected.
	 * Always on for players; the pixel parity test turns it off (the original shows a verdict from the first kill).
	 */
	private boolean tripEarly = true;
	/**
	 * The "Teams" row under History at the group bosses (kills per team size and how they count towards
	 * expected). Always on for players; the pixel parity test turns it off, because the original panel
	 * it compares with has no such row.
	 */
	private boolean teamSplit = true;
	/**
	 * The thin bar under the trip card's sentence. Off for players (the sentence and the rows say the same); the
	 * pixel parity test turns it on, because the original panel still draws it.
	 */
	private boolean tripBar = false;
	/**
	 * The collection log line and the guide's progress line say what is left to open ("3 bosses you killed not
	 * loaded yet", "Collection log: 4 pages not opened yet") with one verb, "opened". Always on for players; the
	 * pixel parity test turns it off (the original says "Load 3 bosses you killed" and "pages loaded").
	 */
	private boolean setupWording = true;
	/**
	 * A small triangle after a coloured unique count on the Recent tab, so the colour is not the only sign of
	 * "clearly more" or "clearly fewer". Always on for players; the pixel parity test turns it off (the original
	 * draws the count in colour only).
	 */
	private boolean uniqueMarks = true;
	/** Per page: which fact is up, and the expected count it was picked at (a kill changes it). */
	private final java.util.Map<String, Integer> factTurns = new java.util.HashMap<>();
	private final java.util.Map<String, Double> factSeenAt = new java.util.HashMap<>();
	/** The page the line was last drawn for, and how many facts it had to choose from. */
	private String factPage;
	private int factCount;
	/** Whether each raid section is open (they start folded, like History). */
	private boolean setupsOpen;
	private boolean bestsOpen;
	/** Raids left for the log: folded open or not. */
	private boolean logOpen;
	private boolean pointsOpen;
	/** Purple chance by setup: the GP value tab instead of the purple chance tab. */
	private boolean setupsGp;
	/** Purple chance by setup: how the raids are grouped ({@link RaidInsights#BY_SETUP} and so on). */
	private int setupsBy = RaidInsights.BY_SETUP;
	/** Points over time: purple chance instead of points. */
	private boolean pointsChance;
	private final LuckGraph graph = new LuckGraph();
	private final JLabel graphHint = new JLabel();
	/** The Luck over time window row (30 days | 90 days | All); only drawn with {@link #boxes}, and only for long histories. */
	private final JPanel graphWindowRow = new JPanel();
	private final JLabel graphNote = new JLabel();
	/** Luck over time: days shown (30 or 90), or 0 for all of it. Not saved: it is All again after a restart. */
	private int graphWindow;
	private final JComboBox<Sort> sortSelect = new JComboBox<>(Sort.values());
	/** True while the sort list gains or loses its GP value option, so that is not taken for a click. */
	private boolean sortOptionsUpdating;
	private final JPanel sortRow = new JPanel(new BorderLayout(4, 0));
	private final JLabel sortLabel = new JLabel("Sort:");
	/** Closest to green log: whether pets count. Not saved: off again after a restart. */
	private boolean greenLogPets;
	/** The right end of the Sort row: the Pets slider and the View button. It shows only while one of them does, so it takes no room otherwise. */
	private final JPanel sortEnd = new JPanel();
	private final PetsSwitch petsSwitch = new PetsSwitch(on ->
	{
		greenLogPets = on;
		refresh();
	});
	/** The pages of the "Closest to green log" list being drawn, by boss; null for any other sort. */
	private java.util.Map<String, com.onrate.engine.stats.GreenLog.Page> greenLogPages;
	/**
	 * False draws the layout from before the View buttons: the key to the bars on the ITEMS line, no button on the Sort
	 * row. Only the pixel comparison with the original panel turns it off (see PanelPixelParityTest).
	 */
	private boolean viewButtons = true;
	/**
	 * The "Trips this session" list on the Recent tab. Always on for players; the pixel parity test turns it
	 * off, because the original panel it compares with has none (TripListTest covers the rows).
	 */
	private boolean tripList = true;
	/**
	 * True once the page being drawn has shown something the item view changes (bars, table, rings or gauge). The View
	 * buttons are only shown then: on a page without such rows (Time, Single drops, an empty list, a boss whose
	 * items are all shared) they would do nothing.
	 */
	private boolean viewApplies;
	/**
	 * Lists that stop at a few entries (the Pets groups, Recent's latest uniques) say how many there are and let you
	 * "show all". False draws them capped without the note, as before. Only the pixel comparison with the original
	 * panel turns it off (see PanelPixelParityTest; CappedListNotesTest covers the notes).
	 */
	private boolean cappedListNotes = true;
	/** Recent: the whole list of latest uniques instead of the newest {@link #RECENT_SHOWN}. */
	private boolean recentAll;
	/** Pets: the whole list of the pets you have, and of the pets you don't, instead of the first {@link #TOP_PETS_SHOWN}. */
	private boolean petsHaveAll;
	private boolean petsDryAll;
	/**
	 * Notes under the Single drops and Time pages are as wide as the panel's text ({@link Theme#TEXT_WIDTH}), so they wrap
	 * instead of running past the right edge. Always on for players; the pixel parity test turns it off, because the
	 * original panel sets them wider than the panel (NoteWidthTest covers the fix).
	 */
	private boolean fixedNoteWidths = true;

	/** The width in pixels of a note's text: the panel's text width, or the original's {@code legacyPx} for the parity test. */
	private int noteWidth(int legacyPx)
	{
		return fixedNoteWidths ? Theme.TEXT_WIDTH : legacyPx;
	}

	/**
	 * Long names in the Time, Items and account rows break at spaces onto more lines (see {@link VerdictTile#wrapIfLong})
	 * and the chance rings' names keep two lines ending in "...", instead of Swing cutting them off. Always on for
	 * players; the pixel parity test turns it off, because the original panel cuts names off.
	 */
	private boolean wrapNames = true;
	/** Width a row has for its contents with the scrollbar showing (the panel is 225 wide), and the most lines a name takes. */
	private static final int ROW_WIDTH = 214;
	private static final int NAME_LINES = 3;
	/**
	 * Item rows (the bars view) can take the keyboard focus, carry the item's name for screen readers and show their
	 * tooltip while focused. Nothing is drawn differently, and no name goes inside the bars. Always on for players;
	 * the pixel parity test turns it off, because the original panel has no focusable rows.
	 */
	private boolean itemNames = true;
	/** Saves the chosen item view (the same setting as Display > Item view). */
	private java.util.function.Consumer<ItemView> setItemView = view -> { };
	/** The View button on the account overview's Sort row, and the one on a boss page's Items line. */
	private final ViewChip accountViewChip = new ViewChip(view -> pickView(view));
	private final ViewChip bossViewChip = new ViewChip(view -> pickView(view));
	/** Boss pages: the "ITEMS" line with the View button, and the key to the bars under it. */
	private final JPanel itemsHeading = new JPanel();
	private final JLabel itemsLegend = new JLabel("bar = got · line = expected");
	private final JLabel kcLabel = new JLabel();
	private final JLabel syncLabel = new JLabel();
	/** Boss pages: GE value received vs expected, weighed by price (the verdict only counts). */
	private final JLabel valueLabel = new JLabel();
	/** Two answers side by side: how many uniques, and what they're worth. */
	private final JPanel verdictTiles = new JPanel()
	{
		/** As tall as its tiles are now: a tile that wraps a long line onto a second one (see VerdictTile) makes it taller. */
		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	};
	/** Under an "On rate" verdict: one notable fact (the driest or luckiest item), or hidden. */
	private final JLabel notableLabel = new JLabel();
	/** Account overview: how long ago the last unique was ("Last unique 12 days ago: ..."). */
	private final JLabel lastUniqueLabel = new JLabel();
	private final VerdictTile uniquesTile = new VerdictTile("UNIQUES");
	private final VerdictTile valueTile = new VerdictTile("GP VALUE");
	/** Boss pages: a tip when the numbers lean on a setting (raid purple chance, solo/duo). */
	private final JLabel hintLabel = new JLabel();
	/** Opens or closes the Luck over time section (remembered; see {@link #setGraphOpener}). */
	private java.util.function.Consumer<Boolean> setGraphOpen = open -> { };
	private final JLabel graphTitle = new JLabel();
	/** Once, during a long trip: a line on the trip card offering the in-game overlay. */
	private JLabel overlayOfferLabel;
	private BooleanSupplier overlayOn = () -> true;
	private BooleanSupplier overlayOfferSeen = () -> true;
	private Runnable markOverlayOfferSeen = () -> { };
	private Runnable turnOnOverlay = () -> { };
	private boolean overlayOfferOnScreen;
	/** A trip this long (kills, or minutes) is when the overlay is worth offering. */
	static final int OVERLAY_OFFER_KILLS = 30;
	static final int OVERLAY_OFFER_MINUTES = 45;
	private final JLabel summaryLabel = new JLabel();
	private final JLabel levelsLabel = new JLabel();
	private final JButton expectedTab = new JButton("Expected");
	private final JButton receivedTab = new JButton("Received");
	private final JPanel rowsPanel = new JPanel();
	private final JLabel noteLabel = new JLabel();
	private final JLabel resetButton = new JLabel("<html><u>Reset this boss\u2026</u></html>");
	/** Boss pages: the explanations behind the numbers (hover only), first on the footer line. */
	private final JLabel aboutLink = new JLabel();
	private final JLabel aboutSep = new JLabel();
	private final JLabel resetSep = new JLabel();
	/** Account overview: copies your verdicts as text to paste anywhere. Only shown once a boss is loaded. */
	private final JLabel shareLink = new JLabel("<html><u>Copy my luck</u></html>");
	private final JLabel shareSep = new JLabel();
	/** What "Copy my luck" copies, or null when there is nothing to share on this page. */
	private String shareText;

	private Tab tab = Tab.EXPECTED;
	private boolean updatingSelection;

	public ExpectedDropsPanel(Tracker tracker, ItemManager itemManager, Supplier<RaidSettings> raidSettings,
		ToIntFunction<String> itemIdFor, Runnable save, BooleanSupplier showWelcome, Runnable dismissWelcome,
		ToLongFunction<String> priceOf)
	{
		this(tracker, itemManager, raidSettings, itemIdFor, save, showWelcome, dismissWelcome, priceOf, () -> ViewOptions.DEFAULT);
	}

	public ExpectedDropsPanel(Tracker tracker, ItemManager itemManager, Supplier<RaidSettings> raidSettings,
		ToIntFunction<String> itemIdFor, Runnable save, BooleanSupplier showWelcome, Runnable dismissWelcome,
		ToLongFunction<String> priceOf, Supplier<ViewOptions> options)
	{
		super();
		this.options = options;
		this.priceOf = priceOf;
		this.showWelcome = showWelcome;
		this.dismissWelcome = dismissWelcome;
		this.tracker = tracker;
		this.itemManager = itemManager;
		this.raidSettings = raidSettings;
		this.itemIdFor = itemIdFor;
		this.save = save;

		setBorder(new EmptyBorder(8, 6, 8, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout(0, 6));
		// catches up as soon as the panel is on screen again, even without RuneLite's onActivate
		addHierarchyListener(e ->
		{
			if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0)
			{
				onScreen = isShowing();
			}
			if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && outOfDate)
			{
				refresh();
			}
		});

		keepTooltipsOpen();

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		JLabel title = new JLabel("On Rate");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		helpLabel.setFont(FontManager.getRunescapeBoldFont());
		helpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		helpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		helpLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleHelp();
			}
		});
		Activatable.install(helpLabel, "Help for this page", this::toggleHelp);
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		titleRow.add(title, BorderLayout.WEST);
		// one click back to the account overview, wherever you are (only on a boss page)
		installTitleLink(accountLink, "Back to the account overview", () -> showBoss(ACCOUNT));
		// on the account overview it becomes a way back to the boss you came from, in the same place
		installTitleLink(backLink, "Back to the boss you were on", () ->
		{
			if (lastBoss != null)
			{
				showBoss(lastBoss);
			}
		});
		JPanel titleRight = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, Theme.M, 0));
		titleRight.setOpaque(false);
		titleRight.add(accountLink);
		titleRight.add(backLink);
		titleRight.add(helpLabel);
		titleRow.add(titleRight, BorderLayout.EAST);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		top.add(titleRow);
		top.add(spacer(Theme.M));
		buildFoldCard(helpCard, helpText);
		top.add(helpCard);

		buildWelcome();
		welcomeBorder = welcomePanel.getBorder();
		top.add(welcomePanel);
		buildTimesNotice();
		timesNoticeBorder = timesNoticePanel.getBorder();
		top.add(timesNoticePanel);
		top.add(spacer(Theme.M));

		setupHeader.setFont(FontManager.getRunescapeSmallFont());
		setupHeader.setAlignmentX(LEFT_ALIGNMENT);
		setupHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setupHeader.setToolTipText("Show or hide the collection log pages you still need to open");
		setupHeader.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setupExpanded = !setupExpanded;
				refresh();
			}
		});
		Activatable.install(setupHeader, "Show or hide the collection log pages you still need to open", () ->
		{
			setupExpanded = !setupExpanded;
			refresh();
		});
		top.add(setupHeader);
		setupProblem.setFont(FontManager.getRunescapeSmallFont());
		setupProblem.setForeground(Theme.TEXT_MUTED);
		setupProblem.setIcon(Theme.infoIcon());
		setupProblem.setToolTipText(Theme.wrap(Texts.SETUP_UNREADABLE_TIP, Theme.TIP_WIDE));
		setupProblem.setVerticalTextPosition(SwingConstants.TOP);
		setupProblem.setIconTextGap(Theme.S);
		setupProblem.setBorder(new EmptyBorder(0, 0, Theme.M, 0));
		setupProblem.setAlignmentX(LEFT_ALIGNMENT);
		setupProblem.setVisible(false);
		top.add(setupProblem);
		// styled like the line above: neutral text with an info mark, hidden unless the Loot Tracker is off
		lootTrackerOffLabel.setFont(FontManager.getRunescapeSmallFont());
		lootTrackerOffLabel.setForeground(Theme.TEXT_MUTED);
		lootTrackerOffLabel.setIcon(Theme.infoIcon());
		lootTrackerOffLabel.setText(Theme.wrap(Texts.LOOT_TRACKER_OFF, Theme.TEXT_WIDTH - 14));
		lootTrackerOffLabel.setToolTipText(Theme.wrap(Texts.LOOT_TRACKER_OFF_TIP, Theme.TIP_WIDE));
		lootTrackerOffLabel.setVerticalTextPosition(SwingConstants.TOP);
		lootTrackerOffLabel.setIconTextGap(Theme.S);
		lootTrackerOffLabel.setBorder(new EmptyBorder(0, 0, Theme.M, 0));
		lootTrackerOffLabel.setAlignmentX(LEFT_ALIGNMENT);
		lootTrackerOffLabel.setVisible(false);
		top.add(lootTrackerOffLabel);
		setupList.setOpaque(false);
		setupList.setAlignmentX(LEFT_ALIGNMENT);
		setupList.setBorder(new EmptyBorder(Theme.S, Theme.M, 0, 0));
		top.add(setupList);
		top.add(setupGap);

		searchField.setFont(FontManager.getRunescapeSmallFont());
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		searchField.setAlignmentX(LEFT_ALIGNMENT);
		searchField.setToolTipText("Try 'vork', 'cox' or an item like 'fang' (3+ letters). Enter opens the first match, Escape clears the search.");
		searchField.getAccessibleContext().setAccessibleName("Search bosses");
		searchField.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "onrate-clear-search");
		searchField.getActionMap().put("onrate-clear-search", new javax.swing.AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				searchField.setText("");
			}
		});
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				filterBosses();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				filterBosses();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				filterBosses();
			}
		});
		searchField.addActionListener(e ->
		{
			if (bossSelect.getItemCount() > 0)
			{
				showBoss(bossSelect.getItemAt(0));
			}
		});
		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchRow.setAlignmentX(LEFT_ALIGNMENT);
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		top.add(searchRow);
		noMatchLabel.setFont(FontManager.getRunescapeSmallFont());
		noMatchLabel.setForeground(Theme.TEXT_MUTED);
		noMatchLabel.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		noMatchLabel.setAlignmentX(LEFT_ALIGNMENT);
		noMatchLabel.setVisible(false);
		top.add(noMatchLabel);
		top.add(spacer(Theme.S));

		fillBossList("");
		bossSelect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		bossSelect.setAlignmentX(LEFT_ALIGNMENT);
		// open list only: add what an item search matched; the closed box and the default look stay as they are
		javax.swing.ListCellRenderer<? super String> baseRenderer = bossSelect.getRenderer();
		bossSelect.setRenderer((list, value, index, isSelected, cellHasFocus) ->
		{
			java.awt.Component c = baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			if (index >= 0 && !bossReasons.isEmpty() && c instanceof JLabel)
			{
				String reason = bossReasons.get(value);
				if (reason != null)
				{
					((JLabel) c).setText(value + " \u00b7 " + reason);
				}
			}
			return c;
		});
		bossSelect.getAccessibleContext().setAccessibleName("Boss");
		bossSelect.addActionListener(e ->
		{
			if (!updatingSelection)
			{
				refresh();
			}
		});
		top.add(bossSelect);
		top.add(spacer(Theme.M));

		// header: boss icon, name and kill count, then the verdict in big letters
		JPanel header = headerRow;
		header.setOpaque(false);
		header.setAlignmentX(LEFT_ALIGNMENT);
		headerIcon.setPreferredSize(new Dimension(36, 32));
		headerIcon.setHorizontalAlignment(SwingConstants.CENTER);
		header.add(headerIcon, BorderLayout.WEST);
		JPanel names = new JPanel();
		names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
		names.setOpaque(false);
		headerName.setFont(FontManager.getRunescapeBoldFont());
		headerName.setForeground(Color.WHITE);
		kcLabel.setFont(FontManager.getRunescapeSmallFont());
		kcLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		names.add(headerName);
		names.add(kcLabel);
		header.add(names, BorderLayout.CENTER);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		top.add(header);
		top.add(spacer(Theme.S));

		verdictLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.PLAIN, 24f));
		verdictLabel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(verdictLabel);
		verdictTiles.setOpaque(false);
		verdictTiles.setAlignmentX(LEFT_ALIGNMENT);
		verdictTiles.setBorder(new EmptyBorder(Theme.HAIR, 0, Theme.S, 0));
		top.add(verdictTiles);
		notableLabel.setFont(FontManager.getRunescapeSmallFont());
		notableLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		notableLabel.setAlignmentX(LEFT_ALIGNMENT);
		notableLabel.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		notableLabel.setVisible(false);
		// a click shows the next fact (see showTiles)
		notableLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				nextFact();
			}
		});
		Activatable.install(notableLabel, "Next fact", this::nextFact);
		top.add(notableLabel);
		lastUniqueLabel.setFont(FontManager.getRunescapeSmallFont());
		lastUniqueLabel.setForeground(Theme.TEXT_MUTED);
		lastUniqueLabel.setAlignmentX(LEFT_ALIGNMENT);
		lastUniqueLabel.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		lastUniqueLabel.setVisible(false);
		top.add(lastUniqueLabel);

		for (JLabel l : new JLabel[]{summaryLabel, valueLabel, syncLabel, hintLabel})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(Theme.TEXT_MUTED);
			l.setAlignmentX(LEFT_ALIGNMENT);
			// the info mark sits at the first line of wrapped text
			l.setVerticalTextPosition(SwingConstants.TOP);
			l.setIconTextGap(Theme.S);
			top.add(l);
		}
		levelsLabel.setFont(FontManager.getRunescapeSmallFont());
		levelsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		levelsLabel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(levelsLabel);
		top.add(spacer(Theme.M));

		graphPanel.setLayout(new BoxLayout(graphPanel, BoxLayout.Y_AXIS));
		graphPanel.setOpaque(false);
		graphPanel.setAlignmentX(LEFT_ALIGNMENT);
		graphTitle.setFont(FontManager.getRunescapeSmallFont());
		graphTitle.setForeground(Theme.TEXT_MUTED);
		brightenOnHover(graphTitle);
		graphTitle.setAlignmentX(LEFT_ALIGNMENT);
		graphTitle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		graphTitle.setToolTipText("Your account's luck, one point per day you play");
		graphTitle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setGraphOpen.accept(!options.get().isLuckGraph());
				refresh();
			}
		});
		Activatable.install(graphTitle, "Luck over time", () ->
		{
			setGraphOpen.accept(!options.get().isLuckGraph());
			refresh();
		});
		graphPanel.add(graphTitle);
		graphPanel.add(spacer(Theme.HAIR));
		graphWindowRow.setLayout(new BoxLayout(graphWindowRow, BoxLayout.Y_AXIS));
		graphWindowRow.setOpaque(false);
		graphWindowRow.setAlignmentX(LEFT_ALIGNMENT);
		graphPanel.add(graphWindowRow);
		graphPanel.add(graph);
		graphNote.setFont(FontManager.getRunescapeSmallFont());
		graphNote.setForeground(Theme.TEXT_MUTED);
		graphNote.setAlignmentX(LEFT_ALIGNMENT);
		graphPanel.add(graphNote);
		graphHint.setFont(FontManager.getRunescapeSmallFont());
		graphHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		graphHint.setAlignmentX(LEFT_ALIGNMENT);
		graphPanel.add(graphHint);
		graphPanel.add(spacer(Theme.M));
		topItemsPanel.setLayout(new BoxLayout(topItemsPanel, BoxLayout.Y_AXIS));
		topItemsPanel.setOpaque(false);
		topItemsPanel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(topItemsPanel);
		top.add(graphPanel);

		// boss pages: "ITEMS" with the View button on its right; the key to the bars goes under it
		itemsHeading.setLayout(new BoxLayout(itemsHeading, BoxLayout.Y_AXIS));
		itemsHeading.setOpaque(false);
		itemsHeading.setAlignmentX(LEFT_ALIGNMENT);
		itemsHeading.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		JPanel itemsLine = new JPanel(new BorderLayout(6, 0));
		itemsLine.setOpaque(false);
		itemsLine.setAlignmentX(LEFT_ALIGNMENT);
		itemsLine.add(smallLabel("ITEMS", Theme.TEXT_MUTED), BorderLayout.WEST);
		// icon and arrow only, as on the account overview's Sort row; the view's name is in its tooltip
		bossViewChip.setCompact(true);
		itemsLine.add(bossViewChip, BorderLayout.EAST);
		itemsLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		itemsHeading.add(itemsLine);
		itemsLegend.setFont(FontManager.getRunescapeSmallFont());
		itemsLegend.setForeground(Theme.TEXT_MUTED);
		itemsLegend.setAlignmentX(LEFT_ALIGNMENT);
		itemsLegend.setBorder(new EmptyBorder(Theme.HAIR, 0, 0, 0));
		itemsLegend.setToolTipText(new Tip(null).line("The white line is how many you should have by now. A bar short of it is behind; "
			+ "past it is ahead. Rare items have their line near the start.").small());
		itemsHeading.add(itemsLegend);
		itemsHeading.setVisible(false);
		top.add(itemsHeading);

		JPanel tabs = new JPanel(new GridLayout(1, 2, 2, 0));
		tabsRow = tabs;
		tabs.setOpaque(false);
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		styleTab(expectedTab, Tab.EXPECTED);
		styleTab(receivedTab, Tab.RECEIVED);
		tabs.add(expectedTab);
		tabs.add(receivedTab);
		top.add(tabs);

		sortRow.setOpaque(false);
		sortRow.setAlignmentX(LEFT_ALIGNMENT);
		sortRow.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		sortLabel.setFont(FontManager.getRunescapeSmallFont());
		sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortRow.add(sortLabel, BorderLayout.WEST);
		sortSelect.setFont(FontManager.getRunescapeSmallFont());
		sortSelect.getAccessibleContext().setAccessibleName("Sort");
		sortSelect.addActionListener(e ->
		{
			if (sortOptionsUpdating)
			{
				return;
			}
			rememberAccountPage();
			refresh();
		});
		sortRow.add(sortSelect, BorderLayout.CENTER);
		// the Pets slider (only for "Closest to green log") sits beside the View button
		sortEnd.setLayout(new BoxLayout(sortEnd, BoxLayout.X_AXIS));
		sortEnd.setOpaque(false);
		petsSwitch.setVisible(false);
		sortEnd.add(petsSwitch);
		sortEnd.add(accountViewChip);
		sortRow.add(sortEnd, BorderLayout.EAST);
		sortControls(true);
		sortRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		top.add(sortRow);

		rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
		rowsPanel.setOpaque(false);

		JPanel bottom = new JPanel();
		bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
		bottom.setOpaque(false);
		noteLabel.setFont(FontManager.getRunescapeSmallFont());
		noteLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		noteLabel.setAlignmentX(LEFT_ALIGNMENT);
		bottom.add(noteLabel);
		bottom.add(spacer(Theme.M));

		resetButton.setFont(FontManager.getRunescapeSmallFont());
		resetButton.setForeground(Theme.TEXT_MUTED);
		resetButton.setAlignmentX(LEFT_ALIGNMENT);
		resetButton.setToolTipText("Forget the kill count and drops tracked for this boss (asks first)");
		resetButton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		resetButton.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				resetSelected();
			}
		});
		Activatable.install(resetButton, "Reset this boss", this::resetSelected);
		// About, Feedback and Reset: one quiet line (About and Reset only where they apply, see updateFooter)
		aboutLink.setFont(FontManager.getRunescapeSmallFont());
		aboutLink.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		aboutLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		aboutLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggleAbout();
			}
		});
		Activatable.install(aboutLink, "About this page", this::toggleAbout);
		buildFoldCard(aboutCard, aboutText);
		// Escape closes whichever one is open
		javax.swing.KeyStroke escape = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0);
		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(escape, "onrate.closeFolds");
		getActionMap().put("onrate.closeFolds", new javax.swing.AbstractAction()
		{
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e)
			{
				closeFolds();
			}
		});
		JPanel footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		footer.setOpaque(false);
		footer.setAlignmentX(LEFT_ALIGNMENT);
		footer.add(aboutLink);
		footer.add(footerDot(aboutSep));
		footer.add(feedbackLink());
		footer.add(footerDot(shareSep));
		footer.add(buildShareLink());
		footer.add(footerDot(resetSep));
		footer.add(resetButton);
		footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		bottom.add(aboutCard);
		bottom.add(footer);

		add(top, BorderLayout.NORTH);
		add(rowsPanel, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);

		refresh();
	}

	/** Shows the given boss and redraws. Call on the Swing thread. */
	public void showBoss(String bossName)
	{
		updatingSelection = true;
		try
		{
			if (!contains(bossName))
			{
				searchField.setText("");
				fillBossList("");
			}
			bossSelect.setSelectedItem(bossName);
		}
		finally
		{
			updatingSelection = false;
		}
		refresh();
	}

	private boolean contains(String bossName)
	{
		for (int i = 0; i < bossSelect.getItemCount(); i++)
		{
			if (bossSelect.getItemAt(i).equals(bossName))
			{
				return true;
			}
		}
		return false;
	}

	/** True when the boss drops an item it shares with other bosses (Chaos Elemental drops only those). */
	private boolean hasSharedItems(BossDefinition boss)
	{
		return sharedItemOf(boss, false) != null;
	}

	/** The first shared item this boss drops, a pet first when {@code petFirst}; null when none. */
	private String sharedItemOf(BossDefinition boss, boolean petFirst)
	{
		String first = null;
		for (List<com.onrate.engine.data.BossCatalog.PoolSource> sources : tracker.getCatalog().getPools().values())
		{
			for (com.onrate.engine.data.BossCatalog.PoolSource src : sources)
			{
				if (src.getBoss() != boss)
				{
					continue;
				}
				if (petFirst && src.getDrop().isPet())
				{
					return src.getDrop().getName();
				}
				if (first == null)
				{
					first = src.getDrop().getName();
				}
			}
		}
		return first;
	}

	/** Fills the boss list with the bosses matching the search text. */
	private void fillBossList(String query)
	{
		String q = simplify(query);
		Object selected = bossSelect.getSelectedItem();
		if (selected == null)
		{
			// a search with no match emptied the list: bring back the page that was kept
			selected = keptPage;
		}
		bossSelect.removeAllItems();
		if (bossSearch == null)
		{
			bossSearch = new BossSearch(tracker.getCatalog());
		}
		bossReasons = new java.util.HashMap<>();
		if (q.isEmpty() || simplify(ACCOUNT).contains(q))
		{
			bossSelect.addItem(ACCOUNT);
		}
		for (BossSearch.Hit hit : bossSearch.search(query))
		{
			BossDefinition boss = hit.boss();
			if (boss.getDrops().isEmpty() && !hasSharedItems(boss))
			{
				// nothing to show at all
				continue;
			}
			bossSelect.addItem(boss.getName());
			if (hit.reason() != null)
			{
				bossReasons.put(boss.getName(), hit.reason());
			}
		}
		if (selected != null && contains((String) selected))
		{
			bossSelect.setSelectedItem(selected);
		}
	}

	private void filterBosses()
	{
		Object before = bossSelect.getSelectedItem();
		if (before != null)
		{
			keptPage = (String) before;
		}
		else
		{
			before = keptPage;
		}
		updatingSelection = true;
		try
		{
			fillBossList(searchField.getText());
		}
		finally
		{
			updatingSelection = false;
		}
		// nothing matches: keep the page on screen instead of redrawing with no selection
		noMatch = bossSelect.getItemCount() == 0;
		noMatchLabel.setText(noMatch ? "No bosses match \"" + searchField.getText().trim() + "\"" : "");
		noMatchLabel.setVisible(noMatch);
		if (!noMatch && !java.util.Objects.equals(bossSelect.getSelectedItem(), before))
		{
			refresh();
		}
	}

	/** The page being shown: the selected boss, or the kept one while a search matches nothing. */
	private String selectedPage()
	{
		Object item = bossSelect.getSelectedItem();
		if (item != null)
		{
			keptPage = (String) item;
		}
		return keptPage;
	}

	/** For tests: whether the search text currently matches no boss. */
	boolean isNoMatch()
	{
		return noMatch;
	}

	/** For tests: types into the search box. */
	void search(String text)
	{
		searchField.setText(text);
	}

	/** For tests: runs the search box's Escape key action. */
	void pressEscapeInSearch()
	{
		searchField.getActionMap().get("onrate-clear-search")
			.actionPerformed(new java.awt.event.ActionEvent(searchField, java.awt.event.ActionEvent.ACTION_PERFORMED, ""));
	}

	/** For tests: the search box's accessible name. */
	String searchAccessibleName()
	{
		return searchField.getAccessibleContext().getAccessibleName();
	}

	/** For tests: the page the panel is on (kept while a search matches nothing). */
	String currentPage()
	{
		return selectedPage();
	}

	/** For tests: the number of bosses the list offers. */
	int bossCount()
	{
		return bossSelect.getItemCount();
	}

	/** Lower case without apostrophes or dashes, so "kril" finds K'ril and "tztok" finds TzTok-Jad. */
	private static String simplify(String text)
	{
		return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("['\\-]", "").trim();
	}

	/** Recomputes and redraws the selected boss. Call on the Swing thread. */
	/**
	 * RuneLite tells the panel when it is opened or closed in the sidebar. While it's closed a
	 * kill only marks it out of date, and it is rebuilt once when it is opened again.
	 */
	@Override
	public void onActivate()
	{
		shown = true;
		onScreen = true;
		if (outOfDate)
		{
			refresh();
		}
	}

	@Override
	public void onDeactivate()
	{
		shown = false;
		onScreen = false;
	}

	/**
	 * True while the panel is on screen (opened in the sidebar). Safe from any thread. When it is false
	 * the plugin redraws the way the original did, which only marks a closed panel out of date.
	 */
	public boolean isOnScreen()
	{
		return onScreen;
	}

	/**
	 * True when RuneLite's Loot Tracker is known to be off, so the panel says new drops can't be seen.
	 * False when it is on or can't be told. Swing thread.
	 */
	public void setLootTrackerOff(boolean off)
	{
		if (lootTrackerOff == off)
		{
			return;
		}
		lootTrackerOff = off;
		lootTrackerOffLabel.setVisible(off);
		refresh();
	}

	/** Where loading the player's saved data stands; see {@link #setProfileState}. */
	public enum ProfileState
	{
		/** Nobody logged in, and no account loaded yet. */
		NOT_LOGGED_IN,
		/** Logged in, waiting for RuneLite to name the account's profile. */
		LOADING,
		/** The account's saved data couldn't be loaded. Nothing is saved over it. */
		FAILED,
		/** An account is loaded (also for a panel nobody tells, as in tests). */
		LOADED,
	}

	/**
	 * Until an account is loaded the panel shows this instead of a new player's setup steps, which
	 * would look as if the player's data were gone: log in, loading, or that loading failed. Swing thread.
	 */
	public void setProfileState(ProfileState state)
	{
		if (state == null || profileState == state)
		{
			return;
		}
		profileState = state;
		refresh();
		showTimesNoticeWhenReady();
	}

	/** For tests and the plugin: the state set last. */
	public ProfileState getProfileState()
	{
		return profileState;
	}

	/** For tests: whether the "Loot Tracker is off" note is shown. */
	boolean isLootTrackerNoteShown()
	{
		return lootTrackerOffLabel.isVisible();
	}

	public void refresh()
	{
		// RuneLite says when the panel is opened or closed, but the panel also checks for itself:
		// if it's on screen it always redraws, so it can never stay stuck on an old page
		if (!shown && !isShowing())
		{
			outOfDate = true;
			return;
		}
		outOfDate = false;
		try
		{
			draw();
		}
		catch (RuntimeException e)
		{
			// one page that can't be drawn mustn't freeze the whole panel
			log.warn("On Rate could not draw {}", selectedPage(), e);
			rowsPanel.removeAll();
			JLabel error = smallLabel("<html><body style='width: " + Theme.TEXT_WIDTH + "px'>Something went wrong drawing this page. "
				+ "Pick another boss, or the account, to carry on.</body></html>", Theme.TEXT_MUTED);
			error.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(error);
		}
		finally
		{
			revalidate();
			repaint();
		}
	}

	/** Stores the "?" help (HTML without the html tags) as the tooltip and as the text of the help block. */
	private void setHelp(String html)
	{
		helpHtml = html;
		helpLabel.setToolTipText(html == null ? null : Theme.wrap(html, 240));
		helpText.setText(html == null ? "" : Theme.wrap(html, FOLD_TEXT_WIDTH));
		if (html == null)
		{
			helpOpen = false;
		}
		helpCard.setVisible(helpOpen);
	}

	/** Stores the About note (HTML without the html tags) as the tooltip and as the text of the About block. */
	private void setAbout(String html)
	{
		aboutHtml = html;
		aboutLink.setToolTipText(html == null ? null : Theme.wrap(html, 240));
		aboutText.setText(html == null ? "" : Theme.wrap(html, FOLD_TEXT_WIDTH));
		if (html == null)
		{
			aboutOpen = false;
		}
		aboutCard.setVisible(aboutOpen);
	}

	private static final int FOLD_TEXT_WIDTH = 148;

	private void buildFoldCard(Card card, JLabel text)
	{
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setForeground(Theme.TEXT_MUTED);
		text.setAlignmentX(LEFT_ALIGNMENT);
		card.setBorder(CARD_PADDING);
		card.add(text);
		card.setVisible(false);
	}

	private void toggleHelp()
	{
		if (helpHtml == null)
		{
			return;
		}
		helpOpen = !helpOpen;
		helpCard.setVisible(helpOpen);
		foldsChanged();
	}

	private void toggleAbout()
	{
		if (aboutHtml == null)
		{
			return;
		}
		aboutOpen = !aboutOpen;
		aboutCard.setVisible(aboutOpen);
		foldsChanged();
	}

	private void closeFolds()
	{
		if (!helpOpen && !aboutOpen)
		{
			return;
		}
		helpOpen = false;
		aboutOpen = false;
		helpCard.setVisible(false);
		aboutCard.setVisible(false);
		foldsChanged();
	}

	private void foldsChanged()
	{
		revalidate();
		repaint();
	}

	/** For tests: whether the "?" help block / the About block is open. */
	boolean isHelpOpen()
	{
		return helpOpen;
	}

	boolean isAboutOpen()
	{
		return aboutOpen;
	}

	/** Recomputes and redraws the selected page (see {@link #refresh()}). */
	private void draw()
	{
		// the help and About blocks belong to a page: moving to another boss or tab closes them
		String page = bossSelect.getSelectedItem() + "|" + accountTab;
		if (!page.equals(helpPage))
		{
			helpPage = page;
			closeFolds();
		}
		timeIndex = null;
		// the graph offer was on screen and this redraw may move on from it (see the end)
		boolean overlayWasOnScreen = overlayOfferOnScreen && overlayOfferLabel != null && overlayOfferLabel.isShowing();
		overlayOfferOnScreen = false;
		insightsThisDraw.clear();
		drawing = true;
		try
		{
			drawPage();
		}
		finally
		{
			drawing = false;
			insightsThisDraw.clear();
		}
		if (boxes)
		{
			// every run of list rows into one card, like the verdict tiles
			Card.box(rowsPanel);
			Card.box(topItemsPanel);
		}
		welcomePanel.setBoxed(boxes);
		welcomePanel.setBorder(boxes ? CARD_PADDING : welcomeBorder);
		timesNoticePanel.setBoxed(boxes);
		helpCard.setBoxed(boxes);
		aboutCard.setBoxed(boxes);
		timesNoticePanel.setBorder(boxes ? CARD_PADDING : timesNoticeBorder);
		updateFooter();
		// seen, and the player went on to another page or tab: never again on this account
		if (overlayWasOnScreen && !overlayOfferOnScreen)
		{
			markOverlayOfferSeen.run();
		}
	}

	/** True while {@link #draw()} runs: only then are a raid's insights shared between the parts of the page. */
	private boolean drawing;
	/** A raid's insights worked out during the draw in progress, with and without GP value. */
	private final java.util.Map<String, RaidInsights> insightsThisDraw = new java.util.HashMap<>();

	/**
	 * A raid's insights. One draw of a raid page asks for them many times over (the sections, the line of
	 * facts, History's PB tags and every item's hover), and each time goes through every saved raid: within
	 * a draw they are worked out once. Outside a draw they are worked out fresh, as before.
	 */
	private RaidInsights raidInsights(String bossName, boolean gp)
	{
		if (!drawing)
		{
			return tracker.raidInsights(bossName, gp ? priceOf : null);
		}
		return insightsThisDraw.computeIfAbsent(bossName + (gp ? "|gp" : "|"), k -> tracker.raidInsights(bossName, gp ? priceOf : null));
	}

	/** Whether the one-time notice about the times feature is still due, and marking it shown. */
	private BooleanSupplier timesNoticeDue = () -> false;
	private Runnable markTimesNoticeShown = () -> { };

	/**
	 * The one-time notice about the Time page, for players who had On Rate before it: a card in the panel,
	 * like the welcome guide. It appears the first time the panel is on screen, and is marked as shown
	 * then, so it never comes back, whatever the player does with it. The plugin decides who it is for (a new install is
	 * never due).
	 */
	public void setTimesNotice(BooleanSupplier due, Runnable markShown)
	{
		timesNoticeDue = due == null ? () -> false : due;
		markTimesNoticeShown = markShown == null ? () -> { } : markShown;
		addHierarchyListener(e ->
		{
			if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0)
			{
				showTimesNoticeWhenReady();
			}
		});
		showTimesNoticeWhenReady();
	}

	/**
	 * Shows the notice only when the panel is on screen and an account is loaded. Until then nothing is
	 * taken, so the notice is not marked shown before the player can see it (for instance while the panel is
	 * still on the log-in or loading message); it is tried again when either changes.
	 */
	void showTimesNoticeWhenReady()
	{
		if (profileLoaded() && isShowing())
		{
			showTimesNoticeOnce();
		}
	}

	/** The notice's paragraphs: what is new and where to find it. */
	static String[] timesNoticeParagraphs()
	{
		return new String[]{
			"On Rate now times your kills and raids. The <font color='#ffffff'>Time</font> page (Items tab) shows how long each "
				+ "item you don't have takes on average, from the OSRS Wiki's kill speed until you have your own.",
			"Raid pages have three new sections under History: <font color='#ffffff'>purple chance by setup</font> (which of "
				+ "your team sizes, modes and raid levels gets the most purples an hour), your personal bests, and your points over time.",
			"If you use the Raid Data Tracker plugin, On Rate can read your raids from it, so those sections fill straight "
				+ "away. Choose its folder under Raids in On Rate's settings; that has to be done again each time RuneLite starts. "
				+ "You can turn the import off there too.",
			"This message only shows once.",
		};
	}

	/** The notice card, built like the welcome guide: in the panel, under the title. Hidden until it is due. */
	private final Card timesNoticePanel = new Card();
	/** The notice's "Show me" button: the Time page lives on the Items tab, so it needs that tab to be on. */
	private final JButton timesNoticeShow = new JButton("Show me");

	private void buildTimesNotice()
	{
		timesNoticePanel.setLayout(new BoxLayout(timesNoticePanel, BoxLayout.Y_AXIS));
		timesNoticePanel.setAlignmentX(LEFT_ALIGNMENT);
		timesNoticePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		timesNoticePanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.OUTLINE),
			new EmptyBorder(Theme.M, Theme.M, Theme.M, Theme.M)));
		JLabel title = new JLabel("New in On Rate");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Theme.TEXT);
		title.setAlignmentX(LEFT_ALIGNMENT);
		timesNoticePanel.add(title);
		String[] paragraphs = timesNoticeParagraphs();
		for (int i = 0; i < paragraphs.length; i++)
		{
			boolean last = i == paragraphs.length - 1;
			JLabel text = smallLabel(Theme.wrap(paragraphs[i], Theme.CARD_TEXT_WIDTH), Theme.TEXT_MUTED);
			text.setAlignmentX(LEFT_ALIGNMENT);
			text.setBorder(last ? new EmptyBorder(Theme.S, 0, 0, 0)
				: BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(i == 0 ? 0 : 1, 0, 0, 0, Theme.DIVIDER),
					new EmptyBorder(i == 0 ? Theme.HAIR : Theme.S + 1, 0, Theme.S + 1, 0)));
			timesNoticePanel.add(text);
		}
		JButton show = timesNoticeShow;
		show.setFont(FontManager.getRunescapeSmallFont());
		show.setFocusPainted(false);
		show.setToolTipText("Opens the Time page; the raid sections are on each raid's page");
		show.addActionListener(e ->
		{
			if (!options.get().isTopItems())
			{
				// no Items tab, so no Time page: say so and keep the notice, instead of closing it on nothing
				syncTimesNoticeShow();
				return;
			}
			closeTimesNotice();
			openTimePage();
		});
		JButton gotIt = new JButton("Got it");
		gotIt.setFont(FontManager.getRunescapeSmallFont());
		gotIt.setFocusPainted(false);
		gotIt.addActionListener(e -> closeTimesNotice());
		JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, Theme.S, 0));
		buttons.setOpaque(false);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		buttons.add(show);
		buttons.add(gotIt);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttons.getPreferredSize().height));
		timesNoticePanel.add(buttons);
		timesNoticePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, timesNoticePanel.getPreferredSize().height));
		timesNoticePanel.setVisible(false);
	}

	/** Disables "Show me" and says why while the Items tab is off; the Time page is part of that tab. */
	private void syncTimesNoticeShow()
	{
		boolean itemsOn = options.get().isTopItems();
		timesNoticeShow.setEnabled(itemsOn);
		timesNoticeShow.setToolTipText(itemsOn ? "Opens the Time page; the raid sections are on each raid's page"
			: "The Time page is part of the Items tab, which is off. Turn on Items tab under Display in the settings");
	}

	private void closeTimesNotice()
	{
		timesNoticePanel.setVisible(false);
		revalidate();
		repaint();
	}

	/** True when the notice is due now, and marks it shown: it must never come back, however it is closed. */
	boolean takeTimesNotice()
	{
		if (!timesNoticeDue.getAsBoolean())
		{
			return false;
		}
		markTimesNoticeShown.run();
		return true;
	}

	/** Shows the card when it is due: marked shown first, so it stays through this session's redraws but never returns. */
	void showTimesNoticeOnce()
	{
		if (takeTimesNotice())
		{
			syncTimesNoticeShow();
			timesNoticePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, timesNoticePanel.getPreferredSize().height));
			timesNoticePanel.setVisible(true);
			revalidate();
			repaint();
		}
	}

	/** Whether the notice card is on screen. */
	boolean isTimesNoticeVisible()
	{
		return timesNoticePanel.isVisible();
	}

	/** Goes to the account overview's Items tab, on its Time page. */
	public void openTimePage()
	{
		accountTab = TAB_ITEMS;
		topMode = TOP_TIME;
		showBoss(ACCOUNT);
	}

	/**
	 * The overlay offer: during a long trip at one boss, one line on the trip card that turns
	 * the in-game overlay on when clicked. Shown once per account (remembered as for the graph
	 * offer), never when the overlay is on, and not in a session that already showed a tip.
	 */
	public void setOverlayOffer(BooleanSupplier on, BooleanSupplier seen, Runnable markSeen, Runnable turnOn)
	{
		overlayOn = on == null ? () -> true : on;
		overlayOfferSeen = seen == null ? () -> true : seen;
		markOverlayOfferSeen = markSeen == null ? () -> { } : markSeen;
		turnOnOverlay = turnOn == null ? () -> { } : turnOn;
	}

	/**
	 * Where the panel's own view choices are remembered: whether Luck over time is open, and
	 * the item view. They're hidden settings, changed only from the panel.
	 */
	public void setGraphOpener(java.util.function.Consumer<Boolean> opener)
	{
		setGraphOpen = opener == null ? open -> { } : opener;
	}

	/** Where the View button saves the item view; the same setting as in Display, so every page follows it. */
	public void setItemViewSaver(java.util.function.Consumer<ItemView> saver)
	{
		setItemView = saver == null ? view -> { } : saver;
	}

	private void pickView(ItemView view)
	{
		setItemView.accept(view);
		refresh();
	}

	/** Where the account overview's page choice is kept between sessions ("tab,sort,top,timeList"); see {@link #setAccountPageStore}. */
	private java.util.function.Consumer<String> saveAccountPage = page -> { };
	private boolean restoringAccountPage;

	/**
	 * Remembers which account overview page was last open (Bosses, Items or Recent, the sort order
	 * and the Items list), also after RuneLite is closed. Call once, right after the panel is made.
	 */
	public void setAccountPageStore(String saved, java.util.function.Consumer<String> saver)
	{
		saveAccountPage = saver == null ? page -> { } : saver;
		String[] parts = saved == null ? new String[0] : saved.split(",");
		restoringAccountPage = true;
		try
		{
			// three fields from before the Time page's dropdown was remembered, four since
			if (parts.length == 3 || parts.length == 4)
			{
				int tab = Integer.parseInt(parts[0].trim());
				int top = Integer.parseInt(parts[2].trim());
				if (tab >= TAB_BOSSES && tab <= TAB_RECENT)
				{
					accountTab = tab;
				}
				if (top >= TOP_DRIEST && top <= TOP_TIME)
				{
					topMode = top;
				}
				for (Sort sort : Sort.values())
				{
					if (sort.name().equals(parts[1].trim()))
					{
						sortSelect.setSelectedItem(sort);
					}
				}
				if (parts.length == 4)
				{
					for (TimeView view : TimeView.values())
					{
						if (view.name().equals(parts[3].trim()))
						{
							timeView = view;
						}
					}
				}
			}
		}
		catch (NumberFormatException e)
		{
			// unreadable: keep the usual first page
		}
		finally
		{
			restoringAccountPage = false;
		}
		refresh();
	}

	/**
	 * Opens the account overview on its Bosses tab, whatever tab was saved. Called once after
	 * {@link #setAccountPageStore}, so RuneLite always starts on Bosses; the sort order and the Items list
	 * are still the saved ones, and the tab you pick afterwards is remembered for the rest of the session.
	 */
	public void startOnBossesTab()
	{
		accountTab = TAB_BOSSES;
		refresh();
	}

	/** Where which sections are left open is kept between sessions (comma-separated ids); see {@link #setFoldStateStore}. */
	private java.util.function.Consumer<String> saveFoldState = state -> { };
	private boolean restoringFoldState;

	/**
	 * Remembers which of History, Personal bests and Points over time were left open, also after RuneLite is
	 * closed. Purple chance by setup and Raids left for the log always start folded. Call once, right after
	 * the panel is made.
	 */
	public void setFoldStateStore(String saved, java.util.function.Consumer<String> saver)
	{
		saveFoldState = saver == null ? state -> { } : saver;
		restoringFoldState = true;
		try
		{
			java.util.Set<String> ids = new java.util.HashSet<>();
			if (saved != null)
			{
				for (String id : saved.split(","))
				{
					ids.add(id.trim());
				}
			}
			historyOpen = ids.contains(FOLD_HISTORY);
			bestsOpen = ids.contains(FOLD_BESTS);
			pointsOpen = ids.contains(FOLD_POINTS);
		}
		finally
		{
			restoringFoldState = false;
		}
		refresh();
	}

	private static final String FOLD_HISTORY = "history";
	private static final String FOLD_BESTS = "bests";
	private static final String FOLD_POINTS = "points";

	private void rememberFolds()
	{
		if (restoringFoldState)
		{
			return;
		}
		java.util.List<String> open = new java.util.ArrayList<>();
		if (historyOpen)
		{
			open.add(FOLD_HISTORY);
		}
		if (bestsOpen)
		{
			open.add(FOLD_BESTS);
		}
		if (pointsOpen)
		{
			open.add(FOLD_POINTS);
		}
		saveFoldState.accept(String.join(",", open));
	}

	private void rememberAccountPage()
	{
		if (!restoringAccountPage)
		{
			saveAccountPage.accept(accountTab + "," + ((Sort) sortSelect.getSelectedItem()).name() + "," + topMode + "," + timeView.name());
		}
	}

	/** A link at the top right of the title row: hand cursor, accent on hover, works from the keyboard. */
	private static void installTitleLink(JLabel link, String tip, Runnable action)
	{
		link.setFont(FontManager.getRunescapeBoldFont());
		link.setForeground(Theme.TEXT);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(Theme.ACCENT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(Theme.TEXT);
			}
		});
		Activatable.install(link, tip, action);
	}

	private void drawPage()
	{
		// before login the guide's setup steps would read as if the account had nothing yet
		welcomePanel.setVisible(showWelcome.getAsBoolean() && profileLoaded());
		refreshSetup();

		String bossName = selectedPage();
		ViewOptions o = options.get();
		Palette.setColourBlind(o.isColourBlind());
		for (int i = 0; i < welcomeVerdicts.size(); i++)
		{
			// the guide's key follows the colour-blind setting like the verdicts do
			welcomeVerdicts.get(i).setForeground(welcomeVerdictColor(i));
		}
		rowsPanel.removeAll();
		styleTabs();
		boolean account = ACCOUNT.equals(bossName);
		accountLink.setVisible(!account);
		backLink.setVisible(account && lastBoss != null);
		if (!account && bossName != null)
		{
			lastBoss = bossName;
		}
		// the luck graph belongs to the bosses list; the Items and Recent tabs get the room
		// Luck over time sits with the bosses list; its title opens and closes it
		graphPanel.setVisible(account && (accountTab == TAB_BOSSES || accountTab == TAB_ITEMS && !o.isTopItems()));
		graphTitle.setText((o.isLuckGraph() ? "\u25be " : "\u25b8 ") + "LUCK OVER TIME");
		topItemsPanel.setVisible(account && o.isTopItems());
		topItemsPanel.removeAll();
		viewApplies = false;
		sortRow.setVisible(account);
		sortControls(true);
		accountViewChip.setView(o.getItemView());
		bossViewChip.setView(o.getItemView());
		itemsHeading.setVisible(false);
		itemsLegend.setVisible(o.getItemView() == ItemView.TARGET_BARS);
		tabsRow.setVisible(o.getItemView() == ItemView.DELVE_BARS);
		syncLabel.setVisible(!account);
		valueLabel.setVisible(false);
		hintLabel.setVisible(false);
		lastUniqueLabel.setVisible(false);
		headerIcon.setIcon(null);
		headerIcon.setText(null);
		headerIcon.setVisible(true);
		kcLabel.setVisible(true);
		shareText = null;
		if (account)
		{
			refreshAccount(o);
			revalidate();
			repaint();
			return;
		}
		resetButton.setVisible(true);
		BossStats stats = bossName == null ? null : tracker.stats(bossName, raidSettings.get());
		if (stats == null)
		{
			revalidate();
			repaint();
			return;
		}
		boolean raid = stats.getBoss().getRaid() != null;
		boolean delve = stats.getLevelCounts() != null;
		boolean combined = stats.getPartCounts() != null;
		String unit = com.onrate.engine.tracking.Tracker.unitOne(stats.getBoss());
		headerName.setText(stats.getBoss().getName());
		headerName.setToolTipText(null);
		kcLabel.setToolTipText(null);
		// "est." sits next to the expected number on the Uniques tile, where it applies, instead of a bare asterisk
		estimateNote = stats.getBoss().isEstimate()
			? ESTIMATE_NOTE : null;
		setHeaderIcon(stats.getBoss());
		if (combined)
		{
			// the counts per mode or boss sit next to the name, on one line
			kcLabel.setText(partLine(stats.getPartCounts(), raid, stats.getKillCount()));
			// the full names on hover, for when the line is cut off
			kcLabel.setToolTipText(partTable(stats.getPartCounts(), raid));
			levelsLabel.setVisible(false);
		}
		else if (delve)
		{
			kcLabel.setText(String.format(java.util.Locale.ROOT, "Total delves: %,d", stats.getKillCount()));
			levelsLabel.setText(levelTable(stats.getLevelCounts()));
			levelsLabel.setVisible(true);
		}
		else
		{
			kcLabel.setText(stats.isKillCountKnown()
				? String.format(java.util.Locale.ROOT, "%,d %s", stats.getKillCount(), com.onrate.engine.tracking.Tracker.unit(stats.getBoss()))
				: String.format(java.util.Locale.ROOT, "%,d %s tracked", stats.getKillCount(), com.onrate.engine.tracking.Tracker.unit(stats.getBoss())));
			levelsLabel.setVisible(false);
		}
		String reminder = tracker.syncReminder(stats.getBoss().getName());
		if (tracker.logPageUnreadable(stats.getBoss().getName()))
		{
			// something is wrong with the read, not with the player: neutral text with an info mark
			syncLabel.setText(Theme.wrap(Texts.LOG_PAGE_UNREADABLE, Theme.TEXT_WIDTH - 14));
			syncLabel.setForeground(Theme.TEXT_MUTED);
			syncLabel.setIcon(Theme.infoIcon());
			syncLabel.setToolTipText(Theme.wrap(Texts.LOG_PAGE_UNREADABLE_TIP, Theme.TIP_WIDE));
		}
		else if (reminder != null)
		{
			syncLabel.setToolTipText(null);
			syncLabel.setText(Theme.wrap(reminder, Theme.TEXT_WIDTH - 14));
			// an instruction, not a warning: neutral text with an info mark
			syncLabel.setForeground(Theme.TEXT_MUTED);
			syncLabel.setIcon(Theme.infoIcon());
		}
		else if (stats.isClogSynced())
		{
			// all good: nothing to say
			syncLabel.setToolTipText(null);
			syncLabel.setText("");
			syncLabel.setVisible(false);
		}
		else
		{
			syncLabel.setToolTipText(null);
			syncLabel.setText("Counting uniques from your loot from now on");
			syncLabel.setForeground(Theme.TEXT_MUTED);
			syncLabel.setIcon(Theme.infoIcon());
		}
		if (!profileLoaded())
		{
			// before login a "load this page" or "counting from now on" line would be about nobody's data
			syncLabel.setToolTipText(null);
			syncLabel.setText("");
			syncLabel.setVisible(false);
		}
		RowStats any = stats.getAnyRow();
		// everything this boss drops is shared (Chaos Elemental): no verdict of its own
		boolean sharedOnly = stats.getDrops().isEmpty();
		boolean unsynced = reminder != null && stats.getKillCount() > 0;
		if (sharedOnly)
		{
			verdictLabel.setText("All shared");
			verdictLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			verdictLabel.setToolTipText(null);
			summaryLabel.setText(Theme.wrap("Everything it drops also drops from other bosses, so each item is judged on all "
				+ "of them together below.", Theme.TEXT_WIDTH));
		}
		else if (unsynced)
		{
			// The kill count is known but not the drops from before On Rate: any verdict would be
			// "very dry" for the wrong reason. Wait for the log page.
			verdictLabel.setText("Not synced");
			verdictLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			verdictLabel.setToolTipText("<html><body style='width: " + Theme.TIP_WIDE + "px'>On Rate knows your kill count, but not the drops "
				+ "you got before you installed it. Open the page below once and your verdict shows up.</body></html>");
			summaryLabel.setText(any.getExpected() < 0.005 ? "" : String.format(java.util.Locale.ROOT, "%s %s expected by now",
				formatExpected(any.getExpected()), raid ? "purples" : "uniques"));
		}
		else
		{
			showVerdict(any, com.onrate.engine.tracking.Tracker.unit(stats.getBoss()));
		}
		double[] value = o.isGeValue() && !unsynced ? bossValue(stats) : null;
		List<RowStats> counted = new ArrayList<>();
		for (int i = 0; i < stats.getDrops().size(); i++)
		{
			if (tracker.counts(stats.getBoss(), stats.getDrops().get(i)))
			{
				counted.add(stats.getRows().get(i));
			}
		}
		showTiles(unsynced || sharedOnly ? null : any, value, lastValueTip, raid ? "purples" : "uniques", counted, null,
			moreFacts && !unsynced && !sharedOnly ? bossFacts(stats, counted, raid, delve) : java.util.Collections.emptyList(), true);
		lastHintTip = null;
		String hint = unsynced ? null : settingsHint(stats);
		Color hintColor = ColorScheme.LIGHT_GRAY_COLOR;
		if (hint == null && !unsynced)
		{
			// Royal Titans and Yama count as duo unless changed: say so where it matters
			hint = duoLine(stats);
			hintColor = ColorScheme.LIGHT_GRAY_COLOR;
		}
		hintLabel.setText(hint == null ? "" : Theme.wrap(hint, Theme.TEXT_WIDTH - 14));
		hintLabel.setIcon(hint == null ? null : Theme.infoIcon());
		hintLabel.setForeground(hintColor);
		hintLabel.setToolTipText(lastHintTip);
		hintLabel.setVisible(hint != null);
		setHelp(bossHelp(o.getItemView()));

		if (o.isTripCard())
		{
			addTripCard(stats.getBoss().getName());
		}
		if (!sharedOnly)
		{
			renderBossItems(stats, unit, o.getItemView());
		}
		renderBossPools(stats, o.getItemView());
		// the ITEMS line and its View button only when the page drew rows that follow the view
		itemsHeading.setVisible(viewButtons && viewApplies);
		renderHistory(stats, unit);
		if (raid && raidExtras)
		{
			renderRaidInsights(stats, o.isGeValue());
		}
		else if (!raid && raidExtras && logTableAllowed(stats.getBoss()) && !sharedOnly)
		{
			renderBossLogForecast(stats);
		}

		StringBuilder note = new StringBuilder();
		if (raid && combined)
		{
			StringBuilder modes = new StringBuilder();
			for (com.onrate.engine.data.BossPart part : stats.getBoss().getParts())
			{
				if (part.getRaid() == null || "none".equals(part.getRaid()))
				{
					continue;
				}
				if (modes.length() > 0)
				{
					modes.append(", ");
				}
				int team = Math.max(1, raidSettings.get().teamSize(part.getRaid()));
				modes.append(part.getName()).append(' ')
					.append(new java.math.BigDecimal(raidSettings.get().uniqueChance(part.getRaid()) * team * 100)
						.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
					.append('%');
				if (team > 1)
				{
					modes.append(" for the team of ").append(team);
				}
				modes.append(Texts.learnedNote(part.getRaid(), raidSettings.get().learnedRaids(part.getRaid())));
			}
			if ("fractured".equals(stats.getBoss().getRaid()))
			{
				int team = Math.max(1, raidSettings.get().teamSize("fractured"));
				int learned = raidSettings.get().learnedRaids("fractured");
				note.append(String.format(java.util.Locale.ROOT, "Raids On Rate didn't track use a team of %d ("
					+ (learned > 0 ? "your usual team, from your last " + learned + " raids" : "<i>Fractured Archive team size</i>")
					+ ") and an even share of the damage: "
					+ "%.2f%% each. The team rolls %.2f%% (%.2f%% for a solo or duo, more for each bigger team). "
					+ "Change these under <i>Raids</i> and <i>Raid purple chances</i> in On Rate's settings.",
					team, raidSettings.get().uniqueChance("fractured") * 100, raidSettings.get().uniqueChance("fractured") * team * 100,
					raidSettings.get().tfaBaseChance() * 100));
			}
			else
			{
				note.append("Purple chance for raids without points: ").append(modes).append(". Change these under <i>Raid purple chances</i> in On Rate's settings.");
			}
			note.append(raidPointsNote(stats.getBoss()));
		}
		else if (raid)
		{
			note.append(String.format(java.util.Locale.ROOT, "Assumes a %.1f%% chance of a purple per raid (change it under Raid purple chances in On Rate's settings).",
				stats.getRaidUniqueChance() * 100));
		}
		String teamNote = stats.getBoss().getTeam() == null || stats.getBoss().isDetectTeam() ? null
			: raidSettings.get().contributionNote(stats.getBoss().getTeam(), stats.getKillCount());
		if (teamNote != null)
		{
			note.append(teamNote);
		}
		if (stats.getBoss().isDetectTeam())
		{
			if (note.length() > 0)
			{
				note.append("<br><br>");
			}
			java.util.Map<Integer, Integer> seen = tracker.teamKills(stats.getBoss().getName());
			int older = Math.max(0, stats.getKillCount() - seen.values().stream().mapToInt(Integer::intValue).sum());
			String olderNote;
			if (stats.getBoss().getTeam() != null)
			{
				olderNote = raidSettings.get().contributionNote(stats.getBoss().getTeam(), older);
			}
			else
			{
				// fixed when On Rate started counting: the team it saw first
				Double share = tracker.olderShare(stats.getBoss().getName());
				olderNote = share == null || share <= 0 ? null
					: String.format(java.util.Locale.ROOT, "Counted at the team On Rate first saw (%.1f players).", 1 / share);
			}
			note.append(teamSizeNote(seen, stats.getKillCount(), olderNote));
		}
		if (stats.getBoss().getNote() != null)
		{
			if (note.length() > 0)
			{
				note.append("<br><br>");
			}
			note.append(stats.getBoss().getNote());
		}
		if (stats.getBoss().isEstimate())
		{
			if (note.length() > 0)
			{
				note.append("<br><br>");
			}
			note.append(ESTIMATE_NOTE);
		}
		// the explanations are there for whoever wonders, not on screen all the time
		aboutLink.setText(note.length() == 0 ? "" : "<html><u>About</u></html>");
		setAbout(note.length() == 0 ? null : note.toString());
		noteLabel.setText("");
		noteLabel.setToolTipText(null);

		revalidate();
		repaint();
	}

	/** The screen-reader name of a tab button: the selected one says so. */
	static String tabAccessibleName(String label, boolean selected)
	{
		return selected ? label + ", selected" : label;
	}

	private void styleTabs()
	{
		expectedTab.setForeground(tab == Tab.EXPECTED ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		receivedTab.setForeground(tab == Tab.RECEIVED ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		expectedTab.setBorder(tabBorder(tab == Tab.EXPECTED));
		receivedTab.setBorder(tabBorder(tab == Tab.RECEIVED));
		expectedTab.getAccessibleContext().setAccessibleName(tabAccessibleName("Expected", tab == Tab.EXPECTED));
		receivedTab.getAccessibleContext().setAccessibleName(tabAccessibleName("Received", tab == Tab.RECEIVED));
		// the card look: the same tabs as Bosses / Items / Recent (bold, one thin line under the row)
		java.awt.Font font = boxes ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont();
		expectedTab.setFont(font);
		receivedTab.setFont(font);
		tabsRow.setBorder(boxes ? BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.OUTLINE) : null);
	}

	/**
	 * How many raids per mode used their own points instead of the settings, plus what the
	 * Theatre of Blood board told us.
	 */
	private String raidPointsNote(BossDefinition boss)
	{
		List<com.onrate.engine.tracking.RaidRecord> records = tracker.raidRecords(boss.getName());
		if (records.isEmpty())
		{
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (com.onrate.engine.data.BossPart part : boss.getParts())
		{
			int measured = tracker.measuredRaids(boss.getName(), part.getName());
			if (measured > 0)
			{
				// ToA: the game sends the exact points; On Rate's estimate is only the fallback when they don't come
				int estimated = 0;
				for (com.onrate.engine.tracking.RaidRecord r : records)
				{
					if (part.getName().equals(r.getPart()) && r.isEstimated()
						&& !Double.isNaN(com.onrate.engine.tracking.RateModel.purpleChance(r)))
					{
						estimated++;
					}
				}
				sb.append("<br><br>").append(Texts.raidPointsLine(boss.getRaid(), part.getName(), measured, estimated));
			}
		}
		int boards = 0;
		for (com.onrate.engine.tracking.RaidRecord r : records)
		{
			if ("tob".equals(r.getFamily()))
			{
				boards++;
			}
		}
		if (boards > 0)
		{
			sb.append(String.format(java.util.Locale.ROOT, "<br><br>%,d raid%s used the real team size from the end-of-raid board instead of the setting. "
				+ "ToB has no points: the team chance from the settings is divided by that team size.",
				boards, boards == 1 ? "" : "s"));
		}
		int archives = 0;
		int players = 0;
		for (com.onrate.engine.tracking.RaidRecord r : records)
		{
			if ("fractured".equals(r.getFamily()) && r.getTeamSize() > 0 && r.getContribution() < 0)
			{
				archives++;
				players += r.getTeamSize();
			}
		}
		if (archives > 0)
		{
			sb.append(String.format(java.util.Locale.ROOT, "<br><br>%,d raid%s used the team On Rate counted around you at the end (%.1f players on average). "
				+ "On Rate didn't see every fight of those raids, so each assumes an even share of the damage and no deaths.",
				archives, archives == 1 ? "" : "s", players / (double) archives));
		}
		return sb.toString();
	}

	/**
	 * The two verdict tiles: how many uniques (left) and their GP value (right, when prices are
	 * known). With nothing to judge yet ({@code any} null or no luck) the big single word stays.
	 */
	/** Set before {@link #showTiles} on an estimate boss page, null otherwise. */
	private String estimateNote;
	/** The line {@link #showLastUnique} set, or null. */
	private String lastUniqueText;
	/** The notable line set by the last {@link #showTiles}, or null. */
	private String notableText;

	private void showTiles(RowStats any, double[] value, String valueTip, String what, List<RowStats> items,
		List<RowStats> bosses, List<String> pageFacts, boolean bossPage)
	{
		double luck = any == null ? Double.NaN : any.getLuck();
		boolean tiles = !Double.isNaN(luck) && !Verdict.labelOf(luck).isEmpty();
		// a boss page with next to nothing expected says so instead of a verdict (the account overview
		// adds up every boss and keeps its verdicts)
		boolean early = bossPage && tiles && any.getExpected() < Texts.TOO_EARLY_BELOW;
		String notable;
		if (moreFacts)
		{
			// one true fact under any verdict, taking turns (the GP value only counts when its tile is shown)
			double valueLuck = value != null && value[1] >= 1 ? value[2] : Double.NaN;
			List<String> facts = early || !tiles ? java.util.Collections.emptyList()
				: Texts.notableLines(items, bosses, luck, valueLuck, what, pageFacts);
			factCount = facts.size();
			notable = facts.isEmpty() ? null : facts.get(factTurn(any.getExpected()) % facts.size());
			boolean more = factCount > 1;
			notableLabel.setCursor(more ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			notableLabel.setToolTipText(more ? "Click for another fact" : null);
		}
		else
		{
			// "On rate" says little by itself: one notable fact under it, when there is one
			notable = !early && Verdict.isOnRate(luck) ? Texts.notableLine(items) : null;
		}
		notableText = notable;
		notableLabel.setText(notable == null ? "" : "<html><body style='width: " + Theme.TEXT_WIDTH + "px'>" + html(notable) + "</body></html>");
		notableLabel.setVisible(notable != null);
		verdictTiles.setVisible(tiles);
		verdictLabel.setVisible(!tiles);
		summaryLabel.setVisible(!tiles);
		if (!tiles)
		{
			return;
		}
		// no GP value tile when nothing of value is expected (it would read "0 / 0")
		boolean showValue = value != null && value[1] >= 1;
		verdictTiles.removeAll();
		verdictTiles.setLayout(new GridLayout(1, showValue ? 2 : 1, 4, 0));
		String verdict = early ? "Too early" : Verdict.labelOf(luck);
		Tip uniquesTip = new Tip(String.format(java.util.Locale.ROOT, "Uniques \u00b7 %s", verdict))
			.row("Got", String.format(java.util.Locale.ROOT, "%,d %s", any.getReceived(),
				any.getReceived() == 1 ? what.substring(0, what.length() - 1) : what))
			.row("Expected by now", formatExpected(any.getExpected()));
		if (early)
		{
			uniquesTip.line("Less than half a " + (what.equals("purples") ? "purple" : "unique")
				+ " expected so far: too early to say if you are dry or lucky.");
		}
		else
		{
			uniquesTip.row("Compared", capital(luckPercent(any.getShares(), any.getReceived())))
				.line(capital(sharesLine(any.getShares(), any.getReceived())), Theme.TEXT_MUTED);
		}
		uniquesTile.set(verdict,
			early ? Theme.TEXT_MUTED : Palette.onRow((bossPage ? Palette.verdict(luck) : luckColor(luck)).brighter()),
			String.format(java.util.Locale.ROOT, "%,d / %s%s", any.getReceived(), formatExpected(any.getExpected()), estimateNote == null ? "" : " est."),
			early ? "Too few " + unitsOf(selectedPage()) : luckShort(any.getShares(), any.getReceived()),
			uniquesTip
				.note("Counts " + what + ", not their value: a twisted bow counts like a prayer scroll.")
				.note(estimateNote)
				.wide());
		verdictTiles.add(uniquesTile);
		if (showValue)
		{
			valueTile.set(early ? "Too early" : Verdict.labelOf(value[2]),
				early ? Theme.TEXT_MUTED : Palette.onRow((bossPage ? Palette.verdict(value[2]) : luckColor(value[2])).brighter()),
				Rates.gp(value[0]) + " / " + Rates.gp(value[1]),
				early ? "" : luckShort(value[2]), valueTip);
			verdictTiles.add(valueTile);
		}
	}

	/**
	 * Which fact the line under the verdict shows on this page: the next one when the page has just been opened
	 * or a kill changed its expected count, the same one on any other redraw (so it never flickers).
	 */
	private int factTurn(double expected)
	{
		String page = String.valueOf(selectedPage());
		Integer turn = factTurns.get(page);
		Double seenAt = factSeenAt.get(page);
		if (turn == null)
		{
			turn = 0;
		}
		else if (!page.equals(factPage) || seenAt == null || seenAt != expected)
		{
			turn++;
		}
		factPage = page;
		factTurns.put(page, turn);
		factSeenAt.put(page, expected);
		return turn;
	}

	/** A click on the line under the verdict: the next fact. */
	private void nextFact()
	{
		if (!moreFacts || factCount < 2 || factPage == null)
		{
			return;
		}
		factTurns.merge(factPage, 1, Integer::sum);
		refresh();
	}

	/** The big verdict ("ON RATE") and the line under it with the numbers behind it. */
	private void showVerdict(RowStats any, String units)
	{
		double luck = any.getLuck();
		String verdict = Verdict.labelOf(luck);
		if (Double.isNaN(luck) || verdict.isEmpty())
		{
			verdictLabel.setToolTipText(null);
			verdictLabel.setText("No " + units + " yet");
			verdictLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			summaryLabel.setText(any.getReceived() == 0 && any.getExpected() <= 0 ? ""
				: String.format(java.util.Locale.ROOT, "%,d unique%s \u00b7 %s expected", any.getReceived(), any.getReceived() == 1 ? "" : "s",
				formatExpected(any.getExpected())));
			return;
		}
		verdictLabel.setText(verdict);
		verdictLabel.setForeground(Palette.onRow(luckColor(luck).brighter()));
		verdictLabel.setToolTipText(capital(sharesLine(any.getShares(), any.getReceived())) + ".");
		summaryLabel.setText(String.format(java.util.Locale.ROOT, "<html>%,d unique%s \u00b7 %s expected<br>%s</html>",
			any.getReceived(), any.getReceived() == 1 ? "" : "s", formatExpected(any.getExpected()),
			capital(luckShort(any.getShares(), any.getReceived()))));
	}

	/** The boss's pet as its picture (every boss has one), else its first unique. */
	private void setHeaderIcon(BossDefinition boss)
	{
		String item = null;
		for (DropDefinition drop : boss.getDrops())
		{
			if (drop.isPet())
			{
				item = drop.getName();
				break;
			}
		}
		if (item == null && !boss.getDrops().isEmpty())
		{
			item = boss.getDrops().get(0).getName();
		}
		if (item == null)
		{
			// a boss whose drops are all shared (Chaos Elemental): its pet from those
			item = sharedItemOf(boss, true);
		}
		int id = item == null ? -1 : itemIdFor.applyAsInt(item);
		if (id > 0 && itemManager != null)
		{
			itemManager.getImage(id).addTo(headerIcon);
		}
		else
		{
			headerIcon.setText(initials(boss.getName()));
			headerIcon.setFont(FontManager.getRunescapeBoldFont());
			headerIcon.setForeground(Theme.TEXT_MUTED);
		}
	}

	/** The account overview: every boss with kills added up, plus one row per boss. */
	private void refreshAccount(ViewOptions o)
	{
		// worked out on a worker thread when a kill started this draw; otherwise here, as always
		AccountStats account = precomputed == null ? null : precomputed.account(o.isGeValue());
		if (account == null)
		{
			account = tracker.accountStats(raidSettings.get(), o.isGeValue() ? priceOf : null);
		}
		RowStats total = account.getTotal();
		shareText = ShareText.account(account, o.isGeValue());
		resetButton.setVisible(false);
		aboutLink.setText("");
		setAbout(null);
		levelsLabel.setVisible(false);
		levelsLabel.setToolTipText(null);
		headerName.setText("Your account");
		headerName.setToolTipText(null);
		kcLabel.setToolTipText(null);
		kcLabel.setText(account.getRows().size() + (account.getRows().size() == 1 ? " boss" : " bosses") + " with kills");
		List<String> unsynced = account.getUnsynced();
		if (account.getRows().isEmpty() && !unsynced.isEmpty())
		{
			kcLabel.setText(unsynced.size() + (unsynced.size() == 1 ? " boss" : " bosses") + " to load");
		}
		sortRow.setVisible(!account.getRows().isEmpty());
		showVerdict(total, "kills");
		if (account.getRows().isEmpty() && !unsynced.isEmpty())
		{
			verdictLabel.setText("Not synced");
		}
		// the name and the count on one line: the account has no picture to make room for
		headerName.setText("<html>Your account <font color='" + Theme.hex(Theme.TEXT_MUTED) + "' size='-1'>\u00b7 "
			+ kcLabel.getText() + "</font></html>");
		kcLabel.setVisible(false);
		headerIcon.setVisible(false);
		double[] accountValue = o.isGeValue() && account.getExpectedValue() > 0 && !Double.isNaN(account.getValueLuck())
			? new double[]{account.getReceivedValue(), account.getExpectedValue(), account.getValueLuck()} : null;
		estimateNote = null;
		List<RowStats> accountItems = new ArrayList<>();
		for (com.onrate.engine.stats.ItemHighlight h : account.getItems())
		{
			accountItems.add(h.getRow());
		}
		showTiles(account.getRows().isEmpty() ? null : total, accountValue, valueTooltip(account), "uniques", accountItems, account.getRows(),
			java.util.Collections.emptyList(), false);

		showLastUnique();
		if (!moreFacts && notableText != null && lastUniqueText != null)
		{
			// one quiet block under the tiles instead of two
			lastUniqueLabel.setText(Theme.wrap(html(notableText + " " + lastUniqueText), Theme.TEXT_WIDTH));
			notableLabel.setVisible(false);
		}

		List<com.onrate.engine.tracking.HistoryPoint> history = tracker.history();
		// a window (30 or 90 days, ending today) only zooms in on the same cumulative line; session-only, All by default
		long historySpan = LuckWindow.span(history);
		int windowDays = 0;
		graphWindowRow.removeAll();
		if (boxes && o.isLuckGraph() && historySpan > 30)
		{
			boolean has90 = historySpan > 90;
			windowDays = graphWindow == 30 || graphWindow == 90 && has90 ? graphWindow : 0;
			String[] windowLabels = has90 ? new String[]{"30 days", "90 days", "All"} : new String[]{"30 days", "All"};
			int[] windowValues = has90 ? new int[]{30, 90, 0} : new int[]{30, 0};
			int windowSelected = 0;
			for (int i = 0; i < windowValues.length; i++)
			{
				if (windowValues[i] == windowDays)
				{
					windowSelected = i;
				}
			}
			graphWindowRow.add(switchRow(windowLabels, windowSelected, i -> graphWindow = windowValues[i]));
		}
		String graphNoteText = "";
		String graphHintText = history.size() >= 2 ? ""
			: "<html><body style='width: " + Theme.TEXT_WIDTH + "px'>On Rate saves one point a day. Your line starts to show from tomorrow.</body></html>";
		if (windowDays > 0)
		{
			long today = java.time.LocalDate.now().toEpochDay();
			long from = LuckWindow.start(today, windowDays);
			List<com.onrate.engine.tracking.HistoryPoint> shown = LuckWindow.select(history, from, today);
			graph.setWindow(shown, from, today);
			graphNoteText = "<html><body style='width: " + Theme.TEXT_WIDTH + "px'>Your whole account's luck on each day, not just the drops from these "
				+ windowDays + " days.</body></html>";
			if (LuckWindow.inside(shown, from) == 0)
			{
				graphHintText = "<html><body style='width: " + Theme.TEXT_WIDTH + "px'>No kills in the last " + windowDays
					+ " days, so the line stays where it was.</body></html>";
			}
		}
		else
		{
			graph.setPoints(history);
		}
		graph.setVisible(o.isLuckGraph() && history.size() >= 2);
		graphNote.setText(graphNoteText);
		graphNote.setVisible(o.isLuckGraph() && !graphNoteText.isEmpty());
		graphHint.setText(graphHintText);
		graphHint.setVisible(o.isLuckGraph() && !graphHintText.isEmpty());

		// Bosses with almost nothing expected yet (a handful of kills) say nothing about luck:
		// leave them out of the list. They still count in the totals above.
		List<RowStats> rows = new ArrayList<>();
		List<String> hidden = new ArrayList<>();
		for (RowStats row : account.getRows())
		{
			if (row.getExpected() < MIN_EXPECTED_SHOWN && row.getReceived() == 0)
			{
				hidden.add(row.getLabel());
			}
			else
			{
				rows.add(row);
			}
		}
		showValueSort(o.isGeValue());
		Sort sort = (Sort) sortSelect.getSelectedItem();
		greenLogPages = null;
		// "Luckiest by GP value": each boss's GP value verdict orders the list and replaces the word and dot
		java.util.Map<String, double[]> byValue = null;
		if (sort == Sort.VALUE && o.isGeValue())
		{
			byValue = new java.util.HashMap<>();
			for (RowStats row : rows)
			{
				BossStats bossStats = tracker.stats(row.getLabel(), raidSettings.get());
				double[] value = bossStats == null ? null : bossValueOf(bossStats);
				if (value != null && value[1] >= 1)
				{
					byValue.put(row.getLabel(), value);
				}
			}
			java.util.Map<String, double[]> valued = byValue;
			// bosses with nothing of value expected (pet-only) go last
			rows.sort(Comparator.comparingDouble((RowStats r) -> valued.containsKey(r.getLabel()) ? -valued.get(r.getLabel())[2] : 2)
				.thenComparing(r -> r.getLabel().toLowerCase(Locale.ROOT)));
		}
		else if (sort == Sort.GREEN_LOG)
		{
			greenLogPages = com.onrate.engine.stats.GreenLog.compute(tracker, com.onrate.engine.data.KillTimes.bundled(),
				raidSettings.get(), this::canFight, greenLogPets);
			rows.sort(greenLogOrder(greenLogPages));
		}
		else
		{
			rows.sort(comparator(sort, account));
		}
		petsSwitch.setOn(greenLogPets);
		petsSwitch.setVisible(sort == Sort.GREEN_LOG);
		sortEnd.setVisible(petsSwitch.isVisible() || accountViewChip.isVisible());
		boolean tabs = !account.getRows().isEmpty();
		if (accountTab == TAB_ITEMS && !o.isTopItems())
		{
			accountTab = TAB_BOSSES;
		}
		if (tabs)
		{
			renderAccountTabs(o.isTopItems());
		}
		if (tabs && accountTab == TAB_ITEMS)
		{
			renderItemsTab(account, o.getItemView());
			// the Items tab has no sort list; the View button stays only where the page drew rows that follow the view
			if (viewButtons && viewApplies)
			{
				sortControls(false);
			}
			else
			{
				sortRow.setVisible(false);
			}
		}
		else if (tabs && accountTab == TAB_RECENT)
		{
			sortRow.setVisible(false);
			renderRecentTab();
		}
		else
		{
			renderAccountItems(account, total, rows, o.getItemView(), byValue);
			addAccountFootnote(account, hidden, account.getRows().isEmpty() ? java.util.Collections.emptyList() : unsynced);
		}

		String note = account.getRows().isEmpty() && !unsynced.isEmpty()
			? "Open the collection log page" + (unsynced.size() == 1 ? "" : "s") + " of " + joinAnd(unsynced) + " to load your past drops. "
			+ "Your account shows up here once a boss is loaded."
			: account.getRows().isEmpty()
			? "Open your collection log and click the pages of the bosses you do, or go kill one. "
			+ "Your account shows up here."
			: "Every boss you have kills at, added up. Click one to open it.<br><br>"
			+ "<b>Items</b>: your driest and luckiest items, your pets, single drops and how long the items you don't have take (the Time page). "
			+ "Turn it off under Display in the settings to hide all of these. <b>Recent</b>: your latest uniques.<br><br>"
			+ "GP value uses RuneLite's GE prices. Pets and most jars count as 0 GP, so a twisted bow outweighs many cheap drops.<br><br>"
			+ "Items several bosses drop (dragon pickaxe, godsword shards, Virtus, onyx...) are on each boss's page. "
			+ "They count in the totals once On Rate knows every kill count.";
		if (!profileLoaded())
		{
			// no account loaded: the empty account isn't theirs, so no "0 bosses" and no setup steps
			note = Texts.profileNote(profileState);
			headerName.setText("Your account");
			verdictLabel.setText(Texts.profileVerdict(profileState));
			verdictLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			verdictLabel.setToolTipText(null);
			summaryLabel.setText("");
		}
		setHelp(note);
		noteLabel.setText(account.getRows().isEmpty() ? "<html><body style='width: " + Theme.TEXT_WIDTH + "px'>" + note + "</body></html>" : "");
		noteLabel.setToolTipText(null);
	}

	/** For tests: the account page's note and the big verdict word. */
	String noteText()
	{
		return noteLabel.getText();
	}

	String verdictText()
	{
		return verdictLabel.getText();
	}

	boolean isSetupShown()
	{
		return setupHeader.isVisible() || welcomePanel.isVisible();
	}

	boolean isWelcomeShown()
	{
		return welcomePanel.isVisible();
	}

	/** What On Rate knows about your team at a group boss, for the note under the items. */
	public static String teamSizeNote(java.util.Map<Integer, Integer> teamKills, int kc)
	{
		return teamSizeNote(teamKills, kc, null);
	}

	/** Same, with how the kills from before are counted when a setting decides it (Yama). */
	public static String teamSizeNote(java.util.Map<Integer, Integer> teamKills, int kc, String olderNote)
	{
		int seen = 0;
		double players = 0;
		for (java.util.Map.Entry<Integer, Integer> e : teamKills.entrySet())
		{
			seen += e.getValue();
			players += e.getKey() * (double) e.getValue();
		}
		if (seen == 0)
		{
			return "Your team size is counted from your next kill on (the players fighting with you). "
				+ (olderNote == null ? "Until then every kill counts as solo." : "Until then: " + lowerFirst(olderNote));
		}
		String text = String.format(java.util.Locale.ROOT, "On Rate saw %,d of your kills, with %.1f players on average, "
			+ "and gives you that share of each kill.", seen, players / seen);
		int older = Math.max(0, kc - seen);
		if (older > 0)
		{
			text += olderNote == null
				? String.format(java.util.Locale.ROOT, " Your %,d earlier kill%s count as solo.", older, older == 1 ? "" : "s")
				: String.format(java.util.Locale.ROOT, " Your %,d earlier kill%s: %s", older, older == 1 ? "" : "s", lowerFirst(olderNote));
		}
		return text;
	}

	/** On Rate's Discord: bugs, wrong rates and ideas. Opened in the browser, never contacted by the plugin. */
	public static final String DISCORD = "https://discord.gg/gh9fx6T9zn";

	/** On Rate's GitHub issue chooser (bug, wrong drop rate, idea). Opened in the browser, never contacted by the plugin. */
	public static final String REPORT_URL = "https://github.com/Pyrocrad/On-Rate/issues/new/choose";

	/** A quiet link at the bottom of the panel that opens the GitHub issue chooser in your browser. */
	private JLabel feedbackLink()
	{
		JLabel link = new JLabel("<html><u>Feedback</u></html>");
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		link.setAlignmentX(LEFT_ALIGNMENT);
		link.setToolTipText("Report a bug or a wrong drop rate, or share an idea. Opens GitHub in your browser.");
		link.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				net.runelite.client.util.LinkBrowser.browse(REPORT_URL);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		return Activatable.install(link, "Feedback, opens GitHub in your browser", () -> net.runelite.client.util.LinkBrowser.browse(REPORT_URL));
	}

	/** How long "Copied!" stays on the link before it says "Copy my luck" again. */
	private static final int COPIED_MILLIS = 2000;

	/** A quiet footer link that copies the account's verdicts as plain text. Nothing is sent anywhere. */
	private JLabel buildShareLink()
	{
		shareLink.setFont(FontManager.getRunescapeSmallFont());
		shareLink.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		shareLink.setAlignmentX(LEFT_ALIGNMENT);
		shareLink.setToolTipText("Copies your account's verdicts as text, to paste into Discord or Reddit.");
		shareLink.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		shareLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				copyLuck();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				shareLink.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				shareLink.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		Activatable.install(shareLink, "Copy my luck, copies your verdicts as text", this::copyLuck);
		return shareLink;
	}

	/** Puts the share text on the clipboard and says so on the link for a moment. */
	private void copyLuck()
	{
		String text = shareText;
		if (text == null)
		{
			return;
		}
		try
		{
			java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new java.awt.datatransfer.StringSelection(text), null);
		}
		catch (RuntimeException e)
		{
			// no clipboard to copy to (some desktops lock it): say so rather than pretend it worked
			shareLink.setText("<html><u>Couldn't copy</u></html>");
			resetShareLinkSoon();
			return;
		}
		shareLink.setText("<html><u>Copied!</u></html>");
		resetShareLinkSoon();
	}

	private void resetShareLinkSoon()
	{
		javax.swing.Timer timer = new javax.swing.Timer(COPIED_MILLIS, e -> shareLink.setText("<html><u>Copy my luck</u></html>"));
		timer.setRepeats(false);
		timer.start();
	}

	/** The quiet dot between footer links. */
	private static JLabel footerDot(JLabel dot)
	{
		dot.setText("  \u00b7  ");
		dot.setFont(FontManager.getRunescapeSmallFont());
		dot.setForeground(Theme.TEXT_MUTED);
		return dot;
	}

	/** Shows each footer link only where it applies, and a dot only between two links. */
	private void updateFooter()
	{
		boolean about = aboutLink.getText() != null && !aboutLink.getText().isEmpty();
		aboutLink.setVisible(about);
		aboutSep.setVisible(about);
		resetSep.setVisible(resetButton.isVisible());
		boolean share = shareText != null;
		shareLink.setVisible(share);
		shareSep.setVisible(share);
	}

	private static final String ESTIMATE_NOTE = "Estimate: it depends on your team, raid points or setup, which On Rate can't always see.";

	/** One quiet line under the bosses: bosses with too few kills to list (hover for which). */
	private void addAccountFootnote(AccountStats account, List<String> hidden, List<String> unsynced)
	{
		List<String> parts = new ArrayList<>();
		StringBuilder tip = new StringBuilder("<html><body style='width: " + Theme.TIP_WIDE + "px'>");
		if (!unsynced.isEmpty())
		{
			parts.add(String.format(java.util.Locale.ROOT, "%d boss%s not loaded yet", unsynced.size(), unsynced.size() == 1 ? "" : "es"));
			tip.append("<b>Not loaded yet</b>, so not counted: ").append(String.join(", ", unsynced))
				.append(". On Rate knows the kill count but not your drops from before; open their collection log page once.<br><br>");
		}
		if (!hidden.isEmpty())
		{
			java.util.Collections.sort(hidden);
			parts.add(String.format(java.util.Locale.ROOT, "+ %d boss%s too early to list", hidden.size(), hidden.size() == 1 ? "" : "es"));
			tip.append("<b>Too early to judge</b> (under 0.05 uniques expected), so not listed: ")
				.append(String.join(", ", hidden)).append(". They still count in your account total.");
		}
		if (parts.isEmpty())
		{
			return;
		}
		rowsPanel.add(spacer(Theme.S));
		JLabel line = smallLabel("<html><body style='width: " + Theme.TEXT_WIDTH + "px'>" + String.join(" \u00b7 ", parts) + "</body></html>",
			ColorScheme.LIGHT_GRAY_COLOR);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setToolTipText(tip.append("</body></html>").toString());
		rowsPanel.add(line);
	}

	/** A gap in drop rates as a colour: well under the rate is lucky, well over it is dry. */
	private static Color multipleColor(com.onrate.engine.tracking.KillEntry.DropEntry d)
	{
		return multipleColor(d, false);
	}

	/** Grey for a first drop (nothing to compare with) unless {@code evenFirst}: the Drops view colours every drop by its luck. */
	private static Color multipleColor(com.onrate.engine.tracking.KillEntry.DropEntry d, boolean evenFirst)
	{
		Double m = d.getMultiple();
		return (d.isFirst() && !evenFirst) || m == null ? ColorScheme.LIGHT_GRAY_COLOR : Palette.onRow(luckColor(Math.exp(-m)).brighter());
	}

	/**
	 * One block of the Recent tab: the total for these days, then a line per boss. The uniques
	 * are green when more than expected and orange when fewer; the expected number is on hover.
	 * Returns false (and adds nothing) when there were no kills.
	 */
	private boolean renderDays(String title, List<com.onrate.engine.tracking.BossDays> days)
	{
		com.onrate.engine.tracking.BossDays total = days.get(0);
		if (total.getKills() == 0 && total.getUniques() == 0)
		{
			return false;
		}
		JPanel block = new JPanel(new GridLayout(0, 1, 0, 0));
		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		block.setAlignmentX(LEFT_ALIGNMENT);
		for (com.onrate.engine.tracking.BossDays d : days)
		{
			boolean isTotal = d.getBoss() == null;
			BossDefinition boss = isTotal ? null : tracker.getCatalog().byName(d.getBoss());
			// each boss in its own unit ("5 delves", "2 raids"); the total mixes them, so "kills"
			String units = boss == null ? "kills" : com.onrate.engine.tracking.Tracker.unit(boss);
			String unitsHere = d.getKills() == 1 ? units.substring(0, units.length() - 1) : units;
			double luck = Luck.of(d.getUniques(), d.getExpected());
			Color uniquesColor = Double.isNaN(luck) || d.getExpected() <= 0 || (luck >= 0.35 && luck <= 0.65)
				? Color.WHITE : Palette.onRow(luckColor(luck).brighter());
			JPanel line = new JPanel(new BorderLayout(6, 0));
			line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			// boss names in the normal font, a size up from the small one, so they're easy to read
			JLabel name = isTotal ? boldLabel(title) : new JLabel(d.getBoss());
			if (!isTotal)
			{
				name.setFont(FontManager.getRunescapeFont());
				name.setForeground(Theme.TEXT);
				name.setBorder(new EmptyBorder(0, Theme.M, 0, 0));
			}
			line.add(name, BorderLayout.CENTER);
			String color = String.format(java.util.Locale.ROOT, "#%06x", uniquesColor.getRGB() & 0xFFFFFF);
			String mark = uniqueMarks
				? Texts.uniquesMark(luck, d.getExpected(), FontManager.getRunescapeFont().canDisplay(Texts.MARK_MORE.charAt(0))) : "";
			JLabel numbers = smallLabel(String.format(java.util.Locale.ROOT, "<html>%,d %s \u00b7 <font color='%s'>%,d%s%s</font></html>", d.getKills(),
				unitsHere,
				color, d.getUniques(), isTotal ? (d.getUniques() == 1 ? " unique" : " uniques") : "",
				mark.isEmpty() ? "" : " <font size='-2'>" + mark + "</font>"),
				ColorScheme.LIGHT_GRAY_COLOR);
			numbers.setFont(FontManager.getRunescapeFont());
			line.add(numbers, BorderLayout.EAST);
			Tip dayTip = new Tip((isTotal ? "All bosses" : d.getBoss()) + ", " + title.toLowerCase(Locale.ROOT))
				.row(capital(units), String.format(java.util.Locale.ROOT, "%,d", d.getKills()))
				.row("Uniques", String.format(java.util.Locale.ROOT, "%,d", d.getUniques()))
				.row("Expected", "about " + formatExpected(d.getExpected()));
			if (!Double.isNaN(luck) && d.getExpected() > 0)
			{
				dayTip.row("Compared", capital(luckPercent(Poisson.shares(d.getUniques(), d.getExpected()), d.getUniques())), uniquesColor);
			}
			if (isTotal)
			{
				dayTip.note(uniqueMarks
					? "A coloured unique count means clearly more (\u25b2) or fewer (\u25bc) than expected."
					: "A coloured unique count means clearly more or fewer than expected.");
			}
			addUniqueNames(dayTip, isTotal, d, days);
			String tip = dayTip.wide();
			String bossName = d.getBoss();
			// a boss line opens that boss's page
			setTip(line, tip, isTotal ? null : () -> showBoss(bossName));
			block.add(line);
		}
		block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
		rowsPanel.add(spacer(Theme.HAIR));
		rowsPanel.add(Card.row(block));
		return true;
	}

	/**
	 * Which uniques those were, for the hover of a Recent line: one line each ("Tyrannical
	 * ring"), or on the total line a row each with the boss on the left. Nothing when History
	 * doesn't have them.
	 */
	private static void addUniqueNames(Tip tip, boolean isTotal, com.onrate.engine.tracking.BossDays d,
		List<com.onrate.engine.tracking.BossDays> days)
	{
		// {boss, item}
		List<String[]> names = new ArrayList<>();
		for (com.onrate.engine.tracking.BossDays b : isTotal ? days.subList(1, days.size()) : java.util.Collections.singletonList(d))
		{
			for (String n : b.getUniqueNames())
			{
				names.add(new String[]{b.getBoss(), n});
			}
		}
		if (names.isEmpty())
		{
			return;
		}
		tip.line(names.size() == 1 ? "The unique" : "The uniques", Theme.TEXT_MUTED);
		int shown = Math.min(10, names.size());
		for (String[] n : names.subList(0, shown))
		{
			if (isTotal)
			{
				tip.row(n[0], n[1]);
			}
			else
			{
				tip.line(n[1]);
			}
		}
		if (names.size() > shown)
		{
			tip.note("And " + (names.size() - shown) + " more.");
		}
	}

	/** The gap before a unique, for a hover row: "7 kills (0.1\u00d7)", "your first one", or "". */
	private static String gapText(com.onrate.engine.tracking.KillEntry.DropEntry d, String unit)
	{
		String after = afterText(d, unit);
		return after.startsWith("after ") ? after.substring("after ".length()) : after;
	}

	/** The account's Recent tab: today, the last 7 days and the latest uniques across every boss. */
	private void renderRecentTab()
	{
		long nowMs = System.currentTimeMillis();
		boolean days = renderDays("Today", tracker.recentDays(1, nowMs));
		days |= renderDays("Last 7 days", tracker.recentDays(7, nowMs));
		if (days)
		{
			rowsPanel.add(spacer(Theme.M));
		}
		List<com.onrate.engine.tracking.TripStats> trips = tripList ? tracker.recentTrips(nowMs) : java.util.Collections.emptyList();
		if (!trips.isEmpty())
		{
			JLabel tripsTitle = smallLabel("TRIPS THIS SESSION", ColorScheme.LIGHT_GRAY_COLOR);
			tripsTitle.setAlignmentX(LEFT_ALIGNMENT);
			tripsTitle.setToolTipText("Kept until RuneLite closes or you switch account. Today and Last 7 days above are saved.");
			rowsPanel.add(tripsTitle);
			for (com.onrate.engine.tracking.TripStats t : trips)
			{
				rowsPanel.add(spacer(Theme.HAIR));
				rowsPanel.add(tripRow(t, nowMs));
			}
			rowsPanel.add(spacer(Theme.M));
		}
		List<com.onrate.engine.tracking.RecentDrop> recent = tracker.recentDrops(cappedListNotes ? Integer.MAX_VALUE : RECENT_SHOWN);
		int recentTotal = recent.size();
		boolean recentCapped = cappedListNotes && recentTotal > RECENT_SHOWN;
		if (recentCapped)
		{
			// the heading carries "top 10 of N \u00b7 show all"
			int recentShown = recentAll ? recentTotal : RECENT_SHOWN;
			dropListTitle("LATEST UNIQUES", recentShown, recentTotal, RECENT_SHOWN, () -> recentAll = !recentAll,
				"Your latest uniques across every boss, newest first.");
			recent = recent.subList(0, recentShown);
		}
		else if (!recent.isEmpty())
		{
			JLabel latest = smallLabel("LATEST UNIQUES", ColorScheme.LIGHT_GRAY_COLOR);
			latest.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(latest);
		}
		if (recent.isEmpty())
		{
			JLabel none = smallLabel("<html><body style='width: " + Theme.TEXT_WIDTH + "px'>No uniques seen yet. They show up here from now on.</body></html>",
				ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(LEFT_ALIGNMENT);
			none.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
			rowsPanel.add(none);
			return;
		}
		long now = System.currentTimeMillis();
		for (com.onrate.engine.tracking.RecentDrop r : recent)
		{
			rowsPanel.add(spacer(Theme.HAIR));
			rowsPanel.add(recentDropRow(r, now));
		}
	}

	/** A wait for the Longest waits list: a drop that took long, or an item you're still waiting for. */
	private static final class Wait
	{
		final double multiple;
		final com.onrate.engine.tracking.RecentDrop drop;
		final com.onrate.engine.stats.ItemHighlight ongoing;

		Wait(double multiple, com.onrate.engine.tracking.RecentDrop drop, com.onrate.engine.stats.ItemHighlight ongoing)
		{
			this.multiple = multiple;
			this.drop = drop;
			this.ongoing = ongoing;
		}
	}

	/**
	 * Every long wait, longest first: drops that took longer than their drop rate, and items
	 * you're still waiting for (dry for more than one drop rate since the last copy, or since the
	 * start), ranked together by how many times the drop rate the wait is.
	 */
	private List<Wait> allWaits(AccountStats account)
	{
		List<Wait> all = new ArrayList<>();
		for (com.onrate.engine.tracking.RecentDrop d : tracker.dropsByLuck(false, Integer.MAX_VALUE))
		{
			all.add(new Wait(d.getDrop().getMultiple(), d, null));
		}
		for (com.onrate.engine.stats.ItemHighlight h : account.getItems())
		{
			RowStats r = h.getRow();
			double m = r.getStreakMultiple();
			if (!r.isAny() && !r.isComplete() && r.getStreak() != null && r.isStreakExact() && r.getRate() > 0
				&& !Double.isNaN(m) && m > 1)
			{
				all.add(new Wait(m, null, h));
			}
		}
		all.sort((a, b) -> Double.compare(b.multiple, a.multiple));
		return all;
	}

	/** How many drops each list of the Drops view shows. */
	static final int DROPS_SHOWN = 5;
	/** The Drops view: show the whole Luckiest drops / Longest waits list instead of the top {@link #DROPS_SHOWN}. */
	private boolean luckiestDropsAll;
	private boolean longestWaitsAll;

	/**
	 * The Items tab's Drops view: your luckiest single drops and your longest waits (drops that
	 * took long, and items you're still waiting for). Each drop is judged once, by how long it
	 * took; the other views of the tab show where you stand now.
	 */
	private void renderDropLists(AccountStats account)
	{
		long now = System.currentTimeMillis();
		List<com.onrate.engine.tracking.RecentDrop> lucky = tracker.dropsByLuck(true, Integer.MAX_VALUE);
		List<Wait> waits = allWaits(account);
		JLabel note = smallLabel("<html><body style='width: " + noteWidth(190) + "px'>Single drops, each judged once by how long it took. "
			+ "The Items page shows where you stand now.</body></html>", Theme.TEXT_MUTED);
		note.setAlignmentX(LEFT_ALIGNMENT);
		rowsPanel.add(note);
		if (lucky.isEmpty() && waits.isEmpty())
		{
			JLabel none = smallLabel("<html><body style='width: " + noteWidth(190) + "px'>No single drops with a known wait yet. They appear as you get "
				+ "uniques. For one you got before, right-click the item and choose \"I got this at kill...\".</body></html>",
				ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(LEFT_ALIGNMENT);
			none.setBorder(new EmptyBorder(Theme.M, 0, 0, 0));
			rowsPanel.add(none);
			return;
		}
		if (!lucky.isEmpty())
		{
			int luckyShown = luckiestDropsAll ? lucky.size() : Math.min(DROPS_SHOWN, lucky.size());
			dropListTitle("LUCKIEST SINGLE DROPS", luckyShown, lucky.size(), () -> luckiestDropsAll = !luckiestDropsAll,
				"Drops that came sooner than their drop rate says on average, luckiest first.");
			for (com.onrate.engine.tracking.RecentDrop r : lucky.subList(0, luckyShown))
			{
				rowsPanel.add(spacer(Theme.HAIR));
				rowsPanel.add(dropRow(r, now));
			}
		}
		if (!waits.isEmpty())
		{
			int waitsShown = longestWaitsAll ? waits.size() : Math.min(DROPS_SHOWN, waits.size());
			dropListTitle("LONGEST WAITS", waitsShown, waits.size(), () -> longestWaitsAll = !longestWaitsAll,
				"Drops that took longer than their drop rate says on average, and items you are still waiting for, "
					+ "longest first (as a multiple of the drop rate).");
			for (Wait w : waits.subList(0, waitsShown))
			{
				rowsPanel.add(spacer(Theme.HAIR));
				rowsPanel.add(w.drop != null ? dropRow(w.drop, now) : waitingRow(w.ongoing, w.multiple));
			}
		}
	}

	private void dropListTitle(String heading, int shown, int total, Runnable toggle, String help)
	{
		dropListTitle(heading, shown, total, DROPS_SHOWN, toggle, help);
	}

	/** The same for a list that stops at {@code base} entries instead of {@link #DROPS_SHOWN}. */
	private void dropListTitle(String heading, int shown, int total, int base, Runnable toggle, String help)
	{
		rowsPanel.add(spacer(Theme.M));
		JPanel line = new JPanel(new BorderLayout());
		line.setOpaque(false);
		line.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = smallLabel(heading, ColorScheme.LIGHT_GRAY_COLOR);
		title.setToolTipText("<html><body style='width: " + Theme.TIP_WIDE + "px'>" + help + "</body></html>");
		line.add(title, BorderLayout.WEST);
		if (total > base)
		{
			// click to see the whole list, and again to go back to the top few
			boolean all = shown == total;
			String text = all ? "all " + total + " \u00b7 show top " + base : "top " + shown + " of " + total + " \u00b7 show all";
			String tip = all ? "Show only the top " + base : "Show all " + total;
			JLabel more = smallLabel(text, ColorScheme.LIGHT_GRAY_COLOR);
			more.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			more.setToolTipText(tip);
			Runnable flip = () ->
			{
				toggle.run();
				refresh();
			};
			more.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					flip.run();
				}

				@Override
				public void mouseEntered(MouseEvent e)
				{
					more.setForeground(Color.WHITE);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					more.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				}
			});
			line.add(Activatable.install(more, tip, flip), BorderLayout.EAST);
		}
		else
		{
			line.add(smallLabel(String.valueOf(total), Theme.TEXT_MUTED), BorderLayout.EAST);
		}
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		rowsPanel.add(line);
	}

	/**
	 * A row of the Drops view: the item's picture, its name, where it dropped with the kill count,
	 * and how lucky the wait was in the colour of its luck. {@code pill} is a small tag on the right.
	 */
	private JPanel threeLineRow(String item, String name, String where, String luck, Color luckColor, String pill)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(itemIcon(item, name, 28), BorderLayout.WEST);
		JPanel text = new JPanel(new GridLayout(3, 1, 0, 0));
		text.setOpaque(false);
		JLabel first = smallLabel(name, Color.WHITE);
		first.setFont(FontManager.getRunescapeFont());
		text.add(first);
		text.add(smallLabel(where, ColorScheme.LIGHT_GRAY_COLOR));
		text.add(smallLabel(luck, luckColor));
		panel.add(text, BorderLayout.CENTER);
		if (pill != null)
		{
			JLabel tag = smallLabel(pill, luckColor);
			tag.setBorder(javax.swing.BorderFactory.createCompoundBorder(
				javax.swing.BorderFactory.createLineBorder(luckColor), new EmptyBorder(0, Theme.S, 0, Theme.S)));
			if (boxes)
			{
				// the card look: the tinted pill from under the verdict tiles, same word
				tag = new LuckPill(pill, luckColor, 0);
			}
			JPanel east = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
			east.setOpaque(false);
			east.add(tag);
			panel.add(east, BorderLayout.EAST);
		}
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		return Card.row(panel);
	}

	/** A single drop in the Drops view. Left click opens the boss; right click is the Remove from History menu. */
	private JPanel dropRow(com.onrate.engine.tracking.RecentDrop r, long now)
	{
		com.onrate.engine.tracking.KillEntry.DropEntry d = r.getDrop();
		BossDefinition boss = tracker.getCatalog().byName(r.getBoss());
		JPanel panel = threeLineRow(d.getName(), d.getName(), whereText(boss, r.getBoss(), r.getKill()),
			dropLuckShort(d), multipleColor(d, true), null);
		setTip(panel, dropTip(r, now), () -> showBoss(r.getBoss()), true);
		withRemoveMenu(panel, r.getBoss(), r.getKill(), java.util.Collections.singletonList(d));
		return panel;
	}

	/** An item you're still waiting for: tagged "waiting", with the kills so far and how unlikely that is. */
	private JPanel waitingRow(com.onrate.engine.stats.ItemHighlight h, double multiple)
	{
		RowStats r = h.getRow();
		BossDefinition boss = h.getBoss() == null ? null : tracker.getCatalog().byName(h.getBoss());
		String unit = boss == null ? "kills" : com.onrate.engine.tracking.Tracker.unit(boss);
		String where = h.getBoss() == null ? "several bosses" : h.getBoss();
		JPanel panel = threeLineRow(r.getIconItem(), r.getLabel(), where,
			String.format(java.util.Locale.ROOT, "%,d %s \u00b7 %s", r.getStreak(), unit, waitLuckShort(multiple, true)),
			Palette.onRow(luckColor(Math.exp(-multiple)).brighter()), "waiting");
		Tip tip = new Tip(r.getLabel())
			.row("Where", where)
			.row("Without one", String.format(java.util.Locale.ROOT, "%,d %s, %s", r.getStreak(), unit, formatMultiple(multiple) + " the drop rate"))
			.row("Luck", waitLuck(multiple));
		addTimeRows(tip, timeOf(h.getBoss(), r.getIconItem()));
		setTip(panel, tip.note("Still going: each " + (unit.endsWith("s") ? unit.substring(0, unit.length() - 1) : unit)
			+ " is its own roll, so a long wait doesn't make the next one more likely.").wide(),
			h.getBoss() == null ? null : () -> showBoss(h.getBoss()), true);
		return panel;
	}

	/** The hover of a drop: where, the kill count, when, the gap before it and how lucky it was. */
	private String dropTip(com.onrate.engine.tracking.RecentDrop r, long now)
	{
		com.onrate.engine.tracking.KillEntry.DropEntry d = r.getDrop();
		BossDefinition boss = tracker.getCatalog().byName(r.getBoss());
		String unit = boss == null ? "kill" : com.onrate.engine.tracking.Tracker.unitOne(boss);
		Tip tip = new Tip(d.getName())
			.row("Where", whereName(boss, r.getBoss(), r.getKill()))
			.row(capital(unit) + " count", killCount(boss, r.getKill()))
			.row("When", daysAgo(r.getKill().getTime(), now))
			.row("Gap before it", gapText(d, unit));
		if (!dropLuck(d).isEmpty())
		{
			tip.row("Luck", dropLuck(d));
		}
		return tip.wide();
	}

	/**
	 * One finished trip as a row: boss, kills and uniques on top, and underneath when, how long and the verdict.
	 * Under half an expected unique it says "Too early" in a quiet colour, with no percentage.
	 */
	private JPanel tripRow(com.onrate.engine.tracking.TripStats t, long now)
	{
		BossDefinition boss = tracker.getCatalog().byName(t.getBoss());
		String units = boss == null ? "kills" : com.onrate.engine.tracking.Tracker.unit(boss);
		double luck = t.getLuck();
		String verdict = Texts.tripVerdict(luck, t.getExpected());
		boolean early = verdict != null && t.getExpected() < Texts.TOO_EARLY_BELOW;
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
		text.setOpaque(false);
		JLabel what = smallLabel(t.getBoss() + "  \u00b7 " + Texts.tripCounts(t.getKills(), units, t.getReceived()), Color.WHITE);
		what.setFont(FontManager.getRunescapeFont());
		text.add(what);
		String second = daysAgo(t.getLastMillis(), now) + " \u00b7 " + Texts.tripLength(t.getLastMillis() - t.getStartMillis())
			+ (verdict == null ? "" : " \u00b7 " + verdict);
		text.add(smallLabel(second, verdict == null || early ? ColorScheme.LIGHT_GRAY_COLOR : verdictWordColor(luck)));
		panel.add(text, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		Tip tip = new Tip(t.getBoss() + ", a finished trip")
			.row(capital(units), String.format(java.util.Locale.ROOT, "%,d", t.getKills()))
			.row("Uniques / expected", t.getReceived() + " / " + formatExpected(t.getExpected()));
		if (early)
		{
			tip.note("Under half a unique expected: too early to say if this trip was dry or lucky.");
		}
		else if (verdict != null)
		{
			tip.row("Compared", capital(luckPercent(t.getShares(), t.getReceived())));
		}
		setTip(panel, tip.note("Trips are kept until RuneLite closes or you switch account.").wide(), () -> showBoss(t.getBoss()));
		return Card.row(panel);
	}

	/** One latest unique as a row: what and where on top, and underneath when and the wait before it. */
	private JPanel recentDropRow(com.onrate.engine.tracking.RecentDrop r, long now)
	{
		com.onrate.engine.tracking.KillEntry.DropEntry d = r.getDrop();
		BossDefinition boss = tracker.getCatalog().byName(r.getBoss());
		String unit = boss == null ? "kill" : com.onrate.engine.tracking.Tracker.unitOne(boss);
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
		text.setOpaque(false);
		JLabel what = smallLabel(d.getName() + "  \u00b7 " + whereText(boss, r.getBoss(), r.getKill()), Color.WHITE);
		what.setFont(FontManager.getRunescapeFont());
		text.add(what);
		String after = afterText(d, unit);
		text.add(smallLabel(daysAgo(r.getKill().getTime(), now) + (after.isEmpty() ? "" : " \u00b7 " + after), multipleColor(d)));
		panel.add(text, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		// left click opens the boss; right click is the Remove from History menu
		setTip(panel, dropTip(r, now), () -> showBoss(r.getBoss()), true);
		withRemoveMenu(panel, r.getBoss(), r.getKill(), java.util.Collections.singletonList(d));
		return Card.row(panel);
	}

	/**
	 * The folded History section at the bottom of a boss page: the kills On Rate saw, newest
	 * first. Normal bosses list their drops; raids add points and purple chance, group bosses
	 * the team size, Doom the delve level, Royal Titans the titan you looted.
	 */
	private void renderHistory(BossStats stats, String unit)
	{
		BossDefinition boss = stats.getBoss();
		List<com.onrate.engine.tracking.HistoryRow> history = tracker.history(boss.getName());
		// another boss starts at ten rows again, even when the boss in between had no history to show
		if (!boss.getName().equals(historyLimitBoss))
		{
			historyLimitBoss = boss.getName();
			historyLimit = 10;
		}
		rowsPanel.add(spacer(Theme.M));
		JLabel title = smallLabel((historyOpen ? "\u25be" : "\u25b8") + " HISTORY", Theme.TEXT_MUTED);
		brightenOnHover(title);
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setToolTipText("The " + unit + "s and drops On Rate saw since you installed it");
		title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				historyOpen = !historyOpen;
				rememberFolds();
				refresh();
			}
		});
		Activatable.install(title, "History, " + (historyOpen ? "open" : "closed"), () ->
		{
			historyOpen = !historyOpen;
			rememberFolds();
			refresh();
		});
		rowsPanel.add(title);
		if (!historyOpen)
		{
			return;
		}
		if (history.isEmpty())
		{
			addHistoryNote("No " + unit + "s seen yet. History starts from your next " + unit + ".");
			return;
		}
		boolean raid = boss.getRaid() != null;
		// the raids that set a personal best say PB at the end of their line
		java.util.Map<com.onrate.engine.tracking.RaidRecord, String> bests = raid && raidExtras
			? personalBestRaids(boss) : java.util.Collections.emptyMap();
		boolean team = boss.isDetectTeam();
		// delve levels and Colosseum runs: every run is listed with its level or wave
		boolean delve = boss.isDelve() || boss.getWaves() > 0;
		boolean titans = "Royal Titans".equals(boss.getName());
		boolean dropsOnly = !raid && !team && !delve && !titans;
		long now = System.currentTimeMillis();
		int limit = historyMore ? (historyLimit == 0 ? Integer.MAX_VALUE : historyLimit) : 10;
		// rows the list could show: one per unique at a drops-only boss, else one per kill
		int available = 0;
		int shown = 0;
		int drops = 0;
		// which uniques those were ({item, where}), for the hover on the rows under the list
		List<String[]> dropNames = new ArrayList<>();
		for (com.onrate.engine.tracking.HistoryRow h : history)
		{
			com.onrate.engine.tracking.KillEntry k = h.getKill();
			drops += k.getDrops().size();
			for (com.onrate.engine.tracking.KillEntry.DropEntry d : k.getDrops())
			{
				int at = k.getPartKc() != null && boss.hasParts() ? k.getPartKc() : k.getKc();
				dropNames.add(new String[]{d.getName(), String.format(java.util.Locale.ROOT, "%s%s %,d",
					boss.hasParts() && k.getPart() != null && boss.getRaid() == null ? k.getPart() + " " : "", unit, at)});
			}
			if (dropsOnly && k.getDrops().isEmpty())
			{
				continue;
			}
			// a drops-only boss shows a row for each unique, so those are what the limit counts
			available += historyMore && dropsOnly ? k.getDrops().size() : 1;
			if (shown >= limit)
			{
				continue;
			}
			if (!(historyMore && dropsOnly))
			{
				shown++;
			}
			String first;
			String middle;
			if (delve)
			{
				first = boss.getWaves() > 0 ? "W" + (k.getLevel() == null ? "?" : k.getLevel())
					: "Lvl " + (k.getLevel() == null ? "?" : BossDefinition.delveLevelLabel(k.getLevel()));
				middle = daysAgo(k.getTime(), now);
			}
			else
			{
				first = String.format(java.util.Locale.ROOT, "%,d", k.getPartKc() != null && raid ? k.getPartKc() : k.getKc());
				if (raid)
				{
					com.onrate.engine.tracking.RaidRecord r = h.getRaid();
					String mode = k.getPart() != null && !"Normal".equals(k.getPart()) ? shortMode(k.getPart()) + " " : "";
					boolean archive = "fractured".equals(boss.getRaid());
					String chance = Double.isNaN(h.getPurpleChance()) ? "" : String.format(java.util.Locale.ROOT, " \u00b7 %.1f%%", h.getPurpleChance() * 100);
					if (r == null)
					{
						middle = mode + (archive ? "not tracked" : "no points");
					}
					else if (archive)
					{
						// your estimated contribution when On Rate saw every fight, else the team it counted
						middle = (r.getContribution() >= 0 ? String.format(java.util.Locale.ROOT, "share %.0f/700", r.getContribution())
							: r.getTeamSize() > 0 ? "team of " + r.getTeamSize() : "team unknown")
							+ (r.getDeaths() > 0 ? " \u00b7 " + r.getDeaths() + " death" + (r.getDeaths() == 1 ? "" : "s") : "")
							+ chance;
					}
					else if (r.getPersonalPoints() > 0)
					{
						middle = mode + formatPoints(r.getPersonalPoints()) + " points"
							+ (Double.isNaN(h.getPurpleChance()) ? "" : String.format(java.util.Locale.ROOT, " \u00b7 %.1f%%", h.getPurpleChance() * 100));
					}
					else
					{
						middle = mode + "team of " + r.getTeamSize() + (r.getDeaths() > 0 ? " \u00b7 " + r.getDeaths() + " death" + (r.getDeaths() == 1 ? "" : "s") : "");
					}
				}
				else if (team)
				{
					middle = k.getTeam() == null ? "team unknown" : k.getTeam() == 1 ? "solo" : "team of " + k.getTeam() + " (1/" + k.getTeam() + ")";
				}
				else if (titans)
				{
					middle = k.getTitan() == null ? "titan unknown" : k.getTitan();
				}
				else
				{
					middle = null;
				}
			}
			String took = com.onrate.engine.tracking.ChatTimes.format(k.getDurationMillis() == null ? 0 : k.getDurationMillis());
			if (!took.isEmpty() && !dropsOnly)
			{
				// how long it took, after what the row already says
				middle = middle == null ? took : middle + " · " + took;
			}
			if (raid && h.getRaid() != null && bests.containsKey(h.getRaid()) && middle != null)
			{
				middle = "<html>" + Texts.html(middle) + " <font color='" + Theme.hex(Theme.TEXT) + "'>PB</font></html>";
			}
			if (dropsOnly)
			{
				boolean byPart = boss.hasParts() && k.getPart() != null && k.getPartKc() != null;
				for (com.onrate.engine.tracking.KillEntry.DropEntry d : k.getDrops())
				{
					if (historyMore)
					{
						if (shown >= limit)
						{
							break;
						}
						shown++;
					}
					String after = afterText(d, unit).replace("after ", "");
					Tip dropTip = new Tip(d.getName());
					if (byPart)
					{
						dropTip.row("Where", whereName(boss, boss.getName(), k));
					}
					dropTip.row(capital(unit) + " count", String.format(java.util.Locale.ROOT, "%,d", byPart ? k.getPartKc() : k.getKc()))
						.row("When", daysAgo(k.getTime(), now));
					if (!took.isEmpty())
					{
						dropTip.row("Time", took);
					}
					dropTip.row("Gap before it", gapText(d, unit));
					if (!dropLuck(d).isEmpty())
					{
						dropTip.row("Luck", dropLuck(d));
					}
					String tip = dropTip.wide();
					rowsPanel.add(withRemoveMenu(byPart
						? historyTwoLines(String.format(java.util.Locale.ROOT, "%,d", k.getPartKc()), d.getName(), Color.WHITE,
							k.getPart() + (after.isEmpty() ? "" : " \u00b7 " + after), multipleColor(d), tip)
						: historyLine(first, d.getName(), after, Color.WHITE, multipleColor(d), tip),
						boss.getName(), k, java.util.Collections.singletonList(d)));
				}
			}
			else
			{
				StringBuilder loot = new StringBuilder();
				for (com.onrate.engine.tracking.KillEntry.DropEntry d : k.getDrops())
				{
					loot.append(loot.length() > 0 ? ", " : "").append(d.getName());
				}
				String tip = historyKillTip(boss, h, unit, now, loot.toString());
				rowsPanel.add(loot.length() == 0
					? historyLine(first, middle, "", ColorScheme.LIGHT_GRAY_COLOR, ColorScheme.LIGHT_GRAY_COLOR, tip)
					: withRemoveMenu(historyLootLine(first, middle, loot.toString(), tip), boss.getName(), k, k.getDrops()));
			}
		}
		if (dropsOnly && shown == 0)
		{
			addHistoryNote("No uniques since you installed On Rate yet.");
		}
		if (historyMore && (available > shown || (historyLimit != 10 && available > 10)))
		{
			addHistoryMoreLink(available, shown, unit, dropsOnly);
		}
		int seen = tracker.killsSeen(boss.getName());
		// the uniques in History, one row each, on hover of the summary rows
		Tip namesTip = new Tip(drops == 1 ? "The unique" : "The uniques");
		for (String[] n : dropNames.subList(0, Math.min(15, dropNames.size())))
		{
			namesTip.row(n[0], n[1]);
		}
		String tip = dropNames.isEmpty() ? null
			: namesTip.note(dropNames.size() > 15 ? "And " + (dropNames.size() - 15) + " more." : null).wide();
		boolean summary = false;
		if (raid)
		{
			for (com.onrate.engine.data.BossPart part : boss.getParts())
			{
				double a = tracker.averagePurpleChance(boss.getName(), part.getName());
				if (!Double.isNaN(a))
				{
					if (!summary)
					{
						JLabel heading = smallLabel("fractured".equals(boss.getRaid()) ? "Average purple chance from your damage"
							: "Average purple chance from points", ColorScheme.LIGHT_GRAY_COLOR);
						heading.setAlignmentX(LEFT_ALIGNMENT);
						if (boxes)
						{
							// the heading is the first row of the card its part rows go in
							rowsPanel.add(spacer(Theme.S));
							heading.setBorder(new EmptyBorder(Theme.HAIR, 0, Theme.HAIR, 0));
							rowsPanel.add(Card.row(heading));
						}
						else
						{
							heading.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
							rowsPanel.add(heading);
						}
						rowsPanel.add(spacer(Theme.HAIR));
						summary = true;
					}
					rowsPanel.add(cardRow(part.getName(), String.format(java.util.Locale.ROOT, "%.1f%%", a * 100), Color.WHITE, tip));
				}
			}
			for (com.onrate.engine.data.BossPart part : boss.getParts())
			{
				addTimeRow(boss.hasParts() && boss.getParts().size() > 1 ? "Time · " + shortMode(part.getName()) : "Time",
					tracker.killTimes(boss.getName(), part.getName()), -1, raidExtras && !bests.isEmpty());
			}
		}
		else if (!delve)
		{
			rowsPanel.add(spacer(Theme.S));
			rowsPanel.add(cardRow("Seen", String.format(java.util.Locale.ROOT, "last %,d %s%s", seen, unit, seen == 1 ? "" : "s"), Color.WHITE, tip));
			// a boss whose drops are all shared has no expected of its own
			rowsPanel.add(cardRow("Uniques", boss.getDrops().isEmpty() ? String.format(java.util.Locale.ROOT, "%,d", drops)
				: String.format(java.util.Locale.ROOT, "%,d (%s expected)", drops, formatExpected(stats.getAnyRow().getRate() * seen)), Color.WHITE, tip));
			if (teamSplit && team)
			{
				addTeamSplitRow(stats);
			}
			// one fight, one list of kills: the detailed hover. A team boss (solo and team kills together) or a
			// combined page (several fights together) would make "fastest" mean something else, so they keep the average only
			addTimeRow("Time", tracker.killTimes(boss.getName(), null), team || (boss.hasParts() && boss.getParts().size() > 1) ? -1 : seen, false);
		}
		else if (boss.isDelve() && delveTimes)
		{
			addDelveTimeRow(boss);
		}
		if (seen >= com.onrate.engine.tracking.Tracker.MAX_KILLS)
		{
			addHistoryNote("On Rate keeps your last " + com.onrate.engine.tracking.Tracker.MAX_KILLS + " " + unit + "s here.");
		}
	}

	/** The "Teams" row under History at a group boss: kills per team size, with how they count towards expected. */
	private void addTeamSplitRow(BossStats stats)
	{
		BossDefinition boss = stats.getBoss();
		java.util.Map<Integer, Integer> teamKills = tracker.teamKills(boss.getName());
		int kc = stats.getKillCount();
		String text = teamSplitText(teamKills, kc);
		if (text == null)
		{
			return;
		}
		int older = Math.max(0, kc - teamKills.values().stream().mapToInt(Integer::intValue).sum());
		// the same share Statistics uses for the kills from before team counting
		double olderShare = boss.getTeam() != null ? raidSettings.get().contribution(boss.getTeam(), older)
			: tracker.olderShare(boss.getName()) != null ? tracker.olderShare(boss.getName()) : 1.0;
		rowsPanel.add(cardRow("Teams", "<html><body style='width: " + (Theme.TEXT_WIDTH - 50) + "px'><div align='right'>"
			+ html(text) + "</div></body></html>", Color.WHITE,
			teamSplitTip(teamKills, kc, olderShare, "Zalcano".equals(boss.getName()))));
	}

	/**
	 * Kills per team size at a group boss, as "Solo: 12 · Duo: 40 · Trio: 95", sizes from
	 * {@link RaidInsights#TEAM_CAP} up merged into one. Kills from before On Rate counted teams
	 * (the kill count minus the ones it saw) come last as "Earlier". Null when it saw none.
	 */
	static String teamSplitText(java.util.Map<Integer, Integer> teamKills, int kc)
	{
		java.util.Map<Integer, Integer> buckets = teamBuckets(teamKills);
		if (buckets.isEmpty())
		{
			return null;
		}
		List<String> parts = new ArrayList<>();
		for (java.util.Map.Entry<Integer, Integer> e : buckets.entrySet())
		{
			parts.add(teamWord(e.getKey()) + ": " + String.format(java.util.Locale.ROOT, "%,d", e.getValue()));
		}
		int older = Math.max(0, kc - teamKills.values().stream().mapToInt(Integer::intValue).sum());
		if (older > 0)
		{
			parts.add(String.format(java.util.Locale.ROOT, "Earlier: %,d", older));
		}
		return String.join(" · ", parts);
	}

	/** Seen kills per team size for display: sizes of {@link RaidInsights#TEAM_CAP} and up together, sizes below 1 as solo. */
	private static java.util.Map<Integer, Integer> teamBuckets(java.util.Map<Integer, Integer> teamKills)
	{
		java.util.Map<Integer, Integer> buckets = new java.util.TreeMap<>();
		for (java.util.Map.Entry<Integer, Integer> e : teamKills.entrySet())
		{
			if (e.getValue() > 0)
			{
				buckets.merge(Math.min(RaidInsights.TEAM_CAP, Math.max(1, e.getKey())), e.getValue(), Integer::sum);
			}
		}
		return buckets;
	}

	/**
	 * How many of your kills count at a group boss: a kill with 4 players counts as a quarter, the
	 * kills from before team counting at {@code olderShare} each. Worked out from the real team sizes
	 * (not the merged display buckets), the same sum the expected number uses.
	 */
	public static double teamSplitShared(java.util.Map<Integer, Integer> teamKills, int kc, double olderShare)
	{
		int seen = 0;
		double share = 0;
		for (java.util.Map.Entry<Integer, Integer> e : teamKills.entrySet())
		{
			seen += e.getValue();
			share += e.getValue() / (double) Math.max(1, e.getKey());
		}
		return Math.max(0, kc - seen) * olderShare + share;
	}

	private static String countsAs(double kills)
	{
		return Math.abs(kills - Math.rint(kills)) < 1e-9 ? String.format(java.util.Locale.ROOT, "%,d", Math.round(kills))
			: String.format(java.util.Locale.ROOT, "%,.1f", kills);
	}

	/** The hover of the Teams row: each size's kills and what they count as, and the total behind Expected. */
	static String teamSplitTip(java.util.Map<Integer, Integer> teamKills, int kc, double olderShare, boolean around)
	{
		Tip tip = new Tip("Your kills by team size");
		java.util.Map<Integer, Double> counted = new java.util.TreeMap<>();
		for (java.util.Map.Entry<Integer, Integer> e : teamKills.entrySet())
		{
			if (e.getValue() > 0)
			{
				counted.merge(Math.min(RaidInsights.TEAM_CAP, Math.max(1, e.getKey())),
					e.getValue() / (double) Math.max(1, e.getKey()), Double::sum);
			}
		}
		for (java.util.Map.Entry<Integer, Integer> e : teamBuckets(teamKills).entrySet())
		{
			String each = e.getKey() >= RaidInsights.TEAM_CAP ? "each a sixth or less"
				: e.getKey() == 1 ? "each counts in full" : "each counts as 1/" + e.getKey();
			tip.row(teamWord(e.getKey()), String.format(java.util.Locale.ROOT, "%,d kill%s, %s = %s",
				e.getValue(), e.getValue() == 1 ? "" : "s", each, countsAs(counted.get(e.getKey()))));
		}
		int older = Math.max(0, kc - teamKills.values().stream().mapToInt(Integer::intValue).sum());
		if (older > 0)
		{
			tip.row("Earlier", String.format(java.util.Locale.ROOT, "%,d kill%s from before On Rate counted teams = %s",
				older, older == 1 ? "" : "s", countsAs(older * olderShare)));
		}
		tip.row("Counts as", String.format(java.util.Locale.ROOT, "%s of %,d kills", countsAs(teamSplitShared(teamKills, kc, olderShare)), kc));
		tip.note("Expected uniques use this share of your kills. Pets and drops each player rolls for aren't scaled. "
			+ "The team size is the " + (around ? "players around you" : "players seen fighting with you")
			+ " at each kill, and sharing a kill evenly is an assumption.");
		return tip.wide();
	}

	/** "Solo", "Duo", "Trio", "Team of 4" ... "Team of 6+", or "Team unknown". */
	static String teamWord(int team)
	{
		if (team <= 0)
		{
			return "Team unknown";
		}
		switch (team)
		{
			case 1:
				return "Solo";
			case 2:
				return "Duo";
			case 3:
				return "Trio";
			default:
				return team >= RaidInsights.TEAM_CAP ? "Team of " + RaidInsights.TEAM_CAP + "+" : "Team of " + team;
		}
	}

	/**
	 * A team with how it was scaled (Chambers of Xeric): "Trio" for three players in a raid scaled
	 * for three, "1+2" for one player in a raid scaled for three, and "Trio/scaled" when it isn't
	 * known whether players had left (raids from before On Rate counted them).
	 */
	static String teamLabel(int team, int players)
	{
		if (team <= 1 || players == team)
		{
			return teamWord(team);
		}
		if (players < 0)
		{
			return teamWord(team) + "/scaled";
		}
		return players + "+" + (team - players) + (team >= RaidInsights.TEAM_CAP ? "+" : "");
	}

	/** The name of a setup row: its team size and its mode, or at Tombs of Amascut its raid level. */
	static String setupName(BossDefinition boss, RaidInsights.Setup s)
	{
		List<String> parts = new ArrayList<>();
		if (s.getTeam() >= 0)
		{
			parts.add(teamLabel(s.getTeam(), s.getPlayers()));
		}
		if (s.getLevelFrom() >= 0)
		{
			parts.add("level " + s.getLevelFrom() + "–" + (s.getLevelFrom() + RaidInsights.LEVEL_STEP - 1));
		}
		else if ("toa".equals(boss.getRaid()) && s.getTeam() < 0)
		{
			parts.add("Level unknown");
		}
		else if (s.getPart() != null && boss.getParts().size() > 1 && !"toa".equals(boss.getRaid()))
		{
			parts.add(shortMode(s.getPart()));
		}
		return parts.isEmpty() ? "All raids" : String.join(" · ", parts);
	}

	/**
	 * The raids that hold a personal best (fastest, most points, biggest share), each with what it
	 * holds. Only once a mode and team size has more than one raid: a first raid is no record yet.
	 */
	private java.util.Map<com.onrate.engine.tracking.RaidRecord, String> personalBestRaids(BossDefinition boss)
	{
		java.util.Map<com.onrate.engine.tracking.RaidRecord, String> raids = new java.util.IdentityHashMap<>();
		for (RaidInsights.Bests b : raidInsights(boss.getName(), false).getBests())
		{
			if (b.getRaids() < 2)
			{
				continue;
			}
			for (RaidInsights.Best best : new RaidInsights.Best[]{b.getFastest(), b.getMostPoints(), b.getBiggestShare()})
			{
				if (best != null)
				{
					String what = best == b.getFastest() ? "fastest" : best == b.getMostPoints() ? "most points" : "biggest share";
					raids.merge(best.getRaid(), what, (a, c) -> a + ", " + c);
				}
			}
		}
		return raids;
	}

	/** A folding heading under History, like History's own. */
	private void addFoldHeading(String text, boolean open, String tip, Runnable toggle)
	{
		rowsPanel.add(spacer(Theme.M));
		JLabel title = smallLabel((open ? "▾ " : "▸ ") + text, Theme.TEXT_MUTED);
		brightenOnHover(title);
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setToolTipText(tip);
		title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				toggle.run();
				refresh();
			}
		});
		Activatable.install(title, capital(text.toLowerCase(java.util.Locale.ROOT)) + ", " + (open ? "open" : "closed"), () ->
		{
			toggle.run();
			refresh();
		});
		rowsPanel.add(title);
	}

	/** A row of small switches (tabs or chips); the selected one is white with an orange line under it. */
	private JPanel switchRow(String[] labels, int selected, java.util.function.IntConsumer pick)
	{
		JPanel row = new JPanel(new GridLayout(1, labels.length, 2, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(Theme.S, 0, Theme.S, 0));
		// the card look: the same control as Items / Pets / Single drops / Time (a filled cell for the choice)
		JPanel cells = row;
		if (boxes)
		{
			cells = new JPanel(new GridLayout(1, labels.length, Theme.HAIR, 0));
			cells.setBackground(Theme.SURFACE);
			cells.setBorder(new EmptyBorder(Theme.HAIR, Theme.HAIR, Theme.HAIR, Theme.HAIR));
			row.setLayout(new BorderLayout());
			row.add(cells, BorderLayout.CENTER);
		}
		for (int i = 0; i < labels.length; i++)
		{
			int index = i;
			boolean on = i == selected;
			JLabel label = smallLabel(labels[i], on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			label.setHorizontalAlignment(SwingConstants.CENTER);
			label.setOpaque(true);
			label.setBackground(on ? Theme.SURFACE_SELECTED : Theme.SURFACE_RAISED);
			label.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 2, 0, on ? Theme.ACCENT : Theme.SURFACE_RAISED),
				new EmptyBorder(Theme.HAIR, Theme.HAIR, Theme.HAIR, Theme.HAIR)));
			if (boxes)
			{
				label.setOpaque(on);
				label.setBorder(new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S));
			}
			if (!on)
			{
				label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
				label.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						pick.accept(index);
						refresh();
					}
				});
			}
			Activatable.install(label, labels[i] + (on ? ", selected" : ""), () ->
			{
				pick.accept(index);
				refresh();
			});
			cells.add(label);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** The raid sections under History: purple chance by setup, personal bests, points over time. */
	private void renderRaidInsights(BossStats stats, boolean gp)
	{
		BossDefinition boss = stats.getBoss();
		RaidInsights insights = raidInsights(boss.getName(), gp);
		if (insights.isEmpty())
		{
			renderLogForecast(stats, insights);
			return;
		}
		renderSetups(boss, insights, gp);
		renderLogForecast(stats, insights);
		renderBests(boss, insights);
		renderPointsOverTime(boss, insights);
	}

	/**
	 * Raids left for the log: how many more raids it takes to have every unique you're missing, at how
	 * you raid now (your latest raids On Rate saw, else the settings). A forecast, not a target.
	 */
	private void renderLogForecast(BossStats stats, RaidInsights insights)
	{
		RaidInsights.Setup recent = insights.getRecent();
		long avg = recent != null && recent.getTimed() >= RaidInsights.MIN_TIMED ? recent.getAverageMillis() : -1;
		String basis = recent != null
			? String.format(java.util.Locale.ROOT, "your last %,d raid%s", recent.getRaids(), recent.getRaids() == 1 ? "" : "s")
			: "the settings (no raids recorded yet)";
		renderLogTable(stats, "RAIDS LEFT FOR THE LOG",
			"How many more raids it takes to have every unique you're missing, at how you raid now. "
				+ "Each raid is a fresh roll: raids you already did don't change it",
			"Open this raid's collection log page once, so On Rate knows which uniques you have.",
			"You have every unique and the pet. Nothing left for this log page.",
			stats.isClogSynced() ? raidMissing(stats, insights) : null, avg, basis, "raids", null);
	}

	/**
	 * The same forecast for a boss page: the uniques you're missing at their chance a kill, with the hours at
	 * your average kill time when known. A combined page gets one small table per part.
	 */
	private void renderBossLogForecast(BossStats stats)
	{
		BossDefinition boss = stats.getBoss();
		String unit = com.onrate.engine.tracking.Tracker.unit(boss);
		String headingTip = "How many more " + unit + " it takes to have every unique you're missing, at the chances shown. "
			+ "Each " + com.onrate.engine.tracking.Tracker.unitOne(boss) + " is a fresh roll: " + unit
			+ " you already did don't change it";
		String unsynced = "Open this boss's collection log page once, so On Rate knows which uniques you have.";
		String footnote = logFootnote(stats);
		if (boss.hasParts())
		{
			renderPartForecasts(stats, "UNIQUES FORECAST", headingTip, unsynced, footnote);
			return;
		}
		double seconds = boss.isDelve()
			? com.onrate.engine.stats.ItemTimes.doomSecondsPerDelve(tracker, com.onrate.engine.data.KillTimes.bundled(), boss, stats)
			: com.onrate.engine.stats.ItemTimes.secondsPerKill(tracker, com.onrate.engine.data.KillTimes.bundled(), boss, stats);
		RaidMissing m = null;
		if (boss.isDelve() && stats.getKillCount() <= 0)
		{
			// Doom's chance is the average over the delve levels you did: with none there is no chance yet
			unsynced = "No delves seen yet. Do a delve, or open the Doom scoreboard, so On Rate knows how you delve.";
		}
		else if (stats.isClogSynced())
		{
			List<RowStats> counted = new ArrayList<>();
			for (int i = 0; i < stats.getDrops().size(); i++)
			{
				if (tracker.counts(boss, stats.getDrops().get(i)))
				{
					counted.add(stats.getRows().get(i));
				}
			}
			m = killMissing(stats, counted);
		}
		renderLogTable(stats, "UNIQUES FORECAST", headingTip, unsynced,
			"You have every unique and the pet. Nothing left for this log page.",
			m, seconds > 0 ? Math.round(seconds * 1000) : -1, "the drop rates", unit, footnote);
	}

	/** The lines under a boss's forecast table: what it leaves out and what its chances are based on. */
	private String logFootnote(BossStats stats)
	{
		BossDefinition boss = stats.getBoss();
		List<String> lines = new ArrayList<>();
		for (DropDefinition d : boss.getDrops())
		{
			if (d.getGuaranteedAt() != null)
			{
				lines.add(d.getDisplayName() + " is guaranteed at kill " + d.getGuaranteedAt() + " and is not part of this table.");
			}
		}
		if (boss.isDelve())
		{
			lines.add("These chances use the delve levels you have done so far. Delving deeper raises them.");
		}
		if (boss.isEstimate())
		{
			lines.add("These chances use your average share or estimate so far, as the rest of this page does.");
		}
		if (!tracker.sharedPoolsFor(boss.getName(), raidSettings.get()).isEmpty())
		{
			lines.add("Items other bosses drop too are judged together under Shared with other bosses, not in this table.");
		}
		return lines.isEmpty() ? null : String.join(" ", lines);
	}

	/**
	 * A combined page (Dagannoth Kings...): one small table per part, with that part's own kill count and rates,
	 * never a blend. A part with no kills yet shows a note instead of a table.
	 */
	private void renderPartForecasts(BossStats stats, String heading, String headingTip, String unsyncedNote, String footnote)
	{
		BossDefinition boss = stats.getBoss();
		addFoldHeading(heading, logOpen, headingTip, () -> logOpen = !logOpen);
		if (!logOpen)
		{
			return;
		}
		if (!stats.isClogSynced())
		{
			addHistoryNote(unsyncedNote);
			return;
		}
		java.util.Map<String, Integer> kills = tracker.partKills(boss);
		for (com.onrate.engine.data.BossPart part : boss.getParts())
		{
			int kc = kills.getOrDefault(part.getName(), 0);
			JLabel title = smallLabel(part.getName().toUpperCase(java.util.Locale.ROOT)
				+ (kc > 0 ? String.format(java.util.Locale.ROOT, " (%,d kills)", kc) : ""), Theme.TEXT_MUTED);
			title.setAlignmentX(LEFT_ALIGNMENT);
			title.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
			rowsPanel.add(title);
			if (kc <= 0)
			{
				addHistoryNote("No " + part.getName() + " kills seen yet. Kill it, or open the collection log page, "
					+ "and its forecast shows here.");
				continue;
			}
			renderLogBody(stats, partMissing(stats, part), -1, "the drop rates", "kills",
				"You have every unique and the pet from " + part.getName() + ".");
		}
		if (footnote != null)
		{
			addHistoryNote(footnote);
		}
	}

	/** What one part of a combined page still misses, at that part's own chances. */
	private RaidMissing partMissing(BossStats stats, com.onrate.engine.data.BossPart part)
	{
		BossDefinition boss = stats.getBoss();
		RaidMissing m = new RaidMissing();
		for (int i = 0; i < stats.getDrops().size(); i++)
		{
			DropDefinition drop = stats.getDrops().get(i);
			RowStats r = stats.getRows().get(i);
			if (!tracker.counts(boss, drop) || !drop.hasPartRates() || drop.getPartRate(part.getName()) == null
				|| r.getReceived() > 0 || r.isComplete())
			{
				continue;
			}
			double p = com.onrate.engine.tracking.RateModel.partRate(boss, part, drop, raidSettings.get());
			if (!(p > 0))
			{
				continue;
			}
			if (drop.isPet())
			{
				if (Double.isNaN(m.pet))
				{
					m.pet = p;
					m.petLabel = r.getLabel();
				}
				continue;
			}
			m.names.add(r.getLabel());
			m.chances.add(p);
		}
		return m;
	}

	/**
	 * The folded forecast table: what's missing, and the {@code unit}s until 50% and 90% of players at these
	 * chances would have everything. A forecast, not a target.
	 *
	 * @param m null when the collection log page isn't loaded yet
	 */
	private void renderLogTable(BossStats stats, String heading, String headingTip, String unsyncedNote, String doneNote,
		RaidMissing m, long avg, String basis, String unit, String footnote)
	{
		addFoldHeading(heading, logOpen, headingTip, () -> logOpen = !logOpen);
		if (!logOpen)
		{
			return;
		}
		if (m == null)
		{
			addHistoryNote(unsyncedNote);
			return;
		}
		if (renderLogBody(stats, m, avg, basis, unit, doneNote) && footnote != null)
		{
			addHistoryNote(footnote);
		}
	}

	/** The missing line and the 50% / 90% rows for one forecast. Returns false when nothing is missing. */
	private boolean renderLogBody(BossStats stats, RaidMissing m, long avg, String basis, String unit, String doneNote)
	{
		List<String> missing = m.names;
		List<Double> chances = m.chances;
		double pet = m.pet;
		if (missing.isEmpty() && Double.isNaN(pet))
		{
			addHistoryNote(doneNote);
			return false;
		}
		double[] p = chances.stream().mapToDouble(Double::doubleValue).toArray();
		// what's missing, one per line in the hover, each with its chance a raid
		Tip missingTip = new Tip("Missing").line("Chance a " + (unit.equals("raids") ? "raid" : "kill") + ", from " + basis,
			Theme.TEXT_MUTED);
		for (int i = 0; i < missing.size(); i++)
		{
			missingTip.row(missing.get(i), Texts.oneIn(chances.get(i)));
		}
		if (!Double.isNaN(pet))
		{
			missingTip.row(m.petLabel != null ? m.petLabel : petName(stats), Texts.oneIn(pet));
		}
		rowsPanel.add(cardRow("Missing", Texts.missingText(missing.size(), !Double.isNaN(pet)), Color.WHITE, missingTip.wide()));
		// a small table: raids until 50% and 90% of players at your chances would be done
		rowsPanel.add(forecastRow("", "50% chance", "90% chance", Theme.TEXT_MUTED, null));
		if (!missing.isEmpty())
		{
			addForecast(missing.size() == 1 ? missing.get(0) : "All uniques", p, avg, unit);
		}
		if (!Double.isNaN(pet))
		{
			double[] withPet = java.util.Arrays.copyOf(p, p.length + 1);
			withPet[p.length] = pet;
			addForecast(missing.isEmpty() ? "The pet" : "With the pet", withPet, avg, unit);
		}
		return true;
	}

	/** What a raid's log still misses: the uniques with their chance a raid, and the pet's (NaN when you have it). */
	private static final class RaidMissing
	{
		final List<String> names = new ArrayList<>();
		final List<Double> chances = new ArrayList<>();
		double pet = Double.NaN;
		/** The pet's row label, when it is missing. */
		String petLabel;
	}

	/** The missing uniques of a raid at how you raid now (your last raids, else the settings). */
	private RaidMissing raidMissing(BossStats stats, RaidInsights insights)
	{
		BossDefinition boss = stats.getBoss();
		RaidInsights.Setup recent = insights.getRecent();
		com.onrate.engine.data.BossPart usual = usualPart(stats);
		RaidMissing m = new RaidMissing();
		for (RowStats row : stats.getRows())
		{
			DropDefinition drop = row.isAny() ? null : findDrop(stats.getDrops(), row.getLabel());
			if (drop == null || row.getReceived() > 0 || !tracker.counts(boss, drop))
			{
				continue;
			}
			double p = recent != null ? recent.getItemPerRaid(drop.getName())
				: usual == null || !com.onrate.engine.tracking.RateModel.fromPurplePublic(usual, drop) ? Double.NaN
				: com.onrate.engine.tracking.RateModel.partRate(boss, usual, drop, raidSettings.get());
			if (Double.isNaN(p))
			{
				// not from the purple (a flat roll like the Fractured Archive's folio): not part of this forecast
				continue;
			}
			if (drop.isPet())
			{
				m.pet = p;
				continue;
			}
			m.names.add(row.getLabel());
			m.chances.add(p);
		}
		return m;
	}

	/** The uniques a plain boss's page still misses (not received, counted, with a chance a kill), and the pet's chance. */
	private RaidMissing killMissing(BossStats stats, List<RowStats> counted)
	{
		RaidMissing m = new RaidMissing();
		for (RowStats r : counted)
		{
			if (r == null || r.isAny() || r.getReceived() > 0 || r.isComplete() || !(r.getRate() > 0))
			{
				continue;
			}
			DropDefinition drop = findDrop(stats.getDrops(), r.getLabel());
			if (drop != null && drop.getGuaranteedAt() != null)
			{
				// a guaranteed drop (Vorkath's head) isn't an independent roll: left out, said under the table
				continue;
			}
			if (drop != null && drop.isPet())
			{
				// a boss with several pets keeps the first one only; rare
				if (Double.isNaN(m.pet))
				{
					m.pet = r.getRate();
					m.petLabel = r.getLabel();
				}
				continue;
			}
			m.names.add(r.getLabel());
			m.chances.add(r.getRate());
		}
		return m;
	}

	/**
	 * Whether a boss page can show the log forecast table. Every single-fight boss, except: raids (their own
	 * table), the Colosseum (the chance changes with the wave), and bosses flagged
	 * "logForecast": false in bosses.json (Moons of Peril, Wintertodt). A boss with no rows of its own (Chaos
	 * Elemental) is left out by the caller. Doom is in, at the delve levels you have done so far. Team, God Wars and other estimate bosses are in, with a footnote;
	 * a guaranteed drop (Vorkath's head) stays out of the table; a combined page gets one table per part.
	 */
	private static boolean logTableAllowed(BossDefinition boss)
	{
		return boss != null && boss.getRaid() == null && boss.getWaves() <= 0 && boss.isLogForecast();
	}

	/**
	 * The facts a boss page adds to the line under the verdict: how long the rest of the log takes (once the
	 * collection log page is loaded, so On Rate knows what you have) and the average pace of uniques. Hours come
	 * from the same kill time as the Time page. None for Doom: its chances change with the delve level.
	 */
	private List<String> bossFacts(BossStats stats, List<RowStats> counted, boolean raid, boolean delve)
	{
		List<String> facts = new ArrayList<>();
		if (delve)
		{
			return facts;
		}
		BossDefinition boss = stats.getBoss();
		double seconds = com.onrate.engine.stats.ItemTimes.secondsPerKill(tracker,
			com.onrate.engine.data.KillTimes.bundled(), boss, stats);
		String units = com.onrate.engine.tracking.Tracker.unit(boss);
		// the sentence follows the table's rule; a combined page has no one blended sentence (its tables are per part)
		if (stats.isClogSynced() && (raid || logTableAllowed(boss) && !boss.hasParts()))
		{
			// the missing items and, apart, the pet: the line gives the log with and without it
			List<String> names = new ArrayList<>();
			List<Double> chances = new ArrayList<>();
			double pet = Double.NaN;
			String petLabel = null;
			if (raid)
			{
				RaidMissing m = raidMissing(stats, raidInsights(boss.getName(), false));
				names.addAll(m.names);
				chances.addAll(m.chances);
				pet = m.pet;
				petLabel = petName(stats);
			}
			else
			{
				RaidMissing m = killMissing(stats, counted);
				names.addAll(m.names);
				chances.addAll(m.chances);
				pet = m.pet;
				petLabel = m.petLabel;
			}
			boolean petMissing = !Double.isNaN(pet);
			double[] items = chances.stream().mapToDouble(Double::doubleValue).toArray();
			long withoutPet = com.onrate.engine.stats.LogForecast.raidsFor(items, 0.5);
			long withPet = withoutPet;
			if (petMissing)
			{
				double[] all = java.util.Arrays.copyOf(items, items.length + 1);
				all[items.length] = pet;
				withPet = com.onrate.engine.stats.LogForecast.raidsFor(all, 0.5);
			}
			String only = names.size() == 1 && !petMissing ? names.get(0) : names.isEmpty() && petMissing ? petLabel : null;
			String line = Texts.logLeftLine(withPet, withoutPet, petMissing, seconds, units, only);
			if (line != null)
			{
				facts.add(line);
			}
		}
		RowStats any = stats.getAnyRow();
		String pace = Texts.averagePaceLine(any == null ? Double.NaN : any.getRate(), seconds, units, raid ? "purple" : "unique");
		if (pace != null)
		{
			facts.add(pace);
		}
		return facts;
	}

	/** One forecast: its raids, and under it the hours at your average raid time when known. */
	private void addForecast(String label, double[] perRaid, long avgMillis, String unit)
	{
		long half = com.onrate.engine.stats.LogForecast.raidsFor(perRaid, 0.5);
		long most = com.onrate.engine.stats.LogForecast.raidsFor(perRaid, 0.9);
		String tip = new Tip(label).row("50% chance to be done by", Texts.raidsText(half) + " " + unit)
			.row("90% chance to be done by", Texts.raidsText(most) + " " + unit).wide();
		rowsPanel.add(forecastRow(label, Texts.raidsText(half), Texts.raidsText(most), Color.WHITE, tip));
		if (avgMillis > 0 && half >= 0 && most >= 0)
		{
			rowsPanel.add(forecastRow("  in hours", Texts.hoursText(half, avgMillis), Texts.hoursText(most, avgMillis),
				ColorScheme.LIGHT_GRAY_COLOR, tip));
		}
	}

	/** Width of each number column of the forecast table. */
	private static final int FORECAST_COLUMN = 64;

	/** A forecast table row: a label, then two right-aligned columns (50% and 90%). */
	private static JPanel forecastRow(String label, String half, String most, Color color, String tip)
	{
		JPanel row = new JPanel(new BorderLayout(Theme.S, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(new EmptyBorder(Theme.HAIR, 0, Theme.HAIR, 0));
		JLabel name = smallLabel(label, label.startsWith(" ") ? Theme.TEXT_MUTED : Color.WHITE);
		row.add(name, BorderLayout.CENTER);
		JPanel numbers = new JPanel(new java.awt.GridLayout(1, 2, Theme.S, 0));
		numbers.setOpaque(false);
		JLabel a = smallLabel(half, color);
		JLabel b = smallLabel(most, color);
		for (JLabel l : new JLabel[]{a, b})
		{
			l.setHorizontalAlignment(SwingConstants.RIGHT);
			l.setPreferredSize(new Dimension(FORECAST_COLUMN, l.getPreferredSize().height));
			numbers.add(l);
		}
		row.add(numbers, BorderLayout.EAST);
		for (javax.swing.JComponent c : new javax.swing.JComponent[]{row, name, a, b})
		{
			c.setToolTipText(tip);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return Card.row(row);
	}

	/** The raid's pet name, for the missing list. */
	private static String petName(BossStats stats)
	{
		for (DropDefinition d : stats.getDrops())
		{
			if (d.isPet())
			{
				return d.getDisplayName();
			}
		}
		return "Pet";
	}

	/** The raid mode with the most completions (for the settings when no raids were recorded), or null. */
	private static com.onrate.engine.data.BossPart usualPart(BossStats stats)
	{
		com.onrate.engine.data.BossPart best = null;
		int most = -1;
		for (com.onrate.engine.data.BossPart part : stats.getBoss().getParts())
		{
			if (part.getRaid() == null || "none".equals(part.getRaid()))
			{
				continue;
			}
			Integer kc = stats.getPartCounts() == null ? null : stats.getPartCounts().get(part.getName());
			int n = kc == null ? 0 : kc;
			if (n > most)
			{
				most = n;
				best = part;
			}
		}
		return best;
	}

	private void renderSetups(BossDefinition boss, RaidInsights insights, boolean gp)
	{
		addFoldHeading("PURPLE CHANCE BY SETUP", setupsOpen,
			"Which of your setups gets you the most purples an hour, from the raids On Rate timed", () -> setupsOpen = !setupsOpen);
		if (!setupsOpen)
		{
			return;
		}
		boolean gpTab = gp && setupsGp;
		if (gp)
		{
			rowsPanel.add(switchRow(new String[]{"Purple chance", "GP value"}, gpTab ? 1 : 0, i -> setupsGp = i == 1));
		}
		boolean toa = "toa".equals(boss.getRaid());
		boolean modes = boss.getParts().size() > 1 && !toa;
		String choice = toa ? "Raid level" : modes ? "Mode" : null;
		if (choice != null)
		{
			rowsPanel.add(switchRow(new String[]{"Setup", "Team", choice}, setupsBy, i -> setupsBy = i));
		}
		List<RaidInsights.Setup> setups = new ArrayList<>(insights.getSetups(choice == null ? RaidInsights.BY_SETUP : setupsBy));
		if (gpTab)
		{
			// the GP value tab sorts by GP an hour; rows without enough timed raids stay last
			setups.sort(java.util.Comparator.comparing((RaidInsights.Setup s) -> !s.isEnough())
				.thenComparing(s -> s.isEnough() ? -s.getGpPerHour() : 0.0));
		}
		if (setups.isEmpty())
		{
			addHistoryNote("No raids with your points or team yet. Each raid On Rate sees from now on adds to this.");
			return;
		}
		double max = 0;
		for (RaidInsights.Setup s : setups)
		{
			if (s.isEnough())
			{
				max = Math.max(max, gpTab ? s.getGpPerHour() : s.getChancePerHour());
			}
		}
		int timed = 0;
		boolean first = true;
		int shownRows = 0;
		for (RaidInsights.Setup s : setups)
		{
			timed += s.getTimed();
			// only the top few: the best setups first, then those still waiting for timed raids
			if (shownRows++ >= SETUPS_SHOWN)
			{
				continue;
			}
			String name = setupName(boss, s);
			if (!s.isEnough())
			{
				rowsPanel.add(cardRow(name, s.getTimed() + " timed, needs " + RaidInsights.MIN_TIMED,
					ColorScheme.LIGHT_GRAY_COLOR, setupTip(name, s, gp)));
				continue;
			}
			double value = gpTab ? s.getGpPerHour() : s.getChancePerHour();
			String shown = gpTab ? "~" + Rates.gp(value) + "/h" : String.format(java.util.Locale.ROOT, "%.1f%%/h", value * 100);
			String time = com.onrate.engine.tracking.ChatTimes.format((int) s.getAverageMillis());
			String detail = gpTab ? String.format(java.util.Locale.ROOT, "%.1f%%/h · %s avg", s.getChancePerHour() * 100, time)
				: String.format(java.util.Locale.ROOT, "%.1f%% a raid · %s avg", s.getChancePerRaid() * 100, time);
			// the top row says what it is best at, a fact about your own raids, not advice
			String tag = first && setups.size() > 1 ? (gpTab ? "MOST GP" : "FASTEST PURPLES") : null;
			rowsPanel.add(setupRow(name, tag, shown, detail, s.getTimed() + " timed", max > 0 ? value / max : 0,
				gpTab ? Theme.PROGRESS : Theme.RAID_PURPLE, setupTip(name, s, gp)));
			first = false;
		}
		int hidden = setups.size() - Math.min(setups.size(), SETUPS_SHOWN);
		addHistoryNote((hidden > 0 ? "Your top " + SETUPS_SHOWN + " of " + setups.size() + " setups. " : "")
			+ (gpTab ? "What your purples are worth an hour (GE prices, uniques only). "
			: "Your purple chance a raid divided by your raid time. ")
			+ "From " + timed + " timed raid" + (timed == 1 ? "" : "s") + "; raid time is the game's own, without banking. A row needs "
			+ RaidInsights.MIN_TIMED + " timed raids.");
	}

	/** Purple chance by setup lists this many setups, the best first. */
	static final int SETUPS_SHOWN = 5;

	/** One setup: its name (and tag) and value, the details under it, and a bar scaled to the best row. */
	private JPanel setupRow(String name, String tag, String value, String detail, String right, double fraction, Color barColor, String tip)
	{
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S)));
		if (tag != null)
		{
			// on its own line, so a long setup name never pushes it out of the narrow panel
			JLabel label = smallLabel(tag, Theme.TEXT);
			label.setAlignmentX(LEFT_ALIGNMENT);
			row.add(label);
		}
		JPanel top = new JPanel(new BorderLayout(Theme.S, 0));
		top.setOpaque(false);
		top.setAlignmentX(LEFT_ALIGNMENT);
		top.add(smallLabel(name, Color.WHITE), BorderLayout.CENTER);
		top.add(smallLabel(value, Color.WHITE), BorderLayout.EAST);
		row.add(top);
		JPanel bottom = new JPanel(new BorderLayout(Theme.S, 0));
		bottom.setOpaque(false);
		bottom.setAlignmentX(LEFT_ALIGNMENT);
		bottom.add(smallLabel(detail, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.CENTER);
		bottom.add(smallLabel(right, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		row.add(bottom);
		JPanel bar = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(Theme.TRACK_DARK);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(barColor);
				g.fillRect(0, 0, (int) Math.round(getWidth() * Math.max(0, Math.min(1, fraction))), getHeight());
			}
		};
		bar.setAlignmentX(LEFT_ALIGNMENT);
		bar.setPreferredSize(new Dimension(1, 4));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
		row.add(spacer(Theme.HAIR));
		row.add(bar);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		setTip(row, tip, null);
		return Card.row(row);
	}

	private static String setupTip(String name, RaidInsights.Setup s, boolean gp)
	{
		Tip tip = new Tip(name)
			.row("Purple chance a raid", String.format(java.util.Locale.ROOT, "%.1f%%", s.getChancePerRaid() * 100))
			.row("Raids", String.format(java.util.Locale.ROOT, "%,d", s.getRaids()))
			.row("Timed raids", String.format(java.util.Locale.ROOT, "%,d", s.getTimed()));
		if (s.getTimed() > 0)
		{
			tip.row("Average time", com.onrate.engine.tracking.ChatTimes.format((int) s.getAverageMillis()))
				.row("Purple chance an hour", String.format(java.util.Locale.ROOT, "%.1f%%", s.getChancePerHour() * 100));
			if (gp)
			{
				tip.row("GP value an hour", "~" + Rates.gp(s.getGpPerHour()));
			}
		}
		if (s.isEnough() && s.getChancePerHour() > 0)
		{
			tip.note(String.format(java.util.Locale.ROOT, "At this speed and these points, about 1 purple every %.0f hours of raiding.",
				1 / s.getChancePerHour()));
		}
		else
		{
			tip.note("Needs " + RaidInsights.MIN_TIMED + " timed raids before it shows an hour figure.");
		}
		return tip.wide();
	}

	private void renderBests(BossDefinition boss, RaidInsights insights)
	{
		addFoldHeading("PERSONAL BESTS", bestsOpen, "Your fastest raid and best points per mode and team size, from the raids On Rate saw",
			() ->
			{
				bestsOpen = !bestsOpen;
				rememberFolds();
			});
		if (!bestsOpen)
		{
			return;
		}
		String raid = boss.getRaid();
		String second = "tob".equals(raid) ? "No deaths" : "fractured".equals(raid) ? "Biggest share" : "Most points";
		String part = null;
		boolean any = false;
		for (RaidInsights.Bests b : insights.getBests())
		{
			if (b.getFastest() == null && b.getMostPoints() == null && b.getBiggestShare() == null && b.getDeathsKnown() == 0)
			{
				continue;
			}
			if (!b.getPart().equals(part))
			{
				part = b.getPart();
				if (boss.getParts().size() > 1)
				{
					JLabel heading = smallLabel(part.toUpperCase(java.util.Locale.ROOT), Theme.TEXT_MUTED);
					heading.setAlignmentX(LEFT_ALIGNMENT);
					heading.setBorder(new EmptyBorder(Theme.S, 0, Theme.HAIR, 0));
					rowsPanel.add(heading);
				}
				rowsPanel.add(bestsLine("Team", "Fastest", second, Theme.TEXT_MUTED, null, null));
			}
			any = true;
			String fastest = b.getFastest() == null ? "–" : com.onrate.engine.tracking.ChatTimes.format((int) b.getFastest().getValue());
			String other;
			RaidInsights.Best otherBest;
			if ("tob".equals(raid))
			{
				other = b.getDeathsKnown() == 0 ? "–" : b.getWithoutDeath() + " of " + b.getDeathsKnown();
				otherBest = null;
			}
			else if ("fractured".equals(raid))
			{
				otherBest = b.getBiggestShare();
				other = otherBest == null ? "–" : String.format(java.util.Locale.ROOT, "%.0f%%", otherBest.getValue() / 7);
			}
			else
			{
				otherBest = b.getMostPoints();
				other = otherBest == null ? "–" : formatPoints((int) otherBest.getValue());
			}
			rowsPanel.add(bestsLine(teamLabel(b.getTeam(), b.getPlayers()), fastest, other, Color.WHITE,
				bestTip(teamLabel(b.getTeam(), b.getPlayers()) + " · fastest " + fastest, b.getFastest(), b, raid),
				bestTip(teamLabel(b.getTeam(), b.getPlayers()) + " · " + second.toLowerCase(java.util.Locale.ROOT) + " " + other, otherBest, b, raid)));
		}
		if (!any)
		{
			addHistoryNote("No raids with a time or points yet.");
			return;
		}
		addHistoryNote("From the raids On Rate saw. Points are your own, not the team's. In History, the raid that set a best says PB.");
	}

	/** A line of the personal bests table: team, fastest and the second best, each with its own hover. */
	private JPanel bestsLine(String team, String fastest, String other, Color color, String fastestTip, String otherTip)
	{
		JPanel line = new JPanel(new GridLayout(1, 3, Theme.S, 0));
		line.setBackground(color == Color.WHITE ? ColorScheme.DARKER_GRAY_COLOR : Theme.PANEL);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S)));
		line.add(smallLabel(team, color == Color.WHITE ? ColorScheme.LIGHT_GRAY_COLOR : color));
		JLabel a = smallLabel(fastest, color);
		a.setHorizontalAlignment(SwingConstants.RIGHT);
		a.setToolTipText(fastestTip);
		line.add(a);
		JLabel b = smallLabel(other, color);
		b.setHorizontalAlignment(SwingConstants.RIGHT);
		b.setToolTipText(otherTip);
		line.add(b);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		return Card.row(line);
	}

	private static String bestTip(String title, RaidInsights.Best best, RaidInsights.Bests bests, String raid)
	{
		Tip tip = new Tip(title);
		if (best != null)
		{
			com.onrate.engine.tracking.RaidRecord r = best.getRaid();
			if (r.getKc() > 0)
			{
				tip.row("Raid", String.format(java.util.Locale.ROOT, "%,d", r.getKc()));
			}
			if (best.getSavedMillis() > 0)
			{
				tip.row("When", daysAgo(best.getSavedMillis(), System.currentTimeMillis()));
			}
			if (r.getPersonalPoints() > 0)
			{
				tip.row("Points", formatPoints(r.getPersonalPoints()));
			}
			if (r.getDurationMillis() > 0)
			{
				tip.row("Time", com.onrate.engine.tracking.ChatTimes.format(r.getDurationMillis()));
			}
		}
		if ("toa".equals(raid) && bests.getHighestLevel() != null)
		{
			tip.row("Highest raid level", String.valueOf((int) bests.getHighestLevel().getValue()));
		}
		return tip.row("Raids", String.format(java.util.Locale.ROOT, "%,d", bests.getRaids())).small();
	}

	private void renderPointsOverTime(BossDefinition boss, RaidInsights insights)
	{
		boolean withPoints = !"tob".equals(boss.getRaid()) && !"fractured".equals(boss.getRaid());
		addFoldHeading(withPoints ? "POINTS OVER TIME" : "PURPLE CHANCE OVER TIME", pointsOpen,
			"Your last " + RaidPointsGraph.SHOWN + " raids one by one, with the average of the last " + RaidPointsGraph.AVERAGE,
			() ->
			{
				pointsOpen = !pointsOpen;
				rememberFolds();
			});
		if (!pointsOpen)
		{
			return;
		}
		// every mode on one graph (Chambers normal and Challenge Mode, Tombs entry, normal and
		// expert...), raid by raid in the order they were done
		List<RaidInsights.Point> raids = new ArrayList<>();
		for (com.onrate.engine.data.BossPart p : boss.getParts())
		{
			raids.addAll(insights.getPoints(p.getName()));
		}
		raids.sort(java.util.Comparator.comparingLong(RaidInsights.Point::getSavedMillis));
		if (raids.isEmpty())
		{
			addHistoryNote("No raids On Rate saw yet.");
			return;
		}
		boolean chance = !withPoints || pointsChance;
		if (withPoints)
		{
			rowsPanel.add(switchRow(new String[]{"Points", "Purple chance"}, chance ? 1 : 0, i -> pointsChance = i == 1));
		}
		RaidPointsGraph graph = new RaidPointsGraph();
		graph.setPoints(raids, chance);
		if (graph.shown() == 0)
		{
			addHistoryNote(chance ? "No raids with a purple chance yet." : "No raids with your points yet.");
			return;
		}
		rowsPanel.add(graph);
		rowsPanel.add(spacer(Theme.HAIR));
		JLabel key = smallLabel("<html><font color='" + Theme.hex(new Color(150, 150, 150)) + "'>●</font> a raid  <font color='"
			+ Theme.hex(Theme.TEXT) + "'>—</font> last " + RaidPointsGraph.AVERAGE + "  <font color='" + Theme.hex(Theme.RAID_PURPLE)
			+ "'>◆</font> purple</html>", ColorScheme.LIGHT_GRAY_COLOR);
		key.setAlignmentX(LEFT_ALIGNMENT);
		rowsPanel.add(key);
		List<RaidInsights.Point> all = new ArrayList<>();
		for (RaidInsights.Point p : raids)
		{
			double v = chance ? p.getChance() : p.getPoints();
			if (!Double.isNaN(v) && v > 0)
			{
				all.add(p);
			}
		}
		if (all.size() >= 20)
		{
			rowsPanel.add(cardRow("Last 20 raids", averageText(all.subList(all.size() - 20, all.size()), chance), Color.WHITE, null));
			if (all.size() >= 40)
			{
				rowsPanel.add(cardRow("20 before that", averageText(all.subList(all.size() - 40, all.size() - 20), chance),
					ColorScheme.LIGHT_GRAY_COLOR, null));
			}
		}
	}

	private static String averageText(List<RaidInsights.Point> points, boolean chance)
	{
		double sum = 0;
		for (RaidInsights.Point p : points)
		{
			sum += chance ? p.getChance() : p.getPoints();
		}
		double avg = sum / points.size();
		return chance ? String.format(java.util.Locale.ROOT, "%.1f%% avg", avg * 100) : formatPoints((int) Math.round(avg)) + " avg";
	}

	/**
	 * The Doom of Mokhaiotl "Time" row under History, with each delve level's fastest, median and latest time on
	 * hover. Always on for players; the pixel parity test turns it off, because the original panel has no such row
	 * (TimeHoverTest covers it).
	 */
	private boolean delveTimes = true;

	/**
	 * A row under History with how long the kills took on average, when any kill has a time, with a hover: the average only when {@code seen} is negative, otherwise also the fastest timed,
	 * median and latest kill of the {@code seen} kills in History. {@code pbPointer} adds where a raid's fastest
	 * times per team size are.
	 */
	private void addTimeRow(String label, com.onrate.engine.tracking.TimeStats t, int seen, boolean pbPointer)
	{
		if (t == null)
		{
			return;
		}
		String average = com.onrate.engine.tracking.ChatTimes.format(t.getAverageMillis());
		rowsPanel.add(cardRow(label, average + " avg", Color.WHITE, timeTip(t, seen, pbPointer)));
	}

	/** The hover of {@link #addTimeRow}: see there. Package-private for the tests. */
	static String timeTip(com.onrate.engine.tracking.TimeStats t, int seen, boolean pbPointer)
	{
		String average = com.onrate.engine.tracking.ChatTimes.format(t.getAverageMillis());
		if (seen < 0)
		{
			Tip tip = new Tip("Average time")
				.row("Average", average)
				.row("Counted", String.format(java.util.Locale.ROOT, "%,d", t.getCount()));
			if (pbPointer)
			{
				tip.line("Fastest per team size: see Personal bests.");
			}
			return tip.note("The time the game's chat gave for each kill or raid. Kills without a time message aren't counted.").wide();
		}
		String timed = String.format(java.util.Locale.ROOT, "%,d of %,d", t.getCount(), Math.max(seen, t.getCount()));
		Tip tip;
		if (t.getCount() == 1)
		{
			// one time is not a spread: no fastest, median or latest
			tip = new Tip("Kill time in History").row("Time", average).row("Timed kills", timed);
		}
		else
		{
			tip = new Tip("Kill times in History")
				.row("Fastest timed", com.onrate.engine.tracking.ChatTimes.format(t.getBestMillis()))
				.row("Median", com.onrate.engine.tracking.ChatTimes.format(t.getMedianMillis()))
				.row("Average", average)
				.row("Latest", com.onrate.engine.tracking.ChatTimes.format(t.getLastMillis()))
				.row("Timed kills", timed);
		}
		return tip.note("The time the game's chat gave for each kill. Kills without a time message aren't counted. "
			+ "History keeps your last " + com.onrate.engine.tracking.Tracker.MAX_KILLS + " kills, so \"fastest timed\" is the quickest of "
			+ "those, not a personal best.").wide();
	}

	/** The Doom "Time" row: each delve level's own times on hover. Nothing when no delve has a time. */
	private void addDelveTimeRow(BossDefinition boss)
	{
		Tip tip = new Tip("Delve times in History").line("fastest \u00b7 median \u00b7 latest", ColorScheme.LIGHT_GRAY_COLOR);
		int timed = 0;
		for (int level = 1; level <= BossDefinition.DELVE_LEVELS; level++)
		{
			com.onrate.engine.tracking.TimeStats t = tracker.levelTimes(boss.getName(), level);
			if (t == null)
			{
				continue;
			}
			timed += t.getCount();
			tip.row("Level " + BossDefinition.delveLevelLabel(level) + " \u00b7 " + t.getCount(),
				com.onrate.engine.tracking.ChatTimes.format(t.getBestMillis()) + " \u00b7 "
					+ com.onrate.engine.tracking.ChatTimes.format(t.getMedianMillis()) + " \u00b7 "
					+ com.onrate.engine.tracking.ChatTimes.format(t.getLastMillis()));
		}
		if (timed == 0)
		{
			return;
		}
		tip.note("Each level's own times, from the game's chat. Delves without a time message aren't counted. History keeps your last "
			+ com.onrate.engine.tracking.Tracker.MAX_KILLS + " delves, so \"fastest timed\" is not a personal best. 8+ lumps every deeper delve together.");
		rowsPanel.add(cardRow("Time \u00b7 per level", String.format(java.util.Locale.ROOT, "%,d timed", timed), Color.WHITE, tip.wide()));
	}

	/**
	 * The link under History's rows: 10, then 50, then all, and "Show fewer" back to 10 once more than 10 are up.
	 * {@code available} counts the rows the list could show (uniques at a drops-only boss, else kills).
	 */
	private void addHistoryMoreLink(int available, int shown, String unit, boolean dropsOnly)
	{
		String what = dropsOnly ? "uniques" : unit + "s";
		boolean more = available > shown;
		int next = historyLimit == 10 ? 50 : 0;
		String text;
		if (more)
		{
			text = next == 50 && available > 50 ? "Show 50 (" + shown + " of " + available + " " + what + ")"
				: "Show all " + available + " " + what + " (" + shown + " shown)";
		}
		else
		{
			text = "Show fewer";
		}
		JLabel link = smallLabel("<html><u>" + Texts.html(text) + "</u></html>", Theme.TEXT_MUTED);
		link.setAlignmentX(LEFT_ALIGNMENT);
		link.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		brightenOnHover(link);
		link.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		Runnable action = () ->
		{
			historyLimit = more ? (next == 50 && available > 50 ? 50 : 0) : 10;
			refresh();
		};
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				action.run();
			}
		});
		Activatable.install(link, text, action);
		rowsPanel.add(link);
	}

	private JLabel addHistoryNote(String text)
	{
		JLabel note = smallLabel("<html><body style='width: " + Theme.TEXT_WIDTH + "px'>" + text + "</body></html>", ColorScheme.LIGHT_GRAY_COLOR);
		note.setAlignmentX(LEFT_ALIGNMENT);
		note.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		rowsPanel.add(note);
		return note;
	}

	/**
	 * The hover of a History line for a raid, team boss, delve or Royal Titans kill: what the
	 * line abbreviates, one row each.
	 */
	private static String historyKillTip(BossDefinition boss, com.onrate.engine.tracking.HistoryRow h, String unit, long now, String loot)
	{
		com.onrate.engine.tracking.KillEntry k = h.getKill();
		String title;
		if (boss.getWaves() > 0)
		{
			title = "Wave " + (k.getLevel() == null ? "?" : k.getLevel());
		}
		else if (boss.isDelve())
		{
			title = "Delve level " + (k.getLevel() == null ? "?" : BossDefinition.delveLevelLabel(k.getLevel()));
		}
		else
		{
			title = String.format(java.util.Locale.ROOT, "%s %,d", capital(unit), k.getPartKc() != null && boss.getRaid() != null ? k.getPartKc() : k.getKc());
		}
		Tip tip = new Tip(title);
		if (boss.getRaid() != null)
		{
			com.onrate.engine.tracking.RaidRecord r = h.getRaid();
			if (k.getPart() != null && !"Normal".equals(k.getPart()))
			{
				tip.row("Mode", k.getPart());
			}
			boolean archive = "fractured".equals(boss.getRaid());
			if (r == null)
			{
				tip.row(archive ? "Contribution" : "Points", "not recorded");
				if (archive)
				{
					tip.note("On Rate didn't track this raid, so it uses the Fractured Archive team size and team chance from On Rate's settings (under Raids and Raid purple chances).");
				}
			}
			else if (archive)
			{
				tip.row("Your contribution", r.getContribution() < 0 ? "unknown: even share assumed"
					: String.format(java.util.Locale.ROOT, "%.0f of 700 (estimated)", r.getContribution()));
				tip.row("Team", r.getTeamSize() > 0 ? String.valueOf(r.getTeamSize()) : "not counted: Fractured Archive team size used");
				if (r.getDeaths() > 0)
				{
					tip.row("Deaths", r.getDeaths() + " (-10% of your expected contribution each)");
				}
				if (!Double.isNaN(h.getPurpleChance()))
				{
					tip.row("Purple chance", String.format(java.util.Locale.ROOT, "%.2f%%", h.getPurpleChance() * 100));
				}
				tip.note(r.getContribution() < 0
					? "On Rate missed a fight of this raid, so it assumes an even share of the damage."
					: "Estimated from your share of the damage in each fight (Ocellus counts 300, the others 100). The game doesn't show it.");
			}
			else
			{
				if (r.getPersonalPoints() > 0)
				{
					tip.row("Your points", String.format(java.util.Locale.ROOT, "%,d", r.getPersonalPoints()));
				}
				if (!Double.isNaN(h.getPurpleChance()))
				{
					tip.row("Purple chance", String.format(java.util.Locale.ROOT, "%.1f%%", h.getPurpleChance() * 100));
				}
				tip.row("Team", String.valueOf(r.getTeamSize()));
				if (r.getDeaths() > 0)
				{
					tip.row("Deaths", String.valueOf(r.getDeaths()));
				}
			}
		}
		else if (boss.isDetectTeam())
		{
			tip.row("Team", k.getTeam() == null ? "unknown" : k.getTeam() == 1 ? "solo"
				: k.getTeam() + ", your share 1/" + k.getTeam());
		}
		else if ("Royal Titans".equals(boss.getName()))
		{
			tip.row("Titan looted", k.getTitan() == null ? "unknown" : k.getTitan());
		}
		tip.row("When", daysAgo(k.getTime(), now));
		if (k.getDurationMillis() != null)
		{
			tip.row("Time", com.onrate.engine.tracking.ChatTimes.format(k.getDurationMillis()));
		}
		tip.row("Uniques", loot.isEmpty() ? "none" : loot);
		return tip.wide();
	}

	/** A History line with loot: what happened on top, the loot in purple underneath. */
	private JPanel historyLootLine(String first, String middle, String loot, String tip)
	{
		return historyTwoLines(first, middle, ColorScheme.LIGHT_GRAY_COLOR, loot, Theme.RAID_PURPLE, tip);
	}

	/** A History line with two rows of text next to the kill count. */
	private JPanel historyTwoLines(String first, String top, Color topColor, String bottom, Color bottomColor, String tip)
	{
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S)));
		line.setAlignmentX(LEFT_ALIGNMENT);
		JLabel kc = smallLabel(first, Color.WHITE);
		kc.setPreferredSize(new Dimension(38, 14));
		kc.setVerticalAlignment(SwingConstants.TOP);
		line.add(kc, BorderLayout.WEST);
		JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
		text.setOpaque(false);
		text.add(smallLabel(top, topColor));
		text.add(smallLabel(bottom, bottomColor));
		line.add(text, BorderLayout.CENTER);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		setTip(line, tip, null);
		return Card.row(line);
	}

	/** One History line: kill count on the left, then what happened, then the loot or gap. */
	private JPanel historyLine(String first, String middle, String last, Color middleColor, Color lastColor, String tip)
	{
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S)));
		line.setAlignmentX(LEFT_ALIGNMENT);
		JLabel kc = smallLabel(first, Color.WHITE);
		kc.setPreferredSize(new Dimension(38, 14));
		line.add(kc, BorderLayout.WEST);
		if (middle != null)
		{
			line.add(smallLabel(middle, middleColor), BorderLayout.CENTER);
		}
		if (!last.isEmpty())
		{
			// a kill with no drop has nothing to put on the right
			line.add(smallLabel(last, lastColor), middle == null ? BorderLayout.CENTER : BorderLayout.EAST);
		}
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		setTip(line, tip, null);
		return Card.row(line);
	}

	/** Bosses | Items tabs above the account list. */
	private void renderAccountTabs(boolean withItems)
	{
		JPanel tabs = new JPanel(new GridLayout(1, withItems ? 3 : 2, 2, 0));
		tabs.setOpaque(false);
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		tabs.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(Theme.M, 0, Theme.S, 0),
			BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.OUTLINE)));
		tabs.add(accountTab("Bosses", TAB_BOSSES));
		if (withItems)
		{
			tabs.add(accountTab("Items", TAB_ITEMS));
		}
		tabs.add(accountTab("Recent", TAB_RECENT));
		tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		topItemsPanel.add(tabs);
	}

	private JLabel accountTab(String text, int tab)
	{
		boolean selected = accountTab == tab;
		JLabel label = smallLabel(text, selected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? Theme.ACCENT : ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(Theme.HAIR, Theme.S, Theme.S, Theme.S)));
		label.setToolTipText(tab == TAB_ITEMS ? "Your driest and luckiest items across every boss, your pets, single drops and how long the items you don't have take (the Time page)"
			: tab == TAB_RECENT ? "Your latest uniques across every boss" : null);
		if (!selected)
		{
			label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					accountTab = tab;
					rememberAccountPage();
					refresh();
				}
			});
		}
		return Activatable.install(label, text + " tab" + (selected ? ", selected" : ""), () ->
		{
			accountTab = tab;
			rememberAccountPage();
			refresh();
		});
	}

	/** How many items each list of the Items page shows before "show all" (as in the Single drops page). */
	private boolean driestAll;
	private boolean luckiestAll;

	/** The Items tab: items (driest and luckiest), pets, or single drops. */
	private void renderItemsTab(AccountStats account, ItemView view)
	{
		// a segmented control, visibly a level below the Bosses / Items / Recent tabs
		boolean itemsPage = topMode == TOP_DRIEST || topMode == TOP_LUCKIEST;
		JPanel switcher = new JPanel(new GridLayout(2, 2, Theme.HAIR, Theme.HAIR));
		switcher.setBackground(Theme.SURFACE);
		switcher.setAlignmentX(LEFT_ALIGNMENT);
		switcher.setBorder(new EmptyBorder(Theme.HAIR, Theme.HAIR, Theme.HAIR, Theme.HAIR));
		switcher.add(topSwitch("Items", itemsPage, TOP_DRIEST));
		switcher.add(topSwitch("Pets", topMode == TOP_PETS, TOP_PETS));
		switcher.add(topSwitch("Single drops", topMode == TOP_DROPS, TOP_DROPS));
		switcher.add(topSwitch("Time", topMode == TOP_TIME, TOP_TIME));
		switcher.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
		rowsPanel.add(spacer(Theme.S));
		rowsPanel.add(switcher);
		rowsPanel.add(spacer(Theme.S));

		if (topMode == TOP_TIME)
		{
			renderTimes();
		}
		else if (topMode == TOP_DROPS)
		{
			renderDropLists(account);
		}
		else if (topMode == TOP_PETS)
		{
			renderPets(account, view);
		}
		else
		{
			renderItemLists(account, view);
		}
	}

	/** Your real Slayer level, or -1 when it isn't known (not logged in): every Slayer boss is hidden then. */
	private java.util.function.IntSupplier slayerLevel = () -> -1;

	/** Call once after the panel is made: where to read your Slayer level from. */
	public void setSlayerLevel(java.util.function.IntSupplier level)
	{
		slayerLevel = level == null ? () -> -1 : level;
	}

	/**
	 * False for a Slayer boss that needs more Slayer than you have: its items are always left off the
	 * Time page. Until your level is known (not logged in yet) it counts as 0, so no Slayer boss shows.
	 */
	private boolean canFight(String bossName)
	{
		int level = Math.max(0, slayerLevel.getAsInt());
		BossDefinition boss = tracker.getCatalog().byName(bossName);
		return boss == null || boss.getSlayerLevel() <= level;
	}

	/** How many Slayer bosses are hidden on the Time page for lack of Slayer. */
	private int bossesAboveYourSlayer()
	{
		int level = Math.max(0, slayerLevel.getAsInt());
		int hidden = 0;
		for (BossDefinition boss : tracker.getCatalog().getBosses())
		{
			hidden += boss.getSlayerLevel() > level ? 1 : 0;
		}
		return hidden;
	}

	/** The Time page: show the whole list instead of the first {@link #DROPS_SHOWN}. */
	private boolean timesAll;

	/** The lists of the Time page, chosen from its dropdown. */
	private enum TimeView
	{
		ITEMS("Items"), CHART("Ladlor's chart"), PETS("Pets"), RAIDS("Raids");

		private final String label;

		TimeView(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private TimeView timeView = TimeView.ITEMS;

	/**
	 * The Time page: how long, on average, the items you don't have take to get. A dropdown picks
	 * the list: all items quickest first, the
	 * Ladlor chart's items in chart order, pets, or raid items. The time of a kill is your own
	 * average once you have enough timed kills, else the OSRS Wiki's rate; the chance is yours
	 * (raids from your points, Doom from the delve levels you did). Each row has a chip saying which
	 * it used and a thin bar showing how long the grind is.
	 */
	private void renderTimes()
	{
		JPanel viewRow = new JPanel(new BorderLayout(4, 0));
		viewRow.setOpaque(false);
		viewRow.setAlignmentX(LEFT_ALIGNMENT);
		JLabel viewLabel = new JLabel("Show:");
		viewLabel.setFont(FontManager.getRunescapeSmallFont());
		viewLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		viewRow.add(viewLabel, BorderLayout.WEST);
		JComboBox<TimeView> viewSelect = new JComboBox<>(TimeView.values());
		viewSelect.setFont(FontManager.getRunescapeSmallFont());
		viewSelect.getAccessibleContext().setAccessibleName("Show");
		viewSelect.setSelectedItem(timeView);
		viewSelect.addActionListener(e ->
		{
			timeView = (TimeView) viewSelect.getSelectedItem();
			rememberAccountPage();
			refresh();
		});
		viewRow.add(viewSelect, BorderLayout.CENTER);
		viewRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		rowsPanel.add(viewRow);

		String what;
		switch (timeView)
		{
			case CHART:
				what = "In the chart's order. Each time covers all the drops the item needs.";
				break;
			case PETS:
				what = "Pets you don't have, quickest first.";
				break;
			case RAIDS:
				what = "Raid items, quickest first. Your points set your chance.";
				break;
			default:
				what = "Quickest first.";
				break;
		}
		JLabel note = smallLabel("<html><body style='width: " + noteWidth(170) + "px'>" + what + "</body></html>", Theme.TEXT_MUTED);
		note.setAlignmentX(LEFT_ALIGNMENT);
		note.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		int hiddenBosses = bossesAboveYourSlayer();
		if (hiddenBosses > 0)
		{
			what += slayerLevel.getAsInt() >= 0 ? " Bosses above your Slayer level (" + slayerLevel.getAsInt() + ") are hidden."
				: " Slayer bosses are hidden until On Rate knows your Slayer level (log in).";
			note.setText("<html><body style='width: " + noteWidth(170) + "px'>" + what + "</body></html>");
		}
		note.setToolTipText(new Tip("How to read this")
			.row("yours", "Your own timed kills")
			.row("mixed", "A bit of both")
			.row("~", "Based on the wiki's kill speed")
			.row("Thin bar", "How long: each step is ten times more, full is " + (int) EffortBar.FULL_HOURS + " h")
			.note("The wiki's kill speeds assume strong gear and stats, so most players are slower. Your own average takes over after "
				+ com.onrate.engine.stats.ItemTimes.MIN_TIMED_RAIDS + " timed raids or " + com.onrate.engine.stats.ItemTimes.MIN_TIMED_KILLS
				+ " timed kills. Items you have are left out.")
			.wide());
		rowsPanel.add(note);

		if (timeView == TimeView.CHART)
		{
			renderChartTimes();
			return;
		}
		List<com.onrate.engine.stats.ItemTime> todo = new ArrayList<>();
		for (com.onrate.engine.stats.ItemTime t : com.onrate.engine.stats.ItemTimes.compute(tracker,
			com.onrate.engine.data.KillTimes.bundled(), raidSettings.get(), this::canFight))
		{
			boolean inView = timeView == TimeView.PETS ? t.isPet() : !t.isPet() && (timeView != TimeView.RAIDS || t.isRaid());
			if (inView && !t.isHave())
			{
				todo.add(t);
			}
		}
		todo.sort(java.util.Comparator.comparingDouble(com.onrate.engine.stats.ItemTime::getSecondsPerDrop));
		if (todo.isEmpty())
		{
			addTimeNone("Nothing to show. Times appear for bosses with a known pace, and for every boss once you have "
				+ "enough timed kills; items you have are left out.");
			return;
		}
		int shown = timesAll ? todo.size() : Math.min(DROPS_SHOWN, todo.size());
		dropListTitle("QUICKEST", shown, todo.size(), () -> timesAll = !timesAll,
			"The average time to get one of each, quickest first. Fight times from the game don't include banking or walking, "
				+ "so a time from your own kills is a little shorter than your real time.");
		for (com.onrate.engine.stats.ItemTime t : todo.subList(0, shown))
		{
			rowsPanel.add(spacer(Theme.HAIR));
			rowsPanel.add(timeRow(t));
		}
	}

	private void addTimeNone(String text)
	{
		JLabel none = smallLabel("<html><body style='width: " + noteWidth(170) + "px'>" + text + "</body></html>", ColorScheme.LIGHT_GRAY_COLOR);
		none.setAlignmentX(LEFT_ALIGNMENT);
		none.setBorder(new EmptyBorder(Theme.M, 0, 0, 0));
		rowsPanel.add(none);
	}

	/** The Ladlor chart's items you are still missing something of, in chart order. */
	private void renderChartTimes()
	{
		List<com.onrate.engine.stats.ChartTime> chart = com.onrate.engine.stats.ChartTimes.compute(tracker,
			com.onrate.engine.data.KillTimes.bundled(), raidSettings.get(), com.onrate.engine.data.LadlorChart.bundled(), this::canFight);
		if (chart.isEmpty())
		{
			addTimeNone("You have everything on the chart that On Rate can count.");
			return;
		}
		com.onrate.engine.stats.ChartTimes.Total total = com.onrate.engine.stats.ChartTimes.total(chart);
		JLabel all = smallLabel("<html><body style='width: " + noteWidth(170) + "px'>" + chart.size() + " items left, about <font color='#ffffff'>"
			+ com.onrate.engine.stats.ItemTime.formatLong(total.getSeconds()) + "</font> for all of them"
			+ (total.getWithoutTime() > 0 ? " (without the " + total.getWithoutTime() + " that have no time yet)" : "")
			+ "</body></html>", Theme.TEXT_MUTED);
		all.setAlignmentX(LEFT_ALIGNMENT);
		all.setToolTipText(new Tip("All of it")
			.line("Drops at one boss come on the same kills, so this is less than the times below added up.", Theme.TEXT_MUTED)
			.wide());
		rowsPanel.add(all);
		int shown = timesAll ? chart.size() : Math.min(DROPS_SHOWN, chart.size());
		dropListTitle("IN CHART ORDER", shown, chart.size(), () -> timesAll = !timesAll,
			"The chart's items you don't have yet, in the chart's order. Each time is for all the drops the item needs; "
				+ "drops at different bosses add up, drops at one boss come on the same kills.");
		for (com.onrate.engine.stats.ChartTime t : chart.subList(0, shown))
		{
			rowsPanel.add(spacer(Theme.HAIR));
			rowsPanel.add(chartRow(t));
		}
	}

	/** An item, its boss and the average time to get it, with where the time comes from and a bar for how long. */
	private JPanel timeRow(com.onrate.engine.stats.ItemTime t)
	{
		String time = com.onrate.engine.stats.ItemTime.formatLong(t.getSecondsPerDrop());
		Tip tip = new Tip(t.getLabel()).line(capital(t.getBoss()), Theme.TEXT_MUTED)
			.row("Average time", (t.isEstimated() ? "~" : "") + time)
			.row("Time based on", sourceName(t.getSourceTag()))
			.row("Per " + t.getUnit(), com.onrate.engine.stats.ItemTime.formatLong(t.getSecondsPerKill()))
			.row("Chance per " + t.getUnit(), t.getChancePerKill() > 0
				? String.format(java.util.Locale.ROOT, "1 in %,.0f", 1 / t.getChancePerKill()) : "-");
		if (t.isLowConfidence())
		{
			tip.line("The wiki figure rests on a thin source.", Theme.TEXT_MUTED);
		}
		if (t.getHint() != null)
		{
			tip.line(t.getHint(), Theme.TEXT_MUTED);
		}
		// only a time that is (partly) the wiki's has guides: they are where that rate comes from, so a link is offered
		com.onrate.engine.data.KillTimes web = com.onrate.engine.data.KillTimes.bundled();
		List<String> guideTitles = new ArrayList<>();
		for (String title : t.guideTitles())
		{
			if (web.guideUrl(title) != null)
			{
				guideTitles.add(title);
			}
		}
		if (!guideTitles.isEmpty())
		{
			tip.row(guideTitles.size() > 1 ? "Wiki guides" : "Wiki guide", String.join(", ", guideTitles))
				.line("Right-click to open it on the wiki. It is where the rate comes from, not a pace to reach.", Theme.TEXT_MUTED);
		}
		tip.note(Texts.timeBasis(t.getBasis()));
		JPanel row = timeRowPanel(t.getIconItem(), t.getLabel(), t.getBoss(), time, t.isEstimated(), t.getSecondsPerDrop(),
			t.getSourceTag(), tip.wide(), () -> showBoss(t.getBoss()), !guideTitles.isEmpty());
		if (!guideTitles.isEmpty())
		{
			// one entry per guide; the page opens in the browser only when it is picked
			JPopupMenu menu = new JPopupMenu();
			for (String title : guideTitles)
			{
				String url = web.guideUrl(title);
				JMenuItem open = new JMenuItem(guideTitles.size() > 1 ? "Open the wiki guide: " + title : "Open the wiki guide");
				open.setToolTipText(url);
				open.addActionListener(e -> net.runelite.client.util.LinkBrowser.browse(url));
				menu.add(open);
			}
			addPopup(row, menu);
		}
		return row;
	}

	/** A chart item: its step and what is still missing, and the average time for all of it. */
	private JPanel chartRow(com.onrate.engine.stats.ChartTime t)
	{
		String time = t.hasTime() ? com.onrate.engine.stats.ItemTime.formatLong(t.getSeconds()) : "-";
		List<com.onrate.engine.stats.ChartTime.Part> missing = new ArrayList<>();
		for (com.onrate.engine.stats.ChartTime.Part part : t.getParts())
		{
			if (!part.isHave())
			{
				missing.add(part);
			}
		}
		// where to farm it: "Step 29 · Callisto and Artio", "Step 40 · Yama x3", "Step 99 · 2 bosses"
		java.util.Map<String, Integer> perBoss = new java.util.LinkedHashMap<>();
		for (com.onrate.engine.stats.ChartTime.Part part : missing)
		{
			perBoss.merge(part.getBoss(), 1, Integer::sum);
		}
		String what;
		if (perBoss.size() == 1)
		{
			String boss = perBoss.keySet().iterator().next();
			int n = perBoss.get(boss);
			what = n > 1 ? boss + " x" + n : boss;
		}
		else
		{
			what = perBoss.size() + " bosses";
		}
		Tip tip = new Tip(t.getName()).line("Ladlor's chart, step " + t.getStep(), Theme.TEXT_MUTED)
			.row("Average time", t.hasTime() ? (t.isEstimated() ? "~" : "") + time : "no estimate yet");
		if (t.hasTime())
		{
			tip.row("Time based on", sourceName(t.getSourceTag()));
		}
		for (com.onrate.engine.stats.ChartTime.Part part : t.getParts())
		{
			tip.row(part.getLabel() + " (" + part.getBoss() + ")", part.isHave() ? "you have it"
				: Double.isNaN(part.getSeconds()) ? "no estimate" : com.onrate.engine.stats.ItemTime.formatLong(part.getSeconds()));
		}
		if (t.getRoute() != null)
		{
			tip.line(t.getRoute(), Theme.TEXT_MUTED);
		}
		tip.note(t.getNote() != null ? t.getNote()
			: "Each part's time is the wiki's kill speed or your own times, as its row on the Items list says.");
		final String open = missing.isEmpty() ? null : missing.get(0).getBoss();
		return timeRowPanel(t.getIcon(), t.getName(), "Step " + t.getStep() + " \u00b7 " + what, time, t.isEstimated(),
			t.hasTime() ? t.getSeconds() : Double.NaN, t.hasTime() ? t.getSourceTag() : "no data", tip.wide(),
			open == null ? null : () -> showBoss(open));
	}

	/** The long name of a source tag, for a hover. */
	private static String sourceName(String tag)
	{
		switch (tag)
		{
			case "your times":
				return "your own timed kills";
			case "wiki + yours":
				return "the wiki's kill speed and your own times";
			default:
				return "the OSRS Wiki's kill speed";
		}
	}

	/** A small neutral chip for where a time comes from: it says it in words, the colour only backs it up. */
	private static Chip sourceChip(String tag)
	{
		String text;
		Color color;
		String tip;
		switch (tag)
		{
			case "your times":
				text = "yours";
				color = Theme.PROGRESS;
				tip = "The average of your own timed kills. It is fight time only: banking and walking aren't in it.";
				break;
			case "wiki + yours":
				text = "mixed";
				color = Theme.SURFACE_SELECTED;
				tip = "Part of this uses your own timed kills and part the OSRS Wiki's kill speed.";
				break;
			case "no data":
				text = "no data";
				color = Theme.SURFACE_SELECTED;
				tip = "Some part has no time yet: the wiki has no kill speed for its boss and you have no timed kills.";
				break;
			default:
				text = "wiki";
				color = Theme.OUTLINE;
				tip = "The OSRS Wiki's kill speed (it includes banking and assumes strong gear and stats, so most players are slower). "
					+ "Your own average takes over after " + com.onrate.engine.stats.ItemTimes.MIN_TIMED_RAIDS + " timed raids or "
					+ com.onrate.engine.stats.ItemTimes.MIN_TIMED_KILLS + " timed kills.";
				break;
		}
		Chip chip = new Chip(text, color, "no data".equals(text) ? 46 : 38);
		chip.setToolTipText(Theme.wrap(tip, Theme.TIP_SHORT));
		return chip;
	}

	/**
	 * One row of the Time page: picture, name over detail, the time over a chip saying where it comes
	 * from, and a thin bar under the row for how long the grind is.
	 */
	private JPanel timeRowPanel(String iconItem, String name, String sub, String time, boolean estimated, double seconds,
		String source, String tip, Runnable onClick)
	{
		return timeRowPanel(iconItem, name, sub, time, estimated, seconds, source, tip, onClick, false);
	}

	/** {@link #timeRowPanel(String, String, String, String, boolean, double, String, String, Runnable)}; {@code leftOnly}: a right click opens a menu, not the click action. */
	private JPanel timeRowPanel(String iconItem, String name, String sub, String time, boolean estimated, double seconds,
		String source, String tip, Runnable onClick, boolean leftOnly)
	{
		JPanel content = new JPanel(new BorderLayout(4, 0));
		content.setOpaque(false);
		content.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		// the same sizes as the Items and Pets pages: their picture size and row font for the name, the rest small
		content.add(itemIcon(iconItem, name, SMALL_ICON), BorderLayout.WEST);
		JPanel names = new JPanel();
		names.setOpaque(false);
		names.setLayout(new javax.swing.BoxLayout(names, javax.swing.BoxLayout.Y_AXIS));
		JLabel item = rowLabel(name, Color.WHITE);
		item.setAlignmentX(LEFT_ALIGNMENT);
		JLabel detail = smallLabel(sub, Theme.TEXT_MUTED);
		detail.setAlignmentX(LEFT_ALIGNMENT);
		names.add(item);
		names.add(detail);
		content.add(names, BorderLayout.CENTER);
		JPanel right = new JPanel();
		right.setOpaque(false);
		right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.Y_AXIS));
		JLabel timeLabel = boldLabel((estimated ? "~" : "") + time);
		timeLabel.setForeground(estimated ? ColorScheme.LIGHT_GRAY_COLOR : Color.WHITE);
		timeLabel.setAlignmentX(RIGHT_ALIGNMENT);
		right.add(timeLabel);
		// a time from the wiki's rate has no chip (its "~" says so); only your own or mixed times, or no data, are labelled
		boolean wikiRate = !"your times".equals(source) && !"wiki + yours".equals(source) && !"no data".equals(source);
		Chip chip = wikiRate ? null : sourceChip(source);
		if (chip != null)
		{
			chip.setAlignmentX(RIGHT_ALIGNMENT);
			right.add(chip);
		}
		content.add(right, BorderLayout.EAST);
		// the card's outline, the row's border, the picture and the gaps, and the time beside the name
		item.setText(rowName(item, name, 2 + 2 * Theme.S + iconWidth(SMALL_ICON) + 8 + right.getPreferredSize().width));

		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(content, BorderLayout.CENTER);
		panel.add(new EffortBar(seconds), BorderLayout.SOUTH);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		setTip(panel, tip, onClick, leftOnly);
		// after the row's own hover, which would otherwise reach the chip too
		if (chip != null)
		{
			chip.setToolTipText(sourceChip(source).getToolTipText());
		}
		return Card.row(panel);
	}

	/** The Items page: your driest and your luckiest items, a few of each with "show all". */
	private void renderItemLists(AccountStats account, ItemView view)
	{
		List<com.onrate.engine.stats.ItemHighlight> driest = topItems(account, TOP_DRIEST, Integer.MAX_VALUE);
		List<com.onrate.engine.stats.ItemHighlight> luckiest = topItems(account, TOP_LUCKIEST, Integer.MAX_VALUE);
		if (driest.isEmpty() && luckiest.isEmpty())
		{
			JLabel none = smallLabel("Nothing dry or lucky yet.", ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(none);
			return;
		}
		JLabel key = smallLabel("got / expected: expected is an average", Theme.TEXT_MUTED);
		key.setAlignmentX(LEFT_ALIGNMENT);
		key.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		key.setToolTipText(new Tip("Got / expected")
			.line("Expected is the average a player would have by now at your kill count. It is not a number you were owed.")
			.line("So 0 / 2.60 means you have none where an average player would have about 2.6.", Theme.TEXT_MUTED)
			.note("Each kill is its own roll: being behind the average doesn't make the next drop more likely.")
			.wide());
		rowsPanel.add(key);
		if (view == ItemView.GAUGE)
		{
			LuckGauge gauge = new LuckGauge();
			gauge.setLuck(account.getTotal().getLuck());
			rowsPanel.add(gauge);
		}
		if (!driest.isEmpty())
		{
			int shown = driestAll ? driest.size() : Math.min(DROPS_SHOWN, driest.size());
			dropListTitle("DRIEST ITEMS", shown, driest.size(), () -> driestAll = !driestAll,
				"The items you are furthest behind on, driest first. Only items with at least 1 expected.");
			addItemList(driest.subList(0, shown), view);
		}
		if (!luckiest.isEmpty())
		{
			int shown = luckiestAll ? luckiest.size() : Math.min(DROPS_SHOWN, luckiest.size());
			dropListTitle("LUCKIEST ITEMS", shown, luckiest.size(), () -> luckiestAll = !luckiestAll,
				"The items you are furthest ahead on, luckiest first. Only items you have.");
			addItemList(luckiest.subList(0, shown), view);
		}
	}

	private void addItemList(List<com.onrate.engine.stats.ItemHighlight> list, ItemView view)
	{
		if (view == ItemView.COMPACT)
		{
			rowsPanel.add(compactHeader("Item"));
		}
		JPanel[] grid = {null};
		for (com.onrate.engine.stats.ItemHighlight h : list)
		{
			addItemRow(h, view, grid, false);
		}
	}

	/** The Pets page: the luckiest pets you have, then the driest you don't. */
	private void renderPets(AccountStats account, ItemView view)
	{
		List<com.onrate.engine.stats.ItemHighlight> have = petList(account, true);
		List<com.onrate.engine.stats.ItemHighlight> dry = petList(account, false);
		if (have.isEmpty() && dry.isEmpty())
		{
			JLabel none = smallLabel("No pets at the bosses you've killed.", ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(none);
			return;
		}
		JLabel count = smallLabel(String.format(java.util.Locale.ROOT, "You have %d of the %d pets at your bosses", have.size(),
			have.size() + dry.size()), ColorScheme.LIGHT_GRAY_COLOR);
		count.setAlignmentX(LEFT_ALIGNMENT);
		rowsPanel.add(count);
		addPetGroup(true, have, petsHaveAll, () -> petsHaveAll = !petsHaveAll, view);
		addPetGroup(false, dry, petsDryAll, () -> petsDryAll = !petsDryAll, view);
	}

	/** One group of the Pets page: the first {@link #TOP_PETS_SHOWN}, or all of them after "show all". */
	private void addPetGroup(boolean have, List<com.onrate.engine.stats.ItemHighlight> list, boolean all, Runnable toggle, ItemView view)
	{
		if (list.isEmpty())
		{
			return;
		}
		int shown = all && cappedListNotes ? list.size() : Math.min(TOP_PETS_SHOWN, list.size());
		String heading = have ? "LUCKIEST PETS YOU HAVE" : "DRIEST PETS YOU DON'T HAVE";
		String help = have ? "Pets you have, luckiest first: the ones you got earliest compared with their drop rates"
			: "The pets you'd most likely have by now, but don't";
		if (cappedListNotes && list.size() > TOP_PETS_SHOWN)
		{
			dropListTitle(heading, shown, list.size(), TOP_PETS_SHOWN, toggle, help);
		}
		else
		{
			JLabel title = smallLabel(heading, Theme.TEXT_MUTED);
			title.setAlignmentX(LEFT_ALIGNMENT);
			title.setBorder(new EmptyBorder(Theme.M, 0, 0, 0));
			title.setToolTipText(help);
			rowsPanel.add(title);
		}
		if (view == ItemView.COMPACT)
		{
			rowsPanel.add(compactHeader("Pet"));
		}
		JPanel[] grid = {null};
		for (com.onrate.engine.stats.ItemHighlight h : list.subList(0, shown))
		{
			addItemRow(h, view, grid, true);
		}
	}

	/** One row of the Items tab, in the chosen view. {@code grid} holds the open ring grid, if any. */
	private void addItemRow(com.onrate.engine.stats.ItemHighlight h, ItemView view, JPanel[] grid, boolean pets)
	{
		viewApplies = true;
		RowStats r = h.getRow();
		String where = h.getBoss() == null ? "shared by several bosses" : h.getBoss();
		double luck = r.getLuck();
		// the same rows as an item's hover on its boss page
		Tip itemTip = new Tip(r.getLabel()).line(capital(where), Theme.TEXT_MUTED);
		if (!Double.isNaN(luck))
		{
			itemTip.row("Your result", "<b>" + Verdict.labelOf(luck) + "</b>", Palette.onRow(luckColor(luck).brighter()))
				.line(capital(sharesLine(r.getShares(), r.getReceived())), Theme.TEXT_MUTED);
		}
		if (h.getBoss() != null && r.getIconItem() != null)
		{
			List<com.onrate.engine.tracking.RecentDrop> drops = tracker.dropsOf(h.getBoss(), r.getIconItem());
			BossDefinition dropBoss = tracker.getCatalog().byName(h.getBoss());
			if (!drops.isEmpty())
			{
				itemTip.line("How lucky you were when you got it", Theme.TEXT_MUTED);
				for (int i = 0; i < Math.min(3, drops.size()); i++)
				{
					itemTip.row(killText(dropBoss, drops.get(i).getKill()), dropLuck(drops.get(i).getDrop()));
				}
				if (drops.size() > 3)
				{
					itemTip.line("+ " + (drops.size() - 3) + " more", Theme.TEXT_MUTED);
				}
			}
		}
		itemTip.row("Got / expected", String.format(java.util.Locale.ROOT, "%d / %s", r.getReceived(), formatExpected(r.getExpected())));
		if (h.isPet() && !r.isComplete())
		{
			itemTip.row("Chance of one by now", formatPercent(r.getChanceAtLeastOne()));
		}
		itemTip.row("Without one", rateMultiple(r));
		addTimeRows(itemTip, timeOf(h.getBoss(), r.getIconItem()));
		String tip = itemTip.wide();
		Runnable open = h.getBoss() == null ? null : () -> showBoss(h.getBoss());
		if (view == ItemView.COMPACT)
		{
			rowsPanel.add(compactRow(r, tip, open));
		}
		else if (view == ItemView.RINGS)
		{
			if (grid[0] == null)
			{
				grid[0] = new JPanel(new GridLayout(0, 3, 4, 8));
				grid[0].setOpaque(false);
				grid[0].setAlignmentX(LEFT_ALIGNMENT);
				rowsPanel.add(Card.gridRow(grid[0]));
			}
			grid[0].add(itemRing(r, tip, open));
			// the grid grows a cell at a time, so keep its height in step
			grid[0].setMaximumSize(new Dimension(Integer.MAX_VALUE, grid[0].getPreferredSize().height));
		}
		else if (view == ItemView.GAUGE)
		{
			rowsPanel.add(spacer(Theme.HAIR));
			rowsPanel.add(stripRow(r.getIconItem(), r.getShownLabel(), false, r.isComplete() ? 1.0 : r.getLuck(), tip, open));
		}
		else if (view == ItemView.DELVE_BARS)
		{
			rowsPanel.add(spacer(Theme.HAIR));
			rowsPanel.add(buildRow(r, tip, open));
		}
		else
		{
			rowsPanel.add(spacer(Theme.HAIR));
			// the verdict stays the main reading; on the Items page "got / expected" sits beside the dot
			rowsPanel.add(luckRow(r, tip, open, h.isPet() && !r.isComplete()
				? String.format(java.util.Locale.ROOT, "%s \u00b7 %s", Verdict.labelOf(r.getLuck()), formatPercent(r.getChanceAtLeastOne())) : null,
				null, pets ? null : gotOfExpected(r)));
		}
	}

	/**
	 * Boss page: the shared items this boss drops, judged on every boss that drops them
	 * together (the log has one total). Bosses whose kills aren't known yet count as 0.
	 */
	private void renderBossPools(BossStats stats, ItemView view)
	{
		List<com.onrate.engine.stats.SharedPool> pools = tracker.sharedPoolsFor(stats.getBoss().getName(), raidSettings.get());
		if (pools.isEmpty())
		{
			return;
		}
		rowsPanel.add(spacer(Theme.M));
		JLabel title = smallLabel("SHARED WITH OTHER BOSSES", Theme.TEXT_MUTED);
		title.setAlignmentX(LEFT_ALIGNMENT);
		boolean anyMenu = false;
		for (com.onrate.engine.stats.SharedPool pool : pools)
		{
			// the right-click hint only when at least one row below has the menu
			anyMenu |= pool.isLogKnown() && offersSplitMenu(pool);
		}
		title.setToolTipText("<html><body style='width: " + Theme.TIP_WIDE + "px'>These drop from other bosses too, and the collection log has "
			+ "one total for all of them. So each is judged on all those bosses' kills together, and shows the same numbers on "
			+ "every boss that drops it. Hover one to see which bosses."
			+ (anyMenu ? "<br><br>Know where yours came from? Right-click it to move it to each boss separately." : "")
			+ "</body></html>");
		rowsPanel.add(title);
		String page = stats.getBoss().getClogPages().isEmpty() ? stats.getBoss().getName() : stats.getBoss().getClogPages().get(0);
		boolean headerAdded = false;
		List<String> unknown = new ArrayList<>();
		List<String> missingLines = new ArrayList<>();
		for (com.onrate.engine.stats.SharedPool pool : pools)
		{
			if (!pool.isLogKnown())
			{
				// how many you have is unknown until a log page showing it is opened
				unknown.add(pool.getLabel());
				continue;
			}
			RowStats row = pool.toRow();
			boolean splitMenu = offersSplitMenu(pool);
			viewApplies = true;
			String tip = poolTooltip(pool, row, splitMenu);
			if (view == ItemView.COMPACT)
			{
				if (!headerAdded)
				{
					// column titles only when there's a row under them
					rowsPanel.add(compactHeader("Item"));
					headerAdded = true;
				}
				rowsPanel.add(withSplitMenu(compactRow(row, tip, null), pool));
			}
			else
			{
				rowsPanel.add(spacer(Theme.HAIR));
				rowsPanel.add(withSplitMenu(targetRow(row, tip), pool));
			}
			if (pool.isSplitNeedsAnswer() && splitMenu)
			{
				missingLines.add(String.format(java.util.Locale.ROOT, "Your collection log has %s %s On Rate didn't see drop, so it's counted "
					+ "with the other bosses again. Right-click it to say where %s came from.",
					pool.getOlderCopies() == 1 ? "a" : String.valueOf(pool.getOlderCopies()),
					pool.getOlderCopies() == 1 ? pool.getLabel() : pool.getLabel() + " copies",
					pool.getOlderCopies() == 1 ? "it" : "they"));
			}
			List<String> missing = new ArrayList<>(new java.util.TreeSet<>(pool.getMissingPages()));
			missing.remove(page);
			if (!missing.isEmpty())
			{
				missingLines.add(String.format(java.util.Locale.ROOT, "%s also drops from %s. If you've killed %s, open %s log page%s so those kills count too.",
					pool.getLabel(), joinAnd(missing), missing.size() == 1 ? "it" : "them",
					missing.size() == 1 ? "its" : "their", missing.size() == 1 ? "" : "s"));
			}
		}
		StringBuilder text = new StringBuilder();
		if (!unknown.isEmpty())
		{
			text.append(joinAnd(unknown)).append(unknown.size() == 1 ? " shows" : " show").append(" up once you open the ").append(page)
				.append(" page in your collection log.");
		}
		for (String line : missingLines)
		{
			if (text.length() > 0)
			{
				text.append("<br><br>");
			}
			text.append(line);
		}
		if (text.length() > 0)
		{
			rowsPanel.add(spacer(Theme.S));
			JLabel line = smallLabel("<html><body style='width: " + Theme.TEXT_WIDTH + "px'>" + text + "</body></html>", ColorScheme.LIGHT_GRAY_COLOR);
			line.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(line);
		}
	}

	/** See {@link Texts#joinAnd}. */
	public static String joinAnd(List<String> names)
	{
		return Texts.joinAnd(names);
	}

	/** Hover text for the boss page's GP value tile, set by {@link #bossValue}. */
	private String lastValueTip;

	/** GE value of this boss's uniques, received vs expected, with its own verdict; null without prices. */
	/** {received GP, expected GP, luck} of this boss's uniques, or null without prices. Sets {@link #lastValueTip}. */
	private double[] bossValue(BossStats stats)
	{
		double[] value = bossValueOf(stats);
		if (value == null)
		{
			return null;
		}
		lastValueTip = new Tip("GP value \u00b7 " + Verdict.labelOf(value[2]))
			.row("Got", Rates.gp((long) value[0]))
			.row("Expected", Rates.gp((long) value[1]))
			.row("By value", capital(luckPercent(value[2])) + " for your kill count")
			.note("The Uniques tile counts how many you got; this one weighs them by GE price. "
				+ "Pets and most jars count as 0 GP.")
			.wide();
		return value;
	}

	/** The same numbers without touching the hover text, so a list can ask for every boss. */
	private double[] bossValueOf(BossStats stats)
	{
		List<Double> lambdas = new ArrayList<>();
		List<Long> itemPrices = new ArrayList<>();
		List<Integer> itemCaps = new ArrayList<>();
		double expected = 0;
		long received = 0;
		int pieces = 0;
		long piecePrice = 0;
		for (int i = 0; i < stats.getDrops().size(); i++)
		{
			DropDefinition drop = stats.getDrops().get(i);
			RowStats row = stats.getRows().get(i);
			long price = tracker.counts(stats.getBoss(), drop) ? Math.max(0, priceOf.applyAsLong(drop.getName())) : 0;
			if (price <= 0)
			{
				continue;
			}
			lambdas.add(row.getExpected());
			itemPrices.add(price);
			itemCaps.add(row.getCap());
			// at most the copies that count (a scroll you can only have once)
			expected += Poisson.expectedCounted(row.getExpected(), row.getCap()) * price;
			if (Untradeables.isHalberdPiece(drop.getName()))
			{
				pieces += row.getReceived();
				piecePrice = price;
			}
			else
			{
				received += row.getReceived() * price;
			}
		}
		received += Untradeables.setValue(pieces, piecePrice);
		if (expected <= 0)
		{
			return null;
		}
		double[] l = new double[lambdas.size()];
		long[] p = new long[itemPrices.size()];
		int[] c = new int[itemCaps.size()];
		for (int i = 0; i < l.length; i++)
		{
			l[i] = lambdas.get(i);
			p[i] = itemPrices.get(i);
			c[i] = itemCaps.get(i);
		}
		double luck = ValueLuck.of(l, p, c, received, 5000, 1234567L);
		if (Double.isNaN(luck))
		{
			return null;
		}
		return new double[]{received, expected, luck};
	}

	/**
	 * One grey line under the verdict at Royal Titans and Yama when kills count as duo, so a
	 * solo player sees why their expected is half and where to change it. Null otherwise.
	 */
	private String duoLine(BossStats stats)
	{
		BossDefinition boss = stats.getBoss();
		if (boss.getTeam() == null || stats.getKillCount() <= 0)
		{
			return null;
		}
		int kills = stats.getKillCount();
		String which = "kills";
		if (boss.isDetectTeam())
		{
			// counted kills use the real team size; only the older ones use the setting
			int seen = tracker.teamKills(boss.getName()).values().stream().mapToInt(Integer::intValue).sum();
			kills = Math.max(0, kills - seen);
			which = "kills from before On Rate";
		}
		if (kills <= 0)
		{
			return null;
		}
		double share = raidSettings.get().contribution(boss.getTeam(), kills);
		if (share >= 1)
		{
			return null;
		}
		return String.format(java.util.Locale.ROOT, "Your %s count as %s. Change it under <i>Team bosses</i> in the settings.",
			which, share <= 0.5 + 1e-9 ? "duo kills" : "partly duo");
	}

	/**
	 * A tip when most of a raid's numbers come from a setting rather than your own points:
	 * how many raids use it, what your measured raids averaged, and which setting to change.
	 */
	private String settingsHint(BossStats stats)
	{
		BossDefinition boss = stats.getBoss();
		if (boss.getRaid() == null || stats.getKillCount() < 10)
		{
			return null;
		}
		int measured = 0;
		for (com.onrate.engine.data.BossPart part : boss.getParts())
		{
			measured += tracker.measuredRaids(boss.getName(), part.getName());
		}
		if ("tob".equals(boss.getRaid()) || "fractured".equals(boss.getRaid()))
		{
			// ToB and the Fractured Archive have no points; raids with a known team size use it
			measured = 0;
			for (com.onrate.engine.tracking.RaidRecord r : tracker.raidRecords(boss.getName()))
			{
				measured += r.getTeamSize() > 0 || r.getContribution() >= 0 ? 1 : 0;
			}
		}
		int fromSettings = Math.max(0, stats.getKillCount() - measured);
		if (fromSettings * 2 < stats.getKillCount())
		{
			return null;
		}
		String advice;
		switch (boss.getRaid())
		{
			case "cox":
				advice = "Big teams or fewer points? Lower it under <i>Raid purple chances</i> in On Rate's settings (8,676 of your points is about 1%).";
				break;
			case "tob":
				advice = "Check <i>Theatre of Blood team size</i> under Raids in On Rate's settings.";
				break;
			case "fractured":
				advice = "Check <i>Fractured Archive team size</i> under Raids in On Rate's settings.";
				break;
			default:
				advice = "Set your usual chance under <i>Raid purple chances</i> in On Rate's settings.";
		}
		boolean archive = "fractured".equals(boss.getRaid());
		boolean theatre = "tob".equals(boss.getRaid());
		String own = archive ? "share of the damage" : theatre ? "team size" : "points";
		String later = archive ? "your own share of the damage" : theatre ? "the real team size" : "your own points";
		lastHintTip = String.format(java.util.Locale.ROOT, "<html><body style='width: " + Theme.TIP_WIDE + "px'>%,d of your %,d raids have no %s On Rate could read "
			+ "(raids from before you installed it, for example), so they use the purple chance from your settings. "
			+ "Raids from now on use %s.%s<br><br>%s</body></html>",
			fromSettings, stats.getKillCount(), own, later,
			// Theatre of Blood has no points, so History has no average to show
			theatre ? "" : " The average of your tracked raids is under History.", advice);
		return String.format(java.util.Locale.ROOT, "%,d of %,d raids use the purple chance from your settings.", fromSettings, stats.getKillCount());
	}

	/** Hover text for the line {@link #settingsHint} returns. */
	private String lastHintTip;

	/** Items need at least this many expected to count as dry: 0 of 0.2 says nothing yet. */
	private static final double MIN_EXPECTED_DRY = 1.0;
	/** How many items {@link #topItems(AccountStats, int)} returns at most. */
	static final int TOP_ITEMS = 15;

	/**
	 * The driest (or luckiest) items across every boss: driest have at least one expected,
	 * luckiest are ones you have. Ranked by luck percentile.
	 */
	public static List<com.onrate.engine.stats.ItemHighlight> topItems(AccountStats account, int mode)
	{
		return topItems(account, mode, TOP_ITEMS);
	}

	/** The same, at most {@code limit} items (pets are always the few shown per group). */
	public static List<com.onrate.engine.stats.ItemHighlight> topItems(AccountStats account, int mode, int limit)
	{
		List<com.onrate.engine.stats.ItemHighlight> list = new ArrayList<>();
		for (com.onrate.engine.stats.ItemHighlight h : account.getItems())
		{
			RowStats r = h.getRow();
			if (mode == TOP_PETS)
			{
				if (h.isPet() && r.getExpected() > 0)
				{
					list.add(h);
				}
				continue;
			}
			if (Double.isNaN(r.getLuck()))
			{
				continue;
			}
			// driest: below average with at least one expected; luckiest: above average and you have it
			if (mode == TOP_LUCKIEST ? r.getReceived() > 0 && r.getLuck() > 0.5
				: r.getExpected() >= MIN_EXPECTED_DRY && !r.isComplete() && r.getLuck() < 0.5)
			{
				list.add(h);
			}
		}
		if (mode == TOP_PETS)
		{
			// the 5 luckiest pets you have, then the 5 you'd most likely have by now but don't
			List<com.onrate.engine.stats.ItemHighlight> lucky = petList(account, true);
			List<com.onrate.engine.stats.ItemHighlight> dry = petList(account, false);
			List<com.onrate.engine.stats.ItemHighlight> result = new ArrayList<>(lucky.subList(0, Math.min(TOP_PETS_SHOWN, lucky.size())));
			result.addAll(dry.subList(0, Math.min(TOP_PETS_SHOWN, dry.size())));
			return result;
		}
		Comparator<com.onrate.engine.stats.ItemHighlight> byLuck = Comparator.comparingDouble(h -> h.getRow().getLuck());
		list.sort(mode == TOP_LUCKIEST ? byLuck.reversed() : byLuck);
		return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
	}

	/**
	 * Every pet at your bosses (expected above 0), in one group: the ones you have, luckiest first (got with the fewest
	 * kills expected), or the ones you don't, the ones you'd most likely have by now first.
	 */
	static List<com.onrate.engine.stats.ItemHighlight> petList(AccountStats account, boolean have)
	{
		List<com.onrate.engine.stats.ItemHighlight> list = new ArrayList<>();
		for (com.onrate.engine.stats.ItemHighlight h : account.getItems())
		{
			if (h.isPet() && h.getRow().getExpected() > 0 && h.getRow().isComplete() == have)
			{
				list.add(h);
			}
		}
		if (have)
		{
			list.sort(Comparator.comparingDouble((com.onrate.engine.stats.ItemHighlight h) -> h.getRow().getExpected())
				.thenComparing(h -> h.getRow().getLabel()));
		}
		else
		{
			list.sort(Comparator.comparingDouble((com.onrate.engine.stats.ItemHighlight h) -> -h.getRow().getChanceAtLeastOne())
				.thenComparing(h -> h.getRow().getLabel()));
		}
		return list;
	}

	private JLabel topSwitch(String text, boolean selected, int mode)
	{
		JLabel label = smallLabel(text, selected ? Theme.TEXT : Theme.TEXT_MUTED);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setOpaque(selected);
		label.setBackground(Theme.SURFACE_SELECTED);
		label.setBorder(new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S));
		if (!selected)
		{
			label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					topMode = mode;
					rememberAccountPage();
					refresh();
				}
			});
		}
		return Activatable.install(label, text + (selected ? ", selected" : ""), () ->
		{
			topMode = mode;
			rememberAccountPage();
			refresh();
		});
	}

	/**
	 * True when a shared item's right-click menu has something to offer: it can be judged per boss, has a
	 * key, and, when older copies need a home, there is a place to put them. The hover hints and
	 * {@link #withSplitMenu} both use this, so a hint never promises a menu that isn't there.
	 */
	static boolean hasSplitMenu(com.onrate.engine.stats.SharedPool pool, boolean destinationsAvailable)
	{
		return pool.isSplittable() && pool.getKey() != null && (pool.getOlderCopies() == 0 || destinationsAvailable);
	}

	/** True when an item row gets the leave-out (or move back) menu; see {@link #withLeaveOutMenu}. */
	static boolean hasLeaveOutMenu(RowStats r)
	{
		return !r.isAny() && r.getIconItem() != null;
	}

	private boolean offersSplitMenu(com.onrate.engine.stats.SharedPool pool)
	{
		return hasSplitMenu(pool, pool.getOlderCopies() == 0 || !splitDestinations(pool).isEmpty());
	}

	private static String poolTooltip(com.onrate.engine.stats.SharedPool pool, RowStats row)
	{
		return poolTooltip(pool, row, hasSplitMenu(pool, true));
	}

	private static String poolTooltip(com.onrate.engine.stats.SharedPool pool, RowStats row, boolean splitMenu)
	{
		Tip tip = new Tip(pool.getLabel()).line("Shared by several bosses", Theme.TEXT_MUTED);
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			tip.row("Your result", "<b>" + Verdict.labelOf(luck) + "</b>", Palette.onRow(luckColor(luck).brighter()))
				.line(capital(sharesLine(row.getShares(), row.getReceived())), Theme.TEXT_MUTED);
		}
		tip.row("Dropped by", joinAnd(pool.getBosses()))
			.row("Expected from all", formatExpected(pool.getExpected()))
			.row("In your collection log", String.valueOf(pool.getReceived()));
		if (!pool.isComplete())
		{
			tip.note("Kills not counted yet: open the " + joinAnd(pool.getMissingPages()) + " log page"
				+ (pool.getMissingPages().size() == 1 ? "" : "s") + ".");
		}
		if (pool.getAlsoFrom() != null)
		{
			// shared items have no "leave out": the account totals count the log total as it is
			tip.note("Also drops from " + pool.getAlsoFrom() + ", and the collection log counts those too, "
				+ "so this can look luckier than it is.");
		}
		if (splitMenu)
		{
			tip.note(pool.getAlsoFrom() != null
				? "Know where yours came from? Right-click to move it to each boss separately, with the ones from "
					+ pool.getAlsoFrom() + " left out."
				: "Know which boss yours came from? Right-click to move it to each boss separately.");
		}
		return tip.wide();
	}

	private static Comparator<RowStats> comparator(Sort sort, AccountStats account)
	{
		Comparator<RowStats> byName = Comparator.comparing(r -> r.getLabel().toLowerCase(Locale.ROOT));
		if (sort == null)
		{
			sort = Sort.EXPECTED;
		}
		switch (sort)
		{
			case DRIEST:
				return Comparator.comparingDouble((RowStats r) -> Double.isNaN(r.getLuck()) ? 2 : r.getLuck()).thenComparing(byName);
			case LUCKIEST:
				return Comparator.comparingDouble((RowStats r) -> Double.isNaN(r.getLuck()) ? 2 : -r.getLuck()).thenComparing(byName);
			case STREAK:
				// longest since the last unique first, in multiples of the boss's unique rate
				return Comparator.comparingDouble((RowStats r) -> Double.isNaN(r.getStreakMultiple()) ? 1 : -r.getStreakMultiple())
					.thenComparing(byName);
			default:
				return Comparator.comparingDouble((RowStats r) -> -r.getExpected()).thenComparing(byName);
		}
	}

	/**
	 * "Closest to green log": the page that takes the least time first. Pages with no answer come after those
	 * with one, and finished pages last.
	 */
	static Comparator<RowStats> greenLogOrder(java.util.Map<String, com.onrate.engine.stats.GreenLog.Page> pages)
	{
		java.util.function.Function<RowStats, com.onrate.engine.stats.GreenLog.Page> page = r -> pages.get(r.getLabel());
		java.util.function.ToIntFunction<RowStats> rank = r ->
		{
			com.onrate.engine.stats.GreenLog.Page p = page.apply(r);
			return p == null || Double.isNaN(p.getAverageSeconds()) ? 1 : p.isDone() ? 2 : 0;
		};
		return Comparator.comparingInt(rank)
			.thenComparingDouble((RowStats r) -> rank.applyAsInt(r) == 0 ? page.apply(r).getAverageSeconds() : 0)
			.thenComparing(r -> r.getLabel().toLowerCase(Locale.ROOT));
	}

	/** The right of a "Closest to green log" row: the average time (as on the Time page and Ladlor's chart), "~" when it rests on the wiki's kill time. */
	static String greenLogText(com.onrate.engine.stats.GreenLog.Page page)
	{
		if (page == null || Double.isNaN(page.getAverageSeconds()))
		{
			return "-";
		}
		return page.isDone() ? "done" : (page.isEstimated() ? "~" : "") + com.onrate.engine.stats.ItemTime.formatLong(page.getAverageSeconds());
	}

	static Color greenLogColor(com.onrate.engine.stats.GreenLog.Page page)
	{
		if (page == null || Double.isNaN(page.getAverageSeconds()))
		{
			return Theme.TEXT_MUTED;
		}
		return page.isDone() ? Palette.onRow(luckColor(0.95).brighter()) : Theme.TEXT;
	}

	/** The hover of a "Closest to green log" row: what's missing, and when half and 9 in 10 players would be done. */
	private static void addGreenLogTip(Tip tip, com.onrate.engine.stats.GreenLog.Page page, boolean withPets)
	{
		if (page == null)
		{
			tip.row("Green log", "no time for this boss yet");
			return;
		}
		List<String> missing = page.getMissing();
		if (missing.isEmpty())
		{
			tip.row("Green log", page.isPetLeftOut() ? "done, only the pet is missing" : "done");
			return;
		}
		String names = missing.size() <= 4 ? String.join(", ", missing)
			: String.join(", ", missing.subList(0, 4)) + " and " + (missing.size() - 4) + " more";
		tip.row("Missing", names);
		if (Double.isNaN(page.getAverageSeconds()))
		{
			tip.line("An item here has no drop chance, so there is no time.", Theme.TEXT_MUTED);
			return;
		}
		String mark = page.isEstimated() ? "~" : "";
		tip.row("Average", mark + com.onrate.engine.stats.ItemTime.formatLong(page.getAverageSeconds()));
		tip.row("Half are done by", mark + com.onrate.engine.stats.ItemTime.formatLong(page.getMedianSeconds()));
		tip.row("9 in 10 are done by", mark + com.onrate.engine.stats.ItemTime.formatLong(page.getLikelySeconds()));
		tip.row("Per " + page.getUnit(), mark + com.onrate.engine.stats.ItemTime.formatLong(page.getSecondsPerKill()));
		tip.note((withPets ? "Pets count." : "Pets are left out.") + " Each item is its own roll, and an item that other bosses drop too counts "
			+ "once you have it from any of them."
			+ (page.isEstimated() ? " The time per " + page.getUnit() + " is the wiki's rate (strong gear): your own takes over once you have enough timed kills." : ""));
	}

	private static String valueTooltip(AccountStats account)
	{
		double luck = account.getValueLuck();
		if (account.getExpectedValue() <= 0 || Double.isNaN(luck))
		{
			return null;
		}
		return new Tip("GP value \u00b7 " + Verdict.labelOf(luck))
			.row("Got", Rates.gp(account.getReceivedValue()))
			.row("Expected", Rates.gp(account.getExpectedValue()))
			.row("By value", capital(luckPercent(luck)) + " for your kill count")
			.note("RuneLite's GE prices. Pets and most jars count as 0 GP.")
			.wide();
	}

	/** "kills", "raids", "delves" or "chests" for a boss by name. */
	private String unitsOf(String bossName)
	{
		BossDefinition boss = tracker.getCatalog().byName(bossName);
		return boss == null ? "kills" : com.onrate.engine.tracking.Tracker.unit(boss);
	}

	private String accountTooltip(RowStats row, AccountStats account)
	{
		return accountTooltip(row, account, null);
	}

	/** The same; {@code byValue} (when the list is sorted by GP value) adds the boss's GP value verdict. */
	private String accountTooltip(RowStats row, AccountStats account, java.util.Map<String, double[]> byValue)
	{
		Tip tip = new Tip(row.isAny() ? "Whole account" : row.getShownLabel());
		if (row.isAny())
		{
			tip.row("Bosses with kills", String.valueOf(account.getRows().size()));
		}
		else
		{
			tip.row(capital(unitsOf(row.getLabel())), String.format(java.util.Locale.ROOT, "%,d", account.getKillCount(row.getLabel())));
		}
		tip.row("Uniques", String.format(java.util.Locale.ROOT, "Got %d, expected %s", row.getReceived(), formatExpected(row.getExpected())));
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			tip.row("Luck", Verdict.labelOf(luck) + ": " + luckPercent(row.getShares(), row.getReceived()), Palette.onRow(luckColor(luck).brighter()));
		}
		if (byValue != null && !row.isAny())
		{
			double[] value = byValue.get(row.getLabel());
			if (value == null)
			{
				tip.row("By value", "nothing of value expected");
			}
			else
			{
				tip.row("By value", Verdict.labelOf(value[2]) + ": " + luckPercent(value[2]), Palette.onRow(luckColor(value[2]).brighter()));
				tip.row("GP value", Rates.gp(value[0]) + " / " + Rates.gp(value[1]));
			}
		}
		if (greenLogPages != null && !row.isAny())
		{
			addGreenLogTip(tip, greenLogPages.get(row.getLabel()), greenLogPets);
		}
		if (!row.isAny() && row.getDriestItem() != null)
		{
			tip.row("Driest right now", String.format(java.util.Locale.ROOT, "%s, %s the drop rate", row.getDriestItem(), formatMultiple(row.getDriestMultiple())));
		}
		if (!Double.isNaN(luck))
		{
			// the full split is a sentence: under the rows, in grey
			tip.note(capital(sharesLine(row.getShares(), row.getReceived())));
		}
		return tip.wide();
	}

	// ---------------------------------------------------------------- item views

	private static final int SMALL_ICON = 24;
	/** Bosses with fewer uniques expected than this aren't listed in the account overview. */
	private static final double MIN_EXPECTED_SHOWN = 0.05;

	/** "?" help for a boss page: how to read the chosen view (HTML without the html tags). */
	private static String bossHelp(ItemView view)
	{
		String how;
		switch (view)
		{
			case DELVE_BARS:
				how = "<b>Expected</b>: the number is how many you should have by now; the bar is how close that is to the next whole one."
					+ "<br><b>Received</b>: the number is how many you got; the bar is how lucky that is.";
				break;
			case COMPACT:
				how = "Each item shows how many you got / should have by now, and how lucky that is "
					+ "(a luck %: 50% is average, low is dry, high is spooned; hover for how many players have fewer or more).";
				break;
			case RINGS:
				how = "Each ring fills as the chance of having the item by now grows. Once you have it, the ring shows "
					+ "the chance of having had another since your last.";
				break;
			case GAUGE:
				how = "The needle shows your luck from dry to spooned; each item is a dot on the same scale.";
				break;
			default:
				how = "The fill is how many you got; the white line is how many you should have by now.";
		}
		return how + "<br><br>Hover an item for its drop rate, your chances "
			+ "and your dry streak. Right-click one to leave it out of the calculations (a drop you got elsewhere, "
			+ "or jars you don't care about), or to add a drop from before On Rate. Change the view with the View button above the items, or in the settings (Display).";
	}

	private void renderBossItems(BossStats stats, String unit, ItemView view)
	{
		viewApplies = true;
		String bossName = stats.getBoss().getName();
		List<RowStats> all = new ArrayList<>();
		all.add(stats.getAnyRow());
		// items the player left out go last, in a section of their own
		List<RowStats> leftOut = new ArrayList<>();
		for (RowStats r : stats.getRows())
		{
			(tracker.isLeftOut(bossName, r.getIconItem()) ? leftOut : all).add(r);
		}
		switch (view)
		{
			case DELVE_BARS:
				for (int i = 0; i < all.size(); i++)
				{
					if (i > 0)
					{
						rowsPanel.add(spacer(Theme.HAIR));
					}
					rowsPanel.add(withLeaveOutMenu(row(all.get(i), stats, unit), bossName, all.get(i), false));
				}
				break;
			case COMPACT:
				rowsPanel.add(compactHeader("Item"));
				for (RowStats r : all)
				{
					rowsPanel.add(withLeaveOutMenu(compactRow(r, itemTooltip(r, stats, unit), null), bossName, r, false));
				}
				break;
			case RINGS:
				JPanel grid = new JPanel(new GridLayout(0, 3, 4, 8));
				grid.setOpaque(false);
				grid.setAlignmentX(LEFT_ALIGNMENT);
				for (RowStats r : all)
				{
					grid.add(withLeaveOutMenu(itemRing(r, itemTooltip(r, stats, unit)), bossName, r, false));
				}
				grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
				rowsPanel.add(Card.gridRow(grid));
				break;
			case GAUGE:
				LuckGauge gauge = new LuckGauge();
				gauge.setLuck(stats.getAnyRow().getLuck());
				rowsPanel.add(gauge);
				for (RowStats r : all)
				{
					rowsPanel.add(spacer(Theme.HAIR));
					rowsPanel.add(withLeaveOutMenu(stripRow(r.isAny() ? null : r.getIconItem(), r.isAny() ? "Any unique" : r.getLabel(),
						r.isAny(), r.isComplete() ? 1.0 : r.getLuck(), itemTooltip(r, stats, unit), null, Texts.isQuiet(r)), bossName, r, false));
				}
				break;
			default:
				if (!viewButtons)
				{
					rowsPanel.add(itemsHeader("ITEMS", "bar = got · line = expected",
						"The white line is how many you should have by now. A bar short of it is behind; past it is ahead. "
							+ "Rare items have their line near the start."));
				}
				for (int i = 0; i < all.size(); i++)
				{
					if (i > 0)
					{
						rowsPanel.add(spacer(Theme.HAIR));
					}
					rowsPanel.add(withLeaveOutMenu(targetRow(all.get(i), itemTooltip(all.get(i), stats, unit)), bossName, all.get(i), false));
				}
		}
		if (!leftOut.isEmpty())
		{
			rowsPanel.add(spacer(Theme.M));
			rowsPanel.add(sectionHeader("LEFT OUT", "right-click to count again",
				"You left these out of the calculations: they don't count for this boss's verdicts, the account "
					+ "overview, the Items tab or dry messages. Right-click one to count it again."));
			for (RowStats r : leftOut)
			{
				rowsPanel.add(withLeaveOutMenu(leftOutRow(r, stats.getBoss().getName()), bossName, r, true));
			}
		}
	}

	/** A greyed line for an item the player left out: its name and got / expected, nothing judged. */
	private JPanel leftOutRow(RowStats r, String bossName)
	{
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S)));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(smallLabel(r.getShownLabel(), Theme.TEXT_MUTED), BorderLayout.CENTER);
		panel.add(smallLabel(gotOfExpected(r), Theme.TEXT_MUTED), BorderLayout.EAST);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		String tip = new Tip(r.getLabel())
			.line(String.format(java.util.Locale.ROOT, "Got %d, expected %s", r.getReceived(), formatExpected(r.getExpected())))
			.note("Left out of " + bossName + "'s verdicts, the account overview, the Items tab and dry messages. "
				+ "Right-click to count it again.")
			.wide();
		setTip(panel, tip, null);
		return Card.row(panel);
	}

	/**
	 * Adds the right-click menu to an item: "Leave out of the calculations", or "Count it again"
	 * for one that's left out. The "Any unique" line has none.
	 */
	private <T extends java.awt.Component> T withLeaveOutMenu(T c, String bossName, RowStats r, boolean leftOut)
	{
		if (!hasLeaveOutMenu(r))
		{
			return c;
		}
		JPopupMenu menu = new JPopupMenu();
		String splitKey = tracker.splitPoolOf(bossName, r.getIconItem());
		if (splitKey != null)
		{
			// a shared item the player chose to judge per boss: it can go back, not be left out
			JMenuItem back = new JMenuItem("Move it back to Shared with other bosses");
			back.addActionListener(e ->
			{
				tracker.unsplitPool(splitKey);
				save.run();
				dropPendingNumbers();
				refresh();
			});
			menu.add(back);
			addPopup(c, menu);
			return c;
		}
		JMenuItem item = new JMenuItem(leftOut ? "Count it again" : "Leave out of the calculations");
		item.addActionListener(e ->
		{
			tracker.setLeftOut(bossName, r.getIconItem(), !leftOut);
			save.run();
			dropPendingNumbers();
			refresh();
		});
		menu.add(item);
		if (!leftOut && r.getReceived() > 0)
		{
			JMenuItem past = new JMenuItem("I got this at " + unitOneOf(bossName) + "...");
			past.addActionListener(e -> askPastDrop(bossName, r));
			menu.add(past);
		}
		addPopup(c, menu);
		return c;
	}

	private String unitOneOf(String bossName)
	{
		BossDefinition boss = tracker.getCatalog().byName(bossName);
		return boss == null ? "kill" : Tracker.unitOne(boss);
	}

	/**
	 * Asks at which kill count a unique the player already has dropped, and adds it to History
	 * (without a date) and to the item's dry streak.
	 */
	private void askPastDrop(String bossName, RowStats r)
	{
		BossDefinition boss = tracker.getCatalog().byName(bossName);
		String unit = unitOneOf(bossName);
		// a page with several modes or bosses: the count is that one's own, as the game's chat shows it
		boolean hasParts = boss != null && boss.hasParts();
		List<String> parts = tracker.pastDropParts(bossName, r.getIconItem());
		// the number box takes anything up to the biggest count of the parts: the part is checked when OK is pressed
		int limit = 0;
		String preselect = null;
		int preselectKills = -1;
		for (String name : parts)
		{
			int lim = tracker.pastDropLimit(bossName, name);
			limit = Math.max(limit, lim);
			int known = lim >= Tracker.UNKNOWN_PART_LIMIT ? 0 : lim;
			if (known > preselectKills)
			{
				preselectKills = known;
				preselect = name;
			}
		}
		if (!hasParts)
		{
			limit = tracker.pastDropLimit(bossName, null);
		}
		if (limit < 1)
		{
			JOptionPane.showMessageDialog(this, "Open this boss's collection log page first, so On Rate knows your kill count.",
				"I got this at " + unit, JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		javax.swing.SpinnerNumberModel model = new javax.swing.SpinnerNumberModel(1, 1, limit, 1);
		javax.swing.JSpinner spinner = new javax.swing.JSpinner(model);
		// a typed number counts at once, not only after Enter or leaving the field (OK would read the old value)
		if (spinner.getEditor() instanceof javax.swing.JSpinner.DefaultEditor)
		{
			javax.swing.JFormattedTextField field = ((javax.swing.JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
			if (field.getFormatter() instanceof javax.swing.text.DefaultFormatter)
			{
				((javax.swing.text.DefaultFormatter) field.getFormatter()).setCommitsOnValidEdit(true);
			}
		}
		JPanel form = new JPanel(new GridLayout(0, 1, Theme.S, Theme.S));
		form.add(new JLabel("<html><body style='width: " + Theme.DIALOG_WIDTH + "px'>At which " + unit + " did you get your " + r.getLabel() + "?<br>"
			+ "It goes into History without a date. Your counts and expected don't change"
			+ (hasParts ? "." : ", and your dry streak counts from it.") + "</body></html>"));
		// a raid with one mode (The Fractured Archive) has nothing to choose
		final javax.swing.JComboBox<String> partBox = hasParts && parts.size() > 1
			? new javax.swing.JComboBox<>(parts.toArray(new String[0])) : null;
		if (partBox != null)
		{
			if (preselect != null)
			{
				partBox.setSelectedItem(preselect);
			}
			form.add(new JLabel(boss != null && boss.getRaid() != null ? "Mode:" : "Dropped from:"));
			form.add(partBox);
		}
		form.add(new JLabel(capital(unit) + " number:"));
		form.add(spinner);
		// with one copy it can only be the first: no question. With more, ask.
		boolean only = r.getReceived() == 1;
		javax.swing.JCheckBox first = new javax.swing.JCheckBox("This was my first one", false);
		if (!only)
		{
			first.setToolTipText("<html><body style='width: " + Theme.DIALOG_WIDTH + "px'>Its wait is counted from the start, so it can show under "
				+ "Single drops. For a later copy leave this off: its wait is the gap since the earlier one you added."
				+ "</body></html>");
			form.add(first);
		}
		// a refusal the player can fix by changing the number asks again with their entries kept; the rest end the dialog
		while (true)
		{
			int answer = JOptionPane.showConfirmDialog(this, form, "I got this at " + unit, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
			if (answer != JOptionPane.OK_OPTION)
			{
				return;
			}
			try
			{
				spinner.commitEdit();
			}
			catch (java.text.ParseException ex)
			{
				JOptionPane.showMessageDialog(this, "That isn't a number between 1 and " + String.format(java.util.Locale.ROOT, "%,d", model.getMaximum()) + ".",
					"I got this at " + unit, JOptionPane.INFORMATION_MESSAGE);
				continue;
			}
			int kc = ((Number) spinner.getValue()).intValue();
			String part = partBox != null ? (String) partBox.getSelectedItem() : hasParts ? parts.get(0) : null;
			int partLimit = tracker.pastDropLimit(bossName, part);
			if (hasParts && kc > partLimit)
			{
				JOptionPane.showMessageDialog(this, "Your " + part + " count is " + String.format(java.util.Locale.ROOT, "%,d", partLimit) + ", so "
					+ String.format(java.util.Locale.ROOT, "%,d", kc) + " is too high for it.", "I got this at " + unit, JOptionPane.INFORMATION_MESSAGE);
				continue;
			}
			PastDropResult result = tracker.tryAddPastDrop(bossName, r.getIconItem(), kc, only || first.isSelected(), part);
			if (result.isAdded())
			{
				save.run();
				String why = tracker.pastDropNotRankedReason(bossName, r.getIconItem(), kc, part);
				if (why != null)
				{
					JOptionPane.showMessageDialog(this, "<html><body style='width: " + Theme.TIP_WIDE + "px'>Added to History. It isn't in Luckiest drops "
						+ "or Longest waits: " + why + "</body></html>", "I got this at " + unit, JOptionPane.INFORMATION_MESSAGE);
				}
				break;
			}
			String where = unit + " " + String.format(java.util.Locale.ROOT, "%,d", kc) + (part == null ? "" : " of " + part);
			String message;
			switch (result)
			{
				case SAW_AT_COUNT:
					message = "On Rate saw " + r.getLabel() + " at " + where + " itself, so it can't be replaced. Pick another number.";
					break;
				case COUNT_TOO_LOW:
				case COUNT_TOO_HIGH:
					message = where + " isn't a " + unit + " number you can add it at. Pick a number between 1 and your count.";
					break;
				case ALL_COPIES_LISTED:
					message = "History already lists every copy your collection log shows. Remove from History frees one.";
					break;
				case HISTORY_FULL:
					message = "History is full of newer kills, so there is no room for an older one.";
					break;
				default:
					message = "Couldn't add it.";
					break;
			}
			JOptionPane.showMessageDialog(this, "<html><body style='width: " + Theme.TIP_WIDE + "px'>" + message + "</body></html>",
				"I got this at " + unit, JOptionPane.INFORMATION_MESSAGE);
			if (!result.isRetryable())
			{
				break;
			}
		}
		dropPendingNumbers();
		refresh();
	}

	/**
	 * Adds the right-click menu to a shared item: move it to each boss separately. The copies
	 * On Rate saw drop go to the boss that dropped them; the older ones it didn't see go where
	 * the player says: a boss they have kills at, or "not from a boss" for an item other
	 * monsters drop too (a visage from a regular dragon).
	 */
	private <T extends java.awt.Component> T withSplitMenu(T c, com.onrate.engine.stats.SharedPool pool)
	{
		if (!offersSplitMenu(pool))
		{
			return c;
		}
		JPopupMenu menu = new JPopupMenu();
		int older = pool.getOlderCopies();
		if (older == 0)
		{
			JMenuItem item = new JMenuItem("Move it to each boss separately");
			item.addActionListener(e -> splitPool(pool, java.util.Collections.emptyMap()));
			menu.add(item);
			addPopup(c, menu);
			return c;
		}
		Map<String, String> where = splitDestinations(pool);
		if (where.isEmpty())
		{
			return c;
		}
		javax.swing.JMenu all = new javax.swing.JMenu(older == 1 ? "Move my older one to"
			: "Move all " + older + " older ones to");
		for (Map.Entry<String, String> w : where.entrySet())
		{
			JMenuItem item = new JMenuItem(w.getValue());
			item.addActionListener(e -> splitPool(pool, java.util.Collections.singletonMap(w.getKey(), older)));
			all.add(item);
		}
		menu.add(all);
		if (older > 1 && where.size() > 1)
		{
			JMenuItem some = new JMenuItem("Split my " + older + " older ones between bosses...");
			some.addActionListener(e -> askSplit(pool, where));
			menu.add(some);
		}
		addPopup(c, menu);
		return c;
	}

	/**
	 * Where a shared item's older copies can go, key to menu text: the bosses that drop it that
	 * have kills, then "Not from a boss" when the log also counts it from other monsters.
	 */
	private Map<String, String> splitDestinations(com.onrate.engine.stats.SharedPool pool)
	{
		Map<String, String> where = new java.util.LinkedHashMap<>();
		for (String boss : pool.getBosses())
		{
			BossStats s = tracker.stats(boss, raidSettings.get());
			if (s != null && s.getKillCount() > 0)
			{
				where.put(boss, boss);
			}
		}
		if (pool.getAlsoFrom() != null)
		{
			where.put(Tracker.NOT_FROM_A_BOSS, "Not from a boss (" + pool.getAlsoFrom() + ")");
		}
		return where;
	}

	/** Asks how many older copies came from each place, and splits the item when they add up. */
	private void askSplit(com.onrate.engine.stats.SharedPool pool, Map<String, String> where)
	{
		int older = pool.getOlderCopies();
		JPanel form = new JPanel(new GridLayout(0, 2, Theme.M, Theme.S));
		Map<String, javax.swing.JSpinner> counts = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, String> w : where.entrySet())
		{
			javax.swing.JSpinner spinner = new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(0, 0, older, 1));
			counts.put(w.getKey(), spinner);
			form.add(new JLabel(w.getValue()));
			form.add(spinner);
		}
		JPanel content = new JPanel(new BorderLayout(0, Theme.M));
		content.add(new JLabel("<html><body style='width: " + Theme.DIALOG_WIDTH + "px'>Your collection log has " + older + " " + pool.getLabel()
			+ " copies On Rate didn't see drop. Where did they come from? They need to add up to " + older
			+ ".</body></html>"), BorderLayout.NORTH);
		content.add(form, BorderLayout.CENTER);
		while (true)
		{
			int answer = JOptionPane.showConfirmDialog(this, content, "Move " + pool.getLabel() + " to bosses",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (answer != JOptionPane.OK_OPTION)
			{
				return;
			}
			Map<String, Integer> chosen = new java.util.LinkedHashMap<>();
			for (Map.Entry<String, javax.swing.JSpinner> e : counts.entrySet())
			{
				chosen.put(e.getKey(), (Integer) e.getValue().getValue());
			}
			if (splitPool(pool, chosen))
			{
				return;
			}
			JOptionPane.showMessageDialog(this, "They need to add up to " + older + ".", "Move " + pool.getLabel() + " to bosses",
				JOptionPane.WARNING_MESSAGE);
		}
	}

	private boolean splitPool(com.onrate.engine.stats.SharedPool pool, Map<String, Integer> older)
	{
		boolean done = tracker.splitPool(pool.getKey(), older);
		if (done)
		{
			save.run();
			dropPendingNumbers();
			refresh();
		}
		return done;
	}

	/**
	 * Adds the right-click "Remove from History" menu to a line of saved uniques: one item for a
	 * line with one unique, or one per unique when a kill line lists several. It removes only
	 * that saved entry, after asking.
	 */
	private <T extends java.awt.Component> T withRemoveMenu(T c, String bossName,
		com.onrate.engine.tracking.KillEntry kill, List<com.onrate.engine.tracking.KillEntry.DropEntry> drops)
	{
		if (drops.isEmpty())
		{
			return c;
		}
		JPopupMenu menu = new JPopupMenu();
		if (drops.size() == 1)
		{
			menu.add(removeItem("Remove from History", bossName, kill, drops.get(0)));
		}
		else
		{
			javax.swing.JMenu remove = new javax.swing.JMenu("Remove from History");
			for (com.onrate.engine.tracking.KillEntry.DropEntry d : new ArrayList<>(drops))
			{
				remove.add(removeItem(d.getName(), bossName, kill, d));
			}
			menu.add(remove);
		}
		addPopup(c, menu);
		return c;
	}

	private JMenuItem removeItem(String label, String bossName, com.onrate.engine.tracking.KillEntry kill,
		com.onrate.engine.tracking.KillEntry.DropEntry drop)
	{
		JMenuItem item = new JMenuItem(label);
		item.addActionListener(e ->
		{
			BossDefinition boss = tracker.getCatalog().byName(bossName);
			int answer = JOptionPane.showConfirmDialog(this,
				"Remove " + drop.getName() + " (" + whereText(boss, bossName, kill) + ", "
					+ daysAgo(kill.getTime(), System.currentTimeMillis()) + ") from History?\n"
					+ "Only this entry goes from History and Recent. Your unique counts, kill count, expected and dry streak stay as they are.",
				"Remove from History", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.OK_OPTION)
			{
				return;
			}
			if (tracker.removeFromHistory(bossName, kill, drop))
			{
				save.run();
			}
			// also when it was already gone (trimmed, or another account loaded): show what's there
			dropPendingNumbers();
			refresh();
		});
		return item;
	}

	/** Shows the menu on a right-click anywhere on the component, its labels included. */
	private static void addPopup(java.awt.Component c, JPopupMenu menu)
	{
		if (c instanceof javax.swing.JComponent)
		{
			// the keyboard way in: Shift+F10 or the context-menu key on the focused row
			javax.swing.JComponent jc = (javax.swing.JComponent) c;
			if (!jc.isFocusable())
			{
				String name = Activatable.plain(jc.getToolTipText());
				Activatable.install(jc, (name.isEmpty() ? "Item" : name) + ", options", () -> menu.show(jc, 0, jc.getHeight()));
			}
			Activatable.onContextMenu(jc, () -> menu.show(jc, 0, jc.getHeight()));
		}
		popupMouse(c, menu);
	}

	private static void popupMouse(java.awt.Component c, JPopupMenu menu)
	{
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				show(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				show(e);
			}

			private void show(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		if (c instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) c).getComponents())
			{
				popupMouse(child, menu);
			}
		}
	}

	/**
	 * The account overview's Sort row: the sort list shows on the Bosses tab; on the Items tab only the View
	 * button stays. It is icon-only everywhere, like on the boss pages.
	 */
	private void sortControls(boolean withSort)
	{
		sortLabel.setVisible(withSort);
		sortSelect.setVisible(withSort);
		if (!withSort)
		{
			// the Pets slider belongs to the Bosses list's "Closest to green log" sort
			petsSwitch.setVisible(false);
		}
		accountViewChip.setVisible(viewButtons);
		accountViewChip.setCompact(true);
		sortEnd.setVisible(petsSwitch.isVisible() || accountViewChip.isVisible());
	}

	/** The title over the item list ("ITEMS") with a key on the right: the layout without the View button. */
	private JPanel itemsHeader(String title, String key, String keyTip)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		panel.add(smallLabel(title, Theme.TEXT_MUTED), BorderLayout.WEST);
		JLabel right = smallLabel(key, Theme.TEXT_MUTED);
		right.setToolTipText(new Tip(null).line(keyTip).small());
		panel.add(right, BorderLayout.EAST);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		return panel;
	}

	/** A small grey section title with a key on the right ("ITEMS · white line = expected"). */
	private JPanel sectionHeader(String title, String key, String tip)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		panel.add(smallLabel(title, Theme.TEXT_MUTED), BorderLayout.WEST);
		JLabel right = smallLabel(key, Theme.TEXT_MUTED);
		right.setToolTipText(new Tip(null).line(tip).small());
		panel.add(right, BorderLayout.EAST);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		return panel;
	}

	/** The option shows only while the GP value setting is on; a saved choice of it falls back to Luckiest first. */
	private void showValueSort(boolean on)
	{
		boolean has = false;
		for (int i = 0; i < sortSelect.getItemCount(); i++)
		{
			has |= sortSelect.getItemAt(i) == Sort.VALUE;
		}
		if (on == has)
		{
			return;
		}
		sortOptionsUpdating = true;
		try
		{
			if (on)
			{
				int at = 0;
				for (int i = 0; i < sortSelect.getItemCount(); i++)
				{
					at = sortSelect.getItemAt(i) == Sort.LUCKIEST ? i + 1 : at;
				}
				sortSelect.insertItemAt(Sort.VALUE, at);
			}
			else
			{
				boolean selected = sortSelect.getSelectedItem() == Sort.VALUE;
				if (selected)
				{
					sortSelect.setSelectedItem(Sort.LUCKIEST);
				}
				sortSelect.removeItem(Sort.VALUE);
				if (selected)
				{
					rememberAccountPage();
				}
			}
		}
		finally
		{
			sortOptionsUpdating = false;
		}
	}

	/** The row as the list shows it: with the GP value verdict as its luck when the list is sorted by value. */
	private static RowStats shownRow(RowStats row, java.util.Map<String, double[]> byValue)
	{
		if (byValue == null)
		{
			return row;
		}
		double[] value = byValue.get(row.getLabel());
		return row.withDisplayLuck(value == null ? Double.NaN : value[2]);
	}

	private void renderAccountItems(AccountStats account, RowStats total, List<RowStats> rows, ItemView view,
		java.util.Map<String, double[]> byValue)
	{
		rowGroup = new Activatable.Group();
		try
		{
			renderAccountRows(account, total, rows, view, byValue);
		}
		finally
		{
			rowGroup = null;
		}
	}

	private void renderAccountRows(AccountStats account, RowStats total, List<RowStats> rows, ItemView view,
		java.util.Map<String, double[]> byValue)
	{
		if (rows.isEmpty())
		{
			// nothing to list yet: no empty table headers
			return;
		}
		switch (view)
		{
			case DELVE_BARS:
				rowsPanel.add(buildRow(total, accountTooltip(total, account), null));
				for (RowStats row : rows)
				{
					rowsPanel.add(spacer(Theme.HAIR));
					rowsPanel.add(buildRow(shownRow(row, byValue), accountTooltip(row, account, byValue), () -> showBoss(row.getLabel())));
				}
				break;
			case COMPACT:
				rowsPanel.add(compactHeader("Boss"));
				for (RowStats row : rows)
				{
					rowsPanel.add(compactRow(shownRow(row, byValue), accountTooltip(row, account, byValue), () -> showBoss(row.getLabel())));
				}
				break;
			case RINGS:
				JPanel grid = new JPanel(new GridLayout(0, 3, 4, 8));
				grid.setOpaque(false);
				grid.setAlignmentX(LEFT_ALIGNMENT);
				for (RowStats row : rows)
				{
					grid.add(bossRing(shownRow(row, byValue), accountTooltip(row, account, byValue)));
				}
				grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
				rowsPanel.add(Card.gridRow(grid));
				break;
			case GAUGE:
				LuckGauge gauge = new LuckGauge();
				gauge.setLuck(total.getLuck());
				rowsPanel.add(gauge);
				for (RowStats row : rows)
				{
					rowsPanel.add(spacer(Theme.HAIR));
					rowsPanel.add(stripRow(row.getIconItem(), row.getShownLabel(), false, shownRow(row, byValue).getLuck(),
						accountTooltip(row, account, byValue), () -> showBoss(row.getLabel())));
				}
				break;
			default:
				for (int i = 0; i < rows.size(); i++)
				{
					RowStats row = rows.get(i);
					if (i > 0)
					{
						rowsPanel.add(spacer(Theme.HAIR));
					}
					boolean streak = sortSelect.getSelectedItem() == Sort.STREAK;
					com.onrate.engine.stats.GreenLog.Page page = greenLogPages == null ? null : greenLogPages.get(row.getLabel());
					String right = streak ? streakText(row) : greenLogPages != null ? greenLogText(page) : null;
					Color rightColor = streak ? streakColor(row) : greenLogPages != null ? greenLogColor(page) : null;
					rowsPanel.add(luckRow(shownRow(row, byValue), accountTooltip(row, account, byValue), () -> showBoss(row.getLabel()),
						right, rightColor));
				}
		}
	}

	/** How long a tooltip stays while the mouse is over the panel: long enough to read the item details. */
	static final int TOOLTIP_DISMISS_MS = 60_000;

	/**
	 * Tooltips in this panel stay up for a minute instead of Swing's 4 seconds. The delay is one setting
	 * for all of RuneLite, so it's set while the mouse is over the panel and put back when it leaves.
	 */
	private void keepTooltipsOpen()
	{
		javax.swing.ToolTipManager tips = javax.swing.ToolTipManager.sharedInstance();
		int[] usual = {tips.getDismissDelay()};
		boolean[] inside = {false};
		tooltipRestore = () ->
		{
			if (inside[0])
			{
				tips.setDismissDelay(usual[0]);
				inside[0] = false;
			}
		};
		tooltipListener = event ->
		{
			if (!(event instanceof MouseEvent) || !(event.getSource() instanceof java.awt.Component))
			{
				return;
			}
			int id = event.getID();
			if (id != MouseEvent.MOUSE_ENTERED && id != MouseEvent.MOUSE_EXITED)
			{
				return;
			}
			java.awt.Component c = (java.awt.Component) event.getSource();
			boolean over = id == MouseEvent.MOUSE_ENTERED && javax.swing.SwingUtilities.isDescendingFrom(c, this);
			if (over && !inside[0])
			{
				usual[0] = tips.getDismissDelay();
				tips.setDismissDelay(TOOLTIP_DISMISS_MS);
				inside[0] = true;
			}
			else if (!over && inside[0] && id == MouseEvent.MOUSE_ENTERED)
			{
				tips.setDismissDelay(usual[0]);
				inside[0] = false;
			}
			else if (inside[0] && id == MouseEvent.MOUSE_EXITED && !isMouseOverPanel())
			{
				tips.setDismissDelay(usual[0]);
				inside[0] = false;
			}
		};
		java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(tooltipListener, java.awt.AWTEvent.MOUSE_EVENT_MASK);
	}

	/** The listener {@link #keepTooltipsOpen()} added for the whole window, kept so it can be taken off again. */
	private java.awt.event.AWTEventListener tooltipListener;
	/** Puts RuneLite's own tooltip delay back if the mouse is over the panel right now. */
	private Runnable tooltipRestore = () -> { };

	/**
	 * The plugin stopped: takes off what the panel added outside itself, so nothing of it stays behind in
	 * RuneLite. That is the mouse listener for the whole window and, if the mouse is over the panel right
	 * now, the long tooltip delay. Safe to call twice. Swing thread.
	 */
	public void dispose()
	{
		if (tooltipListener != null)
		{
			java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(tooltipListener);
			tooltipListener = null;
		}
		tooltipRestore.run();
	}

	private boolean isMouseOverPanel()
	{
		java.awt.Point p = getMousePosition(true);
		return p != null;
	}

	/** An item picture scaled to {@code size} pixels high, or its initials. */
	private JLabel itemIcon(String item, String label, int size)
	{
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(size * 36 / 32 + 2, size));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		int id = item == null ? -1 : itemIdFor.applyAsInt(item);
		if (id > 0 && itemManager != null)
		{
			net.runelite.client.util.AsyncBufferedImage image = itemManager.getImage(id);
			if (size >= 32)
			{
				image.addTo(icon);
			}
			else
			{
				Runnable scale = () -> icon.setIcon(new javax.swing.ImageIcon(
					image.getScaledInstance(size * 36 / 32, size, java.awt.Image.SCALE_SMOOTH)));
				image.onLoaded(scale);
				scale.run();
			}
		}
		else
		{
			String text = initials(label);
			icon.setText(size < 32 && text.length() > 2 ? text.substring(0, 2) : text);
			icon.setFont(FontManager.getRunescapeSmallFont());
			icon.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		return icon;
	}

	/** A label in the bold font: the "Any unique" row and section titles. */
	private static JLabel boldLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Theme.TEXT);
		return label;
	}

	private static JLabel smallLabel(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		return l;
	}

	private static String gotOfExpected(RowStats r)
	{
		return Texts.gotOfExpected(r);
	}

	/** Item row: name and "got / expected" on top, a bar with the expected marker below. */
	private JPanel targetRow(RowStats r, String tip)
	{
		// nothing expected yet and none got: the row stays, but quietly, so the rows with something to read stand out
		boolean quiet = Texts.isQuiet(r);
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setBackground(Theme.SURFACE);
		panel.setBorder(new EmptyBorder(Theme.HAIR, Theme.S, quiet ? Theme.HAIR : Theme.S, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);

		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		JLabel name;
		if (r.isAny())
		{
			name = boldLabel("Any unique");
		}
		else
		{
			line.add(itemIcon(r.getIconItem(), r.getLabel(), SMALL_ICON), BorderLayout.WEST);
			name = rowLabel(r.getShownLabel(), quiet ? Theme.TEXT_MUTED : Theme.TEXT);
			if (quiet)
			{
				name.setFont(Theme.numberFont());
			}
		}
		line.add(name, BorderLayout.CENTER);
		boolean have = r.isComplete();
		// the name is the bold one; the count is the regular weight, white once there is something to count
		JLabel count = rowLabel(have ? Texts.haveText(r) : gotOfExpected(r),
			r.isAny() || have || r.getReceived() > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
		count.setFont(Theme.numberFont());
		line.add(count, BorderLayout.EAST);
		if (!r.isAny())
		{
			name.setText(rowName(name, r.getShownLabel(), 2 + 2 * Theme.S + iconWidth(SMALL_ICON) + 8 + count.getPreferredSize().width));
		}
		panel.add(line, BorderLayout.CENTER);

		TargetBar bar = new TargetBar();
		bar.setQuiet(quiet);
		if (have)
		{
			bar.set(1, -1, Palette.good());
		}
		else
		{
			// each bar has its own scale, so a rare item's line sits near the start: with 0 / 0.12
			// you're not behind yet, and the bar shows that
			double scale = Math.max(1, Math.max(r.getExpected(), r.getReceived())) * 1.25;
			bar.set(r.getReceived() / scale, r.getExpected() / scale, luckColor(r.getLuck()));
		}
		panel.add(bar, BorderLayout.SOUTH);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		setTip(panel, tip, null);
		return Card.row(panel);
	}

	/** Account row: boss picture, then a luck bar with the boss name and its verdict. */
	private JPanel luckRow(RowStats r, String tip, Runnable onClick)
	{
		return luckRow(r, tip, onClick, null);
	}

	/**
	 * A boss or item row: picture, name and the verdict in its colour, and under them where you
	 * sit on the dry-to-spooned scale. Luck is a marker on a scale, never a fill, so it can't be
	 * read as progress.
	 *
	 * @param right text on the right instead of the verdict, or null
	 * @param rightColor colour of that text, or null for the luck colour
	 */
	private JPanel luckRow(RowStats r, String tip, Runnable onClick, String right, Color rightColor)
	{
		return luckRow(r, tip, onClick, right, rightColor, null);
	}

	/**
	 * @param figure "got / expected" to show beside the dot, in a column of one width on every row so the
	 *     track and its middle tick line up down the list, or null for none
	 */
	private JPanel luckRow(RowStats r, String tip, Runnable onClick, String right, Color rightColor, String figure)
	{
		JPanel panel = new JPanel(new BorderLayout(Theme.S, 0));
		panel.setBackground(Theme.SURFACE);
		panel.setBorder(new EmptyBorder(Theme.S, Theme.HAIR, Theme.S, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(itemIcon(r.getIconItem(), r.getLabel(), SMALL_ICON), BorderLayout.WEST);
		double luck = r.getLuck();
		JPanel body = new JPanel(new BorderLayout(0, Theme.HAIR));
		body.setOpaque(false);
		JPanel line = new JPanel(new BorderLayout(Theme.S, 0));
		line.setOpaque(false);
		JLabel luckName = rowLabel(r.getShownLabel(), Theme.TEXT);
		line.add(luckName, BorderLayout.CENTER);
		String text = right != null ? right : Double.isNaN(luck) ? "-" : Verdict.labelOf(luck);
		Color color = rightColor != null ? rightColor
			: "-".equals(text) || Double.isNaN(luck) ? Theme.TEXT_MUTED : verdictWordColor(luck);
		JLabel verdict = rowLabel(text, color);
		line.add(verdict, BorderLayout.EAST);
		// the card's outline, the row's border, the picture and the gaps, and the verdict beside the name
		luckName.setText(rowName(luckName, r.getShownLabel(),
			2 + Theme.HAIR + Theme.S + iconWidth(SMALL_ICON) + 2 * Theme.S + verdict.getPreferredSize().width));
		body.add(line, BorderLayout.CENTER);
		// the same scale on every row of a long list: one neutral track and the dot, no bands
		LuckStrip strip = new LuckStrip();
		strip.setBands(false);
		strip.setLuck(luck);
		strip.setPreferredSize(new Dimension(80, 12));
		if (figure == null)
		{
			body.add(strip, BorderLayout.SOUTH);
		}
		else
		{
			// the figure shares the strip's line, so the row is no taller than without it
			JLabel figureLabel = smallLabel(figure, r.getReceived() > 0 ? Theme.TEXT : Theme.TEXT_MUTED);
			figureLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			figureLabel.setPreferredSize(new Dimension(FIGURE_WIDTH, strip.getPreferredSize().height));
			JPanel under = new JPanel(new BorderLayout(Theme.S, 0));
			under.setOpaque(false);
			under.add(strip, BorderLayout.CENTER);
			under.add(figureLabel, BorderLayout.EAST);
			body.add(under, BorderLayout.SOUTH);
		}
		panel.add(body, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		setTip(panel, tip, onClick);
		return Card.row(panel);
	}

	/** Width of the "got / expected" column: "0 / 35.5" and "99 / 153" fit; the widest items need about 40. */
	static final int FIGURE_WIDTH = 48;

	private JPanel luckRow(RowStats r, String tip, Runnable onClick, String right)
	{
		return luckRow(r, tip, onClick, right, null);
	}

	/**
	 * Colour of a dry streak: white while it's normal, orange when it's long (fewer than 35% of
	 * players wait this long) and red when it's very long (fewer than 10%), like the verdicts.
	 */
	static Color streakColor(RowStats row)
	{
		double m = row.getStreakMultiple();
		if ("-".equals(streakText(row)))
		{
			return Theme.TEXT_MUTED;
		}
		double luck = Math.exp(-m);
		return luck >= 0.35 ? Theme.TEXT : Palette.onRow(luckColor(luck).brighter());
	}

	/**
	 * The name for a row label: broken over lines when it would not fit beside the other things in the row.
	 *
	 * @param taken width of everything else on the row: the card, borders, picture, gaps and the other labels
	 */
	private String rowName(JLabel label, String name, int taken)
	{
		if (!wrapNames)
		{
			return name;
		}
		return VerdictTile.wrapIfLong(name, label.getFontMetrics(label.getFont()), ROW_WIDTH - taken, NAME_LINES);
	}

	/** Width of the picture cell {@link #itemIcon} makes. */
	private static int iconWidth(int size)
	{
		return size * 36 / 32 + 2;
	}

	/** A row's name or number, in the same font as the verdicts at the top ("On rate"). */
	private static JLabel rowLabel(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(Theme.rowFont());
		l.setForeground(color);
		return l;
	}

	private JPanel compactHeader(String first)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(0, Theme.S, Theme.HAIR, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(smallLabel(first, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		JPanel right = new JPanel(new BorderLayout(6, 0));
		right.setOpaque(false);
		right.add(smallLabel("Got / Exp", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.CENTER);
		JLabel luck = smallLabel("Luck", ColorScheme.LIGHT_GRAY_COLOR);
		luck.setHorizontalAlignment(SwingConstants.CENTER);
		luck.setPreferredSize(new Dimension(38, 14));
		luck.setToolTipText(new Tip(null)
			.line("How lucky you are. 50% is average, lower is drier, higher is luckier.").small());
		right.add(luck, BorderLayout.EAST);
		panel.add(right, BorderLayout.EAST);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		return panel;
	}

	/** One line: picture, name, "got / expected" and a luck chip. */
	private JPanel compactRow(RowStats r, String tip, Runnable onClick)
	{
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(Theme.HAIR, Theme.S, Theme.HAIR, Theme.S)));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		if (r.isAny())
		{
			panel.add(boldLabel("Any unique"), BorderLayout.CENTER);
		}
		else
		{
			panel.add(itemIcon(r.getIconItem(), r.getLabel(), 20), BorderLayout.WEST);
			panel.add(smallLabel(r.getShownLabel(), Texts.isQuiet(r) ? Theme.TEXT_MUTED : Color.WHITE), BorderLayout.CENTER);
		}
		JPanel right = new JPanel(new BorderLayout(6, 0));
		right.setOpaque(false);
		boolean have = r.isComplete();
		right.add(smallLabel(have ? Texts.haveText(r) : gotOfExpected(r),
			Texts.isQuiet(r) ? Theme.TEXT_MUTED : Color.WHITE), BorderLayout.CENTER);
		double luck = r.getLuck();
		String chip = Double.isNaN(luck) ? "-" : pct(luck);
		// the card look: the tinted pill (white on the solid yellow or green chip is hard to read)
		right.add(boxes ? new LuckPill(chip, luckColor(luck), 46) : new Chip(chip, luckColor(luck)), BorderLayout.EAST);
		panel.add(right, BorderLayout.EAST);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		setTip(panel, tip, onClick);
		return Card.row(panel);
	}

	/** Name on the left, the dry-to-spooned scale with your dot on the right. */
	private JPanel stripRow(String item, String label, boolean any, double luck, String tip, Runnable onClick)
	{
		return stripRow(item, label, any, luck, tip, onClick, false);
	}

	private JPanel stripRow(String item, String label, boolean any, double luck, String tip, Runnable onClick, boolean quiet)
	{
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JPanel left = new JPanel(new BorderLayout(4, 0));
		left.setOpaque(false);
		if (!any)
		{
			left.add(itemIcon(item, label, 20), BorderLayout.WEST);
		}
		left.add(any ? boldLabel(label) : smallLabel(label, quiet ? Theme.TEXT_MUTED : Theme.TEXT), BorderLayout.CENTER);
		left.setPreferredSize(new Dimension(110, 18));
		panel.add(left, BorderLayout.WEST);
		LuckStrip strip = new LuckStrip();
		strip.setLuck(luck);
		panel.add(strip, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		setTip(panel, tip, onClick);
		return Card.row(panel);
	}

	/** Item ring: chance of having it by now, or of the next one once you have it. */
	private JPanel itemRing(RowStats r, String tip)
	{
		return itemRing(r, tip, null);
	}

	private JPanel itemRing(RowStats r, String tip, Runnable onClick)
	{
		LuckRing ring = new LuckRing();
		String sub;
		boolean have = r.isComplete();
		if (have)
		{
			ring.set(1, Palette.good());
			sub = Texts.haveText(r);
		}
		else if (r.getReceived() > 0 && r.getStreak() != null && r.getRate() > 0)
		{
			double m = r.getStreakMultiple();
			double p = -Math.expm1(-m);
			ring.set(p, luckColor(Math.exp(-m)));
			sub = String.format(java.util.Locale.ROOT, "%.0f%% so far", p * 100);
		}
		else
		{
			double p = r.getChanceAtLeastOne();
			ring.set(p, luckColor(r.getLuck()));
			sub = String.format(java.util.Locale.ROOT, "%.0f%% by now", p * 100);
		}
		if (r.isAny())
		{
			ring.setText("Any");
			ring.setFont(FontManager.getRunescapeBoldFont());
			ring.setForeground(Theme.TEXT);
		}
		else
		{
			ringPicture(ring, r.getIconItem(), r.getLabel());
		}
		return ringCell(ring, r.isAny() ? "Any unique" : r.getLabel(), sub, tip, onClick, Texts.isQuiet(r));
	}

	/** Boss ring in the account overview: filled to your luck percentile. */
	private JPanel bossRing(RowStats r, String tip)
	{
		LuckRing ring = new LuckRing();
		double luck = r.getLuck();
		ring.set(Double.isNaN(luck) ? 0 : luck, luckColor(luck));
		ringPicture(ring, r.getIconItem(), r.getLabel());
		return ringCell(ring, r.getShownLabel(), Double.isNaN(luck) ? "-" : Verdict.labelOf(luck), tip, () -> showBoss(r.getLabel()), false);
	}

	private void ringPicture(LuckRing ring, String item, String label)
	{
		int id = item == null ? -1 : itemIdFor.applyAsInt(item);
		if (id > 0 && itemManager != null)
		{
			itemManager.getImage(id).addTo(ring);
		}
		else
		{
			ring.setText(initials(label));
			ring.setFont(FontManager.getRunescapeSmallFont());
			ring.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
	}

	/** Wrap width of a name under a ring: a third of the panel, less the gaps. */
	private static final int RING_NAME_WIDTH = 50;

	private JPanel ringCell(LuckRing ring, String name, String sub, String tip, Runnable onClick, boolean quiet)
	{
		JPanel cell = new JPanel(new BorderLayout(0, 1));
		cell.setOpaque(false);
		cell.add(ring, BorderLayout.CENTER);
		JPanel labels = new JPanel();
		labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
		labels.setOpaque(false);
		// a long name wraps to a second line instead of being cut off
		JLabel n = smallLabel("<html><div style='text-align: center; width: " + RING_NAME_WIDTH + "px'>" + html(name.replace("&", "&amp;")) + "</div></html>",
			quiet ? Theme.TEXT_MUTED : Color.WHITE);
		if (wrapNames)
		{
			// two lines at most, the second ending in "..." (the full name is in the tooltip); the breaks are made
			// here from the font, because a CSS width in an HTML label is not in pixels
			String shown = VerdictTile.wrapIfLong(name, n.getFontMetrics(n.getFont()), RING_NAME_WIDTH, 2);
			n.setText(shown.startsWith("<html>")
				? shown.replace("<html>", "<html><div style='text-align: center'>").replace("</html>", "</div></html>")
				: shown);
			n.setHorizontalAlignment(SwingConstants.CENTER);
		}
		n.setAlignmentX(CENTER_ALIGNMENT);
		// room for two lines in every cell, so the rings stay the same size and line up
		n.setVerticalAlignment(SwingConstants.TOP);
		n.setPreferredSize(new Dimension(n.getPreferredSize().width, 26));
		n.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		JLabel s2 = smallLabel(sub, ColorScheme.LIGHT_GRAY_COLOR);
		s2.setAlignmentX(CENTER_ALIGNMENT);
		labels.add(n);
		labels.add(s2);
		cell.add(labels, BorderLayout.SOUTH);
		setTip(cell, tip, onClick);
		return cell;
	}

	/** Tooltip (and click) on a row and everything in it. */
	private static void setTip(JPanel panel, String tip, Runnable onClick)
	{
		setTip(panel, tip, onClick, false);
	}

	/**
	 * {@link #setTip(JPanel, String, Runnable)}, where only a left click runs {@code onClick}
	 * when {@code leftOnly}: for rows whose right click opens a menu.
	 */
	private static void setTip(JPanel panel, String tip, Runnable onClick, boolean leftOnly)
	{
		Color normal = panel.getBackground();
		MouseAdapter click = onClick == null ? null : new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (!leftOnly || javax.swing.SwingUtilities.isLeftMouseButton(e))
				{
					onClick.run();
				}
			}

			// a clickable row lights up under the mouse, so "click to open" shows without a tooltip
			@Override
			public void mouseEntered(MouseEvent e)
			{
				panel.setBackground(Theme.SURFACE_RAISED);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				if (panel.getMousePosition(true) == null)
				{
					panel.setBackground(normal);
				}
			}
		};
		applyTip(panel, tip, click);
		if (onClick != null)
		{
			Activatable.install(panel, Activatable.plain(tip), onClick, rowGroup);
		}
	}

	private static void applyTip(java.awt.Component c, String tip, MouseAdapter click)
	{
		if (c instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) c).setToolTipText(tip);
		}
		if (click != null)
		{
			c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			c.addMouseListener(click);
		}
		if (c instanceof java.awt.Container)
		{
			for (java.awt.Component child : ((java.awt.Container) c).getComponents())
			{
				applyTip(child, tip, click);
			}
		}
	}

	// ---------------------------------------------------------------- extras

	/**
	 * "This trip" card: the verdict first (or a dash only when nothing is expected), then kills,
	 * uniques against expected, what the expected is made of (shared items included), and the trip's state.
	 */
	private void addTripCard(String bossName)
	{
		com.onrate.engine.tracking.TripStats trip = tracker.tripStats();
		if (trip == null || !trip.getBoss().equals(bossName))
		{
			return;
		}
		// the card look: the trip shows a verdict, so it gets the tiles' coloured top edge
		boolean tripEarlyNow = tripEarly && !Double.isNaN(trip.getLuck()) && Verdict.tripTooEarly(trip.getExpected());
		Color tripEdge = boxes && !tripEarlyNow && !Double.isNaN(trip.getLuck()) ? luckColor(trip.getLuck()).brighter() : null;
		// a rounded card, like the verdict tiles
		JPanel card = new JPanel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics graphics)
			{
				java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
				g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(getBackground());
				g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				g.setColor(Theme.OUTLINE);
				g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				if (tripEdge != null)
				{
					g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
					g.setColor(tripEdge);
					g.fillRect(0, 0, getWidth(), 2);
				}
				g.dispose();
			}
		};
		card.setOpaque(false);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(Theme.M, Theme.M + 1, Theme.M, Theme.M + 1));
		card.setAlignmentX(LEFT_ALIGNMENT);

		BossDefinition tripBoss = tracker.getCatalog().byName(bossName);
		String units = tripBoss == null ? "kills" : com.onrate.engine.tracking.Tracker.unit(tripBoss);
		long minutes = Math.max(0, (trip.getLastMillis() - trip.getStartMillis()) / 60_000);
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		head.add(smallLabel("THIS TRIP", Theme.TEXT_MUTED), BorderLayout.WEST);
		head.add(smallLabel(minutes >= 60 ? String.format(java.util.Locale.ROOT, "%dh %02dm", minutes / 60, minutes % 60) : minutes + " min",
			ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		card.add(head);
		card.add(spacer(Theme.S));

		// the verdict first, or how far off one is
		double luck = trip.getLuck();
		JLabel headline = new JLabel(Double.isNaN(luck) ? "\u2013" : tripEarlyNow ? "Too early" : Verdict.labelOf(luck));
		headline.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		headline.setForeground(Double.isNaN(luck) || tripEarlyNow ? ColorScheme.LIGHT_GRAY_COLOR : Palette.onRow(luckColor(luck).brighter()));
		headline.setAlignmentX(LEFT_ALIGNMENT);
		card.add(headline);
		String sentence;
		if (Double.isNaN(luck))
		{
			sentence = "Nothing to expect from this boss yet.";
		}
		else if (tripEarlyNow)
		{
			sentence = String.format(java.util.Locale.ROOT, "%s, %s expected. Too little expected so far to tell dry from lucky.",
				trip.getReceived() == 0 ? "No uniques" : trip.getReceived() == 1 ? "1 unique" : trip.getReceived() + " uniques",
				formatExpected(trip.getExpected()));
		}
		else
		{
			sentence = String.format(java.util.Locale.ROOT, "%s, %s expected. %s for a trip like this.",
				trip.getReceived() == 0 ? "No uniques" : trip.getReceived() == 1 ? "1 unique" : trip.getReceived() + " uniques",
				formatExpected(trip.getExpected()),
				capital(luckPercent(trip.getShares(), trip.getReceived())));
		}
		JLabel why = smallLabel(Theme.wrap(html(sentence), Theme.TEXT_WIDTH), Theme.TEXT);
		why.setAlignmentX(LEFT_ALIGNMENT);
		card.add(why);
		card.add(spacer(Theme.S));
		if (tripBar)
		{
			double[] shares = trip.getShares();
			double fill = Double.isNaN(luck) || tripEarlyNow ? 0
				: shares == null ? 0 : Math.min(1.0, trip.getReceived() <= 0 ? 0 : shares[0]);
			card.add(progressBar(fill, Double.isNaN(luck) || tripEarlyNow ? Theme.QUIET_MARK : luckColor(luck)));
			card.add(spacer(Theme.S));
		}

		double perHour = trip.getKillsPerHour();
		String capUnits = Character.toUpperCase(units.charAt(0)) + units.substring(1);
		String killsLabel = Double.isNaN(perHour) ? capUnits : capUnits + " \u00b7 per hour";
		String killsValue = Double.isNaN(perHour) ? String.format(java.util.Locale.ROOT, "%,d", trip.getKills())
			: String.format(java.util.Locale.ROOT, "%,d \u00b7 %.0f/h", trip.getKills(), perHour);
		String uniquesValue = trip.getReceived() + " / " + formatExpected(trip.getExpected());
		if (boxes)
		{
			// the card look: plain label / value lines, like "Unique value" under them
			card.add(cardRow(killsLabel, killsValue, Color.WHITE, null));
			card.add(cardRow("Uniques / expected", uniquesValue, Color.WHITE, null));
		}
		else
		{
			JPanel tiles = new JPanel(new GridLayout(1, 2, 4, 0));
			tiles.setOpaque(false);
			tiles.setAlignmentX(LEFT_ALIGNMENT);
			tiles.add(tile(killsLabel, killsValue));
			tiles.add(tile("Uniques / expected", uniquesValue));
			card.add(tiles);
			card.add(spacer(Theme.S));
		}

		if (!trip.getDrops().isEmpty())
		{
			java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
			for (String d : trip.getDrops())
			{
				counts.merge(d, 1, Integer::sum);
			}
			java.util.List<String> parts = new java.util.ArrayList<>();
			for (java.util.Map.Entry<String, Integer> e : counts.entrySet())
			{
				parts.add(e.getValue() > 1 ? e.getKey() + " \u00d7" + e.getValue() : e.getKey());
			}
			// a long list wraps under itself, right-aligned like the other values
			card.add(cardRow("Drops", "<html><body style='width: " + (Theme.TEXT_WIDTH - 50) + "px'><div align='right'>"
				+ html(String.join(", ", parts)) + "</div></body></html>", Color.WHITE, null));
		}
		// the shared items it also drops, counted in the totals above
		if (!trip.getSharedNames().isEmpty())
		{
			card.add(cardRow("Shared items", String.format(java.util.Locale.ROOT, "%d got \u00b7 %s expected", trip.getSharedReceived(),
				formatExpected(trip.getSharedExpected())), Color.WHITE,
				"<html><body style='width: " + Theme.TIP_WIDE + "px'>Items other bosses drop too (" + html(String.join(", ", trip.getSharedNames()))
				+ "). They can drop here, so the trip counts them.</body></html>"));
		}

		// value per hour: only once there are enough kills for it to mean something
		double tripValue = options.get().isGeValue() ? tracker.tripValue(priceOf) : Double.NaN;
		double perKill = !options.get().isGeValue() ? 0
			: !Double.isNaN(tripValue) && trip.getKills() > 0 ? tripValue / trip.getKills() : uniqueValuePerKill(bossName);
		if (!Double.isNaN(perHour)
			&& (perKill > 0 || !Double.isNaN(tripValue) && uniqueValuePerKill(bossName) > 0))
		{
			String worthTip = String.format(java.util.Locale.ROOT, "<html><body style='width: " + Theme.TIP_WIDE + "px'>About %s per %s at this trip's speed. "
				+ "Only uniques count (not bones, hides or other loot), so this isn't your full GP per hour. "
				+ "Pets and most jars count as 0 GP.%s</body></html>",
				Rates.gp(perKill), units.substring(0, units.length() - 1), mainValueNote(bossName));
			card.add(cardRow("Unique value", String.format(java.util.Locale.ROOT, "~%s/h expected", Rates.gp(perKill * perHour)),
				Color.WHITE, worthTip));
		}

		// how long since the last kill, and when the trip ends by itself
		long idleMinutes = Math.max(0, (System.currentTimeMillis() - trip.getLastMillis()) / 60_000);
		long limitMinutes = tracker.tripIdleLimitMillis() / 60_000;
		String tripUnit = com.onrate.engine.tracking.Tracker.units(tripBoss, 1);
		String idle = Texts.tripIdle(idleMinutes, limitMinutes, tripUnit);
		card.add(spacer(Theme.S));
		JPanel foot = new JPanel(new BorderLayout());
		foot.setOpaque(false);
		foot.setAlignmentX(LEFT_ALIGNMENT);
		foot.add(smallLabel(idle, Theme.TEXT_MUTED), BorderLayout.WEST);
		JButton reset = new JButton("New trip");
		reset.setFont(FontManager.getRunescapeSmallFont());
		reset.setFocusPainted(false);
		reset.setBorderPainted(false);
		reset.setContentAreaFilled(false);
		reset.setForeground(Theme.TEXT_MUTED);
		reset.setToolTipText(Texts.tripResetTip(tripUnit));
		reset.addActionListener(e ->
		{
			tracker.resetTrip();
			refresh();
		});
		foot.add(reset, BorderLayout.EAST);
		card.add(foot);
		// the overlay is not shown at raids, the Inferno or the Fight Caves, so it is not offered there
		if ((tripBoss == null || !Tracker.isLongRun(tripBoss))
			&& (trip.getKills() >= OVERLAY_OFFER_KILLS || minutes >= OVERLAY_OFFER_MINUTES))
		{
			addOverlayOffer(card);
		}
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		rowsPanel.add(card);
		rowsPanel.add(spacer(Theme.M));
	}

	/** A thin bar filled to {@code fraction} (0 to 1), for the trip card. */
	private static JPanel progressBar(double fraction, Color color)
	{
		JPanel bar = new JPanel()
		{
			@Override
			protected void paintComponent(java.awt.Graphics g)
			{
				super.paintComponent(g);
				g.setColor(color);
				g.fillRect(0, 0, (int) Math.round(getWidth() * Math.max(0, Math.min(1, fraction))), getHeight());
			}
		};
		bar.setBackground(Theme.TRACK);
		bar.setPreferredSize(new Dimension(10, 6));
		bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
		bar.setAlignmentX(LEFT_ALIGNMENT);
		return bar;
	}

	/** The account overview's "Last unique 12 days ago: Vorkath's head." line, from History. */
	private void showLastUnique()
	{
		lastUniqueText = null;
		com.onrate.engine.tracking.Tracker.LastUnique last = tracker.lastUnique();
		if (last == null)
		{
			return;
		}
		java.time.ZoneId zone = java.time.ZoneId.systemDefault();
		java.time.LocalDate today = java.time.LocalDate.now(zone);
		String text;
		if (last.item != null)
		{
			long days = java.time.temporal.ChronoUnit.DAYS.between(
				java.time.Instant.ofEpochMilli(last.time).atZone(zone).toLocalDate(), today);
			String when = days <= 0 ? "today" : days == 1 ? "yesterday" : String.format(java.util.Locale.ROOT, "%,d days ago", days);
			text = String.format(java.util.Locale.ROOT, "Last unique %s: %s (%s).", when, last.item, last.boss);
		}
		else
		{
			text = "No unique since " + java.time.Instant.ofEpochMilli(last.historyStart).atZone(zone).toLocalDate()
				.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + ".";
		}
		lastUniqueText = text;
		lastUniqueLabel.setText(Theme.wrap(html(text), Theme.TEXT_WIDTH));
		lastUniqueLabel.setToolTipText("From History: the drops On Rate saw since you installed it. Items you left out don't count.");
		lastUniqueLabel.setVisible(true);
	}

	/** The overlay offer line at the bottom of a long trip's card, when it's due (see {@link #setOverlayOffer}). */
	private void addOverlayOffer(JPanel card)
	{
		if (overlayOn.getAsBoolean() || overlayOfferSeen.getAsBoolean())
		{
			return;
		}
		JLabel offer = smallLabel(Theme.wrap("Long trip? The in-game overlay shows this on screen. Click to turn it on.",
			Theme.TEXT_WIDTH - 30), Theme.TEXT_MUTED);
		offer.setIcon(Theme.infoIcon());
		offer.setVerticalTextPosition(SwingConstants.TOP);
		offer.setAlignmentX(LEFT_ALIGNMENT);
		offer.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		offer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		offer.setToolTipText("Turns on In-game overlay in the settings (Display). This note shows only once.");
		offer.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				overlayOfferOnScreen = false;
				offer.setVisible(false);
				markOverlayOfferSeen.run();
				turnOnOverlay.run();
			}
		});
		card.add(offer);
		overlayOfferLabel = offer;
		overlayOfferOnScreen = true;
	}

	/** "<br><br>Most of it is the Crystal tool seed (95%)." for the value hover, or "" when it's spread out. */
	private String mainValueNote(String bossName)
	{
		BossStats stats = tracker.stats(bossName, raidSettings.get());
		if (stats == null || stats.getKillCount() <= 0)
		{
			return "";
		}
		List<String> names = new ArrayList<>();
		List<Double> values = new ArrayList<>();
		for (int i = 0; i < stats.getDrops().size(); i++)
		{
			DropDefinition drop = stats.getDrops().get(i);
			if (tracker.counts(stats.getBoss(), drop))
			{
				names.add(stats.getRows().get(i).getLabel());
				values.add(Math.max(0, priceOf.applyAsLong(drop.getName())) * stats.getRows().get(i).getExpected());
			}
		}
		String note = Texts.mainValueSource(names, values);
		return note == null ? "" : "<br><br>" + note;
	}

	/** GP the uniques of one kill are worth on average: each tradeable unique's rate times its price. */
	private double uniqueValuePerKill(String bossName)
	{
		BossStats stats = tracker.stats(bossName, raidSettings.get());
		if (stats == null || stats.getKillCount() <= 0)
		{
			return 0;
		}
		double value = 0;
		for (int i = 0; i < stats.getDrops().size(); i++)
		{
			DropDefinition drop = stats.getDrops().get(i);
			if (!tracker.counts(stats.getBoss(), drop))
			{
				continue;
			}
			long price = Math.max(0, priceOf.applyAsLong(drop.getName()));
			// the average per kill over your kills, so team share and raid points are included
			value += price * stats.getRows().get(i).getExpected() / stats.getKillCount();
		}
		return value;
	}

	/**
	 * A grey label on the left and its value on the right, with a thin line above: the same rows
	 * as in the hovers ({@link Tip#row}). For the trip card.
	 */
	private static JPanel cardRow(String label, String value, Color valueColor, String tip)
	{
		JPanel row = new JPanel(new BorderLayout(Theme.M, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.HAIR, 0, Theme.HAIR, 0)));
		JLabel name = smallLabel(label, Theme.TEXT_MUTED);
		name.setVerticalAlignment(SwingConstants.TOP);
		row.add(name, BorderLayout.WEST);
		JLabel shown = smallLabel(value, valueColor);
		shown.setHorizontalAlignment(SwingConstants.RIGHT);
		shown.setVerticalAlignment(SwingConstants.TOP);
		row.add(shown, BorderLayout.CENTER);
		for (javax.swing.JComponent c : new javax.swing.JComponent[]{row, name, shown})
		{
			c.setToolTipText(tip);
		}
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return Card.row(row);
	}

	private static JPanel tile(String title, String value)
	{
		JPanel t = new JPanel(new BorderLayout());
		t.setBackground(Theme.SURFACE_RAISED);
		t.setBorder(new EmptyBorder(Theme.HAIR, Theme.HAIR, Theme.S, Theme.HAIR));
		JLabel a = smallLabel(title, ColorScheme.LIGHT_GRAY_COLOR);
		a.setHorizontalAlignment(SwingConstants.CENTER);
		JLabel b = new JLabel(value);
		b.setFont(FontManager.getRunescapeBoldFont());
		b.setForeground(Color.WHITE);
		b.setHorizontalAlignment(SwingConstants.CENTER);
		t.add(a, BorderLayout.NORTH);
		t.add(b, BorderLayout.CENTER);
		return t;
	}

	private void buildWelcome()
	{
		welcomePanel.setLayout(new BoxLayout(welcomePanel, BoxLayout.Y_AXIS));
		welcomePanel.setAlignmentX(LEFT_ALIGNMENT);
		welcomePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		welcomePanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.OUTLINE),
			new EmptyBorder(Theme.M, Theme.M, Theme.M, Theme.M)));

		JLabel title = new JLabel("Are you on rate?");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Theme.TEXT);
		title.setAlignmentX(LEFT_ALIGNMENT);
		welcomePanel.add(title);
		JLabel intro = smallLabel(Theme.wrap("On Rate compares the uniques you got with what a typical player "
			+ "would have by now, at the same drop rates and kill counts.", 160), Theme.TEXT_MUTED);
		intro.setAlignmentX(LEFT_ALIGNMENT);
		intro.setBorder(new EmptyBorder(Theme.HAIR, 0, Theme.M, 0));
		welcomePanel.add(intro);

		// numbered steps, each a row with a thin line above it, like the rows in the hovers
		String[][] steps = {
			{"Open your collection log", "and click the page of each boss you do. That loads your kill count and past drops."},
			{"Keep Loot Tracker on.", "On Rate reads new drops from it."},
			{"Go kill something.", "The panel follows your kills."},
		};
		for (int i = 0; i < steps.length; i++)
		{
			JLabel step = smallLabel(Theme.wrap("<font color='" + Theme.hex(Theme.TEXT) + "'>" + steps[i][0] + "</font> "
				+ steps[i][1], 135), Theme.TEXT_MUTED);
			step.setIcon(Theme.stepIcon(i + 1));
			step.setIconTextGap(Theme.M);
			step.setVerticalAlignment(SwingConstants.TOP);
			step.setVerticalTextPosition(SwingConstants.TOP);
			step.setAlignmentX(LEFT_ALIGNMENT);
			step.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.S + 1, 0, Theme.S + 1, 0)));
			welcomePanel.add(step);
		}

		// how far the collection log pages are, with the next one to open
		JPanel progress = new JPanel();
		progress.setLayout(new BoxLayout(progress, BoxLayout.Y_AXIS));
		progress.setOpaque(false);
		progress.setAlignmentX(LEFT_ALIGNMENT);
		progress.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.S + 1, 0, Theme.S, 0)));
		welcomeProgress.setFont(FontManager.getRunescapeSmallFont());
		welcomeProgress.setForeground(Theme.TEXT_MUTED);
		welcomeProgress.setAlignmentX(LEFT_ALIGNMENT);
		welcomeProgressBar.setAlignmentX(LEFT_ALIGNMENT);
		progress.add(welcomeProgress);
		progress.add(welcomeProgressBar);
		progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, progress.getPreferredSize().height));
		progress.setVisible(false);
		welcomeProgressPanel = progress;
		welcomePanel.add(progress);

		JButton gotIt = new JButton("Hide guide");
		gotIt.setFont(FontManager.getRunescapeSmallFont());
		gotIt.setFocusPainted(false);
		gotIt.setToolTipText(Theme.wrap(Texts.HIDE_GUIDE_TIP, Theme.TIP_WIDE));
		gotIt.addActionListener(e ->
		{
			dismissWelcome.run();
			welcomePanel.setVisible(false);
			revalidate();
			repaint();
		});
		JPanel buttonRow = new JPanel(new BorderLayout());
		buttonRow.setOpaque(false);
		buttonRow.setAlignmentX(LEFT_ALIGNMENT);
		buttonRow.setBorder(new EmptyBorder(Theme.S, 0, Theme.S, 0));
		buttonRow.add(gotIt, BorderLayout.WEST);
		buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonRow.getPreferredSize().height));
		welcomePanel.add(buttonRow);

		// the reference part, folded: how to read the words, what they don't mean, and odd moments
		JLabel toggle = smallLabel("", Theme.TEXT_MUTED);
		toggle.setAlignmentX(LEFT_ALIGNMENT);
		toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		toggle.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.S + 1, 0, Theme.S, 0)));
		Runnable flip = () ->
		{
			welcomeMoreOpen = !welcomeMoreOpen;
			styleWelcomeToggle(toggle);
			welcomeMore.setVisible(welcomeMoreOpen);
			welcomePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, welcomePanel.getPreferredSize().height));
			revalidate();
			repaint();
		};
		toggle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				flip.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				toggle.setForeground(Theme.TEXT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				toggle.setForeground(Theme.TEXT_MUTED);
			}
		});
		styleWelcomeToggle(toggle);
		Activatable.install(toggle, "How to read it", flip);
		welcomePanel.add(toggle);

		welcomeMore.setLayout(new BoxLayout(welcomeMore, BoxLayout.Y_AXIS));
		welcomeMore.setOpaque(false);
		welcomeMore.setAlignmentX(LEFT_ALIGNMENT);
		// how the verdict words are drawn: your rank among 100 typical players
		welcomeMore.add(welcomeHeading("Where the words come from",
			"Your rank among 100 typical players with your kill counts, driest first."));
		String[] ranks = {"1", "2 to 10", "11 to 35", "36 to 45", "46 to 55", "56 to 65", "66 to 90", "91 to 99", "100"};
		welcomeVerdicts.clear();
		for (int i = 0; i < ranks.length; i++)
		{
			JPanel row = new JPanel(new BorderLayout(Theme.M, 0));
			row.setOpaque(false);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.HAIR, 0, Theme.HAIR, 0)));
			JLabel word = smallLabel(Verdict.labelOf(WELCOME_VERDICT_LUCK[i]), welcomeVerdictColor(i));
			welcomeVerdicts.add(word);
			row.add(word, BorderLayout.WEST);
			JLabel range = smallLabel(ranks[i], Theme.TEXT);
			range.setHorizontalAlignment(SwingConstants.RIGHT);
			row.add(range, BorderLayout.CENTER);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			welcomeMore.add(row);
		}

		// what the numbers can't tell you
		welcomeMore.add(welcomeHeading("What this does not mean", null));
		String[] limits = {
			"\"Players\" is the drop-rate maths, not real accounts. On Rate has no player data.",
			"Every kill is its own roll. Being dry doesn't make a drop more likely next, and being spooned "
				+ "doesn't make one less likely.",
			"Rates are solo base rates without diary, Combat Achievement or Slayer boosts, so you may look "
				+ "drier than a boosted rate would say.",
		};
		for (String limit : limits)
		{
			welcomeMore.add(welcomeLine(null, limit));
		}

		// what the panel shows in less common moments, so none of it comes as a surprise
		welcomeMore.add(welcomeHeading("Things you may see", null));
		String[][] moments = {
			{"\"Not synced\"", "on a boss: On Rate has its kill count but not your drops from before. Open its log "
				+ "page once."},
			{"Team bosses:", "Royal Titans and older Yama kills count as duo. Change that under Team bosses in the settings."},
			{"Drops from before:", "right-click an item you have and choose \"I got this at kill...\" to add it. "
				+ "Items > Single drops then shows how lucky that drop was."},
			{"Left out:", "right-click an item to leave it out of the calculations (a drop you got elsewhere). "
				+ "Right-click again to count it."},
			{"Shared items:", "items several bosses drop are judged on all of them together."},
		};
		for (String[] m : moments)
		{
			welcomeMore.add(welcomeLine(m[0], m[1]));
		}
		JLabel help = smallLabel("Click the ? for help.", Theme.TEXT_MUTED);
		help.setAlignmentX(LEFT_ALIGNMENT);
		help.setMaximumSize(new Dimension(Integer.MAX_VALUE, help.getPreferredSize().height));
		help.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.S + 1, 0, Theme.S, 0)));
		welcomeMore.add(help);
		welcomeMore.setVisible(welcomeMoreOpen);
		welcomePanel.add(welcomeMore);
		// the guide's height is that of its folded part; it grows when the fold opens (see the toggle)
		welcomePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, welcomePanel.getPreferredSize().height));
	}

	private void styleWelcomeToggle(JLabel toggle)
	{
		toggle.setText((welcomeMoreOpen ? "\u25be " : "\u25b8 ") + "How to read it");
	}

	/** The progress block inside the guide, shown while collection log pages are missing. */
	private JPanel welcomeProgressPanel;

	/** The colour of the welcome key's verdict word {@code i}, as the boss page shows it. */
	private static Color welcomeVerdictColor(int i)
	{
		double luck = WELCOME_VERDICT_LUCK[i];
		return Verdict.isOnRate(luck) ? Theme.TEXT : Palette.onRow(Palette.verdict(luck).brighter());
	}

	/** A wrapped grey line in the welcome guide with a thin line above, starting with an optional white lead. */
	private static JLabel welcomeLine(String lead, String text)
	{
		String html = lead == null ? text : "<font color='" + Theme.hex(Theme.TEXT) + "'>" + lead + "</font> " + text;
		JLabel line = smallLabel(Theme.wrap(html, 160), Theme.TEXT_MUTED);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.DIVIDER), new EmptyBorder(Theme.S, 0, Theme.S, 0)));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
		return line;
	}

	/** A section title in the welcome guide, with an optional grey line under it. */
	private static JPanel welcomeHeading(String title, String sub)
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setBorder(new EmptyBorder(Theme.M + Theme.S, 0, Theme.S, 0));
		JLabel t = new JLabel(title);
		t.setFont(FontManager.getRunescapeBoldFont());
		t.setForeground(Theme.TEXT);
		t.setAlignmentX(LEFT_ALIGNMENT);
		p.add(t);
		if (sub != null)
		{
			JLabel s = smallLabel(Theme.wrap(sub, 160), Theme.TEXT_MUTED);
			s.setAlignmentX(LEFT_ALIGNMENT);
			p.add(s);
		}
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	/** "3 of N pages opened · next: Vorkath" in the guide; gone once every page is loaded. */
	private void updateWelcomeProgress(int total, int done, List<SyncStatus> missing)
	{
		if (welcomeProgressPanel == null)
		{
			return;
		}
		boolean show = total > 0 && !missing.isEmpty();
		if (show)
		{
			// a page of a boss you killed is the next one to open, else the first missing
			SyncStatus next = missing.get(0);
			for (SyncStatus s : missing)
			{
				if (s.isKilled())
				{
					next = s;
					break;
				}
			}
			welcomeProgress.setText(String.format(java.util.Locale.ROOT, "<html><font color='%s'>%d of %d</font> pages " + (setupWording ? "opened" : "loaded") + " · next: %s</html>",
				Theme.hex(Theme.TEXT), done, total, Texts.html(next.getPage())));
			welcomeProgressBar.set(done / (double) total, -1, Theme.PROGRESS);
		}
		if (welcomeProgressPanel.isVisible() != show)
		{
			welcomeProgressPanel.setVisible(show);
			welcomePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, welcomePanel.getPreferredSize().height));
		}
	}

	/** Header (see {@link Texts#setupHeader}) plus, when expanded, the pages still to open. */
	private void refreshSetup()
	{
		List<SyncStatus> checklist = tracker.syncChecklist();
		List<SyncStatus> missing = new ArrayList<>();
		for (SyncStatus s : checklist)
		{
			if (!s.isSynced())
			{
				missing.add(s);
			}
		}
		int done = checklist.size() - missing.size();
		updateWelcomeProgress(checklist.size(), done, missing);

		String problem = Texts.setupProblem(tracker.unreadablePages(), tracker.isHeaderUnreadable());
		setupProblem.setText(problem == null ? "" : Theme.wrap(problem, Theme.TEXT_WIDTH - 14));
		setupProblem.setVisible(problem != null);
		lootTrackerOffLabel.setVisible(lootTrackerOff);

		setupList.removeAll();
		if (!profileLoaded())
		{
			// nothing is loaded yet, so there is nothing to load either: no checklist before login
			setupProblem.setVisible(false);
			setupHeader.setVisible(false);
			setupGap.setVisible(false);
			setupList.setVisible(false);
			return;
		}
		// all loaded: nothing to say, so the line is gone; it only shows when something is missing
		boolean show = !missing.isEmpty();
		setupHeader.setVisible(show);
		setupGap.setVisible(show);
		if (!show)
		{
			setupList.setVisible(false);
			return;
		}

		// Pages of bosses you killed come first; the rest only matter for bosses you do.
		missing.sort((a, b) -> Boolean.compare(b.isKilled(), a.isKilled()));
		// a boss can need two pages (Doom: its log page and the scoreboard): count bosses, not pages
		long killed = missing.stream().filter(SyncStatus::isKilled).flatMap(st -> st.getBosses().stream()).distinct().count();
		String status;
		Color statusColor = Theme.TEXT;
		if (killed == 0 && done > 0)
		{
			statusColor = ColorScheme.LIGHT_GRAY_COLOR;
		}
		if (setupWording)
		{
			status = Texts.setupHeader((int) killed, done, checklist.size());
			if (status == null)
			{
				// something is missing, so the counts cannot be zero; never show "null"
				setupHeader.setVisible(false);
				setupGap.setVisible(false);
				setupList.setVisible(false);
				return;
			}
		}
		else if (killed > 0)
		{
			status = String.format(java.util.Locale.ROOT, "Load %d boss%s you killed", killed, killed == 1 ? "" : "es");
		}
		else if (done == 0)
		{
			status = "Load your bosses from the log";
		}
		else
		{
			status = String.format(java.util.Locale.ROOT, "Collection log: %d page%s loaded", done, done == 1 ? "" : "s");
		}
		setupHeader.setText((setupExpanded ? "\u25be " : "\u25b8 ") + status + (setupExpanded ? "" : " (show)"));
		setupHeader.setForeground(statusColor);
		setupHeader.setToolTipText("<html><body style='width: " + Theme.TIP_WIDE + "px'>" + (setupWording ? "Opening a page loads" : "Loads")
			+ " your kill count and past drops. You don't need all " + checklist.size() + ".</body></html>");
		setupHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setupList.setVisible(setupExpanded);
		if (!setupExpanded)
		{
			return;
		}
		JLabel intro = new JLabel("<html><body style='width: " + Theme.TEXT_WIDTH + "px'>Open these in your collection log. "
			+ "Only the bosses you do matter.</body></html>");
		intro.setFont(FontManager.getRunescapeSmallFont());
		intro.setForeground(Theme.TEXT_MUTED);
		setupList.add(intro);
		boolean othersTitle = false;
		for (SyncStatus s : missing)
		{
			if (killed > 0 && !s.isKilled() && !othersTitle)
			{
				JLabel others = new JLabel("Other bosses:");
				others.setFont(FontManager.getRunescapeSmallFont());
				others.setForeground(Theme.TEXT_MUTED);
				others.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
				setupList.add(others);
				othersTitle = true;
			}
			JLabel item = checklistItem(s);
			if (s.isKilled())
			{
				item.setForeground(Color.WHITE);
			}
			setupList.add(item);
		}
		setupList.setMaximumSize(new Dimension(Integer.MAX_VALUE, setupList.getPreferredSize().height));
	}

	private JLabel checklistItem(SyncStatus status)
	{
		JLabel label = new JLabel(status.getPage());
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.setBorder(new EmptyBorder(0, 0, 0, 0));
		String where = Tracker.DELVE_SCOREBOARD.equals(status.getPage())
			? "Open the Doom scoreboard in game"
			: "Open this page in your collection log";
		label.setToolTipText("<html>" + where + "<br>Loads: " + String.join(", ", status.getBosses())
			+ "<br>Click to show it here</html>");
		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showBoss(status.getBosses().get(0));
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		return Activatable.install(label, status.getPage() + ", show it here", () -> showBoss(status.getBosses().get(0)));
	}

	private JPanel row(RowStats row, BossStats stats, String unit)
	{
		return buildRow(row, itemTooltip(row, stats, unit), null);
	}

	/**
	 * While the account overview's boss list is drawn: its rows share one Tab stop (Up and Down move
	 * between them). Null elsewhere. Only touched on the Swing thread.
	 */
	private static Activatable.Group rowGroup;

	private JPanel buildRow(RowStats row, String tip, Runnable onClick)
	{
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(Theme.HAIR, Theme.HAIR, Theme.HAIR, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (row.isAny())
		{
			icon.setText(row.getLabel());
			icon.setFont(FontManager.getRunescapeBoldFont());
			icon.setForeground(Theme.TEXT_MUTED);
		}
		else
		{
			int id = itemIdFor.applyAsInt(row.getIconItem());
			if (id > 0)
			{
				itemManager.getImage(id).addTo(icon);
			}
			else
			{
				icon.setText(initials(row.getLabel()));
				icon.setFont(FontManager.getRunescapeSmallFont());
				icon.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		}

		// Expected tab works like the Doom plugin: the number on the right is how many you
		// should have by now, and the bar is progress towards the next one.
		// Received tab: the number is how many you got, and the bar is your luck.
		DropBar bar = new DropBar();
		double luck = row.getLuck();
		// In the account overview each row is a boss: show its name inside the bar.
		String name = onClick != null ? row.getShownLabel() : null;
		int number;
		if (tab == Tab.EXPECTED)
		{
			double expected = row.getExpected();
			number = (int) Math.floor(expected);
			double progress = expected - number;
			if (name != null)
			{
				bar.set(progress, Theme.PROGRESS, name, String.format(java.util.Locale.ROOT, "%.0f%%", progress * 100));
			}
			else
			{
				bar.set(progress, Theme.PROGRESS, String.format(java.util.Locale.ROOT, "%.1f%%", progress * 100));
			}
		}
		else
		{
			number = row.getReceived();
			if (name != null)
			{
				bar.set(luck, luckColor(luck), name, Double.isNaN(luck) ? "-" : Verdict.labelOf(luck));
			}
			else
			{
				String text = Double.isNaN(luck) ? "no kills yet" : capital(luckShort(row.getShares(), row.getReceived()));
				bar.set(luck, luckColor(luck), text);
			}
		}

		JLabel count = new JLabel(String.valueOf(number));
		count.setFont(FontManager.getRunescapeBoldFont());
		count.setForeground(number > 0 ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		count.setHorizontalAlignment(SwingConstants.RIGHT);
		count.setPreferredSize(new Dimension(30, 32));

		panel.add(icon, BorderLayout.WEST);
		panel.add(bar, BorderLayout.CENTER);
		panel.add(count, BorderLayout.EAST);

		if (onClick != null)
		{
			panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			MouseAdapter click = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}
			};
			for (java.awt.Component c : new java.awt.Component[]{panel, icon, bar, count})
			{
				c.addMouseListener(click);
			}
		}
		panel.setToolTipText(tip);
		bar.setToolTipText(tip);
		icon.setToolTipText(tip);
		count.setToolTipText(tip);
		if (onClick != null)
		{
			Activatable.install(panel, row.getShownLabel() + ", open", onClick, rowGroup);
		}
		else if (itemNames && !row.isAny())
		{
			nameRow(panel, row.getShownLabel(), tip);
		}
		return Card.row(panel);
	}

	/**
	 * Makes an item row reachable by keyboard: it can take the focus, its accessible name is the item's name (its
	 * description the tooltip as plain text), and the tooltip shows while it has the focus.
	 */
	private static void nameRow(JPanel panel, String name, String tip)
	{
		panel.setFocusable(true);
		panel.getAccessibleContext().setAccessibleName(name);
		if (tip != null)
		{
			panel.getAccessibleContext().setAccessibleDescription(tip.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim());
		}
		panel.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusGained(java.awt.event.FocusEvent e)
			{
				if (panel.isShowing())
				{
					javax.swing.ToolTipManager.sharedInstance().mouseMoved(new MouseEvent(panel, MouseEvent.MOUSE_MOVED,
						System.currentTimeMillis(), 0, panel.getWidth() / 2, panel.getHeight() / 2, 0, false));
				}
			}

			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				javax.swing.ToolTipManager.sharedInstance().mouseExited(new MouseEvent(panel, MouseEvent.MOUSE_EXITED,
					System.currentTimeMillis(), 0, 0, 0, 0, false));
			}
		});
	}

	/**
	 * "You got 1 where 0.19 was expected. You're luckier than 83% of players.": the got / expected
	 * and the comparison as one sentence at the top of an item's tooltip.
	 */
	static String summary(RowStats row)
	{
		String text = String.format(java.util.Locale.ROOT, "You got <b>%d</b> where %s was expected.", row.getReceived(),
			formatExpected(row.getExpected()));
		if (Double.isNaN(row.getLuck()))
		{
			return text;
		}
		String compared = luckPercent(row.getShares(), row.getReceived());
		if (compared.startsWith("drier") || compared.startsWith("luckier"))
		{
			// "luckier than 99.4% (1 in 170 players)" already says who it's compared with
			// the percentage hovers get the tile wording too ("1 in 3 as dry"): the same fact from the other side
			return text + " You're " + compared
				+ (compared.contains("players") ? "." : " of players" + Texts.luckShortNote(row.getShares(), row.getReceived()) + ".");
		}
		if (compared.equals("about average"))
		{
			return text + " That's about average.";
		}
		// "64% have none" is in the split of players below
		return text;
	}

	private static String tooltip(RowStats row, BossStats stats, String unit)
	{
		return tooltip(row, stats, unit, java.util.Collections.emptyList());
	}

	/** Every item's average time, by boss and item, for the hovers: worked out once per redraw. */
	private java.util.Map<String, com.onrate.engine.stats.ItemTime> timeIndex;

	/** The account worked out off the Swing thread for the draw a kill started, or null. */
	private com.onrate.service.AccountPrecompute precomputed;

	/**
	 * The player just changed the data here: numbers worked out in the background before that are
	 * out of date, and must not be drawn over the redraw that follows (see AccountPrecompute#supersede).
	 */
	private void dropPendingNumbers()
	{
		if (precomputed != null)
		{
			precomputed.supersedeKeepingMove();
		}
	}

	/** Lets the plugin hand over numbers it worked out on a worker thread (see AccountPrecompute). */
	public void setPrecomputed(com.onrate.service.AccountPrecompute precomputed)
	{
		this.precomputed = precomputed;
	}

	/**
	 * How long one copy of this item takes on average (your own times where you have enough, else the
	 * wiki's rate), or null when the boss has no time yet. Used by the item hovers everywhere.
	 */
	private com.onrate.engine.stats.ItemTime timeOf(String boss, String iconItem)
	{
		if (boss == null || iconItem == null)
		{
			return null;
		}
		try
		{
			if (timeIndex == null)
			{
				java.util.Map<String, com.onrate.engine.stats.ItemTime> index = new java.util.HashMap<>();
				List<com.onrate.engine.stats.ItemTime> all = precomputed == null ? null : precomputed.itemTimes();
				if (all == null)
				{
					all = com.onrate.engine.stats.ItemTimes.compute(tracker, com.onrate.engine.data.KillTimes.bundled(), raidSettings.get());
				}
				for (com.onrate.engine.stats.ItemTime t : all)
				{
					index.put(t.getBoss() + "|" + com.onrate.engine.data.BossCatalog.normalize(t.getIconItem()), t);
				}
				timeIndex = index;
			}
			return timeIndex.get(boss + "|" + com.onrate.engine.data.BossCatalog.normalize(iconItem));
		}
		catch (RuntimeException e)
		{
			// a hover must never break the page
			return null;
		}
	}

	/** The hover rows for an item's time: how long one takes on average, and where that comes from. */
	private static void addTimeRows(Tip tip, com.onrate.engine.stats.ItemTime t)
	{
		addTimeRows(tip, t, "Time to get one");
	}

	private static void addTimeRows(Tip tip, com.onrate.engine.stats.ItemTime t, String label)
	{
		if (t == null || Double.isNaN(t.getSecondsPerDrop()) || Double.isInfinite(t.getSecondsPerDrop()) || t.getSecondsPerDrop() <= 0)
		{
			return;
		}
		tip.row(label, (t.isEstimated() ? "~" : "") + com.onrate.engine.stats.ItemTime.formatLong(t.getSecondsPerDrop()))
			.row("Time based on", sourceName(t.getSourceTag()));
	}

	/** The item hover with how lucky your drops of it were ({where, luck} per drop) under the verdict. */
	private String itemTooltip(RowStats row, BossStats stats, String unit)
	{
		List<String[]> luck = new ArrayList<>();
		if (!row.isAny() && row.getIconItem() != null)
		{
			String bossName = stats.getBoss().getName();
			BossDefinition boss = stats.getBoss();
			List<com.onrate.engine.tracking.RecentDrop> drops = tracker.dropsOf(bossName, row.getIconItem());
			for (int i = 0; i < Math.min(3, drops.size()); i++)
			{
				com.onrate.engine.tracking.RecentDrop r = drops.get(i);
				luck.add(new String[]{killText(boss, r.getKill()), dropLuck(r.getDrop())});
			}
			if (drops.size() > 3)
			{
				luck.add(new String[]{"+ " + (drops.size() - 3) + " more", ""});
			}
		}
		return tooltip(row, stats, unit, luck, row.isAny() ? anyUniqueTime(stats) : timeOf(stats.getBoss().getName(), row.getIconItem()),
			raidItemRows(stats.getBoss(), row));
	}

	/** Setups an item hover shows at most. */
	static final int ITEM_SETUPS_SHOWN = 3;

	/**
	 * A raid item hover: its chance a raid and an hour at your own setups, from the raids On Rate saw
	 * ({setup, chance} per row), the most an hour first. Empty for other bosses and items.
	 */
	private List<String[]> raidItemRows(BossDefinition boss, RowStats row)
	{
		List<String[]> rows = new ArrayList<>();
		DropDefinition drop = boss == null || boss.getRaid() == null || row.isAny() ? null : findDrop(boss.getDrops(), row.getLabel());
		if (drop == null)
		{
			return rows;
		}
		try
		{
			List<RaidInsights.Setup> setups = new ArrayList<>(raidInsights(boss.getName(), false).getSetups());
			setups.removeIf(s -> Double.isNaN(s.getItemPerRaid(drop.getName())) || s.getItemPerRaid(drop.getName()) <= 0);
			setups.sort(java.util.Comparator.comparing((RaidInsights.Setup s) -> !s.isEnough())
				.thenComparing(s -> s.isEnough() ? -s.getItemPerHour(drop.getName()) : -s.getItemPerRaid(drop.getName())));
			for (RaidInsights.Setup s : setups.subList(0, Math.min(ITEM_SETUPS_SHOWN, setups.size())))
			{
				rows.add(new String[]{setupName(boss, s), Texts.itemSetupText(s.getItemPerRaid(drop.getName()),
					s.isEnough() ? s.getItemPerHour(drop.getName()) : Double.NaN)});
			}
		}
		catch (RuntimeException e)
		{
			// a hover must never break the page
			rows.clear();
		}
		return rows;
	}

	/**
	 * How long a unique of any kind takes at this boss, as an item time: the boss's time per kill over the
	 * uniques you get per kill (what your kills really gave; the drop rates when you have no kills yet).
	 * Null when the boss has no time yet.
	 */
	private com.onrate.engine.stats.ItemTime anyUniqueTime(BossStats stats)
	{
		String boss = stats.getBoss().getName();
		try
		{
			timeOf(boss, "");
			com.onrate.engine.stats.ItemTime base = null;
			double chances = 0;
			for (java.util.Map.Entry<String, com.onrate.engine.stats.ItemTime> e : timeIndex.entrySet())
			{
				com.onrate.engine.stats.ItemTime t = e.getValue();
				if (boss.equals(t.getBoss()) && !t.isPet())
				{
					base = base == null ? t : base;
					chances += t.getChancePerKill();
				}
			}
			if (base == null)
			{
				return null;
			}
			int kills = stats.getKillCount();
			double perKill = kills > 0 && stats.getAnyRow().getExpected() > 0 ? stats.getAnyRow().getExpected() / kills : chances;
			return perKill > 0 ? base.forAnyUnique(perKill) : null;
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	private static String tooltip(RowStats row, BossStats stats, String unit, List<String[]> dropLuck)
	{
		return tooltip(row, stats, unit, dropLuck, null);
	}

	private static String tooltip(RowStats row, BossStats stats, String unit, List<String[]> dropLuck,
		com.onrate.engine.stats.ItemTime time)
	{
		return tooltip(row, stats, unit, dropLuck, time, java.util.Collections.emptyList());
	}

	private static String tooltip(RowStats row, BossStats stats, String unit, List<String[]> dropLuck,
		com.onrate.engine.stats.ItemTime time, List<String[]> setupRows)
	{
		Tip tip = new Tip(row.isAny() ? "Any unique" : row.getLabel());
		tip.line(stats.getBoss().getName(), Theme.TEXT_MUTED);
		DropDefinition drop = row.isAny() ? null : findDrop(stats.getDrops(), row.getLabel());
		int kills = stats.getKillCount();
		String units = kills == 1 ? unit : unit + "s";

		// one plain sentence first, then the verdict and the split of players it comes from
		double luck = row.getLuck();
		tip.line(summary(row));
		if (!Double.isNaN(luck))
		{
			tip.row("Your result", "<b>" + Verdict.labelOf(luck) + "</b>", Palette.onRow(luckColor(luck).brighter()));
			// the players at your kill count: fewer, as many as you (in your colour), more
			tip.split(row.getShares(), row.getReceived(), luckColor(luck));
			tip.line(capital(sharesLine(row.getShares(), row.getReceived())), Theme.TEXT_MUTED);
		}

		if (!dropLuck.isEmpty())
		{
			// each drop on its own: how long it took against the drop rate, luckiest first
			tip.line("How lucky you were when you got it", Theme.TEXT_MUTED);
			for (String[] d : dropLuck)
			{
				if (d[1].isEmpty())
				{
					tip.line(d[0], Theme.TEXT_MUTED);
				}
				else
				{
					tip.row(d[0], d[1]);
				}
			}
		}
		boolean oneRate = !row.isAny() && (drop == null || (!drop.hasLevelRates() && !drop.hasPartRates()));
		if (oneRate)
		{
			tip.row("Drop rate", Rates.format(row.getRate()) + " per " + unit);
		}
		String purpleShare = purpleShare(stats.getBoss(), drop);
		if (purpleShare != null)
		{
			tip.row("Share of a purple", purpleShare);
		}
		tip.row("Chance of one by now", formatPercent(row.getChanceAtLeastOne()));
		String multiple = rateMultiple(row);
		if (multiple != null)
		{
			tip.row(row.isAny() ? "Without a unique" : "Without one", String.format(java.util.Locale.ROOT, "%,d %s, %s", kills, units, multiple));
		}
		if (oneRate && row.getRate() > 0)
		{
			long half = Poisson.killsForChance(row.getRate(), 0.5);
			long most = Poisson.killsForChance(row.getRate(), 0.9);
			Integer sure = drop == null ? null : drop.getGuaranteedAt();
			if (sure != null)
			{
				// the guaranteed kill makes it certain by then (Vorkath's 50th)
				half = Math.min(half, sure);
				most = Math.min(most, sure);
			}
			tip.row("From none, 50% / 90% takes", String.format(java.util.Locale.ROOT, "%,d / %,d %ss", half, most, unit));
		}
		addTimeRows(tip, time, row.isAny() ? "Time to get a unique" : "Time to get one");
		if (!setupRows.isEmpty())
		{
			// your own setups, from the raids On Rate saw: facts about them, not advice
			tip.line("At your setups (raids On Rate saw)", Theme.TEXT_MUTED);
			for (String[] s : setupRows)
			{
				tip.row(s[0], s[1]);
			}
		}

		// rates that depend on the run: one row each, under a small heading
		if (drop != null && drop.hasLevelRates() && stats.getBoss().getWaves() > 0)
		{
			// a run counts every wave up to the claim: the chance by the wave you claim at
			tip.line("Chance over a run, by the wave you claim at", Theme.TEXT_MUTED);
			for (int wave = 1; wave <= stats.getBoss().getWaves(); wave++)
			{
				double cumulative = com.onrate.engine.tracking.RateModel.waveRate(drop, wave);
				if (cumulative > 0)
				{
					tip.row("Wave " + wave, Rates.format(cumulative));
				}
			}
		}
		else if (drop != null && drop.hasLevelRates())
		{
			tip.line("Rate per delve level", Theme.TEXT_MUTED);
			for (int level = 1; level <= BossDefinition.DELVE_LEVELS; level++)
			{
				String rate = drop.getLevelRate(level);
				if (rate != null)
				{
					tip.row(BossDefinition.delveLevelLabel(level), Rates.format(Rates.parse(rate)));
				}
			}
		}
		else if (drop != null && drop.hasPartRates())
		{
			tip.line("Rate per " + unit, Theme.TEXT_MUTED);
			for (java.util.Map.Entry<String, Integer> e : stats.getPartCounts().entrySet())
			{
				String rate = drop.getPartRate(e.getKey());
				if (rate != null)
				{
					tip.row(e.getKey(), Rates.format(Rates.parse(rate)));
				}
			}
		}

		tip.note(streakNote(row, unit));
		if (drop != null && drop.getNote() != null)
		{
			tip.note(drop.getNote());
		}
		if (row.getFastest() != null)
		{
			tip.note(row.getFastest().equals(row.getLongest())
				? String.format(java.util.Locale.ROOT, "Since On Rate: one drop took %,d %s.", row.getFastest(), unit + (row.getFastest() == 1 ? "" : "s"))
				: String.format(java.util.Locale.ROOT, "Since On Rate: fastest drop after %,d %s, longest after %,d.", row.getFastest(),
					unit + (row.getFastest() == 1 ? "" : "s"), row.getLongest()));
		}
		boolean ownDrop = drop != null && stats.getBoss().getDrops().contains(drop);
		if (ownDrop && drop.getAlsoFrom() != null)
		{
			tip.note("Also drops from " + drop.getAlsoFrom() + ", and the collection log counts those too."
				+ (hasLeaveOutMenu(row) ? " Got yours there? Right-click to leave it out." : ""));
		}
		if (drop != null && !ownDrop)
		{
			// a shared item the player split: their own choice of where the older copies came from
			tip.note("Other bosses drop this too. You moved it to each boss separately: the copies On Rate "
				+ "saw drop count for the boss that dropped them, and the older ones where you said."
				+ (hasLeaveOutMenu(row) ? " Right-click to move it back to Shared with other bosses." : ""));
		}
		if (multiple != null)
		{
			// a dry spell says nothing about the next one
			tip.note(String.format(java.util.Locale.ROOT, "Each %s is its own roll. Going without one does not make the next more likely.", unit));
		}
		return tip.wide();
	}

	/**
	 * A raid unique's share of a purple ("5 of 15"), or null when it isn't one or the share
	 * changes with the mode or raid level (CoX prayer scrolls, ToA's high levels).
	 */
	static String purpleShare(BossDefinition boss, DropDefinition drop)
	{
		if (boss == null || drop == null || boss.getRaid() == null || drop.getWeight() == null || drop.hasLevelWeights())
		{
			return null;
		}
		String share = null;
		for (com.onrate.engine.data.BossPart part : boss.getParts())
		{
			if (part.getRaid() == null || "none".equals(part.getRaid()))
			{
				continue;
			}
			Double weight = drop.getWeight(part.getName());
			String s = weight == null ? null : formatWeight(weight) + " of " + formatWeight(boss.getTotalWeight(part.getName()));
			if (share != null && !share.equals(s))
			{
				return null;
			}
			share = s;
		}
		return share;
	}

	private static String formatWeight(double w)
	{
		return w == Math.rint(w) ? String.format(java.util.Locale.ROOT, "%,d", (long) w) : String.format(java.util.Locale.ROOT, "%.1f", w);
	}

	/** See {@link Texts#plainLuck}. */
	public static String plainLuck(double luck)
	{
		return Texts.plainLuck(luck);
	}

	/** See {@link Texts#nextChance}. */
	public static double nextChance(double rate, int kills)
	{
		return Texts.nextChance(rate, kills);
	}

	private static DropDefinition findDrop(List<DropDefinition> drops, String label)
	{
		for (DropDefinition drop : drops)
		{
			if (drop.getDisplayName().equals(label))
			{
				return drop;
			}
		}
		return null;
	}

	/** Red when dry, yellow around average, green when lucky (or the colour-blind palette). */
	static Color luckColor(double luck)
	{
		return Palette.luck(luck);
	}

	/**
	 * The verdict word in a list row: white for "On rate" (the middle of the scale is not news), the
	 * boss page's calmer colours for the rest. The word is always written, so colour never stands alone.
	 */
	static Color verdictWordColor(double luck)
	{
		if (!Double.isNaN(luck) && Verdict.isOnRate(luck))
		{
			return Theme.TEXT;
		}
		return Palette.onRow(Palette.verdict(luck).brighter());
	}

	private void styleTab(JButton button, Tab which)
	{
		button.setFont(FontManager.getRunescapeSmallFont());
		button.setFocusPainted(false);
		button.setContentAreaFilled(false);
		button.setOpaque(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.addActionListener(e ->
		{
			tab = which;
			refresh();
		});
		button.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				button.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setForeground(tab == which ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
	}

	private javax.swing.border.Border tabBorder(boolean selected)
	{
		// the card look: no grey underline under the tab that isn't selected, as on the account tabs
		Color unselected = boxes ? ColorScheme.DARK_GRAY_COLOR : ColorScheme.MEDIUM_GRAY_COLOR;
		return Activatable.withRing(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? Theme.ACCENT : unselected),
			new EmptyBorder(Theme.S, 0, Theme.HAIR, 0)));
	}

	private void resetSelected()
	{
		String bossName = (String) bossSelect.getSelectedItem();
		if (bossName == null)
		{
			return;
		}
		// Cancel is the default button, so Enter doesn't wipe what syncing can't bring back
		int answer = JOptionPane.showOptionDialog(this, Texts.resetConfirm(bossName), "Reset " + bossName,
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, Texts.RESET_OPTIONS,
			Texts.RESET_OPTIONS[1]);
		if (answer == 0)
		{
			tracker.reset(bossName);
			save.run();
			dropPendingNumbers();
			refresh();
		}
	}

	/** A folding heading lights up under the mouse, like the other clickable lines. */
	private static void brightenOnHover(JLabel heading)
	{
		heading.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				heading.setForeground(Theme.TEXT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				heading.setForeground(Theme.TEXT_MUTED);
			}
		});
	}

	private static JPanel spacer(int height)
	{
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setPreferredSize(new Dimension(1, height));
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		return p;
	}

	/** Text field that shows a grey hint while it's empty. */
	private static class HintField extends JTextField
	{
		private final String hint;

		HintField(String hint)
		{
			this.hint = hint;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (getText().isEmpty())
			{
				g.setColor(Palette.readableText ? Theme.TEXT_MUTED : ColorScheme.MEDIUM_GRAY_COLOR.brighter());
				g.setFont(getFont());
				java.awt.Insets in = getInsets();
				g.drawString(hint, in.left + 2, (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2);
			}
		}
	}
}
