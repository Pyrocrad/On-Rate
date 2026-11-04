package com.expecteddrops;

import com.expecteddrops.data.BossCatalog;
import com.expecteddrops.stats.RaidSettings;
import com.expecteddrops.tracking.Tracker;
import com.expecteddrops.ui.ExpectedDropsPanel;
import com.expecteddrops.ui.ViewOptions;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Assume;
import org.junit.Test;

/**
 * Developer tool, not a real test: with the environment variable RENDER_OUT=<dir> it paints the panel with made-up
 * data to PNG files, so a UI change can be looked at without starting RuneLite. Skipped otherwise.
 */
public class PanelRenderTest
{
	private static final RaidSettings RAIDS = raid -> 0.03;
	private static final int WIDTH = 246;

	@Test
	public void render() throws Exception
	{
		String out = System.getenv("RENDER_OUT");
		Assume.assumeNotNull(out);
		System.setProperty("java.awt.headless", "true");
		File dir = new File(out);
		dir.mkdirs();

		BossCatalog catalog;
		try (Reader reader = new InputStreamReader(ExpectedDropsPlugin.class.getResourceAsStream("bosses.json"), StandardCharsets.UTF_8))
		{
			catalog = BossCatalog.load(new Gson(), reader);
		}
		Tracker tracker = new Tracker(catalog);
		tracker.setSettings(RAIDS);
		long now = System.currentTimeMillis();
		boolean colourBlind = "1".equals(System.getenv("RENDER_CB"));
		String suffix = colourBlind ? "-cb" : "";

		Map<String, Integer> zulrah = new LinkedHashMap<>();
		zulrah.put("Tanzanite fang", 6);
		zulrah.put("Magic fang", 1);
		zulrah.put("Serpentine visage", 2);
		zulrah.put("Uncut onyx", 0);
		zulrah.put("Jar of swamp", 1);
		zulrah.put("Pet snakeling", 1);
		tracker.onClogPage("Zulrah", Arrays.asList("Zulrah kills: 1842"), zulrah);
		Map<String, Integer> vorkath = new LinkedHashMap<>();
		vorkath.put("Vorkath's head", 5);
		vorkath.put("Dragonbone necklace", 2);
		vorkath.put("Jar of decay", 1);
		vorkath.put("Vorki", 0);
		tracker.onClogPage("Vorkath", Arrays.asList("Vorkath kills: 2106"), vorkath);
		Map<String, Integer> nex = new LinkedHashMap<>();
		nex.put("Zaryte vambraces", 0);
		nex.put("Nihil horn", 0);
		nex.put("Ancient hilt", 0);
		nex.put("Torva full helm", 0);
		nex.put("Nexling", 0);
		tracker.onClogPage("Nex", Arrays.asList("Nex kills: 140"), nex);
		tracker.onLoot("Zulrah", 1, Arrays.asList(new Tracker.LootItem("Tanzanite fang", 1)), now - 3 * 86_400_000L);
		for (int i = 0; i < 6; i++)
		{
			tracker.onChatMessage("Your Vorkath kill count is: <col=ff0000>" + (2107 + i) + "</col>.", now - (6 - i) * 600_000L);
			tracker.onLoot("Vorkath", 1, Arrays.asList(new Tracker.LootItem("Superior dragon bones", 1)), now - (6 - i) * 600_000L);
		}
		tracker.onLoot("Vorkath", 1, Arrays.asList(new Tracker.LootItem("Dragonbone necklace", 1)), now - 60_000L);

		for (com.expecteddrops.ui.ItemView view : new com.expecteddrops.ui.ItemView[]{
			com.expecteddrops.ui.ItemView.TARGET_BARS, com.expecteddrops.ui.ItemView.COMPACT,
			com.expecteddrops.ui.ItemView.RINGS, com.expecteddrops.ui.ItemView.GAUGE})
		{
			boolean primary = view == com.expecteddrops.ui.ItemView.TARGET_BARS;
			String v = primary ? "" : "-" + view.name().toLowerCase();
			ExpectedDropsPanel panel = build(tracker, view, colourBlind, false);
			if (primary)
			{
				// tab 0 = Bosses, 1 = Items, 2 = Recent; the sort and the Items list are the usual ones
				snap(panel, new File(dir, "account" + suffix + ".png"));
				SwingUtilities.invokeAndWait(() -> panel.setAccountPageStore("1,LUCKIEST,0", page -> { }));
				snap(panel, new File(dir, "account-items" + suffix + ".png"));
				SwingUtilities.invokeAndWait(() -> panel.setAccountPageStore("2,LUCKIEST,0", page -> { }));
				snap(panel, new File(dir, "account-recent" + suffix + ".png"));
				SwingUtilities.invokeAndWait(() -> panel.setAccountPageStore("0,LUCKIEST,0", page -> { }));
				ExpectedDropsPanel welcomePanel = build(tracker, view, colourBlind, true);
				snap(welcomePanel, new File(dir, "welcome" + suffix + ".png"));
				SwingUtilities.invokeAndWait(() -> panel.showBoss("Nex"));
				snap(panel, new File(dir, "boss-early" + suffix + ".png"));
				SwingUtilities.invokeAndWait(() -> panel.showBoss("Vorkath"));
				snap(panel, new File(dir, "boss-trip" + suffix + ".png"));
			}
			SwingUtilities.invokeAndWait(() -> panel.showBoss("Zulrah"));
			snap(panel, new File(dir, "boss" + v + suffix + ".png"));
			if (!primary)
			{
				SwingUtilities.invokeAndWait(() -> panel.showBoss(ExpectedDropsPanel.ACCOUNT));
				snap(panel, new File(dir, "account" + v + suffix + ".png"));
			}
		}
	}

