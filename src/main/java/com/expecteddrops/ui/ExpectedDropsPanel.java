package com.expecteddrops.ui;

import com.expecteddrops.data.BossDefinition;
import com.expecteddrops.data.DropDefinition;
import com.expecteddrops.stats.BossStats;
import com.expecteddrops.stats.DropMath;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.stats.RowStats;
import com.expecteddrops.tracking.Tracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel: pick a boss, see expected vs received for each unique.
 */
public class ExpectedDropsPanel extends PluginPanel
{
	private static final Color ANY_COLOR = new Color(255, 200, 0);
	private static final Color GOOD = new Color(46, 160, 67);
	private static final Color MID = new Color(170, 150, 20);
	private static final Color BAD = new Color(170, 40, 30);

	private enum Tab
	{
		EXPECTED, RECEIVED
	}

	private final Tracker tracker;
	private final ItemManager itemManager;
	private final Supplier<RaidSettings> raidSettings;
	private final ToIntFunction<String> itemIdFor;
	private final Runnable save;

	private final JComboBox<String> bossSelect = new JComboBox<>();
	private final JLabel kcLabel = new JLabel();
	private final JLabel syncLabel = new JLabel();
	private final JLabel summaryLabel = new JLabel();
	private final JLabel levelsLabel = new JLabel();
	private final JButton expectedTab = new JButton("Expected");
	private final JButton receivedTab = new JButton("Received");
	private final JPanel rowsPanel = new JPanel();
	private final JLabel noteLabel = new JLabel();

	private Tab tab = Tab.EXPECTED;
	private boolean updatingSelection;

