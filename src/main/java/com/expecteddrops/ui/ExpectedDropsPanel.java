package com.expecteddrops.ui;

import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.AccountStats;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.tracking.SyncStatus;
import com.expecteddrops.tracking.Tracker;
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
		KILLS("Most kills"),
		STREAK("Longest dry streak"),
		NAME("A to Z");

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
	private JPanel tabsRow;

	private final JPanel welcomePanel = new JPanel();
	private final JLabel setupHeader = new JLabel();
	private final JPanel setupList = new JPanel(new GridLayout(0, 1, 0, 2));
	/** The space under the collection log line, hidden with it. */
	private final java.awt.Component setupGap = spacer(Theme.M);
	private boolean setupExpanded;

	private final HintField searchField = new HintField("Search bosses...");
	private final JLabel accountLink = new JLabel("\u2039 Account");
	private final JPanel headerRow = new JPanel(new BorderLayout(6, 0));
	private final JComboBox<String> bossSelect = new JComboBox<>();
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
	/** Pets shown per group in the Items tab: the luckiest ones you have, the driest ones you don't. */
	static final int TOP_PETS_SHOWN = 5;
	/** Account overview: showing the Items tab (top 10) instead of the bosses. */
	private int accountTab = TAB_BOSSES;
	/** False while the panel is closed in the sidebar (see {@link #onActivate()}). */
	private boolean shown = true;
	private boolean outOfDate;
	private static final int TAB_BOSSES = 0;
	private static final int TAB_ITEMS = 1;
	private static final int TAB_RECENT = 2;
	/** Whether the History section on boss pages is open (it starts folded). */
	private boolean historyOpen;
	private final LuckGraph graph = new LuckGraph();
	private final JLabel graphHint = new JLabel();
	private final JComboBox<Sort> sortSelect = new JComboBox<>(Sort.values());
	private final JPanel sortRow = new JPanel(new BorderLayout(4, 0));
	private final JLabel kcLabel = new JLabel();
	private final JLabel syncLabel = new JLabel();
	/** Boss pages: GE value received vs expected, weighed by price (the verdict only counts). */
	private final JLabel valueLabel = new JLabel();
	/** Two answers side by side: how many uniques, and what they're worth. */
	private final JPanel verdictTiles = new JPanel();
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
	/** Sets the item view (remembered; see {@link #setItemViewChanger}). */
	private java.util.function.Consumer<ItemView> setItemView = view -> { };
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
			if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing() && outOfDate)
			{
				refresh();
			}
		});

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		JLabel title = new JLabel("On Rate");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		helpLabel.setFont(FontManager.getRunescapeBoldFont());
		helpLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		helpLabel.setCursor(Cursor.getDefaultCursor());
		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(LEFT_ALIGNMENT);
		titleRow.add(title, BorderLayout.WEST);
		// one click back to the account overview, wherever you are (only on a boss page)
		accountLink.setFont(FontManager.getRunescapeSmallFont());
		accountLink.setForeground(Theme.TEXT_MUTED);
		accountLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		accountLink.setToolTipText("Back to the account overview");
		accountLink.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showBoss(ACCOUNT);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				accountLink.setForeground(Theme.TEXT);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				accountLink.setForeground(Theme.TEXT_MUTED);
			}
		});
		Activatable.install(accountLink, "Back to the account overview", () -> showBoss(ACCOUNT));
		JPanel titleRight = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, Theme.M, 0));
		titleRight.setOpaque(false);
		titleRight.add(accountLink);
		titleRight.add(helpLabel);
		titleRow.add(titleRight, BorderLayout.EAST);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		top.add(titleRow);
		top.add(spacer(Theme.M));

		buildWelcome();
		top.add(welcomePanel);

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
		setupList.setOpaque(false);
		setupList.setAlignmentX(LEFT_ALIGNMENT);
		setupList.setBorder(new EmptyBorder(Theme.S, Theme.M, 0, 0));
		top.add(setupList);
		top.add(setupGap);

		searchField.setFont(FontManager.getRunescapeSmallFont());
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		searchField.setAlignmentX(LEFT_ALIGNMENT);
		searchField.setToolTipText("Type part of a boss name, e.g. 'vork' or 'cox'. Enter opens the first match.");
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
		top.add(spacer(Theme.S));

		fillBossList("");
		bossSelect.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		bossSelect.setAlignmentX(LEFT_ALIGNMENT);
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
		graphTitle.setForeground(Color.WHITE);
		graphTitle.setAlignmentX(LEFT_ALIGNMENT);
		graphTitle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		graphTitle.setToolTipText("Click to show or hide your account's luck over time, one point per day you play");
		graphTitle.setBorder(new EmptyBorder(0, Theme.S, 0, 0));
		graphTitle.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setGraphOpen.accept(!options.get().isLuckGraph());
				refresh();
			}
		});
		Activatable.install(graphTitle, "Luck graph", () ->
		{
			setGraphOpen.accept(!options.get().isLuckGraph());
			refresh();
		});
		graphPanel.add(spacer(Theme.S));
		graphPanel.add(graph);
		graphHint.setFont(FontManager.getRunescapeSmallFont());
		graphHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		graphHint.setAlignmentX(LEFT_ALIGNMENT);
		graphPanel.add(graphHint);
		graphPanel.add(spacer(Theme.M));
		topItemsPanel.setLayout(new BoxLayout(topItemsPanel, BoxLayout.Y_AXIS));
		topItemsPanel.setOpaque(false);
		topItemsPanel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(topItemsPanel);

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
		JLabel sortLabel = new JLabel("Sort:");
		sortLabel.setFont(FontManager.getRunescapeSmallFont());
		sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortRow.add(sortLabel, BorderLayout.WEST);
		sortSelect.setFont(FontManager.getRunescapeSmallFont());
		sortSelect.addActionListener(e -> refresh());
		sortRow.add(sortSelect, BorderLayout.CENTER);
		sortRow.add(graphTitle, BorderLayout.EAST);
		sortRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		top.add(sortRow);
		top.add(graphPanel);

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
		// Discord on the left, Reset on the right: one quiet line
		JPanel footer = new JPanel(new BorderLayout(6, 0));
		footer.setOpaque(false);
		footer.setAlignmentX(LEFT_ALIGNMENT);
		footer.add(feedbackLink(), BorderLayout.WEST);
		footer.add(resetButton, BorderLayout.EAST);
		footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
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

	/** Fills the boss list with the bosses matching the search text. */
	private void fillBossList(String query)
	{
		String q = simplify(query);
		Object selected = bossSelect.getSelectedItem();
		bossSelect.removeAllItems();
		if (q.isEmpty() || simplify(ACCOUNT).contains(q))
		{
			bossSelect.addItem(ACCOUNT);
		}
		for (BossDefinition boss : tracker.getCatalog().getBosses())
		{
			if (boss.getDrops().isEmpty())
			{
				// kept only for its kill count (Chaos Elemental: everything it drops is shared)
				continue;
			}
			if (q.isEmpty() || matches(boss, q))
			{
				bossSelect.addItem(boss.getName());
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
		updatingSelection = true;
		try
		{
			fillBossList(searchField.getText());
		}
		finally
		{
			updatingSelection = false;
		}
		if (bossSelect.getItemCount() > 0 && bossSelect.getSelectedItem() != before)
		{
			refresh();
		}
	}

	/** A boss matches on its name, its kill count names ("CoX" style short names) or its parts. */
	private static boolean matches(BossDefinition boss, String q)
	{
		if (simplify(boss.getName()).contains(q))
		{
			return true;
		}
		for (String initials : initialsOf(boss.getName()))
		{
			if (q.length() >= 2 && initials.equals(q))
			{
				return true;
			}
		}
		for (String name : boss.getKcNames())
		{
			if (simplify(name).contains(q))
			{
				return true;
			}
		}
		if (boss.hasParts())
		{
			for (com.expecteddrops.data.BossPart part : boss.getParts())
			{
				if (simplify(part.getName()).contains(q))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Lower case without apostrophes or dashes, so "kril" finds K'ril and "tztok" finds TzTok-Jad. */
	private static String simplify(String text)
	{
		return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("['\\-]", "").trim();
	}

	/**
	 * Short names: "cox", "tob", "toa" from all first letters, and "cx", "tb" with "of",
	 * "the" and "and" skipped.
	 */
	private static String[] initialsOf(String name)
	{
		StringBuilder all = new StringBuilder();
		StringBuilder main = new StringBuilder();
		for (String word : name.toLowerCase(Locale.ROOT).split("[\\s-]+"))
		{
			if (word.isEmpty())
			{
				continue;
			}
			all.append(word.charAt(0));
			if (!word.equals("of") && !word.equals("the") && !word.equals("and"))
			{
				main.append(word.charAt(0));
			}
		}
		return new String[]{all.toString(), main.toString()};
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
		if (outOfDate)
		{
			refresh();
		}
	}

	@Override
	public void onDeactivate()
	{
		shown = false;
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
			log.warn("On Rate could not draw {}", bossSelect.getSelectedItem(), e);
			rowsPanel.removeAll();
			JLabel error = smallLabel("<html><body style='width: 165px'>Something went wrong drawing this page. "
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

	/** Recomputes and redraws the selected page (see {@link #refresh()}). */
	private void draw()
	{
		// the graph offer was on screen and this redraw may move on from it (see the end)
		boolean overlayWasOnScreen = overlayOfferOnScreen && overlayOfferLabel != null && overlayOfferLabel.isShowing();
		overlayOfferOnScreen = false;
		drawPage();
		// seen, and the player went on to another page or tab: never again on this account
		if (overlayWasOnScreen && !overlayOfferOnScreen)
		{
			markOverlayOfferSeen.run();
		}
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

	/** See {@link #setGraphOpener}. */
	public void setItemViewChanger(java.util.function.Consumer<ItemView> changer)
	{
		setItemView = changer == null ? view -> { } : changer;
	}

	private void drawPage()
	{
		welcomePanel.setVisible(showWelcome.getAsBoolean());
		refreshSetup();

		String bossName = (String) bossSelect.getSelectedItem();
		ViewOptions o = options.get();
		Palette.setColourBlind(o.isColourBlind());
		rowsPanel.removeAll();
		styleTabs();
		boolean account = ACCOUNT.equals(bossName);
		accountLink.setVisible(!account);
		// the luck graph belongs to the bosses list; the Items and Recent tabs get the room
		// Luck over time sits with the bosses list; its title opens and closes it
		graphPanel.setVisible(account && (accountTab == TAB_BOSSES || accountTab == TAB_ITEMS && !o.isTopItems()));
		graphTitle.setText((o.isLuckGraph() ? "\u25be " : "\u25b8 ") + "Luck graph");
		topItemsPanel.setVisible(account && o.isTopItems());
		topItemsPanel.removeAll();
		sortRow.setVisible(account);
		tabsRow.setVisible(o.getItemView() == ItemView.DELVE_BARS);
		syncLabel.setVisible(!account);
		valueLabel.setVisible(false);
		hintLabel.setVisible(false);
		lastUniqueLabel.setVisible(false);
		headerIcon.setIcon(null);
		headerIcon.setText(null);
		headerIcon.setVisible(true);
		kcLabel.setVisible(true);
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
		String unit = com.expecteddrops.tracking.Tracker.unitOne(stats.getBoss());
		headerName.setText(stats.getBoss().getName());
		headerName.setToolTipText(null);
		kcLabel.setToolTipText(null);
		// "est." sits next to the expected number on the Uniques tile, where it applies, instead of a bare asterisk
		estimateNote = stats.getBoss().isEstimate()
			? "Estimate: the rates here depend on your team, raid points or settings." : null;
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
			kcLabel.setText(String.format("Total delves: %,d", stats.getKillCount()));
			levelsLabel.setText(levelTable(stats.getLevelCounts()));
			levelsLabel.setVisible(true);
		}
		else
		{
			kcLabel.setText(stats.isKillCountKnown()
				? String.format("%,d %s", stats.getKillCount(), com.expecteddrops.tracking.Tracker.unit(stats.getBoss()))
				: String.format("%,d %s tracked", stats.getKillCount(), com.expecteddrops.tracking.Tracker.unit(stats.getBoss())));
			levelsLabel.setVisible(false);
		}
		String reminder = tracker.syncReminder(stats.getBoss().getName());
		if (reminder != null)
		{
			syncLabel.setText(Theme.wrap(reminder, Theme.TEXT_WIDTH - 14));
			// an instruction, not a warning: neutral text with an info mark
			syncLabel.setForeground(Theme.TEXT_MUTED);
			syncLabel.setIcon(Theme.infoIcon());
		}
		else if (stats.isClogSynced())
		{
			// all good: nothing to say
			syncLabel.setText("");
			syncLabel.setVisible(false);
		}
		else
		{
			syncLabel.setText("Counting drops from loot from now on");
			syncLabel.setForeground(Theme.TEXT_MUTED);
			syncLabel.setIcon(Theme.infoIcon());
		}
		RowStats any = stats.getAnyRow();
		boolean unsynced = reminder != null && stats.getKillCount() > 0;
		if (unsynced)
		{
			// The kill count is known but not the drops from before On Rate: any verdict would be
			// "very dry" for the wrong reason. Wait for the log page.
			verdictLabel.setText("Not synced");
			verdictLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			verdictLabel.setToolTipText("<html><body style='width: 240px'>On Rate knows your kill count, but not the drops "
				+ "you got before you installed it. Open the page below once and your verdict shows up.</body></html>");
			summaryLabel.setText(any.getExpected() < 0.005 ? "" : String.format("%s %s expected by now",
				formatExpected(any.getExpected()), raid ? "purples" : "uniques"));
		}
		else
		{
			showVerdict(any);
		}
		double[] value = o.isGeValue() && !unsynced ? bossValue(stats) : null;
		List<RowStats> counted = new ArrayList<>();
		for (int i = 0; i < stats.getBoss().getDrops().size(); i++)
		{
			if (tracker.counts(stats.getBoss(), stats.getBoss().getDrops().get(i)))
			{
				counted.add(stats.getRows().get(i));
			}
		}
		showTiles(unsynced ? null : any, value, lastValueTip, raid ? "purples" : "uniques", counted, true);
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
		helpLabel.setToolTipText(bossHelp(o.getItemView()));

		if (o.isTripCard())
		{
			addTripCard(stats.getBoss().getName());
		}
		renderBossItems(stats, unit, o.getItemView());
		renderBossPools(stats, o.getItemView());
		renderHistory(stats, unit);

		StringBuilder note = new StringBuilder();
		if (raid && combined)
		{
			StringBuilder modes = new StringBuilder();
			for (com.expecteddrops.data.BossPart part : stats.getBoss().getParts())
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
			}
			note.append("Purple chance for raids without points: ").append(modes).append(". Change these under <i>Raid purple chances</i> in the settings.");
			note.append(raidPointsNote(stats.getBoss()));
		}
		else if (raid)
		{
			note.append(String.format("Assumes a %.1f%% chance of a purple per raid (change it under Raid purple chances in the settings).",
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
					: String.format("Counted at the team On Rate first saw (%.1f players).", 1 / share);
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
		noteLabel.setText(note.length() == 0 ? "" : "<html><u>About these numbers</u></html>");
		noteLabel.setToolTipText(note.length() == 0 ? null : "<html><body style='width: 240px'>" + note + "</body></html>");

		revalidate();
		repaint();
	}

	private void styleTabs()
	{
		expectedTab.setForeground(tab == Tab.EXPECTED ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		receivedTab.setForeground(tab == Tab.RECEIVED ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		expectedTab.setBorder(tabBorder(tab == Tab.EXPECTED));
		receivedTab.setBorder(tabBorder(tab == Tab.RECEIVED));
	}

	/**
	 * How many raids per mode used their own points instead of the settings, plus what the
	 * Theatre of Blood board told us.
	 */
	private String raidPointsNote(BossDefinition boss)
	{
		List<com.expecteddrops.tracking.RaidRecord> records = tracker.raidRecords(boss.getName());
		if (records.isEmpty())
		{
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (com.expecteddrops.data.BossPart part : boss.getParts())
		{
			int measured = tracker.measuredRaids(boss.getName(), part.getName());
			if (measured > 0)
			{
				boolean estimated = "toa".equals(boss.getRaid());
				sb.append(String.format("<br><br>%,d %s raid%s used your %s instead of the setting.",
					measured, part.getName(), measured == 1 ? "" : "s", estimated ? "estimated points" : "points"));
			}
		}
		int boards = 0;
		int mvps = 0;
		int deaths = 0;
		for (com.expecteddrops.tracking.RaidRecord r : records)
		{
			if ("tob".equals(r.getFamily()))
			{
				boards++;
				mvps += Boolean.TRUE.equals(r.getMvp()) ? 1 : 0;
				deaths += Math.max(0, r.getDeaths());
			}
		}
		if (boards > 0)
		{
			sb.append(String.format("<br><br>Saved from the end-of-raid board: %,d raid%s, MVP in %,d, %,d death%s. "
				+ "ToB has no points to read, so these use the team chance from the settings split over each raid's real team size.",
				boards, boards == 1 ? "" : "s", mvps, deaths, deaths == 1 ? "" : "s"));
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
		boolean bossPage)
	{
		double luck = any == null ? Double.NaN : any.getLuck();
		boolean tiles = !Double.isNaN(luck) && !DropMath.verdict(luck).isEmpty();
		// a boss page with next to nothing expected says so instead of a verdict (the account overview
		// adds up every boss and keeps its verdicts)
		boolean early = bossPage && tiles && any.getExpected() < Texts.TOO_EARLY_BELOW;
		// "On rate" says little by itself: one notable fact under it, when there is one
		String notable = !early && DropMath.VERDICTS.get(3).equals(DropMath.verdict(luck)) ? Texts.notableLine(items) : null;
		notableText = notable;
		notableLabel.setText(notable == null ? "" : "<html><body style='width: 165px'>" + html(notable) + "</body></html>");
		notableLabel.setVisible(notable != null);
		verdictTiles.setVisible(tiles);
		verdictLabel.setVisible(!tiles);
		summaryLabel.setVisible(!tiles);
		if (!tiles)
		{
			return;
		}
		verdictTiles.removeAll();
		verdictTiles.setLayout(new GridLayout(1, value == null ? 1 : 2, 4, 0));
		// Swing's HTML widths are not screen pixels (the 165 of TEXT_WIDTH fills the panel): a tile is
		// about 3/4 of that wide with two tiles side by side
		int wrap = !bossPage ? 0 : value == null ? Theme.TEXT_WIDTH - 5 : 76;
		boolean estimate = estimateNote != null;
		String gotText = bossPage ? Texts.gotExpected(String.format("%,d", any.getReceived()), formatExpected(any.getExpected()), estimate)
			: String.format("%,d / %s%s", any.getReceived(), formatExpected(any.getExpected()), estimate ? " est." : "");
		String verdict = early ? "Too early" : DropMath.verdict(luck);
		Color verdictColor = early ? Theme.TEXT_MUTED : bossPage ? Palette.verdict(luck).brighter() : luckColor(luck).brighter();
		String shortLine = early ? "Too few " + (what.equals("purples") ? "raids" : "kills") + " to judge"
			: bossPage ? Texts.luckPlayers(any.getShares(), any.getReceived()) : luckShort(any.getShares(), any.getReceived());
		Tip uniquesTip = new Tip(String.format("Uniques \u00b7 %s", verdict))
			.line(String.format("Got %,d %s, expected %s by now", any.getReceived(), what, formatExpected(any.getExpected())));
		if (early)
		{
			uniquesTip.line("Less than half a " + (what.equals("purples") ? "purple" : "unique")
				+ " expected so far: too early to say if you are dry or lucky.");
		}
		else
		{
			uniquesTip.line(capital(luckPercent(any.getShares(), any.getReceived())))
				.line(sharesLine(any.getShares(), any.getReceived()));
		}
		uniquesTile.set(verdict, verdictColor, gotText, shortLine,
			uniquesTip
				.note("Counts how many " + what + ", not what they're worth: a twisted bow counts the same as a prayer scroll. "
					+ "Dry when fewer than 35% of players have this many or fewer (very dry: 10%); spooned when fewer than 35% "
					+ "have this many or more (very spooned: 10%). In between: a bit dry, on rate or a bit spooned.")
				.note(estimateNote)
				.wide(), wrap);
		verdictTiles.add(uniquesTile);
		if (value != null)
		{
			String valueText = bossPage ? Texts.gotExpected(DropMath.formatGp(value[0]), DropMath.formatGp(value[1]), false)
				: DropMath.formatGp(value[0]) + " / " + DropMath.formatGp(value[1]);
			valueTile.set(early ? "Too early" : DropMath.verdict(value[2]),
				early ? Theme.TEXT_MUTED : bossPage ? Palette.verdict(value[2]).brighter() : luckColor(value[2]).brighter(),
				valueText, early ? "" : bossPage ? Texts.luckPlayers(value[2]) : luckShort(value[2]), valueTip, wrap);
			verdictTiles.add(valueTile);
		}
		verdictTiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, verdictTiles.getPreferredSize().height));
	}

	/** The big verdict ("ON RATE") and the line under it with the numbers behind it. */
	private void showVerdict(RowStats any)
	{
		double luck = any.getLuck();
		String verdict = DropMath.verdict(luck);
		if (Double.isNaN(luck) || verdict.isEmpty())
		{
			verdictLabel.setToolTipText(null);
			verdictLabel.setText("No kills yet");
			verdictLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			summaryLabel.setText(any.getReceived() == 0 && any.getExpected() <= 0 ? ""
				: String.format("%,d unique%s \u00b7 %s expected", any.getReceived(), any.getReceived() == 1 ? "" : "s",
				formatExpected(any.getExpected())));
			return;
		}
		verdictLabel.setText(verdict);
		verdictLabel.setForeground(luckColor(luck).brighter());
		verdictLabel.setToolTipText(capital(sharesLine(any.getShares(), any.getReceived())) + ".");
		summaryLabel.setText(String.format("<html>%,d unique%s \u00b7 %s expected<br>%s</html>",
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
		AccountStats account = tracker.accountStats(raidSettings.get(), o.isGeValue() ? priceOf : null);
		RowStats total = account.getTotal();
		resetButton.setVisible(false);
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
		showVerdict(total);
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
		for (com.expecteddrops.stats.ItemHighlight h : account.getItems())
		{
			accountItems.add(h.getRow());
		}
		showTiles(account.getRows().isEmpty() ? null : total, accountValue, valueTooltip(account), "uniques", accountItems, false);

		showLastUnique();
		if (notableText != null && lastUniqueText != null)
		{
			// one quiet block under the tiles instead of two
			lastUniqueLabel.setText(Theme.wrap(html(notableText + " " + lastUniqueText), Theme.TEXT_WIDTH));
			notableLabel.setVisible(false);
		}

		List<com.expecteddrops.tracking.HistoryPoint> history = tracker.history();
		graph.setPoints(history);
		graph.setVisible(o.isLuckGraph() && history.size() >= 2);
		graphHint.setText(history.size() >= 2 ? ""
			: "<html><body style='width: 165px'>On Rate saves one point a day. Your line starts to show from tomorrow.</body></html>");
		graphHint.setVisible(o.isLuckGraph() && history.size() < 2);

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
		rows.sort(comparator((Sort) sortSelect.getSelectedItem(), account));
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
			sortRow.setVisible(false);
			renderItemsTab(account, o.getItemView());
		}
		else if (tabs && accountTab == TAB_RECENT)
		{
			sortRow.setVisible(false);
			renderRecentTab();
		}
		else
		{
			renderAccountItems(account, total, rows, o.getItemView());
			addAccountFootnote(account, hidden, account.getRows().isEmpty() ? java.util.Collections.emptyList() : unsynced);
		}

		String note = account.getRows().isEmpty() && !unsynced.isEmpty()
			? "Open the collection log page" + (unsynced.size() == 1 ? "" : "s") + " of " + joinAnd(unsynced) + " to load your past drops. "
			+ "Your account shows up here once a boss is loaded."
			: account.getRows().isEmpty()
			? "Open your collection log and click the pages of the bosses you do, or go kill one. "
			+ "Your account shows up here."
			: "Every boss you have kills at, added up. Click a boss to open it.<br><br>"
			+ "GE value uses RuneLite's own price list. Untradeable items (pets, jars...) count for the number of "
			+ "uniques but as 0 GP for value, so a twisted bow outweighs a pile of cheap drops.<br><br>"
			+ "The <b>Items</b> tab shows your 10 driest or luckiest items across every boss, and your pets. "
			+ "<b>Recent</b> shows your latest uniques.<br><br>"
			+ "Items that several bosses drop (dragon pickaxe, godsword shards, Virtus, onyx...) have no row here: "
			+ "you find them on the page of each boss that drops them. They do count in the verdict and GE value above, "
			+ "once On Rate knows the kills of every boss that drops them. Pets count once, like in the log. "
			+ "Raids without points use your purple chance settings, and team bosses assume you do all the damage.";
		helpLabel.setToolTipText("<html><body style='width: 240px'>" + note + FEEDBACK_LINE + "</body></html>");
		noteLabel.setText(account.getRows().isEmpty() ? "<html><body style='width: 165px'>" + note + "</body></html>" : "");
		noteLabel.setToolTipText(null);
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
			return "Your team size is counted from your next kill on (the players around you). "
				+ (olderNote == null ? "Until then every kill counts as solo." : "Until then: " + lowerFirst(olderNote));
		}
		String text = String.format("On Rate saw %,d of your kills, with %.1f players on average, "
			+ "and gives you that share of each kill.", seen, players / seen);
		int older = Math.max(0, kc - seen);
		if (older > 0)
		{
			text += olderNote == null
				? String.format(" Your %,d earlier kill%s count as solo.", older, older == 1 ? "" : "s")
				: String.format(" Your %,d earlier kill%s: %s", older, older == 1 ? "" : "s", lowerFirst(olderNote));
		}
		return text;
	}

	/** On Rate's Discord: bugs, wrong rates and ideas. Opened in the browser, never contacted by the plugin. */
	public static final String DISCORD = "https://discord.gg/gh9fx6T9zn";
	private static final String FEEDBACK_LINE = "<br><br>Feedback, bugs or ideas? Join the On Rate Discord (link at the bottom of the panel).";

	/** A quiet link at the bottom of the panel that opens the Discord in your browser. */
	private JLabel feedbackLink()
	{
		JLabel link = new JLabel("<html><u>Feedback? Discord</u></html>");
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		link.setAlignmentX(LEFT_ALIGNMENT);
		link.setToolTipText("Report a bug or a wrong drop rate, or share an idea. Opens " + DISCORD + " in your browser.");
		link.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				net.runelite.client.util.LinkBrowser.browse(DISCORD);
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
		return link;
	}

	private static final String ESTIMATE_NOTE = "Estimate: these numbers depend on your team, points or settings.";
	private static final String ESTIMATE_TIP = "<html><body style='width: 240px'>The rates here depend on things On Rate "
		+ "can't always see: your team size or damage, raid points, or an assumed setup (all bodyguards killed at God Wars). "
		+ "Treat the numbers as an estimate.</body></html>";

	/** One quiet line under the bosses: bosses with too few kills to list (hover for which). */
	private void addAccountFootnote(AccountStats account, List<String> hidden, List<String> unsynced)
	{
		List<String> parts = new ArrayList<>();
		StringBuilder tip = new StringBuilder("<html><body style='width: 240px'>");
		if (!unsynced.isEmpty())
		{
			parts.add(String.format("%d boss%s not loaded yet", unsynced.size(), unsynced.size() == 1 ? "" : "es"));
			tip.append("<b>Not loaded yet</b>, so not counted: ").append(String.join(", ", unsynced))
				.append(". On Rate knows the kill count but not your drops from before; open their collection log page once.<br><br>");
		}
		if (!hidden.isEmpty())
		{
			java.util.Collections.sort(hidden);
			parts.add(String.format("+ %d more boss%s", hidden.size(), hidden.size() == 1 ? "" : "es"));
			tip.append("<b>Too few kills to judge</b> (under 0.05 uniques expected), so not listed: ")
				.append(String.join(", ", hidden)).append(". They still count in your account total.");
		}
		if (parts.isEmpty())
		{
			return;
		}
		rowsPanel.add(spacer(Theme.S));
		JLabel line = smallLabel("<html><body style='width: 165px'>" + String.join(" \u00b7 ", parts) + "</body></html>",
			ColorScheme.LIGHT_GRAY_COLOR);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setToolTipText(tip.append("</body></html>").toString());
		rowsPanel.add(line);
	}

	/** A gap in drop rates as a colour: well under the rate is lucky, well over it is dry. */
	private static Color multipleColor(com.expecteddrops.tracking.KillEntry.DropEntry d)
	{
		Double m = d.getMultiple();
		return d.isFirst() || m == null ? ColorScheme.LIGHT_GRAY_COLOR : luckColor(Math.exp(-m)).brighter();
	}

	/**
	 * One block of the Recent tab: the total for these days, then a line per boss. The uniques
	 * are green when more than expected and orange when fewer; the expected number is on hover.
	 * Returns false (and adds nothing) when there were no kills.
	 */
	private boolean renderDays(String title, List<com.expecteddrops.tracking.BossDays> days)
	{
		com.expecteddrops.tracking.BossDays total = days.get(0);
		if (total.getKills() == 0 && total.getUniques() == 0)
		{
			return false;
		}
		JPanel block = new JPanel(new GridLayout(0, 1, 0, 0));
		block.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		block.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		block.setAlignmentX(LEFT_ALIGNMENT);
		for (com.expecteddrops.tracking.BossDays d : days)
		{
			boolean isTotal = d.getBoss() == null;
			BossDefinition boss = isTotal ? null : tracker.getCatalog().byName(d.getBoss());
			// each boss in its own unit ("5 delves", "2 raids"); the total mixes them, so "kills"
			String units = boss == null ? "kills" : com.expecteddrops.tracking.Tracker.unit(boss);
			String unitsHere = d.getKills() == 1 ? units.substring(0, units.length() - 1) : units;
			double luck = DropMath.luck(d.getUniques(), d.getExpected());
			Color uniquesColor = Double.isNaN(luck) || d.getExpected() <= 0 || (luck >= 0.35 && luck <= 0.65)
				? Color.WHITE : luckColor(luck).brighter();
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
			String color = String.format("#%06x", uniquesColor.getRGB() & 0xFFFFFF);
			JLabel numbers = smallLabel(String.format("<html>%,d %s \u00b7 <font color='%s'>%,d%s</font></html>", d.getKills(),
				unitsHere,
				color, d.getUniques(), isTotal ? (d.getUniques() == 1 ? " unique" : " uniques") : ""),
				ColorScheme.LIGHT_GRAY_COLOR);
			numbers.setFont(FontManager.getRunescapeFont());
			line.add(numbers, BorderLayout.EAST);
			String tip = String.format("<html><b>%s%s</b><br>%,d unique%s in %,d %s<br>About %s expected%s",
				isTotal ? "All bosses, " : d.getBoss() + ", ", title.toLowerCase(Locale.ROOT),
				d.getUniques(), d.getUniques() == 1 ? "" : "s", d.getKills(), unitsHere,
				formatExpected(d.getExpected()),
				Double.isNaN(luck) || d.getExpected() <= 0 ? "" : ": " + html(luckPercent(DropMath.shares(d.getUniques(), d.getExpected()), d.getUniques())))
				+ uniquesLine(isTotal, d, days)
				+ (isTotal ? "" : "<br>Click to open " + d.getBoss()) + "</html>";
			String bossName = d.getBoss();
			// a boss line opens that boss's page
			setTip(line, tip, isTotal ? null : () -> showBoss(bossName));
			block.add(line);
		}
		block.setMaximumSize(new Dimension(Integer.MAX_VALUE, block.getPreferredSize().height));
		rowsPanel.add(spacer(Theme.HAIR));
		rowsPanel.add(block);
		return true;
	}

	/**
	 * Which uniques those were, for the hover of a Recent line: "Tyrannical ring", or on the
	 * total line "Tyrannical ring (Callisto and Artio)". Empty when History doesn't have them.
	 */
	private static String uniquesLine(boolean isTotal, com.expecteddrops.tracking.BossDays d,
		List<com.expecteddrops.tracking.BossDays> days)
	{
		List<String> names = new ArrayList<>();
		if (isTotal)
		{
			for (com.expecteddrops.tracking.BossDays b : days.subList(1, days.size()))
			{
				for (String n : b.getUniqueNames())
				{
					names.add(n + " (" + b.getBoss() + ")");
				}
			}
		}
		else
		{
			names.addAll(d.getUniqueNames());
		}
		if (names.isEmpty())
		{
			return "";
		}
		int shown = Math.min(10, names.size());
		return "<br><br>" + String.join("<br>", names.subList(0, shown))
			+ (names.size() > shown ? "<br>and " + (names.size() - shown) + " more" : "") + "<br>";
	}

	/** The account's Recent tab: the latest uniques across every boss. */
	private void renderRecentTab()
	{
		long nowMs = System.currentTimeMillis();
		boolean days = renderDays("Today", tracker.recentDays(1, nowMs));
		days |= renderDays("Last 7 days", tracker.recentDays(7, nowMs));
		if (days)
		{
			JLabel key = smallLabel("Green: more than expected \u00b7 orange: fewer", ColorScheme.LIGHT_GRAY_COLOR);
			key.setToolTipText("Compared with the uniques you should have had from those kills. Hover a line for the numbers.");
			key.setAlignmentX(LEFT_ALIGNMENT);
			key.setBorder(new EmptyBorder(Theme.HAIR, 0, Theme.M, 0));
			rowsPanel.add(key);
		}
		List<com.expecteddrops.tracking.RecentDrop> recent = tracker.recentDrops(10);
		if (!recent.isEmpty() && days)
		{
			JLabel latest = smallLabel("LATEST DROPS", ColorScheme.LIGHT_GRAY_COLOR);
			latest.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(latest);
		}
		if (recent.isEmpty())
		{
			JLabel none = smallLabel("<html><body style='width: 165px'>No uniques seen yet. Drops show up here from now on.</body></html>",
				ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(LEFT_ALIGNMENT);
			none.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
			rowsPanel.add(none);
			return;
		}
		long now = System.currentTimeMillis();
		for (com.expecteddrops.tracking.RecentDrop r : recent)
		{
			com.expecteddrops.tracking.KillEntry.DropEntry d = r.getDrop();
			BossDefinition boss = tracker.getCatalog().byName(r.getBoss());
			String unit = boss == null ? "kill" : com.expecteddrops.tracking.Tracker.unitOne(boss);
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
			JLabel second = smallLabel(daysAgo(r.getKill().getTime(), now) + (after.isEmpty() ? "" : " \u00b7 " + after),
				multipleColor(d));
			text.add(second);
			panel.add(text, BorderLayout.CENTER);
			panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
			setTip(panel, "<html><b>" + d.getName() + "</b><br>" + r.getBoss() + ", " + unit + " " + String.format("%,d", r.getKill().getKc())
				+ "<br>Click to open " + r.getBoss() + "</html>", () -> showBoss(r.getBoss()));
			rowsPanel.add(spacer(Theme.HAIR));
			rowsPanel.add(panel);
		}
	}

	/**
	 * The folded History section at the bottom of a boss page: the kills On Rate saw, newest
	 * first. Normal bosses list their drops; raids add points and purple chance, group bosses
	 * the team size, Doom the delve level, Royal Titans the titan you looted.
	 */
	private void renderHistory(BossStats stats, String unit)
	{
		BossDefinition boss = stats.getBoss();
		List<com.expecteddrops.tracking.HistoryRow> history = tracker.history(boss.getName());
		rowsPanel.add(spacer(Theme.M));
		JLabel title = smallLabel((historyOpen ? "\u25be" : "\u25b8") + " History", Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setToolTipText("The " + unit + "s and drops On Rate saw since you installed it. Click to "
			+ (historyOpen ? "fold it away." : "open."));
		title.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		title.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				historyOpen = !historyOpen;
				refresh();
			}
		});
		Activatable.install(title, "History, " + (historyOpen ? "open" : "closed"), () ->
		{
			historyOpen = !historyOpen;
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
		boolean team = boss.isDetectTeam();
		// delve levels and Colosseum runs: every run is listed with its level or wave
		boolean delve = boss.isDelve() || boss.getWaves() > 0;
		boolean titans = "Royal Titans".equals(boss.getName());
		boolean dropsOnly = !raid && !team && !delve && !titans;
		long now = System.currentTimeMillis();
		int shown = 0;
		int drops = 0;
		// which uniques those were, for the hover on the line under the list
		List<String> dropNames = new ArrayList<>();
		for (com.expecteddrops.tracking.HistoryRow h : history)
		{
			com.expecteddrops.tracking.KillEntry k = h.getKill();
			drops += k.getDrops().size();
			for (com.expecteddrops.tracking.KillEntry.DropEntry d : k.getDrops())
			{
				int at = k.getPartKc() != null && boss.hasParts() ? k.getPartKc() : k.getKc();
				dropNames.add(String.format("%s \u00b7 %s%s %,d", d.getName(),
					boss.hasParts() && k.getPart() != null && boss.getRaid() == null ? k.getPart() + " " : "", unit, at));
			}
			if (shown >= 10 || (dropsOnly && k.getDrops().isEmpty()))
			{
				continue;
			}
			shown++;
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
				first = String.format("%,d", k.getPartKc() != null && raid ? k.getPartKc() : k.getKc());
				if (raid)
				{
					com.expecteddrops.tracking.RaidRecord r = h.getRaid();
					String mode = k.getPart() != null && !"Normal".equals(k.getPart()) ? shortMode(k.getPart()) + " " : "";
					if (r == null)
					{
						middle = mode + "no points";
					}
					else if (r.getPersonalPoints() > 0)
					{
						middle = mode + formatPoints(r.getPersonalPoints()) + " pts"
							+ (Double.isNaN(h.getPurpleChance()) ? "" : String.format(" \u00b7 %.1f%%", h.getPurpleChance() * 100));
					}
					else
					{
						middle = mode + "team " + r.getTeamSize() + (r.getDeaths() > 0 ? " \u00b7 " + r.getDeaths() + " death" + (r.getDeaths() == 1 ? "" : "s") : "");
					}
				}
				else if (team)
				{
					middle = k.getTeam() == null ? "team ?" : k.getTeam() == 1 ? "solo" : "team " + k.getTeam() + " (1/" + k.getTeam() + ")";
				}
				else if (titans)
				{
					middle = k.getTitan() == null ? "titan ?" : k.getTitan();
				}
				else
				{
					middle = null;
				}
			}
			if (dropsOnly)
			{
				boolean byPart = boss.hasParts() && k.getPart() != null && k.getPartKc() != null;
				for (com.expecteddrops.tracking.KillEntry.DropEntry d : k.getDrops())
				{
					String after = afterText(d, unit).replace("after ", "");
					rowsPanel.add(byPart
						? historyTwoLines(String.format("%,d", k.getPartKc()), d.getName(), Color.WHITE,
							k.getPart() + (after.isEmpty() ? "" : " \u00b7 " + after), multipleColor(d))
						: historyLine(first, d.getName(), after, Color.WHITE, multipleColor(d)));
				}
			}
			else
			{
				StringBuilder loot = new StringBuilder();
				for (com.expecteddrops.tracking.KillEntry.DropEntry d : k.getDrops())
				{
					loot.append(loot.length() > 0 ? ", " : "").append(d.getName());
				}
				rowsPanel.add(loot.length() == 0
					? historyLine(first, middle, "\u2013", ColorScheme.LIGHT_GRAY_COLOR, ColorScheme.LIGHT_GRAY_COLOR)
					: historyLootLine(first, middle, loot.toString()));
			}
		}
		if (dropsOnly && shown == 0)
		{
			addHistoryNote("No uniques since you installed On Rate yet.");
		}
		int seen = tracker.killsSeen(boss.getName());
		StringBuilder note = new StringBuilder();
		if (raid)
		{
			for (com.expecteddrops.data.BossPart part : boss.getParts())
			{
				double a = tracker.averagePurpleChance(boss.getName(), part.getName());
				if (!Double.isNaN(a))
				{
					note.append(String.format("%s%s raids with points: %.1f%% purple chance on average", note.length() > 0 ? "<br>" : "", part.getName(), a * 100));
				}
			}
		}
		else if (!delve)
		{
			note.append(String.format("Last %,d %s%s seen: %,d unique%s (%s expected)", seen, unit, seen == 1 ? "" : "s",
				drops, drops == 1 ? "" : "s", formatExpected(stats.getAnyRow().getRate() * seen)));
		}
		if (seen >= com.expecteddrops.tracking.Tracker.MAX_KILLS)
		{
			note.append(note.length() > 0 ? "<br>" : "").append("On Rate keeps your last ")
				.append(com.expecteddrops.tracking.Tracker.MAX_KILLS).append(" ").append(unit).append("s here.");
		}
		if (note.length() > 0)
		{
			String tip = dropNames.isEmpty() ? null : new Tip(drops == 1 ? "The unique" : "The uniques")
				.line(String.join("<br>", dropNames.subList(0, Math.min(15, dropNames.size()))))
				.note(dropNames.size() > 15 ? "And " + (dropNames.size() - 15) + " more." : null)
				.wide();
			addHistoryNote(note.toString()).setToolTipText(tip);
		}
	}

	private JLabel addHistoryNote(String text)
	{
		JLabel note = smallLabel("<html><body style='width: 165px'>" + text + "</body></html>", ColorScheme.LIGHT_GRAY_COLOR);
		note.setAlignmentX(LEFT_ALIGNMENT);
		note.setBorder(new EmptyBorder(Theme.S, 0, 0, 0));
		rowsPanel.add(note);
		return note;
	}

	/** A History line with loot: what happened on top, the loot in purple underneath. */
	private JPanel historyLootLine(String first, String middle, String loot)
	{
		return historyTwoLines(first, middle, ColorScheme.LIGHT_GRAY_COLOR, loot, Theme.RAID_PURPLE);
	}

	/** A History line with two rows of text next to the kill count. */
	private JPanel historyTwoLines(String first, String top, Color topColor, String bottom, Color bottomColor)
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
		line.setToolTipText("<html>" + first + " \u00b7 " + top + "<br>" + bottom + "</html>");
		return line;
	}

	/** One History line: kill count on the left, then what happened, then the loot or gap. */
	private JPanel historyLine(String first, String middle, String last, Color middleColor, Color lastColor)
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
		JLabel right = smallLabel(last, lastColor);
		line.add(right, middle == null ? BorderLayout.CENTER : BorderLayout.EAST);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		line.setToolTipText(middle == null ? "<html>" + first + ": " + last + "</html>" : "<html>" + first + " \u00b7 " + middle + "<br>" + last + "</html>");
		return line;
	}

	/** Bosses | Items tabs above the account list. */
	private void renderAccountTabs(boolean withItems)
	{
		JPanel tabs = new JPanel(new GridLayout(1, withItems ? 3 : 2, 2, 0));
		tabs.setOpaque(false);
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		tabs.setBorder(new EmptyBorder(Theme.M, 0, Theme.HAIR, 0));
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
		label.setToolTipText(tab == TAB_ITEMS ? "Your 10 driest and 10 luckiest items across every boss, and your pets"
			: tab == TAB_RECENT ? "Your latest uniques across every boss" : "Every boss you have kills at");
		if (!selected)
		{
			label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			label.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					accountTab = tab;
					refresh();
				}
			});
		}
		return Activatable.install(label, text + " tab" + (selected ? ", selected" : ""), () ->
		{
			accountTab = tab;
			refresh();
		});
	}

	/** The Items tab: the 10 driest (or luckiest) items across every boss, in the chosen luck view. */
	private void renderItemsTab(AccountStats account, ItemView view)
	{
		// a segmented control, visibly a level below the Bosses / Items / Recent tabs
		JPanel switcher = new JPanel(new GridLayout(1, 3, Theme.HAIR, 0));
		switcher.setBackground(Theme.SURFACE);
		switcher.setAlignmentX(LEFT_ALIGNMENT);
		switcher.setBorder(new EmptyBorder(Theme.HAIR, Theme.HAIR, Theme.HAIR, Theme.HAIR));
		switcher.add(topSwitch("Driest", topMode == TOP_DRIEST, TOP_DRIEST));
		switcher.add(topSwitch("Luckiest", topMode == TOP_LUCKIEST, TOP_LUCKIEST));
		switcher.add(topSwitch("Pets", topMode == TOP_PETS, TOP_PETS));
		switcher.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		rowsPanel.add(spacer(Theme.S));
		rowsPanel.add(switcher);
		rowsPanel.add(spacer(Theme.S));

		List<com.expecteddrops.stats.ItemHighlight> top = topItems(account, topMode);
		if (top.isEmpty())
		{
			JLabel none = smallLabel(topMode == TOP_PETS ? "No pets at the bosses you've killed."
				: topMode == TOP_LUCKIEST ? "Nothing above average yet." : "Nothing dry right now!",
				ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(none);
			return;
		}
		if (topMode == TOP_PETS)
		{
			int have = 0;
			int all = 0;
			for (com.expecteddrops.stats.ItemHighlight h : account.getItems())
			{
				if (h.isPet() && h.getRow().getExpected() > 0)
				{
					all++;
					have += h.getRow().isComplete() ? 1 : 0;
				}
			}
			JLabel count = smallLabel(String.format("You have %d of the %d pets at your bosses", have, all), ColorScheme.LIGHT_GRAY_COLOR);
			count.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(count);
		}
		if (view == ItemView.COMPACT && topMode != TOP_PETS)
		{
			rowsPanel.add(compactHeader("Item"));
		}
		Boolean group = null;
		for (com.expecteddrops.stats.ItemHighlight h : top)
		{
			RowStats r = h.getRow();
			if (topMode == TOP_PETS && (group == null || group != r.isComplete()))
			{
				group = r.isComplete();
				JLabel title = smallLabel(group ? "Luckiest pets you have" : "Driest pets you don't have", Color.WHITE);
				title.setFont(FontManager.getRunescapeBoldFont());
				title.setAlignmentX(LEFT_ALIGNMENT);
				title.setBorder(new EmptyBorder(Theme.M, 0, 0, 0));
				title.setToolTipText(group ? "The pets you got with the fewest kills expected: the earliest ones"
					: "The pets you'd most likely have by now, but don't");
				rowsPanel.add(title);
				if (view == ItemView.COMPACT)
				{
					rowsPanel.add(compactHeader("Pet"));
				}
			}
			String where = h.getBoss() == null ? "shared by several bosses" : h.getBoss();
			double luck = r.getLuck();
			StringBuilder tb = new StringBuilder(String.format("<html><b>%s</b> (%s)<br>Got %d, expected %s<br>",
				r.getLabel(), where, r.getReceived(), formatExpected(r.getExpected())));
			if (h.isPet() && !r.isComplete())
			{
				tb.append("Chance you'd have it by now: ").append(formatPercent(r.getChanceAtLeastOne())).append("<br>");
			}
			if (!Double.isNaN(luck))
			{
				tb.append(DropMath.verdict(luck)).append(": ").append(html(sharesLine(r.getShares(), r.getReceived()))).append("<br>");
			}
			String past = rateMultipleLine(r);
			if (past != null)
			{
				tb.append(past).append("<br>");
			}
			if (h.getBoss() != null)
			{
				tb.append("Click to open ").append(h.getBoss());
			}
			String tip = tb.append("</html>").toString();
			Runnable open = h.getBoss() == null ? null : () -> showBoss(h.getBoss());
			if (view == ItemView.COMPACT)
			{
				rowsPanel.add(compactRow(r, tip, open));
			}
			else
			{
				rowsPanel.add(spacer(Theme.HAIR));
				rowsPanel.add(luckRow(r, tip, open, h.isPet() && !r.isComplete()
					? String.format("%s \u00b7 %s", DropMath.verdict(r.getLuck()), formatPercent(r.getChanceAtLeastOne())) : null));
			}
		}
	}

	/**
	 * Boss page: the shared items this boss drops, judged on every boss that drops them
	 * together (the log has one total). Bosses whose kills aren't known yet count as 0.
	 */
	private void renderBossPools(BossStats stats, ItemView view)
	{
		List<com.expecteddrops.stats.SharedPool> pools = tracker.sharedPoolsFor(stats.getBoss().getName(), raidSettings.get());
		if (pools.isEmpty())
		{
			return;
		}
		rowsPanel.add(spacer(Theme.M));
		JLabel title = smallLabel("Shared with other bosses", Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setToolTipText("<html><body style='width: 240px'>These drop from other bosses too, and the collection log has "
			+ "one total for all of them. So each is judged on all those bosses' kills together: the same numbers show on "
			+ "every boss that drops it. Hover one to see which bosses.</body></html>");
		rowsPanel.add(title);
		String page = stats.getBoss().getClogPages().isEmpty() ? stats.getBoss().getName() : stats.getBoss().getClogPages().get(0);
		boolean headerAdded = false;
		List<String> unknown = new ArrayList<>();
		List<String> missingLines = new ArrayList<>();
		for (com.expecteddrops.stats.SharedPool pool : pools)
		{
			if (!pool.isLogKnown())
			{
				// how many you have is unknown until a log page showing it is opened
				unknown.add(pool.getLabel());
				continue;
			}
			RowStats row = pool.toRow();
			String tip = poolTooltip(pool, row);
			if (view == ItemView.COMPACT)
			{
				if (!headerAdded)
				{
					// column titles only when there's a row under them
					rowsPanel.add(compactHeader("Item"));
					headerAdded = true;
				}
				rowsPanel.add(compactRow(row, tip, null));
			}
			else
			{
				rowsPanel.add(spacer(Theme.HAIR));
				rowsPanel.add(targetRow(row, tip));
			}
			List<String> missing = new ArrayList<>(new java.util.TreeSet<>(pool.getMissingPages()));
			missing.remove(page);
			if (!missing.isEmpty())
			{
				missingLines.add(String.format("%s also drops from %s. If you've killed %s, open %s log page%s so those kills count too.",
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
			JLabel line = smallLabel("<html><body style='width: 165px'>" + text + "</body></html>", ColorScheme.LIGHT_GRAY_COLOR);
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
		List<Double> lambdas = new ArrayList<>();
		List<Long> itemPrices = new ArrayList<>();
		List<Integer> itemCaps = new ArrayList<>();
		double expected = 0;
		long received = 0;
		for (int i = 0; i < stats.getBoss().getDrops().size(); i++)
		{
			DropDefinition drop = stats.getBoss().getDrops().get(i);
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
			expected += DropMath.expectedValue(row.getExpected(), row.getCap()) * price;
			received += row.getReceived() * price;
		}
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
		double luck = DropMath.valueLuck(l, p, c, received, 5000, 1234567L);
		if (Double.isNaN(luck))
		{
			return null;
		}
		lastValueTip = new Tip("GP value \u00b7 " + DropMath.verdict(luck))
			.line("Got " + DropMath.formatGp(received) + ", expected " + DropMath.formatGp((long) expected))
			.line("By value: " + capital(luckPercent(luck)) + " for your kill count")
			.note("The Uniques tile counts how many you got; this one weighs them by price. "
				+ "Pets, jars and other untradeables count as 0 GP.")
			.wide();
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
		return String.format("Your %s count as %s. Change it under <i>Team bosses</i> in the settings.",
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
		StringBuilder averages = new StringBuilder();
		List<com.expecteddrops.data.BossPart> parts = boss.getParts();
		for (com.expecteddrops.data.BossPart part : parts)
		{
			measured += tracker.measuredRaids(boss.getName(), part.getName());
			double avg = tracker.averagePurpleChance(boss.getName(), part.getName());
			if (!Double.isNaN(avg) && tracker.measuredRaids(boss.getName(), part.getName()) >= 5)
			{
				averages.append(String.format(" Your %s raids with points averaged <b>%.1f%%</b>.", part.getName(), avg * 100));
			}
		}
		if ("tob".equals(boss.getRaid()))
		{
			// ToB has no points; raids with the end-of-raid board use their real team size
			measured = 0;
			for (com.expecteddrops.tracking.RaidRecord r : tracker.raidRecords(boss.getName()))
			{
				measured += r.getTeamSize() > 0 ? 1 : 0;
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
				advice = "Big teams or fewer points? Lower it under <i>Raid purple chances</i> in the settings (8,676 of your points is about 1%).";
				break;
			case "tob":
				advice = "Check <i>ToB team size</i> in the settings.";
				break;
			default:
				advice = "Set your usual chance under <i>Raid purple chances</i> in the settings.";
		}
		lastHintTip = String.format("<html><body style='width: 240px'>%,d of your %,d raids have no points On Rate could read "
			+ "(raids from before you installed it, for example), so they use an average purple chance from the settings. "
			+ "Raids from now on use your own points.%s<br><br>%s</body></html>",
			fromSettings, stats.getKillCount(), averages, advice);
		return String.format("%,d of %,d raids use an average purple chance, not your points. Hover for more.",
			fromSettings, stats.getKillCount());
	}

	/** Hover text for the line {@link #settingsHint} returns. */
	private String lastHintTip;

	/** Items need at least this many expected to count as dry: 0 of 0.2 says nothing yet. */
	private static final double MIN_EXPECTED_DRY = 1.0;
	private static final int TOP_ITEMS = 10;

	/**
	 * The driest (or luckiest) items across every boss: driest have at least one expected,
	 * luckiest are ones you have. Ranked by luck percentile.
	 */
	public static List<com.expecteddrops.stats.ItemHighlight> topItems(AccountStats account, int mode)
	{
		List<com.expecteddrops.stats.ItemHighlight> list = new ArrayList<>();
		for (com.expecteddrops.stats.ItemHighlight h : account.getItems())
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
			List<com.expecteddrops.stats.ItemHighlight> lucky = new ArrayList<>();
			List<com.expecteddrops.stats.ItemHighlight> dry = new ArrayList<>();
			for (com.expecteddrops.stats.ItemHighlight h : list)
			{
				(h.getRow().isComplete() ? lucky : dry).add(h);
			}
			lucky.sort(Comparator.comparingDouble((com.expecteddrops.stats.ItemHighlight h) -> h.getRow().getExpected())
				.thenComparing(h -> h.getRow().getLabel()));
			dry.sort(Comparator.comparingDouble((com.expecteddrops.stats.ItemHighlight h) -> -h.getRow().getChanceAtLeastOne())
				.thenComparing(h -> h.getRow().getLabel()));
			List<com.expecteddrops.stats.ItemHighlight> result = new ArrayList<>(lucky.subList(0, Math.min(TOP_PETS_SHOWN, lucky.size())));
			result.addAll(dry.subList(0, Math.min(TOP_PETS_SHOWN, dry.size())));
			return result;
		}
		Comparator<com.expecteddrops.stats.ItemHighlight> byLuck = Comparator.comparingDouble(h -> h.getRow().getLuck());
		list.sort(mode == TOP_LUCKIEST ? byLuck.reversed() : byLuck);
		return list.size() > TOP_ITEMS ? new ArrayList<>(list.subList(0, TOP_ITEMS)) : list;
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
					refresh();
				}
			});
		}
		return Activatable.install(label, text + (selected ? ", selected" : ""), () ->
		{
			topMode = mode;
			refresh();
		});
	}

	private static String poolTooltip(com.expecteddrops.stats.SharedPool pool, RowStats row)
	{
		StringBuilder sb = new StringBuilder("<html><body style='width: 240px'><b>").append(pool.getLabel()).append("</b><br>");
		sb.append("Dropped by: ").append(String.join(", ", pool.getBosses())).append("<br>");
		sb.append(String.format("Expected from all of them: %s<br>In your collection log: %d<br>",
			formatExpected(pool.getExpected()), pool.getReceived()));
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			sb.append(DropMath.verdict(luck)).append(": ").append(html(sharesLine(row.getShares(), row.getReceived())));
		}
		if (!pool.isComplete())
		{
			sb.append("<br><br>Kills not counted yet (open these log pages): ").append(String.join(", ", pool.getMissingPages()));
		}
		return sb.append("</body></html>").toString();
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
			case KILLS:
				return Comparator.comparingInt((RowStats r) -> -account.getKillCount(r.getLabel())).thenComparing(byName);
			case NAME:
				return byName;
			case STREAK:
				// longest since the last unique first, in multiples of the boss's unique rate
				return Comparator.comparingDouble((RowStats r) -> Double.isNaN(r.getStreakMultiple()) ? 1 : -r.getStreakMultiple())
					.thenComparing(byName);
			default:
				return Comparator.comparingDouble((RowStats r) -> -r.getExpected()).thenComparing(byName);
		}
	}

	/** One line for the overview: GE value received / expected and its verdict (details on hover). */
	private static String valueText(AccountStats account)
	{
		double luck = account.getValueLuck();
		if (account.getExpectedValue() <= 0 || Double.isNaN(luck))
		{
			return "GE value: prices not loaded yet";
		}
		String color = String.format("#%06x", luckColor(luck).brighter().getRGB() & 0xFFFFFF);
		return String.format("<html>GE value: %s / %s \u00b7 <font color='%s'>%s</font></html>",
			DropMath.formatGp(account.getReceivedValue()), DropMath.formatGp(account.getExpectedValue()),
			color, DropMath.verdict(luck));
	}

	private static String valueTooltip(AccountStats account)
	{
		double luck = account.getValueLuck();
		if (account.getExpectedValue() <= 0 || Double.isNaN(luck))
		{
			return null;
		}
		return new Tip("GP value \u00b7 " + DropMath.verdict(luck))
			.line("Got " + DropMath.formatGp(account.getReceivedValue()) + ", expected " + DropMath.formatGp(account.getExpectedValue()))
			.line("By value: " + capital(luckPercent(luck)) + " for your kill counts")
			.note("RuneLite's own price list. Pets, jars and other untradeables count as 0 GP.")
			.wide();
	}

	/** "kills", "raids", "delves" or "chests" for a boss by name. */
	private String unitsOf(String bossName)
	{
		BossDefinition boss = tracker.getCatalog().byName(bossName);
		return boss == null ? "kills" : com.expecteddrops.tracking.Tracker.unit(boss);
	}

	private String accountTooltip(RowStats row, AccountStats account)
	{
		Tip tip = new Tip(row.isAny() ? "Whole account" : row.getShownLabel());
		tip.line(row.isAny() ? account.getRows().size() + " bosses with kills"
			: String.format("%,d %s", account.getKillCount(row.getLabel()), unitsOf(row.getLabel())));
		tip.line(String.format("Got %d uniques, expected %s", row.getReceived(), formatExpected(row.getExpected())));
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			tip.line(DropMath.verdict(luck) + ": " + luckPercent(row.getShares(), row.getReceived()), luckColor(luck).brighter());
			tip.line(capital(sharesLine(row.getShares(), row.getReceived())));
		}
		if (!row.isAny() && row.getDriestItem() != null)
		{
			tip.line(String.format("Driest right now: %s at %s the rate", row.getDriestItem(), formatMultiple(row.getDriestMultiple())));
		}
		if (!row.isAny())
		{
			tip.note("Click to open " + row.getLabel() + ".");
		}
		return tip.wide();
	}

	// ---------------------------------------------------------------- item views

	private static final int SMALL_ICON = 20;
	/** Bosses with fewer uniques expected than this aren't listed in the account overview. */
	private static final double MIN_EXPECTED_SHOWN = 0.05;

	/** "?" help for a boss page: how to read the chosen view. */
	private static String bossHelp(ItemView view)
	{
		String how;
		switch (view)
		{
			case DELVE_BARS:
				how = "<b>Expected</b>: the number is how many you should have by now, the bar your progress to the next one."
					+ "<br><b>Received</b>: the number is how many you got, the bar how lucky that is.";
				break;
			case COMPACT:
				how = "Each item shows how many you got / should have by now, and how lucky that is "
					+ "(a luck %: 50% is average, low is dry, high is spooned; hover for how many players have fewer or more).";
				break;
			case RINGS:
				how = "Each ring fills up as your chance of having the item grows. Once you have it, the ring shows "
					+ "your chance of the next one by now.";
				break;
			case GAUGE:
				how = "The needle shows your luck from dry to spooned; each item is a dot on the same scale.";
				break;
			default:
				how = "The fill is how many you got; the white line is how many you should have by now.";
		}
		return "<html><body style='width: 240px'>" + how + "<br><br>Hover an item for its drop rate, your chances "
			+ "and your dry streak. Right-click one to leave it out of the calculations (a drop you got elsewhere, "
			+ "or jars you don't care about). Click \u21c4 next to ITEMS for another view."
			+ FEEDBACK_LINE + "</body></html>";
	}

	private void renderBossItems(BossStats stats, String unit, ItemView view)
	{
		String bossName = stats.getBoss().getName();
		List<RowStats> all = new ArrayList<>();
		all.add(stats.getAnyRow());
		// items the player left out go last, in a section of their own
		List<RowStats> leftOut = new ArrayList<>();
		for (RowStats r : stats.getRows())
		{
			(tracker.isLeftOut(bossName, r.getIconItem()) ? leftOut : all).add(r);
		}
		if (view != ItemView.TARGET_BARS)
		{
			rowsPanel.add(itemsHeader("ITEMS", view, null, null));
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
					rowsPanel.add(withLeaveOutMenu(compactRow(r, tooltip(r, stats, unit), null), bossName, r, false));
				}
				break;
			case RINGS:
				JPanel grid = new JPanel(new GridLayout(0, 3, 4, 8));
				grid.setOpaque(false);
				grid.setAlignmentX(LEFT_ALIGNMENT);
				for (RowStats r : all)
				{
					grid.add(withLeaveOutMenu(itemRing(r, tooltip(r, stats, unit)), bossName, r, false));
				}
				grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
				rowsPanel.add(grid);
				break;
			case GAUGE:
				LuckGauge gauge = new LuckGauge();
				gauge.setLuck(stats.getAnyRow().getLuck());
				rowsPanel.add(gauge);
				for (RowStats r : all)
				{
					rowsPanel.add(spacer(Theme.HAIR));
					rowsPanel.add(withLeaveOutMenu(stripRow(r.isAny() ? null : r.getIconItem(), r.isAny() ? "Any unique" : r.getLabel(),
						r.isAny(), r.isComplete() ? 1.0 : r.getLuck(), tooltip(r, stats, unit), null), bossName, r, false));
				}
				break;
			default:
				rowsPanel.add(itemsHeader("ITEMS", view, "bar = got \u00b7 line = expected",
					"The bar is how many you got. The white line is how many you should have by now: a bar that stops short "
						+ "of it is behind, one that reaches past it is ahead. Rare items have their line near the start. "
						+ "Right-click an item to leave it out of the calculations."));
				for (int i = 0; i < all.size(); i++)
				{
					if (i > 0)
					{
						rowsPanel.add(spacer(Theme.HAIR));
					}
					rowsPanel.add(withLeaveOutMenu(targetRow(all.get(i), tooltip(all.get(i), stats, unit)), bossName, all.get(i), false));
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
			.line(String.format("Got %d, expected %s", r.getReceived(), formatExpected(r.getExpected())))
			.note("Left out of " + bossName + "'s verdicts, the account overview, the Items tab and dry messages. "
				+ "Right-click to count it again.")
			.wide();
		setTip(panel, tip, null);
		return panel;
	}

	/**
	 * Adds the right-click menu to an item: "Leave out of the calculations", or "Count it again"
	 * for one that's left out. The "Any unique" line has none.
	 */
	private <T extends java.awt.Component> T withLeaveOutMenu(T c, String bossName, RowStats r, boolean leftOut)
	{
		if (r.isAny() || r.getIconItem() == null)
		{
			return c;
		}
		JPopupMenu menu = new JPopupMenu();
		JMenuItem item = new JMenuItem(leftOut ? "Count it again" : "Leave out of the calculations");
		item.addActionListener(e ->
		{
			tracker.setLeftOut(bossName, r.getIconItem(), !leftOut);
			save.run();
			refresh();
		});
		menu.add(item);
		addPopup(c, menu);
		return c;
	}

	/** Shows the menu on a right-click anywhere on the component, its labels included. */
	private static void addPopup(java.awt.Component c, JPopupMenu menu)
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
				addPopup(child, menu);
			}
		}
	}

	/** Short names of the item views, for the switch next to ITEMS. */
	private static String viewName(ItemView view)
	{
		switch (view)
		{
			case DELVE_BARS:
				return "tabs";
			case COMPACT:
				return "table";
			case RINGS:
				return "rings";
			case GAUGE:
				return "gauge";
			default:
				return "bars";
		}
	}

	/**
	 * The title over a list ("ITEMS"), with the view switch next to it ("\u21c4 bars": click for
	 * the next view, remembered) and an optional key on the right.
	 */
	private JPanel itemsHeader(String title, ItemView view, String key, String keyTip)
	{
		JPanel panel = new JPanel(new BorderLayout(6, 0));
		panel.setOpaque(false);
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(new EmptyBorder(0, 0, Theme.S, 0));
		JPanel left = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
		left.setOpaque(false);
		left.add(smallLabel(title + "  ", Theme.TEXT_MUTED));
		ItemView[] views = ItemView.values();
		ItemView next = views[(view.ordinal() + 1) % views.length];
		JLabel change = smallLabel("\u21c4 " + viewName(view), ColorScheme.LIGHT_GRAY_COLOR);
		change.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		change.setToolTipText(new Tip("Change the look").line("Now: " + view)
			.line("Click for " + next + ". The look you pick is kept, here and in the account overview.").small());
		change.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setItemView.accept(next);
				refresh();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				change.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				change.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		Activatable.install(change, "Change the look, now " + viewName(view), () ->
		{
			setItemView.accept(next);
			refresh();
		});
		left.add(change);
		panel.add(left, BorderLayout.WEST);
		if (key != null)
		{
			JLabel right = smallLabel(key, Theme.TEXT_MUTED);
			right.setToolTipText(new Tip(null).line(keyTip).small());
			panel.add(right, BorderLayout.EAST);
		}
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

	private void renderAccountItems(AccountStats account, RowStats total, List<RowStats> rows, ItemView view)
	{
		if (rows.isEmpty())
		{
			// nothing to list yet: no empty table headers
			return;
		}
		rowsPanel.add(itemsHeader("BOSSES", view, null, null));
		switch (view)
		{
			case DELVE_BARS:
				rowsPanel.add(buildRow(total, accountTooltip(total, account), null));
				for (RowStats row : rows)
				{
					rowsPanel.add(spacer(Theme.HAIR));
					rowsPanel.add(buildRow(row, accountTooltip(row, account), () -> showBoss(row.getLabel())));
				}
				break;
			case COMPACT:
				rowsPanel.add(compactHeader("Boss"));
				for (RowStats row : rows)
				{
					rowsPanel.add(compactRow(row, accountTooltip(row, account), () -> showBoss(row.getLabel())));
				}
				break;
			case RINGS:
				JPanel grid = new JPanel(new GridLayout(0, 3, 4, 8));
				grid.setOpaque(false);
				grid.setAlignmentX(LEFT_ALIGNMENT);
				for (RowStats row : rows)
				{
					grid.add(bossRing(row, accountTooltip(row, account)));
				}
				grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, grid.getPreferredSize().height));
				rowsPanel.add(grid);
				break;
			case GAUGE:
				LuckGauge gauge = new LuckGauge();
				gauge.setLuck(total.getLuck());
				rowsPanel.add(gauge);
				for (RowStats row : rows)
				{
					rowsPanel.add(spacer(Theme.HAIR));
					rowsPanel.add(stripRow(row.getIconItem(), row.getShownLabel(), false, row.getLuck(),
						accountTooltip(row, account), () -> showBoss(row.getLabel())));
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
					rowsPanel.add(luckRow(row, accountTooltip(row, account), () -> showBoss(row.getLabel()),
						streak ? streakText(row) : null, streak ? streakColor(row) : null, true));
				}
		}
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
		return String.format("%,d / %s", r.getReceived(), formatExpected(r.getExpected()));
	}

	/** Item row: name and "got / expected" on top, a bar with the expected marker below. */
	private JPanel targetRow(RowStats r, String tip)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setBackground(Theme.SURFACE);
		panel.setBorder(new EmptyBorder(Theme.HAIR, Theme.S, Theme.S, Theme.S));
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
			name = rowLabel(r.getShownLabel(), Color.WHITE);
		}
		line.add(name, BorderLayout.CENTER);
		boolean have = r.isComplete();
		line.add(rowLabel(have ? "obtained" : gotOfExpected(r), Color.WHITE), BorderLayout.EAST);
		panel.add(line, BorderLayout.CENTER);

		TargetBar bar = new TargetBar();
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
		return panel;
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
		return luckRow(r, tip, onClick, right, rightColor, false);
	}

	/** With {@code counts}, the verdict has what you got out of what you should have under it. */
	private JPanel luckRow(RowStats r, String tip, Runnable onClick, String right, Color rightColor, boolean counts)
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
		line.add(rowLabel(r.getShownLabel(), Theme.TEXT), BorderLayout.CENTER);
		String text = right != null ? right : Double.isNaN(luck) ? "-" : DropMath.verdict(luck);
		Color color = rightColor != null ? rightColor
			: "-".equals(text) || Double.isNaN(luck) ? Theme.TEXT_MUTED : luckColor(luck).brighter();
		JLabel verdictText = rowLabel(text, color);
		if (counts && !Double.isNaN(luck))
		{
			JPanel stacked = new JPanel();
			stacked.setLayout(new BoxLayout(stacked, BoxLayout.Y_AXIS));
			stacked.setOpaque(false);
			verdictText.setAlignmentX(RIGHT_ALIGNMENT);
			JLabel got = smallLabel(gotOfExpected(r), Theme.TEXT_MUTED);
			got.setAlignmentX(RIGHT_ALIGNMENT);
			stacked.add(verdictText);
			stacked.add(got);
			line.add(stacked, BorderLayout.EAST);
		}
		else
		{
			line.add(verdictText, BorderLayout.EAST);
		}
		body.add(line, BorderLayout.CENTER);
		LuckStrip strip = new LuckStrip();
		strip.setLuck(luck);
		strip.setPreferredSize(new Dimension(80, 12));
		body.add(strip, BorderLayout.SOUTH);
		panel.add(body, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
		setTip(panel, tip, onClick);
		return panel;
	}

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
		return luck >= 0.35 ? Theme.TEXT : luckColor(luck).brighter();
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
			panel.add(itemIcon(r.getIconItem(), r.getLabel(), 16), BorderLayout.WEST);
			panel.add(smallLabel(r.getShownLabel(), Color.WHITE), BorderLayout.CENTER);
		}
		JPanel right = new JPanel(new BorderLayout(6, 0));
		right.setOpaque(false);
		boolean have = r.isComplete();
		right.add(smallLabel(have ? (r.getCap() > 1 ? "have both" : "have it") : gotOfExpected(r), Color.WHITE), BorderLayout.CENTER);
		double luck = r.getLuck();
		right.add(new Chip(Double.isNaN(luck) ? "-" : pct(luck), luckColor(luck)), BorderLayout.EAST);
		panel.add(right, BorderLayout.EAST);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		setTip(panel, tip, onClick);
		return panel;
	}

	/** Name on the left, the dry-to-spooned scale with your dot on the right. */
	private JPanel stripRow(String item, String label, boolean any, double luck, String tip, Runnable onClick)
	{
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(Theme.S, Theme.S, Theme.S, Theme.S));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JPanel left = new JPanel(new BorderLayout(4, 0));
		left.setOpaque(false);
		if (!any)
		{
			left.add(itemIcon(item, label, 16), BorderLayout.WEST);
		}
		left.add(any ? boldLabel(label) : smallLabel(label, Theme.TEXT), BorderLayout.CENTER);
		left.setPreferredSize(new Dimension(110, 18));
		panel.add(left, BorderLayout.WEST);
		LuckStrip strip = new LuckStrip();
		strip.setLuck(luck);
		panel.add(strip, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		setTip(panel, tip, onClick);
		return panel;
	}

	/** Item ring: chance of having it by now, or of the next one once you have it. */
	private JPanel itemRing(RowStats r, String tip)
	{
		LuckRing ring = new LuckRing();
		String sub;
		boolean have = r.isComplete();
		if (have)
		{
			ring.set(1, Palette.good());
			sub = "obtained";
		}
		else if (r.getReceived() > 0 && r.getStreak() != null && r.getRate() > 0)
		{
			double m = r.getStreakMultiple();
			double p = -Math.expm1(-m);
			ring.set(p, luckColor(Math.exp(-m)));
			sub = String.format("next: %.0f%%", p * 100);
		}
		else
		{
			double p = r.getChanceAtLeastOne();
			ring.set(p, luckColor(r.getLuck()));
			sub = String.format("%.0f%% by now", p * 100);
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
		return ringCell(ring, r.isAny() ? "Any unique" : r.getLabel(), sub, tip, null);
	}

	/** Boss ring in the account overview: filled to your luck percentile. */
	private JPanel bossRing(RowStats r, String tip)
	{
		LuckRing ring = new LuckRing();
		double luck = r.getLuck();
		ring.set(Double.isNaN(luck) ? 0 : luck, luckColor(luck));
		ringPicture(ring, r.getIconItem(), r.getLabel());
		return ringCell(ring, r.getShownLabel(), Double.isNaN(luck) ? "-" : DropMath.verdict(luck), tip, () -> showBoss(r.getLabel()));
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

	private JPanel ringCell(LuckRing ring, String name, String sub, String tip, Runnable onClick)
	{
		JPanel cell = new JPanel(new BorderLayout(0, 1));
		cell.setOpaque(false);
		cell.add(ring, BorderLayout.CENTER);
		JPanel labels = new JPanel();
		labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
		labels.setOpaque(false);
		// a long name wraps to a second line instead of being cut off
		JLabel n = smallLabel("<html><div style='text-align: center; width: " + RING_NAME_WIDTH + "px'>" + html(name.replace("&", "&amp;")) + "</div></html>",
			Color.WHITE);
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
		Color normal = panel.getBackground();
		MouseAdapter click = onClick == null ? null : new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
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

	/** "This trip" card: kills, uniques and expected since the trip started. */
	private void addTripCard(String bossName)
	{
		com.expecteddrops.tracking.TripStats trip = tracker.tripStats();
		if (trip == null || !trip.getBoss().equals(bossName))
		{
			return;
		}
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE), new EmptyBorder(Theme.S, Theme.M, Theme.M, Theme.M)));
		card.setAlignmentX(LEFT_ALIGNMENT);

		long minutes = Math.max(0, (trip.getLastMillis() - trip.getStartMillis()) / 60_000);
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		head.add(smallLabel("THIS TRIP", ColorScheme.BRAND_ORANGE), BorderLayout.WEST);
		head.add(smallLabel(minutes >= 60 ? String.format("%dh %02dm", minutes / 60, minutes % 60) : minutes + " min",
			ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		card.add(head);
		card.add(spacer(Theme.S));

		JPanel tiles = new JPanel(new GridLayout(1, 3, 4, 0));
		tiles.setOpaque(false);
		tiles.setAlignmentX(LEFT_ALIGNMENT);
		BossDefinition tripBoss = tracker.getCatalog().byName(bossName);
		String units = tripBoss == null ? "kills" : com.expecteddrops.tracking.Tracker.unit(tripBoss);
		tiles.add(tile(Character.toUpperCase(units.charAt(0)) + units.substring(1), String.format("%,d", trip.getKills())));
		tiles.add(tile("Uniques", String.valueOf(trip.getReceived())));
		tiles.add(tile("Expected", formatExpected(trip.getExpected())));
		card.add(tiles);
		card.add(spacer(Theme.S));

		double perHour = trip.getKillsPerHour();
		if (!Double.isNaN(perHour))
		{
			JLabel rate = smallLabel(String.format("%.1f %s per hour", perHour, units), Color.WHITE);
			rate.setAlignmentX(LEFT_ALIGNMENT);
			card.add(rate);
		}
		// this trip's own kills (a delve level 1 trip is worth nothing), else the boss's average
		double tripValue = options.get().isGeValue() ? tracker.tripValue(priceOf) : Double.NaN;
		double perKill = !options.get().isGeValue() ? 0
			: !Double.isNaN(tripValue) && trip.getKills() > 0 ? tripValue / trip.getKills() : uniqueValuePerKill(bossName);
		// a trip worth nothing says so (delve level 1 has no uniques: 0 gp/h)
		// (only when prices are known: the boss's uniques are worth something on average)
		if (!Double.isNaN(perHour) && (perKill > 0 || !Double.isNaN(tripValue) && uniqueValuePerKill(bossName) > 0))
		{
			JLabel worth = smallLabel(String.format("<html>Unique value: <font color='#ffffff'>~%s/h</font> expected</html>",
				DropMath.formatGp(perKill * perHour)), ColorScheme.LIGHT_GRAY_COLOR);
			worth.setToolTipText(String.format("<html><body style='width: 240px'>What the uniques should be worth per hour at "
				+ "this trip's speed: about %s per %s. Only uniques count; normal loot (bones, hides, runes...) "
				+ "isn't included, so this is not your full GP per hour. Untradeables count as 0 GP.%s</body></html>",
				DropMath.formatGp(perKill), units.substring(0, units.length() - 1), mainValueNote(bossName)));
			worth.setAlignmentX(LEFT_ALIGNMENT);
			card.add(worth);
		}
		double luck = trip.getLuck();
		if (!Double.isNaN(luck))
		{
			JLabel v = smallLabel(String.format("%s this trip (%s)", DropMath.verdict(luck), luckShort(trip.getShares(), trip.getReceived())),
				luckColor(luck).brighter());
			v.setToolTipText(capital(luckPercent(trip.getShares(), trip.getReceived())) + " for a trip like this");
			v.setAlignmentX(LEFT_ALIGNMENT);
			card.add(v);
		}
		if (!trip.getDrops().isEmpty())
		{
			JLabel d = smallLabel("<html><body style='width: 180px'>Drops: " + String.join(", ", trip.getDrops())
				+ "</body></html>", Color.WHITE);
			d.setAlignmentX(LEFT_ALIGNMENT);
			card.add(d);
		}
		card.add(spacer(Theme.S));
		JButton reset = new JButton("Start a new trip");
		reset.setFont(FontManager.getRunescapeSmallFont());
		reset.setFocusPainted(false);
		reset.setAlignmentX(LEFT_ALIGNMENT);
		reset.setToolTipText("The next kill starts a new trip. A trip also ends after 30 minutes without a kill "
			+ "(2 hours for raids, the Inferno and the Fight Caves).");
		reset.addActionListener(e ->
		{
			tracker.resetTrip();
			refresh();
		});
		card.add(reset);
		if (trip.getKills() >= OVERLAY_OFFER_KILLS || minutes >= OVERLAY_OFFER_MINUTES)
		{
			addOverlayOffer(card);
		}
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		rowsPanel.add(card);
		rowsPanel.add(spacer(Theme.M));
	}

	/** The account overview's "Last unique 12 days ago: Vorkath's head." line, from History. */
	private void showLastUnique()
	{
		lastUniqueText = null;
		com.expecteddrops.tracking.Tracker.LastUnique last = tracker.lastUnique();
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
			String when = days <= 0 ? "today" : days == 1 ? "yesterday" : String.format("%,d days ago", days);
			text = String.format("Last unique %s: %s (%s).", when, last.item, last.boss);
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
		for (int i = 0; i < stats.getBoss().getDrops().size(); i++)
		{
			DropDefinition drop = stats.getBoss().getDrops().get(i);
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
		for (int i = 0; i < stats.getBoss().getDrops().size(); i++)
		{
			DropDefinition drop = stats.getBoss().getDrops().get(i);
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
		welcomePanel.setLayout(new BorderLayout(0, 6));
		welcomePanel.setAlignmentX(LEFT_ALIGNMENT);
		welcomePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		welcomePanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
			new EmptyBorder(Theme.M, Theme.M, Theme.M, Theme.M)));

		JLabel text = new JLabel("<html><body style='width: 140px'>"
			+ "<b>Welcome to On Rate!</b><br><br>"
			+ "<b>1.</b> Open your <b>collection log</b> and click the pages of the bosses you do. "
			+ "That loads your past drops.<br><br>"
			+ "<b>2.</b> Go kill something. The panel follows your kills (keep the <b>Loot Tracker</b> plugin on).<br><br>"
			+ "<b>3.</b> <i>Got it</i> opens the settings: check <i>Team bosses</i> if you solo Royal Titans or Yama. "
			+ "Another look for the lists is one click on \u21c4 next to ITEMS.<br><br>"
			+ "Hover the <b>?</b> for help."
			+ "</body></html>");
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		welcomePanel.add(text, BorderLayout.CENTER);

		JButton gotIt = new JButton("Got it");
		gotIt.setFont(FontManager.getRunescapeSmallFont());
		gotIt.setFocusPainted(false);
		gotIt.setToolTipText("Hide this guide and open On Rate's settings. Turn the guide back on with 'Show welcome guide' there.");
		gotIt.addActionListener(e ->
		{
			dismissWelcome.run();
			welcomePanel.setVisible(false);
			revalidate();
			repaint();
		});
		JPanel buttonRow = new JPanel(new BorderLayout());
		buttonRow.setOpaque(false);
		buttonRow.add(gotIt, BorderLayout.WEST);
		welcomePanel.add(buttonRow, BorderLayout.SOUTH);
		welcomePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, welcomePanel.getPreferredSize().height));
	}

	/** Header "Collection log: 12 / 49 synced" plus, when expanded, the pages still to open. */
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

		setupList.removeAll();
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
		Color statusColor = Theme.ACCENT;
		if (killed > 0)
		{
			status = String.format("Load %d boss%s you killed", killed, killed == 1 ? "" : "es");
		}
		else if (done == 0)
		{
			status = "Load your bosses from the log";
		}
		else
		{
			status = String.format("Collection log: %d page%s loaded", done, done == 1 ? "" : "s");
			statusColor = ColorScheme.LIGHT_GRAY_COLOR;
		}
		setupHeader.setText((setupExpanded ? "\u25be " : "\u25b8 ") + status + (setupExpanded ? "" : " (show)"));
		setupHeader.setForeground(statusColor);
		setupHeader.setToolTipText("<html><body style='width: 240px'>Open these pages in your collection log to load your "
			+ "kill count and past drops. Only the bosses you do matter; you don't need all " + checklist.size() + ".</body></html>");
		setupHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setupList.setVisible(setupExpanded);
		if (!setupExpanded)
		{
			return;
		}
		JLabel intro = new JLabel("<html><body style='width: 165px'>Open these in your collection log. "
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
		return label;
	}

	private JPanel row(RowStats row, BossStats stats, String unit)
	{
		return buildRow(row, tooltip(row, stats, unit), null);
	}

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
				bar.set(progress, Theme.PROGRESS, name, String.format("%.0f%%", progress * 100));
			}
			else
			{
				bar.set(progress, Theme.PROGRESS, String.format("%.1f%%", progress * 100));
			}
		}
		else
		{
			number = row.getReceived();
			if (name != null)
			{
				bar.set(luck, luckColor(luck), name, Double.isNaN(luck) ? "-" : DropMath.verdict(luck));
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
		return panel;
	}

	private static String tooltip(RowStats row, BossStats stats, String unit)
	{
		Tip tip = new Tip(row.isAny() ? "Any unique" : row.getLabel());
		DropDefinition drop = row.isAny() ? null : findDrop(stats.getBoss(), row.getLabel());
		if (drop != null && drop.hasLevelRates() && stats.getBoss().getWaves() > 0)
		{
			// a run counts every wave up to the claim: the chance by the wave you claim at
			StringBuilder rates = new StringBuilder("Chance over a run, by the wave you claim at:");
			boolean first = true;
			for (int wave = 1; wave <= stats.getBoss().getWaves(); wave++)
			{
				double cumulative = com.expecteddrops.tracking.Tracker.waveRate(drop, wave);
				if (cumulative > 0)
				{
					rates.append(first ? " " : ", ").append(wave).append(": ").append(DropMath.formatRate(cumulative));
					first = false;
				}
			}
			tip.line(rates.toString());
		}
		else if (drop != null && drop.hasLevelRates())
		{
			StringBuilder rates = new StringBuilder("Rate per delve level:");
			boolean first = true;
			for (int level = 1; level <= BossDefinition.DELVE_LEVELS; level++)
			{
				String rate = drop.getLevelRate(level);
				if (rate != null)
				{
					rates.append(first ? " " : ", ").append(BossDefinition.delveLevelLabel(level)).append(": ")
						.append(DropMath.formatRate(DropMath.parseRate(rate)));
					first = false;
				}
			}
			tip.line(rates.toString());
		}
		else if (drop != null && drop.hasPartRates())
		{
			StringBuilder rates = new StringBuilder();
			for (java.util.Map.Entry<String, Integer> e : stats.getPartCounts().entrySet())
			{
				String rate = drop.getPartRate(e.getKey());
				if (rate != null)
				{
					rates.append(rates.length() > 0 ? ", " : "").append(e.getKey()).append(" ")
						.append(DropMath.formatRate(DropMath.parseRate(rate)));
				}
			}
			tip.line("Rate per " + unit + ": " + rates);
		}
		else if (!row.isAny())
		{
			tip.line(DropMath.formatRate(row.getRate()) + " per " + unit);
		}
		tip.line(String.format("Got %d, expected %s \u00b7 %s chance of one by now", row.getReceived(),
			formatExpected(row.getExpected()), formatPercent(row.getChanceAtLeastOne())));
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			tip.line(String.format("%s at %,d %ss: %s", DropMath.verdict(luck), stats.getKillCount(), unit,
				luckPercent(row.getShares(), row.getReceived())), luckColor(luck).brighter());
			tip.line(capital(sharesLine(row.getShares(), row.getReceived())));
		}
		tip.line(rateMultipleLine(row));
		if (!row.isAny() && row.getRate() > 0 && (drop == null || (!drop.hasLevelRates() && !drop.hasPartRates())))
		{
			long half = DropMath.killsForChance(row.getRate(), 0.5);
			long most = DropMath.killsForChance(row.getRate(), 0.9);
			Integer sure = drop == null ? null : drop.getGuaranteedAt();
			if (sure != null)
			{
				// the guaranteed kill makes it certain by then (Vorkath's 50th)
				half = Math.min(half, sure);
				most = Math.min(most, sure);
			}
			tip.line(String.format("50%% / 90%% chance by %,d / %,d %ss", half, most, unit));
		}
		tip.note(streakNote(row, unit));
		if (row.getFastest() != null)
		{
			tip.note(row.getFastest().equals(row.getLongest())
				? String.format("Since On Rate: one drop took %,d %s.", row.getFastest(), unit + (row.getFastest() == 1 ? "" : "s"))
				: String.format("Since On Rate: fastest drop after %,d %s, longest after %,d.", row.getFastest(),
					unit + (row.getFastest() == 1 ? "" : "s"), row.getLongest()));
		}
		if (drop != null && drop.getAlsoFrom() != null)
		{
			tip.note("Also drops from " + drop.getAlsoFrom() + ", and the collection log counts those too. "
				+ "Got yours there? Right-click to leave it out.");
		}
		return tip.wide();
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

	private static DropDefinition findDrop(BossDefinition boss, String label)
	{
		for (DropDefinition drop : boss.getDrops())
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

	private static javax.swing.border.Border tabBorder(boolean selected)
	{
		return BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? Theme.ACCENT : ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(Theme.S, 0, Theme.HAIR, 0));
	}

	private void resetSelected()
	{
		String bossName = (String) bossSelect.getSelectedItem();
		if (bossName == null)
		{
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
			"Forget the kill count and drops tracked for " + bossName + "?\nOpening its collection log page will sync it again.",
			"Reset " + bossName, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer == JOptionPane.OK_OPTION)
		{
			tracker.reset(bossName);
			save.run();
			refresh();
		}
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
				g.setColor(ColorScheme.MEDIUM_GRAY_COLOR.brighter());
				g.setFont(getFont());
				java.awt.Insets in = getInsets();
				g.drawString(hint, in.left + 2, (getHeight() + g.getFontMetrics().getAscent()) / 2 - 2);
			}
		}
	}
}
