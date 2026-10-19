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
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
	private static final Color ANY_COLOR = new Color(255, 200, 0);

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
	private boolean setupExpanded;

	private final HintField searchField = new HintField("Search bosses...");
	private final JButton accountButton = new JButton("Account");
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
	/** Boss pages: a tip when the numbers lean on a setting (raid purple chance, solo/duo). */
	private final JLabel hintLabel = new JLabel();
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
		titleRow.add(helpLabel, BorderLayout.EAST);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		top.add(titleRow);
		top.add(spacer(6));

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
		top.add(setupHeader);
		setupList.setOpaque(false);
		setupList.setAlignmentX(LEFT_ALIGNMENT);
		setupList.setBorder(new EmptyBorder(3, 8, 0, 0));
		top.add(setupList);
		top.add(spacer(8));

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
		// one click back to the account overview, wherever you are
		accountButton.setFont(FontManager.getRunescapeSmallFont());
		accountButton.setFocusPainted(false);
		accountButton.setToolTipText("Back to the account overview");
		accountButton.addActionListener(e -> showBoss(ACCOUNT));
		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchRow.setAlignmentX(LEFT_ALIGNMENT);
		searchRow.add(accountButton, BorderLayout.WEST);
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		top.add(searchRow);
		top.add(spacer(4));

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
		top.add(spacer(8));

		// header: boss icon, name and kill count, then the verdict in big letters
		JPanel header = new JPanel(new BorderLayout(6, 0));
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
		top.add(spacer(4));

		verdictLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.PLAIN, 24f));
		verdictLabel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(verdictLabel);

		for (JLabel l : new JLabel[]{summaryLabel, valueLabel, syncLabel, hintLabel})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			l.setAlignmentX(LEFT_ALIGNMENT);
			top.add(l);
		}
		levelsLabel.setFont(FontManager.getRunescapeSmallFont());
		levelsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		levelsLabel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(levelsLabel);
		top.add(spacer(6));

		graphPanel.setLayout(new BoxLayout(graphPanel, BoxLayout.Y_AXIS));
		graphPanel.setOpaque(false);
		graphPanel.setAlignmentX(LEFT_ALIGNMENT);
		JLabel graphTitle = new JLabel("Luck over time");
		graphTitle.setFont(FontManager.getRunescapeSmallFont());
		graphTitle.setForeground(Color.WHITE);
		graphTitle.setAlignmentX(LEFT_ALIGNMENT);
		graphPanel.add(graphTitle);
		graphPanel.add(spacer(2));
		graphPanel.add(graph);
		graphHint.setFont(FontManager.getRunescapeSmallFont());
		graphHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		graphHint.setAlignmentX(LEFT_ALIGNMENT);
		graphPanel.add(graphHint);
		graphPanel.add(spacer(6));
		topItemsPanel.setLayout(new BoxLayout(topItemsPanel, BoxLayout.Y_AXIS));
		topItemsPanel.setOpaque(false);
		topItemsPanel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(topItemsPanel);
		top.add(graphPanel);

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
		sortRow.setBorder(new EmptyBorder(4, 0, 0, 0));
		JLabel sortLabel = new JLabel("Sort:");
		sortLabel.setFont(FontManager.getRunescapeSmallFont());
		sortLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		sortRow.add(sortLabel, BorderLayout.WEST);
		sortSelect.setFont(FontManager.getRunescapeSmallFont());
		sortSelect.addActionListener(e -> refresh());
		sortRow.add(sortSelect, BorderLayout.CENTER);
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
		bottom.add(spacer(8));

		resetButton.setFont(FontManager.getRunescapeSmallFont());
		resetButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
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
		bottom.add(feedbackLink());
		bottom.add(spacer(4));
		bottom.add(resetButton);

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
		if (!shown)
		{
			outOfDate = true;
			return;
		}
		outOfDate = false;
		welcomePanel.setVisible(showWelcome.getAsBoolean());
		refreshSetup();

		String bossName = (String) bossSelect.getSelectedItem();
		ViewOptions o = options.get();
		Palette.setColourBlind(o.isColourBlind());
		rowsPanel.removeAll();
		styleTabs();
		boolean account = ACCOUNT.equals(bossName);
		accountButton.setEnabled(!account);
		graphPanel.setVisible(account && o.isLuckGraph());
		topItemsPanel.setVisible(account && o.isTopItems());
		topItemsPanel.removeAll();
		sortRow.setVisible(account);
		tabsRow.setVisible(o.getItemView() == ItemView.DELVE_BARS);
		syncLabel.setVisible(!account);
		valueLabel.setVisible(false);
		hintLabel.setVisible(false);
		headerIcon.setIcon(null);
		headerIcon.setText(null);
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
		String unit = raid ? "raid" : delve ? "level" : "kill";
		headerName.setText(stats.getBoss().getName() + (stats.getBoss().isEstimate() ? " *" : ""));
		headerName.setToolTipText(stats.getBoss().isEstimate() ? ESTIMATE_TIP : null);
		setHeaderIcon(stats.getBoss());
		if (combined)
		{
			kcLabel.setText(String.format(raid ? "Raids: %,d" : "Total kills: %,d", stats.getKillCount()));
			levelsLabel.setText(partTable(stats.getPartCounts()));
			levelsLabel.setVisible(true);
		}
		else if (delve)
		{
			kcLabel.setText(String.format("Total levels: %,d", stats.getKillCount()));
			levelsLabel.setText(levelTable(stats.getLevelCounts()));
			levelsLabel.setVisible(true);
		}
		else
		{
			kcLabel.setText(stats.isKillCountKnown()
				? String.format("%,d kills", stats.getKillCount())
				: String.format("%,d kills tracked", stats.getKillCount()));
			levelsLabel.setVisible(false);
		}
		String reminder = tracker.syncReminder(stats.getBoss().getName());
		if (reminder != null)
		{
			syncLabel.setText("<html><body style='width: 160px'>" + reminder + "</body></html>");
			syncLabel.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
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
			syncLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		RowStats any = stats.getAnyRow();
		boolean unsynced = reminder != null && stats.getKillCount() > 0;
		if (unsynced)
		{
			// The kill count is known but not the drops from before On Rate: any verdict would be
			// "very dry" for the wrong reason. Wait for the log page.
			verdictLabel.setText("Not synced");
			verdictLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			verdictLabel.setToolTipText("<html><body style='width: 220px'>On Rate knows your kill count, but not the drops "
				+ "you got before you installed it. Open the page below once and your verdict shows up.</body></html>");
			summaryLabel.setText(any.getExpected() < 0.005 ? "" : String.format("%s %s expected by now",
				formatExpected(any.getExpected()), raid ? "purples" : "uniques"));
		}
		else
		{
			showVerdict(any);
		}
		String verdictTip = unsynced ? null : verdictLabel.getToolTipText();
		if (verdictTip != null)
		{
			verdictLabel.setToolTipText("<html><body style='width: 220px'>" + verdictTip + "<br><br>This counts <b>how many</b> "
				+ (raid ? "purples" : "uniques") + " you got, not what they're worth: a twisted bow counts the same as a prayer "
				+ "scroll. The GE value line weighs them by price.</body></html>");
		}
		String value = o.isGeValue() && !unsynced ? bossValueText(stats) : null;
		valueLabel.setText(value == null ? "" : value);
		valueLabel.setToolTipText(value == null ? null : lastValueTip);
		valueLabel.setVisible(value != null);
		lastHintTip = null;
		String hint = unsynced ? null : settingsHint(stats);
		Color hintColor = ColorScheme.LIGHT_GRAY_COLOR;
		if (hint == null && !unsynced)
		{
			// Royal Titans and Yama count as duo unless changed: say so where it matters
			hint = duoLine(stats);
			hintColor = ColorScheme.LIGHT_GRAY_COLOR;
		}
		hintLabel.setText(hint == null ? "" : "<html><body style='width: 165px'>" + hint + "</body></html>");
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
			String olderNote = stats.getBoss().getTeam() == null ? null
				: raidSettings.get().contributionNote(stats.getBoss().getTeam(), older);
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
		noteLabel.setText(note.length() == 0 ? "" : "<html><body style='width: 165px'>" + note + "</body></html>");

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
		verdictLabel.setToolTipText(String.format("Luckier than %.0f%% of players with the same kills. "
			+ "Very dry under 10%%, dry under 35%%, on rate up to 65%%, spooned up to 90%%, very spooned above.", luck * 100));
		summaryLabel.setText(String.format("<html>%,d unique%s \u00b7 %s expected<br>Luckier than %.0f%% of players</html>",
			any.getReceived(), any.getReceived() == 1 ? "" : "s", formatExpected(any.getExpected()), luck * 100));
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
			headerIcon.setForeground(ANY_COLOR);
		}
	}

	/** The account overview: every boss with kills added up, plus one row per boss. */
	private void refreshAccount(ViewOptions o)
	{
		AccountStats account = tracker.accountStats(raidSettings.get(), o.isGeValue() ? priceOf : null);
		RowStats total = account.getTotal();
		resetButton.setVisible(false);
		levelsLabel.setText(o.isGeValue() ? valueText(account) : "");
		levelsLabel.setToolTipText(o.isGeValue() ? valueTooltip(account) : null);
		levelsLabel.setVisible(o.isGeValue() && !account.getRows().isEmpty());
		headerName.setText("Your account");
		headerName.setToolTipText(null);
		java.awt.image.BufferedImage logo = ImageUtil.loadImageResource(ExpectedDropsPanel.class, "/com/expecteddrops/panel_icon.png");
		if (logo != null)
		{
			headerIcon.setIcon(new javax.swing.ImageIcon(logo.getScaledInstance(32, 32, java.awt.Image.SCALE_FAST)));
		}
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

		List<com.expecteddrops.tracking.HistoryPoint> history = tracker.history();
		graph.setPoints(history);
		graph.setVisible(!history.isEmpty());
		graphHint.setText(history.size() >= 2 ? ""
			: "<html><body style='width: 165px'>On Rate saves one point a day. Your line starts to show from tomorrow.</body></html>");
		graphHint.setVisible(history.size() < 2);

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
		helpLabel.setToolTipText("<html><body style='width: 220px'>" + note + FEEDBACK_LINE + "</body></html>");
		noteLabel.setText(account.getRows().isEmpty() ? "<html><body style='width: 165px'>" + note + "</body></html>" : "");
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
		JLabel link = new JLabel("<html><u>Feedback or ideas? Join the Discord</u></html>");
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

	private static final String ESTIMATE_NOTE = "* estimate: depends on your team, points or settings";
	private static final String ESTIMATE_TIP = "<html><body style='width: 200px'>The rates here depend on things On Rate "
		+ "can't always see: your team size or damage, raid points, or an assumed setup (all bodyguards killed at God Wars). "
		+ "Treat the numbers as an estimate.</body></html>";

	/** One quiet line under the bosses: bosses with too few kills to list (hover for which). */
	private void addAccountFootnote(AccountStats account, List<String> hidden, List<String> unsynced)
	{
		List<String> parts = new ArrayList<>();
		StringBuilder tip = new StringBuilder("<html><body style='width: 220px'>");
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
		rowsPanel.add(spacer(4));
		JLabel line = smallLabel("<html><body style='width: 165px'>" + String.join(" \u00b7 ", parts) + "</body></html>",
			ColorScheme.LIGHT_GRAY_COLOR);
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setToolTipText(tip.append("</body></html>").toString());
		rowsPanel.add(line);
	}

	/** "today", "yesterday", "3 days ago" for a time in milliseconds. */
	static String daysAgo(long time, long now)
	{
		long days = java.time.temporal.ChronoUnit.DAYS.between(
			java.time.Instant.ofEpochMilli(time).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
			java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate());
		return days <= 0 ? "today" : days == 1 ? "yesterday" : String.format("%,d days ago", days);
	}

	/** "after 7 kills (0.1x)", "1st one", or "" when the gap is unknown. */
	private static String afterText(com.expecteddrops.tracking.KillEntry.DropEntry d, String unit)
	{
		if (d.isFirst())
		{
			return "1st one";
		}
		if (d.getSince() == null)
		{
			return "";
		}
		if (d.isAtLeast())
		{
			return String.format("after %,d+ %ss", d.getSince(), unit);
		}
		return String.format("after %,d %s%s", d.getSince(), unit, d.getSince() == 1 ? "" : "s")
			+ (d.getMultiple() == null ? "" : " (" + formatMultiple(d.getMultiple()) + ")");
	}

	/** A gap in drop rates as a colour: well under the rate is lucky, well over it is dry. */
	private static Color multipleColor(com.expecteddrops.tracking.KillEntry.DropEntry d)
	{
		Double m = d.getMultiple();
		return d.isFirst() || m == null ? ColorScheme.LIGHT_GRAY_COLOR : luckColor(Math.exp(-m)).brighter();
	}

	/** The account's Recent tab: the latest uniques across every boss. */
	private void renderRecentTab()
	{
		List<com.expecteddrops.tracking.RecentDrop> recent = tracker.recentDrops(10);
		if (recent.isEmpty())
		{
			JLabel none = smallLabel("<html><body style='width: 165px'>No uniques seen yet. Drops show up here from now on.</body></html>",
				ColorScheme.LIGHT_GRAY_COLOR);
			none.setAlignmentX(LEFT_ALIGNMENT);
			none.setBorder(new EmptyBorder(4, 0, 0, 0));
			rowsPanel.add(none);
			return;
		}
		long now = System.currentTimeMillis();
		for (com.expecteddrops.tracking.RecentDrop r : recent)
		{
			com.expecteddrops.tracking.KillEntry.DropEntry d = r.getDrop();
			BossDefinition boss = tracker.getCatalog().byName(r.getBoss());
			String unit = boss != null && boss.getRaid() != null ? "raid" : "kill";
			JPanel panel = new JPanel(new BorderLayout(6, 0));
			panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			panel.setBorder(new EmptyBorder(3, 4, 3, 4));
			panel.setAlignmentX(LEFT_ALIGNMENT);
			JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
			text.setOpaque(false);
			text.add(smallLabel(d.getName() + "  \u00b7 " + r.getBoss() + " " + String.format("%,d", r.getKill().getKc()), Color.WHITE));
			String after = afterText(d, unit);
			JLabel second = smallLabel(daysAgo(r.getKill().getTime(), now) + (after.isEmpty() ? "" : " \u00b7 " + after),
				multipleColor(d));
			text.add(second);
			panel.add(text, BorderLayout.CENTER);
			panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
			setTip(panel, "<html><b>" + d.getName() + "</b><br>" + r.getBoss() + ", kill " + String.format("%,d", r.getKill().getKc())
				+ "<br>Click to open " + r.getBoss() + "</html>", () -> showBoss(r.getBoss()));
			rowsPanel.add(spacer(2));
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
		rowsPanel.add(spacer(8));
		JLabel title = smallLabel((historyOpen ? "\u25be" : "\u25b8") + " History", Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setToolTipText("The kills and drops On Rate saw since you installed it. Click to "
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
		rowsPanel.add(title);
		if (!historyOpen)
		{
			return;
		}
		if (history.isEmpty())
		{
			addHistoryNote("No kills seen yet. History starts from your next kill.");
			return;
		}
		boolean raid = boss.getRaid() != null;
		boolean team = boss.isDetectTeam();
		boolean delve = boss.isDelve();
		boolean titans = "Royal Titans".equals(boss.getName());
		boolean dropsOnly = !raid && !team && !delve && !titans;
		long now = System.currentTimeMillis();
		int shown = 0;
		int drops = 0;
		for (com.expecteddrops.tracking.HistoryRow h : history)
		{
			com.expecteddrops.tracking.KillEntry k = h.getKill();
			drops += k.getDrops().size();
			if (shown >= 10 || (dropsOnly && k.getDrops().isEmpty()))
			{
				continue;
			}
			shown++;
			String first;
			String middle;
			if (delve)
			{
				first = "Lvl " + (k.getLevel() == null ? "?" : BossDefinition.delveLevelLabel(k.getLevel()));
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
				for (com.expecteddrops.tracking.KillEntry.DropEntry d : k.getDrops())
				{
					String after = afterText(d, unit);
					rowsPanel.add(historyLine(first, d.getName(), after.replace("after ", ""), Color.WHITE, multipleColor(d)));
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
			addHistoryNote(note.toString());
		}
	}

	private static String shortMode(String part)
	{
		switch (part)
		{
			case "Challenge Mode":
				return "CM";
			case "Hard Mode":
				return "HM";
			case "Entry Mode":
				return "Entry";
			case "Expert Mode":
				return "Expert";
			default:
				return part;
		}
	}

	private static String formatPoints(int points)
	{
		return points >= 10_000 ? String.format("%.1fk", points / 1000.0) : String.format("%,d", points);
	}

	private void addHistoryNote(String text)
	{
		JLabel note = smallLabel("<html><body style='width: 165px'>" + text + "</body></html>", ColorScheme.LIGHT_GRAY_COLOR);
		note.setAlignmentX(LEFT_ALIGNMENT);
		note.setBorder(new EmptyBorder(3, 0, 0, 0));
		rowsPanel.add(note);
	}

	/** A History line with loot: what happened on top, the loot in purple underneath. */
	private JPanel historyLootLine(String first, String middle, String loot)
	{
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(2, 4, 2, 4)));
		line.setAlignmentX(LEFT_ALIGNMENT);
		JLabel kc = smallLabel(first, Color.WHITE);
		kc.setPreferredSize(new Dimension(38, 14));
		kc.setVerticalAlignment(SwingConstants.TOP);
		line.add(kc, BorderLayout.WEST);
		JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
		text.setOpaque(false);
		text.add(smallLabel(middle, ColorScheme.LIGHT_GRAY_COLOR));
		text.add(smallLabel(loot, new Color(192, 112, 255)));
		line.add(text, BorderLayout.CENTER);
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		line.setToolTipText("<html>" + first + " \u00b7 " + middle + "<br>" + loot + "</html>");
		return line;
	}

	/** One History line: kill count on the left, then what happened, then the loot or gap. */
	private JPanel historyLine(String first, String middle, String last, Color middleColor, Color lastColor)
	{
		JPanel line = new JPanel(new BorderLayout(6, 0));
		line.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		line.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(2, 4, 2, 4)));
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
		tabs.setBorder(new EmptyBorder(6, 0, 2, 0));
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
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? ANY_COLOR : ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(2, 4, 3, 4)));
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
		return label;
	}

	/** The Items tab: the 10 driest (or luckiest) items across every boss, in the chosen luck view. */
	private void renderItemsTab(AccountStats account, ItemView view)
	{
		JPanel switcher = new JPanel(new GridLayout(1, 3, 2, 0));
		switcher.setOpaque(false);
		switcher.setAlignmentX(LEFT_ALIGNMENT);
		switcher.setBorder(new EmptyBorder(4, 0, 4, 0));
		switcher.add(topSwitch("Driest", topMode == TOP_DRIEST, TOP_DRIEST));
		switcher.add(topSwitch("Luckiest", topMode == TOP_LUCKIEST, TOP_LUCKIEST));
		switcher.add(topSwitch("Pets", topMode == TOP_PETS, TOP_PETS));
		switcher.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		rowsPanel.add(switcher);

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
				title.setBorder(new EmptyBorder(6, 0, 0, 0));
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
				tb.append(String.format("%s: luckier than %.0f%% of players<br>", DropMath.verdict(luck), luck * 100))
					.append(plainLuck(luck)).append("<br>");
			}
			String next = nextLine(r, "kill");
			if (next != null)
			{
				tb.append(next).append("<br>");
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
				rowsPanel.add(spacer(2));
				rowsPanel.add(luckRow(r, tip, open));
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
		rowsPanel.add(spacer(8));
		JLabel title = smallLabel("Shared with other bosses", Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setToolTipText("<html><body style='width: 200px'>These drop from other bosses too, and the collection log has "
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
				rowsPanel.add(spacer(2));
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
			rowsPanel.add(spacer(4));
			JLabel line = smallLabel("<html><body style='width: 165px'>" + text + "</body></html>", ColorScheme.LIGHT_GRAY_COLOR);
			line.setAlignmentX(LEFT_ALIGNMENT);
			rowsPanel.add(line);
		}
	}

	/** "Vorkath", "Vorkath and Zulrah", "Vorkath, Zulrah and Cerberus". */
	public static String joinAnd(List<String> names)
	{
		if (names.size() <= 1)
		{
			return String.join("", names);
		}
		return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}

	private static String lowerFirst(String text)
	{
		return text.isEmpty() ? text : Character.toLowerCase(text.charAt(0)) + text.substring(1);
	}

	/** Hover text for the boss page's GE value line, set by {@link #bossValueText}. */
	private String lastValueTip;

	/** GE value of this boss's uniques, received vs expected, with its own verdict; null without prices. */
	private String bossValueText(BossStats stats)
	{
		List<Double> lambdas = new ArrayList<>();
		List<Long> itemPrices = new ArrayList<>();
		double expected = 0;
		long received = 0;
		for (int i = 0; i < stats.getBoss().getDrops().size(); i++)
		{
			DropDefinition drop = stats.getBoss().getDrops().get(i);
			RowStats row = stats.getRows().get(i);
			long price = drop.countsTowardAny() ? Math.max(0, priceOf.applyAsLong(drop.getName())) : 0;
			if (price <= 0)
			{
				continue;
			}
			lambdas.add(row.getExpected());
			itemPrices.add(price);
			expected += row.getExpected() * price;
			received += row.getReceived() * price;
		}
		if (expected <= 0)
		{
			return null;
		}
		double[] l = new double[lambdas.size()];
		long[] p = new long[itemPrices.size()];
		for (int i = 0; i < l.length; i++)
		{
			l[i] = lambdas.get(i);
			p[i] = itemPrices.get(i);
		}
		double luck = DropMath.valueLuck(l, p, received, 5000, 1234567L);
		if (Double.isNaN(luck))
		{
			return null;
		}
		String color = String.format("#%06x", luckColor(luck).brighter().getRGB() & 0xFFFFFF);
		lastValueTip = String.format("<html><body style='width: 200px'>GE value of the uniques you got here: %s<br>"
			+ "What you should have had by now: %s<br>Luckier than %.0f%% of players by value.<br><br>"
			+ "The big verdict counts how many uniques you got; this weighs them by price. "
			+ "Untradeable items (pets, jars...) count as 0 GP.</body></html>",
			DropMath.formatGp(received), DropMath.formatGp((long) expected), luck * 100);
		return String.format("<html>GE value: %s / %s \u00b7 <font color='%s'>%s</font></html>",
			DropMath.formatGp(received), DropMath.formatGp((long) expected), color, DropMath.verdict(luck));
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
		lastHintTip = String.format("<html><body style='width: 220px'>%,d of your %,d raids have no points On Rate could read "
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
		JLabel label = smallLabel(text, selected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? ANY_COLOR : ColorScheme.DARK_GRAY_COLOR),
			new EmptyBorder(0, 4, 1, 4)));
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
		return label;
	}

	private static String poolTooltip(com.expecteddrops.stats.SharedPool pool, RowStats row)
	{
		StringBuilder sb = new StringBuilder("<html><body style='width: 200px'><b>").append(pool.getLabel()).append("</b><br>");
		sb.append("Dropped by: ").append(String.join(", ", pool.getBosses())).append("<br>");
		sb.append(String.format("Expected from all of them: %s<br>In your collection log: %d<br>",
			formatExpected(pool.getExpected()), pool.getReceived()));
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			sb.append(String.format("Luckier than %.0f%% of players: %s", luck * 100, DropMath.verdict(luck)));
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
		return String.format("<html><body style='width: 200px'>GE value of the uniques you got: %s<br>"
			+ "What you should have had by now: %s<br>Luckier than %.0f%% of players by value.<br><br>"
			+ "Uses RuneLite's own price list. Untradeable items (pets, jars...) count as 0 GP here.</body></html>",
			DropMath.formatGp(account.getReceivedValue()), DropMath.formatGp(account.getExpectedValue()), luck * 100);
	}

	private static String accountTooltip(RowStats row, AccountStats account)
	{
		StringBuilder sb = new StringBuilder("<html><b>");
		sb.append(row.isAny() ? "Whole account" : row.getShownLabel()).append("</b><br>");
		if (row.isAny())
		{
			sb.append(account.getRows().size()).append(" bosses with kills<br>");
		}
		else
		{
			sb.append(String.format("Kill count: %,d<br>", account.getKillCount(row.getLabel())));
		}
		sb.append(String.format("Expected: %s &nbsp;Received: %d<br>", formatExpected(row.getExpected()), row.getReceived()));
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			sb.append(String.format("Luckier than %.0f%% of players: %s", luck * 100, DropMath.verdict(luck)));
			sb.append("<br>").append(plainLuck(luck));
		}
		if (row.isAny() && account.getExpectedValue() > 0 && !Double.isNaN(account.getValueLuck()))
		{
			sb.append(String.format("<br><br>GE value received: %s<br>GE value expected: %s<br>Value luck: luckier than %.0f%% (%s)",
				DropMath.formatGp(account.getReceivedValue()), DropMath.formatGp(account.getExpectedValue()),
				account.getValueLuck() * 100, DropMath.verdict(account.getValueLuck())));
			sb.append("<br><i>Untradeable items (pets, jars...) count as 0 GP here.</i>");
		}
		if (!row.isAny() && row.getDriestItem() != null)
		{
			sb.append(String.format("<br>Driest right now: %s at %s the rate", row.getDriestItem(),
				formatMultiple(row.getDriestMultiple())));
		}
		if (!row.isAny())
		{
			sb.append("<br>Click to open");
		}
		return sb.append("</html>").toString();
	}

	// ---------------------------------------------------------------- item views

	private static final int SMALL_ICON = 18;
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
					+ "(luckier than that % of players with the same kills).";
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
		return "<html><body style='width: 220px'>" + how + "<br><br>Hover an item for its drop rate, your chances "
			+ "and your dry streak. You can pick another view in the plugin settings (Display)." + FEEDBACK_LINE + "</body></html>";
	}

	private void renderBossItems(BossStats stats, String unit, ItemView view)
	{
		List<RowStats> all = new ArrayList<>();
		all.add(stats.getAnyRow());
		all.addAll(stats.getRows());
		switch (view)
		{
			case DELVE_BARS:
				for (int i = 0; i < all.size(); i++)
				{
					if (i > 0)
					{
						rowsPanel.add(spacer(2));
					}
					rowsPanel.add(row(all.get(i), stats, unit));
				}
				break;
			case COMPACT:
				rowsPanel.add(compactHeader("Item"));
				for (RowStats r : all)
				{
					rowsPanel.add(compactRow(r, tooltip(r, stats, unit), null));
				}
				break;
			case RINGS:
				JPanel grid = new JPanel(new GridLayout(0, 3, 4, 8));
				grid.setOpaque(false);
				grid.setAlignmentX(LEFT_ALIGNMENT);
				for (RowStats r : all)
				{
					grid.add(itemRing(r, tooltip(r, stats, unit)));
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
					rowsPanel.add(spacer(2));
					rowsPanel.add(stripRow(r.isAny() ? null : r.getIconItem(), r.isAny() ? "Any unique" : r.getLabel(),
						r.isAny(), r.isComplete() ? 1.0 : r.getLuck(), tooltip(r, stats, unit), null));
				}
				break;
			default:
				for (int i = 0; i < all.size(); i++)
				{
					if (i > 0)
					{
						rowsPanel.add(spacer(2));
					}
					rowsPanel.add(targetRow(all.get(i), tooltip(all.get(i), stats, unit)));
				}
		}
	}

	private void renderAccountItems(AccountStats account, RowStats total, List<RowStats> rows, ItemView view)
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
					rowsPanel.add(spacer(2));
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
					rowsPanel.add(spacer(2));
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
						rowsPanel.add(spacer(2));
					}
					rowsPanel.add(luckRow(row, accountTooltip(row, account), () -> showBoss(row.getLabel())));
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
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(3, 4, 3, 4));
		panel.setAlignmentX(LEFT_ALIGNMENT);

		JPanel line = new JPanel(new BorderLayout(4, 0));
		line.setOpaque(false);
		JLabel name;
		if (r.isAny())
		{
			name = smallLabel("Any unique", ANY_COLOR);
		}
		else
		{
			line.add(itemIcon(r.getIconItem(), r.getLabel(), SMALL_ICON), BorderLayout.WEST);
			name = smallLabel(r.getShownLabel(), Color.WHITE);
		}
		line.add(name, BorderLayout.CENTER);
		boolean have = r.isComplete();
		line.add(smallLabel(have ? "obtained" : gotOfExpected(r), Color.WHITE), BorderLayout.EAST);
		panel.add(line, BorderLayout.CENTER);

		TargetBar bar = new TargetBar();
		if (have)
		{
			bar.set(1, -1, Palette.good());
		}
		else
		{
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
		JPanel panel = new JPanel(new BorderLayout(4, 0));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(2, 2, 2, 4));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(itemIcon(r.getIconItem(), r.getLabel(), SMALL_ICON), BorderLayout.WEST);
		DropBar bar = new DropBar();
		double luck = r.getLuck();
		bar.set(Double.isNaN(luck) ? 0 : luck, luckColor(luck), r.getShownLabel(), Double.isNaN(luck) ? "-" : DropMath.verdict(luck));
		bar.setPreferredSize(new Dimension(100, 22));
		panel.add(bar, BorderLayout.CENTER);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		setTip(panel, tip, onClick);
		return panel;
	}

	private JPanel compactHeader(String first)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);
		panel.setBorder(new EmptyBorder(0, 4, 2, 4));
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
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR), new EmptyBorder(2, 4, 2, 4)));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		if (r.isAny())
		{
			panel.add(smallLabel("Any unique", ANY_COLOR), BorderLayout.CENTER);
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
		right.add(new Chip(Double.isNaN(luck) ? "-" : String.format("%.0f%%", luck * 100), luckColor(luck)), BorderLayout.EAST);
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
		panel.setBorder(new EmptyBorder(3, 4, 3, 4));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		JPanel left = new JPanel(new BorderLayout(4, 0));
		left.setOpaque(false);
		if (!any)
		{
			left.add(itemIcon(item, label, 16), BorderLayout.WEST);
		}
		left.add(smallLabel(label, any ? ANY_COLOR : Color.WHITE), BorderLayout.CENTER);
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
			ring.setForeground(ANY_COLOR);
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

	private JPanel ringCell(LuckRing ring, String name, String sub, String tip, Runnable onClick)
	{
		JPanel cell = new JPanel(new BorderLayout(0, 1));
		cell.setOpaque(false);
		cell.add(ring, BorderLayout.CENTER);
		JPanel labels = new JPanel(new GridLayout(2, 1));
		labels.setOpaque(false);
		JLabel n = smallLabel(name, Color.WHITE);
		n.setHorizontalAlignment(SwingConstants.CENTER);
		JLabel s2 = smallLabel(sub, ColorScheme.LIGHT_GRAY_COLOR);
		s2.setHorizontalAlignment(SwingConstants.CENTER);
		labels.add(n);
		labels.add(s2);
		cell.add(labels, BorderLayout.SOUTH);
		setTip(cell, tip, onClick);
		return cell;
	}

	/** Tooltip (and click) on a row and everything in it. */
	private static void setTip(JPanel panel, String tip, Runnable onClick)
	{
		MouseAdapter click = onClick == null ? null : new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
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
			BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE), new EmptyBorder(4, 6, 6, 6)));
		card.setAlignmentX(LEFT_ALIGNMENT);

		long minutes = Math.max(0, (trip.getLastMillis() - trip.getStartMillis()) / 60_000);
		JPanel head = new JPanel(new BorderLayout());
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		head.add(smallLabel("THIS TRIP", ColorScheme.BRAND_ORANGE), BorderLayout.WEST);
		head.add(smallLabel(minutes >= 60 ? String.format("%dh %02dm", minutes / 60, minutes % 60) : minutes + " min",
			ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		card.add(head);
		card.add(spacer(4));

		JPanel tiles = new JPanel(new GridLayout(1, 3, 4, 0));
		tiles.setOpaque(false);
		tiles.setAlignmentX(LEFT_ALIGNMENT);
		tiles.add(tile("Kills", String.format("%,d", trip.getKills())));
		tiles.add(tile("Uniques", String.valueOf(trip.getReceived())));
		tiles.add(tile("Expected", formatExpected(trip.getExpected())));
		card.add(tiles);
		card.add(spacer(4));

		double perHour = trip.getKillsPerHour();
		if (!Double.isNaN(perHour))
		{
			JLabel rate = smallLabel(String.format("%.1f kills per hour", perHour), Color.WHITE);
			rate.setAlignmentX(LEFT_ALIGNMENT);
			card.add(rate);
		}
		double luck = trip.getLuck();
		if (!Double.isNaN(luck))
		{
			JLabel v = smallLabel(String.format("%s this trip (luckier than %.0f%%)", DropMath.verdict(luck), luck * 100),
				luckColor(luck).brighter());
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
		card.add(spacer(4));
		JButton reset = new JButton("Start a new trip");
		reset.setFont(FontManager.getRunescapeSmallFont());
		reset.setFocusPainted(false);
		reset.setAlignmentX(LEFT_ALIGNMENT);
		reset.setToolTipText("The next kill starts a new trip. A trip also ends after 30 minutes without a kill.");
		reset.addActionListener(e ->
		{
			tracker.resetTrip();
			refresh();
		});
		card.add(reset);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));
		rowsPanel.add(card);
		rowsPanel.add(spacer(6));
	}

	private static JPanel tile(String title, String value)
	{
		JPanel t = new JPanel(new BorderLayout());
		t.setBackground(new Color(45, 45, 45));
		t.setBorder(new EmptyBorder(2, 2, 3, 2));
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
			new EmptyBorder(8, 8, 8, 8)));

		JLabel text = new JLabel("<html><body style='width: 140px'>"
			+ "<b>Welcome to On Rate!</b><br><br>"
			+ "<b>1.</b> Open your <b>collection log</b> and click the pages of the bosses you do. "
			+ "That loads your past drops.<br><br>"
			+ "<b>2.</b> Go kill something. The panel follows your kills (keep the <b>Loot Tracker</b> plugin on).<br><br>"
			+ "<b>3.</b> <i>Got it</i> opens the settings: pick a <i>Luck view</i> you like, and check <i>Team bosses</i> if you solo Royal Titans or Yama.<br><br>"
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
		if (missing.isEmpty())
		{
			setupHeader.setText("\u2714 All history loaded (" + done + " pages)");
			setupHeader.setForeground(Palette.good().brighter());
			setupHeader.setCursor(Cursor.getDefaultCursor());
			setupList.setVisible(false);
			return;
		}

		// Pages of bosses you killed come first; the rest only matter for bosses you do.
		missing.sort((a, b) -> Boolean.compare(b.isKilled(), a.isKilled()));
		// a boss can need two pages (Doom: its log page and the scoreboard): count bosses, not pages
		long killed = missing.stream().filter(SyncStatus::isKilled).flatMap(st -> st.getBosses().stream()).distinct().count();
		String status;
		Color statusColor = ColorScheme.PROGRESS_INPROGRESS_COLOR;
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
		setupHeader.setToolTipText("<html><body style='width: 200px'>Open these pages in your collection log to load your "
			+ "kill count and past drops. Only the bosses you do matter; you don't need all " + checklist.size() + ".</body></html>");
		setupHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		setupList.setVisible(setupExpanded);
		if (!setupExpanded)
		{
			return;
		}
		JLabel intro = new JLabel("<html><body style='width: 160px'>Open these in your collection log. "
			+ "Only the bosses you do matter.</body></html>");
		intro.setFont(FontManager.getRunescapeSmallFont());
		intro.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		setupList.add(intro);
		boolean othersTitle = false;
		for (SyncStatus s : missing)
		{
			if (killed > 0 && !s.isKilled() && !othersTitle)
			{
				JLabel others = new JLabel("Other bosses:");
				others.setFont(FontManager.getRunescapeSmallFont());
				others.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
				others.setBorder(new EmptyBorder(4, 0, 0, 0));
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
		panel.setBorder(new EmptyBorder(2, 2, 2, 4));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(36, 32));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		if (row.isAny())
		{
			icon.setText(row.getLabel());
			icon.setFont(FontManager.getRunescapeBoldFont());
			icon.setForeground(ANY_COLOR);
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
				bar.set(progress, luckColor(progress), name, String.format("%.0f%%", progress * 100));
			}
			else
			{
				bar.set(progress, luckColor(progress), String.format("%.1f%%", progress * 100));
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
				String text = Double.isNaN(luck) ? "no kills yet" : String.format("Luckier than %.0f%%", luck * 100);
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
		StringBuilder sb = new StringBuilder("<html><b>");
		sb.append(row.isAny() ? "Any unique" : row.getLabel()).append("</b><br>");
		DropDefinition drop = row.isAny() ? null : findDrop(stats.getBoss(), row.getLabel());
		if (drop != null && drop.hasLevelRates())
		{
			sb.append("Rate per delve level:<br>");
			for (int level = 1; level <= BossDefinition.DELVE_LEVELS; level++)
			{
				String rate = drop.getLevelRate(level);
				if (rate != null)
				{
					sb.append("&nbsp;&nbsp;").append(BossDefinition.delveLevelLabel(level)).append(": ")
						.append(DropMath.formatRate(DropMath.parseRate(rate))).append("<br>");
				}
			}
		}
		else if (drop != null && drop.hasPartRates())
		{
			sb.append("Rate per kill:<br>");
			for (java.util.Map.Entry<String, Integer> e : stats.getPartCounts().entrySet())
			{
				String rate = drop.getPartRate(e.getKey());
				if (rate != null)
				{
					sb.append("&nbsp;&nbsp;").append(e.getKey()).append(": ")
						.append(DropMath.formatRate(DropMath.parseRate(rate))).append("<br>");
				}
			}
		}
		else if (!row.isAny())
		{
			sb.append("Rate: ").append(DropMath.formatRate(row.getRate())).append(" per ").append(unit).append("<br>");
		}
		sb.append(String.format("Expected: %s &nbsp;Received: %d<br>", formatExpected(row.getExpected()), row.getReceived()));
		sb.append("Chance of at least one by now: ").append(formatPercent(row.getChanceAtLeastOne())).append("<br>");
		double luck = row.getLuck();
		if (!Double.isNaN(luck))
		{
			sb.append(String.format("Luckier than %.0f%% of players at %,d %ss<br>", luck * 100, stats.getKillCount(), unit));
			sb.append("<b>").append(plainLuck(luck)).append("</b><br>");
		}
		if (!row.isAny() && row.getRate() > 0 && (drop == null || (!drop.hasLevelRates() && !drop.hasPartRates())))
		{
			sb.append(String.format("50%% / 90%% chance by %,d / %,d %ss<br>",
				DropMath.killsForChance(row.getRate(), 0.5), DropMath.killsForChance(row.getRate(), 0.9), unit));
		}
		String next = nextLine(row, unit);
		if (next != null)
		{
			sb.append(next).append("<br>");
		}
		appendStreak(sb, row, unit);
		sb.append("</html>");
		return sb.toString();
	}

	/**
	 * Luck in plain words: "About 1 in 6 players is this dry (or drier)". Around the middle it
	 * just says you're about average.
	 */
	public static String plainLuck(double luck)
	{
		if (Double.isNaN(luck))
		{
			return null;
		}
		if (luck >= 0.4 && luck <= 0.6)
		{
			return "About half of players have fewer by now: right in the middle";
		}
		if (luck < 0.5)
		{
			long n = Math.max(2, Math.round(1 / Math.max(luck, 0.0001)));
			return String.format("About 1 in %,d players is this dry (or drier)", n);
		}
		long n = Math.max(2, Math.round(1 / Math.max(1 - luck, 0.0001)));
		return String.format("About 1 in %,d players is this lucky (or luckier)", n);
	}

	/** Chance of at least one more in the next {@code kills}, at {@code rate} per kill. */
	public static double nextChance(double rate, int kills)
	{
		return rate <= 0 ? 0 : -Math.expm1(-rate * kills);
	}

	/** "Next 100 kills: 87% chance of another" for a row, or null when it can't drop (more). */
	private static String nextLine(RowStats row, String unit)
	{
		if (row.getRate() <= 0 || row.isComplete())
		{
			return null;
		}
		return String.format("Next 100 %ss: %s chance of %s", unit, formatPercent(nextChance(row.getRate(), 100)),
			row.getReceived() > 0 ? (row.isAny() ? "another unique" : "another") : (row.isAny() ? "a unique" : "one"));
	}

	private static void appendStreak(StringBuilder sb, RowStats row, String unit)
	{
		if (row.getStreak() == null)
		{
			return;
		}
		sb.append("<br><b>Dry streak</b><br>");
		if (!row.isStreakExact() && row.getStreak() == 0)
		{
			sb.append("Your last one came before On Rate,<br>so it counts your streak from your next ").append(unit).append(".<br>");
			return;
		}
		String since = row.getReceived() > 0 || !row.isStreakExact()
			? (row.isAny() ? "since your last unique" : "since your last one")
			: (row.isAny() ? "without a unique so far" : "without one so far");
		sb.append(String.format("%s%,d %ss %s<br>", row.isStreakExact() ? "" : "At least ",
			row.getStreak(), unit, since));
		if (!row.isStreakExact())
		{
			sb.append("(counted from when On Rate started tracking)<br>");
		}
		double m = row.getStreakMultiple();
		if (!Double.isNaN(m) && row.getRate() > 0)
		{
			sb.append("That's ").append(row.isStreakExact() ? "" : "at least ").append(formatMultiple(m))
				.append(" the drop rate<br>");
		}
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

	/** Two-column table of completions per delve level, like the Doom plugin's header. */
	private static String levelTable(java.util.Map<Integer, Integer> levels)
	{
		StringBuilder sb = new StringBuilder("<html><table cellpadding=0 cellspacing=0>");
		int half = (BossDefinition.DELVE_LEVELS + 1) / 2;
		for (int i = 1; i <= half; i++)
		{
			sb.append("<tr>").append(levelCell(i, levels.get(i)));
			int j = i + half;
			if (j <= BossDefinition.DELVE_LEVELS)
			{
				sb.append("<td width=12></td>").append(levelCell(j, levels.get(j)));
			}
			sb.append("</tr>");
		}
		return sb.append("</table></html>").toString();
	}

	/** Kill count per boss on a combined page, e.g. "Dagannoth Rex: 436". */
	private static String partTable(java.util.Map<String, Integer> parts)
	{
		StringBuilder sb = new StringBuilder("<html><table cellpadding=0 cellspacing=0>");
		for (java.util.Map.Entry<String, Integer> e : parts.entrySet())
		{
			sb.append(String.format("<tr><td><font color='#ffcc00'>%s:</font></td><td align=right>&nbsp;%,d</td></tr>",
				e.getKey(), e.getValue()));
		}
		return sb.append("</table></html>").toString();
	}

	private static String levelCell(int level, Integer count)
	{
		return String.format("<td><font color='#ffcc00'>Level %s:</font></td><td align=right>&nbsp;%,d</td>",
			BossDefinition.delveLevelLabel(level), count == null ? 0 : count);
	}

	private static String formatExpected(double expected)
	{
		if (expected >= 100)
		{
			return String.format("%.0f", expected);
		}
		if (expected >= 10)
		{
			return String.format("%.1f", expected);
		}
		return String.format("%.2f", expected);
	}

	private static String formatMultiple(double m)
	{
		if (m < 0.1)
		{
			return String.format("%.2f\u00d7", m);
		}
		return m < 10 ? String.format("%.1f\u00d7", m) : String.format("%.0f\u00d7", m);
	}

	private static String formatPercent(double p)
	{
		if (p > 0.999)
		{
			return ">99.9%";
		}
		return String.format("%.1f%%", p * 100);
	}

	/** Red when dry, yellow around average, green when lucky (or the colour-blind palette). */
	static Color luckColor(double luck)
	{
		return Palette.luck(luck);
	}

	private static String initials(String name)
	{
		StringBuilder sb = new StringBuilder();
		for (String part : name.split("[\\s(]+"))
		{
			if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0)) && sb.length() < 3)
			{
				sb.append(Character.toUpperCase(part.charAt(0)));
			}
		}
		return sb.toString();
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
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(4, 0, 2, 0));
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