	private static ExpectedDropsPanel build(Tracker tracker, com.expecteddrops.ui.ItemView view, boolean colourBlind, boolean welcome)
		throws Exception
	{
		ExpectedDropsPanel[] holder = new ExpectedDropsPanel[1];
		SwingUtilities.invokeAndWait(() ->
		{
			ViewOptions options = new ViewOptions(view, true, true, true, colourBlind, true);
			holder[0] = new ExpectedDropsPanel(tracker, null, () -> RAIDS, name -> -1, () ->
			{
			}, () -> welcome, () ->
			{
			}, name -> 1_000_000L, () -> options);
			com.expecteddrops.ui.Palette.setColourBlind(colourBlind);
			holder[0].refresh();
		});
		return holder[0];
	}

	private static int height = 4000;

	private static void snap(ExpectedDropsPanel panel, File file) throws Exception
	{
		// RuneLite shows the panel in a scroll pane, which is also what makes revalidate() work
		JScrollPane scroll = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		// the panel settles over a few EDT turns: a tile that wraps asks (later) for a re-layout of its row
		for (int pass = 0; pass < 6; pass++)
		{
			SwingUtilities.invokeAndWait(() ->
			{
				invalidateAll(scroll);
				scroll.setSize(WIDTH, height);
				layout(scroll);
				scroll.validate();
				layout(scroll);
				height = Math.max(200, panel.getPreferredSize().height);
			});
		}
		SwingUtilities.invokeAndWait(() ->
		{
			int h = height;
			scroll.setSize(WIDTH, h);
			scroll.validate();
			layout(scroll);
			BufferedImage img = new BufferedImage(WIDTH * 2, h * 2, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(30, 30, 30));
			g.fillRect(0, 0, img.getWidth(), img.getHeight());
			g.scale(2, 2);
			scroll.print(g);
			if (System.getenv("RENDER_DUMP") != null)
			{
				dump(panel, "");
			}
			g.dispose();
			try
			{
				ImageIO.write(img, "png", file);
			}
			catch (java.io.IOException e)
			{
				throw new RuntimeException(e);
			}
			// give the panel back, so the next snapshot can put it in a new scroll pane
			scroll.setViewportView(null);
		});
		height = 4000;
	}

	private static void dump(Component c, String indent)
	{
		if (c.getClass().getSimpleName().equals("VerdictTile") || c.getClass().getSimpleName().equals("LuckPill"))
		{
			System.out.println(indent + c.getClass().getSimpleName() + " " + c.getBounds() + " pref=" + c.getPreferredSize() + " valid=" + c.isValid());
		}
		if (c instanceof javax.swing.JLabel && c.getParent() != null && c.getParent().getClass().getSimpleName().equals("VerdictTile"))
		{
			System.out.println(indent + ((javax.swing.JLabel) c).getText() + " " + c.getBounds() + " pref=" + c.getPreferredSize());
		}
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				dump(child, indent + " ");
			}
		}
	}

	/** Stands in for the window RuneLite puts the panel in, which re-validates what a revalidate() asked for. */
	private static void invalidateAll(Component c)
	{
		c.invalidate();
		if (c instanceof Container)
		{
			for (Component child : ((Container) c).getComponents())
			{
				invalidateAll(child);
			}
		}
	}

	private static void layout(Component c)
	{
		if (c instanceof Container)
		{
			c.doLayout();
			for (Component child : ((Container) c).getComponents())
			{
				layout(child);
			}
		}
	}
}