	public ExpectedDropsPanel(Tracker tracker, ItemManager itemManager, Supplier<RaidSettings> raidSettings,
		ToIntFunction<String> itemIdFor, Runnable save)
	{
		super();
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
		title.setAlignmentX(LEFT_ALIGNMENT);
		top.add(title);
		top.add(spacer(6));

		for (BossDefinition boss : tracker.getCatalog().getBosses())
		{
			bossSelect.addItem(boss.getName());
		}
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
		top.add(spacer(6));

		for (JLabel l : new JLabel[]{kcLabel, syncLabel, summaryLabel})
		{
			l.setFont(FontManager.getRunescapeSmallFont());
			l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			l.setAlignmentX(LEFT_ALIGNMENT);
			top.add(l);
		}
		kcLabel.setForeground(Color.WHITE);
		levelsLabel.setFont(FontManager.getRunescapeSmallFont());
		levelsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		levelsLabel.setAlignmentX(LEFT_ALIGNMENT);
		top.add(levelsLabel);
		top.add(spacer(6));

		JPanel tabs = new JPanel(new GridLayout(1, 2, 2, 0));
		tabs.setOpaque(false);
		tabs.setAlignmentX(LEFT_ALIGNMENT);
		tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		styleTab(expectedTab, Tab.EXPECTED);
		styleTab(receivedTab, Tab.RECEIVED);
		tabs.add(expectedTab);
		tabs.add(receivedTab);
		top.add(tabs);

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

		JButton reset = new JButton("Reset this boss");
		reset.setFont(FontManager.getRunescapeSmallFont());
		reset.setFocusPainted(false);
		reset.setAlignmentX(LEFT_ALIGNMENT);
		reset.setToolTipText("Forget the kill count and drops tracked for this boss");
		reset.addActionListener(e -> resetSelected());
		bottom.add(reset);

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
			bossSelect.setSelectedItem(bossName);
		}
		finally
		{
			updatingSelection = false;
		}
		refresh();
	}

	/** Recomputes and redraws the selected boss. Call on the Swing thread. */
	public void refresh()
	{
		String bossName = (String) bossSelect.getSelectedItem();
		rowsPanel.removeAll();
		BossStats stats = bossName == null ? null : tracker.stats(bossName, raidSettings.get());
		if (stats == null)
		{
			revalidate();
			repaint();
			return;
		}

		boolean raid = stats.getBoss().getRaid() != null;
		boolean delve = stats.getLevelCounts() != null;
		String unit = raid ? "raid" : delve ? "level" : "kill";
		if (delve)
		{
			kcLabel.setText(String.format("Total levels: %,d", stats.getKillCount()));
			levelsLabel.setText(levelTable(stats.getLevelCounts()));
			levelsLabel.setVisible(true);
		}
		else
		{
			kcLabel.setText(stats.isKillCountKnown()
				? String.format("Kill count: %,d", stats.getKillCount())
				: String.format("Kills tracked: %,d", stats.getKillCount()));
			levelsLabel.setVisible(false);
		}
		if (stats.isClogSynced())
		{
			syncLabel.setText("Collection log synced");
			syncLabel.setForeground(GOOD.brighter());
		}
		else if (stats.getBoss().getClogPages().isEmpty())
		{
			syncLabel.setText("Counting drops from loot from now on");
			syncLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		}
		else
		{
			syncLabel.setText("Open its collection log page to sync");
			syncLabel.setForeground(ColorScheme.PROGRESS_INPROGRESS_COLOR);
		}
		RowStats any = stats.getAnyRow();
		summaryLabel.setText(String.format("Uniques: %d received, %.2f expected", any.getReceived(), any.getExpected()));

		rowsPanel.add(row(any, stats, unit));
		for (RowStats row : stats.getRows())
		{
			rowsPanel.add(spacer(2));
			rowsPanel.add(row(row, stats, unit));
		}

		StringBuilder note = new StringBuilder();
		if (raid)
		{
			note.append(String.format("Assumes a %.1f%% chance of a purple per raid (change it in the plugin settings).",
				stats.getRaidUniqueChance() * 100));
		}
		if (stats.getBoss().getNote() != null)
		{
			if (note.length() > 0)
			{
				note.append("<br><br>");
			}
			note.append(stats.getBoss().getNote());
		}
		noteLabel.setText(note.length() == 0 ? "" : "<html><body style='width: 165px'>" + note + "</body></html>");

		expectedTab.setForeground(tab == Tab.EXPECTED ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		receivedTab.setForeground(tab == Tab.RECEIVED ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		expectedTab.setBorder(tabBorder(tab == Tab.EXPECTED));
		receivedTab.setBorder(tabBorder(tab == Tab.RECEIVED));

		revalidate();
		repaint();
	}

	private JPanel row(RowStats row, BossStats stats, String unit)
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
			icon.setText("Any");
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
		int number;
		if (tab == Tab.EXPECTED)
		{
			double expected = row.getExpected();
			number = (int) Math.floor(expected);
			double progress = expected - number;
			bar.set(progress, luckColor(progress), String.format("%.1f%%", progress * 100));
		}
		else
		{
			number = row.getReceived();
			String text = Double.isNaN(luck) ? "no kills yet" : String.format("Luckier than %.0f%%", luck * 100);
			bar.set(luck, luckColor(luck), text);
		}

		JLabel count = new JLabel(String.valueOf(number));
		count.setFont(FontManager.getRunescapeBoldFont());
		count.setForeground(number > 0 ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
		count.setHorizontalAlignment(SwingConstants.RIGHT);
		count.setPreferredSize(new Dimension(26, 32));

		panel.add(icon, BorderLayout.WEST);
		panel.add(bar, BorderLayout.CENTER);
		panel.add(count, BorderLayout.EAST);

		String tip = tooltip(row, stats, unit);
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
		}
		if (!row.isAny() && row.getRate() > 0 && (drop == null || !drop.hasLevelRates()))
		{
			sb.append(String.format("50%% / 90%% chance by %,d / %,d %ss",
				DropMath.killsForChance(row.getRate(), 0.5), DropMath.killsForChance(row.getRate(), 0.9), unit));
		}
		sb.append("</html>");
		return sb.toString();
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

	private static String formatPercent(double p)
	{
		if (p > 0.999)
		{
			return ">99.9%";
		}
		return String.format("%.1f%%", p * 100);
	}

	/** Red when dry, yellow around average, green when lucky. */
	static Color luckColor(double luck)
	{
		if (Double.isNaN(luck))
		{
			return ColorScheme.MEDIUM_GRAY_COLOR;
		}
		if (luck < 0.5)
		{
			return blend(BAD, MID, luck / 0.5);
		}
		return blend(MID, GOOD, (luck - 0.5) / 0.5);
	}

	private static Color blend(Color a, Color b, double t)
	{
		t = Math.max(0, Math.min(1, t));
		return new Color(
			(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			(int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
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
}
